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
package com.sun.xml.internal.rngom.binary.visitor;

import com.sun.xml.internal.rngom.binary.Pattern;
import com.sun.xml.internal.rngom.nc.NameClass;
import org.relaxng.datatype.Datatype;

/**
 * Walks the pattern tree.
 *
 * @author
 *      Kohsuke Kawaguchi (kk@kohsuke.org)
 */
public class PatternWalker implements PatternVisitor {
    public void visitEmpty() {
    }

    public void visitNotAllowed() {
    }

    public void visitError() {
    }

    public void visitGroup(Pattern p1, Pattern p2) {
        visitBinary(p1, p2);
    }

    protected void visitBinary(Pattern p1, Pattern p2) {
        p1.accept(this);
        p2.accept(this);
    }

    public void visitInterleave(Pattern p1, Pattern p2) {
        visitBinary(p1, p2);
    }

    public void visitChoice(Pattern p1, Pattern p2) {
        visitBinary(p1, p2);
    }

    public void visitOneOrMore(Pattern p) {
        p.accept(this);
    }

    public void visitElement(NameClass nc, Pattern content) {
        content.accept(this);
    }

    public void visitAttribute(NameClass ns, Pattern value) {
        value.accept(this);
    }

    public void visitData(Datatype dt) {
    }

    public void visitDataExcept(Datatype dt, Pattern except) {
    }

    public void visitValue(Datatype dt, Object obj) {
    }

    public void visitText() {
    }

    public void visitList(Pattern p) {
        p.accept(this);
    }

    public void visitAfter(Pattern p1, Pattern p2) {
    }
}
