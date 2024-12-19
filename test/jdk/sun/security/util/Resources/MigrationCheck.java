/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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
 *          jdk.jartool/sun.security.tools.jarsigner
 * @summary Check migration correctness of resources files
 * @run testng/othervm MigrationCheck
 */

import static org.testng.AssertJUnit.assertEquals;

import java.util.Collections;
import java.util.List;
import java.util.ListResourceBundle;
import org.testng.annotations.Test;

public class MigrationCheck {

    @Test
    public void testAuth() throws Exception {
        checkResourcesMigration(new sun.security.util.AuthResources(),
                                new sun.security.util.resources.auth());
    }

    @Test
    public void testAuth_de() throws Exception {
        checkResourcesMigration(new sun.security.util.AuthResources_de(),
                                new sun.security.util.resources.auth_de());
    }

    @Test
    public void testAuth_es() throws Exception {
        checkResourcesMigration(new sun.security.util.AuthResources_es(),
                                new sun.security.util.resources.auth_es());
    }

    @Test
    public void testAuth_fr() throws Exception {
        checkResourcesMigration(new sun.security.util.AuthResources_fr(),
                                new sun.security.util.resources.auth_fr());
    }

    @Test
    public void testAuth_it() throws Exception {
        checkResourcesMigration(new sun.security.util.AuthResources_it(),
                                new sun.security.util.resources.auth_it());
    }

    @Test
    public void testAuth_ja() throws Exception {
        checkResourcesMigration(new sun.security.util.AuthResources_ja(),
                                new sun.security.util.resources.auth_ja());
    }

    @Test
    public void testAuth_ko() throws Exception {
        checkResourcesMigration(new sun.security.util.AuthResources_ko(),
                                new sun.security.util.resources.auth_ko());
    }

    @Test
    public void testAuth_sv() throws Exception {
        checkResourcesMigration(new sun.security.util.AuthResources_sv(),
                                new sun.security.util.resources.auth_sv());
    }

    @Test
    public void testAuth_pt_BR() throws Exception {
        checkResourcesMigration(new sun.security.util.AuthResources_pt_BR(),
                                new sun.security.util.resources.auth_pt_BR());
    }

    @Test
    public void testAuth_zh_CN() throws Exception {
        checkResourcesMigration(new sun.security.util.AuthResources_zh_CN(),
                                new sun.security.util.resources.auth_zh_CN());
    }

    @Test
    public void testAuth_zh_TW() throws Exception {
        checkResourcesMigration(new sun.security.util.AuthResources_zh_TW(),
                                new sun.security.util.resources.auth_zh_TW());
    }

    @Test
    public void testResourcesMgrSecurityDefault() throws Exception {
        checkResourcesMgrSecurity(new sun.security.util.Resources());
    }

    @Test
    public void testResourcesMgrAuthDefault() throws Exception {
        checkResourcesMgrAuth(new sun.security.util.AuthResources());
    }

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
