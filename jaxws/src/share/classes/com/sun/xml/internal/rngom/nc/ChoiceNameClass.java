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

public class ChoiceNameClass extends NameClass {

    private final NameClass nameClass1;
    private final NameClass nameClass2;

    public ChoiceNameClass(NameClass nameClass1, NameClass nameClass2) {
        this.nameClass1 = nameClass1;
        this.nameClass2 = nameClass2;
    }

    public boolean contains(QName name) {
        return (nameClass1.contains(name) || nameClass2.contains(name));
    }

    public int containsSpecificity(QName name) {
        return Math.max(
            nameClass1.containsSpecificity(name),
            nameClass2.containsSpecificity(name));
    }

    public int hashCode() {
        return nameClass1.hashCode() ^ nameClass2.hashCode();
    }

    public boolean equals(Object obj) {
        if (obj == null || !(obj instanceof ChoiceNameClass))
            return false;
        ChoiceNameClass other = (ChoiceNameClass) obj;
        return (
            nameClass1.equals(other.nameClass1)
                && nameClass2.equals(other.nameClass2));
    }

    public <V> V accept(NameClassVisitor<V> visitor) {
        return visitor.visitChoice(nameClass1, nameClass2);
    }

    public boolean isOpen() {
        return nameClass1.isOpen() || nameClass2.isOpen();
    }
}
