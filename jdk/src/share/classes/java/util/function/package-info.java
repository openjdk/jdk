/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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
 * <em>Functional interfaces</em> provide typing for lambda expressions. Each
 * functional interface provides a single abstract method to which the lambda
 * expression's parameter and return types are matched.
 *
 * <p>The interfaces in this package are all functional interfaces used with the
 * collections and streams frameworks. The operation identified by each
 * interface is generally applied to a collection or stream of objects.
 *
 * <p>All functional interface implementations are expected to ensure that:
 * <ul>
 * <li>When used for aggregate operations upon many elements it should not be
 * assumed that the operation will be called upon elements in any specific order.
 * </li>
 * <li>{@code null} values are accepted and returned by these functional
 * interfaces according to the constraints of the specification in which the
 * functional interfaces are used. The functional interfaces themselves do not
 * constrain or mandate use of {@code null} values. Most usages of the
 * functional interfaces will define the role, if any, of {@code null} for that
 * context.
 * </li>
 * </ul>
 */
package java.util.function;
