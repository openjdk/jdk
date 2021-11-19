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
 * @bug 8276764
 * @summary perform a jar creation benchmark
 * @library /test/lib
 * @modules jdk.jartool
 * @build jdk.test.lib.Platform
 *        jdk.test.lib.util.FileUtils
 * @run testng CreateJarBenchmark 
 */

import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.spi.ToolProvider;
import java.util.stream.Stream;
import java.util.zip.ZipException;

import jdk.test.lib.util.FileUtils;

public class CreateJarBenchmark {
    private static final ToolProvider JAR_TOOL = ToolProvider.findFirst("jar")
        .orElseThrow(() ->
            new RuntimeException("jar tool not found")
        );

    private final ByteArrayOutputStream baos = new ByteArrayOutputStream();
    private final PrintStream out = new PrintStream(baos);
    private Runnable onCompletion;

    @BeforeMethod
    public void reset() {
        onCompletion = null;
    }

    @AfterMethod
    public void run() {
        if (onCompletion != null) {
            onCompletion.run();
        }
    }

    @Test
    public void testSingleDir() throws IOException {
        // Create a single testjar directory containing 10000 files
        mkdir("testjar");
        for(int i = 0; i < 10000; i++) {
            createFile("testjar/testfile"+i);
        }

        onCompletion = () -> rm("test.jar", "testjar");

        // Perform 100x jar creations
        long start = System.currentTimeMillis(); 
        for(int i = 0; i < 100; i++) {
            jar("cf test.jar testjar");
            rm("test.jar");
        }
        long finish = System.currentTimeMillis();

        System.out.println("single directory jar creation benchmark = " + (finish-start) + "ms");
    }

    @Test
    public void testMultiDir() throws IOException {
        // Create a nested 10x20 set of sub-dirs each containing 50 files
        mkdir("testjar");
        for(int i = 0; i < 10; i++) {
            mkdir("testjar/testdir" + i);
            for(int j = 0; j < 20; j++) {
                mkdir("testjar/testdir" + i + "/subdir" + j);
                for(int k = 0; k < 50; k++) {
                    createFile("testjar/testdir" + i + "/subdir" + j + "/testfile" + k);
                }
            }
        }

        onCompletion = () -> rm("test.jar", "testjar");

        // Perform 100x jar creations
        long start = System.currentTimeMillis();
        for(int i = 0; i < 100; i++) {
            jar("cf test.jar testjar");
            rm("test.jar");
        }
        long finish = System.currentTimeMillis();

        System.out.println("multi directory jar creation benchmark = " + (finish-start) + "ms");
    }

    private Stream<Path> mkpath(String... args) {
        return Arrays.stream(args).map(d -> Paths.get(".", d.split("/")));
    }

    private void mkdir(String... dirs) {
        Arrays.stream(dirs).forEach(p -> {
            try {
                Files.createDirectories((new File(p)).toPath());
            } catch (IOException x) {
                throw new UncheckedIOException(x);
            }
        });
    }

    private void createFile(String... files) {
        Arrays.stream(files).forEach(p -> {
            try {
                try (FileOutputStream fos = new FileOutputStream(p)) {
                    // Create file with fixed content
                    byte[] bytes = new byte[10000];
                    Arrays.fill(bytes, (byte)0x41);
                    fos.write(bytes);
                }
            } catch (IOException x) {
                throw new UncheckedIOException(x);
            }
        });
    }

    private void rm(String... files) {
        Arrays.stream(files).forEach(p -> {
            try {
                Path path = (new File(p)).toPath();
                if (Files.isDirectory(path)) {
                    FileUtils.deleteFileTreeWithRetry(path);
                } else {
                    FileUtils.deleteFileIfExistsWithRetry(path);
                }
            } catch (IOException x) {
                throw new UncheckedIOException(x);
            }
        });
    }

    private void jar(String cmdline) throws IOException {
        System.out.println("jar " + cmdline);
        baos.reset();

        // the run method catches IOExceptions, we need to expose them
        ByteArrayOutputStream baes = new ByteArrayOutputStream();
        PrintStream err = new PrintStream(baes);
        PrintStream saveErr = System.err;
        System.setErr(err);
        int rc = JAR_TOOL.run(out, err, cmdline.split(" +"));
        System.setErr(saveErr);
        if (rc != 0) {
            String s = baes.toString();
            if (s.startsWith("java.util.zip.ZipException: duplicate entry: ")) {
                throw new ZipException(s);
            }
            throw new IOException(s);
        }
    }
}
