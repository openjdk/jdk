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

/// Indicates that if the enclosing class or interface is present in the AOT
/// cache in the AOT-initialized state, the annotated method must be executed
/// before bootstrap phase 3 (that is, before [System#initPhase3]).
///
/// The annotated method must be declared `private` and `static`, must be named
/// `runtimeSetup`, and must have no arguments or return value.  The enclosing
/// class must be annotated with [AOTSafeClassInitializer], meaning that it is
/// allowed to be stored in the AOT-initialized state.
///
/// The annotated method will be executed if and only if the class was loaded
/// in the AOT-initialized state from the AOT cache.
///
/// The author of the class is responsible for deciding whether some or all of
/// a class's initialization state should be re-initialized in any way.  In all
/// cases, the static initializer (`<clinit>` method) of any given class or
/// interface is run at most once, either in the assembly phase (only for an
/// AOT-initialized class) or in the production run.
///
/// After a `static` `final` field is assigned a value in an AOT-initialized
/// class, its value may never be changed, as such values are always immutable
/// runtime constants.  (...Barring `System.out` and its two siblings.)
/// Rarely, a `static` field may require differing values in the assembly phase
/// for an AOT cache, and for the production run.  Such variables must be
/// marked non-`final`, and should be adjusted by the `runtimeSetup` method.
/// Full constant folding (as if with a `final` field) may usually be recovered
/// by also marking the field as [Stable].  That annotation instructs the JIT
/// to perform constant folding, and _only_ during the production run, after
/// `runtimeSetup` has had a chance to give the field its "finally final"
/// value.
///
/// A related method is `resetArchivedStates`, which allows special handling of
/// an AOT-initialized class, at the end of the assembly phase run which builds
/// an AOT cache.  The `resetArchivedStates` may "tear down" state that should
/// not be stored in the AOT cache, which the `runtimeSetup` method may then
/// "build up again" as the production run begins.  This additional method is
/// currently only used by [Class] to reset a cache field, but it may be
/// expanded to other classes and interfaces later on, using more
/// annotation-driven logic.
///
/// The logic in `classFileParser.cpp` performs checks on the annotated method: If the
/// annotated method's signature differs from that described above, or if (during the
/// assembly phase) the class is not marked to have an AOT-safe initializer, a
/// [ClassFormatError] will be thrown.
///
/// This annotation is only recognized on privileged code and is ignored elsewhere.
///
/// @since 26
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface AOTRuntimeSetup {
}
