package cn.bfops;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;

/** Vanilla carriers keep the original optional-client connection contract. */
final class ConquestEquipment {
    static final String TAG="bfops_support";
    static SupportRules.Kind kind(String id){
        if(id==null||(!id.startsWith("battlefield_ops:")&&!id.startsWith("gd656conquest:")))return null;
        return SupportRules.Kind.parse(id);
    }
    static SupportRules.Kind kind(ItemStack stack){
        // Registered form first, then the legacy paper carrier so old inventories still resolve.
        if(stack!=null&&stack.getItem() instanceof SupportItem item)return item.kind();
        var data=stack.get(DataComponents.CUSTOM_DATA);return data==null?null:kind(data.copyTag().getString(TAG));
    }
    static ItemStack stack(String id,int count){
        var kind=kind(id);if(kind==null)return ItemStack.EMPTY;
        SupportItem registered=SupportItems.item(kind);
        if(registered!=null){
            return new ItemStack(registered,Math.max(1,count));
        }
        var out=new ItemStack(Items.PAPER,Math.max(1,Math.min(count,kind==SupportRules.Kind.AMMO_BAG||kind==SupportRules.Kind.MEDIC_BAG?64:1)));
        var tag=new CompoundTag();tag.putString(TAG,kind.key());out.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));
        out.set(DataComponents.CUSTOM_NAME,Component.literal(kind.label).withStyle(net.minecraft.ChatFormatting.AQUA));
        out.set(DataComponents.CUSTOM_MODEL_DATA,new CustomModelData(656100+kind.ordinal()));
        String usage=switch(kind){
            case MEDIC_BAG->"右键自用；空手以外持此物右键队友治疗";
            case AMMO_BAG->"右键自用；持此物右键队友补充弹药";
            case MED_BOX->"指向四格内地面右键放置，自动治疗附近队友";
            case AMMO_BOX->"指向四格内地面右键放置，自动补给附近队友";
            case RADIO_BEACON->"加入小队后指向地面右键放置，供小队步兵部署";
            case GROUND_SENSOR->"指向四格内地面右键放置，扫描附近敌人";
        };
        out.set(DataComponents.LORE,new net.minecraft.world.item.component.ItemLore(java.util.List.of(
            Component.literal(usage).withStyle(net.minecraft.ChatFormatting.GRAY),
            Component.literal("仅在本局内使用；有效补给队友才获得支援分").withStyle(net.minecraft.ChatFormatting.DARK_AQUA))));
        return out;
    }
}
