/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package jaxp.library;

import java.security.Permission;
import java.security.Permissions;
import java.security.Policy;
import java.util.PropertyPermission;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;

/**
 * This is a base class that every test class must extend if it needs to be run
 * with security mode.
 */
public class JAXPBaseTest {
    /**
     * Backing up policy.
     */
    protected static Policy policy;

    /**
     * Backing up security manager.
     */
    private static SecurityManager sm;

    /*
     * Install a SecurityManager along with a base Policy to allow testNG to
     * run when there is a security manager.
     */
    @BeforeClass
    public void setUpClass() throws Exception {
        setPolicy(new TestPolicy());
        System.setSecurityManager(new SecurityManager());
    }

    /*
     * Install the original Policy and SecurityManager when there is a security
     * manager.
     */
    @AfterClass
    public void tearDownClass() throws Exception {
        System.setSecurityManager(sm);
        setPolicy(policy);
    }

    /*
     * Utility Method used to set the current Policy.
     */
    protected static void setPolicy(Policy p) {
        Policy.setPolicy(p);
    }

    /*
     * Add the specified permission(s) to the test policy.
     * Note there is no way to add permissions to current permissions. Reset
     * test policy by setting minimal permmisons in addition to specified
     * permissions when calling this method.
     */
    protected static void setPermissions(Permission... ps) {
        Policy.setPolicy(new TestPolicy(ps));
    }

    /*
     * Add the specified permission(s) to the test policy.
     * Note there is no way to add permissions to current permissions. Reset
     * test policy by setting minimal permmisons in addition to specified
     * permissions when calling this method.
     */
    protected static void setPermissions(Permissions ps) {
        Policy.setPolicy(new TestPolicy(ps));
    }

    /**
     * Backing up policy and security manager for restore when there is a
     * security manager.
     */
    public JAXPBaseTest() {
        policy = Policy.getPolicy();
        sm = System.getSecurityManager();
    }

    /**
     * Safety acquire a system property.
     * Note invocation of this method will restore permission to limited
     * minimal permission of tests. If there is additional permission set
     * already, you need restore permission by yourself.
     * @param propName System property name to be acquired.
     * @return property value
     */
    protected String getSystemProperty(final String propName) {
        setPermissions(new PropertyPermission(propName, "read"));
        try {
            return System.getProperty(propName);
        } finally {
            setPermissions();
        }
    }

    /**
     * Safety set a system property by given system value.
     *
     * @param propName System property name to be set.
     * @param propValue System property value to be set.
     */
    protected void setSystemProperty(final String propName, final String propValue) {
        setPermissions(new PropertyPermission(propName, "write"));
        try {
            if (propValue == null) {
                System.clearProperty(propName);
            } else {
                System.setProperty(propName, propValue);
            }
        } finally {
            setPermissions();
        }
    }
}
