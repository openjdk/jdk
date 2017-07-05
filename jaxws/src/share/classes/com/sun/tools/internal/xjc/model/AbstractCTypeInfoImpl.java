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

import javax.activation.MimeType;

import com.sun.codemodel.internal.JExpression;
import com.sun.tools.internal.xjc.outline.Outline;
import com.sun.xml.internal.bind.v2.model.annotation.Locatable;
import com.sun.xml.internal.bind.v2.model.core.ID;
import com.sun.xml.internal.bind.v2.runtime.Location;
import com.sun.xml.internal.xsom.XSComponent;
import com.sun.xml.internal.xsom.XmlString;

/**
 * Partial implementation of {@link CTypeInfo}.
 *
 * <p>
 * The inheritance of {@link TypeUse} by {@link CTypeInfo}
 * isn't a normal inheritance (see {@link CTypeInfo} for more.)
 * This class implments methods on {@link TypeUse} for {@link CTypeInfo}.
 *
 * @author Kohsuke Kawaguchi
 */
abstract class AbstractCTypeInfoImpl implements CTypeInfo {

    private final CCustomizations customizations;

    private final XSComponent source;

    protected AbstractCTypeInfoImpl(Model model, XSComponent source, CCustomizations customizations) {
        if(customizations==null)
            customizations = CCustomizations.EMPTY;
        else
            customizations.setParent(model,this);
        this.customizations = customizations;
        this.source = source;
    }

    public final boolean isCollection() {
        return false;
    }

    public final CAdapter getAdapterUse() {
        return null;
    }

    public final ID idUse() {
        return ID.NONE;
    }

    public final XSComponent getSchemaComponent() {
        return source;
    }

    /**
     * @deprecated
     *      why are you calling an unimplemented method?
     */
    public final boolean canBeReferencedByIDREF() {
        // we aren't doing any error check in XJC, so no point in implementing this method.
        throw new UnsupportedOperationException();
    }

    /**
     * No default {@link MimeType}.
     */
    public MimeType getExpectedMimeType() {
        return null;
    }

    public CCustomizations getCustomizations() {
        return customizations;
    }

    // this is just a convenient default
    public JExpression createConstant(Outline outline, XmlString lexical) {
        return null;
    }

    public final Locatable getUpstream() {
        throw new UnsupportedOperationException();
    }

    public final Location getLocation() {
        throw new UnsupportedOperationException();
    }
}
