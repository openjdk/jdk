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
 *
 */

/*
 * @test id=SerialC1
 * @bug 8316694
 * @requires vm.debug == true
 * @summary test that relocated nmethod is correctly deoptimized
 * @library /test/lib /
 * @modules java.base/jdk.internal.misc java.management
 *
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbatch -XX:+TieredCompilation -XX:TieredStopAtLevel=1
 * -XX:+SegmentedCodeCache -XX:-DeoptimizeRandom -XX:+DeoptimizeALot -XX:+UseSerialGC compiler.whitebox.RelocateNMethodMultiplePaths
 */

/*
 * @test id=SerialC2
 * @bug 8316694
 * @requires vm.debug == true
 * @summary test that relocated nmethod is correctly deoptimized
 * @library /test/lib /
 * @modules java.base/jdk.internal.misc java.management
 *
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbatch -XX:+TieredCompilation
 * -XX:+SegmentedCodeCache -XX:-DeoptimizeRandom -XX:+DeoptimizeALot -XX:+UseSerialGC compiler.whitebox.RelocateNMethodMultiplePaths
 */

/*
 * @test id=ParallelC1
 * @bug 8316694
 * @requires vm.debug == true
 * @summary test that relocated nmethod is correctly deoptimized
 * @library /test/lib /
 * @modules java.base/jdk.internal.misc java.management
 *
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbatch -XX:+TieredCompilation -XX:TieredStopAtLevel=1
 * -XX:+SegmentedCodeCache -XX:-DeoptimizeRandom -XX:+DeoptimizeALot -XX:+UseParallelGC compiler.whitebox.RelocateNMethodMultiplePaths
 */

/*
 * @test id=ParallelC2
 * @bug 8316694
 * @requires vm.debug == true
 * @summary test that relocated nmethod is correctly deoptimized
 * @library /test/lib /
 * @modules java.base/jdk.internal.misc java.management
 *
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbatch -XX:+TieredCompilation
 * -XX:+SegmentedCodeCache -XX:-DeoptimizeRandom -XX:+DeoptimizeALot -XX:+UseParallelGC compiler.whitebox.RelocateNMethodMultiplePaths
 */

/*
 * @test id=G1C1
 * @bug 8316694
 * @requires vm.debug == true
 * @summary test that relocated nmethod is correctly deoptimized
 * @library /test/lib /
 * @modules java.base/jdk.internal.misc java.management
 *
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbatch -XX:+TieredCompilation -XX:TieredStopAtLevel=1
 * -XX:+SegmentedCodeCache -XX:-DeoptimizeRandom -XX:+DeoptimizeALot -XX:+UseG1GC compiler.whitebox.RelocateNMethodMultiplePaths
 */

/*
 * @test id=G1C2
 * @bug 8316694
 * @requires vm.debug == true
 * @summary test that relocated nmethod is correctly deoptimized
 * @library /test/lib /
 * @modules java.base/jdk.internal.misc java.management
 *
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbatch -XX:+TieredCompilation
 * -XX:+SegmentedCodeCache -XX:-DeoptimizeRandom -XX:+DeoptimizeALot -XX:+UseG1GC compiler.whitebox.RelocateNMethodMultiplePaths
 */

/*
 * @test id=ShenandoahC1
 * @bug 8316694
 * @requires vm.debug == true
 * @summary test that relocated nmethod is correctly deoptimized
 * @library /test/lib /
 * @modules java.base/jdk.internal.misc java.management
 *
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbatch -XX:+TieredCompilation -XX:TieredStopAtLevel=1
 * -XX:+SegmentedCodeCache -XX:-DeoptimizeRandom -XX:+DeoptimizeALot -XX:+UseShenandoahGC compiler.whitebox.RelocateNMethodMultiplePaths
 */

/*
 * @test id=ShenandoahC2
 * @bug 8316694
 * @requires vm.debug == true
 * @summary test that relocated nmethod is correctly deoptimized
 * @library /test/lib /
 * @modules java.base/jdk.internal.misc java.management
 *
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbatch -XX:+TieredCompilation
 * -XX:+SegmentedCodeCache -XX:-DeoptimizeRandom -XX:+DeoptimizeALot -XX:+UseShenandoahGC compiler.whitebox.RelocateNMethodMultiplePaths
 */

/*
 * @test id=ZGCC1
 * @bug 8316694
 * @requires vm.debug == true
 * @summary test that relocated nmethod is correctly deoptimized
 * @library /test/lib /
 * @modules java.base/jdk.internal.misc java.management
 *
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbatch -XX:+TieredCompilation -XX:TieredStopAtLevel=1
 * -XX:+SegmentedCodeCache -XX:-DeoptimizeRandom -XX:+DeoptimizeALot -XX:+UseZGC compiler.whitebox.RelocateNMethodMultiplePaths
 */

/*
 * @test id=ZGCC2
 * @bug 8316694
 * @requires vm.debug == true
 * @summary test that relocated nmethod is correctly deoptimized
 * @library /test/lib /
 * @modules java.base/jdk.internal.misc java.management
 *
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbatch -XX:+TieredCompilation
 * -XX:+SegmentedCodeCache -XX:-DeoptimizeRandom -XX:+DeoptimizeALot -XX:+UseZGC compiler.whitebox.RelocateNMethodMultiplePaths
 */

package compiler.whitebox;

import compiler.whitebox.CompilerWhiteBoxTest;
import java.lang.reflect.Method;
import jdk.test.whitebox.WhiteBox;
import jdk.test.whitebox.code.BlobType;
import jdk.test.whitebox.code.NMethod;

public class RelocateNMethodMultiplePaths {

    private static final WhiteBox WHITE_BOX = WhiteBox.getWhiteBox();

    private static final int PATH_ONE_RESULT = 1;
    private static final int PATH_TWO_RESULT = 2;

    public static void main(String [] args) throws Exception {
        // Get method that will be relocated
        Method method = RelocateNMethodMultiplePaths.class.getMethod("function", boolean.class);
        WHITE_BOX.testSetDontInlineMethod(method, true);

        // Verify not initially compiled
        CompilerWhiteBoxTest.checkNotCompiled(method, false);

        // Call function enough to compile
        callFunction(true);

        // Verify now compiled
        CompilerWhiteBoxTest.checkCompiled(method, false);

        // Get newly created nmethod
        NMethod origNmethod = NMethod.get(method, false);

        // Relocate nmethod and mark old for cleanup
        WHITE_BOX.relocateNMethodFromMethod(method, BlobType.MethodNonProfiled.id);

        // Trigger GC to clean up old nmethod
        WHITE_BOX.fullGC();

        // Verify function still compiled after old was cleaned up
        CompilerWhiteBoxTest.checkCompiled(method, false);

        // Get new nmethod and verify it's actually new
        NMethod newNmethod = NMethod.get(method, false);
        if (origNmethod.entry_point == newNmethod.entry_point) {
            throw new RuntimeException("Did not create new nmethod");
        }

        // Verify function still produces correct result
        if (function(true) != PATH_ONE_RESULT) {
            throw new RuntimeException("Relocated function produced incorrect result in path one");
        }

        // Call function again with different path and verify result
        if (function(false) != PATH_TWO_RESULT) {
            throw new RuntimeException("Relocated function produced incorrect result in path two");
        }

        // Verify function can be correctly deoptimized
        WHITE_BOX.deoptimizeMethod(method);
        CompilerWhiteBoxTest.checkNotCompiled(method, false);
    }

    // Call function multiple times to trigger compilation
    private static void callFunction(boolean pathOne) {
        for (int i = 0; i < CompilerWhiteBoxTest.THRESHOLD; i++) {
            function(pathOne);
        }
    }

    public static int function(boolean pathOne) {
        if (pathOne) {
            return PATH_ONE_RESULT;
        } else {
            return PATH_TWO_RESULT;
        }
    }
}
