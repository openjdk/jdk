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
package com.sun.xml.internal.ws.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.xml.internal.ws.developer.JAXWSProperties;
import com.sun.xml.internal.ws.encoding.soap.internal.AttachmentBlock;
import com.sun.xml.internal.ws.util.ByteArrayDataSource;

import static com.sun.xml.internal.ws.handler.HandlerChainCaller.IGNORE_FAULT_PROPERTY;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Iterator;
import javax.xml.ws.handler.MessageContext;
import javax.xml.ws.handler.MessageContext.Scope;
import javax.xml.namespace.QName;
import javax.xml.soap.AttachmentPart;
import javax.xml.soap.SOAPException;
import javax.activation.DataHandler;


/**
 * Utility to manipulate MessageContext properties
 *
 * @author WS Development Team
 */
public class MessageContextUtil {

    public static Integer getHttpStatusCode(MessageContext ctxt) {
        return (Integer)ctxt.get(MessageContext.HTTP_RESPONSE_CODE);
    }

    public static void setHttpStatusCode(MessageContext ctxt, Integer code) {
        ctxt.put(MessageContext.HTTP_RESPONSE_CODE, code);
        ctxt.setScope(MessageContext.HTTP_RESPONSE_CODE, Scope.APPLICATION);
    }

    public static void setQueryString(MessageContext ctxt, String queryString) {
        ctxt.put(MessageContext.QUERY_STRING, queryString);
        ctxt.setScope(MessageContext.QUERY_STRING, Scope.APPLICATION);
    }

    public static void setPathInfo(MessageContext ctxt, String pathInfo) {
        ctxt.put(MessageContext.PATH_INFO, pathInfo);
        ctxt.setScope(MessageContext.PATH_INFO, Scope.APPLICATION);
    }

    public static void setHttpExchange(MessageContext ctxt, HttpExchange exch) {
        ctxt.put(JAXWSProperties.HTTP_EXCHANGE, exch);
        ctxt.setScope(JAXWSProperties.HTTP_EXCHANGE, Scope.APPLICATION);
    }

    public static HttpExchange getHttpExchange(MessageContext ctxt) {
        return (HttpExchange)ctxt.get(JAXWSProperties.HTTP_EXCHANGE);
    }

    public static void setHttpRequestMethod(MessageContext ctxt, String method) {
        ctxt.put(MessageContext.HTTP_REQUEST_METHOD, method);
        ctxt.setScope(MessageContext.HTTP_REQUEST_METHOD, Scope.APPLICATION);
    }

    public static void setHttpRequestHeaders(MessageContext ctxt,
            Map<String, List<String>> headers) {
        ctxt.put(MessageContext.HTTP_REQUEST_HEADERS, headers);
        ctxt.setScope(MessageContext.HTTP_REQUEST_HEADERS, Scope.APPLICATION);
    }

    public static void setHttpResponseHeaders(MessageContext ctxt,
            Map<String, List<String>> headers) {
        ctxt.put(MessageContext.HTTP_RESPONSE_HEADERS, headers);
        ctxt.setScope(MessageContext.HTTP_RESPONSE_HEADERS, Scope.APPLICATION);
    }

    public static Map<String, List<String>> getHttpResponseHeaders(MessageContext ctxt) {
        return (Map<String, List<String>>)ctxt.get(MessageContext.HTTP_RESPONSE_HEADERS);
    }

    public static void setWsdlOperation(MessageContext ctxt, QName name) {
        ctxt.put(MessageContext.WSDL_OPERATION, name);
        ctxt.setScope(MessageContext.WSDL_OPERATION, Scope.APPLICATION);
    }

    private static Map<String, DataHandler> getMessageAttachments(MessageContext ctxt) {
        String property = MessageContext.INBOUND_MESSAGE_ATTACHMENTS;
        Boolean out = (Boolean)ctxt.get(MessageContext.MESSAGE_OUTBOUND_PROPERTY);
        if (out != null && out) {
            property = MessageContext.OUTBOUND_MESSAGE_ATTACHMENTS;
        }

        Object att = ctxt.get(property);
        if(att == null){
            Map<String, DataHandler> attMap = new HashMap<String, DataHandler>();
            ctxt.put(property, attMap);
            ctxt.setScope(property, Scope.APPLICATION);
            return attMap;
        }
        return (Map<String, DataHandler>)att;
    }

    public static void copyInboundMessageAttachments(MessageContext ctxt, Iterator<AttachmentPart> attachments) throws SOAPException {
        Map<String, DataHandler> attachMap = getMessageAttachments(ctxt);
        while(attachments.hasNext()){
            AttachmentPart ap = attachments.next();
            DataHandler dh = new DataHandler(new ByteArrayDataSource(ap.getRawContentBytes(), ap.getContentType()));
            attachMap.put(ap.getContentId(), dh);
        }
    }

    public static void addMessageAttachment(MessageContext ctxt, String cid, DataHandler dh){
        Map<String, DataHandler> attachMap = getMessageAttachments(ctxt);
        attachMap.put(cid, dh);
    }

    /*
     * See HandlerChainCaller for full details. When a ProtocolException
     * is caught from the handler chain, this method is used to tell
     * the runtime whether to use the fault in the current message or
     * use the exception and create a new message.
     */
    public static boolean ignoreFaultInMessage(MessageContext context) {
        if (context.get(IGNORE_FAULT_PROPERTY) == null) {
            return false;
        }
        return (Boolean) context.get(IGNORE_FAULT_PROPERTY);
    }

}
