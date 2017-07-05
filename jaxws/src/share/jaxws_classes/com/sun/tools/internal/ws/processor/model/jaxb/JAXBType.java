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

package com.sun.tools.internal.ws.processor.model.jaxb;

import com.sun.tools.internal.ws.processor.model.AbstractType;
import com.sun.tools.internal.ws.processor.model.java.JavaType;

import javax.xml.namespace.QName;
import java.util.ArrayList;
import java.util.List;

/**
 * Top-level binding between JAXB generated Java type
 * and XML Schema element declaration.
 *
 * @author
 *     Vivek Pandey
 */
public class JAXBType extends AbstractType{
    public JAXBType(JAXBType jaxbType){
        setName(jaxbType.getName());
        this.jaxbMapping = jaxbType.getJaxbMapping();
        this.jaxbModel = jaxbType.getJaxbModel();
        init();
    }

    public JAXBType(){}

    public JAXBType(QName name, JavaType type){
        super(name, type);
    }

    public JAXBType(QName name, JavaType type, JAXBMapping jaxbMapping, JAXBModel jaxbModel){
        super(name, type);
        this.jaxbMapping = jaxbMapping;
        this.jaxbModel = jaxbModel;
        init();
    }

    public void accept(JAXBTypeVisitor visitor) throws Exception {
        visitor.visit(this);
    }

    private void init() {
        if (jaxbMapping != null)
            wrapperChildren = jaxbMapping.getWrapperStyleDrilldown();
        else
            wrapperChildren =  new ArrayList<JAXBProperty>();
    }

    public boolean isUnwrappable(){
        return jaxbMapping != null && jaxbMapping.getWrapperStyleDrilldown() != null;
    }

    public boolean hasWrapperChildren(){
        return wrapperChildren.size() > 0;
    }

    public boolean isLiteralType() {
        return true;
    }

    public List<JAXBProperty> getWrapperChildren(){
        return wrapperChildren;
    }

    public void setWrapperChildren(List<JAXBProperty> children) {
        wrapperChildren = children;
    }

    public JAXBMapping getJaxbMapping() {
        return jaxbMapping;
    }

    public void setJaxbMapping(JAXBMapping jaxbMapping) {
        this.jaxbMapping = jaxbMapping;
        init();
    }

    public void setUnwrapped(boolean unwrapped) {
        this.unwrapped = unwrapped;
    }

    public boolean isUnwrapped() {
        return unwrapped;
    }

    private JAXBMapping jaxbMapping;

    public JAXBModel getJaxbModel() {
        return jaxbModel;
    }

    public void setJaxbModel(JAXBModel jaxbModel) {
        this.jaxbModel = jaxbModel;
    }

    private JAXBModel jaxbModel;
    private boolean unwrapped = false;
    private List<JAXBProperty> wrapperChildren;
}
