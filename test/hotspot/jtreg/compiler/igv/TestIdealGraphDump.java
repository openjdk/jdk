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

import jdk.test.lib.Asserts;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class TestIdealGraphDump {

    public static void main(String[] args) throws Exception {
        testLevel1();
        testLevel2();
        testHigherLevelHasMorePhases();
        testXmlStructure();
    }

    private static void testLevel1() throws Exception {
        Path xmlFile = dumpAtLevel(1);
        String content = Files.readString(xmlFile);

        Asserts.assertTrue(content.contains("<graphDocument>"), "Must contain <graphDocument> root element");
        Asserts.assertTrue(content.contains("</graphDocument>"), "Must contain closing </graphDocument>");
        Asserts.assertTrue(content.contains("<group>"), "Must contain <group> element");
        Asserts.assertTrue(content.contains("<graph name="), "Must contain at least one <graph> element");

        int graphCount = countOccurrences(content, "<graph name=");
        Asserts.assertTrue(graphCount >= 1, "Level 1 must produce at least 1 graph dump, got " + graphCount);

        System.out.println("testLevel1 PASSED: " + graphCount + " graph(s) at level 1");
    }

    private static void testLevel2() throws Exception {
        Path xmlFile = dumpAtLevel(2);
        String content = Files.readString(xmlFile);

        Asserts.assertTrue(content.contains("<graphDocument>"), "Must contain <graphDocument>");
        Asserts.assertTrue(content.contains("<graph name="), "Must contain at least one <graph>");

        int graphCount = countOccurrences(content, "<graph name=");
        Asserts.assertTrue(graphCount >= 1, "Level 2 must produce at least 1 graph dump, got " + graphCount);

        System.out.println("testLevel2 PASSED: " + graphCount + " graph(s) at level 2");
    }

    private static void testHigherLevelHasMorePhases() throws Exception {
        Path xmlLevel1 = dumpAtLevel(1);
        Path xmlLevel4 = dumpAtLevel(4);

        String content1 = Files.readString(xmlLevel1);
        String content4 = Files.readString(xmlLevel4);

        int count1 = countOccurrences(content1, "<graph name=");
        int count4 = countOccurrences(content4, "<graph name=");

        Asserts.assertTrue(count4 >= count1,
            "Higher print level must produce at least as many graphs: level 1 had " + count1 +
            ", level 4 had " + count4);

        System.out.println("testHigherLevelHasMorePhases PASSED: level 1=" + count1 + ", level 4=" + count4);
    }

    private static void testXmlStructure() throws Exception {
        Path xmlFile = dumpAtLevel(2);
        String content = Files.readString(xmlFile);

        Asserts.assertTrue(content.contains("<properties>"), "Must contain <properties> element");
        Asserts.assertTrue(content.contains("<nodes>"), "Must contain <nodes> element");
        Asserts.assertTrue(content.contains("<edges>"), "Must contain <edges> element");
        Asserts.assertTrue(content.contains("<node id="), "Must contain at least one <node>");
        Asserts.assertTrue(content.contains("<method name="), "Must contain <method> element with name");
        Asserts.assertTrue(content.contains("<bytecodes>"), "Must contain <bytecodes> element");

        Asserts.assertTrue(content.contains("controlFlow") || content.contains("<controlFlow>"),
            "Must contain control flow information");

        System.out.println("testXmlStructure PASSED");
    }

    private static Path dumpAtLevel(int level) throws Exception {
        Path xmlFile = Files.createTempFile("igv_level" + level + "_", ".xml");
        xmlFile.toFile().deleteOnExit();

        List<String> options = new ArrayList<>();
        options.add("-Xbatch");
        options.add("-XX:PrintIdealGraphLevel=" + level);
        options.add("-XX:PrintIdealGraphFile=" + xmlFile.toAbsolutePath());
        options.add("-XX:CompileCommand=compileonly,compiler.igv.TestIdealGraphDump$TestMethod::compute");
        options.add(TestMethod.class.getName());

        OutputAnalyzer oa = ProcessTools.executeTestJava(options);
        oa.shouldHaveExitValue(0);
        oa.shouldNotContain("# A fatal error has been detected by the Java Runtime Environment");

        Asserts.assertTrue(Files.exists(xmlFile), "IGV XML file must exist: " + xmlFile);
        Asserts.assertTrue(Files.size(xmlFile) > 0, "IGV XML file must not be empty: " + xmlFile);

        return xmlFile;
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

    public static class TestMethod {
        public static void main(String[] args) {
            int sum = 0;
            for (int i = 0; i < 20_000; i++) {
                sum += compute(i, i + 1);
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
    }
}
