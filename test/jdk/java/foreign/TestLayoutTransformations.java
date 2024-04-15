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

/*
 * @test
 * @modules java.base/jdk.internal.foreign.layout
 * @run junit TestLayoutTransformations
 */

import jdk.internal.foreign.layout.LayoutTransformers;
import org.junit.jupiter.api.Test;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.ValueLayout;
import java.nio.ByteOrder;

import static java.lang.foreign.ValueLayout.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class TestLayoutTransformations {

    @Test
    void single() {
        ValueLayout.OfInt intLayout = JAVA_INT.withName("someName");
        var actual = LayoutTransformers.removeNames().apply(intLayout);
        assertEquals(JAVA_INT, actual);
    }

    @Test
    void structOfValueLayouts() {
        MemoryLayout layout = MemoryLayout.structLayout(
                JAVA_INT.withName("x").withOrder(ByteOrder.LITTLE_ENDIAN),
                JAVA_INT.withName("Y").withOrder(ByteOrder.LITTLE_ENDIAN)
        );

        MemoryLayout expected = MemoryLayout.structLayout(
                JAVA_INT.withName("x").withOrder(ByteOrder.BIG_ENDIAN),
                JAVA_INT.withName("Y").withOrder(ByteOrder.BIG_ENDIAN)
        );
        var actual = LayoutTransformers.setByteOrder(ByteOrder.BIG_ENDIAN).apply(layout);
        assertEquals(expected, actual);
    }

    @Test
    @SuppressWarnings("restricted")
    void removeNames() {
        MemoryLayout layout = MemoryLayout.structLayout(
                JAVA_INT.withName("x"),
                JAVA_INT.withName("y"),
                MemoryLayout.sequenceLayout(8, JAVA_INT.withName("array_element")),
                ADDRESS.withTargetLayout(MemoryLayout.unionLayout(JAVA_INT.withName("u1"), JAVA_FLOAT.withName("u2")).withName("union")).withName("address")
        ).withName("struct");

        MemoryLayout expected = MemoryLayout.structLayout(
                JAVA_INT,
                JAVA_INT,
                MemoryLayout.sequenceLayout(8, JAVA_INT),
                ADDRESS.withTargetLayout(MemoryLayout.unionLayout(JAVA_INT, JAVA_FLOAT))
        );

        var transformer = LayoutTransformers.removeNames();
        var actual = transformer.apply(layout);
        assertEquals(expected, actual);
    }

}
