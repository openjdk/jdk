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
package com.sun.xml.internal.xsom.impl;

import com.sun.xml.internal.xsom.XSComplexType;
import com.sun.xml.internal.xsom.XSType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 *
 *
 * @author
 *     Kohsuke Kawaguchi (kohsuke.kawaguchi@sun.com)
 */
class Util {
    private static XSType[] listDirectSubstitutables( XSType _this ) {
        ArrayList r = new ArrayList();

        // TODO: handle @block
        Iterator itr = ((SchemaImpl)_this.getOwnerSchema()).parent.iterateTypes();
        while( itr.hasNext() ) {
            XSType t = (XSType)itr.next();
            if( t.getBaseType()==_this )
                r.add(t);
        }
        return (XSType[]) r.toArray(new XSType[r.size()]);
    }

    public static XSType[] listSubstitutables( XSType _this ) {
        Set substitables = new HashSet();
        buildSubstitutables( _this, substitables );
        return (XSType[]) substitables.toArray(new XSType[substitables.size()]);
    }

    public static void buildSubstitutables( XSType _this, Set substitutables ) {
        if( _this.isLocal() )    return;
        buildSubstitutables( _this, _this, substitutables );
    }

    private static void buildSubstitutables( XSType head, XSType _this, Set substitutables ) {
        if(!isSubstitutable(head,_this))
            return;    // no derived type of _this can substitute head.

        if(substitutables.add(_this)) {
            XSType[] child = listDirectSubstitutables(_this);
            for( int i=0; i<child.length; i++ )
                buildSubstitutables( head, child[i], substitutables );
        }
    }

    /**
     * Implements
     * <code>Validation Rule: Schema-Validity Assessment (Element) 1.2.1.2.4</code>
     */
    private static boolean isSubstitutable( XSType _base, XSType derived ) {
        // too ugly to the point that it's almost unbearable.
        // I mean, it's not even transitive. Thus we end up calling this method
        // for each candidate
        if( _base.isComplexType() ) {
            XSComplexType base = _base.asComplexType();

            for( ; base!=derived; derived=derived.getBaseType() ) {
                if( base.isSubstitutionProhibited( derived.getDerivationMethod() ) )
                    return false;    // Type Derivation OK (Complex)-1
            }
            return true;
        } else {
            // simple type don't have any @block
            return true;
        }
    }

}
