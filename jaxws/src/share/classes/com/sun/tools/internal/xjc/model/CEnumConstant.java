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
package com.sun.tools.internal.xjc.model;

import com.sun.tools.internal.xjc.model.nav.NClass;
import com.sun.tools.internal.xjc.model.nav.NType;
import com.sun.xml.internal.bind.v2.model.core.EnumConstant;

import org.xml.sax.Locator;

/**
 * Enumeration constant.
 *
 * @author Kohsuke Kawaguchi
 */
public final class CEnumConstant implements EnumConstant<NType,NClass> {
    /** Name of the constant. */
    public final String name;
    /** Javadoc comment. Can be null. */
    public final String javadoc;
    /** Lexical representation of this enum constant. Always non-null. */
    private final String lexical;

    private CEnumLeafInfo parent;

    private final Locator locator;

    /**
     * @param name
     */
    public CEnumConstant(String name, String javadoc, String lexical, Locator loc) {
        assert name!=null;
        this.name = name;
        this.javadoc = javadoc;
        this.lexical = lexical;
        this.locator = loc;
    }

    public CEnumLeafInfo getEnclosingClass() {
        return parent;
    }

    /*package*/ void setParent(CEnumLeafInfo parent) {
        this.parent = parent;
    }

    public String getLexicalValue() {
        return lexical;
    }

    public String getName() {
        return name;
    }

    public Locator getLocator() {
        return locator;
    }
}
