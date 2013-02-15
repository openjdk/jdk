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

import sun.hotspot.WhiteBox;
import sun.management.ManagementFactoryHelper;
import com.sun.management.HotSpotDiagnosticMXBean;

import java.lang.reflect.Method;

/*
 * @author igor.ignatyev@oracle.com
 */
public abstract class CompilerWhiteBoxTest {
    protected static final WhiteBox WHITE_BOX = WhiteBox.getWhiteBox();
    protected static final Method METHOD = getMethod("method");
    protected static final int COMPILE_THRESHOLD
            = Integer.parseInt(getVMOption("CompileThreshold", "10000"));

    protected static Method getMethod(String name) {
        try {
            return CompilerWhiteBoxTest.class.getDeclaredMethod(name);
        } catch (NoSuchMethodException | SecurityException e) {
            throw new RuntimeException(
                    "exception on getting method " + name, e);
        }
    }

    protected static String getVMOption(String name, String defaultValue) {
        String result;
        HotSpotDiagnosticMXBean diagnostic
                = ManagementFactoryHelper.getDiagnosticMXBean();
        result = diagnostic.getVMOption(name).getValue();
        return result == null ? defaultValue : result;
    }

    protected final void runTest() throws RuntimeException {
        if (ManagementFactoryHelper.getCompilationMXBean() == null) {
            System.err.println(
                    "Warning: test is not applicable in interpreted mode");
            return;
        }
        System.out.println("at test's start:");
        printInfo(METHOD);
        try {
            test();
        } catch (Exception e) {
            System.out.printf("on exception '%s':", e.getMessage());
            printInfo(METHOD);
            throw new RuntimeException(e);
        }
        System.out.println("at test's end:");
        printInfo(METHOD);
    }

    protected static void checkNotCompiled(Method method) {
        if (WHITE_BOX.isMethodCompiled(method)) {
            throw new RuntimeException(method + " must be not compiled");
        }
        if (WHITE_BOX.getMethodCompilationLevel(method) != 0) {
            throw new RuntimeException(method + " comp_level must be == 0");
        }
    }

    protected static void checkCompiled(Method method)
            throws InterruptedException {
        final long start = System.currentTimeMillis();
        waitBackgroundCompilation(method);
        if (WHITE_BOX.isMethodQueuedForCompilation(method)) {
            System.err.printf("Warning: %s is still in queue after %dms%n",
                    method, System.currentTimeMillis() - start);
            return;
        }
        if (!WHITE_BOX.isMethodCompiled(method)) {
            throw new RuntimeException(method + " must be compiled");
        }
        if (WHITE_BOX.getMethodCompilationLevel(method) == 0) {
            throw new RuntimeException(method + " comp_level must be != 0");
        }
    }

    protected static void waitBackgroundCompilation(Method method)
            throws InterruptedException {
        final Object obj = new Object();
        synchronized (obj) {
            for (int i = 0; i < 10; ++i) {
                if (!WHITE_BOX.isMethodQueuedForCompilation(method)) {
                    break;
                }
                obj.wait(1000);
            }
        }
    }

    protected static void printInfo(Method method) {
        System.out.printf("%n%s:%n", method);
        System.out.printf("\tcompilable:\t%b%n",
                WHITE_BOX.isMethodCompilable(method));
        System.out.printf("\tcompiled:\t%b%n",
                WHITE_BOX.isMethodCompiled(method));
        System.out.printf("\tcomp_level:\t%d%n",
                WHITE_BOX.getMethodCompilationLevel(method));
        System.out.printf("\tin_queue:\t%b%n",
                WHITE_BOX.isMethodQueuedForCompilation(method));
        System.out.printf("compile_queues_size:\t%d%n%n",
                WHITE_BOX.getCompileQueuesSize());
    }

    protected abstract void test() throws Exception;

    protected final int compile() {
        int result = 0;
        for (int i = 0; i < COMPILE_THRESHOLD; ++i) {
            result += method();
        }
        return result;
    }


    protected int method() {
        return 42;
    }
}
