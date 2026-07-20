/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package org.openjdk.bench.javax.tools;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

import javax.tools.JavaCompiler;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Thread)
@Warmup(iterations = 5, time = 3, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 5, timeUnit = TimeUnit.SECONDS)
@Fork(value = 2, jvmArgs = "-Xmx1g")
public class JavacNameTable {

    private List<JavaSourceFromString> compilationUnits;
    private JavaCompiler compiler;
    private StandardJavaFileManager fileManager;
    private File classDir;

    @Setup
    public void prepare() throws IOException {

        // Create a source file with lots of names
        StringBuilder buf = new StringBuilder();
        buf.append("class BigSource {\n");
        for (int i = 0; i < 20000; i++) {
            buf.append(String.format(
                //"final String name%05d = \"some text #%5d\";\n", i, i));
                "String name%05d;\n", i, i));
        }
        buf.append("}\n");
        String bigSource = buf.toString();

        compiler = ToolProvider.getSystemJavaCompiler();

        fileManager = compiler.getStandardFileManager(null, null, null);
        classDir = Files.createTempDirectory(
          JavacNameTable.class.getName()).toFile();
        fileManager.setLocation(StandardLocation.CLASS_OUTPUT,
          Collections.singleton(classDir));

        compilationUnits = new ArrayList<>();
        compilationUnits.add(new JavaSourceFromString("BigSource", bigSource));
    }

    @TearDown
    public void tearDown() {
        for (File f : classDir.listFiles()) {
            if (f.isFile()) {
                f.delete();
            } else {
                throw new IllegalStateException("Unexpected non-file: " + f);
            }
        }
        classDir.delete();
    }

    @Benchmark
    public Boolean testSharedTable() throws Exception {
        return testCompile(null);
    }

    @Benchmark
    public Boolean testUnsharedTable() throws Exception {
        return testCompile("-XDuseUnsharedTable=true");
    }

    @Benchmark
    public Boolean testStringTable() throws Exception {
        return testCompile("-XDuseStringTable=true");
    }

    @Benchmark
    public Boolean testInternStringTable() throws Exception {
        return testCompile("-XDinternStringTable=true");
    }

    public Boolean testCompile(String flag) throws Exception {
        final List<String> options = flag != null ?
          Collections.singletonList(flag) : null;
        JavaCompiler.CompilationTask task = compiler.getTask(null, fileManager,
          null, options, null, compilationUnits);
        return task.call();
    }

    private static class JavaSourceFromString extends SimpleJavaFileObject {

        private final String code;

        JavaSourceFromString(String name, String code) {
            super(URI.create("string:///"
              + name.replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE);
            this.code = code;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return code;
        }
    }
}
