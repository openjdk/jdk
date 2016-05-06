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
 * @test 8151754
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
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
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

    private ByteArrayOutputStream out;
    private ByteArrayOutputStream err;

    private JShellTool getShellTool() {
        class NoOutputAllowedStream extends OutputStream {
            private final String label;
            NoOutputAllowedStream(String label) {
               this.label = label;
            }
            @Override
            public void write(int b) { fail("Unexpected output to: " + label); }
        }
        return new JShellTool(
                new TestingInputStream(),
                new PrintStream(out),
                new PrintStream(err),
                new PrintStream(new NoOutputAllowedStream("console")),
                new TestingInputStream(),
                new PrintStream(new NoOutputAllowedStream("userout")),
                new PrintStream(new NoOutputAllowedStream("usererr")),
                new ReplToolTesting.MemoryPreferences(),
                Locale.ROOT);
    }

    private String getOutput() {
        byte[] bytes = out.toByteArray();
        out.reset();
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private String getError() {
        byte[] bytes = err.toByteArray();
        err.reset();
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private void start(Consumer<String> checkOutput, Consumer<String> checkError, String... args) throws Exception {
        JShellTool tool = getShellTool();
        tool.start(args);
        if (checkOutput != null) {
            checkOutput.accept(getOutput());
        } else {
            assertEquals("", getOutput(), "Output: ");
        }
        if (checkError != null) {
            checkError.accept(getError());
        } else {
            assertEquals("", getError(), "Error: ");
        }
    }

    private void start(String expectedOutput, String expectedError, String... args) throws Exception {
        start(s -> assertEquals(s.trim(), expectedOutput, "Output: "), s -> assertEquals(s.trim(), expectedError, "Error: "), args);
    }

    @BeforeMethod
    public void setUp() {
        out = new ByteArrayOutputStream();
        err = new ByteArrayOutputStream();
    }

    @Test
    public void testUsage() throws Exception {
        start(s -> {
            assertTrue(s.split("\n").length >= 7, s);
            assertTrue(s.startsWith("Usage:   jshell <options>"), s);
        },  null, "-help");
    }

    @Test
    public void testUnknown() throws Exception {
        start(s -> {
            assertTrue(s.split("\n").length >= 7, s);
            assertTrue(s.startsWith("Usage:   jshell <options>"), s);
        }, s -> assertEquals(s.trim(), "Unknown option: -unknown"), "-unknown");
    }

    @Test(enabled = false) // TODO 8080883
    public void testStartup() throws Exception {
        Compiler compiler = new Compiler();
        Path p = compiler.getPath("file.txt");
        compiler.writeToFile(p);
        start("", "'-startup' requires a filename argument.", "-startup");
        start("", "Conflicting -startup or -nostartup option.", "-startup", p.toString(), "-startup", p.toString());
        start("", "Conflicting -startup or -nostartup option.", "-nostartup", "-startup", p.toString());
        start("", "Conflicting -startup or -nostartup option.", "-startup", p.toString(), "-nostartup");
    }

    @Test
    public void testClasspath() throws Exception {
        for (String cp : new String[] {"-cp", "-classpath"}) {
            start("", "Conflicting -classpath option.", cp, ".", "-classpath", ".");
            start("", "Argument to -classpath missing.", cp);
        }
    }

    @Test
    public void testNegFeedbackOption() throws Exception {
        start("", "Argument to -feedback missing. Mode required.", "-feedback");
        start("", "Does not match any current feedback mode: blorp -- -feedback blorp", "-feedback", "blorp");
    }

    @Test
    public void testVersion() throws Exception {
        start(s -> assertTrue(s.startsWith("jshell")), null, "-version");
    }

    @AfterMethod
    public void tearDown() {
        out = null;
        err = null;
    }
}
