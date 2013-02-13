/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

/**
 * An abstract superclass for threaded tests.
 *
 * This class will try to read a property named test.concurrency.
 * The property can be provided by passing this option to jtreg:
 * -javaoption:-Dtest.concurrency=#
 *
 * If the property is not set the class will use a heuristic to determine the
 * maximum number of threads that can be fired to execute a given test.
 *
 * This code will have to be revisited if jprt starts using concurrency for
 * for running jtreg tests.
 */
public abstract class JavacTestingAbstractThreadedTest {

    protected static AtomicInteger numberOfThreads = new AtomicInteger();

    protected static int getThreadPoolSize() {
        Integer testConc = Integer.getInteger("test.concurrency");
        if (testConc != null) return testConc;
        int cores = Runtime.getRuntime().availableProcessors();
        numberOfThreads.set(Math.max(2, Math.min(8, cores / 2)));
        return numberOfThreads.get();
    }

    protected static void checkAfterExec() throws InterruptedException {
        checkAfterExec(true);
    };

    protected static boolean throwAssertionOnError = true;

    protected static boolean printAll = false;

    protected static StringWriter errSWriter = new StringWriter();
    protected static PrintWriter errWriter = new PrintWriter(errSWriter);

    protected static StringWriter outSWriter = new StringWriter();
    protected static PrintWriter outWriter = new PrintWriter(outSWriter);

    protected static void checkAfterExec(boolean printCheckCount)
            throws InterruptedException {
        pool.shutdown();
        pool.awaitTermination(15, TimeUnit.MINUTES);
        if (errCount.get() > 0) {
            if (throwAssertionOnError) {
                closePrinters();
                System.err.println(errSWriter.toString());
                throw new AssertionError(
                    String.format("%d errors found", errCount.get()));
            } else {
                System.err.println(
                        String.format("%d errors found", errCount.get()));
            }
        } else if (printCheckCount) {
            outWriter.println("Total check executed: " + checkCount.get());
        }
        /*
         * This output is for supporting debugging. It does not mean that a given
         * test had executed that number of threads concurrently. The value printed
         * here is the maximum possible amount.
         */
        closePrinters();
        if (printAll) {
            System.out.println(errSWriter.toString());
            System.out.println(outSWriter.toString());
        }
        System.out.println("Total number of threads in thread pool: " +
                numberOfThreads.get());
    }

    protected static void closePrinters() {
        errWriter.close();
        outWriter.close();
    }

    protected static void processException(Throwable t) {
        errCount.incrementAndGet();
        t.printStackTrace(errWriter);
        pool.shutdown();
    }

    //number of checks executed
    protected static AtomicInteger checkCount = new AtomicInteger();

    //number of errors found while running combo tests
    protected static AtomicInteger errCount = new AtomicInteger();

    //create default shared JavaCompiler - reused across multiple compilations
    protected static JavaCompiler comp = ToolProvider.getSystemJavaCompiler();

    protected static ExecutorService pool = Executors.newFixedThreadPool(
            getThreadPoolSize(), new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r);
            t.setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
                @Override
                public void uncaughtException(Thread t, Throwable e) {
                    pool.shutdown();
                    errCount.incrementAndGet();
                    e.printStackTrace(System.err);
                }
            });
            return t;
        }
    });

    /*
     * File manager is not thread-safe so it cannot be re-used across multiple
     * threads. However we cache per-thread FileManager to avoid excessive
     * object creation
     */
    protected static final ThreadLocal<StandardJavaFileManager> fm =
        new ThreadLocal<StandardJavaFileManager>() {
            @Override protected StandardJavaFileManager initialValue() {
                return comp.getStandardFileManager(null, null, null);
            }
        };

}
