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

/*
 * @test
 * @bug     8348212
 * @summary Ensure the warn() phase executes when the compiler is invoked via the API
 * @modules jdk.compiler/com.sun.tools.javac.api
 */

import com.sun.tools.javac.api.JavacTaskImpl;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

public class TestJavacTaskWithWarning {

    static final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    static final StandardJavaFileManager fm = compiler.getStandardFileManager(null, null, null);

    public static void warningTest() throws Exception {

        // Create a source file that will generate a warning
        String srcdir = System.getProperty("test.src");
        File file = new File(srcdir, "GeneratesWarning.java");
        try (PrintStream out = new PrintStream(new FileOutputStream(file))) {
            out.print(
                """
                public class GeneratesWarning {
                    public GeneratesWarning() {
                        hashCode();     // generates a "this-escape" warning
                    }
                }
                """);
        }

        // Compile it using API
        Iterable<? extends JavaFileObject> files = fm.getJavaFileObjectsFromFiles(List.of(file));
        StringWriter buf = new StringWriter();
        List<String> options = List.of(
          "-Xlint:this-escape",
          "-XDrawDiagnostics"
        );
        JavacTaskImpl task = (JavacTaskImpl)compiler.getTask(new PrintWriter(buf), fm, null, options, null, files);
        task.analyze();

        // Verify warning was generated
        if (!buf.toString().contains("compiler.warn.possible.this.escape"))
            throw new AssertionError("warning not found in:\n" + buf);
    }

    public static void main(String[] args) throws Exception {
        try {
            warningTest();
        } finally {
            fm.close();
        }
    }
}
