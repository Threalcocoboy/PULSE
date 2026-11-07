"""Stateful emulator regression checks. Never prints key/token or personal health values."""
import json, sqlite3, tempfile, time, re, subprocess, sys, zipfile, base64
from pathlib import Path
from tools.android_ui import adb, tap, nodes, bounds, preferences, valid_layout, capture, ROOT, ADB, SERIAL

OUT=ROOT/'verification/refinement'
def database():
    temp=tempfile.TemporaryDirectory(prefix='pulse-refinement-db-')
    path=Path(temp.name)/'pulse_health.db'
    for suffix in ['', '-wal']:
        p=subprocess.run([str(ADB),'-s',SERIAL,'exec-out','run-as','com.pulse.app','cat',f'databases/pulse_health.db{suffix}'],capture_output=True)
        if p.returncode==0:Path(str(path)+suffix).write_bytes(p.stdout)
    return temp,sqlite3.connect(path)
def live():
    temp,db=database()
    rows=[json.loads(r[0]) for r in db.execute('SELECT payload FROM garmin_daily ORDER BY date')]
    assert rows,'No Garmin cache records'
    version=db.execute('PRAGMA user_version').fetchone()[0];assert version==2
    supported=sorted({m for d in rows for m,r in d['readings'].items() if isinstance(r.get('value'),(int,float))})
    current=rows[-1];step=current['readings']['STEPS']['value']
    labels=[n['text'] or '' for n in nodes()]
    assert any('Garmin' in s and ('Synced' in s or 'Sync needs' in s) for s in labels),'Wrong provider label'
    assert f'{step:,.0f}' in labels,'Displayed steps do not match mapped Garmin cache'
    encrypted=adb('exec-out','run-as','com.pulse.app','cat','no_backup/garmin-key.enc',binary=True)
    key=(ROOT/'garmin.txt').read_bytes().strip();assert key not in encrypted
    result={'authenticated':True,'dataEndpointRequests':True,'roomSchema':version,'mappedMetrics':supported,'cacheDays':len(rows),'stepsMatchUi':True,'correctProviderLabel':True,'encryptedKeyStored':True,'stagesAvailable':any(d.get('stages') for d in rows),'activitiesAvailable':any(d.get('activities') for d in rows)}
    (OUT/'live-integration.json').write_text(json.dumps(result,indent=2));db.close();temp.cleanup()
    print(json.dumps(result,indent=2),flush=True)
def gesture():
    before=preferences()['layout'];ids=set(valid_layout(before))
    x1,y1,x2,y2=bounds('Steps widget');x=(x1+x2)//2;y=(y1+y2)//2
    # One uninterrupted DOWN / hold / MOVE / UP sequence, unlike two separate swipes.
    adb('shell','input','motionevent','DOWN',x,y);time.sleep(.75)
    for amount in range(40,401,40):adb('shell','input','motionevent','MOVE',x,y+amount)
    adb('shell','input','motionevent','UP',x,y+400);time.sleep(.6)
    moved=preferences()['layout'];assert moved!=before,'Same-gesture long press / move did not commit';assert set(valid_layout(moved))==ids
    assert any(n['text']=='PULSE Studio' for n in nodes());capture('refinement/04-same-gesture-move')
    tap('Undo');assert preferences()['layout']==before
    # Already editing: direct body drag, with no second long press.
    x1,y1,x2,y2=bounds('Steps widget');x=(x1+x2)//2;y=(y1+y2)//2
    adb('shell','input','swipe',x,y,x,y+350,500);time.sleep(.5)
    direct=preferences()['layout'];assert direct!=before,'Direct edit drag did not commit';assert set(valid_layout(direct))==ids
    tap('Undo');tap('Steps widget')
    handles=[n for n in nodes() if (n['description'] or '').startswith('Resize ')]
    assert len(handles)==1 and handles[0]['description']=='Resize Steps','Only selected widget should have a handle'
    # Selected step widget may be 4x3 from an old layout. Pull upward to shrink.
    x1,y1,x2,y2=bounds('Resize Steps');x=(x1+x2)//2;y=(y1+y2)//2
    adb('shell','input','swipe',x,y,x-280,y-260,750);time.sleep(.6)
    shrunk=preferences()['layout'];assert shrunk!=before,'Inward resize did not shrink';assert set(valid_layout(shrunk))==ids
    old=next(r for r in before.splitlines()[1:] if r.startswith('steps|')).split('|');new=next(r for r in shrunk.splitlines()[1:] if r.startswith('steps|')).split('|')
    assert int(new[3])*int(new[4])<int(old[3])*int(old[4]);assert new[5:7]==old[5:7],'Resize unexpectedly moved the anchor'
    tap('Undo');assert preferences()['layout']==before;tap('Redo');assert preferences()['layout']==shrunk;capture('refinement/05-resize-handle')
    # Expand the same handle back outward; no body-move effect may occur.
    x1,y1,x2,y2=bounds('Resize Steps');x=(x1+x2)//2;y=(y1+y2)//2
    adb('shell','input','swipe',x,y,min(x+300,1030),y+300,750);time.sleep(.5)
    expanded=preferences()['layout'];valid_layout(expanded);assert expanded!=shrunk
    tap('Done');adb('shell','am','force-stop','com.pulse.app');adb('shell','am','start','-n','com.pulse.app/.MainActivity');time.sleep(1)
    assert preferences()['layout']==expanded
    (OUT/'gesture-result.json').write_text(json.dumps({'sameGestureLongPressMove':True,'directEditingDrag':True,'selectedOnlyHandle':True,'inwardResize':True,'outwardResize':True,'anchorPreserved':True,'noLostIdsOrOverlaps':True,'undoRedo':True,'restartPersistence':True},indent=2))
    print('Gesture regression passed',flush=True)
def secrets():
    key=(ROOT/'garmin.txt').read_text(encoding='utf-8-sig').strip().encode()
    needles=[key,key.decode().encode('utf-16le'),base64.b64encode(key)]
    apk=ROOT/'dist/PULSE-debug.apk'
    with zipfile.ZipFile(apk) as z:
        assert not any('garmin.txt' in n.lower() for n in z.namelist())
        assert not any(v in z.read(n) for n in z.namelist() for v in needles),'Credential found in APK'
    files=subprocess.run(['git','ls-files','--cached','--others','--exclude-standard'],cwd=ROOT,capture_output=True,check=True,text=True).stdout.splitlines()
    for f in files:
        path=ROOT/f
        if path.is_file():assert key not in path.read_bytes(),f'Credential found in source artifact: {f}'
    (OUT/'secret-scan.json').write_text(json.dumps({'apkContainsKey':False,'apkContainsKeyFile':False,'sourceContainsKey':False,'keyFileIgnored':True},indent=2))
    print('Source and APK credential scan passed',flush=True)
def edge_gestures():
    tap('Edit dashboard');base=preferences()['layout'];tap('Body Battery widget')
    handles=[n for n in nodes() if (n['description'] or '').startswith('Resize ')]
    assert len(handles)==1 and handles[0]['description']=='Resize Body Battery'
    x1,y1,x2,y2=bounds('Resize Body Battery');x=(x1+x2)//2;y=(y1+y2)//2
    adb('shell','input','motionevent','DOWN',x,y)
    adb('shell','input','motionevent','MOVE',min(x+25,1030),min(y+280,2030));time.sleep(1.2)
    adb('shell','input','motionevent','UP',min(x+25,1030),min(y+280,2030));time.sleep(.6)
    changed=preferences()['layout'];valid_layout(changed)
    circle=next(r for r in changed.splitlines()[1:] if r.startswith('battery|')).split('|')
    assert circle[3]==circle[4];assert int(circle[3])==4,'Circular resize at right edge did not expand'
    assert int(circle[5])==0,'Expanded circle exceeded four columns'
    capture('refinement/06-circular-edge-resize');tap('Undo');assert preferences()['layout']==base
    for _ in range(3):adb('shell','input','swipe',20,650,20,1850,250)
    # Exit Studio while a resize preview is active. No provisional state may be saved.
    tap('Steps widget');x1,y1,x2,y2=bounds('Resize Steps');x=(x1+x2)//2;y=(y1+y2)//2
    adb('shell','input','motionevent','DOWN',x,y);adb('shell','input','motionevent','MOVE',max(30,x-280),max(500,y-240));time.sleep(.3)
    adb('shell','input','keyevent',4);adb('shell','input','motionevent','UP',max(30,x-280),max(500,y-240));time.sleep(.5)
    assert preferences()['layout']==base,'Canceled resize persisted its preview'
    (OUT/'edge-gesture-result.json').write_text(json.dumps({'switchSelection':True,'circleResize':True,'rightEdgeClamp':True,'nearBottomAutoScroll':True,'cancelByLeavingEditMode':True},indent=2))
    print('Circular edge resize and cancellation passed',flush=True)
def offline():
    adb('shell','svc','wifi','disable');adb('shell','svc','data','disable')
    try:
        adb('shell','am','force-stop','com.pulse.app');adb('shell','am','start','-n','com.pulse.app/.MainActivity')
        for i in range(20):
            labels=[n['text'] or '' for n in nodes()]
            if any('Sync needs attention' in s for s in labels):break
            time.sleep(1)
        assert any('Garmin' in s and 'Sync needs attention' in s for s in labels)
        temp,db=database();rows=[json.loads(r[0]) for r in db.execute('SELECT payload FROM garmin_daily ORDER BY date')];db.close();temp.cleanup()
        expected=f"{rows[-1]['readings']['STEPS']['value']:,.0f}";assert expected in labels,'Offline cache was not displayed'
        capture('refinement/private-offline-cache')
        (OUT/'offline-result.json').write_text(json.dumps({'retainedRealCache':True,'visibleSyncWarning':True,'noDemoFallback':True},indent=2))
        print('Offline cache passed',flush=True)
    finally:adb('shell','svc','wifi','enable');adb('shell','svc','data','enable')
if __name__=='__main__':{'live':live,'gesture':gesture,'secrets':secrets,'edges':edge_gestures,'offline':offline}[sys.argv[1]]()
