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
 * @bug 8354305
 * @summary Ensure SHAKE message digest algorithms behave the same
 *      as correspondent XOF of the same output size
 * @library /test/lib
 * @modules java.base/sun.security.provider
 */

import jdk.test.lib.Asserts;
import jdk.test.lib.security.SeededSecureRandom;
import sun.security.provider.SHA3;

import java.security.MessageDigest;

public class SHAKEhash {
    public static void main(String[] args) throws Exception {
        var random = SeededSecureRandom.one();
        var s1 = new SHA3.SHAKE128();
        var m1 = MessageDigest.getInstance("SHAKE128-256"); // use standard name
        var s2 = new SHA3.SHAKE256();
        var m2 = MessageDigest.getInstance("SHAKE256"); // use alias
        for (var i = 0; i < 1_000_000; i++) {
            var msg = random.nBytes(random.nextInt(100));
            s1.update(msg);
            m1.update(msg);
            Asserts.assertEqualsByteArray(s1.squeeze(32), m1.digest());
            s2.update(msg);
            m2.update(msg);
            Asserts.assertEqualsByteArray(s2.squeeze(64), m2.digest());
            s1.reset();
            s2.reset();
        }
    }
}
