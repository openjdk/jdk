/*
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

/*
 * This file is available under and governed by the GNU General Public
 * License version 2 only, as published by the Free Software Foundation.
 * However, a notice that is now available elsewhere in this distribution
 * accompanied the original version of this file, and, per its terms,
 * should not be removed.
 */

package jdk.internal.org.commonmark.internal.inline;

import jdk.internal.org.commonmark.internal.util.Escaping;
import jdk.internal.org.commonmark.node.HardLineBreak;
import jdk.internal.org.commonmark.node.Text;
import jdk.internal.org.commonmark.parser.beta.Scanner;

import java.util.regex.Pattern;

/**
 * Parse a backslash-escaped special character, adding either the escaped  character, a hard line break
 * (if the backslash is followed by a newline), or a literal backslash to the block's children.
 */
public class BackslashInlineParser implements InlineContentParser {

    private static final Pattern ESCAPABLE = Pattern.compile('^' + Escaping.ESCAPABLE);

    @Override
    public ParsedInline tryParse(InlineParserState inlineParserState) {
        Scanner scanner = inlineParserState.scanner();
        // Backslash
        scanner.next();

        char next = scanner.peek();
        if (next == '\n') {
            scanner.next();
            return ParsedInline.of(new HardLineBreak(), scanner.position());
        } else if (ESCAPABLE.matcher(String.valueOf(next)).matches()) {
            scanner.next();
            return ParsedInline.of(new Text(String.valueOf(next)), scanner.position());
        } else {
            return ParsedInline.of(new Text("\\"), scanner.position());
        }
    }
}
