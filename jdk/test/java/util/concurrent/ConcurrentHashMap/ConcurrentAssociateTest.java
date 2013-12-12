/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * @test
 * @bug 8028564
 * @run testng ConcurrentAssociateTest
 * @summary Test that association operations, such as put and compute,
 * place entries in the map
 */
@Test
public class ConcurrentAssociateTest {

    // The number of entries for each thread to place in a map
    private static final int N = Integer.getInteger("n", 128);
    // The number of iterations of the test
    private static final int I = Integer.getInteger("i", 256);

    // Object to be placed in the concurrent map
    static class X {
        // Limit the hash code to trigger collisions
        int hc = ThreadLocalRandom.current().nextInt(1, 9);

        public int hashCode() { return hc; }
    }

    @Test
    public void testPut() {
        test("CHM.put", (m, o) -> m.put(o, o));
    }

    @Test
    public void testCompute() {
        test("CHM.compute", (m, o) -> m.compute(o, (k, v) -> o));
    }

    @Test
    public void testComputeIfAbsent() {
        test("CHM.computeIfAbsent", (m, o) -> m.computeIfAbsent(o, (k) -> o));
    }

    @Test
    public void testMerge() {
        test("CHM.merge", (m, o) -> m.merge(o, o, (v1, v2) -> v1));
    }

    @Test
    public void testPutAll() {
        test("CHM.putAll", (m, o) -> {
            Map<Object, Object> hm = new HashMap<>();
            hm.put(o, o);
            m.putAll(hm);
        });
    }

    private static void test(String desc, BiConsumer<ConcurrentMap<Object, Object>, Object> associator) {
        for (int i = 0; i < I; i++) {
            testOnce(desc, associator);
        }
    }

    static class AssociationFailure extends RuntimeException {
        AssociationFailure(String message) {
            super(message);
        }
    }

    private static void testOnce(String desc, BiConsumer<ConcurrentMap<Object, Object>, Object> associator) {
        ConcurrentHashMap<Object, Object> m = new ConcurrentHashMap<>();
        CountDownLatch s = new CountDownLatch(1);

        Supplier<Runnable> sr = () -> () -> {
            try {
                s.await();
            }
            catch (InterruptedException e) {
            }

            for (int i = 0; i < N; i++) {
                Object o = new X();
                associator.accept(m, o);
                if (!m.containsKey(o)) {
                    throw new AssociationFailure(desc + " failed: entry does not exist");
                }
            }
        };

        int ps = Runtime.getRuntime().availableProcessors();
        Stream<CompletableFuture> runners = IntStream.range(0, ps)
                .mapToObj(i -> sr.get())
                .map(CompletableFuture::runAsync);

        CompletableFuture all = CompletableFuture.allOf(
                runners.toArray(CompletableFuture[]::new));

        // Trigger the runners to start associating
        s.countDown();
        try {
            all.join();
        } catch (CompletionException e) {
            Throwable t = e.getCause();
            if (t instanceof AssociationFailure) {
                throw (AssociationFailure) t;
            }
            else {
                throw e;
            }
        }
    }
}
