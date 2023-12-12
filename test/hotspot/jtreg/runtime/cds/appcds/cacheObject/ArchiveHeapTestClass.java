/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @modules java.base/sun.invoke.util java.logging
 * @library /test/jdk/lib/testlibrary /test/lib
 *          /test/hotspot/jtreg/runtime/cds/appcds
 *          /test/hotspot/jtreg/runtime/cds/appcds/test-classes
 * @build ArchiveHeapTestClass Hello pkg.ClassInPackage
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar boot.jar
 *             CDSTestClassA CDSTestClassA$XX CDSTestClassA$YY
 *             CDSTestClassB CDSTestClassC CDSTestClassD
 *             CDSTestClassE CDSTestClassF CDSTestClassG
 *             pkg.ClassInPackage
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar app.jar Hello
 * @run driver ArchiveHeapTestClass
 */

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
                                      "-Xlog:cds+heap");
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
        output.shouldMatch("warning.*cds.*Loading ArchiveHeapTestClass " + CDSTestClassA_name);
        output.shouldMatch("warning.*cds.*Initializing ArchiveHeapTestClass " + CDSTestClassA_name);
        output.shouldContain("Archived field " + CDSTestClassA_name + "::" + ARCHIVE_TEST_FIELD_NAME);
        output.shouldMatch("Archived object klass CDSTestClassA .*\\[LCDSTestClassA;");
        output.shouldMatch("Archived object klass CDSTestClassA .*CDSTestClassA\\$YY");

        TestCommon.run("-Xbootclasspath/a:" + bootJar, "-cp", appJar, "-Xlog:cds+heap", CDSTestClassA_name)
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

        testCase("Use a disallowed class: in unnamed module but not in unname package");
        output = dumpBootAndHello(CDSTestClassE_name);
        mustFail(output, "Class pkg.ClassInPackage not allowed in archive heap");

        testCase("Use a disallowed class: not in java.base module");
        output = dumpBootAndHello(CDSTestClassF_name);
        mustFail(output, "Class java.util.logging.Level not allowed in archive heap");

        if (false) { // JDK-8293187
            testCase("sun.invoke.util.Wrapper");
            output = dumpBootAndHello(CDSTestClassG_name);
            mustSucceed(output);
        }
    }
}

class CDSTestClassA {
    static final String output = "CDSTestClassA.<clinit> was executed";
    static Object[] archivedObjects;
    static {
        archivedObjects = new Object[5];
        archivedObjects[0] = output;
        archivedObjects[1] = new CDSTestClassA[0];
        archivedObjects[2] = new YY();
        archivedObjects[3] = new int[0];
        archivedObjects[4] = new int[2][2];
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
        // Not in java.base
        archivedObjects = new Object[1];
        archivedObjects[0] = sun.invoke.util.Wrapper.BOOLEAN;
    }
}
