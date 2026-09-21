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
import org.junit.jupiter.api.function.Executable;
import java.net.InetAddress;
import java.util.Arrays;

import static impl.FaultyResolverProviderGetImpl.EXCEPTION_MESSAGE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/*
 * @test
 * @summary Test that InetAddress fast-fails if custom provider fails to
 *  instantiate a resolver.
 * @library providers/faulty
 * @build faulty.provider/impl.FaultyResolverProviderGetImpl
 * @run junit/othervm ProviderGetExceptionTest
 */

public class ProviderGetExceptionTest {

    @Test
    public void getByNameExceptionTest() {
        String hostName = "test.host";
        System.out.println("Looking up address for the following host name:" + hostName);
        callInetAddressAndCheckException(() -> InetAddress.getByName(hostName));
    }

    @Test
    public void getByAddressExceptionTest() {
        byte[] address = new byte[]{1, 2, 3, 4};
        System.out.println("Looking up host name for the following address:" + Arrays.toString(address));
        callInetAddressAndCheckException(() -> InetAddress.getByAddress(address).getHostName());
    }

    private void callInetAddressAndCheckException(Executable apiCall) {
        IllegalArgumentException iae = assertThrows(IllegalArgumentException.class, apiCall);
        System.out.println("Got exception of expected type:" + iae);
        assertNull(iae.getCause(), "cause is not null");
        assertEquals(EXCEPTION_MESSAGE, iae.getMessage());
    }
}
