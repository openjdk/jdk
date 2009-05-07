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
package com.sun.xml.internal.xsom.impl;

import com.sun.xml.internal.xsom.XSNotation;
import com.sun.xml.internal.xsom.impl.parser.SchemaDocumentImpl;
import com.sun.xml.internal.xsom.visitor.XSFunction;
import com.sun.xml.internal.xsom.visitor.XSVisitor;
import org.xml.sax.Locator;

/**
 *
 * @author
 *     Kohsuke Kawaguchi (kohsuke.kawaguchi@sun.com)
 */
public class NotationImpl extends DeclarationImpl implements XSNotation {

    public NotationImpl( SchemaDocumentImpl owner, AnnotationImpl _annon,
        Locator _loc, ForeignAttributesImpl _fa, String _name,
        String _publicId, String _systemId ) {
        super(owner,_annon,_loc,_fa,owner.getTargetNamespace(),_name,false);

        this.publicId = _publicId;
        this.systemId = _systemId;
    }

    private final String publicId;
    private final String systemId;

    public String getPublicId() { return publicId; }
    public String getSystemId() { return systemId; }

    public void visit(XSVisitor visitor) {
        visitor.notation(this);
    }

    public Object apply(XSFunction function) {
        return function.notation(this);
    }

}
