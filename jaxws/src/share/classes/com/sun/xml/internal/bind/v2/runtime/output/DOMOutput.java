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
package com.sun.xml.internal.bind.v2.runtime.output;

import com.sun.xml.internal.bind.v2.runtime.AssociationMap;
import com.sun.xml.internal.bind.marshaller.SAX2DOMEx;

import org.w3c.dom.Node;
import org.xml.sax.SAXException;


/**
 * {@link XmlOutput} implementation that does associative marshalling to DOM.
 *
 * <p>
 * This is used for binder.
 *
 * @author Kohsuke Kawaguchi
 */
public final class DOMOutput extends SAXOutput {
    private final AssociationMap assoc;

    public DOMOutput(Node node, AssociationMap assoc) {
        super(new SAX2DOMEx(node));
        this.assoc = assoc;
        assert assoc!=null;
    }

    private SAX2DOMEx getBuilder() {
        return (SAX2DOMEx)out;
    }

    public void endStartTag() throws SAXException {
        super.endStartTag();

        Object op = nsContext.getCurrent().getOuterPeer();
        if(op!=null)
            assoc.addOuter( getBuilder().getCurrentElement(), op );

        Object ip = nsContext.getCurrent().getInnerPeer();
        if(ip!=null)
            assoc.addInner( getBuilder().getCurrentElement(), ip );
    }
}
