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
import java.lang.invoke.*;
import java.lang.reflect.Field;

/// Indicates all instance final fields in the annotated class should be trusted
/// as constants by compilers in `ciField::is_constant`.
///
/// The compiler already treats static final fields and instance final fields in
/// record classes and hidden classes as constant.  All classes in select
/// packages (Defined in `trust_final_non_static_fields` in `ciField.cpp`) in
/// the boot class loader also have their instance final fields trusted.
///
/// The [Stable] annotation treats fields as constants once they are not the
/// zero or null value.  In comparison, a final instance field trusted by this
/// annotation can trust zero and null values.
///
/// As a result, this should be used on classes where package-wide trusting is
/// not possible due to backward compatibility concerns, such as for `java.util`
/// classes.
///
/// A separate flag [Field#isTrustedFinal()] prevents core reflection from
/// modifying the value of a field via [Field#set].  ([VarHandle] already blocks
/// modification for all `final` fields, and [MethodHandle] setter for trusted
/// final fields can only be created with the `IMPL_LOOKUP`)  This is currently
/// handled by `fieldDescriptor::is_trusted_final`, distinct from the trusting
/// mechanism in the compiler.  This mechanism protects the static final fields
/// and instance final fields in record and hidden classes, but does not cover
/// stable fields, final fields in the classes in the select packages, or final
/// fields in classes specified by this annotation.
///
/// This annotation is only recognized on privileged code and is ignored elsewhere.
///
/// @since 26
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface TrustFinalFields {
}
