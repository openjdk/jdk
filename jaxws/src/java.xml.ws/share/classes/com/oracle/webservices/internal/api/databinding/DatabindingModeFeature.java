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

package com.oracle.webservices.internal.api.databinding;

import java.util.HashMap;
import java.util.Map;

import javax.xml.ws.WebServiceFeature;

public class DatabindingModeFeature extends WebServiceFeature implements com.sun.xml.internal.ws.api.ServiceSharedFeatureMarker {
    /**
     * Constant value identifying the DatabindingFeature
     */
    static public final String ID = "http://jax-ws.java.net/features/databinding";

    static public final String GLASSFISH_JAXB = "glassfish.jaxb";

    //These constants should be defined in the corresponding plugin package
//    static public final String ECLIPSELINK_JAXB = "eclipselink.jaxb";
//    static public final String ECLIPSELINK_SDO = "eclipselink.sdo";
//    static public final String TOPLINK_JAXB = "toplink.jaxb";
//    static public final String TOPLINK_SDO = "toplink.sdo";

    private String mode;
    private Map<String, Object> properties;

    public DatabindingModeFeature(String mode) {
        super();
        this.mode = mode;
        properties = new HashMap<String, Object>();
    }

    public String getMode() {
        return mode;
    }

    public String getID() {
        return ID;
    }

    public Map<String, Object> getProperties() {
        return properties;
    }

    public static Builder builder() { return new Builder(new DatabindingModeFeature(null)); }

    public final static class Builder {
        final private DatabindingModeFeature o;
        Builder(final DatabindingModeFeature x) { o = x; }
        public DatabindingModeFeature build() { return o; }
//        public DatabindingModeFeature build() { return (DatabindingModeFeature) FeatureValidator.validate(o); }
        public Builder value(final String x) { o.mode = x; return this; }
    }
}
