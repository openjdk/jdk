/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

package compiler.vectorapi.reshape.utils;

import java.util.List;
import java.util.stream.Stream;

import static compiler.vectorapi.reshape.utils.VectorReshapeHelper.*;
import static compiler.vectorapi.reshape.utils.VectorSpeciesPair.makePair;

/**
 * The cast intrinsics implemented on each platform, commented out tests are the ones that are
 * supposed to work but currently don't.
 */
public class TestCastMethods {
    public static final List<VectorSpeciesPair> AVX1_CAST_TESTS = List.of(
            makePair(BSPEC64, SSPEC64),
            makePair(BSPEC64, SSPEC128),
            makePair(BSPEC64, ISPEC128),
            makePair(BSPEC64, FSPEC128),
//            makePair(BSPEC64, DSPEC256),
            makePair(SSPEC64, BSPEC64),
            makePair(SSPEC128, BSPEC64),
            makePair(SSPEC64, ISPEC64),
            makePair(SSPEC64, ISPEC128),
            makePair(SSPEC64, LSPEC128),
            makePair(SSPEC64, FSPEC64),
            makePair(SSPEC64, FSPEC128),
            makePair(SSPEC64, DSPEC128),
//            makePair(SSPEC64, DSPEC256),
            makePair(ISPEC128, BSPEC64),
            makePair(ISPEC64, SSPEC64),
            makePair(ISPEC128, SSPEC64),
            makePair(ISPEC64, LSPEC128),
            makePair(ISPEC64, FSPEC64),
            makePair(ISPEC128, FSPEC128),
            makePair(ISPEC64, DSPEC128),
            makePair(ISPEC128, DSPEC256),
            makePair(LSPEC128, SSPEC64),
            makePair(LSPEC128, ISPEC64),
            makePair(FSPEC64, ISPEC64),
            makePair(FSPEC128, ISPEC128),
            makePair(FSPEC64, DSPEC128),
            makePair(FSPEC128, DSPEC256),
            makePair(DSPEC128, FSPEC64),
            makePair(DSPEC256, FSPEC128)
    );

    public static final List<VectorSpeciesPair> AVX2_CAST_TESTS = Stream.concat(AVX1_CAST_TESTS.stream(), Stream.of(
            makePair(BSPEC128, SSPEC256),
            makePair(BSPEC64, ISPEC256),
            makePair(BSPEC64, LSPEC256),
            makePair(BSPEC64, FSPEC256),
            makePair(SSPEC256, BSPEC128),
            makePair(SSPEC128, ISPEC256),
            makePair(SSPEC64, LSPEC256),
            makePair(SSPEC128, FSPEC256),
            makePair(ISPEC256, BSPEC64),
            makePair(ISPEC256, SSPEC128),
            makePair(ISPEC128, LSPEC256),
            makePair(ISPEC256, FSPEC256),
            makePair(LSPEC256, BSPEC64),
            makePair(LSPEC256, SSPEC64),
            makePair(LSPEC256, ISPEC128),
            makePair(FSPEC256, ISPEC256)
    )).toList();

    public static final List<VectorSpeciesPair> AVX512_CAST_TESTS = Stream.concat(AVX2_CAST_TESTS.stream(), Stream.of(
            makePair(BSPEC128, ISPEC512),
            makePair(BSPEC64, LSPEC512),
            makePair(BSPEC128, FSPEC512),
            makePair(BSPEC64, DSPEC512),
            makePair(SSPEC256, ISPEC512),
            makePair(SSPEC128, LSPEC512),
            makePair(SSPEC256, FSPEC512),
            makePair(SSPEC128, DSPEC512),
            makePair(ISPEC512, BSPEC128),
            makePair(ISPEC512, SSPEC256),
            makePair(ISPEC256, LSPEC512),
            makePair(ISPEC512, FSPEC512),
            makePair(ISPEC256, DSPEC512),
            makePair(LSPEC512, BSPEC64),
            makePair(LSPEC512, SSPEC128),
            makePair(LSPEC512, ISPEC256),
            makePair(FSPEC512, ISPEC512),
            makePair(FSPEC256, DSPEC512),
            makePair(DSPEC512, FSPEC256)
    )).toList();

    public static final List<VectorSpeciesPair> AVX512BW_CAST_TESTS = Stream.concat(AVX512_CAST_TESTS.stream(), Stream.of(
            makePair(BSPEC256, SSPEC512),
            makePair(SSPEC512, BSPEC256)
    )).toList();

    public static final List<VectorSpeciesPair> AVX512DQ_CAST_TESTS = Stream.concat(AVX512_CAST_TESTS.stream(), Stream.of(
            makePair(LSPEC128, DSPEC128),
            makePair(LSPEC256, DSPEC256),
            makePair(LSPEC512, DSPEC512),
            makePair(DSPEC128, LSPEC128),
            makePair(DSPEC256, LSPEC256),
            makePair(DSPEC512, LSPEC512)
    )).toList();

    public static final List<VectorSpeciesPair> SVE_CAST_TESTS = List.of(
            makePair(BSPEC64, SSPEC128),
            makePair(BSPEC128, SSPEC256),
            makePair(BSPEC256, SSPEC512),
            makePair(BSPEC64, ISPEC256),
            makePair(BSPEC128, ISPEC512),
            makePair(BSPEC64, LSPEC512),
            makePair(BSPEC64, FSPEC256),
            makePair(BSPEC128, FSPEC512),
            makePair(BSPEC64, DSPEC512),
            makePair(SSPEC128, BSPEC64),
            makePair(SSPEC256, BSPEC128),
            makePair(SSPEC512, BSPEC256),
            makePair(SSPEC64, ISPEC128),
            makePair(SSPEC128, ISPEC256),
            makePair(SSPEC256, ISPEC512),
            makePair(SSPEC64, LSPEC256),
            makePair(SSPEC128, LSPEC512),
            makePair(SSPEC64, FSPEC128),
            makePair(SSPEC128, FSPEC256),
            makePair(SSPEC256, FSPEC512),
            makePair(SSPEC64, DSPEC256),
            makePair(SSPEC128, DSPEC512),
            makePair(ISPEC256, BSPEC64),
            makePair(ISPEC512, BSPEC128),
            makePair(ISPEC128, SSPEC64),
            makePair(ISPEC256, SSPEC128),
            makePair(ISPEC512, SSPEC256),
            makePair(ISPEC64, LSPEC128),
            makePair(ISPEC128, LSPEC256),
            makePair(ISPEC256, LSPEC512),
            makePair(ISPEC64, FSPEC64),
            makePair(ISPEC128, FSPEC128),
            makePair(ISPEC256, FSPEC256),
            makePair(ISPEC512, FSPEC512),
            makePair(ISPEC64, DSPEC128),
            makePair(ISPEC128, DSPEC256),
            makePair(ISPEC256, DSPEC512),
            makePair(LSPEC512, BSPEC64),
            makePair(LSPEC256, SSPEC64),
            makePair(LSPEC512, SSPEC128),
            makePair(LSPEC128, ISPEC64),
            makePair(LSPEC256, ISPEC128),
            makePair(LSPEC512, ISPEC256),
            makePair(LSPEC128, FSPEC64),
            makePair(LSPEC256, FSPEC128),
            makePair(LSPEC512, FSPEC256),
            makePair(LSPEC128, DSPEC128),
            makePair(LSPEC256, DSPEC256),
            makePair(LSPEC512, DSPEC512),
            makePair(FSPEC256, BSPEC64),
            makePair(FSPEC512, BSPEC128),
            makePair(FSPEC128, SSPEC64),
            makePair(FSPEC256, SSPEC128),
            makePair(FSPEC512, SSPEC256),
            makePair(FSPEC64, ISPEC64),
            makePair(FSPEC128, ISPEC128),
            makePair(FSPEC256, ISPEC256),
            makePair(FSPEC512, ISPEC512),
            makePair(FSPEC64, LSPEC128),
            makePair(FSPEC128, LSPEC256),
            makePair(FSPEC256, LSPEC512),
            makePair(FSPEC64, DSPEC128),
            makePair(FSPEC128, DSPEC256),
            makePair(FSPEC256, DSPEC512),
            makePair(DSPEC512, BSPEC64),
            makePair(DSPEC256, SSPEC64),
            makePair(DSPEC512, SSPEC128),
            makePair(DSPEC128, ISPEC64),
            makePair(DSPEC256, ISPEC128),
            makePair(DSPEC512, ISPEC256),
            makePair(DSPEC128, LSPEC128),
            makePair(DSPEC256, LSPEC256),
            makePair(DSPEC512, LSPEC512),
            makePair(DSPEC128, FSPEC64),
            makePair(DSPEC256, FSPEC128),
            makePair(DSPEC512, FSPEC256)
    );

    public static final List<VectorSpeciesPair> NEON_CAST_TESTS = List.of(
            makePair(BSPEC64, SSPEC64),
            makePair(BSPEC64, SSPEC128),
            makePair(BSPEC64, ISPEC128),
            makePair(BSPEC64, FSPEC128),
            makePair(SSPEC64, BSPEC64),
            makePair(SSPEC128, BSPEC64),
            makePair(SSPEC64, ISPEC128),
            makePair(SSPEC64, FSPEC128),
            makePair(ISPEC128, BSPEC64),
            makePair(ISPEC128, SSPEC64),
            makePair(ISPEC64, LSPEC128),
            makePair(ISPEC64, FSPEC64),
            makePair(ISPEC128, FSPEC128),
            makePair(ISPEC64, DSPEC128),
            makePair(LSPEC128, ISPEC64),
            makePair(LSPEC128, FSPEC64),
            makePair(LSPEC128, DSPEC128),
            makePair(FSPEC128, BSPEC64),
            makePair(FSPEC128, SSPEC64),
            makePair(FSPEC64, ISPEC64),
            makePair(FSPEC128, ISPEC128),
            makePair(FSPEC64, LSPEC128),
            makePair(FSPEC64, DSPEC128),
            makePair(DSPEC128, ISPEC64),
            makePair(DSPEC128, LSPEC128),
            makePair(DSPEC128, FSPEC64)
    );
}
