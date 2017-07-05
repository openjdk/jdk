/*
 * Copyright (c) 1997, 2011, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.codemodel.internal.JClass;
import com.sun.tools.internal.xjc.Options;
import com.sun.tools.internal.xjc.generator.bean.field.FieldRenderer;

import org.w3c.dom.Element;

/**
 * Particles in the &lt;content> declaration in the binding file.
 *
 */
public class BIContent
{
    /**
     * Wraps a given particle.
     *
     * <p>
     * This object should be created through
     * the {@link #create(Element, BIElement)} method.
     */
    private BIContent( Element e, BIElement _parent ) {
        this.element = e;
        this.parent = _parent;
        this.opts = parent.parent.model.options;
    }

    /** The particle element which this object is wrapping. */
    protected final Element element;

    /** The parent object.*/
    protected final BIElement parent;

    private final Options opts;

    /**
     * Gets the realization of this particle, if any.
     *
     * @return
     *      null if the "collection" attribute was not specified.
     */
    public final FieldRenderer getRealization() {
        String v = DOMUtil.getAttribute(element,"collection");
        if(v==null)     return null;

        v = v.trim();
        if(v.equals("array"))   return opts.getFieldRendererFactory().getArray();
        if(v.equals("list"))
            return opts.getFieldRendererFactory().getList(
                parent.parent.codeModel.ref(ArrayList.class));

        // the correctness of the attribute value must be
        // checked by the validator.
        throw new InternalError("unexpected collection value: "+v);
    }

    /**
     * Gets the property name of this particle.
     *
     * @return
     *      always a non-null, valid string.
     */
    public final String getPropertyName() {
        String r = DOMUtil.getAttribute(element,"property");

        // in case of <element-ref>, @property is optional and
        // defaults to @name.
        // in all other cases, @property is mandatory.
        if(r!=null)     return r;
        return DOMUtil.getAttribute(element,"name");
    }

    /**
     * Gets the type of this property, if any.
     * <p>
     * &lt;element-ref> particle doesn't have the type.
     *
     * @return
     *      null if none is specified.
     */
    public final JClass getType() {
        try {
            String type = DOMUtil.getAttribute(element,"supertype");
            if(type==null)     return null;

            // TODO: does this attribute defaults to the current package?
            int idx = type.lastIndexOf('.');
            if(idx<0)   return parent.parent.codeModel.ref(type);
            else        return parent.parent.getTargetPackage().ref(type);
        } catch( ClassNotFoundException e ) {
            // TODO: better error handling
            throw new NoClassDefFoundError(e.getMessage());
        }
    }





    /**
     * Creates an appropriate subclass of BIContent
     * by sniffing the tag name.
     * <p>
     * This method should be only called by the BIElement class.
     */
    static BIContent create( Element e, BIElement _parent ) {
        return new BIContent(e,_parent);
    }


}
