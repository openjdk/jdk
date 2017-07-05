/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package com.apple.jobjc;

import java.nio.ByteBuffer;

public class BenchUnsafe extends BaseBench{
    final static int ITERS = 1000000;

    final static NativeBuffer NBUF = new NativeBuffer(2 * ITERS);
    final static ByteBuffer BBUF = NBUF.buffer;
    final static long ADDR = NBUF.bufferPtr;
    final static long ADDR_MAX = NBUF.bufferPtr + ITERS;

    final static NativeBuffer NBUF2 = new NativeBuffer(2 * ITERS);
    final static ByteBuffer BBUF2 = NBUF2.buffer;
    final static long ADDR2 = NBUF2.bufferPtr;
    final static long ADDR2_MAX = NBUF2.bufferPtr + ITERS;

    final static long ARG = 345;

    final static long BUFSIZE = BBUF.limit();

    public void testIt(){
        this.bench("Memory writes", 5, 3, 100L,

                new Task("buffer.putLong"){
            @Override public void run() {
                for(long i = 0; i < ITERS; i++)
                    BBUF.putLong((int) i, ARG);
            }},

            new Task("unsafe.putLong"){
                @Override public void run() {
                    for(long i = ADDR; i < ADDR_MAX; i++)
                        UNSAFE.putLong(i, ARG);
                }});
    }
}
