/*
 *  Copyright (c) 2021, Intel Corporation. All rights reserved.
 *  DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 *  This code is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU General Public License version 2 only, as
 *  published by the Free Software Foundation.
 *
 *  This code is distributed in the hope that it will be useful, but WITHOUT
 *  ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 *  FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 *  version 2 for more details (a copy is included in the LICENSE file that
 *  accompanied this code).
 *
 *  You should have received a copy of the GNU General Public License version
 *  2 along with this work; if not, write to the Free Software Foundation,
 *  Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 *  Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 *  or visit www.oracle.com if you need additional information or have any
 *  questions.
 *
 */

package org.openjdk.bench.jdk.incubator.vector;

import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;
import org.openjdk.jmh.annotations.*;

import java.util.Objects;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.function.IntUnaryOperator;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 5)
@Measurement(iterations = 3, time = 5)
@Fork(value = 1, jvmArgsPrepend = {"--add-modules=jdk.incubator.vector"})
public class BlackScholes {

    @Param("1024")
    int size;


    float[] s0; // Stock Price
    float[] x;  // Strike Price
    float[] t;  // Maturity
    float[] call;
    float[] put;
    float r;    // risk-neutrality
    float sig;  // volatility
    Random rand;


    float randFloat(float low, float high) {
       float val = rand.nextFloat();
       return (1.0f - val) * low + val * high;
    }

    float[] fillRandom(float low, float high) {
        float[] array = new float[size];
        for (int i = 0; i < array.length; i++) {
            array[i] = randFloat(low, high);
        }
        return array;
    }

    @Setup
    public void init() {
        rand = new Random();
        s0 = fillRandom(5.0f, 30.0f);
        x  = fillRandom(1.0f, 100.0f);
        t  = fillRandom(0.25f, 10.0f);
        r = 0.02f;
        sig = 0.30f;
        call = new float[size];
        put = new float[size];
    }

    static final float Y = 0.2316419f;
    static final float A1 = 0.31938153f;
    static final float A2 = -0.356563782f;
    static final float A3 = 1.781477937f;
    static final float A4 = -1.821255978f;
    static final float A5 = 1.330274429f;
    static final float PI = (float)Math.PI;

    float cdf(float inp) {
        float x = inp;
        if (inp < 0f) {
            x = -inp;
        }

        float term = 1f / (1f + (Y * x));
        float term_pow2 = term * term;
        float term_pow3 = term_pow2 * term;
        float term_pow4 = term_pow2 * term_pow2;
        float term_pow5 = term_pow2 * term_pow3;

        float part1 = (1f / (float)Math.sqrt(2f * PI)) * (float)Math.exp((-x * x) * 0.5f);

        float part2 = (A1 * term) +
                      (A2 * term_pow2) +
                      (A3 * term_pow3) +
                      (A4 * term_pow4) +
                      (A5 * term_pow5);

        if (inp >= 0f)
            return 1f - part1 * part2;
        else
            return part1 * part2;

    }

    public void scalar_black_scholes_kernel(int off) {
        float sig_sq_by2 = 0.5f * sig * sig;
        for (int i = off; i < size; i++ ) {
            float log_s0byx = (float)Math.log(s0[i] / x[i]);
            float sig_sqrt_t = sig * (float)Math.sqrt(t[i]);
            float exp_neg_rt = (float)Math.exp(-r * t[i]);
            float d1 = (log_s0byx + (r + sig_sq_by2) * t[i])/(sig_sqrt_t);
            float d2 = d1 - sig_sqrt_t;
            call[i] = s0[i] * cdf(d1) - exp_neg_rt * x[i] * cdf(d2);
            put[i]  = call[i] + exp_neg_rt - s0[i];
       }
    }

    @Benchmark
    public void scalar_black_scholes() {
        scalar_black_scholes_kernel(0);
    }

    static final VectorSpecies<Float> fsp = FloatVector.SPECIES_PREFERRED;

    FloatVector vcdf(FloatVector vinp) {
        var vx = vinp.abs();
        var vone = FloatVector.broadcast(fsp, 1.0f);
        var vtwo = FloatVector.broadcast(fsp, 2.0f);
        var vterm = vone.div(vone.add(vx.mul(Y)));
        var vterm_pow2 = vterm.mul(vterm);
        var vterm_pow3 = vterm_pow2.mul(vterm);
        var vterm_pow4 = vterm_pow2.mul(vterm_pow2);
        var vterm_pow5 = vterm_pow2.mul(vterm_pow3);
        var vpart1 = vone.div(vtwo.mul(PI).lanewise(VectorOperators.SQRT)).mul(vx.mul(vx).neg().lanewise(VectorOperators.EXP).mul(0.5f));
        var vpart2 = vterm.mul(A1).add(vterm_pow2.mul(A2)).add(vterm_pow3.mul(A3)).add(vterm_pow4.mul(A4)).add(vterm_pow5.mul(A5));
        var vmask = vinp.compare(VectorOperators.GT, 0f);
        var vresult1 = vpart1.mul(vpart2);
        var vresult2 = vresult1.neg().add(vone);
        var vresult = vresult1.blend(vresult2, vmask);

        return vresult;
    }

    public int vector_black_scholes_kernel() {
        int i = 0;
        var vsig = FloatVector.broadcast(fsp, sig);
        var vsig_sq_by2 = vsig.mul(vsig).mul(0.5f);
        var vr = FloatVector.broadcast(fsp, r);
        var vnegr = FloatVector.broadcast(fsp, -r);
        for (; i <= x.length - fsp.length(); i += fsp.length()) {
            var vx = FloatVector.fromArray(fsp, x, i);
            var vs0 = FloatVector.fromArray(fsp, s0, i);
            var vt = FloatVector.fromArray(fsp, t, i);
            var vlog_s0byx = vs0.div(vx).lanewise(VectorOperators.LOG);
            var vsig_sqrt_t = vt.lanewise(VectorOperators.SQRT).mul(vsig);
            var vexp_neg_rt = vt.mul(vnegr).lanewise(VectorOperators.EXP);
            var vd1 = vsig_sq_by2.add(vr).mul(vt).add(vlog_s0byx).div(vsig_sqrt_t);
            var vd2 = vd1.sub(vsig_sqrt_t);
            var vcall = vs0.mul(vcdf(vd1)).sub(vx.mul(vexp_neg_rt).mul(vcdf(vd2)));
            var vput = vcall.add(vexp_neg_rt).sub(vs0);
            vcall.intoArray(call, i);
            vput.intoArray(put, i);
        }
        return i;
    }

    @Benchmark
    public void vector_black_scholes() {
        int processed = vector_black_scholes_kernel();
        if (processed < size) {
            scalar_black_scholes_kernel(processed);
        }
    }
}

