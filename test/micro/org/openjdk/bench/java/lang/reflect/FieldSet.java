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
package org.openjdk.bench.java.lang.reflect;

import java.lang.reflect.Field;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadLocalRandom;

import org.openjdk.jmh.annotations.*;

@BenchmarkMode(Mode.AverageTime)
@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(3)
public class FieldSet {

    static class FieldHolder {
        Object obj;
        int intValue;
        long longValue;
        FieldHolder(Object obj, int i, long l) {
            this.obj = obj;
            this.intValue = i;
            this.longValue = l;
        }
    }

    static class FinalFieldHolder {
        final Object obj;
        final int intValue;
        final long longValue;
        FinalFieldHolder(Object obj, int i, long l) {
            this.obj = obj;
            this.intValue = i;
            this.longValue = l;
        }
    }

    private FieldHolder fieldHolder;
    private FinalFieldHolder finalFieldHolder;

    private Field objField1, objField2, objField3;
    private Field intField1, intField2, intField3;
    private Field longField1, longField2, longField3;

    @Setup
    public void setup() throws Exception {
        fieldHolder = new FieldHolder(new Object(), 1, 1L);
        finalFieldHolder = new FinalFieldHolder(new Object(), 1, 1L);

        // non-final && !override
        objField1 = FieldHolder.class.getDeclaredField("obj");
        intField1 = FieldHolder.class.getDeclaredField("intValue");
        longField1 = FieldHolder.class.getDeclaredField("longValue");

        // non-final && override
        objField2 = FieldHolder.class.getDeclaredField("obj");
        objField2.setAccessible(true);
        intField2 = FieldHolder.class.getDeclaredField("intValue");
        intField2.setAccessible(true);
        longField2 = FieldHolder.class.getDeclaredField("longValue");
        longField2.setAccessible(true);

        // final && override
        objField3 = FinalFieldHolder.class.getDeclaredField("obj");
        objField3.setAccessible(true);
        intField3 = FinalFieldHolder.class.getDeclaredField("intValue");
        intField3.setAccessible(true);
        longField3 = FinalFieldHolder.class.getDeclaredField("longValue");
        longField3.setAccessible(true);
    }

    // non-final && !override

    @Benchmark
    public void setNonFinalObjectField() throws Exception {
        objField1.set(fieldHolder, new Object());
    }

    @Benchmark
    public void setNonFinalIntField() throws Exception {
        int newValue = ThreadLocalRandom.current().nextInt();
        intField1.setInt(fieldHolder, newValue);
    }

    @Benchmark
    public void setNonFinalLongField() throws Exception {
        long newValue = ThreadLocalRandom.current().nextLong();
        longField1.setLong(fieldHolder, newValue);
    }

    // non-final && override

    @Benchmark
    public void setNonFinalObjectFieldWithOverride() throws Exception {
        objField2.set(fieldHolder, new Object());
    }

    @Benchmark
    public void setNonFinalIntFieldWithOverride() throws Exception {
        int newValue = ThreadLocalRandom.current().nextInt();
        intField2.setInt(fieldHolder, newValue);
    }

    @Benchmark
    public void setNonFinalLongFieldWithOverride() throws Exception {
        long newValue = ThreadLocalRandom.current().nextLong();
        longField2.setLong(fieldHolder, newValue);
    }

    // final && override

    @Benchmark
    public void setFinalObjectField()throws Exception {
        objField3.set(finalFieldHolder, new Object());
    }

    @Benchmark
    public void setFinalIntField() throws Exception {
        int newValue = ThreadLocalRandom.current().nextInt();
        intField3.setInt(finalFieldHolder, newValue);
    }

    @Benchmark
    public void setFinalLongField() throws Exception {
        long newValue = ThreadLocalRandom.current().nextLong();
        longField3.setLong(finalFieldHolder, newValue);
    }
}
