package cn.bfops.crawl.compat.tacz;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.PartEntity;

final class PlayerPronePartEntity extends PartEntity<Player> {
   private final boolean headPart;
   private final float damageMultiplier;
   private AABB boundingBox = AABB.ofSize(Vec3.ZERO, 0.2, 0.2, 0.2);

   PlayerPronePartEntity(Player parent, boolean headPart) {
      this(parent, headPart, 1.0F);
   }

   PlayerPronePartEntity(Player parent, boolean headPart, float damageMultiplier) {
      super(parent);
      this.headPart = headPart;
      this.damageMultiplier = damageMultiplier;
      this.noPhysics = true;
   }

   boolean isHeadPart() {
      return this.headPart;
   }

   void setPartBoundingBox(AABB boundingBox) {
      if (boundingBox != null) {
         this.boundingBox = boundingBox;
         this.setBoundingBox(boundingBox);
      }
   }

   public boolean hurt(DamageSource source, float amount) {
      Player parent = (Player)this.getParent();
      return parent != null && parent.isAlive() ? parent.hurt(source, amount * this.damageMultiplier) : false;
   }

   public boolean isPickable() {
      return true;
   }

   public boolean canBeCollidedWith() {
      return true;
   }

   protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
   }

   protected void readAdditionalSaveData(CompoundTag compoundTag) {
   }

   protected void addAdditionalSaveData(CompoundTag compoundTag) {
   }

   public boolean shouldBeSaved() {
      return false;
   }

   boolean isPronePartActive() {
      Player parent = (Player)this.getParent();
      return parent != null && parent.isAlive() && parent.getPose() == Pose.SWIMMING && !parent.isSwimming();
   }
}
