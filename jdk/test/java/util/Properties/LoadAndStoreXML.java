/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8000354
 * @summary Basic test of storeToXML and loadToXML
 */

import java.io.*;
import java.util.*;
import java.security.*;

public class LoadAndStoreXML {

    /**
     * Simple policy implementation that grants a set of permissions to
     * all code sources and protection domains.
     */
    static class SimplePolicy extends Policy {
        private final Permissions perms;

        public SimplePolicy(Permission...permissions) {
            perms = new Permissions();
            for (Permission permission : permissions)
                perms.add(permission);
        }

        @Override
        public PermissionCollection getPermissions(CodeSource cs) {
            return perms;
        }

        @Override
        public PermissionCollection getPermissions(ProtectionDomain pd) {
            return perms;
        }

        @Override
        public boolean implies(ProtectionDomain pd, Permission p) {
            return perms.implies(p);
        }
    }

    /**
     * Sanity test that properties saved with Properties#storeToXML can be
     * read with Properties#loadFromXML.
     */
    static void test() throws IOException {
        Properties props = new Properties();
        props.put("k1", "foo");
        props.put("k2", "bar");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        props.storeToXML(out, "no comment");

        Properties p = new Properties();
        ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
        p.loadFromXML(in);

        if (!p.equals(props)) {
            System.err.println("stored: " + props);
            System.err.println("loaded: " + p);
            throw new RuntimeException("Test failed");
        }
    }

    public static void main(String[] args) throws IOException {

        // run test without security manager
        test();

        // re-run test with security manager
        Policy orig = Policy.getPolicy();
        Policy p = new SimplePolicy(new RuntimePermission("setSecurityManager"),
                                    new PropertyPermission("line.separator", "read"));
        Policy.setPolicy(p);
        System.setSecurityManager(new SecurityManager());
        try {
            test();
        } finally {
            // turn off security manager and restore policy
            System.setSecurityManager(null);
            Policy.setPolicy(orig);
        }

    }
}
