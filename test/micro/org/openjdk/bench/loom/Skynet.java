/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.openjdk.bench.loom;

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

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;

@Fork(3)
@Warmup(iterations = 5, time = 10)
@Measurement(iterations = 10, time = 10)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Thread)
@SuppressWarnings("preview")
public class Skynet {

    @Param({"1000000"})
    public int num;

    static class Channel<T> {
        private final BlockingQueue<T> q;

        Channel() {
            q = new SynchronousQueue<>();
        }

        void send(T e) {
            boolean interrupted = false;
            while (true) {
                try {
                    q.put(e);
                    break;
                } catch (InterruptedException x) {
                    interrupted = true;
                }
            }
            if (interrupted)
                Thread.currentThread().interrupt();
        }

        T receive() {
            boolean interrupted = false;
            T e;
            while (true) {
                try {
                    e = q.take();
                    break;
                } catch (InterruptedException x) {
                    interrupted = true;
                }
            }
            if (interrupted)
                Thread.currentThread().interrupt();
            return e;
        }
    }

    static void skynet(Channel<Long> result, int num, int size, int div) {
        if (size == 1) {
            result.send((long)num);
        } else {
            var chan = new Channel<Long>();
            for (int i = 0; i < div; i++) {
                int subNum = num + i * (size / div);
                Thread.startVirtualThread(() -> skynet(chan, subNum, size / div, div));
            }
            long sum = 0;
            for (int i = 0; i < div; i++) {
                sum += chan.receive();
            }
            result.send(sum);
        }
    }

    @Benchmark
    public long skynet() {
        var chan = new Channel<Long>();
        Thread.startVirtualThread(() -> skynet(chan, 0, num, 10));
        return chan.receive();
    }

}
