/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

package java.lang.invoke;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates that a method or constructor is intended to be a legal
 * bootstrap method declaration for a dynamically-computed constant.
 *
 * If a method or constructor is annotated with an annotation of this
 * interface, compilers are required to generate an error message if
 * any of the following does not hold:
 * <ul>
 * <li>The declaration takes at least 3 arguments, including the receiver
 *     if the declaration is an instance method.  The arguments can be
 *     variable-arity.
 * <li>The first argument type is exactly {@link MethodHandles.Lookup}.
 * <li>The second argument type is {@linkplain MethodHandle#asType(MethodType)
 *     convertible} from {@link String}.
 * <li>The third argument type is convertible from {@link Class}.
 * </ul>
 *
 * @apiNote
 * There is no restriction on the return type - if the return type is
 * {@code void}, it is treated as if the bootstrap method always returns
 * {@code null}.
 *
 * @see java.lang.invoke##bsm Execution of bootstrap methods
 * @jvms 5.4.3.6 Dynamically-Computed Constant and Call Site Resolution
 * @since 27
 */
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.CONSTRUCTOR, ElementType.METHOD})
@Documented
public @interface ConstantBootstrap {
}
