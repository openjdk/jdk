/*
 * Copyright (c) 2000, 2003, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.corba.se.spi.ior;

import java.util.List ;
import java.util.Iterator ;

import org.omg.CORBA_2_3.portable.OutputStream ;

import com.sun.corba.se.spi.ior.Identifiable ;
import com.sun.corba.se.spi.ior.Writeable ;
import com.sun.corba.se.spi.ior.ObjectId ;
import com.sun.corba.se.spi.ior.WriteContents ;

import com.sun.corba.se.spi.orb.ORB ;

/** Base template for creating TaggedProfiles.  A TaggedProfile will often contain
* tagged components.  A template that does not contain components acts like
* an empty immutable list.
*
* @author Ken Cavanaugh
*/
public interface TaggedProfileTemplate extends List, Identifiable,
    WriteContents, MakeImmutable
{
    /** Return an iterator that iterates over tagged components with
    * identifier id.  It is not possible to modify the list through this
    * iterator.
    */
    public Iterator iteratorById( int id ) ;

    /** Create a TaggedProfile from this template.
    */
    TaggedProfile create( ObjectKeyTemplate oktemp, ObjectId id ) ;

    /** Write the profile create( oktemp, id ) to the OutputStream os.
    */
    void write( ObjectKeyTemplate oktemp, ObjectId id, OutputStream os) ;

    /** Return true if temp is equivalent to this template.  Equivalence
     * means that in some sense an invocation on a profile created by this
     * template has the same results as an invocation on a profile
     * created from temp.  Equivalence may be weaker than equality.
     */
    boolean isEquivalent( TaggedProfileTemplate temp );

    /** Return the tagged components in this profile (if any)
     * in the GIOP marshalled form, which is required for Portable
     * Interceptors.  Returns null if either the profile has no
     * components, or if this type of profile can never contain
     * components.
     */
    org.omg.IOP.TaggedComponent[] getIOPComponents(
        ORB orb, int id );
}
