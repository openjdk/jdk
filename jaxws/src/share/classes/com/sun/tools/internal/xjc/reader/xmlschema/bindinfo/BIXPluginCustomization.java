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
package com.sun.tools.internal.xjc.reader.xmlschema.bindinfo;

import javax.xml.namespace.QName;

import com.sun.tools.internal.xjc.Plugin;
import com.sun.tools.internal.xjc.model.Model;
import com.sun.tools.internal.xjc.reader.Ring;

import org.w3c.dom.Element;
import org.xml.sax.Locator;

/**
 * Customization specified via {@link Plugin#getCustomizationURIs()}.
 *
 * @author Kohsuke Kawaguchi
 */
public final class BIXPluginCustomization extends AbstractDeclarationImpl {

    /**
     * Customization element.
     */
    public final Element element;

    private QName name;

    public BIXPluginCustomization(Element e, Locator _loc) {
        super(_loc);
        element = e;
    }

    public void onSetOwner() {
        super.onSetOwner();
        if(!Ring.get(Model.class).options.pluginURIs.contains(element.getNamespaceURI()))
            markAsAcknowledged();
    }

    public final QName getName() {
        if(name==null)
            name = new QName(element.getNamespaceURI(),element.getLocalName());
        return name;
    }
}
