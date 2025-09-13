/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package jdk.internal.vm.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/// This annotation is a stronger form of [AOTSafeClassInitializer].
/// It indicates that the annotated class _X_ should be stored in
/// the AOT cache in the initialized state.
///
/// This annotation takes effect only if _X_ is included in
/// an AOT cache (e.g., if _X_ was used during an AOT training run).
///
/// During the AOT assembly phase, after _X_ is loaded, it will be
/// proactively initialized by the JVM. Afterwards, _X_ will be
/// treated as if it had the [AOTSafeClassInitializer] annotation.
/// Please see [AOTSafeClassInitializer] for details.
///
/// The only difference between [AOTInitialize] and [AOTSafeClassInitializer]
/// is the former will proactively initialize the annotated class during
/// the AOT assembly phase, whereas the latter will not.
///
/// Before adding this annotation to a class _X_, the author must determine
/// that it's safe to execute the static analyzer of _X_ during the AOT
/// assembly phase. In addition, all supertypes of _X_ must also have this
/// annotation.
///
/// This annotation is only recognized on privileged code and is ignored elsewhere.
///
/// @since 26
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface AOTInitialize {
}
