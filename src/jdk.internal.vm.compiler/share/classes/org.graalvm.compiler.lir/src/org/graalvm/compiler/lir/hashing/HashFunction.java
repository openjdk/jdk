/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.lir.hashing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.graalvm.compiler.lir.gen.ArithmeticLIRGenerator;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.Value;

/**
 * This class provides a set of cheap imperfect hash functions based on the paper "Improving Switch
 * Statement Performance with Hashing Optimized at Compile Time".
 * (http://programming.sirrida.de/hashsuper.pdf)
 */
public abstract class HashFunction {

    /**
     * Applies the hash function.
     *
     * @param value the value to be hashed
     * @param min {@code value} is guaranteed to be greater or equal to this minimum
     * @return the hash value within int range
     */
    public abstract int apply(long value, long min);

    /**
     * Generates LIR that implements the hash function in terms of value and min.
     *
     * @param value the value to be hashed
     * @param min the lowest key
     * @param gen the lir generator
     * @return new lir value with the hash function applied
     */
    public abstract Value gen(Value value, Value min, ArithmeticLIRGenerator gen);

    /**
     * Returns an estimate of number of CPU cycles necessary to apply the hash function.
     */
    public abstract int effort();

    /**
     * @return a list of all available hash functions
     */
    public static final List<HashFunction> instances() {
        return Collections.unmodifiableList(instances);
    }

    private static List<HashFunction> instances = new ArrayList<>();

    private static int[] mersennePrimes = {3, 7, 31, 127, 8191, 131071, 524287, 2147483647};

    static {
      //@formatter:off

        add("val", 0,
            (val, min) -> val,
            gen -> (val, min) -> val);

        add("val - min", 1,
            (val, min) -> val - min,
            gen -> (val, min) -> gen.emitSub(val, min, false));

        add("val >> min", 1,
            (val, min) -> val >> min,
            gen -> (val, min) -> gen.emitShr(val, min));

        add("val >> (val & min)", 2,
            (val, min) -> val >> (val & min),
            gen -> (val, min) -> gen.emitShr(val, gen.emitAnd(val, min)));

        add("(val >> min) ^ val", 2,
            (val, min) -> (val >> min) ^ val,
            gen -> (val, min) -> gen.emitXor(gen.emitShr(val, min), val));

        add("(val >> min) * val", 3,
            (val, min) -> (val >> min) * val,
            gen -> (val, min) -> gen.emitMul(gen.emitShr(val, min), val, false));

        addWithPrimes("(val * prime) >> min", 3,
                      prime -> (val, min) -> (val * prime) >> min,
                      (gen, prime) -> (val, min) -> gen.emitShr(gen.emitMul(val, prime, false), min));

        addWithPrimes("rotateRight(val, prime)", 3,
                      prime -> (val, min) -> Long.rotateRight(val, prime),
                      (gen, prime) -> (val, min) -> gen.emitRor(val, prime));

        addWithPrimes("rotateRight(val, prime) + val", 4,
                      prime -> (val, min) -> Long.rotateRight(val, prime) + val,
                      (gen, prime) -> (val, min) -> gen.emitAdd(gen.emitRor(val, prime), val, false));

        addWithPrimes("rotateRight(val, prime) ^ val", 4,
                      prime -> (val, min) -> Long.rotateRight(val, prime) ^ val,
                      (gen, prime) -> (val, min) -> gen.emitXor(gen.emitRor(val, prime), val));
      //@formatter:on
    }

    private static void add(String toString, int effort, BiFunction<Long, Long, Long> f, Function<ArithmeticLIRGenerator, BiFunction<Value, Value, Value>> gen) {
        instances.add(new HashFunction() {

            @Override
            public int apply(long value, long min) {
                return f.apply(value, min).intValue();
            }

            @Override
            public int effort() {
                return effort;
            }

            @Override
            public String toString() {
                return toString;
            }

            @Override
            public Value gen(Value val, Value min, ArithmeticLIRGenerator t) {
                return gen.apply(t).apply(t.emitNarrow(val, 32), t.emitNarrow(min, 32));
            }
        });
    }

    private static void addWithPrimes(String toString, int effort, Function<Integer, BiFunction<Long, Long, Long>> f,
                    BiFunction<ArithmeticLIRGenerator, Value, BiFunction<Value, Value, Value>> gen) {
        for (int p : mersennePrimes) {
            add(toString, effort, f.apply(p), g -> gen.apply(g, g.getLIRGen().emitJavaConstant(JavaConstant.forInt(p))));
        }
    }
}
