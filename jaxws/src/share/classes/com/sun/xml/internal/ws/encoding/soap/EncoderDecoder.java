/*
 * Portions Copyright 2006 Sun Microsystems, Inc.  All Rights Reserved.
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
package com.sun.xml.internal.ws.encoding.soap;

import com.sun.xml.internal.ws.encoding.EncoderDecoderBase;
import com.sun.xml.internal.ws.encoding.jaxb.JAXBBridgeInfo;
import com.sun.xml.internal.ws.encoding.jaxb.RpcLitPayload;
import com.sun.xml.internal.ws.encoding.soap.internal.AttachmentBlock;
import com.sun.xml.internal.ws.encoding.soap.internal.HeaderBlock;
import com.sun.xml.internal.ws.encoding.soap.internal.InternalMessage;
import com.sun.xml.internal.ws.model.*;
import com.sun.xml.internal.ws.model.soap.SOAPBinding;
import com.sun.xml.internal.ws.server.RuntimeContext;
import com.sun.xml.internal.ws.handler.HandlerContext;
import com.sun.xml.internal.ws.handler.MessageContextUtil;
import com.sun.xml.internal.ws.pept.ept.MessageInfo;
import com.sun.xml.internal.ws.client.BindingProviderProperties;
import com.sun.xml.internal.ws.client.RequestContext;

import javax.activation.DataHandler;
import javax.xml.namespace.QName;
import javax.xml.transform.Source;
import javax.xml.ws.handler.MessageContext;
import javax.xml.ws.WebServiceException;
import java.awt.Image;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Type;
import java.net.URLEncoder;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * @author Vivek Pandey
 *
 * Base SOAP encoder decoder class.
 */
public abstract class EncoderDecoder extends EncoderDecoderBase {

    /**
     * Fills the data[] which is ordered list of java method parameters.
     *
     * @param context
     *            will be used if needed
     * @param obj
     * @param data
     * @return if the parameter is a return
     */
    protected Object fillData(RuntimeContext context, Parameter param, Object obj, Object[] data,
                              SOAPBinding binding, ParameterBinding paramBinding) {
        if (param.isWrapperStyle()) {
            Object resp = null;
            for (Parameter p : ((WrapperParameter) param).getWrapperChildren()) {
                QName name = p.getName();
                Object value = null;
                if (binding.isDocLit()){
                    value = super.getWrapperChildValue(context, ((JAXBBridgeInfo)obj).getValue(),
                                name.getNamespaceURI(), name.getLocalPart());
                }else if (binding.isRpcLit()){
                    value = getWrapperChildValue(context, obj, name.getNamespaceURI(), name
                            .getLocalPart());
                    if(value == null)
                        value = setIfPrimitive(p.getTypeReference().type);
                }
                if (p.isResponse())
                    resp = value;
                else {
                    if (data[p.getIndex()] != null) {
                        Parameter.setHolderValue(data[p.getIndex()], value);
                    } else {
                        data[p.getIndex()] = p.createHolderValue(value);
                    }
                }
            }
            return resp;
        }

        if(!paramBinding.isAttachment()){
            if(paramBinding.isUnbound())
                obj = setIfPrimitive(param.getTypeReference().type);
            else
                obj = (obj != null)?((JAXBBridgeInfo)obj).getValue():null;
        }
        if (param.isResponse()) {
            if(paramBinding.isUnbound())
                return setIfPrimitive(param.getTypeReference().type);
            return obj;
        } else if (data[param.getIndex()] != null) {
            Parameter.setHolderValue(data[param.getIndex()], obj);
        } else {
            data[param.getIndex()] = param.createHolderValue(obj);
        }
        return null;
    }

    /**
     * Returns the default values of primitive types. To be called when the object referene by this type is null.
     * @param type
     * @return default values of primitive types if type is primitive else null
     */
    private Object setIfPrimitive(Type type) {
        if(type instanceof Class){
            Class cls = (Class)type;
            if(cls.isPrimitive()){
                if(cls.getName().equals(boolean.class.getName())){
                    return false;
                }
                return 0;
            }
        }
        return null;
    }

    /**
     * creates the payload to be serilized over the wire. It can be constructed
     * from the parameters in data[] or from the result.
     *
     * @param context
     *            runtime context. It can be used to get access to the
     *            JAXBContext.
     * @param data
     *            parameters
     * @param result
     *            it could be null if there are no response object,for example
     *            incase of outgoing client message.
     * @return Payload - decided by the binding used
     */
    protected Object createPayload(RuntimeContext context, Parameter param, Object[] data,
                                   Object result, SOAPBinding binding, ParameterBinding paramBinding) {
        if(paramBinding.isAttachment()){
            Object obj = null;
            if(param.isResponse())
                obj = result;
            else
                obj = param.getHolderValue(data[param.getIndex()]);
            return obj;
        }
        if (binding.isRpcLit() && paramBinding.isBody()) {
            return createRpcLitPayload(context, (WrapperParameter) param, data, result);
        }
        Object obj = createDocLitPayloadValue(context, param, data, result);
        RuntimeModel model = context.getModel();
        return new JAXBBridgeInfo(model.getBridge(param.getTypeReference()), obj);
    }

    /*
     * Returns the value corresponding to the localName or part accessor from
     * rpclit structure.
     *
     * @see EncoderDecoderBase#getWrapperChildValue(RuntimeContext,
     *      Object, String, String)
     */
    @Override
    protected Object getWrapperChildValue(RuntimeContext context, Object obj, String nsURI,
                                          String localName) {
        RpcLitPayload payload = (RpcLitPayload) obj;
        JAXBBridgeInfo rpcParam = payload.getBridgeParameterByName(localName);
        if(rpcParam != null)
            return rpcParam.getValue();
        return null;
    }

    /**
     * Gives the binding specific object to be serialized.
     *
     * @param context
     * @param data
     * @param result
     */
    private Object createDocLitPayloadValue(RuntimeContext context, Parameter param, Object[] data, Object result) {
        if (param.isWrapperStyle()) {
            return createJAXBBeanPayload(context, (WrapperParameter) param, data, result);
        }
        return getBarePayload(param, data, result);
    }

    /**
     * Gets the HeaderBlock corresponding to the given QName.
     *
     * @param name
     * @param headers
     * @return the <code>HeaderBlock</code> corresponding to the given
     * <code>QName name</code>
     */
    protected HeaderBlock getHeaderBlock(QName name, List<HeaderBlock> headers) {
        for (HeaderBlock header : headers) {
            if (name.equals(header.getName()))
                return header;
        }
        return null;
    }

    /**
     * Returns either the value corresponding to the parameter or result.
     *
     * @param param
     * @param data
     * @param result
     * @return Either the value of response of the parameter corresponding to
     *         the parameter index, takes care of Holder.value.
     *
     */
    private Object getBarePayload(Parameter param, Object[] data, Object result) {
        Object obj = null;
        if (param.isResponse()) {
            obj = result;
        } else {
            obj = param.getHolderValue(data[param.getIndex()]);
        }
        return obj;
    }

    /**
     * Creates JAXB style wrapper bean from the parameters or result.
     *
     * @param context
     * @param param
     *            WrapperParameter
     * @param data
     * @param result
     * @return non-null JAXB style bean.
     */
    private Object createJAXBBeanPayload(RuntimeContext context, WrapperParameter param,
                                         Object[] data, Object result) {
        Class bean = (Class) param.getTypeReference().type;
        try {
            Object obj = bean.newInstance();
            for( Parameter p : param.getWrapperChildren() ) {
                Object value;
                if (p.isResponse())
                    value = result;
                else
                    value = p.getHolderValue(data[p.getIndex()]);
                QName name = p.getName();
                setWrapperChildValue(context, obj, value,
                    name.getNamespaceURI(), name.getLocalPart());
            }
            return obj;
        } catch(Exception e){
            throw new SerializationException(e);
        }
    }

    /**
     * Creates RpcLitPayload from the parameters or response.
     *
     * @param context
     * @param param
     * @param data
     * @param result
     * @return non-null RpcLitPayload
     */
    private Object createRpcLitPayload(RuntimeContext context, WrapperParameter param,
                                       Object[] data, Object result) {
        RpcLitPayload payload = new RpcLitPayload(param.getName());

        for  (Parameter p : param.getWrapperChildren()) {
            if(p.getBinding().isUnbound())
                continue;
            Object value = null;
            if (p.isResponse())
                value = result;
            else
                value = p.getHolderValue(data[p.getIndex()]);
            checkRPCLitNullableParameter(p, value);
            RuntimeModel model = context.getModel();
            JAXBBridgeInfo bi = new JAXBBridgeInfo(model.getBridge(p.getTypeReference()), value);
            payload.addParameter(bi);
        }
        return payload;
    }

    protected Object getAttachment(RuntimeContext rtContext, Map<String, AttachmentBlock> attachments,
                                   Parameter param, ParameterBinding paramBinding){
        try {
            for (Map.Entry<String,AttachmentBlock> entry : attachments.entrySet()) {
                AttachmentBlock ab = entry.getValue();
                String part = ab.getWSDLPartName();
                // part can be null if the Content-Id is not encoded as per AP 1.0 R2933. Which is ok since there could be attachments
                // other than WSDL MIME bound
                if(part == null)
                    continue;

                if(part.equals(param.getPartName()) || part.equals("<"+param.getPartName())){
                    Class type = (Class)param.getTypeReference().type;

                    if(DataHandler.class.isAssignableFrom(type))
                        return ab.asDataHandler();
                    if(byte[].class==type)
                        return ab.asByteArray();
                    if(Source.class.isAssignableFrom(type))
                        return ab.asSource();
                    if(Image.class.isAssignableFrom(type))
                        return ab.asImage();
                    if(InputStream.class==type)
                        return ab.asDataHandler().getInputStream();
                    if(isXMLMimeType(paramBinding.getMimeType())) {
                        JAXBBridgeInfo bi = (JAXBBridgeInfo)rtContext.getDecoderInfo(param.getName());
                        ab.deserialize(rtContext.getBridgeContext(),bi);
                        return bi.getValue();
                    }
                }
            }
            return null;
        } catch (IOException e) {
            throw new SerializationException(e);
        }
    }

    protected void addAttachmentPart(RuntimeContext rtContext, InternalMessage im, Object obj, Parameter mimeParam){
        if(obj == null)
            return;
        RuntimeModel model = rtContext.getModel();
        String mimeType = mimeParam.getBinding().getMimeType();
        String contentId;
        try {
            contentId = URLEncoder.encode(mimeParam.getPartName(), "UTF-8")+ '=' +UUID.randomUUID()+"@jaxws.sun.com";
            contentId="<"+contentId+">";
        } catch (UnsupportedEncodingException e) {
            throw new SerializationException(e);
        }

        AttachmentBlock ab;

        if(obj instanceof DataHandler)
            ab = AttachmentBlock.fromDataHandler(contentId,(DataHandler)obj);
        else
        if(obj instanceof Source)
            // this is potentially broken, as there's no guarantee this will work.
            // we should have our own AttachmentBlock implementation for this.
            ab = AttachmentBlock.fromDataHandler(contentId, new DataHandler(obj,mimeType));
        else
        if(obj instanceof byte[])
            ab = AttachmentBlock.fromByteArray(contentId,(byte[])obj,mimeType);
        else
        if(isXMLMimeType(mimeType))
            ab = AttachmentBlock.fromJAXB(contentId,
                    new JAXBBridgeInfo(model.getBridge(mimeParam.getTypeReference()), obj),
                    rtContext, mimeType );
        else
            // this is also broken, as there's no guarantee that the object type and the MIME type
            // matches. But most of the time it matches, so it mostly works.
            ab = AttachmentBlock.fromDataHandler(contentId,new DataHandler(obj,mimeType));

        //populate the attachment map in the message context
        HandlerContext hc = rtContext.getHandlerContext();
        if(hc != null){
            MessageContext mc = hc.getMessageContext();
            if(mc != null){
                MessageContextUtil.addMessageAttachment(mc, ab.getId(), ab.asDataHandler());
            }
        }

        im.addAttachment(ab);
    }

    protected void copyAttachmentProperty(Map<String, Object> ctxt, InternalMessage im) {
        if(ctxt == null)
            return;
        Map<String, DataHandler> attMap = (Map<String, DataHandler>) ctxt.get(MessageContext.OUTBOUND_MESSAGE_ATTACHMENTS);
        if (attMap == null)
            return;
        for (Map.Entry<String, DataHandler> att : attMap.entrySet()) {
            im.addAttachment(AttachmentBlock.fromDataHandler(att.getKey(), att.getValue()));
        }
    }

    private boolean isXMLMimeType(String mimeType){
        if(mimeType.equals("text/xml") || mimeType.equals("application/xml"))
            return true;
        return false;
    }

    /**
     * Checks rpclit body parts for nullability
     */
    void checkRPCLitNullableParameter(Parameter param, Object value) {
        if(value == null)
            throw new WebServiceException("Method Parameter: "+param.getName() +" cannot be null. This is BP 1.1 R2211 violation.");
    }

}
