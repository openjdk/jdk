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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.testng.annotations.BeforeTest;

import toolbox.JavacTask;
import toolbox.TestRunner;
import toolbox.ToolBox;

/*
 * This class installs a class in a temporary diretory so we can test
 * finding classes that are not visible to the system class loader.
 */
public class LocalExecutionTestSupport extends ReplToolTesting {

    public static final String MY_CLASS_SOURCE = """
        package test;
        public class MyClass {
            public static final String FOO = "bar";
        }""";

    protected final ToolBox tb = new ToolBox();

    protected Path baseDir;                 // base working directory
    protected Path sourcesDir;              // sources directory
    protected Path classesDir;              // classes directory

    // Install file "test/MyClass.class" in some temporary directory somewhere
    @BeforeTest
    public void installMyClass() throws IOException {

        // Create directories
        baseDir = Files.createTempDirectory(getClass().getSimpleName()).toAbsolutePath();
        sourcesDir = createWorkSubdir("sources");
        classesDir = createWorkSubdir("classes");

        // Create source file
        tb.writeJavaFiles(sourcesDir, MY_CLASS_SOURCE);

        // Compile source file
        new JavacTask(tb)
            .outdir(classesDir)
            .files(sourcesDir.resolve("test/MyClass.java"))
            .run();
    }

    protected Path createWorkSubdir(String name) throws IOException {
        return createSubdir(baseDir, name);
    }

    protected Path createSubdir(Path base, String name) throws IOException {
        Path dir = base.resolve(name);
        Files.createDirectories(dir);
        return dir;
    }

    protected String[] prependArgs(String[] args, String... prepends) {
        String[] newArgs = new String[prepends.length + args.length];
        System.arraycopy(prepends, 0, newArgs, 0, prepends.length);
        System.arraycopy(args, 0, newArgs, prepends.length, args.length);
        return newArgs;
    }
}
