/*
 * Copyright (c) 1998, 2020, Oracle and/or its affiliates. All rights reserved.
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

/**
 * Provides support for RMI Object Activation.  A remote
 * object's reference can be made ``persistent'' and later activated into a
 * ``live'' object using the RMI activation mechanism.
 *
 * <p>Implementations are not required to support the activation
 * mechanism. If activation is not supported by this implementation,
 * several specific activation API methods are all required to throw
 * {@code UnsupportedOperationException}. If activation is supported by this
 * implementation, these methods must never throw {@code
 * UnsupportedOperationException}. These methods are denoted by the
 * presence of an entry for {@code UnsupportedOperationException} in the
 * <strong>Throws</strong> section of each method's specification.
 *
 * @since 1.2
 * @deprecated The RMI Activation mechanism has been deprecated and may
 * be removed from a future version of the Java Platform. All of the classes
 * and interfaces in this package have been terminally deprecated. The
 * {@code rmid} tool has also been terminally deprecated. There is no
 * replacement for the RMI Activation mechanism in the Java Platform. Users of
 * RMI Activation are advised to migrate their applications to other technologies.
 */
package java.rmi.activation;
