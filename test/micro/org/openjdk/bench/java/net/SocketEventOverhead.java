/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.event.SocketReadEvent;
import jdk.internal.event.SocketWriteEvent;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.TimeUnit;

/**
 * Test the overhead of the handling jfr events SocketReadEvent and
 * SocketWriteEvent without the latencies of the actual I/O code.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@State(Scope.Thread)
public class SocketEventOverhead {

    @Fork(value = 1, jvmArgsAppend = {
        "--add-exports",
        "java.base/jdk.internal.event=ALL-UNNAMED" })
    @Benchmark
    public int socketWriteJFRDisabled(SkeletonFixture fixture) {
        return fixture.write();
    }

    @Fork(value = 1, jvmArgsAppend = {
        "--add-exports",
        "java.base/jdk.internal.event=ALL-UNNAMED",
        "-XX:StartFlightRecording:jdk.SocketWrite#enabled=false"})
    @Benchmark
    public int socketWriteJFREnabledEventDisabled(SkeletonFixture fixture) {
        return fixture.write();
    }

    @Fork(value = 1, jvmArgsAppend = {
        "--add-exports",
        "java.base/jdk.internal.event=ALL-UNNAMED",
        "-XX:StartFlightRecording:jdk.SocketWrite#enabled=true,jdk.SocketWrite#threshold=1s"})
    @Benchmark
    public int socketWriteJFREnabledEventNotEmitted(SkeletonFixture fixture) {
        return fixture.write();
    }

    @Fork(value = 1, jvmArgsAppend = {
        "--add-exports","java.base/jdk.internal.event=ALL-UNNAMED",
        "-XX:StartFlightRecording:jdk.SocketWrite#enabled=true,jdk.SocketWrite#threshold=0ms,disk=false,jdk.SocketWrite#stackTrace=false"})
    @Benchmark
    public int socketWriteJFREnabledEventEmitted(SkeletonFixture fixture) {
        return fixture.write();
    }

    @Fork(value = 1, jvmArgsAppend = {
        "--add-exports",
        "java.base/jdk.internal.event=ALL-UNNAMED" })
    @Benchmark
    public int socketReadJFRDisabled(SkeletonFixture fixture) {
        return fixture.read();
    }

    @Fork(value = 1, jvmArgsAppend = {
        "--add-exports",
        "java.base/jdk.internal.event=ALL-UNNAMED",
        "-XX:StartFlightRecording:jdk.SocketRead#enabled=false"})
    @Benchmark
    public int socketReadJFREnabledEventDisabled(SkeletonFixture fixture) {
        return fixture.read();
    }

    @Fork(value = 1, jvmArgsAppend = {
        "--add-exports",
        "java.base/jdk.internal.event=ALL-UNNAMED",
        "-XX:StartFlightRecording:jdk.SocketRead#enabled=true,jdk.SocketRead#threshold=1s"})
    @Benchmark
    public int socketReadJFREnabledEventNotEmitted(SkeletonFixture fixture) {
        return fixture.read();
    }

    @Fork(value = 1, jvmArgsAppend = {
        "--add-exports","java.base/jdk.internal.event=ALL-UNNAMED",
        "-XX:StartFlightRecording:jdk.SocketRead#enabled=true,jdk.SocketRead#threshold=0ms,disk=false,jdk.SocketRead#stackTrace=false"})
    @Benchmark
    public int socketReadJFREnabledEventEmitted(SkeletonFixture fixture) {
        return fixture.read();
    }

    /**
     * Fixture with fake read/write operations that have only the JFR event
     * boilerplate code for managing jfr events.  No actual transfer is done
     * to eliminate the I/O portion and measure the overhead of JFR event
     * handling in it's various states.
     */
    @State(Scope.Thread)
    public static class SkeletonFixture {

        private final InetSocketAddress remote = new InetSocketAddress("localhost",5000);

        public SocketAddress getRemoteAddress() {
            return remote;
        }

        public int write() {
            if (! SocketWriteEvent.enabled()) {
                return write0();
            }
            int nbytes = 0;
            long start = SocketWriteEvent.timestamp();
            try {
                nbytes = write0();
            } finally {
                SocketWriteEvent.offer(start, nbytes, getRemoteAddress());
            }
            return nbytes;
        }

        private int write0() {
            return 1024;
        }

        public int read() {
            if (! SocketReadEvent.enabled()) {
                return read0();
            }
            int nbytes = 0;
            long start = SocketReadEvent.timestamp();
            try {
                nbytes = read0();
            } finally {
                SocketReadEvent.offer(start, nbytes, getRemoteAddress(), 0);
            }
            return nbytes;
        }

        private int read0() {
            return 1024;
        }
    }
}
