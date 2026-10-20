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
import jdk.internal.vm.annotation.Contended;
import jdk.test.lib.valueclass.AsValueClass;

/*
 * @test
 * @bug     8384107
 * @summary Test contended field layout and oop maps with flattened value types
 *
 * @library /test/lib
 * @modules java.base/jdk.internal.vm.annotation
 * @run main/othervm -XX:-RestrictContended -XX:ContendedPaddingWidth=128 -Xmx128m OopMapsCustomValue
 */
public class OopMapsCustomValue {
    public static final int COUNT = 10000;

    // Small value type (32 bits) — guaranteed to be flattened
    @AsValueClass
    static class Small {
        int x;
        Small(int x) { this.x = x; }
    }

    // Value type with reference + primitive — flattened with compressed oops,
    // not flattened with full size oops, creating distinct layout patterns
    @AsValueClass
    static class Opt {
        Object o;
        boolean b;
        Opt(Object o, boolean b) { this.o = o; this.b = b; }
    }

    // Contended fields using exact value types for flattening
    public static class R0 {
        int i1;
        int i2;
        Small s01;
        Small s02;
        @Contended
        Small s03;
        @Contended
        Small s04;
        @Contended
        Opt opt01;
        @Contended
        Opt opt02;
    }

    public static class R1 extends R0 {
        int i3;
        int i4;
        Small s05;
        Small s06;
        @Contended
        Small s07;
        @Contended
        Small s08;
        @Contended
        Opt opt03;
        @Contended
        Opt opt04;
    }

    // Same-group contended with exact value types
    public static class G {
        @Contended("g1")
        Small s01;
        @Contended("g1")
        Opt opt01;
        @Contended("g2")
        Small s02;
        @Contended("g2")
        Opt opt02;
    }

    public static void main(String[] args) throws Exception {
        testContendedValueFields();
        testContendedSameGroup();
    }

    static void testContendedValueFields() {
        Object anchor = new Object();

        R1[] rs = new R1[COUNT];
        for (int i = 0; i < COUNT; i++) {
            R1 r = new R1();
            r.s01 = new Small(1);
            r.s02 = new Small(2);
            r.s03 = new Small(3);
            r.s04 = new Small(4);
            r.opt01 = new Opt(anchor, true);
            r.opt02 = new Opt(anchor, false);
            r.i1 = 1;
            r.i2 = 2;
            r.s05 = new Small(5);
            r.s06 = new Small(6);
            r.s07 = new Small(7);
            r.s08 = new Small(8);
            r.opt03 = new Opt(anchor, true);
            r.opt04 = new Opt(anchor, false);
            r.i3 = 3;
            r.i4 = 4;
            rs[i] = r;
        }

        System.gc();

        for (int i = 0; i < COUNT; i++) {
            R1 r = rs[i];
            if (r.s01.x != 1) throw new Error("s01");
            if (r.s02.x != 2) throw new Error("s02");
            if (r.s03.x != 3) throw new Error("s03");
            if (r.s04.x != 4) throw new Error("s04");
            if (r.opt01.o != anchor || r.opt01.b != true) throw new Error("opt01");
            if (r.opt02.o != anchor || r.opt02.b != false) throw new Error("opt02");
            if (r.i1 != 1) throw new Error("i1");
            if (r.i2 != 2) throw new Error("i2");
            if (r.s05.x != 5) throw new Error("s05");
            if (r.s06.x != 6) throw new Error("s06");
            if (r.s07.x != 7) throw new Error("s07");
            if (r.s08.x != 8) throw new Error("s08");
            if (r.opt03.o != anchor || r.opt03.b != true) throw new Error("opt03");
            if (r.opt04.o != anchor || r.opt04.b != false) throw new Error("opt04");
            if (r.i3 != 3) throw new Error("i3");
            if (r.i4 != 4) throw new Error("i4");
        }
    }

    static void testContendedSameGroup() {
        Object anchor = new Object();

        G[] gs = new G[COUNT];
        for (int i = 0; i < COUNT; i++) {
            G g = new G();
            g.s01 = new Small(1);
            g.opt01 = new Opt(anchor, true);
            g.s02 = new Small(2);
            g.opt02 = new Opt(anchor, false);
            gs[i] = g;
        }

        System.gc();

        for (int i = 0; i < COUNT; i++) {
            G g = gs[i];
            if (g.s01.x != 1) throw new Error("g1 s01");
            if (g.opt01.o != anchor || g.opt01.b != true) throw new Error("g1 opt01");
            if (g.s02.x != 2) throw new Error("g2 s02");
            if (g.opt02.o != anchor || g.opt02.b != false) throw new Error("g2 opt02");
        }
    }
}
