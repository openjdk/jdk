/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An action that applies an additional style to text.
 */
public final class AddStyle implements Action {

    private final Style style;
    private final Pattern pattern;
    private final StyledText text;

    /**
     * Constructs an action that applies an additional style to regex finds in
     * text.
     *
     * @param style the style to add (to already existing styles)
     * @param pattern the regex used to search the text
     * @param text the text to search
     */
    public AddStyle(Style style, Pattern pattern, StyledText text) {
        this.style = style;
        this.pattern = pattern;
        this.text = text;
    }

    @Override
    public void perform() {
        var singleStyle = Set.of(style);
        Matcher matcher = pattern.matcher(text.asCharSequence());
        while (matcher.find()) {
            int start = matcher.start();
            int end = matcher.end();
            text.subText(start, end).addStyle(singleStyle);
        }
    }
}
