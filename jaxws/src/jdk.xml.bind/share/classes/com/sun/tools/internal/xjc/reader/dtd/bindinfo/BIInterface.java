/*
 * Copyright (c) 1997, 2015, Oracle and/or its affiliates. All rights reserved.
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

import java.util.StringTokenizer;

import org.w3c.dom.Element;
import org.xml.sax.Locator;

/**
 * {@code <interface>} declaration in the binding file.
 */
public final class BIInterface
{
    BIInterface( Element e ) {
        this.dom = e;
        name = DOMUtil.getAttribute(e,"name");
        members = parseTokens(DOMUtil.getAttribute(e,"members"));

        if(DOMUtil.getAttribute(e,"properties")!=null) {
            fields = parseTokens(DOMUtil.getAttribute(e,"properties"));
            throw new AssertionError("//interface/@properties is not supported");
        } else    // no property was specified
            fields = new String[0];
    }

    /** {@code <interface>} element in the binding file. */
    private final Element dom;

    /** Name of the generated Java interface. */
    private final String name;

    /**
     * Gets the name of this interface.
     * This name should also used as the class name.
     */
    public String name() { return name; }


    private final String[] members;

    /**
     * Gets the names of interfaces/classes that implement
     * this interface.
     */
    public String[] members() { return members; }


    private final String[] fields;

    /** Gets the names of fields in this interface. */
    public String[] fields() { return fields; }


    /** Gets the location where this declaration is declared. */
    public Locator getSourceLocation() {
        return DOMLocator.getLocationInfo(dom);
    }



    /** splits a list into an array of strings. */
    private static String[] parseTokens( String value ) {
        StringTokenizer tokens = new StringTokenizer(value);

        String[] r = new String[tokens.countTokens()];
        int i=0;
        while(tokens.hasMoreTokens())
            r[i++] = tokens.nextToken();

        return r;
    }
}
