/*
 * Copyright (c) 2021, 2026, Oracle and/or its affiliates. All rights reserved.
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

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertThrows;
/*
 * @test
 * @summary checks that InetAddress forward lookup API throw UnknownHostException
 *   when resolver returns empty address stream.
 * @library providers/empty
 * @build empty.results.provider/impl.EmptyResultsProviderImpl
 * @run junit/othervm EmptyResultsStreamTest
 */
public class EmptyResultsStreamTest {

    @Test()
    public void getAllByNameTest() {
        assertThrows(UnknownHostException.class, () -> {
            System.err.println("getAllByName unexpectedly completed: " +
                    Arrays.deepToString(InetAddress.getAllByName("test1.org")));
        });
    }

    @Test()
    public void getByNameTest() {
        assertThrows(UnknownHostException.class, () -> {
            System.err.println("getByName unexpectedly completed: " +
                    InetAddress.getByName("test2.org"));
        });
    }
}
