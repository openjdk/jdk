/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.ws.client;

import com.sun.xml.internal.ws.api.server.Container;
import com.sun.xml.internal.ws.model.wsdl.WSDLServiceImpl;
import java.util.Map;
import javax.xml.namespace.QName;
import com.sun.org.glassfish.gmbal.AMXMetadata;
import com.sun.org.glassfish.gmbal.Description;
import com.sun.org.glassfish.gmbal.ManagedAttribute;
import com.sun.org.glassfish.gmbal.ManagedObject;
import java.net.URL;

/**
 * @author Harold Carr
 */
@ManagedObject
@Description("Metro Web Service client")
@AMXMetadata(type="WSClient")
public final class MonitorRootClient extends com.sun.xml.internal.ws.server.MonitorBase {

    private final Stub stub;

    MonitorRootClient(final Stub stub) {
        this.stub = stub;
    }

    /*
    private static final Logger logger = Logger.getLogger(
        com.sun.xml.internal.ws.util.Constants.LoggingDomain + ".client.stub");
    */

    //
    // From WSServiceDelegate
    //

    @ManagedAttribute
    private Container getContainer() { return stub.owner.getContainer(); }

    @ManagedAttribute
    private Map<QName, PortInfo> qnameToPortInfoMap() { return stub.owner.getQNameToPortInfoMap(); }

    @ManagedAttribute
    private QName serviceName() { return stub.owner.getServiceName(); }

    @ManagedAttribute
    private Class serviceClass() { return stub.owner.getServiceClass(); }

    @ManagedAttribute
    private URL wsdlDocumentLocation() { return stub.owner.getWSDLDocumentLocation(); }

    @ManagedAttribute
    private WSDLServiceImpl wsdlService() { return stub.owner.getWsdlService(); }



}
