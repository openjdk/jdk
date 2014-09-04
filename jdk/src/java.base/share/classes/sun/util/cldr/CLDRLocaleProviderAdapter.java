/*
 * Copyright (c) 2012, 2014, Oracle and/or its affiliates. All rights reserved.
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

package sun.util.cldr;

import java.io.File;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.text.spi.BreakIteratorProvider;
import java.text.spi.CollatorProvider;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.spi.TimeZoneNameProvider;
import sun.util.locale.provider.JRELocaleProviderAdapter;
import sun.util.locale.provider.LocaleProviderAdapter;

/**
 * LocaleProviderAdapter implementation for the CLDR locale data.
 *
 * @author Masayoshi Okutsu
 * @author Naoto Sato
 */
public class CLDRLocaleProviderAdapter extends JRELocaleProviderAdapter {
    private static final String LOCALE_DATA_JAR_NAME = "cldrdata.jar";

    public CLDRLocaleProviderAdapter() {
        final String sep = File.separator;
        String localeDataJar = java.security.AccessController.doPrivileged(
                    new sun.security.action.GetPropertyAction("java.home"))
                + sep + "lib" + sep + "ext" + sep + LOCALE_DATA_JAR_NAME;

        // Peek at the installed extension directory to see if the jar file for
        // CLDR resources is installed or not.
        final File f = new File(localeDataJar);
        boolean result = AccessController.doPrivileged(
                new PrivilegedAction<Boolean>() {
                    @Override
                    public Boolean run() {
                        return f.exists();
                    }
                });
        if (!result) {
            throw new UnsupportedOperationException();
        }
    }

    /**
     * Returns the type of this LocaleProviderAdapter
     * @return the type of this
     */
    @Override
    public LocaleProviderAdapter.Type getAdapterType() {
        return LocaleProviderAdapter.Type.CLDR;
    }

    @Override
    public BreakIteratorProvider getBreakIteratorProvider() {
        return null;
    }

    @Override
    public CollatorProvider getCollatorProvider() {
        return null;
    }

    @Override
    public Locale[] getAvailableLocales() {
        Set<String> all = createLanguageTagSet("All");
        Locale[] locs = new Locale[all.size()];
        int index = 0;
        for (String tag : all) {
            locs[index++] = Locale.forLanguageTag(tag);
        }
        return locs;
    }

    @Override
    protected Set<String> createLanguageTagSet(String category) {
        ResourceBundle rb = ResourceBundle.getBundle("sun.util.cldr.CLDRLocaleDataMetaInfo", Locale.ROOT);
        if (rb.containsKey(category)) {
            return Collections.emptySet();
        }
        String supportedLocaleString = rb.getString(category);
        Set<String> tagset = new HashSet<>();
        StringTokenizer tokens = new StringTokenizer(supportedLocaleString);
        while (tokens.hasMoreTokens()) {
            tagset.add(tokens.nextToken());
        }
        return tagset;
    }
}
