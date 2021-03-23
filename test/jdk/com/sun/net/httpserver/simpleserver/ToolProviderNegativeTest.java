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
 * @summary Negative tests for the SimpleFileServerToolProvider
 * @library /test/lib
 * @modules jdk.httpserver
 * @build jdk.test.lib.util.FileUtils
 * @run testng ToolProviderNegativeTest
 */

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.function.Consumer;
import java.util.spi.ToolProvider;
import jdk.test.lib.util.FileUtils;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

public class ToolProviderNegativeTest {

    static final ToolProvider SIMPLESERVER_TOOL = ToolProvider.findFirst("simpleserver")
        .orElseThrow(() -> new RuntimeException("simpleserver tool not found"));

    @Test(expectedExceptions = NullPointerException.class)
    public void testArgsNull() {
        var baos = new ByteArrayOutputStream();
        var ps = new PrintStream(baos);
        simpleserver(ps, (String[]) null);
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testPrintWriterNull() {
        simpleserver( null, new String[]{});
    }

    @Test
    public void testBadOption() {
        simpleserver("--badOption")
            .assertFailure()
            .resultChecker(r ->
                assertContains(r.output, "Error: unknown option(s): --badOption")
            );
    }

    @DataProvider(name = "tooManyOptionArgs")
    public Object[][] tooManyOptionArgs() {
        return new Object[][] {
                {"-b", "localhost"},
                {"-d", "/some/path"},
                {"-o", "none"},
                {"-p", "8001"}
                // doesn't fail for -h option
        };
    }

    @Test(dataProvider = "tooManyOptionArgs")
    public void testTooManyOptionArgs(String option, String arg) {
        simpleserver(option, arg, arg)
            .assertFailure()
            .resultChecker(r ->
                assertContains(r.output, "Error: unknown option(s): " + arg)
            );
    }

    @DataProvider(name = "noArg")
    public Object[][] noArg() {
        return new Object[][] {
                {"-b"},
                {"-d"},
                {"-o"},
                {"-p"}
                // doesn't fail for -h option
        };
    }

    @Test(dataProvider = "noArg")
    public void testNoArg(String option) {
        simpleserver(option)
            .assertFailure()
            .resultChecker(r ->
                assertContains(r.output, "Error: no value given for " + option)
            );
    }

    @DataProvider(name = "invalidValue")
    public Object[][] invalidValue() {
        return new Object[][] {
                {"-b", "[127.0.0.1]"},
                {"-b", "192.168.1.220..."},
                {"-b", "badhost"},

                {"-d", "\u0000"},
                // TODO
                // expect failure at Path::of, not at actual file system access
                // thus no need to be file system specific?

                {"-o", ""},
                {"-o", "bad-output-level"},

                {"-p", ""},
                {"-p", "+-"},
                {"-p", "\u0000"},
        };
    }

    @Test(dataProvider = "invalidValue")
    public void testInvalidValue(String option, String value) {
        simpleserver(option, value)
            .assertFailure()
            .resultChecker(r ->
                assertContains(r.output, "Error: invalid value given for "
                        + option + ": " + value)
            );
    }

    @Test
    public void testPortOutOfRange() {
        simpleserver("-p", "65536")  // range 0 to 65535
                .assertFailure()
                .resultChecker(r ->
                        assertContains(r.output, "Error: server config failed: "
                                + "port out of range:65536")
                );
    }

    @Test
    public void testRootNotAbsolute() {
        var root = Path.of("");
        assertFalse(root.isAbsolute());
        simpleserver("-d", root.toString())
                .assertFailure()
                .resultChecker(r ->
                        assertContains(r.output, "Error: server config failed: "
                                + "Path is not absolute: " + root.toString())
                );
    }

    static final Path CWD = Path.of(".").toAbsolutePath().normalize();
    static final Path TEST_DIR = CWD.resolve("dir");
    static final Path TEST_FILE = TEST_DIR.resolve("file.txt");

    @BeforeTest
    public void makeTestDirectoryAndFile() throws IOException {
        if (Files.exists(TEST_DIR))
            FileUtils.deleteFileTreeWithRetry(TEST_DIR);
        Files.createDirectories(TEST_DIR);
        Files.createFile(TEST_FILE);
    }

    @Test
    public void testRootNotADirectory() {
        var file = TEST_FILE.toString();
        assertFalse(Files.isDirectory(TEST_FILE));
        simpleserver("-d", file)
                .assertFailure()
                .resultChecker(r ->
                        assertContains(r.output, "Error: server config failed: "
                                + "Path not a directory: " + file)
                );
    }

    @Test
    public void testRootDoesNotExist() {
        Path root = TEST_DIR.resolve("not/existant/dir");
        assertFalse(Files.exists(root));
        simpleserver("-d", root.toString())
                .assertFailure()
                .resultChecker(r ->
                        assertContains(r.output, "Error: server config failed: "
                                + "Path does not exist: " + root.toString())
                );
    }

    @Test
    public void testRootNotReadable() throws IOException {
        Path root = Files.createDirectories(TEST_DIR.resolve("not/readable/dir"));
        try {
            root.toFile().setReadable(false);
            assertFalse(Files.isReadable(root));
            simpleserver("-d", root.toString())
                    .assertFailure()
                    .resultChecker(r ->
                            assertContains(r.output, "Error: server config failed: "
                                    + "Path is not readable: " + root.toString()));
        } finally {
            root.toFile().setReadable(true);
        }
    }

    @AfterTest
    public void deleteTestDirectory() throws IOException {
        if (Files.exists(TEST_DIR))
            FileUtils.deleteFileTreeWithRetry(TEST_DIR);
    }

    // --- helper methods ---

    static void assertContains(String output, String subString) {
        if (output.contains(subString))
            assertTrue(true);
        else fail("Expected to find [" + subString + "], in output [" + output + "]");
    }

    static Result simpleserver(String... args) {
        var baos = new ByteArrayOutputStream();
        var ps = new PrintStream(baos);
        System.out.println("simpleserver " + Arrays.toString(args));
        int ec = SIMPLESERVER_TOOL.run(ps, ps, args);
        return new Result(ec, baos.toString(UTF_8));
    }

    static void simpleserver(PrintStream ps, String... args) {
        System.out.println("simpleserver " + Arrays.toString(args));
        SIMPLESERVER_TOOL.run(ps, ps, args);
    }

    static class Result {
        final int exitCode;
        final String output;

        Result(int exitValue, String output) {
            this.exitCode = exitValue;
            this.output = output;
        }
        Result assertFailure() { assertTrue(exitCode != 0, output); return this; }
        Result resultChecker(Consumer<Result> r) { r.accept(this); return this; }
    }
}
