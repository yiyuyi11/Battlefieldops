from pathlib import Path
p=Path('src/main/java/cn/bfops/Network.java');s=p.read_text('utf-8').replace('hasChannel(p.connection,','hasChannel((net.neoforged.neoforge.common.extensions.ICommonPacketListener)(Object)p.connection,');p.write_text(s,'utf-8')
