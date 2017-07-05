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

import com.sun.xml.internal.ws.pept.ept.MessageInfo;
import com.sun.xml.internal.ws.pept.presentation.MessageStruct;
import com.sun.xml.internal.ws.binding.BindingImpl;
import com.sun.xml.internal.ws.client.BindingProviderProperties;
import com.sun.xml.internal.ws.encoding.internal.InternalEncoder;
import com.sun.xml.internal.ws.encoding.jaxb.JAXBBridgeInfo;
import com.sun.xml.internal.ws.encoding.soap.internal.AttachmentBlock;
import com.sun.xml.internal.ws.encoding.soap.internal.BodyBlock;
import com.sun.xml.internal.ws.encoding.soap.internal.HeaderBlock;
import com.sun.xml.internal.ws.encoding.soap.internal.InternalMessage;
import com.sun.xml.internal.ws.model.CheckedException;
import com.sun.xml.internal.ws.model.ExceptionType;
import com.sun.xml.internal.ws.model.JavaMethod;
import com.sun.xml.internal.ws.model.Parameter;
import com.sun.xml.internal.ws.model.ParameterBinding;
import com.sun.xml.internal.ws.model.RuntimeModel;
import com.sun.xml.internal.ws.model.WrapperParameter;
import com.sun.xml.internal.ws.model.soap.SOAPBinding;
import com.sun.xml.internal.ws.model.soap.SOAPRuntimeModel;
import com.sun.xml.internal.ws.server.RuntimeContext;
import com.sun.xml.internal.ws.util.MessageInfoUtil;
import com.sun.xml.internal.ws.util.StringUtils;

import javax.xml.ws.Holder;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * @author Vivek Pandey
 *
 * Server SOAP encoder decoder
 */
public class ServerEncoderDecoder extends EncoderDecoder implements InternalEncoder {
    public ServerEncoderDecoder() {
    }

    /*
     * (non-Javadoc)
     *
     * @see com.sun.xml.internal.ws.encoding.util.EncoderDecoderBase#toMessageInfo(java.lang.Object,
     *      com.sun.pept.ept.MessageInfo)
     */
    public void toMessageInfo(Object intMessage, MessageInfo mi) {
        InternalMessage im = (InternalMessage) intMessage;
        RuntimeContext rtContext = (RuntimeContext) mi.getMetaData(BindingProviderProperties.JAXWS_RUNTIME_CONTEXT);

        BodyBlock bodyBlock = im.getBody();
        JavaMethod jm = rtContext.getModel().getJavaMethod(mi.getMethod());
        mi.setMEP(jm.getMEP());
        List<HeaderBlock> headers = im.getHeaders();
        Map<String, AttachmentBlock> attachments = im.getAttachments();

        Iterator<Parameter> iter = jm.getRequestParameters().iterator();
        Object bodyValue = (bodyBlock == null) ? null :  bodyBlock.getValue();

        int numInputParams = jm.getInputParametersCount();
        Object data[] = new Object[numInputParams];
        SOAPBinding soapBinding = (SOAPBinding)jm.getBinding();
        while (iter.hasNext()) {
            Parameter param = iter.next();
            ParameterBinding paramBinding = param.getInBinding();
            Object obj = null;
            if (paramBinding.isBody()) {
                obj = bodyValue;
            } else if (headers != null && paramBinding.isHeader()) {
                HeaderBlock header = getHeaderBlock(param.getName(), headers);
                obj = (header != null)?header.getValue():null;
            } else if (paramBinding.isAttachment()) {
              obj = getAttachment(rtContext, attachments, param, paramBinding);
            }
            fillData(rtContext, param, obj, data, soapBinding, paramBinding);
        }
        Iterator<Parameter> resIter = jm.getResponseParameters().iterator();
        while(resIter.hasNext()){
            Parameter p = resIter.next();
            createOUTHolders(p, data);
        }
        mi.setData(data);
    }

    /*
     * (non-Javadoc)
     *
     * @see com.sun.xml.internal.ws.encoding.util.EncoderDecoderBase#toInternalMessage(com.sun.pept.ept.MessageInfo)
     */
    public Object toInternalMessage(MessageInfo mi) {
        RuntimeContext rtContext = MessageInfoUtil.getRuntimeContext(mi);
        RuntimeModel model = rtContext.getModel();
        JavaMethod jm = model.getJavaMethod(mi.getMethod());
        Object[] data = mi.getData();
        Object result = mi.getResponse();
        InternalMessage im = new InternalMessage();
        if(rtContext.getHandlerContext() != null){
            copyAttachmentProperty(rtContext.getHandlerContext().getMessageContext(), im);
        }
        BindingImpl bindingImpl =
            (BindingImpl)rtContext.getRuntimeEndpointInfo().getBinding();
        String bindingId = bindingImpl.getBindingId();

        switch (mi.getResponseType()) {
            case MessageStruct.CHECKED_EXCEPTION_RESPONSE:
                if(!(result instanceof java.lang.Exception)){
                    throw new SerializationException("exception.incorrectType", result.getClass().toString());
                }
                CheckedException ce = jm.getCheckedException(result.getClass());
                if(ce == null){
                    throw new SerializationException("exception.notfound", result.getClass().toString());
                }
                Object detail = getDetail(jm.getCheckedException(result.getClass()), result);
                JAXBBridgeInfo di = new JAXBBridgeInfo(model.getBridge(ce.getDetailType()), detail);

                if (bindingId.equals(javax.xml.ws.soap.SOAPBinding.SOAP11HTTP_BINDING)) {
                    SOAPRuntimeModel.createFaultInBody(result, null, di, im);
                } else if (bindingId.equals(javax.xml.ws.soap.SOAPBinding.SOAP12HTTP_BINDING)){
                    SOAPRuntimeModel.createSOAP12FaultInBody(result, null, null, di, im);
                }

                return im;
            case MessageStruct.UNCHECKED_EXCEPTION_RESPONSE:
                if (bindingId.equals(javax.xml.ws.soap.SOAPBinding.SOAP11HTTP_BINDING))
                    SOAPRuntimeModel.createFaultInBody(result, getActor(), null, im);
                else if (bindingId.equals(javax.xml.ws.soap.SOAPBinding.SOAP12HTTP_BINDING))
                    SOAPRuntimeModel.createSOAP12FaultInBody(result, null, null, null, im);
                return im;
        }

        SOAPBinding soapBinding = (SOAPBinding)jm.getBinding();
        Iterator<Parameter> iter = jm.getResponseParameters().iterator();
        while (iter.hasNext()) {
            Parameter param = iter.next();
            ParameterBinding paramBinding = param.getOutBinding();
            Object obj = createPayload(rtContext, param, data, result, soapBinding, paramBinding);
            if (paramBinding.isBody()) {
                im.setBody(new BodyBlock(obj));
            } else if (paramBinding.isHeader()) {
                im.addHeader(new HeaderBlock((JAXBBridgeInfo)obj));
            } else if (paramBinding.isAttachment()) {
                addAttachmentPart(rtContext, im, obj, param);
            }
        }
        return im;
    }

    private Object getDetail(CheckedException ce, Object exception) {
        if(ce.getExceptionType().equals(ExceptionType.UserDefined)){
            return createDetailFromUserDefinedException(ce, exception);
        }
        try {
            Method m = exception.getClass().getMethod("getFaultInfo");
            return m.invoke(exception);
        } catch(Exception e){
            throw new SerializationException(e);
        }
    }

    private Object createDetailFromUserDefinedException(CheckedException ce, Object exception) {
        Class detailBean = ce.getDetailBean();
        Field[] fields = detailBean.getDeclaredFields();
        try {
            Object detail = detailBean.newInstance();
            for(Field f : fields){
                Method em = exception.getClass().getMethod(getReadMethod(f));
                Method sm = detailBean.getMethod(getWriteMethod(f), em.getReturnType());
                sm.invoke(detail, em.invoke(exception));
            }
            return detail;
        } catch(Exception e){
            throw new SerializationException(e);
        }
    }

    private String getReadMethod(Field f){
        if(f.getType().isAssignableFrom(boolean.class))
            return "is" + StringUtils.capitalize(f.getName());
        return "get" + StringUtils.capitalize(f.getName());
    }

    private String getWriteMethod(Field f){
        return "set" + StringUtils.capitalize(f.getName());
    }

    /**
     * @return the actor
     */
    public String getActor() {
        return null;
    }

    /**
     * To be used by the incoming message on the server side to set the OUT
     * holders with Holder instance.
     *
     * @param data
     */
    private void createOUTHolders(Parameter param, Object[] data) {
        if(param.isWrapperStyle()){
            for(Parameter p : ((WrapperParameter)param).getWrapperChildren()){
                if(!p.isResponse() && p.isOUT())
                    data[p.getIndex()] = new Holder();
            }
            return;
        }
        //its BARE
        if (!param.isResponse() && param.isOUT())
            data[param.getIndex()] = new Holder();
    }
}
