/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.bind.v2.model.core;

import javax.xml.bind.annotation.XmlEnumValue;

/**
 * Individual constant of an enumeration.
 *
 * <p>
 * Javadoc in this class uses the following sample to explain the semantics:
 * <pre>
 * &#64;XmlEnum(Integer.class)
 * enum Foo {
 *   &#64;XmlEnumValue("1")
 *   ONE,
 *   &#64;XmlEnumValue("2")
 *   TWO
 * }
 * </pre>
 *
 * @see EnumLeafInfo
 * @author Kohsuke Kawaguchi
 */
public interface EnumConstant<T,C> {

    /**
     * Gets the {@link EnumLeafInfo} to which this constant belongs to.
     *
     * @return never null.
     */
    EnumLeafInfo<T,C> getEnclosingClass();

    /**
     * Lexical value of this constant.
     *
     * <p>
     * This value should be evaluated against
     * {@link EnumLeafInfo#getBaseType()} to obtain the typed value.
     *
     * <p>
     * This is the same value as written in the {@link XmlEnumValue} annotation.
     * In the above example, this method returns "1" and "2".
     *
     * @return
     *      never null.
     */
    String getLexicalValue();

    /**
     * Gets the constant name.
     *
     * <p>
     * In the above example this method return "ONE" and "TWO".
     *
     * @return
     *      never null. A valid Java identifier.
     */
    String getName();
}
