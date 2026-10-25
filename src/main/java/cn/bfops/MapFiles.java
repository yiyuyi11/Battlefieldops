package cn.bfops;
import java.nio.file.*;
import java.io.IOException;

/** Write-then-replace; retain the previous complete version for recovery. */
final class MapFiles {
    static String revision(String json){
        try{return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(json.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException ex){throw new IllegalStateException(ex);}
    }
    static void expect(String current,String revision){
        if(!revision(current).equals(revision))throw new IllegalArgumentException("地图已被其他操作修改，请先保留草稿并重新载入，未覆盖服务器版本");
    }
    static void save(Path directory,String id,String json) throws IOException {
        if(!id.matches("[a-z0-9_-]{1,32}"))throw new IllegalArgumentException("地图 ID 无效");
        Files.createDirectories(directory);
        Path dest=directory.resolve(id+".json"),tmp=directory.resolve(id+".json.tmp");
        Files.writeString(tmp,json);
        if(Files.exists(dest))Files.copy(dest,directory.resolve(id+".json.bak"),StandardCopyOption.REPLACE_EXISTING);
        try{Files.move(tmp,dest,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
        catch(AtomicMoveNotSupportedException ex){Files.move(tmp,dest,StandardCopyOption.REPLACE_EXISTING);}
    }
}
