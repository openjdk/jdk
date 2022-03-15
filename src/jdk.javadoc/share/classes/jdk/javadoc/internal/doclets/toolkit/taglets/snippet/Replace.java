/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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

package jdk.javadoc.internal.doclets.toolkit.taglets.snippet;

import java.util.ArrayList;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An action that replaces characters in text.
 *
 * <p><b>This is NOT part of any supported API.
 * If you write code that depends on this, you do so at your own risk.
 * This code and its internal interfaces are subject to change or
 * deletion without notice.</b>
 */
public final class Replace implements Action {

    private final Pattern pattern;
    private final String replacement;
    private final StyledText text;

    /**
     * Constructs an action that replaces regex finds in text.
     *
     * @param replacement the replacement string
     * @param pattern the regex used to search the text
     * @param text the text
     */
    public Replace(String replacement, Pattern pattern, StyledText text) {
        this.replacement = replacement;
        this.pattern = pattern;
        this.text = text;
    }

    @Override
    public void perform() {
        record Replacement(int start, int end, String value) { }
        // until JDK-8261619 is resolved, translating replacements requires some
        // amount of waste and careful index manipulation
        String textString = text.asCharSequence().toString();
        Matcher matcher = pattern.matcher(textString);
        var replacements = new ArrayList<Replacement>();
        StringBuilder b = new StringBuilder();
        int off = 0; // cumulative offset caused by replacements (can become negative)
        while (matcher.find()) {
            int start = matcher.start();
            int end = matcher.end();
            // replacements are computed as they may have special symbols
            matcher.appendReplacement(b, replacement);
            String s = b.substring(start + off);
            off = b.length() - end;
            replacements.add(new Replacement(start, end, s));
        }
        // there's no need to call matcher.appendTail(b)
        for (int i = replacements.size() - 1; i >= 0; i--) {
            Replacement r = replacements.get(i);
            text.subText(r.start(), r.end()).replace(Set.of(), r.value());
        }
    }
}
