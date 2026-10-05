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

/*
 * @test
 * @bug 8379968
 * @summary Test jdk.internal.vm.ThreadSnapshot.of(Thread) when thread is exiting
 * @modules java.base/jdk.internal.vm
 * @run main/othervm ThreadExiting platform 100
 * @run main/othervm ThreadExiting virtual 100
 */

import java.time.Instant;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.Phaser;

import jdk.internal.vm.ThreadSnapshot;

public class ThreadExiting {
    static volatile Thread target;
    static volatile int creatorAtIteration = 1;

    public static void main(String[] args) throws Exception {
        ThreadFactory factory = switch (args[0]) {
            case "platform" -> Thread.ofPlatform().factory();
            case "virtual"  -> Thread.ofVirtual().factory();
            default         -> throw new RuntimeException("Unknown thread kind");
        };
        int iterations = Integer.parseInt(args[1]);

        Phaser sync = new Phaser(2);
        Thread threadCreator = factory.newThread(() -> {
            for (int i = 1; i <= iterations; i++) {
                target = factory.newThread(() -> {});
                sync.arriveAndAwaitAdvance();
                target.start();
                try {
                    target.join();
                } catch (InterruptedException ie) {}
                creatorAtIteration++;
            }
        });
        threadCreator.start();

        for (int i = 1; i <= iterations; i++) {
            System.out.format("%s %d of %d ...%n", Instant.now(), i, iterations);
            sync.arriveAndAwaitAdvance();
            while (creatorAtIteration == i) {
                ThreadSnapshot.of(target);
            }
        }
        threadCreator.join();
    }
}
