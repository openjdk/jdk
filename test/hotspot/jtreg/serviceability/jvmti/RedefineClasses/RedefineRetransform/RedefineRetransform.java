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
 *
 * @bug 7124710
 *
 * @requires vm.jvmti
 * @modules java.base/jdk.internal.org.objectweb.asm
 * @library /test/lib
 *
 * @comment main/othervm/native -Xlog:redefine*=trace -agentlib:RedefineRetransform RedefineRetransform
 * @run main/othervm/native -agentlib:RedefineRetransform RedefineRetransform 1
 * @run main/othervm/native -agentlib:RedefineRetransform RedefineRetransform 2
 * @run main/othervm/native -agentlib:RedefineRetransform RedefineRetransform 3
 * @run main/othervm/native -agentlib:RedefineRetransform RedefineRetransform 4
 * @run main/othervm/native -agentlib:RedefineRetransform RedefineRetransform 5
 * @run main/othervm/native -agentlib:RedefineRetransform RedefineRetransform 6
 */

import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import jdk.internal.org.objectweb.asm.AnnotationVisitor;
import jdk.internal.org.objectweb.asm.ClassReader;
import jdk.internal.org.objectweb.asm.ClassVisitor;
import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.Opcodes;
import jdk.internal.org.objectweb.asm.Type;

public class RedefineRetransform {
    static {
        System.loadLibrary("RedefineRetransform");
    }

	@Retention(RetentionPolicy.RUNTIME)
    @interface ClassVersion {
        int value();
    }

    // Use runtime-visible annotation to specify class version
    @ClassVersion(0)
    static class TestClass {
        public TestClass() { }
    }

    // redefines testClass with classBytes, instrument with classLoadHookBytes (is != null)
    private static native byte[] nRedefine(Class testClass, byte[] classBytes, byte[] classLoadHookBytes);
    // retransforms testClass with classBytes (if != null)
    private static native byte[] nRetransform(Class testClass, byte[] classBytes);

    private static byte[] initialClassBytes;

    private static class VersionScanner extends ClassVisitor {
        private Integer detectedVersion;
        private Integer versionToSet;
        // to get version
        public VersionScanner() {
            super(Opcodes.ASM7);
        }
        // to set version
        public VersionScanner(int verToSet, ClassVisitor classVisitor) {
            super(Opcodes.ASM7, classVisitor);
            versionToSet = verToSet;
        }

        public int detectedVersion() {
            if (detectedVersion == null) {
                throw new RuntimeException("Version not detected");
            }
            return detectedVersion;
        }

        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            //log("visitAnnotation: descr = '" + descriptor + "', visible = " + visible);
            if (Type.getDescriptor(ClassVersion.class).equals(descriptor)) {
                return new AnnotationVisitor(Opcodes.ASM7, super.visitAnnotation(descriptor, visible)) {
                    @Override
                    public void visit(String name, Object value) {
                        //log("visit: name = '" + name + "', value = " + value
                        //        + " (" + (value == null ? "N/A" : value.getClass()) + ")");
                        if ("value".equals(name) && value instanceof Integer intValue) {
                            detectedVersion = intValue;
                            if (versionToSet != null) {
                                //log("replace with " + versionToSet);
                                value = versionToSet;
                            }
                        }
                        super.visit(name, value);
                    }
                };
            }
            return super.visitAnnotation(descriptor, visible);
        }
    }

    private static byte[] getClassBytes(int ver) {
        if (ver < 0) {
            return null;
        }
        ClassWriter cw = new ClassWriter(0);
        ClassReader cr = new ClassReader(initialClassBytes);
        cr.accept(new VersionScanner(ver, cw), 0);
        return cw.toByteArray();
    }

    private static int getClassBytesVersion(byte[] classBytes) {
        ClassReader cr = new ClassReader(classBytes);
        VersionScanner scanner = new VersionScanner();
        cr.accept(scanner, 0);
        return scanner.detectedVersion();
    }

    static void init() {
        try {
            initialClassBytes = TestClass.class.getClassLoader()
                    .getResourceAsStream("RedefineRetransform$TestClass.class")
                    .readAllBytes();
            log("Read TestClass bytes: " + initialClassBytes.length);
        } catch (IOException ex) {
            throw new RuntimeException("Failed to read class bytes", ex);
        }
    }

    static void redefine(int ver) {
        redefine(ver, -1);
    }

    static void redefine(int ver, int classLoadHookVer) {
        byte[] classBytes = getClassBytes(ver);
        byte[] classLoadHookBytes = getClassBytes(classLoadHookVer);

        byte[] hookClassBytes = nRedefine(TestClass.class, classBytes, classLoadHookBytes);
        if (hookClassBytes == null) {
            throw new RuntimeException("Redefine error (ver = " + ver + ")");
        }
        // verity ClassFileLoadHook get expected class bytes
        int hookVer = getClassBytesVersion(hookClassBytes);
        if (hookVer != ver) {
            throw new RuntimeException("CLFH got unexpected version: "  + hookVer
                    + " (expected " + ver + ")");
        }
    }

    static void retransform(int ver, int expectedVer) {
        byte[] classBytes = getClassBytes(ver);
        byte[] hookClassBytes = nRetransform(TestClass.class, classBytes);
        int hookVer = getClassBytesVersion(hookClassBytes);
        if (hookVer != expectedVer) {
            throw new RuntimeException("CLFH got unexpected version: "  + hookVer
                    + " (expected " + expectedVer + ")");
        }
    }

    public static void main(String[] args) throws Exception {
        int testCase;
        try {
            testCase= Integer.valueOf(args[0]);
        } catch (Exception ex) {
            throw new RuntimeException("Single numeric argument expected", ex);
        }
        init();
        switch (testCase) {
        case 1:
            test("Redefine-Retransform-Retransform", () -> {
                redefine(1);
                retransform(2, 1);
                retransform(3, 1);
            });
            break;

        case 2:
            test("Redefine-Retransform-Redefine-Redefine", () -> {
                redefine(1);
                retransform(2, 1);
                redefine(3);
                redefine(4);
            });
            break;

        case 3:
            test("Redefine-Retransform-Redefine-Retransform", () -> {
                redefine(1);
                retransform(2, 1);    // sets cached class bytes
                redefine(3);                    // resets cached class bytes
                retransform(4, 3);
            });
            break;

        case 4:
            test("Retransform-Redefine-Retransform-Retransform", () -> {
                retransform(1, 0);    // sets cached class bytes
                redefine(2);                    // reset cached class bytes
                retransform(3, 2);    // sets cached class bytes
                retransform(4, 2);
            });
            break;

        case 5:
            test("Redefine-Retransform-Redefine-Retransform with CFLH", () -> {
                redefine(1, 5);    // sets cached class bytes
                retransform(2, 1);
                redefine(3, 6);    // updates cached class bytes
                retransform(4, 3);
            });
            break;

        case 6:
            test("Retransform-Redefine-Retransform-Retransform with CFLH", () -> {
                retransform(1, 0);    // sets cached class bytes
                redefine(2, 5);    // updates cached class bytes
                retransform(3, 2);
                retransform(4, 2);
            });
            break;
        }
    }

    private static void log(Object msg) {
        System.out.println(msg);
    }

    private interface Test {
        void test();
    }

    private static void test(String name, Test theTest) {
        log(">>Test: " + name);
        theTest.test();
        log("<<Test: " + name + " - OK");
        log("");
    }
}
