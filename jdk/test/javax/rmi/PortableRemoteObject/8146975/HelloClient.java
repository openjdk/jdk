/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;

import javax.naming.InitialContext;
import javax.naming.Context;
import javax.naming.NameNotFoundException;

import javax.rmi.PortableRemoteObject;



public class HelloClient implements Runnable {
    static final int MAX_RETRY = 10;
    static final int ONE_SECOND = 1000;
    private static boolean responseReceived;

    public static void main(String args[]) throws Exception {
        executeRmiClientCall();
    }

    @Override
    public void run() {
        try {
            executeRmiClientCall();
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }


    public static boolean isResponseReceived () {
        return responseReceived;
    }

    public static void executeRmiClientCall() throws Exception {
        Context ic;
        Object objref;
        HelloInterface helloSvc;
        String response;
        Object testResponse;
        int retryCount = 0;

        ArrayList<Test> listParam = new ArrayList<Test>();
        listParam.add(null);
        System.out.println("HelloClient.main: enter ...");
        while (retryCount < MAX_RETRY) {
            try {
                ic = new InitialContext();
                System.out.println("HelloClient.main: HelloService lookup ...");
                // STEP 1: Get the Object reference from the Name Service
                // using JNDI call.
                objref = ic.lookup("HelloService");
                System.out.println("HelloClient: Obtained a ref. to Hello server.");

                // STEP 2: Narrow the object reference to the concrete type and
                // invoke the method.
                helloSvc = (HelloInterface) PortableRemoteObject.narrow(objref,
                    HelloInterface.class);

                Test3 test3 = new Test3(listParam);
                Test3 test3Response = helloSvc.sayHelloWithTest3(test3);
                System.out.println("Server says: Test3 response  ==  " + test3Response);

                Test3 test3WithNullList = new Test3(null);
                test3Response = helloSvc.sayHelloWithTest3(test3WithNullList);
                System.out.println("Server says: Test3 response  ==  "
                        + test3Response);

                Test4 test4 = new Test4(listParam);
                Test3 test4Response = helloSvc.sayHelloWithTest3(test4);
                System.out.println("Server says: Test4 response  ==  " + test4Response);

                responseReceived = true;
                break;
            } catch (NameNotFoundException nnfEx) {
                System.err.println("NameNotFoundException Caught  .... try again");
                retryCount++;
                try {
                    Thread.sleep(ONE_SECOND);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                continue;
            } catch (Throwable t) {
                System.err.println("Exception " + t + "Caught");
                t.printStackTrace();
                throw new RuntimeException(t);
            }
        }
        System.err.println("HelloClient terminating ");
        try {
            Thread.sleep(ONE_SECOND);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
