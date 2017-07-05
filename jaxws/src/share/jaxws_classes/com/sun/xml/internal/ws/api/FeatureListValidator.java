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

package com.sun.xml.internal.ws.api;

import javax.xml.ws.WebServiceFeature;
import javax.xml.ws.WebServiceException;

/**
 * Validates a list of {@link WebServiceFeature} instances when they are added to
 * the client or service binding.
 * <p>
 * {@link WebServiceFeature} classes may specify validator beans using {@link FeatureListValidatorAnnotation}.
 * <p>
 * Current behavior will allow runtime components to add features to the binding after initial validation; however,
 * this behavior is discouraged and will not be supported in later releases of the reference
 * implementation.
 *
 * @since 2.2.8
 * @see FeatureListValidatorAnnotation
 */
public interface FeatureListValidator {
    /**
     * Validates feature list.  Implementations should throw {@link WebServiceException} if the
     * list of features is invalid.  Implementations may add features to the list or make other
     * changes; however, only validators belonging to features on the original list will be
     * invoked.
     *
     * @param list feature list
     */
    public void validate(WSFeatureList list);
}
