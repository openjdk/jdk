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
package com.sun.tools.internal.ws.processor.model.jaxb;

import com.sun.tools.internal.ws.processor.model.AbstractType;

import javax.xml.namespace.QName;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Vivek Pandey
 *
 * RPC Structure that will be used to create RpcLitPayload latter
 */
public class RpcLitStructure extends AbstractType {
    private List<RpcLitMember> members;
    private JAXBModel jaxbModel;

    /**
     *
     */
    public RpcLitStructure() {
        super();
        // TODO Auto-generated constructor stub
    }
    public RpcLitStructure(QName name, JAXBModel jaxbModel){
        setName(name);
        this.jaxbModel = jaxbModel;
        this.members = new ArrayList<RpcLitMember>();

    }
    public RpcLitStructure(QName name, JAXBModel jaxbModel, List<RpcLitMember> members){
        setName(name);
        this.members = members;
    }

    public void accept(JAXBTypeVisitor visitor) throws Exception {
        visitor.visit(this);
    }

    public List<RpcLitMember> getRpcLitMembers(){
        return members;
    }

    public List<RpcLitMember> setRpcLitMembers(List<RpcLitMember> members){
        return this.members = members;
    }

    public void addRpcLitMember(RpcLitMember member){
        members.add(member);
    }
    /**
     * @return Returns the jaxbModel.
     */
    public JAXBModel getJaxbModel() {
        return jaxbModel;
    }
    /**
     * @param jaxbModel The jaxbModel to set.
     */
    public void setJaxbModel(JAXBModel jaxbModel) {
        this.jaxbModel = jaxbModel;
    }

    public boolean isLiteralType() {
        return true;
    }
}
