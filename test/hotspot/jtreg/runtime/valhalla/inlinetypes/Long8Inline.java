/*
 * Copyright (c) 2015, 2024, Oracle and/or its affiliates. All rights reserved.
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
import jdk.test.lib.Asserts;

@LooselyConsistentValue
public final value class Long8Inline {

    final long longField1;
    final long longField2;
    final long longField3;
    final long longField4;
    final long longField5;
    final long longField6;
    final long longField7;
    final long longField8;

    public Long8Inline() {
        this(0, 0, 0, 0, 0, 0, 0, 0);
    }

    public Long8Inline(long l1, long l2, long l3, long l4, long l5, long l6, long l7, long l8) {
        longField1 = l1;
        longField2 = l2;
        longField3 = l3;
        longField4 = l4;
        longField5 = l5;
        longField6 = l6;
        longField7 = l7;
        longField8 = l8;
    }

    public long getLongField1() { return longField1; }
    public long getLongField2() { return longField2; }
    public long getLongField3() { return longField3; }
    public long getLongField4() { return longField4; }
    public long getLongField5() { return longField5; }
    public long getLongField6() { return longField6; }
    public long getLongField7() { return longField7; }
    public long getLongField8() { return longField8; }

    static void check(Long8Inline value,
            long long1,
            long long2,
            long long3,
            long long4,
            long long5,
            long long6,
            long long7,
            long long8) {
        Asserts.assertEquals(value.getLongField1(), long1, "Field 1 incorrect");
        Asserts.assertEquals(value.getLongField2(), long2, "Field 2 incorrect");
        Asserts.assertEquals(value.getLongField3(), long3, "Field 3 incorrect");
        Asserts.assertEquals(value.getLongField4(), long4, "Field 4 incorrect");
        Asserts.assertEquals(value.getLongField5(), long5, "Field 5 incorrect");
        Asserts.assertEquals(value.getLongField6(), long6, "Field 6 incorrect");
        Asserts.assertEquals(value.getLongField7(), long7, "Field 7 incorrect");
        Asserts.assertEquals(value.getLongField8(), long8, "Field 8 incorrect");
    }

}
