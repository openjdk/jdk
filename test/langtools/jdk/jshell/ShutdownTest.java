/*
 * Copyright (c) 2015, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Shutdown tests
 * @build KullaTesting TestingInputStream
 * @run junit ShutdownTest
 */

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Consumer;

import jdk.jshell.JShell;
import jdk.jshell.JShell.Subscription;
import org.junit.jupiter.api.Assertions;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

public class ShutdownTest extends KullaTesting {

    int shutdownCount;

    void shutdownCounter(JShell state) {
        ++shutdownCount;
    }

    @Test //TODO 8139873
    @Disabled
    public void testExit() {
        shutdownCount = 0;
        getState().onShutdown(this::shutdownCounter);
        assertEval("System.exit(1);");
        assertEquals(1, shutdownCount);
    }

    @Test
    public void testCloseCallback() {
        shutdownCount = 0;
        getState().onShutdown(this::shutdownCounter);
        getState().close();
        assertEquals(1, shutdownCount);
    }

    @Test
    public void testCloseUnsubscribe() {
        shutdownCount = 0;
        Subscription token = getState().onShutdown(this::shutdownCounter);
        getState().unsubscribe(token);
        getState().close();
        assertEquals(0, shutdownCount);
    }

    @Test
    public void testTwoShutdownListeners() {
        ShutdownListener listener1 = new ShutdownListener();
        ShutdownListener listener2 = new ShutdownListener();
        Subscription subscription1 = getState().onShutdown(listener1);
        Subscription subscription2 = getState().onShutdown(listener2);
        getState().unsubscribe(subscription1);
        getState().close();

        assertEquals(0, listener1.getEvents(), "Checking got events");
        assertEquals(1, listener2.getEvents(), "Checking got events");

        getState().close();

        assertEquals(0, listener1.getEvents(), "Checking got events");
        assertEquals(1, listener2.getEvents(), "Checking got events");

        getState().unsubscribe(subscription2);
    }

    @Test
    public void testCloseException() {
        Assertions.assertThrows(IllegalStateException.class, () -> {
            getState().close();
            getState().eval("45");
        });
    }

    @Test //TODO 8139873
    @Disabled
    public void testShutdownException() {
        Assertions.assertThrows(IllegalStateException.class, () -> {
            assertEval("System.exit(0);");
            getState().eval("45");
        });
    }

    @Test
    public void testNullCallback() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            getState().onShutdown(null);
        });
    }

    @Test
    public void testSubscriptionAfterClose() {
        Assertions.assertThrows(IllegalStateException.class, () -> {
            getState().close();
            getState().onShutdown(e -> {});
        });
    }

    @Test //TODO 8139873
    @Disabled
    public void testSubscriptionAfterShutdown() {
        Assertions.assertThrows(IllegalStateException.class, () -> {
            assertEval("System.exit(0);");
            getState().onShutdown(e -> {});
        });
    }

    @Test
    public void testRunShutdownHooks() throws IOException {
        Path temporary = Paths.get("temp");
        Files.newOutputStream(temporary).close();
        assertEval("import java.io.*;");
        assertEval("import java.nio.file.*;");
        assertEval("""
                        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                            try {
                                Files.delete(Paths.get("$TEMPORARY"));
                            } catch (IOException ex) {
                                //ignored
                            }
                        }))
                        """.replace("$TEMPORARY", temporary.toAbsolutePath()
                                                           .toString()
                                                           .replace("\\", "\\\\")));
        getState().close();
        assertFalse(Files.exists(temporary));
    }

    private Method currentTestMethod;

    @BeforeEach
    public void setUp(TestInfo testInfo) {
        currentTestMethod = testInfo.getTestMethod().orElseThrow();
        super.setUp();
    }

    @BeforeEach
    public void setUp() {
    }

    @Override
    public void setUp(Consumer<JShell.Builder> bc) {
        Consumer<JShell.Builder> augmentedBuilder = switch (currentTestMethod.getName()) {
            case "testRunShutdownHooks" -> builder -> {
                builder.executionEngine(Presets.TEST_STANDARD_EXECUTION);
                bc.accept(builder);
            };
            default -> bc;
        };
        super.setUp(augmentedBuilder);
    }

    private static class ShutdownListener implements Consumer<JShell> {
        private int count;

        @Override
        public void accept(JShell shell) {
            ++count;
        }

        public int getEvents() {
            return count;
        }
    }
}
