package cn.bfops.killicon.client.render.effect;

import net.minecraft.util.Mth;

public class DigitalScrollEffect {
   private float animationDuration = 1.25F;
   private float animationRefreshRate = 0.01F;
   private DigitalScrollEffect.Easing easing = DigitalScrollEffect.Easing.QUINTIC_OUT;
   private float startValue = 0.0F;
   private float targetValue = 0.0F;
   private float currentValue = 0.0F;
   private long startTime = -1L;
   private long lastUpdateTime = -1L;
   private boolean isAnimating = false;

   public DigitalScrollEffect() {
   }

   public DigitalScrollEffect(float animationDuration, float animationRefreshRate, DigitalScrollEffect.Easing easing) {
      this.animationDuration = animationDuration;
      this.animationRefreshRate = animationRefreshRate;
      this.easing = easing;
   }

   public void setAnimationDuration(float duration) {
      if (duration > 0.0F) {
         this.animationDuration = duration;
      }
   }

   public void setAnimationRefreshRate(float refreshRate) {
      if (refreshRate > 0.0F) {
         this.animationRefreshRate = refreshRate;
      }
   }

   public void setEasing(DigitalScrollEffect.Easing easing) {
      this.easing = easing;
   }

   public float getAnimationDuration() {
      return this.animationDuration;
   }

   public float getAnimationRefreshRate() {
      return this.animationRefreshRate;
   }

   public DigitalScrollEffect.Easing getEasing() {
      return this.easing;
   }

   public void startAnimation(float targetValue) {
      this.startAnimation(this.currentValue, targetValue);
   }

   public void startAnimation(float startValue, float targetValue) {
      this.startValue = startValue;
      this.targetValue = targetValue;
      this.currentValue = startValue;
      this.startTime = System.currentTimeMillis();
      this.lastUpdateTime = this.startTime;
      this.isAnimating = true;
   }

   public void stopAnimation() {
      if (this.isAnimating) {
         this.currentValue = this.targetValue;
         this.isAnimating = false;
         this.startTime = -1L;
         this.lastUpdateTime = -1L;
      }
   }

   public boolean isAnimating() {
      return this.isAnimating;
   }

   public float getCurrentValue() {
      return this.currentValue;
   }

   public float getTargetValue() {
      return this.targetValue;
   }

   public void setTargetValue(float targetValue) {
      this.targetValue = targetValue;
      if (!this.isAnimating) {
         this.startAnimation(this.currentValue, targetValue);
      }
   }

   public float getStartValue() {
      return this.startValue;
   }

   public float update(long currentTime) {
      if (this.isAnimating && this.startTime >= 0L && !(this.animationDuration <= 0.0F)) {
         if (this.lastUpdateTime > 0L && (float)(currentTime - this.lastUpdateTime) < this.animationRefreshRate * 1000.0F) {
            return this.currentValue;
         } else {
            float elapsedSeconds = (float)(currentTime - this.startTime) / 1000.0F;
            float progress = Math.min(elapsedSeconds / this.animationDuration, 1.0F);
            float easedProgress = this.applyEasing(progress);
            this.currentValue = Mth.lerp(easedProgress, this.startValue, this.targetValue);
            if (progress >= 1.0F) {
               this.currentValue = this.targetValue;
               this.isAnimating = false;
            }

            this.lastUpdateTime = currentTime;
            return this.currentValue;
         }
      } else {
         return this.currentValue;
      }
   }

   public float update() {
      return this.update(System.currentTimeMillis());
   }

   public int getRoundedValue() {
      return Math.round(this.currentValue);
   }

   private float applyEasing(float progress) {
      if (progress <= 0.0F) {
         return 0.0F;
      } else if (progress >= 1.0F) {
         return 1.0F;
      } else {
         switch (this.easing) {
            case CUBIC_OUT:
               float t = 1.0F - progress;
               return 1.0F - t * t * t;
            case LINEAR:
               return progress;
            case QUINTIC_OUT:
            default:
               float t2 = 1.0F - progress;
               return 1.0F - t2 * t2 * t2 * t2 * t2;
         }
      }
   }

   public void reset() {
      this.startValue = 0.0F;
      this.targetValue = 0.0F;
      this.currentValue = 0.0F;
      this.startTime = -1L;
      this.lastUpdateTime = -1L;
      this.isAnimating = false;
   }

   public static enum Easing {
      QUINTIC_OUT,
      CUBIC_OUT,
      LINEAR;
   }
}
