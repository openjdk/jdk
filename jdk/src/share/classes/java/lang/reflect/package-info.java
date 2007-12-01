/*
 * Copyright 1998-2006 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/**
 * Provides classes and interfaces for obtaining reflective
 * information about classes and objects.  Reflection allows
 * programmatic access to information about the fields, methods and
 * constructors of loaded classes, and the use of reflected fields,
 * methods, and constructors to operate on their underlying
 * counterparts, within security restrictions.
 *
 * <p>{@code AccessibleObject} allows suppression of access checks if
 * the necessary {@code ReflectPermission} is available.
 *
 * <p>{@code Array} provides static methods to dynamically create and
 * access arrays.
 *
 * <p>Classes in this package, along with {@code java.lang.Class}
 * accommodate applications such as debuggers, interpreters, object
 * inspectors, class browsers, and services such as Object
 * Serialization and JavaBeans that need access to either the public
 * members of a target object (based on its runtime class) or the
 * members declared by a given class.
 *
 * @since JDK1.1
 */
package java.lang.reflect;
