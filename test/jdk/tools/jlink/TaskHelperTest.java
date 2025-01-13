/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.util.*;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import jdk.tools.jlink.internal.PluginRepository;
import jdk.tools.jlink.internal.TaskHelper;
import jdk.tools.jlink.internal.TaskHelper.Option;
import jdk.tools.jlink.internal.TaskHelper.OptionsHelper;
import jdk.tools.jlink.plugin.Plugin;
import jdk.tools.jlink.plugin.ResourcePool;
import jdk.tools.jlink.plugin.ResourcePoolBuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import jdk.tools.jlink.internal.TaskHelper.BadArgs;

/*
 * @test
 * @summary Test TaskHelper option parsing
 * @bug 8303884
 * @modules jdk.jlink/jdk.tools.jlink.internal
 *          jdk.jlink/jdk.tools.jlink.plugin
 * @run junit TaskHelperTest
 */
public class TaskHelperTest {
    private static TaskHelper taskHelper;
    private static OptionsHelper<TaskHelperTest> optionsHelper;

    private static final List<Option<TaskHelperTest>> OPTIONS = List.of(
        new Option<>(true, (task, opt, arg) -> {
            System.out.println(arg);
            mainArgValue = arg;
        }, true, "--main-expecting"),
        new Option<>(false, (task, opt, arg) -> {
            mainFlag = true;
        }, true, "--main-no-arg")
    );

    private static String argValue;
    private static String mainArgValue;
    private static boolean mainFlag = false;

    public record ArgTestCase(String cmdLine, String[] tokens, String pluginArgValue, String mainArgValue, boolean mainFlagSet) {};

    public static class TestPluginWithRawOption implements Plugin {
        @Override
        public ResourcePool transform(ResourcePool in, ResourcePoolBuilder out) {
            return out.build();
        }

        @Override
        public boolean hasArguments() {
            return true;
        }

        @Override
        public boolean hasRawArgument() {
            return true;
        }

        @Override
        public String getName() {
            return "raw-arg-plugin";
        }

        @Override
        public void configure(Map<String, String> config) {
            config.forEach((k, v) -> {
                System.out.println(k + " -> " + v);
            });
            var v = config.get(getName());
            if (v == null)
                throw new AssertionError();
            argValue = v;
        }
    }

    @BeforeAll
    public static void setup() {
        taskHelper = new TaskHelper(TaskHelper.JLINK_BUNDLE);
        optionsHelper = taskHelper.newOptionsHelper(TaskHelperTest.class, OPTIONS.toArray(Option[]::new));
        PluginRepository.registerPlugin(new TestPluginWithRawOption());
    }

    @BeforeEach
    public void reset() {
        argValue = null;
        mainArgValue = null;
        mainFlag = false;
    }

    public static Stream<ArgTestCase> gnuStyleUsages() {
        return Stream.of(
            new ArgTestCase(
                    "--main-expecting=--main-no-arg --main-no-arg",
                    new String[] { "--main-expecting=--main-no-arg", "--main-no-arg" },
                    null,
                    "--main-no-arg",
                    true
            ),
            new ArgTestCase(
                    "--main-expecting ' --main-no-arg' --main-no-arg",
                    new String[] { "--main-expecting", " --main-no-arg", "--main-no-arg" },
                    null,
                    " --main-no-arg",
                    true
            ),
            new ArgTestCase(
                    "--raw-arg-plugin=--main-no-arg --main-no-arg",
                    new String[] { "--raw-arg-plugin=--main-no-arg", "--main-no-arg" },
                    "--main-no-arg",
                    null,
                    true
            ),
            new ArgTestCase(
                    "--raw-arg-plugin ' --main-no-arg' --main-no-arg",
                    new String[] { "--raw-arg-plugin", " --main-no-arg", "--main-no-arg" },
                    " --main-no-arg",
                    null,
                    true
            ),
            new ArgTestCase(
                    "--raw-arg-plugin=--main-expecting=value --main-no-arg",
                    new String[] { "--raw-arg-plugin=--main-expecting=value", "--main-no-arg" },
                    "--main-expecting=value",
                    null,
                    true
            ),
            new ArgTestCase(
                    "--raw-arg-plugin='--main-expecting value' --main-no-arg",
                    new String[] { "--raw-arg-plugin=--main-expecting value", "--main-no-arg" },
                    "--main-expecting value",
                    null,
                    true
            ),
            new ArgTestCase(
                    "--raw-arg-plugin='--main-expecting value' --main-expecting realValue",
                    new String[] { "--raw-arg-plugin=--main-expecting value", "--main-expecting", "realValue" },
                    "--main-expecting value",
                    "realValue",
                    false
            ));
    }

    @ParameterizedTest
    @MethodSource("gnuStyleUsages")
    public void testGnuStyleOptionAsArgValue(ArgTestCase testCase) throws TaskHelper.BadArgs {
        System.out.println("Test cmdline: " + testCase.cmdLine());
        var args = testCase.tokens();
        var remaining = optionsHelper.handleOptions(this, args);
        try {
            // trigger Plugin::configure
            taskHelper.getPluginsConfig(null, null, null);
        } catch (IOException ex) {
            fail("Unexpected IOException");
        }
        assertTrue(remaining.isEmpty());
        assertEquals(testCase.mainFlagSet(), mainFlag);
        assertEquals(testCase.pluginArgValue(), argValue);
        assertEquals(testCase.mainArgValue(), mainArgValue);
    }

    @Test
    public void testGnuStyleOptionAsArgValueMissing() {
            var invalidFormat = new String[][] {
                { "--main-expecting", "--main-no-arg --list", "--main-no-arg" },
                { "--main-expecting", "--main-no-arg", "--main-no-arg" },
                { "--raw-arg-plugin", "--main-no-arg --list", "--main-no-arg" },
                { "--raw-arg-plugin", "--main-no-arg", "--main-no-arg" },
                { "--raw-arg-plugin", "--main-expecting", "value", "--main-no-arg" }
        };

        for (var args: invalidFormat) {
            try {
                optionsHelper.handleOptions(this, args);
                fail("Should get an ambiguous error");
            } catch (BadArgs ex) {
                // expected
            }
        }
    }

    @Test
    public void testRemaining() throws BadArgs {
        String[] args = { "--raw-arg-plugin=--main-expecting", "value", "--main-no-arg" };
        var remaining = optionsHelper.handleOptions(this, args);
        assertEquals(2, remaining.size());
    }
}