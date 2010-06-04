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

package com.sun.corba.se.spi.orbutil.fsm ;

import java.util.Set ;
import java.util.HashSet ;

import com.sun.corba.se.spi.orbutil.fsm.Input ;
import com.sun.corba.se.spi.orbutil.fsm.StateEngine ;
import com.sun.corba.se.impl.orbutil.fsm.StateEngineImpl ;
import com.sun.corba.se.impl.orbutil.ORBUtility ;
import com.sun.corba.se.spi.orbutil.fsm.FSM ;

/**
 * This is the main class that represents an instance of a state machine
 * using a state engine.  It may be used as a base class, in which case
 * the guards and actions have access to the derived class.
 *
 * @author Ken Cavanaugh
 */
public class FSMImpl implements FSM
{
    private boolean debug ;
    private State state ;
    private StateEngineImpl stateEngine ;

    /** Create an instance of an FSM using the StateEngine
    * in a particular start state.
    */
    public FSMImpl( StateEngine se, State startState )
    {
        this( se, startState, false ) ;
    }

    public FSMImpl( StateEngine se, State startState, boolean debug )
    {
        state = startState ;
        stateEngine = (StateEngineImpl)se ;
        this.debug = debug ;
    }

    /** Return the current state.
    */
    public State getState()
    {
        return state ;
    }

    /** Perform the transition for the given input in the current state.  This proceeds as follows:
    * <p>Let S be the current state of the FSM.
    * If there are guarded actions for S with input in, evaluate their guards successively until
    * all have been evaluted, or one returns a non-DISABLED Result.
    * <ol>
    * <li>If a DEFERED result is returned, retry the input
    * <li>If a ENABLED result is returned, the action for the guarded action
    * is the current action
    * <li>Otherwise there is no enabled action.  If S has a default action and next state, use them; otherwise
    * use the state engine default action (the next state is always the current state).
    * </ol>
    * After the action is available, the transition proceeds as follows:
    * <ol>
    * <li>If the next state is not the current state, execute the current state postAction method.
    * <li>Execute the action.
    * <li>If the next state is not the current state, execute the next state preAction method.
    * <li>Set the current state to the next state.
    * </ol>
    */
    public void doIt( Input in )
    {
        stateEngine.doIt( this, in, debug ) ;
    }

    // Methods for use only by StateEngineImpl

    public void internalSetState( State nextState )
    {
        if (debug) {
            ORBUtility.dprint( this, "Calling internalSetState with nextState = " +
                nextState ) ;
        }

        state = nextState ;

        if (debug) {
            ORBUtility.dprint( this, "Exiting internalSetState with state = " +
                state ) ;
        }
    }
}

// end of FSMImpl.java
