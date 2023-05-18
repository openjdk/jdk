/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Verify proper behavior of TreeScanner w.r.t. templated Strings
 * @modules jdk.compiler
 */

import java.io.*;
import java.util.*;
import javax.tools.*;
import com.sun.source.tree.*;
import com.sun.source.util.*;
import java.net.URI;
import java.net.URISyntaxException;

public class TreeScannerTest {
    private static final String JAVA_VERSION = System.getProperty("java.specification.version");

    public static void main(String... args) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        String code = """
                      public class Test {
                          private void test(int a) {
                              String s1 = TEST."p\\{a}s";
                              String s2 = "p\\{a}s";
                          }
                      }
                      """;
        JavacTask task = (JavacTask) compiler.getTask(null, null, null,
            List.of("--enable-preview", "-source", JAVA_VERSION), null, List.of(new TestJFO(code)));
        StringBuilder output = new StringBuilder();
        TreeScanner<Void,Void> checker = new TreeScanner<Void, Void>() {
            private boolean log;

            @Override
            public Void visitStringTemplate(StringTemplateTree node, Void p) {
                boolean prevLog = log;
                try {
                    log = true;
                    return super.visitStringTemplate(node, p);
                } finally {
                    log = prevLog;
                }
            }

            @Override
            public Void scan(Tree tree, Void p) {
                if (log) {
                    output.append("(");
                    output.append(tree != null ? tree.getKind() : "null");
                    try {
                        return super.scan(tree, p);
                    } finally {
                        output.append(")");
                    }
                } else {
                    return super.scan(tree, p);
                }
            }

        };

        checker.scan(task.parse(), null);

        String expected = "(IDENTIFIER)(IDENTIFIER)(null)(IDENTIFIER)";
        if (!expected.equals(output.toString())) {
            throw new AssertionError("expected output not found, found: " + output);
        }
    }

    private static final class TestJFO extends SimpleJavaFileObject {
        private final String code;

        public TestJFO(String code) throws URISyntaxException, IOException {
            super(new URI("mem://Test.java"), Kind.SOURCE);
            this.code = code;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
            return code;
        }

    }
}
