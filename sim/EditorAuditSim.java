package cn.bfops;
import java.nio.file.*;

public final class EditorAuditSim {
    static void check(boolean value,String reason){if(!value)throw new AssertionError(reason);}
    public static void main(String[] args)throws Exception{
        DraftHistory h=new DraftHistory();h.reset("initial");h.record("vertex");h.record("drag");
        check(h.undo().equals("vertex"),"undo drag");check(h.redo().equals("drag"),"redo drag");
        h.undo();h.record("replacement");check(h.redo().equals("replacement"),"discard abandoned redo");
        Path dir=Files.createTempDirectory("bfops-map-audit");
        try{
            MapFiles.save(dir,"arena","first");MapFiles.save(dir,"arena","second");
            check(Files.readString(dir.resolve("arena.json")).equals("second"),"saved content");
            check(Files.readString(dir.resolve("arena.json.bak")).equals("first"),"backup content");
            check(!Files.exists(dir.resolve("arena.json.tmp")),"no partial file");
            boolean rejected=false;try{MapFiles.save(dir,"../escape","bad");}catch(IllegalArgumentException ex){rejected=true;}
            check(rejected,"reject unsafe id");
            rejected=false;try{MapFiles.expect("new",MapFiles.revision("old"));}catch(IllegalArgumentException ex){rejected=true;}
            check(rejected,"reject stale save");MapFiles.expect("same",MapFiles.revision("same"));
        }finally{for(String name:new String[]{"arena.json","arena.json.bak","arena.json.tmp"})Files.deleteIfExists(dir.resolve(name));Files.delete(dir);}
        for(int z=0;z<8;z++)for(int x=0;x<8;x++)check(TerrainProjection.index(x+.5,z+.5,4,4,8,1,8,8)==z*8+x,"pixel identity");
        check(TerrainProjection.index(8,0,4,4,8,1,8,8)==-1,"outside old view");
        Form f=new Form(new Layout.Rect(0,0,100,80),null);for(int i=0;i<20;i++)f.take(20);f.finish();
        check(f.wheel(20,20,-1)&&f.scroll()>0,"scroll long form");int offset=f.scroll();
        check(!f.wheel(20,20,0)&&f.scroll()==offset,"zero delta stable");
        f.scrollTo(offset);f.finish();check(f.scroll()==offset,"restored scroll");
        for(int width:new int[]{320,427,640,960}){int size=CombatHud.mapSize(width,240);check(size>=64&&8+size<width/2,"small screen map fits");}
        System.out.println("EditorAuditSim OK: history, atomic save/backup, conflict, reprojection, scroll, HUD sizes");
    }
}
