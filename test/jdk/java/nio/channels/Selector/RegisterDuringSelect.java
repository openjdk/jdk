/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @bug 8201315
 * @summary Test that channels can be registered, interest ops can changed,
 *          and keys cancelled while a selection operation is in progress.
 */

import java.io.IOException;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.Pipe;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Phaser;

public class RegisterDuringSelect {

    static Callable<Void> selectLoop(Selector sel, Phaser barrier) {
        return new Callable<Void>() {
            @Override
            public Void call() throws IOException {
                for (;;) {
                    try {
                        sel.select();
                    } catch (ClosedSelectorException ignore) {
                        return null;
                    }
                    if (sel.isOpen()) {
                        barrier.arriveAndAwaitAdvance();
                        System.out.println("selectLoop advanced ...");
                    } else {
                        // closed
                        return null;
                    }
                }
            }
        };
    }
    /**
     * Invoke register, interestOps, and cancel concurrently with a thread
     * doing a selection operation
     */
    public static void main(String args[]) throws Exception {
        Future<Void> result;

        ExecutorService pool = Executors.newFixedThreadPool(1);
        try (Selector sel = Selector.open()) {
            Phaser barrier = new Phaser(2);

            // submit task to do the select loop
            result = pool.submit(selectLoop(sel, barrier));

            Pipe p = Pipe.open();
            try {
                Pipe.SourceChannel sc = p.source();
                sc.configureBlocking(false);

                System.out.println("register ...");
                SelectionKey key = sc.register(sel, SelectionKey.OP_READ);
                if (!sel.keys().contains(key))
                    throw new RuntimeException("key not in key set");
                sel.wakeup();
                barrier.arriveAndAwaitAdvance();

                System.out.println("interestOps ...");
                key.interestOps(0);
                sel.wakeup();
                barrier.arriveAndAwaitAdvance();

                System.out.println("cancel ...");
                key.cancel();
                sel.wakeup();
                barrier.arriveAndAwaitAdvance();
                if (sel.keys().contains(key))
                    throw new RuntimeException("key not removed from key set");

            } finally {
                p.source().close();
                p.sink().close();
            }

        } finally {
            pool.shutdown();
        }

        // ensure selectLoop completes without exception
        result.get();

    }
}

