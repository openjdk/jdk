/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.xml.internal.ws.api.server;

import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;

import javax.xml.namespace.QName;

/**
 * Resolves port address for an endpoint. A WSDL may contain multiple
 * endpoints, and some of the endpoints may be packaged in a single WAR file.
 * If an endpoint is serving the WSDL, it would be nice to fill the port addresses
 * of other endpoints in the WAR.
 *
 * <p>
 * This interface is implemented by the caller of
 * {@link SDDocument#writeTo} method so
 * that the {@link SDDocument} can correctly fills the addresses of known
 * endpoints.
 *
 *
 * @author Jitendra Kotamraju
 */
public abstract class PortAddressResolver {
    /**
     * Gets the endpoint address for a WSDL port
     *
     * @param serviceName
     *       WSDL service name(wsd:service in WSDL) for which address is needed. Always non-null.
     * @param portName
     *       WSDL port name(wsdl:port in WSDL) for which address is needed. Always non-null.
     * @return
     *      The address needs to be put in WSDL for port element's location
     *      attribute. Can be null. If it is null, existing port address
     *      is written as it is (without any patching).
     */
    public abstract @Nullable String getAddressFor(@NotNull QName serviceName, @NotNull String portName);

    /**
     * Gets the endpoint address for a WSDL port
     *
     * @param serviceName
     *       WSDL service name(wsd:service in WSDL) for which address is needed. Always non-null.
     * @param portName
     *       WSDL port name(wsdl:port in WSDL) for which address is needed. Always non-null.
     * @param currentAddress
     *       Whatever current address specified for the port in the WSDL
     * @return
     *      The address needs to be put in WSDL for port element's location
     *      attribute. Can be null. If it is null, existing port address
     *      is written as it is (without any patching).
     */
    public @Nullable String getAddressFor(@NotNull QName serviceName, @NotNull String portName, String currentAddress) {
        return getAddressFor(serviceName, portName);
    }
}
