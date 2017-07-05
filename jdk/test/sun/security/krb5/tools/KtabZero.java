/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

import sun.security.krb5.internal.ktab.KeyTab;
import sun.security.krb5.internal.ktab.KeyTabConstants;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Paths;

/*
 * @test
 * @bug 8014196
 * @summary ktab creates a file with zero kt_vno
 */
public class KtabZero {

    static final String NAME = "k.tab";

    public static void main(String[] args) throws Exception {

        // 0. Non-existing keytab
        Files.deleteIfExists(Paths.get(NAME));
        check(true);

        // 1. Create with KeyTab
        Files.deleteIfExists(Paths.get(NAME));
        KeyTab.getInstance(NAME).save();
        check(false);

        // 2. Create with the tool
        Files.deleteIfExists(Paths.get(NAME));
        try {
            Class ktab = Class.forName("sun.security.krb5.internal.tools.Ktab");
            ktab.getDeclaredMethod("main", String[].class).invoke(null,
                    (Object)(("-k " + NAME + " -a me@HERE pass").split(" ")));
        } catch (ClassNotFoundException cnfe) {
            // Only Windows has ktab tool
            System.out.println("No ktab tool here. Ignored.");
            return;
        }
        check(false);
    }

    // Checks existence as well as kt-vno
    static void check(boolean showBeMissing) throws Exception {
        KeyTab kt = KeyTab.getInstance(NAME);
        if (kt.isMissing() != showBeMissing) {
            throw new Exception("isMissing is not " + showBeMissing);
        }
        Field f = KeyTab.class.getDeclaredField("kt_vno");
        f.setAccessible(true);
        if (f.getInt(kt) != KeyTabConstants.KRB5_KT_VNO) {
            throw new Exception("kt_vno is " + f.getInt(kt));
        }
    }
}
