/*
 * Copyright 2002-2003 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.corba.se.spi.orbutil.fsm ;

import com.sun.corba.se.impl.orbutil.fsm.NameBase ;

import java.util.Map ;
import java.util.HashMap ;
import java.util.Set ;
import java.util.HashSet ;

import com.sun.corba.se.impl.orbutil.fsm.GuardedAction ;
import com.sun.corba.se.impl.orbutil.fsm.NameBase ;

/** Base class for all states in a StateEngine.  This must be used
* as the base class for all states in transitions added to a StateEngine.
*/
public class StateImpl extends NameBase implements State {
    private Action defaultAction ;
    private State defaultNextState ;
    private Map inputToGuardedActions ;

    public StateImpl( String name )
    {
        super( name ) ;
        defaultAction = null ;
        inputToGuardedActions = new HashMap() ;
    }

    public void preAction( FSM fsm )
    {
    }

    public void postAction( FSM fsm )
    {
    }

    // Methods for use only by StateEngineImpl.

    public State getDefaultNextState()
    {
        return defaultNextState ;
    }

    public void setDefaultNextState( State defaultNextState )
    {
        this.defaultNextState = defaultNextState ;
    }

    public Action getDefaultAction()
    {
        return defaultAction ;
    }

    public void setDefaultAction( Action defaultAction )
    {
        this.defaultAction = defaultAction ;
    }

    public void addGuardedAction( Input in, GuardedAction ga )
    {
        Set gas = (Set)inputToGuardedActions.get( in ) ;
        if (gas == null) {
            gas = new HashSet() ;
            inputToGuardedActions.put( in, gas ) ;
        }

        gas.add( ga ) ;
    }

    public Set getGuardedActions( Input in )
    {
        return (Set)inputToGuardedActions.get( in ) ;
    }
}
