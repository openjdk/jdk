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

package com.sun.xml.internal.ws.transport.http.client;

import java.io.OutputStream;
import com.sun.xml.internal.ws.spi.runtime.WSConnection;
import java.util.Map;

import javax.xml.ws.soap.SOAPBinding;

import static com.sun.xml.internal.ws.client.BindingProviderProperties.BINDING_ID_PROPERTY;
import com.sun.xml.internal.ws.spi.runtime.ClientTransportFactory;
import java.util.HashMap;

/**
 * @author WS Development Team
 */
public class HttpClientTransportFactory implements ClientTransportFactory {


    public HttpClientTransportFactory() {
        this(null);
    }

    public HttpClientTransportFactory(OutputStream logStream) {
        _logStream = logStream;
    }

    /*
    public WSConnection create() {
        Map<String, Object> context = new HashMap<String, Object>();
        context.put(BINDING_ID_PROPERTY, SOAPBinding.SOAP11HTTP_BINDING);

        return new HttpClientTransport(_logStream, context);
    }
     */

    /**
     * Binding Id, Endpoint address and other metadata is in the property bag
     */
    public WSConnection create(Map<String, Object> context) {
        return new HttpClientTransport(_logStream, context);
    }

    private OutputStream _logStream;
}
