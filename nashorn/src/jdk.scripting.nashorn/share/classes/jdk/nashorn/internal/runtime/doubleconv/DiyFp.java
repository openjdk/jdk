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

// This "Do It Yourself Floating Point" class implements a floating-point number
// with a uint64 significand and an int exponent. Normalized DiyFp numbers will
// have the most significant bit of the significand set.
// Multiplication and Subtraction do not normalize their results.
// DiyFp are not designed to contain special doubles (NaN and Infinity).
class DiyFp {

    private long f_;
    private int e_;

    static final int kSignificandSize = 64;
    static final long kUint64MSB = 0x8000000000000000L;


    DiyFp() {
        this.f_ = 0;
        this.e_ = 0;
    }

    DiyFp(final long f, final int e) {
        this.f_ = f;
        this.e_ = e;
    }

    // this = this - other.
    // The exponents of both numbers must be the same and the significand of this
    // must be bigger than the significand of other.
    // The result will not be normalized.
    void subtract(final DiyFp other) {
        assert (e_ == other.e_);
        assert Long.compareUnsigned(f_, other.f_) >= 0;
        f_ -= other.f_;
    }

    // Returns a - b.
    // The exponents of both numbers must be the same and this must be bigger
    // than other. The result will not be normalized.
    static DiyFp minus(final DiyFp a, final DiyFp b) {
        final DiyFp result = new DiyFp(a.f_, a.e_);
        result.subtract(b);
        return result;
    }


    // this = this * other.
    final void multiply(final DiyFp other) {
        // Simply "emulates" a 128 bit multiplication.
        // However: the resulting number only contains 64 bits. The least
        // significant 64 bits are only used for rounding the most significant 64
        // bits.
        final long kM32 = 0xFFFFFFFFL;
        final long a = f_ >>> 32;
        final long b = f_ & kM32;
        final long c = other.f_ >>> 32;
        final long d = other.f_ & kM32;
        final long ac = a * c;
        final long bc = b * c;
        final long ad = a * d;
        final long bd = b * d;
        long tmp = (bd >>> 32) + (ad & kM32) + (bc & kM32);
        // By adding 1U << 31 to tmp we round the final result.
        // Halfway cases will be round up.
        tmp += 1L << 31;
        final long result_f = ac + (ad >>> 32) + (bc >>> 32) + (tmp >>> 32);
        e_ += other.e_ + 64;
        f_ = result_f;
    }

    // returns a * b;
    static DiyFp times(final DiyFp a, final DiyFp b) {
        final DiyFp result = new DiyFp(a.f_, a.e_);
        result.multiply(b);
        return result;
    }

    void normalize() {
        assert(f_ != 0);
        long significand = this.f_;
        int exponent = this.e_;

        // This method is mainly called for normalizing boundaries. In general
        // boundaries need to be shifted by 10 bits. We thus optimize for this case.
        final long k10MSBits = 0xFFC00000L << 32;
        while ((significand & k10MSBits) == 0) {
            significand <<= 10;
            exponent -= 10;
        }
        while ((significand & kUint64MSB) == 0) {
            significand <<= 1;
            exponent--;
        }
        this.f_ = significand;
        this.e_ = exponent;
    }

    static DiyFp normalize(final DiyFp a) {
        final DiyFp result = new DiyFp(a.f_, a.e_);
        result.normalize();
        return result;
    }

    long f() { return f_; }
    int e() { return e_; }

    void setF(final long new_value) { f_ = new_value; }
    void setE(final int new_value) { e_ = new_value; }

    @Override
    public String toString() {
        return "DiyFp[f=" + f_ + ", e=" + e_ + "]";
    }

}
