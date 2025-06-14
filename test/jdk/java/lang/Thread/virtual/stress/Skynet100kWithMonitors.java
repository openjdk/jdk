/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @test
 * @summary Stress test virtual threads with a variation of the Skynet 1M benchmark that uses
 *   a channel implementation based on object monitors. This variant uses a reduced number of
 *   100k virtual threads at the final level.
 * @requires vm.debug != true & vm.continuations
 * @run main/othervm/timeout=300 Skynet100kWithMonitors 50
 */

/*
 * @test
 * @requires vm.debug == true & vm.continuations
 * @run main/othervm/timeout=300 Skynet100kWithMonitors 10
 */

public class Skynet100kWithMonitors {

    public static void main(String[] args) {
        int iterations = (args.length) > 0 ? Integer.parseInt(args[0]) : 10;
        for (int i = 0; i < iterations; i++) {
            skynet(100_000, 4999950000L);
        }
    }

    static void skynet(int num, long expected) {
        long start = System.currentTimeMillis();
        var chan = new Channel<Long>();

        Thread.startVirtualThread(() -> skynet(chan, 0, num, 10));

        long sum = chan.receive();
        long end = System.currentTimeMillis();
        System.out.format("Result: %d in %s ms%n", sum, (end-start));
        if (sum != expected)
            throw new RuntimeException("Expected " + expected);
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

    static class Channel<T> {
        private final Object lock = new Object();
        private T element;

        Channel() {
        }

        void send(T e) {
            boolean interrupted = false;
            synchronized (lock) {
                while (element != null) {
                    try {
                        lock.wait();
                    } catch (InterruptedException x) {
                        interrupted = true;
                    }
                }
                element = e;
                lock.notifyAll();
            }
            if (interrupted)
                Thread.currentThread().interrupt();
        }

        T receive() {
            T e;
            boolean interrupted = false;
            synchronized (lock) {
                while ((e = element) == null) {
                    try {
                        lock.wait();
                    } catch (InterruptedException x) {
                        interrupted = true;
                    }
                }
                element = null;
                lock.notifyAll();
            }
            if (interrupted)
                Thread.currentThread().interrupt();
            return e;
        }
    }
}
