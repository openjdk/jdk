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

/// The `@IntrinsicCandidate` indicates that an annotated method is
/// recognized by `vmIntrinsics.hpp` for special treatment by the HotSpot
/// VM.  When a class is loading, the HotSpot VM checks the consistency of
/// recognized methods and `@IntrinsicCandidate` annotations, unless the
/// `CheckIntrinsics` VM flag is disabled.
///
/// Most frequently, the special treatment of an intrinsic is *intrinsification*,
/// which replaces a candidate method's body, bytecode or native, with
/// handwritten platform assembly and/or compiler IR.  See
/// <a href="../intrinsics.md">intrinsics design document</a> for
/// what intrinsics are and cautions for working with annotated methods.
///
/// @since 16
@Target({ElementType.METHOD, ElementType.CONSTRUCTOR})
@Retention(RetentionPolicy.RUNTIME)
public @interface IntrinsicCandidate {
}
