/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
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

package jdk.nashorn.internal.parser;

import static jdk.nashorn.internal.parser.TokenType.COLON;
import static jdk.nashorn.internal.parser.TokenType.COMMARIGHT;
import static jdk.nashorn.internal.parser.TokenType.EOF;
import static jdk.nashorn.internal.parser.TokenType.ESCSTRING;
import static jdk.nashorn.internal.parser.TokenType.RBRACE;
import static jdk.nashorn.internal.parser.TokenType.RBRACKET;
import static jdk.nashorn.internal.parser.TokenType.STRING;

import java.util.ArrayList;
import java.util.List;
import jdk.nashorn.internal.ir.Expression;
import jdk.nashorn.internal.ir.LiteralNode;
import jdk.nashorn.internal.ir.Node;
import jdk.nashorn.internal.ir.ObjectNode;
import jdk.nashorn.internal.ir.PropertyNode;
import jdk.nashorn.internal.ir.UnaryNode;
import jdk.nashorn.internal.runtime.ErrorManager;
import jdk.nashorn.internal.runtime.Source;

/**
 * Parses JSON text and returns the corresponding IR node. This is derived from the objectLiteral production of the main parser.
 *
 * See: 15.12.1.2 The JSON Syntactic Grammar
 */
public class JSONParser extends AbstractParser {

    /**
     * Constructor
     * @param source  the source
     * @param errors  the error manager
     */
    public JSONParser(final Source source, final ErrorManager errors) {
        super(source, errors, false);
    }

    /**
     * Implementation of the Quote(value) operation as defined in the ECMA script spec
     * It wraps a String value in double quotes and escapes characters within in
     *
     * @param value string to quote
     *
     * @return quoted and escaped string
     */
    public static String quote(final String value) {

        final StringBuilder product = new StringBuilder();

        product.append("\"");

        for (final char ch : value.toCharArray()) {
            // TODO: should use a table?
            switch (ch) {
            case '\\':
                product.append("\\\\");
                break;
            case '"':
                product.append("\\\"");
                break;
            case '\b':
                product.append("\\b");
                break;
            case '\f':
                product.append("\\f");
                break;
            case '\n':
                product.append("\\n");
                break;
            case '\r':
                product.append("\\r");
                break;
            case '\t':
                product.append("\\t");
                break;
            default:
                if (ch < ' ') {
                    product.append(Lexer.unicodeEscape(ch));
                    break;
                }

                product.append(ch);
                break;
            }
        }

        product.append("\"");

        return product.toString();
    }

    /**
     * Public parsed method - start lexing a new token stream for
     * a JSON script
     *
     * @return the JSON literal
     */
    public Node parse() {
        stream = new TokenStream();

        lexer = new Lexer(source, stream) {

            @Override
            protected boolean skipComments() {
                return false;
            }

            @Override
            protected boolean isStringDelimiter(final char ch) {
                return ch == '\"';
            }

            // ECMA 15.12.1.1 The JSON Lexical Grammar - JSONWhiteSpace
            @Override
            protected boolean isWhitespace(final char ch) {
                return Lexer.isJsonWhitespace(ch);
            }

            @Override
            protected boolean isEOL(final char ch) {
                return Lexer.isJsonEOL(ch);
            }

            // ECMA 15.12.1.1 The JSON Lexical Grammar - JSONNumber
            @Override
            protected void scanNumber() {
                // Record beginning of number.
                final int startPosition = position;
                // Assume value is a decimal.
                TokenType valueType = TokenType.DECIMAL;

                // floating point can't start with a "." with no leading digit before
                if (ch0 == '.') {
                    error(Lexer.message("json.invalid.number"), STRING, position, limit);
                }

                // First digit of number.
                int digit = convertDigit(ch0, 10);

                // skip first digit
                skip(1);

                if (digit != 0) {
                    // Skip over remaining digits.
                    while (convertDigit(ch0, 10) != -1) {
                        skip(1);
                    }
                }

                if (ch0 == '.' || ch0 == 'E' || ch0 == 'e') {
                    // Must be a double.
                    if (ch0 == '.') {
                        // Skip period.
                        skip(1);

                        boolean mantissa = false;
                        // Skip mantissa.
                        while (convertDigit(ch0, 10) != -1) {
                            mantissa = true;
                            skip(1);
                        }

                        if (! mantissa) {
                            // no digit after "."
                            error(Lexer.message("json.invalid.number"), STRING, position, limit);
                        }
                    }

                    // Detect exponent.
                    if (ch0 == 'E' || ch0 == 'e') {
                        // Skip E.
                        skip(1);
                        // Detect and skip exponent sign.
                        if (ch0 == '+' || ch0 == '-') {
                            skip(1);
                        }
                        boolean exponent = false;
                        // Skip exponent.
                        while (convertDigit(ch0, 10) != -1) {
                            exponent = true;
                            skip(1);
                        }

                        if (! exponent) {
                            // no digit after "E"
                            error(Lexer.message("json.invalid.number"), STRING, position, limit);
                        }
                    }

                    valueType = TokenType.FLOATING;
                }

                // Add number token.
                add(valueType, startPosition);
            }

            // ECMA 15.12.1.1 The JSON Lexical Grammar - JSONEscapeCharacter
            @Override
            protected boolean isEscapeCharacter(final char ch) {
                switch (ch) {
                    case '"':
                    case '/':
                    case '\\':
                    case 'b':
                    case 'f':
                    case 'n':
                    case 'r':
                    case 't':
                    // could be unicode escape
                    case 'u':
                        return true;
                    default:
                        return false;
                }
            }
        };

        k = -1;

        next();

        final Node resultNode = jsonLiteral();
        expect(EOF);

        return resultNode;
    }

    @SuppressWarnings("fallthrough")
    private LiteralNode<?> getStringLiteral() {
        final LiteralNode<?> literal = getLiteral();
        final String         str     = (String)literal.getValue();

        for (int i = 0; i < str.length(); i++) {
            final char ch = str.charAt(i);
            switch (ch) {
            default:
                if (ch > 0x001f) {
                    break;
                }
            case '"':
            case '\\':
                throw error(AbstractParser.message("unexpected.token", str));
            }
        }

        return literal;
    }

    /**
     * Parse a JSON literal from the token stream
     * @return the JSON literal as a Node
     */
    private Expression jsonLiteral() {
        final long literalToken = token;

        switch (type) {
        case STRING:
            return getStringLiteral();
        case ESCSTRING:
        case DECIMAL:
        case FLOATING:
            return getLiteral();
        case FALSE:
            next();
            return LiteralNode.newInstance(literalToken, finish, false);
        case TRUE:
            next();
            return LiteralNode.newInstance(literalToken, finish, true);
        case NULL:
            next();
            return LiteralNode.newInstance(literalToken, finish);
        case LBRACKET:
            return arrayLiteral();
        case LBRACE:
            return objectLiteral();
        /*
         * A.8.1 JSON Lexical Grammar
         *
         * JSONNumber :: See 15.12.1.1
         *    -opt DecimalIntegerLiteral JSONFractionopt ExponentPartopt
         */
        case SUB:
            next();

            final long realToken = token;
            final Object value = getValue();

            if (value instanceof Number) {
                next();
                return new UnaryNode(literalToken, LiteralNode.newInstance(realToken, finish, (Number)value));
            }

            throw error(AbstractParser.message("expected", "number", type.getNameOrType()));
        default:
            break;
        }

        throw error(AbstractParser.message("expected", "json literal", type.getNameOrType()));
    }

    /**
     * Parse an array literal from the token stream
     * @return the array literal as a Node
     */
    private LiteralNode<Expression[]> arrayLiteral() {
        // Unlike JavaScript array literals, elison is not permitted in JSON.

        // Capture LBRACKET token.
        final long arrayToken = token;
        // LBRACKET tested in caller.
        next();

        LiteralNode<Expression[]> result = null;
        // Prepare to accummulating elements.
        final List<Expression> elements = new ArrayList<>();

loop:
        while (true) {
            switch (type) {
            case RBRACKET:
                next();
                result = LiteralNode.newInstance(arrayToken, finish, elements);
                break loop;

            case COMMARIGHT:
                next();
                break;

            default:
                // Add expression element.
                elements.add(jsonLiteral());
                // Comma between array elements is mandatory in JSON.
                if (type != COMMARIGHT && type != RBRACKET) {
                   throw error(AbstractParser.message("expected", ", or ]", type.getNameOrType()));
                }
                break;
            }
        }

        return result;
    }

    /**
     * Parse an object literal from the token stream
     * @return the object literal as a Node
     */
    private ObjectNode objectLiteral() {
        // Capture LBRACE token.
        final long objectToken = token;
        // LBRACE tested in caller.
        next();

        // Prepare to accumulate elements.
        final List<PropertyNode> elements = new ArrayList<>();

        // Create a block for the object literal.
loop:
        while (true) {
            switch (type) {
            case RBRACE:
                next();
                break loop;

            case COMMARIGHT:
                next();
                break;

            default:
                // Get and add the next property.
                final PropertyNode property = propertyAssignment();
                elements.add(property);

                // Comma between property assigments is mandatory in JSON.
                if (type != RBRACE && type != COMMARIGHT) {
                    throw error(AbstractParser.message("expected", ", or }", type.getNameOrType()));
                }
                break;
            }
        }

        // Construct new object literal.
        return new ObjectNode(objectToken, finish, elements);
    }

    /**
     * Parse a property assignment from the token stream
     * @return the property assignment as a Node
     */
    private PropertyNode propertyAssignment() {
        // Capture firstToken.
        final long propertyToken = token;
        LiteralNode<?> name = null;

        if (type == STRING) {
            name = getStringLiteral();
        } else if (type == ESCSTRING) {
            name = getLiteral();
        }

        if (name != null) {
            expect(COLON);
            final Expression value = jsonLiteral();
            return new PropertyNode(propertyToken, value.getFinish(), name, value, null, null);
        }

        // Raise an error.
        throw error(AbstractParser.message("expected", "string", type.getNameOrType()));
    }

}
