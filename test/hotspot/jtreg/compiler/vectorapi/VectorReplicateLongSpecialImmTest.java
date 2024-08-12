/*
 * Copyright (c) 2022, Arm Limited. All rights reserved.
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

import jdk.incubator.vector.LongVector;
import jdk.incubator.vector.VectorSpecies;

import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * @test
 * @bug 8282528
 * @summary AArch64: Incorrect replicate2L_zero rule
 * @library /test/lib
 * @requires os.arch == "aarch64"
 * @modules jdk.incubator.vector
 * @run testng/othervm -XX:UseSVE=0 -XX:-TieredCompilation -XX:CompileThreshold=100 -XX:+IgnoreUnrecognizedVMOptions -XX:CompileCommand=MemLimit,*.*,0 compiler.vectorapi.VectorReplicateLongSpecialImmTest
 */
public class VectorReplicateLongSpecialImmTest {

    private static final VectorSpecies<Long> lspec = LongVector.SPECIES_128;
    private static final int INVOC_COUNT = 1000;

    private static void assertEquals(LongVector lv, long expected) {
        Assert.assertEquals(lv.lane(0), expected);
        Assert.assertEquals(lv.lane(1), expected);
    }

    @Test
    public void testReplicateL_Imm() {
        for (int ic = 0; ic < INVOC_COUNT * INVOC_COUNT; ic++) {
            // On AArch64 ReplicateL will generate movi, which requires the 64-bit
            // imm must be in the form of
            // 'aaaaaaaabbbbbbbbccccccccddddddddeeeeeeeeffffffffgggggggghhhhhhhh'
            assertEquals(LongVector.broadcast(lspec, 0x0000000000000000L), 0x0000000000000000L);
            assertEquals(LongVector.broadcast(lspec, 0x00000000000000FFL), 0x00000000000000FFL);
            assertEquals(LongVector.broadcast(lspec, 0x000000000000FF00L), 0x000000000000FF00L);
            assertEquals(LongVector.broadcast(lspec, 0x000000000000FFFFL), 0x000000000000FFFFL);
            assertEquals(LongVector.broadcast(lspec, 0x0000000000FF0000L), 0x0000000000FF0000L);
            assertEquals(LongVector.broadcast(lspec, 0x0000000000FF00FFL), 0x0000000000FF00FFL);
            assertEquals(LongVector.broadcast(lspec, 0x0000000000FFFF00L), 0x0000000000FFFF00L);
            assertEquals(LongVector.broadcast(lspec, 0x0000000000FFFFFFL), 0x0000000000FFFFFFL);
            assertEquals(LongVector.broadcast(lspec, 0x00000000FF000000L), 0x00000000FF000000L);
            assertEquals(LongVector.broadcast(lspec, 0x00000000FF0000FFL), 0x00000000FF0000FFL);
            assertEquals(LongVector.broadcast(lspec, 0x00000000FF00FF00L), 0x00000000FF00FF00L);
            assertEquals(LongVector.broadcast(lspec, 0x00000000FF00FFFFL), 0x00000000FF00FFFFL);
            assertEquals(LongVector.broadcast(lspec, 0x00000000FFFF0000L), 0x00000000FFFF0000L);
            assertEquals(LongVector.broadcast(lspec, 0x00000000FFFF00FFL), 0x00000000FFFF00FFL);
            assertEquals(LongVector.broadcast(lspec, 0x00000000FFFFFF00L), 0x00000000FFFFFF00L);
            assertEquals(LongVector.broadcast(lspec, 0x00000000FFFFFFFFL), 0x00000000FFFFFFFFL);
            assertEquals(LongVector.broadcast(lspec, 0x000000FF00000000L), 0x000000FF00000000L);
            assertEquals(LongVector.broadcast(lspec, 0x000000FF000000FFL), 0x000000FF000000FFL);
            assertEquals(LongVector.broadcast(lspec, 0x000000FF0000FF00L), 0x000000FF0000FF00L);
            assertEquals(LongVector.broadcast(lspec, 0x000000FF0000FFFFL), 0x000000FF0000FFFFL);
            assertEquals(LongVector.broadcast(lspec, 0x000000FF00FF0000L), 0x000000FF00FF0000L);
            assertEquals(LongVector.broadcast(lspec, 0x000000FF00FF00FFL), 0x000000FF00FF00FFL);
            assertEquals(LongVector.broadcast(lspec, 0x000000FF00FFFF00L), 0x000000FF00FFFF00L);
            assertEquals(LongVector.broadcast(lspec, 0x000000FF00FFFFFFL), 0x000000FF00FFFFFFL);
            assertEquals(LongVector.broadcast(lspec, 0x000000FFFF000000L), 0x000000FFFF000000L);
            assertEquals(LongVector.broadcast(lspec, 0x000000FFFF0000FFL), 0x000000FFFF0000FFL);
            assertEquals(LongVector.broadcast(lspec, 0x000000FFFF00FF00L), 0x000000FFFF00FF00L);
            assertEquals(LongVector.broadcast(lspec, 0x000000FFFF00FFFFL), 0x000000FFFF00FFFFL);
            assertEquals(LongVector.broadcast(lspec, 0x000000FFFFFF0000L), 0x000000FFFFFF0000L);
            assertEquals(LongVector.broadcast(lspec, 0x000000FFFFFF00FFL), 0x000000FFFFFF00FFL);
            assertEquals(LongVector.broadcast(lspec, 0x000000FFFFFFFF00L), 0x000000FFFFFFFF00L);
            assertEquals(LongVector.broadcast(lspec, 0x000000FFFFFFFFFFL), 0x000000FFFFFFFFFFL);
            assertEquals(LongVector.broadcast(lspec, 0x0000FF0000000000L), 0x0000FF0000000000L);
            assertEquals(LongVector.broadcast(lspec, 0x0000FF00000000FFL), 0x0000FF00000000FFL);
            assertEquals(LongVector.broadcast(lspec, 0x0000FF000000FF00L), 0x0000FF000000FF00L);
            assertEquals(LongVector.broadcast(lspec, 0x0000FF000000FFFFL), 0x0000FF000000FFFFL);
            assertEquals(LongVector.broadcast(lspec, 0x0000FF0000FF0000L), 0x0000FF0000FF0000L);
            assertEquals(LongVector.broadcast(lspec, 0x0000FF0000FF00FFL), 0x0000FF0000FF00FFL);
            assertEquals(LongVector.broadcast(lspec, 0x0000FF0000FFFF00L), 0x0000FF0000FFFF00L);
            assertEquals(LongVector.broadcast(lspec, 0x0000FF0000FFFFFFL), 0x0000FF0000FFFFFFL);
            assertEquals(LongVector.broadcast(lspec, 0x0000FF00FF000000L), 0x0000FF00FF000000L);
            assertEquals(LongVector.broadcast(lspec, 0x0000FF00FF0000FFL), 0x0000FF00FF0000FFL);
            assertEquals(LongVector.broadcast(lspec, 0x0000FF00FF00FF00L), 0x0000FF00FF00FF00L);
            assertEquals(LongVector.broadcast(lspec, 0x0000FF00FF00FFFFL), 0x0000FF00FF00FFFFL);
            assertEquals(LongVector.broadcast(lspec, 0x0000FF00FFFF0000L), 0x0000FF00FFFF0000L);
            assertEquals(LongVector.broadcast(lspec, 0x0000FF00FFFF00FFL), 0x0000FF00FFFF00FFL);
            assertEquals(LongVector.broadcast(lspec, 0x0000FF00FFFFFF00L), 0x0000FF00FFFFFF00L);
            assertEquals(LongVector.broadcast(lspec, 0x0000FF00FFFFFFFFL), 0x0000FF00FFFFFFFFL);
            assertEquals(LongVector.broadcast(lspec, 0x0000FFFF00000000L), 0x0000FFFF00000000L);
            assertEquals(LongVector.broadcast(lspec, 0x0000FFFF000000FFL), 0x0000FFFF000000FFL);
            assertEquals(LongVector.broadcast(lspec, 0x0000FFFF0000FF00L), 0x0000FFFF0000FF00L);
            assertEquals(LongVector.broadcast(lspec, 0x0000FFFF0000FFFFL), 0x0000FFFF0000FFFFL);
            assertEquals(LongVector.broadcast(lspec, 0x0000FFFF00FF0000L), 0x0000FFFF00FF0000L);
            assertEquals(LongVector.broadcast(lspec, 0x0000FFFF00FF00FFL), 0x0000FFFF00FF00FFL);
            assertEquals(LongVector.broadcast(lspec, 0x0000FFFF00FFFF00L), 0x0000FFFF00FFFF00L);
            assertEquals(LongVector.broadcast(lspec, 0x0000FFFF00FFFFFFL), 0x0000FFFF00FFFFFFL);
            assertEquals(LongVector.broadcast(lspec, 0x0000FFFFFF000000L), 0x0000FFFFFF000000L);
            assertEquals(LongVector.broadcast(lspec, 0x0000FFFFFF0000FFL), 0x0000FFFFFF0000FFL);
            assertEquals(LongVector.broadcast(lspec, 0x0000FFFFFF00FF00L), 0x0000FFFFFF00FF00L);
            assertEquals(LongVector.broadcast(lspec, 0x0000FFFFFF00FFFFL), 0x0000FFFFFF00FFFFL);
            assertEquals(LongVector.broadcast(lspec, 0x0000FFFFFFFF0000L), 0x0000FFFFFFFF0000L);
            assertEquals(LongVector.broadcast(lspec, 0x0000FFFFFFFF00FFL), 0x0000FFFFFFFF00FFL);
            assertEquals(LongVector.broadcast(lspec, 0x0000FFFFFFFFFF00L), 0x0000FFFFFFFFFF00L);
            assertEquals(LongVector.broadcast(lspec, 0x0000FFFFFFFFFFFFL), 0x0000FFFFFFFFFFFFL);
            assertEquals(LongVector.broadcast(lspec, 0x00FF000000000000L), 0x00FF000000000000L);
            assertEquals(LongVector.broadcast(lspec, 0x00FF0000000000FFL), 0x00FF0000000000FFL);
            assertEquals(LongVector.broadcast(lspec, 0x00FF00000000FF00L), 0x00FF00000000FF00L);
            assertEquals(LongVector.broadcast(lspec, 0x00FF00000000FFFFL), 0x00FF00000000FFFFL);
            assertEquals(LongVector.broadcast(lspec, 0x00FF000000FF0000L), 0x00FF000000FF0000L);
            assertEquals(LongVector.broadcast(lspec, 0x00FF000000FF00FFL), 0x00FF000000FF00FFL);
            assertEquals(LongVector.broadcast(lspec, 0x00FF000000FFFF00L), 0x00FF000000FFFF00L);
            assertEquals(LongVector.broadcast(lspec, 0x00FF000000FFFFFFL), 0x00FF000000FFFFFFL);
            assertEquals(LongVector.broadcast(lspec, 0x00FF0000FF000000L), 0x00FF0000FF000000L);
            assertEquals(LongVector.broadcast(lspec, 0x00FF0000FF0000FFL), 0x00FF0000FF0000FFL);
            assertEquals(LongVector.broadcast(lspec, 0x00FF0000FF00FF00L), 0x00FF0000FF00FF00L);
            assertEquals(LongVector.broadcast(lspec, 0x00FF0000FF00FFFFL), 0x00FF0000FF00FFFFL);
            assertEquals(LongVector.broadcast(lspec, 0x00FF0000FFFF0000L), 0x00FF0000FFFF0000L);
            assertEquals(LongVector.broadcast(lspec, 0x00FF0000FFFF00FFL), 0x00FF0000FFFF00FFL);
            assertEquals(LongVector.broadcast(lspec, 0x00FF0000FFFFFF00L), 0x00FF0000FFFFFF00L);
            assertEquals(LongVector.broadcast(lspec, 0x00FF0000FFFFFFFFL), 0x00FF0000FFFFFFFFL);
            assertEquals(LongVector.broadcast(lspec, 0x00FF00FF00000000L), 0x00FF00FF00000000L);
            assertEquals(LongVector.broadcast(lspec, 0x00FF00FF000000FFL), 0x00FF00FF000000FFL);
            assertEquals(LongVector.broadcast(lspec, 0x00FF00FF0000FF00L), 0x00FF00FF0000FF00L);
            assertEquals(LongVector.broadcast(lspec, 0x00FF00FF0000FFFFL), 0x00FF00FF0000FFFFL);
            assertEquals(LongVector.broadcast(lspec, 0x00FF00FF00FF0000L), 0x00FF00FF00FF0000L);
            assertEquals(LongVector.broadcast(lspec, 0x00FF00FF00FF00FFL), 0x00FF00FF00FF00FFL);
            assertEquals(LongVector.broadcast(lspec, 0x00FF00FF00FFFF00L), 0x00FF00FF00FFFF00L);
            assertEquals(LongVector.broadcast(lspec, 0x00FF00FF00FFFFFFL), 0x00FF00FF00FFFFFFL);
            assertEquals(LongVector.broadcast(lspec, 0x00FF00FFFF000000L), 0x00FF00FFFF000000L);
            assertEquals(LongVector.broadcast(lspec, 0x00FF00FFFF0000FFL), 0x00FF00FFFF0000FFL);
            assertEquals(LongVector.broadcast(lspec, 0x00FF00FFFF00FF00L), 0x00FF00FFFF00FF00L);
            assertEquals(LongVector.broadcast(lspec, 0x00FF00FFFF00FFFFL), 0x00FF00FFFF00FFFFL);
            assertEquals(LongVector.broadcast(lspec, 0x00FF00FFFFFF0000L), 0x00FF00FFFFFF0000L);
            assertEquals(LongVector.broadcast(lspec, 0x00FF00FFFFFF00FFL), 0x00FF00FFFFFF00FFL);
            assertEquals(LongVector.broadcast(lspec, 0x00FF00FFFFFFFF00L), 0x00FF00FFFFFFFF00L);
            assertEquals(LongVector.broadcast(lspec, 0x00FF00FFFFFFFFFFL), 0x00FF00FFFFFFFFFFL);
            assertEquals(LongVector.broadcast(lspec, 0x00FFFF0000000000L), 0x00FFFF0000000000L);
            assertEquals(LongVector.broadcast(lspec, 0x00FFFF00000000FFL), 0x00FFFF00000000FFL);
            assertEquals(LongVector.broadcast(lspec, 0x00FFFF000000FF00L), 0x00FFFF000000FF00L);
            assertEquals(LongVector.broadcast(lspec, 0x00FFFF000000FFFFL), 0x00FFFF000000FFFFL);
            assertEquals(LongVector.broadcast(lspec, 0x00FFFF0000FF0000L), 0x00FFFF0000FF0000L);
            assertEquals(LongVector.broadcast(lspec, 0x00FFFF0000FF00FFL), 0x00FFFF0000FF00FFL);
            assertEquals(LongVector.broadcast(lspec, 0x00FFFF0000FFFF00L), 0x00FFFF0000FFFF00L);
            assertEquals(LongVector.broadcast(lspec, 0x00FFFF0000FFFFFFL), 0x00FFFF0000FFFFFFL);
            assertEquals(LongVector.broadcast(lspec, 0x00FFFF00FF000000L), 0x00FFFF00FF000000L);
            assertEquals(LongVector.broadcast(lspec, 0x00FFFF00FF0000FFL), 0x00FFFF00FF0000FFL);
            assertEquals(LongVector.broadcast(lspec, 0x00FFFF00FF00FF00L), 0x00FFFF00FF00FF00L);
            assertEquals(LongVector.broadcast(lspec, 0x00FFFF00FF00FFFFL), 0x00FFFF00FF00FFFFL);
            assertEquals(LongVector.broadcast(lspec, 0x00FFFF00FFFF0000L), 0x00FFFF00FFFF0000L);
            assertEquals(LongVector.broadcast(lspec, 0x00FFFF00FFFF00FFL), 0x00FFFF00FFFF00FFL);
            assertEquals(LongVector.broadcast(lspec, 0x00FFFF00FFFFFF00L), 0x00FFFF00FFFFFF00L);
            assertEquals(LongVector.broadcast(lspec, 0x00FFFF00FFFFFFFFL), 0x00FFFF00FFFFFFFFL);
            assertEquals(LongVector.broadcast(lspec, 0x00FFFFFF00000000L), 0x00FFFFFF00000000L);
            assertEquals(LongVector.broadcast(lspec, 0x00FFFFFF000000FFL), 0x00FFFFFF000000FFL);
            assertEquals(LongVector.broadcast(lspec, 0x00FFFFFF0000FF00L), 0x00FFFFFF0000FF00L);
            assertEquals(LongVector.broadcast(lspec, 0x00FFFFFF0000FFFFL), 0x00FFFFFF0000FFFFL);
            assertEquals(LongVector.broadcast(lspec, 0x00FFFFFF00FF0000L), 0x00FFFFFF00FF0000L);
            assertEquals(LongVector.broadcast(lspec, 0x00FFFFFF00FF00FFL), 0x00FFFFFF00FF00FFL);
            assertEquals(LongVector.broadcast(lspec, 0x00FFFFFF00FFFF00L), 0x00FFFFFF00FFFF00L);
            assertEquals(LongVector.broadcast(lspec, 0x00FFFFFF00FFFFFFL), 0x00FFFFFF00FFFFFFL);
            assertEquals(LongVector.broadcast(lspec, 0x00FFFFFFFF000000L), 0x00FFFFFFFF000000L);
            assertEquals(LongVector.broadcast(lspec, 0x00FFFFFFFF0000FFL), 0x00FFFFFFFF0000FFL);
            assertEquals(LongVector.broadcast(lspec, 0x00FFFFFFFF00FF00L), 0x00FFFFFFFF00FF00L);
            assertEquals(LongVector.broadcast(lspec, 0x00FFFFFFFF00FFFFL), 0x00FFFFFFFF00FFFFL);
            assertEquals(LongVector.broadcast(lspec, 0x00FFFFFFFFFF0000L), 0x00FFFFFFFFFF0000L);
            assertEquals(LongVector.broadcast(lspec, 0x00FFFFFFFFFF00FFL), 0x00FFFFFFFFFF00FFL);
            assertEquals(LongVector.broadcast(lspec, 0x00FFFFFFFFFFFF00L), 0x00FFFFFFFFFFFF00L);
            assertEquals(LongVector.broadcast(lspec, 0x00FFFFFFFFFFFFFFL), 0x00FFFFFFFFFFFFFFL);
            assertEquals(LongVector.broadcast(lspec, 0xFF00000000000000L), 0xFF00000000000000L);
            assertEquals(LongVector.broadcast(lspec, 0xFF000000000000FFL), 0xFF000000000000FFL);
            assertEquals(LongVector.broadcast(lspec, 0xFF0000000000FF00L), 0xFF0000000000FF00L);
            assertEquals(LongVector.broadcast(lspec, 0xFF0000000000FFFFL), 0xFF0000000000FFFFL);
            assertEquals(LongVector.broadcast(lspec, 0xFF00000000FF0000L), 0xFF00000000FF0000L);
            assertEquals(LongVector.broadcast(lspec, 0xFF00000000FF00FFL), 0xFF00000000FF00FFL);
            assertEquals(LongVector.broadcast(lspec, 0xFF00000000FFFF00L), 0xFF00000000FFFF00L);
            assertEquals(LongVector.broadcast(lspec, 0xFF00000000FFFFFFL), 0xFF00000000FFFFFFL);
            assertEquals(LongVector.broadcast(lspec, 0xFF000000FF000000L), 0xFF000000FF000000L);
            assertEquals(LongVector.broadcast(lspec, 0xFF000000FF0000FFL), 0xFF000000FF0000FFL);
            assertEquals(LongVector.broadcast(lspec, 0xFF000000FF00FF00L), 0xFF000000FF00FF00L);
            assertEquals(LongVector.broadcast(lspec, 0xFF000000FF00FFFFL), 0xFF000000FF00FFFFL);
            assertEquals(LongVector.broadcast(lspec, 0xFF000000FFFF0000L), 0xFF000000FFFF0000L);
            assertEquals(LongVector.broadcast(lspec, 0xFF000000FFFF00FFL), 0xFF000000FFFF00FFL);
            assertEquals(LongVector.broadcast(lspec, 0xFF000000FFFFFF00L), 0xFF000000FFFFFF00L);
            assertEquals(LongVector.broadcast(lspec, 0xFF000000FFFFFFFFL), 0xFF000000FFFFFFFFL);
            assertEquals(LongVector.broadcast(lspec, 0xFF0000FF00000000L), 0xFF0000FF00000000L);
            assertEquals(LongVector.broadcast(lspec, 0xFF0000FF000000FFL), 0xFF0000FF000000FFL);
            assertEquals(LongVector.broadcast(lspec, 0xFF0000FF0000FF00L), 0xFF0000FF0000FF00L);
            assertEquals(LongVector.broadcast(lspec, 0xFF0000FF0000FFFFL), 0xFF0000FF0000FFFFL);
            assertEquals(LongVector.broadcast(lspec, 0xFF0000FF00FF0000L), 0xFF0000FF00FF0000L);
            assertEquals(LongVector.broadcast(lspec, 0xFF0000FF00FF00FFL), 0xFF0000FF00FF00FFL);
            assertEquals(LongVector.broadcast(lspec, 0xFF0000FF00FFFF00L), 0xFF0000FF00FFFF00L);
            assertEquals(LongVector.broadcast(lspec, 0xFF0000FF00FFFFFFL), 0xFF0000FF00FFFFFFL);
            assertEquals(LongVector.broadcast(lspec, 0xFF0000FFFF000000L), 0xFF0000FFFF000000L);
            assertEquals(LongVector.broadcast(lspec, 0xFF0000FFFF0000FFL), 0xFF0000FFFF0000FFL);
            assertEquals(LongVector.broadcast(lspec, 0xFF0000FFFF00FF00L), 0xFF0000FFFF00FF00L);
            assertEquals(LongVector.broadcast(lspec, 0xFF0000FFFF00FFFFL), 0xFF0000FFFF00FFFFL);
            assertEquals(LongVector.broadcast(lspec, 0xFF0000FFFFFF0000L), 0xFF0000FFFFFF0000L);
            assertEquals(LongVector.broadcast(lspec, 0xFF0000FFFFFF00FFL), 0xFF0000FFFFFF00FFL);
            assertEquals(LongVector.broadcast(lspec, 0xFF0000FFFFFFFF00L), 0xFF0000FFFFFFFF00L);
            assertEquals(LongVector.broadcast(lspec, 0xFF0000FFFFFFFFFFL), 0xFF0000FFFFFFFFFFL);
            assertEquals(LongVector.broadcast(lspec, 0xFF00FF0000000000L), 0xFF00FF0000000000L);
            assertEquals(LongVector.broadcast(lspec, 0xFF00FF00000000FFL), 0xFF00FF00000000FFL);
            assertEquals(LongVector.broadcast(lspec, 0xFF00FF000000FF00L), 0xFF00FF000000FF00L);
            assertEquals(LongVector.broadcast(lspec, 0xFF00FF000000FFFFL), 0xFF00FF000000FFFFL);
            assertEquals(LongVector.broadcast(lspec, 0xFF00FF0000FF0000L), 0xFF00FF0000FF0000L);
            assertEquals(LongVector.broadcast(lspec, 0xFF00FF0000FF00FFL), 0xFF00FF0000FF00FFL);
            assertEquals(LongVector.broadcast(lspec, 0xFF00FF0000FFFF00L), 0xFF00FF0000FFFF00L);
            assertEquals(LongVector.broadcast(lspec, 0xFF00FF0000FFFFFFL), 0xFF00FF0000FFFFFFL);
            assertEquals(LongVector.broadcast(lspec, 0xFF00FF00FF000000L), 0xFF00FF00FF000000L);
            assertEquals(LongVector.broadcast(lspec, 0xFF00FF00FF0000FFL), 0xFF00FF00FF0000FFL);
            assertEquals(LongVector.broadcast(lspec, 0xFF00FF00FF00FF00L), 0xFF00FF00FF00FF00L);
            assertEquals(LongVector.broadcast(lspec, 0xFF00FF00FF00FFFFL), 0xFF00FF00FF00FFFFL);
            assertEquals(LongVector.broadcast(lspec, 0xFF00FF00FFFF0000L), 0xFF00FF00FFFF0000L);
            assertEquals(LongVector.broadcast(lspec, 0xFF00FF00FFFF00FFL), 0xFF00FF00FFFF00FFL);
            assertEquals(LongVector.broadcast(lspec, 0xFF00FF00FFFFFF00L), 0xFF00FF00FFFFFF00L);
            assertEquals(LongVector.broadcast(lspec, 0xFF00FF00FFFFFFFFL), 0xFF00FF00FFFFFFFFL);
            assertEquals(LongVector.broadcast(lspec, 0xFF00FFFF00000000L), 0xFF00FFFF00000000L);
            assertEquals(LongVector.broadcast(lspec, 0xFF00FFFF000000FFL), 0xFF00FFFF000000FFL);
            assertEquals(LongVector.broadcast(lspec, 0xFF00FFFF0000FF00L), 0xFF00FFFF0000FF00L);
            assertEquals(LongVector.broadcast(lspec, 0xFF00FFFF0000FFFFL), 0xFF00FFFF0000FFFFL);
            assertEquals(LongVector.broadcast(lspec, 0xFF00FFFF00FF0000L), 0xFF00FFFF00FF0000L);
            assertEquals(LongVector.broadcast(lspec, 0xFF00FFFF00FF00FFL), 0xFF00FFFF00FF00FFL);
            assertEquals(LongVector.broadcast(lspec, 0xFF00FFFF00FFFF00L), 0xFF00FFFF00FFFF00L);
            assertEquals(LongVector.broadcast(lspec, 0xFF00FFFF00FFFFFFL), 0xFF00FFFF00FFFFFFL);
            assertEquals(LongVector.broadcast(lspec, 0xFF00FFFFFF000000L), 0xFF00FFFFFF000000L);
            assertEquals(LongVector.broadcast(lspec, 0xFF00FFFFFF0000FFL), 0xFF00FFFFFF0000FFL);
            assertEquals(LongVector.broadcast(lspec, 0xFF00FFFFFF00FF00L), 0xFF00FFFFFF00FF00L);
            assertEquals(LongVector.broadcast(lspec, 0xFF00FFFFFF00FFFFL), 0xFF00FFFFFF00FFFFL);
            assertEquals(LongVector.broadcast(lspec, 0xFF00FFFFFFFF0000L), 0xFF00FFFFFFFF0000L);
            assertEquals(LongVector.broadcast(lspec, 0xFF00FFFFFFFF00FFL), 0xFF00FFFFFFFF00FFL);
            assertEquals(LongVector.broadcast(lspec, 0xFF00FFFFFFFFFF00L), 0xFF00FFFFFFFFFF00L);
            assertEquals(LongVector.broadcast(lspec, 0xFF00FFFFFFFFFFFFL), 0xFF00FFFFFFFFFFFFL);
            assertEquals(LongVector.broadcast(lspec, 0xFFFF000000000000L), 0xFFFF000000000000L);
            assertEquals(LongVector.broadcast(lspec, 0xFFFF0000000000FFL), 0xFFFF0000000000FFL);
            assertEquals(LongVector.broadcast(lspec, 0xFFFF00000000FF00L), 0xFFFF00000000FF00L);
            assertEquals(LongVector.broadcast(lspec, 0xFFFF00000000FFFFL), 0xFFFF00000000FFFFL);
            assertEquals(LongVector.broadcast(lspec, 0xFFFF000000FF0000L), 0xFFFF000000FF0000L);
            assertEquals(LongVector.broadcast(lspec, 0xFFFF000000FF00FFL), 0xFFFF000000FF00FFL);
            assertEquals(LongVector.broadcast(lspec, 0xFFFF000000FFFF00L), 0xFFFF000000FFFF00L);
            assertEquals(LongVector.broadcast(lspec, 0xFFFF000000FFFFFFL), 0xFFFF000000FFFFFFL);
            assertEquals(LongVector.broadcast(lspec, 0xFFFF0000FF000000L), 0xFFFF0000FF000000L);
            assertEquals(LongVector.broadcast(lspec, 0xFFFF0000FF0000FFL), 0xFFFF0000FF0000FFL);
            assertEquals(LongVector.broadcast(lspec, 0xFFFF0000FF00FF00L), 0xFFFF0000FF00FF00L);
            assertEquals(LongVector.broadcast(lspec, 0xFFFF0000FF00FFFFL), 0xFFFF0000FF00FFFFL);
            assertEquals(LongVector.broadcast(lspec, 0xFFFF0000FFFF0000L), 0xFFFF0000FFFF0000L);
            assertEquals(LongVector.broadcast(lspec, 0xFFFF0000FFFF00FFL), 0xFFFF0000FFFF00FFL);
            assertEquals(LongVector.broadcast(lspec, 0xFFFF0000FFFFFF00L), 0xFFFF0000FFFFFF00L);
            assertEquals(LongVector.broadcast(lspec, 0xFFFF0000FFFFFFFFL), 0xFFFF0000FFFFFFFFL);
            assertEquals(LongVector.broadcast(lspec, 0xFFFF00FF00000000L), 0xFFFF00FF00000000L);
            assertEquals(LongVector.broadcast(lspec, 0xFFFF00FF000000FFL), 0xFFFF00FF000000FFL);
            assertEquals(LongVector.broadcast(lspec, 0xFFFF00FF0000FF00L), 0xFFFF00FF0000FF00L);
            assertEquals(LongVector.broadcast(lspec, 0xFFFF00FF0000FFFFL), 0xFFFF00FF0000FFFFL);
            assertEquals(LongVector.broadcast(lspec, 0xFFFF00FF00FF0000L), 0xFFFF00FF00FF0000L);
            assertEquals(LongVector.broadcast(lspec, 0xFFFF00FF00FF00FFL), 0xFFFF00FF00FF00FFL);
            assertEquals(LongVector.broadcast(lspec, 0xFFFF00FF00FFFF00L), 0xFFFF00FF00FFFF00L);
            assertEquals(LongVector.broadcast(lspec, 0xFFFF00FF00FFFFFFL), 0xFFFF00FF00FFFFFFL);
            assertEquals(LongVector.broadcast(lspec, 0xFFFF00FFFF000000L), 0xFFFF00FFFF000000L);
            assertEquals(LongVector.broadcast(lspec, 0xFFFF00FFFF0000FFL), 0xFFFF00FFFF0000FFL);
            assertEquals(LongVector.broadcast(lspec, 0xFFFF00FFFF00FF00L), 0xFFFF00FFFF00FF00L);
            assertEquals(LongVector.broadcast(lspec, 0xFFFF00FFFF00FFFFL), 0xFFFF00FFFF00FFFFL);
            assertEquals(LongVector.broadcast(lspec, 0xFFFF00FFFFFF0000L), 0xFFFF00FFFFFF0000L);
            assertEquals(LongVector.broadcast(lspec, 0xFFFF00FFFFFF00FFL), 0xFFFF00FFFFFF00FFL);
            assertEquals(LongVector.broadcast(lspec, 0xFFFF00FFFFFFFF00L), 0xFFFF00FFFFFFFF00L);
            assertEquals(LongVector.broadcast(lspec, 0xFFFF00FFFFFFFFFFL), 0xFFFF00FFFFFFFFFFL);
            assertEquals(LongVector.broadcast(lspec, 0xFFFFFF0000000000L), 0xFFFFFF0000000000L);
            assertEquals(LongVector.broadcast(lspec, 0xFFFFFF00000000FFL), 0xFFFFFF00000000FFL);
            assertEquals(LongVector.broadcast(lspec, 0xFFFFFF000000FF00L), 0xFFFFFF000000FF00L);
            assertEquals(LongVector.broadcast(lspec, 0xFFFFFF000000FFFFL), 0xFFFFFF000000FFFFL);
            assertEquals(LongVector.broadcast(lspec, 0xFFFFFF0000FF0000L), 0xFFFFFF0000FF0000L);
            assertEquals(LongVector.broadcast(lspec, 0xFFFFFF0000FF00FFL), 0xFFFFFF0000FF00FFL);
            assertEquals(LongVector.broadcast(lspec, 0xFFFFFF0000FFFF00L), 0xFFFFFF0000FFFF00L);
            assertEquals(LongVector.broadcast(lspec, 0xFFFFFF0000FFFFFFL), 0xFFFFFF0000FFFFFFL);
            assertEquals(LongVector.broadcast(lspec, 0xFFFFFF00FF000000L), 0xFFFFFF00FF000000L);
            assertEquals(LongVector.broadcast(lspec, 0xFFFFFF00FF0000FFL), 0xFFFFFF00FF0000FFL);
            assertEquals(LongVector.broadcast(lspec, 0xFFFFFF00FF00FF00L), 0xFFFFFF00FF00FF00L);
            assertEquals(LongVector.broadcast(lspec, 0xFFFFFF00FF00FFFFL), 0xFFFFFF00FF00FFFFL);
            assertEquals(LongVector.broadcast(lspec, 0xFFFFFF00FFFF0000L), 0xFFFFFF00FFFF0000L);
            assertEquals(LongVector.broadcast(lspec, 0xFFFFFF00FFFF00FFL), 0xFFFFFF00FFFF00FFL);
            assertEquals(LongVector.broadcast(lspec, 0xFFFFFF00FFFFFF00L), 0xFFFFFF00FFFFFF00L);
            assertEquals(LongVector.broadcast(lspec, 0xFFFFFF00FFFFFFFFL), 0xFFFFFF00FFFFFFFFL);
            assertEquals(LongVector.broadcast(lspec, 0xFFFFFFFF00000000L), 0xFFFFFFFF00000000L);
            assertEquals(LongVector.broadcast(lspec, 0xFFFFFFFF000000FFL), 0xFFFFFFFF000000FFL);
            assertEquals(LongVector.broadcast(lspec, 0xFFFFFFFF0000FF00L), 0xFFFFFFFF0000FF00L);
            assertEquals(LongVector.broadcast(lspec, 0xFFFFFFFF0000FFFFL), 0xFFFFFFFF0000FFFFL);
            assertEquals(LongVector.broadcast(lspec, 0xFFFFFFFF00FF0000L), 0xFFFFFFFF00FF0000L);
            assertEquals(LongVector.broadcast(lspec, 0xFFFFFFFF00FF00FFL), 0xFFFFFFFF00FF00FFL);
            assertEquals(LongVector.broadcast(lspec, 0xFFFFFFFF00FFFF00L), 0xFFFFFFFF00FFFF00L);
            assertEquals(LongVector.broadcast(lspec, 0xFFFFFFFF00FFFFFFL), 0xFFFFFFFF00FFFFFFL);
            assertEquals(LongVector.broadcast(lspec, 0xFFFFFFFFFF000000L), 0xFFFFFFFFFF000000L);
            assertEquals(LongVector.broadcast(lspec, 0xFFFFFFFFFF0000FFL), 0xFFFFFFFFFF0000FFL);
            assertEquals(LongVector.broadcast(lspec, 0xFFFFFFFFFF00FF00L), 0xFFFFFFFFFF00FF00L);
            assertEquals(LongVector.broadcast(lspec, 0xFFFFFFFFFF00FFFFL), 0xFFFFFFFFFF00FFFFL);
            assertEquals(LongVector.broadcast(lspec, 0xFFFFFFFFFFFF0000L), 0xFFFFFFFFFFFF0000L);
            assertEquals(LongVector.broadcast(lspec, 0xFFFFFFFFFFFF00FFL), 0xFFFFFFFFFFFF00FFL);
            assertEquals(LongVector.broadcast(lspec, 0xFFFFFFFFFFFFFF00L), 0xFFFFFFFFFFFFFF00L);
            assertEquals(LongVector.broadcast(lspec, 0xFFFFFFFFFFFFFFFFL), 0xFFFFFFFFFFFFFFFFL);
        }
    }
}
