/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

package org.openjdk.bench.java.math;

import java.math.BigInteger;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 3, time = 1)
@Fork(value = 3)
public class BigIntegerEquals {

    // The below list was derived from stats gathered from running tests in
    // the security area, which is the biggest client of BigInteger in JDK.
    //
    // Every time bigInteger1.equals(bigInteger2) was called, the
    // Math.min(bigInteger1.bitLength(), bigInteger2.bitLength()) value
    // was recorded. Recorded values were then sorted by frequency in
    // descending order. Top 20 of the most frequent values were then picked.
    @Param({
            "256",
            "255",
            "521",
            "384",
              "1",
             "46",
            "252",
            "446",
            "448",
            "383",
            "520",
            "254",
            "130",
            "445",
            "129",
            "447",
            "519",
            "251",
            "382",
            "253",
    })
    private int nBits;

    private BigInteger x, y;

    @Setup
    public void setup() {
        var p = Shared.createPair(nBits);
        x = p.x();
        y = p.y();
    }

    @Benchmark
    public boolean testEquals() {
        return x.equals(y);
    }
}
