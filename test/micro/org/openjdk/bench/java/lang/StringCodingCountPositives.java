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
package org.openjdk.bench.java.lang;

import jdk.internal.access.JavaLangAccess;
import jdk.internal.access.SharedSecrets;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.CompilerControl;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Warmup;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 3)
@Measurement(iterations = 5, time = 3)
@Fork(value = 2, jvmArgs = {"--add-exports=java.base/jdk.internal.access=ALL-UNNAMED"})
public class StringCodingCountPositives {

    public static final JavaLangAccess JLA = SharedSecrets.getJavaLangAccess();

    private static final byte[] BUFFER = """
            Lorem ipsum dolor sit amet, consectetur adipiscing elit. Aliquam ac sem eu
            urna egestas placerat. Etiam finibus ipsum nulla, non mattis dolor cursus a.
            Nulla nec nisl consectetur, lacinia neque id, accumsan ante. Curabitur et
            sapien in magna porta ultricies. Sed vel pellentesque nibh. Pellentesque dictum
            dignissim diam eu ultricies. Class aptent taciti sociosqu ad litora torquent
            per conubia nostra, per inceptos himenaeos. Suspendisse erat diam, fringilla
            sed massa sed, posuere viverra orci. Suspendisse tempor libero non gravida
            efficitur. Vivamus lacinia risus non orci viverra, at consectetur odio laoreet.
            Suspendisse potenti.""".getBytes(StandardCharsets.UTF_8);

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public int countPositives() {
        return JLA.countPositives(BUFFER, 0, BUFFER.length);
    }

}
