/*
 * Copyright 2003-2006 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package sun.security.jca;

import java.io.File;
import java.lang.reflect.*;

import java.security.*;

import sun.security.util.PropertyExpander;

/**
 * Class representing a configured provider. Encapsulates configuration
 * (className plus optional argument), the provider loading logic, and
 * the loaded Provider object itself.
 *
 * @author  Andreas Sterbenz
 * @since   1.5
 */
final class ProviderConfig {

    private final static sun.security.util.Debug debug =
        sun.security.util.Debug.getInstance("jca", "ProviderConfig");

    // classname of the SunPKCS11-Solaris provider
    private static final String P11_SOL_NAME =
        "sun.security.pkcs11.SunPKCS11";

    // config file argument of the SunPKCS11-Solaris provider
    private static final String P11_SOL_ARG  =
        "${java.home}/lib/security/sunpkcs11-solaris.cfg";

    // maximum number of times to try loading a provider before giving up
    private final static int MAX_LOAD_TRIES = 30;

    // parameters for the Provider(String) constructor,
    // use by doLoadProvider()
    private final static Class[] CL_STRING = { String.class };

    // lock to use while loading a provider. it ensures that each provider
    // is loaded only once and that we can detect recursion.
    // NOTE that because of 4944382 we use the system classloader as lock.
    // By using the same lock to load classes as to load providers we avoid
    // deadlock due to lock ordering. However, this class may be initialized
    // early in the startup when the system classloader has not yet been set
    // up. Use a temporary lock object if that is the case.
    // Any of this may break if the class loading implementation is changed.
    private static volatile Object LOCK = new Object();

    private static Object getLock() {
        Object o = LOCK;
        // check if lock is already set to the class loader
        if (o instanceof ClassLoader) {
            return o;
        }
        Object cl = AccessController.doPrivileged(
                                new PrivilegedAction<Object>() {
            public Object run() {
                return ClassLoader.getSystemClassLoader();
            }
        });
        // check if class loader initialized now (non-null)
        if (cl != null) {
            LOCK = cl;
            o = cl;
        }
        return o;
    }


    // name of the provider class
    private final String className;

    // argument to the provider constructor,
    // empty string indicates no-arg constructor
    private final String argument;

    // number of times we have already tried to load this provider
    private int tries;

    // Provider object, if loaded
    private volatile Provider provider;

    // flag indicating if we are currently trying to load the provider
    // used to detect recursion
    private boolean isLoading;

    ProviderConfig(String className, String argument) {
        if (className.equals(P11_SOL_NAME) && argument.equals(P11_SOL_ARG)) {
            checkSunPKCS11Solaris();
        }
        this.className = className;
        this.argument = expand(argument);
    }

    ProviderConfig(String className) {
        this(className, "");
    }

    ProviderConfig(Provider provider) {
        this.className = provider.getClass().getName();
        this.argument = "";
        this.provider = provider;
    }

    // check if we should try to load the SunPKCS11-Solaris provider
    // avoid if not available (pre Solaris 10) to reduce startup time
    // or if disabled via system property
    private void checkSunPKCS11Solaris() {
        Boolean o = AccessController.doPrivileged(
                                new PrivilegedAction<Boolean>() {
            public Boolean run() {
                File file = new File("/usr/lib/libpkcs11.so");
                if (file.exists() == false) {
                    return Boolean.FALSE;
                }
                if ("false".equalsIgnoreCase(System.getProperty
                        ("sun.security.pkcs11.enable-solaris"))) {
                    return Boolean.FALSE;
                }
                return Boolean.TRUE;
            }
        });
        if (o == Boolean.FALSE) {
            tries = MAX_LOAD_TRIES;
        }
    }

    private boolean hasArgument() {
        return argument.length() != 0;
    }

    // should we try to load this provider?
    private boolean shouldLoad() {
        return (tries < MAX_LOAD_TRIES);
    }

    // do not try to load this provider again
    private void disableLoad() {
        tries = MAX_LOAD_TRIES;
    }

    boolean isLoaded() {
        return (provider != null);
    }

    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof ProviderConfig == false) {
            return false;
        }
        ProviderConfig other = (ProviderConfig)obj;
        return this.className.equals(other.className)
            && this.argument.equals(other.argument);
    }

    public int hashCode() {
        return className.hashCode() + argument.hashCode();
    }

    public String toString() {
        if (hasArgument()) {
            return className + "('" + argument + "')";
        } else {
            return className;
        }
    }

    /**
     * Get the provider object. Loads the provider if it is not already loaded.
     */
    Provider getProvider() {
        // volatile variable load
        Provider p = provider;
        if (p != null) {
            return p;
        }
        if (shouldLoad() == false) {
            return null;
        }
        synchronized (getLock()) {
            p = provider;
            if (p != null) {
                // loaded by another thread while we were blocked on lock
                return p;
            }
            if (isLoading) {
                // because this method is synchronized, this can only
                // happen if there is recursion.
                if (debug != null) {
                    debug.println("Recursion loading provider: " + this);
                    new Exception("Call trace").printStackTrace();
                }
                return null;
            }
            try {
                isLoading = true;
                tries++;
                p = doLoadProvider();
            } finally {
                isLoading = false;
            }
            provider = p;
        }
        return p;
    }

    /**
     * Load and instantiate the Provider described by this class.
     *
     * NOTE use of doPrivileged().
     *
     * @return null if the Provider could not be loaded
     *
     * @throws ProviderException if executing the Provider's constructor
     * throws a ProviderException. All other Exceptions are ignored.
     */
    private Provider doLoadProvider() {
        return AccessController.doPrivileged(new PrivilegedAction<Provider>() {
            public Provider run() {
                if (debug != null) {
                    debug.println("Loading provider: " + ProviderConfig.this);
                }
                try {
                    ClassLoader cl = ClassLoader.getSystemClassLoader();
                    Class<?> provClass;
                    if (cl != null) {
                        provClass = cl.loadClass(className);
                    } else {
                        provClass = Class.forName(className);
                    }
                    Object obj;
                    if (hasArgument() == false) {
                        obj = provClass.newInstance();
                    } else {
                        Constructor<?> cons = provClass.getConstructor(CL_STRING);
                        obj = cons.newInstance(argument);
                    }
                    if (obj instanceof Provider) {
                        if (debug != null) {
                            debug.println("Loaded provider " + obj);
                        }
                        return (Provider)obj;
                    } else {
                        if (debug != null) {
                            debug.println(className + " is not a provider");
                        }
                        disableLoad();
                        return null;
                    }
                } catch (Exception e) {
                    Throwable t;
                    if (e instanceof InvocationTargetException) {
                        t = ((InvocationTargetException)e).getCause();
                    } else {
                        t = e;
                    }
                    if (debug != null) {
                        debug.println("Error loading provider " + ProviderConfig.this);
                        t.printStackTrace();
                    }
                    // provider indicates fatal error, pass through exception
                    if (t instanceof ProviderException) {
                        throw (ProviderException)t;
                    }
                    // provider indicates that loading should not be retried
                    if (t instanceof UnsupportedOperationException) {
                        disableLoad();
                    }
                    return null;
                }
            }
        });
    }

    /**
     * Perform property expansion of the provider value.
     *
     * NOTE use of doPrivileged().
     */
    private static String expand(final String value) {
        // shortcut if value does not contain any properties
        if (value.contains("${") == false) {
            return value;
        }
        return AccessController.doPrivileged(new PrivilegedAction<String>() {
            public String run() {
                try {
                    return PropertyExpander.expand(value);
                } catch (GeneralSecurityException e) {
                    throw new ProviderException(e);
                }
            }
        });
    }

}
