"""Build the standalone diagnostic APK with Android SDK 35 and JDK 17."""
import os, pathlib, subprocess, json, zipfile, hashlib, sys, base64
import xml.etree.ElementTree as ET
from build_env import SDK, JDK, BT, ANDROID, sdktool, javatool
sys.stdout.reconfigure(encoding='utf-8'); sys.stderr.reconfigure(encoding='utf-8')
ROOT=pathlib.Path(__file__).resolve().parent
OUT=ROOT/'build'; OUT.mkdir(exist_ok=True)
ledger=OUT/'build-events.json'
events=json.loads(ledger.read_text(encoding='utf-8')) if ledger.exists() else []
def run(args):
    args=[str(a) for a in args]
    p=subprocess.run(args,cwd=ROOT,capture_output=True,env={**os.environ,'JAVA_HOME':str(JDK),'PATH':str(JDK/'bin')+os.pathsep+os.environ['PATH']})
    stdout=p.stdout.decode('utf-8',errors='replace'); stderr=p.stderr.decode('utf-8',errors='replace')
    e=dict(command=args,stdin='',stdout=stdout,stderr=stderr,stdout_base64=base64.b64encode(p.stdout).decode(),stderr_base64=base64.b64encode(p.stderr).decode(),exit_status=p.returncode); events.append(e)
    (OUT/'build-events.json').write_text(json.dumps(events,ensure_ascii=False,indent=2),encoding='utf-8')
    print(pathlib.Path(args[0]).name, 'exit='+str(p.returncode),flush=True)
    if stdout: print(stdout)
    if stderr: print(stderr,file=sys.stderr)
    if p.returncode: sys.exit(p.returncode)
    return p
for name in ['classes','dex','generated','assets']:
    (OUT/name).mkdir(exist_ok=True)
for f in (OUT/'classes').rglob('*.class'): f.unlink()
for f in (OUT/'dex').glob('*.dex'): f.unlink()
for f in ['LICENSE','NOTICE','THIRD-PARTY-LICENSES.txt']:
    (OUT/'assets'/f).write_bytes((ROOT/f).read_bytes())
for item in json.loads((ROOT/'dependency-lock.json').read_text(encoding='utf-8')):
    dependency=ROOT/item['path']
    if hashlib.sha256(dependency.read_bytes()).hexdigest()!=item['sha256']:
        raise SystemExit('Dependency checksum mismatch: '+item['path'])
deps=sorted((ROOT/'deps').glob('*.jar'))
run([sdktool('aapt2'),'compile','--dir',ROOT/'res','-o',OUT/'resources.zip'])
run([sdktool('aapt2'),'link','-I',ANDROID,'--manifest',ROOT/'AndroidManifest.xml','-A',OUT/'assets','--java',OUT/'generated','-o',OUT/'resources.apk',OUT/'resources.zip'])
legacy={'AlgorithmCatalog.java','ImageQuantizer.java','ExperimentalQuantizer.java','ResearchQuantizer.java','BlockQuantizer.java'}
sources=sorted(p for p in (ROOT/'src').rglob('*.java') if p.name not in legacy)+sorted((OUT/'generated').rglob('*.java'))
run([javatool('javac'),'-J-Dfile.encoding=UTF-8','-J-Duser.language=en','-J-Duser.country=US','--release','8','-encoding','UTF-8','-cp',os.pathsep.join(map(str,[ANDROID]+deps)),'-d',OUT/'classes',*sources])
run([javatool('jar'),'cf',OUT/'classes.jar','-C',OUT/'classes','.'])
run([sdktool('d8'),'--min-api','26','--lib',ANDROID,'--output',OUT/'dex',OUT/'classes.jar',*deps])
(OUT/'unsigned.apk').write_bytes((OUT/'resources.apk').read_bytes())
with zipfile.ZipFile(OUT/'unsigned.apk','a',zipfile.ZIP_DEFLATED) as z:
    for f in (OUT/'dex').glob('*.dex'): z.write(f,f.name)
run([sdktool('zipalign'),'-f','4',OUT/'unsigned.apk',OUT/'aligned.apk'])
KEYSTORE=pathlib.Path(os.environ.get('MIAOMO_KEYSTORE',str(OUT/'debug.keystore')))
if not KEYSTORE.exists():
    if os.environ.get('MIAOMO_KEYSTORE'): raise SystemExit('Configured MIAOMO_KEYSTORE does not exist')
    run([javatool('keytool'),'-genkeypair','-keystore',KEYSTORE,'-storepass','android','-keypass','android','-alias','androiddebugkey','-keyalg','RSA','-keysize','2048','-validity','3650','-dname','CN=Ink NFC Diagnostic Test'])
version=ET.parse(ROOT/'AndroidManifest.xml').getroot().attrib['{http://schemas.android.com/apk/res/android}versionName'].split('-')[0]
APK=OUT/('miaomo-'+version+'.apk')
run([sdktool('apksigner'),'sign','--ks',KEYSTORE,'--ks-pass','pass:android','--out',APK,OUT/'aligned.apk'])
run([sdktool('apksigner'),'verify','--verbose','--print-certs',APK])
run([sdktool('aapt2'),'dump','badging',APK])
print('APK='+str(APK)); print('SHA256='+hashlib.sha256(APK.read_bytes()).hexdigest())
