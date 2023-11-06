/*
 * Copyright (c) 2012, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/*
 * FallbackProviderAdapter implementation. Fallback provider serves the
 * following purposes:
 *
 * - Locale data for ROOT, in case CLDR provider is absent.
 * - Locale data for BreakIterator/Collator resources for all locales.
 * - "Gan-nen" support for SimpleDateFormat (provides "FirstYear" for
 *   Japanese locales.
 */
public class FallbackLocaleProviderAdapter extends JRELocaleProviderAdapter {

    /**
     * Returns the type of this LocaleProviderAdapter
     */
    @Override
    public LocaleProviderAdapter.Type getAdapterType() {
        return Type.FALLBACK;
    }

    @Override
    protected Set<String> createLanguageTagSet(String category) {
        return switch (category) {
            case "BreakIteratorInfo", "BreakIteratorRules", "CollationData" -> {
                // ensure to include en-US
                var s = new HashSet<>(super.createLanguageTagSet(category));
                s.add("en-US");
                yield Collections.unmodifiableSet(s);
            }
            case "FormatData" -> Set.of("ja", "und");
            default -> Set.of("und");
        };
    }
}
