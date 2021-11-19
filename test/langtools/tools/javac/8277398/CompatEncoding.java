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

/*
 * @test
 * @bug     8277398
 * @summary javac does not accept encoding name COMPAT
 * @modules java.compiler
 *          jdk.compiler
 * @compile -encoding utf-8 CompatEncoding.java
 * @run main CompatEncoding
 */

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.tools.*;
import static java.nio.file.StandardOpenOption.*;
import static java.nio.charset.StandardCharsets.US_ASCII;

public class CompatEncoding {
    boolean error;
    final StandardJavaFileManager fm;
    final JavaCompiler compiler;
    Iterable<? extends JavaFileObject> files;
    DiagnosticListener<JavaFileObject> dl;
    final File testFile = new File("Compat.java");

    CompatEncoding() throws IOException {
        dl = new DiagnosticListener<JavaFileObject>() {
            public void report(Diagnostic<? extends JavaFileObject> message) {
                error = message.getKind() == Diagnostic.Kind.ERROR;
            }
        };
        compiler = ToolProvider.getSystemJavaCompiler();
        fm = compiler.getStandardFileManager(dl, null, null);
        files = fm.getJavaFileObjectsFromFiles(Arrays.asList(testFile));
        createTestFile();
    }
    final void createTestFile() throws IOException {
        List<String> scratch = new ArrayList<>();
        scratch.add("class Compat{}");
        Files.write(testFile.toPath(), scratch, US_ASCII,
                CREATE, TRUNCATE_EXISTING);
    }
    void test() {
        error = false;
        Iterable<String> args = Arrays.asList("-encoding", "COMPAT", "-d", ".");
        compiler.getTask(null, fm, dl, args, null, files).call();
        if (error)
            throw new AssertionError("Error reported");
    }

    void close() throws IOException {
        fm.close();
    }

    public static void main(String[] args) throws IOException {
        CompatEncoding self = new CompatEncoding();
        try {
            self.test();
        } finally {
            self.close();
        }
    }
}
