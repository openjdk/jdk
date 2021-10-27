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

import org.testng.Assert;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.Charset;

/**
 * @test
 * @bug 8153133
 * @summary Test Thread.dumpStack()
 * @run testng/othervm DumpStackTest
 * @run testng/othervm -Djava.security.manager -Djava.security.policy=${test.src}/dump-stack-test.policy DumpStackTest
 * @run testng/othervm -Djava.security.debug=access,stack -Djava.security.manager -Djava.security.policy=${test.src}/dump-stack-test.policy DumpStackTest
 */
public class DumpStackTest {

    private PrintStream originalSysErr;
    private ByteArrayOutputStream switchedSysErrOS;

    @BeforeTest
    public void beforeTest() {
        originalSysErr = System.err;
        switchedSysErrOS = new ByteArrayOutputStream();
        System.setErr(new PrintStream(switchedSysErrOS));
    }

    @AfterTest
    public void afterTest() {
        if (originalSysErr != null) {
            System.setErr(originalSysErr);
        }
    }

    /**
     * Initiates a call tree which finally ends up calling Thread.dumpStack(). The stacktrace is then
     * verified to match the expected call stack.
     */
    @Test
    public void testDumpStack() {
        triggerDumpStackCall();
        // capture the generated stacktrace in System.err
        String dumpStackOutput = switchedSysErrOS.toString(Charset.defaultCharset());
        System.out.println("Thread.dumpStack() generated following output:\n" + dumpStackOutput);
        Assert.assertFalse(dumpStackOutput.isEmpty(), "System.err content is empty");
        if (System.getProperty("java.security.debug") != null
                && System.getProperty("java.security.debug").contains("stack")) {
            // in the case where java.security.debug system property contains "stack" as a value,
            // we don't do additional line by line checks of the stacktrace, because the System.err
            // will be polluted with a lot of other stacktraces from within the security layer.
            // As long as the Thread.dumpStack() call from within this test method succeeds without
            // any exceptions, we consider this test as passed.
            return;
        }
        // split by lines
        String[] lines = dumpStackOutput.split(System.lineSeparator());
        // We only verify the stacktrace starting with this current test method's stackframe. We
        // aren't interested in anything before this stackframe since those frames belong to the
        // test infrastructure framework and can potentially change.
        // In these verifications we ignore the line numbers in the stacktrace.
        Assert.assertTrue(lines[1].startsWith("\tat java.base/java.lang.Thread.dumpStack(Thread.java:"));
        Assert.assertTrue(lines[2].startsWith("\tat DumpStackTest$Parent.doSomething(DumpStackTest.java:"));
        Assert.assertTrue(lines[3].startsWith("\tat DumpStackTest.c(DumpStackTest.java:"));
        Assert.assertTrue(lines[4].startsWith("\tat DumpStackTest.b(DumpStackTest.java:"));
        Assert.assertTrue(lines[5].startsWith("\tat DumpStackTest.a(DumpStackTest.java:"));
        Assert.assertTrue(lines[6].startsWith("\tat DumpStackTest.triggerDumpStackCall(DumpStackTest.java:"));
        Assert.assertTrue(lines[7].startsWith("\tat DumpStackTest.testDumpStack(DumpStackTest.java:"));
    }

    private void triggerDumpStackCall() {
        a();
    }

    private void a() {
        b();
    }

    private void b() {
        c();
    }

    private void c() {
        new Child().doSomething();
    }

    private static class Parent {

        protected void doSomething() {
            Thread.dumpStack();
        }
    }

    private static class Child extends Parent {
    }
}
