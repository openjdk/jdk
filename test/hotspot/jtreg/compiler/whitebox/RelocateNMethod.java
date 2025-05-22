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
 * @test id=Serial
 * @bug 8316694
 * @summary test that nmethod::relocate() correctly creates a new nmethod
 * @library /test/lib /
 * @modules java.base/jdk.internal.misc java.management
 *
 * @requires vm.opt.DeoptimizeALot != true
 *
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbatch -XX:+SegmentedCodeCache -XX:+UseSerialGC compiler.whitebox.RelocateNMethod
 */

/*
 * @test id=Parallel
 * @bug 8316694
 * @summary test that nmethod::relocate() correctly creates a new nmethod
 * @library /test/lib /
 * @modules java.base/jdk.internal.misc java.management
 *
 * @requires vm.opt.DeoptimizeALot != true
 *
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbatch -XX:+SegmentedCodeCache -XX:+UseParallelGC compiler.whitebox.RelocateNMethod
 */

/*
 * @test id=G1
 * @bug 8316694
 * @summary test that nmethod::relocate() correctly creates a new nmethod
 * @library /test/lib /
 * @modules java.base/jdk.internal.misc java.management
 *
 * @requires vm.opt.DeoptimizeALot != true
 *
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbatch -XX:+SegmentedCodeCache -XX:+UseG1GC compiler.whitebox.RelocateNMethod
 */

/*
 * @test id=Shenandoah
 * @bug 8316694
 * @summary test that nmethod::relocate() correctly creates a new nmethod
 * @library /test/lib /
 * @modules java.base/jdk.internal.misc java.management
 *
 * @requires vm.opt.DeoptimizeALot != true
 *
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbatch -XX:+SegmentedCodeCache -XX:+UseShenandoahGC compiler.whitebox.RelocateNMethod
 */

/*
 * @test id=ZGC
 * @bug 8316694
 * @summary test that nmethod::relocate() correctly creates a new nmethod
 * @library /test/lib /
 * @modules java.base/jdk.internal.misc java.management
 *
 * @requires vm.opt.DeoptimizeALot != true
 *
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbatch -XX:+SegmentedCodeCache -XX:+UseZGC compiler.whitebox.RelocateNMethod
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

        WHITE_BOX.relocateNMethodFromMethod(method, BlobType.MethodProfiled.id);

        WHITE_BOX.fullGC();

        checkCompiled();

        NMethod newNmethod = NMethod.get(method, false);
        if (origNmethod.entry_point == newNmethod.entry_point) {
            throw new RuntimeException("Did not create new nmethod");
        }
    }
}
