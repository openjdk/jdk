/*
 * Copyright (c) 2020, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Test UnixDomainSocketAddress constructor
 * @library /test/lib
 * @run junit/othervm ${test.main.class}
 */


import static java.lang.System.err;

import java.net.UnixDomainSocketAddress;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class LengthTest {
    private static final int namelen = 100;    // length close to max

    public static List<String> strings() {
        assert namelen > 0;
        return List.of(
                "",
                "x".repeat(100),
                "x".repeat(namelen),
                "x".repeat(namelen - 1)
        );
    }

    @ParameterizedTest
    @MethodSource("strings")
    public void expectPass(String s) {
        var addr = UnixDomainSocketAddress.of(s);
        assertEquals(s, addr.getPath().toString(), "getPathName.equals(s)");
        var p = Path.of(s);
        addr = UnixDomainSocketAddress.of(p);
        assertEquals(p, addr.getPath(), "getPath.equals(p)");
    }

    @Test
    public void expectNPE() {
        String s = null;
        NullPointerException npe =
                assertThrows(NullPointerException.class, () -> UnixDomainSocketAddress.of(s));
        err.println("\tCaugth expected NPE for UnixDomainSocketAddress.of(s): " + npe);
        Path p = null;
        npe = assertThrows(NullPointerException.class, () -> UnixDomainSocketAddress.of(p));
        err.println("\tCaugth expected NPE for UnixDomainSocketAddress.of(p): " + npe);
    }
}
