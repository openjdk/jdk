/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

package sun.hotspot.tools.ctw;

import sun.management.ManagementFactoryHelper;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;

import java.util.List;
import java.util.concurrent.*;

public class CompileTheWorld {
    /**
     * Entry point. Compiles classes in {@code args}, or all classes in
     * boot-classpath if args is empty
     *
     * @param args paths to jar/zip, dir contains classes, or to .lst file
     *             contains list of classes to compile
     */
    public static void main(String[] args) {
        String logfile = Utils.LOG_FILE;
        PrintStream os = null;
        if (logfile != null) {
            try {
                os = new PrintStream(Files.newOutputStream(Paths.get(logfile)));
            } catch (IOException io) {
            }
        }
        if (os != null) {
            System.setOut(os);
        }

        try {
            try {
                if (ManagementFactoryHelper.getCompilationMXBean() == null) {
                    throw new RuntimeException(
                            "CTW can not work in interpreted mode");
                }
            } catch (java.lang.NoClassDefFoundError e) {
                // compact1, compact2 support
            }
            String[] paths = args;
            boolean skipRtJar = false;
            if (args.length == 0) {
                paths = getDefaultPaths();
                skipRtJar = true;
            }
            ExecutorService executor = createExecutor();
            long start = System.currentTimeMillis();
            try {
                String path;
                for (int i = 0, n = paths.length; i < n
                        && !PathHandler.isFinished(); ++i) {
                    path = paths[i];
                    if (skipRtJar && i > 0 && isRtJar(path)) {
                        // rt.jar is not first, so skip it
                        continue;
                    }
                    PathHandler.create(path, executor).process();
                }
            } finally {
                await(executor);
            }
            System.out.printf("Done (%d classes, %d methods, %d ms)%n",
                    Compiler.getClassCount(),
                    Compiler.getMethodCount(),
                    System.currentTimeMillis() - start);
        } finally {
            if (os != null) {
                os.close();
            }
        }
    }

    private static ExecutorService createExecutor() {
        final int threadsCount = Math.min(
                Runtime.getRuntime().availableProcessors(),
                Utils.CI_COMPILER_COUNT);
        ExecutorService result;
        if (threadsCount > 1) {
            result = new ThreadPoolExecutor(threadsCount, threadsCount,
                    /* keepAliveTime */ 0L, TimeUnit.MILLISECONDS,
                    new ArrayBlockingQueue<>(threadsCount),
                    new ThreadPoolExecutor.CallerRunsPolicy());
        } else {
            result = new CurrentThreadExecutor();
        }
        return result;
    }

    private static String[] getDefaultPaths() {
        String property = System.getProperty("sun.boot.class.path");
        System.out.println(
                "# use 'sun.boot.class.path' as args: " + property);
        return Utils.PATH_SEPARATOR.split(property);
    }

    private static void await(ExecutorService executor) {
        executor.shutdown();
        while (!executor.isTerminated()) {
            try {
                executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private static boolean isRtJar(String path) {
        return Utils.endsWithIgnoreCase(path, File.separator + "rt.jar");
    }

    private static class CurrentThreadExecutor extends AbstractExecutorService {
        private boolean isShutdown;

        @Override
        public void shutdown() {
            this.isShutdown = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            return null;
        }

        @Override
        public boolean isShutdown() {
            return isShutdown;
        }

        @Override
        public boolean isTerminated() {
            return isShutdown;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit)
                throws InterruptedException {
            return isShutdown;
        }

        @Override
        public void execute(Runnable command) {
            command.run();
        }
    }
}

