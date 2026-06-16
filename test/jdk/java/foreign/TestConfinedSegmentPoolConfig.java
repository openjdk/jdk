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
 *                    TestConfinedSegmentPoolConfig
 * @run junit/othervm --add-opens=java.base/java.lang=ALL-UNNAMED
 *                    -Djava.lang.foreign.native.confined.pool.power.size=0
 *                    TestConfinedSegmentPoolConfig
 * @run junit/othervm --add-opens=java.base/java.lang=ALL-UNNAMED
 *                    -Djava.lang.foreign.native.confined.pool.power.size=1
 *                    TestConfinedSegmentPoolConfig
 * @run junit/othervm --add-opens=java.base/java.lang=ALL-UNNAMED
 *                    -Djava.lang.foreign.native.confined.pool.power.size=2
 *                    TestConfinedSegmentPoolConfig
 * @run junit/othervm --add-opens=java.base/java.lang=ALL-UNNAMED
 *                    -Djava.lang.foreign.native.confined.pool.power.size=3
 *                    TestConfinedSegmentPoolConfig
 * @run junit/othervm --add-opens=java.base/java.lang=ALL-UNNAMED
 *                    -Djava.lang.foreign.native.confined.pool.power.size=4
 *                    TestConfinedSegmentPoolConfig
 * @run junit/othervm --add-opens=java.base/java.lang=ALL-UNNAMED
 *                    -Djava.lang.foreign.native.confined.pool.power.size=5
 *                    TestConfinedSegmentPoolConfig
 * @run junit/othervm --add-opens=java.base/java.lang=ALL-UNNAMED
 *                    -Djava.lang.foreign.native.confined.pool.power.size=6
 *                    TestConfinedSegmentPoolConfig
 * @run junit/othervm --add-opens=java.base/java.lang=ALL-UNNAMED
 *                    -Djava.lang.foreign.native.confined.pool.power.size=7
 *                    TestConfinedSegmentPoolConfig
 * @run junit/othervm --add-opens=java.base/java.lang=ALL-UNNAMED
 *                    -Djava.lang.foreign.native.confined.pool.power.size=-1
 *                    TestConfinedSegmentPoolConfig
 * @run junit/othervm --add-opens=java.base/java.lang=ALL-UNNAMED
 *                    -Djava.lang.foreign.native.confined.pool.power.size=23847682736221
 *                    TestConfinedSegmentPoolConfig
 * @run junit/othervm --add-opens=java.base/java.lang=ALL-UNNAMED
 *                    -Djava.lang.foreign.native.confined.pool.power.size=TEXT
 *                    TestConfinedSegmentPoolConfig
 * @run junit/othervm --add-opens=java.base/java.lang=ALL-UNNAMED
 *                    -Dsun.nio.PageAlignDirectMemory=true
 *                    -Djava.lang.foreign.native.confined.pool.power.size=PAGE_ALIGN
 *                    TestConfinedSegmentPoolConfig
 */

import jdk.internal.access.JavaLangAccess;
import jdk.internal.access.SharedSecrets;
import org.junit.jupiter.api.Test;

import static org.junit.Assert.assertEquals;

final class TestConfinedSegmentPoolConfig {

    static final long MAX = 64;
    static final long DEFAULT = 64;
    static final long MIN = 8;
    static final long DISABLED = -1;

    static final JavaLangAccess JLA = SharedSecrets.getJavaLangAccess();
    static final String POOLED_MEMORY_PROPERTY = "java.lang.foreign.native.confined.pool.power.size";

    @Test
    void pooledMemorySize() {
        final long actual = JLA.pooledMemorySize();
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
            case "-1"             -> DISABLED;
            case "23847682736221" -> MAX;
            case "TEXT"           -> DEFAULT;  // Covers the case of a non-number
            case "PAGE_ALIGN"     -> DISABLED; // The text is used only as a flag
            default -> throw new AssertionError(configParameter + " -> " + actual);
        };
        assertEquals(expected, actual);
    }

}
