/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package sun.util.locale;

import java.util.Collections;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Represents the BCP 47 't' extension
 */
public final class TransformedContentExtension extends Extension {

    public static final char SINGLETON = 't';
    private static final Pattern FIELD = Pattern.compile("-?(?<fsep>[a-zA-Z]\\d)-(?<fval>(-?\\w{3,8})+)");
    private final String sourceLang;
    private final SortedSet<Field> fields;

    TransformedContentExtension(String value) throws LocaleSyntaxException {
        super(SINGLETON);

        Matcher m = FIELD.matcher(value);
        if (m.find()) {
            var sourceEnd = m.start();
            int parsed;
            sourceLang = sourceEnd != 0 ? value.substring(0, sourceEnd) : null;
            fields = new TreeSet<>();
            do {
                var f = new Field(m.group("fsep"), m.group("fval"));
                if (!fields.add(f)) {
                    throw new LocaleSyntaxException("Field duplicates for the separator '" +
                            f.fsep() + "' within the Transformed Content extension",
                            m.start() + 1); // +1 for the leading '-' of the duplicated field
                }
                parsed = m.end();
            } while (m.find());

            // check whether the entire value was parsed
            if (parsed != value.length()) {
                throw new LocaleSyntaxException("Unrecognized field '" + value.substring(parsed) + "'", parsed);
            }
        } else {
            sourceLang = value;
            fields = Collections.emptySortedSet();
        }

        // validate source lang
        if (sourceLang != null) {
            var pp = new ParseStatus();
            LanguageTag.parse(sourceLang, pp);
            if (pp.isError()) {
                throw new LocaleSyntaxException("Source language tag is invalid within the t extension: " +
                        sourceLang);
            }
        }

        // set the canonical ID as the value
        var sourceLangStr = sourceLang != null ? sourceLang : "";
        var fieldsStr = fields.stream()
            .map(f -> f.fsep() + "-" + f.fval())
            .collect(Collectors.joining("-"));
        var delim = sourceLangStr.isEmpty() || fieldsStr.isEmpty() ? "" : "-";
        setValue(sourceLangStr + delim + fieldsStr);
    }

    public Optional<String> getSourceLang() {
        return Optional.ofNullable(sourceLang);
    }

    public SortedSet<Field> getFields() {
        return fields;
    }

    /**
     * Record representing a field which consists of a separator and a value (subtags).
     * @param fsep field separator
     * @param fval subtags for the separator. Can contain multiple subtags concatenated with a '-'.
     */
    public record Field(String fsep, String fval) implements Comparable<Field> {
        @Override
        public int compareTo(Field f) {
            return fsep().compareTo(f.fsep());
        }
    }
}
