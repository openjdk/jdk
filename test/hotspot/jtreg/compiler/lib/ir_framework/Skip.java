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
 * This annotation skips the execution of a {@link Test @Test}-annotated method completely. This is useful when the
 * {@link Test @Test}-method causes the VM to misbehave (crash, wrong execution etc.) with no immediately available fix.
 *
 * <p>
 * It is preferable to use {@link Skip @Skip} over commenting a test out because one can still execute the skipped
 * test by passing the property flag {@code -DIgnoreSkip=true}. This can be useful when trying to quickly
 * verify if a disabled test is still failing or not.
 *
 * <p>
 * The {@link Skip @Skip} annotation can only be used at {@link Test @Test}-annotated methods. If the
 * {@link Test @Test}-method is part of a custom run test, then the {@link Run @Run}-method is also not executed.
 * A special case is a {@link Run @Run}-annotated method with multiple associated {@link Test @Test}-methods. In this
 * case, you should either skip all or none to limit the impact. When only needing to disable one of them, consider
 * moving it to a separate custom run test with a 1-to-1 {@link Run @Run}-{@link Test @Test}-relation for minimum impact.
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface Skip {
}
