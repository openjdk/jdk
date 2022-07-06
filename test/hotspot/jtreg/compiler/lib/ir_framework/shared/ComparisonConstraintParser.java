/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

package compiler.lib.ir_framework.shared;

import java.util.function.BiPredicate;
import java.util.function.Function;

/**
 * Utility class to parse a comparator either in the applyIf* or in the counts properties of an @IR rules.
 */
public class ComparisonConstraintParser<T extends Comparable<T>> {

    private enum Comparator {
        ONE_CHAR, TWO_CHARS
    }

    public static <T extends Comparable<T>> Comparison<T> parse(String constraint, Function<String, T> parseFunction,
                                                                String postfixErrorMsg) {
        try {
            return parseConstraintAndValue(constraint, parseFunction);
        } catch (EmptyConstraintException e) {
            TestFormat.fail("Provided empty value " + postfixErrorMsg);
            throw new UnreachableCodeException();
        } catch (MissingConstraintValueException e) {
            TestFormat.fail("Provided empty value after comparator \"" + e.getComparator() + "\" " + postfixErrorMsg);
            throw new UnreachableCodeException();
        } catch (InvalidComparatorException e) {
            TestFormat.fail("Provided invalid comparator \"" + e.getComparator() + "\" " + postfixErrorMsg);
            throw new UnreachableCodeException();
        } catch (InvalidConstraintValueException e) {
            String comparator = e.getComparator();
            if (!comparator.isEmpty()) {
                comparator = " after comparator \"" + comparator + "\"";
            }
            TestFormat.fail("Provided invalid value \"" + e.getInvalidValue() + "\""
                                   + comparator + " " + postfixErrorMsg);
            throw new UnreachableCodeException();
        }
    }

    private static <T extends Comparable<T>> Comparison<T> parseConstraintAndValue(String constraint,
                                                                                   Function<String, T> parseFunction) throws
            EmptyConstraintException, MissingConstraintValueException,
            InvalidComparatorException, InvalidConstraintValueException {
        ParsedResult<T> result = parse(constraint);
        T givenValue = parseGivenValue(parseFunction, result);
        return new Comparison<>(givenValue, result.comparator, result.comparisonPredicate);
    }

    private static <T extends Comparable<T>> ParsedResult<T> parse(String constraint) throws
            EmptyConstraintException, MissingConstraintValueException, InvalidComparatorException {
        constraint = constraint.trim();
        if (constraint.isEmpty()) {
            throw new EmptyConstraintException();
        }
        switch (constraint.charAt(0)) {
            case '<' -> {
                throwIfNoValueAfterComparator(constraint, Comparator.ONE_CHAR);
                if (constraint.charAt(1) == '=') {
                    throwIfNoValueAfterComparator(constraint, Comparator.TWO_CHARS);
                    return new ParsedResult<>(constraint.substring(2).trim(), "<=", (x, y) -> x.compareTo(y) <= 0);
                } else {
                    return new ParsedResult<>(constraint.substring(1).trim(), "<", (x, y) -> x.compareTo(y) < 0);
                }
            }
            case '>' -> {
                throwIfNoValueAfterComparator(constraint, Comparator.ONE_CHAR);
                if (constraint.charAt(1) == '=') {
                    throwIfNoValueAfterComparator(constraint, Comparator.TWO_CHARS);
                    return new ParsedResult<>(constraint.substring(2).trim(), ">=", (x, y) -> x.compareTo(y) >= 0);
                } else {
                    return new ParsedResult<>(constraint.substring(1).trim(), ">", (x, y) -> x.compareTo(y) > 0);
                }
            }
            case '!' -> {
                throwIfNoValueAfterComparator(constraint, Comparator.ONE_CHAR);
                if (constraint.charAt(1) != '=') {
                    throw new InvalidComparatorException("!");
                }
                throwIfNoValueAfterComparator(constraint, Comparator.TWO_CHARS);
                return new ParsedResult<>(constraint.substring(2).trim(), "!=", (x, y) -> x.compareTo(y) != 0);
            }
            case '=' -> { // Allowed syntax, equivalent to not using any symbol.
                throwIfNoValueAfterComparator(constraint, Comparator.ONE_CHAR);
                return new ParsedResult<>(constraint.substring(1).trim(), "=", (x, y) -> x.compareTo(y) == 0);
            }
            default -> {
                return new ParsedResult<>(constraint.trim(), "=", (x, y) -> x.compareTo(y) == 0);
            }
        }
    }

    private static void throwIfNoValueAfterComparator(String constraint, Comparator comparator) throws MissingConstraintValueException {
        switch (comparator) {
            case ONE_CHAR -> {
                if (constraint.length() == 1) {
                    throw new MissingConstraintValueException(constraint);
                }
            }
            case TWO_CHARS -> {
                if (constraint.length() == 2) {
                    throw new MissingConstraintValueException(constraint);
                }
            }
        }
    }

    private static <T extends Comparable<T>> T parseGivenValue(Function<String, T> parseFunction, ParsedResult<T> result)
            throws InvalidConstraintValueException {
        try {
            return parseFunction.apply(result.value);
        }
        catch (NumberFormatException e) {
            throw new InvalidConstraintValueException(result.value, result.comparator);
        }
    }

    static class ParsedResult<T> {
        public String value;
        public BiPredicate<T, T> comparisonPredicate;
        public String comparator;

        public ParsedResult(String value, String comparator,BiPredicate<T, T> comparisonPredicate) {
            this.value = value;
            this.comparator = comparator;
            this.comparisonPredicate = comparisonPredicate;
        }
    }
}
