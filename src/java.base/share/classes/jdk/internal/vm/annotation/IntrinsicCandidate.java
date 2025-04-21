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
 * by the HotSpot VM (see {@code LibraryCallKit::try_to_inline} in {@code
 * library_call.cpp}) if an intrinsic is available. Intrinsification replaces a
 * method's body, bytecode or native, with hand-written platform assembly and/or
 * compiler IR that may have different but compatible semantics from the original.
 * <p>
 * Many Java library functions have properties that cannot be deduced by the
 * compiler for optimization, or can utilize specific hardware instructions not
 * modeled by the compiler IR, making intrinsics necessary. However, due to the
 * error-prone nature of platform assembly and compiler IR, intrinsics should be
 * kept minimal, like locking sections - whatever can be done in Java code
 * should be done in Java.
 * <p>
 * In best practice, intrinsic methods are usually private, unsafe, and
 * static, and their encapsulating callers should perform:
 * <ul>
 * <li>Heap access, such as field reads into local variables;</li>
 * <li>Validation, such as null checks, range checks, type checks,
 * on local variables;</li>
 * <li>All validated values should be in local variables instead of re-read from
 * fields to avoid TOCTOU races.</li>
 * </ul>
 * <p>
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
