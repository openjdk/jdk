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

/*
 * @test
 * @summary Basic checks for SecurityException from body processors APIs
 * @run testng/othervm/java.security.policy=httpclient.policy FileProcessorPermissionTest
 */

import java.io.FilePermission;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.Permission;
import java.security.Permissions;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.security.ProtectionDomain;
import java.util.List;
import jdk.incubator.http.HttpRequest;
import jdk.incubator.http.HttpResponse;
import org.testng.annotations.Test;
import static java.nio.file.StandardOpenOption.*;
import static org.testng.Assert.*;

public class FileProcessorPermissionTest {

    static final String testSrc = System.getProperty("test.src", ".");
    static final Path fromFilePath = Paths.get(testSrc, "FileProcessorPermissionTest.java");
    static final Path asFilePath = Paths.get(testSrc, "asFile.txt");
    static final Path CWD = Paths.get(".");
    static final Class<SecurityException> SE = SecurityException.class;

    static AccessControlContext withPermissions(Permission... perms) {
        Permissions p = new Permissions();
        for (Permission perm : perms) {
            p.add(perm);
        }
        ProtectionDomain pd = new ProtectionDomain(null, p);
        return new AccessControlContext(new ProtectionDomain[]{ pd });
    }

    static AccessControlContext noPermissions() {
        return withPermissions(/*empty*/);
    }

    @Test
    public void test() throws Exception {
        List<PrivilegedExceptionAction<?>> list = List.of(
                () -> HttpRequest.BodyPublisher.fromFile(fromFilePath),

                () -> HttpResponse.BodyHandler.asFile(asFilePath),
                () -> HttpResponse.BodyHandler.asFile(asFilePath, CREATE),
                () -> HttpResponse.BodyHandler.asFile(asFilePath, CREATE, WRITE),
                () -> HttpResponse.BodyHandler.asFile(asFilePath, CREATE, WRITE, READ),
                () -> HttpResponse.BodyHandler.asFile(asFilePath, CREATE, WRITE, READ, DELETE_ON_CLOSE),

                () -> HttpResponse.BodyHandler.asFileDownload(CWD),
                () -> HttpResponse.BodyHandler.asFileDownload(CWD, CREATE),
                () -> HttpResponse.BodyHandler.asFileDownload(CWD, CREATE, WRITE),
                () -> HttpResponse.BodyHandler.asFileDownload(CWD, CREATE, WRITE, READ),
                () -> HttpResponse.BodyHandler.asFileDownload(CWD, CREATE, WRITE, READ, DELETE_ON_CLOSE),

                // TODO: what do these even mean by themselves, maybe ok means nothing?
                () -> HttpResponse.BodyHandler.asFile(asFilePath, DELETE_ON_CLOSE),
                () -> HttpResponse.BodyHandler.asFile(asFilePath, READ)
        );

        // sanity, just run http ( no security manager )
        System.setSecurityManager(null);
        try {
            for (PrivilegedExceptionAction pa : list) {
                AccessController.doPrivileged(pa);
            }
        } finally {
            System.setSecurityManager(new SecurityManager());
        }

        // Run with all permissions, i.e. no further restrictions than test's AllPermission
        for (PrivilegedExceptionAction pa : list) {
            try {
                assert System.getSecurityManager() != null;
                AccessController.doPrivileged(pa, null, new Permission[] { });
            } catch (PrivilegedActionException pae) {
                fail("UNEXPECTED Exception:" + pae);
                pae.printStackTrace();
            }
        }

        // Run with limited permissions, i.e. just what is required
        AccessControlContext minimalACC = withPermissions(
                new FilePermission(fromFilePath.toString() , "read"),
                new FilePermission(asFilePath.toString(), "read,write,delete"),
                new FilePermission(CWD.toString(), "read,write,delete")
        );
        for (PrivilegedExceptionAction pa : list) {
            try {
                assert System.getSecurityManager() != null;
                AccessController.doPrivileged(pa, minimalACC);
            } catch (PrivilegedActionException pae) {
                fail("UNEXPECTED Exception:" + pae);
                pae.printStackTrace();
            }
        }

        // Run with NO permissions, i.e. expect SecurityException
        for (PrivilegedExceptionAction pa : list) {
            try {
                assert System.getSecurityManager() != null;
                AccessController.doPrivileged(pa, noPermissions());
                fail("EXPECTED SecurityException");
            } catch (SecurityException expected) {
                System.out.println("Caught expected SE:" + expected);
            }
        }
    }
}
