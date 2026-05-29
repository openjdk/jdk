/*
 * Copyright (c) 2003, 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.file.Files;
import java.nio.file.Path;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import java.nio.file.StandardCopyOption;
import jdk.test.lib.compiler.CompilerUtils;

/**
 * Replaces RedefineSetUp.sh.
 * Compiles Different_ExampleRedefine.java as ExampleRedefine, renames
 * the class file to Different_ExampleRedefine.class, then compiles
 * the real ExampleRedefine.java.
 */
public class RedefineSetUpDriver {
    public static void main(String[] args) throws Exception {
        String testSrc = System.getProperty("test.src");
        String testClasses = System.getProperty("test.classes");
        Path srcDir = Path.of("redefinesrc");
        Path outDir = Path.of(testClasses);
        Files.createDirectories(srcDir);

        // Compile Different_ExampleRedefine.java as ExampleRedefine
        Files.copy(Path.of(testSrc, "Different_ExampleRedefine.java"),
            srcDir.resolve("ExampleRedefine.java"));
        Files.copy(Path.of(testSrc, "Counter.java"),
            srcDir.resolve("Counter.java"));
        CompilerUtils.compile(srcDir, outDir);

        // Rename to Different_ExampleRedefine.class
        Files.move(outDir.resolve("ExampleRedefine.class"),
            outDir.resolve("Different_ExampleRedefine.class"));
        Files.copy(outDir.resolve("Different_ExampleRedefine.class"),
            Path.of("Different_ExampleRedefine.class"),
            StandardCopyOption.REPLACE_EXISTING);

        // Clean and compile the real ExampleRedefine
        Files.delete(srcDir.resolve("ExampleRedefine.java"));
        Files.delete(srcDir.resolve("Counter.java"));
        Files.copy(Path.of(testSrc, "ExampleRedefine.java"),
            srcDir.resolve("ExampleRedefine.java"));
        Files.copy(Path.of(testSrc, "Counter.java"),
            srcDir.resolve("Counter.java"));
        CompilerUtils.compile(srcDir, outDir);

        // Also copy to current working directory for tests that read from "."
        Files.copy(outDir.resolve("Different_ExampleRedefine.class"),
            Path.of("Different_ExampleRedefine.class"),
            StandardCopyOption.REPLACE_EXISTING);
        Files.copy(outDir.resolve("ExampleRedefine.class"),
            Path.of("ExampleRedefine.class"),
            StandardCopyOption.REPLACE_EXISTING);

        System.out.println("RedefineSetUp complete");
    }
}
