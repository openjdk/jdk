/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8280473
 * @library /test/lib
 * @modules java.base/jdk.internal.org.objectweb.asm
 *
 * @run main/othervm -XX:+TieredCompilation -XX:TieredStopAtLevel=1
 *                   -Xbatch -XX:CompileThreshold=100 -XX:CompileCommand=compileonly,*::test
 *                   -XX:CompileCommand=quiet -XX:+PrintCompilation
 *                     compiler.runtime.TestConstantDynamic
 * @run main/othervm -XX:-TieredCompilation
 *                   -Xbatch -XX:CompileThreshold=100 -XX:CompileCommand=compileonly,*::test
 *                   -XX:CompileCommand=quiet -XX:+PrintCompilation
 *                     compiler.runtime.TestConstantDynamic
 */

package compiler.runtime;

import jdk.internal.org.objectweb.asm.*;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.lang.constant.ConstantDescs;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandleProxies;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;

import static jdk.internal.org.objectweb.asm.ClassWriter.*;
import static jdk.internal.org.objectweb.asm.Opcodes.*;

public class TestConstantDynamic {
    static final Class<TestConstantDynamic> THIS_CLASS = TestConstantDynamic.class;

    static final String THIS_CLASS_NAME = THIS_CLASS.getName().replace('.', '/');
    static final String CLASS_NAME = THIS_CLASS_NAME + "$Test";

    public interface Test {
        Object run(boolean b);
    }

    public static final String PATH = System.getProperty("test.classes", ".") + java.io.File.separator;
    private static int ID = 0;

    /* =================================================================================================== */

    static final String BSM_DESC = "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/Class;)Ljava/lang/Object;";
    static final Handle BSM = new Handle(H_INVOKESTATIC, THIS_CLASS_NAME, "bsm", BSM_DESC, false);

    static Object bsm(MethodHandles.Lookup lookup, String name, Class c) throws IllegalAccessException {
        Object[] classData = MethodHandles.classData(lookup, ConstantDescs.DEFAULT_NAME, Object[].class);
        Object value = classData[0];
        System.out.printf("BSM: lookup=%s name=\"%s\" class=%s => \"%s\"\n", lookup, name, c, classData[0]);
        return value;
    }

    static final Handle THROWING_BSM = new Handle(H_INVOKESTATIC, THIS_CLASS_NAME, "throwingBSM", BSM_DESC, false);

    static Object throwingBSM(MethodHandles.Lookup lookup, String name, Class c) throws IllegalAccessException {
        Object[] classData = (Object[])MethodHandles.classData(lookup, ConstantDescs.DEFAULT_NAME, Object[].class);
        Object value = classData[0];
        System.out.printf("BSM: lookup=%s name=\"%s\" class=%s value=\"%s\" => Exception\n", lookup, name, c, value);
        throw new IllegalArgumentException(lookup.lookupClass().getName() + ": " + c.getName() + " " + name + " " + value);
    }

    /* =================================================================================================== */

    static byte[] generateClassFile(String suffix, String desc, int retOpcode, Handle bsm) throws IOException {
        var cw = new ClassWriter(COMPUTE_MAXS | COMPUTE_FRAMES);
        String name = CLASS_NAME + "_" + suffix + "_" + (++ID);
        cw.visit(V19, ACC_PUBLIC | ACC_SUPER, name, null, "java/lang/Object", null);

        Handle localBSM = new Handle(H_INVOKESTATIC, name, "bsm", BSM_DESC, false);

        {
            var mv = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, "bsm", BSM_DESC, null, null);

            mv.visitLdcInsn(bsm);
            mv.visitIntInsn(ALOAD, 0);
            mv.visitIntInsn(ALOAD, 1);
            mv.visitIntInsn(ALOAD, 2);
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/invoke/MethodHandle", "invokeExact", BSM_DESC, false);
            mv.visitInsn(ARETURN);
            mv.visitMaxs(0, 0);
        }

        {
            var mv = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, "test", "(Z)" + desc, null, null);
            mv.visitCode();

            Label endL = new Label();
            Label falseL = new Label();

            mv.visitIntInsn(ILOAD, 0);
            mv.visitJumpInsn(Opcodes.IFNE, falseL);

            mv.visitLdcInsn(new ConstantDynamic("first", desc, localBSM)); // is resolved on b = false

            mv.visitJumpInsn(GOTO, endL);

            mv.visitLabel(falseL);

            mv.visitLdcInsn(new ConstantDynamic("second", desc, localBSM)); // is resolved on b = true

            mv.visitLabel(endL);
            mv.visitInsn(retOpcode);
            mv.visitMaxs(0, 0);
        }
        byte[] classFile = cw.toByteArray();

        try (FileOutputStream fos = new FileOutputStream(PATH + name + ".class")) {
            fos.write(classFile);
        }

        return classFile;
    }

    static Test generate(String desc, int retOpcode, Object value, Handle bsm, boolean shouldThrow) {
        try {
            byte[] classFile = generateClassFile("CD", desc, retOpcode, bsm);
            Object[] classData = new Object[] { value };
            MethodHandles.Lookup testLookup = MethodHandles.lookup().defineHiddenClassWithClassData(classFile, classData, true);
            Method testMethod = testLookup.lookupClass().getDeclaredMethod("test", boolean.class);
            MethodHandle testMH = testLookup.unreflect(testMethod);

            if (shouldThrow) {
                // Install empty handler for linkage errors, but throw an error on successful invocation.
                // try { Test.test(b); throw AssertionError(); } catch (LinkageError e) { /* expected */ }
                testMH = MethodHandles.filterReturnValue(testMH,
                        MethodHandles.dropArguments(
                            MethodHandles.insertArguments(
                                    MethodHandles.throwException(testMH.type().returnType(), AssertionError.class),
                                    0, new AssertionError("no exception thrown")),
                            0, testMH.type().returnType()));

                testMH = MethodHandles.catchException(testMH, LinkageError.class,
                        MethodHandles.empty(MethodType.methodType(testMH.type().returnType(), LinkageError.class)));
            } else {
                Class<?> type = testMH.type().returnType();
                testMH = MethodHandles.filterReturnValue(testMH,
                                MethodHandles.insertArguments(VALIDATE_MH, 0, value)
                                        .asType(MethodType.methodType(type, type)));
            }

            return MethodHandleProxies.asInterfaceInstance(Test.class, testMH);
        } catch (Throwable e) {
            throw new InternalError(e);
        }
    }

    static final MethodHandle VALIDATE_MH;
    static {
        try {
            VALIDATE_MH = MethodHandles.lookup().findStatic(THIS_CLASS, "validateResult",
                                                            MethodType.methodType(Object.class, Object.class, Object.class));
        } catch (ReflectiveOperationException e) {
            throw new InternalError(e);
        }
    }
    static Object validateResult(Object expected, Object actual) {
        if ((expected == null && actual != null) ||
            (expected != null && !expected.equals(actual))) {
            throw new AssertionError(String.format("expected=%s != actual=%s", expected.toString(), actual.toString()));
        }
        return actual;
    }

    private Handle bsm;
    private boolean shouldThrow;

    TestConstantDynamic(Handle bsm, boolean shouldThrow) {
        this.bsm = bsm;
        this.shouldThrow = shouldThrow;
    }

    static TestConstantDynamic shouldNotThrow() {
        return new TestConstantDynamic(BSM, false);
    }

    static TestConstantDynamic shouldThrow() {
        return new TestConstantDynamic(THROWING_BSM, true);
    }

    static void shouldThrow(Handle bsm, String desc, int retOpcode, Object value) {
        (new TestConstantDynamic(bsm, true)).test(desc, retOpcode, value);
    }

    void test(String desc, int retOpcode, Object value) {
        Test test = generate(desc, retOpcode, value, bsm, shouldThrow);

        for (int i = 0; i < 200; i++) {
            test.run(false);
        }
        for (int i = 0; i < 200; i++) {
            test.run(true);
        }
    }

    static void run(TestConstantDynamic t) {
        t.test("Z", IRETURN, Boolean.TRUE);
        t.test("B", IRETURN, Byte.MAX_VALUE);
        t.test("S", IRETURN, Short.MAX_VALUE);
        t.test("C", IRETURN, Character.MAX_VALUE);
        t.test("I", IRETURN, Integer.MAX_VALUE);
        t.test("J", LRETURN, Long.MAX_VALUE);
        t.test("F", FRETURN, Float.MAX_VALUE);
        t.test("D", DRETURN, Double.MAX_VALUE);

        t.test("Ljava/lang/Object;", ARETURN, new Object());
        t.test("Ljava/lang/Object;", ARETURN, null);

        t.test("[Ljava/lang/Object;", ARETURN, new Object[0]);
        t.test("[Ljava/lang/Object;", ARETURN, null);

        t.test("[I", ARETURN, new int[0]);
        t.test("[I", ARETURN, null);

        t.test("Ljava/lang/Runnable;", ARETURN, (Runnable)(() -> {}));
        t.test("Ljava/lang/Runnable;", ARETURN, null);
    }

    public static void main(String[] args) {
        run(shouldNotThrow());

        run(shouldThrow()); // use error-throwing BSM

        shouldThrow(BSM, "Ljava/lang/Runnable;", ARETURN, new Object()); // not a Runnable
    }
}
