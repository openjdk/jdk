/*
 * Copyright 1996-2002 Sun Microsystems, Inc.  All Rights Reserved.
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
/*
 * Licensed Materials - Property of IBM
 * RMI-IIOP v1.0
 * Copyright IBM Corp. 1998 1999  All Rights Reserved
 *
 */

package com.sun.corba.se.impl.corba;

import java.util.Vector;
import org.omg.CORBA.ContextList;
import org.omg.CORBA.Bounds;
import org.omg.CORBA.ORB;

public class ContextListImpl extends ContextList
{
    private final int    INITIAL_CAPACITY       = 2;
    private final int    CAPACITY_INCREMENT     = 2;

    private org.omg.CORBA.ORB _orb;
    private Vector _contexts;

    public ContextListImpl(org.omg.CORBA.ORB orb)
    {
        // Note: This orb could be an instanceof ORBSingleton or ORB
        _orb = orb;
        _contexts = new Vector(INITIAL_CAPACITY, CAPACITY_INCREMENT);
    }

    public int count()
    {
        return _contexts.size();
    }

    public void add(String ctxt)
    {
        _contexts.addElement(ctxt);
    }

    public String item(int index)
        throws Bounds
    {
        try {
            return (String) _contexts.elementAt(index);
        } catch (ArrayIndexOutOfBoundsException e) {
            throw new Bounds();
        }
    }

    public void remove(int index)
        throws Bounds
    {
        try {
            _contexts.removeElementAt(index);
        } catch (ArrayIndexOutOfBoundsException e) {
            throw new Bounds();
        }
    }

}
