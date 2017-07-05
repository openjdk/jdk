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
import com.sun.xml.internal.ws.util.ASCIIUtility;

import javax.activation.DataHandler;
import javax.xml.bind.attachment.AttachmentUnmarshaller;
import javax.xml.soap.AttachmentPart;
import javax.xml.soap.SOAPException;
import javax.xml.ws.WebServiceException;
import java.util.Map;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.net.URLDecoder;

/**
 * @author Vivek Pandey
 *
 * AttachmentUnmarshaller, will be called by jaxb unmarshaller to process xop package.
 */
public class JAXWSAttachmentUnmarshaller extends AttachmentUnmarshaller {

    /**
     *
     */
    public JAXWSAttachmentUnmarshaller(){
    }

    /**
     *
     * @param cid
     * @return a <code>DataHandler</code> for the attachment
     */
    public DataHandler getAttachmentAsDataHandler(String cid) {
        AttachmentBlock ab = attachments.get(decodeCid(cid));
        if(ab == null)
            //TODO localize exception message
            throw new IllegalArgumentException("Attachment corresponding to "+cid+ " not found!");
        return ab.asDataHandler();
    }

    /**
     *
     * @param cid
     * @return the attachment as a <code>byte[]</code>
     */
    public byte[] getAttachmentAsByteArray(String cid) {
        AttachmentBlock ab = attachments.get(decodeCid(cid));
        if(ab == null)
            throw new IllegalArgumentException("Attachment corresponding to "+cid+ " not found!");
        return ab.asByteArray();
    }

    /**
     *
     * @return true if XOPPackage
     */
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

    /**
     * Must be called before marshalling any data.
     * @param attachments Reference to Map from InternalMessage
     */
    public void setAttachments(Map<String, AttachmentBlock> attachments){
        this.attachments = attachments;
    }

    /**
     *
     * @param cid
     * @return
     */
    private String decodeCid(String cid){
        if(cid.startsWith("cid:"))
            cid = cid.substring(4, cid.length());
//        try {       // Added fix for CR 6456442
            //return "<"+URLDecoder.decode(cid, "UTF-8")+">";
            return "<"+cid+">";
//        } catch (UnsupportedEncodingException e) {
//            throw new WebServiceException(e);
//        }
    }

    private Map<String, AttachmentBlock> attachments;
    private boolean isXOP;
}
