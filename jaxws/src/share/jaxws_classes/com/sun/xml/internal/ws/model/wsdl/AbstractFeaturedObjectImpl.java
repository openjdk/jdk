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

package com.sun.xml.internal.ws.model.wsdl;import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;
import com.sun.xml.internal.ws.api.model.wsdl.WSDLFeaturedObject;
import com.sun.xml.internal.ws.binding.WebServiceFeatureList;

import javax.xml.stream.XMLStreamReader;
import javax.xml.ws.WebServiceFeature;

/**
 * @author Kohsuke Kawaguchi
 */
abstract class AbstractFeaturedObjectImpl extends AbstractExtensibleImpl implements WSDLFeaturedObject {
    protected WebServiceFeatureList features;

    protected AbstractFeaturedObjectImpl(XMLStreamReader xsr) {
        super(xsr);
    }
    protected AbstractFeaturedObjectImpl(String systemId, int lineNumber) {
        super(systemId, lineNumber);
    }

    public final void addFeature(WebServiceFeature feature) {
        if (features == null)
            features = new WebServiceFeatureList();

        features.add(feature);
    }

    public @NotNull WebServiceFeatureList getFeatures() {
        if(features == null)
            return new WebServiceFeatureList();
        return features;
    }

    public final WebServiceFeature getFeature(String id) {
        if (features != null) {
            for (WebServiceFeature f : features) {
                if (f.getID().equals(id))
                    return f;
            }
        }

        return null;
    }

    @Nullable
    public <F extends WebServiceFeature> F getFeature(@NotNull Class<F> featureType) {
        if(features==null)
            return null;
        else
            return features.get(featureType);
    }
}
