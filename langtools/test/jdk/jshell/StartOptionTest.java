/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @test 8151754 8080883 8160089 8166581
 * @summary Testing start-up options.
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.jdeps/com.sun.tools.javap
 *          jdk.jshell/jdk.internal.jshell.tool
 * @library /tools/lib
 * @build Compiler toolbox.ToolBox
 * @run testng StartOptionTest
 */

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.function.Consumer;

import jdk.internal.jshell.tool.JShellTool;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

@Test
public class StartOptionTest {

    private ByteArrayOutputStream cmdout;
    private ByteArrayOutputStream cmderr;
    private ByteArrayOutputStream console;
    private ByteArrayOutputStream userout;
    private ByteArrayOutputStream usererr;

    private JShellTool getShellTool() {
        return new JShellTool(
                new TestingInputStream(),
                new PrintStream(cmdout),
                new PrintStream(cmderr),
                new PrintStream(console),
                null,
                new PrintStream(userout),
                new PrintStream(usererr),
                new ReplToolTesting.MemoryPreferences(),
                new HashMap<>(),
                Locale.ROOT);
    }

    private void check(ByteArrayOutputStream str, Consumer<String> checkOut, String label) {
        byte[] bytes = str.toByteArray();
        str.reset();
        String out =  new String(bytes, StandardCharsets.UTF_8);
        if (checkOut != null) {
            checkOut.accept(out);
        } else {
            assertEquals("", out, label + ": Expected empty -- ");
        }
    }

    private void start(Consumer<String> checkOutput, Consumer<String> checkError, String... args) throws Exception {
        JShellTool tool = getShellTool();
        tool.start(args);
        check(cmdout, checkOutput, "cmdout");
        check(cmderr, checkError, "cmderr");
        check(console, null, "console");
        check(userout, null, "userout");
        check(usererr, null, "usererr");
    }

    private void start(String expectedOutput, String expectedError, String... args) throws Exception {
        start(s -> assertEquals(s.trim(), expectedOutput, "cmdout: "), s -> assertEquals(s.trim(), expectedError, "cmderr: "), args);
    }

    @BeforeMethod
    public void setUp() {
        cmdout  = new ByteArrayOutputStream();
        cmderr  = new ByteArrayOutputStream();
        console = new ByteArrayOutputStream();
        userout = new ByteArrayOutputStream();
        usererr = new ByteArrayOutputStream();
    }

    @Test
    public void testUsage() throws Exception {
        for (String opt : new String[]{"-h", "--help"}) {
            start(s -> {
                assertTrue(s.split("\n").length >= 7, "Not enough usage lines: " + s);
                assertTrue(s.startsWith("Usage:   jshell <options>"), "Unexpect usage start: " + s);
            }, null, opt);
        }
    }

    @Test
    public void testUnknown() throws Exception {
        start(s -> { },
              s -> assertEquals(s.trim(), "Unknown option: u"), "-unknown");
        start(s -> { },
              s -> assertEquals(s.trim(), "Unknown option: unknown"), "--unknown");
    }

    public void testStartup() throws Exception {
        Compiler compiler = new Compiler();
        Path p = compiler.getPath("file.txt");
        compiler.writeToFile(p);
        start("", "Argument to startup missing.", "--startup");
        start("", "Only one --startup or --no-startup option may be used.", "--startup", p.toString(), "--startup", p.toString());
        start("", "Only one --startup or --no-startup option may be used.", "--no-startup", "--startup", p.toString());
        start("", "Only one --startup or --no-startup option may be used.", "--startup", p.toString(), "--no-startup");
        start("", "Argument to startup missing.", "--no-startup", "--startup");
    }

    public void testStartupFailedOption() throws Exception {
        try {
            start("", "", "-R-hoge-foo-bar");
        } catch (IllegalStateException ex) {
            String s = ex.getMessage();
            assertTrue(s.startsWith("Launching JShell execution engine threw: Failed remote"), s);
            return;
        }
        fail("Expected IllegalStateException");
    }

    public void testStartupUnknown() throws Exception {
        start("", "File 'UNKNOWN' for '--startup' is not found.", "--startup", "UNKNOWN");
    }

    @Test
    public void testClasspath() throws Exception {
        for (String cp : new String[] {"--class-path"}) {
            start("", "Only one --class-path option may be used.", cp, ".", "--class-path", ".");
            start("", "Argument to class-path missing.", cp);
        }
    }

    @Test
    public void testFeedbackOptionConflict() throws Exception {
        start("", "Only one feedback option (--feedback, -q, -s, or -v) may be used.",
                "--feedback", "concise", "--feedback", "verbose");
        start("", "Only one feedback option (--feedback, -q, -s, or -v) may be used.", "--feedback", "concise", "-s");
        start("", "Only one feedback option (--feedback, -q, -s, or -v) may be used.", "--feedback", "verbose", "-q");
        start("", "Only one feedback option (--feedback, -q, -s, or -v) may be used.", "--feedback", "concise", "-v");
        start("", "Only one feedback option (--feedback, -q, -s, or -v) may be used.", "-v", "--feedback", "concise");
        start("", "Only one feedback option (--feedback, -q, -s, or -v) may be used.", "-q", "-v");
        start("", "Only one feedback option (--feedback, -q, -s, or -v) may be used.", "-s", "-v");
        start("", "Only one feedback option (--feedback, -q, -s, or -v) may be used.", "-v", "-q");
        start("", "Only one feedback option (--feedback, -q, -s, or -v) may be used.", "-q", "-s");
    }

    @Test
    public void testNegFeedbackOption() throws Exception {
        start("", "Argument to feedback missing.", "--feedback");
        start("", "Does not match any current feedback mode: blorp -- --feedback blorp", "--feedback", "blorp");
    }

    @Test
    public void testVersion() throws Exception {
        start(s -> assertTrue(s.startsWith("jshell"), "unexpected version: " + s), null, "--version");
    }

    @AfterMethod
    public void tearDown() {
        cmdout  = null;
        cmderr  = null;
        console = null;
        userout = null;
        usererr = null;
    }
}
