/*
 * Copyright (c) 2003, Oracle and/or its affiliates. All rights reserved.
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

// RMI Activation Functional Test

import java.rmi.*;
import java.rmi.server.*;
import java.rmi.activation.*;
import java.util.*;

// CountServerImpl

public class CountServerImpl
    extends Activatable
    implements CountInterface {

    private static final String PROG_NAME       = "CountServerImpl";
    private static final String SERVER_OBJECT   = "CountServer";
    private static final String CLASS_NAME      = "activation.CountServerImpl";

    private static final String POLICY_FILE   = "policy_file";

    private static final String USER_DIR      =
                        System.getProperty("user.dir").replace('\\', '/');

    private static final String CODE_LOCATION = "file:"+USER_DIR+"/";

    private static final MarshalledObject DATA = null;
    private static ActivationDesc ACTIVATION_DESC = null;

    // Class variable
    private static int classCount = 0;

    // Instance variable
    private int instanceCount;
    private TestInterface ref;

    public CountServerImpl(ActivationID id, MarshalledObject data)
        throws RemoteException {
        super(id, 0);
        instanceCount = 0;
        classCount++;
        if (data != null) {
            try {
                ref = (TestInterface)data.get();
                ref.ping(SERVER_OBJECT);
            }
            catch (Exception e) {
                System.err.println("Exception: " + e);
            }
        }
    }

    public void ping() throws RemoteException {}

    public int getCount() throws RemoteException {
        return instanceCount;
    }

    public int incrementCount() throws RemoteException {
        return ++instanceCount;
    }

    public int decrementCount() throws RemoteException {
        return --instanceCount;
    }

    public int getClassCount() throws RemoteException {
        return classCount;
    }

    public String getProperty(String s) throws RemoteException {
        return System.getProperty(s);
    }

    public void exit() throws RemoteException {
        System.exit(0);
    }

    public boolean unexportObject(boolean force) {
        boolean succeeded = false;
        try {
            succeeded = Activatable.unexportObject(this, force);
        }
        catch (Exception e) {
            System.err.println("Exception: " + e);
            e.printStackTrace();
        }
        return succeeded;
    }

    public ActivationID getActivationID() throws RemoteException {
        return super.getID();
    }

    public void inactive()
        throws RemoteException, ActivationException, UnknownObjectException {

        //ShutDown s = new ShutDown(super.getID(),this,ShutDown.NORMAL_SHUTDOWN);
    }

    public void unregister()
        throws RemoteException, ActivationException, UnknownObjectException {
        unregister(super.getID());
    }

    public void register()
        throws RemoteException, ActivationException, UnknownObjectException {
        register(ACTIVATION_DESC);
    }

    public ActivationGroupID getCurrentGroupID() throws RemoteException {
        return ActivationGroup.currentGroupID();
    }

    private static void setup() {

        try {

          CountInterface rsi;   // Remote server interface

          System.setSecurityManager(new RMISecurityManager());

          rsi = (CountInterface)Activatable.register(ACTIVATION_DESC);
          System.out.println("Got stub for "+SERVER_OBJECT+" implementation");

          Naming.rebind(SERVER_OBJECT, rsi);
          System.out.println("Exported "+SERVER_OBJECT+" implementation");

        } catch (Exception e) {
            System.err.println("Exception: " + e);
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {

        try {
            Properties props = new Properties();
            props.setProperty("java.security.policy", POLICY_FILE);

            ActivationGroupDesc agd = new ActivationGroupDesc(props, null);

            ActivationGroupID agid = ActivationGroup.getSystem().registerGroup(agd);

            ACTIVATION_DESC = new ActivationDesc(agid,
                        CLASS_NAME, CODE_LOCATION, DATA, false);
        }
        catch (Exception e) {
            System.err.println("Exception: " + e);
            e.printStackTrace();
        }

        setup();
    }
}
