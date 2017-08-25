/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.debug.test;

import static org.graalvm.compiler.debug.DebugContext.NO_DESCRIPTION;
import static org.graalvm.compiler.debug.DebugContext.NO_GLOBAL_METRIC_VALUES;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.Formatter;
import java.util.List;

import org.graalvm.compiler.debug.Assertions;
import org.graalvm.compiler.debug.CounterKey;
import org.graalvm.compiler.debug.DebugCloseable;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.DebugContext.Scope;
import org.graalvm.compiler.debug.DebugDumpHandler;
import org.graalvm.compiler.debug.DebugHandler;
import org.graalvm.compiler.debug.DebugHandlersFactory;
import org.graalvm.compiler.debug.DebugOptions;
import org.graalvm.compiler.debug.DebugVerifyHandler;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.util.EconomicMap;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

@SuppressWarnings("try")
public class DebugContextTest {

    static class DebugContextSetup {
        final Formatter dumpOutput = new Formatter();
        final Formatter verifyOutput = new Formatter();
        final ByteArrayOutputStream logOutput = new ByteArrayOutputStream();
        DebugHandlersFactory handlers = new DebugHandlersFactory() {
            @Override
            public List<DebugHandler> createHandlers(OptionValues options) {
                return Arrays.asList(new DebugDumpHandler() {
                    @Override
                    public void dump(DebugContext ignore, Object object, String format, Object... arguments) {
                        dumpOutput.format("Dumping %s with label \"%s\"%n", object, String.format(format, arguments));
                    }
                }, new DebugVerifyHandler() {
                    @Override
                    public void verify(DebugContext ignore, Object object, String format, Object... args) {
                        verifyOutput.format("Verifying %s with label \"%s\"%n", object, String.format(format, args));
                    }
                });
            }
        };

        DebugContext openDebugContext(OptionValues options) {
            return DebugContext.create(options, NO_DESCRIPTION, NO_GLOBAL_METRIC_VALUES, new PrintStream(logOutput), Collections.singletonList(handlers));

        }
    }

    @Test
    public void testDisabledScopes() {
        OptionValues options = new OptionValues(EconomicMap.create());
        DebugContextSetup setup = new DebugContextSetup();
        try (DebugContext debug = setup.openDebugContext(options);
                        DebugContext.Scope d = debug.scope("TestDisabledScoping")) {
            for (int level = DebugContext.BASIC_LEVEL; level <= DebugContext.VERY_DETAILED_LEVEL; level++) {
                debug.dump(level, "an object", "at level %d", level);
                debug.verify("an object", "at level %d", level);
                debug.log(level, "log statement at level %d", level);
            }
        }
        String log = setup.logOutput.toString();
        String dumpOutput = setup.dumpOutput.toString();
        String verifyOutput = setup.verifyOutput.toString();
        Assert.assertTrue(log, log.isEmpty());
        Assert.assertTrue(dumpOutput, dumpOutput.isEmpty());
        Assert.assertTrue(verifyOutput, verifyOutput.isEmpty());
    }

    @Test
    public void testDumping() {
        for (int level = DebugContext.BASIC_LEVEL; level <= DebugContext.VERY_DETAILED_LEVEL; level++) {
            OptionValues options = new OptionValues(EconomicMap.create());
            options = new OptionValues(options, DebugOptions.Dump, "Scope" + level + ":" + level);
            DebugContextSetup setup = new DebugContextSetup();
            try (DebugContext debug = setup.openDebugContext(options);
                            DebugContext.Scope s0 = debug.scope("TestDumping")) {
                try (DebugContext.Scope s1 = debug.scope("Scope1")) {
                    try (DebugContext.Scope s2 = debug.scope("Scope2")) {
                        try (DebugContext.Scope s3 = debug.scope("Scope3")) {
                            try (DebugContext.Scope s4 = debug.scope("Scope4")) {
                                try (DebugContext.Scope s5 = debug.scope("Scope5")) {
                                    debug.dump(level, "an object", "at level %d", level);
                                }
                            }
                        }
                    }
                }

            }

            String expect = String.format("Dumping an object with label \"at level %d\"%n", level);
            String dump = setup.dumpOutput.toString();
            Assert.assertEquals(expect, dump);
        }
    }

    @Test
    public void testLogging() throws IOException {
        OptionValues options = new OptionValues(EconomicMap.create());
        options = new OptionValues(options, DebugOptions.Log, ":5");
        DebugContextSetup setup = new DebugContextSetup();
        try (DebugContext debug = setup.openDebugContext(options)) {
            for (int level = DebugContext.BASIC_LEVEL; level <= DebugContext.VERY_DETAILED_LEVEL; level++) {
                try (DebugContext.Scope s0 = debug.scope("TestLogging")) {
                    debug.log(level, "log statement at level %d", level);
                    try (DebugContext.Scope s1 = debug.scope("Level1")) {
                        debug.log(level, "log statement at level %d", level);
                        try (DebugContext.Scope s2 = debug.scope("Level2")) {
                            debug.log(level, "log statement at level %d", level);
                            try (DebugContext.Scope s3 = debug.scope("Level3")) {
                                debug.log(level, "log statement at level %d", level);
                                try (DebugContext.Scope s4 = debug.scope("Level4")) {
                                    debug.log(level, "log statement at level %d", level);
                                    try (DebugContext.Scope s5 = debug.scope("Level5")) {
                                        debug.log(level, "log statement at level %d", level);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        DataInputStream in = new DataInputStream(getClass().getResourceAsStream(getClass().getSimpleName() + ".testLogging.input"));
        byte[] buf = new byte[in.available()];
        in.readFully(buf);
        String threadLabel = "[thread:" + Thread.currentThread().getId() + "]";
        String expect = new String(buf).replace("[thread:1]", threadLabel);

        String log = setup.logOutput.toString();
        Assert.assertEquals(expect, log);
    }

    @Test
    public void testEnabledSandbox() {
        EconomicMap<OptionKey<?>, Object> map = EconomicMap.create();
        // Configure with an option that enables scopes
        map.put(DebugOptions.DumpOnError, true);
        OptionValues options = new OptionValues(map);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DebugContext debug = DebugContext.create(options, NO_DESCRIPTION, NO_GLOBAL_METRIC_VALUES, new PrintStream(baos), DebugHandlersFactory.LOADER);
        Exception e = new Exception("testEnabledSandbox");
        String scopeName = "";
        try {
            try (DebugContext.Scope d = debug.sandbox("TestExceptionHandling", debug.getConfig())) {
                scopeName = d.getQualifiedName();
                throw e;
            } catch (Throwable t) {
                assert e == t;
                debug.handle(t);
            }
        } catch (Throwable t) {
            // The exception object should propagate all the way out through
            // a enabled sandbox scope
            Assert.assertEquals(e, t);
        }
        String logged = baos.toString();
        String expected = String.format("Exception raised in scope %s: %s", scopeName, e);
        String line = "-------------------------------------------------------";
        Assert.assertTrue(String.format("Could not find \"%s\" in content between lines below:%n%s%n%s%s", expected, line, logged, line), logged.contains(expected));
    }

    @Test
    public void testDisabledSandbox() {
        EconomicMap<OptionKey<?>, Object> map = EconomicMap.create();
        // Configure with an option that enables scopes
        map.put(DebugOptions.DumpOnError, true);
        OptionValues options = new OptionValues(map);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DebugContext debug = DebugContext.create(options, NO_DESCRIPTION, NO_GLOBAL_METRIC_VALUES, new PrintStream(baos), DebugHandlersFactory.LOADER);
        Exception e = new Exception("testDisabledSandbox");
        try {
            // Test a disabled sandbox scope
            try (DebugContext.Scope d = debug.sandbox("TestExceptionHandling", null)) {
                throw e;
            } catch (Throwable t) {
                assert e == t;
                debug.handle(t);
            }
        } catch (Throwable t) {
            // The exception object should propagate all the way out through
            // a disabled sandbox scope
            Assert.assertEquals(e, t);
        }
        String logged = baos.toString();
        Assert.assertTrue(logged, logged.isEmpty());
    }

    /**
     * Tests that using a {@link DebugContext} on a thread other than the one on which it was
     * created causes an assertion failure.
     */
    @Test
    public void testInvariantChecking() throws InterruptedException {
        Assume.assumeTrue(Assertions.assertionsEnabled());
        EconomicMap<OptionKey<?>, Object> map = EconomicMap.create();
        // Configure with an option that enables counters
        map.put(DebugOptions.Counters, "");
        OptionValues options = new OptionValues(map);
        DebugContext debug = DebugContext.create(options, DebugHandlersFactory.LOADER);
        CounterKey counter = DebugContext.counter("DebugContextTestCounter");
        AssertionError[] result = {null};
        Thread thread = new Thread() {

            @Override
            public void run() {
                try {
                    counter.add(debug, 1);
                } catch (AssertionError e) {
                    result[0] = e;
                }
            }
        };
        thread.start();
        thread.join();

        Assert.assertNotNull("Expected thread to throw AssertionError", result[0]);
    }

    @Test
    public void testDisableIntercept() {
        EconomicMap<OptionKey<?>, Object> map = EconomicMap.create();
        // Configure with an option that enables scopes
        map.put(DebugOptions.DumpOnError, true);
        OptionValues options = new OptionValues(map);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DebugContext debug = DebugContext.create(options, NO_DESCRIPTION, NO_GLOBAL_METRIC_VALUES, new PrintStream(baos), DebugHandlersFactory.LOADER);
        Exception e = new Exception();
        try {
            try (DebugCloseable disabled = debug.disableIntercept(); Scope s1 = debug.scope("ScopeWithDisabledIntercept")) {
                try (Scope s2 = debug.scope("InnerScopeInheritsDisabledIntercept")) {
                    throw e;
                }
            } catch (Throwable t) {
                assert e == t;
                debug.handle(t);
            }
        } catch (Throwable t) {
            // The exception object should propagate all the way out through
            // an intercept disabled scope
            Assert.assertEquals(e, t);
        }
        String logged = baos.toString();
        Assert.assertEquals("Exception should not have been intercepted", "", logged);
    }
}
