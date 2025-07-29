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

/// Indicates that the static initializer of this class or interface
/// (its `<clinit>` method) is allowed to be _AOT-initialized_,
/// because its author considers it safe to execute during the AOT
/// assembly phase.
///
/// This annotation directs the VM to expect that normal execution of Java code
/// during the assembly phase could trigger initialization of this class,
/// and if that happens, to store the resulting static field values in the
/// AOT cache.  (These fields happen to be allocated in the `Class` mirror.)
///
/// During the production run, the static initializer (`<clinit>`) of
/// this class or interface will not be executed, if it was already
/// executed during the assembling of the AOT being used to start the
/// production run.  In that case the resulting static field states
/// (within the `Class` mirror) were already stored in the AOT cache.
///
/// Currently, this annotation is used mainly for supporting AOT
/// linking of APIs, including bootstrap methods, in the
/// `java.lang.invoke` package.
///
/// In more detail, the AOT assembly phase performs the following:
///
/// 1. It loads and links (but does not initialize) the classes that were loaded
///    during the application's training run.
/// 2. During linking of these classes, it resolves their constant pool
///    entries, when it is safe and beneficial to do so.
/// 3. As part of those resolutions, bootstrap methods may be called and may
///    create graphs of Java objects to support linkage states.
/// 4. Every object within those graphs must have its class AOT-initialized,
///    along with every relevant superclass and implemented interface, along
///    with classes for every object created during the course of static
///    initialization (running `<clinit>` for each such class or interface).
///
/// Thus, in order to determine that a class or interface _X_ is safe to
/// AOT-initialize requires evaluating every other class or interface _Y_ that
/// the `<clinit>` of _X_ will initialize (during AOT cache assembly), and
/// ensuring that each such _Y_ is (recursively) safe to AOT-initialize.
///
/// For example, an AOT-resolved constant pool entry for an invokedynamic or
/// invokehandle bytecode can have direct or indirect references to Java objects.
/// To ensure the correctness of the AOT-resolved constant pool entrties, the VM
/// must AOT-initialize the classes of such Java objects.
///
/// In addition, such Java objects may have references to static fields whose
/// object identity is important. For example, `PrimitiveClassDescImpl::CD_void`.
/// To ensure correctness, we must also store classes like `PrimitiveClassDescImpl`
/// in the initialized state. The VM requires implementor to manually annotate
/// such classes with `@AOTSafeClassInitializer`.
///
/// There is one more requirement for a class to be safe for
/// AOT initialization, and that is compatibility with all eventual production
/// runs.  The state of an AOT-initialized class _X_ must not contain any data
/// (anything reachable from _X_) that is incompatible with the eventual
/// production run.
///
/// In general, if some sort of computed datum, environmental setting, or
/// variable behavior may differ between the AOT assembly phase and the
/// production run, it may not be immutably bound to _X_, if _X_ is to be
/// marked AOT-initialized.  Here are specific examples:
///
///  - The value of an environment string (if it may differ in the production run).
///
///  - A transient configuration parameter specific to this VM run, such as
///    wall clock time, process ID, host name, temporary directory names, etc.
///
///  - A random seed or key that may need to be re-sampled at production
///    startup.
///
/// What is more, if the initialization of _X_ computes with some value _V_
/// obtained from some other class _Y_, _Y_ should also be safe for AOT
/// initialization, if there is any way for _X_ to detect a mismatch between
/// the version of _V_ produced at AOT time, and the version of _V_ produced in
/// the production run.  Specifically, if _V_ has an object identity, _X_
/// should not test that identity (compare it against another or get its
/// hashcode) unless _Y_ is also marked for AOT initialization.
///
/// Thus, to support AOT-time linkage, a class _X_ should be marked for (possible)
/// AOT initialization whenever objects it creates (such as `MethodHandle`s)
/// may be required to execute a `java.lang.invoke` API request, or (more
/// remotely) if the execution of such an API touches _X_ for initialization,
/// or even if such an API request is in any way sensitive to values stored in
/// the fields of _X_, even if the sensitivity is a simple reference identity
/// test.  As noted above, all supertypes of _X_ must also have the
/// `@AOTSafeClassInitializer` annotation, and must also be safe for AOT
/// initialization.
///
/// The author of an AOT-initialized class may elect to patch some states at
/// production startup, using an [AOTRuntimeSetup] method, as long as the
/// pre-patched field values (present during AOT assembly) are determined to be
/// compatible with the post-patched values that apply to the production run.
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
