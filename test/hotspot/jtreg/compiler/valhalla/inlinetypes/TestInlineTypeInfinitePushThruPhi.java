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

/**
 * @test
 * @bug 8386676
 * @summary [lworld] Infinite loop when pushing inline types down though phis during IGVN
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+IgnoreUnrecognizedVMOptions -XX:+StressIGVN
 *                   -XX:CompileCommand=compileonly,${test.main.class}::test -Xbatch
 *                   -XX:-UseOnStackReplacement -XX:StressSeed=3696073068 -XX:CompileTaskTimeout=8000 ${test.main.class}
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions  -XX:+IgnoreUnrecognizedVMOptions -XX:+StressIGVN
 *                   -XX:CompileCommand=compileonly,${test.main.class}::test -Xbatch
 *                   -XX:-UseOnStackReplacement -XX:CompileTaskTimeout=8000 ${test.main.class}
 */

package compiler.valhalla.inlinetypes;

import jdk.internal.vm.annotation.LooselyConsistentValue;
import jdk.internal.vm.annotation.NullRestricted;

public class TestInlineTypeInfinitePushThruPhi {
    static V2 v2 = new V2(3);

    public static void main(String[] args) {
        for (int i = 0; i < 20_000; i++) {
            test();
        }
    }

    public static void test() {
        V1 v1 = new V1(null, v2);
        for (int i = 0; i < 100; i++) {
            v1 = new V1(v1.x, v1.y);
        }
    }

    @LooselyConsistentValue
    static value class V1 {
        V2 x;
        @NullRestricted
        V2 y;

        V1(V2 x, V2 y) {
            this.x = x;
            this.y = y;
        }
    }

    static value class V2 {
        int i;

        V2(int i) {
            this.i = i;
        }
    }
}
