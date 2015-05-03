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

/*
 * Use is subject to the license terms.
 */
package com.sun.tools.internal.xjc.outline;

import java.util.List;

import com.sun.codemodel.internal.JClass;
import com.sun.codemodel.internal.JDefinedClass;
import com.sun.tools.internal.xjc.model.CClassInfo;
import com.sun.tools.internal.xjc.model.CCustomizable;
import com.sun.tools.internal.xjc.model.CPropertyInfo;
import com.sun.istack.internal.NotNull;

/**
 * Outline object that provides per-{@link CClassInfo} information
 * for filling in methods/fields for a bean.
 *
 * This interface is accessible from {@link Outline}
 *
 * @author Kohsuke Kawaguchi (kohsuke.kawaguchi@sun.com)
 */
public abstract class ClassOutline implements CustomizableOutline {

    /**
     * A {@link Outline} that encloses all the class outlines.
     */
    public abstract @NotNull Outline parent();

    /**
     * {@link PackageOutline} that contains this class.
     */
    public @NotNull PackageOutline _package() {
        return parent().getPackageContext(ref._package());
    }

    /**
     * This {@link ClassOutline} holds information about this {@link CClassInfo}.
     */
    public final @NotNull CClassInfo target;

    /**
     * The exposed aspect of the a bean.
     *
     * implClass is always assignable to this type.
     * <p>
     * Usually this is the public content interface, but
     * it could be the same as the implClass.
     */
    public final @NotNull JDefinedClass ref;

    /**
     * The implementation aspect of a bean.
     * The actual place where fields/methods should be generated into.
     */
    public final @NotNull JDefinedClass implClass;

    /**
     * The implementation class that shall be used for reference.
     * <p>
     * Usually this field holds the same value as the {@link #implClass} method,
     * but sometimes it holds the user-specified implementation class
     * when it is specified.
     * <p>
     * This is the type that needs to be used for generating fields.
     */
    public final @NotNull JClass implRef;




    protected ClassOutline( CClassInfo _target, JDefinedClass exposedClass, JClass implRef, JDefinedClass _implClass) {
        this.target = _target;
        this.ref = exposedClass;
        this.implRef = implRef;
        this.implClass = _implClass;
    }

    /**
     * Gets all the {@link FieldOutline}s newly declared
     * in this class.
     */
    public final FieldOutline[] getDeclaredFields() {
        List<CPropertyInfo> props = target.getProperties();
        FieldOutline[] fr = new FieldOutline[props.size()];
        for( int i=0; i<fr.length; i++ )
            fr[i] = parent().getField(props.get(i));
        return fr;
    }

    /**
     * Returns the super class of this class, if it has the
     * super class and it is also a JAXB-bound class.
     * Otherwise null.
     */
    public final ClassOutline getSuperClass() {
        CClassInfo s = target.getBaseClass();
        if(s==null)     return null;
        return parent().getClazz(s);
    }

    @Override
    public JDefinedClass getImplClass() {
        return implClass;
    }

    @Override
    public CCustomizable getTarget() {
        return target;
    }
}
