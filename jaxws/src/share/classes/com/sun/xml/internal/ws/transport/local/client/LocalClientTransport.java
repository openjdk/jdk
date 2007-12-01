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

package com.sun.xml.internal.ws.transport.local.client;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import com.sun.xml.internal.ws.client.ClientTransportException;
import com.sun.xml.internal.ws.handler.MessageContextImpl;
import com.sun.xml.internal.ws.server.RuntimeEndpointInfo;
import com.sun.xml.internal.ws.server.Tie;
import com.sun.xml.internal.ws.spi.runtime.WSConnection;
import com.sun.xml.internal.ws.spi.runtime.WebServiceContext;
import com.sun.xml.internal.ws.transport.WSConnectionImpl;
import com.sun.xml.internal.ws.util.localization.Localizable;
import com.sun.xml.internal.ws.util.ByteArrayBuffer;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.net.HttpURLConnection;
import java.net.ProtocolException;

import com.sun.xml.internal.ws.transport.local.server.LocalConnectionImpl;
import com.sun.xml.internal.ws.transport.local.LocalMessage;

import static com.sun.xml.internal.ws.developer.JAXWSProperties.CONTENT_NEGOTIATION_PROPERTY;

import javax.xml.ws.http.HTTPException;

/**
 * @author WS Development Team
 */
public class LocalClientTransport extends WSConnectionImpl {

    private RuntimeEndpointInfo endpointInfo;
    private Tie tie = new Tie();
    LocalMessage lm = new LocalMessage();

    //this class is used primarily for debugging purposes
    public LocalClientTransport(RuntimeEndpointInfo endpointInfo) {
        this(endpointInfo, null);
    }

    public LocalClientTransport(RuntimeEndpointInfo endpointInfo,
                                OutputStream logStream) {
        this.endpointInfo = endpointInfo;
        debugStream = logStream;
    }


    @Override
    public OutputStream getOutput() {
        try {
            lm.setOutput(new ByteArrayBuffer());
            return lm.getOutput();
        }
        catch (Exception ex) {
            throw new ClientTransportException("local.client.failed",ex);
        }
    }

    private static void checkMessageContentType(WSConnection con, boolean response) {
        String negotiation = System.getProperty(CONTENT_NEGOTIATION_PROPERTY, "none").intern();
        String contentType = con.getHeaders().get("Content-Type").get(0);

        // Use indexOf() to handle Multipart/related types
        if (negotiation == "none") {
            // OK only if XML
            if (contentType.indexOf("text/xml") < 0 &&
                   contentType.indexOf("application/soap+xml") < 0 &&
                   contentType.indexOf("application/xop+xml") < 0)
            {
                throw new RuntimeException("Invalid content type '" + contentType
                    + "' with content negotiation set to '" + negotiation + "'.");
            }
        }
        else if (negotiation == "optimistic") {
            // OK only if FI
            if (contentType.indexOf("application/fastinfoset") < 0 &&
                   contentType.indexOf("application/soap+fastinfoset") < 0)
            {
                throw new RuntimeException("Invalid content type '" + contentType
                    + "' with content negotiation set to '" + negotiation + "'.");
            }
        }
        else if (negotiation == "pessimistic") {
            // OK if FI request is anything and response is FI
            if (response &&
                    contentType.indexOf("application/fastinfoset") < 0 &&
                    contentType.indexOf("application/soap+fastinfoset") < 0)
            {
                throw new RuntimeException("Invalid content type '" + contentType
                    + "' with content negotiation set to '" + negotiation + "'.");
            }
        }
    }

    @Override
    public void closeOutput() {
        super.closeOutput();
        WSConnection con = new LocalConnectionImpl(lm);

        // Copy headers for content negotiation
        con.setHeaders(getHeaders());

        // Check request content type based on negotiation property
        checkMessageContentType(this, false);

        try {
            // Set a MessageContext per invocation
            WebServiceContext wsContext = endpointInfo.getWebServiceContext();
            wsContext.setMessageContext(new MessageContextImpl());
            tie.handle(con, endpointInfo);

            checkMessageContentType(con, true);
        }
        catch (Exception ex) {
            new ProtocolException("Server side Exception:" + ex);
        }
    }

    @Override
    public InputStream getInput() {
        try {
            return lm.getOutput().newInputStream();
        }
        catch (Exception ex) {
            throw new ClientTransportException("local.client.failed",ex);
        }
    }

    @Override
    public void setHeaders(Map<String, List<String>> headers) {
        lm.setHeaders(headers);
    }

    @Override
    public Map<String, List<String>> getHeaders() {
        return lm.getHeaders();
    }

}
