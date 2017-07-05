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

import java.io.FilePermission;
import java.security.Permission;
import java.security.Permissions;
import java.security.Policy;
import static jaxp.library.JAXPBaseTest.setPolicy;
import org.testng.annotations.BeforeClass;

/**
 * This is a base class that every test class that need to access local XML
 * files must extend if it needs to be run with security mode.
 */
public class JAXPFileBaseTest extends JAXPBaseTest {
    /*
     * Install a SecurityManager along with a base Policy to allow testNG to
     * run when there is a security manager.
     */
    @BeforeClass
    @Override
    public void setUpClass() throws Exception {
        setPolicy(new FileTestPolicy());
        System.setSecurityManager(new SecurityManager());
    }

    /*
     * Add the specified permission(s) to the test policy.
     * Note there is no way to add permissions to current permissions. Reset
     * test policy by setting minimal permmisons in addition to specified
     * permissions when calling this method.
     */
    protected static void setPermissions(Permission... ps) {
        Policy.setPolicy(new FileTestPolicy(ps));
    }

    /*
     * Add the specified permission(s) to the test policy.
     * Note there is no way to add permissions to current permissions. Reset
     * test policy by setting minimal permmisons in addition to specified
     * permissions when calling this method.
     */
    protected static void setPermissions(Permissions ps) {
        Policy.setPolicy(new FileTestPolicy(ps));
    }
}

/**
 * This policy is only given to tests that need access local files. Additional
 * permissions for accessing local files have been granted by default.
 * @author HaiboYan
 */
class FileTestPolicy extends TestPolicy {
    /**
     * Constructor which sets the minimum permissions by default allowing testNG
     * to work with a SecurityManager.
     * @param ps permissions to be added.
     */
    public FileTestPolicy(Permissions ps) {
        super(ps);
    }

    /**
     * Constructor which sets the minimum permissions by default allowing testNG
     * to work with a SecurityManager.
     * @param ps permission array to be added.
     */
    public FileTestPolicy(Permission... ps) {
        super(ps);
    }

    /**
     * Defines the minimal permissions required by testNG when running these
     * tests
     */
    @Override
    protected void setMinimalPermissions() {
        super.setMinimalPermissions();
        permissions.add(new FilePermission(System.getProperty("user.dir") + "/-",
                "read, write"));
        permissions.add(new FilePermission(System.getProperty("test.src") + "/-",
                "read"));
    }
}
