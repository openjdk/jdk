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

/*
 * @test
 * @bug 8335912
 * @summary test extract jar files overwrite existing files behavior
 * @library /test/lib
 * @modules jdk.jartool
 * @build jdk.test.lib.Platform
 *        jdk.test.lib.util.FileUtils
 * @run junit/othervm ExtractFilesTest
 */

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.spi.ToolProvider;
import java.util.stream.Stream;

import jdk.test.lib.util.FileUtils;

 @TestInstance(Lifecycle.PER_CLASS)
 public class ExtractFilesTest {
    private static final ToolProvider JAR_TOOL = ToolProvider.findFirst("jar")
        .orElseThrow(() ->
            new RuntimeException("jar tool not found")
        );

    private final String nl = System.lineSeparator();
    private final ByteArrayOutputStream baos = new ByteArrayOutputStream();
    private final PrintStream out = new PrintStream(baos);

    @BeforeAll
    public void setupJar() throws IOException {
        mkdir("test1 test2");
        echo("testfile1", "test1/testfile1");
        echo("testfile2", "test2/testfile2");
        jar("cf test.jar -C test1 . -C test2 .");
        rm("test1 test2");
    }

    @AfterAll
    public void cleanup() {
        rm("test.jar");
    }

    /**
     * Regular clean extract with expected output.
     */
    @Test
    public void testExtract() throws IOException {
        jar("xvf test.jar");
        println();
        String output = "  created: META-INF/" + nl +
                " inflated: META-INF/MANIFEST.MF" + nl +
                " inflated: testfile1" + nl +
                " inflated: testfile2" + nl;
        rm("META-INF testfile1 testfile2");
        Assertions.assertArrayEquals(baos.toByteArray(), output.getBytes());
    }

    /**
     * Extract should overwrite existing file as default behavior.
     */
    @Test
    public void testOverwrite() throws IOException {
        touch("testfile1");
        jar("xvf test.jar");
        println();
        String output = "  created: META-INF/" + nl +
                " inflated: META-INF/MANIFEST.MF" + nl +
                " inflated: testfile1" + nl +
                " inflated: testfile2" + nl;
        Assertions.assertEquals("testfile1", cat("testfile1"));
        rm("META-INF testfile1 testfile2");
        Assertions.assertArrayEquals(baos.toByteArray(), output.getBytes());
    }

    /**
     * Extract with legacy style option `k` should preserve existing files.
     */
    @Test
    public void testKeptOldFile() throws IOException {
        touch("testfile1");
        jar("xkvf test.jar");
        println();
        String output = "  created: META-INF/" + nl +
                " inflated: META-INF/MANIFEST.MF" + nl +
                "  skipped: testfile1 exists" + nl +
                " inflated: testfile2" + nl;
        Assertions.assertEquals("", cat("testfile1"));
        Assertions.assertEquals("testfile2", cat("testfile2"));
        rm("META-INF testfile1 testfile2");
        Assertions.assertArrayEquals(baos.toByteArray(), output.getBytes());
    }

    /**
     * Extract with gnu style -k should preserve existing files.
     */
    @Test
    public void testGnuOptionsKeptOldFile() throws IOException {
        touch("testfile1 testfile2");
        jar("-x -k -v -f test.jar");
        println();
        String output = "  created: META-INF/" + nl +
                " inflated: META-INF/MANIFEST.MF" + nl +
                "  skipped: testfile1 exists" + nl +
                "  skipped: testfile2 exists" + nl;
        Assertions.assertEquals("", cat("testfile1"));
        Assertions.assertEquals("", cat("testfile2"));
        rm("META-INF testfile1 testfile2");
        Assertions.assertArrayEquals(baos.toByteArray(), output.getBytes());
    }

    /**
     * Extract with gnu style long option --keep-old-files should preserve existing files.
     */
    @Test
    public void testGnuLongOptionsKeptOldFile() throws IOException {
        touch("testfile2");
        jar("-x --keep-old-files -v -f test.jar");
        println();
        String output = "  created: META-INF/" + nl +
                " inflated: META-INF/MANIFEST.MF" + nl +
                " inflated: testfile1" + nl +
                "  skipped: testfile2 exists" + nl;
        Assertions.assertEquals("testfile1", cat("testfile1"));
        Assertions.assertEquals("", cat("testfile2"));
        rm("META-INF testfile1 testfile2");
        Assertions.assertArrayEquals(baos.toByteArray(), output.getBytes());
    }

    /**
     * Test jar will issue warning when use keep option in non-extraction mode.
     */
    @Test
    public void testWarningOnInvalidKeepOption() throws IOException {
        var err = jar("tkf test.jar");
        println();

        String output = "META-INF/" + nl +
                "META-INF/MANIFEST.MF" + nl +
                "testfile1" + nl +
                "testfile2" + nl;

        Assertions.assertArrayEquals(baos.toByteArray(), output.getBytes());
        Assertions.assertEquals("Warning: The --keep-old-files/-k/k option is not valid with current usage, will be ignored." + nl, err);
    }

    private Stream<Path> mkpath(String... args) {
        return Arrays.stream(args).map(d -> Path.of(".", d.split("/")));
    }

    private void mkdir(String cmdline) {
        System.out.println("mkdir -p " + cmdline);
        mkpath(cmdline.split(" +")).forEach(p -> {
            try {
                Files.createDirectories(p);
            } catch (IOException x) {
                throw new UncheckedIOException(x);
            }
        });
    }

    private void touch(String cmdline) {
        System.out.println("touch " + cmdline);
        mkpath(cmdline.split(" +")).forEach(p -> {
            try {
                Files.createFile(p);
            } catch (IOException x) {
                throw new UncheckedIOException(x);
            }
        });
    }

    private void echo(String text, String path) {
        System.out.println("echo '" + text + "' > " + path);
        try {
            var p = Path.of(".", path.split("/"));
            Files.writeString(p, text);
        } catch (IOException x) {
            throw new UncheckedIOException(x);
        }
    }

    private String cat(String path) {
        System.out.println("cat " + path);
        try {
            return Files.readString(Path.of(path));
        } catch (IOException x) {
            throw new UncheckedIOException(x);
        }
    }

    private void rm(String cmdline) {
        System.out.println("rm -rf " + cmdline);
        mkpath(cmdline.split(" +")).forEach(p -> {
            try {
                if (Files.isDirectory(p)) {
                    FileUtils.deleteFileTreeWithRetry(p);
                } else {
                    FileUtils.deleteFileIfExistsWithRetry(p);
                }
            } catch (IOException x) {
                throw new UncheckedIOException(x);
            }
        });
    }

    private String jar(String cmdline) throws IOException {
        System.out.println("jar " + cmdline);
        baos.reset();

        // the run method catches IOExceptions, we need to expose them
        ByteArrayOutputStream baes = new ByteArrayOutputStream();
        PrintStream err = new PrintStream(baes);
        PrintStream saveErr = System.err;
        System.setErr(err);
        try {
            int rc = JAR_TOOL.run(out, err, cmdline.split(" +"));
            if (rc != 0) {
                throw new IOException(baes.toString());
            }
        } finally {
            System.setErr(saveErr);
        }
        return baes.toString();
    }

    private void println() throws IOException {
        System.out.println(new String(baos.toByteArray()));
    }
}
