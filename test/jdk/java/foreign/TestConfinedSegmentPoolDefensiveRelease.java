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
 * @modules java.base/jdk.internal.access
 * @run junit/othervm --add-opens=java.base/java.lang=ALL-UNNAMED
 *                    TestConfinedSegmentPoolDefensiveRelease
 * @run junit/othervm --add-opens=java.base/java.lang=ALL-UNNAMED
 *                    -Djava.lang.foreign.native.confined.pool.power.size=0
 *                    TestConfinedSegmentPoolDefensiveRelease
 * @run junit/othervm --add-opens=java.base/java.lang=ALL-UNNAMED
 *                    -Djava.lang.foreign.native.confined.pool.power.size=1
 *                    TestConfinedSegmentPoolDefensiveRelease
 * @run junit/othervm --add-opens=java.base/java.lang=ALL-UNNAMED
 *                    -Djava.lang.foreign.native.confined.pool.power.size=2
 *                    TestConfinedSegmentPoolDefensiveRelease
 * @run junit/othervm --add-opens=java.base/java.lang=ALL-UNNAMED
 *                    -Djava.lang.foreign.native.confined.pool.power.size=3
 *                    TestConfinedSegmentPoolDefensiveRelease
 * @run junit/othervm --add-opens=java.base/java.lang=ALL-UNNAMED
 *                    -Djava.lang.foreign.native.confined.pool.power.size=4
 *                    TestConfinedSegmentPoolDefensiveRelease
 * @run junit/othervm --add-opens=java.base/java.lang=ALL-UNNAMED
 *                    -Djava.lang.foreign.native.confined.pool.power.size=5
 *                    TestConfinedSegmentPoolDefensiveRelease
 * @run junit/othervm --add-opens=java.base/java.lang=ALL-UNNAMED
 *                    -Djava.lang.foreign.native.confined.pool.power.size=6
 *                    TestConfinedSegmentPoolDefensiveRelease
 * @run junit/othervm --add-opens=java.base/java.lang=ALL-UNNAMED
 *                    -Djava.lang.foreign.native.confined.pool.power.size=7
 *                    TestConfinedSegmentPoolDefensiveRelease
 * @run junit/othervm --add-opens=java.base/java.lang=ALL-UNNAMED
 *                    -Djava.lang.foreign.native.confined.pool.power.size=-1
 *                    TestConfinedSegmentPoolDefensiveRelease
 *  * @run junit/othervm --add-opens=java.base/java.lang=ALL-UNNAMED
 *                    -Djava.lang.foreign.native.confined.pool.power.size=23847682736221
 *                    TestConfinedSegmentPoolDefensiveRelease
 *  * @run junit/othervm --add-opens=java.base/java.lang=ALL-UNNAMED
 *                    -Djava.lang.foreign.native.confined.pool.power.size=TEXT
 *                    TestConfinedSegmentPoolDefensiveRelease
 *  * @run junit/othervm --add-opens=java.base/java.lang=ALL-UNNAMED
 *                    -Dsun.nio.PageAlignDirectMemory=true
 *                    -Djava.lang.foreign.native.confined.pool.power.size=PAGE_ALIGN
 *                    TestConfinedSegmentPoolDefensiveRelease
 */

import jdk.internal.access.JavaLangAccess;
import jdk.internal.access.SharedSecrets;
import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

final class TestConfinedSegmentPoolDefensiveRelease {

    static final JavaLangAccess JLA = SharedSecrets.getJavaLangAccess();
    static final long SIZE = 42;
    static final long OUT_OF_SIZE = 1024;

    @Test
    void releaseWithNoPreviousAcquire() {
        Thread untouchedThread = new Thread(() -> {});
        assertThrows(IllegalStateException.class, () -> JLA.releaseAndZeroOutPooledMemory(untouchedThread, SIZE));
    }

    @Test
    void releaseAfterRelease() {
        try (Arena arena = Arena.ofConfined()){
            arena.allocate(1);
        }
        assertThrows(IllegalStateException.class, () -> JLA.releaseAndZeroOutPooledMemory(Thread.currentThread(), SIZE));
    }

    @Test
    void releaseIllegalSize() {
        // Only test with pooling enabled
        if (JLA.pooledMemorySize() > 0) {
            try (Arena arena = Arena.ofConfined()) {
                arena.allocate(1);
                assertThrows(AssertionError.class, () -> JLA.releaseAndZeroOutPooledMemory(Thread.currentThread(), OUT_OF_SIZE));
            }
        }
    }

}
