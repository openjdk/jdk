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
 * @summary test that the jar content ordering is sorted
 * @library /test/lib
 * @modules jdk.jartool
 * @build jdk.test.lib.Platform
 *        jdk.test.lib.util.FileUtils
 * @run testng ContentOrder
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.spi.ToolProvider;
import java.util.stream.Stream;
import java.util.zip.ZipException;

import jdk.test.lib.util.FileUtils;

public class ContentOrder {
    private static final ToolProvider JAR_TOOL = ToolProvider.findFirst("jar")
        .orElseThrow(() ->
            new RuntimeException("jar tool not found")
        );

    private final String nl = System.lineSeparator();
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

    // Test that the jar content ordering when processing a single directory is sorted
    @Test
    public void testSingleDir() throws IOException {
        mkdir("testjar/Ctest1", "testjar/Btest2/subdir1", "testjar/Atest3");
        touch("testjar/Ctest1/testfile1", "testjar/Ctest1/testfile2", "testjar/Ctest1/testfile3");
        touch("testjar/Btest2/subdir1/testfileC", "testjar/Btest2/subdir1/testfileB", "testjar/Btest2/subdir1/testfileA");
        touch("testjar/Atest3/fileZ", "testjar/Atest3/fileY", "testjar/Atest3/fileX");

        onCompletion = () -> rm("test.jar", "testjar");

        jar("cf test.jar testjar");
        jar("tf test.jar");
        System.out.println(new String(baos.toByteArray()));
        String output = "META-INF/" + nl +
                "META-INF/MANIFEST.MF" + nl +
                "testjar/" + nl +
                "testjar/Atest3/" + nl +
                "testjar/Atest3/fileX" + nl +
                "testjar/Atest3/fileY" + nl +
                "testjar/Atest3/fileZ" + nl +
                "testjar/Btest2/" + nl +
                "testjar/Btest2/subdir1/" + nl +
                "testjar/Btest2/subdir1/testfileA" + nl +
                "testjar/Btest2/subdir1/testfileB" + nl +
                "testjar/Btest2/subdir1/testfileC" + nl +
                "testjar/Ctest1/" + nl +
                "testjar/Ctest1/testfile1" + nl +
                "testjar/Ctest1/testfile2" + nl +
                "testjar/Ctest1/testfile3" + nl;
        Assert.assertEquals(baos.toByteArray(), output.getBytes());
    }

    // Test that when specifying multiple directories or releases that the sort
    // ordering is done on each directory and release, reserving the order of
    // the directories/releases specified on the command line
    @Test
    public void testMultiDirWithReleases() throws IOException {
        mkdir("testjar/foo/classes",
              "testjar/foo11/classes/Zclasses",
              "testjar/foo11/classes/Yclasses",
              "testjar/foo17/classes/Bclasses",
              "testjar/foo17/classes/Aclasses");
        touch("testjar/foo/classes/testfile1", "testjar/foo/classes/testfile2");
        touch("testjar/foo11/classes/Zclasses/testfile1", "testjar/foo11/classes/Zclasses/testfile2");
        touch("testjar/foo11/classes/Yclasses/testfileA", "testjar/foo11/classes/Yclasses/testfileB");
        touch("testjar/foo17/classes/Bclasses/testfile1", "testjar/foo17/classes/Bclasses/testfile2");
        touch("testjar/foo17/classes/Aclasses/testfileA", "testjar/foo17/classes/Aclasses/testfileB");

        onCompletion = () -> rm("test.jar", "testjar");

        jar("cf test.jar -C testjar/foo classes " +
            "--release 17 -C testjar/foo17 classes/Bclasses -C testjar/foo17 classes/Aclasses " +
            "--release 11 -C testjar/foo11 classes/Zclasses -C testjar/foo11 classes/Yclasses");
        jar("tf test.jar");
        System.out.println(new String(baos.toByteArray()));
        String output = "META-INF/" + nl +
                "META-INF/MANIFEST.MF" + nl +
                "classes/" + nl +
                "classes/testfile1" + nl +
                "classes/testfile2" + nl +
                "META-INF/versions/17/classes/Bclasses/" + nl +
                "META-INF/versions/17/classes/Bclasses/testfile1" + nl +
                "META-INF/versions/17/classes/Bclasses/testfile2" + nl +
                "META-INF/versions/17/classes/Aclasses/" + nl +
                "META-INF/versions/17/classes/Aclasses/testfileA" + nl +
                "META-INF/versions/17/classes/Aclasses/testfileB" + nl +
                "META-INF/versions/11/classes/Zclasses/" + nl +
                "META-INF/versions/11/classes/Zclasses/testfile1" + nl +
                "META-INF/versions/11/classes/Zclasses/testfile2" + nl +
                "META-INF/versions/11/classes/Yclasses/" + nl +
                "META-INF/versions/11/classes/Yclasses/testfileA" + nl +
                "META-INF/versions/11/classes/Yclasses/testfileB" + nl;
        Assert.assertEquals(baos.toByteArray(), output.getBytes());
    }

    private Stream<Path> mkpath(String... args) {
        return Arrays.stream(args).map(d -> Paths.get(".", d.split("/")));
    }

    private void mkdir(String... dirs) {
        System.out.println("mkdir -p " + Arrays.toString(dirs));
        Arrays.stream(dirs).forEach(p -> {
            try {
                Files.createDirectories((new File(p)).toPath());
            } catch (IOException x) {
                throw new UncheckedIOException(x);
            }
        });
    }

    private void touch(String... files) {
        System.out.println("touch " + Arrays.toString(files));
        Arrays.stream(files).forEach(p -> {
            try {
                Files.createFile((new File(p)).toPath());
            } catch (IOException x) {
                throw new UncheckedIOException(x);
            }
        });
    }

    private void rm(String... files) {
        System.out.println("rm -rf " + Arrays.toString(files));
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
