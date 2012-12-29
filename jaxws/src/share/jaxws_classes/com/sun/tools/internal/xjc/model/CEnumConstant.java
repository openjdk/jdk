/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.internal.xjc.model;

import com.sun.tools.internal.xjc.model.nav.NClass;
import com.sun.tools.internal.xjc.model.nav.NType;
import com.sun.xml.internal.bind.v2.model.core.EnumConstant;

import com.sun.xml.internal.xsom.XSComponent;
import org.xml.sax.Locator;

/**
 * Enumeration constant.
 *
 * @author Kohsuke Kawaguchi
 */
public final class CEnumConstant implements EnumConstant<NType,NClass>, CCustomizable {
    /** Name of the constant. */
    public final String name;
    /** Javadoc comment. Can be null. */
    public final String javadoc;
    /** Lexical representation of this enum constant. Always non-null. */
    private final String lexical;

    private CEnumLeafInfo parent;

    private final XSComponent source;

    private final CCustomizations customizations;

    private final Locator locator;

    /**
     * @param name
     */
    public CEnumConstant(String name, String javadoc, String lexical, XSComponent source, CCustomizations customizations, Locator loc) {
        assert name!=null;
        this.name = name;
        this.javadoc = javadoc;
        this.lexical = lexical;
        this.source = source;
        this.customizations = customizations;
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

    public XSComponent getSchemaComponent() {
        return source;
    }

    public CCustomizations getCustomizations() {
        return customizations;
    }

    public Locator getLocator() {
        return locator;
    }
}
