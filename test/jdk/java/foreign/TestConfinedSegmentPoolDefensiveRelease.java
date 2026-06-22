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
 * @modules java.base/jdk.internal.foreign
 * @library /test/lib
 * @run junit/othervm --add-opens=java.base/java.lang=ALL-UNNAMED
 *                    --add-opens=java.base/jdk.internal.foreign=ALL-UNNAMED
 *                    TestConfinedSegmentPoolDefensiveRelease
 * @run junit/othervm --add-opens=java.base/java.lang=ALL-UNNAMED
 *                    --add-opens=java.base/jdk.internal.foreign=ALL-UNNAMED
 *                    -Djava.lang.foreign.native.confined.pool.power.size=0
 *                    TestConfinedSegmentPoolDefensiveRelease
 * @run junit/othervm --add-opens=java.base/java.lang=ALL-UNNAMED
 *                    --add-opens=java.base/jdk.internal.foreign=ALL-UNNAMED
 *                    -Djava.lang.foreign.native.confined.pool.power.size=1
 *                    TestConfinedSegmentPoolDefensiveRelease
 * @run junit/othervm --add-opens=java.base/java.lang=ALL-UNNAMED
 *                    --add-opens=java.base/jdk.internal.foreign=ALL-UNNAMED
 *                    -Djava.lang.foreign.native.confined.pool.power.size=2
 *                    TestConfinedSegmentPoolDefensiveRelease
 * @run junit/othervm --add-opens=java.base/java.lang=ALL-UNNAMED
 *                    --add-opens=java.base/jdk.internal.foreign=ALL-UNNAMED
 *                    -Djava.lang.foreign.native.confined.pool.power.size=3
 *                    TestConfinedSegmentPoolDefensiveRelease
 * @run junit/othervm --add-opens=java.base/java.lang=ALL-UNNAMED
 *                    --add-opens=java.base/jdk.internal.foreign=ALL-UNNAMED
 *                    -Djava.lang.foreign.native.confined.pool.power.size=4
 *                    TestConfinedSegmentPoolDefensiveRelease
 * @run junit/othervm --add-opens=java.base/java.lang=ALL-UNNAMED
 *                    --add-opens=java.base/jdk.internal.foreign=ALL-UNNAMED
 *                    -Djava.lang.foreign.native.confined.pool.power.size=5
 *                    TestConfinedSegmentPoolDefensiveRelease
 * @run junit/othervm --add-opens=java.base/java.lang=ALL-UNNAMED
 *                    --add-opens=java.base/jdk.internal.foreign=ALL-UNNAMED
 *                    -Djava.lang.foreign.native.confined.pool.power.size=6
 *                    TestConfinedSegmentPoolDefensiveRelease
 * @run junit/othervm --add-opens=java.base/java.lang=ALL-UNNAMED
 *                    --add-opens=java.base/jdk.internal.foreign=ALL-UNNAMED
 *                    -Djava.lang.foreign.native.confined.pool.power.size=7
 *                    TestConfinedSegmentPoolDefensiveRelease
 * @run junit/othervm --add-opens=java.base/java.lang=ALL-UNNAMED
 *                    --add-opens=java.base/jdk.internal.foreign=ALL-UNNAMED
 *                    -Djava.lang.foreign.native.confined.pool.power.size=-1
 *                    TestConfinedSegmentPoolDefensiveRelease
 * @run junit/othervm --add-opens=java.base/java.lang=ALL-UNNAMED
 *                    --add-opens=java.base/jdk.internal.foreign=ALL-UNNAMED
 *                    -Djava.lang.foreign.native.confined.pool.power.size=23847682736221
 *                    TestConfinedSegmentPoolDefensiveRelease
 * @run junit/othervm --add-opens=java.base/java.lang=ALL-UNNAMED
 *                    --add-opens=java.base/jdk.internal.foreign=ALL-UNNAMED
 *                    -Djava.lang.foreign.native.confined.pool.power.size=TEXT
 *                    TestConfinedSegmentPoolDefensiveRelease
 * @run junit/othervm --add-opens=java.base/java.lang=ALL-UNNAMED
 *                    --add-opens=java.base/jdk.internal.foreign=ALL-UNNAMED
 *                    -Dsun.nio.PageAlignDirectMemory=true
 *                    -Djava.lang.foreign.native.confined.pool.power.size=PAGE_ALIGN
 *                    TestConfinedSegmentPoolDefensiveRelease
 */

import jdk.internal.foreign.ConfinedSegmentPool;
import jdk.test.lib.thread.VThreadRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.IllegalStateException;
import java.lang.foreign.Arena;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.junit.Assert.assertThrows;

final class TestConfinedSegmentPoolDefensiveRelease {

    static final long SIZE = 42;
    static final long OUT_OF_SIZE = 1_024;
    static final Method RELEASE = releaseMethod();

    @ParameterizedTest
    @MethodSource("threadFactories")
    void releaseWithNoPreviousAcquire(String name, Thread.Builder threadBuilder) throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread untouchedThread = threadBuilder.factory().newThread(() -> {
            try {
                assertThrows(IllegalStateException.class, () -> release(Thread.currentThread(), SIZE));
            } catch (Throwable throwable) {
                failure.set(throwable);
            }
        });
        untouchedThread.start();
        untouchedThread.join();
        if (failure.get() != null) {
            // Expose any exception from the thread.
            throw new AssertionError(name, failure.get());
        }
    }

    static Stream<Arguments> threadFactories() {
        return Stream.of(
                Arguments.of("platform", Thread.ofPlatform()),
                Arguments.of("virtual", Thread.ofVirtual()));
    }

    @Test
    void releaseAfterRelease() {
        try (Arena arena = Arena.ofConfined()){
            arena.allocate(1);
        }
        assertThrows(IllegalStateException.class, () -> release(Thread.currentThread(), SIZE));
    }

    @Test
    void releaseAfterReleaseVt() {
        VThreadRunner.run(this::releaseAfterRelease);
    }

    @Test
    void releaseIllegalSize() {
        // Only test with pooling enabled
        if (ConfinedSegmentPool.pooledMemorySize() > 0) {
            try (Arena arena = Arena.ofConfined()) {
                arena.allocate(1);
                assertThrows(AssertionError.class, () -> release(Thread.currentThread(), OUT_OF_SIZE));
            }
        }
    }

    @Test
    void releaseIllegalSizeVt() {
        VThreadRunner.run(this::releaseIllegalSize);
    }

    static void release(Thread thread, long size) throws Throwable {
        try {
            RELEASE.invoke(null, thread, size);
        } catch (InvocationTargetException ex) {
            throw ex.getCause();
        }
    }

    static Method releaseMethod() {
        try {
            Method method = ConfinedSegmentPool.class.getDeclaredMethod("release", Thread.class, long.class);
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

}
