/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package org.openjdk.bench.java.lang.foreign;

import java.lang.foreign.*;
import java.lang.invoke.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.*;
import jdk.internal.misc.Unsafe;
import org.openjdk.jmh.annotations.*;

@State(Scope.Benchmark)
@Fork(value = 1, jvmArgs = { "--enable-native-access=ALL-UNNAMED", "--add-opens=java.base/jdk.internal.misc=ALL-UNNAMED" })
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 3, time = 1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class FFMStructAccessTest {

    private static final int ITERS = 10;

    private static final Unsafe UNSAFE = Utils.unsafe;

    private static final GroupLayout LAYOUT = MemoryLayout.structLayout(
            ValueLayout.JAVA_INT_UNALIGNED.withName("x"),
            ValueLayout.JAVA_INT_UNALIGNED.withName("y"),
            ValueLayout.JAVA_INT_UNALIGNED.withName("z"),
            ValueLayout.JAVA_INT_UNALIGNED.withName("w")
    ).withName("vec4i");

    private static final VarHandle X = LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("x"))
            .withInvokeExactBehavior();
    private static final VarHandle Y = LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("y"))
            .withInvokeExactBehavior();
    private static final VarHandle Z = LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("z"))
            .withInvokeExactBehavior();
    private static final VarHandle W = LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("w"))
            .withInvokeExactBehavior();
    private static final MemorySegment EVERYTHING = MemorySegment.NULL.reinterpret(Long.MAX_VALUE);

    public record Vec4iSegment(MemorySegment segment) {
        int x() { return segment.get(ValueLayout.JAVA_INT_UNALIGNED, 0L); }
        int y() { return segment.get(ValueLayout.JAVA_INT_UNALIGNED, 4L); }
        int z() { return segment.get(ValueLayout.JAVA_INT_UNALIGNED, 8L); }
        int w() { return segment.get(ValueLayout.JAVA_INT_UNALIGNED, 12L); }

        Vec4iSegment x(int value) {
            segment.set(ValueLayout.JAVA_INT_UNALIGNED, 0L, value);
            return this;
        }
        Vec4iSegment y(int value) {
            segment.set(ValueLayout.JAVA_INT_UNALIGNED, 4L, value);
            return this;
        }
        Vec4iSegment z(int value) {
            segment.set(ValueLayout.JAVA_INT_UNALIGNED, 8L, value);
            return this;
        }
        Vec4iSegment w(int value) {
            segment.set(ValueLayout.JAVA_INT_UNALIGNED, 12L, value);
            return this;
        }
    }

    public record Vec4iSegmentVH(MemorySegment segment) {
        int x() { return (int)X.get(segment, 0L); }
        int y() { return (int)Y.get(segment, 0L); }
        int z() { return (int)Z.get(segment, 0L); }
        int w() { return (int)W.get(segment, 0L); }

        Vec4iSegmentVH x(int value) {
            X.set(segment, 0L, value);
            return this;
        }
        Vec4iSegmentVH y(int value) {
            Y.set(segment, 0L, value);
            return this;
        }
        Vec4iSegmentVH z(int value) {
            Z.set(segment, 0L, value);
            return this;
        }
        Vec4iSegmentVH w(int value) {
            W.set(segment, 0L, value);
            return this;
        }
    }

    public record Vec4iSegmentIndirect(MemorySegment segment) {
        int x() { return xInternal(); }
        int y() { return yInternal(); }
        int z() { return zInternal(); }
        int w() { return wInternal(); }

        Vec4iSegmentIndirect x(int value) {
            xInternal(value);
            return this;
        }
        Vec4iSegmentIndirect y(int value) {
            yInternal(value);
            return this;
        }
        Vec4iSegmentIndirect z(int value) {
            zInternal(value);
            return this;
        }
        Vec4iSegmentIndirect w(int value) {
            wInternal(value);
            return this;
        }

        int xInternal() { return segment.get(ValueLayout.JAVA_INT_UNALIGNED, 0L); }
        int yInternal() { return segment.get(ValueLayout.JAVA_INT_UNALIGNED, 4L); }
        int zInternal() { return segment.get(ValueLayout.JAVA_INT_UNALIGNED, 8L); }
        int wInternal() { return segment.get(ValueLayout.JAVA_INT_UNALIGNED, 12L); }

        void xInternal(int value) { segment.set(ValueLayout.JAVA_INT_UNALIGNED, 0L, value); }
        void yInternal(int value) { segment.set(ValueLayout.JAVA_INT_UNALIGNED, 4L, value); }
        void zInternal(int value) { segment.set(ValueLayout.JAVA_INT_UNALIGNED, 8L, value); }
        void wInternal(int value) { segment.set(ValueLayout.JAVA_INT_UNALIGNED, 12L, value); }
    }

    public record Vec4iSegmentDeep(MemorySegment segment) {
        int x() { return xA(); }
        int y() { return yA(); }
        int z() { return zA(); }
        int w() { return wA(); }

        Vec4iSegmentDeep x(int value) {
            xA(value);
            return this;
        }
        Vec4iSegmentDeep y(int value) {
            yA(value);
            return this;
        }
        Vec4iSegmentDeep z(int value) {
            zA(value);
            return this;
        }
        Vec4iSegmentDeep w(int value) {
            wA(value);
            return this;
        }

        int xA() { return xB(); }
        int yA() { return yB(); }
        int zA() { return zB(); }
        int wA() { return wB(); }

        int xB() { return xC(); }
        int yB() { return yC(); }
        int zB() { return zC(); }
        int wB() { return wC(); }

        int xC() { return xD(); }
        int yC() { return yD(); }
        int zC() { return zD(); }
        int wC() { return wD(); }

        int xD() { return segment.get(ValueLayout.JAVA_INT_UNALIGNED, 0L); }
        int yD() { return segment.get(ValueLayout.JAVA_INT_UNALIGNED, 4L); }
        int zD() { return segment.get(ValueLayout.JAVA_INT_UNALIGNED, 8L); }
        int wD() { return segment.get(ValueLayout.JAVA_INT_UNALIGNED, 12L); }

        void xA(int value) { xB(value); }
        void yA(int value) { yB(value); }
        void zA(int value) { zB(value); }
        void wA(int value) { wB(value); }

        void xB(int value) { xC(value); }
        void yB(int value) { yC(value); }
        void zB(int value) { zC(value); }
        void wB(int value) { wC(value); }

        void xC(int value) { xD(value); }
        void yC(int value) { yD(value); }
        void zC(int value) { zD(value); }
        void wC(int value) { wD(value); }

        void xD(int value) { segment.set(ValueLayout.JAVA_INT_UNALIGNED, 0L, value); }
        void yD(int value) { segment.set(ValueLayout.JAVA_INT_UNALIGNED, 4L, value); }
        void zD(int value) { segment.set(ValueLayout.JAVA_INT_UNALIGNED, 8L, value); }
        void wD(int value) { segment.set(ValueLayout.JAVA_INT_UNALIGNED, 12L, value); }
    }

    public record Vec4iAddressSegment(long address) {
        private MemorySegment asSegment() {
            return MemorySegment.ofAddress(address).reinterpret(LAYOUT.byteSize());
        }

        int x() { return asSegment().get(ValueLayout.JAVA_INT_UNALIGNED, 0L); }
        int y() { return asSegment().get(ValueLayout.JAVA_INT_UNALIGNED, 4L); }
        int z() { return asSegment().get(ValueLayout.JAVA_INT_UNALIGNED, 8L); }
        int w() { return asSegment().get(ValueLayout.JAVA_INT_UNALIGNED, 12L); }

        Vec4iAddressSegment x(int value) {
            asSegment().set(ValueLayout.JAVA_INT_UNALIGNED, 0L, value);
            return this;
        }
        Vec4iAddressSegment y(int value) {
            asSegment().set(ValueLayout.JAVA_INT_UNALIGNED, 4L, value);
            return this;
        }
        Vec4iAddressSegment z(int value) {
            asSegment().set(ValueLayout.JAVA_INT_UNALIGNED, 8L, value);
            return this;
        }
        Vec4iAddressSegment w(int value) {
            asSegment().set(ValueLayout.JAVA_INT_UNALIGNED, 12L, value);
            return this;
        }
    }

    public record Vec4iAddressUnsafe(long address) {
        int x() { return UNSAFE.getIntUnaligned(null, address + 0L); }
        int y() { return UNSAFE.getIntUnaligned(null, address + 4L); }
        int z() { return UNSAFE.getIntUnaligned(null, address + 8L); }
        int w() { return UNSAFE.getIntUnaligned(null, address + 12L); }

        Vec4iAddressUnsafe x(int value) {
            UNSAFE.putIntUnaligned(null, address + 0L, value);
            return this;
        }
        Vec4iAddressUnsafe y(int value) {
            UNSAFE.putIntUnaligned(null, address + 4L, value);
            return this;
        }
        Vec4iAddressUnsafe z(int value) {
            UNSAFE.putIntUnaligned(null, address + 8L, value);
            return this;
        }
        Vec4iAddressUnsafe w(int value) {
            UNSAFE.putIntUnaligned(null, address + 12L, value);
            return this;
        }
    }

    public record Vec4iAddressUnsafeIndirect(long address) {
        int x() { return xInternal(); }
        int y() { return yInternal(); }
        int z() { return zInternal(); }
        int w() { return wInternal(); }

        Vec4iAddressUnsafeIndirect x(int value) {
            xInternal(value);
            return this;
        }
        Vec4iAddressUnsafeIndirect y(int value) {
            yInternal(value);
            return this;
        }
        Vec4iAddressUnsafeIndirect z(int value) {
            zInternal(value);
            return this;
        }
        Vec4iAddressUnsafeIndirect w(int value) {
            wInternal(value);
            return this;
        }

        int xInternal() { return UNSAFE.getIntUnaligned(null, address + 0L); }
        int yInternal() { return UNSAFE.getIntUnaligned(null, address + 4L); }
        int zInternal() { return UNSAFE.getIntUnaligned(null, address + 8L); }
        int wInternal() { return UNSAFE.getIntUnaligned(null, address + 12L); }

        void xInternal(int value) { UNSAFE.putIntUnaligned(null, address + 0L, value); }
        void yInternal(int value) { UNSAFE.putIntUnaligned(null, address + 4L, value); }
        void zInternal(int value) { UNSAFE.putIntUnaligned(null, address + 8L, value); }
        void wInternal(int value) { UNSAFE.putIntUnaligned(null, address + 12L, value); }
    }

    public record Vec4iAddressUnsafeDeep(long address) {
        int x() { return xA(); }
        int y() { return yA(); }
        int z() { return zA(); }
        int w() { return wA(); }

        Vec4iAddressUnsafeDeep x(int value) {
            xA(value);
            return this;
        }
        Vec4iAddressUnsafeDeep y(int value) {
            yA(value);
            return this;
        }
        Vec4iAddressUnsafeDeep z(int value) {
            zA(value);
            return this;
        }
        Vec4iAddressUnsafeDeep w(int value) {
            wA(value);
            return this;
        }

        int xA() { return xB(); }
        int yA() { return yB(); }
        int zA() { return zB(); }
        int wA() { return wB(); }

        int xB() { return xC(); }
        int yB() { return yC(); }
        int zB() { return zC(); }
        int wB() { return wC(); }

        int xC() { return xD(); }
        int yC() { return yD(); }
        int zC() { return zD(); }
        int wC() { return wD(); }

        int xD() { return UNSAFE.getIntUnaligned(null, address + 0L); }
        int yD() { return UNSAFE.getIntUnaligned(null, address + 4L); }
        int zD() { return UNSAFE.getIntUnaligned(null, address + 8L); }
        int wD() { return UNSAFE.getIntUnaligned(null, address + 12L); }

        void xA(int value) { xB(value); }
        void yA(int value) { yB(value); }
        void zA(int value) { zB(value); }
        void wA(int value) { wB(value); }

        void xB(int value) { xC(value); }
        void yB(int value) { yC(value); }
        void zB(int value) { zC(value); }
        void wB(int value) { wC(value); }

        void xC(int value) { xD(value); }
        void yC(int value) { yD(value); }
        void zC(int value) { zD(value); }
        void wC(int value) { wD(value); }

        void xD(int value) { UNSAFE.putIntUnaligned(null, address + 0L, value); }
        void yD(int value) { UNSAFE.putIntUnaligned(null, address + 4L, value); }
        void zD(int value) { UNSAFE.putIntUnaligned(null, address + 8L, value); }
        void wD(int value) { UNSAFE.putIntUnaligned(null, address + 12L, value); }
    }

    public record Vec4iAddressEverything(long address) {
        int x() { return EVERYTHING.get(ValueLayout.JAVA_INT_UNALIGNED, address + 0L); }
        int y() { return EVERYTHING.get(ValueLayout.JAVA_INT_UNALIGNED, address + 4L); }
        int z() { return EVERYTHING.get(ValueLayout.JAVA_INT_UNALIGNED, address + 8L); }
        int w() { return EVERYTHING.get(ValueLayout.JAVA_INT_UNALIGNED, address + 12L); }

        Vec4iAddressEverything x(int value) {
            EVERYTHING.set(ValueLayout.JAVA_INT_UNALIGNED, address + 0L, value);
            return this;
        }
        Vec4iAddressEverything y(int value) {
            EVERYTHING.set(ValueLayout.JAVA_INT_UNALIGNED, address + 4L, value);
            return this;
        }
        Vec4iAddressEverything z(int value) {
            EVERYTHING.set(ValueLayout.JAVA_INT_UNALIGNED, address + 8L, value);
            return this;
        }
        Vec4iAddressEverything w(int value) {
            EVERYTHING.set(ValueLayout.JAVA_INT_UNALIGNED, address + 12L, value);
            return this;
        }
    }

    public record Vec4iBuffer(ByteBuffer buffer) {
        int x() { return buffer.getInt(0); }
        int y() { return buffer.getInt(4); }
        int z() { return buffer.getInt(8); }
        int w() { return buffer.getInt(12); }

        Vec4iBuffer x(int value) {
            buffer.putInt(0, value);
            return this;
        }
        Vec4iBuffer y(int value) {
            buffer.putInt(4, value);
            return this;
        }
        Vec4iBuffer z(int value) {
            buffer.putInt(8, value);
            return this;
        }
        Vec4iBuffer w(int value) {
            buffer.putInt(12, value);
            return this;
        }
    }

    public static final class Vec4iStatic {
        static int x(MemorySegment segment) { return segment.get(ValueLayout.JAVA_INT_UNALIGNED, 0L); }
        static int y(MemorySegment segment) { return segment.get(ValueLayout.JAVA_INT_UNALIGNED, 4L); }
        static int z(MemorySegment segment) { return segment.get(ValueLayout.JAVA_INT_UNALIGNED, 8L); }
        static int w(MemorySegment segment) { return segment.get(ValueLayout.JAVA_INT_UNALIGNED, 12L); }

        static void x(MemorySegment segment, int value) { segment.set(ValueLayout.JAVA_INT_UNALIGNED, 0L, value); }
        static void y(MemorySegment segment, int value) { segment.set(ValueLayout.JAVA_INT_UNALIGNED, 4L, value); }
        static void z(MemorySegment segment, int value) { segment.set(ValueLayout.JAVA_INT_UNALIGNED, 8L, value); }
        static void w(MemorySegment segment, int value) { segment.set(ValueLayout.JAVA_INT_UNALIGNED, 12L, value); }
    }

    private MemorySegment src = Arena.global().allocate(LAYOUT);
    private MemorySegment dst = Arena.global().allocate(LAYOUT);

    private Vec4iSegment ss = new Vec4iSegment(src);
    private Vec4iSegment ds = new Vec4iSegment(dst);

    private Vec4iSegmentVH ssv = new Vec4iSegmentVH(src);
    private Vec4iSegmentVH dsv = new Vec4iSegmentVH(dst);

    private Vec4iSegmentIndirect ssi = new Vec4iSegmentIndirect(src);
    private Vec4iSegmentIndirect dsi = new Vec4iSegmentIndirect(dst);

    private Vec4iSegmentDeep ssdp = new Vec4iSegmentDeep(src);
    private Vec4iSegmentDeep dsdp = new Vec4iSegmentDeep(dst);

    private Vec4iAddressSegment sas = new Vec4iAddressSegment(src.address());
    private Vec4iAddressSegment das = new Vec4iAddressSegment(dst.address());

    private Vec4iAddressUnsafe sau = new Vec4iAddressUnsafe(src.address());
    private Vec4iAddressUnsafe dau = new Vec4iAddressUnsafe(dst.address());

    private Vec4iAddressUnsafeIndirect saui = new Vec4iAddressUnsafeIndirect(src.address());
    private Vec4iAddressUnsafeIndirect daui = new Vec4iAddressUnsafeIndirect(dst.address());

    private Vec4iAddressUnsafeDeep saud = new Vec4iAddressUnsafeDeep(src.address());
    private Vec4iAddressUnsafeDeep daud = new Vec4iAddressUnsafeDeep(dst.address());

    private Vec4iAddressEverything sae = new Vec4iAddressEverything(src.address());
    private Vec4iAddressEverything dae = new Vec4iAddressEverything(dst.address());

    private Vec4iBuffer sb = new Vec4iBuffer(src.asByteBuffer().order(ByteOrder.nativeOrder()));
    private Vec4iBuffer db = new Vec4iBuffer(dst.asByteBuffer().order(ByteOrder.nativeOrder()));

    // ------------------

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void copyUnsafeInline() {
        var d = this.dst.address();
        var s = this.src.address();

        for (var i = 0; i < ITERS; i++) {
            UNSAFE.putIntUnaligned(null, d + 0L, UNSAFE.getIntUnaligned(null, s + 0L));
            UNSAFE.putIntUnaligned(null, d + 4L, UNSAFE.getIntUnaligned(null, s + 4L));
            UNSAFE.putIntUnaligned(null, d + 8L, UNSAFE.getIntUnaligned(null, s + 8L));
            UNSAFE.putIntUnaligned(null, d + 12L, UNSAFE.getIntUnaligned(null, s + 12L));

            UNSAFE.putIntUnaligned(null, d + 0L, UNSAFE.getIntUnaligned(null, s + 0L));
            UNSAFE.putIntUnaligned(null, d + 4L, UNSAFE.getIntUnaligned(null, s + 4L));
            UNSAFE.putIntUnaligned(null, d + 8L, UNSAFE.getIntUnaligned(null, s + 8L));
            UNSAFE.putIntUnaligned(null, d + 12L, UNSAFE.getIntUnaligned(null, s + 12L));

            UNSAFE.putIntUnaligned(null, d + 0L, UNSAFE.getIntUnaligned(null, s + 0L));
            UNSAFE.putIntUnaligned(null, d + 4L, UNSAFE.getIntUnaligned(null, s + 4L));
            UNSAFE.putIntUnaligned(null, d + 8L, UNSAFE.getIntUnaligned(null, s + 8L));
            UNSAFE.putIntUnaligned(null, d + 12L, UNSAFE.getIntUnaligned(null, s + 12L));

            UNSAFE.putIntUnaligned(null, d + 0L, UNSAFE.getIntUnaligned(null, s + 0L));
            UNSAFE.putIntUnaligned(null, d + 4L, UNSAFE.getIntUnaligned(null, s + 4L));
            UNSAFE.putIntUnaligned(null, d + 8L, UNSAFE.getIntUnaligned(null, s + 8L));
            UNSAFE.putIntUnaligned(null, d + 12L, UNSAFE.getIntUnaligned(null, s + 12L));

            UNSAFE.putIntUnaligned(null, d + 0L, UNSAFE.getIntUnaligned(null, s + 0L));
            UNSAFE.putIntUnaligned(null, d + 4L, UNSAFE.getIntUnaligned(null, s + 4L));
            UNSAFE.putIntUnaligned(null, d + 8L, UNSAFE.getIntUnaligned(null, s + 8L));
            UNSAFE.putIntUnaligned(null, d + 12L, UNSAFE.getIntUnaligned(null, s + 12L));

            UNSAFE.putIntUnaligned(null, d + 0L, UNSAFE.getIntUnaligned(null, s + 0L));
            UNSAFE.putIntUnaligned(null, d + 4L, UNSAFE.getIntUnaligned(null, s + 4L));
            UNSAFE.putIntUnaligned(null, d + 8L, UNSAFE.getIntUnaligned(null, s + 8L));
            UNSAFE.putIntUnaligned(null, d + 12L, UNSAFE.getIntUnaligned(null, s + 12L));

            UNSAFE.putIntUnaligned(null, d + 0L, UNSAFE.getIntUnaligned(null, s + 0L));
            UNSAFE.putIntUnaligned(null, d + 4L, UNSAFE.getIntUnaligned(null, s + 4L));
            UNSAFE.putIntUnaligned(null, d + 8L, UNSAFE.getIntUnaligned(null, s + 8L));
            UNSAFE.putIntUnaligned(null, d + 12L, UNSAFE.getIntUnaligned(null, s + 12L));

            UNSAFE.putIntUnaligned(null, d + 0L, UNSAFE.getIntUnaligned(null, s + 0L));
            UNSAFE.putIntUnaligned(null, d + 4L, UNSAFE.getIntUnaligned(null, s + 4L));
            UNSAFE.putIntUnaligned(null, d + 8L, UNSAFE.getIntUnaligned(null, s + 8L));
            UNSAFE.putIntUnaligned(null, d + 12L, UNSAFE.getIntUnaligned(null, s + 12L));

            UNSAFE.putIntUnaligned(null, d + 0L, UNSAFE.getIntUnaligned(null, s + 0L));
            UNSAFE.putIntUnaligned(null, d + 4L, UNSAFE.getIntUnaligned(null, s + 4L));
            UNSAFE.putIntUnaligned(null, d + 8L, UNSAFE.getIntUnaligned(null, s + 8L));
            UNSAFE.putIntUnaligned(null, d + 12L, UNSAFE.getIntUnaligned(null, s + 12L));

            UNSAFE.putIntUnaligned(null, d + 0L, UNSAFE.getIntUnaligned(null, s + 0L));
            UNSAFE.putIntUnaligned(null, d + 4L, UNSAFE.getIntUnaligned(null, s + 4L));
            UNSAFE.putIntUnaligned(null, d + 8L, UNSAFE.getIntUnaligned(null, s + 8L));
            UNSAFE.putIntUnaligned(null, d + 12L, UNSAFE.getIntUnaligned(null, s + 12L));

            UNSAFE.putIntUnaligned(null, d + 0L, UNSAFE.getIntUnaligned(null, s + 0L));
            UNSAFE.putIntUnaligned(null, d + 4L, UNSAFE.getIntUnaligned(null, s + 4L));
            UNSAFE.putIntUnaligned(null, d + 8L, UNSAFE.getIntUnaligned(null, s + 8L));
            UNSAFE.putIntUnaligned(null, d + 12L, UNSAFE.getIntUnaligned(null, s + 12L));

            UNSAFE.putIntUnaligned(null, d + 0L, UNSAFE.getIntUnaligned(null, s + 0L));
            UNSAFE.putIntUnaligned(null, d + 4L, UNSAFE.getIntUnaligned(null, s + 4L));
            UNSAFE.putIntUnaligned(null, d + 8L, UNSAFE.getIntUnaligned(null, s + 8L));
            UNSAFE.putIntUnaligned(null, d + 12L, UNSAFE.getIntUnaligned(null, s + 12L));

            UNSAFE.putIntUnaligned(null, d + 0L, UNSAFE.getIntUnaligned(null, s + 0L));
            UNSAFE.putIntUnaligned(null, d + 4L, UNSAFE.getIntUnaligned(null, s + 4L));
            UNSAFE.putIntUnaligned(null, d + 8L, UNSAFE.getIntUnaligned(null, s + 8L));
            UNSAFE.putIntUnaligned(null, d + 12L, UNSAFE.getIntUnaligned(null, s + 12L));

            UNSAFE.putIntUnaligned(null, d + 0L, UNSAFE.getIntUnaligned(null, s + 0L));
            UNSAFE.putIntUnaligned(null, d + 4L, UNSAFE.getIntUnaligned(null, s + 4L));
            UNSAFE.putIntUnaligned(null, d + 8L, UNSAFE.getIntUnaligned(null, s + 8L));
            UNSAFE.putIntUnaligned(null, d + 12L, UNSAFE.getIntUnaligned(null, s + 12L));

            UNSAFE.putIntUnaligned(null, d + 0L, UNSAFE.getIntUnaligned(null, s + 0L));
            UNSAFE.putIntUnaligned(null, d + 4L, UNSAFE.getIntUnaligned(null, s + 4L));
            UNSAFE.putIntUnaligned(null, d + 8L, UNSAFE.getIntUnaligned(null, s + 8L));
            UNSAFE.putIntUnaligned(null, d + 12L, UNSAFE.getIntUnaligned(null, s + 12L));

            UNSAFE.putIntUnaligned(null, d + 0L, UNSAFE.getIntUnaligned(null, s + 0L));
            UNSAFE.putIntUnaligned(null, d + 4L, UNSAFE.getIntUnaligned(null, s + 4L));
            UNSAFE.putIntUnaligned(null, d + 8L, UNSAFE.getIntUnaligned(null, s + 8L));
            UNSAFE.putIntUnaligned(null, d + 12L, UNSAFE.getIntUnaligned(null, s + 12L));
        }
    }

    // ------------------

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void copySegmentInline() {
        var dst = this.dst;
        var src = this.src;

        for (var i = 0; i < ITERS; i++) {
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 0L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 0L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 4L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 4L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 8L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 8L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 12L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 12L));

            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 0L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 0L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 4L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 4L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 8L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 8L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 12L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 12L));

            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 0L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 0L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 4L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 4L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 8L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 8L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 12L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 12L));

            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 0L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 0L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 4L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 4L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 8L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 8L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 12L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 12L));

            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 0L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 0L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 4L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 4L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 8L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 8L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 12L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 12L));

            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 0L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 0L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 4L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 4L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 8L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 8L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 12L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 12L));

            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 0L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 0L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 4L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 4L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 8L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 8L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 12L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 12L));

            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 0L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 0L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 4L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 4L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 8L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 8L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 12L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 12L));

            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 0L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 0L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 4L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 4L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 8L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 8L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 12L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 12L));

            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 0L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 0L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 4L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 4L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 8L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 8L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 12L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 12L));

            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 0L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 0L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 4L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 4L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 8L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 8L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 12L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 12L));

            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 0L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 0L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 4L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 4L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 8L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 8L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 12L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 12L));

            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 0L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 0L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 4L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 4L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 8L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 8L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 12L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 12L));

            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 0L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 0L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 4L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 4L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 8L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 8L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 12L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 12L));

            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 0L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 0L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 4L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 4L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 8L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 8L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 12L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 12L));

            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 0L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 0L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 4L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 4L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 8L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 8L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 12L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 12L));
        }
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void copySegmentInlineDouble() {
        var dst = this.dst;
        var src = this.src;

        for (var i = 0; i < ITERS; i++) {
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 0L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 0L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 4L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 4L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 8L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 8L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 12L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 12L));

            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 0L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 0L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 4L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 4L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 8L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 8L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 12L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 12L));

            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 0L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 0L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 4L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 4L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 8L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 8L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 12L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 12L));

            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 0L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 0L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 4L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 4L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 8L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 8L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 12L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 12L));

            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 0L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 0L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 4L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 4L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 8L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 8L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 12L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 12L));

            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 0L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 0L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 4L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 4L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 8L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 8L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 12L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 12L));

            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 0L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 0L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 4L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 4L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 8L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 8L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 12L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 12L));

            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 0L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 0L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 4L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 4L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 8L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 8L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 12L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 12L));

            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 0L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 0L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 4L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 4L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 8L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 8L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 12L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 12L));

            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 0L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 0L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 4L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 4L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 8L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 8L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 12L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 12L));

            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 0L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 0L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 4L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 4L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 8L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 8L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 12L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 12L));

            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 0L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 0L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 4L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 4L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 8L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 8L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 12L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 12L));

            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 0L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 0L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 4L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 4L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 8L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 8L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 12L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 12L));

            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 0L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 0L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 4L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 4L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 8L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 8L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 12L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 12L));

            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 0L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 0L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 4L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 4L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 8L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 8L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 12L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 12L));

            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 0L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 0L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 4L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 4L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 8L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 8L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 12L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 12L));

            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 0L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 0L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 4L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 4L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 8L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 8L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 12L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 12L));

            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 0L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 0L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 4L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 4L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 8L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 8L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 12L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 12L));

            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 0L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 0L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 4L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 4L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 8L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 8L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 12L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 12L));

            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 0L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 0L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 4L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 4L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 8L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 8L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 12L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 12L));

            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 0L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 0L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 4L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 4L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 8L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 8L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 12L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 12L));

            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 0L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 0L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 4L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 4L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 8L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 8L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 12L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 12L));

            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 0L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 0L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 4L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 4L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 8L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 8L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 12L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 12L));

            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 0L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 0L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 4L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 4L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 8L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 8L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 12L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 12L));

            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 0L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 0L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 4L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 4L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 8L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 8L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 12L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 12L));

            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 0L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 0L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 4L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 4L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 8L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 8L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 12L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 12L));

            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 0L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 0L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 4L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 4L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 8L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 8L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 12L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 12L));

            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 0L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 0L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 4L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 4L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 8L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 8L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 12L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 12L));

            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 0L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 0L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 4L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 4L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 8L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 8L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 12L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 12L));

            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 0L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 0L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 4L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 4L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 8L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 8L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 12L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 12L));

            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 0L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 0L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 4L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 4L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 8L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 8L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 12L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 12L));

            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 0L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 0L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 4L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 4L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 8L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 8L));
            dst.set(ValueLayout.JAVA_INT_UNALIGNED, 12L, src.get(ValueLayout.JAVA_INT_UNALIGNED, 12L));
        }
    }

    // ------------------

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void copySegmentVHInline() {
        var d = this.ds;
        var s = this.ss;

        for (var i = 0; i < ITERS; i++) {
            X.set(d.segment, 0L, (int)X.get(s.segment, 0L));
            Y.set(d.segment, 0L, (int)Y.get(s.segment, 0L));
            Z.set(d.segment, 0L, (int)Z.get(s.segment, 0L));
            W.set(d.segment, 0L, (int)W.get(s.segment, 0L));

            X.set(d.segment, 0L, (int)X.get(s.segment, 0L));
            Y.set(d.segment, 0L, (int)Y.get(s.segment, 0L));
            Z.set(d.segment, 0L, (int)Z.get(s.segment, 0L));
            W.set(d.segment, 0L, (int)W.get(s.segment, 0L));

            X.set(d.segment, 0L, (int)X.get(s.segment, 0L));
            Y.set(d.segment, 0L, (int)Y.get(s.segment, 0L));
            Z.set(d.segment, 0L, (int)Z.get(s.segment, 0L));
            W.set(d.segment, 0L, (int)W.get(s.segment, 0L));

            X.set(d.segment, 0L, (int)X.get(s.segment, 0L));
            Y.set(d.segment, 0L, (int)Y.get(s.segment, 0L));
            Z.set(d.segment, 0L, (int)Z.get(s.segment, 0L));
            W.set(d.segment, 0L, (int)W.get(s.segment, 0L));

            X.set(d.segment, 0L, (int)X.get(s.segment, 0L));
            Y.set(d.segment, 0L, (int)Y.get(s.segment, 0L));
            Z.set(d.segment, 0L, (int)Z.get(s.segment, 0L));
            W.set(d.segment, 0L, (int)W.get(s.segment, 0L));

            X.set(d.segment, 0L, (int)X.get(s.segment, 0L));
            Y.set(d.segment, 0L, (int)Y.get(s.segment, 0L));
            Z.set(d.segment, 0L, (int)Z.get(s.segment, 0L));
            W.set(d.segment, 0L, (int)W.get(s.segment, 0L));

            X.set(d.segment, 0L, (int)X.get(s.segment, 0L));
            Y.set(d.segment, 0L, (int)Y.get(s.segment, 0L));
            Z.set(d.segment, 0L, (int)Z.get(s.segment, 0L));
            W.set(d.segment, 0L, (int)W.get(s.segment, 0L));

            X.set(d.segment, 0L, (int)X.get(s.segment, 0L));
            Y.set(d.segment, 0L, (int)Y.get(s.segment, 0L));
            Z.set(d.segment, 0L, (int)Z.get(s.segment, 0L));
            W.set(d.segment, 0L, (int)W.get(s.segment, 0L));

            X.set(d.segment, 0L, (int)X.get(s.segment, 0L));
            Y.set(d.segment, 0L, (int)Y.get(s.segment, 0L));
            Z.set(d.segment, 0L, (int)Z.get(s.segment, 0L));
            W.set(d.segment, 0L, (int)W.get(s.segment, 0L));

            X.set(d.segment, 0L, (int)X.get(s.segment, 0L));
            Y.set(d.segment, 0L, (int)Y.get(s.segment, 0L));
            Z.set(d.segment, 0L, (int)Z.get(s.segment, 0L));
            W.set(d.segment, 0L, (int)W.get(s.segment, 0L));

            X.set(d.segment, 0L, (int)X.get(s.segment, 0L));
            Y.set(d.segment, 0L, (int)Y.get(s.segment, 0L));
            Z.set(d.segment, 0L, (int)Z.get(s.segment, 0L));
            W.set(d.segment, 0L, (int)W.get(s.segment, 0L));

            X.set(d.segment, 0L, (int)X.get(s.segment, 0L));
            Y.set(d.segment, 0L, (int)Y.get(s.segment, 0L));
            Z.set(d.segment, 0L, (int)Z.get(s.segment, 0L));
            W.set(d.segment, 0L, (int)W.get(s.segment, 0L));

            X.set(d.segment, 0L, (int)X.get(s.segment, 0L));
            Y.set(d.segment, 0L, (int)Y.get(s.segment, 0L));
            Z.set(d.segment, 0L, (int)Z.get(s.segment, 0L));
            W.set(d.segment, 0L, (int)W.get(s.segment, 0L));

            X.set(d.segment, 0L, (int)X.get(s.segment, 0L));
            Y.set(d.segment, 0L, (int)Y.get(s.segment, 0L));
            Z.set(d.segment, 0L, (int)Z.get(s.segment, 0L));
            W.set(d.segment, 0L, (int)W.get(s.segment, 0L));

            X.set(d.segment, 0L, (int)X.get(s.segment, 0L));
            Y.set(d.segment, 0L, (int)Y.get(s.segment, 0L));
            Z.set(d.segment, 0L, (int)Z.get(s.segment, 0L));
            W.set(d.segment, 0L, (int)W.get(s.segment, 0L));

            X.set(d.segment, 0L, (int)X.get(s.segment, 0L));
            Y.set(d.segment, 0L, (int)Y.get(s.segment, 0L));
            Z.set(d.segment, 0L, (int)Z.get(s.segment, 0L));
            W.set(d.segment, 0L, (int)W.get(s.segment, 0L));
        }
    }

    // ------------------

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void copyUnsafe() {
        var d = this.dau;
        var s = this.sau;

        for (var i = 0; i < ITERS; i++) {
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());

            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());

            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());

            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
        }
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void copyUnsafeIndirect() {
        var d = this.daui;
        var s = this.saui;

        for (var i = 0; i < ITERS; i++) {
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());

            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());

            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());

            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
        }
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void copyUnsafeDeep() {
        var d = this.daud;
        var s = this.saud;

        for (var i = 0; i < ITERS; i++) {
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());

            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());

            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());

            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
        }
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void copyUnsafeDeepDouble() {
        var d = this.daud;
        var s = this.saud;

        for (var i = 0; i < ITERS; i++) {
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());

            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());

            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());

            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());

            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());

            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());

            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());

            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
        }
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void copySegmentDeep() {
        var d = this.dsdp;
        var s = this.ssdp;

        for (var i = 0; i < ITERS; i++) {
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());

            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());

            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());

            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
        }
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void copySegmentDeepDouble() {
        var d = this.dsdp;
        var s = this.ssdp;

        for (var i = 0; i < ITERS; i++) {
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());

            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());

            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());

            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());

            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());

            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());

            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());

            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
        }
    }

    // ------------------

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void copySegment() {
        var d = this.ds;
        var s = this.ss;

        for (var i = 0; i < ITERS; i++) {
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());

            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());

            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());

            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
        }
    }

    // ------------------

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void copySegmentVH() {
        var d = this.dsv;
        var s = this.ssv;

        for (var i = 0; i < ITERS; i++) {
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());

            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());

            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());

            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
        }
    }

    // ------------------

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void copySegmentReinterpret() {
        var d = this.das;
        var s = this.sas;

        for (var i = 0; i < ITERS; i++) {
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());

            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());

            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());

            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
        }
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void copySegmentEverything() {
        var d = this.dae;
        var s = this.sae;

        for (var i = 0; i < ITERS; i++) {
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());

            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());

            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());

            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
        }
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void copyBuffer() {
        var d = this.db;
        var s = this.sb;

        for (var i = 0; i < ITERS; i++) {
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());

            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());

            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());

            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
        }
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void copyBufferInline() {
        var d = this.db.buffer;
        var s = this.sb.buffer;

        for (var i = 0; i < ITERS; i++) {
            d.putInt(0, s.getInt(0));
            d.putInt(4, s.getInt(4));
            d.putInt(8, s.getInt(8));
            d.putInt(12, s.getInt(12));

            d.putInt(0, s.getInt(0));
            d.putInt(4, s.getInt(4));
            d.putInt(8, s.getInt(8));
            d.putInt(12, s.getInt(12));

            d.putInt(0, s.getInt(0));
            d.putInt(4, s.getInt(4));
            d.putInt(8, s.getInt(8));
            d.putInt(12, s.getInt(12));

            d.putInt(0, s.getInt(0));
            d.putInt(4, s.getInt(4));
            d.putInt(8, s.getInt(8));
            d.putInt(12, s.getInt(12));

            d.putInt(0, s.getInt(0));
            d.putInt(4, s.getInt(4));
            d.putInt(8, s.getInt(8));
            d.putInt(12, s.getInt(12));

            d.putInt(0, s.getInt(0));
            d.putInt(4, s.getInt(4));
            d.putInt(8, s.getInt(8));
            d.putInt(12, s.getInt(12));

            d.putInt(0, s.getInt(0));
            d.putInt(4, s.getInt(4));
            d.putInt(8, s.getInt(8));
            d.putInt(12, s.getInt(12));

            d.putInt(0, s.getInt(0));
            d.putInt(4, s.getInt(4));
            d.putInt(8, s.getInt(8));
            d.putInt(12, s.getInt(12));

            d.putInt(0, s.getInt(0));
            d.putInt(4, s.getInt(4));
            d.putInt(8, s.getInt(8));
            d.putInt(12, s.getInt(12));

            d.putInt(0, s.getInt(0));
            d.putInt(4, s.getInt(4));
            d.putInt(8, s.getInt(8));
            d.putInt(12, s.getInt(12));

            d.putInt(0, s.getInt(0));
            d.putInt(4, s.getInt(4));
            d.putInt(8, s.getInt(8));
            d.putInt(12, s.getInt(12));

            d.putInt(0, s.getInt(0));
            d.putInt(4, s.getInt(4));
            d.putInt(8, s.getInt(8));
            d.putInt(12, s.getInt(12));

            d.putInt(0, s.getInt(0));
            d.putInt(4, s.getInt(4));
            d.putInt(8, s.getInt(8));
            d.putInt(12, s.getInt(12));

            d.putInt(0, s.getInt(0));
            d.putInt(4, s.getInt(4));
            d.putInt(8, s.getInt(8));
            d.putInt(12, s.getInt(12));

            d.putInt(0, s.getInt(0));
            d.putInt(4, s.getInt(4));
            d.putInt(8, s.getInt(8));
            d.putInt(12, s.getInt(12));

            d.putInt(0, s.getInt(0));
            d.putInt(4, s.getInt(4));
            d.putInt(8, s.getInt(8));
            d.putInt(12, s.getInt(12));
        }
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void copySegmentStaticAccessors() {
        var d = this.dst;
        var s = this.src;

        for (var i = 0; i < ITERS; i++) {
            Vec4iStatic.x(d, Vec4iStatic.x(s));
            Vec4iStatic.y(d, Vec4iStatic.y(s));
            Vec4iStatic.z(d, Vec4iStatic.z(s));
            Vec4iStatic.w(d, Vec4iStatic.w(s));

            Vec4iStatic.x(d, Vec4iStatic.x(s));
            Vec4iStatic.y(d, Vec4iStatic.y(s));
            Vec4iStatic.z(d, Vec4iStatic.z(s));
            Vec4iStatic.w(d, Vec4iStatic.w(s));

            Vec4iStatic.x(d, Vec4iStatic.x(s));
            Vec4iStatic.y(d, Vec4iStatic.y(s));
            Vec4iStatic.z(d, Vec4iStatic.z(s));
            Vec4iStatic.w(d, Vec4iStatic.w(s));

            Vec4iStatic.x(d, Vec4iStatic.x(s));
            Vec4iStatic.y(d, Vec4iStatic.y(s));
            Vec4iStatic.z(d, Vec4iStatic.z(s));
            Vec4iStatic.w(d, Vec4iStatic.w(s));

            Vec4iStatic.x(d, Vec4iStatic.x(s));
            Vec4iStatic.y(d, Vec4iStatic.y(s));
            Vec4iStatic.z(d, Vec4iStatic.z(s));
            Vec4iStatic.w(d, Vec4iStatic.w(s));

            Vec4iStatic.x(d, Vec4iStatic.x(s));
            Vec4iStatic.y(d, Vec4iStatic.y(s));
            Vec4iStatic.z(d, Vec4iStatic.z(s));
            Vec4iStatic.w(d, Vec4iStatic.w(s));

            Vec4iStatic.x(d, Vec4iStatic.x(s));
            Vec4iStatic.y(d, Vec4iStatic.y(s));
            Vec4iStatic.z(d, Vec4iStatic.z(s));
            Vec4iStatic.w(d, Vec4iStatic.w(s));

            Vec4iStatic.x(d, Vec4iStatic.x(s));
            Vec4iStatic.y(d, Vec4iStatic.y(s));
            Vec4iStatic.z(d, Vec4iStatic.z(s));
            Vec4iStatic.w(d, Vec4iStatic.w(s));

            Vec4iStatic.x(d, Vec4iStatic.x(s));
            Vec4iStatic.y(d, Vec4iStatic.y(s));
            Vec4iStatic.z(d, Vec4iStatic.z(s));
            Vec4iStatic.w(d, Vec4iStatic.w(s));

            Vec4iStatic.x(d, Vec4iStatic.x(s));
            Vec4iStatic.y(d, Vec4iStatic.y(s));
            Vec4iStatic.z(d, Vec4iStatic.z(s));
            Vec4iStatic.w(d, Vec4iStatic.w(s));

            Vec4iStatic.x(d, Vec4iStatic.x(s));
            Vec4iStatic.y(d, Vec4iStatic.y(s));
            Vec4iStatic.z(d, Vec4iStatic.z(s));
            Vec4iStatic.w(d, Vec4iStatic.w(s));

            Vec4iStatic.x(d, Vec4iStatic.x(s));
            Vec4iStatic.y(d, Vec4iStatic.y(s));
            Vec4iStatic.z(d, Vec4iStatic.z(s));
            Vec4iStatic.w(d, Vec4iStatic.w(s));

            Vec4iStatic.x(d, Vec4iStatic.x(s));
            Vec4iStatic.y(d, Vec4iStatic.y(s));
            Vec4iStatic.z(d, Vec4iStatic.z(s));
            Vec4iStatic.w(d, Vec4iStatic.w(s));

            Vec4iStatic.x(d, Vec4iStatic.x(s));
            Vec4iStatic.y(d, Vec4iStatic.y(s));
            Vec4iStatic.z(d, Vec4iStatic.z(s));
            Vec4iStatic.w(d, Vec4iStatic.w(s));

            Vec4iStatic.x(d, Vec4iStatic.x(s));
            Vec4iStatic.y(d, Vec4iStatic.y(s));
            Vec4iStatic.z(d, Vec4iStatic.z(s));
            Vec4iStatic.w(d, Vec4iStatic.w(s));

            Vec4iStatic.x(d, Vec4iStatic.x(s));
            Vec4iStatic.y(d, Vec4iStatic.y(s));
            Vec4iStatic.z(d, Vec4iStatic.z(s));
            Vec4iStatic.w(d, Vec4iStatic.w(s));
        }
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void copySegmentIndirect() {
        var d = this.dsi;
        var s = this.ssi;

        for (var i = 0; i < ITERS; i++) {
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());

            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());

            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());

            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
            d.x(s.x()).y(s.y()).z(s.z()).w(s.w());
        }
    }
}
