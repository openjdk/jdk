/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

package jdk.test.lib.hotspot.ir_framework;

import java.util.HashMap;
import java.util.Map;

/**
 * Compilation levels used by the framework. The compilation levels map to the used levels in HotSpot (apart from the
 * framework specific values {@link #SKIP} and {@link #WAIT_FOR_COMPILATION} that cannot be found in HotSpot).
 *
 * <p>
 * The compilation levels can be specified in the {@link Test}, {@link ForceCompile} and {@link DontCompile} annotation.
 *
 *
 * @see Test
 * @see ForceCompile
 * @see DontCompile
 */
public enum CompLevel {

    /**
     * Can only be used at {@link Test#compLevel()}. After the warm-up, the framework keeps invoking the test over a span
     * of 10s (configurable by setting the property flag {@code -DWaitForCompilationTimeout}) until HotSpot compiles the
     * {@link Test} method. If the method was not compiled after 10s, an exception is thrown. The framework does not wait
     * for the compilation if the test VM is run with {@code -Xcomp}, {@code -XX:-UseCompiler} or {@code -DStressCC=true}.
     */
    WAIT_FOR_COMPILATION(-4),
    /**
     * Can only be used at {@link Test#compLevel()}. Skip a compilation of the {@link Test @Test} method completely.
     */
    SKIP(-3),
    /**
     *  Use any compilation level depending on the usage:
     *  <ul>
     *      <li><p>{@link Test @Test}, {@link ForceCompile @ForceCompile}: Use the highest available compilation level
     *      which is usually C2.</li>
     *      <li><p>{@link DontCompile @DontCompile}: Prevents any compilation of the associated helper method.</li>
     *  </ul>
     */
    ANY(-2),
    /**
     *  Compilation level 1: C1 compilation without any profile information.
     */
    C1(1),
    /**
     *  Compilation level 2: C1 compilation with limited profile information: Includes Invocation and backedge counters.
     */
    C1_LIMITED_PROFILE(2),
    /**
     *  Compilation level 3: C1 compilation with full profile information: Includes Invocation and backedge counters with MDO.
     */
    C1_FULL_PROFILE(3),
    /**
     * Compilation level 4: C2 compilation with full optimizations.
     */
    C2(4);

    private static final Map<Integer, CompLevel> typesByValue = new HashMap<>();
    private final int value;

    static {
        for (CompLevel level : CompLevel.values()) {
            typesByValue.put(level.value, level);
        }
    }

    CompLevel(int level) {
        this.value = level;
    }

    /**
     * Get the compilation level as integer value. These will match the levels specified in HotSpot (if available).
     *
     * @return the compilation level as integer.
     */
    public int getValue() {
        return value;
    }

    /**
     * Get the compilation level enum from the specified integer.
     *
     * @param value the compilation level as integer.
     * @throws TestRunException if {@code value} does not specify a valid compilation level.
     * @return the compilation level enum for {@code value}.
     */
    public static CompLevel forValue(int value) {
        CompLevel level = typesByValue.get(value);
        TestRun.check(level != null, "Invalid compilation level " + value);
        return level;
    }

    /**
     * Checks if two compilation levels are overlapping.
     */
    static boolean overlapping(CompLevel l1, CompLevel l2) {
        return l1.isC1() == l2.isC1() || (l1 == C2 && l2 == C2);
    }

    static CompLevel join(CompLevel l1, CompLevel l2) {
        return switch (l1) {
            case ANY -> l2;
            case C1, C1_LIMITED_PROFILE, C1_FULL_PROFILE -> l2.isC1() || l2 == ANY ? C1 : SKIP;
            case C2 -> l2 == C2 || l2 == ANY ? C2 : SKIP;
            default -> SKIP;
        };
    }

    private boolean isC1() {
        return this == C1 || this == C1_LIMITED_PROFILE || this == C1_FULL_PROFILE;
    }
}
