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

package com.sun.xml.internal.ws.api.fastinfoset;

import com.sun.xml.internal.ws.api.FeatureConstructor;

import javax.xml.ws.WebServiceFeature;

import com.sun.org.glassfish.gmbal.ManagedAttribute;
import com.sun.org.glassfish.gmbal.ManagedData;

/**
 * Enable or disable Fast Infoset on a Web service.
 * <p>
 * The following describes the affects of this feature with respect
 * to being enabled or disabled:
 * <ul>
 *  <li> ENABLED: In this Mode, Fast Infoset will be enabled.
 *  <li> DISABLED: In this Mode, Fast Infoset will be disabled and the
 *       Web service will not process incoming messages or produce outgoing
 *       messages encoded using Fast Infoset.
 * </ul>
 * <p>
 * If this feature is not present on a Web service then the default behaviour
 * is equivalent to this feature being present and enabled.
 * @author Paul.Sandoz@Sun.Com
 */
@ManagedData
public class FastInfosetFeature extends WebServiceFeature {
    /**
     * Constant value identifying the {@link FastInfosetFeature}
     */
    public static final String ID = "http://java.sun.com/xml/ns/jaxws/fastinfoset";

    /**
     * Create a {@link FastInfosetFeature}. The instance created will be enabled.
     */
    public FastInfosetFeature() {
        this.enabled = true;
    }

    /**
     * Create a {@link FastInfosetFeature}
     *
     * @param enabled specifies whether this feature should
     *                be enabled or not.
     */
    @FeatureConstructor({"enabled"})
    public FastInfosetFeature(boolean enabled) {
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
