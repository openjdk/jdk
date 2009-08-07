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


package com.sun.xml.internal.xsom;

import java.util.Iterator;
import java.util.Collection;

/**
 * Common aspect of {@link XSComplexType} and {@link XSAttGroupDecl}
 * as the container of attribute uses/attribute groups.
 *
 * @author
 *     Kohsuke Kawaguchi (kohsuke.kawaguchi@sun.com)
 */
public interface XSAttContainer extends XSDeclaration {
    XSWildcard getAttributeWildcard();

    /**
     * Looks for the attribute use with the specified name from
     * all the attribute uses that are directly/indirectly
     * referenced from this component.
     *
     * <p>
     * This is the exact implementation of the "attribute use"
     * schema component.
     */
    XSAttributeUse getAttributeUse( String nsURI, String localName );

    /**
     * Lists all the attribute uses that are directly/indirectly
     * referenced from this component.
     *
     * <p>
     * This is the exact implementation of the "attribute use"
     * schema component.
     */
    Iterator<? extends XSAttributeUse> iterateAttributeUses();

    /**
     * Gets all the attribute uses.
     */
    Collection<? extends XSAttributeUse> getAttributeUses();

    /**
     * Looks for the attribute use with the specified name from
     * the attribute uses which are declared in this complex type.
     *
     * This does not include att uses declared in att groups that
     * are referenced from this complex type, nor does include
     * att uses declared in base types.
     */
    XSAttributeUse getDeclaredAttributeUse( String nsURI, String localName );

    /**
     * Lists all the attribute uses that are declared in this complex type.
     */
    Iterator<? extends XSAttributeUse> iterateDeclaredAttributeUses();

    /**
     * Lists all the attribute uses that are declared in this complex type.
     */
    Collection<? extends XSAttributeUse> getDeclaredAttributeUses();


    /**
     * Iterates all AttGroups which are directly referenced from
     * this component.
     */
    Iterator<? extends XSAttGroupDecl> iterateAttGroups();

    /**
     * Iterates all AttGroups which are directly referenced from
     * this component.
     */
    Collection<? extends XSAttGroupDecl> getAttGroups();
}
