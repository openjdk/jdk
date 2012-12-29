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

package com.sun.tools.internal.xjc.outline;

import java.util.Set;

import javax.xml.bind.annotation.XmlNsForm;
import javax.xml.bind.annotation.XmlSchema;

import com.sun.codemodel.internal.JDefinedClass;
import com.sun.codemodel.internal.JPackage;
import com.sun.tools.internal.xjc.generator.bean.ObjectFactoryGenerator;

/**
 * Outline object that provides per-package information.
 *
 * This interface is accessible from {@link Outline}.
 *
 * @author
 *     Kohsuke Kawaguchi (kohsuke.kawaguchi@sun.com)
 */
public interface PackageOutline {

    /**
     * The exposed package this context is representing.
     *
     * <p>
     * An exposed package is a package visible to users, a package
     * supposed to be used by client applications. Sometime
     * we have another parallel package that's not visible to users.
     */
    JPackage _package();

    /**
     * Generated ObjectFactory from package.
     *
     * This method allows a caller to obtain a reference to such
     * ObjectFactory from its package.
     *
     * Must not be null.
     */
    JDefinedClass objectFactory();

    /**
     * Generates an ObjectFactory class for this package.
     */
    ObjectFactoryGenerator objectFactoryGenerator();

    /**
     * Gets {@link ClassOutline}s whose {@link ClassOutline#_package()}
     * points to this object.
     *
     * @return can be empty but never null.
     */
    Set<? extends ClassOutline> getClasses();

    /**
     * The namespace URI most commonly used in classes in this package.
     * This should be used as the namespace URI for {@link XmlSchema#namespace()}.
     *
     * <p>
     * Null if no default
     */
    public String getMostUsedNamespaceURI();

    /**
     * The element form default for this package.
     * <p>
     * The value is computed by examining what would yield the smallest generated code.
     */
    public XmlNsForm getElementFormDefault();

    /**
     * The attribute form default for this package.
     * <p>
     * The value is computed by examining what would yield the smallest generated code.
     */
    public XmlNsForm getAttributeFormDefault();
}
