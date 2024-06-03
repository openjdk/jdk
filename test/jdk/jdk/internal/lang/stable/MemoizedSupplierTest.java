/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @summary Basic tests for StableValue implementations
 * @modules java.base/jdk.internal.lang
 * @compile --enable-preview -source ${jdk.version} MemoizedSupplierTest.java
 * @run junit/othervm --enable-preview MemoizedSupplierTest
 */

import jdk.internal.lang.StableValue;
import org.junit.jupiter.api.Test;

import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

final class MemoizedSupplierTest {

    private static final int VALUE = 42;
    private static final Supplier<Integer> SUPPLIER = () -> VALUE;

    @Test
    void basic() {
        StableTestUtil.CountingSupplier<Integer> original = new StableTestUtil.CountingSupplier<>(SUPPLIER);
        Supplier<Integer> supplier = StableValue.memoizedSupplier(original);
        assertEquals(VALUE, supplier.get());
        assertEquals(1, original.cnt());
        assertEquals(VALUE, supplier.get());
        assertEquals(1, original.cnt());
    }

/*    @Test
    void toStringTest() {
        Supplier<Integer> supplier = StableValue.memoizedSupplier(SUPPLIER);
        String expectedEmpty = "MemoizedSupplier[original=" + SUPPLIER + ", delegate=StableValue.unset]";
        assertEquals(expectedEmpty, supplier.toString());
        supplier.get();
        String expectedSet = "MemoizedSupplier[original=" + SUPPLIER + ", delegate=StableValue[" + VALUE + "]]";
        assertEquals(expectedSet, supplier.toString());
    }*/

}