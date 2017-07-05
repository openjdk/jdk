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
package com.sun.xml.internal.ws.encoding;

import com.sun.xml.internal.ws.pept.ept.MessageInfo;
import com.sun.xml.internal.bind.api.AccessorException;
import com.sun.xml.internal.bind.api.JAXBRIContext;
import com.sun.xml.internal.bind.api.RawAccessor;
import com.sun.xml.internal.ws.encoding.soap.SerializationException;
import com.sun.xml.internal.ws.server.RuntimeContext;
import com.sun.xml.internal.ws.model.RuntimeModel;

import javax.xml.bind.JAXBException;
import java.util.Map;

/**
 * @author Vivek Pandey
 *
 * Base Abstract class to be used for encoding-decoding a given binding.
 */
public abstract class EncoderDecoderBase {
    /**
     * Creates an internal message based thats binding dependent.
     *
     * @param messageInfo
     * @return the internal message given a messageInfo
     */
    public Object toInternalMessage(MessageInfo messageInfo) {
        throw new UnsupportedOperationException("Not Implementated!");
    }

    /**
     * Fills in MessageInfo from binding dependent internal message.
     *
     * @param internalMessage
     * @param messageInfo
     */
    public void toMessageInfo(Object internalMessage, MessageInfo messageInfo) {
        throw new UnsupportedOperationException("Not Implementated!");
    }

    /**
     * Get the wrapper child value from a jaxb style wrapper bean.
     *
     * @param context
     *            RuntimeContext to be passed by the encoder/decoder processing
     *            SOAP message during toMessageInfo()
     * @param wrapperBean
     *            The wrapper bean instance
     * @param nsURI
     *            namespace of the wrapper child property
     * @param localName
     *            local name of the wrapper child property
     * @return The wrapper child
     *
     */
    protected Object getWrapperChildValue(RuntimeContext context, Object wrapperBean, String nsURI,
            String localName) {
        if (wrapperBean == null)
            return null;

        RawAccessor ra = getRawAccessor(context, wrapperBean.getClass(), nsURI, localName);
        try {
            return ra.get(wrapperBean);
        } catch (AccessorException e) {
            throw new SerializationException(e);
        }
    }

    /**
     * Set the wrapper child value from a jaxb style wrapper bean.
     *
     * @param context
     *            context RuntimeContext to be passed by the encoder/decoder
     *            processing SOAP message during toMessageInfo()
     * @param wrapperBean
     *            The wrapper bean instance
     * @param value
     *            value of the wrapper child property
     * @param nsURI
     *            namespace of the wrapper child property
     * @param localName
     *            localName local name of the wrapper child property
     */
    protected void setWrapperChildValue(RuntimeContext context, Object wrapperBean, Object value,
            String nsURI, String localName) {
        if (wrapperBean == null)
            return;
        RawAccessor ra = getRawAccessor(context, wrapperBean.getClass(), nsURI, localName);
        try {
            ra.set(wrapperBean, value);
        } catch (AccessorException e) {
            throw new SerializationException(e);
        }
    }

    private RawAccessor getRawAccessor(RuntimeContext context, Class wrapperBean, String nsURI, String localName){
        RuntimeModel model = context.getModel();
        Map<Integer, RawAccessor> map = model.getRawAccessorMap();
        int id  = getHashCode(wrapperBean, nsURI, localName);
        RawAccessor ra = map.get(id);
        if(ra == null){
            JAXBRIContext jaxbContext = model.getJAXBContext();
            try {
                ra = jaxbContext.getElementPropertyAccessor(wrapperBean, nsURI,
                        localName);
                map.put(id, ra);
            } catch (JAXBException e) {
                throw new SerializationException(e);
            }
        }
        return ra;
    }

    private int getHashCode(Class bean, String uri, String pfix){
        return bean.hashCode()+uri.hashCode()+pfix.hashCode();
    }
}
