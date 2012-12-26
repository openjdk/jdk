/*
 * Copyright (c) 2006, 2012, Oracle and/or its affiliates. All rights reserved.
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
 * @bug     6467152 6716076 6829503
 * @summary deadlock occurs in LogManager initialization and JVM termination
 * @author  Serguei Spitsyn / Hitachi / Martin Buchholz
 *
 * @build    LoggingDeadlock2
 * @run  main/timeout=15 LoggingDeadlock2
 *
 * There is a clear deadlock between LogManager.<clinit> and
 * Cleaner.run() methods.
 * T1 thread:
 *   The LogManager.<clinit> creates LogManager.manager object,
 *   sets shutdown hook with the Cleaner class and then waits
 *   to lock the LogManager.manager monitor.
 * T2 thread:
 *   It is started by the System.exit() as shutdown hook thread.
 *   It locks the LogManager.manager monitor and then calls the
 *   static methods of the LogManager class (in this particular
 *   case it is a trick of the inner classes implementation).
 *   It is waits when the LogManager.<clinit> is completed.
 *
 * This is a regression test for this bug.
 */

import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.LogManager;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

public class LoggingDeadlock2 {

    public static void realMain(String arg[]) throws Throwable {
        try {
            System.out.println(javaChildArgs);
            ProcessBuilder pb = new ProcessBuilder(javaChildArgs);
            ProcessResults r = run(pb.start());
            equal(r.exitValue(), 99);
            equal(r.out(), "");
            equal(r.err(), "");
        } catch (Throwable t) { unexpected(t); }
    }

    public static class JavaChild {
        public static void main(String args[]) throws Throwable {
            final CyclicBarrier startingGate = new CyclicBarrier(2);
            final Throwable[] thrown = new Throwable[1];

            // Some random variation, to help tickle races.
            final Random rnd = new Random();
            final boolean dojoin = rnd.nextBoolean();
            final int JITTER = 1024;
            final int iters1 = rnd.nextInt(JITTER);
            final int iters2 = JITTER - iters1;
            final AtomicInteger counter = new AtomicInteger(0);

            Thread exiter = new Thread() {
                public void run() {
                    try {
                        startingGate.await();
                        for (int i = 0; i < iters1; i++)
                            counter.getAndIncrement();
                        System.exit(99);
                    } catch (Throwable t) {
                        t.printStackTrace();
                        System.exit(86);
                    }
                }};
            exiter.start();

            startingGate.await();
            for (int i = 0; i < iters2; i++)
                counter.getAndIncrement();
            // This may or may not result in a first call to
            // Runtime.addShutdownHook after shutdown has already
            // commenced.
            LogManager.getLogManager();

            if (dojoin) {
                exiter.join();
                if (thrown[0] != null)
                    throw new Error(thrown[0]);
                check(counter.get() == JITTER);
            }
        }
    }

    //----------------------------------------------------------------
    // The rest of this test is copied from ProcessBuilder/Basic.java
    //----------------------------------------------------------------
    private static final String javaExe =
        System.getProperty("java.home") +
        File.separator + "bin" + File.separator + "java";

    private static final String classpath =
        System.getProperty("java.class.path");

    private static final List<String> javaChildArgs =
        Arrays.asList(new String[]
            { javaExe, "-classpath", classpath,
              "LoggingDeadlock2$JavaChild"});

    private static class ProcessResults {
        private final String out;
        private final String err;
        private final int exitValue;
        private final Throwable throwable;

        public ProcessResults(String out,
                              String err,
                              int exitValue,
                              Throwable throwable) {
            this.out = out;
            this.err = err;
            this.exitValue = exitValue;
            this.throwable = throwable;
        }

        public String out()          { return out; }
        public String err()          { return err; }
        public int exitValue()       { return exitValue; }

        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("<STDOUT>\n" + out() + "</STDOUT>\n")
                .append("<STDERR>\n" + err() + "</STDERR>\n")
                .append("exitValue = " + exitValue + "\n");
            if (throwable != null)
                sb.append(throwable.getStackTrace());
            return sb.toString();
        }
    }

    private static class StreamAccumulator extends Thread {
        private final InputStream is;
        private final StringBuilder sb = new StringBuilder();
        private Throwable throwable = null;

        public String result () throws Throwable {
            if (throwable != null)
                throw throwable;
            return sb.toString();
        }

        StreamAccumulator (InputStream is) {
            this.is = is;
        }

        public void run() {
            try {
                Reader r = new InputStreamReader(is);
                char[] buf = new char[4096];
                int n;
                while ((n = r.read(buf)) > 0) {
                    sb.append(buf,0,n);
                }
            } catch (Throwable t) {
                throwable = t;
            } finally {
                try { is.close(); }
                catch (Throwable t) { throwable = t; }
            }
        }
    }

    private static ProcessResults run(Process p) {
        Throwable throwable = null;
        int exitValue = -1;
        String out = "";
        String err = "";

        StreamAccumulator outAccumulator =
            new StreamAccumulator(p.getInputStream());
        StreamAccumulator errAccumulator =
            new StreamAccumulator(p.getErrorStream());

        try {
            outAccumulator.start();
            errAccumulator.start();

            exitValue = p.waitFor();

            outAccumulator.join();
            errAccumulator.join();

            out = outAccumulator.result();
            err = errAccumulator.result();
        } catch (Throwable t) {
            throwable = t;
        }

        return new ProcessResults(out, err, exitValue, throwable);
    }

    //--------------------- Infrastructure ---------------------------
    static volatile int passed = 0, failed = 0;
    static void pass() {passed++;}
    static void fail() {failed++; Thread.dumpStack();}
    static void fail(String msg) {System.out.println(msg); fail();}
    static void unexpected(Throwable t) {failed++; t.printStackTrace();}
    static void check(boolean cond) {if (cond) pass(); else fail();}
    static void check(boolean cond, String m) {if (cond) pass(); else fail(m);}
    static void equal(Object x, Object y) {
        if (x == null ? y == null : x.equals(y)) pass();
        else fail(x + " not equal to " + y);}
    public static void main(String[] args) throws Throwable {
        try {realMain(args);} catch (Throwable t) {unexpected(t);}
        System.out.printf("%nPassed = %d, failed = %d%n%n", passed, failed);
        if (failed > 0) throw new AssertionError("Some tests failed");}
}
