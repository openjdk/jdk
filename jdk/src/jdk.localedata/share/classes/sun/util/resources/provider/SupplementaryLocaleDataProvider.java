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

package sun.util.resources.provider;

import java.lang.reflect.Module;
import java.util.Locale;
import java.util.ResourceBundle;

import sun.util.locale.provider.ResourceBundleProviderSupport;
import sun.util.resources.LocaleData;

/**
 * {@code SupplementaryLocaleDataProvider} in module jdk.localedata implements
 * {@code JavaTimeSupplementaryProvider} in module java.base. This class works as a
 * service agent between {@code ResourceBundle.getBundle} callers in java.base
 * and resource bundles in jdk.localedata.
 */
public class SupplementaryLocaleDataProvider extends LocaleData.SupplementaryResourceBundleProvider {
    @Override
    protected boolean isSupportedInModule(String baseName, Locale locale) {
        // The assumption here is that there are two modules containing
        // resource bundles for locale support. If resource bundles are split
        // into more modules, this method will need to be changed to determine
        // what locales are exactly supported.
        return !super.isSupportedInModule(baseName, locale);
    }

    @Override
    public ResourceBundle getBundle(String baseName, Locale locale) {
        Module module = LocaleDataProvider.class.getModule();
        String bundleName = toBundleName(baseName, locale);
        return ResourceBundleProviderSupport.loadResourceBundle(module, bundleName);
    }
}
