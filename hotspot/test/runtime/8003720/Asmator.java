import com.sun.xml.internal.ws.org.objectweb.asm.*;

class Asmator {
    static byte[] fixup(byte[] buf) throws java.io.IOException {
        ClassReader cr = new ClassReader(buf);
        ClassWriter cw = new ClassWriter(0) {
            public MethodVisitor visitMethod(
                final int access,
                final String name,
                final String desc,
                final String signature,
                final String[] exceptions)
            {
                MethodVisitor mv = super.visitMethod(access,
                        name,
                        desc,
                        signature,
                        exceptions);
                if (mv == null)  return null;
                if (name.equals("callme")) {
                    // make receiver go dead!
                    mv.visitInsn(Opcodes.ACONST_NULL);
                    mv.visitVarInsn(Opcodes.ASTORE, 0);
                }
                return mv;
            }
        };
        cr.accept(cw, 0);
        return cw.toByteArray();
    }
}
