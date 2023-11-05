/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

import impl.DelegatingProviderImpl;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;

import static impl.DelegatingProviderImpl.changeReverseLookupAddress;
import static impl.DelegatingProviderImpl.lastReverseLookupThrowable;

/*
 * @test
 * @summary checks delegation of illegal reverse lookup request to the built-in
 *  InetAddressResolver.
 * @library providers/delegating
 * @build delegating.provider/impl.DelegatingProviderImpl
 * @run testng/othervm ReverseLookupDelegationTest
 */
public class ReverseLookupDelegationTest {

    @Test
    public void delegateHostNameLookupWithWrongByteArray() throws UnknownHostException {
        // The underlying resolver implementation will ignore the supplied
        // byte array and will replace it with byte array of incorrect size.
        changeReverseLookupAddress = true;
        String canonicalHostName = InetAddress.getByAddress(new byte[]{1, 2, 3, 4}).getCanonicalHostName();
        // Output canonical host name and the exception thrown by the built-in resolver
        System.err.println("Canonical host name:" + canonicalHostName);
        System.err.println("Exception thrown by the built-in resolver:" + lastReverseLookupThrowable);

        // Check that originally supplied byte array was used to construct canonical host name after
        // failed reverse lookup.
        Assert.assertEquals("1.2.3.4", canonicalHostName, "unexpected canonical hostname");

        // Check that on a provider side the IllegalArgumentException has been thrown by the built-in resolver
        Assert.assertTrue(lastReverseLookupThrowable instanceof IllegalArgumentException,
                "wrong exception type is thrown by the built-in resolver");
    }
}
