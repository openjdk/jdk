/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8016110 8170832
 * @summary verify Japanese character in an argument are treated correctly
 * @compile -XDignore.symbol.file I18NArgTest.java
 * @run main I18NArgTest
 */
import java.io.IOException;
import java.util.Map;
import java.util.HashMap;
import java.util.HexFormat;
import java.nio.charset.StandardCharsets;

public class I18NArgTest extends TestHelper {
    public static void main(String... args) throws IOException {
        if (!isWindows) {
            return;
        }
        if (!"MS932".equals(System.getProperty("sun.jnu.encoding"))) {
            System.err.println("MS932 encoding not set, test skipped");
            return;
        }
        if (args.length == 0) {
            execTest(0x30bd); // MS932 Katakana SO, 0x835C
        } else {
            testCharacters(args);
        }
    }
    static void execTest(int unicodeValue) {
        String hexValue = Integer.toHexString(unicodeValue);
        String unicodeStr = Character.toString((char)unicodeValue);
        execTest("\"" + unicodeStr + "\"", hexValue);
        execTest("\\" + unicodeStr + "\\", hexValue);
        execTest(" " + unicodeStr + " ", hexValue);
        execTest("'" + unicodeStr + "'", hexValue);
        execTest("\t" + unicodeStr + "\t", hexValue);
        execTest("*" + unicodeStr + "*", hexValue);
        execTest("?" + unicodeStr + "?", hexValue);

        execTest("\"" + unicodeStr + unicodeStr + "\"", hexValue + hexValue);
        execTest("\\" + unicodeStr + unicodeStr + "\\", hexValue + hexValue);
        execTest(" " + unicodeStr + unicodeStr + " ", hexValue + hexValue);
        execTest("'" + unicodeStr + unicodeStr + "'", hexValue + hexValue);
        execTest("\t" + unicodeStr + unicodeStr + "\t", hexValue + hexValue);
        execTest("*" + unicodeStr + unicodeStr + "*", hexValue + hexValue);
        execTest("?" + unicodeStr + unicodeStr + "?", hexValue + hexValue);

        execTest("\"" + unicodeStr + "a" + unicodeStr + "\"", hexValue + "0061" + hexValue);
        execTest("\\" + unicodeStr + "a" + unicodeStr + "\\", hexValue + "0061" + hexValue);
        execTest(" " + unicodeStr + "a" + unicodeStr + " ", hexValue + "0061"+ hexValue);
        execTest("'" + unicodeStr + "a" + unicodeStr + "'", hexValue + "0061"+ hexValue);
        execTest("\t" + unicodeStr + "a" + unicodeStr + "\t", hexValue + "0061"+ hexValue);
        execTest("*" + unicodeStr + "a" + unicodeStr + "*", hexValue + "0061"+ hexValue);
        execTest("?" + unicodeStr + "a" + unicodeStr + "?", hexValue + "0061"+ hexValue);

        execTest("\"" + unicodeStr + "\u00b1" + unicodeStr + "\"", hexValue + "00b1" + hexValue);
        execTest("\\" + unicodeStr + "\u00b1" + unicodeStr + "\\", hexValue + "00b1" + hexValue);
        execTest(" " + unicodeStr + "\u00b1" + unicodeStr + " ", hexValue + "00b1"+ hexValue);
        execTest("'" + unicodeStr + "\u00b1" + unicodeStr + "'", hexValue + "00b1"+ hexValue);
        execTest("\t" + unicodeStr + "\u00b1" + unicodeStr + "\t", hexValue + "00b1"+ hexValue);
        execTest("*" + unicodeStr + "\u00b1" + unicodeStr + "*", hexValue + "00b1"+ hexValue);
        execTest("?" + unicodeStr + "\u00b1" + unicodeStr + "?", hexValue + "00b1"+ hexValue);
    }

    static void execTest(String unicodeStr, String hexValue) {
        TestResult tr = doExec(javaCmd,
                "-Dtest.src=" + TEST_SOURCES_DIR.getAbsolutePath(),
                "-Dtest.classes=" + TEST_CLASSES_DIR.getAbsolutePath(),
                "-cp", TEST_CLASSES_DIR.getAbsolutePath(),
                "I18NArgTest", unicodeStr, hexValue);
        System.out.println(tr.testOutput);
        if (!tr.isOK()) {
            System.err.println(tr);
            throw new RuntimeException("test fails");
        }

        // Test via JDK_JAVA_OPTIONS
        Map<String, String> env = new HashMap<>();
        String sysPropName = "foo.bar";
        // When pass "-Dfoo.bar=<unicodestr>" via the JDK_JAVA_OPTIONS environment variable,
        // we expect that system property value to be passed along to the main method with the
        // correct encoding
        // If <unicodestr> contains space or tab, it should be enclosed with double quotes.
        if (unicodeStr.contains(" ") || unicodeStr.contains("\t")) {
            unicodeStr = "\"" + unicodeStr + "\"";
        }
        String jdkJavaOpts = "-D" + sysPropName + "=" + unicodeStr;
        env.put("JDK_JAVA_OPTIONS", jdkJavaOpts);
        tr = doExec(env,javaCmd,
                "-Dtest.src=" + TEST_SOURCES_DIR.getAbsolutePath(),
                "-Dtest.classes=" + TEST_CLASSES_DIR.getAbsolutePath(),
                "-cp", TEST_CLASSES_DIR.getAbsolutePath(),
                "I18NArgTest", unicodeStr, hexValue, sysPropName);
        System.out.println(tr.testOutput);
        if (!tr.isOK()) {
            System.err.println(tr);
            throw new RuntimeException("test fails");
        }
    }

    static void testCharacters(String... args) {
        String input = args[0];
        String expected = args[1];
        var hexValue = HexFormat.of().formatHex(input.getBytes(StandardCharsets.UTF_16));
        System.out.println("input:" + input);
        System.out.println("expected:" + expected);
        System.out.println("obtained:" + hexValue);
        if (!hexValue.contains(expected)) {
            String message = "Error: output does not contain expected value" +
                "expected:" + expected + " obtained:" + hexValue;
            throw new RuntimeException(message);
        }
        if (args.length == 3) {
            // verify the value of the system property matches the expected value
            String sysPropName = args[2];
            String sysPropVal = System.getProperty(sysPropName);
            if (sysPropVal == null) {
                throw new RuntimeException("Missing system property " + sysPropName);
            }
            var sysPropHexVal = HexFormat.of().formatHex(sysPropVal.getBytes(StandardCharsets.UTF_16));
            System.out.println("System property " + sysPropName + " computed hex value: "
                    + sysPropHexVal);
            if (!sysPropHexVal.contains(expected)) {
                throw new RuntimeException("Unexpected value in system property, expected "
                        + expected + ", but got " + sysPropHexVal);
            }
        }
    }
}
