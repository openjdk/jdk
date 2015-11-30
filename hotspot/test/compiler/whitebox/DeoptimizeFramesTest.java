/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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

import compiler.whitebox.CompilerWhiteBoxTest;

/*
 * @test DeoptimizeFramesTest
 * @bug 8028595
 * @library /testlibrary /test/lib /
 * @modules java.management
 * @build DeoptimizeFramesTest
 * @run main ClassFileInstaller sun.hotspot.WhiteBox
 *                              sun.hotspot.WhiteBox$WhiteBoxPermission
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI -Xmixed
 *                   -XX:CompileCommand=compileonly,DeoptimizeFramesTest$TestCaseImpl::method
 *                   -XX:+IgnoreUnrecognizedVMOptions -XX:-DeoptimizeRandom -XX:-DeoptimizeALot
 *                   DeoptimizeFramesTest true
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI -Xmixed
 *                   -XX:CompileCommand=compileonly,DeoptimizeFramesTest$TestCaseImpl::method
 *                   -XX:+IgnoreUnrecognizedVMOptions -XX:-DeoptimizeRandom -XX:-DeoptimizeALot
 *                   DeoptimizeFramesTest false
 * @summary testing of WB::deoptimizeFrames()
 */
import java.lang.reflect.Executable;
import java.util.concurrent.Callable;
import java.util.concurrent.Phaser;

import sun.hotspot.code.NMethod;
import jdk.test.lib.Asserts;
import jdk.test.lib.InfiniteLoop;

public class DeoptimizeFramesTest extends CompilerWhiteBoxTest {
    private final boolean makeNotEntrant;
    private final Phaser phaser;

    private DeoptimizeFramesTest(boolean makeNotEntrant, Phaser phaser) {
        super(new TestCaseImpl(phaser));
        // to prevent inlining of #method
        WHITE_BOX.testSetDontInlineMethod(method, true);
        this.makeNotEntrant = makeNotEntrant;
        this.phaser = phaser;
        System.out.printf("DeoptimizeFramesTest(makeNotEntrant = %b)%n",
                makeNotEntrant);
    }

    public static void main(String[] args) throws Exception {
        Asserts.assertEQ(args.length, 1,
                "[TESTBUG] args should contain 1 element");
        new DeoptimizeFramesTest(Boolean.valueOf(args[0]), new Phaser()).runTest();
    }

    @Override
    protected void test() throws Exception {
        compile();
        checkCompiled();
        NMethod nm = NMethod.get(method, testCase.isOsr());

        WHITE_BOX.deoptimizeFrames(makeNotEntrant);
        // #method should still be compiled, since it didn't have frames on stack
        checkCompiled();
        NMethod nm2 = NMethod.get(method, testCase.isOsr());
        Asserts.assertEQ(nm.compile_id, nm2.compile_id,
                "should be the same nmethod");

        phaser.register();
        Thread t = new Thread(() -> compile(1));
        t.start();
        // pass 1st phase, #method is on stack
        int p = phaser.arriveAndAwaitAdvance();
        WHITE_BOX.deoptimizeFrames(makeNotEntrant);
        // pass 2nd phase, #method can exit
        phaser.awaitAdvance(phaser.arriveAndDeregister());

        try {
            t.join();
        } catch (InterruptedException e) {
            throw new Error("method '" + method + "' is still executing", e);
        }

        // invoke one more time to recompile not entrant if any
        compile(1);

        nm2 = NMethod.get(method, testCase.isOsr());
        if (makeNotEntrant) {
            if (nm2 != null) {
                Asserts.assertNE(nm.compile_id, nm2.compile_id,
                        String.format("compilation %d can't be available", nm.compile_id));
            }
        } else {
            Asserts.assertEQ(nm.compile_id, nm2.compile_id, "should be the same nmethod");
        }
    }


    private static class TestCaseImpl implements TestCase {
        private static final Executable EXECUTABLE;
        static {
            try {
                EXECUTABLE = TestCaseImpl.class.getDeclaredMethod("method");
            } catch (NoSuchMethodException e) {
                throw new Error("[TESTBUG] method not found", e);
            }
        }

        private final Phaser phaser;

        public TestCaseImpl(Phaser phaser) {
            this.phaser = phaser;
            phaser.register();
        }

        @Override
        public String name() {
            return "2phases";
        }

        @Override
        public Executable getExecutable() {
            return EXECUTABLE;
        }

        @Override
        public Callable<Integer> getCallable() {
            return () -> {
                return method();
            };
        }

        @Override
        public boolean isOsr() {
            return false;
        }

        private int method() {
            phaser.arriveAndAwaitAdvance();
            phaser.arriveAndAwaitAdvance();
            return 0;
        }
    }
}
