/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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
 * @test 8151754 8080883 8160089 8170162 8166581 8172102 8171343 8178023
 * @summary Testing start-up options.
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.jdeps/com.sun.tools.javap
 *          jdk.jshell/jdk.internal.jshell.tool
 * @library /tools/lib
 * @build Compiler toolbox.ToolBox
 * @run testng StartOptionTest
 */

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.function.Consumer;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import jdk.jshell.tool.JavaShellToolBuilder;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

@Test
public class StartOptionTest {

    private ByteArrayOutputStream cmdout;
    private ByteArrayOutputStream cmderr;
    private ByteArrayOutputStream console;
    private ByteArrayOutputStream userout;
    private ByteArrayOutputStream usererr;
    private InputStream cmdInStream;

    private JavaShellToolBuilder builder() {
        // turn on logging of launch failures
        Logger.getLogger("jdk.jshell.execution").setLevel(Level.ALL);
        return JavaShellToolBuilder
                    .builder()
                    .out(new PrintStream(cmdout), new PrintStream(console), new PrintStream(userout))
                    .err(new PrintStream(cmderr), new PrintStream(usererr))
                    .in(cmdInStream, null)
                    .persistence(new HashMap<>())
                    .env(new HashMap<>())
                    .locale(Locale.ROOT);
    }

    private void runShell(String... args) {
        try {
            builder()
                    .run(args);
        } catch (Exception ex) {
            fail("Repl tool died with exception", ex);
        }
    }

    protected void check(ByteArrayOutputStream str, Consumer<String> checkOut, String label) {
        byte[] bytes = str.toByteArray();
        str.reset();
        String out =  new String(bytes, StandardCharsets.UTF_8);
        if (checkOut != null) {
            checkOut.accept(out);
        } else {
            assertEquals("", out, label + ": Expected empty -- ");
        }
    }

    protected void start(Consumer<String> checkCmdOutput,
            Consumer<String> checkUserOutput, Consumer<String> checkError,
            String... args) throws Exception {
        runShell(args);
        check(cmdout, checkCmdOutput, "cmdout");
        check(cmderr, checkError, "cmderr");
        check(console, null, "console");
        check(userout, checkUserOutput, "userout");
        check(usererr, null, "usererr");
    }

    protected void start(String expectedCmdOutput, String expectedError, String... args) throws Exception {
        startWithUserOutput(expectedCmdOutput, "",  expectedError, args);
    }

    private void startWithUserOutput(String expectedCmdOutput, String expectedUserOutput,
            String expectedError, String... args) throws Exception {
        start(
                s -> assertEquals(s.trim(), expectedCmdOutput, "cmdout: "),
                s -> assertEquals(s.trim(), expectedUserOutput, "userout: "),
                s -> assertEquals(s.trim(), expectedError, "cmderr: "),
                args);
    }

    @BeforeMethod
    public void setUp() {
        cmdout  = new ByteArrayOutputStream();
        cmderr  = new ByteArrayOutputStream();
        console = new ByteArrayOutputStream();
        userout = new ByteArrayOutputStream();
        usererr = new ByteArrayOutputStream();
        cmdInStream = new ByteArrayInputStream("/exit\n".getBytes());
    }

    protected String writeToFile(String stuff) throws Exception {
        Compiler compiler = new Compiler();
        Path p = compiler.getPath("doit.repl");
        compiler.writeToFile(p, stuff);
        return p.toString();
    }

    public void testCommandFile() throws Exception {
        String fn = writeToFile("String str = \"Hello \"\n/list\nSystem.out.println(str + str)\n/exit\n");
        startWithUserOutput("1 : String str = \"Hello \";", "Hello Hello", "", "--no-startup", fn, "-s");
    }

    public void testUsage() throws Exception {
        for (String opt : new String[]{"-h", "--help"}) {
            start(s -> {
                assertTrue(s.split("\n").length >= 7, "Not enough usage lines: " + s);
                assertTrue(s.startsWith("Usage:   jshell <options>"), "Unexpect usage start: " + s);
                assertTrue(s.contains("--show-version"), "Expected help: " + s);
                assertFalse(s.contains("Welcome"), "Unexpected start: " + s);
            }, null, null, opt);
        }
    }

    public void testHelpExtra() throws Exception {
        for (String opt : new String[]{"-X", "--help-extra"}) {
            start(s -> {
                assertTrue(s.split("\n").length >= 5, "Not enough help-extra lines: " + s);
                assertTrue(s.contains("--add-exports"), "Expected --add-exports: " + s);
                assertTrue(s.contains("--execution"), "Expected --add-exports: " + s);
                assertFalse(s.contains("Welcome"), "Unexpected start: " + s);
            }, null, null, opt);
        }
    }

    public void testUnknown() throws Exception {
        start(null, null,
              s -> assertEquals(s.trim(), "Unknown option: u"), "-unknown");
        start(null, null,
              s -> assertEquals(s.trim(), "Unknown option: unknown"), "--unknown");
    }

    public void testStartup() throws Exception {
        Compiler compiler = new Compiler();
        Path p = compiler.getPath("file.txt");
        compiler.writeToFile(p);
        start("", "Argument to startup missing.", "--startup");
        start("", "Conflicting options: both --startup and --no-startup were used.", "--no-startup", "--startup", p.toString());
        start("", "Conflicting options: both --startup and --no-startup were used.", "--startup", p.toString(), "--no-startup");
        start("", "Argument to startup missing.", "--no-startup", "--startup");
    }

    public void testStartupFailedOption() throws Exception {
        start(
                s -> assertEquals(s.trim(), "", "cmdout: "),
                s -> assertEquals(s.trim(), "", "userout: "),
                s -> assertTrue(s.contains("Unrecognized option: -hoge-foo-bar"), "cmderr: " + s),
                "-R-hoge-foo-bar");
    }

    public void testStartupUnknown() throws Exception {
        start("", "File 'UNKNOWN' for '--startup' is not found.", "--startup", "UNKNOWN");
        start("", "File 'UNKNOWN' for '--startup' is not found.", "--startup", "DEFAULT", "--startup", "UNKNOWN");
    }

    public void testClasspath() throws Exception {
        for (String cp : new String[] {"--class-path"}) {
            start("", "Only one --class-path option may be used.", cp, ".", "--class-path", ".");
            start("", "Argument to class-path missing.", cp);
        }
    }

    public void testUnknownModule() throws Exception {
        start(
                s -> assertEquals(s.trim(), "", "cmdout: "),
                s -> assertEquals(s.trim(), "", "userout: "),
                s -> assertTrue(s.contains("rror") && s.contains("unKnown"), "cmderr: " + s),
                "--add-modules", "unKnown");
    }

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

    public void testNegFeedbackOption() throws Exception {
        start("", "Argument to feedback missing.", "--feedback");
        start("", "Does not match any current feedback mode: blorp -- --feedback blorp", "--feedback", "blorp");
    }

    public void testVersion() throws Exception {
        start(
                s -> {
                    assertTrue(s.startsWith("jshell"), "unexpected version: " + s);
                    assertFalse(s.contains("Welcome"), "Unexpected start: " + s);
                },
                null, null,
                "--version");
    }

    public void testShowVersion() throws Exception {
        runShell("--show-version");
        check(cmdout,
                s -> {
                    assertTrue(s.startsWith("jshell"), "unexpected version: " + s);
                    assertTrue(s.contains("Welcome"), "Expected start (but got no welcome): " + s);
                },
                "cmdout");
        check(cmderr, null, "cmderr");
        check(console,
                s -> assertTrue(s.trim().startsWith("jshell>"), "Expected prompt, got: " + s),
                "console");
        check(userout, null, "userout");
        check(usererr, null, "usererr");
    }

    @AfterMethod
    public void tearDown() {
        cmdout  = null;
        cmderr  = null;
        console = null;
        userout = null;
        usererr = null;
        cmdInStream = null;
    }
}
