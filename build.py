from pathlib import Path
import subprocess,zipfile,hashlib,json,shutil
ROOT=Path(__file__).resolve().parent
JAVA=Path('C:/Program Files/Java/jdk-21/bin')
LIB=Path('D:/USTBL/.minecraft/libraries')
MC=LIB/'net/minecraft/client/1.21.1-20240808.144430/client-1.21.1-20240808.144430-srg.jar'
NEO=LIB/'net/neoforged/neoforge/21.1.233/neoforge-21.1.233-universal.jar'
cached=(ROOT.parent/'.port/compile-cp.txt').read_text('utf-8-sig').strip().split(';')
cp=[str(MC),str(NEO)]+[p for p in cached if Path(p).is_file() and 'artifacts\\neoforge-' not in p and 'artifacts/neoforge-' not in p]
classes=ROOT/'build/classes'
assert classes.resolve() == (ROOT.resolve()/'build'/'classes')
if classes.exists(): shutil.rmtree(classes)
classes.mkdir(parents=True,exist_ok=True)
sources=list((ROOT/'src/main/java').rglob('*.java'))
args=['--release','21','-encoding','UTF-8','-proc:none','-classpath',';'.join(cp),'-d',str(classes)]+list(map(str,sources))
(ROOT/'build/javac.args').write_text('\n'.join('"'+s.replace('\\','/')+'"' for s in args),'utf-8')
result=subprocess.run([str(JAVA/'javac.exe'),'@'+str(ROOT/'build/javac.args')],cwd=ROOT)
if result.returncode:raise SystemExit(result.returncode)
out=ROOT/'build/libs/battlefield-ops-0.6.3-neoforge-1.21.1.jar';out.parent.mkdir(exist_ok=True)
with zipfile.ZipFile(out,'w',zipfile.ZIP_DEFLATED) as z:
    for folder in [classes,ROOT/'src/main/resources']:
        for f in folder.rglob('*'):
            if f.is_file():z.write(f,f.relative_to(folder).as_posix())
    z.writestr('META-INF/MANIFEST.MF','Manifest-Version: 1.0\nImplementation-Version: 0.6.3\n\n')
print('BUILD OK:',out)
(ROOT/'build/compile-classpath.txt').write_text(';'.join(cp),'utf-8')
