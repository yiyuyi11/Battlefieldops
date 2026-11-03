package cn.bfops;
import java.nio.file.*;
import java.util.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Check the shipped client command and the display-only preview without booting a Minecraft world. */
public final class PreviewCommandSim {
    public static void main(String[] args)throws Exception{
        int checks=0;Set<String> literals=new HashSet<>();
        try(var paths=Files.list(Path.of("build/classes/cn/bfops"))){
            for(var path:paths.filter(p->p.getFileName().toString().startsWith("DeathPreviewScreen")&&p.toString().endsWith(".class")).toList()){
                var node=new ClassNode();new ClassReader(Files.readAllBytes(path)).accept(node,0);
                for(var method:node.methods)for(var insn:method.instructions){
                    if(insn instanceof LdcInsnNode load&&load.cst instanceof String value)literals.add(value);
                    if(insn instanceof MethodInsnNode call){
                        if(call.owner.startsWith("cn/bfops/Network")||call.owner.startsWith("cn/bfops/voice/network")||call.owner.equals("cn/bfops/Client")&&call.name.equals("send"))throw new AssertionError("Preview sends a network request");
                        if(Set.of("kill","hurt","setHealth","respawn","sendRespawn","handleRespawn").contains(call.name))throw new AssertionError("Preview mutates player life");
                        checks++;
                    }
                }
            }
        }
        for(String command:List.of("bfpreview","death","downed")){if(!literals.contains(command))throw new AssertionError("Missing command "+command);checks++;}
        var dropdown=new ClassNode();new ClassReader(Files.readAllBytes(Path.of("build/classes/cn/bfops/FrontScreen.class"))).accept(dropdown,0);
        boolean kind=false,icon=false;
        for(var method:dropdown.methods)if(method.name.equals("dropdown"))for(var insn:method.instructions)if(insn instanceof MethodInsnNode call&&call.owner.endsWith("ChoiceScreen$Choice")){
            kind|=call.name.equals("kind");icon|=call.name.equals("icon");
        }
        if(!kind||!icon)throw new AssertionError("Selected dropdown option artwork is missing");checks++;
        try(var paths=Files.list(Path.of("build/classes/cn/bfops"))){
            for(var path:paths.filter(p->p.getFileName().toString().startsWith("Client")&&p.toString().endsWith(".class")).toList()){
                var node=new ClassNode();new ClassReader(Files.readAllBytes(path)).accept(node,0);
                for(var method:node.methods)for(var insn:method.instructions)if(insn instanceof MethodInsnNode call){
                    if(call.owner.equals("cn/bfops/MuzzleGlow")&&Set.of("muzzle","draw").contains(call.name))throw new AssertionError("Artificial muzzle HUD glow still enabled");
                }
                checks++;
            }
        }
        System.out.println("PreviewCommandSim: "+checks+" command/artwork/display-only checks passed");
    }
}
