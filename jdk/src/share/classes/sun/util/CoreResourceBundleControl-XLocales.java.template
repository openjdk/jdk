/*
 * Copyright (c) 2005, Oracle and/or its affiliates. All rights reserved.
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

#warn This file is preprocessed before being compiled

package sun.util;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.ResourceBundle.Control;

/**
 * This is a convenient class for loading some of internal resources faster
 * if they are built with Resources.gmk defined in J2SE workspace. Also,
 * they have to be in class file format.
 *
 * "LOCALE_LIST" will be replaced at built time by a list of locales we
 * defined in Defs.gmk. We want to exclude these locales from search to
 * gain better performance. For example, since we know if the resource
 * is built with Resources.gmk, they are not going to provide basename_en.class
 * & basename_en_US.class resources, in that case, continuing searching them
 * is expensive. By excluding them from the candidate locale list, these
 * resources won't be searched.
 *
 * @since 1.6.
 */
public class CoreResourceBundleControl extends ResourceBundle.Control {
    /* the candidate locale list to search */
    private final Collection<Locale> excludedJDKLocales;
    /* singlton instance of the resource bundle control. */
    private static CoreResourceBundleControl resourceBundleControlInstance =
        new CoreResourceBundleControl();

    protected CoreResourceBundleControl() {
        excludedJDKLocales = Arrays.asList(#LOCALE_LIST#);
    }

    /**
     * This method is to provide a customized ResourceBundle.Control to speed
     * up the search of resources in JDK.
     *
     * @return the instance of resource bundle control.
     */
    public static CoreResourceBundleControl getRBControlInstance() {
        return resourceBundleControlInstance;
    }

    /**
     * This method is to provide a customized ResourceBundle.Control to speed
     * up the search of resources in JDK, with the bundle's package name check.
     *
     * @param bundleName bundle name to check
     * @return the instance of resource bundle control if the bundle is JDK's,
     *    otherwise returns null.
     */
    public static CoreResourceBundleControl getRBControlInstance(String bundleName) {
        if (bundleName.startsWith("com.sun.") ||
            bundleName.startsWith("java.") ||
            bundleName.startsWith("javax.") ||
            bundleName.startsWith("sun.")) {
            return resourceBundleControlInstance;
        } else {
            return null;
        }
    }

    /**
     * @returns a list of candidate locales to search from.
     * @exception NullPointerException if baseName or locale is null.
     */
    @Override
    public List<Locale> getCandidateLocales(String baseName, Locale locale) {
        List<Locale> candidates = super.getCandidateLocales(baseName, locale);
        candidates.removeAll(excludedJDKLocales);
        return candidates;
    }

    /**
     * @ returns TTL_DONT_CACHE so that ResourceBundle instance won't be cached.
     * User of this CoreResourceBundleControl should probably maintain a hard reference
     * to the ResourceBundle object themselves.
     */
    @Override
    public long getTimeToLive(String baseName, Locale locale) {
        return ResourceBundle.Control.TTL_DONT_CACHE;
    }
}
