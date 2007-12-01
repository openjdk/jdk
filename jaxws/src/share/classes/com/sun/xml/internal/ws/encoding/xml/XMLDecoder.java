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

package com.sun.xml.internal.ws.encoding.xml;
import javax.xml.stream.XMLStreamReader;

import com.sun.xml.internal.ws.pept.encoding.Decoder;
import com.sun.xml.internal.ws.pept.ept.MessageInfo;
import com.sun.xml.internal.ws.encoding.jaxb.*;
import com.sun.xml.internal.ws.encoding.soap.internal.BodyBlock;
import com.sun.xml.internal.ws.encoding.soap.internal.InternalMessage;

import static javax.xml.stream.XMLStreamReader.*;
import javax.xml.soap.SOAPMessage;
import java.util.logging.Logger;



/**
 * @author WS Development Team
 */
public class XMLDecoder implements Decoder {

    private static final Logger logger = Logger.getLogger(
        com.sun.xml.internal.ws.util.Constants.LoggingDomain + ".xml.decoder");

    /* (non-Javadoc)
     * @see com.sun.pept.encoding.Decoder#decode(com.sun.pept.ept.MessageInfo)
     */
    public void decode(MessageInfo arg0) {
        throw new UnsupportedOperationException();
    }

    /* (non-Javadoc)
     * @see com.sun.pept.encoding.Decoder#receieveAndDecode(com.sun.pept.ept.MessageInfo)
     */
    public void receiveAndDecode(MessageInfo arg0) {
        throw new UnsupportedOperationException();
    }

    /**
     * parses and binds body from xmlMessage.
     * @param xmlMessage
     * @param messageInfo
     * @return InternalMessage representation of xmlMessage
     */
    public InternalMessage toInternalMessage(XMLMessage xmlMessage,
                    MessageInfo messageInfo) {
            return null;
    }

    /**
     * Parses and binds xmlMessage.
     * @param xmlMessage
     * @param internalMessage
     * @param messageInfo
     *
     */
    public InternalMessage toInternalMessage(XMLMessage xmlMessage,
            InternalMessage internalMessage, MessageInfo messageInfo) {
        return null;
    }

    public SOAPMessage toSOAPMessage(MessageInfo messageInfo) {
        return null;
    }

    public void toMessageInfo(InternalMessage internalMessage, MessageInfo messageInfo) { }

    public void decodeDispatchMethod(XMLStreamReader reader, InternalMessage request, MessageInfo messageInfo) {
    }


    /*
    *
    */
   protected void convertBodyBlock(InternalMessage request, MessageInfo messageInfo) {
       BodyBlock bodyBlock = request.getBody();
       if (bodyBlock != null) {
           Object value = bodyBlock.getValue();
           if (value instanceof JAXBBeanInfo) {
               System.out.println("******* NOT HANDLED JAXBBeanInfo ***********");
           } else if (value instanceof XMLMessage) {
               XMLMessage xmlMessage = (XMLMessage)value;
               //XMLStreamReader reader = SourceReaderFactory.createSourceReader(source, true);
               //XMLStreamReaderUtil.nextElementContent(reader);
               //decodeBodyContent(reader, request, messageInfo);
           } else {
               System.out.println("****** Unknown type in BodyBlock ***** "+value.getClass());
           }
       }
   }

}
