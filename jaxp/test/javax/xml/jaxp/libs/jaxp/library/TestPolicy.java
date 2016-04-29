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

import java.security.AllPermission;
import java.security.CodeSource;
import java.security.Permission;
import java.security.PermissionCollection;
import java.security.Permissions;
import java.security.Policy;
import java.security.ProtectionDomain;
import java.security.SecurityPermission;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.PropertyPermission;
import java.util.StringJoiner;

/*
 * Simple Policy class that supports the required Permissions to validate the
 * JAXP concrete classes.
 * Note: permission can only be added. You may want to create a new TestPolicy
 *       instance if you need remove permissions.
 */
public class TestPolicy extends Policy {
    protected final PermissionCollection permissions = new Permissions();

    /**
     * Constructor which sets the minimum permissions by default allowing testNG
     * to work with a SecurityManager.
     */
    public TestPolicy() {
        setMinimalPermissions();
    }

    /**
     * Construct an instance with the minimal permissions required by the test
     * environment and additional permission(s) as specified.
     * @param ps permissions to be added.
     */
    public TestPolicy(Permissions ps) {
        setMinimalPermissions();
        TestPolicy.this.addPermissions(ps);
    }

    /**
     * Construct an instance with the minimal permissions required by the test
     * environment and additional permission(s) as specified.
     * @param ps permission array to be added.
     */
    public TestPolicy(Permission... ps) {
        setMinimalPermissions();
        addPermissions(ps);
    }

    /**
     * Defines the minimal permissions required by testNG when running these
     * tests
     */
    protected void setMinimalPermissions() {
        permissions.add(new SecurityPermission("getPolicy"));
        permissions.add(new SecurityPermission("setPolicy"));
        permissions.add(new RuntimePermission("getClassLoader"));
        permissions.add(new RuntimePermission("setSecurityManager"));
        permissions.add(new RuntimePermission("createSecurityManager"));
        permissions.add(new PropertyPermission("testng.show.stack.frames",
                "read"));
        permissions.add(new PropertyPermission("user.dir", "read"));
        permissions.add(new PropertyPermission("test.src", "read"));
        permissions.add(new PropertyPermission("file.separator", "read"));
        permissions.add(new PropertyPermission("line.separator", "read"));
        permissions.add(new PropertyPermission("fileStringBuffer", "read"));
        permissions.add(new PropertyPermission("dataproviderthreadcount", "read"));
        permissions.add(new RuntimePermission("charsetProvider"));
    }

    /*
     * Add permissions for your tests.
     * @param permissions to be added.
     */
    private void addPermissions(Permissions ps) {
        Collections.list(ps.elements()).forEach(p -> permissions.add(p));
    }


    /*
     * Add permissions for your tests.
     * @param permissions to be added.
     */
    private void addPermissions(Permission[] ps) {
        Arrays.stream(ps).forEach(p -> permissions.add(p));
    }

    /**
     * Set all permissions. Caution: this should not called carefully unless
     * it's really needed.
     */
    private void setAllPermissions() {
        permissions.add(new AllPermission());
    }

    /*
     * Overloaded methods from the Policy class.
     */
    @Override
    public String toString() {
        StringJoiner sj = new StringJoiner("\n", "policy: ", "");
        Enumeration<Permission> perms = permissions.elements();
        while (perms.hasMoreElements()) {
            sj.add(perms.nextElement().toString());
        }
        return sj.toString();

    }

    @Override
    public PermissionCollection getPermissions(ProtectionDomain domain) {
        return permissions;
    }

    @Override
    public PermissionCollection getPermissions(CodeSource codesource) {
        return permissions;
    }

    @Override
    public boolean implies(ProtectionDomain domain, Permission perm) {
        return permissions.implies(perm);
    }
}
