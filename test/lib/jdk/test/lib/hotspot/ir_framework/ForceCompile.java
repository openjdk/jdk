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

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Force a compilation of the annotated <b>helper method</b> (not specifying {@link Test @Test},
 * {@link Check @Check} or {@link Test @Run}) immediately at the specified level:
 * <ul>
 *     <li><p>{@link CompLevel#ANY} (default): Highest available compilation level is selected which is usually
 *            {@link CompLevel#C2}</li>
 *     <li><p>{@link CompLevel#C1}: Level 1: C1 compilation without any profile information.</li>
 *     <li><p>{@link CompLevel#C1_LIMITED_PROFILE}: Level 2: C1 compilation with limited profile information:
 *            Includes Invocation and backedge counters.</li>
 *     <li><p>{@link CompLevel#C1_FULL_PROFILE}: Level 3: C1 compilation with full profile information:
 *            Includes Invocation and backedge counters with MDO.</li>
 *     <li><p>{@link CompLevel#C2}: Level 4: C2 compilation with full optimizations.</li>
 *     <li><p>{@link CompLevel#SKIP}: Does not apply to {@code @ForceCompile} and results in a
 *            {@link TestFormatException}.</li>
 *     <li><p>{@link CompLevel#WAIT_FOR_COMPILATION}: Does not apply to {@code @ForceCompile} and results in a
 *            {@link TestFormatException}.</li>
 * </ul>
 * <p>
 *  Using this annotation on <i>non-helper</i> methods results in a {@link TestFormatException}.
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface ForceCompile {
    /**
     * The compilation level to compile the helper method at.
     */
    CompLevel value() default CompLevel.ANY;
}
