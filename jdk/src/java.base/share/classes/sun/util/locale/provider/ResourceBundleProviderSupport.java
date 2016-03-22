/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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

package sun.util.locale.provider;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.lang.reflect.Module;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;

/**
 * ResourceBundleProviderSupport provides convenience methods for loading
 * resource bundles.
 */
public class ResourceBundleProviderSupport {
    /**
     * Loads a {@code ResourceBundle} of the given {@code bundleName} local to
     * the given {@code module}.
     *
     * @apiNote
     * {@link Class#forName(Module, String)} does a stack-based permission check.
     * Caller of this method is responsible for doing an appropriate permission
     * on behalf of the caller before calling this method.
     *
     * @param module     the module from which the {@code ResourceBundle} is loaded
     * @param bundleName the bundle name for the {@code ResourceBundle} class,
     *                   such as "com.example.app.MyResources_fr"
     * @return the {@code ResourceBundle}, or null if no {@code ResourceBundle} is found
     * @throws SecurityException
     *         if a security manager exists, it denies loading the class given by
     *         {@code bundleName} from the given {@code module}.
     *         If the given module is "java.base", this method will not do security check.
     * @throws NullPointerException
     *         if {@code module} or {@code bundleName) is null
     * @see Class#forName(Module, String)
     */
    public static ResourceBundle loadResourceBundle(Module module, String bundleName)
    {
        Class<?> c = Class.forName(module, bundleName);
        if (c != null && ResourceBundle.class.isAssignableFrom(c)) {
            try {
                @SuppressWarnings("unchecked")
                Class<ResourceBundle> bundleClass = (Class<ResourceBundle>) c;
                Constructor<ResourceBundle> ctor = bundleClass.getConstructor();
                if (!Modifier.isPublic(ctor.getModifiers())) {
                    return null;
                }
                // java.base may not be able to read the bundleClass's module.
                PrivilegedAction<Void> pa1 = () -> { ctor.setAccessible(true); return null; };
                AccessController.doPrivileged(pa1);
                try {
                    return ctor.newInstance((Object[]) null);
                } catch (InvocationTargetException e) {
                    uncheckedThrow(e);
                } catch (InstantiationException | IllegalAccessException e) {
                    throw new InternalError(e);
                }
            } catch (NoSuchMethodException e) {
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> void uncheckedThrow(Throwable t) throws T {
       if (t != null)
            throw (T)t;
       else
            throw new Error("Unknown Exception");
    }

    /**
     * Loads properties of the given {@code bundleName} local to the given
     * {@code module} and returns a {@code ResourceBundle} produced from the
     * loaded properties.
     *
     * @apiNote This method is intended for internal use.  Need to refactor.
     *
     * @param module     the module from which the properties are loaded
     * @param bundleName the bundle name of the properties,
     *                   such as "com.example.app.MyResources_de"
     * @return the {@code ResourceBundle} produced from the loaded properties,
     *         or null if no properties are found
     * @see PropertyResourceBundle
     */
    public static ResourceBundle loadPropertyResourceBundle(Module module, String bundleName)
            throws IOException
    {
        String resourceName = toResourceName(bundleName, "properties");
        if (resourceName == null) {
            return null;
        }

        PrivilegedAction<InputStream> pa = () -> {
            try {
                return module.getResourceAsStream(resourceName);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        };
        try (InputStream stream = AccessController.doPrivileged(pa)) {
            if (stream != null) {
                return new PropertyResourceBundle(stream);
            } else {
                return null;
            }
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    private static String toResourceName(String bundleName, String suffix) {
        if (bundleName.contains("://")) {
            return null;
        }
        StringBuilder sb = new StringBuilder(bundleName.length() + 1 + suffix.length());
        sb.append(bundleName.replace('.', '/')).append('.').append(suffix);
        return sb.toString();
    }
}
