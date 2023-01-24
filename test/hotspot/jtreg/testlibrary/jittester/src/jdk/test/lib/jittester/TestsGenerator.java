/*
 * Copyright (c) 2016, 2023, Oracle and/or its affiliates. All rights reserved.
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

package jdk.test.lib.jittester;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.BiConsumer;
import java.util.Optional;


public abstract class TestsGenerator implements BiConsumer<IRNode, IRNode> {
    private static final int DEFAULT_JTREG_TIMEOUT = 120;
    protected static final String JAVA_BIN = getJavaPath();
    protected static final String JAVAC = Paths.get(JAVA_BIN, "javac").toString();
    protected static final String JAVA = Paths.get(JAVA_BIN, "java").toString();
    protected final Path generatorDir;
    protected final TempDir tmpDir;

    protected TestsGenerator(String suffix) {
        generatorDir = getRoot().resolve(suffix).toAbsolutePath();
        tmpDir = new TempDir(suffix);
    }

    protected void generateGoldenOut(String mainClassName) {
        String classPath = tmpDir.path + File.pathSeparator
                + generatorDir.toString();
        ProcessBuilder pb = new ProcessBuilder(JAVA, "-Xint", HeaderFormatter.DISABLE_WARNINGS, "-Xverify",
                "-cp", classPath, "-Dstdout.encoding=UTF-8", mainClassName);
        try {
            ProcessRunner.runProcess(pb, generatorDir.resolve(mainClassName).toString(), Phase.GOLD_RUN);
        } catch (IOException | InterruptedException e)  {
            throw new Error("Can't run generated test ", e);
        }
    }

    protected void compilePrinter() {
        Path root = getRoot();
        ProcessBuilder pbPrinter = new ProcessBuilder(JAVAC,
                "-d", tmpDir.path.toString(),
                root.resolve("jdk")
                    .resolve("test")
                    .resolve("lib")
                    .resolve("jittester")
                    .resolve("jtreg")
                    .resolve("Printer.java")
                    .toString());
        try {
            int exitCode = ProcessRunner.runProcess(pbPrinter,
                    root.resolve("Printer").toString(), Phase.COMPILE);
            if (exitCode != 0) {
                throw new Error("Printer compilation returned exit code " + exitCode);
            }
        } catch (IOException | InterruptedException e) {
            throw new Error("Can't compile printer", e);
        }
    }

    protected static void ensureExisting(Path path) {
        if (Files.notExists(path)) {
            try {
                Files.createDirectories(path);
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    protected static Path getRoot() {
        return Paths.get(ProductionParams.testbaseDir.value());
    }

    protected static void writeFile(Path targetDir, String fileName, String content) {
        try (FileWriter file = new FileWriter(targetDir.resolve(fileName).toFile())) {
            file.write(content);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static String getJavaPath() {
        String[] env = { "test.jdk", "JDK_HOME", "JAVA_HOME", "BOOTDIR" };
        for (String name : env) {
            String path = Optional.ofNullable(System.getenv(name))
                                  .orElse(System.getProperty(name));
            if (path != null) {
                return Paths.get(path)
                            .resolve("bin")
                            .toAbsolutePath()
                            .toString();
            }
        }
        return "";
    }
}
