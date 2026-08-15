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
 * @requires vm.debug == true & vm.compiler2.enabled & vm.flagless
 * @run driver ${test.main.class}
 */

package compiler.igv;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import jdk.test.lib.Asserts;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class TestIdealGraphDump {

    private static final String TEST_CLASS = TestMethods.class.getName();
    private static final String METHOD_COMPUTE = TEST_CLASS + "::compute";
    private static final String METHOD_BRANCH = TEST_CLASS + "::branchyMethod";

    private static final Map<Integer, Path> dumpCache = new HashMap<>();

    public static void main(String[] args) throws Exception {
        testDisabled();
        testLevel0();
        testLevel1();
        testLevel2();
        testLevel3();
        testLevel4();
        testLevel5();
        testLevel6();
        testMonotonicallyIncreasingGraphCounts();
        testXmlWellFormedness();
        testMethodNameInGraph();
        testMultipleMethods();
        testIGVPrintLevelDirective();
    }

    private static void testDisabled() throws Exception {
        Path xmlFile = getCachedDump(-1);
        Asserts.assertTrue(Files.size(xmlFile) == 0,
            "Level -1 (disabled) must produce an empty file");
    }

    private static void testLevel0() throws Exception {
        Path xmlFile = getCachedDump(0);
        Asserts.assertTrue(Files.size(xmlFile) == 0,
            "Level 0 must produce an empty file (no system-wide dumps)");
    }

    private static void testLevel1() throws Exception {
        String content = getCachedContent(1);
        assertContainsPhase(content, "After Parsing", 1);
        assertContainsPhase(content, "Before Matching", 1);
        assertContainsPhase(content, "Final Code", 1);
        assertNotContainsPhase(content, "PhaseCCP 1", 1);
    }

    private static void testLevel2() throws Exception {
        String content = getCachedContent(2);
        assertContainsPhase(content, "After Parsing", 2);
        assertContainsPhase(content, "Final Code", 2);
        assertContainsPhase(content, "Iter GVN 1", 2);
        assertContainsPhase(content, "PhaseCCP 1", 2);
        assertNotContainsPhase(content, "Before Macro Expansion", 2);
    }

    private static void testLevel3() throws Exception {
        String content = getCachedContent(3);
        assertContainsPhase(content, "Before Macro Expansion", 3);
        assertNotContainsPhase(content, "Initial Liveness", 3);
    }

    private static void testLevel4() throws Exception {
        String content = getCachedContent(4);
        assertContainsPhase(content, "Initial Liveness", 4);
        assertNotContainsPhase(content, "After Iter GVN Step", 4);
    }

    private static void testLevel5() throws Exception {
        String content = getCachedContent(5);
        assertContainsPhase(content, "After Iter GVN Step", 5);
        assertNotContainsPhase(content, "Bytecode", 5);
    }

    private static void testLevel6() throws Exception {
        String content = getCachedContent(6);
        Asserts.assertTrue(containsPhase(content, "Bytecode"),
            "Level 6 must contain per-bytecode graphs (e.g., 'Bytecode 0: ...')");
    }

    private static void testMonotonicallyIncreasingGraphCounts() throws Exception {
        int prevCount = 0;
        for (int level = 1; level <= 6; level++) {
            String content = getCachedContent(level);
            int count = countGraphs(content);
            Asserts.assertTrue(count >= prevCount,
                "Level " + level + " (" + count + " graphs) must have at least as many as level " +
                (level - 1) + " (" + prevCount + " graphs)");
            prevCount = count;
        }
    }

    private static void testXmlWellFormedness() throws Exception {
        Path xmlFile = getCachedDump(2);

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        try {
            builder.parse(xmlFile.toFile());
        } catch (Exception e) {
            Asserts.fail("IGV XML at level 2 is not well-formed: " + e.getMessage());
        }

        String content = getCachedContent(2);
        Asserts.assertTrue(content.contains("<graphDocument>"), "Must contain <graphDocument>");
        Asserts.assertTrue(content.contains("</graphDocument>"), "Must contain closing </graphDocument>");
        Asserts.assertTrue(content.contains("<properties>"), "Must contain <properties>");
        Asserts.assertTrue(content.contains("<nodes>"), "Must contain <nodes>");
        Asserts.assertTrue(content.contains("<edges>"), "Must contain <edges>");
        Asserts.assertTrue(content.contains("<node id="), "Must contain <node> elements");
        Asserts.assertTrue(content.contains("<method name="), "Must contain <method>");
        Asserts.assertTrue(content.contains("<bytecodes>"), "Must contain <bytecodes>");
        Asserts.assertTrue(content.contains("<controlFlow>"), "Must contain <controlFlow>");
    }

    private static void testMethodNameInGraph() throws Exception {
        String content = getCachedContent(1);
        Asserts.assertTrue(content.contains("TestMethods.compute"),
            "Graph output must contain the compiled method name 'TestMethods.compute'");
    }

    private static void testMultipleMethods() throws Exception {
        Path xmlFile = dumpMultipleMethods(1);
        String content = Files.readString(xmlFile);

        Asserts.assertTrue(content.contains("TestMethods.compute"),
            "Must contain graphs for 'compute' method");
        Asserts.assertTrue(content.contains("TestMethods.branchyMethod"),
            "Must contain graphs for 'branchyMethod' method");

        int computeFinalCode = countMethodPhase(content, "TestMethods.compute", "Final Code");
        int branchFinalCode = countMethodPhase(content, "TestMethods.branchyMethod", "Final Code");
        Asserts.assertEquals(computeFinalCode, 1,
            "compute must emit exactly one 'Final Code' graph, got " + computeFinalCode);
        Asserts.assertEquals(branchFinalCode, 1,
            "branchyMethod must emit exactly one 'Final Code' graph, got " + branchFinalCode);
    }

    private static void testIGVPrintLevelDirective() throws Exception {
        Path xmlFile = Files.createTempFile("igv_directive_", ".xml");
        xmlFile.toFile().deleteOnExit();

        List<String> options = new ArrayList<>();
        options.add("-Xbatch");
        options.add("-XX:PrintIdealGraphLevel=0");
        options.add("-XX:PrintIdealGraphFile=" + xmlFile.toAbsolutePath());
        options.add("-XX:CompileCommand=IGVPrintLevel," + METHOD_COMPUTE + ",2");
        options.add(TEST_CLASS);

        OutputAnalyzer oa = ProcessTools.executeTestJava(options);
        oa.shouldHaveExitValue(0);
        oa.shouldNotContain("# A fatal error has been detected by the Java Runtime Environment");

        String content = Files.readString(xmlFile);
        Asserts.assertTrue(Files.size(xmlFile) > 0,
            "Per-method IGVPrintLevel directive must produce output even with system level 0");
        Asserts.assertTrue(content.contains("TestMethods.compute"),
            "Directive-based dump must contain the target method");
        Asserts.assertFalse(content.contains("TestMethods.branchyMethod"),
            "Directive-based dump must NOT contain non-targeted method");
        assertContainsPhase(content, "After Parsing", 2);
    }

    private static Path getCachedDump(int level) throws Exception {
        if (!dumpCache.containsKey(level)) {
            dumpCache.put(level, dumpAtLevel(level));
        }
        return dumpCache.get(level);
    }

    private static String getCachedContent(int level) throws Exception {
        return Files.readString(getCachedDump(level));
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
               content.contains(">" + phaseName + "<") ||
               content.contains("'" + phaseName);
    }

    private static void assertContainsPhase(String content, String phaseName, int level) {
        Asserts.assertTrue(containsPhase(content, phaseName),
            "Level " + level + " must contain phase '" + phaseName + "'");
    }

    private static void assertNotContainsPhase(String content, String phaseName, int level) {
        Asserts.assertFalse(containsPhase(content, phaseName),
            "Level " + level + " must NOT contain phase '" + phaseName + "'");
    }

    private static int countMethodPhase(String content, String methodName, String phaseName) {
        int count = 0;
        int groupStart = 0;
        while ((groupStart = content.indexOf("<group>", groupStart)) != -1) {
            int groupEnd = content.indexOf("</group>", groupStart);
            if (groupEnd == -1) {
                break;
            }
            String group = content.substring(groupStart, groupEnd);
            if (group.contains(methodName)) {
                count += countOccurrences(group, "<graph name='" + phaseName + "'>");
            }
            groupStart = groupEnd;
        }
        return count;
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
