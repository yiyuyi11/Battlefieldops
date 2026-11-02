package cn.bfops.killicon.client.render.effect;

import java.util.Random;

public class TextScrambleEffect {
   private static final String SCRAMBLE_LATIN = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
   private static final String SCRAMBLE_NUMERIC = "0123456789";
   private static final String SCRAMBLE_CJK = "的一是在不了有和人这中大为上个国我以要他时来用们生到作地于出就分对成会可主发年样能下部动方出进面家种已心";
   private static final Random RANDOM = new Random();
   private final String target;
   private final boolean[] shouldScramble;
   private final long startTime;
   private final long duration;
   private final long refreshRate;
   private final boolean active;
   private final char[] cachedChars;
   private long lastRefreshTime = 0L;

   public TextScrambleEffect(String text, long duration, long refreshRate, boolean active) {
      this.target = text;
      this.duration = duration;
      this.refreshRate = refreshRate;
      this.active = active;
      this.startTime = System.currentTimeMillis();
      this.shouldScramble = new boolean[text.length()];
      this.cachedChars = new char[text.length()];
      if (active) {
         for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            this.shouldScramble[i] = this.isScrambleable(c);
            if (this.shouldScramble[i]) {
               this.cachedChars[i] = this.getRandomCharLike(c);
            }
         }
      }
   }

   private boolean isScrambleable(char c) {
      if (Character.isWhitespace(c)) {
         return false;
      } else {
         return Character.isDigit(c) ? false : c != '+' && c != '-' && c != '.' && c != ',' && c != '(' && c != ')' && c != ':' && c != '|';
      }
   }

   public String getCurrentText() {
      if (!this.active) {
         return this.target;
      } else {
         long now = System.currentTimeMillis();
         long elapsed = now - this.startTime;
         float progress = Math.min(1.0F, (float)elapsed / (float)this.duration);
         if (progress >= 1.0F) {
            return this.target;
         } else {
            boolean shouldUpdateCache = now - this.lastRefreshTime >= this.refreshRate;
            if (shouldUpdateCache) {
               this.lastRefreshTime = now;
            }

            StringBuilder sb = new StringBuilder();

            for (int i = 0; i < this.target.length(); i++) {
               if (!this.shouldScramble[i]) {
                  sb.append(this.target.charAt(i));
               } else {
                  float charRevealStart = (float)i / (float)this.target.length() * 0.4F;
                  float charRevealDuration = 0.6F;
                  float charProgress = Math.max(0.0F, Math.min(1.0F, (progress - charRevealStart) / charRevealDuration));
                  if (charProgress >= 1.0F) {
                     sb.append(this.target.charAt(i));
                  } else {
                     if (shouldUpdateCache) {
                        this.cachedChars[i] = this.getRandomCharLike(this.target.charAt(i));
                     }

                     sb.append(this.cachedChars[i]);
                  }
               }
            }

            return sb.toString();
         }
      }
   }

   private char getRandomCharLike(char original) {
      if (original >= 19968 && original <= '鿿') {
         return "的一是在不了有和人这中大为上个国我以要他时来用们生到作地于出就分对成会可主发年样能下部动方出进面家种已心".charAt(RANDOM.nextInt("的一是在不了有和人这中大为上个国我以要他时来用们生到作地于出就分对成会可主发年样能下部动方出进面家种已心".length()));
      } else if ((original < 'a' || original > 'z') && (original < 'A' || original > 'Z')) {
         return original >= '0' && original <= '9'
            ? "0123456789".charAt(RANDOM.nextInt("0123456789".length()))
            : "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ".charAt(RANDOM.nextInt("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ".length()));
      } else {
         return "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ".charAt(RANDOM.nextInt("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ".length()));
      }
   }

   public boolean isFinished() {
      return !this.active || System.currentTimeMillis() - this.startTime >= this.duration;
   }
}
