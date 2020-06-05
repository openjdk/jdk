/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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


package micro.benchmarks;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

/**
 * Benchmarks cost of ArrayList.
 */
public class GroupAllocationBenchmark extends BenchmarkBase {

    static class Node {
        Object x;
        Object y;
        Object z1;
        Object z2;
        Object z3;

        Node(Object x, Object y) {
            this.x = x;
            this.y = y;
        }
    }

    @State(Scope.Benchmark)
    public static class BenchState {
        public byte[] bytes = new byte[16];
        Object c;
        Object a;
        Object b;
        Pair pair = new Pair();
    }

    static class Pair {
        Object left;
        Object right;
    }

    @Benchmark
    public void groupAllocate3(Blackhole bh) {
        bh.consume(new Node(new Node(bh, bh), new Node(bh, bh)));
    }

    @Benchmark
    public void groupAllocate2(Blackhole bh) {
        bh.consume(new Node(new Node(bh, bh), null));
    }

    @Benchmark
    public void groupAllocate1(Blackhole bh) {
        bh.consume(new Node(null, null));
    }

    class BufByteArray {
        int begin;
        int end;
        byte[] bytes;

        BufByteArray(byte[] bytes) {
            this.bytes = bytes;
            this.begin = 116;
            this.end = 324;
        }
    }

    // object(145)=Buf$ByteArray[int begin=116,int end=324,byte[] bytes=294]
    @Benchmark
    public void bufDecode(Blackhole bh, BenchState state) {
        Object a = new BufByteArray(state.bytes);
        Object b = new BufByteArray(state.bytes);
        Pair pair = state.pair;
        pair.left = a;
        pair.right = b;
        bh.consume(pair);
    }

    class TxnLevelImpl {
        int prevReadCount;
        long minRetryTimeoutNanos = 9223372036854775807L;
        long consumedRetryDelta;
        int prevBargeCount;
        int prevWriteThreshold;
        int logSize;
        int[] indices;
        Object[] prevValues;
        int beforeCommitSize;
        int whileValidatingSize;
        int whilePreparingSize;
        int whileCommittingSize;
        int afterCommitSize;
        int afterRollbackSize;
        boolean phantom;
        boolean waiters;
        Object txn;
        Object executor;
        TxnLevelImpl parUndo;
        Object parLevel;
        Object root;
        Object blockedBy;
        Object state;

        TxnLevelImpl(Object a, Object b, Object c) {
            txn = a;
            executor = b;
            root = c;
        }
    }

    @Benchmark
    public void rangeTxnLevelImpl(Blackhole bh, BenchState state) {
        TxnLevelImpl o = new TxnLevelImpl(state.a, state.b, state.c);
        bh.consume(o);
    }
}
