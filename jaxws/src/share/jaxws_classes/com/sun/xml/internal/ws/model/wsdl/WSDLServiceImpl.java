/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.ws.model.wsdl;

import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;
import com.sun.xml.internal.ws.api.model.wsdl.WSDLPort;
import com.sun.xml.internal.ws.api.model.wsdl.WSDLService;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamReader;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Implementation of {@link WSDLService}
 *
 * @author Vivek Pandey
 */
public final class WSDLServiceImpl extends AbstractExtensibleImpl implements WSDLService {
    private final QName name;
    private final Map<QName, WSDLPortImpl> ports;
    private final WSDLModelImpl parent;

    public WSDLServiceImpl(XMLStreamReader xsr,WSDLModelImpl parent, QName name) {
        super(xsr);
        this.parent = parent;
        this.name = name;
        ports = new LinkedHashMap<QName,WSDLPortImpl>();
    }

    public @NotNull
    WSDLModelImpl getParent() {
        return parent;
    }

    public QName getName() {
        return name;
    }

    public WSDLPortImpl get(QName portName) {
        return ports.get(portName);
    }

    public WSDLPort getFirstPort() {
        if(ports.isEmpty())
            return null;
        else
            return ports.values().iterator().next();
    }

    public Iterable<WSDLPortImpl> getPorts(){
        return ports.values();
    }

    /**
    * gets the first port in this service which matches the portType
    */
    public @Nullable
    WSDLPortImpl getMatchingPort(QName portTypeName){
        for(WSDLPortImpl port : getPorts()){
            QName ptName = port.getBinding().getPortTypeName();
            assert (ptName != null);
            if(ptName.equals(portTypeName))
                return port;
        }
        return null;
    }

    /**
     * Populates the Map that holds port name as key and {@link WSDLPort} as the value.
     *
     * @param portName Must be non-null
     * @param port     Must be non-null
     * @throws NullPointerException if either opName or ptOp is null
     */
    public void put(QName portName, WSDLPortImpl port) {
        if (portName == null || port == null)
            throw new NullPointerException();
        ports.put(portName, port);
    }

    void freeze(WSDLModelImpl root) {
        for (WSDLPortImpl port : ports.values()) {
            port.freeze(root);
        }
    }
}
