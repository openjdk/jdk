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
 * @test TestFlatteningBudget
 * @modules java.base/jdk.internal.vm.annotation
 *          java.base/jdk.internal.value
 *          java.base/jdk.internal.misc
 * @library /test/lib
 * @enablePreview
 * @requires vm.opt.UseFieldFlattening != "false"
 * @requires vm.opt.UseNullableAtomicValueFlattening != "false"
 * @requires vm.compMode != "Xcomp"
 * @run main runtime.valhalla.inlinetypes.TestFlatteningBudget
 */

package runtime.valhalla.inlinetypes;

import com.sun.management.HotSpotDiagnosticMXBean;
import com.sun.management.VMOption;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.util.ArrayList;
import jdk.internal.misc.Unsafe;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;

public class TestFlatteningBudget {

    static value record Leaf(Object o) {};
    static value record Node4(Leaf l1, Leaf l2, Leaf l3, Leaf l4) {};
    static value record Node16(Node4 n1, Node4 n2, Node4 n3, Node4 n4) {};
    static value record Node64(Node16 n1, Node16 n2, Node16 n3, Node16 n4) {};
    static value record Node256(Node64 n1, Node64 n2, Node64 n3, Node64 n4) {};
    static value record Node1024(Node256 n1, Node256 n2, Node256 n3, Node256 n4) {};
    static value record Node4096(Node1024 n1, Node1024 n2, Node1024 n3, Node1024 n4) {};
    static value record Node16384(Node4096 n1, Node4096 n2, Node4096 n3, Node4096 n4) {};
    static value record Node65536(Node16384 n1, Node16384 n2, Node16384 n3, Node16384 n4) {};
    static value record Node262144(Node65536 n1, Node65536 n2, Node65536 n3, Node65536 n4) {};
    static value record Node1048576(Node262144 n1, Node262144 n2, Node262144 n3, Node262144 n4) {};
    static value record Node4194304(Node1048576 n1, Node1048576 n2, Node1048576 n3, Node1048576 n4) {};
    static value record Node16777216(Node4194304 n1, Node4194304 n2, Node4194304 n3, Node4194304 n4) {};
    static value record Node67108864(Node16777216 n1, Node16777216 n2, Node16777216 n3, Node16777216 n4) {};
    static value record Node268435456(Node67108864 n1, Node67108864 n2, Node67108864 n3, Node67108864 n4) {};
    static value record Node1073741824(Node268435456 n1, Node268435456 n2, Node268435456 n3, Node268435456 n4) {};
    static value record Node4294967296(Node1073741824 n1, Node1073741824 n2, Node1073741824 n3, Node1073741824 n4) {};
    static value record Node17179869184(Node4294967296 n1, Node4294967296 n2, Node4294967296 n3, Node4294967296 n4) {};
    static value class Holder { Node262144 f = null; }

    // Test 0 : no crash with humongous objects
    static class Test0 {
        public static void main(String[] args) {
            var h = new Holder();
            System.out.println("SUCCESS");
        }
    }

    static value class Value0 {
        byte b = (byte)0;
    }

    static value class Value1 {
        int i = 0;
    }

    static class Container {
        public Value0 v0;
        public Value1 v1;
    }



    // Test 1 : impact of flattening budget on flattening decisions
    static class Test1 {
        static final Unsafe UNSAFE = Unsafe.getUnsafe();
        static HotSpotDiagnosticMXBean hsDiag = ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class);

        public static void main(String[] args) throws Exception {

            int flatteningBudget = Integer.valueOf(args[0]);
            var c = new Container();
            Class<?> klass = c.getClass();
            Field f0 = klass.getDeclaredField("v0");
            if (flatteningBudget >= 2) {
                if (!UNSAFE.isFlatField(f0)) {
                    throw new RuntimeException("Test 1: field f0 is unexpectedly not flat with flattening budget of " + flatteningBudget);
                }
            } else {
                if (UNSAFE.isFlatField(f0)) {
                    throw new RuntimeException("Test 1: field f0 is unexpectedly flat with flattening budget of " + flatteningBudget);
                }
            }
            Field f1 = klass.getDeclaredField("v1");
            if (flatteningBudget > 4) {
                if (!UNSAFE.isFlatField(f1) ) {
                    throw new RuntimeException("Test 1: field f1 is unexpectedly not flat with flattening budget of " + flatteningBudget);
                }
            } else {
                if (UNSAFE.isFlatField(f1) ) {
                    throw new RuntimeException("Test 1: field f1 is unexpectedly flat with flattening budget of " + flatteningBudget);
                }
            }
            System.out.println("SUCCESS");
        }
    }

    static void runTest(String... args) throws Exception {
        ArrayList<String> allArgs = new ArrayList<>();
        allArgs.add("--enable-preview");
        allArgs.add("--add-exports");
        allArgs.add("java.base/jdk.internal.misc=ALL-UNNAMED");
        allArgs.add("-XX:+UnlockExperimentalVMOptions");
        for (String s : args) {
          allArgs.add(s);
        }
        ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder(allArgs.toArray(new String[allArgs.size()]));
        OutputAnalyzer out = new OutputAnalyzer(pb.start());
        System.out.println(out.getOutput());
        out.shouldHaveExitValue(0);
    }

    static public void main(String[] args) throws Exception {
        runTest("runtime.valhalla.inlinetypes.TestFlatteningBudget$Test0");
        runTest("-XX:FlatteningBudget=128", "runtime.valhalla.inlinetypes.TestFlatteningBudget$Test1", "128");
        runTest("-XX:FlatteningBudget=4", "runtime.valhalla.inlinetypes.TestFlatteningBudget$Test1", "4");
        runTest("-XX:FlatteningBudget=2", "runtime.valhalla.inlinetypes.TestFlatteningBudget$Test1", "2");
        runTest("-XX:FlatteningBudget=0", "runtime.valhalla.inlinetypes.TestFlatteningBudget$Test1", "0");
    }
}
