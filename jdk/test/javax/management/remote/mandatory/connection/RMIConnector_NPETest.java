/*
 * Copyright (c) 2010, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @summary NPE IN RMIConnector.connect
 * @bug 6984520
 * @run clean RMIConnector_NPETest
 * @run build RMIConnector_NPETest
 * @run main RMIConnector_NPETest
 */

import java.io.*;
import java.lang.management.*;
import java.rmi.registry.*;
import javax.management.*;
import javax.management.remote.*;
import javax.management.remote.rmi.*;

public class RMIConnector_NPETest  {

    public static void main(String argv[]) throws Exception {
        boolean testFailed = false;
        String rmidCmd = System.getProperty("java.home") + File.separator +
            "bin" + File.separator + "rmid -port 3333";
        String stopRmidCmd = System.getProperty("java.home") + File.separator +
                "bin" + File.separator + "rmid -stop -port 3333";
    try {
        //start an rmid daemon and give it some time
        System.out.println("Starting rmid");
        Runtime.getRuntime().exec(rmidCmd);
        Thread.sleep(5000);

        MBeanServer mbs = MBeanServerFactory.createMBeanServer();
        RMIJRMPServerImpl rmiserver = new RMIJRMPServerImpl(3333, null, null, null);
        rmiserver.setMBeanServer(mbs);
        RMIConnector agent = new RMIConnector(rmiserver, null);
        agent.connect();
    } catch(NullPointerException npe) {
        npe.printStackTrace();
        testFailed = true;
    } catch (Exception e) {
        // OK
    } finally {
        System.out.println("Stopping rmid");
        Runtime.getRuntime().exec(stopRmidCmd);
        }

    if(testFailed)
        throw new Exception("Test failed");

    }
}
