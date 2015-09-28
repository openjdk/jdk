/*
 * Copyright (c) 1997, 2014, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.xml.internal.bind.api.Bridge;
import com.sun.xml.internal.bind.api.TypeReference;
import com.sun.xml.internal.ws.api.model.JavaMethod;
import com.sun.xml.internal.ws.api.model.Parameter;
import com.sun.xml.internal.ws.api.model.ParameterBinding;
import com.sun.xml.internal.ws.spi.db.RepeatedElementBridge;
import com.sun.xml.internal.ws.spi.db.WrapperComposite;
import com.sun.xml.internal.ws.spi.db.XMLBridge;
import com.sun.xml.internal.ws.spi.db.TypeInfo;

import javax.jws.WebParam.Mode;
import javax.xml.namespace.QName;
import javax.xml.ws.Holder;
import java.util.List;

/**
 * runtime Parameter that abstracts the annotated java parameter
 *
 * <p>
 * A parameter may be bound to a header, a body, or an attachment.
 * Note that when it's bound to a body, it's bound to a body,
 * it binds to the whole payload.
 *
 * <p>
 * Sometimes multiple Java parameters are packed into the payload,
 * in which case the subclass {@link WrapperParameter} is used.
 *
 * @author Vivek Pandey
 */
public class ParameterImpl implements Parameter {

    private ParameterBinding binding;
    private ParameterBinding outBinding;
    private String partName;
    private final int index;
    private final Mode mode;
    /** @deprecated */
    private TypeReference typeReference;
    private TypeInfo typeInfo;
    private QName name;
    private final JavaMethodImpl parent;

    WrapperParameter wrapper;
    TypeInfo itemTypeInfo;

    public ParameterImpl(JavaMethodImpl parent, TypeInfo type, Mode mode, int index) {
        assert type != null;

        this.typeInfo = type;
        this.name = type.tagName;
        this.mode = mode;
        this.index = index;
        this.parent = parent;
    }

    public AbstractSEIModelImpl getOwner() {
        return parent.owner;
    }

    public JavaMethod getParent() {
        return parent;
    }

    /**
     * @return Returns the name.
     */
    public QName getName() {
        return name;
    }

    public XMLBridge getXMLBridge() {
        return getOwner().getXMLBridge(typeInfo);
    }

    public XMLBridge getInlinedRepeatedElementBridge() {
        TypeInfo itemType = getItemType();
        if (itemType != null && itemType.getWrapperType() == null) {
            XMLBridge xb = getOwner().getXMLBridge(itemType);
            if (xb != null) return new RepeatedElementBridge(typeInfo, xb);
        }
        return null;
    }

    public TypeInfo getItemType() {
        if (itemTypeInfo != null) return itemTypeInfo;
        //RpcLit cannot inline repeated element in wrapper
        if (parent.getBinding().isRpcLit() || wrapper == null) return null;
        //InlinedRepeatedElementBridge is only used for dynamic wrapper (no wrapper class)
        if (!WrapperComposite.class.equals(wrapper.getTypeInfo().type)) return null;
        if (!getBinding().isBody()) return null;
        itemTypeInfo = typeInfo.getItemType();
        return  itemTypeInfo;
    }

    /**  @deprecated  */
    public Bridge getBridge() {
        return getOwner().getBridge(typeReference);
    }
    /**  @deprecated  */
    protected Bridge getBridge(TypeReference typeRef) {
        return getOwner().getBridge(typeRef);
    }

    /**
     * TODO: once the model gets JAXBContext, shouldn't {@link Bridge}s
     * be made available from model objects?
     * @deprecated use getTypeInfo
     * @return Returns the TypeReference associated with this Parameter
     */
    public TypeReference getTypeReference() {
        return typeReference;
    }
    public TypeInfo getTypeInfo() {
        return typeInfo;
    }

    /**
     * Sometimes we need to overwrite the typeReferenc, such as during patching for rpclit
     * @see AbstractSEIModelImpl#applyRpcLitParamBinding(JavaMethodImpl, WrapperParameter, WSDLBoundPortType, WebParam.Mode)
     * @deprecated
     */
    void setTypeReference(TypeReference type){
        typeReference = type;
        name = type.tagName;
    }


    public Mode getMode() {
        return mode;
    }

    public int getIndex() {
        return index;
    }

    /**
     * @return true if {@code this instanceof} {@link WrapperParameter}.
     */
    public boolean isWrapperStyle() {
        return false;
    }

    public boolean isReturnValue() {
        return index==-1;
    }

    /**
     * @return the Binding for this Parameter
     */
    public ParameterBinding getBinding() {
        if(binding == null)
            return ParameterBinding.BODY;
        return binding;
    }

    /**
     * @param binding
     */
    public void setBinding(ParameterBinding binding) {
        this.binding = binding;
    }

    public void setInBinding(ParameterBinding binding){
        this.binding = binding;
    }

    public void setOutBinding(ParameterBinding binding){
        this.outBinding = binding;
    }

    public ParameterBinding getInBinding(){
        return binding;
    }

    public ParameterBinding getOutBinding(){
        if(outBinding == null)
            return binding;
        return outBinding;
    }

    public boolean isIN() {
        return mode==Mode.IN;
    }

    public boolean isOUT() {
        return mode==Mode.OUT;
    }

    public boolean isINOUT() {
        return mode==Mode.INOUT;
    }

    /**
     * If true, this parameter maps to the return value of a method invocation.
     *
     * <p>
     * {@link JavaMethodImpl#getResponseParameters()} is guaranteed to have
     * at most one such {@link ParameterImpl}. Note that there coule be none,
     * in which case the method returns {@code void}.
     */
    public boolean isResponse() {
        return index == -1;
    }


    /**
     * Gets the holder value if applicable. To be called for inbound client side
     * message.
     *
     * @param obj
     * @return the holder value if applicable.
     */
    public Object getHolderValue(Object obj) {
        if (obj != null && obj instanceof Holder)
            return ((Holder) obj).value;
        return obj;
    }

    public String getPartName() {
        if(partName == null)
            return name.getLocalPart();
        return partName;
    }

    public void setPartName(String partName) {
        this.partName = partName;
    }

    void fillTypes(List<TypeInfo> types) {
        TypeInfo itemType = getItemType();
        if (itemType != null) {
            types.add(itemType);
            if (itemType.getWrapperType() != null) types.add(getTypeInfo());
        } else {
            types.add(getTypeInfo());
        }
    }
}
