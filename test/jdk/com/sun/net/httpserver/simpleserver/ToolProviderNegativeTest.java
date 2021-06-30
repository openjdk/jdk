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
 * @summary Negative tests for SimpleFileServerToolProvider
 * @library /test/lib
 * @modules jdk.httpserver
 * @build jdk.test.lib.util.FileUtils jdk.test.lib.Platform
 * @run testng/othervm ToolProviderNegativeTest
 */

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.function.Consumer;
import java.util.spi.ToolProvider;
import jdk.test.lib.Platform;
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
            .assertExternalTermination()
            .resultChecker(r ->
                assertContains(r.output, "Error: unknown option: --badOption")
            );
    }

    @DataProvider(name = "tooManyOptionArgs")
    public Object[][] tooManyOptionArgs() {
        return new Object[][] {
                {"-b", "localhost"},
                {"-d", "/some/path"},
                {"-o", "none"},
                {"-p", "0"},
                {"--bind-address", "localhost"},
                {"--directory", "/some/path"},
                {"--output", "none"},
                {"--port", "0"}
                // doesn't fail for -h option
        };
    }

    @Test(dataProvider = "tooManyOptionArgs")
    public void testTooManyOptionArgs(String opt, String arg) {
        simpleserver(opt, arg, arg)
            .assertExternalTermination()
            .resultChecker(r ->
                assertContains(r.output, "Error: unknown option: " + arg)
            );
    }

    @DataProvider(name = "noArg")
    public Object[][] noArg() {
        return new Object[][] {
                {"-b"},
                {"-d"},
                {"-o"},
                {"-p"},
                {"--bind-address"},
                {"--directory"},
                {"--output"},
                {"--port"}
                // doesn't fail for -h option
        };
    }

    @Test(dataProvider = "noArg")
    public void testNoArg(String opt) {
        simpleserver(opt)
            .assertExternalTermination()
            .resultChecker(r ->
                assertContains(r.output, "Error: no value given for " + opt)
            );
    }

    @DataProvider(name = "invalidValue")
    public Object[][] invalidValue() {
        return new Object[][] {
                {"-b", "[127.0.0.1]"},
                {"-b", "badhost"},
                {"--bind-address", "192.168.1.220..."},

                {"-d", "\u0000"},
                // TODO: expect failure at Path::of, not at actual file system access
                //  need to be file system specific?

                {"-o", "bad-output-level"},
                {"--output", "bad-output-level"},

                {"-p", "+-"},
                {"--port", "\u0000"},
        };
    }

    @Test(dataProvider = "invalidValue")
    public void testInvalidValue(String opt, String value) {
        simpleserver(opt, value)
            .assertExternalTermination()
            .resultChecker(r ->
                assertContains(r.output, "Error: invalid value given for "
                        + opt + ": " + value)
            );
    }

    @DataProvider
    public Object[][] portOptions() { return new Object[][] {{"-p"}, {"--port"}}; }

    @Test(dataProvider = "portOptions")
    public void testPortOutOfRange(String opt) {
        simpleserver(opt, "65536")  // range 0 to 65535
                .assertExternalTermination()
                .resultChecker(r ->
                        assertContains(r.output, "Error: server config failed: "
                                + "port out of range:65536")
                );
    }

    @DataProvider
    public Object[][] dirOptions() { return new Object[][] {{"-d"}, {"--directory"}}; }

    @Test(dataProvider = "dirOptions")
    public void testRootNotAbsolute(String opt) {
        var root = Path.of(".");
        assertFalse(root.isAbsolute());
        simpleserver(opt, root.toString())
                .assertExternalTermination()
                .resultChecker(r ->
                        assertContains(r.output, "Error: server config failed: "
                                + "Path is not absolute: ")
                );
    }

    @Test(dataProvider = "dirOptions")
    public void testRootNotADirectory(String opt) {
        var file = TEST_FILE.toString();
        assertFalse(Files.isDirectory(TEST_FILE));
        simpleserver(opt, file)
                .assertExternalTermination()
                .resultChecker(r ->
                        assertContains(r.output, "Error: server config failed: "
                                + "Path is not a directory: " + file)
                );
    }

    @Test(dataProvider = "dirOptions")
    public void testRootDoesNotExist(String opt) {
        Path root = TEST_DIR.resolve("not/existent/dir");
        assertFalse(Files.exists(root));
        simpleserver(opt, root.toString())
                .assertExternalTermination()
                .resultChecker(r ->
                        assertContains(r.output, "Error: server config failed: "
                                + "Path does not exist: " + root.toString())
                );
    }

    @Test(dataProvider = "dirOptions")
    public void testRootNotReadable(String opt) throws IOException {
        Path root = Files.createDirectories(TEST_DIR.resolve("not/readable/dir"));
        if (!Platform.isWindows()) {  // not applicable to Windows
                                      // reason: cannot revoke an owner's read
                                      // access to a directory that was created
                                      // by that owner
            try {
                root.toFile().setReadable(false, false);
                assertFalse(Files.isReadable(root));
                simpleserver(opt, root.toString())
                        .assertExternalTermination()
                        .resultChecker(r ->
                                assertContains(r.output, "Error: server config failed: "
                                        + "Path is not readable: " + root.toString()));
            } finally {
                root.toFile().setReadable(true, false);
            }
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

    static record Result(int exitCode, String output) {
        Result assertExternalTermination() { assertTrue(exitCode != 0, output); return this; }
        Result resultChecker(Consumer<Result> r) { r.accept(this); return this; }
    }
}
