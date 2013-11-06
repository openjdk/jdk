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
  * Indicates whether or not a JDK specific type or package is an
  * exported part of the JDK suitable for use outside of the JDK
  * implementation itself.
  *
  * This annotation should only be applied to types and packages
  * <em>outside</em> of the Java SE namespaces of {@code java.*} and
  * {@code javax.*} packages.  For example, certain portions of {@code
  * com.sun.*} are official parts of the JDK meant to be generally
  * usable while other portions of {@code com.sun.*} are not.  This
  * annotation type allows those portions to be easily and
  * programmatically distinguished.
  *
  * <p>If in one release a type or package is
  * <code>@Exported(true)</code>, in a subsequent major release such a
  * type or package can transition to <code>@Exported(false)</code>.
  *
  * <p>If a type or package is <code>@Exported(false)</code> in a
  * release, it may be removed in a subsequent major release.
  *
  * <p>If a top-level type has an <code>@Exported</code> annotation,
  * any nested member types with the top-level type should have an
  * <code>@Exported</code> annotation with the same value.
  *
  * (In exceptional cases, if a nested type is going to be removed
  * before its enclosing type, the nested type's could be
  * <code>@Exported(false)</code> while its enclosing type was
  * <code>@Exported(true)</code>.)
  *
  * Likewise, if a package has an <code>@Exported</code> annotation,
  * top-level types within that package should also have an
  * <code>@Exported</code> annotation.
  *
  * Sometimes a top-level type may have a different
  * <code>@Exported</code> value than its package.
  *
  * @since 1.8
  */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.PACKAGE})
@Exported
public @interface Exported {
    /**
     * Whether or not the annotated type or package is an exported part of the JDK.
     */
    boolean value() default true;
}
