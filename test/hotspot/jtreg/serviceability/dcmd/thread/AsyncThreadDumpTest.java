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
 * @library /test/lib
 * @run main/othervm/native -Djdk.trackAllThreads=true AsyncThreadDumpTest
 */

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantLock;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import jdk.test.lib.dcmd.PidJcmdExecutor;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.threaddump.ThreadDump;


public class AsyncThreadDumpTest {

    private static native void init();
    private static native void printThread(Thread thread);

    private static void printThreadJava(Thread thread) {
        String suffix = thread.isVirtual() ? " virtual" : "";
        System.out.println("#" + thread.threadId() + " \"" + thread.getName() + "\"" + suffix + ", state=" + thread.getState());
        for (StackTraceElement ste : thread.getStackTrace()) {
            System.out.print("      ");
            System.out.println(ste);
        }
    }

    static class SyncObject1 extends Object {}
    static class SyncObject2 extends Object {}
    static class SyncObject3 extends Object {}

    static void log(String s) {
        System.out.println(s);
    }

    public static void main(String[] args) throws Exception {
        System.loadLibrary("AsyncThreadDumpTest");
        init();

        List<Test> tests = new ArrayList<>();
        {
            var started = new AtomicBoolean();
            var done = new AtomicBoolean();
            var lock = new SyncObject1();
            var lock2 = new ReentrantLock();

            tests.add(new Test("mounted vthread",
                Thread.ofVirtual().start(() -> {
                    synchronized(started) {
                        synchronized(lock) {
                            lock2.lock();
                            started.set(true);
                            // spin until done
                            while (!done.get()) {
                                Thread.onSpinWait();
                            }
                            lock2.unlock();
                        }
                    }
                }))
                .starter((thread) -> {
                    awaitTrue(started);
                })
                .terminator((thread) -> {
                    done.set(true);
                })
                .expected(".*- locked .*\\W" + Pattern.quote(started.getClass().getName()) + "\\W.*",
                          ".*- locked .*\\W" + Pattern.quote(lock.getClass().getName()) + "\\W.*")
            );
        }

        {
            var lock = new SyncObject1();
            var lock2 = new SyncObject2();
            var reLock = new ReentrantLock();

            var sem = new Semaphore(1, true);
            sem.acquire();

            tests.add(new Test("unmounted vthread waiting on semaphore",
                Thread.ofVirtual().start(() -> {
                    synchronized(lock) {
                        synchronized(lock2) {
                            reLock.lock();
                            sem.acquireUninterruptibly(); // blocks here
                            reLock.unlock();
                        }
                    }
                }))
                .starter((thread) -> {
                    while (thread.getState() == Thread.State.RUNNABLE) {
                        Thread.sleep(5);
                    }
                })
                .terminator((thread) -> {
                    sem.release();
                })
                .expected(".*- locked .*\\W" + Pattern.quote(lock.getClass().getName()) + "\\W.*",
                          ".*- locked .*\\W" + Pattern.quote(lock2.getClass().getName()) + "\\W.*")
            );
        }

        var waitLock = new SyncObject2();
        synchronized (waitLock) {
            {
                var lock2 = new ReentrantLock();

                tests.add(new Test("unmounted vthread waiting on sync object",
                    Thread.ofVirtual().start(() -> {
                        lock2.lock();
                        synchronized(waitLock) { // blocks here
                        }
                        lock2.unlock();
                    }))
                    .starter((thread) -> {
                        while (thread.getState() == Thread.State.RUNNABLE) {
                            Thread.sleep(5);
                        }
                    })
                    .terminator((thread) -> {
                        // nothing to do
                    })
                );
            }

            {
                var lock2 = new ReentrantLock();

                tests.add(new Test("platform thread waiting on sync object",
                    Thread.ofPlatform().start(() -> {
                        lock2.lock();
                        synchronized(waitLock) { // blocks here
                        }
                        lock2.unlock();
                    }))
                    .starter((thread) -> {
                        while (thread.getState() == Thread.State.RUNNABLE) {
                            Thread.sleep(5);
                        }
                    })
                    .terminator((thread) -> {
                        // nothing to do
                    })
                );
            }

            // test all testcases in "synchronized (waitLock)"
            test(tests);
        }

//        Path file = genThreadDumpPath(".txt");
//        OutputAnalyzer out = jcmdThreadDumpToFile(file, "-format=plain"/*"-async"*/);
    }


    private static interface ThreadAction {
        public void run(Thread thread) throws Exception;
    }

    private static class Test {
        String name;
        Thread thread;
        ThreadAction starter;
        ThreadAction terminator;
        String[] regexps;

        Test(String name, Thread t) {
            this.name = name;
            this.thread = t;
        }
        Test starter(ThreadAction action) {
            this.starter = action;
            return this;
        }
        Test terminator(ThreadAction action) {
            this.terminator = action;
            return this;
        }
        Test expected(String... regexps) {
            this.regexps = regexps;
            return this;
        }
    }

    private static void test(List<Test> tests) throws Exception {
        OutputAnalyzer output;
        try {
            for (Test test: tests) {
                if (test.starter != null) {
                    test.starter.run(test.thread);
                }
            }
            output = jcmdThreadDump();

            for (Test test: tests) {
                System.out.println("thread #" + test.thread.threadId() + " (" + test.name + ") =============");

                System.out.println("- JVMTI GetStackTrace:");
                printThread(test.thread);

                System.out.println("- Thread.getStackTrace()):");
                printThreadJava(test.thread);

                System.out.println("- jcmd Thread.dump_to_file:");
                System.out.println(getOutputForThread(output, test.thread).getStdout());

                System.out.println("============================");
            }

            {
                String cmd1 = "Thread.print -l";
                System.out.println("output for " + cmd1);
                OutputAnalyzer o1 = new PidJcmdExecutor().execute(cmd1, true);
                System.out.println(o1.getStdout());
            }


        } finally {
            for (Test test: tests) {
                if (test.terminator != null) {
                    test.terminator.run(test.thread);
                }
            }
        }
        for (Test test: tests) {
            if (test.regexps != null) {
                OutputAnalyzer threadOutput = getOutputForThread(output, test.thread);
                for (String regexp: test.regexps) {
                    threadOutput.shouldMatch(regexp);
                }
            }
        }
    }

    static OutputAnalyzer getOutputForThread(OutputAnalyzer out, String start, String end) throws Exception {
        String outStr = out.getStdout();
        int startIndex = outStr.indexOf(start);
        if (startIndex < 0) {
            throw new RuntimeException("Could not find \"" + start + "\"");
        }
        int endIndex = outStr.indexOf(end, startIndex + 1);
        String sub = endIndex > 0
                    ? outStr.substring(startIndex, endIndex)
                    : outStr.substring(startIndex);
        return new OutputAnalyzer(sub, "");
    }

    static OutputAnalyzer getOutputForThread(OutputAnalyzer out, Thread thread) throws Exception {
        String threadPrefix = "Thread #";
        String threadStart = threadPrefix + thread.threadId() + " ";
        return getOutputForThread(out, threadStart, threadPrefix);
    }

    private static Path genThreadDumpPath(String suffix) throws IOException {
        Path dir = Path.of(".").toAbsolutePath();
        Path file = Files.createTempFile(dir, "threads-", suffix);
        Files.delete(file);
        return file;
    }

    private static OutputAnalyzer jcmdThreadDump() {
        boolean silent = true;
        String cmd = "Thread.dump_to_file -";
        return new PidJcmdExecutor().execute(cmd, !silent);
    }

    /**
     * Returns true if the given file contains a line with the string.
     */
    private static boolean find(Path file, String text) throws IOException {
        try (Stream<String> stream = Files.lines(file)) {
            return  stream.anyMatch(line -> line.indexOf(text) >= 0);
        }
    }

    private static void awaitTrue(AtomicBoolean ref) throws Exception {
        while (!ref.get()) {
            Thread.sleep(20);
        }
    }

}
