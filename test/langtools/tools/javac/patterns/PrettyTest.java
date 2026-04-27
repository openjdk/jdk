/*
 * Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @enablePreview
 * @summary Test behavior of Pretty
 * @modules jdk.compiler
 */

import java.io.IOException;
import java.io.StringWriter;
import java.net.URI;
import java.util.Arrays;
import java.util.List;

import javax.tools.*;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.util.JavacTask;

public class PrettyTest {

    public static void main(String[] args) throws Exception {
        new PrettyTest().run();
    }

    void run() throws Exception {
        String code = "class Test {\n" +
                      "    boolean t(Object o) {\n" +
                      "         boolean b;\n" +
                      "         boolean _ = true;\n" +
                      "         b = o instanceof String s;\n" +
                      "         b = o instanceof R(String s);\n" +
                      "         b = o instanceof R(var s);\n" +
                      "         b = o instanceof R2(R(var s), String t);\n" +
                      "         b = o instanceof R2(R(var s), var t);\n" +
                      "         b = o instanceof R(String _);\n" +
                      "         b = o instanceof R2(R(var _), var _);\n" +
                      "         b = o instanceof R2(R(_), var t);\n" +
                      "    }\n" +
                      "    record R(String s) {}\n" +
                      "    record R2(R r, String s) {}\n" +
                      "}\n";
        String pretty = parse(code).toString().replaceAll("\\R", "\n");
        String expected = """
                \n\
                class Test {
                    \n\
                    boolean t(Object o) {
                        boolean b;
                        boolean _ = true;
                        b = o instanceof String s;
                        b = o instanceof R(String s);
                        b = o instanceof R(var s);
                        b = o instanceof R2(R(var s), String t);
                        b = o instanceof R2(R(var s), var t);
                        b = o instanceof R(String _);
                        b = o instanceof R2(R(var _), var _);
                        b = o instanceof R2(R(_), var t);
                    }
                    \n\
                    class R {
                        private final String s;
                    }
                    \n\
                    class R2 {
                        private final R r;
                        private final String s;
                    }
                }""";
        if (!expected.equals(pretty)) {
            throw new AssertionError("Actual prettified source: " + pretty);
        }
    }

    private CompilationUnitTree parse(String code) throws IOException {
        final JavaCompiler tool = ToolProvider.getSystemJavaCompiler();
        assert tool != null;
        DiagnosticListener<JavaFileObject> noErrors = d -> {};

        StringWriter out = new StringWriter();
        JavacTask ct = (JavacTask) tool.getTask(out, null, noErrors,
            List.of(), null,
            Arrays.asList(new MyFileObject(code)));
        return ct.parse().iterator().next();
    }

    static class MyFileObject extends SimpleJavaFileObject {
        private String text;

        public MyFileObject(String text) {
            super(URI.create("myfo:/Test.java"), JavaFileObject.Kind.SOURCE);
            this.text = text;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return text;
        }
    }
}
