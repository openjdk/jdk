/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * The cast intrinsics implemented on each platform.
 */
public class TestCastMethods {
    public static final List<VectorSpeciesPair> RVV_CAST_TESTS = List.of(
        // ====== from B ======
        // from B 64
            // to X 64
            makePair(BSPEC64, SSPEC64),
            makePair(BSPEC64, SSPEC64, true),
            // to X 128
            makePair(BSPEC64, SSPEC128),
            makePair(BSPEC64, SSPEC128, true),
            makePair(BSPEC64, ISPEC128),
            makePair(BSPEC64, ISPEC128, true),
            makePair(BSPEC64, FSPEC128),
            // to X 256
            makePair(BSPEC64, SSPEC256),
            makePair(BSPEC64, SSPEC256, true),
            makePair(BSPEC64, ISPEC256),
            makePair(BSPEC64, ISPEC256, true),
            makePair(BSPEC64, LSPEC256),
            makePair(BSPEC64, LSPEC256, true),
            makePair(BSPEC64, FSPEC256),
            makePair(BSPEC64, DSPEC256),
            // to X 512
            makePair(BSPEC64, SSPEC512),
            makePair(BSPEC64, SSPEC512, true),
            makePair(BSPEC64, ISPEC512),
            makePair(BSPEC64, ISPEC512, true),
            makePair(BSPEC64, LSPEC512),
            makePair(BSPEC64, LSPEC512, true),
            makePair(BSPEC64, FSPEC512),
            makePair(BSPEC64, DSPEC512),

        // from B 128
            // to X 64
            makePair(BSPEC128, SSPEC64),
            makePair(BSPEC128, SSPEC64, true),
            // to X 128
            makePair(BSPEC128, SSPEC128),
            makePair(BSPEC128, SSPEC128, true),
            makePair(BSPEC128, ISPEC128),
            makePair(BSPEC128, ISPEC128, true),
            makePair(BSPEC128, FSPEC128),
            // to X 256
            makePair(BSPEC128, SSPEC256),
            makePair(BSPEC128, SSPEC256, true),
            makePair(BSPEC128, ISPEC256),
            makePair(BSPEC128, ISPEC256, true),
            makePair(BSPEC128, LSPEC256),
            makePair(BSPEC128, LSPEC256, true),
            makePair(BSPEC128, FSPEC256),
            makePair(BSPEC128, DSPEC256),
            // to X 512
            makePair(BSPEC128, SSPEC512),
            makePair(BSPEC128, SSPEC512, true),
            makePair(BSPEC128, ISPEC512),
            makePair(BSPEC128, ISPEC512, true),
            makePair(BSPEC128, LSPEC512),
            makePair(BSPEC128, LSPEC512, true),
            makePair(BSPEC128, FSPEC512),
            makePair(BSPEC128, DSPEC512),

        // from B 256
            // to X 64
            makePair(BSPEC256, SSPEC64),
            makePair(BSPEC256, SSPEC64, true),
            // to X 128
            makePair(BSPEC256, SSPEC128),
            makePair(BSPEC256, SSPEC128, true),
            makePair(BSPEC256, ISPEC128),
            makePair(BSPEC256, ISPEC128, true),
            makePair(BSPEC256, FSPEC128),
            // to X 256
            makePair(BSPEC256, SSPEC256),
            makePair(BSPEC256, SSPEC256, true),
            makePair(BSPEC256, ISPEC256),
            makePair(BSPEC256, ISPEC256, true),
            makePair(BSPEC256, LSPEC256),
            makePair(BSPEC256, LSPEC256, true),
            makePair(BSPEC256, FSPEC256),
            makePair(BSPEC256, DSPEC256),
            // to X 512
            makePair(BSPEC256, SSPEC512),
            makePair(BSPEC256, SSPEC512, true),
            makePair(BSPEC256, ISPEC512),
            makePair(BSPEC256, ISPEC512, true),
            makePair(BSPEC256, LSPEC512),
            makePair(BSPEC256, LSPEC512, true),
            makePair(BSPEC256, FSPEC512),
            makePair(BSPEC256, DSPEC512),


        // ====== from S ======
        // from S 64
            // to X 64
            makePair(SSPEC64, BSPEC64),
            makePair(SSPEC64, ISPEC64),
            makePair(SSPEC64, ISPEC64, true),
            makePair(SSPEC64, FSPEC64),
            // to X 128
            makePair(SSPEC64, BSPEC128),
            makePair(SSPEC64, ISPEC128),
            makePair(SSPEC64, ISPEC128, true),
            makePair(SSPEC64, LSPEC128),
            makePair(SSPEC64, LSPEC128, true),
            makePair(SSPEC64, FSPEC128),
            makePair(SSPEC64, DSPEC128),
            // to X 256
            makePair(SSPEC64, BSPEC256),
            makePair(SSPEC64, ISPEC256),
            makePair(SSPEC64, ISPEC256, true),
            makePair(SSPEC64, LSPEC256),
            makePair(SSPEC64, LSPEC256, true),
            makePair(SSPEC64, FSPEC256),
            makePair(SSPEC64, DSPEC256),
            // to X 512
            makePair(SSPEC64, BSPEC512),
            makePair(SSPEC64, ISPEC512),
            makePair(SSPEC64, ISPEC512, true),
            makePair(SSPEC64, LSPEC512),
            makePair(SSPEC64, LSPEC512, true),
            makePair(SSPEC64, FSPEC512),
            makePair(SSPEC64, DSPEC512),

        // from S 128
            // to X 64
            makePair(SSPEC128, BSPEC64),
            // to X 128
            makePair(SSPEC128, BSPEC128),
            makePair(SSPEC128, ISPEC128),
            makePair(SSPEC128, ISPEC128, true),
            makePair(SSPEC128, LSPEC128),
            makePair(SSPEC128, LSPEC128, true),
            makePair(SSPEC128, FSPEC128),
            makePair(SSPEC128, DSPEC128),
            // to X 256
            makePair(SSPEC128, BSPEC256),
            makePair(SSPEC128, ISPEC256),
            makePair(SSPEC128, ISPEC256, true),
            makePair(SSPEC128, LSPEC256),
            makePair(SSPEC128, LSPEC256, true),
            makePair(SSPEC128, FSPEC256),
            makePair(SSPEC128, DSPEC256),
            // to X 512
            makePair(SSPEC128, BSPEC512),
            makePair(SSPEC128, ISPEC512),
            makePair(SSPEC128, ISPEC512, true),
            makePair(SSPEC128, LSPEC512),
            makePair(SSPEC128, LSPEC512, true),
            makePair(SSPEC128, FSPEC512),
            makePair(SSPEC128, DSPEC512),

        // from S 256
            // to X 64
            makePair(SSPEC256, BSPEC64),
            // to X 128
            makePair(SSPEC256, BSPEC128),
            makePair(SSPEC256, ISPEC128),
            makePair(SSPEC256, ISPEC128, true),
            makePair(SSPEC256, FSPEC128),
            // to X 256
            makePair(SSPEC256, BSPEC256),
            makePair(SSPEC256, ISPEC256),
            makePair(SSPEC256, ISPEC256, true),
            makePair(SSPEC256, LSPEC256),
            makePair(SSPEC256, LSPEC256, true),
            makePair(SSPEC256, FSPEC256),
            makePair(SSPEC256, DSPEC256),
            // to X 512
            makePair(SSPEC256, BSPEC512),
            makePair(SSPEC256, ISPEC512),
            makePair(SSPEC256, ISPEC512, true),
            makePair(SSPEC256, LSPEC512),
            makePair(SSPEC256, LSPEC512, true),
            makePair(SSPEC256, FSPEC512),
            makePair(SSPEC256, DSPEC512),

        // from S 512
            // to X 64
            makePair(SSPEC512, BSPEC64),
            // to X 128
            makePair(SSPEC512, BSPEC128),
            makePair(SSPEC512, ISPEC128),
            makePair(SSPEC512, ISPEC128, true),
            makePair(SSPEC512, FSPEC128),
            // to X 256
            makePair(SSPEC512, BSPEC256),
            makePair(SSPEC512, ISPEC256),
            makePair(SSPEC512, ISPEC256, true),
            makePair(SSPEC512, LSPEC256),
            makePair(SSPEC512, LSPEC256, true),
            makePair(SSPEC512, FSPEC256),
            makePair(SSPEC512, DSPEC256),
            // to X 512
            makePair(SSPEC512, BSPEC512),
            makePair(SSPEC512, ISPEC512),
            makePair(SSPEC512, ISPEC512, true),
            makePair(SSPEC512, LSPEC512),
            makePair(SSPEC512, LSPEC512, true),
            makePair(SSPEC512, FSPEC512),
            makePair(SSPEC512, DSPEC512),


        // ====== from I ======
        // from I 64
            // to X 64
            makePair(ISPEC64, SSPEC64),
            makePair(ISPEC64, FSPEC64),
            // to X 128
            makePair(ISPEC64, LSPEC128),
            makePair(ISPEC64, LSPEC128, true),
            makePair(ISPEC64, FSPEC128),
            makePair(ISPEC64, DSPEC128),

        // from I 128
            // to X 64
            makePair(ISPEC128, BSPEC64),
            makePair(ISPEC128, SSPEC64),
            makePair(ISPEC128, FSPEC64),
            // to X 128
            makePair(ISPEC128, BSPEC128),
            makePair(ISPEC128, SSPEC128),
            makePair(ISPEC128, LSPEC128),
            makePair(ISPEC128, LSPEC128, true),
            makePair(ISPEC128, FSPEC128),
            makePair(ISPEC128, DSPEC128),
            // to X 256
            makePair(ISPEC128, BSPEC256),
            makePair(ISPEC128, SSPEC256),
            makePair(ISPEC128, LSPEC256),
            makePair(ISPEC128, LSPEC256, true),
            makePair(ISPEC128, FSPEC256),
            makePair(ISPEC128, DSPEC256),

        // from I 256
            // to X 64
            makePair(ISPEC256, BSPEC64),
            makePair(ISPEC256, SSPEC64),
            makePair(ISPEC256, FSPEC64),
            // to X 128
            makePair(ISPEC256, BSPEC128),
            makePair(ISPEC256, SSPEC128),
            makePair(ISPEC256, LSPEC128),
            makePair(ISPEC256, LSPEC128, true),
            makePair(ISPEC256, FSPEC128),
            makePair(ISPEC256, DSPEC128),
            // to X 256
            makePair(ISPEC256, BSPEC256),
            makePair(ISPEC256, SSPEC256),
            makePair(ISPEC256, LSPEC256),
            makePair(ISPEC256, LSPEC256, true),
            makePair(ISPEC256, FSPEC256),
            makePair(ISPEC256, DSPEC256),

        // from I 512
            // to X 64
            makePair(ISPEC512, BSPEC64),
            makePair(ISPEC512, SSPEC64),
            makePair(ISPEC512, FSPEC64),
            // to X 128
            makePair(ISPEC512, BSPEC128),
            makePair(ISPEC512, SSPEC128),
            makePair(ISPEC512, LSPEC128),
            makePair(ISPEC512, LSPEC128, true),
            makePair(ISPEC512, FSPEC128),
            makePair(ISPEC512, DSPEC128),
            // to X 256
            makePair(ISPEC512, BSPEC256),
            makePair(ISPEC512, SSPEC256),
            makePair(ISPEC512, LSPEC256),
            makePair(ISPEC512, LSPEC256, true),
            makePair(ISPEC512, FSPEC256),
            makePair(ISPEC512, DSPEC256),


        // ====== from L ======
        // from L 128
            // to X 64
            makePair(LSPEC128, SSPEC64),
            makePair(LSPEC128, ISPEC64),
            makePair(LSPEC128, FSPEC64),
            makePair(LSPEC128, DSPEC64),
            // to X 128
            makePair(LSPEC128, SSPEC128),
            makePair(LSPEC128, ISPEC128),
            makePair(LSPEC128, FSPEC128),
            makePair(LSPEC128, DSPEC128),
            // to X 256
            makePair(LSPEC128, ISPEC256),
            makePair(LSPEC128, FSPEC256),
            makePair(LSPEC128, DSPEC256),
            // to X 512
            makePair(LSPEC128, ISPEC512),
            makePair(LSPEC128, FSPEC512),
            makePair(LSPEC128, DSPEC512),

        // from L 256
            // to X 64
            makePair(LSPEC256, BSPEC64),
            makePair(LSPEC256, SSPEC64),
            makePair(LSPEC256, ISPEC64),
            makePair(LSPEC256, FSPEC64),
            // to X 128
            makePair(LSPEC256, BSPEC128),
            makePair(LSPEC256, SSPEC128),
            makePair(LSPEC256, ISPEC128),
            makePair(LSPEC256, FSPEC128),
            makePair(LSPEC256, DSPEC128),
            // to X 256
            makePair(LSPEC256, BSPEC256),
            makePair(LSPEC256, SSPEC256),
            makePair(LSPEC256, ISPEC256),
            makePair(LSPEC256, FSPEC256),
            makePair(LSPEC256, DSPEC256),
            // to X 512
            makePair(LSPEC256, BSPEC512),
            makePair(LSPEC256, SSPEC512),
            makePair(LSPEC256, ISPEC512),
            makePair(LSPEC256, FSPEC512),
            makePair(LSPEC256, DSPEC512),

        // from L 512
            // to X 64
            makePair(LSPEC512, BSPEC64),
            makePair(LSPEC512, SSPEC64),
            makePair(LSPEC512, ISPEC64),
            makePair(LSPEC512, FSPEC64),
            // to X 128
            makePair(LSPEC512, BSPEC128),
            makePair(LSPEC512, SSPEC128),
            makePair(LSPEC512, ISPEC128),
            makePair(LSPEC512, FSPEC128),
            makePair(LSPEC512, DSPEC128),
            // to X 256
            makePair(LSPEC512, BSPEC256),
            makePair(LSPEC512, SSPEC256),
            makePair(LSPEC512, ISPEC256),
            makePair(LSPEC512, FSPEC256),
            makePair(LSPEC512, DSPEC256),
            // to X 512
            makePair(LSPEC512, BSPEC512),
            makePair(LSPEC512, SSPEC512),
            makePair(LSPEC512, ISPEC512),
            makePair(LSPEC512, FSPEC512),
            makePair(LSPEC512, DSPEC512),


        // ====== from F ======
        // from F 64
            // to X 64
            makePair(FSPEC64, SSPEC64),
            makePair(FSPEC64, ISPEC64),
            // to X 128
            makePair(FSPEC64, ISPEC128),
            makePair(FSPEC64, LSPEC128),
            makePair(FSPEC64, DSPEC128),

        // from F 128
            // to X 64
            makePair(FSPEC128, BSPEC64),
            makePair(FSPEC128, SSPEC64),
            makePair(FSPEC128, ISPEC64),
            // to X 128
            makePair(FSPEC128, BSPEC128),
            makePair(FSPEC128, SSPEC128),
            makePair(FSPEC128, ISPEC128),
            makePair(FSPEC128, LSPEC128),
            makePair(FSPEC128, DSPEC128),
            // to X 256
            makePair(FSPEC128, BSPEC256),
            makePair(FSPEC128, SSPEC256),
            makePair(FSPEC128, ISPEC256),
            makePair(FSPEC128, LSPEC256),
            makePair(FSPEC128, DSPEC256),

        // from F 256
            // to X 64
            makePair(FSPEC256, BSPEC64),
            makePair(FSPEC256, SSPEC64),
            makePair(FSPEC256, ISPEC64),
            // to X 128
            makePair(FSPEC256, BSPEC128),
            makePair(FSPEC256, SSPEC128),
            makePair(FSPEC256, ISPEC128),
            makePair(FSPEC256, LSPEC128),
            makePair(FSPEC256, DSPEC128),
            // to X 256
            makePair(FSPEC256, BSPEC256),
            makePair(FSPEC256, SSPEC256),
            makePair(FSPEC256, ISPEC256),
            makePair(FSPEC256, LSPEC256),
            makePair(FSPEC256, DSPEC256),

        // from F 512
            // to X 64
            makePair(FSPEC512, BSPEC64),
            makePair(FSPEC512, SSPEC64),
            makePair(FSPEC512, ISPEC64),
            // to X 128
            makePair(FSPEC512, BSPEC128),
            makePair(FSPEC512, SSPEC128),
            makePair(FSPEC512, ISPEC128),
            makePair(FSPEC512, LSPEC128),
            makePair(FSPEC512, DSPEC128),
            // to X 256
            makePair(FSPEC512, BSPEC256),
            makePair(FSPEC512, SSPEC256),
            makePair(FSPEC512, ISPEC256),
            makePair(FSPEC512, LSPEC256),
            makePair(FSPEC512, DSPEC256),


        // ====== from D ======
        // from D 128
            // to X 64
            makePair(DSPEC128, SSPEC64),
            makePair(DSPEC128, ISPEC64),
            makePair(DSPEC128, LSPEC64),
            makePair(DSPEC128, FSPEC64),
            // to X 128
            makePair(DSPEC128, SSPEC128),
            makePair(DSPEC128, ISPEC128),
            makePair(DSPEC128, LSPEC128),
            makePair(DSPEC128, FSPEC128),
            // to X 256
            makePair(DSPEC128, ISPEC256),
            makePair(DSPEC128, LSPEC256),
            makePair(DSPEC128, FSPEC256),
            // to X 512
            makePair(DSPEC128, ISPEC512),
            makePair(DSPEC128, LSPEC512),
            makePair(DSPEC128, FSPEC512),

        // from D 256
            // to X 64
            makePair(DSPEC256, BSPEC64),
            makePair(DSPEC256, SSPEC64),
            makePair(DSPEC256, ISPEC64),
            makePair(DSPEC256, LSPEC64),
            makePair(DSPEC256, FSPEC64),
            // to X 128
            makePair(DSPEC256, BSPEC128),
            makePair(DSPEC256, SSPEC128),
            makePair(DSPEC256, ISPEC128),
            makePair(DSPEC256, LSPEC128),
            makePair(DSPEC256, FSPEC128),
            // to X 256
            makePair(DSPEC256, BSPEC256),
            makePair(DSPEC256, SSPEC256),
            makePair(DSPEC256, ISPEC256),
            makePair(DSPEC256, LSPEC256),
            makePair(DSPEC256, FSPEC256),
            // to X 512
            makePair(DSPEC256, BSPEC512),
            makePair(DSPEC256, SSPEC512),
            makePair(DSPEC256, ISPEC512),
            makePair(DSPEC256, LSPEC512),
            makePair(DSPEC256, FSPEC512),

        // from D 512
            // to X 64
            makePair(DSPEC512, BSPEC64),
            makePair(DSPEC512, SSPEC64),
            makePair(DSPEC512, ISPEC64),
            makePair(DSPEC512, LSPEC64),
            makePair(DSPEC512, FSPEC64),
            // to X 128
            makePair(DSPEC512, BSPEC128),
            makePair(DSPEC512, SSPEC128),
            makePair(DSPEC512, ISPEC128),
            makePair(DSPEC512, LSPEC128),
            makePair(DSPEC512, FSPEC128),
            // to X 256
            makePair(DSPEC512, BSPEC256),
            makePair(DSPEC512, SSPEC256),
            makePair(DSPEC512, ISPEC256),
            makePair(DSPEC512, LSPEC256),
            makePair(DSPEC512, FSPEC256),
            // to X 512
            makePair(DSPEC512, BSPEC512),
            makePair(DSPEC512, SSPEC512),
            makePair(DSPEC512, ISPEC512),
            makePair(DSPEC512, LSPEC512),
            makePair(DSPEC512, FSPEC512)

    );

    public static final List<VectorSpeciesPair> AVX1_CAST_TESTS = List.of(
            makePair(BSPEC64, SSPEC64),
            makePair(BSPEC64, SSPEC128),
            makePair(BSPEC64, ISPEC128),
            makePair(BSPEC64, FSPEC128),
            makePair(BSPEC64, DSPEC256),
            makePair(SSPEC64, BSPEC64),
            makePair(SSPEC128, BSPEC64),
            makePair(SSPEC64, ISPEC64),
            makePair(SSPEC64, ISPEC128),
            makePair(SSPEC64, LSPEC128),
            makePair(SSPEC64, FSPEC64),
            makePair(SSPEC64, FSPEC128),
            makePair(SSPEC64, DSPEC128),
            makePair(SSPEC64, DSPEC256),
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
            makePair(FSPEC128, SSPEC64),
            makePair(DSPEC128, FSPEC64),
            makePair(DSPEC256, FSPEC128),
            makePair(DSPEC128, ISPEC64),
            makePair(BSPEC64, SSPEC64, true),
            makePair(BSPEC64, SSPEC128, true),
            makePair(BSPEC64, ISPEC128, true),
            makePair(SSPEC64, ISPEC64, true),
            makePair(SSPEC64, ISPEC128, true),
            makePair(SSPEC64, LSPEC128, true),
            makePair(ISPEC64, LSPEC128, true)
    );

    public static final List<VectorSpeciesPair> AVX2_CAST_TESTS = Stream.concat(AVX1_CAST_TESTS.stream(), Stream.of(
            makePair(DSPEC256, ISPEC128),
            makePair(DSPEC256, SSPEC64),
            makePair(FSPEC256, ISPEC256),
            makePair(FSPEC256, SSPEC128),
            makePair(FSPEC256, BSPEC64),
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
            makePair(BSPEC128, SSPEC256, true),
            makePair(BSPEC64, ISPEC256, true),
            makePair(BSPEC64, LSPEC256, true),
            makePair(SSPEC128, ISPEC256, true),
            makePair(SSPEC64, LSPEC256, true),
            makePair(ISPEC128, LSPEC256, true)
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
            makePair(FSPEC256, DSPEC512),
            makePair(DSPEC512, FSPEC256),
            makePair(DSPEC512, ISPEC256),
            makePair(DSPEC512, SSPEC128),
            makePair(DSPEC512, BSPEC64),
            makePair(FSPEC512, ISPEC512),
            makePair(FSPEC512, SSPEC256),
            makePair(FSPEC512, BSPEC128),
            makePair(BSPEC128, ISPEC512, true),
            makePair(BSPEC64, LSPEC512, true),
            makePair(SSPEC256, ISPEC512, true),
            makePair(SSPEC128, LSPEC512, true),
            makePair(ISPEC256, LSPEC512, true)
    )).toList();

    public static final List<VectorSpeciesPair> AVX512BW_CAST_TESTS = Stream.concat(AVX512_CAST_TESTS.stream(), Stream.of(
            makePair(BSPEC256, SSPEC512),
            makePair(SSPEC512, BSPEC256),
            makePair(BSPEC256, SSPEC512, true)
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
            makePair(SSPEC64, ISPEC64),
            makePair(SSPEC64, ISPEC128),
            makePair(SSPEC128, ISPEC256),
            makePair(SSPEC256, ISPEC512),
            makePair(SSPEC64, LSPEC128),
            makePair(SSPEC64, LSPEC256),
            makePair(SSPEC128, LSPEC128),
            makePair(SSPEC128, LSPEC512),
            makePair(SSPEC64, FSPEC64),
            makePair(SSPEC64, FSPEC128),
            makePair(SSPEC128, FSPEC256),
            makePair(SSPEC256, FSPEC512),
            makePair(SSPEC64, DSPEC128),
            makePair(SSPEC64, DSPEC256),
            makePair(SSPEC128, DSPEC128),
            makePair(SSPEC128, DSPEC512),
            makePair(ISPEC256, BSPEC64),
            makePair(ISPEC512, BSPEC128),
            makePair(ISPEC64,  SSPEC64),
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
            makePair(LSPEC128, SSPEC64),
            makePair(LSPEC256, SSPEC64),
            makePair(LSPEC128, SSPEC128),
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
            makePair(FSPEC64,  SSPEC64),
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
            makePair(DSPEC128, SSPEC64),
            makePair(DSPEC256, SSPEC64),
            makePair(DSPEC128, SSPEC128),
            makePair(DSPEC512, SSPEC128),
            makePair(DSPEC128, ISPEC64),
            makePair(DSPEC256, ISPEC128),
            makePair(DSPEC512, ISPEC256),
            makePair(DSPEC128, LSPEC128),
            makePair(DSPEC256, LSPEC256),
            makePair(DSPEC512, LSPEC512),
            makePair(DSPEC128, FSPEC64),
            makePair(DSPEC256, FSPEC128),
            makePair(DSPEC512, FSPEC256),

            makePair(BSPEC64, SSPEC64, true),
            makePair(BSPEC64, SSPEC128, true),
            makePair(BSPEC64, SSPEC256, true),
            makePair(BSPEC64, SSPEC512, true),
            makePair(BSPEC64, ISPEC128, true),
            makePair(BSPEC64, ISPEC256, true),
            makePair(BSPEC64, ISPEC512, true),
            makePair(BSPEC64, LSPEC256, true),
            makePair(BSPEC64, LSPEC512, true),
            makePair(BSPEC128, SSPEC64, true),
            makePair(BSPEC128, SSPEC128, true),
            makePair(BSPEC128, SSPEC256, true),
            makePair(BSPEC128, SSPEC512, true),
            makePair(BSPEC128, ISPEC128, true),
            makePair(BSPEC128, ISPEC256, true),
            makePair(BSPEC128, ISPEC512, true),
            makePair(BSPEC128, LSPEC256, true),
            makePair(BSPEC128, LSPEC512, true),
            makePair(BSPEC256, SSPEC64, true),
            makePair(BSPEC256, SSPEC128, true),
            makePair(BSPEC256, SSPEC256, true),
            makePair(BSPEC256, SSPEC512, true),
            makePair(BSPEC256, ISPEC128, true),
            makePair(BSPEC256, ISPEC256, true),
            makePair(BSPEC256, ISPEC512, true),
            makePair(BSPEC256, LSPEC256, true),
            makePair(BSPEC256, LSPEC512, true),
            makePair(BSPEC512, SSPEC64, true),
            makePair(BSPEC512, SSPEC128, true),
            makePair(BSPEC512, SSPEC256, true),
            makePair(BSPEC512, SSPEC512, true),
            makePair(BSPEC512, ISPEC128, true),
            makePair(BSPEC512, ISPEC256, true),
            makePair(BSPEC512, ISPEC512, true),
            makePair(BSPEC512, LSPEC256, true),
            makePair(BSPEC512, LSPEC512, true),

            makePair(SSPEC64, ISPEC64, true),
            makePair(SSPEC64, ISPEC128, true),
            makePair(SSPEC64, ISPEC256, true),
            makePair(SSPEC64, ISPEC512, true),
            makePair(SSPEC64, LSPEC128, true),
            makePair(SSPEC64, LSPEC256, true),
            makePair(SSPEC64, LSPEC512, true),
            makePair(SSPEC128, ISPEC128, true),
            makePair(SSPEC128, ISPEC256, true),
            makePair(SSPEC128, ISPEC512, true),
            makePair(SSPEC128, LSPEC128, true),
            makePair(SSPEC128, LSPEC256, true),
            makePair(SSPEC128, LSPEC512, true),
            makePair(SSPEC256, ISPEC128, true),
            makePair(SSPEC256, ISPEC256, true),
            makePair(SSPEC256, ISPEC512, true),
            makePair(SSPEC256, LSPEC256, true),
            makePair(SSPEC256, LSPEC512, true),
            makePair(SSPEC512, ISPEC128, true),
            makePair(SSPEC512, ISPEC256, true),
            makePair(SSPEC512, ISPEC512, true),
            makePair(SSPEC512, LSPEC256, true),
            makePair(SSPEC512, LSPEC512, true),

            makePair(ISPEC64, LSPEC128, true),
            makePair(ISPEC64, LSPEC256, true),
            makePair(ISPEC128, LSPEC128, true),
            makePair(ISPEC128, LSPEC256, true),
            makePair(ISPEC256, LSPEC128, true),
            makePair(ISPEC256, LSPEC256, true),
            makePair(ISPEC512, LSPEC128, true),
            makePair(ISPEC512, LSPEC256, true)
    );

    public static final List<VectorSpeciesPair> NEON_CAST_TESTS = List.of(
            makePair(BSPEC64, SSPEC64),
            makePair(BSPEC64, SSPEC128),
            makePair(BSPEC64, ISPEC128),
            makePair(BSPEC64, FSPEC128),
            makePair(SSPEC64, BSPEC64),
            makePair(SSPEC128, BSPEC64),
            makePair(SSPEC64, ISPEC64),
            makePair(SSPEC64, ISPEC128),
            makePair(SSPEC64,  LSPEC128),
            makePair(SSPEC128, LSPEC128),
            makePair(SSPEC64, FSPEC64),
            makePair(SSPEC64, FSPEC128),
            makePair(SSPEC64,  DSPEC128),
            makePair(SSPEC128, DSPEC128),
            makePair(ISPEC128, BSPEC64),
            makePair(ISPEC128, SSPEC64),
            makePair(ISPEC64,  SSPEC64),
            makePair(ISPEC64,  LSPEC128),
            makePair(ISPEC64, FSPEC64),
            makePair(ISPEC128, FSPEC128),
            makePair(ISPEC64, DSPEC128),
            makePair(LSPEC128, SSPEC64),
            makePair(LSPEC128, SSPEC128),
            makePair(LSPEC128, ISPEC64),
            makePair(LSPEC128, FSPEC64),
            makePair(LSPEC128, DSPEC128),
            makePair(FSPEC128, BSPEC64),
            makePair(FSPEC64, SSPEC64),
            makePair(FSPEC128, SSPEC64),
            makePair(FSPEC64, ISPEC64),
            makePair(FSPEC128, ISPEC128),
            makePair(FSPEC64, LSPEC128),
            makePair(FSPEC64, DSPEC128),
            makePair(DSPEC128, SSPEC64),
            makePair(DSPEC128, SSPEC128),
            makePair(DSPEC128, ISPEC64),
            makePair(DSPEC128, LSPEC128),
            makePair(DSPEC128, FSPEC64),

            makePair(BSPEC64, SSPEC64, true),
            makePair(BSPEC64, SSPEC128, true),
            makePair(BSPEC64, ISPEC128, true),
            makePair(BSPEC128, SSPEC64, true),
            makePair(BSPEC128, SSPEC128, true),
            makePair(BSPEC128, ISPEC128, true),
            makePair(SSPEC64, ISPEC64, true),
            makePair(SSPEC64, ISPEC128, true),
            makePair(SSPEC64, LSPEC128, true),
            makePair(SSPEC128, ISPEC128, true),
            makePair(SSPEC128, LSPEC128, true),
            makePair(ISPEC64, LSPEC128, true)
    );
}
