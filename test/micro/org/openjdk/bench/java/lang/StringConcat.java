/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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
package org.openjdk.bench.java.lang;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;

/**
 * Trivial String concatenation benchmark.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
@Fork(3)
public class StringConcat {

    @Param("4711")
    public int intValue;

    public String stringValue = String.valueOf(intValue);

    public Object objectValue = Long.valueOf(intValue);

    public boolean boolValue = true;

    public byte byteValue = (byte)-128;

    public String emptyString = "";

    @Benchmark
    public String concatConstInt() {
        return "string" + intValue;
    }

    @Benchmark
    public String concatConstString() {
        return "string" + stringValue;
    }

    @Benchmark
    public String concatEmptyRight() {
        return stringValue + emptyString;
    }

    @Benchmark
    public String concatEmptyLeft() {
        return emptyString + stringValue;
    }

    @Benchmark
    public String concatEmptyConstInt() {
        return "" + intValue;
    }

    @Benchmark
    public String concatEmptyConstString() {
        return "" + stringValue;
    }

    @Benchmark
    public String concatMethodConstString() {
        return "string".concat(stringValue);
    }

    @Benchmark
    public String concatConstIntConstInt() {
        return "string" + intValue + "string" + intValue;
    }

    @Benchmark
    public String concatConstStringConstInt() {
        return "string" + stringValue + "string" + intValue;
    }

    @Benchmark
    public String concatMix4String() {
        // Investigate "profile pollution" between shared LFs that might eliminate some JIT optimizations
        String s1 = "string" + stringValue + stringValue + stringValue + stringValue;
        String s2 = "string" + stringValue + "string" + stringValue + stringValue + stringValue;
        String s3 = stringValue + stringValue + "string" + stringValue + "string" + stringValue + "string";
        String s4 = "string" + stringValue + "string" + stringValue + "string" + stringValue + "string" + stringValue + "string";
        return s1 + s2 + s3 + s4;
    }

    @Benchmark
    public String concatConst4String() {
        return "string" + stringValue + stringValue + stringValue + stringValue;
    }

    @Benchmark
    public String concat4String() {
        return stringValue + stringValue + stringValue + stringValue;
    }

    @Benchmark
    public String concatConst2String() {
        return "string" + stringValue + stringValue;
    }

    @Benchmark
    public String concatConstBoolByte() {
        return "string" + boolValue + byteValue;
    }

    @Benchmark
    public String concatConst6String() {
        return "string" + stringValue + stringValue + stringValue + stringValue + stringValue + stringValue;
    }

    @Benchmark
    public String concat6String() {
        return stringValue + stringValue + stringValue + stringValue + stringValue + stringValue;
    }

    @Benchmark
    public String concatConst6Object() {
        return "string" + objectValue + objectValue + objectValue + objectValue + objectValue + objectValue;
    }

    private String
            f0="1", f1="1", f2="1", f3="1", f4="1", f5="1", f6="1", f7="1", f8="1", f9="1",
            f10="1", f11="1", f12="1", f13="1", f14="1", f15="1", f16="1", f17="1", f18="1", f19="1",
            f20="1", f21="1", f22="1", f23="1", f24="1", f25="1", f26="1", f27="1", f28="1", f29="1",
            f30="1", f31="1", f32="1", f33="1", f34="1", f35="1", f36="1", f37="1", f38="1", f39="1",
            f40="1", f41="1", f42="1", f43="1", f44="1", f45="1", f46="1", f47="1", f48="1", f49="1",
            f50="1", f51="1", f52="1", f53="1", f54="1", f55="1", f56="1", f57="1", f58="1", f59="1",
            f60="1", f61="1", f62="1", f63="1", f64="1", f65="1", f66="1", f67="1", f68="1", f69="1",
            f70="1", f71="1", f72="1", f73="1", f74="1", f75="1", f76="1", f77="1", f78="1", f79="1",
            f80="1", f81="1", f82="1", f83="1", f84="1", f85="1", f86="1", f87="1", f88="1", f89="1",
            f90="1", f91="1", f92="1", f93="1", f94="1", f95="1", f96="1", f97="1", f98="1", f99="1",
            f100="1",f101="1",f102="1",f103="1",f104="1",f105="1",f106="1",f107="1",f108="1",f109="1",
            f110="1",f111="1",f112="1",f113="1",f114="1",f115="1",f116="1",f117="1",f118="1",f119="1",
            f120="1",f121="1",f122="1";

    @Benchmark
    public String concat13String() {
        return f0 + ","+ f1 + ","+ f2 + ","+ f3 + ","+ f4 + ","+ f5 + ","+ f6 + ","+ f7 + ","+ f8 + ","+ f9 + ","
                + f10 + ","+ f11 + ","+ f12;
    }

    @Benchmark
    public String concat23String() {
        return f0 + ","+ f1 + ","+ f2 + ","+ f3 + ","+ f4 + ","+ f5 + ","+ f6 + ","+ f7 + ","+ f8 + ","+ f9 + ","
                + f10 + ","+ f11 + ","+ f12 + ","+ f13 + ","+ f14 + ","+ f15 + ","+ f16 + ","+ f17 + ","+ f18 + ","+ f19 + ","
                + f20 + ","+ f21 + ","+ f22;
    }
    @Benchmark
    public String concat123String() {
        return f0 + ","+ f1 + ","+ f2 + ","+ f3 + ","+ f4 + ","+ f5 + ","+ f6 + ","+ f7 + ","+ f8 + ","+ f9 + ","
                + f10 + ","+ f11 + ","+ f12 + ","+ f13 + ","+ f14 + ","+ f15 + ","+ f16 + ","+ f17 + ","+ f18 + ","+ f19 + ","
                + f20 + ","+ f21 + ","+ f22 + ","+ f23 + ","+ f24 + ","+ f25 + ","+ f26 + ","+ f27 + ","+ f28 + ","+ f29 + ","
                + f30 + ","+ f31 + ","+ f32 + ","+ f33 + ","+ f34 + ","+ f35 + ","+ f36 + ","+ f37 + ","+ f38 + ","+ f39 + ","
                + f40 + ","+ f41 + ","+ f42 + ","+ f43 + ","+ f44 + ","+ f45 + ","+ f46 + ","+ f47 + ","+ f48 + ","+ f49 + ","
                + f50 + ","+ f51 + ","+ f52 + ","+ f53 + ","+ f54 + ","+ f55 + ","+ f56 + ","+ f57 + ","+ f58 + ","+ f59 + ","
                + f60 + ","+ f61 + ","+ f62 + ","+ f63 + ","+ f64 + ","+ f65 + ","+ f66 + ","+ f67 + ","+ f68 + ","+ f69 + ","
                + f70 + ","+ f71 + ","+ f72 + ","+ f73 + ","+ f74 + ","+ f75 + ","+ f76 + ","+ f77 + ","+ f78 + ","+ f79 + ","
                + f80 + ","+ f81 + ","+ f82 + ","+ f83 + ","+ f84 + ","+ f85 + ","+ f86 + ","+ f87 + ","+ f88 + ","+ f89 + ","
                + f90 + ","+ f91 + ","+ f92 + ","+ f93 + ","+ f94 + ","+ f95 + ","+ f96 + ","+ f97 + ","+ f98 + ","+ f99 + ","
                +f100 + ","+f101 + ","+f102 + ","+f103 + ","+f104 + ","+f105 + ","+f106 + ","+f107 + ","+f108 + ","+f109 + ","
                +f110 + ","+f111 + ","+f112 + ","+f113 + ","+f114 + ","+f115 + ","+f116 + ","+f117 + ","+f118 + ","+f119 + ","
                +f120 + ","+f121 + ","+f122;
    }

    @Benchmark
    public String concat23StringConst() {
        return f0 + f0 + f0 + f0 + f0 + f0 + f0 + f0 + f0 + f0 + f0 + f0 + f0 + f0 + f0 + f0 + f0 + f0 + f0 + f0 + f0 + f0 + f0 + """
                A really long constant string. Such as a copyright header:
                 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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
                """;
    }

    public static void main(String... args) {
        StringConcat concat = new StringConcat();
        concat.concat4String();
        concat.concat123String();
        concat.concat6String();
        concat.concat13String();
        concat.concat23String();
        concat.concatConstInt();
    }


}
