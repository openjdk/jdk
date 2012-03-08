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

package com.sun.tools.internal.xjc.api.impl.s2j;

import javax.xml.namespace.QName;

import com.sun.codemodel.internal.JCodeModel;
import com.sun.codemodel.internal.JType;
import com.sun.tools.internal.xjc.api.Mapping;
import com.sun.tools.internal.xjc.api.Property;
import com.sun.tools.internal.xjc.model.CElementInfo;
import com.sun.tools.internal.xjc.outline.FieldOutline;

/**
 * @author Kohsuke Kawaguchi
 */
public /*for BSH*/ final class PropertyImpl implements Property {
    protected final FieldOutline fr;
    protected final QName elementName;
    protected final Mapping parent;
    protected final JCodeModel codeModel;

    PropertyImpl( Mapping parent, FieldOutline fr, QName elementName ) {
        this.parent = parent;
        this.fr = fr;
        this.elementName = elementName;
        this.codeModel = fr.getRawType().owner();
    }

    public final String name() {
        return fr.getPropertyInfo().getName(false);
    }

    /** Returns raw schema name for simpleType property. May return null for other types. */
    public final QName rawName() {
        if (fr instanceof ElementAdapter) {
            CElementInfo eInfo = ((ElementAdapter)fr).ei;
            if ((eInfo != null) && (eInfo.getProperty() != null)) {
                return eInfo.getProperty().getTypes().get(0).getTypeName();
            }
        }
        return null;
    }

    public final QName elementName() {
        return elementName;
    }

    public final JType type() {
        return fr.getRawType();
    }
}
