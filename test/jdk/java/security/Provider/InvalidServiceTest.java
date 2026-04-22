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
 * @bug 8344361
 * @summary Restore null return for invalid services
 */

import java.security.Provider;

public class InvalidServiceTest {

    public static void main(String[] args) throws Exception {
        Provider p1 = new LProvider("LegacyFormat");
        // this returns a service with null class name. Helps exercise the code path
        Provider.Service s1 = p1.getService("MessageDigest", "SHA-1");
        if (s1 != null)
            throw new RuntimeException("expecting null service");
    }

    private static class LProvider extends Provider {
        LProvider(String name) {
            super(name, "1.0", null);
            put("Signature.MD5withRSA", "com.foo.Sig");
            put("MessageDigest.SHA-1 ImplementedIn", "Software");
        }
    }
}
