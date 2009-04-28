/*
 * Copyright 2002-2007 Sun Microsystems, Inc.  All Rights Reserved.
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
package org.jdesktop.swingx.designer.jibxhelpers;

import org.jibx.runtime.IAliasable;
import org.jibx.runtime.IMarshaller;
import org.jibx.runtime.IMarshallingContext;
import org.jibx.runtime.IUnmarshaller;
import org.jibx.runtime.IUnmarshallingContext;
import org.jibx.runtime.JiBXException;
import org.jibx.runtime.impl.MarshallingContext;
import org.jibx.runtime.impl.UnmarshallingContext;

import java.awt.Insets;

/**
 * InsetsMapper
 *
 * @author Created by Jasper Potts (Jun 8, 2007)
 */
public class InsetsMapper implements IMarshaller, IUnmarshaller, IAliasable {
    private static final String ELEMENT_NAME = "insets";
    private static final String TOP_NAME = "top";
    private static final String BOTTOM_NAME = "bottom";
    private static final String LEFT_NAME = "left";
    private static final String RIGHT_NAME = "right";

    private String uri;
    private int index;
    private String name;

    public InsetsMapper() {
        uri = null;
        index = 0;
        name = ELEMENT_NAME;
    }

    public InsetsMapper(String uri, int index, String name) {
        this.uri = uri;
        this.index = index;
        this.name = name;
//        System.out.println("InsetsMapper.CONSTRCUTED with uri="+uri+" index="+index+" name="+name);
    }

    public boolean isExtension(int i) {
        return false;
    }

    public boolean isPresent(IUnmarshallingContext iUnmarshallingContext) throws JiBXException {
        return iUnmarshallingContext.isAt(uri, ELEMENT_NAME);
    }

    public void marshal(Object object, IMarshallingContext iMarshallingContext) throws JiBXException {
        if (!(object instanceof Insets)) {
            throw new JiBXException("Invalid object type for marshaller");
        } else if (!(iMarshallingContext instanceof MarshallingContext)) {
            throw new JiBXException("Invalid object type for marshaller");
        } else {
//            System.out.println("InsetsMapper.marshal name="+name);
            MarshallingContext ctx = (MarshallingContext) iMarshallingContext;
            Insets insets = (Insets) object;
            ctx.startTagAttributes(index, name).
                    attribute(index, TOP_NAME, insets.top).
                    attribute(index, BOTTOM_NAME, insets.bottom).
                    attribute(index, LEFT_NAME, insets.left).
                    attribute(index, RIGHT_NAME, insets.right).
                    closeStartEmpty();
        }
    }

    public Object unmarshal(Object object, IUnmarshallingContext iUnmarshallingContext) throws JiBXException {
        // make sure we're at the appropriate start tag
        UnmarshallingContext ctx = (UnmarshallingContext) iUnmarshallingContext;
        if (!ctx.isAt(uri, name)) {
//            System.out.println("InsetsMapper.unmarshal name="+name+" uri="+uri+" currentNode="+ctx.getName());
            ctx.throwStartTagNameError(uri, name);
        } else {
//        System.out.println("InsetsMapper.unmarshal name="+name+" uri="+uri+" currentNode="+ctx.getName());
        }
        // get values
        int top = ctx.attributeInt(uri, TOP_NAME, index);
        int bottom = ctx.attributeInt(uri, BOTTOM_NAME, index);
        int left = ctx.attributeInt(uri, LEFT_NAME, index);
        int right = ctx.attributeInt(uri, RIGHT_NAME, index);
        // create new hashmap if needed
        Insets insets = (Insets) object;
        if (insets == null) {
            insets = new Insets(top, left, bottom, right);
        } else {
            insets.set(top, left, bottom, right);
        }
        ctx.parsePastEndTag(uri, name);
        return insets;
    }
}
