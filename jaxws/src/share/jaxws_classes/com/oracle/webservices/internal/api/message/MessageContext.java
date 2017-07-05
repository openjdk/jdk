/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 1997-2012 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * http://glassfish.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */

package com.oracle.webservices.internal.api.message;

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
    SOAPMessage getAsSOAPMessage() throws SOAPException;

    /**
     * Gets the SAAJ SOAPMessage representation of the SOAP message.
     * @deprecated use getAsSOAPMessage
     * @return The SOAPMessage
     */
    SOAPMessage getSOAPMessage() throws SOAPException;

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
    ContentType writeTo( OutputStream out ) throws IOException;

    /**
     * The version of {@link #writeTo(OutputStream)}
     * that writes to NIO {@link ByteBuffer}.
     *
     * <p>
     * TODO: for the convenience of implementation, write
     * an adapter that wraps {@link WritableByteChannel} to {@link OutputStream}.
     */
//  ContentType writeTo( WritableByteChannel buffer );

    /**
     * Gets the Content-type of this message. For an out-bound message that this getContentType()
     * method returns a null, the Content-Type can be determined only by calling the writeTo
     * method to write the MessageContext to an OutputStream.
     *
     * @return The MIME content type of this message
     */
    ContentType getContentType();
}
