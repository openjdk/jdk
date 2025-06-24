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

/// Indicates a class or interface that should have its static initializer
/// (`<clinit>`) executed whenever it is referenced in an AOT cache.
///
/// In AOT assembly run, an object graph from metaspace to heap objects is
/// constructed.  When an object is in the heap, its class must be initialized.
/// However, class initialization may have dependencies on other classes in the
/// initializer that won't be initialized because they do not have live objects.
/// For example, `MethodHandles.IMPL_NAMES` is copied to
/// `DirectMethodHandle.IMPL_NAMES`, but there is no object relationship from
/// DMH to MHs, therefore we need to mark MethodHandles as AOTCI so it is
/// consistently initialized when it is ever referenced.
///
/// This annotation is only recognized on privileged code and is ignored
/// elsewhere.
///
/// @since 26
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface AOTClassInitializer {
}
