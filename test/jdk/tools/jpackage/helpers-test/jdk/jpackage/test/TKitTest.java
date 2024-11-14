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
package jdk.jpackage.test;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import jdk.jpackage.test.Annotations.Parameters;
import jdk.jpackage.test.Annotations.Test;
import jdk.jpackage.test.Functional.ThrowingRunnable;
import static jdk.jpackage.test.Functional.ThrowingRunnable.toRunnable;
import static jdk.jpackage.test.Functional.ThrowingSupplier.toSupplier;

public class TKitTest {

    @Parameters
    public static Collection<Object[]> assertTestsData() {
        List<MethodCallConfig> data = new ArrayList<>();

        var assertFunc = MethodCallConfig.build("assertTrue", boolean.class, String.class);
        data.addAll(List.of(assertFunc.args(true).pass().expectLog("assertTrue()").createForMessage("Catbird")));
        data.addAll(List.of(assertFunc.args(false).fail().expectLog("Failed").createForMessage("Catbird")));

        assertFunc = MethodCallConfig.build("assertFalse", boolean.class, String.class);
        data.addAll(List.of(assertFunc.args(false).pass().expectLog("assertFalse()").createForMessage("Stork")));
        data.addAll(List.of(assertFunc.args(true).fail().expectLog("Failed").createForMessage("Stork")));

        assertFunc = MethodCallConfig.build("assertEquals", String.class, String.class, String.class);
        data.addAll(List.of(assertFunc.args("a", "a").pass().expectLog("assertEquals(a)").createForMessage("Crow")));
        data.addAll(List.of(assertFunc.args("a", "b").fail().expectLog("Expected [a]. Actual [b]").createForMessage("Crow")));

        assertFunc = MethodCallConfig.build("assertEquals", long.class, long.class, String.class);
        data.addAll(List.of(assertFunc.args(7, 7).pass().expectLog("assertEquals(7)").createForMessage("Owl")));
        data.addAll(List.of(assertFunc.args(7, 10).fail().expectLog("Expected [7]. Actual [10]").createForMessage("Owl")));

        assertFunc = MethodCallConfig.build("assertNotEquals", String.class, String.class, String.class);
        data.addAll(List.of(assertFunc.args("a", "b").pass().expectLog("assertNotEquals(a, b)").createForMessage("Tit")));
        data.addAll(List.of(assertFunc.args("a", "a").fail().expectLog("Unexpected [a] value").createForMessage("Tit")));

        assertFunc = MethodCallConfig.build("assertNotEquals", long.class, long.class, String.class);
        data.addAll(List.of(assertFunc.args(7, 10).pass().expectLog("assertNotEquals(7, 10)").createForMessage("Duck")));
        data.addAll(List.of(assertFunc.args(7, 7).fail().expectLog("Unexpected [7] value").createForMessage("Duck")));

        assertFunc = MethodCallConfig.build("assertNull", Object.class, String.class);
        data.addAll(List.of(assertFunc.args((Object) null).pass().expectLog("assertNull()").createForMessage("Ibis")));
        data.addAll(List.of(assertFunc.args("v").fail().expectLog("Unexpected not null value [v]").createForMessage("Ibis")));

        assertFunc = MethodCallConfig.build("assertNotNull", Object.class, String.class);
        data.addAll(List.of(assertFunc.args("v").pass().expectLog("assertNotNull(v)").createForMessage("Pigeon")));
        data.addAll(List.of(assertFunc.args((Object) null).fail().expectLog("Unexpected null value").createForMessage("Pigeon")));

        assertFunc = MethodCallConfig.build("assertStringListEquals", List.class, List.class, String.class);
        data.addAll(List.of(assertFunc.args(List.of(), List.of()).pass().expectLog(
                "assertStringListEquals()").createForMessage("Gull")));

        data.addAll(List.of(assertFunc.args(List.of("a", "b"), List.of("a", "b")).pass().expectLog(
                "assertStringListEquals()",
                "assertStringListEquals(1, a)",
                "assertStringListEquals(2, b)").createForMessage("Pelican")));

        assertFunc.fail().withAutoExpectLogPrefix(false);
        for (var msg : new String[] { "Raven", null }) {
            data.addAll(List.of(assertFunc.args(List.of("a"), List.of("a", "b"), msg).expectLog(
                    concatMessages("TRACE: assertStringListEquals()", msg),
                    "TRACE: assertStringListEquals(1, a)",
                    concatMessages("ERROR: Actual list is longer than expected by 1 elements", msg)
            ).create()));

            data.addAll(List.of(assertFunc.args(List.of("n", "m"), List.of("n"), msg).expectLog(
                    concatMessages("TRACE: assertStringListEquals()", msg),
                    "TRACE: assertStringListEquals(1, n)",
                    concatMessages("ERROR: Actual list is shorter than expected by 1 elements", msg)
            ).create()));

            data.addAll(List.of(assertFunc.args(List.of("a", "b"), List.of("n", "m"), msg).expectLog(
                    concatMessages("TRACE: assertStringListEquals()", msg),
                    concatMessages("ERROR: (1) Expected [a]. Actual [n]", msg)
            ).create()));
        }

        return data.stream().map(v -> {
            return new Object[]{v};
        }).toList();
    }

    public record MethodCallConfig(Method method, Object[] args, boolean expectFail, String[] expectLog) {
        @Override
        public String toString() {
            return String.format("%s%s%s", method.getName(), Arrays.toString(args), expectFail ? "!" : "");
        }

        static Builder build(String name, Class<?> ... parameterTypes) {
            return new Builder(name, parameterTypes);
        }

        private static class Builder {
            Builder(Method method) {
                Objects.requireNonNull(method);
                this.method = method;
            }

            Builder(String name, Class<?> ... parameterTypes) {
                method = toSupplier(() -> TKit.class.getMethod(name, parameterTypes)).get();
            }

            MethodCallConfig create() {
                String[] effectiveExpectLog;
                if (!withAutoExpectLogPrefix) {
                    effectiveExpectLog = expectLog;
                } else {
                    var prefix = expectFail ? "ERROR: " : "TRACE: ";
                    effectiveExpectLog = Stream.of(expectLog).map(line -> {
                        return prefix + line;
                    }).toArray(String[]::new);
                }
                return new MethodCallConfig(method, args, expectFail, effectiveExpectLog);
            }

            MethodCallConfig[] createForMessage(String msg) {
                return Arrays.asList(msg, null).stream().map(curMsg -> {
                    var builder = new Builder(method);
                    builder.expectFail = expectFail;
                    builder.withAutoExpectLogPrefix = withAutoExpectLogPrefix;
                    builder.args = Stream.concat(Stream.of(args), Stream.of(curMsg)).toArray();
                    builder.expectLog = Arrays.copyOf(expectLog, expectLog.length);
                    builder.expectLog[0] = concatMessages(builder.expectLog[0], curMsg);
                    return builder.create();
                }).toArray(MethodCallConfig[]::new);
            }

            Builder fail() {
                expectFail = true;
                return this;
            }

            Builder pass() {
                expectFail = false;
                return this;
            }

            Builder args(Object ... v) {
                args = v;
                return this;
            }

            Builder expectLog(String expectLogFirstStr, String ... extra) {
                expectLog = Stream.concat(Stream.of(expectLogFirstStr), Stream.of(extra)).toArray(String[]::new);
                return this;
            }

            Builder withAutoExpectLogPrefix(boolean v) {
                withAutoExpectLogPrefix = v;
                return this;
            }

            private final Method method;
            private Object[] args = new Object[0];
            private boolean expectFail;
            private String[] expectLog;
            private boolean withAutoExpectLogPrefix = true;
        }
    }

    public TKitTest(MethodCallConfig methodCall) {
        this.methodCall = methodCall;
    }

    @Test
    public void test() {
        runAssertWithExpectedLogOutput(() -> {
            methodCall.method.invoke(null, methodCall.args);
        }, methodCall.expectFail, methodCall.expectLog);
    }

    private static void runAssertWithExpectedLogOutput(ThrowingRunnable action,
            boolean expectFail, String... expectLogStrings) {
        runWithExpectedLogOutput(() -> {
            TKit.assertAssert(!expectFail, toRunnable(action));
        }, expectLogStrings);
    }

    private static void runWithExpectedLogOutput(ThrowingRunnable action,
            String... expectLogStrings) {
        final var buf = new ByteArrayOutputStream();
        try (PrintStream ps = new PrintStream(buf, true, StandardCharsets.UTF_8)) {
            TKit.withExtraLogStream(action, ps);
        } finally {
            toRunnable(() -> {
                var output = new BufferedReader(new InputStreamReader(
                        new ByteArrayInputStream(buf.toByteArray()),
                        StandardCharsets.UTF_8)).lines().map(line -> {
                            // Skip timestamp
                            return line.substring(LOG_MSG_TIMESTAMP_LENGTH);
                        }).toList();
                if (output.size() == 1 && expectLogStrings.length == 1) {
                    TKit.assertEquals(expectLogStrings[0], output.get(0), null);
                } else {
                    TKit.assertStringListEquals(List.of(expectLogStrings), output, null);
                }
            }).run();
        }
    }

    private static String concatMessages(String msg, String msg2) {
        if (msg2 != null && !msg2.isBlank()) {
            return msg + ": " + msg2;
        }
        return msg;
    }

    private final MethodCallConfig methodCall;

    private static final int LOG_MSG_TIMESTAMP_LENGTH = "[HH:mm:ss.SSS] ".length();
}
