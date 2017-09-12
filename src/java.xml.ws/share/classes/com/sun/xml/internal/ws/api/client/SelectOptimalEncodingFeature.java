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

package com.sun.xml.internal.ws.api.client;

import com.sun.xml.internal.ws.api.FeatureConstructor;

import javax.xml.ws.WebServiceFeature;

import com.sun.org.glassfish.gmbal.ManagedAttribute;
import com.sun.org.glassfish.gmbal.ManagedData;

/**
 * Client side feature to enable or disable the selection of the optimal
 * encoding by the client when sending outbound messages.
 * <p>
 * The following describes the affects of this feature with respect
 * to being enabled or disabled:
 * <ul>
 *  <li> ENABLED: In this Mode, the most optimal encoding will be selected
 *       depending on the configuration and capabilities of the client
 *       the capabilities of the Web service.
 *  <li> DISABLED: In this Mode, the default encoding will be selected.
 * </ul>
 * <p>
 * If this feature is not present on a Web service then the default behaviour
 * is equivalent to this feature being present and disabled.
 * <p>
 * If this feature is enabled by the client and the Service supports the
 * Fast Infoset encoding, as specified by the {@link com.sun.xml.internal.ws.api.fastinfoset.FastInfosetFeature},
 * and Fast Infoset is determined to be the most optimal encoding, then the
 * Fast Infoset encoding will be automatically selected by the client.
 * <p>
 * TODO: Still not sure if a feature is a server side only thing or can
 * also be a client side thing. If the former then this class should be
 * removed.
 * @author Paul.Sandoz@Sun.Com
 */
@ManagedData
public class SelectOptimalEncodingFeature extends WebServiceFeature {
    /**
     * Constant value identifying the {@link SelectOptimalEncodingFeature}
     */
    public static final String ID = "http://java.sun.com/xml/ns/jaxws/client/selectOptimalEncoding";

    /**
     * Create a {@link SelectOptimalEncodingFeature}.
     * The instance created will be enabled.
     */
    public SelectOptimalEncodingFeature() {
        this.enabled = true;
    }

    /**
     * Create a {@link SelectOptimalEncodingFeature}
     *
     * @param enabled specifies whether this feature should
     *                be enabled or not.
     */
    @FeatureConstructor({"enabled"})
    public SelectOptimalEncodingFeature(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * {@inheritDoc}
     */
    @ManagedAttribute
    public String getID() {
        return ID;
    }
}
