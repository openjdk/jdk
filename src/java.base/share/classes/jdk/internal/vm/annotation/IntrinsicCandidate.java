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
 * recognized by {@code vmIntrinsics.hpp} and may be subject to intrinsification
 * by the HotSpot VM.  (See {@code LibraryCallKit::try_to_inline} in {@code
 * library_call.cpp} for logic that checks if an intrinsic is available and
 * applicable at a given call site.)  Intrinsification replaces a candidate
 * method's body, bytecode or native, with handwritten platform assembly and/or
 * compiler IR.  Many Java library methods have properties that cannot be
 * deduced by the compiler for optimization, or can utilize specific hardware
 * instructions not modeled by the compiler IR, making intrinsics necessary.
 * <p>
 * Intrinsification may never happen, or happen at any moment during execution.
 * For example, the bytecodes of a candidate method may be executed by lower
 * compilation tiers of VM execution, while higher compilation tiers may replace
 * the bytecodes with specialized assembly code and/or compiler IR.  Therefore,
 * intrinsic implementors must ensure that non-bytecode execution has the same
 * results as execution of the actual Java code in all application contexts
 * (given the assumptions on the arguments hold).
 * <p>
 * A candidate method should contain a minimal piece of Java code that should be
 * replaced by an intrinsic wholesale.  The backing intrinsic is (in the common
 * case) <strong>unsafe</strong> - they do not perform checks, but have
 * assumptions on their arguments that can ensure type safety.  These
 * assumptions must be clearly documented on the candidate methods, and the
 * callers are fully responsible for preventing any kind of type safety
 * violation.  As long as these assumptions hold, readers can simply refer to
 * the candidate method's Java code body for program behaviors.
 * <blockquote style="font-size:smaller;"><p id="unsafe-details">
 * Examples of type safety violations include: dereferencing a null pointer;
 * accessing a field or method on an object which does not possess that field or
 * method; accessing an element of an array not actually present in the array;
 * and manipulating managed references in a way that prevents the GC from
 * managing them.
 * </blockquote>
 * <p id="validation">
 * To ensure type safety, candidate methods are typically private, and access to
 * them are encapsulated by more accessible methods that perform argument
 * validations.  Any validation must be done on values that are exclusively
 * accessed by the current thread: shared fields must be read into local
 * variables, and shared arrays must be copied to an exclusive copy, to ensure
 * each shared location (a field or an array component) is accessed exactly once.
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
 * </blockquote><p>
 * The HotSpot VM checks, when loading a class, the consistency of recognized
 * methods and {@code @IntrinsicCandidate} annotations, unless the {@code
 * CheckIntrinsics} VM flag is disabled.
 *
 * @since 16
 */
@Target({ElementType.METHOD, ElementType.CONSTRUCTOR})
@Retention(RetentionPolicy.RUNTIME)
public @interface IntrinsicCandidate {
}
