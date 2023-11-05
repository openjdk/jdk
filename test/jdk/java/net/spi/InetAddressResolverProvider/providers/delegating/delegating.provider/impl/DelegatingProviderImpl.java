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

public class DelegatingProviderImpl extends InetAddressResolverProvider {

    public static volatile boolean changeReverseLookupAddress;
    public static volatile Throwable lastReverseLookupThrowable;

    @Override
    public InetAddressResolver get(Configuration configuration) {
        System.out.println("The following provider will be used by current test:" +
                this.getClass().getCanonicalName());
        return new InetAddressResolver() {
            @Override
            public Stream<InetAddress> lookupByName(String host, LookupPolicy lookupPolicy) throws UnknownHostException {
                return configuration.builtinResolver().lookupByName(host, lookupPolicy);
            }

            @Override
            public String lookupByAddress(byte[] addr) throws UnknownHostException {
                try {
                    if (!changeReverseLookupAddress) {
                        return configuration.builtinResolver().lookupByAddress(addr);
                    } else {
                        // Deliberately supply address bytes array with wrong size
                        return configuration.builtinResolver().lookupByAddress(new byte[]{1, 2, 3});
                    }
                } catch (Throwable t) {
                    lastReverseLookupThrowable = t;
                    throw t;
                }
            }
        };
    }

    @Override
    public String name() {
        return "DelegatingProvider";
    }
}
