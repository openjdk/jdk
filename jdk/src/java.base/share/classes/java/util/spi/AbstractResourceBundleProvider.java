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

package java.util.spi;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Module;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Locale;
import java.util.ResourceBundle;
import sun.util.locale.provider.ResourceBundleProviderSupport;
import static sun.security.util.SecurityConstants.GET_CLASSLOADER_PERMISSION;


/**
 * {@code AbstractResourceBundleProvider} is an abstract class for helping
 * implement the {@link ResourceBundleProvider} interface.
 *
 * @since 9
 */
public abstract class AbstractResourceBundleProvider implements ResourceBundleProvider {
    private static final String FORMAT_CLASS = "java.class";
    private static final String FORMAT_PROPERTIES = "java.properties";

    private final String[] formats;

    /**
     * Constructs an {@code AbstractResourceBundleProvider} with the
     * "java.properties" format. This constructor is equivalent to
     * {@code AbstractResourceBundleProvider("java.properties")}.
     */
    protected AbstractResourceBundleProvider() {
        this(FORMAT_PROPERTIES);
    }

    /**
     * Constructs an {@code AbstractResourceBundleProvider} with the specified
     * {@code formats}. The {@link #getBundle(String, Locale)} method looks up
     * resource bundles for the given {@code formats}. {@code formats} must
     * be "java.class" or "java.properties".
     *
     * @param formats the formats to be used for loading resource bundles
     * @throws NullPointerException if the given {@code formats} is null
     * @throws IllegalArgumentException if the given {@code formats} is not
     *         "java.class" or "java.properties".
     */
    protected AbstractResourceBundleProvider(String... formats) {
        this.formats = formats.clone();  // defensive copy
        if (this.formats.length == 0) {
            throw new IllegalArgumentException("empty formats");
        }
        for (String f : this.formats) {
            if (!FORMAT_CLASS.equals(f) && !FORMAT_PROPERTIES.equals(f)) {
                throw new IllegalArgumentException(f);
            }
        }
    }

    /**
     * Returns the bundle name for the given {@code baseName} and {@code
     * locale}.  This method is called from the default implementation of the
     * {@link #getBundle(String, Locale)} method.
     *
     * @implNote The default implementation of this method is the same as the
     * implementation of
     * {@link java.util.ResourceBundle.Control#toBundleName(String, Locale)}.
     *
     * @param baseName the base name of the resource bundle, a fully qualified
     *                 class name
     * @param locale   the locale for which a resource bundle should be loaded
     * @return the bundle name for the resource bundle
     */
    protected String toBundleName(String baseName, Locale locale) {
        return ResourceBundle.Control.getControl(ResourceBundle.Control.FORMAT_DEFAULT)
                   .toBundleName(baseName, locale);
    }

    /**
     * Returns a {@code ResourceBundle} for the given {@code baseName} and
     * {@code locale}. This method calls the
     * {@link #toBundleName(String, Locale) toBundleName} method to get the
     * bundle name for the {@code baseName} and {@code locale}. The formats
     * specified by the constructor will be searched to find the resource
     * bundle.
     *
     * @implNote
     * The default implementation of this method will find the resource bundle
     * local to the module of this provider.
     *
     * @param baseName the base bundle name of the resource bundle, a fully
     *                 qualified class name.
     * @param locale the locale for which the resource bundle should be instantiated
     * @return {@code ResourceBundle} of the given {@code baseName} and {@code locale},
     *         or null if no resource bundle is found
     * @throws NullPointerException if {@code baseName} or {@code locale} is null
     * @throws UncheckedIOException if any IO exception occurred during resource
     *         bundle loading
     */
    @Override
    public ResourceBundle getBundle(String baseName, Locale locale) {
        Module module = this.getClass().getModule();
        String bundleName = toBundleName(baseName, locale);
        ResourceBundle bundle = null;
        for (String format : formats) {
            try {
                if (FORMAT_CLASS.equals(format)) {
                    PrivilegedAction<ResourceBundle> pa = () ->
                                    ResourceBundleProviderSupport
                                         .loadResourceBundle(module, bundleName);
                    bundle = AccessController.doPrivileged(pa, null, GET_CLASSLOADER_PERMISSION);
                } else if (FORMAT_PROPERTIES.equals(format)) {
                    bundle = ResourceBundleProviderSupport
                                 .loadPropertyResourceBundle(module, bundleName);
                }
                if (bundle != null) {
                    break;
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return bundle;
    }
}
