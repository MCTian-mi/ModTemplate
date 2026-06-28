package dev.tianmi.rfbplugins;

import com.gtnewhorizons.retrofuturabootstrap.api.ClassNodeHandle;
import com.gtnewhorizons.retrofuturabootstrap.api.ExtensibleClassLoader;
import com.gtnewhorizons.retrofuturabootstrap.api.RfbClassTransformer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.jar.Manifest;

public class FmlTweakerSecurityManagerTransformer implements RfbClassTransformer {
    private static final String TARGET_CLASS = "net.minecraftforge.fml.common.launcher.FMLTweaker";

    @Override
    public @NotNull String id() {
        return "no-fml-security-manager";
    }

    @Override
    public boolean shouldTransformClass(
            @NotNull ExtensibleClassLoader loader,
            @NotNull RfbClassTransformer.@NotNull Context context,
            @Nullable Manifest manifest,
            @NotNull String className,
            @NotNull ClassNodeHandle classNode
    ) {
        return TARGET_CLASS.equals(className);
    }

    @Override
    public void transformClass(
            @NotNull ExtensibleClassLoader loader,
            @NotNull RfbClassTransformer.@NotNull Context context,
            @Nullable Manifest manifest,
            @NotNull String className,
            @NotNull ClassNodeHandle classNode
    ) {
        ClassNode cn = classNode.getNode();
        if (cn == null) return;

        boolean changed = false;
        for (MethodNode mn : cn.methods) {
            if ("<init>".equals(mn.name) && "()V".equals(mn.desc)) {
                for (AbstractInsnNode insn : mn.instructions) {
                    if (insn.getOpcode() == Opcodes.INVOKESTATIC) {
                        MethodInsnNode methodInsn = (MethodInsnNode) insn;
                        if ("java/lang/System".equals(methodInsn.owner)
                                && "setSecurityManager".equals(methodInsn.name)
                                && "(Ljava/lang/SecurityManager;)V".equals(methodInsn.desc)) {
                            mn.instructions.set(insn, new InsnNode(Opcodes.POP));
                            changed = true;
                        }
                    }
                }
            }
        }

        if (changed) {
            classNode.computeMaxs();
        }
    }
}


