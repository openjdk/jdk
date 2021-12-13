/*
 * Copyright (c) 2014, 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.openjdk.bench.java.io;

import org.openjdk.bench.java.io.BlackholedOutputStream;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.infra.Blackhole;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectInputFilter;
import java.io.ObjectInputFilter.Status;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
public class SerialFilterOverhead {

    private byte[] bytes;
    private int count;
    @Setup
    public void setup(Blackhole bh) throws IOException {
        count = 10;
        bytes = serialize(count, new Class1());
    }

    @TearDown
    public void teardown() throws IOException {
        bytes = null;
    }

    @Benchmark
    public void readNoFilter() throws IOException, ClassNotFoundException {
        deserialize(count, bytes, null);
    }

    @Benchmark
    public void readSerialFilter() throws IOException, ClassNotFoundException {
        deserialize(count, bytes, new Filter());
    }

    @Benchmark
    public void readNanoTimeFilter() throws IOException, ClassNotFoundException {
        deserialize(count, bytes, new NanoTimeFilter());
    }

    private static void deserialize(int count, byte[] bytes, ObjectInputFilter filter)
            throws IOException, ClassNotFoundException {
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            if (filter != null)
                ois.setObjectInputFilter(filter);
            for (int i = 0; i < count; i++) {
                Object o = ois.readObject();
            }
        }
    }

    private static byte[] serialize(int count, Object o) throws IOException {
        try (ByteArrayOutputStream ba = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(ba)) {
            for (int i = 0; i < count; i++) {
                oos.writeObject(o);
            }
            return ba.toByteArray();
        }
    }


    // A class with three fields.
    public static class Class1 implements Serializable {
        private static final long serialVersionUID = 2L;
        private String string;
        private long longValue;
        private Integer integer;

        public Class1() {
            this.string = "now is the time";
            this.longValue = 12345L;
            this.integer = 17;
        }
    }

    static class Filter implements ObjectInputFilter {
        public Status checkInput(FilterInfo info) {
            return Status.ALLOWED;
        }
    }

    static class NanoTimeFilter implements ObjectInputFilter {
        public Status checkInput(FilterInfo info) {
            long t = System.nanoTime();
            return Status.ALLOWED;
        }
    }

}
