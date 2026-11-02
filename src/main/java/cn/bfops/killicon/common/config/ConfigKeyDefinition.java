package cn.bfops.killicon.common.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public final class ConfigKeyDefinition {
   private final String key;
   private final String elementId;
   private final ConfigType type;
   private final Category category;
   private final String dependsOn;
   private final JsonElement defaultValue;
   private final String[] choices;
   private final String textureTab;

   private ConfigKeyDefinition(ConfigKeyDefinition.Builder b) {
      this.key = b.key;
      this.elementId = b.elementId;
      this.type = b.type;
      this.category = b.category;
      this.dependsOn = b.dependsOn;
      this.defaultValue = b.defaultValue;
      this.choices = b.choices;
      this.textureTab = b.textureTab;
   }

   public String key() {
      return this.key;
   }

   public String elementId() {
      return this.elementId;
   }

   public ConfigType type() {
      return this.type;
   }

   public Category category() {
      return this.category;
   }

   public String dependsOn() {
      return this.dependsOn;
   }

   public JsonElement defaultValue() {
      return this.defaultValue;
   }

   public String[] choices() {
      return this.choices;
   }

   public String textureTab() {
      return this.textureTab;
   }

   public static ConfigKeyDefinition.Builder builder() {
      return new ConfigKeyDefinition.Builder();
   }

   public static final class Builder {
      private String key;
      private String elementId;
      private ConfigType type = ConfigType.STRING;
      private Category category = Category.EFFECT;
      private String dependsOn;
      private JsonElement defaultValue;
      private String[] choices;
      private String textureTab;

      public ConfigKeyDefinition.Builder key(String key) {
         this.key = key;
         return this;
      }

      public ConfigKeyDefinition.Builder element(String elementId) {
         this.elementId = elementId;
         return this;
      }

      public ConfigKeyDefinition.Builder type(ConfigType type) {
         this.type = type;
         return this;
      }

      public ConfigKeyDefinition.Builder category(Category category) {
         this.category = category;
         return this;
      }

      public ConfigKeyDefinition.Builder dependsOn(String dependsOn) {
         this.dependsOn = dependsOn;
         return this;
      }

      public ConfigKeyDefinition.Builder textureTab(String textureTab) {
         this.textureTab = textureTab;
         return this;
      }

      public ConfigKeyDefinition.Builder defaultValue(boolean v) {
         this.defaultValue = new JsonPrimitive(v);
         return this;
      }

      public ConfigKeyDefinition.Builder defaultValue(int v) {
         this.defaultValue = new JsonPrimitive(v);
         return this;
      }

      public ConfigKeyDefinition.Builder defaultValue(float v) {
         this.defaultValue = new JsonPrimitive(v);
         return this;
      }

      public ConfigKeyDefinition.Builder defaultValue(String v) {
         this.defaultValue = new JsonPrimitive(v);
         return this;
      }

      public ConfigKeyDefinition.Builder defaultValue(JsonElement v) {
         this.defaultValue = v;
         return this;
      }

      public ConfigKeyDefinition.Builder choices(String... choices) {
         this.choices = choices;
         return this;
      }

      public ConfigKeyDefinition build() {
         if (this.key == null || this.elementId == null) {
            throw new IllegalStateException("ConfigKeyDefinition 必须提供 key 与 element: " + this.key + "@" + this.elementId);
         } else if (this.defaultValue == null) {
            throw new IllegalStateException("ConfigKeyDefinition 必须提供默认值: " + this.elementId + "/" + this.key);
         } else {
            return new ConfigKeyDefinition(this);
         }
      }
   }
}
