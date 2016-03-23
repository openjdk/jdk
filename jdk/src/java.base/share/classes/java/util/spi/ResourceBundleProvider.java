/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Locale;
import java.util.ResourceBundle;

/**
 * {@code ResourceBundleProvider} is a provider interface that is used for
 * loading resource bundles. Implementation classes of this interface are loaded
 * with {@link java.util.ServiceLoader ServiceLoader} during a call to the
 * {@link ResourceBundle#getBundle(String, Locale, ClassLoader)
 * ResourceBundle.getBundle} method. The provider service type is determined by
 * {@code basename+"Provider"}. For example, if the base name is
 * "com.example.app.MyResources", {@code com.example.app.MyResourcesProvider}
 * will be the provider service type.
 * <p>
 * This providers's {@link #getBundle(String, Locale) getBundle} method is called
 * through the resource bundle loading process instead of {@link
 * java.util.ResourceBundle.Control#newBundle(String, Locale, String, ClassLoader, boolean)
 * ResourceBundle.Control.newBundle()}. Refer to {@link ResourceBundle} for
 * details.
 *
 * @since 9
 */
public interface ResourceBundleProvider {
    /**
     * Returns a {@code ResourceBundle} for the given bundle name and locale.
     * This method returns null if there is no {@code ResourceBundle} found
     * for the given parameters.
     *
     *
     * @param baseName
     *        the base bundle name of the resource bundle, a fully
     *        qualified class name
     * @param locale
     *        the locale for which the resource bundle should be loaded
     * @return the ResourceBundle created for the given parameters, or null if no
     *         {@code ResourceBundle} for the given parameters is found
     */
    public ResourceBundle getBundle(String baseName, Locale locale);
}
