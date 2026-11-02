/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test id=aot
 * @bug 8362566
 * @summary Test the contents of -Xlog:aot+map with AOT workflow
 * @requires vm.cds.supports.aot.class.linking
 * @library /test/lib /test/hotspot/jtreg/runtime/cds /test/hotspot/jtreg/runtime/cds/appcds/test-classes
 * @modules java.base/jdk.internal.misc
 * @build AOTMapTest Hello
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar app.jar AOTMapTestApp
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar cust.jar Hello
 * @run driver/timeout=240 AOTMapTest AOT --two-step-training
 */

/**
 * @test id=dynamic
 * @bug 8362566
 * @summary Test the contents of -Xlog:aot+map with dynamic CDS archive
 * @requires vm.cds.supports.aot.class.linking
 * @library /test/lib /test/hotspot/jtreg/runtime/cds /test/hotspot/jtreg/runtime/cds/appcds/test-classes
 * @modules java.base/jdk.internal.misc
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @build AOTMapTest Hello
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar app.jar AOTMapTestApp
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar cust.jar Hello
 * @run main/othervm/timeout=240 -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:. AOTMapTest DYNAMIC
 */

/**
 * @test id=valhalla
 * @bug 8362566
 * @summary Test the contents of -Xlog:aot+map with AOT workflow and flat arrays
 * @enablePreview
 * @requires vm.cds.supports.aot.class.linking & vm.debug & vm.cds.write.archived.java.heap
 * @library /test/lib /test/hotspot/jtreg/runtime/cds /test/hotspot/jtreg/runtime/cds/appcds/test-classes
 * @modules java.base/jdk.internal.value java.base/jdk.internal.misc java.base/jdk.internal.vm.annotation
 * @build Hello AOTMapTest
 * @compile test-classes/AOTMapTestValhallaHelper.java
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar app.jar
 *                 AOTMapTestApp
 *                 Hello
 *                 AOTMapTestValhallaHelper
 *                 AOTMapTestValhallaHelper$Wrapper
 *                 AOTMapTestValhallaHelper$WrapperWrapper
 *                 AOTMapTestValhallaHelper$ArchivedData
 * @run main/othervm/timeout=240 AOTMapTest AOT --two-step-training Valhalla
 */

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jdk.test.lib.cds.CDSAppTester;
import jdk.test.lib.helpers.ClassFileInstaller;
import jdk.test.lib.Platform;

public class AOTMapTest {
    static final String appJar = ClassFileInstaller.getJarPath("app.jar");
    static final String mainClass = "AOTMapTestApp";
    static final String classLoadLogFile = "production.class.load.log";
    static boolean testValhalla;
    public static void main(String[] args) throws Exception {
        testValhalla = args.length >= 3 && args[2].equals("Valhalla");
        Tester tester = new Tester();
        tester.run(args);

        if (tester.isDynamicWorkflow()) {
            // For dynamic workflow, the AOT map file doesn't include classes in the base archive, so
            // AOTMapReader.validateClasses() will fail.
            validate(tester.dumpMapFile, false);
        } else {
            validate(tester.dumpMapFile, true);
        }
        validate(tester.runMapFile, true);
    }

    static void validate(String mapFileName, boolean checkClases) throws Exception {
        AOTMapReader.MapFile mapFile = AOTMapReader.read(mapFileName);
        if (checkClases) {
            AOTMapReader.validate(mapFile, classLoadLogFile);
        } else {
            AOTMapReader.validate(mapFile, null);
        }
        mapFile.shouldHaveClass("AOTMapTestApp"); // built-in class
        mapFile.shouldHaveClass("Hello"); // unregistered class


        if (testValhalla) {
            validateValhalla(mapFile);
        }
    }

    static void validateValhalla(AOTMapReader.MapFile mapFile) {
        checkArchivedData(mapFile);
        checkWrapperArray(mapFile);
        checkWrapperArray(mapFile);
    }

    static void checkArchivedData(AOTMapReader.MapFile mapFile) {
        /* Should have something like this
         *
         * 0x000000008816dda8: @@ Object (0x0102dbb5) AOTMapTestValhallaHelper$ArchivedData
         *  - klass: 'AOTMapTestValhallaHelper$ArchivedData' 0x0000000800525c00
         *  - fields (7 words):
         *    - Flat inline type field 'AOTMapTestValhallaHelper$Wrapper':
         *      - Flat inline type field 'java/lang/Integer':
         *        - private final value 'value' (fields 0x00000000) 'I' @8  -1431677611 (0xaaaa5555)
         *        - [null_marker] @12 Field marked as non-null
         *      - [null_marker] @13 Field marked as non-null
         *    - Flat inline null-free type field 'AOTMapTestValhallaHelper$WrapperWrapper':
         *      - Flat inline type field 'AOTMapTestValhallaHelper$Wrapper':
         *        - Flat inline type field 'java/lang/Integer':
         *          - private final value 'value' (fields 0x00000000) 'I' @16  -1145346458 (0xbbbb6666)
         *          - [null_marker] @20 Field marked as non-null
         *        - [null_marker] @21 Field marked as non-null
         * ....
         */
        String s = getExactlyOneHeapObject(mapFile, "AOTMapTestValhallaHelper$ArchivedData");
        System.out.println(s);
        checkMatch(s, "final value 'value' .fields 0x00000000. 'I' @[0-9]+ +-1431677611 .0xaaaa5555.");
        checkMatch(s, "final value 'value' .fields 0x00000000. 'I' @[0-9]+ +-1145346458 .0xbbbb6666.");

        Pattern pattern = Pattern.compile("Flat value type field 'java/lang/Integer'.*\n" +
                                          ".*final value 'value'.*@([0-9]+).*\n" +
                                          ".*null_marker.*@([0-9]+)");
        Matcher matcher = pattern.matcher(s);

        if (!matcher.find()) {
            throw new RuntimeException("Pattern " + pattern + " not found in output: " + s);
        }

        int value_offset = Integer.parseInt(matcher.group(1));
        int marker_offset = Integer.parseInt(matcher.group(2));
        if (marker_offset != value_offset + 4) {
            throw new RuntimeException("marker offset (" + marker_offset + ") should be value_offset (" + value_offset + ") + 4");
        }
    }

    static void checkWrapperArray(AOTMapReader.MapFile mapFile) {
        /* Should have something like this
         *
         * 0x00000000881ada98: @@ Object (0x01035b53) [LAOTMapTestValhallaHelper$Wrapper; length: 3
         *  - klass: 'AOTMapTestValhallaHelper$Wrapper'[] 0x000000080052a400
         *  - Flat value type element 'AOTMapTestValhallaHelper$Wrapper': - Index   0 offset  16:
         *  - Flat value type field 'java/lang/Integer':
         *    - private final value 'value' (fields 0x00000000) 'I' @16  43690 (0x0000aaaa)
         *    - [null_marker] @20 Field marked as non-null
         *    - [null_marker] @21 Element marked as non-null
         */
        String s = getExactlyOneHeapObject(mapFile, "[LAOTMapTestValhallaHelper$Wrapper;");
        System.out.println(s);

        Pattern pattern = Pattern.compile("Flat value type field 'java/lang/Integer'.*\n" +
                                          ".*final value 'value'.*@([0-9]+).*\n" +
                                          ".*null_marker.*@([0-9]+) Field.*\n" +
                                          ".*null_marker.*@([0-9]+) Element");
        Matcher matcher = pattern.matcher(s);

        int found = 0;
        while (matcher.find()) {
            int value_offset = Integer.parseInt(matcher.group(1));
            int field_marker_offset = Integer.parseInt(matcher.group(2));
            int element_marker_offset = Integer.parseInt(matcher.group(3));
            if (field_marker_offset != value_offset + 4) {
                throw new RuntimeException("marker offset (" + field_marker_offset + ") should be value_offset (" + value_offset + ") + 4");
            }
            if (element_marker_offset != value_offset + 5) {
                throw new RuntimeException("marker offset (" + element_marker_offset + ") should be value_offset (" + value_offset + ") + 5");
            }
            found ++;
        }

        if (found != 3) {
            throw new RuntimeException("Expected 3 pairs of null markers but found " + found);
        }
    }

    static String getExactlyOneHeapObject(AOTMapReader.MapFile mapFile, String klass) {
        String[] objs = mapFile.getHeapObjectsOfType(klass);
        if (objs == null || objs.length != 1) {
            throw new RuntimeException("Expected exactly one heap object of type " + klass + " but got " +
                                       ((objs == null) ? "none" : ("" + objs.length)));
        }
        return objs[0];
    }

    static void checkMatch(String s, String regexp) {
        if (!s.matches("(?s).*" + regexp + ".*")) { // (?s) enables DOTALL mode
            System.err.println("Found:\n" + s);
            throw new RuntimeException("String does not match " + regexp);
        }
    }


    static class Tester extends CDSAppTester {
        String dumpMapFile;
        String runMapFile;

        public Tester() {
            super(mainClass);

            dumpMapFile = "test" + "0" + ".dump.aotmap";
            runMapFile  = "test" + "0" + ".run.aotmap";
        }

        @Override
        public String classpath(RunMode runMode) {
            return appJar;
        }

        @Override
        public String[] vmArgs(RunMode runMode) {
            ArrayList<String> vmArgs = new ArrayList<>();

            vmArgs.add("-Xmx128M");
            vmArgs.add("-Xlog:aot=debug");
            vmArgs.add("--add-exports");
            vmArgs.add("java.base/jdk.internal.misc=ALL-UNNAMED");

            if (testValhalla) {
                vmArgs.add("--enable-preview");
                vmArgs.add("--add-exports");
                vmArgs.add("java.base/jdk.internal.value=ALL-UNNAMED");

                if (runMode == RunMode.ASSEMBLY) {
                    vmArgs.add("-XX:AOTInitTestClass=AOTMapTestValhallaHelper");
                }
            }

            // filesize=0 ensures that a large map file not broken up in multiple files.
            String logMapPrefix = "-Xlog:aot+map=trace,aot+map+oops=trace:file=";
            String logSuffix = ":none:filesize=0";

            if (runMode == RunMode.ASSEMBLY || runMode == RunMode.DUMP_DYNAMIC || runMode == RunMode.DUMP_STATIC) {
                vmArgs.add(logMapPrefix + dumpMapFile + logSuffix);
            } else if (runMode == RunMode.PRODUCTION) {
                vmArgs.add(logMapPrefix + runMapFile + logSuffix);
                vmArgs.add("-Xlog:class+load:file=" + classLoadLogFile + logSuffix);
            }

            return vmArgs.toArray(new String[vmArgs.size()]);
        }

        @Override
        public String[] appCommandLine(RunMode runMode) {
            return new String[] {
                mainClass,
                testValhalla ? "Valhalla" : "none"
            };
        }
    }
}

class AOTMapTestApp {
    static URLClassLoader loader; // keep Hello class alive
    public static void main(String[] args) throws Exception {
        System.out.println("Hello AOTMapTestApp");
        testCustomLoader();

        if (args[0].equals("Valhalla")) {
            Class<?> c = Class.forName("AOTMapTestValhallaHelper");
            Object o = c.newInstance();
            System.out.println(o);
        }
    }

    static void testCustomLoader() throws Exception {
        File custJar = new File("cust.jar");
        URL[] urls = new URL[] {custJar.toURI().toURL()};
        loader = new URLClassLoader(urls, AOTMapTestApp.class.getClassLoader());
        Class<?> c = loader.loadClass("Hello");
        System.out.println(c);
    }
}
