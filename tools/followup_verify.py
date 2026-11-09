from tools.android_ui import adb,nodes,tap,capture,preferences,ui
from pathlib import Path
import re,time,json
Path('verification/followup').mkdir(exist_ok=True)
def last(label):
 n=[n for n in ui().iter('node') if n.get('text')==label][-1]
 a,b,c,d=map(int,re.findall(r"\d+",n.get('bounds')));adb('shell','input','tap',(a+c)//2,(b+d)//2);time.sleep(.6)
def option(label):
 for _ in range(8):
  if any(n['text']==label for n in nodes()):tap(label);return
  n=next(n for n in ui().iter('node') if n.get('scrollable')=='true')
  a,b,c,d=map(int,re.findall(r"\d+",n.get('bounds')));adb('shell','input','swipe',(a+c)//2,d-40,(a+c)//2,b+40,400);time.sleep(.4)
 raise AssertionError('Missing dropdown option '+label)
adb('shell','am','start','-n','com.pulse.app/.MainActivity');time.sleep(2)
for _ in range(5):
 ns=nodes()
 if any(n['description']=='Edit dashboard' for n in ns):break
 if any(n['text']=='Done' for n in ns):tap('Done')
 else:adb('shell','input','keyevent',4);time.sleep(.5)
assert '|DEMO|' in preferences()['settings']
before=preferences()['layout']
tap('Edit dashboard');tap('Add widget');last('Steps');tap('Distance');capture('followup/distance-popup')
tap('Shape & size');tap('circle');tap('Metric');last('Distance');option('Goals');capture('followup/goals-circle-popup')
assert preferences()['layout']==before
tap('Place');time.sleep(1)
after=preferences()['layout'];assert after.splitlines()[1].split('|')[1]=='GOAL';assert after.splitlines()[1].split('|')[6]=='0'
capture('followup/top-placement');tap('Undo');time.sleep(1);assert preferences()['layout']==before
tap('Done');tap('Collection');time.sleep(2);capture('followup/monthly-class');tap('History');time.sleep(2);capture('followup/history-dots');tap('Home')
Path('verification/followup/smoke.json').write_text(json.dumps({'configure_does_not_commit':True,'place_at_top':True,'undo_restores_dashboard':True,'popup_circle_and_distance_inspected':True},indent=2))
print('Smoke flow passed')
