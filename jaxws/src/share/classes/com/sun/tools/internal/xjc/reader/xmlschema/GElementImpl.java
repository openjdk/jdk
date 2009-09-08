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

package com.sun.tools.internal.xjc.reader.xmlschema;

import javax.xml.namespace.QName;

import com.sun.tools.internal.xjc.reader.gbind.Element;
import com.sun.xml.internal.xsom.XSElementDecl;

/**
 * {@link Element} that wraps {@link XSElementDecl}.
 *
 * @author Kohsuke Kawaguchi
 */
final class GElementImpl extends GElement {
    public final QName tagName;

    /**
     * The representative {@link XSElementDecl}.
     *
     * Even though multiple {@link XSElementDecl}s maybe represented by
     * a single {@link GElementImpl} (especially when they are local),
     * the schema spec requires that they share the same type and other
     * characteristic.
     *
     * (To be really precise, you may have different default values,
     * nillability, all that, so if that becomes a real issue we have
     * to reconsider this design.)
     */
    public final XSElementDecl decl;

    public GElementImpl(QName tagName, XSElementDecl decl) {
        this.tagName = tagName;
        this.decl = decl;
    }

    public String toString() {
        return tagName.toString();
    }

    String getPropertyNameSeed() {
        return tagName.getLocalPart();
    }
}
