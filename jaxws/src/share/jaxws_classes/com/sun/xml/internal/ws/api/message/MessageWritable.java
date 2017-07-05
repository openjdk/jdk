/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.ws.api.message;

import java.io.IOException;
import java.io.OutputStream;

import javax.xml.ws.soap.MTOMFeature;

import com.oracle.webservices.internal.api.message.ContentType;

/**
 * A Message implementation may implement this interface as an alternative way to write the
 * message into the OutputStream.
 *
 * @author shih-chang.chen@oracle.com
 */
public interface MessageWritable {

    /**
     * Gets the Content-type of this message.
     *
     * @return The MIME content type of this message
     */
    ContentType getContentType();

    /**
     * Writes the XML infoset portion of this MessageContext
     * (from &lt;soap:Envelope> to &lt;/soap:Envelope>).
     *
     * @param out
     *      Must not be null. The caller is responsible for closing the stream,
     *      not the callee.
     *
     * @return
     *      The MIME content type of this message (such as "application/xml").
     *      This information is often ncessary by transport.
     *
     * @throws IOException
     *      if a {@link OutputStream} throws {@link IOException}.
     */
    ContentType writeTo( OutputStream out ) throws IOException;

    /**
     * Passes configuration information to this message to ensure the proper
     * wire format is created. (from &lt;soap:Envelope> to &lt;/soap:Envelope>).
     *
     * @param mtomFeature
     *            The standard <code>WebServicesFeature</code> for specifying
     *            the MTOM enablement and possibly threshold for the endpoint.
     *            This value may be <code>null</code>.
     */
    void setMTOMConfiguration(final MTOMFeature mtomFeature);
}
