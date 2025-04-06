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
 * @bug 8340404
 * @summary Check that a CharsetProvider SPI can be deployed as a module
 * @build provider/*
 * @run main/othervm CharsetProviderAsModuleTest
 */

import java.nio.charset.Charset;

public class CharsetProviderAsModuleTest {

    // Basic test ensures that our BAZ charset is loaded via the BazProvider
    public static void main(String[] args) {
        var cs = Charset.availableCharsets();
        Charset bazCs;
        // check provider is providing BAZ via charsets()
        if (!cs.containsKey("BAZ")) {
            throw new RuntimeException("SPI BazProvider did not provide BAZ Charset");
        } else {
            bazCs = cs.get("BAZ");
            // check provider is in a named module
            if (!bazCs.getClass().getModule().isNamed()) {
                throw new RuntimeException("BazProvider is not a named module");
            }
            var aliases = bazCs.aliases();
            // check BAZ cs aliases were loaded correctly
            if (!aliases.contains("BAZ-1") || !aliases.contains("BAZ-2")) {
                throw new RuntimeException("BAZ Charset did not provide correct aliases");
            }
            // check provider implements charsetForName()
            if (!bazCs.equals(Charset.forName("BAZ"))) {
                throw new RuntimeException("SPI BazProvider provides bad charsetForName()");
            }
        }
    }
}
