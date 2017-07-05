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

package com.sun.tools.internal.xjc.reader.xmlschema;

import com.sun.tools.internal.xjc.reader.gbind.Element;
import com.sun.xml.internal.xsom.XSWildcard;

/**
 * {@link Element} that represents a wildcard,
 * for the "ease of binding" we always just bind this to DOM elements.
 * @author Kohsuke Kawaguchi
 */
final class GWildcardElement extends GElement {

    /**
     * If true, bind to {@code Object} for eager JAXB unmarshalling.
     * Otherwise bind to DOM (I hate "you can put both" semantics,
     * so I'm not going to do that in this binding mode.)
     */
    private boolean strict = true;

    public String toString() {
        return "#any";
    }

    String getPropertyNameSeed() {
        return "any";
    }

    public void merge(XSWildcard wc) {
        switch(wc.getMode()) {
        case XSWildcard.LAX:
        case XSWildcard.SKIP:
            strict = false;
        }
    }

    public boolean isStrict() {
        return strict;
    }
}
