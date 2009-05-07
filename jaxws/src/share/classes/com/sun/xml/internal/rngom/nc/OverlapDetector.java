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

class OverlapDetector implements NameClassVisitor<Void> {
    private NameClass nc1;
    private NameClass nc2;
    private boolean overlaps = false;

    static final String IMPOSSIBLE = "\u0000";

    private OverlapDetector(NameClass nc1, NameClass nc2) {
        this.nc1 = nc1;
        this.nc2 = nc2;
        nc1.accept(this);
        nc2.accept(this);
    }

    private void probe(QName name) {
        if (nc1.contains(name) && nc2.contains(name))
            overlaps = true;
    }

    public Void visitChoice(NameClass nc1, NameClass nc2) {
        nc1.accept(this);
        nc2.accept(this);
        return null;
    }

    public Void visitNsName(String ns) {
        probe(new QName(ns, IMPOSSIBLE));
        return null;
    }

    public Void visitNsNameExcept(String ns, NameClass ex) {
        probe(new QName(ns, IMPOSSIBLE));
        ex.accept(this);
        return null;
    }

    public Void visitAnyName() {
        probe(new QName(IMPOSSIBLE, IMPOSSIBLE));
        return null;
    }

    public Void visitAnyNameExcept(NameClass ex) {
        probe(new QName(IMPOSSIBLE, IMPOSSIBLE));
        ex.accept(this);
        return null;
    }

    public Void visitName(QName name) {
        probe(name);
        return null;
    }

    public Void visitNull() {
        return null;
    }

    static boolean overlap(NameClass nc1, NameClass nc2) {
        if (nc2 instanceof SimpleNameClass) {
            SimpleNameClass snc = (SimpleNameClass) nc2;
            return nc1.contains(snc.name);
        }
        if (nc1 instanceof SimpleNameClass) {
            SimpleNameClass snc = (SimpleNameClass) nc1;
            return nc2.contains(snc.name);
        }
        return new OverlapDetector(nc1, nc2).overlaps;
    }
}
