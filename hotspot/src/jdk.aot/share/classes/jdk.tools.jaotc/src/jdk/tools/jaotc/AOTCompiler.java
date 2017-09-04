/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
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

package jdk.tools.jaotc;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.graalvm.compiler.options.OptionValues;

import jdk.vm.ci.meta.ResolvedJavaMethod;

final class AOTCompiler {

    private final Main main;

    private final OptionValues graalOptions;

    private CompileQueue compileQueue;

    private final AOTBackend backend;

    /**
     * Compile queue.
     */
    private class CompileQueue extends ThreadPoolExecutor {

        /**
         * Time of the start of this queue.
         */
        private final long startTime;

        /**
         * Method counter for successful compilations.
         */
        private final AtomicInteger successfulMethodCount = new AtomicInteger();

        /**
         * Method counter for failed compilations.
         */
        private final AtomicInteger failedMethodCount = new AtomicInteger();

        /**
         * Create a compile queue with the given number of threads.
         */
        CompileQueue(final int threads) {
            super(threads, threads, 0L, TimeUnit.MILLISECONDS, new PriorityBlockingQueue<>());
            startTime = System.currentTimeMillis();
        }

        @Override
        protected void afterExecute(Runnable r, Throwable t) {
            AOTCompilationTask task = (AOTCompilationTask) r;
            if (task.getResult() != null) {
                final int count = successfulMethodCount.incrementAndGet();
                if (count % 100 == 0) {
                    main.printer.printInfo(".");
                }
                CompiledMethodInfo result = task.getResult();
                if (result != null) {
                    task.getHolder().addCompiledMethod(result);
                }
            } else {
                failedMethodCount.incrementAndGet();
                main.printer.printlnVerbose("");
                ResolvedJavaMethod method = task.getMethod();
                main.printer.printlnVerbose(" failed " + method.getName() + method.getSignature().toMethodDescriptor());
            }
        }

        @Override
        protected void terminated() {
            final long endTime = System.currentTimeMillis();
            final int success = successfulMethodCount.get();
            final int failed = failedMethodCount.get();
            main.printer.printlnInfo("");
            main.printer.printlnInfo(success + " methods compiled, " + failed + " methods failed (" + (endTime - startTime) + " ms)");
        }

    }

    /**
     * @param main
     * @param graalOptions
     * @param aotBackend
     * @param threads number of compilation threads
     */
    AOTCompiler(Main main, OptionValues graalOptions, AOTBackend aotBackend, final int threads) {
        this.main = main;
        this.graalOptions = graalOptions;
        this.compileQueue = new CompileQueue(threads);
        this.backend = aotBackend;
    }

    /**
     * Compile all methods in all classes passed.
     *
     * @param classes a list of class to compile
     * @throws InterruptedException
     */
    List<AOTCompiledClass> compileClasses(List<AOTCompiledClass> classes) throws InterruptedException {
        main.printer.printlnInfo("Compiling with " + compileQueue.getCorePoolSize() + " threads");
        main.printer.printInfo("."); // Compilation progress indication.

        for (AOTCompiledClass c : classes) {
            for (ResolvedJavaMethod m : c.getMethods()) {
                enqueueMethod(c, m);
            }
        }

        // Shutdown queue and wait for all tasks to complete.
        compileQueue.shutdown();
        compileQueue.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);

        List<AOTCompiledClass> compiledClasses = new ArrayList<>();
        for (AOTCompiledClass compiledClass : classes) {
            if (compiledClass.hasCompiledMethods()) {
                compiledClasses.add(compiledClass);
            }
        }
        return compiledClasses;
    }

    /**
     * Enqueue a method in the {@link #compileQueue}.
     *
     * @param method method to be enqueued
     */
    private void enqueueMethod(AOTCompiledClass aotClass, ResolvedJavaMethod method) {
        AOTCompilationTask task = new AOTCompilationTask(main, graalOptions, aotClass, method, backend);
        try {
            compileQueue.execute(task);
        } catch (RejectedExecutionException e) {
            e.printStackTrace();
        }
    }

    static void logCompilation(String methodName, String message) {
        LogPrinter.writeLog(message + " " + methodName);
    }

}
