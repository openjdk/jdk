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

package compiler.vectorapi;

/*
 * @test
 * @bug 8329555
 * @modules jdk.incubator.vector
 *
 * @run main/othervm -Xbatch -XX:+TieredCompilation compiler.vectorapi.TestBiMorphicMismatchedMemSegment
 */


import jdk.incubator.vector.ByteVector;

import java.lang.foreign.MemorySegment;
import java.nio.ByteOrder;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

public class TestBiMorphicMismatchedMemSegment {
    public static void main(String[] args) {
        AtomicLong aLong = new AtomicLong();

        IntStream.range(0, 10000).forEach(j -> {
            byte[] bytes = new byte[64];
            ThreadLocalRandom.current().nextBytes(bytes);
            var byteSegment = MemorySegment.ofArray(bytes);
            var byteFragment = ByteVector.SPECIES_PREFERRED.fromMemorySegment(byteSegment, 0, ByteOrder.LITTLE_ENDIAN);
            float[] floats = new float[128];
            byte[] targetBytes = new byte[512];
            var floatSegment = MemorySegment.ofArray(floats);
            var targetByteSegment = MemorySegment.ofArray(targetBytes);
            byteFragment.intoMemorySegment(floatSegment, ThreadLocalRandom.current().nextInt(0, 448), ByteOrder.LITTLE_ENDIAN);
            byteFragment.intoMemorySegment(targetByteSegment, ThreadLocalRandom.current().nextInt(0, 448), ByteOrder.LITTLE_ENDIAN);
            var l = 0;
            for (int i = 0; i < floats.length; i++) {
                l += (int) floats[i];
            }
            aLong.addAndGet(l);
        });

        System.out.println(aLong.get());
    }
}
