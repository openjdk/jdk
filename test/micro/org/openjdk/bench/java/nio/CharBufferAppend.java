/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Benchmark for {@code CharBuffer} implementations of the {@code Appendable}
 * methods which accept a {@code CharSequence} source.
 */
@Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Fork(1)
public class CharBufferAppend {

    static final int SIZE = 32768;

    static String str;
    static StringBuffer strbuf;
    static StringBuilder strbld;
    static CharBuffer hbDst;
    static CharBuffer hbSrc;
    static CharBuffer dbSrc;
    static CharBuffer dbDst;

    static {
        char[] chars = new char[SIZE];
        Arrays.fill(chars, (char)27);

        strbld = new StringBuilder(SIZE);
        strbld.append(chars);

        str = strbld.toString();

        strbuf = new StringBuffer(SIZE);
        strbuf.append(chars);

        hbDst = CharBuffer.allocate(SIZE);
        hbSrc = CharBuffer.wrap(chars);

        dbDst = ByteBuffer.allocateDirect(2*SIZE).asCharBuffer();
        dbSrc = ByteBuffer.allocateDirect(2*SIZE).asCharBuffer();
        dbSrc.put(chars);
        dbSrc.clear();
    };

    @Benchmark
    public CharBuffer appendDirectToDirect() {
        dbDst.clear();
        dbSrc.clear();
        return dbDst.append(dbSrc);
    }

    @Benchmark
    public CharBuffer appendDirectToHeap() {
        hbDst.clear();
        dbSrc.clear();
        return hbDst.append(dbSrc);
    }

    @Benchmark
    public CharBuffer appendHeapToHeap() {
        hbDst.clear();
        hbSrc.clear();
        return hbDst.append(hbSrc);
    }

    @Benchmark
    public CharBuffer appendHeapToDirect() {
        dbDst.clear();
        hbSrc.clear();
        return dbDst.append(hbSrc);
    }

    @Benchmark
    public CharBuffer appendString() {
        hbDst.clear();
        return hbDst.append(str);
    }

    @Benchmark
    public CharBuffer appendStringBuffer() {
        hbDst.clear();
        return hbDst.append(strbuf);
    }

    @Benchmark
    public CharBuffer appendStringBuilder() {
        hbDst.clear();
        return hbDst.append(strbld);
    }

    @Benchmark
    public CharBuffer appendSubString() {
        hbDst.clear();
        return hbDst.append(str, SIZE/4, 3*SIZE/4);
    }

    @Benchmark
    public CharBuffer appendSubStringBuffer() {
        hbDst.clear();
        return hbDst.append(strbuf, SIZE/4, 3*SIZE/4);
    }

    @Benchmark
    public CharBuffer appendSubStringBuilder() {
        hbDst.clear();
        return hbDst.append(strbld, SIZE/4, 3*SIZE/4);
    }

    @Benchmark
    public CharBuffer appendStringToDirect() {
        dbDst.clear();
        return dbDst.append(str);
    }

    @Benchmark
    public CharBuffer appendStringBufferToDirect() {
        dbDst.clear();
        return dbDst.append(strbuf);
    }

    @Benchmark
    public CharBuffer appendStringBuilderToDirect() {
        dbDst.clear();
        return dbDst.append(strbld);
    }

    @Benchmark
    public CharBuffer appendSubStringToDirect() {
        dbDst.clear();
        return dbDst.append(str, SIZE/4, 3*SIZE/4);
    }

    @Benchmark
    public CharBuffer appendSubStringBufferToDirect() {
        dbDst.clear();
        return dbDst.append(strbuf, SIZE/4, 3*SIZE/4);
    }

    @Benchmark
    public CharBuffer appendSubStringBuilderToDirect() {
        dbDst.clear();
        return dbDst.append(strbld, SIZE/4, 3*SIZE/4);
    }
}
