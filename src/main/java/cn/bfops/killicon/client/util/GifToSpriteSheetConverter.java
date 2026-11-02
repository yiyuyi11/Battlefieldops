package cn.bfops.killicon.client.util;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class GifToSpriteSheetConverter {
   private static final Logger LOGGER = LoggerFactory.getLogger("battlefield_ops-client");

   public static GifToSpriteSheetConverter.ConversionResult convertGifToSpriteSheet(Path gifPath, Path outputPngPath) {
      try {
         GifToSpriteSheetConverter.ConversionResult var15;
         try (ImageInputStream stream = ImageIO.createImageInputStream(gifPath.toFile())) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) {
               return new GifToSpriteSheetConverter.ConversionResult(false);
            }

            ImageReader reader = readers.next();
            reader.setInput(stream);
            int numFrames = reader.getNumImages(true);
            if (numFrames == 0) {
               return new GifToSpriteSheetConverter.ConversionResult(false);
            }

            int width = reader.getWidth(0);
            int height = reader.getHeight(0);
            int totalHeight = height * numFrames;
            BufferedImage spriteSheet = new BufferedImage(width, totalHeight, 2);
            Graphics2D g2d = spriteSheet.createGraphics();
            long totalDelay = 0L;

            for (int i = 0; i < numFrames; i++) {
               BufferedImage frame = reader.read(i);
               g2d.drawImage(frame, 0, i * height, null);
               totalDelay += (long)getFrameDelay(reader, i);
            }

            g2d.dispose();
            File outputFile = outputPngPath.toFile();
            ImageIO.write(spriteSheet, "png", outputFile);
            int avgInterval = numFrames > 0 ? (int)(totalDelay / (long)numFrames) : 100;
            var15 = new GifToSpriteSheetConverter.ConversionResult(outputFile, numFrames, avgInterval, width, height);
         }

         return var15;
      } catch (IOException var18) {
         LOGGER.error("GIF conversion failed", var18);
         return new GifToSpriteSheetConverter.ConversionResult(false);
      }
   }

   private static int getFrameDelay(ImageReader reader, int imageIndex) throws IOException {
      IIOMetadata metadata = reader.getImageMetadata(imageIndex);
      String formatName = metadata.getNativeMetadataFormatName();
      Node root = metadata.getAsTree(formatName);
      if ("javax_imageio_gif_image_1.0".equals(formatName)) {
         NodeList children = root.getChildNodes();

         for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if ("GraphicControlExtension".equals(node.getNodeName())) {
               NamedNodeMap attrs = node.getAttributes();
               Node delayNode = attrs.getNamedItem("delayTime");
               if (delayNode != null) {
                  int delay = Integer.parseInt(delayNode.getNodeValue());
                  if (delay <= 0) {
                     delay = 10;
                  }

                  return delay * 10;
               }
            }
         }
      }

      return 100;
   }

   public static class ConversionResult {
      public final File outputPng;
      public final int frameCount;
      public final int intervalMs;
      public final int frameWidth;
      public final int frameHeight;
      public final boolean success;

      public ConversionResult(File outputPng, int frameCount, int intervalMs, int frameWidth, int frameHeight) {
         this.outputPng = outputPng;
         this.frameCount = frameCount;
         this.intervalMs = intervalMs;
         this.frameWidth = frameWidth;
         this.frameHeight = frameHeight;
         this.success = true;
      }

      public ConversionResult(boolean success) {
         this.outputPng = null;
         this.frameCount = 0;
         this.intervalMs = 0;
         this.frameWidth = 0;
         this.frameHeight = 0;
         this.success = success;
      }
   }
}
