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

import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.text.spi.BreakIteratorProvider;
import java.text.spi.CollatorProvider;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.StringTokenizer;
import sun.util.locale.provider.JRELocaleProviderAdapter;
import sun.util.locale.provider.LocaleProviderAdapter;
import sun.util.locale.provider.LocaleDataMetaInfo;

/**
 * LocaleProviderAdapter implementation for the CLDR locale data.
 *
 * @author Masayoshi Okutsu
 * @author Naoto Sato
 */
public class CLDRLocaleProviderAdapter extends JRELocaleProviderAdapter {

    private final LocaleDataMetaInfo metaInfo;

    public CLDRLocaleProviderAdapter() {
        try {
            metaInfo = AccessController.doPrivileged(new PrivilegedExceptionAction<LocaleDataMetaInfo>() {
                    @Override
                public LocaleDataMetaInfo run() {
                    for (LocaleDataMetaInfo ldmi : ServiceLoader.loadInstalled(LocaleDataMetaInfo.class)) {
                        if (ldmi.getType() == LocaleProviderAdapter.Type.CLDR) {
                            return ldmi;
                        }
                    }
                    return null;
                    }
                });
        }  catch (Exception e) {
            // Catch any exception, and fail gracefully as if CLDR locales do not exist.
            // It's ok ignore it if something wrong happens because there always is the
            // JRE or FALLBACK LocaleProviderAdapter that will do the right thing.
            throw new UnsupportedOperationException(e);
        }

        if (metaInfo == null) {
            throw new UnsupportedOperationException("CLDR locale data could not be found.");
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
        Set<String> all = createLanguageTagSet("AvailableLocales");
        Locale[] locs = new Locale[all.size()];
        int index = 0;
        for (String tag : all) {
            locs[index++] = Locale.forLanguageTag(tag);
        }
        return locs;
    }

    @Override
    protected Set<String> createLanguageTagSet(String category) {
        String supportedLocaleString = metaInfo.availableLanguageTags(category);
        if (supportedLocaleString == null) {
            return Collections.emptySet();
        }
        Set<String> tagset = new HashSet<>();
        StringTokenizer tokens = new StringTokenizer(supportedLocaleString);
        while (tokens.hasMoreTokens()) {
            tagset.add(tokens.nextToken());
        }
        return tagset;
    }
}
