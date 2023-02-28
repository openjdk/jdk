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

package impl;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.spi.InetAddressResolver;
import java.net.spi.InetAddressResolverProvider;
import java.util.stream.Stream;

public class WithBootstrapResolverUsageProvider extends InetAddressResolverProvider {

    public static volatile long numberOfGetCalls;

    @Override
    public InetAddressResolver get(Configuration configuration) {
        numberOfGetCalls++;
        System.out.println("The following provider will be used by current test:" +
                this.getClass().getCanonicalName());
        System.out.println("InetAddressResolverProvider::get() called " + numberOfGetCalls + " times");

        // We use different names to avoid InetAddress-level caching
        doLookup("foo" + numberOfGetCalls + ".A.org");

        // We need second call to test how InetAddress internals maintain reference to a bootstrap resolver
        doLookup("foo" + numberOfGetCalls + ".B.org");

        return new InetAddressResolver() {
            @Override
            public Stream<InetAddress> lookupByName(String host, LookupPolicy lookupPolicy)
                    throws UnknownHostException {
                return Stream.of(InetAddress.getByAddress(host, new byte[]{127, 0, 2, 1}));
            }

            @Override
            public String lookupByAddress(byte[] addr) throws UnknownHostException {
                return configuration.builtinResolver().lookupByAddress(addr);
            }
        };
    }

    // Perform an InetAddress resolution lookup operation
    private static void doLookup(String hostName) {
        try {
            InetAddress.getByName(hostName);
        } catch (UnknownHostException e) {
            // Ignore UHE since the bootstrap resolver is used here
        }
    }

    @Override
    public String name() {
        return "WithBootstrapResolverUsageProvider";
    }
}
