/*
 * Copyright (c) 1997, 2014, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Collection;

import com.sun.codemodel.internal.JClass;
import com.sun.codemodel.internal.JClassContainer;
import com.sun.codemodel.internal.JCodeModel;
import com.sun.codemodel.internal.JPackage;
import com.sun.codemodel.internal.JType;
import com.sun.tools.internal.xjc.ErrorReceiver;
import com.sun.tools.internal.xjc.model.CClassInfo;
import com.sun.tools.internal.xjc.model.CClassInfoParent;
import com.sun.tools.internal.xjc.model.CElementInfo;
import com.sun.tools.internal.xjc.model.CEnumLeafInfo;
import com.sun.tools.internal.xjc.model.CPropertyInfo;
import com.sun.tools.internal.xjc.model.CTypeRef;
import com.sun.tools.internal.xjc.model.Model;
import com.sun.tools.internal.xjc.util.CodeModelClassFactory;

/**
 * Root of the outline. Captures which code is generated for which model component.
 *
 * <p>
 * This object also provides access to various utilities, such as
 * error reporting etc, for the convenience of code that builds the outline.
 *
 * @author Kohsuke Kawaguchi
 */
public interface Outline {
    /**
     * This outline is for this model.
     */
    Model getModel();

    /**
     * Short for {@code getModel().codeModel}.
     */
    JCodeModel getCodeModel();

    /** Gets the object that wraps the generated field for a given {@link CPropertyInfo}. */
    FieldOutline getField( CPropertyInfo fu );

    /**
     * Gets per-package context information.
     *
     * This method works for every visible package
     * (those packages which are supposed to be used by client applications.)
     *
     * @return
     *      If this grammar doesn't produce anything in the specified
     *      package, return null.
     */
    PackageOutline getPackageContext( JPackage _Package );

    /**
     * Returns all the {@link ClassOutline}s known to this object.
     */
    Collection<? extends ClassOutline> getClasses();

    /**
     * Obtains per-class context information.
     */
    ClassOutline getClazz( CClassInfo clazz );

    /**
     * If the {@link CElementInfo} generates a class,
     * returns such a class. Otherwise return null.
     */
    ElementOutline getElement(CElementInfo ei);

    EnumOutline getEnum(CEnumLeafInfo eli);

    /**
     * Gets all the {@link EnumOutline}s.
     */
    Collection<EnumOutline> getEnums();

    /** Gets all package-wise contexts at once. */
    Iterable<? extends PackageOutline> getAllPackageContexts();

    /**
     * Gets a reference to
     * <code>new CodeModelClassFactory(getErrorHandler())</code>.
     */
    CodeModelClassFactory getClassFactory();

    /**
     * Any error during the back-end proccessing should be
     * sent to this object.
     */
    ErrorReceiver getErrorReceiver();

    JClassContainer getContainer(CClassInfoParent parent, Aspect aspect );

    /**
     * Resolves a type reference to the actual (possibly generated) type.
     *
     * Short for {@code resolve(ref.getType(),aspect)}.
     */
    JType resolve(CTypeRef ref,Aspect aspect);

    /**
     * Copies the specified class into the user's package and returns
     * a reference to it.
     */
    JClass addRuntime(Class clazz);
}
