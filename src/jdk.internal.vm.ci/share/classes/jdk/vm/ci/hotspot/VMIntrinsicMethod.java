/*
 * Copyright (c) 2016, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.vm.ci.hotspot;

import jdk.vm.ci.meta.Signature;

/**
 * Describes a method for which the VM has an intrinsic implementation.
 *
 * @param declaringClass the name of the class declaring the intrinsified method. The name is in class file format
 *                       (see JVMS {@jvms 4.2.1}). For example, {@code "java/lang/Thread"} instead of
 *                       {@code "java.lang.Thread"}.
 * @param name           the name of the intrinsified method. This is not guaranteed to be a legal method name (e.g.,
 *                       there is a HotSpot intrinsic with the name {@code "<compiledLambdaForm>"}).
 * @param descriptor     the {@link Signature#toMethodDescriptor() descriptor} of the intrinsified method. This is not
 *                       guaranteed to be a legal method descriptor (e.g., intrinsics for signature polymorphic
 *                       methods have a descriptor of {@code "*"}).
 * @param id             the unique VM identifier for the intrinsic.
 * @param isAvailable    this value reflects the `ControlIntrinsic`, `DisableIntrinsic` and `UseXXXIntrinsic` VM flags
 *                       as well as other factors such as the current CPU.
 * @param c1Supported    true if this intrinsic is supported by C1.
 * @param c2Supported    true if this intrinsic is supported by C2.
 */
public record VMIntrinsicMethod(String declaringClass,
                                String name,
                                String descriptor,
                                int id,
                                boolean isAvailable,
                                boolean c1Supported,
                                boolean c2Supported) {
}
