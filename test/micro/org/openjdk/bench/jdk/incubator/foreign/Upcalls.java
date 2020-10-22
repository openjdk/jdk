/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.openjdk.bench.jdk.incubator.foreign;

import jdk.incubator.foreign.FunctionDescriptor;
import jdk.incubator.foreign.LibraryLookup;
import jdk.incubator.foreign.MemoryAddress;
import jdk.incubator.foreign.CLinker;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.util.concurrent.TimeUnit;

import static java.lang.invoke.MethodHandles.lookup;
import static jdk.incubator.foreign.CLinker.C_INT;
import static jdk.incubator.foreign.CLinker.C_POINTER;

@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@State(org.openjdk.jmh.annotations.Scope.Thread)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(value = 3, jvmArgsAppend = { "--add-modules=jdk.incubator.foreign", "-Dforeign.restricted=permit" })
public class Upcalls {

    static final CLinker abi = CLinker.getInstance();
    static final MethodHandle blank;
    static final MethodHandle identity;

    static final MemoryAddress cb_blank;
    static final MemoryAddress cb_identity;

    static final long cb_blank_jni;
    static final long cb_identity_jni;

    static {
        System.loadLibrary("UpcallsJNI");

        String className = "org/openjdk/bench/jdk/incubator/foreign/Upcalls";
        cb_blank_jni = makeCB(className, "blank", "()V");
        cb_identity_jni = makeCB(className, "identity", "(I)I");

        try {
            LibraryLookup ll = LibraryLookup.ofLibrary("Upcalls");
            {
                LibraryLookup.Symbol addr = ll.lookup("blank").get();
                MethodType mt = MethodType.methodType(void.class, MemoryAddress.class);
                FunctionDescriptor fd = FunctionDescriptor.ofVoid(C_POINTER);
                blank = abi.downcallHandle(addr, mt, fd);

                cb_blank = abi.upcallStub(
                    lookup().findStatic(Upcalls.class, "blank", MethodType.methodType(void.class)),
                    FunctionDescriptor.ofVoid()
                ).address();
            }
            {
                LibraryLookup.Symbol addr = ll.lookup("identity").get();
                MethodType mt = MethodType.methodType(int.class, int.class, MemoryAddress.class);
                FunctionDescriptor fd = FunctionDescriptor.of(C_INT, C_INT, C_POINTER);
                identity = abi.downcallHandle(addr, mt, fd);

                cb_identity = abi.upcallStub(
                    lookup().findStatic(Upcalls.class, "identity", MethodType.methodType(int.class, int.class)),
                    FunctionDescriptor.of(C_INT, C_INT)
                ).address();
            }
        } catch (ReflectiveOperationException e) {
            throw new BootstrapMethodError(e);
        }
    }

    static native void blank(long cb);
    static native int identity(int x, long cb);
    static native long makeCB(String holder, String name, String signature);

    @Benchmark
    public void jni_blank() throws Throwable {
        blank(cb_blank_jni);
    }

    @Benchmark
    public void panama_blank() throws Throwable {
        blank.invokeExact(cb_blank);
    }

    @Benchmark
    public int jni_identity() throws Throwable {
        return identity(10, cb_identity_jni);
    }

    @Benchmark
    public int panama_identity() throws Throwable {
        return (int) identity.invokeExact(10, cb_identity);
    }

    static void blank() {}
    static int identity(int x) { return x; }
}
