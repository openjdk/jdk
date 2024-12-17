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
import java.util.ResourceBundle;
import org.testng.annotations.Test;
import sun.security.util.AuthResources;
import sun.security.util.Resources;
import sun.security.util.ResourcesMgr;

public class MigrationCheck {

    @Test
    public void testSecurityDefault() throws Exception {
        checkResourcesMgrSecurity(new Resources());
    }

    @Test
    public void testAuthDefault() throws Exception {
        checkResourcesMgrAuth(new AuthResources());
    }

    private static void checkResourcesMgrSecurity(ResourceBundle res) {
        System.out.println(">>>> Checking " + res.getClass().getName());
        List<String> keys = Collections.list(res.getKeys());

        for (String key : keys) {
            assertEquals(res.getString(key),
                         ResourcesMgr.getString(key));
        }
    }

    private static void checkResourcesMgrAuth(ResourceBundle res) {
        System.out.println(">>>> Checking " + res.getClass().getName());
        List<String> keys = Collections.list(res.getKeys());

        for (String key : keys) {
            assertEquals(res.getString(key),
                         ResourcesMgr.getAuthResourceString(key));
        }
    }
}
