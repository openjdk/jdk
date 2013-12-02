/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 8028215
 * @summary SetDefaultORBTest setting ORB impl via properties test
 * @run main/othervm SetDefaultORBTest
 *
 */

import java.util.Properties;

import org.omg.CORBA.ORB;


public class SetDefaultORBTest {

    public static void main(String[] args) {
        Properties systemProperties = System.getProperties();
        systemProperties.setProperty("org.omg.CORBA.ORBSingletonClass",
                "com.sun.corba.se.impl.orb.ORBSingleton");
        System.setSecurityManager(new SecurityManager());
        Properties props = new Properties();
        props.put("org.omg.CORBA.ORBClass", "com.sun.corba.se.impl.orb.ORBImpl");
        ORB orb = ORB.init(args, props);
        Class<?> orbClass = orb.getClass();
        if (orbClass.getName().equals("com.sun.corba.se.impl.orb.ORBImpl")) {
            System.out.println("orbClass is com.sun.corba.se.impl.orb.ORBimpl  as expected");
        } else {
            throw new RuntimeException("com.sun.corba.se.impl.orb.ORBimpl class expected for ORBImpl");
        }
        ORB singletonORB = ORB.init();
        Class<?> singletoneOrbClass = singletonORB.getClass();
        if (singletoneOrbClass.getName().equals("com.sun.corba.se.impl.orb.ORBSingleton")) {
            System.out.println("singeletonOrbClass is com.sun.corba.se.impl.orb.ORBSingleton  as expected");
        } else {
            throw new RuntimeException("com.sun.corba.se.impl.orb.ORBSingleton class expected for ORBSingleton");
        }
    }
}
