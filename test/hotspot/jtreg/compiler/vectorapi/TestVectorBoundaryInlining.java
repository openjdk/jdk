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

package compiler.vectorapi;

import jdk.incubator.vector.ByteVector;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;

import java.lang.management.ManagementFactory;

/*
 * @test
 * @summary A Vector API value crossing a method boundary C2 does not inline is
 *          materialized on the heap. A hot method that takes or returns vectors
 *          must be inlined whether the vector crosses as an argument or as a
 *          return value, so that it allocates nothing per call.
 * @modules jdk.incubator.vector jdk.management
 * @library /test/lib /
 * @requires vm.compiler2.enabled
 * @run main/othervm/timeout=300 -XX:+UnlockExperimentalVMOptions -XX:+EnableVectorSupport -Xbatch compiler.vectorapi.TestVectorBoundaryInlining
 * @run main/othervm/timeout=300 -XX:+UnlockExperimentalVMOptions -XX:+EnableVectorSupport -Xbatch -XX:-TieredCompilation compiler.vectorapi.TestVectorBoundaryInlining
 */

public class TestVectorBoundaryInlining {

    static final VectorSpecies<Byte> SP = ByteVector.SPECIES_PREFERRED;
    static volatile long sink;

    static final ByteVector T = table();

    // A zero vector, used by the return-value method below.
    static final ByteVector ZERO = ByteVector.zero(SP);

    static ByteVector table() {
        byte[] t = new byte[SP.length()];
        for (int i = 0; i < t.length; i++) {
            t[i] = (byte) ", :[]{}".charAt(i % 7);
        }
        return ByteVector.fromArray(SP, t, 0);
    }

    // A method taking vectors as arguments. It is deliberately
    // padded well beyond FreqInlineSize (325 bytes by default) so that the
    // bytecode-size inlining heuristic would refuse it unless vector-taking
    // callees are exempted, and it uses only operations that intrinsify on every
    // platform the Vector API supports (no shuffle/rearrange), so the test is not
    // sensitive to whether a particular op happens to fall back on the host CPU.
    static long classify(ByteVector c0, ByteVector c1, ByteVector c2, ByteVector c3) {
        long m = 0;
        m |= c0.lanewise(VectorOperators.AND, (byte) 1).compare(VectorOperators.EQ, c1).toLong();
        m |= c0.lanewise(VectorOperators.AND, (byte) 2).compare(VectorOperators.EQ, c1).toLong() << 8;
        m |= c0.lanewise(VectorOperators.AND, (byte) 3).compare(VectorOperators.EQ, c1).toLong() << 16;
        m |= c0.lanewise(VectorOperators.AND, (byte) 4).compare(VectorOperators.EQ, c1).toLong() << 24;
        m |= c0.lanewise(VectorOperators.AND, (byte) 5).compare(VectorOperators.EQ, c1).toLong() << 32;
        m |= c0.lanewise(VectorOperators.AND, (byte) 6).compare(VectorOperators.EQ, c1).toLong() << 40;
        m |= c0.lanewise(VectorOperators.AND, (byte) 7).compare(VectorOperators.EQ, c1).toLong() << 48;
        m |= c0.lanewise(VectorOperators.AND, (byte) 8).compare(VectorOperators.EQ, c1).toLong() << 56;
        m |= c0.or((byte) 0x20).compare(VectorOperators.EQ, c2).toLong();
        m |= c1.or((byte) 0x20).compare(VectorOperators.EQ, c3).toLong() << 8;
        m |= c2.or((byte) 0x20).compare(VectorOperators.EQ, c0).toLong() << 16;
        m |= c3.or((byte) 0x20).compare(VectorOperators.EQ, c1).toLong() << 24;
        m |= c0.lanewise(VectorOperators.XOR, c1).compare(VectorOperators.EQ, c2).toLong() << 32;
        m |= c1.lanewise(VectorOperators.XOR, c2).compare(VectorOperators.EQ, c3).toLong() << 40;
        m |= c2.lanewise(VectorOperators.XOR, c3).compare(VectorOperators.EQ, c0).toLong() << 48;
        m |= c3.lanewise(VectorOperators.XOR, c0).compare(VectorOperators.EQ, c1).toLong() << 56;
        m |= c0.lanewise(VectorOperators.AND, (byte) 9).compare(VectorOperators.EQ, c1).toLong();
        m |= c0.lanewise(VectorOperators.AND, (byte) 10).compare(VectorOperators.EQ, c1).toLong() << 8;
        m |= c0.lanewise(VectorOperators.AND, (byte) 11).compare(VectorOperators.EQ, c1).toLong() << 16;
        m |= c0.lanewise(VectorOperators.AND, (byte) 12).compare(VectorOperators.EQ, c1).toLong() << 24;
        if ((m & sink) == 1) m += T.lane(0);
        if ((m & sink) == 2) m += T.lane(1);
        return m;
    }

    static long alloc() {
        return ((com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean())
                .getCurrentThreadAllocatedBytes();
    }

    // Verify the allocation counter actually moves, so the test cannot pass
    // vacuously when thread allocation accounting is unsupported or disabled
    // (getCurrentThreadAllocatedBytes() would then report a constant -1).
    static void requireAllocationAccounting() {
        com.sun.management.ThreadMXBean bean =
                (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        if (!bean.isThreadAllocatedMemorySupported()) {
            throw new jtreg.SkippedException("Thread allocated memory is not supported");
        }
        bean.setThreadAllocatedMemoryEnabled(true);
        long before = alloc();
        Object[] garbage = new Object[10_000];
        for (int i = 0; i < garbage.length; i++) {
            garbage[i] = new Object();
        }
        sink += garbage.length;
        long after = alloc();
        if (after < before || after - before < 100_000) {
            throw new RuntimeException("Thread allocated memory counter did not advance: "
                                       + before + " -> " + after);
        }
    }

    // The four arguments are consecutive vectors at the preferred species width;
    // stride accordingly so this works for any SPECIES_PREFERRED (16 bytes on
    // aarch64/SSE, up to 64 bytes with AVX-512).
    static final int STRIDE = SP.length();

    // A value crossing a non-inlined boundary is materialized whether it is a
    // vector argument or a vector return, so cover both.

    static long runArgs(byte[] a) {
        return classify(ByteVector.fromArray(SP, a, 0),
                        ByteVector.fromArray(SP, a, 1 * STRIDE),
                        ByteVector.fromArray(SP, a, 2 * STRIDE),
                        ByteVector.fromArray(SP, a, 3 * STRIDE));
    }

    // Returns a vector and takes none, so the vector crosses the boundary as a
    // return value. Like the argument method it is padded past FreqInlineSize
    // (325 bytes by default), otherwise it would inline under the ordinary
    // frequency heuristic and the check would not exercise the exemption.
    static ByteVector make(byte[] a) {
        ByteVector v = ByteVector.fromArray(SP, a, 0);
        long m = 0;
        m |= v.lanewise(VectorOperators.AND, (byte) 1).compare(VectorOperators.EQ, ZERO).toLong();
        m |= v.lanewise(VectorOperators.AND, (byte) 2).compare(VectorOperators.EQ, ZERO).toLong() << 8;
        m |= v.lanewise(VectorOperators.AND, (byte) 3).compare(VectorOperators.EQ, ZERO).toLong() << 16;
        m |= v.lanewise(VectorOperators.AND, (byte) 4).compare(VectorOperators.EQ, ZERO).toLong() << 24;
        m |= v.lanewise(VectorOperators.AND, (byte) 5).compare(VectorOperators.EQ, ZERO).toLong() << 32;
        m |= v.lanewise(VectorOperators.AND, (byte) 6).compare(VectorOperators.EQ, ZERO).toLong() << 40;
        m |= v.lanewise(VectorOperators.AND, (byte) 7).compare(VectorOperators.EQ, ZERO).toLong() << 48;
        m |= v.lanewise(VectorOperators.AND, (byte) 8).compare(VectorOperators.EQ, ZERO).toLong() << 56;
        m |= v.or((byte) 0x20).compare(VectorOperators.EQ, ZERO).toLong();
        m |= v.lanewise(VectorOperators.XOR, ZERO).compare(VectorOperators.EQ, v).toLong();
        m |= v.lanewise(VectorOperators.AND, (byte) 9).compare(VectorOperators.EQ, ZERO).toLong();
        m |= v.lanewise(VectorOperators.AND, (byte) 10).compare(VectorOperators.EQ, ZERO).toLong();
        m |= v.lanewise(VectorOperators.AND, (byte) 11).compare(VectorOperators.EQ, ZERO).toLong();
        m |= v.lanewise(VectorOperators.AND, (byte) 12).compare(VectorOperators.EQ, ZERO).toLong();
        if ((m & sink) == 1) m += 1;
        return v.add((byte) m);
    }

    static long runReturn(byte[] a) {
        return make(a).lane(0);
    }

    // Measure steady-state bytes allocated per call, taking the minimum over
    // several windows so residual compilation/deoptimization does not distort it.
    // The measured call includes the ByteVector.fromArray loads; the loads
    // themselves must not allocate either. Kept as two explicit loops (rather
    // than a callback) so the measured call sites are direct and inlinable.
    static double bytesPerCallArgs(byte[] a) {
        double best = Double.MAX_VALUE;
        for (int w = 0; w < 10; w++) {
            for (int i = 0; i < 200_000; i++) {
                sink += runArgs(a);
            }
            long before = alloc();
            for (int i = 0; i < 2_000_000; i++) {
                sink += runArgs(a);
            }
            best = Math.min(best, (alloc() - before) / 2_000_000.0);
        }
        return best;
    }

    static double bytesPerCallReturn(byte[] a) {
        double best = Double.MAX_VALUE;
        for (int w = 0; w < 10; w++) {
            for (int i = 0; i < 200_000; i++) {
                sink += runReturn(a);
            }
            long before = alloc();
            for (int i = 0; i < 2_000_000; i++) {
                sink += runReturn(a);
            }
            best = Math.min(best, (alloc() - before) / 2_000_000.0);
        }
        return best;
    }

    static void check(String what, double perCall, StringBuilder failures) {
        System.out.printf("species=%d lanes, %s: %.2f B/call%n", SP.length(), what, perCall);
        if (perCall > 1.0) {
            failures.append("Vector was materialized on the heap at the non-inlined " +
                            "call boundary (").append(what).append("): ")
                    .append(String.format("%.2f", perCall)).append(" B/call\n");
        }
    }

    public static void main(String[] args) {
        requireAllocationAccounting();

        byte[] a = new byte[4 * STRIDE];
        for (int i = 0; i < a.length; i++) {
            a[i] = (byte) i;
        }

        // Warm up. The results depend on the input, so just consume them
        // through the volatile sink rather than asserting on their values.
        for (int i = 0; i < 200_000; i++) {
            sink += runArgs(a);
            sink += runReturn(a);
        }

        // Measure both boundaries so a regression in either is reported, not
        // just whichever is checked first.
        StringBuilder failures = new StringBuilder();
        check("vector arguments", bytesPerCallArgs(a), failures);
        check("vector return", bytesPerCallReturn(a), failures);
        if (failures.length() != 0) {
            throw new AssertionError(failures.toString());
        }
    }
}
