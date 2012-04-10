/*
 * Copyright (c) 1997, 2011, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.org.jvnet.ws.message;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;

import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPMessage;

/**
 * MessageContext represents a container of a SOAP message and all the properties
 * including the transport headers.
 *
 * MessageContext is a composite {@link PropertySet} that combines properties exposed from multiple
 * {@link PropertySet}s into one.
 *
 * <p>
 * This implementation allows one {@link PropertySet} to assemble
 * all properties exposed from other "satellite" {@link PropertySet}s.
 * (A satellite may itself be a {@link DistributedPropertySet}, so
 * in general this can form a tree.)
 *
 * @author shih-chang.chen@oracle.com
 */
public interface MessageContext extends DistributedPropertySet {
    /**
     * Gets the SAAJ SOAPMessage representation of the SOAP message.
     *
     * @return The SOAPMessage
     */
    SOAPMessage getSOAPMessage() throws SOAPException;

    /**
     * Adds the {@link PropertySet}
     *
     * @param satellite the PropertySet
     */
    void addSatellite(PropertySet satellite);

    /**
     * Removes the {@link PropertySet}
     *
     * @param satellite the PropertySet
     */
    void removeSatellite(PropertySet satellite);

    /**
     * Copies all the {@link PropertySet} of this MessageContext into the other MessageContext
     *
     * @param otherMessageContext the MessageContext
     */
    void copySatelliteInto(MessageContext otherMessageContext);

    /**
     * Gets the {@link PropertySet}
     *
     * @param satellite the PropertySet type
     */
    <T extends PropertySet> T getSatellite(Class<T> satelliteClass);

    /**
     * Writes the XML infoset portion of this MessageContext
     * (from &lt;soap:Envelope> to &lt;/soap:Envelope>).
     *
     * @param out
     *      Must not be null. The caller is responsible for closing the stream,
     *      not the callee.
     *
     * @return
     *      The MIME content type of the encoded message (such as "application/xml").
     *      This information is often ncessary by transport.
     *
     * @throws IOException
     *      if a {@link OutputStream} throws {@link IOException}.
     */
    //TODO chen: wait for DISI
//    ContentType writeTo( OutputStream out ) throws IOException;

    /**
     * The version of {@link #writeTo(OutputStream)}
     * that writes to NIO {@link ByteBuffer}.
     *
     * <p>
     * TODO: for the convenience of implementation, write
     * an adapter that wraps {@link WritableByteChannel} to {@link OutputStream}.
     */
    //TODO chen: wait for DISI
//    ContentType writeTo( WritableByteChannel buffer );
}
