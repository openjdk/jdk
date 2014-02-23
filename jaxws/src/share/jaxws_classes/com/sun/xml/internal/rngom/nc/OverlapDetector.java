/*
 * Copyright (c) 2005, 2012, Oracle and/or its affiliates. All rights reserved.
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
/*
 * Copyright (C) 2004-2011
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
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
