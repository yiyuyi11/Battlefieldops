from pathlib import Path
p=Path('src/main/java/cn/bfops')
f=p/'Minimap.java';s=f.read_text('utf-8');s=s.replace('        int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));\n        if (steps > 4000) return;','        int[] clipped=MapGeometry.clip(x0,y0,x1,y1,x,y,x+w-1,y+h-1);\n        if(clipped==null)return;\n        x0=clipped[0];y0=clipped[1];x1=clipped[2];y1=clipped[3];\n        color|=0xff000000;\n        int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));');s=s.replace('py + 4, color(owner));','py + 4, color(owner)|0xff000000);').replace('py + 8, BLUE);','py + 8, BLUE|0xff000000);').replace('py + 8, RED);','py + 8, RED|0xff000000);');s=s.replace('        int px = sx(worldX), py = sy(worldZ);','        color|=0xff000000;\n        int px = sx(worldX), py = sy(worldZ);');f.write_text(s,'utf-8')
f=p/'OpsScreen.java';s=f.read_text('utf-8').replace('        if (pendingLoad) {\n            load();','        if (pendingLoad && (selected.isEmpty() || selected.equals(Engine.str(Client.state,"selected","")))) {\n            load();');s=s.replace('row(form, "删除地图", "需先结束对局", () -> {\n                    pendingLoad = true;\n                    action("delete");\n                });','''row(form, "删除地图", "保留删除备份", () -> discardThen(()->{
                    minecraft.setScreen(new net.minecraft.client.gui.screens.ConfirmScreen(yes->{
                        minecraft.setScreen(this);
                        if(yes){action("delete");selected="";pendingLoad=true;}
                    },net.minecraft.network.chat.Component.literal("删除这张地图配置？"),
                      net.minecraft.network.chat.Component.literal("此操作不会删除世界地形，服务器会保留配置删除备份。")));
                }));''');f.write_text(s,'utf-8')
