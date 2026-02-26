/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.test.lib.Asserts.assertFalse;
import static jdk.test.lib.Asserts.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import javax.net.ssl.SSLEngine;
import jdk.test.lib.security.SecurityUtils;

/*
 * @test
 * @bug 8370885
 * @summary Default namedGroups values are not being filtered against
 *          algorithm constraints
 * @library /javax/net/ssl/templates
 *          /test/lib
 * @run main/othervm DefaultNamedGroups
 */

public class DefaultNamedGroups extends SSLEngineTemplate {

    protected static final String DISABLED_NG = "secp256r1";
    protected static final List<String> REFERENCE_NG = Stream.of(
                    "X25519MLKEM768",
                    "x25519",
                    "secp256r1",
                    "secp384r1",
                    "secp521r1",
                    "x448",
                    "ffdhe2048",
                    "ffdhe3072",
                    "ffdhe4096",
                    "ffdhe6144",
                    "ffdhe8192")
            .sorted()
            .toList();

    protected DefaultNamedGroups() throws Exception {
        super();
    }

    public static void main(String[] args) throws Exception {
        SecurityUtils.addToDisabledTlsAlgs(DISABLED_NG);
        var test = new DefaultNamedGroups();

        for (SSLEngine engine :
                new SSLEngine[]{test.serverEngine, test.clientEngine}) {
            checkEngineDefaultNG(engine);
        }
    }

    private static void checkEngineDefaultNG(SSLEngine engine) {
        var defaultConfigNG = new ArrayList<>(List.of(
                engine.getSSLParameters().getNamedGroups()));

        assertFalse(defaultConfigNG.contains(DISABLED_NG));
        defaultConfigNG.add(DISABLED_NG);
        assertTrue(REFERENCE_NG.equals(
                        defaultConfigNG.stream().sorted().toList()),
                "Named groups returned by engine: " + defaultConfigNG);
    }
}
