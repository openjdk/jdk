/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8393482
 * @summary Test bailout from PhaseCCP and compilation when CHA changed
 * @requires vm.compiler2.enabled & vm.flagless & vm.bits == 64 & vm.debug == true
 * @comment The test uses debug flag TracePhaseCCP to check for DecodeNKlass type
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:.
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   -XX:+TracePhaseCCP -XX:+PrintCompilation
 *                   -XX:+LogVMOutput -XX:LogFile=ccp_test.log
 *                   -XX:CompileCommand=quiet
 *                   -XX:CompileCommand=compileonly,${test.main.class}::test*
 *                   -XX:CompileCommand=inline,${test.main.class}::inline*
 *                   ${test.main.class}
 */

package compiler.ccp;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import jdk.test.lib.Utils;
import jdk.test.whitebox.WhiteBox;

/*
 * The loader thread watches the VM log file until CCP prints the exact
 * type for DecodeNKlass generated for array.clone(). At that point
 * both, klass load and decode, nodes have cached the old exact type.
 * The loader thread then loads Child subclass making the cached type
 * of DecodeNKlass invalid. Without the fix, CCP verification asserts.
 * With the fix, PrintCompilation reports that compilation was skipped
 * because of concurrent class loading.
 */

public class TestConcurrentClassLoadingDuringCCP {
    private static final WhiteBox WB = WhiteBox.getWhiteBox();
    private static final String TEST_CLASS  = TestConcurrentClassLoadingDuringCCP.class.getName();
    private static final String CHILD_CLASS = TEST_CLASS + "$Child";
    private static final Path LOG_FILE      = Path.of("ccp_test.log");
    private static final long TIMEOUT_SEC   = Utils.adjustTimeout(60); // 60 sec

    private static volatile Throwable failure;

    static class Parent {}
    static class Child extends Parent {}

    public static void main(String[] args) throws Exception {
        if (WB.isClassAlive(CHILD_CLASS)) {
            throw new RuntimeException(CHILD_CLASS + " should not be loaded");
        }
        Method testMethod = TestConcurrentClassLoadingDuringCCP.class
                            .getDeclaredMethod("test", Parent[][].class);

        CountDownLatch loaderReady = new CountDownLatch(1);
        Thread loader = new Thread(() -> {
            try {
                // Unblock await()
                loaderReady.countDown();
                // Wait TracePhaseCCP output
                waitForLog(" DecodeNKlass  === ", "$Parent:Constant");
                // Load subclass
                Class.forName(CHILD_CLASS);
            } catch (Throwable t) {
                failure = t;
            }
        }, "loader");
        loader.start();

        if (!loaderReady.await(TIMEOUT_SEC, TimeUnit.SECONDS)) {
            throw new RuntimeException("Loader thread did not start");
        }
        // Request C2 Tier4 compilation
        if (!WB.enqueueMethodForCompilation(testMethod, 4)) {
            throw new RuntimeException("Could not enqueue test method for C2 compilation");
        }
        loader.join(TimeUnit.SECONDS.toMillis(TIMEOUT_SEC));
        if (loader.isAlive()) {
            loader.interrupt();
            throw new RuntimeException("Timeout waiting for loader thread");
        }
        if (failure != null) {
            throw new RuntimeException(failure);
        }

        waitForLog(TEST_CLASS + "::test", "COMPILE SKIPPED: concurrent class loading");
        if (WB.isMethodCompiled(testMethod)) {
            throw new RuntimeException("Compilation should bail out");
        }
    }

    private static void waitForLog(String... patterns) throws Exception {
        long timeout = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SEC);
        do {
            String output = Files.readString(LOG_FILE);
            if (output.lines().anyMatch(line -> containsAll(line, patterns))) {
                return; // Found " DecodeNKlass === .* $Parent:Constant"
            }
            Thread.sleep(1);
        } while (System.nanoTime() < timeout);
        throw new RuntimeException("Did not find " + String.join("...", patterns) +
                                   " in " + LOG_FILE);
    }

    private static boolean containsAll(String line, String... patterns) {
        for (String pattern : patterns) {
            if (!line.contains(pattern)) {
                return false;
            }
        }
        return true;
    }

    // When C2 parses the test() and inlined methods, Child has not been loaded,
    // so C2 treats Parent as a leaf class. The klass type generated for `clone`
    // thus contains exact Parent class.
    // After CCP caches this type, the loader thread loads Child subclass.
    // This invalidates the exact Parent and causes LoadNKlass type to widen
    // while CCP is still running.

    // Use static variables to avoid folding of inlined methods.
    private static volatile int result;
    private static volatile int var;

    // Use 2-dims array to force C2 generate LoadNklass -> DecodeNKlass nodes.
    static Parent[][] test(Parent[][] array) {
        Parent[][] clone = array.clone();
        var = inline0();
        return clone;
    }

    private static int inline0() {
        return inline1() + inline1() + inline1() + inline1();
    }

    private static int inline1() {
        return inline2() + inline2() + inline2() + inline2();
    }

    private static int inline2() {
        return inline3() + inline3() + inline3() + inline3();
    }

    private static int inline3() {
        return inline4() + inline4() + inline4() + inline4();
    }

    private static int inline4() {
        return inline5() + inline5() + inline5() + inline5();
    }

    private static int inline5() {
        return result;
    }
}
