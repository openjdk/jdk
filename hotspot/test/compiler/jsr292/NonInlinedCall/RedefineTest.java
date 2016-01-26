/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * @test
 * @bug 8072008
 * @library /testlibrary /test/lib
 * @compile -XDignore.symbol.file RedefineTest.java Agent.java
 * @run main ClassFileInstaller sun.hotspot.WhiteBox
 *                              sun.hotspot.WhiteBox$WhiteBoxPermission
 *                              java.lang.invoke.RedefineTest
 *                              Agent
 *                              jdk.test.lib.Asserts
 * @run main Agent agent.jar java.lang.invoke.RedefineTest
 * @run main/othervm -Xbootclasspath/a:. -javaagent:agent.jar
 *                   -XX:+IgnoreUnrecognizedVMOptions
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   -Xbatch -XX:-TieredCompilation -XX:CICompilerCount=1
 *                      java.lang.invoke.RedefineTest
 */
package java.lang.invoke;

import sun.hotspot.WhiteBox;
import sun.misc.Unsafe;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.vm.annotation.DontInline;
import java.lang.instrument.ClassDefinition;
import java.lang.instrument.Instrumentation;

import static jdk.internal.org.objectweb.asm.Opcodes.*;

public class RedefineTest {
    static final MethodHandles.Lookup LOOKUP = MethodHandles.Lookup.IMPL_LOOKUP;
    static final Unsafe UNSAFE = Unsafe.getUnsafe();

    static final String NAME = "java/lang/invoke/RedefineTest$T";

    static Class<?> getClass(int r) {
        byte[] classFile = getClassFile(r);
        return UNSAFE.defineClass(NAME, classFile, 0, classFile.length, null, null);
    }

    /**
     * Generates a class of the following shape:
     *     static class T {
     *         @DontInline public static int f() { return $r; }
     *     }
     */
    static byte[] getClassFile(int r) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        MethodVisitor mv;
        cw.visit(52, ACC_PUBLIC | ACC_SUPER, NAME, null, "java/lang/Object", null);
        {
            mv = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, "f", "()I", null, null);
            mv.visitAnnotation("Ljdk/internal/vm/annotation/DontInline;", true);
            mv.visitCode();
            mv.visitLdcInsn(r);
            mv.visitInsn(IRETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }
        cw.visitEnd();
        return cw.toByteArray();
    }

    static final MethodHandle mh;
    static final Class<?> CLS = getClass(0);
    static {
        try {
            mh = LOOKUP.findStatic(CLS, "f", MethodType.methodType(int.class));
        } catch (Exception e) {
            throw new Error(e);
        }
    }

    static final WhiteBox WB = WhiteBox.getWhiteBox();

    @DontInline
    static int invokeBasic() {
        try {
            return (int)mh.invokeExact();
        } catch (Throwable e) {
            throw new Error(e);
        }
    }

    static Instrumentation instr;
    public static void premain(String args, Instrumentation instr) {
        RedefineTest.instr = instr;
    }


    public static void main(String[] args) throws Exception {
        for (int i = 0; i < 20_000; i++) {
            int r = invokeBasic();
            if (r != 0) {
                throw new Error(r + " != 0");
            }
        }
        // WB.ensureCompiled();

        redefine();

        int exp = (instr != null) ? 1 : 0;

        for (int i = 0; i < 20_000; i++) {
            if (invokeBasic() != exp) {
                throw new Error();
            }
        }

        WB.clearInlineCaches();

        for (int i = 0; i < 20_000; i++) {
            if (invokeBasic() != exp) {
                throw new Error();
            }
        }

        // WB.ensureCompiled();
    }

    static void redefine() {
        if (instr == null) {
            System.out.println("NOT REDEFINED");
            return;
        }
        ClassDefinition cd = new ClassDefinition(CLS, getClassFile(1));
        try {
            instr.redefineClasses(cd);
        } catch (Exception e) {
            throw new Error(e);
        }
        System.out.println("REDEFINED");
    }
}
