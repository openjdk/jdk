/*
 * Copyright (c) 2009, Oracle and/or its affiliates. All rights reserved.
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
package sun.net.ftp;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ServiceConfigurationError;
//import sun.misc.Service;

/**
 * Service provider class for FtpClient.
 * Sub-classes of FtpClientProvider provide an implementation of {@link FtpClient}
 * and associated classes. Applications do not normally use this class directly.
 * See {@link #provider() } for how providers are found and loaded.
 *
 * @since 1.7
 */
public abstract class FtpClientProvider {

    /**
     * Creates a FtpClient from this provider.
     *
     * @return The created {@link FtpClient}.
     */
    public abstract FtpClient createFtpClient();
    private static final Object lock = new Object();
    private static FtpClientProvider provider = null;

    /**
     * Initializes a new instance of this class.
     *
     * @throws SecurityException if a security manager is installed and it denies
     *         {@link RuntimePermission}<tt>("ftpClientProvider")</tt>
     */
    protected FtpClientProvider() {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(new RuntimePermission("ftpClientProvider"));
        }
    }

    private static boolean loadProviderFromProperty() {
        String cm = System.getProperty("sun.net.ftpClientProvider");
        if (cm == null) {
            return false;
        }
        try {
            Class c = Class.forName(cm, true, null);
            provider = (FtpClientProvider) c.newInstance();
            return true;
        } catch (ClassNotFoundException x) {
            throw new ServiceConfigurationError(x.toString());
        } catch (IllegalAccessException x) {
            throw new ServiceConfigurationError(x.toString());
        } catch (InstantiationException x) {
            throw new ServiceConfigurationError(x.toString());
        } catch (SecurityException x) {
            throw new ServiceConfigurationError(x.toString());
        }
    }

    private static boolean loadProviderAsService() {
        //        Iterator i = Service.providers(FtpClientProvider.class,
        //                ClassLoader.getSystemClassLoader());
        //        while (i.hasNext()) {
        //            try {
        //                provider = (FtpClientProvider) i.next();
        //                return true;
        //            } catch (ServiceConfigurationError sce) {
        //                if (sce.getCause() instanceof SecurityException) {
        //                    // Ignore, try next provider, if any
        //                    continue;
        //                }
        //                throw sce;
        //            }
        //        }
        return false;
    }

    /**
     * Returns the system wide default FtpClientProvider for this invocation of
     * the Java virtual machine.
     *
     * <p> The first invocation of this method locates the default provider
     * object as follows: </p>
     *
     * <ol>
     *
     *   <li><p> If the system property
     *   <tt>java.net.FtpClientProvider</tt> is defined then it is
     *   taken to be the fully-qualified name of a concrete provider class.
     *   The class is loaded and instantiated; if this process fails then an
     *   unspecified unchecked error or exception is thrown.  </p></li>
     *
     *   <li><p> If a provider class has been installed in a jar file that is
     *   visible to the system class loader, and that jar file contains a
     *   provider-configuration file named
     *   <tt>java.net.FtpClientProvider</tt> in the resource
     *   directory <tt>META-INF/services</tt>, then the first class name
     *   specified in that file is taken.  The class is loaded and
     *   instantiated; if this process fails then an unspecified unchecked error or exception is
     *   thrown.  </p></li>
     *
     *   <li><p> Finally, if no provider has been specified by any of the above
     *   means then the system-default provider class is instantiated and the
     *   result is returned.  </p></li>
     *
     * </ol>
     *
     * <p> Subsequent invocations of this method return the provider that was
     * returned by the first invocation.  </p>
     *
     * @return  The system-wide default FtpClientProvider
     */
    public static FtpClientProvider provider() {
        synchronized (lock) {
            if (provider != null) {
                return provider;
            }
            return (FtpClientProvider) AccessController.doPrivileged(
                    new PrivilegedAction<Object>() {

                        public Object run() {
                            if (loadProviderFromProperty()) {
                                return provider;
                            }
                            if (loadProviderAsService()) {
                                return provider;
                            }
                            provider = new sun.net.ftp.impl.DefaultFtpClientProvider();
                            return provider;
                        }
                    });
        }
    }
}
