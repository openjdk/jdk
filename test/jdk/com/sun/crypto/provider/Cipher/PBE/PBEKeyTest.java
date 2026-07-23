/*
 * Copyright (c) 1998, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8348732
 * @summary test PBEKey
 * @author Jan Luehe
 */
import java.security.spec.*;
import javax.crypto.*;
import javax.crypto.spec.*;

public class PBEKeyTest {

    public static void main(String[] args) throws Exception {

        SecretKeyFactory fac = SecretKeyFactory.getInstance("PBEWithMD5AndDES");

        // Valid password
        char[] pass = new char[] { 'p', 'a', 's', 's', 'w', 'o', 'r', 'd' };
        testPassword(pass, fac, "ASCII password", true);

        pass = new char[] { 'p', 'a', 's', 's', 'w', 'o', 'r', '\u0019' };
        testPassword(pass, fac, "non-visible characters", true);

        pass = new char[] { 'p', 'a', 's', 's', 'w', 'o', 'r', (char)0xff };
        testPassword(pass, fac, "non-ASCII characters", false);
    }

    private static void testPassword(char[] pass, SecretKeyFactory fac, String desc,
                                     boolean expectPass) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(pass);
        SecretKey skey = fac.generateSecret(spec);
        KeySpec spec1 = fac.getKeySpec(skey, PBEKeySpec.class);
        SecretKey skey1 = fac.generateSecret(spec1);
        if (expectPass && !skey.equals(skey1)) {
            throw new Exception(desc + ": Equal keys not equal");
        } else if (!expectPass && skey.equals(skey1)) {
            throw new Exception(desc + ": Keys should not be the same but are!");
        }
        System.out.println(new String(((PBEKeySpec)spec1).getPassword()));
    }
}
