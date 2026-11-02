package cn.bfops;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import net.minecraft.server.level.ServerPlayer;

/** One bounded compressed admin save; stays below the C2S payload limit without chunk-upload state. */
final class MapTransfer {
    private static final Map<ServerPlayer,Long> LAST=new WeakHashMap<>();
    static byte[] pack(String json){
        byte[] raw=json.getBytes(StandardCharsets.UTF_8);
        Model.require(raw.length<=240000,"地图配置超过 240KB，草稿已保留");
        try{
            var bytes=new ByteArrayOutputStream();
            try(var zip=new DeflaterOutputStream(bytes)){zip.write(raw);}
            byte[] result=bytes.toByteArray();Model.require(result.length<=30000,"地图压缩后仍超过传输上限，请减少复杂配件规则，草稿已保留");return result;
        }catch(IOException ex){throw new IllegalStateException("地图压缩失败，未提交保存",ex);}
    }
    static void receive(ServerPlayer player,byte[] packed){
        if(BattlefieldOps.engine==null||!player.hasPermissions(2))return;
        long now=System.currentTimeMillis();
        if(now-LAST.getOrDefault(player,0L)<500){Network.send(player,Map.of("type","mapSaveError","message","保存较频繁，请稍候重试；草稿仍保留"));return;}
        LAST.put(player,now);
        try{
            if(packed.length>30000)throw new IllegalArgumentException("地图传输数据过大");
            byte[] raw;
            try(var stream=new InflaterInputStream(new ByteArrayInputStream(packed))){raw=stream.readNBytes(240001);}
            Model.require(raw.length<=240000,"地图解压后过大");
            String json=new String(raw,StandardCharsets.UTF_8);
            var request=com.google.gson.JsonParser.parseString(json).getAsJsonObject();
            Model.require(Engine.str(request,"op","").equals("save"),"此通道只接受地图保存");
            BattlefieldOps.engine.request(player,json);
        }catch(Exception ex){Network.send(player,Map.of("type","mapSaveError","message","地图保存失败："+(ex.getMessage()==null?"数据无效":ex.getMessage())));}
    }
    private MapTransfer(){}
}
