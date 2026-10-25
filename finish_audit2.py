from pathlib import Path
p=Path('src/main/java/cn/bfops')
f=p/'BattleScreen.java';s=f.read_text('utf-8').replace('leftForm.finish();leftForm.scrollTo(scrollMemory[tab]);','leftForm.scrollTo(scrollMemory[tab]);leftForm.finish();');f.write_text(s,'utf-8')
f=p/'MapPicker.java';s=f.read_text('utf-8').replace('if (level == null) return 64;','if (level == null) return Double.NaN;');f.write_text(s,'utf-8')
f=p/'TerrainPreview.java';s=f.read_text('utf-8').replace('    private int[] heights;','    private int[] heights;\n    private int[] colors;');s=s.replace('        if(resized){','        int[] oldColors=colors;int oldW=tw,oldH=th;\n        double oldX=centerX,oldZ=centerZ,oldScale=scale,oldAspect=aspect;\n        String oldDimension=dimension;\n        if(resized){',1);s=s.replace('            for(int z=0;z<th;z++)for(int x=0;x<tw;x++)texture.getPixels().setPixelRGBA(x,z,unknown(x,z));','''            colors=new int[tw*th];
            for(int z=0;z<th;z++)for(int x=0;x<tw;x++){
                int color=unknown(x,z);
                if(oldColors!=null&&target.equals(oldDimension)){
                    double wx=cx+((x+.5)/tw-.5)*span,wz=cz+((z+.5)/th-.5)*span*ratio;
                    int oldIndex=TerrainProjection.index(wx,wz,oldX,oldZ,oldScale,oldAspect,oldW,oldH);
                    if(oldIndex>=0)color=oldColors[oldIndex];
                }
                colors[z*tw+x]=color;texture.getPixels().setPixelRGBA(x,z,color);
            }''');s=s.replace('                texture.getPixels().setPixelRGBA(sx,sz,color);','                colors[cursor]=color;\n                texture.getPixels().setPixelRGBA(sx,sz,color);');s=s.replace('location=null;texture=null;','location=null;texture=null;colors=null;');f.write_text(s,'utf-8')
f=p/'OpsScreen.java';s=f.read_text('utf-8').replace('        request.add("arena", BattlefieldOps.GSON.toJsonTree(draft));','''        request.add("arena", BattlefieldOps.GSON.toJsonTree(draft));
        request.addProperty("baseId", savedDraft.isEmpty()?"":BattlefieldOps.GSON.fromJson(savedDraft,Model.Arena.class).id);
        request.addProperty("baseRevision",MapFiles.revision(savedDraft));''');f.write_text(s,'utf-8')
f=p/'Engine.java';s=f.read_text('utf-8').replace('                    arena.validate();\n                    Model.require(!matches.containsKey(arena.id)', '''                    arena.validate();
                    if(request.has("baseRevision")){
                        String baseId=str(request,"baseId","");
                        Model.Arena base=maps.get(baseId);
                        MapFiles.expect(base==null?"":BattlefieldOps.GSON.toJson(base),str(request,"baseRevision",""));
                        Model.require(arena.id.equals(baseId)||!maps.containsKey(arena.id),"地图 ID 已存在，请换一个 ID");
                    }
                    Model.require(!matches.containsKey(arena.id)''');f.write_text(s,'utf-8')
for f in [Path('build.py'),Path('gradle.properties'),Path('src/main/resources/META-INF/neoforge.mods.toml')]:
 if f.exists():f.write_text(f.read_text('utf-8').replace('0.5.2','0.5.3'),'utf-8')
s=Path('install-0.5.2.ps1').read_text('utf-8').replace('0.5.2','0.5.3').replace("'battlefield-ops-0.5.1-neoforge-1.21.1.jar'","'battlefield-ops-0.5.1-neoforge-1.21.1.jar','battlefield-ops-0.5.2-neoforge-1.21.1.jar'")
Path('install-0.5.3.ps1').write_text(s,'utf-8-sig')
