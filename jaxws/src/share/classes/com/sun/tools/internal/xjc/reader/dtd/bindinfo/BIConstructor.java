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
package com.sun.tools.internal.xjc.reader.dtd.bindinfo;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

import com.sun.tools.internal.xjc.model.CClassInfo;

import org.w3c.dom.Element;
import org.xml.sax.Locator;

/**
 * &lt;constructor> declaration in the binding file.
 *
 * <p>
 * Since JAXB will generate both interfaces and implementations,
 * A constructor declaration will create:
 *
 * <ul>
 *  <li> a method declaration in the factory interface
 *  <li> a method implementation in the factory implementation class
 *  <li> a constructor implementation in the actual implementation class
 * </ul>
 */
public class BIConstructor
{
    BIConstructor( Element _node ) {
        this.dom = _node;

        StringTokenizer tokens = new StringTokenizer(
            DOMUtil.getAttribute(_node,"properties"));

        List<String> vec = new ArrayList<String>();
        while(tokens.hasMoreTokens())
            vec.add(tokens.nextToken());
        properties = vec.toArray(new String[0]);

        if( properties.length==0 )
            throw new AssertionError("this error should be catched by the validator");
    }

    /** &lt;constructor> element in the source binding file. */
    private final Element dom;

    /** properties specified by @properties. */
    private final String[] properties;

    /**
     * Creates a constructor declaration into the ClassItem.
     *
     * @param   cls
     *      ClassItem object that corresponds to the
     *      element declaration that contains this declaration.
     */
    public void createDeclaration( CClassInfo cls ) {
        cls.addConstructor(properties);
    }

    /** Gets the location where this declaration is declared. */
    public Locator getSourceLocation() {
        return DOMLocator.getLocationInfo(dom);
    }


}
