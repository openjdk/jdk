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
 * Service-provider class for {@linkplain InetAddressResolver InetAddress resolvers}.
 *
 * <p>A resolver provider is a factory for custom implementations of {@linkplain
 * InetAddressResolver resolvers}. A resolver define operations for looking up
 * (resolving) host names and IP addresses.
 * <p>A resolver provider is a concrete subclass of this class that has a
 * zero-argument constructor and implements the abstract methods specified below.
 *
 * <h2 id="system-wide-resolver"> Installing the system-wide resolver </h2>
 * <p> Resolver providers are discovered by {@link InetAddress} to instantiate and
 * install a <i>system-wide resolver</i>. Resolver providers are located by
 * {@link InetAddress} using the {@link ServiceLoader} facility.
 *
 * <p>Host name resolution and reverse name resolution operations performed by
 * {@link InetAddress} use the <i>system-wide</i> {@linkplain InetAddressResolver
 * resolver}. The system-wide resolver is set once, lazily, after the VM is fully
 * initialized and when an invocation of a method in {@link InetAddress} class
 * triggers the first lookup operation.
 *
 * <p> A <i>custom resolver</i> can be installed as the system-wide resolver
 * by deploying an {@code InetAddressResolverProvider}. If no resolver provider
 * is found, then the <a href="../InetAddress.html#built-in-resolver">built-in
 * resolver</a> will be set as the system-wide resolver.
 *
 * <p> A custom resolver is found and installed as the system-wide resolver
 * as follows:
 * <ol>
 *  <li>The {@link ServiceLoader} mechanism is used to locate an
 *      {@code InetAddressResolverProvider} using the
 *      system class loader. The order in which providers are located is
 *      {@linkplain ServiceLoader#load(java.lang.Class, java.lang.ClassLoader)
 *      implementation specific}.
 *      The first provider found will be used to instantiate the
 *      {@link InetAddressResolver InetAddressResolver} by invoking the
 *      {@link InetAddressResolverProvider#get(InetAddressResolverProvider.Configuration)}
 *      method. The returned {@code InetAddressResolver} will be installed as the
 *      system-wide resolver.
 *  <li>If the previous step fails to find any resolver provider the
 *      built-in resolver will be set as the system-wide resolver.
 * </ol>
 *
 * <p> If instantiating a custom resolver from a provider discovered in
 * step 1 throws an error or exception, the system-wide resolver will not be
 * installed and the error or exception will be propagated to the calling thread.
 * Otherwise, any lookup operation will be performed through the installed
 * <i>system-wide resolver</i>.
 *
 * @implNote {@link InetAddress} will use the <i>built-in resolver</i> for any lookup operation
 * that might occur before the VM is fully booted.
 *
 * @since 18
 */
public abstract class InetAddressResolverProvider {

    /**
     * Initialise and return an {@link InetAddressResolver} provided by
     * this provider. This method is called by {@link InetAddress} when
     * <a href="#system-wide-resolver">installing</a>
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
     * implementation initialization should be as simple as possible, in order to avoid
     * possible risks of deadlock or class loading cycles during the instantiation of the
     * service provider.
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
     * A {@code Configuration} object is supplied to the
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
