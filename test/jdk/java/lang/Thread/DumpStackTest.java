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
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

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

    @BeforeMethod
    public void beforeEachTest() {
        originalSysErr = System.err;
        switchedSysErrOS = new ByteArrayOutputStream();
        System.setErr(new PrintStream(switchedSysErrOS));
    }

    @AfterMethod
    public void afterEachTest() {
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
            // we don't do additional line by line checks of the stacktrace because the System.err
            // will be polluted with a lot of other stacktraces from within the security layer.
            // As long as the Thread.dumpStack() call from within this test method succeeds without
            // any exceptions, we consider this test as passed.
            return;
        }
        // split by lines
        String[] lines = dumpStackOutput.split(System.lineSeparator());
        assertStackTrace(lines, 1);
    }

    /**
     * Launches multiple threads, each of which initiate a call tree which finally
     * ends up calling Thread.dumpStack(). The stacktrace generated in each thread is then verified
     * to match the expected call stack.
     */
    @Test
    public void testMultiThreadDumpStack() throws Exception {
        int numThreads = 5;
        String threadNamePrefix = "test-dump-stack-";
        ExecutorService execService = Executors.newFixedThreadPool(numThreads, new ThreadFactory() {
            private final AtomicInteger id = new AtomicInteger();

            @Override
            public Thread newThread(final Runnable r) {
                Thread t = new Thread(r);
                t.setName(threadNamePrefix + id.incrementAndGet());
                return t;
            }
        });
        try {
            CountDownLatch taskTriggerLatch = new CountDownLatch(numThreads);
            List<Future<Void>> results = new ArrayList<>();
            for (int i = 0; i < numThreads; i++) {
                results.add(execService.submit(new Callable<Void>() {
                    @Override
                    public Void call() throws Exception {
                        // let the other tasks know we are ready to trigger our work
                        taskTriggerLatch.countDown();
                        // wait for the other task to let us know they are ready to trigger their work too
                        taskTriggerLatch.await();
                        triggerDumpStackCall();
                        return null;
                    }
                }));
            }
            // wait for completion of each task
            for (int i = 0; i < numThreads; i++) {
                results.get(i).get();
            }
        } finally {
            execService.shutdown();
        }
        // capture the generated stacktrace in System.err
        String dumpStackOutput = switchedSysErrOS.toString(Charset.defaultCharset());
        System.out.println("Thread.dumpStack() across multiple threads generated following output:\n" + dumpStackOutput);
        Assert.assertFalse(dumpStackOutput.isEmpty(), "System.err content is empty");
        if (System.getProperty("java.security.debug") != null
                && System.getProperty("java.security.debug").contains("stack")) {
            // in the case where java.security.debug system property contains "stack" as a value,
            // we don't do additional line by line checks of the stacktrace because the System.err
            // will be polluted with a lot of other stacktraces from within the security layer.
            // As long as the Thread.dumpStack() call from within this test method succeeds without
            // any exceptions, we consider this test as passed.
            return;
        }
        // split by lines
        String[] lines = dumpStackOutput.split(System.lineSeparator());
        for (int i = 0; i < numThreads; i++) {
            String threadDumpFirstLine = threadNamePrefix + (i + 1) + " Stack trace";
            // find the first line of each thread's stack
            int lineIndex = findMatchingLine(lines, threadDumpFirstLine);
            Assert.assertNotEquals(lineIndex, -1, "\"" + threadDumpFirstLine
                    + "\" missing in System.err content");
            // starting the next line we expect the stacktrace
            assertStackTrace(lines, lineIndex + 1);
        }
    }

    /**
     * Returns the index from the "lines" array if that line equals the expectedContent.
     * Else returns -1.
     */
    private static int findMatchingLine(String[] lines, String expectedContent) {
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].equals(expectedContent)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Verifies that the {@code lines} contains the expected stacktrace element lines.
     * The {@code firstLineIndex} represents the index in the lines array from where the
     * verification should be started
     */
    private void assertStackTrace(String[] lines, int firstLineIndex) {
        // We only verify the stacktrace starting with this current test's stackframe. We
        // aren't interested in anything before this stackframe since those frames belong to the
        // test infrastructure framework and can potentially change.
        // In these verifications we ignore the line numbers in the stacktrace.
        // Each thread stack trace will be of the form:
        // <thread-name> Stack trace
        //	at java.base/java.lang.Thread.dumpStack(Thread.java:xxx)
        //	at DumpStackTest$Parent.doSomething(DumpStackTest.java:xxx)
        //	at DumpStackTest.c(DumpStackTest.java:xxx)
        //	at DumpStackTest.b(DumpStackTest.java:xxx)
        //	at DumpStackTest.a(DumpStackTest.java:xxx)
        //	at DumpStackTest.triggerDumpStackCall(DumpStackTest.java:xxx)
        Assert.assertTrue(lines[firstLineIndex].startsWith("\tat java.base/java.lang.Thread.dumpStack(Thread.java:"));
        Assert.assertTrue(lines[firstLineIndex + 1].startsWith("\tat DumpStackTest$Parent.doSomething(DumpStackTest.java:"));
        Assert.assertTrue(lines[firstLineIndex + 2].startsWith("\tat DumpStackTest.c(DumpStackTest.java:"));
        Assert.assertTrue(lines[firstLineIndex + 3].startsWith("\tat DumpStackTest.b(DumpStackTest.java:"));
        Assert.assertTrue(lines[firstLineIndex + 4].startsWith("\tat DumpStackTest.a(DumpStackTest.java:"));
        Assert.assertTrue(lines[firstLineIndex + 5].startsWith("\tat DumpStackTest.triggerDumpStackCall(DumpStackTest.java:"));
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
