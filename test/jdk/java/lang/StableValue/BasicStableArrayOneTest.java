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
 * @summary Basic tests for StableArrayOne implementations
 * @modules java.base/jdk.internal.lang
 * @compile --enable-preview -source ${jdk.version} BasicStableArrayOneTest.java
 * @compile Util.java
 * @run junit/othervm --enable-preview BasicStableArrayOneTest
 */

import jdk.internal.lang.StableArray;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

final class BasicStableArrayOneTest {

    @Test
    void empty() {
        StableArray.Shape shape = StableArray.Shape.of(0);
        StableArray<Integer> arr = StableArray.of(shape);
        assertEquals(shape, arr.shape());
        assertEquals("[]", arr.toString());
    }

    @Test
    void one() {
        StableArray.Shape shape = StableArray.Shape.of(1);
        StableArray<Integer> arr = StableArray.of(StableArray.Shape.of(1));
        assertEquals(shape, arr.shape());
        assertThrows(UnsupportedOperationException.class, arr::get);
    }

    Stream<Executable> unsupportedMethods() {
        return Stream.of(

        )
    }


}
