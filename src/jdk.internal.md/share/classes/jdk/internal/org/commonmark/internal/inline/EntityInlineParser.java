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

import jdk.internal.org.commonmark.text.AsciiMatcher;
import jdk.internal.org.commonmark.internal.util.Html5Entities;
import jdk.internal.org.commonmark.node.Text;
import jdk.internal.org.commonmark.parser.beta.Position;
import jdk.internal.org.commonmark.parser.beta.Scanner;

/**
 * Attempts to parse a HTML entity or numeric character reference.
 */
public class EntityInlineParser implements InlineContentParser {

    private static final AsciiMatcher hex = AsciiMatcher.builder().range('0', '9').range('A', 'F').range('a', 'f').build();
    private static final AsciiMatcher dec = AsciiMatcher.builder().range('0', '9').build();
    private static final AsciiMatcher entityStart = AsciiMatcher.builder().range('A', 'Z').range('a', 'z').build();
    private static final AsciiMatcher entityContinue = entityStart.newBuilder().range('0', '9').build();

    @Override
    public ParsedInline tryParse(InlineParserState inlineParserState) {
        Scanner scanner = inlineParserState.scanner();
        Position start = scanner.position();
        // Skip `&`
        scanner.next();

        char c = scanner.peek();
        if (c == '#') {
            // Numeric
            scanner.next();
            if (scanner.next('x') || scanner.next('X')) {
                int digits = scanner.match(hex);
                if (1 <= digits && digits <= 6 && scanner.next(';')) {
                    return entity(scanner, start);
                }
            } else {
                int digits = scanner.match(dec);
                if (1 <= digits && digits <= 7 && scanner.next(';')) {
                    return entity(scanner, start);
                }
            }
        } else if (entityStart.matches(c)) {
            scanner.match(entityContinue);
            if (scanner.next(';')) {
                return entity(scanner, start);
            }
        }

        return ParsedInline.none();
    }

    private ParsedInline entity(Scanner scanner, Position start) {
        String text = scanner.getSource(start, scanner.position()).getContent();
        return ParsedInline.of(new Text(Html5Entities.entityToString(text)), scanner.position());
    }
}
