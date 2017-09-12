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

package com.sun.corba.se.impl.orbutil.fsm ;

import java.util.HashMap ;
import java.util.HashSet ;
import java.util.Set ;
import java.util.Iterator ;

import org.omg.CORBA.INTERNAL ;

import com.sun.corba.se.impl.orbutil.ORBUtility ;

import com.sun.corba.se.spi.orbutil.fsm.Input ;
import com.sun.corba.se.spi.orbutil.fsm.Guard ;
import com.sun.corba.se.spi.orbutil.fsm.Action ;
import com.sun.corba.se.spi.orbutil.fsm.ActionBase ;
import com.sun.corba.se.spi.orbutil.fsm.State ;
import com.sun.corba.se.spi.orbutil.fsm.StateEngine ;
import com.sun.corba.se.spi.orbutil.fsm.StateImpl ;
import com.sun.corba.se.spi.orbutil.fsm.FSM ;
import com.sun.corba.se.spi.orbutil.fsm.FSMImpl ;

import com.sun.corba.se.impl.orbutil.fsm.GuardedAction ;

/**
 * Encodes the state transition function for a finite state machine.
 *
 * @author Ken Cavanaugh
 */
public class StateEngineImpl implements StateEngine
{
    // An action that does nothing at all.
    private static Action emptyAction = new ActionBase( "Empty" )
    {
        public void doIt( FSM fsm, Input in )
        {
        }
    } ;

    private boolean initializing ;
    private Action defaultAction ;

    public StateEngineImpl()
    {
        initializing = true ;
        defaultAction = new ActionBase("Invalid Transition")
            {
                public void doIt( FSM fsm, Input in )
                {
                    throw new INTERNAL(
                        "Invalid transition attempted from " +
                            fsm.getState() + " under " + in ) ;
                }
            } ;
    }

    public StateEngine add( State oldState, Input input, Guard guard, Action action,
        State newState ) throws IllegalArgumentException,
        IllegalStateException
    {
        mustBeInitializing() ;

        StateImpl oldStateImpl = (StateImpl)oldState ;
        GuardedAction ga = new GuardedAction( guard, action, newState ) ;
        oldStateImpl.addGuardedAction( input, ga ) ;

        return this ;
    }

    public StateEngine add( State oldState, Input input, Action action,
        State newState ) throws IllegalArgumentException,
        IllegalStateException
    {
        mustBeInitializing() ;

        StateImpl oldStateImpl = (StateImpl)oldState ;
        GuardedAction ta = new GuardedAction( action, newState ) ;
        oldStateImpl.addGuardedAction( input, ta ) ;

        return this ;
    }

    public StateEngine setDefault( State oldState, Action action, State newState )
        throws IllegalArgumentException, IllegalStateException
    {
        mustBeInitializing() ;

        StateImpl oldStateImpl = (StateImpl)oldState ;
        oldStateImpl.setDefaultAction( action ) ;
        oldStateImpl.setDefaultNextState( newState ) ;

        return this ;
    }

    public StateEngine setDefault( State oldState, State newState )
        throws IllegalArgumentException, IllegalStateException
    {
        return setDefault( oldState, emptyAction, newState ) ;
    }

    public StateEngine setDefault( State oldState )
        throws IllegalArgumentException, IllegalStateException
    {
        return setDefault( oldState, oldState ) ;
    }

    public void done() throws IllegalStateException
    {
        mustBeInitializing() ;

        // optimize FSM here if desired.  For example,
        // we could choose different strategies for implementing
        // the state transition function based on the distribution
        // of values for states and input labels.

        initializing = false ;
    }

    public void setDefaultAction( Action act ) throws IllegalStateException
    {
        mustBeInitializing() ;
        defaultAction = act ;
    }

    public void doIt( FSM fsm, Input in, boolean debug )
    {
        // This method is present only for debugging.
        // innerDoIt does the actual transition.

        if (debug)
            ORBUtility.dprint( this, "doIt enter: currentState = " +
                fsm.getState() + " in = " + in ) ;

        try {
            innerDoIt( fsm, in, debug ) ;
        } finally {
            if (debug)
                ORBUtility.dprint( this, "doIt exit" ) ;
        }
    }

    private StateImpl getDefaultNextState( StateImpl currentState )
    {
        // Use the currentState defaults if
        // set, otherwise use the state engine default.
        StateImpl nextState = (StateImpl)currentState.getDefaultNextState() ;
        if (nextState == null)
            // The state engine default never changes the state
            nextState = currentState ;

        return nextState ;
    }

    private Action getDefaultAction( StateImpl currentState )
    {
        Action action = currentState.getDefaultAction() ;
        if (action == null)
            action = defaultAction ;

        return action ;
    }

    private void innerDoIt( FSM fsm, Input in, boolean debug )
    {
        if (debug) {
            ORBUtility.dprint( this, "Calling innerDoIt with input " + in ) ;
        }

        // Locals needed for performing the state transition, once we determine
        // the required transition.
        StateImpl currentState = null ;
        StateImpl nextState = null ;
        Action action = null ;

        // Do until no guard has deferred.
        boolean deferral = false ;
        do {
            deferral = false ; // clear this after each deferral!
            currentState = (StateImpl)fsm.getState() ;
            nextState = getDefaultNextState( currentState ) ;
            action = getDefaultAction( currentState ) ;

            if (debug) {
                ORBUtility.dprint( this, "currentState      = " + currentState ) ;
                ORBUtility.dprint( this, "in                = " + in ) ;
                ORBUtility.dprint( this, "default nextState = " + nextState    ) ;
                ORBUtility.dprint( this, "default action    = " + action ) ;
            }

            Set gas = currentState.getGuardedActions(in) ;
            if (gas != null) {
                Iterator iter = gas.iterator() ;

                // Search for a guard that is not DISABLED.
                // All DISABLED means use defaults.
                while (iter.hasNext()) {
                    GuardedAction ga = (GuardedAction)iter.next() ;
                    Guard.Result gr = ga.getGuard().evaluate( fsm, in ) ;
                    if (debug)
                        ORBUtility.dprint( this,
                            "doIt: evaluated " + ga + " with result " + gr ) ;

                    if (gr == Guard.Result.ENABLED) {
                        // ga has the next state and action.
                        nextState = (StateImpl)ga.getNextState() ;
                        action = ga.getAction() ;
                        if (debug) {
                            ORBUtility.dprint( this, "nextState = " + nextState ) ;
                            ORBUtility.dprint( this, "action    = " + action ) ;
                        }
                        break ;
                    } else if (gr == Guard.Result.DEFERED) {
                        deferral = true ;
                        break ;
                    }
                }
            }
        } while (deferral) ;

        performStateTransition( fsm, in, nextState, action, debug ) ;
    }

    private void performStateTransition( FSM fsm, Input in,
        StateImpl nextState, Action action, boolean debug )
    {
        StateImpl currentState = (StateImpl)fsm.getState() ;

        // Perform the state transition.  Pre and post actions are only
        // performed if the state changes (see UML hidden transitions).

        boolean different = !currentState.equals( nextState ) ;

        if (different) {
            if (debug)
                ORBUtility.dprint( this,
                    "doIt: executing postAction for state " + currentState ) ;
            try {
                currentState.postAction( fsm ) ;
            } catch (Throwable thr) {
                if (debug)
                    ORBUtility.dprint( this,
                        "doIt: postAction threw " + thr ) ;

                if (thr instanceof ThreadDeath)
                    throw (ThreadDeath)thr ;
            }
        }

        try {
            // Note that action may be null in a transition, which simply
            // means that no action is needed.  Note that action.doIt may
            // throw an exception, in which case the exception is
            // propagated after making sure that the transition is properly
            // completed.
            if (action != null)
                action.doIt( fsm, in ) ;
        } finally {
            if (different) {
                if (debug)
                    ORBUtility.dprint( this,
                        "doIt: executing preAction for state " + nextState ) ;

                try {
                    nextState.preAction( fsm ) ;
                } catch (Throwable thr) {
                    if (debug)
                        ORBUtility.dprint( this,
                            "doIt: preAction threw " + thr ) ;

                    if (thr instanceof ThreadDeath)
                        throw (ThreadDeath)thr ;
                }

                ((FSMImpl)fsm).internalSetState( nextState ) ;
            }

            if (debug)
                ORBUtility.dprint( this, "doIt: state is now " + nextState ) ;
        }
    }

    public FSM makeFSM( State startState ) throws IllegalStateException
    {
        mustNotBeInitializing() ;

        return new FSMImpl( this, startState ) ;
    }

    private void mustBeInitializing() throws IllegalStateException
    {
        if (!initializing)
            throw new IllegalStateException(
                "Invalid method call after initialization completed" ) ;
    }

    private void mustNotBeInitializing() throws IllegalStateException
    {
        if (initializing)
            throw new IllegalStateException(
                "Invalid method call before initialization completed" ) ;
    }
}

// end of StateEngineImpl.java
