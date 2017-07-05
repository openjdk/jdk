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

package com.sun.xml.internal.ws.spi.runtime;

import javax.xml.stream.XMLStreamWriter;
import java.io.OutputStream;

/**
 * SOAPEncoder to encode JAXWS runtime objects. Using this caller could optimize
 * SOAPMessage creation and not use JAXWS default encoding of SOAPMessage
 */
public interface InternalSoapEncoder {
    /**
     *  Writes an object to output stream
     * @param obj payload to be written
     * @param messageInfo object containing informations to help JAXWS write the objects. Get
     *        this object from SOAPMessageContext.getMessageInfo()
     * @param out stream to write to
     * @param mtomCallback callback is called if there any attachments while
     *                     encoding the object
     */
    public void write(Object obj, Object messageInfo, OutputStream out, MtomCallback mtomCallback);

    /**
     * Writes an object to output stream
     * @param obj payload to be written
     * @param messageInfo object containing informations to help JAXWS write the objects. Get
     *        this object from SOAPMessageContext.getMessageInfo()
     * @param out stream writer to write to
     * @param mtomCallback callback is called if there any attachments while
     *                     encoding the object
     */
    public void write(Object obj, Object messageInfo, XMLStreamWriter out, MtomCallback mtomCallback);
}
