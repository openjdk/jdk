/*
 * Copyright (c) 2016, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.ServiceLoader;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.tools.Tool;
import jdk.internal.jshell.tool.JShellToolProvider;
import jdk.jshell.tool.JavaShellToolBuilder;
import org.testng.annotations.Test;
import static org.testng.Assert.assertTrue;

/*
 * @test
 * @bug 8170044 8171343 8179856 8185840 8190383 8353332
 * @summary Test ServiceLoader launching of jshell tool
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.jdeps/com.sun.tools.javap
 *          jdk.jshell/jdk.internal.jshell.tool:+open
 *          jdk.jshell/jdk.internal.jshell.tool.resources:+open
 * @library /tools/lib
 * @build Compiler toolbox.ToolBox
 * @run testng/othervm --patch-module jdk.jshell=${test.src}/StartOptionTest-module-patch ToolProviderTest
 */
@Test
public class ToolProviderTest extends StartOptionTest {

    // Through the provider, the console and console go to command out (we assume,
    // because it works with the current tests) that console and user output are
    // after command out.
    @Override
    protected void startExCoUoCeCn(int expectedExitCode,
            String expectedCmdOutput,
            String expectedUserOutput,
            String expectedError,
            String expectedConsole,
            String... args) {
        super.startExCoUoCeCn(expectedExitCode,
                (expectedCmdOutput  == null? "" : expectedCmdOutput) +
                (expectedConsole    == null? "" : expectedConsole) +
                (expectedUserOutput == null? "" : expectedUserOutput),
                null, expectedError, null, args);
    }

    @Override
    protected void startCheckUserOutput(Consumer<String> checkUserOutput, String... args) {
        runShell(args);
        check(cmdout, checkUserOutput, "userout");
        check(usererr, null, "usererr");
    }

    @Override
    protected void startCheckCommandUserOutput(Consumer<String> checkCommandOutput,
            Consumer<String> checkUserOutput,
            Consumer<String> checkCombinedCommandUserOutput,
            String... args) {
        runShell(args);
        check(cmdout, checkCombinedCommandUserOutput, "userout");
        check(usererr, null, "usererr");
    }

    @Override
    protected int runShell(String... args) {
        //make sure the JShell running during the test is not using persisted preferences from the machine:
        Function<JavaShellToolBuilder, JavaShellToolBuilder> prevAugmentedToolBuilder =
                getAndSetAugmentedToolBuilder(builder -> builder.persistence(getThisTestPersistence()));
        try {
            ServiceLoader<Tool> sl = ServiceLoader.load(Tool.class);
            for (Tool provider : sl) {
                if (provider.name().equals("jshell")) {
                    return provider.run(cmdInStream, cmdout, cmderr, args);
                }
            }
            throw new AssertionError("Repl tool not found by ServiceLoader: " + sl);
        } finally {
            getAndSetAugmentedToolBuilder(prevAugmentedToolBuilder);
        }
    }

    // Test --show-version
    @Override
    public void testShowVersion() {
        startCo(s -> {
            assertTrue(s.startsWith("jshell "), "unexpected version: " + s);
            assertTrue(s.contains("Welcome"), "Expected start (but got no welcome): " + s);
            assertTrue(s.trim().contains("jshell>"), "Expected prompt, got: " + s);
        },
                "--show-version");
    }

    private Function<JavaShellToolBuilder, JavaShellToolBuilder> getAndSetAugmentedToolBuilder
        (Function<JavaShellToolBuilder, JavaShellToolBuilder> augmentToolBuilder) {
        try {
            Field f = JShellToolProvider.class.getDeclaredField("augmentToolBuilder");

            f.setAccessible(true);

            Function<JavaShellToolBuilder, JavaShellToolBuilder> prev =
                    (Function<JavaShellToolBuilder, JavaShellToolBuilder>) f.get(null);

            f.set(null, augmentToolBuilder);

            return prev;
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
