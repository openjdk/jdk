/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

package jdk;

import java.lang.annotation.*;

/**
  * Indicates whether or not a JDK specific type or package is a
  * supported part of the JDK.
  *
  * This annotation should only be applied to types and packages
  * <em>outside</em> of the Java SE namespaces of {@code java.*} and
  * {@code javax.*} packages.  For example, certain portions of {@code
  * com.sun.*} are official parts of the JDK meant to be generally
  * usable while other portions of {@code com.sun.*} are not.  This
  * annotation type allows those portions to be easily and
  * programmaticly distinguished.
  *
  * @since 1.8
  */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.PACKAGE})
@Supported
public @interface Supported {
    /**
     * Whether or not this package or type is a supported part of the JDK.
     */
    boolean value() default true;
}
