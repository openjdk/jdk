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

package com.sun.tools.internal.xjc.reader.xmlschema.bindinfo;

import java.util.Collection;
import java.util.Collections;

import com.sun.codemodel.internal.JCodeModel;
import com.sun.tools.internal.xjc.reader.Ring;
import com.sun.tools.internal.xjc.reader.xmlschema.BGMBuilder;
import com.sun.xml.internal.bind.annotation.XmlLocation;
import com.sun.xml.internal.xsom.XSComponent;

import org.xml.sax.Locator;

/**
 * Abstract partial implementation of {@link BIDeclaration}
 *
 * @author
 *     Kohsuke Kawaguchi (kohsuke.kawaguchi@sun.com)
 */
abstract class AbstractDeclarationImpl implements BIDeclaration {

    @Deprecated // eventually delete this in favor of using JAXB
    protected AbstractDeclarationImpl(Locator loc) {
        this.loc = loc;
    }

    protected AbstractDeclarationImpl() {}


    @XmlLocation
    Locator loc;    // set by JAXB
    public Locator getLocation() { return loc; }

    protected BindInfo parent;
    public void setParent(BindInfo p) { this.parent=p; }

    protected final XSComponent getOwner() {
        return parent.getOwner();
    }
    protected final BGMBuilder getBuilder() {
        return parent.getBuilder();
    }
    protected final JCodeModel getCodeModel() {
        return Ring.get(JCodeModel.class);
    }


    private boolean isAcknowledged = false;

    public final boolean isAcknowledged() { return isAcknowledged; }

    public void onSetOwner() {
    }

    public Collection<BIDeclaration> getChildren() {
        return Collections.emptyList();
    }

    public void markAsAcknowledged() {
        isAcknowledged = true;
    }
}
