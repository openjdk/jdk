/*
 * Copyright (c) 2013, 2016, Oracle and/or its affiliates. All rights reserved.
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
import jdk.internal.misc.SharedSecrets;
import jdk.internal.reflect.ConstantPool;
import java.lang.reflect.Executable;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Provide method to compile whole class.
 * Also contains compiled methods and classes counters.
 */
public class Compiler {

    private static final WhiteBox WHITE_BOX = WhiteBox.getWhiteBox();
    private static final AtomicLong METHOD_COUNT = new AtomicLong(0L);

    private Compiler() { }

    /**
     * @return count of processed methods
     */
    public static long getMethodCount() {
        return METHOD_COUNT.get();
    }

    /**
     * Compiles all methods and constructors.
     *
     * @param aClass class to compile
     * @param id an id of the class
     * @param executor executor used for compile task invocation
     * @throws NullPointerException if {@code class} or {@code executor}
     *                              is {@code null}
     */
    public static void compileClass(Class<?> aClass, long id, Executor executor) {
        Objects.requireNonNull(aClass);
        Objects.requireNonNull(executor);
        try {
            ConstantPool constantPool = SharedSecrets.getJavaLangAccess().
                    getConstantPool(aClass);
            if (Utils.COMPILE_THE_WORLD_PRELOAD_CLASSES) {
                preloadClasses(aClass.getName(), id, constantPool);
            }
            int startLevel = Utils.INITIAL_COMP_LEVEL;
            int endLevel = Utils.TIERED_COMPILATION ? Utils.TIERED_STOP_AT_LEVEL : startLevel;
            for (int i = startLevel; i <= endLevel; ++i) {
                WHITE_BOX.enqueueInitializerForCompilation(aClass, i);
            }
            long methodCount = 0;
            for (Executable e : aClass.getDeclaredConstructors()) {
                ++methodCount;
                executor.execute(new CompileMethodCommand(id, e));
            }
            for (Executable e : aClass.getDeclaredMethods()) {
                ++methodCount;
                executor.execute(new CompileMethodCommand(id, e));
            }
            METHOD_COUNT.addAndGet(methodCount);

            if (Utils.DEOPTIMIZE_ALL_CLASSES_RATE > 0
                    && (id % Utils.DEOPTIMIZE_ALL_CLASSES_RATE == 0)) {
                WHITE_BOX.deoptimizeAll();
            }
        } catch (Throwable t) {
            CompileTheWorld.OUT.printf("[%d]\t%s\tskipping %s%n", id, aClass.getName(), t);
            t.printStackTrace();
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
            CompileTheWorld.OUT.printf("[%d]\t%s\tpreloading failed : %s%n",
                    id, className, t);
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
         * @param method    compiled for compilation
         */
        public CompileMethodCommand(long classId, Executable method) {
            this.classId = classId;
            this.className = method.getDeclaringClass().getName();
            this.method = method;
        }

        @Override
        public final void run() {
            int compLevel = Utils.INITIAL_COMP_LEVEL;
            if (Utils.TIERED_COMPILATION) {
                for (int i = compLevel; i <= Utils.TIERED_STOP_AT_LEVEL; ++i) {
                    WHITE_BOX.deoptimizeMethod(method);
                    compileAtLevel(i);
                }
            } else {
                compileAtLevel(compLevel);
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

        private void compileAtLevel(int compLevel) {
            if (WHITE_BOX.isMethodCompilable(method, compLevel)) {
                try {
                    WHITE_BOX.enqueueMethodForCompilation(method, compLevel);
                    waitCompilation();
                    int tmp = WHITE_BOX.getMethodCompilationLevel(method);
                    if (tmp != compLevel) {
                        log("compilation level = " + tmp
                                + ", but not " + compLevel);
                    } else if (Utils.IS_VERBOSE) {
                        log("compilation level = " + tmp + ". OK");
                    }
                } catch (Throwable t) {
                    log("error on compile at " + compLevel
                            + " level");
                    t.printStackTrace();
                }
            } else if (Utils.IS_VERBOSE) {
                log("not compilable at " + compLevel);
            }
        }

        private void log(String message) {
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
            CompileTheWorld.ERR.println(builder);
        }
    }

}
