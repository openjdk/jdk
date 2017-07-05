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

import java.rmi.*;
import java.rmi.server.*;
import java.util.*;
import java.io.*;


/**
 * Server accepts agents and could test for validity.  Acts as both a home
 * server and a regular server.  The agent will jump to this host and
 * the server will create a thread and allow the agent to run inside of
 * it.  The agent just queries the system.properties for machine info.
 */
public class AgentServerImpl
    extends UnicastRemoteObject
    implements AgentServer
{

    /**
     * Constructor
     *
     * @exception RemoteException If a network problem occurs.
     */
    public AgentServerImpl() throws RemoteException {
        // Could use to set up state of server
    }

    /**
     * Instantiates Agent Server Implementation and sets security
     * manager
     */
    public static void main(String args[]) {

        // Set the security Manager
        //System.setSecurityManager(new MyRMISecurityManager());

        try {
            AgentServerImpl server = new AgentServerImpl();
            Naming.rebind("/AgentServer", server);
            System.out.println("Ready to receive agents.");
                System.err.println("DTI_DoneInitializing");
        } catch (Exception e) {
                System.err.println("DTI_Error");
            System.err.println("Did not establish server");
            e.printStackTrace();
        }
    }

    /**
     * Remote method called by Agent to have server accept it.
     */
    public synchronized void accept(Agent agent)
        throws RemoteException //, InvalidAgentException
    {
        Thread t;

        // Could check validity of agent here
        // checkValid(agent);

        // Create new thread to run agent
        t = new Thread(agent);

        System.out.println("Agent Accepted: " + t);

        // Start agent
        t.start();
    }

    /**
     * Remote method called by Agent to return to final server.
     */
    public synchronized void returnHome(Agent agent)
        throws RemoteException //, InvalidAgentException
    {
        Enumeration info = null;
        boolean bErrorsOccurred = false;

        // Could check validity of agent here
        // checkValid(agent);

        // Grab and print collected info from agent
        info = agent.getInfo().elements();
        System.out.println("Collected information:");
        while (info.hasMoreElements()) {
            System.out.println("     " + (String) info.nextElement());
        }

        System.out.println("\nErrors:");
        System.out.println(agent.getErrors());
        if(!(agent.getErrors()).equals(""))
                bErrorsOccurred = true;

        if(bErrorsOccurred)
    {
                System.err.println("DTI_Error");
                System.err.println("DTI_DoneExecuting");
        }
        else
    {
                System.err.println("DTI_DoneExecuting");
    }

        }
}
