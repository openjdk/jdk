/*
 * Copyright (c) 2014, 2020, Red Hat Inc. All rights reserved.
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

/*

Sample runs on Ryzen 2950X:

Before JDK-8254078:

Benchmark                                                    (BASIC_TYPE)  (SIZE)  Mode  Cnt     Score     Error  Units
DataOutputStreamTest.dataOutputStreamOverBufferedFileStream          char    4096  avgt    6    58.884 ±   1.018  us/op
DataOutputStreamTest.dataOutputStreamOverBufferedFileStream         short    4096  avgt    6    59.821 ±   0.928  us/op
DataOutputStreamTest.dataOutputStreamOverBufferedFileStream           int    4096  avgt    6    59.596 ±   1.036  us/op
DataOutputStreamTest.dataOutputStreamOverByteArray                   char    4096  avgt    6    48.122 ±   5.672  us/op
DataOutputStreamTest.dataOutputStreamOverByteArray                  short    4096  avgt    6    49.669 ±   6.732  us/op
DataOutputStreamTest.dataOutputStreamOverByteArray                    int    4096  avgt    6    53.289 ±   5.453  us/op
DataOutputStreamTest.dataOutputStreamOverRawFileStream               char    4096  avgt    6  5479.807 ±  84.198  us/op
DataOutputStreamTest.dataOutputStreamOverRawFileStream              short    4096  avgt    6  5442.496 ± 116.902  us/op
DataOutputStreamTest.dataOutputStreamOverRawFileStream                int    4096  avgt    6  5458.223 ±  95.796  us/op

After:
DataOutputStreamTest.dataOutputStreamOverBufferedFileStream          char    4096  avgt    6    29.819 ±  0.805  us/op
DataOutputStreamTest.dataOutputStreamOverBufferedFileStream         short    4096  avgt    6    29.763 ±  0.510  us/op
DataOutputStreamTest.dataOutputStreamOverBufferedFileStream           int    4096  avgt    6    14.590 ±  0.603  us/op
DataOutputStreamTest.dataOutputStreamOverByteArray                   char    4096  avgt    6    30.153 ±  1.285  us/op
DataOutputStreamTest.dataOutputStreamOverByteArray                  short    4096  avgt    6    29.437 ±  1.286  us/op
DataOutputStreamTest.dataOutputStreamOverByteArray                    int    4096  avgt    6    17.165 ±  5.683  us/op
DataOutputStreamTest.dataOutputStreamOverRawFileStream               char    4096  avgt    6  3488.750 ± 77.946  us/op
DataOutputStreamTest.dataOutputStreamOverRawFileStream              short    4096  avgt    6  3458.778 ± 68.897  us/op
DataOutputStreamTest.dataOutputStreamOverRawFileStream                int    4096  avgt    6  1709.026 ± 35.592  us/op

*/

package org.openjdk.bench.java.io;

import org.openjdk.jmh.annotations.*;

import java.io.*;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Fork(value = 1, warmups = 0)
@Measurement(iterations = 6, time = 1)
@Warmup(iterations=2, time = 2)
public class DataOutputStreamTest {

    enum BasicType { CHAR, SHORT, INT, STRING }

    @State(Scope.Benchmark)
    public static class BenchmarkState {
        @Param({"4096"}) int SIZE;
        @Param({"char", "short", "int", /*"string"*/}) String BASIC_TYPE;
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream(SIZE);
        final File f = new File("DataOutputStreamTest.tmp");
        String outputString;
        FileOutputStream fileOutputStream;
        DataOutput bufferedFileStream, rawFileStream, byteArrayStream;
        BasicType basicType;

        @Setup(Level.Trial)
        public void setup() {
            try {
                fileOutputStream = new FileOutputStream(f);
                byteArrayStream = new DataOutputStream(byteArrayOutputStream);
                rawFileStream = new DataOutputStream(fileOutputStream);
                bufferedFileStream = new DataOutputStream(new BufferedOutputStream(fileOutputStream));
                switch (BASIC_TYPE.toLowerCase()) {
                    case "char": basicType = BasicType.CHAR; break;
                    case "short": basicType =  BasicType.SHORT; break;
                    case "int": basicType = BasicType.INT; break;
                    case "string": basicType = BasicType.STRING; break;
                    default: throw new RuntimeException("Unhandled basic type:" + BASIC_TYPE);
                };
                outputString = new String(new byte[SIZE]);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    public void writeChars(BenchmarkState state, DataOutput dataOutput) {
        try {
            for (int i = 0; i < state.SIZE; i += 2) {
                dataOutput.writeChar(i);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void writeShorts(BenchmarkState state, DataOutput dataOutput) {
        try {
            for (int i = 0; i < state.SIZE; i += 2) {
                dataOutput.writeShort(i);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void writeInts(BenchmarkState state, DataOutput dataOutput) {
        try {
            for (int i = 0; i < state.SIZE; i += 4) {
                dataOutput.writeInt(i);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void writeString(BenchmarkState state, DataOutput dataOutput) {
        try {
            dataOutput.writeChars(state.outputString);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void write(BenchmarkState state, DataOutput dataOutput) {
        switch (state.basicType) {
            case CHAR: writeChars(state, dataOutput); break;
            case SHORT: writeShorts(state, dataOutput); break;
            case INT: writeInts(state, dataOutput); break;
            case STRING: writeString(state, dataOutput); break;
        }
    }
    @Benchmark
    public void dataOutputStreamOverByteArray(BenchmarkState state)
            throws IOException {
        state.byteArrayOutputStream.reset();
        write(state, state.byteArrayStream);
        state.byteArrayOutputStream.flush();
    }

    @Benchmark
    public void dataOutputStreamOverRawFileStream(BenchmarkState state)
            throws IOException {
        state.fileOutputStream.getChannel().position(0);
        write(state, state.rawFileStream);
        state.fileOutputStream.flush();
    }

    @Benchmark
    public void dataOutputStreamOverBufferedFileStream(BenchmarkState state)
            throws IOException{
        state.fileOutputStream.getChannel().position(0);
        write(state, state.bufferedFileStream);
        state.fileOutputStream.flush();
    }
}
