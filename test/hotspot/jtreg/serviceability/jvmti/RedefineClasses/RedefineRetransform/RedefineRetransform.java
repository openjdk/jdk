/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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
import java.lang.classfile.Annotation;
import java.lang.classfile.AnnotationElement;
import java.lang.classfile.AnnotationValue;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.attribute.RuntimeVisibleAnnotationsAttribute;
import java.lang.constant.ClassDesc;
import java.util.NoSuchElementException;

/*
 * The test verifies that after interleaved RedefineClasses/RetransformClasses calls
 * JVMTI passes correct class bytes to ClassFileLoadHook (as per JVMTI spec).
 * To distinguish class version the test instruments test class overriding runtime-visible annotation.
 */
public class RedefineRetransform {
    static {
        System.loadLibrary("RedefineRetransform");
    }

    @Retention(RetentionPolicy.RUNTIME)
    @interface ClassVersion {
        int value();
    }

    // Use runtime-visible annotation to specify class version.
    @ClassVersion(0)
    static class TestClass {
        public TestClass() { }
    }

    // Redefines testClass with classBytes, instruments with classLoadHookBytes (if != null).
    // Returns class bytes passed to ClassFileLoadHook or null on error.
    private static native byte[] nRedefine(Class testClass, byte[] classBytes, byte[] classLoadHookBytes);
    // Retransforms testClass, instruments with classBytes (if != null).
    // Returns class bytes passed to ClassFileLoadHook or null on error.
    private static native byte[] nRetransform(Class testClass, byte[] classBytes);

    // Class bytes for initial TestClass (ClassVersion == 0).
    private static byte[] initialClassBytes;

    private static final ClassDesc CD_ClassVersion = ClassVersion.class.describeConstable().orElseThrow();

    // Generates TestClass class bytes with the specified ClassVersion value.
    private static byte[] getClassBytes(int ver) {
        if (ver < 0) {
            return null;
        }
        return ClassFile.of().transformClass(ClassFile.of().parse(initialClassBytes),
                // overwrites previously passed RVAA
                ClassTransform.endHandler(clb -> clb.with(RuntimeVisibleAnnotationsAttribute
                        .of(Annotation.of(CD_ClassVersion, AnnotationElement.ofInt("value", ver))))));
    }

    // Extracts ClassVersion values from the provided class bytes.
    private static int getClassBytesVersion(byte[] classBytes) {
        var cm = ClassFile.of().parse(classBytes);
        var rvaa = cm.findAttribute(Attributes.runtimeVisibleAnnotations()).orElseThrow();
        var elements = rvaa.annotations().stream().filter(anno -> anno.className().isFieldType(CD_ClassVersion)).findFirst().orElseThrow().elements();
        if (elements.size() != 1)
            throw new NoSuchElementException();
        var element = elements.getFirst();
        if (!element.name().equalsString("value") || !(element.value() instanceof AnnotationValue.OfInt intVal))
            throw new NoSuchElementException();
        return intVal.intValue();
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

    // Redefines TestClass to the version specified.
    static void redefine(int ver) {
        redefine(ver, -1);
    }

    // Redefines TestClass to the version specified
    // instrumenting (from ClassFileLoadHook) with 'classLoadHookVer' class bytes (if >= 0).
    // Also verifies that class bytes passed to ClassFileLoadHook have correct version (ver).
    static void redefine(int ver, int classLoadHookVer) {
        byte[] classBytes = getClassBytes(ver);
        byte[] classLoadHookBytes = getClassBytes(classLoadHookVer);

        byte[] hookClassBytes = nRedefine(TestClass.class, classBytes, classLoadHookBytes);
        if (hookClassBytes == null) {
            throw new RuntimeException("Redefine error (ver = " + ver + ")");
        }
        // verify ClassFileLoadHook gets the expected class bytes
        int hookVer = getClassBytesVersion(hookClassBytes);
        if (hookVer != ver) {
            throw new RuntimeException("CLFH got unexpected version: "  + hookVer
                    + " (expected " + ver + ")");
        }
    }

    // Retransforms TestClass instrumenting (from ClassFileLoadHook) with 'ver' class bytes (if >= 0).
    // Verifies that class bytes passed to ClassFileLoadHook have correct version (expectedVer).
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
            testCase = Integer.valueOf(args[0]);
        } catch (Exception ex) {
            throw new RuntimeException("Single numeric argument expected", ex);
        }
        init();
        switch (testCase) {
        case 1:
            test("Redefine-Retransform-Retransform", () -> {
                redefine(1);        // cached class bytes are not set
                retransform(2, 1);  // sets cached class bytes to ver 1
                retransform(3, 1);  // uses existing cache
            });
            break;

        case 2:
            test("Redefine-Retransform-Redefine-Redefine", () -> {
                redefine(1);        // cached class bytes are not set
                retransform(2, 1);  // sets cached class bytes to ver 1
                redefine(3);        // resets cached class bytes to nullptr
                redefine(4);        // cached class bytes are not set
            });
            break;

        case 3:
            test("Redefine-Retransform-Redefine-Retransform", () -> {
                redefine(1);        // cached class bytes are not set
                retransform(2, 1);  // sets cached class bytes to ver 1
                redefine(3);        // resets cached class bytes to nullptr
                retransform(4, 3);  // sets cached class bytes to ver 3
            });
            break;

        case 4:
            test("Retransform-Redefine-Retransform-Retransform", () -> {
                retransform(1, 0);  // sets cached class bytes to ver 0 (initially loaded)
                redefine(2);        // resets cached class bytes to nullptr
                retransform(3, 2);  // sets cached class bytes to ver 2
                retransform(4, 2);  // uses existing cache
            });
            break;

        case 5:
            test("Redefine-Retransform-Redefine-Retransform with CFLH", () -> {
                redefine(1, 5);     // CFLH sets cached class bytes to ver 1
                retransform(2, 1);  // uses existing cache
                redefine(3, 6);     // resets cached class bytes to nullptr,
                                    // CFLH sets cached class bytes to ver 3
                retransform(4, 3);  // uses existing cache
            });
            break;

        case 6:
            test("Retransform-Redefine-Retransform-Retransform with CFLH", () -> {
                retransform(1, 0);  // sets cached class bytes to ver 0 (initially loaded)
                redefine(2, 5);     // resets cached class bytes to nullptr,
                                    // CFLH sets cached class bytes to ver 2
                retransform(3, 2);  // uses existing cache
                retransform(4, 2);  // uses existing cache
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
