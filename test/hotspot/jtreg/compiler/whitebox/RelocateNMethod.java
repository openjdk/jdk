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
 * @test id=SerialTiered
 * @bug 8316694
 * @summary test that nmethod::relocate() correctly creates a new nmethod
 * @library /test/lib /
 * @modules java.base/jdk.internal.misc java.management
 *
 * @requires vm.opt.DeoptimizeALot != true
 * @requires vm.gc.Serial
 *
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbatch -XX:+SegmentedCodeCache -XX:+TieredCompilation
 * -XX:+UseSerialGC -XX:+UnlockExperimentalVMOptions -XX:+NMethodRelocation compiler.whitebox.RelocateNMethod
 */

/*
 * @test id=SerialNonTiered
 * @bug 8316694
 * @summary test that nmethod::relocate() correctly creates a new nmethod
 * @library /test/lib /
 * @modules java.base/jdk.internal.misc java.management
 *
 * @requires vm.opt.DeoptimizeALot != true
 * @requires vm.gc.Serial
 *
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbatch -XX:+SegmentedCodeCache -XX:-TieredCompilation
 * -XX:+UseSerialGC -XX:+UnlockExperimentalVMOptions -XX:+NMethodRelocation compiler.whitebox.RelocateNMethod
 */

/*
 * @test id=ParallelTiered
 * @bug 8316694
 * @summary test that nmethod::relocate() correctly creates a new nmethod
 * @library /test/lib /
 * @modules java.base/jdk.internal.misc java.management
 *
 * @requires vm.opt.DeoptimizeALot != true
 * @requires vm.gc.Parallel
 *
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbatch -XX:+SegmentedCodeCache -XX:+TieredCompilation
 * -XX:+UseParallelGC -XX:+UnlockExperimentalVMOptions -XX:+NMethodRelocation compiler.whitebox.RelocateNMethod
 */

/*
 * @test id=ParallelNonTiered
 * @bug 8316694
 * @summary test that nmethod::relocate() correctly creates a new nmethod
 * @library /test/lib /
 * @modules java.base/jdk.internal.misc java.management
 *
 * @requires vm.opt.DeoptimizeALot != true
 * @requires vm.gc.Parallel
 *
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbatch -XX:+SegmentedCodeCache -XX:-TieredCompilation
 * -XX:+UseParallelGC -XX:+UnlockExperimentalVMOptions -XX:+NMethodRelocation compiler.whitebox.RelocateNMethod
 */

/*
 * @test id=G1Tiered
 * @bug 8316694
 * @summary test that nmethod::relocate() correctly creates a new nmethod
 * @library /test/lib /
 * @modules java.base/jdk.internal.misc java.management
 *
 * @requires vm.opt.DeoptimizeALot != true
 * @requires vm.gc.G1
 *
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbatch -XX:+SegmentedCodeCache -XX:+TieredCompilation
 * -XX:+UseG1GC -XX:+UnlockExperimentalVMOptions -XX:+NMethodRelocation compiler.whitebox.RelocateNMethod
 */

/*
 * @test id=G1NonTiered
 * @bug 8316694
 * @summary test that nmethod::relocate() correctly creates a new nmethod
 * @library /test/lib /
 * @modules java.base/jdk.internal.misc java.management
 *
 * @requires vm.opt.DeoptimizeALot != true
 * @requires vm.gc.G1
 *
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbatch -XX:+SegmentedCodeCache -XX:-TieredCompilation
 * -XX:+UseG1GC -XX:+UnlockExperimentalVMOptions -XX:+NMethodRelocation compiler.whitebox.RelocateNMethod
 */

/*
 * @test id=ShenandoahTiered
 * @bug 8316694
 * @summary test that nmethod::relocate() correctly creates a new nmethod
 * @library /test/lib /
 * @modules java.base/jdk.internal.misc java.management
 *
 * @requires vm.opt.DeoptimizeALot != true
 * @requires vm.gc.Shenandoah
 *
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbatch -XX:+SegmentedCodeCache -XX:+TieredCompilation
 * -XX:+UseShenandoahGC -XX:+UnlockExperimentalVMOptions -XX:+NMethodRelocation compiler.whitebox.RelocateNMethod
 */

/*
 * @test id=ShenandoahNonTiered
 * @bug 8316694
 * @summary test that nmethod::relocate() correctly creates a new nmethod
 * @library /test/lib /
 * @modules java.base/jdk.internal.misc java.management
 *
 * @requires vm.opt.DeoptimizeALot != true
 * @requires vm.gc.Shenandoah
 *
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbatch -XX:+SegmentedCodeCache -XX:-TieredCompilation
 * -XX:+UseShenandoahGC -XX:+UnlockExperimentalVMOptions -XX:+NMethodRelocation compiler.whitebox.RelocateNMethod
 */

/*
 * @test id=ZGCTiered
 * @bug 8316694
 * @summary test that nmethod::relocate() correctly creates a new nmethod
 * @library /test/lib /
 * @modules java.base/jdk.internal.misc java.management
 *
 * @requires vm.opt.DeoptimizeALot != true
 * @requires vm.gc.Z
 *
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbatch -XX:+SegmentedCodeCache -XX:+TieredCompilation
 * -XX:+UseZGC -XX:+UnlockExperimentalVMOptions -XX:+NMethodRelocation compiler.whitebox.RelocateNMethod
 */

/*
 * @test id=ZGCNonTiered
 * @bug 8316694
 * @summary test that nmethod::relocate() correctly creates a new nmethod
 * @library /test/lib /
 * @modules java.base/jdk.internal.misc java.management
 *
 * @requires vm.opt.DeoptimizeALot != true
 * @requires vm.gc.Z
 *
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbatch -XX:+SegmentedCodeCache -XX:-TieredCompilation
 * -XX:+UseZGC -XX:+UnlockExperimentalVMOptions -XX:+NMethodRelocation compiler.whitebox.RelocateNMethod
 */

package compiler.whitebox;

import java.lang.reflect.Method;
import jdk.test.whitebox.code.BlobType;
import jdk.test.whitebox.code.NMethod;
import jdk.test.whitebox.WhiteBox;

import compiler.whitebox.CompilerWhiteBoxTest;

public class RelocateNMethod extends CompilerWhiteBoxTest {

    public static void main(String[] args) throws Exception {
        CompilerWhiteBoxTest.main(RelocateNMethod::new, new String[] {"CONSTRUCTOR_TEST", "METHOD_TEST", "STATIC_TEST"});
    }

    private RelocateNMethod(TestCase testCase) {
        super(testCase);
        // to prevent inlining of #method
        WHITE_BOX.testSetDontInlineMethod(method, true);
    }

    @Override
    protected void test() throws Exception {
        checkNotCompiled();

        compile();

        checkCompiled();
        NMethod origNmethod = NMethod.get(method, false);

        WHITE_BOX.relocateNMethodFromMethod(method, BlobType.MethodNonProfiled.id);

        WHITE_BOX.fullGC();

        checkCompiled();

        NMethod newNmethod = NMethod.get(method, false);
        if (origNmethod.entry_point == newNmethod.entry_point) {
            throw new RuntimeException("Did not create new nmethod");
        }
    }
}
