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
 *
 * @author Ken Cavanaugh
 */
public interface Guard
{
    public static final class Complement extends GuardBase {
        private Guard guard ;

        public Complement( GuardBase guard )
        {
            super( "not(" + guard.getName() + ")" ) ;
            this.guard = guard ;
        }

        public Result evaluate( FSM fsm, Input in )
        {
            return guard.evaluate( fsm, in ).complement() ;
        }
    }

    public static final class Result {
        private String name ;

        private Result( String name )
        {
            this.name = name ;
        }

        public static Result convert( boolean res )
        {
            return res ? ENABLED : DISABLED ;
        }

        public Result complement()
        {
            if (this == ENABLED)
                return DISABLED ;
            else if (this == DISABLED)
                return ENABLED ;
            else
                return DEFERED ;
        }

        public String toString()
        {
            return "Guard.Result[" + name + "]" ;
        }

        public static final Result ENABLED = new Result( "ENABLED" ) ;
        public static final Result DISABLED = new Result( "DISABLED" ) ;
        public static final Result DEFERED = new Result( "DEFERED" ) ;
    }

    /** Called by the state engine to determine whether a
    * transition is enabled, defered, or disabled.
    * The result is interpreted as follows:
    * <ul>
    * <li>ENABLED if the transition is ready to proceed
    * <li>DISABLED if the transition is not ready to proceed
    * <li>DEFERED if the action associated with the transition
    * is to be deferred.  This means that the input will not be
    * acted upon, but rather it will be saved for later execution.
    * Typically this is implemented using a CondVar wait, and the
    * blocked thread represents the defered input.  The defered
    * input is retried when the thread runs again.
    * </ul>
    *
    * @param FSM fsm is the state machine causing this action.
    * @param Input in is the input that caused the transition.
    */
    public Result evaluate( FSM fsm, Input in ) ;
}

// end of Action.java
