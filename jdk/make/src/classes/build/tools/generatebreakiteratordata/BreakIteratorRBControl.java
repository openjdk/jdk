/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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

package build.tools.generatebreakiteratordata;

import java.util.Arrays;
import java.util.ResourceBundle;
import java.util.List;
import java.util.Locale;

class BreakIteratorRBControl extends ResourceBundle.Control {
    static final BreakIteratorRBControl INSTANCE = new BreakIteratorRBControl();

    private static final String RESOURCES = ".resources.";

    private BreakIteratorRBControl() {
    }

    @Override
    public Locale getFallbackLocale(String baseName, Locale locale) {
        // No fallback
        return null;
    }

    @Override
    public List<Locale> getCandidateLocales(String baseName, Locale locale) {
        // No parents lookup
        return Arrays.asList(locale);
    }

    /**
     * Changes baseName to its per-language package name and
     * calls the super class implementation.
     */
    @Override
    public String toBundleName(String baseName, Locale locale) {
        String newBaseName = baseName;
        String lang = locale.getLanguage();
        if (lang.length() > 0) {
            int index = baseName.indexOf(RESOURCES);
            if (index > 0) {
                index += RESOURCES.length();
                newBaseName = baseName.substring(0, index) + lang + "."
                                  + baseName.substring(index);
            }
        }
        return super.toBundleName(newBaseName, locale);
    }
}
