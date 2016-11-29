/*
 * Copyright (c) 2014, 2016, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Formatter;
import java.util.Iterator;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

/**
 * A mechanism for accessing service providers via JVMCI.
 */
public final class Services {

    private Services() {
    }

    private static int getJavaSpecificationVersion() {
        String value = System.getProperty("java.specification.version");
        if (value.startsWith("1.")) {
            value = value.substring(2);
        }
        return Integer.parseInt(value);
    }

    /**
     * The integer value corresponding to the value of the {@code java.specification.version} system
     * property after any leading {@code "1."} has been stripped.
     */
    public static final int JAVA_SPECIFICATION_VERSION = getJavaSpecificationVersion();

    // Use reflection so that this compiles on Java 8
    private static final Method getModule;
    private static final Method getPackages;
    private static final Method addUses;
    private static final Method isExported;
    private static final Method addExports;

    static {
        if (JAVA_SPECIFICATION_VERSION >= 9) {
            try {
                getModule = Class.class.getMethod("getModule");
                Class<?> moduleClass = getModule.getReturnType();
                getPackages = moduleClass.getMethod("getPackages");
                addUses = moduleClass.getMethod("addUses", Class.class);
                isExported = moduleClass.getMethod("isExported", String.class, moduleClass);
                addExports = moduleClass.getMethod("addExports", String.class, moduleClass);
            } catch (NoSuchMethodException | SecurityException e) {
                throw new InternalError(e);
            }
        } else {
            getModule = null;
            getPackages = null;
            addUses = null;
            isExported = null;
            addExports = null;
        }
    }

    @SuppressWarnings("unchecked")
    static <T> T invoke(Method method, Object receiver, Object... args) {
        try {
            return (T) method.invoke(receiver, args);
        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            throw new InternalError(e);
        }
    }

    /**
     * Performs any required security checks and dynamic reconfiguration to allow the module of a
     * given class to access the classes in the JVMCI module.
     *
     * Note: This API uses {@link Class} instead of {@code Module} to provide backwards
     * compatibility for JVMCI clients compiled against a JDK release earlier than 9.
     *
     * @param requestor a class requesting access to the JVMCI module for its module
     * @throws SecurityException if a security manager is present and it denies
     *             {@link JVMCIPermission}
     */
    public static void exportJVMCITo(Class<?> requestor) {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(new JVMCIPermission());
        }
        if (JAVA_SPECIFICATION_VERSION >= 9) {
            Object jvmci = invoke(getModule, Services.class);
            Object requestorModule = invoke(getModule, requestor);
            if (jvmci != requestorModule) {
                String[] packages = invoke(getPackages, jvmci);
                for (String pkg : packages) {
                    // Export all JVMCI packages dynamically instead
                    // of requiring a long list of --add-exports
                    // options on the JVM command line.
                    boolean exported = invoke(isExported, jvmci, pkg, requestorModule);
                    if (!exported) {
                        invoke(addExports, jvmci, pkg, requestorModule);
                    }
                }
            }
        }
    }

    /**
     * Gets an {@link Iterable} of the JVMCI providers available for a given service.
     *
     * @throws SecurityException if a security manager is present and it denies
     *             {@link JVMCIPermission}
     */
    public static <S> Iterable<S> load(Class<S> service) {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(new JVMCIPermission());
        }
        if (JAVA_SPECIFICATION_VERSION >= 9) {
            Object jvmci = invoke(getModule, Services.class);
            invoke(addUses, jvmci, service);
        }

        // Restrict JVMCI clients to be on the class path or module path
        return ServiceLoader.load(service, ClassLoader.getSystemClassLoader());
    }

    /**
     * Gets the JVMCI provider for a given service for which at most one provider must be available.
     *
     * @param service the service whose provider is being requested
     * @param required specifies if an {@link InternalError} should be thrown if no provider of
     *            {@code service} is available
     * @throws SecurityException if a security manager is present and it denies
     *             {@link JVMCIPermission}
     */
    public static <S> S loadSingle(Class<S> service, boolean required) {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(new JVMCIPermission());
        }
        if (JAVA_SPECIFICATION_VERSION >= 9) {
            Object jvmci = invoke(getModule, Services.class);
            invoke(addUses, jvmci, service);
        }
        // Restrict JVMCI clients to be on the class path or module path
        Iterable<S> providers = ServiceLoader.load(service, ClassLoader.getSystemClassLoader());
        S singleProvider = null;
        try {
            for (Iterator<S> it = providers.iterator(); it.hasNext();) {
                singleProvider = it.next();
                if (it.hasNext()) {
                    throw new InternalError(String.format("Multiple %s providers found", service.getName()));
                }
            }
        } catch (ServiceConfigurationError e) {
            // If the service is required we will bail out below.
        }
        if (singleProvider == null && required) {
            String javaHome = System.getProperty("java.home");
            String vmName = System.getProperty("java.vm.name");
            Formatter errorMessage = new Formatter();
            errorMessage.format("The VM does not expose required service %s.%n", service.getName());
            errorMessage.format("Currently used Java home directory is %s.%n", javaHome);
            errorMessage.format("Currently used VM configuration is: %s", vmName);
            throw new UnsupportedOperationException(errorMessage.toString());
        }
        return singleProvider;
    }
}
