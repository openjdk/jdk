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

import java.io.IOException;
import java.net.URL;
import java.security.CodeSource;
import java.security.Permission;
import java.security.Policy;
import java.security.ProtectionDomain;
import java.security.SecureClassLoader;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.PropertyPermission;

/*
 * @test
 * @bug 6826789
 * @summary Make sure equivalent ProtectionDomains are granted the same
 *          permissions when the CodeSource URLs are different but resolve
 *          to the same ip address after name service resolution.
 * @run main/othervm/java.security.policy=DefineClass.policy DefineClass
 */

public class DefineClass {

    // permissions that are expected to be granted by the policy file
    private final static Permission[] GRANTED_PERMS = new Permission[] {
        new PropertyPermission("user.home", "read"),
        new PropertyPermission("user.name", "read")
    };

    // Base64 encoded bytes of a simple class: "public class Foo {}"
    private final static String FOO_CLASS =
        "yv66vgAAADQADQoAAwAKBwALBwAMAQAGPGluaXQ+AQADKClWAQAEQ29kZQEA" +
        "D0xpbmVOdW1iZXJUYWJsZQEAClNvdXJjZUZpbGUBAAhGb28uamF2YQwABAAF" +
        "AQADRm9vAQAQamF2YS9sYW5nL09iamVjdAAhAAIAAwAAAAAAAQABAAQABQAB" +
        "AAYAAAAdAAEAAQAAAAUqtwABsQAAAAEABwAAAAYAAQAAAAEAAQAIAAAAAgAJ";

    // Base64 encoded bytes of a simple class: "public class Bar {}"
    private final static String BAR_CLASS =
        "yv66vgAAADQADQoAAwAKBwALBwAMAQAGPGluaXQ+AQADKClWAQAEQ29kZQEA" +
        "D0xpbmVOdW1iZXJUYWJsZQEAClNvdXJjZUZpbGUBAAhCYXIuamF2YQwABAAF" +
        "AQADQmFyAQAQamF2YS9sYW5nL09iamVjdAAhAAIAAwAAAAAAAQABAAQABQAB" +
        "AAYAAAAdAAEAAQAAAAUqtwABsQAAAAEABwAAAAYAAQAAAAEAAQAIAAAAAgAJ";

    public static void main(String[] args) throws Exception {

        MySecureClassLoader scl = new MySecureClassLoader();
        Policy p = Policy.getPolicy();
        ArrayList<Permission> perms1 = getPermissions(scl, p,
                                                      "http://localhost/",
                                                      "Foo", FOO_CLASS);
        checkPerms(perms1, GRANTED_PERMS);
        ArrayList<Permission> perms2 = getPermissions(scl, p,
                                                      "http://127.0.0.1/",
                                                      "Bar", BAR_CLASS);
        checkPerms(perms2, GRANTED_PERMS);
        assert(perms1.equals(perms2));
    }

    // returns the permissions granted to the codebase URL
    private static ArrayList<Permission> getPermissions(MySecureClassLoader scl,
                                                        Policy p, String url,
                                                        String className,
                                                        String classBytes)
                                                        throws IOException {
        CodeSource cs = new CodeSource(new URL(url), (Certificate[])null);
        Base64.Decoder bd = Base64.getDecoder();
        byte[] bytes = bd.decode(classBytes);
        Class<?> c = scl.defineMyClass(className, bytes, cs);
        ProtectionDomain pd = c.getProtectionDomain();
        return Collections.list(p.getPermissions(pd).elements());
    }

    private static void checkPerms(List<Permission> perms,
                                   Permission... grantedPerms)
        throws Exception
    {
        if (!perms.containsAll(Arrays.asList(grantedPerms))) {
            throw new Exception("Granted permissions not correct");
        }
    }

    // A SecureClassLoader that allows the test to define its own classes
    private static class MySecureClassLoader extends SecureClassLoader {
        Class<?> defineMyClass(String name, byte[] b, CodeSource cs) {
            return super.defineClass(name, b, 0, b.length, cs);
        }
    }
}
