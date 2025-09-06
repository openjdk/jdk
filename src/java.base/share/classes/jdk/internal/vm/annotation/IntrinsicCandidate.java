/*
 * Copyright (c) 2015, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.annotation.*;

/**
 * The {@code @IntrinsicCandidate} indicates that an annotated method is
 * recognized by {@code vmIntrinsics.hpp} for special treatment by the HotSpot
 * VM.  When a class is loading, the HotSpot VM checks the consistency of
 * recognized methods and {@code @IntrinsicCandidate} annotations, unless the
 * {@code CheckIntrinsics} VM flag is disabled.
 *
 * <h2 id="intrinsification">Intrinsification</h2>
 * Most frequently, the special treatment of an intrinsic is
 * <em>intrinsification</em>, which replaces a candidate method's body, bytecode
 * or native, with handwritten platform assembly and/or compiler IR.  (See
 * {@code LibraryCallKit::try_to_inline} in {@code library_call.cpp} for logic
 * that checks if an intrinsic is available and applicable at a given call site.)
 * Many Java library methods have properties that cannot be deduced by the
 * compiler for optimization, or can utilize specific hardware instructions not
 * modeled by the compiler IR, making intrinsics necessary.
 * <p>
 * During execution, intrinsification may happen and may be rolled back at any
 * moment; this loading and unloading process may happen zero to many times.
 * For example, the bytecode of a candidate method may be executed by lower
 * compilation tiers of VM execution, while higher compilation tiers may replace
 * the bytecode with specialized assembly code and/or compiler IR.  Therefore,
 * intrinsic implementors must ensure that non-bytecode execution has the same
 * results as execution of the actual Java code in all application contexts
 * (given that the assumptions on the arguments hold).
 * <p>
 * A candidate method should contain a minimal piece of Java code that should be
 * replaced by an intrinsic wholesale.  The backing intrinsic is (in the common
 * case) <strong>unsafe</strong> - it may not perform checks, but instead makes
 * assumptions on its arguments that type safety is ensured by callers.  These
 * assumptions must be clearly documented on the candidate methods, and the
 * callers are fully responsible for preventing any kind of type safety
 * violation.  As long as these assumptions hold, readers can simply refer to
 * the candidate method's Java code body for program behaviors.
 * <blockquote style="font-size:smaller;"><p id="unsafe-details">
 * Examples of type safety violations include: dereferencing a null pointer;
 * accessing a field or method on an object which does not possess that field or
 * method; accessing an element of an array not actually present in the array;
 * and manipulating object references in a way that prevents the GC from
 * managing them.
 * </blockquote>
 * <p id="validation">
 * To ensure type safety, candidate methods are typically private, and access to
 * them are encapsulated by more accessible methods that perform argument
 * validations.  Any validation must be done on values that are exclusively
 * accessed by the current thread: shared fields must be read into local
 * variables, and shared arrays must be copied to an exclusive copy, to ensure
 * each shared location (a field or an array component) is accessed at most once.
 * If a shared location is read multiple times for check and for use, race
 * conditions may cause two reads to produce distinct values, known as TOCTOU
 * (time of check and time of use), and the read for use may produce an illegal
 * value.  Finally, the thread-exclusive validated values are passed to the
 * candidate method.
 * <blockquote style="font-size:smaller;"><p id="racy-array">
 * For some highly optimized algorithms, it may be impractical to ensure that
 * array data is read or written only once by the intrinsic.  If the caller of
 * the intrinsic cannot guarantee that such array data is unshared, then the
 * caller must also document the effects of any race condition.  (Such a race
 * occurs when another thread writes the array data during the execution of the
 * intrinsic.)  For example, the documentation can simply say that the result is
 * undefined if a race happens.  However, race conditions must not lead to
 * program failures or type safety breaches, as listed above.
 * <p>
 * Reasoning about such race conditions is difficult, but it is a necessary
 * skill when working with intrinsics that can observe racing shared variables.
 * One example of a tolerable race is a repeated read of a shared reference.
 * This only works if the algorithm takes no action based on the first read,
 * other than deciding to perform the second read; it must "forget what it saw"
 * in the first read.  This is why the array-mismatch intrinsics can sometimes
 * report a tentative search hit (maybe using vectorized code), which can then
 * be confirmed (by scalar code) as the caller makes a fresh and independent
 * observation.
 * </blockquote>
 *
 * @since 16
 */
@Target({ElementType.METHOD, ElementType.CONSTRUCTOR})
@Retention(RetentionPolicy.RUNTIME)
public @interface IntrinsicCandidate {
}
