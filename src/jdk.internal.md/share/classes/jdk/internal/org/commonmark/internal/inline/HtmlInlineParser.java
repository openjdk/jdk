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
import jdk.internal.org.commonmark.node.HtmlInline;
import jdk.internal.org.commonmark.parser.beta.Position;
import jdk.internal.org.commonmark.parser.beta.Scanner;

/**
 * Attempt to parse inline HTML.
 */
public class HtmlInlineParser implements InlineContentParser {

    private static final AsciiMatcher asciiLetter = AsciiMatcher.builder().range('A', 'Z').range('a', 'z').build();

    // spec: A tag name consists of an ASCII letter followed by zero or more ASCII letters, digits, or hyphens (-).
    private static final AsciiMatcher tagNameStart = asciiLetter;
    private static final AsciiMatcher tagNameContinue = tagNameStart.newBuilder().range('0', '9').c('-').build();

    // spec: An attribute name consists of an ASCII letter, _, or :, followed by zero or more ASCII letters, digits,
    // _, ., :, or -. (Note: This is the XML specification restricted to ASCII. HTML5 is laxer.)
    private static final AsciiMatcher attributeStart = asciiLetter.newBuilder().c('_').c(':').build();
    private static final AsciiMatcher attributeContinue = attributeStart.newBuilder().range('0', '9').c('.').c('-').build();
    // spec: An unquoted attribute value is a nonempty string of characters not including whitespace, ", ', =, <, >, or `.
    private static final AsciiMatcher attributeValueEnd = AsciiMatcher.builder()
            .c(' ').c('\t').c('\n').c('\u000B').c('\f').c('\r')
            .c('"').c('\'').c('=').c('<').c('>').c('`')
            .build();

    @Override
    public ParsedInline tryParse(InlineParserState inlineParserState) {
        Scanner scanner = inlineParserState.scanner();
        Position start = scanner.position();
        // Skip over `<`
        scanner.next();

        char c = scanner.peek();
        if (tagNameStart.matches(c)) {
            if (tryOpenTag(scanner)) {
                return htmlInline(start, scanner);
            }
        } else if (c == '/') {
            if (tryClosingTag(scanner)) {
                return htmlInline(start, scanner);
            }
        } else if (c == '?') {
            if (tryProcessingInstruction(scanner)) {
                return htmlInline(start, scanner);
            }
        } else if (c == '!') {
            // comment, declaration or CDATA
            scanner.next();
            c = scanner.peek();
            if (c == '-') {
                if (tryComment(scanner)) {
                    return htmlInline(start, scanner);
                }
            } else if (c == '[') {
                if (tryCdata(scanner)) {
                    return htmlInline(start, scanner);
                }
            } else if (asciiLetter.matches(c)) {
                if (tryDeclaration(scanner)) {
                    return htmlInline(start, scanner);
                }
            }
        }

        return ParsedInline.none();
    }

    private static ParsedInline htmlInline(Position start, Scanner scanner) {
        String text = scanner.getSource(start, scanner.position()).getContent();
        HtmlInline node = new HtmlInline();
        node.setLiteral(text);
        return ParsedInline.of(node, scanner.position());
    }

    private static boolean tryOpenTag(Scanner scanner) {
        // spec: An open tag consists of a < character, a tag name, zero or more attributes, optional whitespace,
        // an optional / character, and a > character.
        scanner.next();
        scanner.match(tagNameContinue);
        boolean whitespace = scanner.whitespace() >= 1;
        // spec: An attribute consists of whitespace, an attribute name, and an optional attribute value specification.
        while (whitespace && scanner.match(attributeStart) >= 1) {
            scanner.match(attributeContinue);
            // spec: An attribute value specification consists of optional whitespace, a = character,
            // optional whitespace, and an attribute value.
            whitespace = scanner.whitespace() >= 1;
            if (scanner.next('=')) {
                scanner.whitespace();
                char valueStart = scanner.peek();
                if (valueStart == '\'') {
                    scanner.next();
                    if (scanner.find('\'') < 0) {
                        return false;
                    }
                    scanner.next();
                } else if (valueStart == '"') {
                    scanner.next();
                    if (scanner.find('"') < 0) {
                        return false;
                    }
                    scanner.next();
                } else {
                    if (scanner.find(attributeValueEnd) <= 0) {
                        return false;
                    }
                }

                // Whitespace is required between attributes
                whitespace = scanner.whitespace() >= 1;
            }
        }

        scanner.next('/');
        return scanner.next('>');
    }

    private static boolean tryClosingTag(Scanner scanner) {
        // spec: A closing tag consists of the string </, a tag name, optional whitespace, and the character >.
        scanner.next();
        if (scanner.match(tagNameStart) >= 1) {
            scanner.match(tagNameContinue);
            scanner.whitespace();
            return scanner.next('>');
        }
        return false;
    }

    private static boolean tryProcessingInstruction(Scanner scanner) {
        // spec: A processing instruction consists of the string <?, a string of characters not including the string ?>,
        // and the string ?>.
        scanner.next();
        while (scanner.find('?') > 0) {
            scanner.next();
            if (scanner.next('>')) {
                return true;
            }
        }
        return false;
    }

    private static boolean tryComment(Scanner scanner) {
        // spec: An [HTML comment](@) consists of `<!-->`, `<!--->`, or  `<!--`, a string of
        // characters not including the string `-->`, and `-->` (see the
        // [HTML spec](https://html.spec.whatwg.org/multipage/parsing.html#markup-declaration-open-state)).

        // Skip first `-`
        scanner.next();
        if (!scanner.next('-')) {
            return false;
        }

        if (scanner.next('>') || scanner.next("->")) {
            return true;
        }

        while (scanner.find('-') >= 0) {
            if (scanner.next("-->")) {
                return true;
            } else {
                scanner.next();
            }
        }

        return false;
    }

    private static boolean tryCdata(Scanner scanner) {
        // spec: A CDATA section consists of the string <![CDATA[, a string of characters not including the string ]]>,
        // and the string ]]>.

        // Skip `[`
        scanner.next();

        if (scanner.next("CDATA[")) {
            while (scanner.find(']') >= 0) {
                if (scanner.next("]]>")) {
                    return true;
                } else {
                    scanner.next();
                }
            }
        }

        return false;
    }

    private static boolean tryDeclaration(Scanner scanner) {
        // spec: A declaration consists of the string <!, an ASCII letter, zero or more characters not including
        // the character >, and the character >.
        scanner.match(asciiLetter);
        if (scanner.whitespace() <= 0) {
            return false;
        }
        if (scanner.find('>') >= 0) {
            scanner.next();
            return true;
        }
        return false;
    }
}
