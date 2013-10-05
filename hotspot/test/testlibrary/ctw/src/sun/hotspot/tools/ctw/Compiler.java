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

import sun.hotspot.WhiteBox;
import sun.misc.SharedSecrets;
import sun.reflect.ConstantPool;

import java.lang.reflect.Executable;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Provide method to compile whole class.
 * Also contains compiled methods and classes counters.
 */
public class Compiler {
    private Compiler() { }
    private static final WhiteBox WHITE_BOX = WhiteBox.getWhiteBox();
    private static final AtomicLong CLASS_COUNT = new AtomicLong(0L);
    private static final AtomicLong METHOD_COUNT = new AtomicLong(0L);
    private static volatile boolean CLASSES_LIMIT_REACHED = false;

    /**
     * @return count of processed classes
     */
    public static long getClassCount() {
        return CLASS_COUNT.get();
    }

    /**
     * @return count of processed methods
     */
    public static long getMethodCount() {
        return METHOD_COUNT.get();
    }

    /**
     * @return {@code true} if classes limit is reached
     */
    public static boolean isLimitReached() {
        return CLASSES_LIMIT_REACHED;
    }

    /**
     * Compiles all methods and constructors.
     *
     * @param aClass class to compile
     * @param executor executor used for compile task invocation
     * @throws NullPointerException if {@code class} or {@code executor}
     *                              is {@code null}
     */
    public static void compileClass(Class aClass, Executor executor) {
        Objects.requireNonNull(aClass);
        Objects.requireNonNull(executor);
        long id = CLASS_COUNT.incrementAndGet();
        if (id > Utils.COMPILE_THE_WORLD_STOP_AT) {
            CLASS_COUNT.decrementAndGet();
            CLASSES_LIMIT_REACHED = true;
            return;
        }

        if (id >= Utils.COMPILE_THE_WORLD_START_AT) {
            String name = aClass.getName();
            try {
                System.out.printf("[%d]\t%s%n", id, name);
                ConstantPool constantPool = SharedSecrets.getJavaLangAccess().
                        getConstantPool(aClass);
                if (Utils.COMPILE_THE_WORLD_PRELOAD_CLASSES) {
                    preloadClasses(name, id, constantPool);
                }
                long methodCount = 0;
                for (Executable e : aClass.getDeclaredConstructors()) {
                    ++methodCount;
                    executor.execute(new CompileMethodCommand(id, name, e));
                }
                for (Executable e : aClass.getDeclaredMethods()) {
                    ++methodCount;
                    executor.execute(new CompileMethodCommand(id, name, e));
                }
                METHOD_COUNT.addAndGet(methodCount);

                if (Utils.DEOPTIMIZE_ALL_CLASSES_RATE > 0
                        && (id % Utils.DEOPTIMIZE_ALL_CLASSES_RATE == 0)) {
                    WHITE_BOX.deoptimizeAll();
                }
            } catch (Throwable t) {
                System.out.printf("[%d]\t%s\tskipping %s%n", id, name, t);
                t.printStackTrace();
            }
        }
    }

    private static void preloadClasses(String className, long id,
            ConstantPool constantPool) {
        try {
            for (int i = 0, n = constantPool.getSize(); i < n; ++i) {
                try {
                    constantPool.getClassAt(i);
                } catch (IllegalArgumentException ignore) {
                }
            }
        } catch (Throwable t) {
            System.out.printf("[%d]\t%s\tpreloading failed : %s%n", id,
                    className, t);
        }
    }



    /**
     * Compilation of method.
     * Will compile method on all available comp levels.
     */
    private static class CompileMethodCommand implements Runnable {
        private final long classId;
        private final String className;
        private final Executable method;

        /**
         * @param classId   id of class
         * @param className name of class
         * @param method    compiled for compilation
         */
        public CompileMethodCommand(long classId, String className,
                Executable method) {
            this.classId = classId;
            this.className = className;
            this.method = method;
        }

        @Override
        public final void run() {
            int compLevel = Utils.INITIAL_COMP_LEVEL;
            if (Utils.TIERED_COMPILATION) {
                for (int i = compLevel; i <= Utils.TIERED_STOP_AT_LEVEL; ++i) {
                    WHITE_BOX.deoptimizeMethod(method);
                    compileMethod(method, i);
                }
            } else {
                compileMethod(method, compLevel);
            }
        }

        private void waitCompilation() {
            if (!Utils.BACKGROUND_COMPILATION) {
                return;
            }
            final Object obj = new Object();
            synchronized (obj) {
                for (int i = 0;
                     i < 10 && WHITE_BOX.isMethodQueuedForCompilation(method);
                     ++i) {
                    try {
                        obj.wait(1000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }

        private void compileMethod(Executable method, int compLevel) {
            if (WHITE_BOX.isMethodCompilable(method, compLevel)) {
                try {
                    WHITE_BOX.enqueueMethodForCompilation(method, compLevel);
                    waitCompilation();
                    int tmp = WHITE_BOX.getMethodCompilationLevel(method);
                    if (tmp != compLevel) {
                        logMethod(method, "compilation level = " + tmp
                                + ", but not " + compLevel);
                    } else if (Utils.IS_VERBOSE) {
                        logMethod(method, "compilation level = " + tmp + ". OK");
                    }
                } catch (Throwable t) {
                    logMethod(method, "error on compile at " + compLevel
                            + " level");
                    t.printStackTrace();
                }
            } else if (Utils.IS_VERBOSE) {
                logMethod(method, "not compilable at " + compLevel);
            }
        }

        private void logMethod(Executable method, String message) {
            StringBuilder builder = new StringBuilder("[");
            builder.append(classId);
            builder.append("]\t");
            builder.append(className);
            builder.append("::");
            builder.append(method.getName());
            builder.append('(');
            Class[] params = method.getParameterTypes();
            for (int i = 0, n = params.length - 1; i < n; ++i) {
                builder.append(params[i].getName());
                builder.append(", ");
            }
            if (params.length != 0) {
                builder.append(params[params.length - 1].getName());
            }
            builder.append(')');
            if (message != null) {
                builder.append('\t');
                builder.append(message);
            }
            System.err.println(builder);
        }
    }

}
