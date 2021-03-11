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
 * @summary Negative tests for simpleserver tool
 * @library /test/lib
 * @modules jdk.httpserver
 * @build jdk.test.lib.util.FileUtils
 * @run testng CommandLineNegativeTest
 */

import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.function.Consumer;
import java.util.spi.ToolProvider;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.assertEquals;

public class CommandLineNegativeTest {

    static final ToolProvider SIMPLESERVER_TOOL = ToolProvider.findFirst("simpleserver")
        .orElseThrow(() -> new RuntimeException("simpleserver tool not found"));

    @Test(expectedExceptions = java.lang.NullPointerException.class)
    public void testArgsNull() {
        var baos = new ByteArrayOutputStream();
        var ps = new PrintStream(baos);
        simpleserver(ps, (String[]) null);
    }

    @Test(expectedExceptions = java.lang.NullPointerException.class)
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

    @Test
    public void testBindAddressUnknownHost() {
        simpleserver("-b", "[127.0.0.1]")
            .assertFailure()
            .resultChecker(r ->
                assertContains(r.output, "Error: invalid value given for -b: [127.0.0.1]")
            );
    }

    // TODO
    // bind address unknown host
    // directory
    // output level
    // path

    // ---

    static void assertContains(String output, String subString) {
        if (output.contains(subString))
            assertTrue(true);
        else
            assertTrue(false,"Expected to find [" + subString + "], in output ["
                             + output + "]");
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
