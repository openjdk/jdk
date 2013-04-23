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

package com.sun.xml.internal.ws.api;

import java.util.List;

import com.sun.xml.internal.ws.api.server.Container;
import com.sun.xml.internal.ws.api.server.WSEndpoint;
import com.sun.xml.internal.ws.client.Stub;
import javax.xml.ws.WebServiceFeature;

/**
 * Allows registration of multiple {@link Component}s against the {@link ComponentRegistry} implementations
 * of the {@link Container}, {@link WSEndpoint}, {@link WSService}, or {@link Stub}.  The
 * registration is guaranteed to occur early in the initialization of these objects prior to tubeline creation
 * (applicable to endpoint and stub only).
 * <p>
 * Because the Container is shared among all Stubs created from a common WSService object, this feature must
 * be passed during WSService initialization in order to register a Component against the client-side Container.
 * <p>
 * IllegalArgumentException will be thrown if the feature is used with an inappropriate target, e.g. stub target
 * used during WSEndpoint initialization.
 *
 * @since 2.2.8
 */
public class ComponentsFeature extends WebServiceFeature implements ServiceSharedFeatureMarker {
    private final List<ComponentFeature> componentFeatures;

    /**
     * Constructs ComponentFeature with indicated component and target
     * @param component component
     * @param target target
     */
    public ComponentsFeature(List<ComponentFeature> componentFeatures) {
        this.enabled = true;
        this.componentFeatures = componentFeatures;
    }

    @Override
    public String getID() {
        return ComponentsFeature.class.getName();
    }

    /**
     * Retrieves component
     * @return component
     */
    public List<ComponentFeature> getComponentFeatures() {
        return componentFeatures;
    }
}
