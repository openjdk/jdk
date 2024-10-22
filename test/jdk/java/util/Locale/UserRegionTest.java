/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8342582
 * @summary Test if "user.region" system property successfully overrides
 *          other locale related system properties at startup
 * @modules jdk.localedata
 * @run junit/othervm
 *      -Duser.region=DE
 *      -Duser.language=en
 *      -Duser.script=Latn
 *      -Duser.country=US
 *      -Duser.variant=FOO UserRegionTest
 * @run junit/othervm
 *      -Duser.region=DE_POSIX
 *      -Duser.language=en
 *      -Duser.script=Latn
 *      -Duser.country=US
 *      -Duser.variant=FOO UserRegionTest
 * @run junit/othervm
 *      -Duser.region=_POSIX
 *      -Duser.language=en
 *      -Duser.script=Latn
 *      -Duser.country=US
 *      -Duser.variant=FOO UserRegionTest
 */

import java.util.Locale;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class UserRegionTest {
    @Test
    public void testDefaultLocale() {
        var region = System.getProperty("user.region").split("_");
        var expected = Locale.of(System.getProperty("user.language"),
                region[0], region.length > 1 ? region[1] : "");
        assertEquals(expected, Locale.getDefault());
        assertEquals(expected, Locale.getDefault(Locale.Category.FORMAT));
        assertEquals(expected, Locale.getDefault(Locale.Category.DISPLAY));
    }

    @Test
    public void testNumberFormat() {
        if (System.getProperty("user.region").startsWith("DE")) {
            assertEquals("0,50000", String.format("%.5f", 0.5f));
        } else {
            assertEquals("0.50000", String.format("%.5f", 0.5f));
        }
    }
}
