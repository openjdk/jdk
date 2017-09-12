/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 8072480
 * @summary Unit test for CreateSymbols
 * @clean *
 * @run main CreateSymbolsTest
 */

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

public class CreateSymbolsTest {

    static final String CREATE_SYMBOLS_NAME = "symbolgenerator.CreateSymbols";

    public static void main(String... args) throws Exception {
        new CreateSymbolsTest().doRun();
    }

    void doRun() throws Exception {
        Path testClasses = Paths.get(System.getProperty("test.classes"));
        Path compileDir = testClasses.resolve("data");
        deleteRecursively(compileDir);
        Files.createDirectories(compileDir);
        Path createSymbols = findFile("../../make/src/classes/build/tools/symbolgenerator/CreateSymbols.java");

        if (createSymbols == null) {
            System.err.println("Warning: cannot find CreateSymbols, skipping.");
            return ;
        }

        Path createTestImpl = findFile("../../make/test/sym/CreateSymbolsTestImpl.java");

        if (createTestImpl == null) {
            System.err.println("Warning: cannot find CreateSymbolsTestImpl, skipping.");
            return ;
        }

        Path toolBox = findFile("../../test/tools/lib/ToolBox.java");

        if (toolBox == null) {
            System.err.println("Warning: cannot find ToolBox, skipping.");
            return ;
        }

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();

        try (StandardJavaFileManager fm = compiler.getStandardFileManager(null, null, null)) {
            compiler.getTask(null,
                             null,
                             null,
                             Arrays.asList("-d", compileDir.toAbsolutePath().toString()),
                             null,
                             fm.getJavaFileObjects(createSymbols, createTestImpl, toolBox)
                            ).call();
        }

        URLClassLoader cl = new URLClassLoader(new URL[] {testClasses.toUri().toURL(), compileDir.toUri().toURL()});
        Class<?> createSymbolTest = cl.loadClass("CreateSymbolsTestImpl");

        createSymbolTest.getMethod("main", String[].class).invoke(null, (Object) new String[0]);
    }

    Path findFile(String path) {
        Path testSrc = Paths.get(System.getProperty("test.src", "."));

        for (Path d = testSrc; d != null; d = d.getParent()) {
            if (Files.exists(d.resolve("TEST.ROOT"))) {
                Path createSymbols = d.resolve(path);
                if (Files.exists(createSymbols)) {
                    return createSymbols;
                }
            }
        }

        return null;
    }

    void deleteRecursively(Path dir) throws IOException {
        Files.walkFileTree(dir, new FileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
