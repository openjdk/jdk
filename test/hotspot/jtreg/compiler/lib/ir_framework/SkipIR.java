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

package compiler.lib.ir_framework;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * This annotation skips the IR matching of an individual {@link Test @IR}-rule found at a {@link Test @Test}-method.
 * This is useful when the {@link Test @Test}-method causes the VM to emit an unexpected graph shape with no immediately
 * available fix. Note that this only restricts the IR matching and not the execution of the {@link Test @Test}-method
 * itself. To skip the entire execution, use the {@link Skip @Skip} annotation instead.
 *
 * <p>
 * It is preferable to use {@link SkipIR @SkipIR} over commenting an IR rule out because one can enable IR matching
 * of the skipped IR rule by passing the property flag {@code -DIgnoreSkip=true}. This can be useful when trying to
 * quickly verify if a disabled IR rule is still failing or not.
 *
 * <p>
 * The {@link SkipIR @SkipIR} annotation can only be used in combination with at least one {@link Test @IR}-rule at
 * a {@link Test @Test}-method. The {@link SkipIR#value()} parameter defines which IR rules should be disabled.
 * One (e.g. {@code @SkipIR(2)}) or multiple (e.g. {@code @SkipIR({3, 4})}) IR rules can be disabled by specifying the IR
 * rule number(s) as paremeter (note that the first IR rule is rule 1).
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface SkipIR {
    int[] value();
}
