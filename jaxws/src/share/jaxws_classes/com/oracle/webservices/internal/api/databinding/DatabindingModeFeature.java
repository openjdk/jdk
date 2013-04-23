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

package com.oracle.webservices.internal.api.databinding;

import java.util.HashMap;
import java.util.Map;

import javax.xml.ws.WebServiceFeature;

public class DatabindingModeFeature extends WebServiceFeature {
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
