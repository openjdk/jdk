/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jfr.internal.query;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import jdk.jfr.internal.query.Configuration.Truncate;
import jdk.jfr.internal.query.Query.Condition;
import jdk.jfr.internal.query.Query.Expression;
import jdk.jfr.internal.query.Query.Formatter;
import jdk.jfr.internal.query.Query.Grouper;
import jdk.jfr.internal.query.Query.OrderElement;
import jdk.jfr.internal.query.Query.Property;
import jdk.jfr.internal.query.Query.SortOrder;
import jdk.jfr.internal.query.Query.Source;
import jdk.jfr.internal.util.Tokenizer;

final class QueryParser implements AutoCloseable {
    static final char[] SEPARATORS = {'=', ',', ';', '(', ')'};

    private final Tokenizer tokenizer;

    public QueryParser(String text) {
        tokenizer = new Tokenizer(text, SEPARATORS);
    }

    public List<String> column() throws ParseException {
        if (!tokenizer.accept("COLUMN")) {
            return List.of();
        }
        List<String> texts = new ArrayList<>();
        texts.add(text());
        while (tokenizer.accept(",")) {
            texts.add(text());
        }
        return texts;
    }

    public List<Formatter> format() throws ParseException {
        if (tokenizer.accept("FORMAT")) {
            List<Formatter> formatters = new ArrayList<>();
            formatters.add(formatter());
            while (tokenizer.accept(",")) {
                formatters.add(formatter());
            }
            return formatters;
        }
        return List.of();
    }

    private Formatter formatter() throws ParseException {
        List<Property> properties = new ArrayList<>();
        properties.add(property());
        while (tokenizer.accept(";")) {
            properties.add(property());
        }
        return new Formatter(properties);
    }

    public List<Expression> select() throws ParseException {
        tokenizer.expect("SELECT");
        if (tokenizer.accept("*")) {
            return List.of();
        }
        List<Expression> expressions = new ArrayList<>();
        if (tokenizer.accept("FROM")) {
            throw new ParseException("Missing fields in SELECT statement", position());
        }
        expressions.add(expression());
        while (tokenizer.accept(",")) {
            Expression exp = expression();
            if (exp.name().equalsIgnoreCase("FROM")) {
                throw new ParseException("Missing field name in SELECT statement, or qualify field with event type if name is called '" + exp.name() + "'", position());
            }
            expressions.add(exp);
        }
        return expressions;
    }

    private Expression expression() throws ParseException {
        Expression aggregator = aggregator();
        if (aggregator != null) {
            return aggregator;
        } else {
            return new Expression(eventField(), alias(), Aggregator.MISSING);
        }
    }

    private Expression aggregator() throws ParseException {
        for (Aggregator function : Aggregator.values()) {
            if (tokenizer.accept(function.name, "(")) {
                String eventField = eventField();
                tokenizer.expect(")");
                return new Expression(eventField, alias(), function);
            }
        }
        return null;
    }

    private Optional<String> alias() throws ParseException {
        Optional<String> alias = Optional.empty();
        if (tokenizer.accept("AS")) {
            alias = Optional.of(symbol());
        }
        return alias;
    }

    public List<Source> from() throws ParseException {
        tokenizer.expect("FROM");
        List<Source> sources = new ArrayList<>();
        sources.add(source());
        while (tokenizer.accept(",")) {
            sources.add(source());
        }
        return sources;
    }

    private Source source() throws ParseException {
        String type = type();
        if (tokenizer.accept("SELECT")) {
            throw new ParseException("Subquery is not allowed", position());
        }
        if (tokenizer.accept("INNER", "JOIN", "LEFT", "RIGHT", "FULL")) {
            throw new ParseException("JOIN is not allowed", position());
        }
        return new Source(type, alias());
    }

    private String type() throws ParseException {
        return tokenizer.next();
    }

    public List<Condition> where() throws ParseException {
        if (tokenizer.accept("WHERE")) {
            List<Condition> conditions = new ArrayList<>();
            conditions.add(condition());
            while (tokenizer.accept("AND")) {
                conditions.add(condition());
            }
            return conditions;
        }
        return List.of();
    }

    private Condition condition() throws ParseException {
        String field = eventField();
        if (tokenizer.acceptAny("<", ">", "<>", ">=", "<=", "==", "BETWEEN", "LIKE", "IN")) {
            throw new ParseException("The only operator allowed in WHERE clause is '='", position());
        }
        tokenizer.expect("=");
        String value = text();
        return new Condition(field, value);
    }

    public List<Grouper> groupBy() throws ParseException {
        if (tokenizer.accept("HAVING")) {
            throw new ParseException("HAVING is not allowed", position());
        }
        if (tokenizer.accept("GROUP")) {
            tokenizer.expect("BY");
            List<Grouper> groupers = new ArrayList<>();
            groupers.add(grouper());
            while (tokenizer.accept(",")) {
                groupers.add(grouper());
            }
            return groupers;
        }
        return new ArrayList<>(); // Need to return mutable collection
    }

    private Grouper grouper() throws ParseException {
        return new Grouper(eventField());
    }

    public List<OrderElement> orderBy() throws ParseException {
        if (tokenizer.accept("ORDER")) {
            tokenizer.expect("BY");
            List<OrderElement> fields = new ArrayList<>();
            fields.add(orderer());
            while (tokenizer.accept(",")) {
                fields.add(orderer());
            }
            return fields;
        }
        return List.of();
    }

    private OrderElement orderer() throws ParseException {
        return new OrderElement(eventField(), sortOrder());
    }

    private SortOrder sortOrder() throws ParseException {
        if (tokenizer.accept("ASC")) {
            return SortOrder.ASCENDING;
        }
        if (tokenizer.accept("DESC")) {
            return SortOrder.DESCENDING;
        }
        return SortOrder.NONE;
    }

    private String text() throws ParseException {
        if (tokenizer.peekChar() != '\'') {
            throw new ParseException("Expected text to start with a single quote character", position());
        }
        return tokenizer.next();
    }

    private String symbol() throws ParseException {
        String s = tokenizer.next();
        for (int index = 0; index < s.length(); index++) {
            int cp = s.codePointAt(index);
            if (!Character.isLetter(cp)) {
                throw new ParseException("Symbol must consist of letters, found '" + s.charAt(index) + "' in '" + s + "'",
                        position());
            }
        }
        return s;
    }

    private String eventField() throws ParseException {
        if (!tokenizer.hasNext()) {
            throw new ParseException("Unexpected end when looking for event field", position());
        }
        if (tokenizer.peekChar() == '\'') {
            throw new ParseException("Expected unquoted symbolic name (not label)", position());
        }
        String name = tokenizer.next();
        if (name.equals("*")) {
            return name;
        }
        for (int index = 0; index < name.length(); index++) {
            char c = name.charAt(index);
            boolean valid = index == 0 ? Character.isJavaIdentifierStart(c) : Character.isJavaIdentifierPart(c);
            if (c != '.' && c != '[' && c!= ']' && c != '|' && !valid) {
                throw new ParseException("Not a valid field name: " + name, position());
            }
        }
        return name;
    }

    private Property property() throws ParseException {
        String text = tokenizer.next();
        Consumer<Field> style = switch (text.toLowerCase()) {
            case "none" -> field -> {};
            case "missing:" -> field -> {};
            case "normalized" -> field -> field.normalized = field.percentage = true;
            case "truncate-beginning" -> field -> field.truncate = Truncate.BEGINNING;
            case "truncate-end" -> field -> field.truncate = Truncate.END;
            default -> {
                if (text.startsWith("missing:")) {
                    yield missing(text.substring("missing:".length()));
                }
                if (text.startsWith("cell-height:")) {
                    yield cellHeight(text.substring("cell-height:".length()));
                }
                // This option is experimental and may not work properly
                // with rounding and truncation.
                if (text.startsWith("ms-precision:")) {
                    yield millisPrecision(text.substring("ms-precision:".length()));
                }
                throw new ParseException("Unknown formatter '" + text + "'", position());
            }
        };
        return new Property(text, style);
    }

    private Consumer<Field> missing(String missing) {
        if ("whitespace".equals(missing)) {
           return field -> field.missingText = "";
        } else {
           return field -> field.missingText = missing;
        }
    }

    private Consumer<Field> cellHeight(String height) throws ParseException {
        try {
            int h = Integer.parseInt(height);
            if (h < 1) {
                throw new ParseException("Expected 'cell-height:' to be at least 1' ", position());
            }
            return field -> field.cellHeight = h;
        } catch (NumberFormatException nfe) {
            throw new ParseException("Not valid number for 'cell-height:' " + height, position());
        }
    }

    private Consumer<Field> millisPrecision(String digits) throws ParseException {
        try {
            int d = Integer.parseInt(digits);
            if (d < 0) {
                throw new ParseException("Expected 'precision:' to be at least 0' ", position());
            }
            return field -> field.precision = d;
        } catch (NumberFormatException nfe) {
            throw new ParseException("Not valid number for 'precision:' " + digits, position());
        }
    }

    public int position() {
        return tokenizer.getPosition();
    }

    public int limit() throws ParseException {
        if (tokenizer.accept("LIMIT")) {
            try {
                if (tokenizer.hasNext()) {
                    String number = tokenizer.next();
                    int limit= Integer.parseInt(number);
                    if (limit < 0) {
                        throw new ParseException("Expected a positive integer after LIMIT", position());
                    }
                    return limit;
                }
            } catch (NumberFormatException nfe) {
                // Fall through
            }
            throw new ParseException("Expected an integer after LIMIT", position());
        }
        return Integer.MAX_VALUE;
    }

    @Override
    public void close() throws ParseException {
        tokenizer.close();
    }
}
