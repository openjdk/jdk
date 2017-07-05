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

import com.sun.xml.internal.ws.encoding.soap.internal.AttachmentBlock;
import com.sun.xml.internal.ws.handler.HandlerContext;
import com.sun.xml.internal.ws.handler.MessageContextUtil;
import com.sun.xml.internal.ws.spi.runtime.MtomCallback;
import com.sun.xml.internal.ws.util.ByteArrayDataSource;

import javax.activation.DataHandler;
import javax.xml.bind.attachment.AttachmentMarshaller;
import javax.xml.ws.WebServiceException;
import javax.xml.ws.handler.MessageContext;
import java.io.ByteArrayInputStream;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * @author WS Development Team
 *
 *
 */
public class JAXWSAttachmentMarshaller extends AttachmentMarshaller {

    public JAXWSAttachmentMarshaller(boolean isXOP){
        this.isXOP = isXOP;
    }

    public boolean isXOPPackage() {
        return isXOP;
    }

    /**
     * set the XOP package if the incoming SOAP envelope is a XOP package
     * @param isXOP
     */
    public void setXOPPackage(boolean isXOP){
        this.isXOP = isXOP;
    }

    /*
     * @see AttachmentMarshaller#addMtomAttachment(DataHandler, String, String)
     */
    public String addMtomAttachment(DataHandler data, String elementNamespace, String elementName) {
        if(!isXOP)
            return null;
        String cid = encodeCid(elementNamespace);
        if(cid != null){
            String cidBracket = '<' + cid + '>';
            attachments.put(cidBracket, AttachmentBlock.fromDataHandler(cidBracket,data));
            addToMessageContext(cidBracket, data);
            if(mtomCallback != null)
                mtomCallback.addedMtomAttachment(cidBracket, data, elementNamespace, elementName);
            isXopped = true;
            cid = "cid:"+cid;
        }
        return cid;
    }

    /**
     * <p>Consider binary <code>data</code> for optimized binary storage as an attachment.
     * <p/>
     * <p>Since content type is not known, the attachment's MIME content type must be set to "application/octet-stream".</p>
     * <p/>
     * <p/>
     * The <code>elementNamespace</code> and <code>elementLocalName</code>
     * parameters provide the
     * context that contains the binary data. This information could
     * be used by the MIME-based package processor to determine if the
     * binary data should be inlined or optimized as an attachment.
     *
     * @param data             represents the data to be attached. Must be non-null. The actual data region is
     *                         specified by <tt>(data,offset,len)</tt> tuple.
     * @param mimeType         If the data has an associated MIME type known to JAXB, that is passed
     *                         as this parameter. If none is known, "application/octet-stream".
     *                         This parameter may never be null.
     * @param elementNamespace the namespace URI of the element that encloses the base64Binary data.
     *                         Can be empty but never null.
     * @param elementLocalName The local name of the element. Always a non-null valid string.
     * @return content-id URI, cid, to the attachment containing
     *         <code>data</code> or null if data should be inlined.
     * @see #addMtomAttachment(javax.activation.DataHandler, String, String)
     */
    public String addMtomAttachment(byte[] data, int offset, int len, String mimeType, String elementNamespace, String elementLocalName) {
        if(!isXOP)
            return null;

        //TODO: With performance results we need to find out what length would need optimization
        if(len < mtomThresholdValue)
            return null;

        //this will not be needed if saaj exposes api that takes
        //byte[] actualData = getActualData(data, offset, len);

        String cid = encodeCid(elementNamespace);
        if(cid != null){
            String cidBracket = '<' + cid + '>';
            DataHandler dh = new DataHandler(new ByteArrayDataSource(data, offset, len, "application/octet-stream"));
            attachments.put(cidBracket, AttachmentBlock.fromDataHandler(cidBracket,dh));
            addToMessageContext(cidBracket, dh);
            if(mtomCallback != null)
                mtomCallback.addedMtomAttachment(cidBracket, dh, elementNamespace, elementLocalName);
            isXopped = true;
            cid = "cid:"+cid;
        }
        return cid;

    }

    /*
     * @see AttachmentMarshaller#addSwaRefAttachment(DataHandler)
     */
    public String addSwaRefAttachment(DataHandler data) {
        String cid = encodeCid(null);
        if(cid != null){
            String cidBracket = '<' + cid + '>';
            attachments.put(cidBracket, AttachmentBlock.fromDataHandler("<"+cid+">", data));
            addToMessageContext(cidBracket, data);
            isXopped = false;
            cid = "cid:"+cid;
        }
        return cid;
    }

    private void addToMessageContext(String cid, DataHandler dh){
        if(hc == null)
            return;
        MessageContext ctxt = hc.getMessageContext();
        if(ctxt == null)
            return;
        MessageContextUtil.addMessageAttachment(ctxt, cid, dh);
    }

    /**
     *
     * @param ns
     * @return
     */
    private String encodeCid(String ns){
        String cid="example.jaxws.sun.com";
        String name = UUID.randomUUID()+"@";
        if(ns != null && (ns.length() > 0)){
            try {
                URI uri = new URI(ns);
                String host = uri.toURL().getHost();
                cid = host;
            } catch (URISyntaxException e) {
                e.printStackTrace();
                return null;
            } catch (MalformedURLException e) {
                try {
                    cid = URLEncoder.encode(ns, "UTF-8");
                } catch (UnsupportedEncodingException e1) {
                    throw new WebServiceException(e);
                }
            }
        }
        return name + cid;
    }

    /**
     * Must be called before marshalling any data.
     * @param attachments Reference to Map from InternalMessage
     */
    public void setAttachments(Map<String, AttachmentBlock> attachments){
        this.attachments = attachments;
        isXopped = false;
    }

    public void setHandlerContaxt(HandlerContext hc){
        this.hc = hc;
    }

    /**
     *
     * @return true if Xopped, false otherwise
     */
    public boolean isXopped() {
        return isXopped;
    }

    public void setMtomThresholdValue(Integer mtomThresholdValue) {
        if((mtomThresholdValue != null) && (mtomThresholdValue >=0))
            this.mtomThresholdValue = mtomThresholdValue;
    }

    public void setMtomCallback(MtomCallback mtomCallback){
        this.mtomCallback = mtomCallback;
    }

    private MtomCallback mtomCallback;
    private boolean isXOP;
    private boolean isXopped;
    private Map<String, AttachmentBlock> attachments;
    private HandlerContext hc;
    private int mtomThresholdValue = 1000;

}
