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

import java.util.ArrayList;
import java.util.Collection;

import com.sun.tools.internal.xjc.Plugin;

/**
 * Represents the list of {@link CPluginCustomization}s attached to a JAXB model component.
 *
 * <p>
 * When {@link Plugin}s register the customization namespace URIs through {@link Plugin#getCustomizationURIs()},
 * XJC will treat those URIs just like XJC's own extension "http://java.sun.com/xml/ns/xjc" and make them
 * available as DOM nodes through {@link CPluginCustomization}. A {@link Plugin} can then access
 * this information to change its behavior.
 *
 * @author Kohsuke Kawaguchi
 */
public final class CCustomizations extends ArrayList<CPluginCustomization> {

    /**
     * All {@link CCustomizations} used by a {@link Model} form a single linked list
     * so that we can look for unacknowledged customizations later.
     *
     * @see CPluginCustomization#markAsAcknowledged()
     * @see #setParent(Model,CCustomizable)
     */
    /*package*/ CCustomizations next;

    /**
     * The owner model component that carries these customizations.
     */
    private CCustomizable owner;

    public CCustomizations() {
    }

    public CCustomizations(Collection<? extends CPluginCustomization> cPluginCustomizations) {
        super(cPluginCustomizations);
    }

    /*package*/ void setParent(Model model,CCustomizable owner) {
        if(this.owner!=null)     return;

//        // loop check
//        for( CCustomizations c = model.customizations; c!=null; c=c.next )
//            assert c!=this;

        this.next = model.customizations;
        model.customizations = this;
        assert owner!=null;
        this.owner = owner;
    }

    /**
     * Gets the model component that carries this customization.
     *
     * @return never null.
     */
    public CCustomizable getOwner() {
        assert owner!=null;
        return owner;
    }

    /**
     * Finds the first {@link CPluginCustomization} that belongs to the given namespace URI.
     * @return null if not found
     */
    public CPluginCustomization find( String nsUri ) {
        for (CPluginCustomization p : this) {
            if(fixNull(p.element.getNamespaceURI()).equals(nsUri))
                return p;
        }
        return null;
    }

    /**
     * Finds the first {@link CPluginCustomization} that belongs to the given namespace URI and the local name.
     * @return null if not found
     */
    public CPluginCustomization find( String nsUri, String localName ) {
        for (CPluginCustomization p : this) {
            if(fixNull(p.element.getNamespaceURI()).equals(nsUri)
            && fixNull(p.element.getLocalName()).equals(localName))
                return p;
        }
        return null;
    }

    private String fixNull(String s) {
        if(s==null) return "";
        else        return s;
    }

    /**
     * Convenient singleton instance that represents an empty {@link CCustomizations}.
     */
    public static final CCustomizations EMPTY = new CCustomizations();

    /**
     * Merges two {@link CCustomizations} objects into one.
     */
    public static CCustomizations merge(CCustomizations lhs, CCustomizations rhs) {
        if(lhs==null || lhs.isEmpty())   return rhs;
        if(rhs==null || rhs.isEmpty())   return lhs;

        CCustomizations r = new CCustomizations(lhs);
        r.addAll(rhs);
        return r;
    }

    public boolean equals(Object o) {
        return this==o;
    }

    public int hashCode() {
        return System.identityHashCode(this);
    }
}
