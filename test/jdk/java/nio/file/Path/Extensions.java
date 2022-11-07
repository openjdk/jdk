/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.file.Path;

import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/*
 * @test
 * @bug 8057113
 * @summary Verify getExtension method
 * @run testng Extensions
 */
public class Extensions {
    /**
     * Returns path name string and expected extension pairs.
     *
     * @return {@code {{"pathname", "extension"},...}}
     */
    @DataProvider
    static Object[][] getProvider() {
        Object[][] pairs = {
            {"",               null},
            {".",              null},
            {"..",             ""},
            {"...",            ""},
            {"....",           ""},
            {".....",          ""},
            {"aa",             null},
            {"a.",             ""},
            {".a",             null},
            {"..a",            "a"},
            {"...a",           "a"},
            {"....a",          "a"},
            {".a.b",           "b"},
            {"...a.b",         "b"},
            {"...a.b.",        ""},
            {"..foo",          "foo"},
            {"foo.",           ""},
            {"test.",          ""},
            {"test..",         ""},
            {"test...",        ""},
            {"test.rb",        "rb"},
            {"a/b/d/test.rb" , "rb"},
            {".a/b/d/test.rb", "rb"},
            {"test",           null},
            {".profile",       null},
            {".profile.sh",    "sh"},
            {"foo.tar.gz",     "gz"},
            {"foo.bar.",       ""},
            {"archive.zip",    "zip"},
            {"compress.gzip",  "gzip"},
            {"waitwhat.&$!#%", "&$!#%"},
            {"6.283185307",    "283185307"}
        };
        return pairs;
    }

    @Test(dataProvider = "getProvider")
    public static void get(String pathname, String extension) {
        Assert.assertEquals(Path.of(pathname).getExtension(), extension);
    }
}
