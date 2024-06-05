/*
 * Copyright (c) 2004, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8322732
 * @summary ForkJoinPool utilizes available workers even with arbitrary task dependencies
 */
import java.util.concurrent.Callable;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicInteger;

public class Starvation {
    static final AtomicInteger count = new AtomicInteger();
    static final Callable<Void> noop = new Callable<Void>() {
            public Void call() {
                return null; }};
    static final class AwaitCount implements Callable<Void> {
        private int c;
        AwaitCount(int c) { this.c = c; }
        public Void call() {
            while (count.get() == c) Thread.onSpinWait();
            return null; }};

    public static void main(String[] args) throws Exception {
        try (var pool = new ForkJoinPool(2)) {
            for (int i = 0; i < 100_000; i++) {
                var future1 = pool.submit(new AwaitCount(i));
                var future2 = pool.submit(noop);
                future2.get();
                count.set(i + 1);
                future1.get();
            }
        }
    }
}
