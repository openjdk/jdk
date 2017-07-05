/*
 * Copyright (c) 1997, 2015, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.internal.xjc.reader.dtd.bindinfo;

import java.util.ArrayList;

import com.sun.tools.internal.xjc.generator.bean.field.FieldRenderer;
import com.sun.tools.internal.xjc.generator.bean.field.FieldRendererFactory;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;

/** {@code <attribute>} declaration in the binding file. */
public class BIAttribute
{
    /**
     * Wraps a given {@code <attribute>} element.
     * <p>
     * Should be created only from {@link BIElement}.
     */
    BIAttribute( BIElement _parent, Element _e ) {
        this.parent = _parent;
        this.element = _e;
    }

    private final BIElement parent;
    private final Element element;

    /** Gets the name of this attribute-property declaration. */
    public final String name() {
        return element.getAttribute("name");
    }


    /**
     * Gets the conversion method for this attribute, if any.
     *
     * @return
     *        If the convert attribute is not specified, this
     *        method returns null.
     */
    public BIConversion getConversion() {
        if (element.getAttributeNode("convert") == null)
            return null;

        String cnv = element.getAttribute("convert");
        return parent.conversion(cnv);
    }

    /**
     * Gets the realization of this particle, if any.
     *
     * @return
     *      null if the "collection" attribute was not specified.
     */
    public final FieldRenderer getRealization() {
        Attr a = element.getAttributeNode("collection");
        if(a==null)     return null;

        String v = element.getAttribute("collection").trim();

        FieldRendererFactory frf = parent.parent.model.options.getFieldRendererFactory();
        if(v.equals("array"))   return frf.getArray();
        if(v.equals("list"))
            return frf.getList(
                parent.parent.codeModel.ref(ArrayList.class));

        // the correctness of the attribute value must be
        // checked by the validator.
        throw new InternalError("unexpected collection value: "+v);
    }

    /**
     * Gets the property name for this attribute.
     *
     * @return
     *      always a non-null, valid string.
     */
    public final String getPropertyName() {
        String r = DOMUtil.getAttribute(element,"property");
        if(r!=null)     return r;
        else            return name();
    }
}
