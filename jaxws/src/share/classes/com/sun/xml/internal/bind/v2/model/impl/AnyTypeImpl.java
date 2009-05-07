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
package com.sun.xml.internal.bind.v2.model.impl;

import javax.xml.namespace.QName;
import javax.xml.bind.annotation.XmlIDREF;

import com.sun.xml.internal.bind.v2.WellKnownNamespace;
import com.sun.xml.internal.bind.v2.runtime.Location;
import com.sun.xml.internal.bind.v2.model.core.NonElement;
import com.sun.xml.internal.bind.v2.model.core.TypeInfo;
import com.sun.xml.internal.bind.v2.model.nav.Navigator;
import com.sun.xml.internal.bind.v2.model.annotation.Locatable;

/**
 * {@link TypeInfo} implementation for <tt>xs:anyType</tt>.
 *
 * @author Kohsuke Kawaguchi
 */
class AnyTypeImpl<T,C> implements NonElement<T,C> {

    private final T type;
    private final Navigator<T,C,?,?> nav;

    public AnyTypeImpl(Navigator<T,C,?,?> nav) {
        this.type = nav.ref(Object.class);
        this.nav = nav;
    }

    public QName getTypeName() {
        return name;
    }

    public T getType() {
        return type;
    }

    public Locatable getUpstream() {
        return null;
    }

    public boolean isSimpleType() {
        return false;
    }

    public Location getLocation() {
        return nav.getClassLocation(nav.asDecl(Object.class));
    }

    /**
     * xs:anyType can be referenced from {@link XmlIDREF}.
     *
     * @deprecated
     *      why are you calling a method whose return value is always known?
     */
    public final boolean canBeReferencedByIDREF() {
        return true;
    }

    private static final QName name = new QName(WellKnownNamespace.XML_SCHEMA,"anyType");
}
