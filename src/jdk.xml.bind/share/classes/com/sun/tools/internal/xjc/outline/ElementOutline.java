/*
 * Copyright (c) 1997, 2015, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.internal.xjc.outline;

import com.sun.codemodel.internal.JDefinedClass;
import com.sun.tools.internal.xjc.model.CCustomizable;
import com.sun.tools.internal.xjc.model.CElementInfo;

/**
 * Outline object that provides per-{@link CElementInfo} information
 * for filling in methods/fields for a bean.
 *
 * This interface is accessible from {@link Outline}. This object is
 * not created for all {@link CElementInfo}s.
 * It is only for those {@link CElementInfo} that has a class.
 * (IOW, {@link CElementInfo#hasClass()}
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class ElementOutline implements CustomizableOutline {

    /**
     * A {@link Outline} that encloses all the class outlines.
     */
    public abstract Outline parent();

    /**
     * {@link PackageOutline} that contains this class.
     */
    public PackageOutline _package() {
        return parent().getPackageContext(implClass._package());
    }

    /**
     * This {@link ElementOutline} holds information about this {@link CElementInfo}.
     */
    public final CElementInfo target;

    /**
     * The implementation aspect of a bean.
     * The actual place where fields/methods should be generated into.
     */
    public final JDefinedClass implClass;


    protected ElementOutline(CElementInfo target, JDefinedClass implClass) {
        this.target = target;
        this.implClass = implClass;
    }

    @Override
    public CCustomizable getTarget() {
        return target;
    }

    @Override
    public JDefinedClass getImplClass() {
        return implClass;
    }
}
