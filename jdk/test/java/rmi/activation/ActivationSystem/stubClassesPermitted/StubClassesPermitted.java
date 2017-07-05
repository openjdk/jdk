/*
 * Copyright (c) 1999, 2012, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4179055
 * @summary Some java apps need to have access to read "accessClassInPackage.sun.rmi.server"
 * @author Laird Dornin
 *
 * @library ../../../testlibrary
 * @build TestLibrary RMID ActivationLibrary
 *     CanCreateStubs StubClassesPermitted_Stub
 * @run main/othervm/policy=security.policy/secure=java.lang.SecurityManager/timeout=240 StubClassesPermitted
 */

import java.io.*;
import java.rmi.*;
import java.rmi.server.*;
import java.rmi.registry.Registry;
import java.rmi.activation.*;
import java.security.CodeSource;
import java.util.Properties;
import java.util.StringTokenizer;

/**
 * The RMI activation system needs to explicitly allow itself to
 * create the following sun.* classes on behalf of code that runs with
 * user privileges and needs to make use of RMI activation:
 *
 *     sun.rmi.server.Activation$ActivationMonitorImpl_Stub
 *     sun.rmi.server.Activation$ActivationSystemImpl_Stub
 *     sun.rmi.registry.RegistryImpl_Stub
 *
 * The test causes the activation system to need to create each of
 * these classes in turn.  The test will fail if the activation system
 * does not allow these classes to be created.
 */
public class StubClassesPermitted
    extends Activatable implements Runnable, CanCreateStubs
{
    public static boolean sameGroup = false;
    private static int registryPort = -1;
    private static CanCreateStubs canCreateStubs = null;
    private static Registry registry = null;

    public static void main(String args[]) {

        sameGroup = true;

        RMID rmid = null;

        System.err.println("\nRegression test for bug/rfe 4179055\n");

        try {
            TestLibrary.suggestSecurityManager("java.lang.SecurityManager");

            registry = TestLibrary.createRegistryOnUnusedPort();
            registryPort = TestLibrary.getRegistryPort(registry);

            // must run with java.lang.SecurityManager or the test
            // result will be nullified if running with a build where
            // 4180392 has not been fixed.
            String smClassName =
                System.getSecurityManager().getClass().getName();
            if (!smClassName.equals("java.lang.SecurityManager")) {
                TestLibrary.bomb("Test must run with java.lang.SecurityManager");
            }

            // start an rmid.
            RMID.removeLog();
            rmid = RMID.createRMID();
            rmid.start();

            //rmid.addOptions(new String[] {"-C-Djava.rmi.server.logCalls=true"});

            // Ensure that activation groups run with the correct
            // security manager.
            //
            Properties p = new Properties();
            p.put("java.security.policy",
                  TestParams.defaultGroupPolicy);
            p.put("java.security.manager",
                  "java.lang.SecurityManager");

            // This action causes the following classes to be created
            // in this VM (RMI must permit the creation of these classes):
            //
            // sun.rmi.server.Activation$ActivationSystemImpl_Stub
            // sun.rmi.server.Activation$ActivationMonitorImpl_Stub
            //
            System.err.println("Create activation group, in a new VM");
            ActivationGroupDesc groupDesc =
                new ActivationGroupDesc(p, null);
            ActivationSystem system = ActivationGroup.getSystem();
            ActivationGroupID groupID = system.registerGroup(groupDesc);

            System.err.println("register activatable");
            // Fix for: 4271615: make sure activation group runs in a new VM
            ActivationDesc desc = new ActivationDesc
                (groupID, "StubClassesPermitted", null, null);
            canCreateStubs = (CanCreateStubs) Activatable.register(desc);

            // ensure registry stub can be passed in a remote call
            System.err.println("getting the registry");
            registry = canCreateStubs.getRegistry();

            // make sure a client cant load just any sun.* class, just
            // as a sanity check, try to create a class we are not
            // allowed to access but which was passed in a remote call
            try {
                System.err.println("accessing forbidden class");
                Object secureRandom = canCreateStubs.getForbiddenClass();

                TestLibrary.bomb("test allowed to access forbidden class," +
                                 " sun.security.provider.SecureRandom");
            } catch (java.security.AccessControlException e) {

                // Make sure we received a *local* AccessControlException
                ByteArrayOutputStream bout = new ByteArrayOutputStream();
                PrintStream ps = new PrintStream(bout);
                e.printStackTrace(ps);
                ps.flush();
                String trace = new String(bout.toByteArray());
                if ((trace.indexOf("exceptionReceivedFromServer") >= 0) ||
                    trace.equals(""))
                {
                    throw e;
                }
                System.err.println("received expected local access control exception");
            }

            // make sure that an ActivationGroupID can be passed in a
            // remote call; this is slightly more inclusive than
            // just passing a reference to the activation system
            System.err.println("returning group desc");
            canCreateStubs.returnGroupID();

            // Clean up object
            System.err.println
                ("Deactivate object via method call");
            canCreateStubs.shutdown();

            System.err.println
                ("\nsuccess: StubClassesPermitted test passed ");

        } catch (Exception e) {
            TestLibrary.bomb("\nfailure: unexpected exception ", e);
        } finally {
            try {
                Thread.sleep(4000);
            } catch (InterruptedException e) {
            }

            canCreateStubs = null;
            ActivationLibrary.rmidCleanup(rmid);
            System.err.println("rmid shut down");
        }
    }

    static ActivationGroupID GroupID = null;

    /**
     * implementation of CanCreateStubs
     */
    public StubClassesPermitted
        (ActivationID id, MarshalledObject mo) throws RemoteException
    {
        // register/export anonymously
        super(id, 0);

        // obtain reference to the test registry
        registry = java.rmi.registry.LocateRegistry.
            getRegistry(registryPort);
    }

    /**
     * Spawns a thread to deactivate the object.
     */
    public void shutdown() throws Exception {
        (new Thread(this,"StubClassesPermitted")).start();
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

    /**
     * Return a reference to the RMI registry, to make sure that
     * the stub for it can be deserialized in the test client VM.
     */
    public Registry getRegistry() throws RemoteException {
        if (sameGroup) {
            System.out.println("in same group");
        } else {
            System.out.println("not in same group");
        }
        return registry;
    }

    /**
     * Remote call to create and return a random serializable sun.*
     * class, the test should get a local security exception when
     * trying to create the class.  Ensure that not all sun.* classes
     * can be resolved in a remote call.
     */
    public Object getForbiddenClass() throws RemoteException {
        System.err.println("creating sun class");
        return new sun.security.provider.SecureRandom();
    }

    /**
     * Ensures that an activation group id can be passed in a remote
     * call (class may contain a remote reference to the activation
     * system implementation).
     */
    public ActivationGroupID returnGroupID() throws RemoteException {
        return ActivationGroup.currentGroupID();
    }
}
