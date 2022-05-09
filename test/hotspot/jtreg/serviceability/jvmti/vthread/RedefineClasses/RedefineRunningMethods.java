/*
 * Copyright (c) 2014, 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8055008 8197901 8010319
 * @summary Redefine EMCP and non-EMCP methods that are running in an infinite loop
 * @requires vm.jvmti
 * @library /test/lib
 * @modules java.base/jdk.internal.misc java.base/jdk.internal.vm
 * @modules java.compiler
 *          java.instrument
 *          jdk.jartool/sun.tools.jar
 * @build jdk.test.lib.helpers.ClassFileInstaller jdk.test.lib.compiler.InMemoryJavaCompiler
 * @run main RedefineClassHelper
 * @run main/othervm/timeout=180 -Xint --enable-preview -javaagent:redefineagent.jar
 *     -Xlog:redefine+class+iklass+add=trace,redefine+class+iklass+purge=trace,class+loader+data=debug,safepoint+cleanup,gc+phases=debug:rt.log
 *     RedefineRunningMethods
 */

import jdk.internal.vm.Continuation;
import jdk.internal.vm.ContinuationScope;

class RedefineContinuation {
    static final ContinuationScope FOO = new ContinuationScope() {};

    static void moveToHeap(int a) {
        boolean res = Continuation.yield(FOO);
    }
}

// package access top-level class to avoid problem with RedefineClassHelper
// and nested types.
class RedefineRunningMethods_B {
    static int count1 = 0;
    static int count2 = 0;
    public static volatile boolean stop = false;
    static void localSleep() {
        RedefineContinuation.moveToHeap(1);
    }

    public static void infinite() {
        while (!stop) { count1++; localSleep(); }
    }
    public static void infinite_emcp() {
        System.out.println("infinite_emcp called");
        while (!stop) { count2++; localSleep(); }
    }
}


public class RedefineRunningMethods {

    public static String newB =
                "class RedefineRunningMethods_B {" +
                "   static int count1 = 0;" +
                "   static int count2 = 0;" +
                "   public static volatile boolean stop = false;" +
                "  static void localSleep() { " +
                "      RedefineContinuation.moveToHeap(2);" +
                " } " +
                "   public static void infinite() { " +
                "       System.out.println(\"infinite called\");" +
                "   }" +
                "   public static void infinite_emcp() { " +
                "       System.out.println(\"infinite_emcp called\");" +
                "       while (!stop) { count2++; localSleep(); }" +
                "   }" +
                "}";

    public static String evenNewerB =
                "class RedefineRunningMethods_B {" +
                "   static int count1 = 0;" +
                "   static int count2 = 0;" +
                "   public static volatile boolean stop = false;" +
                "  static void localSleep() { " +
                "      RedefineContinuation.moveToHeap(3);" +
                " } " +
                "   public static void infinite() { }" +
                "   public static void infinite_emcp() { " +
                "       System.out.println(\"infinite_emcp now obsolete called\");" +
                "   }" +
                "}";

    static void test_redef_emcp() {
        System.out.println("test_redef");
        Continuation cont = new Continuation(RedefineContinuation.FOO, ()-> {
              RedefineRunningMethods_B.infinite_emcp();
        });

        while (!cont.isDone()) {
            cont.run();
            // System.gc();
        }
    }

    static void test_redef_infinite() {
        System.out.println("test_redef");
        Continuation cont = new Continuation(RedefineContinuation.FOO, ()-> {
              RedefineRunningMethods_B.infinite();
        });

        while (!cont.isDone()) {
            cont.run();
            // System.gc();
        }
    }

    public static void main(String[] args) throws Exception {

        // Start with GC
        System.gc();

        new Thread() {
            public void run() {
                test_redef_infinite();
            }
        }.start();

        new Thread() {
            public void run() {
                test_redef_emcp();
            }
        }.start();

        RedefineClassHelper.redefineClass(RedefineRunningMethods_B.class, newB);

        System.gc();

        RedefineRunningMethods_B.infinite();

        // Start a thread with the second version of infinite_emcp running
        new Thread() {
            public void run() {
                test_redef_emcp();
            }
        }.start();

        for (int i = 0; i < 20 ; i++) {
            String s = new String("some garbage");
            System.gc();
        }

        RedefineClassHelper.redefineClass(RedefineRunningMethods_B.class, evenNewerB);
        System.gc();

        for (int i = 0; i < 20 ; i++) {
            RedefineRunningMethods_B.infinite();
            String s = new String("some garbage");
            System.gc();
        }

        RedefineRunningMethods_B.infinite_emcp();

        // purge should clean everything up.
        RedefineRunningMethods_B.stop = true;

        for (int i = 0; i < 20 ; i++) {
            // RedefineRunningMethods_B.infinite();
            String s = new String("some garbage");
            System.gc();
        }
    }
}
