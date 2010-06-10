/*
 * Copyright (c) 2002, 2003, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.corba.se.spi.orb.ORBVersion ;

import com.sun.corba.se.spi.ior.iiop.GIOPVersion ;
import com.sun.corba.se.spi.ior.iiop.IIOPProfile ;

import com.sun.corba.se.spi.orb.ORB ;

/** An IOR is represented as a list of profiles.
* Only instances of TaggedProfile are contained in the list.
*/
public interface IOR extends List, Writeable, MakeImmutable
{
    ORB getORB() ;

    /** Return the type id string from the IOR.
    */
    String getTypeId() ;

    /** Return an iterator that iterates over tagged profiles with
    * identifier id.  It is not possible to modify the list through this
    * iterator.
    */
    Iterator iteratorById( int id ) ;

    /** Return a representation of this IOR in the standard GIOP stringified
     * format that begins with "IOR:".
     */
    String stringify() ;

    /** Return a representation of this IOR in the standard GIOP marshalled
     * form.
     */
    org.omg.IOP.IOR getIOPIOR() ;

    /** Return true if this IOR has no profiles.
     */
    boolean isNil() ;

    /** Return true if this IOR is equivalent to ior.  Here equivalent means
     * that the typeids are the same, they have the same number of profiles,
     * and each profile is equivalent to the corresponding profile.
     */
    boolean isEquivalent(IOR ior) ;

    /** Return the IORTemplate for this IOR.  This is simply a list
     * of all TaggedProfileTemplates derived from the TaggedProfiles
     * of the IOR.
     */
    IORTemplateList getIORTemplates() ;

    /** Return the first IIOPProfile in this IOR.
     * XXX THIS IS TEMPORARY FOR BACKWARDS COMPATIBILITY AND WILL BE REMOVED
     * SOON!
     */
    IIOPProfile getProfile() ;
}
