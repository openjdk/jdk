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

import java.io.OutputStream;
import com.sun.xml.internal.ws.server.RuntimeEndpointInfo;
import com.sun.xml.internal.ws.spi.runtime.ClientTransportFactory;
import com.sun.xml.internal.ws.spi.runtime.WSConnection;

import java.util.Map;

/**
 * @author WS Development Team
 */
public class LocalClientTransportFactory implements ClientTransportFactory {
    private RuntimeEndpointInfo endpointInfo;
    private OutputStream logStream;

    //this class is used primarily for debugging purposes
    public LocalClientTransportFactory(RuntimeEndpointInfo endpointInfo) {
        this(endpointInfo, null);
    }

    public LocalClientTransportFactory(RuntimeEndpointInfo endpointInfo,
        OutputStream logStream) {
        this.endpointInfo = endpointInfo;
        this.logStream = logStream;
    }

    public WSConnection create() {
        return create(null);
    }

    public WSConnection create(Map<String, Object> context) {
        return new LocalClientTransport(endpointInfo, logStream);
    }

}
