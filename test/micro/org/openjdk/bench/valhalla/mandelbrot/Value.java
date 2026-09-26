/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package org.openjdk.bench.valhalla.mandelbrot;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;

@Fork(value = 3, jvmArgsAppend = {"--enable-preview"})
public class Value extends MandelbrotBase {

    private ValueComplex coordToComplex(int x, int y, int width, int height) {
        double cx = ((double) x) / (((double) width) / 2.0) - 1.0;
        double cy = ((double) y) / (((double) height) / 2.0) - 1.0;
        return new ValueComplex(cy * SCALE, cx * SCALE);
    }

    private static int count(ValueComplex c) {
        ValueComplex z = c;
        for (int i = 1; i < MAX_ITER; i++) {
            if (z.length() >= 2.0) return i;
            z = z.mul(z).add(c);
        }
        return MAX_ITER;
    }

    @Benchmark
    public int[][] mandelbrot_value() {
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                points[x][y] = count(coordToComplex(x, y, size, size));
            }
        }
        return points;
    }

    private Complex coordToComplex_interface(int x, int y, int width, int height) {
        double cx = ((double) x) / (((double) width) / 2.0) - 1.0;
        double cy = ((double) y) / (((double) height) / 2.0) - 1.0;
        return new ValueComplex(cy * SCALE, cx * SCALE);
    }

    private static int count_interface(Complex c) {
        Complex z = c;
        for (int i = 1; i < MAX_ITER; i++) {
            if (z.length() >= 2.0) return i;
            z = z.mul(z).add(c);
        }
        return MAX_ITER;
    }

    @Benchmark
    public int[][] mandelbrot_interface() {
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                points[x][y] = count_interface(coordToComplex_interface(x, y, size, size));
            }
        }
        return points;
    }

    public static interface Complex {
        public Complex add(Complex that);
        public Complex mul(Complex that);
        public double length();
        public double re();
        public double im();
    }

    public static value class ValueComplex implements Complex {

        public final double re;
        public final double im;

        public ValueComplex(double re, double im) {
            this.re =  re;
            this.im =  im;
        }

        @Override
        public double re() { return re; }

        @Override
        public double im() { return im; }

        public ValueComplex add(ValueComplex that) {
            return new ValueComplex(this.re + that.re, this.im + that.im);
        }

        @Override
        public Complex add(Complex that) {
            return new ValueComplex(this.re + that.re(), this.im + that.im());
        }

        public ValueComplex mul(ValueComplex that) {
            return new ValueComplex(this.re * that.re - this.im * that.im,
                    this.re * that.im + this.im * that.re);
        }

        @Override
        public Complex mul(Complex that) {
            return new ValueComplex(this.re * that.re() - this.im * that.im(),
                    this.re * that.im() + this.im * that.re());
        }

        public double length() {
            return Math.sqrt(re * re + im * im);
        }

    }
}
