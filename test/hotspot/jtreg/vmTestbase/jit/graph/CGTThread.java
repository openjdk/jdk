/*
 * Copyright (c) 1998, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

package jit.graph;
import java.util.*;
import java.lang.*;
import java.lang.reflect.*;
import nsk.share.TestFailure;

class CGTThread extends Thread
{
    private String ThreadName = null;
    private Vector Sumation = new Vector(100000);
    private Vector IDlist = new Vector(100000);


    CGTThread( String name )
    {
                ThreadName = name;
                setName(name);
    }


    public void run()
    {
                if (Globals.VERBOSE)
                    System.out.println("\t\t" + Thread.currentThread().getName() + " started");
                Long numFcalls = new Long(Globals.RANDOM_LOOP - 1);
                Integer staticFcalls = new Integer(Globals.STATIC_LOOP);
                MethodData methodCallStr = Globals.nextRandomMethod();
                Globals.addFunctionIDToVector(methodCallStr.id, IDlist);
                Throwable invocationExcept = null;

                boolean skipVerify = false;

                try
                {
                        methodCallStr.nextMethod.invoke(methodCallStr.instance, new Object []{Sumation, IDlist, numFcalls, staticFcalls});
                }
                catch (IllegalAccessException iax)
                {
                        throw new TestFailure("Illegal Access Exception");
                }
                catch (InvocationTargetException itx)
                {
                        System.out.println("Invocation Target Exception");
                        invocationExcept = itx.getTargetException();
                        System.out.println(invocationExcept);
                        if (invocationExcept.getClass() == itx.getClass())
                        {
                                System.out.println("Processing Exception Invocation Target Exception");
                                while (invocationExcept.getClass() == itx.getClass())
                                        invocationExcept = ((InvocationTargetException)invocationExcept).getTargetException();
                                System.out.println(invocationExcept);
                        }
                        if (invocationExcept instanceof StackOverflowError)
                        //StackOverFlow is not a failure
                        {
                                System.out.println("Warning: stack overflow: skipping verification...");
                                skipVerify = true;
                        }
                        else if (invocationExcept instanceof OutOfMemoryError)
                        //OutOfMemoryError is not a failure
                        {
                                System.out.println("Warning: test devoured heap ;), skipping verification...");
                                skipVerify = true;
                        }
                        else
                        {
                                invocationExcept.printStackTrace();
                                System.exit(1);
                        }
                }

            if( !skipVerify )
                verify(Sumation, IDlist);
    }

    void verify(Vector Sum, Vector ID)
    {
                long oldsum = 0;
                long newsum;
                System.out.println(ThreadName + " has begun call stack validation");
                if (Sum.size() != ID.size())
                    {
                                System.out.println("Vector Length's Do Not Match, VERIFY ERROR");
                                System.out.println("Thread Name: " + ThreadName);
                                throw new TestFailure("Sumation Element Count = " + Sum.size() + " ID Element Count = " +ID.size());
                    }
                long vectorSize = Sum.size();
                while (!Sum.isEmpty())
                    {
                                if (CGT.shouldFinish())
                                {
                                   System.out.println(Thread.currentThread().getName() + ": skipping verification due to timeout");
                                   return;
                                }

                                newsum = ((Long)Sum.firstElement()).longValue();
                                Sum.removeElementAt(0);

                                int functionID = ((Integer)ID.firstElement()).intValue();
                                ID.removeElementAt(0);

                                if ((newsum - oldsum) != (functionID))
                                    {
                                                System.out.println("Function Call structure invalid, VERIFY ERROR");
                                                System.out.println("Thread Name: " + ThreadName);
                                                System.out.println("Expected = " +(newsum - oldsum) + " Actual = " +functionID);
                                                throw new TestFailure("Test failed.");
//                                                System.exit(1);
                                    }
                                oldsum = newsum;
                    }
                Globals.decNumThreads();
                System.out.println(ThreadName + "'s function call structure validated succesfully ("+vectorSize+" calls validated)");
    }

}
