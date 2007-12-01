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

import javax.xml.bind.JAXBContext;
import javax.xml.ws.LogicalMessage;
import javax.xml.transform.Source;
import com.sun.xml.internal.ws.encoding.xml.XMLMessage;

/**
 * Implementation of LogicalMessage that is used in the
 * XML/HTTP binding. It is similar to LogicalMessageImpl
 * except that the context object passed in is an
 * {@link XMLHandlerContext} rather than a {@link HandlerContext}.
 *
 * @see LogicalMessageImpl
 * @see XMLHandlerContext
 * @see XMLLogicalMessageContextImpl
 *
 * @author WS Development Team
 */
public class XMLLogicalMessageImpl implements LogicalMessage {

    private XMLHandlerContext ctxt;

    public XMLLogicalMessageImpl(XMLHandlerContext ctxt) {
        this.ctxt = ctxt;
    }

    /*
     * Gets the source from XMLMessage. XMLMessage gives a copy of existing
     * data
     */
    public Source getPayload() {
        XMLMessage xmlMessage = ctxt.getXMLMessage();
        return xmlMessage.getPayload();
    }

    /*
     * Sets the Source as payload in XMLMessage
     */
    public void setPayload(Source source) {
        XMLMessage xmlMessage = ctxt.getXMLMessage();
        xmlMessage = new XMLMessage(source, xmlMessage.getAttachments(), xmlMessage.useFastInfoset());
        ctxt.setXMLMessage(xmlMessage);
    }

    /*
     * Gets XMLMessage data as JAXB bean
     */
    public Object getPayload(JAXBContext jaxbContext) {
        XMLMessage xmlMessage = ctxt.getXMLMessage();
        return xmlMessage.getPayload(jaxbContext);
    }

    /*
     * Sets JAXB bean into XMLMessage
     */
    public void setPayload(Object bean, JAXBContext jaxbContext) {
        XMLMessage xmlMessage = ctxt.getXMLMessage();
        xmlMessage = new XMLMessage(bean, jaxbContext, xmlMessage.getAttachments(), xmlMessage.useFastInfoset());
        ctxt.setXMLMessage(xmlMessage);
    }

    public XMLHandlerContext getHandlerContext() {
        return ctxt;
    }

}
