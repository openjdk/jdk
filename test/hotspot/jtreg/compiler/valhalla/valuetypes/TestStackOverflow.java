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
 * @bug 8392320
 * @summary Test that stack banging when creating extended c2 frame doesn't crash
 * @enablePreview
 * @library /test/lib
 * @run main/othervm -Xcomp -Xbatch -XX:-TieredCompilation -XX:CompileCommand=exclude,TestStackOverflow::foo TestStackOverflow
 */

import java.util.concurrent.ThreadLocalRandom;

public class TestStackOverflow {
    static V0 v0 = new V0(1);
    static V1 v1 = new V1(2);
    static int currentDepth;
    static V0 sink0;
    static V1 sink1;

    interface VI {
      void recurse(V0 v0);
    }

    static value class V0 implements VI {
        long a0,a1,a2,a3,a4,a5,a6,a7,a8,a9,a10,a11,a12,a13,a14,a15,a16,a17,a18,a19;
        V0(long i) {
            a0=a1=a2=a3=a4=a5=a6=a7=a8=a9=a10=a11=a12=a13=a14=a15=a16=a17=a18=a19=i;
        }
        public void recurse(V0 v0) {
            try {
                sink0 = this;
                fooRO();
            } catch (StackOverflowError soe) {}
        }
        static void staticRecurse(V0 v) {
            try {
                sink0 = v;
                foo();
            } catch (StackOverflowError soe) {}
        }
    }

    static value class V1 implements VI {
        long a0,a1,a2,a3,a4,a5,a6,a7,a8,a9,a10,a11,a12,a13,a14,a15,a16,a17,a18,a19,
        a20,a21,a22,a23,a24,a25,a26,a27,a28,a29,a30,a31,a32,a33,a34,a35,a36,a37,a38,
        a39,a40,a41,a42,a43,a44,a45,a46,a47,a48,a49,a50,a51,a52,a53,a54,a55,a56,a57,
        a58,a59,a60,a61,a62,a63;
        V1(long i) {
            a0=a1=a2=a3=a4=a5=a6=a7=a8=a9=a10=a11=a12=a13=a14=a15=a16=a17=a18=a19=
            a20=a21=a22=a23=a24=a25=a26=a27=a28=a29=a30=a31=a32=a33=a34=a35=a36=a37=
            a38=a39=a40=a41=a42=a43=a44=a45=a46=a47=a48=a49=a50=a51=a52=a53=a54=a55=
            a56=a57=a58=a59=a60=a61=a62=a63=i;
        }
        public void recurse(V0 v0) {
            try {
                sink1 = this;
                fooRO();
            } catch (StackOverflowError soe) {}
        }
        static void staticRecurse(V1 v) {
            try {
                sink1 = v;
                foo();
            } catch (StackOverflowError soe) {}
        }
    }

    static void fooRO() {
        try {
            currentDepth++;
            VI receiver = ThreadLocalRandom.current().nextBoolean() ? v0 : v1;
            receiver.recurse(v0);
        } catch (StackOverflowError soe) {}
    }

    static void testVIEPRO() {
        for (int i = 0; i < 20; i++) {
            currentDepth = 0;
            fooRO();
            System.out.println("VIEPRO iteration " + i + " passed. Maxdepth: " + currentDepth);
        }
    }

    static void foo() {
        try {
            currentDepth++;
            if (ThreadLocalRandom.current().nextBoolean()) {
                V0.staticRecurse(v0);
            } else {
                V1.staticRecurse(v1);
            }
        } catch (StackOverflowError soe) {}
    }

    static void testVIEP() {
        for (int i = 0; i < 20; i++) {
            currentDepth = 0;
            foo();
            System.out.println("VIEP iteration " + i + " passed. Maxdepth: " + currentDepth);
        }
    }

    public static void main(String[] args) throws Exception {
        testVIEP();
        testVIEPRO();
    }
}
