/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
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
package com.sun.xml.internal.bind.v2.model.core;

/**
 * {@link NonElement} that represents an {@link Enum} class.
 *
 * @author Kohsuke Kawaguchi
 */
public interface EnumLeafInfo<T,C> extends LeafInfo<T,C> {
    /**
     * The same as {@link #getType()} but an {@link EnumLeafInfo}
     * is guaranteed to represent an enum declaration, which is a
     * kind of a class declaration.
     *
     * @return
     *      always non-null.
     */
    C getClazz();

    /**
     * Returns the base type of the enumeration.
     *
     * <p>
     * For example, with the following enum class, this method
     * returns {@link BuiltinLeafInfo} for {@link Integer}.
     *
     * <pre>
     * &amp;XmlEnum(Integer.class)
     * enum Foo {
     *   &amp;XmlEnumValue("1")
     *   ONE,
     *   &amp;XmlEnumValue("2")
     *   TWO
     * }
     * </pre>
     *
     * @return
     *      never null.
     */
    NonElement<T,C> getBaseType();

    /**
     * Returns the read-only list of enumeration constants.
     *
     * @return
     *      never null. Can be empty (really?).
     */
    Iterable<? extends EnumConstant> getConstants();
}
