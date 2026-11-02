package cn.bfops.killicon.common.honor;

public record HonorDefinition(
   String id, ConditionType conditionType, String conditionParams, String nameKey, String descKey, String textureKey, int unlockRequired
) {
   public HonorDefinition(String id, ConditionType conditionType, String conditionParams, String nameKey, String descKey, String textureKey, int unlockRequired) {
      if (id == null || id.isBlank()) {
         throw new IllegalArgumentException("honor id must not be blank");
      } else if (conditionType == null) {
         throw new IllegalArgumentException("conditionType must not be null");
      } else {
         if (unlockRequired <= 0) {
            unlockRequired = 1;
         }

         this.id = id;
         this.conditionType = conditionType;
         this.conditionParams = conditionParams;
         this.nameKey = nameKey;
         this.descKey = descKey;
         this.textureKey = textureKey;
         this.unlockRequired = unlockRequired;
      }
   }

   public HonorDefinition(String id, ConditionType conditionType, String conditionParams) {
      this(id, conditionType, conditionParams, "battlefield_ops.honor." + id + ".name", "battlefield_ops.honor." + id + ".desc", "honor_" + id, 1);
   }

   public HonorDefinition(String id, ConditionType conditionType, String conditionParams, int unlockRequired) {
      this(id, conditionType, conditionParams, "battlefield_ops.honor." + id + ".name", "battlefield_ops.honor." + id + ".desc", "honor_" + id, unlockRequired);
   }

   public String displayNameKey() {
      return this.nameKey != null ? this.nameKey : "battlefield_ops.honor." + this.id + ".name";
   }

   public String descriptionKey() {
      return this.descKey != null ? this.descKey : "battlefield_ops.honor." + this.id + ".desc";
   }

   public String textureKey() {
      return this.textureKey != null ? this.textureKey : "honor_" + this.id;
   }
}
