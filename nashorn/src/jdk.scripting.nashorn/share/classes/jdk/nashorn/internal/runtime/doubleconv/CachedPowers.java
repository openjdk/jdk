/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

// This file is available under and governed by the GNU General Public
// License version 2 only, as published by the Free Software Foundation.
// However, the following notice accompanied the original version of this
// file:
//
// Copyright 2010 the V8 project authors. All rights reserved.
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are
// met:
//
//     * Redistributions of source code must retain the above copyright
//       notice, this list of conditions and the following disclaimer.
//     * Redistributions in binary form must reproduce the above
//       copyright notice, this list of conditions and the following
//       disclaimer in the documentation and/or other materials provided
//       with the distribution.
//     * Neither the name of Google Inc. nor the names of its
//       contributors may be used to endorse or promote products derived
//       from this software without specific prior written permission.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
// "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
// LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
// A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
// OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
// SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
// LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
// DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
// THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
// (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
// OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

package jdk.nashorn.internal.runtime.doubleconv;

public class CachedPowers {

    static class CachedPower {
        final private long significand;
        final private int binaryExponent;
        final private int decimalExponent;

        CachedPower(final long significand, final int binaryExponent, final int decimalExponent) {
            this.significand = significand;
            this.binaryExponent = binaryExponent;
            this.decimalExponent = decimalExponent;
        }
    }

    static private final CachedPower[] kCachedPowers = {
            new CachedPower(0xfa8fd5a0081c0288L, -1220, -348),
            new CachedPower(0xbaaee17fa23ebf76L, -1193, -340),
            new CachedPower(0x8b16fb203055ac76L, -1166, -332),
            new CachedPower(0xcf42894a5dce35eaL, -1140, -324),
            new CachedPower(0x9a6bb0aa55653b2dL, -1113, -316),
            new CachedPower(0xe61acf033d1a45dfL, -1087, -308),
            new CachedPower(0xab70fe17c79ac6caL, -1060, -300),
            new CachedPower(0xff77b1fcbebcdc4fL, -1034, -292),
            new CachedPower(0xbe5691ef416bd60cL, -1007, -284),
            new CachedPower(0x8dd01fad907ffc3cL, -980, -276),
            new CachedPower(0xd3515c2831559a83L, -954, -268),
            new CachedPower(0x9d71ac8fada6c9b5L, -927, -260),
            new CachedPower(0xea9c227723ee8bcbL, -901, -252),
            new CachedPower(0xaecc49914078536dL, -874, -244),
            new CachedPower(0x823c12795db6ce57L, -847, -236),
            new CachedPower(0xc21094364dfb5637L, -821, -228),
            new CachedPower(0x9096ea6f3848984fL, -794, -220),
            new CachedPower(0xd77485cb25823ac7L, -768, -212),
            new CachedPower(0xa086cfcd97bf97f4L, -741, -204),
            new CachedPower(0xef340a98172aace5L, -715, -196),
            new CachedPower(0xb23867fb2a35b28eL, -688, -188),
            new CachedPower(0x84c8d4dfd2c63f3bL, -661, -180),
            new CachedPower(0xc5dd44271ad3cdbaL, -635, -172),
            new CachedPower(0x936b9fcebb25c996L, -608, -164),
            new CachedPower(0xdbac6c247d62a584L, -582, -156),
            new CachedPower(0xa3ab66580d5fdaf6L, -555, -148),
            new CachedPower(0xf3e2f893dec3f126L, -529, -140),
            new CachedPower(0xb5b5ada8aaff80b8L, -502, -132),
            new CachedPower(0x87625f056c7c4a8bL, -475, -124),
            new CachedPower(0xc9bcff6034c13053L, -449, -116),
            new CachedPower(0x964e858c91ba2655L, -422, -108),
            new CachedPower(0xdff9772470297ebdL, -396, -100),
            new CachedPower(0xa6dfbd9fb8e5b88fL, -369, -92),
            new CachedPower(0xf8a95fcf88747d94L, -343, -84),
            new CachedPower(0xb94470938fa89bcfL, -316, -76),
            new CachedPower(0x8a08f0f8bf0f156bL, -289, -68),
            new CachedPower(0xcdb02555653131b6L, -263, -60),
            new CachedPower(0x993fe2c6d07b7facL, -236, -52),
            new CachedPower(0xe45c10c42a2b3b06L, -210, -44),
            new CachedPower(0xaa242499697392d3L, -183, -36),
            new CachedPower(0xfd87b5f28300ca0eL, -157, -28),
            new CachedPower(0xbce5086492111aebL, -130, -20),
            new CachedPower(0x8cbccc096f5088ccL, -103, -12),
            new CachedPower(0xd1b71758e219652cL, -77, -4),
            new CachedPower(0x9c40000000000000L, -50, 4),
            new CachedPower(0xe8d4a51000000000L, -24, 12),
            new CachedPower(0xad78ebc5ac620000L, 3, 20),
            new CachedPower(0x813f3978f8940984L, 30, 28),
            new CachedPower(0xc097ce7bc90715b3L, 56, 36),
            new CachedPower(0x8f7e32ce7bea5c70L, 83, 44),
            new CachedPower(0xd5d238a4abe98068L, 109, 52),
            new CachedPower(0x9f4f2726179a2245L, 136, 60),
            new CachedPower(0xed63a231d4c4fb27L, 162, 68),
            new CachedPower(0xb0de65388cc8ada8L, 189, 76),
            new CachedPower(0x83c7088e1aab65dbL, 216, 84),
            new CachedPower(0xc45d1df942711d9aL, 242, 92),
            new CachedPower(0x924d692ca61be758L, 269, 100),
            new CachedPower(0xda01ee641a708deaL, 295, 108),
            new CachedPower(0xa26da3999aef774aL, 322, 116),
            new CachedPower(0xf209787bb47d6b85L, 348, 124),
            new CachedPower(0xb454e4a179dd1877L, 375, 132),
            new CachedPower(0x865b86925b9bc5c2L, 402, 140),
            new CachedPower(0xc83553c5c8965d3dL, 428, 148),
            new CachedPower(0x952ab45cfa97a0b3L, 455, 156),
            new CachedPower(0xde469fbd99a05fe3L, 481, 164),
            new CachedPower(0xa59bc234db398c25L, 508, 172),
            new CachedPower(0xf6c69a72a3989f5cL, 534, 180),
            new CachedPower(0xb7dcbf5354e9beceL, 561, 188),
            new CachedPower(0x88fcf317f22241e2L, 588, 196),
            new CachedPower(0xcc20ce9bd35c78a5L, 614, 204),
            new CachedPower(0x98165af37b2153dfL, 641, 212),
            new CachedPower(0xe2a0b5dc971f303aL, 667, 220),
            new CachedPower(0xa8d9d1535ce3b396L, 694, 228),
            new CachedPower(0xfb9b7cd9a4a7443cL, 720, 236),
            new CachedPower(0xbb764c4ca7a44410L, 747, 244),
            new CachedPower(0x8bab8eefb6409c1aL, 774, 252),
            new CachedPower(0xd01fef10a657842cL, 800, 260),
            new CachedPower(0x9b10a4e5e9913129L, 827, 268),
            new CachedPower(0xe7109bfba19c0c9dL, 853, 276),
            new CachedPower(0xac2820d9623bf429L, 880, 284),
            new CachedPower(0x80444b5e7aa7cf85L, 907, 292),
            new CachedPower(0xbf21e44003acdd2dL, 933, 300),
            new CachedPower(0x8e679c2f5e44ff8fL, 960, 308),
            new CachedPower(0xd433179d9c8cb841L, 986, 316),
            new CachedPower(0x9e19db92b4e31ba9L, 1013, 324),
            new CachedPower(0xeb96bf6ebadf77d9L, 1039, 332),
            new CachedPower(0xaf87023b9bf0ee6bL, 1066, 340)
    };

    static final int kCachedPowersOffset = 348;  // -1 * the first decimal_exponent.
    static final double kD_1_LOG2_10 = 0.30102999566398114;  //  1 / lg(10)
    // Difference between the decimal exponents in the table above.
    static final int kDecimalExponentDistance = 8;
    static final int kMinDecimalExponent = -348;
    static final int kMaxDecimalExponent = 340;

    static int getCachedPowerForBinaryExponentRange(
            final int min_exponent,
            final int max_exponent,
            final DiyFp power) {
        final int kQ = DiyFp.kSignificandSize;
        final double k = Math.ceil((min_exponent + kQ - 1) * kD_1_LOG2_10);
        final int index =
                (kCachedPowersOffset + (int) k - 1) / kDecimalExponentDistance + 1;
        assert (0 <= index && index < kCachedPowers.length);
        final CachedPower cached_power = kCachedPowers[index];
        assert (min_exponent <= cached_power.binaryExponent);
        assert (cached_power.binaryExponent <= max_exponent);
        power.setF(cached_power.significand);
        power.setE(cached_power.binaryExponent);
        return cached_power.decimalExponent;
    }


    static int getCachedPowerForDecimalExponent(final int requested_exponent,
                                                final DiyFp power) {
        assert (kMinDecimalExponent <= requested_exponent);
        assert (requested_exponent < kMaxDecimalExponent + kDecimalExponentDistance);
        final int index =
                (requested_exponent + kCachedPowersOffset) / kDecimalExponentDistance;
        final CachedPower cached_power = kCachedPowers[index];
        power.setF(cached_power.significand);
        power.setE(cached_power.binaryExponent);
        final int found_exponent = cached_power.decimalExponent;
        assert (found_exponent <= requested_exponent);
        assert (requested_exponent < found_exponent + kDecimalExponentDistance);
        return cached_power.decimalExponent;
    }

}
