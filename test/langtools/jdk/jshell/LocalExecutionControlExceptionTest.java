/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8299934
 * @summary Test LocalExecutionControl
 * @run junit LocalExecutionControlExceptionTest
 */

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import jdk.jshell.EvalException;
import jdk.jshell.JShell;
import jdk.jshell.execution.LocalExecutionControlProvider;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;

public class LocalExecutionControlExceptionTest {

    @Test
    @ExtendWith(NoUncaughtExceptionHandleInterceptor.class)
    public void testUncaughtExceptions() throws InterruptedException {
        List<Throwable> excs = new ArrayList<>();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Thread.setDefaultUncaughtExceptionHandler((t, ex) -> excs.add(ex));
        try (var jshell = JShell.builder()
                                .err(new PrintStream(out))
                                .out(new PrintStream(out))
                                .executionEngine(new LocalExecutionControlProvider(), Map.of())
                                .build()) {
            {
                var events = jshell.eval("throw new java.io.IOException();");
                Assertions.assertEquals(1, events.size());
                Assertions.assertNotNull(events.get(0).exception());
                Assertions.assertTrue(events.get(0).exception() instanceof EvalException);
                Assertions.assertEquals("java.io.IOException",
                                        ((EvalException) events.get(0).exception()).getExceptionClassName());
                Assertions.assertEquals(0, excs.size());
                Assertions.assertEquals(0, out.size());
            }
            {
                var events = jshell.eval("throw new IllegalAccessException();");
                Assertions.assertEquals(1, events.size());
                Assertions.assertNotNull(events.get(0).exception());
                Assertions.assertTrue(events.get(0).exception() instanceof EvalException);
                Assertions.assertEquals("java.lang.IllegalAccessException",
                                        ((EvalException) events.get(0).exception()).getExceptionClassName());
                Assertions.assertEquals(0, excs.size());
                Assertions.assertEquals(0, out.size());
            }
            jshell.eval("""
                        <T extends Throwable> T t2(Throwable t) throws T {
                            throw (T) t;
                        }
                        """);
            jshell.eval("""
                        void t(Throwable t) {
                            throw t2(t);
                        }
                        """);
            {
                var events = jshell.eval("""
                                         {
                                             var t = new Thread(() -> t(new java.io.IOException()));
                                             t.start();
                                             t.join();
                                         }
                                         """);
                Assertions.assertEquals(1, events.size());
                Assertions.assertNull(events.get(0).exception());
                Assertions.assertEquals(0, excs.size());
                Assertions.assertEquals(0, out.size());
            }
            {
                var events = jshell.eval("""
                                         {
                                             var t = new Thread(() -> t(new IllegalAccessException()));
                                             t.start();
                                             t.join();
                                         }
                                         """);
                Assertions.assertEquals(1, events.size());
                Assertions.assertNull(events.get(0).exception());
                Assertions.assertEquals(0, excs.size());
                Assertions.assertEquals(0, out.size());
            }
            Thread outsideOfJShell = new Thread(() -> {
                t(new IOException());
            });
            outsideOfJShell.start();
            outsideOfJShell.join();
            Assertions.assertEquals(1, excs.size());
        }
    }

    void t(Throwable t) {
        throw t2(t);
    }

    <T extends Throwable> T t2(Throwable t) throws T {
        throw (T) t;
    }

    public static final class NoUncaughtExceptionHandleInterceptor implements InvocationInterceptor {
        public void interceptTestMethod(Invocation<Void> invocation,
                                        ReflectiveInvocationContext<Method> invocationContext,
                                        ExtensionContext extensionContext) throws Throwable {
            Throwable[] exc = new Throwable[1];
            //the tests normally run in a ThreadGroup which handles uncaught exception
            //run in a ThreadGroup which does not handle the uncaught exceptions, and let them
            //pass to the default uncaught handler for the test:
            var thread = new Thread(Thread.currentThread().getThreadGroup().getParent(), "test-group") {
                public void run() {
                    try {
                        invocation.proceed();
                    } catch (Throwable ex) {
                        exc[0] = ex;
                    }
                }
            };
            thread.start();
            thread.join();
            if (exc[0] != null) {
                throw exc[0];
            }
        }
    }
}
