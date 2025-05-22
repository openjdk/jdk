/*
 * Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

/**
 * Measures connection setup times
 */
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Thread)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 3)
public class SocketChannelConnectionSetup {

    private ServerSocketChannel ssc;

    private Path sscFilePath;

    private SocketChannel s1, s2;

    @Param({"INET", "UNIX"})
    private String family;

    @Setup(Level.Trial)
    public void beforeRun() throws IOException {
        StandardProtocolFamily typedFamily = StandardProtocolFamily.valueOf(family);
        ssc = ServerSocketChannel.open(typedFamily).bind(null);
        // Record the UDS file path right after binding, as the socket may be
        // closed later due to a failure, and subsequent calls to `getPath()`
        // will throw.
        sscFilePath = ssc.getLocalAddress() instanceof UnixDomainSocketAddress udsChannel
                ? udsChannel.getPath()
                : null;
    }

    @TearDown(Level.Trial)
    public void afterRun() throws Exception {
        ssc.close();
        if (sscFilePath != null) {
            Files.delete(sscFilePath);
        }
    }

    @Benchmark
    @Measurement(iterations = 5, batchSize=200)
    public void test() throws IOException {
        s1 = SocketChannel.open(ssc.getLocalAddress());
        s2 = ssc.accept();
        s1.close();
        s2.close();
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(org.openjdk.bench.java.net.SocketChannelConnectionSetup.class.getSimpleName())
                .warmupForks(1)
                .forks(2)
                .build();

        new Runner(opt).run();

        opt = new OptionsBuilder()
                .include(org.openjdk.bench.java.net.SocketChannelConnectionSetup.class.getSimpleName())
                .jvmArgs("-Djdk.net.useFastTcpLoopback=true")
                .warmupForks(1)
                .forks(2)
                .build();

        new Runner(opt).run();
    }
}
