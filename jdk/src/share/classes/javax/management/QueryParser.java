/*
 * Copyright 2008 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package javax.management;

import java.util.ArrayList;
import java.util.Formatter;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * <p>Parser for JMX queries represented in an SQL-like language.</p>
 */
/*
 * Note that if a query starts with ( then we don't know whether it is
 * a predicate or just a value that is parenthesized.  So, inefficiently,
 * we try to parse a predicate and if that doesn't work we try to parse
 * a value.
 */
class QueryParser {
    // LEXER STARTS HERE

    private static class Token {
        final String string;
        Token(String s) {
            this.string = s;
        }

        @Override
        public String toString() {
            return string;
        }
    }

    private static final Token
            END = new Token("<end of string>"),
            LPAR = new Token("("), RPAR = new Token(")"),
            COMMA = new Token(","), DOT = new Token("."), SHARP = new Token("#"),
            PLUS = new Token("+"), MINUS = new Token("-"),
            TIMES = new Token("*"), DIVIDE = new Token("/"),
            LT = new Token("<"), GT = new Token(">"),
            LE = new Token("<="), GE = new Token(">="),
            NE = new Token("<>"), EQ = new Token("="),
            NOT = new Id("NOT"), INSTANCEOF = new Id("INSTANCEOF"),
            FALSE = new Id("FALSE"), TRUE = new Id("TRUE"),
            BETWEEN = new Id("BETWEEN"), AND = new Id("AND"),
            OR = new Id("OR"), IN = new Id("IN"),
            LIKE = new Id("LIKE"), CLASS = new Id("CLASS");

    // Keywords that can appear where an identifier can appear.
    // If an attribute is one of these, then it must be quoted when
    // converting a query into a string.
    // We use a TreeSet so we can look up case-insensitively.
    private static final Set<String> idKeywords =
            new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
    static {
        for (Token t : new Token[] {NOT, INSTANCEOF, FALSE, TRUE, LIKE, CLASS})
            idKeywords.add(t.string);
    };

    public static String quoteId(String id) {
        if (id.contains("\"") || idKeywords.contains(id))
            return '"' + id.replace("\"", "\"\"") + '"';
        else
            return id;
    }

    private static class Id extends Token {
        Id(String id) {
            super(id);
        }

        // All other tokens use object identity, which means e.g. that one
        // occurrence of the string constant 'x' is not the same as another.
        // For identifiers, we ignore case when testing for equality so that
        // for a keyword such as AND you can also spell it as "And" or "and".
        // But we keep the original case of the identifier, so if it's not
        // a keyword we will distinguish between the attribute Foo and the
        // attribute FOO.
        @Override
        public boolean equals(Object o) {
            return (o instanceof Id && (((Id) o).toString().equalsIgnoreCase(toString())));
        }
    }

    private static class QuotedId extends Token {
        QuotedId(String id) {
            super(id);
        }

        @Override
        public String toString() {
            return '"' + string.replace("\"", "\"\"") + '"';
        }
    }

    private static class StringLit extends Token {
        StringLit(String s) {
            super(s);
        }

        @Override
        public String toString() {
            return '\'' + string.replace("'", "''") + '\'';
        }
    }

    private static class LongLit extends Token {
        long number;

        LongLit(long number) {
            super(Long.toString(number));
            this.number = number;
        }
    }

    private static class DoubleLit extends Token {
        double number;

        DoubleLit(double number) {
            super(Double.toString(number));
            this.number = number;
        }
    }

    private static class Tokenizer {
        private final String s;
        private final int len;
        private int i = 0;

        Tokenizer(String s) {
            this.s = s;
            this.len = s.length();
        }

        private int thisChar() {
            if (i == len)
                return -1;
            return s.codePointAt(i);
        }

        private void advance() {
            i += Character.charCount(thisChar());
        }

        private int thisCharAdvance() {
            int c = thisChar();
            advance();
            return c;
        }

        Token nextToken() {
            // In this method, c is the character we're looking at, and
            // thisChar() is the character after that.  Everything must
            // preserve these invariants.  When we return we then have
            // thisChar() being the start of the following token, so
            // the next call to nextToken() will begin from there.
            int c;

            // Skip space
            do {
                if (i == len)
                    return null;
                c = thisCharAdvance();
            } while (Character.isWhitespace(c));

            // Now c is the first character of the token, and tokenI points
            // to the character after that.
            switch (c) {
                case '(': return LPAR;
                case ')': return RPAR;
                case ',': return COMMA;
                case '.': return DOT;
                case '#': return SHARP;
                case '*': return TIMES;
                case '/': return DIVIDE;
                case '=': return EQ;
                case '-': return MINUS;
                case '+': return PLUS;

                case '>':
                    if (thisChar() == '=') {
                        advance();
                        return GE;
                    } else
                        return GT;

                case '<':
                    c = thisChar();
                    switch (c) {
                        case '=': advance(); return LE;
                        case '>': advance(); return NE;
                        default: return LT;
                    }

                case '!':
                    if (thisCharAdvance() != '=')
                        throw new IllegalArgumentException("'!' must be followed by '='");
                    return NE;

                case '"':
                case '\'': {
                    int quote = c;
                    StringBuilder sb = new StringBuilder();
                    while (true) {
                        while ((c = thisChar()) != quote) {
                            if (c < 0) {
                                throw new IllegalArgumentException(
                                        "Unterminated string constant");
                            }
                            sb.appendCodePoint(thisCharAdvance());
                        }
                        advance();
                        if (thisChar() == quote) {
                            sb.appendCodePoint(quote);
                            advance();
                        } else
                            break;
                    }
                    if (quote == '\'')
                        return new StringLit(sb.toString());
                    else
                        return new QuotedId(sb.toString());
                }
            }

            // Is it a numeric constant?
            if (Character.isDigit(c) || c == '.') {
                StringBuilder sb = new StringBuilder();
                int lastc = -1;
                while (true) {
                    sb.appendCodePoint(c);
                    c = Character.toLowerCase(thisChar());
                    if (c == '+' || c == '-') {
                        if (lastc != 'e')
                            break;
                    } else if (!Character.isDigit(c) && c != '.' && c != 'e')
                        break;
                    lastc = c;
                    advance();
                }
                String s = sb.toString();
                if (s.indexOf('.') >= 0 || s.indexOf('e') >= 0) {
                    double d = parseDoubleCheckOverflow(s);
                    return new DoubleLit(d);
                } else {
                    // Like the Java language, we allow the numeric constant
                    // x where -x = Long.MIN_VALUE, even though x is not
                    // representable as a long (it is Long.MAX_VALUE + 1).
                    // Code in the parser will reject this value if it is
                    // not the operand of unary minus.
                    long l = -Long.parseLong("-" + s);
                    return new LongLit(l);
                }
            }

            // It must be an identifier.
            if (!Character.isJavaIdentifierStart(c)) {
                StringBuilder sb = new StringBuilder();
                Formatter f = new Formatter(sb);
                f.format("Bad character: %c (%04x)", c, c);
                throw new IllegalArgumentException(sb.toString());
            }

            StringBuilder id = new StringBuilder();
            while (true) { // identifier
                id.appendCodePoint(c);
                c = thisChar();
                if (!Character.isJavaIdentifierPart(c))
                    break;
                advance();
            }

            return new Id(id.toString());
        }
    }

    /* Parse a double as a Java compiler would do it, throwing an exception
     * if the input does not fit in a double.  We assume that the input
     * string is not "Infinity" and does not have a leading sign.
     */
    private static double parseDoubleCheckOverflow(String s) {
        double d = Double.parseDouble(s);
        if (Double.isInfinite(d))
            throw new NumberFormatException("Overflow: " + s);
        if (d == 0.0) {  // Underflow checking is hard!  CR 6604864
            String ss = s;
            int e = s.indexOf('e');  // we already forced E to lowercase
            if (e > 0)
                ss = s.substring(0, e);
            ss = ss.replace("0", "").replace(".", "");
            if (!ss.isEmpty())
                throw new NumberFormatException("Underflow: " + s);
        }
        return d;
    }

    // PARSER STARTS HERE

    private final List<Token> tokens;
    private int tokenI;
    // The current token is always tokens[tokenI].

    QueryParser(String s) {
        // Construct the complete list of tokens immediately and append
        // a sentinel (END).
        tokens = new ArrayList<Token>();
        Tokenizer tokenizer = new Tokenizer(s);
        Token t;
        while ((t = tokenizer.nextToken()) != null)
            tokens.add(t);
        tokens.add(END);
    }

    private Token current() {
        return tokens.get(tokenI);
    }

    // If the current token is t, then skip it and return true.
    // Otherwise, return false.
    private boolean skip(Token t) {
        if (t.equals(current())) {
            tokenI++;
            return true;
        }
        return false;
    }

    // If the current token is one of the ones in 'tokens', then skip it
    // and return its index in 'tokens'.  Otherwise, return -1.
    private int skipOne(Token... tokens) {
        for (int i = 0; i < tokens.length; i++) {
            if (skip(tokens[i]))
                return i;
        }
        return -1;
    }

    // If the current token is t, then skip it and return.
    // Otherwise throw an exception.
    private void expect(Token t) {
        if (!skip(t))
            throw new IllegalArgumentException("Expected " + t + ", found " + current());
    }

    private void next() {
        tokenI++;
    }

    QueryExp parseQuery() {
        QueryExp qe = query();
        if (current() != END)
            throw new IllegalArgumentException("Junk at end of query: " + current());
        return qe;
    }

    // The remainder of this class is a classical recursive-descent parser.
    // We only need to violate the recursive-descent scheme in one place,
    // where parentheses make the grammar not LL(1).

    private QueryExp query() {
        QueryExp lhs = andquery();
        while (skip(OR))
            lhs = Query.or(lhs, andquery());
        return lhs;
    }

    private QueryExp andquery() {
        QueryExp lhs = predicate();
        while (skip(AND))
            lhs = Query.and(lhs, predicate());
        return lhs;
    }

    private QueryExp predicate() {
        // Grammar hack.  If we see a paren, it might be (query) or
        // it might be (value).  We try to parse (query), and if that
        // fails, we parse (value).  For example, if the string is
        // "(2+3)*4 < 5" then we will try to parse the query
        // "2+3)*4 < 5", which will fail at the ), so we'll back up to
        // the paren and let value() handle it.
        if (skip(LPAR)) {
            int parenIndex = tokenI - 1;
            try {
                QueryExp qe = query();
                expect(RPAR);
                return qe;
            } catch (IllegalArgumentException e) {
                // OK: try parsing a value
            }
            tokenI = parenIndex;
        }

        if (skip(NOT))
            return Query.not(predicate());

        if (skip(INSTANCEOF))
            return Query.isInstanceOf(stringvalue());

        if (skip(LIKE)) {
            StringValueExp sve = stringvalue();
            String s = sve.getValue();
            try {
                return new ObjectName(s);
            } catch (MalformedObjectNameException e) {
                throw new IllegalArgumentException(
                        "Bad ObjectName pattern after LIKE: '" + s + "'", e);
            }
        }

        ValueExp lhs = value();

        return predrhs(lhs);
    }

    // The order of elements in the following arrays is important.  The code
    // in predrhs depends on integer indexes.  Change with caution.
    private static final Token[] relations = {
            EQ, LT, GT, LE, GE, NE,
         // 0,  1,  2,  3,  4,  5,
    };
    private static final Token[] betweenLikeIn = {
            BETWEEN, LIKE, IN
         // 0,       1,    2,
    };

    private QueryExp predrhs(ValueExp lhs) {
        Token start = current(); // for errors

        // Look for < > = etc
        int i = skipOne(relations);
        if (i >= 0) {
            ValueExp rhs = value();
            switch (i) {
                case 0: return Query.eq(lhs, rhs);
                case 1: return Query.lt(lhs, rhs);
                case 2: return Query.gt(lhs, rhs);
                case 3: return Query.leq(lhs, rhs);
                case 4: return Query.geq(lhs, rhs);
                case 5: return Query.not(Query.eq(lhs, rhs));
                // There is no Query.ne so <> is shorthand for the above.
                default:
                    throw new AssertionError();
            }
        }

        // Must be BETWEEN LIKE or IN, optionally preceded by NOT
        boolean not = skip(NOT);
        i = skipOne(betweenLikeIn);
        if (i < 0)
            throw new IllegalArgumentException("Expected relation at " + start);

        QueryExp q;
        switch (i) {
            case 0: { // BETWEEN
                ValueExp lower = value();
                expect(AND);
                ValueExp upper = value();
                q = Query.between(lhs, lower, upper);
                break;
            }

            case 1: { // LIKE
                if (!(lhs instanceof AttributeValueExp)) {
                    throw new IllegalArgumentException(
                            "Left-hand side of LIKE must be an attribute");
                }
                AttributeValueExp alhs = (AttributeValueExp) lhs;
                StringValueExp sve = stringvalue();
                String s = sve.getValue();
                q = Query.match(alhs, patternValueExp(s));
                break;
            }

            case 2: { // IN
                expect(LPAR);
                List<ValueExp> values = new ArrayList<ValueExp>();
                values.add(value());
                while (skip(COMMA))
                    values.add(value());
                expect(RPAR);
                q = Query.in(lhs, values.toArray(new ValueExp[values.size()]));
                break;
            }

            default:
                throw new AssertionError();
        }

        if (not)
            q = Query.not(q);

        return q;
    }

    private ValueExp value() {
        ValueExp lhs = factor();
        int i;
        while ((i = skipOne(PLUS, MINUS)) >= 0) {
            ValueExp rhs = factor();
            if (i == 0)
                lhs = Query.plus(lhs, rhs);
            else
                lhs = Query.minus(lhs, rhs);
        }
        return lhs;
    }

    private ValueExp factor() {
        ValueExp lhs = term();
        int i;
        while ((i = skipOne(TIMES, DIVIDE)) >= 0) {
            ValueExp rhs = term();
            if (i == 0)
                lhs = Query.times(lhs, rhs);
            else
                lhs = Query.div(lhs, rhs);
        }
        return lhs;
    }

    private ValueExp term() {
        boolean signed = false;
        int sign = +1;
        if (skip(PLUS))
            signed = true;
        else if (skip(MINUS)) {
            signed = true; sign = -1;
        }

        Token t = current();
        next();

        if (t instanceof DoubleLit)
            return Query.value(sign * ((DoubleLit) t).number);
        if (t instanceof LongLit) {
            long n = ((LongLit) t).number;
            if (n == Long.MIN_VALUE && sign != -1)
                throw new IllegalArgumentException("Illegal positive integer: " + n);
            return Query.value(sign * n);
        }
        if (signed)
            throw new IllegalArgumentException("Expected number after + or -");

        if (t == LPAR) {
            ValueExp v = value();
            expect(RPAR);
            return v;
        }
        if (t.equals(FALSE) || t.equals(TRUE)) {
            return Query.value(t.equals(TRUE));
        }
        if (t.equals(CLASS))
            return Query.classattr();

        if (t instanceof StringLit)
            return Query.value(t.string); // Not toString(), which would requote '

        // At this point, all that remains is something that will call Query.attr

        if (!(t instanceof Id) && !(t instanceof QuotedId))
            throw new IllegalArgumentException("Unexpected token " + t);

        String name1 = name(t);

        if (skip(SHARP)) {
            Token t2 = current();
            next();
            String name2 = name(t2);
            return Query.attr(name1, name2);
        }
        return Query.attr(name1);
    }

    // Initially, t is the first token of a supposed name and current()
    // is the second.
    private String name(Token t) {
        StringBuilder sb = new StringBuilder();
        while (true) {
            if (!(t instanceof Id) && !(t instanceof QuotedId))
                throw new IllegalArgumentException("Unexpected token " + t);
            sb.append(t.string);
            if (current() != DOT)
                break;
            sb.append('.');
            next();
            t = current();
            next();
        }
        return sb.toString();
    }

    private StringValueExp stringvalue() {
        // Currently the only way to get a StringValueExp when constructing
        // a QueryExp is via Query.value(String), so we only recognize
        // string literals here.  But if we expand queries in the future
        // that might no longer be true.
        Token t = current();
        next();
        if (!(t instanceof StringLit))
            throw new IllegalArgumentException("Expected string: " + t);
        return Query.value(t.string);
    }

    // Convert the SQL pattern syntax, using % and _, to the Query.match
    // syntax, using * and ?.  The tricky part is recognizing \% and
    // \_ as literal values, and also not replacing them inside [].
    // But Query.match does not recognize \ inside [], which makes our
    // job a tad easier.
    private StringValueExp patternValueExp(String s) {
        int c;
        for (int i = 0; i < s.length(); i += Character.charCount(c)) {
            c = s.codePointAt(i);
            switch (c) {
                case '\\':
                    i++;  // i += Character.charCount(c), but we know it's 1!
                    if (i >= s.length())
                        throw new IllegalArgumentException("\\ at end of pattern");
                    break;
                case '[':
                    i = s.indexOf(']', i);
                    if (i < 0)
                        throw new IllegalArgumentException("[ without ]");
                    break;
                case '%':
                    s = s.substring(0, i) + "*" + s.substring(i + 1);
                    break;
                case '_':
                    s = s.substring(0, i) + "?" + s.substring(i + 1);
                    break;
                case '*':
                case '?':
                    s = s.substring(0, i) + '\\' + (char) c + s.substring(i + 1);
                    i++;
                    break;
            }
        }
        return Query.value(s);
    }
}
