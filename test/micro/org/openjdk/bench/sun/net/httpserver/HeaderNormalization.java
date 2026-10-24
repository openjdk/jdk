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
package org.openjdk.bench.sun.net.httpserver;

import com.sun.net.httpserver.Headers;
import org.openjdk.jmh.annotations.*;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Benchmarks {@code jdk.httpserver} header normalization.
 * <p>
 * You can run this benchmark as follows:
 * <pre>{@code
 * make run-test TEST="micro:HeaderNormalization" MICRO="OPTIONS=-prof gc"
 * }</pre>
 */
@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 5, time = 3, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@State(org.openjdk.jmh.annotations.Scope.Thread)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(value = 3, jvmArgs = {
        "--add-exports", "jdk.httpserver/com.sun.net.httpserver=ALL-UNNAMED",
        "--add-opens", "jdk.httpserver/com.sun.net.httpserver=ALL-UNNAMED",
})
public class HeaderNormalization {

    private static final Function<String, String> NORMALIZE = findNormalize();

    private static Function<String, String> findNormalize() {
        var lookup = MethodHandles.lookup();
        MethodHandle handle;
        try {
            handle = MethodHandles
                    .privateLookupIn(Headers.class, lookup)
                    .findStatic(
                            Headers.class, "normalize",
                            MethodType.methodType(String.class, String.class));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return key -> {
            try {
                return (String) handle.invokeExact(key);
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        };
    }

    @Param({
            "Accept-charset",   // Already normalized
            "4ccept-charset",   // Already normalized with a non-alpha first letter
            "accept-charset",   // Only the first `a` must be upper-cased
            "Accept-Charset",   // Only `c` must be lower-cased
            "ACCEPT-CHARSET",   // All secondary must be lower-cased
    })
    private String key;

    @Benchmark
    public String n26() {
        return NORMALIZE.apply(key);
    }

    @Benchmark
    public String n25() {
        return normalize25(key);
    }

    /**
     * The {@code com.sun.net.httpserver.Headers::normalize} method used in Java 25 and before.
     */
    private static String normalize25(String key) {
        Objects.requireNonNull(key);
        int len = key.length();
        if (len == 0) {
            return key;
        }
        char[] b = key.toCharArray();
        if (b[0] >= 'a' && b[0] <= 'z') {
            b[0] = (char)(b[0] - ('a' - 'A'));
        } else if (b[0] == '\r' || b[0] == '\n')
            throw new IllegalArgumentException("illegal character in key");

        for (int i=1; i<len; i++) {
            if (b[i] >= 'A' && b[i] <= 'Z') {
                b[i] = (char) (b[i] + ('a' - 'A'));
            } else if (b[i] == '\r' || b[i] == '\n')
                throw new IllegalArgumentException("illegal character in key");
        }
        return new String(b);
    }

}
