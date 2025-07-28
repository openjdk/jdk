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

/// Indicates that the static initializer of this class or interface is
/// considered "safe" for AOT assembly. I.e., if this class or interface
/// has been initialized in the AOT assembly phase, then this class or interface
/// can be safely stored in the AOT cache in the "initialized" state:
///
/// 1. During the production run, the static initializer of this class or
///    interface will not be executed.
/// 2. The values of the static fields of this class or interface will be the same
///    as their values at the end of the assembly phase.
///
/// Currently, this annotation is used only for supporting AOT linking of
/// java.lang.invoke primitives.
///
/// The AOT assembly phase performs the following:
///
/// 1. Load and link (but does not initialize) all classes that were loaded
///    during the application's training run.
/// 2. During linking of these classes, we resolve constant pool
///    entries when it's safe and beneficial to do so.
///
/// An AOT-resolved constant pool entry for an invokedynamic or invokehandle bytecode can
/// have direct or indirect references to Java objects. To ensure the correctness
/// of the AOT-resolved constant pool entrties, we store the classes of such Java objects
/// in the AOT cache in the initialized state (as described above).
///
/// However, such Java objects may have references to static fields whose object identity
/// is important. For example, `PrimitiveClassDescImpl::CD_void`. To ensure correctness,
/// we must also store classes like `PrimitiveClassDescImpl` in the initialized state.
/// We require the implementors of java.lang.invoke to manually annotate such classes with
/// `@AOTSafeClassInitializer`. This should be done when:
///
/// 1. It's possible for an artifact used in the linking java.lang.invoke primitives
///    (usually a MethodHandle) to directly or indirectly remember the value of a static
///    field in this class.
/// 2. You have validated that the static initializer of this class doesn't depend on
///    transient states (i.e., names of temporary directories) that cannot be carried over
///    to a future production run.
/// 3. All supertypes of this class must also have the `@AOTSafeClassInitializer`
///    annotation.
///
/// In the assembly phase, `classFileParser.cpp` performs checks on the annotated
/// classes, to ensure all supertypes of this class that must be initialized when
/// this class is initialized have the `@AOTSafeClassInitializer` annotation.
/// Otherwise, a [ClassFormatError] will be thrown. (This assembly phase restriction
/// allows module patching and instrumentation to work on annotated classes when
/// AOT is not enabled)
///
/// This annotation is only recognized on privileged code and is ignored elsewhere.
///
/// @since 26
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface AOTSafeClassInitializer {
}
