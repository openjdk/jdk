/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8345940
 * @modules java.base/sun.security.util
 *          java.base/sun.security.util.resources
 *          java.base/sun.security.tools.keytool
 *          java.base/sun.security.tools.keytool.resources
 *          jdk.jartool/sun.security.tools.jarsigner
 *          jdk.jartool/sun.security.tools.jarsigner.resources
 * @summary Check migration correctness of resources files
 * @run testng/othervm MigrationCheck
 */

import static org.testng.AssertJUnit.assertEquals;

import java.util.Collections;
import java.util.List;
import java.util.ListResourceBundle;
import org.testng.annotations.Test;

public class MigrationCheck {

    // Auth

    @Test
    public void testAuth() {
        checkResourcesMigration(new sun.security.util.AuthResources(),
                                new sun.security.util.resources.auth());
    }

    @Test
    public void testAuth_de() {
        checkResourcesMigration(new sun.security.util.AuthResources_de(),
                                new sun.security.util.resources.auth_de());
    }

    @Test
    public void testAuth_es() {
        checkResourcesMigration(new sun.security.util.AuthResources_es(),
                                new sun.security.util.resources.auth_es());
    }

    @Test
    public void testAuth_fr() {
        checkResourcesMigration(new sun.security.util.AuthResources_fr(),
                                new sun.security.util.resources.auth_fr());
    }

    @Test
    public void testAuth_it() {
        checkResourcesMigration(new sun.security.util.AuthResources_it(),
                                new sun.security.util.resources.auth_it());
    }

    @Test
    public void testAuth_ja() {
        checkResourcesMigration(new sun.security.util.AuthResources_ja(),
                                new sun.security.util.resources.auth_ja());
    }

    @Test
    public void testAuth_ko() {
        checkResourcesMigration(new sun.security.util.AuthResources_ko(),
                                new sun.security.util.resources.auth_ko());
    }

    @Test
    public void testAuth_sv() {
        checkResourcesMigration(new sun.security.util.AuthResources_sv(),
                                new sun.security.util.resources.auth_sv());
    }

    @Test
    public void testAuth_pt_BR() {
        checkResourcesMigration(new sun.security.util.AuthResources_pt_BR(),
                                new sun.security.util.resources.auth_pt_BR());
    }

    @Test
    public void testAuth_zh_CN() {
        checkResourcesMigration(new sun.security.util.AuthResources_zh_CN(),
                                new sun.security.util.resources.auth_zh_CN());
    }

    @Test
    public void testAuth_zh_TW() {
        checkResourcesMigration(new sun.security.util.AuthResources_zh_TW(),
                                new sun.security.util.resources.auth_zh_TW());
    }

    // Security

    @Test
    public void testSecurity() {
        checkResourcesMigration(new sun.security.util.Resources(),
                new sun.security.util.resources.security());
    }

    @Test
    public void testSecurity_de() {
        checkResourcesMigration(new sun.security.util.Resources_de(),
                new sun.security.util.resources.security_de());
    }

    @Test
    public void testSecurity_es() {
        checkResourcesMigration(new sun.security.util.Resources_es(),
                new sun.security.util.resources.security_es());
    }

    @Test
    public void testSecurity_fr() {
        checkResourcesMigration(new sun.security.util.Resources_fr(),
                new sun.security.util.resources.security_fr());
    }

    @Test
    public void testSecurity_it() {
        checkResourcesMigration(new sun.security.util.Resources_it(),
                new sun.security.util.resources.security_it());
    }

    @Test
    public void testSecurity_ja() {
        checkResourcesMigration(new sun.security.util.Resources_ja(),
                new sun.security.util.resources.security_ja());
    }

    @Test
    public void testSecurity_ko() {
        checkResourcesMigration(new sun.security.util.Resources_ko(),
                new sun.security.util.resources.security_ko());
    }

    @Test
    public void testSecurity_pt_BR() {
        checkResourcesMigration(new sun.security.util.Resources_pt_BR(),
                new sun.security.util.resources.security_pt_BR());
    }

    @Test
    public void testSecurity_sv() {
        checkResourcesMigration(new sun.security.util.Resources_sv(),
                new sun.security.util.resources.security_sv());
    }

    @Test
    public void testSecurity_zh_CN() {
        checkResourcesMigration(new sun.security.util.Resources_zh_CN(),
                new sun.security.util.resources.security_zh_CN());
    }

    @Test
    public void testSecurity_zh_TW() {
        checkResourcesMigration(new sun.security.util.Resources_zh_TW(),
                new sun.security.util.resources.security_zh_TW());
    }

    // Keytool

    @Test
    public void testKeytool() {
        checkResourcesMigration(new sun.security.tools.keytool.Resources(),
                new sun.security.tools.keytool.resources.keytool());
    }

    @Test
    public void testKeytool_de() {
        checkResourcesMigration(new sun.security.tools.keytool.Resources_de(),
                new sun.security.tools.keytool.resources.keytool_de());
    }

    @Test
    public void testKeytool_es() {
        checkResourcesMigration(new sun.security.tools.keytool.Resources_es(),
                new sun.security.tools.keytool.resources.keytool_es());
    }

    @Test
    public void testKeytool_fr() {
        checkResourcesMigration(new sun.security.tools.keytool.Resources_fr(),
                new sun.security.tools.keytool.resources.keytool_fr());
    }

    @Test
    public void testKeytool_it() {
        checkResourcesMigration(new sun.security.tools.keytool.Resources_it(),
                new sun.security.tools.keytool.resources.keytool_it());
    }

    @Test
    public void testKeytool_ja() {
        checkResourcesMigration(new sun.security.tools.keytool.Resources_ja(),
                new sun.security.tools.keytool.resources.keytool_ja());
    }

    @Test
    public void testKeytool_ko() {
        checkResourcesMigration(new sun.security.tools.keytool.Resources_ko(),
                new sun.security.tools.keytool.resources.keytool_ko());
    }

    @Test
    public void testKeytool_pt_BR() {
        checkResourcesMigration(new sun.security.tools.keytool.Resources_pt_BR(),
                new sun.security.tools.keytool.resources.keytool_pt_BR());
    }

    @Test
    public void testKeytool_sv() {
        checkResourcesMigration(new sun.security.tools.keytool.Resources_sv(),
                new sun.security.tools.keytool.resources.keytool_sv());
    }

    @Test
    public void testKeytool_zh_CN() {
        checkResourcesMigration(new sun.security.tools.keytool.Resources_zh_CN(),
                new sun.security.tools.keytool.resources.keytool_zh_CN());
    }

    @Test
    public void testKeytool_zh_TW() {
        checkResourcesMigration(new sun.security.tools.keytool.Resources_zh_TW(),
                new sun.security.tools.keytool.resources.keytool_zh_TW());
    }

    // Jarsigner

    @Test
    public void testJarsigner() {
        checkResourcesMigration(new sun.security.tools.jarsigner.Resources(),
                new sun.security.tools.jarsigner.resources.jarsigner());
    }

    @Test
    public void testJarsigner_de() {
        checkResourcesMigration(new sun.security.tools.jarsigner.Resources_de(),
                new sun.security.tools.jarsigner.resources.jarsigner_de());
    }

    @Test
    public void testJarsigner_ja() {
        checkResourcesMigration(new sun.security.tools.jarsigner.Resources_ja(),
                new sun.security.tools.jarsigner.resources.jarsigner_ja());
    }

    @Test
    public void testJarsigner_zh_CN() {
        checkResourcesMigration(new sun.security.tools.jarsigner.Resources_zh_CN(),
                new sun.security.tools.jarsigner.resources.jarsigner_zh_CN());
    }

    // ResourcesMgr

    @Test
    public void testResourcesMgrSecurityDefault() {
        checkResourcesMgrSecurity(new sun.security.util.Resources());
    }

    @Test
    public void testResourcesMgrAuthDefault() {
        checkResourcesMgrAuth(new sun.security.util.AuthResources());
    }

    // Helper methods

    private static void checkResourcesMigration(ListResourceBundle fromRes,
                                                ListResourceBundle toRes) {
        System.out.println(">>>> Checking from " + fromRes.getClass().getName()
                                   + " to " + toRes.getClass().getName());
        List<String> keys = Collections.list(fromRes.getKeys());

        for (String key : keys) {
            assertEquals(fromRes.getString(key), toRes.getString(key));
        }
    }

    private static void checkResourcesMgrSecurity(ListResourceBundle res) {
        System.out.println(">>>> Checking " + res.getClass().getName());
        List<String> keys = Collections.list(res.getKeys());

        for (String key : keys) {
            assertEquals(res.getString(key),
                         sun.security.util.ResourcesMgr.getString(key));
        }
    }

    private static void checkResourcesMgrAuth(ListResourceBundle res) {
        System.out.println(">>>> Checking " + res.getClass().getName());
        List<String> keys = Collections.list(res.getKeys());

        for (String key : keys) {
            assertEquals(res.getString(key),
                         sun.security.util.ResourcesMgr.getAuthResourceString(
                                 key));
        }
    }
}
