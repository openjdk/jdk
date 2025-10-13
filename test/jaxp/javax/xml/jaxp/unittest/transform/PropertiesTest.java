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
 */

package transform;

import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamSource;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import jaxp.library.JUnitTestUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.assertTrue;

/*
 * @test
 * @bug 8344925
 * @summary Transformer properties tests
 * @library /javax/xml/jaxp/libs /javax/xml/jaxp/unittest /test/lib
 * @run junit/othervm transform.PropertiesTest
 */
public class PropertiesTest {
    private static final String USER_DIR = System.getProperty("user.dir");
    private static final String TEST_DIR = System.getProperty("test.src");
    // Test parameters:
    // generate-translet: indicates whether to generate translet
    // translet-name: the name of the translet
    // package-name: the package name
    // destination-directory: the destination
    // expected: the class path
    private static Stream<Arguments> testData() {
        String destination = JUnitTestUtil.CLS_DIR + "/testdir";
        return Stream.of(
                Arguments.of(true, "MyTranslet", "org.myorg", destination, "/org/myorg/MyTranslet.class"),
                Arguments.of(false, "Translet", "not.generate", destination, "/not/generate/Translet.class"),
                // translet named after the stylesheet
                Arguments.of(true, null, "org.myorg", destination, "/org/myorg/transform.class"),
                // default package name die.verwandlung since JDK 9
                Arguments.of(true, "MyTranslet", null, destination, "/die/verwandlung/MyTranslet.class"),
                Arguments.of(true, "MyTranslet", "org.myorg", null, "/org/myorg/MyTranslet.class")
                );
    }

    @BeforeAll
    public static void setup() throws Exception {
        // so that the translet is generated under test.classes
        JUnitTestUtil.copyFile(JUnitTestUtil.SRC_DIR + "/transform.xsl", JUnitTestUtil.CLS_DIR + "/transform.xsl");
    }

    @ParameterizedTest
    @MethodSource("testData")
    public void test(boolean generateTranslet, String name, String packageName,
                     String destination, String expected)
            throws Exception {
        TransformerFactory tf = TransformerFactory.newInstance();

        tf.setAttribute("generate-translet", generateTranslet);
        if (name != null) tf.setAttribute("translet-name", name);
        if (packageName != null) tf.setAttribute("package-name", packageName);
        if (destination != null) tf.setAttribute("destination-directory", destination);

        String xslFile = JUnitTestUtil.CLS_DIR + "/transform.xsl";
        String xslSysId = JUnitTestUtil.getSystemId(xslFile);
        StreamSource xsl = new StreamSource(xslSysId);
        tf.newTemplates(xsl);

        String path = (destination != null) ? destination + expected : new File(xslFile).getParent() + expected;

        if (generateTranslet) {
            assertTrue(Files.exists(Path.of(path)), "Translet is expected at " + expected);
        } else {
            assertTrue(Files.notExists(Path.of(path)), "Translet is not to be generated.");
        }
    }
}
