import pathlib,subprocess,sys,json,os,base64
from build_env import JDK, ANDROID, javatool
sys.stdout.reconfigure(encoding='utf-8')
ROOT=pathlib.Path(__file__).resolve().parent
OUT=ROOT/'build/image-tests'; OUT.mkdir(parents=True,exist_ok=True)
ledger=OUT/'image-test-events.json'
events=json.loads(ledger.read_text(encoding='utf-8')) if ledger.exists() else []
def run(args):
    command=list(map(str,args)); p=subprocess.run(command,capture_output=True)
    e=dict(command=command,stdin='',stdout=p.stdout.decode('utf-8','replace'),stderr=p.stderr.decode('utf-8','replace'),exit_status=p.returncode,stdout_base64=base64.b64encode(p.stdout).decode(),stderr_base64=base64.b64encode(p.stderr).decode())
    events.append(e); ledger.write_text(json.dumps(events,ensure_ascii=False,indent=2),encoding='utf-8'); print(json.dumps(e,ensure_ascii=False)); assert p.returncode==0
src=ROOT/'src/dev/inknfclab'
names=['Protocol','ImageCodec','ImageQuantizer','ExperimentalQuantizer','AlgorithmCatalog','ResearchQuantizer','ImageTransfer','NciDiscovery','BlockQuantizer','InkSightQuantizer','InkSightFitter','PhotoEdit','PhotoDecorations','EditHistory','AppUi','UpdateVersion','GitHubUpdates','QPollCompatibility','NfcControllerProfile','StPollingTransaction','NfcCapabilities']
tests=['ProtocolTest','ImageCodecTest','ImageTransferTest','FullTransferTest','NciDiscoveryTest','InkSightQuantizerTest','PhotoEditTest','EditSceneTest','UpdateVersionTest','QPollCompatibilityTest','StPollingTransactionTest','NfcCapabilitiesTest']
run([javatool('javac'),'-J-Dfile.encoding=UTF-8','-J-Duser.language=en','--release','8','-encoding','UTF-8','-cp',ANDROID,'-d',OUT,*[src/(n+'.java') for n in names],*[ROOT/'tests'/(n+'.java') for n in tests]])
for name in tests: run([javatool('java'),'-Dfile.encoding=UTF-8','-cp',str(OUT)+os.pathsep+str(ANDROID),'dev.inknfclab.'+name,*([ROOT/'tests/inksight-fixtures'] if name=='InkSightQuantizerTest' else [])])
