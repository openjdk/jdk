/*
 * Copyright (c) 2014, 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.openjdk.bench.java.lang;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.concurrent.TimeUnit;

/**
 * Performance test of String.hashCode() function
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Fork(jvmArgsAppend = "--add-opens=java.base/java.lang=ALL-UNNAMED")
public class StringHashCode {
    static final MethodHandle HASHCODE_LATIN1;
    static final MethodHandle HASHCODE_UTF16;

    static {
        try {
            var lookup = MethodHandles.privateLookupIn(String.class, MethodHandles.lookup());
            var latin1Klass = lookup.findClass("java.lang.StringLatin1");
            HASHCODE_LATIN1 = lookup.findStatic(latin1Klass, "hashCode",
                                                MethodType.methodType(int.class, byte.class.arrayType()));
            var utf16Klass  = lookup.findClass("java.lang.StringUTF16");
            HASHCODE_UTF16  = lookup.findStatic(utf16Klass,  "hashCode",
                                                MethodType.methodType(int.class, byte.class.arrayType()));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Param({"1", "3", "10", "30", "100", "300", "1000"})
    private int length;

    private String zeroCached;
    private String nonZeroCached;
    private byte[] latin1Uncached;
    private byte[] utf16Uncached;

    @Setup(Level.Iteration)
    public void setup() {
        char[] cachedValue = new char[length];
        zeroCached = new String(cachedValue);
        cachedValue[0] = 1;
        nonZeroCached = new String(cachedValue);

        latin1Uncached = new byte[length];
        utf16Uncached = new byte[length * 2];
    }

    /**
     * Benchmark testing String.hashCode() for a zero hashed String with
     * the result cached in String
     */
    @Benchmark
    public int zeroCached() {
        return zeroCached.hashCode();
    }

    /**
     * Benchmark testing String.hashCode() for a non-zero hashed String with
     * the result cached in String
     */
    @Benchmark
    public int nonZeroCached() {
        return nonZeroCached.hashCode();
    }

    /**
     * Benchmark the computation of String.hashCode() for Latin1 String
     */
    @Benchmark
    public int uncachedLatin1() throws Throwable {
        return (int)HASHCODE_LATIN1.invokeExact(latin1Uncached);
    }

    /**
     * Benchmark the computation of String.hashCode() for UTF16 String
     */
    @Benchmark
    public int uncachedUTF16() throws Throwable {
        return (int)HASHCODE_UTF16.invokeExact(utf16Uncached);
    }

}
