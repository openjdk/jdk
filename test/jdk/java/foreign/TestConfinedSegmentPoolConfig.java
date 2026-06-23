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
 * @run junit/othervm --add-opens=java.base/java.lang=ALL-UNNAMED
 *                    --add-opens=java.base/jdk.internal.foreign=ALL-UNNAMED
 *                    TestConfinedSegmentPoolConfig
 * @run junit/othervm --add-opens=java.base/java.lang=ALL-UNNAMED
 *                    --add-opens=java.base/jdk.internal.foreign=ALL-UNNAMED
 *                    -Djava.lang.foreign.native.confined.pool.power.size=0
 *                    -Djava.lang.foreign.native.confined.pool.power.slots=0
 *                    TestConfinedSegmentPoolConfig
 * @run junit/othervm --add-opens=java.base/java.lang=ALL-UNNAMED
 *                    --add-opens=java.base/jdk.internal.foreign=ALL-UNNAMED
 *                    -Djava.lang.foreign.native.confined.pool.power.size=1
 *                    -Djava.lang.foreign.native.confined.pool.power.slots=1
 *                    TestConfinedSegmentPoolConfig
 * @run junit/othervm --add-opens=java.base/java.lang=ALL-UNNAMED
 *                    --add-opens=java.base/jdk.internal.foreign=ALL-UNNAMED
 *                    -Djava.lang.foreign.native.confined.pool.power.size=2
 *                    -Djava.lang.foreign.native.confined.pool.power.slots=2
 *                    TestConfinedSegmentPoolConfig
 * @run junit/othervm --add-opens=java.base/java.lang=ALL-UNNAMED
 *                    --add-opens=java.base/jdk.internal.foreign=ALL-UNNAMED
 *                    -Djava.lang.foreign.native.confined.pool.power.size=3
 *                    -Djava.lang.foreign.native.confined.pool.power.slots=3
 *                    TestConfinedSegmentPoolConfig
 * @run junit/othervm --add-opens=java.base/java.lang=ALL-UNNAMED
 *                    --add-opens=java.base/jdk.internal.foreign=ALL-UNNAMED
 *                    -Djava.lang.foreign.native.confined.pool.power.size=4
 *                    -Djava.lang.foreign.native.confined.pool.power.slots=4
 *                    TestConfinedSegmentPoolConfig
 * @run junit/othervm --add-opens=java.base/java.lang=ALL-UNNAMED
 *                    --add-opens=java.base/jdk.internal.foreign=ALL-UNNAMED
 *                    -Djava.lang.foreign.native.confined.pool.power.size=5
 *                    -Djava.lang.foreign.native.confined.pool.power.slots=5
 *                    TestConfinedSegmentPoolConfig
 * @run junit/othervm --add-opens=java.base/java.lang=ALL-UNNAMED
 *                    --add-opens=java.base/jdk.internal.foreign=ALL-UNNAMED
 *                    -Djava.lang.foreign.native.confined.pool.power.size=6
 *                    -Djava.lang.foreign.native.confined.pool.power.slots=6
 *                    TestConfinedSegmentPoolConfig
 * @run junit/othervm --add-opens=java.base/java.lang=ALL-UNNAMED
 *                    --add-opens=java.base/jdk.internal.foreign=ALL-UNNAMED
 *                    -Djava.lang.foreign.native.confined.pool.power.size=7
 *                    -Djava.lang.foreign.native.confined.pool.power.slots=7
 *                    TestConfinedSegmentPoolConfig
 * @run junit/othervm --add-opens=java.base/java.lang=ALL-UNNAMED
 *                    --add-opens=java.base/jdk.internal.foreign=ALL-UNNAMED
 *                    -Djava.lang.foreign.native.confined.pool.power.size=24
 *                    -Djava.lang.foreign.native.confined.pool.power.slots=24
 *                    TestConfinedSegmentPoolConfig
 * @run junit/othervm --add-opens=java.base/java.lang=ALL-UNNAMED
 *                    --add-opens=java.base/jdk.internal.foreign=ALL-UNNAMED
 *                    -Djava.lang.foreign.native.confined.pool.power.size=-1
 *                    -Djava.lang.foreign.native.confined.pool.power.slots=-1
 *                    TestConfinedSegmentPoolConfig
 * @run junit/othervm --add-opens=java.base/java.lang=ALL-UNNAMED
 *                    --add-opens=java.base/jdk.internal.foreign=ALL-UNNAMED
 *                    -Djava.lang.foreign.native.confined.pool.power.size=23847682736221
 *                    -Djava.lang.foreign.native.confined.pool.power.slots=23847682736221
 *                    TestConfinedSegmentPoolConfig
 * @run junit/othervm --add-opens=java.base/java.lang=ALL-UNNAMED
 *                    --add-opens=java.base/jdk.internal.foreign=ALL-UNNAMED
 *                    -Djava.lang.foreign.native.confined.pool.power.size=TEXT
 *                    -Djava.lang.foreign.native.confined.pool.power.slots=TEXT
 *                    TestConfinedSegmentPoolConfig
 * @run junit/othervm --add-opens=java.base/java.lang=ALL-UNNAMED
 *                    --add-opens=java.base/jdk.internal.foreign=ALL-UNNAMED
 *                    -Dsun.nio.PageAlignDirectMemory=true
 *                    -Djava.lang.foreign.native.confined.pool.power.size=PAGE_ALIGN
 *                    TestConfinedSegmentPoolConfig
 */

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.Assert.assertEquals;

final class TestConfinedSegmentPoolConfig {

    static final long MAX = 64;
    static final long DEFAULT = 64;
    static final long MIN = 8;
    static final long DISABLED = -1;

    static final String POOLED_MEMORY_PROPERTY = "java.lang.foreign.native.confined.pool.power.size";
    static final String SLOT_COUNT_PROPERTY = "java.lang.foreign.native.confined.pool.power.slots";

    @Test
    void pooledMemorySize() {
        final long actual = getPooledMemorySize();;
        final String configParameter = System.getProperty(POOLED_MEMORY_PROPERTY);
        final long expected = switch (configParameter) {
            case null             -> DEFAULT;
            case "0"              -> DISABLED;
            case "1"              -> MIN;
            case "2"              -> MIN;
            case "3"              -> 1 << 3;
            case "4"              -> 1 << 4;
            case "5"              -> 1 << 5;
            case "6"              -> 1 << 6;
            case "7"              -> MAX;
            case "24"             -> MAX;
            case "-1"             -> DISABLED;
            case "23847682736221" -> DEFAULT;
            case "TEXT"           -> DEFAULT;  // Covers the case of a non-number
            case "PAGE_ALIGN"     -> DISABLED; // The text is used only as a flag
            default -> throw new AssertionError(configParameter + " -> " + actual);
        };
        assertEquals(expected, actual);
    }

    @Test
    void virtualThreadSlotCount() {
        final int actual = confinedSegmentPoolSlots();
        final String configParameter = System.getProperty(SLOT_COUNT_PROPERTY);
        final int expected = switch (configParameter) {
            case null             -> defaultSlotCount();
            case "0"              -> 1 << 1;   // clamped to min power
            case "1"              -> 1 << 1;
            case "2"              -> 1 << 2;
            case "3"              -> 1 << 3;
            case "4"              -> 1 << 4;
            case "5"              -> 1 << 5;
            case "6"              -> 1 << 6;
            case "7"              -> 1 << 7;
            case "24"             -> 1 << 20; // 20 is max
            case "-1"             -> 1 << 1;
            case "23847682736221" -> defaultSlotCount(); // Too big for an int
            case "TEXT"           -> defaultSlotCount(); // Covers the case of a non-number
            case "PAGE_ALIGN"     -> defaultSlotCount(); // The text is used only as a flag
            default -> throw new AssertionError(configParameter + " -> " + actual);
        };
        assertEquals(expected, actual);
    }

    static int defaultSlotCount() {
        int target = Runtime.getRuntime().availableProcessors() << 1;
        int power = Integer.SIZE - Integer.numberOfLeadingZeros(target - 1);
        return 1 << Math.clamp(power, 1, 20);
    }

    static int confinedSegmentPoolSlots() {
        try {
            Class<?> poolClass = Class.forName("jdk.internal.foreign.ConfinedSegmentPool$VirtualThreadPool");
            Field slotsField = poolClass.getDeclaredField("SLOTS");
            slotsField.setAccessible(true);
            return slotsField.getInt(null);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError(ex);
        }
    }

    static long getPooledMemorySize() {
        try {
            Class<?> poolClass = Class.forName("jdk.internal.foreign.ConfinedSegmentPool");
            return (long) poolClass.getMethod("pooledMemorySize").invoke(null);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError(ex);
        }
    }

}
