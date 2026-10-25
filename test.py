from pathlib import Path
import subprocess,shutil
root=Path(__file__).resolve().parent
java=Path('C:/Program Files/Java/jdk-21/bin')
cp=str(root/'build/classes')+';D:/USTBL/.minecraft/libraries/net/minecraft/client/1.21.1-20240808.144430/client-1.21.1-20240808.144430-extra.jar;'+(root/'build/compile-classpath.txt').read_text('utf-8')

# Log4j's terminal appender probes for a real console by shelling out (JLine ExecPty). Under a headless
# runner that spawn is blocked, the probe never returns, and any test whose first action touches the
# logger hangs with no output at all. Disabling the JLine terminal is enough: logging still works, it just
# uses the plain console appender. This is a harness concern only -- the shipped mod never runs this way.
JVM_FLAGS=['-Dterminal.jline=false','-Dlog4j2.disable.jansi=true']

dest=root/'build/sim'
assert dest.resolve() == root/'build'/'sim'
if dest.exists(): shutil.rmtree(dest)
dest.mkdir(parents=True,exist_ok=True)
sources=list((root/'sim').glob('*.java'))
subprocess.run([str(java/'javac.exe'),'--release','21','-encoding','UTF-8','-proc:none','-cp',cp,'-d',str(dest),*map(str,sources)],check=True)
for source in sources:
    package='cn.bfops.' if 'package cn.bfops;' in source.read_text('utf-8') else ''
    result=subprocess.run([str(java/'java.exe'),*JVM_FLAGS,'-cp',cp+';'+str(dest),package+source.stem])
    if result.returncode: raise SystemExit(result.returncode)
