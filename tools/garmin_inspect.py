"""Read Garmin's published Swagger document; optional redacted, read-only key probe.

Credentials are read in-process from garmin.txt and never printed, cached or passed as arguments.
Only official HTTPS origins are used; authenticated redirects are rejected.
"""
import ast, base64, json, re, sys, urllib.request, urllib.error
from pathlib import Path
from datetime import date, timedelta

ROOT=Path(__file__).resolve().parents[1]
DOC=ROOT/'tools/garmin-docs/personal-openapi.json'
class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self,*args,**kwargs): return None

def extract():
    text=(DOC.parent/'swagger-page.js').read_text(encoding='utf-8')
    for match in re.finditer(r'JSON\.parse\(\s*([\'\"])',text):
        start=match.end()-1; quote=text[start]; pos=start+1
        while pos<len(text):
            if text[pos]=='\\':pos+=2;continue
            if text[pos]==quote:break
            pos+=1
        try: spec=json.loads(ast.literal_eval(text[start:pos+1]))
        except (ValueError,SyntaxError,json.JSONDecodeError):continue
        if isinstance(spec,dict) and 'openapi' in spec:
            DOC.write_text(json.dumps(spec,indent=2),encoding='utf-8');break
    else: raise SystemExit('Official Swagger document could not be extracted')
    for path,methods in spec['paths'].items():
        for verb,op in methods.items():
            if verb in ['get','post']:
                print(verb.upper(),path,op.get('summary',''))
                print('  params:',[(p['name'],p.get('description','')[:180]) for p in op.get('parameters',[])])
                print('  response:',json.dumps(op.get('responses',{}).get('200',{}).get('content',{}))[:500])

def schemas():
    spec=json.loads(DOC.read_text(encoding='utf-8'))
    for name,schema in spec['components']['schemas'].items():
        if any(k in name.lower() for k in ['summary','heart','sleep','bodybattery','hrv','activity','token']):
            print(name, json.dumps(schema)[:14000])

def probe():
    key=(ROOT/'garmin.txt').read_text(encoding='utf-8-sig').strip()
    if not key or '\n' in key or '\r' in key: raise SystemExit('Key file format rejected (not displayed)')
    opener=urllib.request.build_opener(NoRedirect())
    def request(path,token=None):
        headers={'Authorization':('ApiKey '+key if token is None else 'Bearer '+token),'Accept':'*/*' if token is None else 'application/json'}
        req=urllib.request.Request('https://personal.connectapi.garmin.com'+path,headers=headers,method='POST' if token is None else 'GET',data=b'' if token is None else None)
        try:
            with opener.open(req,timeout=25) as r:return r.status,r.read(8_000_000)
        except urllib.error.HTTPError as e:return e.code,b''
        except Exception:return 0,b''
    status,body=request('/personalapitoken-service/personal-api/personal-api-key/token')
    print(json.dumps({'tokenHttpStatus':status,'tokenBodyLogged':False}))
    if status!=200:return
    token=body.decode().strip().strip('"')
    try: claims=json.loads(base64.urlsafe_b64decode(token.split('.')[1]+'==='));print(json.dumps({'tokenIsJwt':True,'hasExpiry':'exp' in claims}))
    except Exception:print(json.dumps({'tokenIsJwt':False}));return
    today=date.today();start=today-timedelta(days=6)
    endpoints={
        'summary':f'/usersummary-service/v2/dailysummaries?startDate={start}&endDate={today}',
        'sleep':f'/sleep-service/sleep/dailySleep?date={today}',
        'heart':f'/wellness-service/wellness/dailyHeartRate?date={today}',
        'hrv':f'/hrv-service/hrv/{today}',
        'bodyBattery':f'/wellness-service/wellness/bodyBattery/reports/daily?startDate={start}&endDate={today}',
        'activities':f'/activitylist-service/activities/search/activities?start=0&limit=5&startDate={start}&endDate={today}'
    }
    results={}
    for name,path in endpoints.items():
        status,raw=request(path,token)
        try: parsed=json.loads(raw)
        except Exception:parsed=None
        def shape(value,depth=0):
            if depth>2:return type(value).__name__
            if isinstance(value,dict):return {k:shape(v,depth+1) for k,v in value.items()}
            if isinstance(value,list):return {'items':len(value),'shape':shape(value[0],depth+1) if value else None}
            return type(value).__name__
        results[name]={'status':status,'shape':shape(parsed)}
        # Raw personal records remain only in ignored local probe files for mapping validation.
        if parsed is not None:(DOC.parent/f'private-{name}.json').write_text(json.dumps(parsed),encoding='utf-8')
    (ROOT/'verification/refinement').mkdir(exist_ok=True)
    (ROOT/'verification/refinement/garmin-probe.json').write_text(json.dumps(results,indent=2),encoding='utf-8')
    print(json.dumps(results,indent=2))
if __name__=='__main__': {'extract':extract,'schemas':schemas,'probe':probe}[sys.argv[1]]()
