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

package com.sun.tools.internal.xjc.model;

import javax.activation.MimeType;

import com.sun.codemodel.internal.JExpression;
import com.sun.tools.internal.xjc.model.nav.NType;
import com.sun.tools.internal.xjc.outline.Outline;
import com.sun.xml.internal.bind.v2.model.core.ID;
import com.sun.xml.internal.xsom.XmlString;

/**
 * Information about how another type is referenced.
 *
 * <p>
 * In practice it is often easier to use {@link CTypeInfo}
 * instead of {@link NType}, so this interface defines {@link #getInfo()}.
 *
 * @author Kohsuke Kawaguchi
 * @see TypeUseImpl
 */
public interface TypeUse {
    /**
     * If the use can hold multiple values of the specified type.
     */
    boolean isCollection();

    /**
     * If this type use is adapting the type, returns the adapter.
     * Otherwise return null.
     */
    CAdapter getAdapterUse();

    /**
     * Individual item type.
     */
    CNonElement getInfo();

    /**
     * Whether the referenced type (individual item type in case of collection)
     * is ID/IDREF.
     *
     * <p>
     * ID is a property of a relationship. When a bean Foo has an ID property
     * called 'bar' whose type is String, Foo isn't an ID, String isn't an ID,
     * but this relationship is an ID (in the sense that Foo uses this String
     * as an ID.)
     *
     * <p>
     * The same thing can be said with IDREF. When Foo refers to Bar by means of
     * IDREF, neither Foo nor Bar is IDREF.
     *
     * <p>
     * That's why we have this method in {@link TypeUse}.
     */
    ID idUse();

    /**
     * A {@link TypeUse} can have an associated MIME type.
     */
    MimeType getExpectedMimeType();

    /**
     * Creates a constant for the given lexical value.
     *
     * <p>
     * For example, to create a constant 1 for <tt>xs:int</tt>, you'd do:
     * <pre>
     * CBuiltinLeafInfo.INT.createConstant( codeModel, "1", null );
     * </pre>
     *
     * <p>
     * This method is invoked at the backend as a part of the code generation process.
     *
     * @throws IllegalStateException
     *      if the type isn't bound to a text in XML.
     *
     * @return null
     *      if the constant cannot be created for this {@link TypeUse}
     *      (such as when it's a collection)
     */
    JExpression createConstant(Outline outline, XmlString lexical);
}
