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

import java.lang.module.ModuleFinder;
import static jdk.test.lib.Asserts.*;

/*
 * @test
 * @bug 8308398
 * @library /test/lib
 * @summary Verify jdk.crypto.ec empty module
 * @run main ecModuleCheck
 */

/* This test verifies jdk.crypto.ec is in the image, but not resolvable.
 */
public class ecModuleCheck {
    public static void main(String[] args) throws Exception {
        // True if module is found in the image.
        assertTrue(ModuleFinder.ofSystem().find("jdk.crypto.ec").isPresent(),
            "jdk.crypto.ec was not found in image.");
        // Since the module empty, isPresent() should be false.
        assertFalse(ModuleLayer.boot().findModule("jdk.crypto.ec").
            isPresent(), "jdk.crypto.ec shouldn't be resolvable.");
    }
}
