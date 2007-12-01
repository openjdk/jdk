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

package com.sun.corba.se.impl.orbutil.fsm ;

import com.sun.corba.se.spi.orbutil.fsm.Guard ;
import com.sun.corba.se.spi.orbutil.fsm.GuardBase ;
import com.sun.corba.se.spi.orbutil.fsm.Input ;
import com.sun.corba.se.spi.orbutil.fsm.Action ;
import com.sun.corba.se.spi.orbutil.fsm.State ;
import com.sun.corba.se.spi.orbutil.fsm.FSM ;

public class GuardedAction {
    private static Guard trueGuard = new GuardBase( "true" ) {
        public Guard.Result evaluate( FSM fsm, Input in )
        {
            return Guard.Result.ENABLED ;
        }
    } ;

    private Guard guard ;
    private Action action ;
    private State nextState ;

    public GuardedAction( Action action, State nextState )
    {
        this.guard = trueGuard ;
        this.action = action ;
        this.nextState = nextState ;
    }

    public GuardedAction( Guard guard, Action action, State nextState )
    {
        this.guard = guard ;
        this.action = action ;
        this.nextState = nextState ;
    }

    public String toString()
    {
        return "GuardedAction[action=" + action + " guard=" + guard +
            " nextState=" + nextState + "]" ;
    }

    public Action getAction() { return action ; }
    public Guard getGuard() { return guard ; }
    public State getNextState() { return nextState ; }
}
