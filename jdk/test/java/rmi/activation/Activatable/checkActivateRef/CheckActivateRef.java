/*
 * Copyright (c) 1998, 1999, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @bug 4105080
 * @summary Activation retry during a remote method call to an activatable
 *          object can cause infinite recursion in some situations. The
 *          RemoteRef contained in the ActivatableRef should never be
 *          an ActivatableRef, but another type.
 * (Needs /othervm to evade JavaTest security manager --aecolley)
 * @author Ann Wollrath
 *
 * @bug 4164971
 * @summary allow non-public activatable class and/or constructor
 *          Main test class hasa non-public constructor to ensure
 *          functionality is in place
 *
 * @library ../../../testlibrary
 * @build TestLibrary RMID ActivateMe CheckActivateRef_Stub
 * @run main/othervm/policy=security.policy/timeout=240 -Djava.rmi.server.ignoreStubClasses=true CheckActivateRef
 * @run main/othervm/policy=security.policy/timeout=240 -Djava.rmi.server.ignoreStubClasses=false CheckActivateRef
 */

import java.io.*;
import java.rmi.*;
import java.rmi.server.*;
import java.rmi.activation.*;
import sun.rmi.server.ActivatableRef;
import java.lang.reflect.*;
import java.util.Properties;

public class CheckActivateRef
        extends Activatable
        implements ActivateMe, Runnable
{

    private CheckActivateRef(ActivationID id, MarshalledObject obj)
        throws ActivationException, RemoteException
    {
        super(id, 0);
    }

    public void ping()
    {}

    /**
     * Spawns a thread to deactivate the object.
     */
    public void shutdown() throws Exception
    {
        (new Thread(this,"CheckActivateRef")).start();
    }

    /**
     * Thread to deactivate object. First attempts to make object
     * inactive (via the inactive method).  If that fails (the
     * object may still have pending/executing calls), then
     * unexport the object forcibly.
     */
    public void run() {
        ActivationLibrary.deactivate(this, getID());
    }

    public static void main(String[] args)  {
        /*
         * The following line is required with the JDK 1.2 VM so that the
         * VM can exit gracefully when this test completes.  Otherwise, the
         * conservative garbage collector will find a handle to the server
         * object on the native stack and not clear the weak reference to
         * it in the RMI runtime's object table.
         */
        Object dummy = new Object();
        RMID rmid = null;
        ActivateMe obj;

        // test should tolerate certain types of failures
        int failures = 0;
        int i = 0;

        System.err.println("\nRegression test for bug 4105080\n");
        System.err.println("java.security.policy = " +
                           System.getProperty("java.security.policy",
                                              "no policy"));


        String propValue =
            System.getProperty("java.rmi.server.useDynamicProxies", "false");
        boolean useDynamicProxies = Boolean.parseBoolean(propValue);

        CheckActivateRef server;
        try {
            TestLibrary.suggestSecurityManager(TestParams.defaultSecurityManager);

            // start an rmid.
            RMID.removeLog();
            rmid = RMID.createRMID();
            rmid.start();

            /* Cause activation groups to have a security policy that will
             * allow security managers to be downloaded and installed
             */
            Properties p = new Properties();
            // this test must always set policies/managers in its
            // activation groups
            p.put("java.security.policy",
                  TestParams.defaultGroupPolicy);
            p.put("java.security.manager",
                  TestParams.defaultSecurityManager);
            p.put("java.rmi.server.useDynamicProxies", propValue);

            /*
             * Activate an object by registering its object
             * descriptor and invoking a method on the
             * stub returned from the register call.
             */
            System.err.println("Create activation group in this VM");
            ActivationGroupDesc groupDesc =
                new ActivationGroupDesc(p, null);
            ActivationSystem system = ActivationGroup.getSystem();
            ActivationGroupID groupID = system.registerGroup(groupDesc);
            ActivationGroup.createGroup(groupID, groupDesc, 0);
            System.err.println("Creating descriptor");
            ActivationDesc desc =
                new ActivationDesc("CheckActivateRef", null, null);
            System.err.println("Registering descriptor");
            obj = (ActivateMe) Activatable.register(desc);

            System.err.println("proxy = " + obj);

            if (useDynamicProxies && !Proxy.isProxyClass(obj.getClass()))
            {
                throw new RuntimeException("proxy is not dynamic proxy");
            }

            /*
             * Loop a bunch of times to force activator to
             * spawn VMs (groups)
             */
            try {
                for (; i < 7; i++) {

                    System.err.println("Activate object via method call");

                    /*
                     * Fix for 4277196: if we got an inactive group
                     * exception, it is likely that we accidentally
                     * invoked a method on an old activation
                     * group. Give some time for the group to go away
                     * and then retry the activation.
                     */
                    try {
                        obj.ping();
                    } catch (RemoteException e) {
                        Exception detail = (Exception) e.detail;
                        if ((detail != null) &&
                            (detail instanceof ActivationException) &&
                            (detail.getMessage().equals("group is inactive")))
                        {
                            try {
                                Thread.sleep(5000);
                            } catch (InterruptedException ex) {
                            }
                            obj.ping();

                        } else {
                            throw e;
                        }
                    }

                    System.err.println("proxy = " + obj);

                    /*
                     * Now that object is activated, check to make sure that
                     * the RemoteRef inside the stub's ActivatableRef
                     * is *not* an ActivatableRef.
                     */
                    ActivatableRef aref;
                    if (obj instanceof RemoteStub) {
                        aref = (ActivatableRef) ((RemoteObject) obj).getRef();
                    } else if (Proxy.isProxyClass(obj.getClass())) {
                        RemoteObjectInvocationHandler handler =
                            (RemoteObjectInvocationHandler)
                            Proxy.getInvocationHandler(obj);
                        aref = (ActivatableRef) handler.getRef();
                    } else {
                        throw new RuntimeException("unknown proxy type");
                    }

                    final ActivatableRef ref = aref;
                    Field f = (Field)
                        java.security.AccessController.doPrivileged
                        (new java.security.PrivilegedExceptionAction() {
                            public Object run() throws Exception {
                                Field ff = ref.getClass().getDeclaredField("ref");
                                ff.setAccessible(true);
                                return ff;
                            }
                        });
                    Object insideRef = f.get(ref);
                    System.err.println("insideRef = " + insideRef);
                    if (insideRef instanceof ActivatableRef) {
                        TestLibrary.bomb("Embedded ref is an ActivatableRef");
                    } else {
                        System.err.println("ActivatableRef's embedded ref type: " +
                                           insideRef.getClass().getName());
                    }

                    /*
                     * Clean up object too.
                     */
                    System.err.println("Deactivate object via method call");
                    obj.shutdown();

                    try {
                        // give activation group time to go away
                        Thread.sleep(3000);
                    } catch (InterruptedException e) {
                    }
                }
            } catch (java.rmi.UnmarshalException ue) {
                // account for test's activation race condition
                if (ue.detail instanceof java.io.IOException) {
                    if ((failures ++) >= 3) {
                        throw ue;
                    }
                } else {
                    throw ue;
                }
            }

            System.err.println("\nsuccess: CheckActivateRef test passed ");

        } catch (java.rmi.activation.ActivationException e) {
            // test only needs to pass 3 times in 7
            if (i < 4) {
                TestLibrary.bomb(e);
            }
        } catch (Exception e) {
            if (e instanceof java.security.PrivilegedActionException)
                e = ((java.security.PrivilegedActionException)e).getException();
            TestLibrary.bomb("\nfailure: unexpected exception " +
                             e.getClass().getName(), e);

        } finally {
            ActivationLibrary.rmidCleanup(rmid);
            obj = null;
        }
    }
}
