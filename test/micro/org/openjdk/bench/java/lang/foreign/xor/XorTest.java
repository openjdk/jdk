/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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

package org.openjdk.bench.java.lang.foreign.xor;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@State(org.openjdk.jmh.annotations.Scope.Thread)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Fork(value = 3, jvmArgs = { "--enable-native-access=ALL-UNNAMED",
        "-Djava.library.path=micro/native",
        "--add-opens=java.base/jdk.internal.misc=ALL-UNNAMED" })

public class XorTest {

    XorOp impl = null;
    int alen;
    int off;
    int len;
    byte[] src, dst;

    @Param
    SizeKind sizeKind;

    @Param
    ArrayKind arrayKind;

    public enum SizeKind {
        SMALL,
        MEDIUM,
        LARGE;
    }

    public enum ArrayKind {
        JNI_ELEMENTS,
        JNI_REGION,
        JNI_CRITICAL,
        FOREIGN_NO_INIT,
        FOREIGN_INIT,
        FOREIGN_CRITICAL,
        UNSAFE;
    }

    @Setup
    public void setup() throws Throwable {
        switch (arrayKind) {
            case JNI_CRITICAL:
                impl = new GetArrayCriticalXorOpImpl();
                break;
            case JNI_ELEMENTS:
                impl = new GetArrayElementsXorOpImpl();
                break;
            case JNI_REGION:
                impl = new GetArrayRegionXorOpImpl();
                break;
            case FOREIGN_NO_INIT:
                impl = new GetArrayForeignXorOpImpl();
                break;
            case FOREIGN_INIT:
                impl = new GetArrayForeignXorOpInitImpl();
                break;
            case FOREIGN_CRITICAL:
                impl = new GetArrayForeignXorOpCriticalImpl();
                break;
            case UNSAFE:
                impl = new GetArrayUnsafeXorOpImpl();
                break;
            default:
                throw new UnsupportedOperationException(arrayKind.toString());
        }

        switch (sizeKind) {
            case SMALL:
                alen = 1048576;             // 1 MB
                off = 1024 * 10;
                len = 1024 * 100;           // 100 KB
                break;
            case MEDIUM:
                alen = 1048576 * 8;         // 8 MB
                off = 1048576 * 1;
                len = 1048576 * 2;          // 2 MB
                break;
            case LARGE:
                alen = 1048576 * 100;       // 100 MB
                off = 1048576 * 5;
                len = 1048576 * 10;         // 10 MB
                break;
            default:
                throw new UnsupportedOperationException(sizeKind.toString());
        }

        src = new byte[alen];
        dst = new byte[alen];
        Arrays.fill(src, off, off + len, (byte)0xaa);
        Arrays.fill(dst, off, off + len, (byte)0x5a);
        check();
    }

    void check() throws Throwable {
        impl.xor(src, off, dst, off, len);
        if (!verify(dst, off, off + len, (byte)0xf0)) {
            throw new IllegalStateException("Failed to verify");
        }
    }


    @Benchmark
    public void xor() throws Throwable {
        impl.xor(src, off, dst, off, len);
    }

    static boolean verify(byte[] buf, int start, int end, byte val) {
        for (int i = start; i < end; ++i) {
            if (buf[i] != val)
                return false;
        }
        return true;
    }

}
