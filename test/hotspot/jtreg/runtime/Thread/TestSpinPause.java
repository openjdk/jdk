/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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
 * @test id=default
 * @summary Check the default SpinPause implementation for synchronized statements.
 * @bug 8278241
 * @library /test/lib
 * @requires os.arch=="aarch64"
 * @run main/othervm TestSpinPause
 * @run main/othervm -Xint TestSpinPause
 * @run main/othervm -Xcomp TestSpinPause
 */

/*
 * @test id=none
 * @summary Check SpinPause for synchronized statements with OnSpinWaitInst=none.
 * @bug 8278241
 * @library /test/lib
 * @requires os.arch=="aarch64"
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:OnSpinWaitInst=none TestSpinPause
 * @run main/othervm -Xint -XX:+UnlockDiagnosticVMOptions -XX:OnSpinWaitInst=none TestSpinPause
 * @run main/othervm -Xcomp -XX:+UnlockDiagnosticVMOptions -XX:OnSpinWaitInst=none TestSpinPause
 */

/*
 * @test id=nop
 * @summary Check SpinPause for synchronized statements with OnSpinWaitInst=nop.
 * @bug 8278241
 * @library /test/lib
 * @requires os.arch=="aarch64"
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:OnSpinWaitInst=nop TestSpinPause
 * @run main/othervm -Xint -XX:+UnlockDiagnosticVMOptions -XX:OnSpinWaitInst=nop TestSpinPause
 * @run main/othervm -Xcomp -XX:+UnlockDiagnosticVMOptions -XX:OnSpinWaitInst=nop TestSpinPause
 */

/*
 * @test id=isb
 * @summary Check SpinPause for synchronized statements with OnSpinWaitInst=isb.
 * @bug 8278241
 * @library /test/lib
 * @requires os.arch=="aarch64"
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:OnSpinWaitInst=isb TestSpinPause
 * @run main/othervm -Xint -XX:+UnlockDiagnosticVMOptions -XX:OnSpinWaitInst=isb TestSpinPause
 * @run main/othervm -Xcomp -XX:+UnlockDiagnosticVMOptions -XX:OnSpinWaitInst=isb TestSpinPause
 */

/*
 * @test id=yield
 * @summary Check SpinPause for synchronized statements with OnSpinWaitInst=yield.
 * @bug 8278241
 * @library /test/lib
 * @requires os.arch=="aarch64"
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:OnSpinWaitInst=yield TestSpinPause
 * @run main/othervm -Xint -XX:+UnlockDiagnosticVMOptions -XX:OnSpinWaitInst=yield TestSpinPause
 * @run main/othervm -Xcomp -XX:+UnlockDiagnosticVMOptions -XX:OnSpinWaitInst=yield TestSpinPause
 */

/*
 * @test id=nop-count-10
 * @summary Check SpinPause for synchronized statements with OnSpinWaitInst=nop and OnSpinWaitInstCount=10.
 * @bug 8278241
 * @library /test/lib
 * @requires os.arch=="aarch64"
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:OnSpinWaitInst=nop -XX:OnSpinWaitInstCount=10 TestSpinPause
 * @run main/othervm -Xint -XX:+UnlockDiagnosticVMOptions -XX:OnSpinWaitInst=nop -XX:OnSpinWaitInstCount=10 TestSpinPause
 * @run main/othervm -Xcomp -XX:+UnlockDiagnosticVMOptions -XX:OnSpinWaitInst=nop -XX:OnSpinWaitInstCount=10 TestSpinPause
 */

/*
 * @test id=isb-count-3
 * @summary Check SpinPause for synchronized statements with OnSpinWaitInst=isb and OnSpinWaitInstCount=3.
 * @bug 8278241
 * @library /test/lib
 * @requires os.arch=="aarch64"
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:OnSpinWaitInst=isb -XX:OnSpinWaitInstCount=3 TestSpinPause
 * @run main/othervm -Xint -XX:+UnlockDiagnosticVMOptions -XX:OnSpinWaitInst=isb -XX:OnSpinWaitInstCount=3 TestSpinPause
 * @run main/othervm -Xcomp -XX:+UnlockDiagnosticVMOptions -XX:OnSpinWaitInst=isb -XX:OnSpinWaitInstCount=3 TestSpinPause
 */

/*
 * @test id=yield-count-3
 * @summary Check SpinPause for synchronized statements with OnSpinWaitInst=yield and OnSpinWaitInstCount=3.
 * @bug 8278241
 * @library /test/lib
 * @requires os.arch=="aarch64"
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:OnSpinWaitInst=yield -XX:OnSpinWaitInstCount=3 TestSpinPause
 * @run main/othervm -Xint -XX:+UnlockDiagnosticVMOptions -XX:OnSpinWaitInst=yield -XX:OnSpinWaitInstCount=3 TestSpinPause
 * @run main/othervm -Xcomp -XX:+UnlockDiagnosticVMOptions -XX:OnSpinWaitInst=yield -XX:OnSpinWaitInstCount=3 TestSpinPause
 */

public class TestSpinPause {
    private Integer[] valueHolder;

    private TestSpinPause () {
        valueHolder = new Integer[] {Integer.valueOf(101)};
    }

    private void getSet() {
        final int iterCount = 100;
        for (int i = 0; i < iterCount; ++i) {
           synchronized (valueHolder) {
               Integer v = valueHolder[0];
               valueHolder[0] = Integer.reverse(v);
           }
        }
    }

    public static void main(String[] args) throws Exception {
        TestSpinPause test = new TestSpinPause();
        Thread t1 = new Thread(test::getSet);
        Thread t2 = new Thread(test::getSet);
        t1.start();
        t2.start();
        t1.join();
        t2.join();
        System.out.println("Done: " + test.valueHolder[0]);
    }
}
