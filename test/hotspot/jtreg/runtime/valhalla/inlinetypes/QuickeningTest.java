/*
 * Copyright (c) 2018, 2026, Oracle and/or its affiliates. All rights reserved.
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

package runtime.valhalla.inlinetypes;

import jdk.internal.vm.annotation.LooselyConsistentValue;
import jdk.internal.vm.annotation.NullRestricted;
import jdk.test.lib.Asserts;

/*
 * @test QuickeningTest
 * @summary Test quickening of getfield and putfield applied to inline fields
 * @library /test/lib
 * @modules java.base/jdk.internal.vm.annotation
 * @enablePreview
 * @compile QuickeningTest.java
 * @run main runtime.valhalla.inlinetypes.QuickeningTest
 */

public class QuickeningTest {

    @LooselyConsistentValue
    static value class Point {
        final int x;
        final int y;

        public Point(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }

    @LooselyConsistentValue
    static value class JumboInline {
        final long l0;
        final long l1;
        final long l2;
        final long l3;
        final long l4;
        final long l5;
        final long l6;
        final long l7;
        final long l8;
        final long l9;
        final long l10;
        final long l11;
        final long l12;
        final long l13;
        final long l14;
        final long l15;
        final long l16;
        final long l17;
        final long l18;
        final long l19;

        public JumboInline(long l0Val, long l1Val) {
            l0 = l0Val;
            l1 = l1Val;
            l2 = l0Val+1;
            l3 = l1Val+2;
            l4 = l0Val+3;
            l5 = l1Val+4;
            l6 = l0Val+5;
            l7 = l1Val+6;
            l8 = l0Val+7;
            l9 = l1Val+8;
            l10 = l0Val+9;
            l11 = l1Val+10;
            l12 = l0Val+11;
            l13 = l1Val+12;
            l14 = l0Val+13;
            l15 = l1Val+14;
            l16 = l0Val+15;
            l17 = l1Val+16;
            l18 = l0Val+17;
            l19 = l1Val+18;
        }
    }

    static class Parent {
        Point nfp;       /* Not flattenable inline field */
        @NullRestricted
        Point fp;         /* Flattenable and flattened inline field */
        @NullRestricted
        JumboInline fj;    /* Flattenable not flattened inline field */

        Parent() {
            fp = new Point(1, 2);
            fj = new JumboInline(3L, 4L);
            super();
        }

        public void setNfp(Point p) { nfp = p; }
        public void setFp(Point p) { fp = p; }
        public void setFj(JumboInline j) { fj = j; }
    }

    static class Child extends Parent {
        // This class inherited fields from the Parent class
        Point nfp2;      /* Not flattenable inline field */
        @NullRestricted
        Point fp2;        /* Flattenable and flattened inline field */
        @NullRestricted
        JumboInline fj2;   /* Flattenable not flattened inline field */

        Child() {
            fp2 = new Point(5, 6);
            fj2 = new JumboInline(7L, 8L);
            super();
        }

        public void setNfp2(Point p) { nfp2 = p; }
        public void setFp2(Point p)  { fp2 = p; }
        public void setFj2(JumboInline j) { fj2 = j; }
    }

    @LooselyConsistentValue
    static value class Value {
        final Point nfp;       /* Not flattenable inline field */
        @NullRestricted
        final Point fp;         /* Flattenable and flattened inline field */
        @NullRestricted
        final JumboInline fj;    /* Flattenable not flattened inline field */

        private Value() {
            nfp = null;
            fp = new Point(9, 10);
            fj = new JumboInline(11L, 12L);
        }
    }

    static void testUninitializedFields() {
        Parent p = new Parent();
        Asserts.assertEquals(p.nfp, null, "invalid uninitialized not flattenable");
        Asserts.assertEquals(p.fp.x, 1, "invalid value for uninitialized flattened field");
        Asserts.assertEquals(p.fp.y, 2, "invalid value for uninitialized flattened field");
        Asserts.assertEquals(p.fj.l0, 3L, "invalid value for uninitialized flattened field");
        Asserts.assertEquals(p.fj.l1, 4L, "invalid value for uninitialized flattened field");

        Child c = new Child();
        Asserts.assertEquals(c.nfp, null, "invalid uninitialized not flattenable field");
        Asserts.assertEquals(c.fp.x, 1, "invalid value for uninitialized flattened field");
        Asserts.assertEquals(c.fp.y, 2, "invalid value for uninitialized flattened field");
        Asserts.assertEquals(c.fj.l0, 3L, "invalid value for uninitialized flattened field");
        Asserts.assertEquals(c.fj.l1, 4L, "invalid value for uninitialized flattened field");
        Asserts.assertEquals(c.nfp2, null, "invalid uninitialized not flattenable");
        Asserts.assertEquals(c.fp2.x, 5, "invalid value for uninitialized flattened field");
        Asserts.assertEquals(c.fp2.y, 6, "invalid value for uninitialized flattened field");
        Asserts.assertEquals(c.fj2.l0, 7L, "invalid value for uninitialized not flattened field");
        Asserts.assertEquals(c.fj2.l1, 8L, "invalid value for uninitialized not flattened field");

        Value v = new Value();
        Asserts.assertEquals(v.nfp, null, "invalid uninitialized not flattenable");
        Asserts.assertEquals(v.fp.x, 9, "invalid value for uninitialized flattened field");
        Asserts.assertEquals(v.fp.y, 10, "invalid value for uninitialized flattened field");
        Asserts.assertEquals(v.fj.l0, 11L, "invalid value for uninitialized not flattened field");
        Asserts.assertEquals(v.fj.l1, 12L, "invalid value for uninitialized not flattened field");
    }

    static void testPutfieldAndGetField() {
        Point p1 = new Point(16, 47);
        Point p2 = new Point(32, 64);

        JumboInline j1 = new JumboInline(4, 5);
        JumboInline j2 = new JumboInline(7, 9);

        Parent p = new Parent();
        // executing each setter twice to test quickened bytecodes
        p.setNfp(p1);
        p.setNfp(p2);
        p.setFp(p2);
        p.setFp(p1);
        p.setFj(j1);
        p.setFj(j2);

        Asserts.assertTrue(p.nfp.equals(p2), "invalid updated not flattenable field");
        Asserts.assertEquals(p.fp.x, 16, "invalid value for updated flattened field");
        Asserts.assertEquals(p.fp.y, 47, "invalid value for updated flattened field");
        Asserts.assertTrue(p.fj.equals(j2), "invalid value for updated not flattened field");

        Child c = new Child();
        c.setNfp(p1);
        c.setNfp(p2);
        c.setFp(p2);
        c.setFp(p1);
        c.setFj(j1);
        c.setFj(j2);
        c.setNfp2(p2);
        c.setNfp2(p1);
        c.setFp2(p1);
        c.setFp2(p2);
        c.setFj2(j2);
        c.setFj2(j1);

        Asserts.assertTrue(c.nfp.equals(p2), "invalid updated not flattenable field");
        Asserts.assertEquals(c.fp.x, 16, "invalid value for updated flattened field");
        Asserts.assertEquals(c.fp.y, 47, "invalid value for updated flattened field");
        Asserts.assertTrue(c.fj.equals(j2), "invalid value for updated not flattened field");

        Asserts.assertTrue(c.nfp2.equals(p1), "invalid updated not flattenable field");
        Asserts.assertEquals(c.fp2.x, 32, "invalid value for updated flattened field");
        Asserts.assertEquals(c.fp2.y, 64, "invalid value for updated flattened field");
        Asserts.assertTrue(c.fj2.equals(j1), "invalid value for updated not flattened field");
    }

    public static void main(String[] args) {
        testUninitializedFields();
        testUninitializedFields(); // run twice to test quickened bytecodes
        testPutfieldAndGetField();
    }
}
