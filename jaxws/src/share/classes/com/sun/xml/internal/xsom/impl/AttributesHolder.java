/*
 * Copyright 2006 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.xml.internal.xsom.impl;

import com.sun.xml.internal.xsom.XSAttGroupDecl;
import com.sun.xml.internal.xsom.XSAttributeUse;
import com.sun.xml.internal.xsom.impl.parser.SchemaDocumentImpl;
import org.xml.sax.Locator;

import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public abstract class AttributesHolder extends DeclarationImpl {

    protected AttributesHolder( SchemaDocumentImpl _parent, AnnotationImpl _annon,
        Locator loc, ForeignAttributesImpl _fa, String _name, boolean _anonymous ) {

        super(_parent,_annon,loc,_fa,_parent.getTargetNamespace(),_name,_anonymous);
    }

    /** set the local wildcard. */
    public abstract void setWildcard(WildcardImpl wc);

    /**
     * Local attribute use.
     * It has to be {@link TreeMap} or otherwise we cannot guarantee
     * the order of iteration.
     */
    protected final Map<UName,AttributeUseImpl> attributes = new TreeMap<UName,AttributeUseImpl>(UName.comparator);
    public void addAttributeUse( UName name, AttributeUseImpl a ) {
        attributes.put( name, a );
    }
    /** prohibited attributes. */
    protected final Set<UName> prohibitedAtts = new HashSet<UName>();
    public void addProhibitedAttribute( UName name ) {
        prohibitedAtts.add(name);
    }
    public List<XSAttributeUse> getAttributeUses() {
        // TODO: this is fairly inefficient
        List<XSAttributeUse> v = new ArrayList<XSAttributeUse>();
        v.addAll(attributes.values());
        for( XSAttGroupDecl agd : getAttGroups() )
            v.addAll(agd.getAttributeUses());
        return v;
    }
    public Iterator<XSAttributeUse> iterateAttributeUses() {
        return getAttributeUses().iterator();
    }



    public XSAttributeUse getDeclaredAttributeUse( String nsURI, String localName ) {
        return attributes.get(new UName(nsURI,localName));
    }

    public Iterator<AttributeUseImpl> iterateDeclaredAttributeUses() {
        return attributes.values().iterator();
    }

    public Collection<AttributeUseImpl> getDeclaredAttributeUses() {
        return attributes.values();
    }


    /** {@link Ref.AttGroup}s that are directly refered from this. */
    protected final Set<Ref.AttGroup> attGroups = new HashSet<Ref.AttGroup>();

    public void addAttGroup( Ref.AttGroup a ) { attGroups.add(a); }

    // Iterates all AttGroups which are directly referenced from this component
    // this does not iterate att groups referenced from the base type
    public Iterator<XSAttGroupDecl> iterateAttGroups() {
        return new Iterator<XSAttGroupDecl>() {
            private final Iterator<Ref.AttGroup> itr = attGroups.iterator();
            public boolean hasNext() { return itr.hasNext(); }
            public XSAttGroupDecl next() {
                return itr.next().get();
            }
            public void remove() { itr.remove(); }
        };
    }

    public Set<XSAttGroupDecl> getAttGroups() {
        return new AbstractSet<XSAttGroupDecl>() {
            public Iterator<XSAttGroupDecl> iterator() {
                return iterateAttGroups();
            }

            public int size() {
                return attGroups.size();
            }
        };
    }
}
