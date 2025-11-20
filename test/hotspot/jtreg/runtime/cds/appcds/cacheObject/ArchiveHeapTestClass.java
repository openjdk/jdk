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
 *
 */

/*
 * @test
 * @bug 8214781 8293187
 * @summary Test for the -XX:ArchiveHeapTestClass flag
 * @requires vm.debug == true & vm.cds.write.archived.java.heap
 * @requires vm.cds.supports.aot.class.linking
 * @modules java.logging
 * @library /test/jdk/lib/testlibrary /test/lib
 *          /test/hotspot/jtreg/runtime/cds/appcds
 *          /test/hotspot/jtreg/runtime/cds/appcds/test-classes
 * @build ArchiveHeapTestClass Hello pkg.ClassInPackage
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar boot.jar
 *             CDSTestClassA CDSTestClassA$XX CDSTestClassA$YY
 *             CDSTestClassB CDSTestClassC CDSTestClassD
 *             CDSTestClassE CDSTestClassF CDSTestClassG CDSTestClassG$MyEnum CDSTestClassG$Wrapper
 *             pkg.ClassInPackage
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar app.jar Hello
 * @run driver ArchiveHeapTestClass
 */

import jdk.test.lib.cds.CDSTestUtils;
import jdk.test.lib.Platform;
import jdk.test.lib.helpers.ClassFileInstaller;
import jdk.test.lib.process.OutputAnalyzer;

public class ArchiveHeapTestClass {
    static final String bootJar = ClassFileInstaller.getJarPath("boot.jar");
    static final String appJar = ClassFileInstaller.getJarPath("app.jar");
    static final String[] appClassList = {"Hello"};

    static final String CDSTestClassA_name = CDSTestClassA.class.getName();
    static final String CDSTestClassB_name = CDSTestClassB.class.getName();
    static final String CDSTestClassC_name = CDSTestClassC.class.getName();
    static final String CDSTestClassD_name = CDSTestClassD.class.getName();
    static final String CDSTestClassE_name = CDSTestClassE.class.getName();
    static final String CDSTestClassF_name = CDSTestClassF.class.getName();
    static final String CDSTestClassG_name = CDSTestClassG.class.getName();
    static final String ClassInPackage_name = pkg.ClassInPackage.class.getName().replace('.', '/');
    static final String ARCHIVE_TEST_FIELD_NAME = "archivedObjects";

    public static void main(String[] args) throws Exception {
        testDebugBuild();
    }

    static OutputAnalyzer dumpHelloOnly(String... extraOpts) throws Exception {
        return TestCommon.dump(appJar, appClassList, extraOpts);
    }

    static OutputAnalyzer dumpBootAndHello(String bootClass, String... extraOpts) throws Exception {
        String classlist[] = TestCommon.concat(appClassList, bootClass);
        extraOpts = TestCommon.concat(extraOpts,
                                      "-Xbootclasspath/a:" + bootJar,
                                      "-XX:ArchiveHeapTestClass=" + bootClass,
                                      "-Xlog:aot+heap");
        return TestCommon.dump(appJar, classlist, extraOpts);
    }

    static int caseNum = 0;
    static void testCase(String s) {
        System.out.println("==================================================");
        System.out.println(" Test " + (++caseNum) + ": " + s);
    }

    static void mustContain(OutputAnalyzer output, String... expectStrs) throws Exception {
        for (String s : expectStrs) {
            output.shouldContain(s);
        }
    }

    static void mustFail(OutputAnalyzer output, String... expectStrs) throws Exception {
        mustContain(output, expectStrs);
        output.shouldNotHaveExitValue(0);
    }

    static void mustSucceed(OutputAnalyzer output, String... expectStrs) throws Exception {
        mustContain(output, expectStrs);
        output.shouldHaveExitValue(0);
    }

    static void testDebugBuild() throws Exception {
        OutputAnalyzer output;

        testCase("Simple positive case");
        output = dumpBootAndHello(CDSTestClassA_name);
        mustSucceed(output, CDSTestClassA.getOutput()); // make sure <clinit> is executed
        output.shouldMatch("warning.*aot.*Loading ArchiveHeapTestClass " + CDSTestClassA_name);
        output.shouldMatch("warning.*aot.*Initializing ArchiveHeapTestClass " + CDSTestClassA_name);
        output.shouldContain("Archived field " + CDSTestClassA_name + "::" + ARCHIVE_TEST_FIELD_NAME);
        output.shouldMatch("Archived object klass CDSTestClassA .*\\[LCDSTestClassA;");
        output.shouldMatch("Archived object klass CDSTestClassA .*CDSTestClassA\\$YY");

        TestCommon.run("-Xbootclasspath/a:" + bootJar, "-cp", appJar, "-Xlog:aot+heap", CDSTestClassA_name)
            .assertNormalExit(CDSTestClassA.getOutput(),
                              "resolve subgraph " + CDSTestClassA_name);

        testCase("Class doesn't exist");
        output = dumpHelloOnly("-XX:ArchiveHeapTestClass=NoSuchClass");
        mustFail(output, "Fail to initialize archive heap: NoSuchClass cannot be loaded");

        testCase("Class doesn't exist (objarray)");
        output = dumpHelloOnly("-XX:ArchiveHeapTestClass=[LNoSuchClass;");
        mustFail(output, "Fail to initialize archive heap: [LNoSuchClass; cannot be loaded");

        testCase("Not an instance klass");
        output = dumpHelloOnly("-XX:ArchiveHeapTestClass=[Ljava/lang/Object;");
        mustFail(output, "Fail to initialize archive heap: [Ljava/lang/Object; is not an instance class");

        testCase("Not in boot loader");
        output = dumpHelloOnly("-XX:ArchiveHeapTestClass=Hello");
        mustFail(output, "Fail to initialize archive heap: Hello cannot be loaded by the boot loader");

        testCase("Not from unnamed module");
        output = dumpHelloOnly("-XX:ArchiveHeapTestClass=java/lang/Object");
        mustFail(output, "ArchiveHeapTestClass java/lang/Object is not in unnamed module");

        testCase("Not from unnamed package");
        output = dumpBootAndHello(ClassInPackage_name);
        mustFail(output, "ArchiveHeapTestClass pkg/ClassInPackage is not in unnamed package");

        testCase("Field not found");
        output = dumpBootAndHello(CDSTestClassB_name);
        mustFail(output, "Unable to find the static T_OBJECT field CDSTestClassB::archivedObjects");

        testCase("Not a static field");
        output = dumpBootAndHello(CDSTestClassC_name);
        mustFail(output, "Unable to find the static T_OBJECT field CDSTestClassC::archivedObjects");

        testCase("Not a T_OBJECT field");
        output = dumpBootAndHello(CDSTestClassD_name);
        mustFail(output, "Unable to find the static T_OBJECT field CDSTestClassD::archivedObjects");

        if (!CDSTestUtils.isAOTClassLinkingEnabled()) {
            testCase("Use a disallowed class: in unnamed module but not in unname package");
            output = dumpBootAndHello(CDSTestClassE_name);
            mustFail(output, "Class pkg.ClassInPackage not allowed in archive heap");

            testCase("Use a disallowed class: not in java.base module");
            output = dumpBootAndHello(CDSTestClassF_name);
            mustFail(output, "Class java.util.logging.Level not allowed in archive heap");
        }

        testCase("Complex enums");
        output = dumpBootAndHello(CDSTestClassG_name, "-XX:+AOTClassLinking", "-Xlog:cds+class=debug");
        mustSucceed(output);

        TestCommon.run("-Xbootclasspath/a:" + bootJar, "-cp", appJar, "-Xlog:aot+heap,cds+init",
                       CDSTestClassG_name)
            .assertNormalExit("init subgraph " + CDSTestClassG_name,
                              "Initialized from CDS");
    }
}

class CDSTestClassA {
    static final String output = "CDSTestClassA.<clinit> was executed";
    static Object[] archivedObjects;
    static {
        // The usual convention would be to call this here:
        //     CDS.initializeFromArchive(CDSTestClassA.class);
        // However, the CDS class is not exported to the unnamed module by default,
        // and we don't want to use "--add-exports java.base/jdk.internal.misc=ALL-UNNAMED", as
        // that would disable the archived full module graph, which will disable
        // CDSConfig::is_using_aot_linked_classes().
        //
        // Instead, HeapShared::initialize_test_class_from_archive() will set up the
        // "archivedObjects" field first, before calling CDSTestClassA.<clinit>. So
        // if we see that archivedObjects is magically non-null here, that means
        // it has been restored from the CDS archive.
        if (archivedObjects == null) {
            archivedObjects = new Object[5];
            archivedObjects[0] = output;
            archivedObjects[1] = new CDSTestClassA[0];
            archivedObjects[2] = new YY();
            archivedObjects[3] = new int[0];
            archivedObjects[4] = new int[2][2];
        } else {
            System.out.println("Initialized from CDS");
        }
        System.out.println(output);
        System.out.println("CDSTestClassA   module  = " + CDSTestClassA.class.getModule());
        System.out.println("CDSTestClassA   package = " + CDSTestClassA.class.getPackage());
        System.out.println("CDSTestClassA[] module  = " + archivedObjects[1].getClass().getModule());
        System.out.println("CDSTestClassA[] package = " + archivedObjects[1].getClass().getPackage());
    }

    static String getOutput() {
        return output;
    }

    public static void main(String args[]) {
        if (CDSTestClassA.class.getModule().isNamed()) {
            throw new RuntimeException("CDSTestClassA must be in unnamed module");
        }
        if (CDSTestClassA.class.getPackage() != null) {
            throw new RuntimeException("CDSTestClassA must be in null package");
        }
        if (archivedObjects[1].getClass().getModule().isNamed()) {
            throw new RuntimeException("CDSTestClassA[] must be in unnamed module");
        }
        if (archivedObjects[1].getClass().getPackage() != null) {
            throw new RuntimeException("CDSTestClassA[] must be in null package");
        }
        XX.doit();
        YY.doit();
    }

    // This is an inner class that has NOT been archived.
    static class XX {
        static void doit() {
            System.out.println("XX module  = " + XX.class.getModule());
            System.out.println("XX package = " + XX.class.getPackage());

            if (XX.class.getModule().isNamed()) {
                throw new RuntimeException("XX must be in unnamed module");
            }
            if (XX.class.getPackage() != null) {
                throw new RuntimeException("XX must be in null package");
            }
        }
    }

    // This is an inner class that HAS been archived.
    static class YY {
        static void doit() {
            System.out.println("YY module  = " + YY.class.getModule());
            System.out.println("YY package = " + YY.class.getPackage());

            if (YY.class.getModule().isNamed()) {
                throw new RuntimeException("YY must be in unnamed module");
            }
            if (YY.class.getPackage() != null) {
                throw new RuntimeException("YY must be in null package");
            }
        }
    }
}

class CDSTestClassB {
    // No field named "archivedObjects"
}

class CDSTestClassC {
    Object[] archivedObjects; // Not a static field
}

class CDSTestClassD {
    static int archivedObjects; // Not an int field
}

class CDSTestClassE {
    static Object[] archivedObjects;
    static {
        // Not in unnamed package of unnamed module
        archivedObjects = new Object[1];
        archivedObjects[0] = new pkg.ClassInPackage();
    }
}

class CDSTestClassF {
    static Object[] archivedObjects;
    static {
        // Not in java.base
        archivedObjects = new Object[1];
        archivedObjects[0] = java.util.logging.Level.OFF;
    }
}

class CDSTestClassG {
    static Object[] archivedObjects;
    static {
        if (archivedObjects == null) {
            archivedObjects = new Object[13];
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
        } else {
            System.out.println("Initialized from CDS");
        }
    }

    public static void main(String args[]) {
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
