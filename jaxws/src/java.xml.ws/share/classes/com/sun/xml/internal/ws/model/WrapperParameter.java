/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.ws.model;

import com.sun.xml.internal.ws.api.model.JavaMethod;
import com.sun.xml.internal.ws.api.model.ParameterBinding;
import com.sun.xml.internal.ws.spi.db.TypeInfo;
import com.sun.xml.internal.ws.spi.db.WrapperComposite;

import javax.jws.WebParam.Mode;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link ParameterImpl} that represents a wrapper,
 * which is a parameter that consists of multiple nested {@link ParameterImpl}s
 * within, which together form a body part.
 *
 * <p>
 * Java method parameters represented by nested {@link ParameterImpl}s will be
 * packed into a "wrapper bean" and it becomes the {@link ParameterImpl} for the
 * body.
 *
 * <p>
 * This parameter is only used for the {@link ParameterBinding#BODY} binding.
 * Other parameters that bind to other parts (such as headers or unbound)
 * will show up directly under {@link JavaMethod}.
 *
 * @author Vivek Pandey
 */
public class WrapperParameter extends ParameterImpl {
    protected final List<ParameterImpl> wrapperChildren = new ArrayList<ParameterImpl>();

    // TODO: wrapper parameter doesn't use 'typeRef' --- it only uses tag name.
    public WrapperParameter(JavaMethodImpl parent, TypeInfo typeRef, Mode mode, int index) {
        super(parent, typeRef, mode, index);
                //chen workaround for document-literal wrapper - new feature on eclipselink API requested
        typeRef.properties().put(WrapperParameter.class.getName(), this);
    }

    /**
     *
     * @deprecated
     *      Why are you calling a method that always return true?
     */
    @Override
    public boolean isWrapperStyle() {
        return true;
    }

    /**
     * @return Returns the wrapperChildren.
     */
    public List<ParameterImpl> getWrapperChildren() {
        return wrapperChildren;
    }

    /**
     * Adds a new child parameter.
     *
     * @param wrapperChild
     */
    public void addWrapperChild(ParameterImpl wrapperChild) {
        wrapperChildren.add(wrapperChild);
        wrapperChild.wrapper = this;
        // must bind to body. see class javadoc
        assert wrapperChild.getBinding()== ParameterBinding.BODY;
    }

    public void clear(){
        wrapperChildren.clear();
    }

    @Override
    void fillTypes(List<TypeInfo> types) {
        super.fillTypes(types);
        if(WrapperComposite.class.equals(getTypeInfo().type)) {
            for (ParameterImpl p : wrapperChildren) p.fillTypes(types);
        }
//        if(getParent().getBinding().isRpcLit()) {
//            // for rpc/lit, we need to individually marshal/unmarshal wrapped values,
//            // so their TypeReference needs to be collected
////            assert getTypeReference().type==CompositeStructure.class;
//            for (ParameterImpl p : wrapperChildren)
//                p.fillTypes(types);
//        }
    }
}
