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
package org.openjdk.bench.java.nio;

import java.nio.CharBuffer;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Benchmark for bulk get methods of a {@code CharBuffer} created from a
 * {@code CharSequence}.
 */

@Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Fork(1)
public class StringCharBufferBulkTransfer {
    private static final int LENGTH = 16384;

    char[] buf = new char[LENGTH];
    CharBuffer cb = CharBuffer.wrap(new String(buf));
    char[] dst = new char[LENGTH];
    CharBuffer cbw = CharBuffer.allocate(LENGTH);

    @Benchmark
    public void absoluteBulkGet() {
        cb.get(0, dst, 0, dst.length);
    }

    @Benchmark
    public void relativeBulkGet() {
        cb.get(dst, 0, dst.length);
        cb.position(0);
    }

    @Benchmark
    public void getChars() {
        cb.getChars(0, LENGTH, dst, 0);
    }

    @Benchmark
    public void absoluteBulkPut() {
        cbw.put(0, cb, 0, dst.length);
    }
}
