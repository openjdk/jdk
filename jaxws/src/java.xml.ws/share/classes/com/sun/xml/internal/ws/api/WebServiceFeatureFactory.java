/*
 * Copyright (c) 2009, 2013, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.xml.internal.ws.binding.WebServiceFeatureList;

import javax.xml.ws.WebServiceFeature;
import java.lang.annotation.Annotation;

/**
 * Factory methods to get web service features from the corresponding
 * feature annotations
 *
 * @author Jitendra Kotamraju
 */
public class WebServiceFeatureFactory {

    /**
     * Returns a feature list for feature annotations(i.e which have
     * {@link javax.xml.ws.spi.WebServiceFeatureAnnotation} meta annotation)
     *
     * @param ann list of annotations(that can also have non-feature annotations)
     * @return non-null feature list object
     */
    public static WSFeatureList getWSFeatureList(Iterable<Annotation> ann) {
        WebServiceFeatureList list = new WebServiceFeatureList();
        list.parseAnnotations(ann);
        return list;
    }

    /**
     * Returns a corresponding feature for a feature annotation(i.e which has
     * {@link javax.xml.ws.spi.WebServiceFeatureAnnotation} meta annotation)
     *
     * @param ann any annotation, not required to be a feature annotation
     * @return corresponding feature for the annotation
     *         null, if the annotation is not a feature annotation
     */
    public static WebServiceFeature getWebServiceFeature(Annotation ann) {
        return WebServiceFeatureList.getFeature(ann);
    }

}
