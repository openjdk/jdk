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
import java.net.spi.InetAddressResolver.LookupPolicy;
import java.net.spi.InetAddressResolverProvider;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Stream;

import testlib.ResolutionRegistry;

public class SimpleResolverProviderImpl extends InetAddressResolverProvider {

    public static ResolutionRegistry registry = new ResolutionRegistry();
    private static List<LookupPolicy> LOOKUP_HISTORY = Collections.synchronizedList(new ArrayList<>());
    private static volatile long LAST_LOOKUP_TIMESTAMP;
    private static Logger LOGGER = Logger.getLogger(SimpleResolverProviderImpl.class.getName());

    @Override
    public InetAddressResolver get(Configuration configuration) {
        System.out.println("The following provider will be used by current test:" + this.getClass().getCanonicalName());
        return new InetAddressResolver() {
            @Override
            public Stream<InetAddress> lookupByName(String host, LookupPolicy lookupPolicy) throws UnknownHostException {
                LOGGER.info("Looking-up addresses for '" + host + "'. Lookup characteristics:" +
                        Integer.toString(lookupPolicy.characteristics(), 2));
                LOOKUP_HISTORY.add(lookupPolicy);
                LAST_LOOKUP_TIMESTAMP = System.nanoTime();
                return registry.lookupHost(host, lookupPolicy);
            }

            @Override
            public String lookupByAddress(byte[] addr) throws UnknownHostException {
                LOGGER.info("Looking host name for the following address:" + ResolutionRegistry.addressBytesToString(addr));
                return registry.lookupAddress(addr);
            }
        };
    }

    // Utility methods
    public static LookupPolicy lastLookupPolicy() {
        return lookupPolicyHistory(0);
    }

    public static long getLastLookupTimestamp() {
        return LAST_LOOKUP_TIMESTAMP;
    }

    public static LookupPolicy lookupPolicyHistory(int position) {
        if (LOOKUP_HISTORY.isEmpty()) {
            throw new RuntimeException("No registered lookup policies");
        }
        if (position >= LOOKUP_HISTORY.size()) {
            throw new IllegalArgumentException("No element available with provided position");
        }
        return LOOKUP_HISTORY.get(LOOKUP_HISTORY.size() - position - 1);
    }


    @Override
    public String name() {
        return "simpleInetAddressResolver";
    }
}
