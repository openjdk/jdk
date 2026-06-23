/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @test TestIdealGraphDump
 * @bug 8370870
 * @summary Verify that IGV graph dumping produces well-structured XML at different print levels
 * @library /test/lib
 * @requires vm.debug == true & vm.compiler2.enabled
 * @run driver compiler.igv.TestIdealGraphDump
 */

package compiler.igv;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import jdk.test.lib.Asserts;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class TestIdealGraphDump {

    private static final String TEST_CLASS = TestMethods.class.getName();
    private static final String METHOD_COMPUTE = TEST_CLASS + "::compute";
    private static final String METHOD_BRANCH = TEST_CLASS + "::branchyMethod";

    public static void main(String[] args) throws Exception {
        testDisabled();
        testLevel0();
        testLevel1();
        testLevel2();
        testLevel3();
        testLevel4();
        testLevel5();
        testLevel6();
        testLevelSpecificPhases();
        testMonotonicallyIncreasingGraphCounts();
        testXmlWellFormedness();
        testMethodNameInGraph();
        testMultipleMethods();
        testIGVPrintLevelDirective();
    }

    private static void testDisabled() throws Exception {
        Path xmlFile = dumpAtLevel(-1);
        Asserts.assertTrue(Files.size(xmlFile) == 0,
            "Level -1 (disabled) must produce an empty file");
        System.out.println("testDisabled PASSED");
    }

    private static void testLevel0() throws Exception {
        Path xmlFile = dumpAtLevel(0);
        Asserts.assertTrue(Files.size(xmlFile) == 0,
            "Level 0 must produce an empty file (no system-wide dumps)");
        System.out.println("testLevel0 PASSED");
    }

    private static void testLevel1() throws Exception {
        Path xmlFile = dumpAtLevel(1);
        String content = Files.readString(xmlFile);
        assertContainsPhase(content, "After Parsing", 1);
        assertContainsPhase(content, "Final Code", 1);
        System.out.println("testLevel1 PASSED: " + countGraphs(content) + " graph(s)");
    }

    private static void testLevel2() throws Exception {
        Path xmlFile = dumpAtLevel(2);
        String content = Files.readString(xmlFile);
        assertContainsPhase(content, "After Parsing", 2);
        assertContainsPhase(content, "Final Code", 2);
        assertContainsPhase(content, "After Iter GVN 1", 2);
        assertContainsPhase(content, "PhaseCCP 1", 2);
        System.out.println("testLevel2 PASSED: " + countGraphs(content) + " graph(s)");
    }

    private static void testLevel3() throws Exception {
        Path xmlFile = dumpAtLevel(3);
        String content = Files.readString(xmlFile);
        assertContainsPhase(content, "Before Macro Expansion", 3);
        System.out.println("testLevel3 PASSED: " + countGraphs(content) + " graph(s)");
    }

    private static void testLevel4() throws Exception {
        Path xmlFile = dumpAtLevel(4);
        String content = Files.readString(xmlFile);
        int count = countGraphs(content);
        Asserts.assertTrue(count > 0, "Level 4 must produce graphs");
        System.out.println("testLevel4 PASSED: " + count + " graph(s)");
    }

    private static void testLevel5() throws Exception {
        Path xmlFile = dumpAtLevel(5);
        String content = Files.readString(xmlFile);
        assertContainsPhase(content, "After Iter GVN Step", 5);
        System.out.println("testLevel5 PASSED: " + countGraphs(content) + " graph(s)");
    }

    private static void testLevel6() throws Exception {
        Path xmlFile = dumpAtLevel(6);
        String content = Files.readString(xmlFile);
        int count = countGraphs(content);
        Asserts.assertTrue(count > 0, "Level 6 must produce graphs");
        System.out.println("testLevel6 PASSED: " + count + " graph(s)");
    }

    private static void testLevelSpecificPhases() throws Exception {
        Path xmlLevel1 = dumpAtLevel(1);
        Path xmlLevel2 = dumpAtLevel(2);
        Path xmlLevel5 = dumpAtLevel(5);

        String content1 = Files.readString(xmlLevel1);
        String content2 = Files.readString(xmlLevel2);
        String content5 = Files.readString(xmlLevel5);

        Asserts.assertTrue(containsPhase(content1, "Final Code"),
            "Level 1 must contain 'Final Code' phase");
        Asserts.assertFalse(containsPhase(content1, "PhaseCCP 1"),
            "Level 1 must NOT contain 'PhaseCCP 1' (requires level 2+)");
        Asserts.assertFalse(containsPhase(content1, "After Iter GVN Step"),
            "Level 1 must NOT contain 'After Iter GVN Step' (requires level 5+)");

        Asserts.assertTrue(containsPhase(content2, "PhaseCCP 1"),
            "Level 2 must contain 'PhaseCCP 1'");
        Asserts.assertFalse(containsPhase(content2, "After Iter GVN Step"),
            "Level 2 must NOT contain 'After Iter GVN Step' (requires level 5+)");

        Asserts.assertTrue(containsPhase(content5, "After Iter GVN Step"),
            "Level 5 must contain 'After Iter GVN Step'");

        System.out.println("testLevelSpecificPhases PASSED");
    }

    private static void testMonotonicallyIncreasingGraphCounts() throws Exception {
        int prevCount = 0;
        for (int level = 1; level <= 6; level++) {
            Path xmlFile = dumpAtLevel(level);
            String content = Files.readString(xmlFile);
            int count = countGraphs(content);
            Asserts.assertTrue(count >= prevCount,
                "Level " + level + " (" + count + " graphs) must have at least as many as level " +
                (level - 1) + " (" + prevCount + " graphs)");
            prevCount = count;
        }
        System.out.println("testMonotonicallyIncreasingGraphCounts PASSED");
    }

    private static void testXmlWellFormedness() throws Exception {
        Path xmlFile = dumpAtLevel(2);

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        try {
            builder.parse(xmlFile.toFile());
        } catch (Exception e) {
            Asserts.fail("IGV XML at level 2 is not well-formed: " + e.getMessage());
        }

        String content = Files.readString(xmlFile);
        Asserts.assertTrue(content.contains("<graphDocument>"), "Must contain <graphDocument>");
        Asserts.assertTrue(content.contains("</graphDocument>"), "Must contain closing </graphDocument>");
        Asserts.assertTrue(content.contains("<properties>"), "Must contain <properties>");
        Asserts.assertTrue(content.contains("<nodes>"), "Must contain <nodes>");
        Asserts.assertTrue(content.contains("<edges>"), "Must contain <edges>");
        Asserts.assertTrue(content.contains("<node id="), "Must contain <node> elements");
        Asserts.assertTrue(content.contains("<method name="), "Must contain <method>");
        Asserts.assertTrue(content.contains("<bytecodes>"), "Must contain <bytecodes>");
        Asserts.assertTrue(content.contains("<controlFlow>"), "Must contain <controlFlow>");

        System.out.println("testXmlWellFormedness PASSED");
    }

    private static void testMethodNameInGraph() throws Exception {
        Path xmlFile = dumpAtLevel(1);
        String content = Files.readString(xmlFile);

        Asserts.assertTrue(content.contains("TestMethods.compute"),
            "Graph output must contain the compiled method name 'TestMethods.compute'");

        System.out.println("testMethodNameInGraph PASSED");
    }

    private static void testMultipleMethods() throws Exception {
        Path xmlFile = dumpMultipleMethods(2);
        String content = Files.readString(xmlFile);

        Asserts.assertTrue(content.contains("TestMethods.compute"),
            "Must contain graphs for 'compute' method");
        Asserts.assertTrue(content.contains("TestMethods.branchyMethod"),
            "Must contain graphs for 'branchyMethod' method");

        int groupCount = countOccurrences(content, "<group>");
        Asserts.assertTrue(groupCount >= 2,
            "Must have at least 2 method groups in output, got " + groupCount);

        System.out.println("testMultipleMethods PASSED: " + groupCount + " method group(s)");
    }

    private static void testIGVPrintLevelDirective() throws Exception {
        Path xmlFile = Files.createTempFile("igv_directive_", ".xml");
        xmlFile.toFile().deleteOnExit();

        List<String> options = new ArrayList<>();
        options.add("-Xbatch");
        options.add("-XX:PrintIdealGraphLevel=0");
        options.add("-XX:PrintIdealGraphFile=" + xmlFile.toAbsolutePath());
        options.add("-XX:CompileCommand=option," + METHOD_COMPUTE + ",IGVPrintLevel,2");
        options.add("-XX:CompileCommand=compileonly," + METHOD_COMPUTE);
        options.add(TEST_CLASS);

        OutputAnalyzer oa = ProcessTools.executeTestJava(options);
        oa.shouldHaveExitValue(0);
        oa.shouldNotContain("# A fatal error has been detected by the Java Runtime Environment");

        String content = Files.readString(xmlFile);
        Asserts.assertTrue(Files.size(xmlFile) > 0,
            "Per-method IGVPrintLevel directive must produce output even with system level 0");
        Asserts.assertTrue(content.contains("TestMethods.compute"),
            "Directive-based dump must contain the target method");
        assertContainsPhase(content, "After Parsing", 2);

        System.out.println("testIGVPrintLevelDirective PASSED");
    }

    private static Path dumpAtLevel(int level) throws Exception {
        Path xmlFile = Files.createTempFile("igv_level" + level + "_", ".xml");
        xmlFile.toFile().deleteOnExit();

        List<String> options = new ArrayList<>();
        options.add("-Xbatch");
        options.add("-XX:PrintIdealGraphLevel=" + level);
        options.add("-XX:PrintIdealGraphFile=" + xmlFile.toAbsolutePath());
        options.add("-XX:CompileCommand=compileonly," + METHOD_COMPUTE);
        options.add(TEST_CLASS);

        OutputAnalyzer oa = ProcessTools.executeTestJava(options);
        oa.shouldHaveExitValue(0);
        oa.shouldNotContain("# A fatal error has been detected by the Java Runtime Environment");

        return xmlFile;
    }

    private static Path dumpMultipleMethods(int level) throws Exception {
        Path xmlFile = Files.createTempFile("igv_multi_", ".xml");
        xmlFile.toFile().deleteOnExit();

        List<String> options = new ArrayList<>();
        options.add("-Xbatch");
        options.add("-XX:PrintIdealGraphLevel=" + level);
        options.add("-XX:PrintIdealGraphFile=" + xmlFile.toAbsolutePath());
        options.add("-XX:CompileCommand=compileonly," + METHOD_COMPUTE);
        options.add("-XX:CompileCommand=compileonly," + METHOD_BRANCH);
        options.add(TEST_CLASS);

        OutputAnalyzer oa = ProcessTools.executeTestJava(options);
        oa.shouldHaveExitValue(0);
        oa.shouldNotContain("# A fatal error has been detected by the Java Runtime Environment");

        return xmlFile;
    }

    private static int countGraphs(String content) {
        return countOccurrences(content, "<graph name=");
    }

    private static boolean containsPhase(String content, String phaseName) {
        return content.contains("'" + phaseName + "'") ||
               content.contains("\"" + phaseName + "\"") ||
               content.contains(">" + phaseName + "<");
    }

    private static void assertContainsPhase(String content, String phaseName, int level) {
        Asserts.assertTrue(containsPhase(content, phaseName),
            "Level " + level + " must contain phase '" + phaseName + "'");
    }

    private static int countOccurrences(String str, String sub) {
        int count = 0;
        int idx = 0;
        while ((idx = str.indexOf(sub, idx)) != -1) {
            count++;
            idx += sub.length();
        }
        return count;
    }

    public static class TestMethods {
        public static void main(String[] args) {
            int sum = 0;
            for (int i = 0; i < 20_000; i++) {
                sum += compute(i, i + 1);
                sum += branchyMethod(i, i % 7);
            }
            System.out.println(sum);
        }

        static int compute(int a, int b) {
            int result = 0;
            for (int i = 0; i < a % 10; i++) {
                result += b * i;
            }
            return result;
        }

        static int branchyMethod(int x, int y) {
            if (x > y) {
                return x * y + 1;
            } else if (x == y) {
                return x + y;
            } else {
                return y - x;
            }
        }
    }
}
