/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
 * @modules java.base/jdk.internal.ref
 *          jdk.incubator.foreign/jdk.incubator.foreign
 * @run testng/othervm -Dforeign.restricted=permit TestCleaner
 */

import jdk.incubator.foreign.MemorySegment;
import java.lang.ref.Cleaner;
import jdk.internal.ref.CleanerFactory;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class TestCleaner {

    static class SegmentState {
        private AtomicInteger cleanupCalls = new AtomicInteger(0);

        void cleanup() {
            cleanupCalls.incrementAndGet();
        }

        int cleanupCalls() {
            return cleanupCalls.get();
        }
    }

    @Test(dataProvider = "cleaners")
    public void testAtMostOnce(RegisterKind registerKind, Supplier<Cleaner> cleanerFactory, SegmentFunction segmentFunction) {
        SegmentState segmentState = new SegmentState();
        MemorySegment root = MemorySegment.allocateNative(10).share();
        MemorySegment segment = root.address().asSegmentRestricted(10, () -> {
            root.close();
            segmentState.cleanup();
        }, null);

        if (registerKind == RegisterKind.BEFORE) {
            // register cleaners before
            segment = segment.registerCleaner(cleanerFactory.get());
        }

        kickGCAndCheck(segmentState, segment);

        segment = segmentFunction.apply(segment);

        kickGCAndCheck(segmentState, segment);

        if (segment.isAlive() && registerKind == RegisterKind.AFTER) {
            // register cleaners after
            segment = segment.registerCleaner(cleanerFactory.get());
        }

        kickGCAndCheck(segmentState, segment);
        segment = null;
        while (segmentState.cleanupCalls() == 0) {
            byte[] b = new byte[100];
            System.gc();
            try {
                Thread.sleep(10);
            } catch (InterruptedException ex) {
                throw new AssertionError(ex);
            }
        }
        assertEquals(segmentState.cleanupCalls(), 1);
    }

    private void kickGCAndCheck(SegmentState segmentState, MemorySegment segment) {
        for (int i = 0 ; i < 100 ; i++) {
            byte[] b = new byte[100];
            System.gc();
            Thread.onSpinWait();
        }
        //check that cleanup has not been called by any cleaner yet!
        assertEquals(segmentState.cleanupCalls(), segment.isAlive() ? 0 : 1);
    }

    @Test(dataProvider = "segmentFunctions")
    public void testBadDoubleRegister(Supplier<Cleaner> cleanerFactory, SegmentFunction segmentFunction) {
        MemorySegment segment = MemorySegment.allocateNative(10);
        segment = segment.registerCleaner(cleanerFactory.get());
        segment = segmentFunction.apply(segment);
        try {
            segment.registerCleaner(cleanerFactory.get()); // error here!
            fail();
        } catch (IllegalStateException ex) {
            if (!segment.isAlive()) {
                assertTrue(ex.getMessage().contains("This segment is already closed"));
            } else {
                assertTrue(ex.getMessage().contains("Already registered with a cleaner"));
            }
        }
    }

    enum SegmentFunction implements Function<MemorySegment, MemorySegment> {
        IDENTITY(Function.identity()),
        CLOSE(s -> { s.close(); return s; }),
        SHARE(s -> { return s.share(); });

        private final Function<MemorySegment, MemorySegment> segmentFunction;

        SegmentFunction(Function<MemorySegment, MemorySegment> segmentFunction) {
            this.segmentFunction = segmentFunction;
        }

        @Override
        public MemorySegment apply(MemorySegment segment) {
            return segmentFunction.apply(segment);
        }
    }

    @DataProvider
    static Object[][] segmentFunctions() {
        Supplier<?>[] cleaners = {
                (Supplier<Cleaner>)Cleaner::create,
                (Supplier<Cleaner>)CleanerFactory::cleaner
        };

        SegmentFunction[] segmentFunctions = SegmentFunction.values();
        Object[][] data = new Object[cleaners.length * segmentFunctions.length][3];

        for (int cleaner = 0 ; cleaner < cleaners.length ; cleaner++) {
            for (int segmentFunction = 0 ; segmentFunction < segmentFunctions.length ; segmentFunction++) {
                data[cleaner + (cleaners.length * segmentFunction)] =
                        new Object[] { cleaners[cleaner], segmentFunctions[segmentFunction] };
            }
        }

        return data;
    }

    enum RegisterKind {
        BEFORE,
        AFTER;
    }

    @DataProvider
    static Object[][] cleaners() {
        Supplier<?>[] cleaners = {
                (Supplier<Cleaner>)Cleaner::create,
                (Supplier<Cleaner>)CleanerFactory::cleaner
        };

        List<Object[]> data = new ArrayList<>();
        for (RegisterKind kind : RegisterKind.values()) {
            for (Object cleaner : cleaners) {
                for (SegmentFunction segmentFunction : SegmentFunction.values()) {
                    data.add(new Object[] {kind, cleaner, segmentFunction});
                }
            }
        }
        return data.toArray(Object[][]::new);
    }
}
