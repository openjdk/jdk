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
 * @test
 * @bug 8275908
 * @summary Quick test for the new WhiteBox methods of JDK-8275908
 *
 * @requires vm.compiler2.enabled & vm.compMode != "Xcomp"
 * @requires vm.opt.DeoptimizeALot != true
 *
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   -Xbatch -XX:-UseOnStackReplacement -XX:-TieredCompilation
 *                   -XX:+UnlockExperimentalVMOptions -XX:PerMethodTrapLimit=100 -XX:PerBytecodeTrapLimit=4
 *                   -XX:TypeProfileLevel=0
 *                   -XX:+IgnoreUnrecognizedVMOptions -XX:-AlwaysIncrementalInline -XX:-StressIncrementalInlining
 *                   -XX:CompileCommand=compileonly,compiler.uncommontrap.Decompile::uncommonTrap
 *                   -XX:CompileCommand=inline,compiler.uncommontrap.Decompile*::foo
 *                   compiler.uncommontrap.Decompile
 */

package compiler.uncommontrap;

import java.lang.reflect.Method;

import jdk.test.lib.Asserts;
import jdk.test.whitebox.WhiteBox;

public class Decompile {

    private static final WhiteBox WB = WhiteBox.getWhiteBox();
    // The number of deoptimizations after which a method will be made not-entrant
    private static final int PerBytecodeTrapLimit = WB.getIntxVMFlag("PerBytecodeTrapLimit").intValue();
    // The number of interpreter invocations after which a decompiled method will be re-compiled.
    private static final int Tier0InvokeNotifyFreq = (int)Math.pow(2, WB.getIntxVMFlag("Tier0InvokeNotifyFreqLog"));
    // VM builds without JVMCI like x86_32 call the bimorphic inlining trap just 'bimorphic'
    // while all the other builds with JVMCI call it 'bimorphic_or_optimized_type_check'.
    // Only builds with JVMCI have the "EnableJVMCI" flag.
    private static final boolean isJVMCISupported = (WB.getBooleanVMFlag("EnableJVMCI") != null);
    private static final String bimorphicTrapName = isJVMCISupported ? "bimorphic_or_optimized_type_check" : "bimorphic";

    static class Base {
        void foo() {}
    }
    static class X extends Base {
        void foo() {}
    }
    static class Y extends Base {
        void foo() {}
    }

    static void uncommonTrap(Base t) {
        t.foo();
    }

    private static void printCounters(Method uncommonTrap_m, int invocations) {
        System.out.println("-----------------------------------------------------------------");
        System.out.println("invocations=" + invocations + " " +
                           "method compiled=" + WB.isMethodCompiled(uncommonTrap_m) + " " +
                           "decompileCount=" + WB.getMethodDecompileCount(uncommonTrap_m) + "\n" +
                           "trapCount=" + WB.getMethodTrapCount(uncommonTrap_m) + " " +
                           "trapCount(class_check)=" + WB.getMethodTrapCount(uncommonTrap_m, "class_check") + " " +
                           "trapCount(" + bimorphicTrapName + ")=" +
                           WB.getMethodTrapCount(uncommonTrap_m, bimorphicTrapName) + "\n" +
                           "globalDeoptCount=" + WB.getDeoptCount() + " " +
                           "globalDeoptCount(class_check)=" + WB.getDeoptCount("class_check", null) + " " +
                           "globalDeoptCount(" + bimorphicTrapName + ")=" +
                           WB.getDeoptCount(bimorphicTrapName, null));
        System.out.println("-----------------------------------------------------------------");
    }

    private static void check(Method uncommonTrap_m, int invocations, boolean isCompiled, int decompileCount,
                              int trapCount, int trapCountClassCheck, int trapCountBimorphic,
                              int deoptCount, int deoptCountClassCheck, int deoptCountBimorphic) {

        printCounters(uncommonTrap_m, invocations);

        Asserts.assertEQ(isCompiled, WB.isMethodCompiled(uncommonTrap_m),
                         "Wrong compilation status.");
        Asserts.assertEQ(decompileCount, WB.getMethodDecompileCount(uncommonTrap_m),
                         "Wrong number of decompilations.");
        Asserts.assertEQ(trapCount, WB.getMethodTrapCount(uncommonTrap_m),
                         "Wrong number of traps.");
        Asserts.assertEQ(trapCountClassCheck, WB.getMethodTrapCount(uncommonTrap_m, "class_check"),
                         "Wrong number of traps.");
        Asserts.assertEQ(trapCountBimorphic, WB.getMethodTrapCount(uncommonTrap_m, bimorphicTrapName),
                         "Wrong number of traps.");
        Asserts.assertEQ(deoptCount, WB.getDeoptCount(),
                         "Wrong number of deoptimizations.");
        Asserts.assertEQ(deoptCountClassCheck, WB.getDeoptCount("class_check", null),
                         "Wrong number of class_check deoptimizations.");
        Asserts.assertEQ(deoptCountBimorphic, WB.getDeoptCount(bimorphicTrapName, null),
                         "Wrong number of " + bimorphicTrapName + "deoptimizations.");
    }
    public static void main(String[] args) throws Exception {

        // Get a handle of the test method for usage with the WhiteBox API.
        Method uncommonTrap_m = Decompile.class
            .getDeclaredMethod("uncommonTrap", new Class[] { Base.class });

        int invocations = 0;
        Base b = new Base();
        // This is a little tricky :) We have to define 'x' already here otherwise
        // the class 'X' won't be loaded and 'uncommonTrap()' will be compiled without
        // a class check but a CHA dependency that class 'B' has no subtypes.
        X x = new X();
        Y y = new Y();

        // Warmup and compile with an object of type 'Base' as receiver, but don't invoke compiled code.
        while(!WB.isMethodCompiled(uncommonTrap_m)) {
            invocations++;
            uncommonTrap(b);
        }
        check(uncommonTrap_m, invocations, true /* is_compiled */, 0 /* decompileCount */,
              0 /* trapCount  */, 0 /* trapCountClassCheck  */, 0 /* trapCountBimorphic  */,
              0 /* deoptCount */, 0 /* deoptCountClassCheck */, 0 /* deoptCountBimorphic */);

        // Invoke compiled code 'PerBytecodeTrapLimit' times with an receiver object of type 'X'.
        // This should deoptimize 'PerBytecodeTrapLimit' times and finally decompile the method.
        for (int i = 0; i < PerBytecodeTrapLimit; i++) {
            invocations++;
            uncommonTrap(x);
        }
        check(uncommonTrap_m, invocations, false /* is_compiled */, 1 /* decompileCount */,
              PerBytecodeTrapLimit /* trapCount  */, PerBytecodeTrapLimit /* trapCountClassCheck  */, 0 /* trapCountBimorphic  */,
              PerBytecodeTrapLimit /* deoptCount */, PerBytecodeTrapLimit /* deoptCountClassCheck */, 0 /* deoptCountBimorphic */);

        // Invoke the method 'Tier0InvokeNotifyFreq' more times with an receiver object of type 'X'.
        // This should re-compile the method again with bimorphic inlining for receiver types 'Base' and 'X'.
        for (int i = 0; i < Tier0InvokeNotifyFreq; i++) {
            invocations++;
            uncommonTrap(x);
        }
        check(uncommonTrap_m, invocations, true /* is_compiled */, 1 /* decompileCount */,
              PerBytecodeTrapLimit /* trapCount  */, PerBytecodeTrapLimit /* trapCountClassCheck  */, 0 /* trapCountBimorphic  */,
              PerBytecodeTrapLimit /* deoptCount */, PerBytecodeTrapLimit /* deoptCountClassCheck */, 0 /* deoptCountBimorphic */);

        // Invoke compiled code 'PerBytecodeTrapLimit' times with an receiver object of type 'Y'.
        // This should deoptimize 'PerBytecodeTrapLimit' times and finally decompile the method.
        for (int i = 0; i < PerBytecodeTrapLimit; i++) {
            invocations++;
            uncommonTrap(y);
        }
        check(uncommonTrap_m, invocations, false /* is_compiled */, 2 /* decompileCount */,
              2*PerBytecodeTrapLimit /* trapCount  */, PerBytecodeTrapLimit /* trapCountClassCheck  */, PerBytecodeTrapLimit /* trapCountBimorphic  */,
              2*PerBytecodeTrapLimit /* deoptCount */, PerBytecodeTrapLimit /* deoptCountClassCheck */, PerBytecodeTrapLimit /* deoptCountBimorphic */);
    }
}
