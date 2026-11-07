"""Small ADB smoke-test driver. Runs against the isolated emulator, never a physical phone."""
import json, re, subprocess, sys, time, xml.etree.ElementTree as ET, sqlite3, tempfile
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
ADB=Path.home()/"AppData/Local/Android/Sdk/platform-tools/adb.exe"
SERIAL="emulator-5554"
def adb(*args, binary=False):
    p=subprocess.run([str(ADB),"-s",SERIAL,*map(str,args)],capture_output=True,check=True)
    return p.stdout if binary else p.stdout.decode("utf-8",errors="replace")
def ui():
    adb("shell","uiautomator","dump","/sdcard/pulse-ui.xml")
    return ET.fromstring(adb("shell","cat","/sdcard/pulse-ui.xml"))
def nodes():
    return [{"text":n.get("text"),"description":n.get("content-desc"),"bounds":n.get("bounds"),"selected":n.get("selected")} for n in ui().iter("node") if n.get("text") or n.get("content-desc")]
def tap(label):
    n=None
    for attempt in range(5):
        n=next((n for n in ui().iter("node") if n.get("text")==label or n.get("content-desc")==label),None)
        if n is not None:break
        adb("shell","input","swipe",540,1950,540,1050,300);time.sleep(.4)
    if n is None and label=="Back":
        adb("shell","input","keyevent",4);time.sleep(.4);print("Used Android Back",flush=True);return
    if n is None: raise AssertionError(f"Missing control: {label}. Visible: {nodes()}")
    x1,y1,x2,y2=map(int,re.findall(r"\d+",n.get("bounds")))
    adb("shell","input","tap",(x1+x2)//2,(y1+y2)//2); time.sleep(.45)
    print(f"Clicked {label}",flush=True)
def bounds(label):
    n=next((n for n in ui().iter("node") if n.get("text")==label or n.get("content-desc")==label),None)
    assert n is not None,f"Missing {label}"
    return tuple(map(int,re.findall(r"\d+",n.get("bounds"))))
def preferences():
    raw=adb("exec-out","run-as","com.pulse.app","cat","files/datastore/pulse_preferences.preferences_pb",binary=True)
    def varint(blob,pos):
        v=0;shift=0
        while pos<len(blob):
            b=blob[pos];pos+=1;v|=(b&127)<<shift
            if b<128:return v,pos
            shift+=7
        raise ValueError("invalid varint")
    def walk(blob):
        pos=0;found=[]
        try:
            while pos<len(blob):
                tag,pos=varint(blob,pos);wire=tag&7
                if wire==0:_,pos=varint(blob,pos)
                elif wire==1:pos+=8
                elif wire==5:pos+=4
                elif wire==2:
                    n,pos=varint(blob,pos);value=blob[pos:pos+n];pos+=n
                    if value.startswith((b"v1\n",b"v1|")):found.append(value.decode())
                    else:found+=walk(value)
                else:break
        except (ValueError,UnicodeDecodeError):pass
        return found
    return {"layout" if s.startswith("v1\n") else "settings":s for s in walk(raw)}
def valid_layout(encoded):
    cells=set();ids=[]
    for row in encoded.splitlines()[1:]:
        if not row:continue
        a=row.split("|");ids.append(a[0]);w,h,x,y=map(int,a[3:7]);assert 0<=x and x+w<=4 and y>=0
        if a[2]=="CIRCLE":assert w==h
        for r in range(y,y+h):
            for c in range(x,x+w):assert (c,r) not in cells;cells.add((c,r))
    assert len(ids)==len(set(ids));return ids
def capture(name):
    (ROOT/"verification"/f"{name}.png").write_bytes(adb("exec-out","screencap","-p",binary=True))
    (ROOT/"verification"/f"{name}.xml").write_text(adb("shell","cat","/sdcard/pulse-ui.xml"),encoding="utf-8")
    print(f"Captured {name}",flush=True)
def smoke():
    visible=nodes()
    if any(n["text"]=="Get Started" for n in visible):
        tap("Get Started");capture("02-source");tap("Use clearly labeled demo data");tap("Continue");tap("Save goals");tap("Continue");capture("03-studio-intro");tap("Continue");tap("Open PULSE")
    assert any(n["text"]=="Home" for n in nodes())
    capture("04-home")
    tap("History");capture("05-history");tap("30D");tap("Sleep");capture("06-sleep-history")
    tap("Achievements");capture("07-achievements");tap("Jan");capture("08-medal");tap("Back")
    tap("Home");tap("Settings");tap("Appearance");tap("Light");capture("09-light-theme");tap("Dark");tap("Back");tap("Back")
    tap("Edit dashboard");capture("10-studio");tap("+ Add");tap("Distance");capture("11-widget-added");tap("Undo");tap("Redo");tap("Done")
    adb("shell","am","force-stop","com.pulse.app");adb("shell","am","start","-n","com.pulse.app/.MainActivity");time.sleep(1)
    assert any(n["text"]=="Home" for n in nodes());capture("12-restarted")
    print("SMOKE TEST PASSED",flush=True)
def studio():
    current=nodes()
    if any(n["description"]=="Close sheet" for n in current):adb("shell","input","keyevent",4);time.sleep(.5)
    if not any(n["text"]=="PULSE Studio" for n in nodes()):tap("Edit dashboard")
    tap("Reset");tap("Done")
    original=preferences()["layout"];ids=valid_layout(original)
    x1,y1,x2,y2=bounds("Steps widget");x=(x1+x2)//2;y=(y1+y2)//2
    adb("shell","input","swipe",x,y,x,y,700);time.sleep(.5)
    assert any(n["text"]=="PULSE Studio" for n in nodes());capture("13-long-press")
    x1,y1,x2,y2=bounds("Steps widget");x=(x1+x2)//2;y=(y1+y2)//2
    adb("shell","input","swipe",x,y,x,y+450,900);time.sleep(.8)
    moved=preferences()["layout"];assert moved!=original,"Drag did not commit";assert set(valid_layout(moved))==set(ids);capture("14-drag-reflow")
    tap("Undo");assert preferences()["layout"]==original
    tap("Redo");assert preferences()["layout"]==moved
    # Select any widget and use the real size control. Tests the same resize command as the handle.
    tap("Undo");tap("Steps widget");tap("4×2");capture("15-resized")
    resized=preferences()["layout"];assert resized!=original;valid_layout(resized)
    tap("Done");tap("Configure");tap("Remove widget");removed=preferences()["layout"];assert len(valid_layout(removed))==len(ids)-1
    tap("Undo");assert preferences()["layout"]==resized
    tap("Redo");assert preferences()["layout"]==removed
    tap("Undo");tap("Done");adb("shell","am","force-stop","com.pulse.app");adb("shell","am","start","-n","com.pulse.app/.MainActivity");time.sleep(.7)
    assert preferences()["layout"]==resized;capture("16-layout-persisted")
    (ROOT/"verification"/"studio-result.json").write_text(json.dumps({"drag":True,"reflow":True,"resize":True,"remove":True,"undo":True,"redo":True,"restart":True,"widgets":len(ids)},indent=2))
    print("STUDIO TEST PASSED",flush=True)
def features():
    # Starts on Profile & Goals with default input values.
    for old,new in [("5530","8000"),("8.0","7.5")]:
        tap(old);adb("shell","input","keyevent",123)
        for _ in range(8):adb("shell","input","keyevent",67)
        adb("shell","input","text",new);adb("shell","input","keyevent",4)
    tap("Save goals");assert "|8000|7.5|" in preferences()["settings"];capture("17-goals")
    tap("Back");tap("Devices & Data");tap("Health Connect");time.sleep(.7);capture("18-real-source-status")
    assert "HEALTH_CONNECT" in preferences()["settings"]
    tap("Review / request permissions");capture("19-permission-flow")
    print(json.dumps(nodes(),ensure_ascii=True),flush=True)
def export_data():
    tap("Demo Mode");tap("Back");tap("Privacy & Data");tap("Export local data");time.sleep(.7)
    raw=adb("exec-out","run-as","com.pulse.app","cat","cache/exports/pulse-data.json",binary=True)
    data=json.loads(raw);assert data["mode"]=="DEMO";assert "layout" in data;assert "cachedRealRecords" in data
    capture("20-export-share-sheet");adb("shell","input","keyevent",4);time.sleep(.3)
    tap("Delete local PULSE data");capture("21-delete-confirmation");tap("Cancel")
    tap("Back");tap("Back");tap("Achievements");tap("Jan");tap("Share medal");capture("22-medal-share-sheet")
    raw=adb("exec-out","run-as","com.pulse.app","cat","cache/exports/pulse-medal.png",binary=True);assert raw.startswith(b"\x89PNG\r\n\x1a\n")
    (ROOT/"verification"/"shared-medal.png").write_bytes(raw)
    adb("shell","input","keyevent",4);adb("shell","input","keyevent",4);history_detail()
def history_detail():
    tap("History")
    chart=next(n for n in ui().iter("node") if "recorded days of" in n.get("content-desc",""))
    x1,y1,x2,y2=map(int,re.findall(r"\d+",chart.get("bounds")));adb("shell","input","tap",(x1+x2)//2,(y1+y2)//2)
    tap("Open day");capture("23-day-detail");tap("Back");tap("Home")
    adb("shell","am","force-stop","com.pulse.app");adb("shell","am","start","-n","com.pulse.app/.MainActivity");time.sleep(.8)
    assert "|8000|7.5|" in preferences()["settings"]
    print("GOALS, SOURCE SEPARATION, EXPORT, SHARING AND DAY DETAIL PASSED",flush=True)
def resize_handle():
    tap("Edit dashboard");tap("Reset");base=preferences()["layout"]
    tap("Heart rate widget");x1,y1,x2,y2=bounds("Resize Heart rate");x=(x1+x2)//2;y=(y1+y2)//2
    adb("shell","input","swipe",x,y,min(x+450,1040),y,900);time.sleep(.6)
    changed=preferences()["layout"];assert changed!=base,"Resize handle did not change the footprint";assert set(valid_layout(changed))==set(valid_layout(base))
    capture("31-corner-resize");tap("Undo");assert preferences()["layout"]==base;tap("Done")
    print("CORNER RESIZE PASSED",flush=True)
def deletion():
    tap("Home");tap("Settings");tap("Privacy & Data");tap("Delete local PULSE data");tap("Delete local data");time.sleep(.8)
    assert any(n["text"]=="Get Started" for n in nodes()),"Onboarding was not reset"
    assert preferences()=={},"Preferences were not cleared"
    exported=subprocess.run([str(ADB),"-s",SERIAL,"shell","run-as","com.pulse.app","ls","cache/exports"],capture_output=True)
    assert exported.returncode!=0,"Export cache was not removed"
    adb("shell","am","force-stop","com.pulse.app")
    with tempfile.TemporaryDirectory(prefix="pulse-db-check-") as temp:
        dbpath=Path(temp)/"pulse_health.db"
        for suffix in ["","-wal"]:
            p=subprocess.run([str(ADB),"-s",SERIAL,"exec-out","run-as","com.pulse.app","cat",f"databases/pulse_health.db{suffix}"],capture_output=True)
            if p.returncode==0:Path(str(dbpath)+suffix).write_bytes(p.stdout)
        db=sqlite3.connect(dbpath);counts={table:db.execute(f"SELECT COUNT(*) FROM {table}").fetchone()[0] for table in ["daily","medals","garmin_daily"]};db.close()
        assert all(v==0 for v in counts.values()),counts
    adb("shell","am","start","-n","com.pulse.app/.MainActivity");time.sleep(.5);capture("35-deletion-reset")
    (ROOT/"verification"/"deletion-result.json").write_text(json.dumps({"onboardingReset":True,"preferencesCleared":True,"exportCacheRemoved":True,"databaseRows":counts},indent=2))
    print("CONFIRMED LOCAL DELETION PASSED",flush=True)
if __name__=="__main__":
    action=sys.argv[1]
    if action=="nodes": print(json.dumps(nodes(),indent=2))
    elif action=="tap": tap(sys.argv[2]);print(json.dumps(nodes(),indent=2))
    elif action=="capture": ui();capture(sys.argv[2])
    elif action=="smoke": smoke()
    elif action=="studio":studio()
    elif action=="preferences":print(json.dumps(preferences(),indent=2))
    elif action=="features":features()
    elif action=="export":export_data()
    elif action=="history-detail":history_detail()
    elif action=="resize-handle":resize_handle()
    elif action=="delete":deletion()
