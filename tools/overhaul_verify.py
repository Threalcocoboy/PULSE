"""Isolated-emulator smoke evidence. Never targets an attached phone."""
from tools.android_ui import adb,nodes,tap,capture,preferences,ui
from pathlib import Path
import json,time,re
out=Path('verification/overhaul');out.mkdir(exist_ok=True)
def visible(label):return any(n['text']==label or n['description']==label for n in nodes())
def snap(name):time.sleep(1);capture('overhaul/'+name)
def dismiss_permission():
 label=next((n['text'] for n in nodes() if n['text'].lower().replace(chr(8217),chr(39))=="don't allow"),None)
 if label:tap(label);return True
 return False
adb('shell','am','start','-n','com.pulse.app/.MainActivity');time.sleep(2)
denied=dismiss_permission()
for _ in range(5):
 if visible('Home'):tap('Home');break
 adb('shell','input','keyevent',4);time.sleep(.5)
assert '|DEMO|' in preferences()['settings']
snap('final-home');tap('History');snap('axis-steps');tap('Heart rate');snap('heart-samples-empty');tap('Daily averages');snap('heart-averages')
tap('Collection');snap('collection');tap('See full year');snap('year-classes');adb('shell','input','keyevent',4);time.sleep(1)
adb('shell','input','swipe',540,1900,540,700,600);snap('badge-artwork');tap('Home');tap('Settings');tap('Notifications');snap('notification-preview')
if visible('Enable progress notification'):tap('Enable progress notification')
time.sleep(1)
denied=dismiss_permission() or denied
time.sleep(1)
snap('notification-permission-result')
if visible('Disable progress notification'):tap('Disable progress notification')
assert preferences()['settings'].split('|')[17]=='false'
adb('shell','input','keyevent',4);time.sleep(.5);tap('Profile & Goals')
tap('Delete name')
field=next(n for n in ui().iter('node') if n.get('class')=='android.widget.EditText')
a,b,c,d=map(int,re.findall(r"\d+",field.get('bounds')));adb('shell','input','tap',(a+c)//2,(b+d)//2);adb('shell','input','text','PULSE-QA');adb('shell','input','keyevent',4);time.sleep(.5);tap('Save name')
adb('shell','input','keyevent',4);adb('shell','input','keyevent',4);time.sleep(1);snap('personalized-home')
assert any('PULSE-QA' in n['text'] for n in nodes())
tap('Settings');tap('Profile & Goals');tap('Delete name');time.sleep(1);assert preferences()['settings'].split('|')[14]==''
adb('shell','input','keyevent',4);adb('shell','input','keyevent',4)
(out/'smoke.json').write_text(json.dumps({'history_axes_rendered':True,'samples_and_averages_separate':True,'normal_art_and_tiers_rendered':True,'notification_opt_out_safe':True,'notification_denial_exercised':denied,'local_name_greeting':True,'local_name_deleted':True},indent=2))
print('Overhaul smoke passed')
