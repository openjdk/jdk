/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.openjdk.bench.jdk.incubator.foreign.points.support;

import jdk.incubator.foreign.MemoryLayout;
import jdk.incubator.foreign.MemoryLayouts;
import jdk.incubator.foreign.MemorySegment;

import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;

import static jdk.incubator.foreign.MemoryLayout.PathElement.groupElement;

public class PanamaPoint implements AutoCloseable {

    public static final MemoryLayout LAYOUT = MemoryLayout.ofStruct(
        MemoryLayouts.JAVA_INT.withOrder(ByteOrder.nativeOrder()).withName("x"),
        MemoryLayouts.JAVA_INT.withOrder(ByteOrder.nativeOrder()).withName("y")
    );

    private static final VarHandle VH_x = LAYOUT.varHandle(int.class, groupElement("x"));
    private static final VarHandle VH_y = LAYOUT.varHandle(int.class, groupElement("y"));

    private final MemorySegment segment;

    public PanamaPoint(int x, int y) {
        this(MemorySegment.allocateNative(LAYOUT), x, y);
    }

    public PanamaPoint(MemorySegment segment, int x, int y) {
        this(segment);
        setX(x);
        setY(y);
    }

    public PanamaPoint(MemorySegment segment) {
        this.segment = segment;
    }

    public void setX(int x) {
        VH_x.set(segment.baseAddress(), x);
    }

    public int getX() {
        return (int) VH_x.get(segment.baseAddress());
    }

    public void setY(int y) {
        VH_y.set(segment.baseAddress(), y);
    }

    public int getY() {
        return (int) VH_y.get(segment.baseAddress());
    }

    @Override
    public void close() {
        segment.close();
    }
}
