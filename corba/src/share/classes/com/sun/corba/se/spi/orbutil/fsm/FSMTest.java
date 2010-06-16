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

import com.sun.corba.se.spi.orbutil.fsm.Input ;
import com.sun.corba.se.spi.orbutil.fsm.Action ;
import com.sun.corba.se.spi.orbutil.fsm.Guard ;
import com.sun.corba.se.spi.orbutil.fsm.StateEngine ;
import com.sun.corba.se.spi.orbutil.fsm.StateImpl ;
import com.sun.corba.se.spi.orbutil.fsm.StateEngineFactory ;
import com.sun.corba.se.spi.orbutil.fsm.FSM ;

class TestInput {
    TestInput( Input value, String msg )
    {
        this.value = value ;
        this.msg = msg ;
    }

    public String toString()
    {
        return "Input " + value + " : " + msg ;
    }

    public Input getInput()
    {
        return value ;
    }

    Input value ;
    String msg ;
}

class TestAction1 implements Action
{
    public void doIt( FSM fsm, Input in )
    {
        System.out.println( "TestAction1:" ) ;
        System.out.println( "\tlabel    = " + label ) ;
        System.out.println( "\toldState = " + oldState ) ;
        System.out.println( "\tnewState = " + newState ) ;
        if (label != in)
            throw new Error( "Unexcepted Input " + in ) ;
        if (oldState != fsm.getState())
            throw new Error( "Unexpected old State " + fsm.getState() ) ;
    }

    public TestAction1( State oldState, Input label, State newState )
    {
        this.oldState = oldState ;
        this.newState = newState ;
        this.label = label ;
    }

    private State oldState ;
    private Input label ;
    private State newState ;
}

class TestAction2 implements Action
{
    private State oldState ;
    private State newState ;

    public void doIt( FSM fsm, Input in )
    {
        System.out.println( "TestAction2:" ) ;
        System.out.println( "\toldState = " + oldState ) ;
        System.out.println( "\tnewState = " + newState ) ;
        System.out.println( "\tinput    = " + in ) ;
        if (oldState != fsm.getState())
            throw new Error( "Unexpected old State " + fsm.getState() ) ;
    }

    public TestAction2( State oldState, State newState )
    {
        this.oldState = oldState ;
        this.newState = newState ;
    }
}

class TestAction3 implements Action {
    private State oldState ;
    private Input label ;

    public void doIt( FSM fsm, Input in )
    {
        System.out.println( "TestAction1:" ) ;
        System.out.println( "\tlabel    = " + label ) ;
        System.out.println( "\toldState = " + oldState ) ;
        if (label != in)
            throw new Error( "Unexcepted Input " + in ) ;
    }

    public TestAction3( State oldState, Input label )
    {
        this.oldState = oldState ;
        this.label = label ;
    }
}

class NegateGuard implements Guard {
    Guard guard ;

    public NegateGuard( Guard guard )
    {
        this.guard = guard ;
    }

    public Guard.Result evaluate( FSM fsm, Input in )
    {
        return guard.evaluate( fsm, in ).complement() ;
    }
}

class MyFSM extends FSMImpl {
    public MyFSM( StateEngine se )
    {
        super( se, FSMTest.STATE1 ) ;
    }

    public int counter = 0 ;
}

public class FSMTest {
    public static final State   STATE1 = new StateImpl( "1" ) ;
    public static final State   STATE2 = new StateImpl( "2" ) ;
    public static final State   STATE3 = new StateImpl( "3" ) ;
    public static final State   STATE4 = new StateImpl( "4" ) ;

    public static final Input   INPUT1 = new InputImpl( "1" ) ;
    public static final Input   INPUT2 = new InputImpl( "2" ) ;
    public static final Input   INPUT3 = new InputImpl( "3" ) ;
    public static final Input   INPUT4 = new InputImpl( "4" ) ;

    private Guard counterGuard = new Guard() {
        public Guard.Result evaluate( FSM fsm, Input in )
        {
            MyFSM mfsm = (MyFSM) fsm ;
            return Guard.Result.convert( mfsm.counter < 3 ) ;
        }
    } ;

    private static void add1( StateEngine se, State oldState, Input in, State newState )
    {
        se.add( oldState, in, new TestAction1( oldState, in, newState ), newState ) ;
    }

    private static void add2( StateEngine se, State oldState, State newState )
    {
        se.setDefault( oldState, new TestAction2( oldState, newState ), newState ) ;
    }

    public static void main( String[] args )
    {
        TestAction3 ta3 = new TestAction3( STATE3, INPUT1 ) ;

        StateEngine se = StateEngineFactory.create() ;
        add1( se, STATE1, INPUT1, STATE1 ) ;
        add2( se, STATE1,         STATE2 ) ;

        add1( se, STATE2, INPUT1, STATE2 ) ;
        add1( se, STATE2, INPUT2, STATE2 ) ;
        add1( se, STATE2, INPUT3, STATE1 ) ;
        add1( se, STATE2, INPUT4, STATE3 ) ;

        se.add(   STATE3, INPUT1, ta3,  STATE3 ) ;
        se.add(   STATE3, INPUT1, ta3,  STATE4 ) ;
        add1( se, STATE3, INPUT2, STATE1 ) ;
        add1( se, STATE3, INPUT3, STATE2 ) ;
        add1( se, STATE3, INPUT4, STATE2 ) ;

        MyFSM fsm = new MyFSM( se ) ;
        TestInput in11 = new TestInput( INPUT1, "1.1" ) ;
        TestInput in12 = new TestInput( INPUT1, "1.2" ) ;
        TestInput in21 = new TestInput( INPUT2, "2.1" ) ;
        TestInput in22 = new TestInput( INPUT2, "2.2" ) ;
        TestInput in31 = new TestInput( INPUT3, "3.1" ) ;
        TestInput in32 = new TestInput( INPUT3, "3.2" ) ;
        TestInput in33 = new TestInput( INPUT3, "3.3" ) ;
        TestInput in41 = new TestInput( INPUT4, "4.1" ) ;

        fsm.doIt( in11.getInput() ) ;
        fsm.doIt( in12.getInput() ) ;
        fsm.doIt( in41.getInput() ) ;
        fsm.doIt( in11.getInput() ) ;
        fsm.doIt( in22.getInput() ) ;
        fsm.doIt( in31.getInput() ) ;
        fsm.doIt( in33.getInput() ) ;
        fsm.doIt( in41.getInput() ) ;
        fsm.doIt( in41.getInput() ) ;
        fsm.doIt( in41.getInput() ) ;
        fsm.doIt( in22.getInput() ) ;
        fsm.doIt( in32.getInput() ) ;
        fsm.doIt( in41.getInput() ) ;
        fsm.doIt( in11.getInput() ) ;
        fsm.doIt( in12.getInput() ) ;
        fsm.doIt( in11.getInput() ) ;
        fsm.doIt( in11.getInput() ) ;
        fsm.doIt( in11.getInput() ) ;
        fsm.doIt( in11.getInput() ) ;
        fsm.doIt( in11.getInput() ) ;
    }
}
