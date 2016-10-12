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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.rmi.MarshalledObject;
import java.rmi.NotBoundException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.AlreadyBoundException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Objects;
import java.security.Security;

import org.testng.Assert;
import org.testng.TestNG;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/*
 * @test
 * @library /java/rmi/testlibrary
 * @modules java.rmi/sun.rmi.registry
 *          java.rmi/sun.rmi.server
 *          java.rmi/sun.rmi.transport
 *          java.rmi/sun.rmi.transport.tcp
 * @build TestLibrary
 * @summary Test filters for the RMI Registry
 * @run testng/othervm RegistryFilterTest
 * @run testng/othervm
 *        -Dsun.rmi.registry.registryFilter=!java.lang.Long;!RegistryFilterTest$RejectableClass
 *        RegistryFilterTest
 * @run testng/othervm/policy=security.policy
 *        -Djava.security.properties=${test.src}/java.security-extra1
 *        RegistryFilterTest
 */
public class RegistryFilterTest {
    private static Registry impl;
    private static int port;
    private static Registry registry;

    static final int REGISTRY_MAX_ARRAY = 10000;

    static final String registryFilter =
            System.getProperty("sun.rmi.registry.registryFilter",
                    Security.getProperty("sun.rmi.registry.registryFilter"));

    @DataProvider(name = "bindAllowed")
    static Object[][] bindAllowedObjects() {
        Object[][] objects = {
        };
        return objects;
    }

    /**
     * Data RMI Regiry bind test.
     * - name
     * - Object
     * - true/false if object is blacklisted by a filter (implicit or explicit)
     * @return array of test data
     */
    @DataProvider(name = "bindData")
    static Object[][] bindObjects() {
        Object[][] data = {
                { "byte[max]", new XX(new byte[REGISTRY_MAX_ARRAY]), false },
                { "String", new XX("now is the time"), false},
                { "String[]", new XX(new String[3]), false},
                { "Long[4]", new XX(new Long[4]), registryFilter != null },
                { "rej-byte[toobig]", new XX(new byte[REGISTRY_MAX_ARRAY + 1]), true },
                { "rej-MarshalledObject", createMarshalledObject(), true },
                { "rej-RejectableClass", new RejectableClass(), registryFilter != null},
        };
        return data;
    }

    static XX createMarshalledObject() {
        try {
            return new XX(new MarshalledObject<>(null));
        } catch (IOException ioe) {
            return new XX(ioe);
        }
    }

    @BeforeSuite
    static void setupRegistry() {
        try {
            impl = TestLibrary.createRegistryOnEphemeralPort();
            port = TestLibrary.getRegistryPort(impl);
            registry = LocateRegistry.getRegistry("localhost", port);
        } catch (RemoteException ex) {
            Assert.fail("initialization of registry", ex);
        }

        System.out.printf("RMI Registry filter: %s%n", registryFilter);
    }


    /*
     * Test registry rejects an object with the max array size  + 1.
     */
    @Test(dataProvider="bindData")
    public void simpleBind(String name, Remote obj, boolean blacklisted) throws RemoteException, AlreadyBoundException, NotBoundException {
        try {
            registry.bind(name, obj);
            Assert.assertFalse(blacklisted, "Registry filter did not reject (but should have) ");
            registry.unbind(name);
        } catch (Exception rex) {
            Assert.assertTrue(blacklisted, "Registry filter should not have rejected");
        }
    }

    /*
    * Test registry rejects an object with a well known class
    * if blacklisted in the security properties.
    */
    @Test
    public void simpleRejectableClass() throws RemoteException, AlreadyBoundException, NotBoundException {
        RejectableClass r1 = null;
        try {
            String name = "reject1";
            r1 = new RejectableClass();
            registry.bind(name, r1);
            registry.unbind(name);
            Assert.assertNull(registryFilter, "Registry filter should not have rejected");
        } catch (Exception rex) {
            Assert.assertNotNull(registryFilter, "Registry filter should have rejected");
        }
    }

    /**
     * A simple Serializable Remote object that is passed by value.
     * It and its contents are checked by the Registry serial filter.
     */
    static class XX implements Serializable, Remote {
        private static final long serialVersionUID = 362498820763181265L;

        final Object obj;

        XX(Object obj) {
            this.obj = obj;
        }

        public String toString() {
            return super.toString() + "//" + Objects.toString(obj);
        }
    }
    /**
     * A simple Serializable Remote object that is passed by value.
     * It and its contents are checked by the Registry serial filter.
     */
    static class RejectableClass implements Serializable, Remote {
        private static final long serialVersionUID = 362498820763181264L;

        RejectableClass() {}
    }

}
