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
 *
 */

/*
 * @test id=interpreted
 * @summary Ensures that large flat array indexing doesn't cause overflows.
 * @bug 8371604
 * @enablePreview
 * @requires vm.flagless & os.maxMemory >= 19G
 * @library /test/lib /
 * @modules java.base/jdk.internal.value
 * @run junit/othervm/timeout=480 -Xmx18G -Xint
        runtime.valhalla.inlinetypes.FlatArrayLargeIndicesTest
 */

/*
 * @test id=c1
 * @summary Ensures that large flat array indexing doesn't cause overflows.
 * @bug 8371604
 * @enablePreview
 * @requires vm.flagless & os.maxMemory >= 19G
 * @library /test/lib /
 * @modules java.base/jdk.internal.value
 * @run junit/othervm/timeout=480 -Xmx18G -XX:TieredStopAtLevel=3 -Xcomp
        runtime.valhalla.inlinetypes.FlatArrayLargeIndicesTest
 */

/*
 * @test id=c2
 * @summary Ensures that large flat array indexing doesn't cause overflows.
 * @bug 8371604
 * @enablePreview
 * @requires vm.flagless & os.maxMemory >= 19G
 * @library /test/lib /
 * @modules java.base/jdk.internal.value
 * @run junit/othervm/timeout=480 -Xmx18G -XX:-TieredCompilation -Xcomp
        runtime.valhalla.inlinetypes.FlatArrayLargeIndicesTest
 */

package runtime.valhalla.inlinetypes;

import java.util.Arrays;
import jdk.internal.value.ValueClass;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public final class FlatArrayLargeIndicesTest {
    private static final int BIG = 290_000_000; // 64*BIG > int32 addressable limit
    private static final int INDEX_1 = 272146430; // real: observed in one crash
    private static final int INDEX_2 = 289062500; // real: observed in another crash

    public static value record Box(int underlying) {}

    @Test
    public void testStore() {
        Box[] arr = new Box[BIG];
        assertFlat(arr);
        arr[INDEX_1] = new Box(19);
        System.out.println(arr);
    }

    @Test
    public void testLoad() {
        Box[] arr = new Box[BIG];
        assertFlat(arr);
        Box box = arr[INDEX_2];
        assertNull(box, "the box should be null");
    }

    private void assertFlat(Box[] arr) {
        assertTrue(ValueClass.isFlatArray(arr), "expected the array to be flattened");
    }

}
