/*
 * Copyright (c) 2014, 2021, Oracle and/or its affiliates. All rights reserved.
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
package jdk.vm.ci.services;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Formatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

import jdk.internal.misc.TerminatingThreadLocal;
import jdk.internal.misc.VM;

/**
 * Provides utilities needed by JVMCI clients.
 */
public final class Services {

    /**
     * Guards code that should be run when building an JVMCI shared library but should be excluded
     * from (being compiled into) the library. Such code must be directly guarded by an {@code if}
     * statement on this field - the guard cannot be behind a method call.
     */
    public static final boolean IS_BUILDING_NATIVE_IMAGE = Boolean.parseBoolean(VM.getSavedProperty("jdk.vm.ci.services.aot"));

    /**
     * Guards code that should only be run in a JVMCI shared library. Such code must be directly
     * guarded by an {@code if} statement on this field - the guard cannot be behind a method call.
     *
     * The value of this field in a JVMCI shared library runtime must be {@code true}.
     */
    public static final boolean IS_IN_NATIVE_IMAGE;
    static {
        /*
         * Prevents javac from constant folding use of this field. It is set to true by the process
         * that builds the shared library.
         */
        IS_IN_NATIVE_IMAGE = false;
    }

    private Services() {
    }

    /**
     * In a native image, this field is initialized by {@link #initializeSavedProperties(byte[])}.
     */
    private static volatile Map<String, String> savedProperties;

    static final boolean JVMCI_ENABLED = Boolean.parseBoolean(VM.getSavedProperties().get("jdk.internal.vm.ci.enabled"));

    /**
     * Checks that JVMCI is enabled in the VM and throws an error if it isn't.
     */
    static void checkJVMCIEnabled() {
        if (!JVMCI_ENABLED) {
            throw new Error("The EnableJVMCI VM option must be true (i.e., -XX:+EnableJVMCI) to use JVMCI");
        }
    }

    /**
     * Gets an unmodifiable copy of the system properties saved when {@link System} is initialized.
     */
    public static Map<String, String> getSavedProperties() {
        checkJVMCIEnabled();
        if (IS_IN_NATIVE_IMAGE) {
            if (savedProperties == null) {
                throw new InternalError("Saved properties not initialized");
            }
        } else {
            if (savedProperties == null) {
                synchronized (Services.class) {
                    if (savedProperties == null) {
                        @SuppressWarnings("removal")
                        SecurityManager sm = System.getSecurityManager();
                        if (sm != null) {
                            sm.checkPermission(new JVMCIPermission());
                        }
                        savedProperties = VM.getSavedProperties();
                    }
                }
            }
        }
        return savedProperties;
    }

    /**
     * Helper method equivalent to {@link #getSavedProperties()}{@code .getOrDefault(name, def)}.
     */
    public static String getSavedProperty(String name, String def) {
        return Services.getSavedProperties().getOrDefault(name, def);
    }

    /**
     * Helper method equivalent to {@link #getSavedProperties()}{@code .get(name)}.
     */
    public static String getSavedProperty(String name) {
        return Services.getSavedProperties().get(name);
    }

    /**
     * Causes the JVMCI subsystem to be initialized if it isn't already initialized.
     */
    public static void initializeJVMCI() {
        checkJVMCIEnabled();
        try {
            Class.forName("jdk.vm.ci.runtime.JVMCI");
        } catch (ClassNotFoundException e) {
            throw new InternalError(e);
        }
    }

    private static final Map<Class<?>, List<?>> servicesCache = IS_BUILDING_NATIVE_IMAGE ? new HashMap<>() : null;

    @SuppressWarnings("unchecked")
    private static <S> Iterable<S> load0(Class<S> service) {
        if (IS_IN_NATIVE_IMAGE || IS_BUILDING_NATIVE_IMAGE) {
            List<?> list = servicesCache.get(service);
            if (list != null) {
                return (Iterable<S>) list;
            }
            if (IS_IN_NATIVE_IMAGE) {
                throw new InternalError(String.format("No %s providers found when building native image", service.getName()));
            }
        }

        Iterable<S> providers = ServiceLoader.load(service, ClassLoader.getSystemClassLoader());
        if (IS_BUILDING_NATIVE_IMAGE) {
            synchronized (servicesCache) {
                ArrayList<S> providersList = new ArrayList<>();
                for (S provider : providers) {
                    providersList.add(provider);
                }
                servicesCache.put(service, providersList);
                providers = providersList;
            }
        }
        return providers;
    }

    /**
     * Opens all JVMCI packages to {@code otherModule}.
     */
    static void openJVMCITo(Module otherModule) {
        Module jvmci = Services.class.getModule();
        if (jvmci != otherModule) {
            Set<String> packages = jvmci.getPackages();
            for (String pkg : packages) {
                boolean opened = jvmci.isOpen(pkg, otherModule);
                if (!opened) {
                    jvmci.addOpens(pkg, otherModule);
                }
            }
        }
    }

    /**
     * Gets an {@link Iterable} of the JVMCI providers available for a given service.
     *
     * @throws SecurityException if a security manager is present and it denies <tt>
     *             {@link RuntimePermission}("jvmci")</tt>
     */
    public static <S> Iterable<S> load(Class<S> service) {
        @SuppressWarnings("removal")
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(new JVMCIPermission());
        }
        return load0(service);
    }

    /**
     * Gets the JVMCI provider for a given service for which at most one provider must be available.
     *
     * @param service the service whose provider is being requested
     * @param required specifies if an {@link InternalError} should be thrown if no provider of
     *            {@code service} is available
     * @throws SecurityException if a security manager is present and it denies <tt>
     *             {@link RuntimePermission}("jvmci")</tt>
     */
    public static <S> S loadSingle(Class<S> service, boolean required) {
        @SuppressWarnings("removal")
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(new JVMCIPermission());
        }
        Iterable<S> providers = load0(service);

        S singleProvider = null;
        for (S provider : providers) {
            if (singleProvider != null) {
                throw new InternalError(String.format("Multiple %s providers found: %s, %s", service.getName(), singleProvider.getClass().getName(), provider.getClass().getName()));
            }
            singleProvider = provider;
        }
        if (singleProvider == null && required) {
            String javaHome = Services.getSavedProperty("java.home");
            String vmName = Services.getSavedProperty("java.vm.name");
            Formatter errorMessage = new Formatter();
            errorMessage.format("The VM does not expose required service %s.%n", service.getName());
            errorMessage.format("Currently used Java home directory is %s.%n", javaHome);
            errorMessage.format("Currently used VM configuration is: %s", vmName);
            throw new UnsupportedOperationException(errorMessage.toString());
        }
        return singleProvider;
    }

    /**
     * Creates a thread-local variable that notifies {@code onThreadTermination} when a thread
     * terminates and it has been initialized in the terminating thread (even if it was initialized
     * with a null value). A typical use is to release resources associated with a thread.
     *
     * @param initialValue a supplier to be used to determine the initial value
     * @param onThreadTermination a consumer invoked by a thread when terminating and the
     *            thread-local has an associated value for the terminating thread. The current
     *            thread's value of the thread-local variable is passed as a parameter to the
     *            consumer.
     */
    public static <T> ThreadLocal<T> createTerminatingThreadLocal(Supplier<T> initialValue, Consumer<T> onThreadTermination) {
        Objects.requireNonNull(initialValue, "initialValue must be non null.");
        Objects.requireNonNull(onThreadTermination, "onThreadTermination must be non null.");
        return new TerminatingThreadLocal<>() {

            @Override
            protected T initialValue() {
                return initialValue.get();
            }

            @Override
            protected void threadTerminated(T value) {
                onThreadTermination.accept(value);
            }
        };
    }

    /**
     * Initializes {@link #savedProperties} from the byte array returned by
     * {@code jdk.internal.vm.VMSupport.serializeSavedPropertiesToByteArray()}.
     */
    @VMEntryPoint
    private static void initializeSavedProperties(byte[] serializedProperties) throws IOException {
        if (!IS_IN_NATIVE_IMAGE) {
            throw new InternalError("Can only initialize saved properties in JVMCI shared library runtime");
        }
        Properties props = new Properties();
        props.load(new ByteArrayInputStream(serializedProperties));
        Map<String, String> map = new HashMap<>(props.size());
        for (var e : props.entrySet()) {
            map.put((String) e.getKey(), (String) e.getValue());
        }

        savedProperties = Collections.unmodifiableMap(map);
    }
}
