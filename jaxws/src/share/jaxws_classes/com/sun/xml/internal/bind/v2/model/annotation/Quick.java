/*
 * Copyright (c) 1997, 2011, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.bind.v2.model.annotation;

import java.lang.annotation.Annotation;

import com.sun.xml.internal.bind.v2.runtime.Location;

/**
 * Base implementation of {@link Locatable} {@link Annotation}.
 *
 * <p>
 * Derived classes of this class is provided for annotations that are commonly
 * used in JAXB, to improve the performance of {@link LocatableAnnotation#create}.
 *
 * @author Kohsuke Kawaguchi
 */
public /*so that our code generator can refer to this class*/ abstract class Quick implements Annotation, Locatable, Location {
    private final Locatable upstream;

    protected Quick(Locatable upstream) {
        this.upstream = upstream;
    }

    /**
     * Gets the annotation object that this object is wrapping.
     */
    protected abstract Annotation getAnnotation();

    /**
     * Factory method to create a new instance of the same kind.
     * A {@link Quick} object also works as a factory of itself
     */
    protected abstract Quick newInstance( Locatable upstream, Annotation core );

    public final Location getLocation() {
        return this;
    }

    public final Locatable getUpstream() {
        return upstream;
    }

    public final String toString() {
        return getAnnotation().toString();
    }
}
