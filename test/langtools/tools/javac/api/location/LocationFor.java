/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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
 * @bug 8351556
 * @summary Test Location.locationFor
 * @modules java.compiler jdk.compiler
 * @run main LocationFor
 */

import javax.tools.JavaFileManager.Location;
import javax.tools.StandardLocation;

public class LocationFor {

    public static void main(String... args) throws Exception {
        // Non-output, non-module location.
        {
            Location loc = StandardLocation.locationFor("MY_LOCATION");
            assertFalse(loc.isOutputLocation());
            assertFalse(loc.isModuleOrientedLocation());
        }

        // Output, non-module location.
        {
            Location loc = StandardLocation.locationFor("MY_LOCATION_OUTPUT");
            assertTrue(loc.isOutputLocation());
            assertFalse(loc.isModuleOrientedLocation());
        }

        // Non-output, module location.
        {
            Location loc = StandardLocation.locationFor("MODULE");
            assertFalse(loc.isOutputLocation());
            assertTrue(loc.isModuleOrientedLocation());
        }

        // Output, module location.
        if (false) { // JDK-8351561
            Location loc = StandardLocation.locationFor("MODULE_OUTPUT");
            assertTrue(loc.isOutputLocation());
            assertTrue(loc.isModuleOrientedLocation());
        }

        // Test standard locations identity.
        for (Location loc : StandardLocation.values()) {
            Location cached = StandardLocation.locationFor(loc.getName());
            assertTrue(loc == cached);
        }
    }

    private static void assertFalse(boolean cond) {
        if (cond) {
            throw new AssertionError("Assertion failed");
        }
    }

    private static void assertTrue(boolean cond) {
        if (!cond) {
            throw new AssertionError("Assertion failed");
        }
    }

}
