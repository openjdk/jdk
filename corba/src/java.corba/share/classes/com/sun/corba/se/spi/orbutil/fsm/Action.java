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
 * Description goes here
 *
 * @author Ken Cavanaugh
 */
public interface Action
{
        /** Called by the state engine to perform an action
        * before a state transition takes place.  The FSM is
        * passed so that the Action may set the next state in
        * cases when that is required.  FSM and Input together
        * allow actions to be written that depend on the state and
        * input, but this should generally be avoided, as the
        * reason for a state machine in the first place is to cleanly
        * separate the actions and control flow.   Note that an
        * action should complete in a timely manner.  If the state machine
        * is used for concurrency control with multiple threads, the
        * action must not allow multiple threads to run simultaneously
        * in the state machine, as the state could be corrupted.
        * Any exception thrown by the Action for the transition
        * will be propagated to doIt.
        * @param FSM fsm is the state machine causing this action.
        * @param Input in is the input that caused the transition.
        */
        public void doIt( FSM fsm, Input in ) ;
}

// end of Action.java
