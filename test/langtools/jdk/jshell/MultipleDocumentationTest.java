/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.stream.Collectors;
import jdk.jshell.JShell;
import org.testng.annotations.Test;
import static org.testng.Assert.assertEquals;

/*
 * @test
 * @bug 8274734
 * @summary Verify multiple SourceCodeAnalysis instances can concurrently provide documentation.
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.jdeps/com.sun.tools.javap
 *          jdk.jshell/jdk.internal.jshell.tool
 * @build Compiler toolbox.ToolBox
 * @run testng MultipleDocumentationTest
 */
@Test
public class MultipleDocumentationTest {

    public void testMultipleDocumentation() {
        String input = "java.lang.String";

        try (var state1 = JShell.builder()
                                .out(new PrintStream(new ByteArrayOutputStream()))
                                .err(new PrintStream(new ByteArrayOutputStream()))
                                .build()) {
            var sca1 = state1.sourceCodeAnalysis();
            List<String> javadocs1 =
                    sca1.documentation(input, input.length(), true)
                        .stream()
                        .map(d -> d.javadoc())
                        .collect(Collectors.toList());

            try (var state2 = JShell.builder()
                                    .out(new PrintStream(new ByteArrayOutputStream()))
                                    .err(new PrintStream(new ByteArrayOutputStream()))
                                    .build()) {
                var sca2 = state2.sourceCodeAnalysis();
                List<String> javadocs2 = sca2.documentation(input, input.length(), true)
                                             .stream()
                                             .map(d -> d.javadoc())
                                             .collect(Collectors.toList());

                assertEquals(javadocs2, javadocs1);
            }
        }
    }

}
