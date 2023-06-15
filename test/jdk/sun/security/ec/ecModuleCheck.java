/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Optional;

/*
 * @test
 * @bug 8308398
 * @summary Verify jdk.crypto.ec dummy module exists
 * @modules jdk.crypto.ec
 * @run main ecModuleCheck
 */

/* This test verifies that with jdk.crypto.ec loaded that the EC modules is
 * available. The KeyPairGenerator is just to verify SunEC is working.  Other
 * tests access internal sun.security.ec APIs from java.base (see TestEC.java)
 */
public class ecModuleCheck {
    public static void main(String[] args) throws Exception {
        if (!ModuleLayer.boot().findModule("jdk.crypto.ec").isPresent()) {
            throw new AssertionError("jdk.crypto.ec module does not exist");
        }
        System.out.println("jdk.crypto.ec module exists");
    }
}
