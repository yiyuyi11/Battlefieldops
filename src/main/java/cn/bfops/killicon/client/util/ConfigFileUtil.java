package cn.bfops.killicon.client.util;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public final class ConfigFileUtil {
   private ConfigFileUtil() {
   }

   public static String readText(File file) throws IOException {
      byte[] bytes = Files.readAllBytes(file.toPath());
      CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT);

      try {
         return decoder.decode(ByteBuffer.wrap(bytes)).toString();
      } catch (CharacterCodingException var4) {
         return new String(bytes, Charset.forName("GBK"));
      }
   }
}
