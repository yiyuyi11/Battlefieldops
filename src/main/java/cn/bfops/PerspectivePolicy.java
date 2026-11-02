package cn.bfops;
import net.minecraft.client.CameraType;
/** Perspective ownership ends on leaving the match; vehicle choice persists across seats. */
final class PerspectivePolicy {
    private CameraType previous,vehicle;
    private boolean riding;
    CameraType update(boolean active,boolean passenger,CameraType current){
        if(!active){CameraType result=previous==null?current:previous;previous=null;vehicle=null;riding=false;return result;}
        if(previous==null)previous=current;
        if(passenger){if(!riding){riding=true;if(vehicle!=null)current=vehicle;}vehicle=current;return current;}
        if(riding){vehicle=current;riding=false;}
        return CameraType.FIRST_PERSON;
    }
}
