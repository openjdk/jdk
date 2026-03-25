/*
 * Copyright (c) 2019, 2026, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test VolatileTest
 * @summary check effect of volatile keyword on flattenable fields
 * @modules java.base/jdk.internal.misc
 *          java.base/jdk.internal.vm.annotation
 * @library /test/lib
 * @enablePreview
 * @compile VolatileTest.java
 * @run main/othervm -XX:+UseFieldFlattening runtime.valhalla.inlinetypes.VolatileTest
 */

import jdk.internal.misc.Unsafe;
import jdk.internal.vm.annotation.LooselyConsistentValue;
import jdk.internal.vm.annotation.NullRestricted;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.lang.reflect.*;
import java.util.List;
import jdk.test.lib.Asserts;

public class VolatileTest {
    static final Unsafe U = Unsafe.getUnsafe();
    static boolean atomicLayoutEnabled;


    @LooselyConsistentValue
    static value class MyValue {
        int i = 0;
        int j = 0;
    }

    static class MyContainer {
        @NullRestricted
        MyValue mv0;
        @NullRestricted
        volatile MyValue mv1;

        MyContainer() {
            mv0 = new MyValue();
            mv1 = new MyValue();
            super();
        }
    }

    static public void main(String[] args) {
        Class<?> c = MyContainer.class;
        Field f0 = null;
        Field f1 = null;
        try {
            f0 = c.getDeclaredField("mv0");
            f1 = c.getDeclaredField("mv1");
        } catch(NoSuchFieldException e) {
            e.printStackTrace();
            return;
        }
        Asserts.assertTrue(U.isFlatField(f0), "mv0 should be flattened");
        Asserts.assertFalse(U.isFlatField(f1), "mv1 should not be flattened");
    }
}
