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
package com.sun.xml.internal.rngom.nc;

import javax.xml.namespace.QName;

public class SimpleNameClass extends NameClass {

    public final QName name;

    public SimpleNameClass(QName name) {
        this.name = name;
    }

    public SimpleNameClass( String nsUri, String localPart ) {
        this( new QName(nsUri,localPart) );
    }

    public boolean contains(QName name) {
        return this.name.equals(name);
    }

    public int containsSpecificity(QName name) {
        return contains(name) ? SPECIFICITY_NAME : SPECIFICITY_NONE;
    }

    public int hashCode() {
        return name.hashCode();
    }

    public boolean equals(Object obj) {
        if (obj == null || !(obj instanceof SimpleNameClass))
            return false;
        SimpleNameClass other = (SimpleNameClass) obj;
        return name.equals(other.name);
    }

    public <V> V accept(NameClassVisitor<V> visitor) {
        return visitor.visitName(name);
    }

    public boolean isOpen() {
        return false;
    }
}
