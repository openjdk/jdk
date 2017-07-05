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

package com.sun.tools.internal.xjc.reader.dtd.bindinfo;

import org.w3c.dom.Element;
import org.xml.sax.Locator;

class DOMLocator {
    private static final String locationNamespace =
        "http://www.sun.com/xmlns/jaxb/dom-location";
    private static final String systemId    = "systemid";
    private static final String column      = "column";
    private static final String line        = "line";

    /** Sets the location information to a specified element. */
    public static void setLocationInfo( Element e, Locator loc ) {
        e.setAttributeNS(locationNamespace,"loc:"+systemId,loc.getSystemId());
        e.setAttributeNS(locationNamespace,"loc:"+column,Integer.toString(loc.getLineNumber()));
        e.setAttributeNS(locationNamespace,"loc:"+line,Integer.toString(loc.getColumnNumber()));
    }

    /**
     * Gets the location information from an element.
     *
     * <p>
     * For this method to work, the setLocationInfo method has to be
     * called before.
     */
    public static Locator getLocationInfo( final Element e ) {
        if(DOMUtil.getAttribute(e,locationNamespace,systemId)==null)
            return null;    // no location information

        return new Locator(){
            public int getLineNumber() {
                return Integer.parseInt(DOMUtil.getAttribute(e,locationNamespace,line));
            }
            public int getColumnNumber() {
                return Integer.parseInt(DOMUtil.getAttribute(e,locationNamespace,column));
            }
            public String getSystemId() {
                return DOMUtil.getAttribute(e,locationNamespace,systemId);
            }
            // we are not interested in PUBLIC ID.
            public String getPublicId() { return null; }
        };
    }
}
