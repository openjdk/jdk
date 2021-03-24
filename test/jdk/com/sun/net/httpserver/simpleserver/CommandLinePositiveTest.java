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
 * @summary Negative tests for simpleserver command-line tool
 * @library /test/lib
 * @modules jdk.httpserver
 * @build jdk.test.lib.util.FileUtils
 * @run testng/othervm CommandLineNegativeTest
 */

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.function.Consumer;
import jdk.test.lib.util.FileUtils;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

public class CommandLineNegativeTest {

    static final String JAVA = System.getProperty("java.home") + "/bin/java";
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

    @DataProvider(name = "badOption")
    public Object[][] badOption() {
        return new Object[][] {
                {"--badOption"},
                {"null"}
        };
    }

    @Test(dataProvider = "badOption")
    public void testBadOption(String option) throws Exception {
        simpleserver(JAVA, "-m", "jdk.httpserver", option)
                .resultChecker(r ->
                        assertContains(r.output, "Error: unknown option(s): " + option)
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
    public void testTooManyOptionArgs(String option, String arg) throws Exception {
        simpleserver(JAVA, "-m", "jdk.httpserver", option, arg, arg)
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
    public void testNoArg(String option) throws Exception {
        simpleserver(JAVA, "-m", "jdk.httpserver", option)
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

//                {"-d", ""},
                // TODO: expect failure at Path::of, not at actual file system access
                //  need to be file system specific?

                {"-o", ""},
                {"-o", "bad-output-level"},

                {"-p", ""},
                {"-p", "+-"},
        };
    }

    @Test(dataProvider = "invalidValue")
    public void testInvalidValue(String option, String value) throws Exception {
        simpleserver(JAVA, "-m", "jdk.httpserver", option, value)
                .resultChecker(r ->
                        assertContains(r.output, "Error: invalid value given for "
                                + option + ": " + value)
                );
    }

    @Test
    public void testPortOutOfRange() throws Exception {
        simpleserver(JAVA, "-m", "jdk.httpserver", "-p", "65536")  // range 0 to 65535
                .resultChecker(r ->
                        assertContains(r.output, "Error: server config failed: "
                                + "port out of range:65536")
                );
    }

    @Test
    public void testRootNotAbsolute() throws Exception {
        var root = Path.of("");
        assertFalse(root.isAbsolute());
        simpleserver(JAVA, "-m", "jdk.httpserver", "-d", root.toString())
                .resultChecker(r ->
                        assertContains(r.output, "Error: server config failed: "
                                + "Path is not absolute: " + root.toString())
                );
    }

    @Test
    public void testRootNotADirectory() throws Exception {
        var file = TEST_FILE.toString();
        assertFalse(Files.isDirectory(TEST_FILE));
        simpleserver(JAVA, "-m", "jdk.httpserver", "-d", file)
                .resultChecker(r ->
                        assertContains(r.output, "Error: server config failed: "
                                + "Path not a directory: " + file)
                );
    }

    @Test
    public void testRootDoesNotExist() throws Exception {
        Path root = TEST_DIR.resolve("not/existant/dir");
        assertFalse(Files.exists(root));
        simpleserver(JAVA, "-m", "jdk.httpserver", "-d", root.toString())
                .resultChecker(r ->
                        assertContains(r.output, "Error: server config failed: "
                                + "Path does not exist: " + root.toString())
                );
    }

    @Test
    public void testRootNotReadable() throws Exception {
        Path root = Files.createDirectories(TEST_DIR.resolve("not/readable/dir"));
        try {
            root.toFile().setReadable(false);
            assertFalse(Files.isReadable(root));
            simpleserver(JAVA, "-m", "jdk.httpserver", "-d", root.toString())
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

    static Result simpleserver(String... args) throws Exception {
        System.out.println("simpleserver " + Arrays.toString(args));
        var p = new ProcessBuilder(args)
                .redirectErrorStream(true)
                .directory(TEST_DIR.toFile())
                .start();
        var in = new BufferedInputStream(p.getInputStream());
        var out = new ByteArrayOutputStream();
        var t = new Thread("read-server-output") {
            @Override
            public void run() {
                try {
                    in.transferTo(out);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        };
        t.start();
        Thread.sleep(5000);
//        p.destroy(); // only needed for positive testing
        t.join();
        return new Result(out.toString(UTF_8));
    }

    static record Result(String output) {
        Result resultChecker(Consumer<Result> r) { r.accept(this); return this; }
    }
}
