/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

package java.net.spi;

import sun.net.ResolverProviderConfiguration;

import java.net.InetAddress;
import java.util.ServiceLoader;

/**
 * A resolver provider class is a factory for custom implementations of {@linkplain
 * InetAddressResolver resolvers} which define operations for looking-up (resolving) host names
 * and IP addresses.
 * Resolver providers are <a href="{@docRoot}/java.base/java/net/InetAddress.html#resolverProviders">
 * discovered</a> by {@link InetAddress} to instantiate and install a <i>system-wide resolver</i>.
 * <p>
 * A resolver provider is a concrete subclass of this class that has a zero-argument
 * constructor and implements the abstract methods specified below.
 * <p>
 * Resolver providers are located using the {@link ServiceLoader} facility, as specified by
 * {@link InetAddress}.
 *
 * @since 18
 */
public abstract class InetAddressResolverProvider {

    /**
     * Initialise and return the {@link InetAddressResolver} provided by
     * this provider. This method is called by {@link InetAddress} when
     * <a href="{@docRoot}/java.base/java/net/InetAddress.html#resolverProviders">installing</a>
     * the system-wide resolver implementation.
     * <p>
     * Any error or exception thrown by this method is considered as
     * a failure of {@code InetAddressResolver} instantiation and will be propagated to
     * the calling thread.
     * @param configuration a {@link Configuration} instance containing platform built-in address
     *                     resolution configuration.
     * @return the resolver provided by this provider
     */
    public abstract InetAddressResolver get(Configuration configuration);

    /**
     * Returns the name of this provider.
     *
     * @return the resolver provider name
     */
    public abstract String name();

    /**
     * The {@code RuntimePermission("inetAddressResolverProvider")} is
     * necessary to subclass and instantiate the {@code InetAddressResolverProvider} class,
     * as well as to obtain resolver from an instance of that class,
     * and it is also required to obtain the operating system name resolution configurations.
     */
    private static final RuntimePermission INET_ADDRESS_RESOLVER_PERMISSION =
            new RuntimePermission("inetAddressResolverProvider");

    /**
     * Creates a new instance of {@code InetAddressResolverProvider}.
     *
     * @throws SecurityException if a security manager is present and its
     *                           {@code checkPermission} method doesn't allow the
     *                           {@code RuntimePermission("inetAddressResolverProvider")}.
     * @implNote It is recommended that an {@code InetAddressResolverProvider} service
     * implementation does not perform any heavy initialization in its
     * constructor, in order to avoid possible risks of deadlock or class
     * loading cycles during the instantiation of the service provider.
     */
    protected InetAddressResolverProvider() {
        this(checkPermission());
    }

    private InetAddressResolverProvider(Void unused) {
    }

    @SuppressWarnings("removal")
    private static Void checkPermission() {
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(INET_ADDRESS_RESOLVER_PERMISSION);
        }
        return null;
    }

    /**
     * A {@code Configuration} interface is supplied to the
     * {@link InetAddressResolverProvider#get(Configuration)} method when installing a
     * system-wide custom resolver implementation.
     * The custom resolver implementation can then delegate to the built-in resolver
     * provided by this interface if it needs to.
     *
     * @since 18
     */
    public sealed interface Configuration permits ResolverProviderConfiguration {
        /**
         * Returns platform built-in {@linkplain InetAddressResolver resolver}.
         *
         * @return the JDK built-in resolver.
         */
        InetAddressResolver builtinResolver();

        /**
         * Reads the localhost name from the system configuration.
         *
         * @return the localhost name.
         */
        String lookupLocalHostName();
    }
}
