/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8365978
 * @summary Unsafe::compareAndSetFlatValue crashes with -XX:-UseArrayFlattening
 * @enablePreview
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 * @run main/othervm -XX:CompileCommand=compileonly,compiler.valhalla.inlinetypes.PutFlatValueWithoutUseArrayFlattening::test
 *                   -XX:-TieredCompilation -Xcomp
 *                   -XX:-UseArrayFlattening -XX:+UseFieldFlattening -XX:+IgnoreUnrecognizedVMOptions -XX:+PreloadClasses
 *                   compiler.valhalla.inlinetypes.PutFlatValueWithoutUseArrayFlattening
 * @run main/othervm -XX:+UseFieldFlattening -XX:+IgnoreUnrecognizedVMOptions -XX:+PreloadClasses
 *                   compiler.valhalla.inlinetypes.PutFlatValueWithoutUseArrayFlattening
 */

package compiler.valhalla.inlinetypes;

import java.lang.reflect.Field;
import jdk.internal.misc.Unsafe;
import jdk.test.lib.Asserts;

public class PutFlatValueWithoutUseArrayFlattening {
    static public value class SmallValue {
        byte a;
        byte b;
        SmallValue(int a, int b) {
            this.a = (byte)a;
            this.b = (byte)b;
        }
    }

    SmallValue f;
    private static final long OFFSET;
    private static final boolean IS_FLATTENED;
    private static final int LAYOUT;
    static private final Unsafe U = Unsafe.getUnsafe();
    static {
        try {
            Field f = PutFlatValueWithoutUseArrayFlattening.class.getDeclaredField("f");
            OFFSET = U.objectFieldOffset(f);
            IS_FLATTENED = U.isFlatField(f);
            Asserts.assertTrue(IS_FLATTENED, "Field f should be flat, the test makes no sense otherwise. And why isn't it?!");
            LAYOUT = U.fieldLayout(f);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void test(boolean flag) {
        var newVal = new SmallValue(1, 1);
        var oldVal = new SmallValue(0, 0);
        f = oldVal;
        if (flag) {
            U.compareAndSetFlatValue(this, OFFSET, LAYOUT, SmallValue.class, oldVal, newVal);
        }
    }

    static public void main(String args[]) {
        new SmallValue(0, 0);
        new PutFlatValueWithoutUseArrayFlattening().test(false);
    }
}
