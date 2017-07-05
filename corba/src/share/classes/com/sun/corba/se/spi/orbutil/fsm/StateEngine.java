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

package com.sun.corba.se.spi.orbutil.fsm;

/**
 * A StateEngine defines the state transition function for a
 * finite state machine (FSM). A FSM always has a current state.
 * In response to an Input, the FSM performs an Action and
 * makes a transition to a new state.  Note that any object can
 * be used as an input if it supports the Input interface.
 * For example, a protocol message may be an input.  The FSM
 * uses only the result of calling getLabel on the Input to
 * drive the transition.
 * <p>
 * The function can be non-deterministic
 * in that the same input may cause transitions to different new
 * states from the current state.  In this case, the action that
 * is executed for the transition must set the correct new state.
 *
 * @author Ken Cavanaugh
 */
public interface StateEngine
{
        /** Add a new transition (old,in,guard,act,new) to the state engine.
        * Multiple calls to add with the same old and in are permitted,
        * in which case only a transition in which the guard evaluates to
        * true will be taken.  If no such transition is enabled, a default
        * will be taken.  If more than one transition is enabled, one will
        * be chosen arbitrarily.
        * This method can only be called before done().  An attempt to
        * call it after done() results in an IllegalStateException.
        */
        public StateEngine add( State oldState, Input input, Guard guard,
            Action action, State newState ) throws IllegalStateException ;

        /** Add a transition with a guard that always evaluates to true.
        */
        public StateEngine add( State oldState, Input input,
            Action action, State newState ) throws IllegalStateException ;

        /** Set the default transition and action for a state.
        * This transition will be used if no more specific transition was
        * defined for the actual input.  Repeated calls to this method
        * simply change the default.
        * This method can only be called before done().  An attempt to
        * call it after done() results in an IllegalStateException.
        */
        public StateEngine setDefault( State oldState, Action action, State newState )
                throws IllegalStateException ;

        /** Equivalent to setDefault( oldState, act, newState ) where act is an
         * action that does nothing.
         */
        public StateEngine setDefault( State oldState, State newState )
                throws IllegalStateException ;

        /** Euaivalent to setDefault( oldState, oldState )
         */
        public StateEngine setDefault( State oldState )
                throws IllegalStateException ;

        /** Set the default action used in this state engine.  This is the
        * action that is called whenever there is no applicable transition.
        * Normally this would simply flag an error.  This method can only
        * be called before done().  An attempt to
        * call it after done() results in an IllegalStateException.
        */
        public void setDefaultAction( Action act ) throws IllegalStateException ;

        /** Called after all transitions have been added to the state engine.
        * This provides an opportunity for the implementation to optimize
        * its representation before the state engine is used.  This method
        * may only be called once.  An attempt to call it more than once
        * results in an IllegalStateException.
        */
        public void done() throws IllegalStateException ;

        /** Create an instance of a FSM that uses this state engine.
        * The initial state of the FSM will be the stateState specified
        * here.  This method can only be called after done().  An attempt
        * to call it before done results in an IllegalStateException.
        */
        public FSM makeFSM( State startState ) throws IllegalStateException ;
}

// end of StateEngine.java
