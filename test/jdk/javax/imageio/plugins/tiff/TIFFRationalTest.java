/*
 * Copyright (c) 2016, 2025, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug     8152293
 * @summary Verify bounds check and no terms reduction for TIFF_[S]RATIONAL
 * @run     junit TIFFRationalTest
 */

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import javax.imageio.plugins.tiff.TIFFField;
import javax.imageio.plugins.tiff.TIFFTag;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.*;

public class TIFFRationalTest {
    // largest unsigned 32-bit integer
    private static final long MAX_UINT32 = 0xffffffffL;

    // bogus TIFF tag number
    private static final int TAG_NUMBER = 12345;

    private static Stream<Arguments> paramsSignedRational() {

        int signedRationals[][] = {
            { -1,   0},
            { 11,  22},
            { 22,  11},
            {-11,  22},
            {-22,  11},
            { 11, -22},
            { 22, -11},
            {-11, -22},
            {-22, -11},
            {  0,  11},
            {  0, -11},
            { 11,  13},
            {-11,  13},
            { 11, -13},
            {105,  30},
            { 30, 105}
        };

        final int n = signedRationals.length;

        int type = TIFFTag.TIFF_SRATIONAL;
        TIFFTag tag = new TIFFTag("tag", TAG_NUMBER, 1 << type);
        TIFFField f = new TIFFField(tag, type, n, signedRationals);

        List<Arguments> list = new ArrayList<Arguments>();
        for (int i = 0; i < n; i++) {
            list.add(Arguments.of(f, i, signedRationals[i]));
        }

        return list.stream();
    }

    private static Stream<Arguments> paramsUnsignedRational() {

        long unsignedRationals[][] = {
            {  1,  0},
            { 11, 22},
            { 22, 11},
            {  0, 11},
            { 11, 13},
            {105, 30},
            { 30, 105}
        };

        final int n = unsignedRationals.length;

        int type = TIFFTag.TIFF_RATIONAL;
        TIFFTag tag = new TIFFTag("tag", TAG_NUMBER, 1 << type);
        TIFFField f = new TIFFField(tag, type, n, unsignedRationals);

        List<Arguments> list = new ArrayList<Arguments>();
        for (int i = 0; i < n; i++) {
            list.add(Arguments.of(f, i, unsignedRationals[i]));
        }

        return list.stream();
    }

    @ParameterizedTest
    @MethodSource("paramsSignedRational")
    void signedRational(TIFFField f, int i, int[] expected) {
        int fraction[] = f.getAsSRational(i);
        assertEquals(expected[0], fraction[0],
                     "numerator reduction failure: " +
                     fraction[0] + " != " + expected[0]);
        assertEquals(expected[1], fraction[1],
                     "denominator reduction failure: " +
                     fraction[1] + " != " + expected[1]);

        String s =
            Integer.toString(expected[0]) + "/" +
            Integer.toString(expected[1]);
        assertEquals(s, f.getValueAsString(i),
                     "invalid string representation");
    }

    @ParameterizedTest
    @MethodSource("paramsUnsignedRational")
    void unsignedRational(TIFFField f, int i, long[] expected) {
        long[] fraction = f.getAsRational(i);
        assertEquals(expected[0], fraction[0],
                     "numerator reduction failure: " +
                     fraction[0] + " != " + expected[0]);
        assertEquals(expected[1], fraction[1],
                     "denominator reduction failure: " +
                     fraction[1] + " != " + expected[1]);

        String s =
            Long.toString(expected[0]) + "/" +
            Long.toString(expected[1]);
        assertEquals(s, f.getValueAsString(i),
                     "invalid string representation");
    }

    private static Stream<Arguments> aberrantRationals() {
        List<Arguments> list = new ArrayList<Arguments>();

        list.add(Arguments.of(new long[] {-1,  1}));
        list.add(Arguments.of(new long[] { 1, -1}));
        list.add(Arguments.of(new long[] {-1, -1}));
        list.add(Arguments.of(new long[] {MAX_UINT32 + 1, 1}));
        list.add(Arguments.of(new long[] {1, MAX_UINT32 + 1}));
        list.add(Arguments.of(new long[] {MAX_UINT32 + 1, MAX_UINT32 + 1}));

        return list.stream();
    }

    @ParameterizedTest
    @MethodSource("aberrantRationals")
    void rationalOverflow(long[] frac) {
        int type = TIFFTag.TIFF_RATIONAL;
        TIFFTag tag = new TIFFTag("tag", TAG_NUMBER, 1 << type);
        assertThrows(IllegalArgumentException.class,
                     () -> new TIFFField(tag, type, 1, new long[][]{frac}));
    }
}
