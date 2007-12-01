/*
 * Copyright 2006 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.tools.internal.txw2.model.prop;

import com.sun.codemodel.JType;

import javax.xml.namespace.QName;

/**
 * Common implementation between elements and attributes.
 *
 * @author Kohsuke Kawaguchi
 */
abstract class XmlItemProp extends Prop {
    private final QName name;
    private final JType type;

    public XmlItemProp(QName name, JType valueType) {
        this.name = name;
        this.type = valueType;
    }

    public final boolean equals(Object o) {
        if (this.getClass()!=o.getClass()) return false;

        XmlItemProp that = (XmlItemProp)o;

        return this.name.equals(that.name)
            && this.type.equals(that.type);
    }

    public final int hashCode() {
        return name.hashCode()*29 + type.hashCode();
    }
}
