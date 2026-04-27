/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

/**
 * @test AOT cache should preserve heap object identity when required by JLS. For example, Enums and Integers.
 * @requires vm.cds
 * @requires vm.cds.supports.aot.class.linking
 * @requires vm.debug
 * @library /test/lib
 * @build HeapObjectIdentity
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar dummy.jar
 *                 Dummy
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar boot.jar
 *                 HeapObjectIdentityApp
 *                 MyAOTInitedClass
 *                 MyAOTInitedClass$MyEnum
 *                 MyAOTInitedClass$Wrapper
 * @run driver HeapObjectIdentity AOT --two-step-training
 */

import jdk.test.lib.cds.CDSAppTester;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.helpers.ClassFileInstaller;

public class HeapObjectIdentity {
    static final String appJar = ClassFileInstaller.getJarPath("dummy.jar");
    static final String bootJar = ClassFileInstaller.getJarPath("boot.jar");
    static final String mainClass = "HeapObjectIdentityApp"; // Loaded from boot.jar

    public static void main(String[] args) throws Exception {
        Tester t = new Tester();
        t.run(args);

        // Integer$IntegerCache should preserve the object identity of cached Integer objects,
        // even when the cache size is different between assembly and production.
        t.productionRun(new String[] {
                "-XX:AutoBoxCacheMax=2048"
                });
    }

    static class Tester extends CDSAppTester {
        public Tester() {
            super(mainClass);
        }

        @Override
        public String classpath(RunMode runMode) {
            return appJar;
        }

        @Override
        public String[] vmArgs(RunMode runMode) {
            String bootcp = "-Xbootclasspath/a:" + bootJar;
            if (runMode == RunMode.ASSEMBLY) {
                return new String[] {
                    "-Xlog:aot+class=debug",
                    "-XX:AOTInitTestClass=MyAOTInitedClass",
                    bootcp
                };
            } else {
                return new String[] {bootcp};
            }
        }

        @Override
        public String[] appCommandLine(RunMode runMode) {
            return new String[] {
                mainClass,
                runMode.toString(),
            };
        }

        @Override
        public void checkExecution(OutputAnalyzer out, RunMode runMode) throws Exception {
            if (runMode == RunMode.ASSEMBLY) {
                out.shouldContain("MyAOTInitedClass aot-linked inited");
            }
        }
    }
}

class HeapObjectIdentityApp {
    public static void main(String... args) {
        MyAOTInitedClass.test();
    }
}

// This class is loaded by the boot loader, as -XX:AOTInitTestClass is not too friendly
// with classes by other loaders.
class MyAOTInitedClass {
    static Object[] archivedObjects;
    static {
        if (archivedObjects == null) {
            archivedObjects = new Object[14];
            archivedObjects[0] = Wrapper.BOOLEAN;
            archivedObjects[1] = Wrapper.INT.zero();
            archivedObjects[2] = Wrapper.DOUBLE.zero();
            archivedObjects[3] = MyEnum.DUMMY1;

            archivedObjects[4] = Boolean.class;
            archivedObjects[5] = Byte.class;
            archivedObjects[6] = Character.class;
            archivedObjects[7] = Short.class;
            archivedObjects[8] = Integer.class;
            archivedObjects[9] = Long.class;
            archivedObjects[10] = Float.class;
            archivedObjects[11] = Double.class;
            archivedObjects[12] = Void.class;

            archivedObjects[13] = Integer.valueOf(1);
        } else {
            System.out.println("Initialized from CDS");
        }
    }

    public static void test() {
        if (archivedObjects[0] != Wrapper.BOOLEAN) {
            throw new RuntimeException("Huh 0");
        }

        if (archivedObjects[1] != Wrapper.INT.zero()) {
            throw new RuntimeException("Huh 1");
        }

        if (archivedObjects[2] != Wrapper.DOUBLE.zero()) {
            throw new RuntimeException("Huh 2");
        }

        if (archivedObjects[3] != MyEnum.DUMMY1) {
            throw new RuntimeException("Huh 3");
        }

        if (MyEnum.BOOLEAN != true) {
            throw new RuntimeException("Huh 10.1");
        }
        if (MyEnum.BYTE != -128) {
            throw new RuntimeException("Huh 10.2");
        }
        if (MyEnum.CHAR != 'c') {
            throw new RuntimeException("Huh 10.3");
        }
        if (MyEnum.SHORT != -12345) {
            throw new RuntimeException("Huh 10.4");
        }
        if (MyEnum.INT != -123456) {
            throw new RuntimeException("Huh 10.5");
        }
        if (MyEnum.LONG != 0x1234567890L) {
            throw new RuntimeException("Huh 10.6");
        }
        if (MyEnum.LONG2 != -0x1234567890L) {
            throw new RuntimeException("Huh 10.7");
        }
        if (MyEnum.FLOAT != 567891.0f) {
            throw new RuntimeException("Huh 10.8");
        }
        if (MyEnum.DOUBLE != 12345678905678.890) {
            throw new RuntimeException("Huh 10.9");
        }

        checkClass(4, Boolean.class);
        checkClass(5, Byte.class);
        checkClass(6, Character.class);
        checkClass(7, Short.class);
        checkClass(8, Integer.class);
        checkClass(9, Long.class);
        checkClass(10, Float.class);
        checkClass(11, Double.class);
        checkClass(12, Void.class);

        if (archivedObjects[13] != Integer.valueOf(1)) {
            throw new RuntimeException("Integer cache identity test failed");
        }

        System.out.println("Success!");
    }

    static void checkClass(int index, Class c) {
        if (archivedObjects[index] != c) {
            throw new RuntimeException("archivedObjects[" + index + "] should be " + c);
        }
    }

    // Simplified version of sun.invoke.util.Wrapper
    public enum Wrapper {
        //        wrapperType      simple     primitiveType  simple     char  emptyArray
        BOOLEAN(  Boolean.class,   "Boolean", boolean.class, "boolean", 'Z', new boolean[0]),
        INT    (  Integer.class,   "Integer",     int.class,     "int", 'I', new     int[0]),
        DOUBLE (   Double.class,    "Double",  double.class,  "double", 'D', new  double[0])
        ;

        public static final int COUNT = 10;
        private static final Object DOUBLE_ZERO = (Double)(double)0;

        private final Class<?> wrapperType;
        private final Class<?> primitiveType;
        private final char     basicTypeChar;
        private final String   basicTypeString;
        private final Object   emptyArray;

        Wrapper(Class<?> wtype,
                String wtypeName,
                Class<?> ptype,
                String ptypeName,
                char tchar,
                Object emptyArray) {
            this.wrapperType = wtype;
            this.primitiveType = ptype;
            this.basicTypeChar = tchar;
            this.basicTypeString = String.valueOf(this.basicTypeChar);
            this.emptyArray = emptyArray;
        }

        public Object zero() {
            return switch (this) {
                case BOOLEAN -> Boolean.FALSE;
                case INT -> (Integer)0;
                case DOUBLE -> DOUBLE_ZERO;
                default -> null;
            };
        }
    }

    enum MyEnum {
        DUMMY1,
        DUMMY2;

        static final boolean BOOLEAN = true;
        static final byte    BYTE    = -128;
        static final short   SHORT   = -12345;
        static final char    CHAR    = 'c';
        static final int     INT     = -123456;
        static final long    LONG    =  0x1234567890L;
        static final long    LONG2   = -0x1234567890L;
        static final float   FLOAT   = 567891.0f;
        static final double  DOUBLE  = 12345678905678.890;
    }
}

class Dummy {}
