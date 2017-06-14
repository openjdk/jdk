/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

import org.omg.CORBA.ORB;

/*
 * @test
 * @bug 8049375
 * @summary Extend how the org.omg.CORBA.ORB handles the search for orb.properties
 * @library /lib/testlibrary
 * @build jdk.testlibrary.*
 * @modules java.corba
 * @compile OrbPropertiesTest.java TestOrbImpl.java TestSingletonOrbImpl.java
 * @run main/othervm
 *    -Djava.naming.provider.url=iiop://localhost:1050
 *    -Djava.naming.factory.initial=com.sun.jndi.cosnaming.CNCtxFactory
 *    OrbPropertiesTest -port 1049
 * @run main/othervm/secure=java.lang.SecurityManager/policy=jtreg.test.policy
 *    -Djava.naming.provider.url=iiop://localhost:3050
 *    -Djava.naming.factory.initial=com.sun.jndi.cosnaming.CNCtxFactory
 *    OrbPropertiesTest -port 3049
 */
public class OrbPropertiesTest {

    public static void main(String[] args) throws Exception {
        updateOrbPropertiesFile();
        // create and initialize the ORB
        ORB orb = ORB.init(args, null);
        if (!(orb instanceof TestOrbImpl)) {
            throw new RuntimeException("org.omg.CORBA.ORBClass property not set as expected");
        }
        ORB singletonOrb = ORB.init();
        System.out.println("singletonOrb class == " + singletonOrb.getClass().getName());
        if (!(singletonOrb instanceof TestSingletonOrbImpl)) {
            throw new RuntimeException("org.omg.CORBA.ORBSingletonClass property not set as expected");
        }

    }

    private static void updateOrbPropertiesFile() throws Exception {
        String orbPropertiesFile = System.getProperty("java.home", ".") + "/conf/orb.properties";
        String orbClassMapping = "org.omg.CORBA.ORBClass TestOrbImpl";
        String orbSingletonClassMapping = "org.omg.CORBA.ORBSingletonClass TestSingletonOrbImpl";
        String orbPropertiesMappings = orbClassMapping + "\n" + orbSingletonClassMapping +"\n";
        try (PrintWriter hfPWriter = new PrintWriter(new BufferedWriter(
                new FileWriter(orbPropertiesFile, false)))) {
            hfPWriter.println(orbPropertiesMappings);
        } catch (IOException ioEx) {
            ioEx.printStackTrace();
            throw ioEx;
        }
    }
}
