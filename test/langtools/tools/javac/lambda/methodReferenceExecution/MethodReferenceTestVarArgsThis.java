/*
 * Copyright (c) 2012, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8003639
 * @summary convert lambda testng tests to jtreg and add them
 * @run junit MethodReferenceTestVarArgsThis
 */

import java.lang.reflect.Array;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

/**
 * @author Robert Field
 */

interface NsII { String m(Integer a, Integer b); }

interface Nsiii { String m(int a, int b, int c); }

interface Nsi { String m(int a); }

interface NsaO { String m(Object[] a); }

interface Nsai { String m(int[] a); }

interface Nsvi { String m(int... va); }

public class MethodReferenceTestVarArgsThis {

    // These should be processed as var args

    String xvI(Integer... vi) {
        StringBuilder sb = new StringBuilder("xvI:");
        for (Integer i : vi) {
            sb.append(i);
            sb.append("-");
        }
        return sb.toString();
    }

    String xIvI(Integer f, Integer... vi) {
        StringBuilder sb = new StringBuilder("xIvI:");
        sb.append(f);
        for (Integer i : vi) {
            sb.append(i);
            sb.append("-");
        }
        return sb.toString();
    }

    String xvi(int... vi) {
        int sum = 0;
        for (int i : vi) {
            sum += i;
        }
        return "xvi:" + sum;
    }

    String xIvi(Integer f, int... vi) {
        int sum = 0;
        for (int i : vi) {
            sum += i;
        }
        return "xIvi:(" + f + ")" + sum;
    }

    String xvO(Object... vi) {
        StringBuilder sb = new StringBuilder("xvO:");
        for (Object i : vi) {
            if (i.getClass().isArray()) {
                sb.append("[");
                int len = Array.getLength(i);
                for (int x = 0; x < len; ++x)  {
                    sb.append(Array.get(i, x));
                    sb.append(",");
                }
                sb.append("]");

            } else {
                sb.append(i);
            }
            sb.append("*");
        }
        return sb.toString();
    }

    @Test
    public void testVarArgsNsSuperclass() {
        NsII q;

        q = this::xvO;
        assertEquals("xvO:55*66*", q.m(55,66));
    }

    @Test
    public void testVarArgsNsArray() {
        Nsai q;

        q = this::xvO;
        assertEquals("xvO:[55,66,]*", q.m(new int[] { 55,66 } ));
    }

    @Test
    public void testVarArgsNsII() {
        NsII q;

        q = this::xvI;
        assertEquals("xvI:33-7-", q.m(33,7));

        q = this::xIvI;
        assertEquals("xIvI:5040-", q.m(50,40));

        q = this::xvi;
        assertEquals("xvi:123", q.m(100,23));

        q = this::xIvi;
        assertEquals("xIvi:(9)21", q.m(9,21));
    }

    @Test
    public void testVarArgsNsiii() {
        Nsiii q;

        q = this::xvI;
        assertEquals("xvI:3-2-1-", q.m(3, 2, 1));

        q = this::xIvI;
        assertEquals("xIvI:88899-2-", q.m(888, 99, 2));

        q = this::xvi;
        assertEquals("xvi:987", q.m(900,80,7));

        q = this::xIvi;
        assertEquals("xIvi:(333)99", q.m(333,27, 72));
    }

    @Test
    public void testVarArgsNsi() {
        Nsi q;

        q = this::xvI;
        assertEquals("xvI:3-", q.m(3));

        q = this::xIvI;
        assertEquals("xIvI:888", q.m(888));

        q = this::xvi;
        assertEquals("xvi:900", q.m(900));

        q = this::xIvi;
        assertEquals("xIvi:(333)0", q.m(333));
    }

    // These should NOT be processed as var args

    @Test
    public void testVarArgsNsaO() {
        NsaO q;

        q = this::xvO;
        assertEquals("xvO:yo*there*dude*", q.m(new String[] { "yo", "there", "dude" }));
    }


}
