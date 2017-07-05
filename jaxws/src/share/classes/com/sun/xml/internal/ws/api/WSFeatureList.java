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

package com.sun.xml.internal.ws.api;import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;

import javax.xml.ws.WebServiceFeature;

/**
 * Read-only list of {@link WebServiceFeature}s.
 *
 * @author Kohsuke Kawaguchi
 */
public interface WSFeatureList extends Iterable<WebServiceFeature> {
    /**
     * Checks if a particular {@link WebServiceFeature} is enabled.
     *
     * @return
     *      true if enabled.
     */
    boolean isEnabled(@NotNull Class<? extends WebServiceFeature> feature);

    /**
     * Gets a {@link WebServiceFeature} of the specific type.
     *
     * @param featureType
     *      The type of the feature to retrieve.
     * @return
     *      If the feature is present and enabled, return a non-null instance.
     *      Otherwise null.
     */
    @Nullable <F extends WebServiceFeature> F get(@NotNull Class<F> featureType);

    /**
     * Obtains all the features in the array.
      */
    @NotNull WebServiceFeature[] toArray();
}
