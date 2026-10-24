/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package org.openjdk.bench.java.net;

import org.openjdk.jmh.annotations.*;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.concurrent.TimeUnit;

/**
 * Tests java.net.URL.toString performance
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 3)
public class URLToString {

    @Param({"false", "true"})
    boolean auth;

    @Param({"false", "true"})
    boolean query;

    @Param({"false", "true"})
    boolean ref;

    private URL url;

    @Setup()
    public void setup() throws MalformedURLException {
        StringBuilder sb = new StringBuilder();
        if (auth) {
            sb.append("http://hostname");
        } else {
            sb.append("file:");
        }
        sb.append("/some/long/path/to/jar/app-1.0.jar!/org/summerframework/samples/horseclinic/HorseClinicApplication.class");
        if (query) {
            sb.append("?param=value");
        }
        if (ref) {
            sb.append("#fragment");
        }

        url = URI.create(sb.toString()).toURL();
    }

    @Benchmark
    public String urlToString() {
        return url.toString();
    }
}
