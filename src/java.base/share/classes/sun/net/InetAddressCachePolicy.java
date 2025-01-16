/*
 * Copyright (c) 1998, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package sun.net;

import java.security.Security;

public final class InetAddressCachePolicy {

    // Controls the cache policy for successful lookups only
    private static final String cachePolicyProp = "networkaddress.cache.ttl";
    private static final String cachePolicyPropFallback =
        "sun.net.inetaddr.ttl";

    // Controls the cache stale policy for successful lookups only
    private static final String cacheStalePolicyProp =
        "networkaddress.cache.stale.ttl";
    private static final String cacheStalePolicyPropFallback =
        "sun.net.inetaddr.stale.ttl";

    // Controls the cache policy for negative lookups only
    private static final String negativeCachePolicyProp =
        "networkaddress.cache.negative.ttl";
    private static final String negativeCachePolicyPropFallback =
        "sun.net.inetaddr.negative.ttl";

    public static final int FOREVER = -1;
    public static final int NEVER = 0;

    /* default value for positive lookups */
    public static final int DEFAULT_POSITIVE = 30;

    /* The Java-level namelookup cache policy for successful lookups:
     *
     * -1: caching forever
     * any positive value: the number of seconds to cache an address for
     *
     * default value is 30 seconds
     */
    private static volatile int cachePolicy = DEFAULT_POSITIVE;

    /* The Java-level namelookup cache stale policy:
     *
     * any positive value: the number of seconds to use the stale names
     * zero: do not use stale names
     *
     * default value is never (NEVER).
     */
    private static volatile int staleCachePolicy = NEVER;

    /* The Java-level namelookup cache policy for negative lookups:
     *
     * -1: caching forever
     * any positive value: the number of seconds to cache an address for
     *
     * default value is 0. It can be set to some other value for
     * performance reasons.
     */
    private static volatile int negativeCachePolicy = NEVER;

    /*
     * Initialize
     */
    static {
        /* If the cache policy property is not specified
         *  then the default positive cache value is used.
         */
        Integer tmp = getProperty(cachePolicyProp, cachePolicyPropFallback);
        if (tmp != null) {
            cachePolicy = tmp < 0 ? FOREVER : tmp;
        }
        tmp = getProperty(negativeCachePolicyProp,
                          negativeCachePolicyPropFallback);

        if (tmp != null) {
            negativeCachePolicy = tmp < 0 ? FOREVER : tmp;
        }
        if (cachePolicy > 0) {
            tmp = getProperty(cacheStalePolicyProp,
                              cacheStalePolicyPropFallback);
            if (tmp != null) {
                staleCachePolicy = tmp;
            }
        }
    }

    private static Integer getProperty(String cachePolicyProp,
                                       String cachePolicyPropFallback) {
        try {
            String tmpString = Security.getProperty(cachePolicyProp);
            if (tmpString != null) {
                return Integer.valueOf(tmpString);
            }
        } catch (NumberFormatException ignored) {
            // Ignore
        }

        try {
            String tmpString = System.getProperty(cachePolicyPropFallback);
            if (tmpString != null) {
                return Integer.decode(tmpString);
            }
        } catch (NumberFormatException ignored) {
            // Ignore
        }
        return null;
    }

    public static int get() {
        return cachePolicy;
    }

    public static int getStale() {
        return staleCachePolicy;
    }

    public static int getNegative() {
        return negativeCachePolicy;
    }
}
