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

import com.sun.xml.internal.xsom.XSDeclaration;
import com.sun.xml.internal.xsom.impl.parser.SchemaDocumentImpl;
import com.sun.xml.internal.xsom.util.NameGetter;
import org.xml.sax.Locator;

abstract class DeclarationImpl extends ComponentImpl implements XSDeclaration
{
    DeclarationImpl( SchemaDocumentImpl owner,
        AnnotationImpl _annon, Locator loc, ForeignAttributesImpl fa,
        String _targetNamespace, String _name,    boolean _anonymous ) {

        super(owner,_annon,loc,fa);
        this.targetNamespace = _targetNamespace;
        this.name = _name;
        this.anonymous = _anonymous;
    }

    private final String name;
    public String getName() { return name; }

    private final String targetNamespace;
    public String getTargetNamespace() { return targetNamespace; }

    private final boolean anonymous;
    /** @deprecated */
    public boolean isAnonymous() { return anonymous; }

    public final boolean isGlobal() { return !isAnonymous(); }
    public final boolean isLocal() { return isAnonymous(); }
}
