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

package java.lang.template;

import java.util.*;
import java.util.stream.Collectors;

import jdk.internal.javac.PreviewFeature;

/**
 * The Java compiler produces implementations of {@link StringTemplate} to
 * represent string templates and text block templates. Libraries may produce
 * {@link StringTemplate} instances as long as they conform to the requirements
 * of this interface. Like {@link String}, instances of {@link StringTemplate}
 * implementations are considered immutable.
 * <p>
 * Implementations of this interface must minimally implement the methods
 * {@link StringTemplate#fragments()} and {@link StringTemplate#values()}.
 * <p>
 * The {@link StringTemplate#fragments()} method must return an immutable
 * {@code List<String>} consistent with the string template body. The list
 * contains the string of characters preceeding each of the embedded expressions
 * plus the string of characters following the last embedded expression. The order
 * of the strings is left to right as they appear in the string template.
 * For example; {@snippet :
 * StringTemplate st = "The \{name} and \{address} of the resident.";
 * List<String> fragments = st.fragments();
 * }
 * {@code fragments} will be equivalent to <code>List.of("The ", " and ", " of the resident.")</code>.
 * <p>
 * The {@link StringTemplate#values()} method returns an immutable {@code
 * List<Object>} of values accumulated by evaluating embedded expressions prior
 * to instantiating the {@link StringTemplate}. The values are accumulated left
 * to right. The first element of the list is the result of the leftmost
 * embedded expression. The last element of the list is the result of the
 * rightmost embedded expression.
 * For example,
 * {@snippet :
 * int x = 10;
 * int y = 20;
 * StringTemplate st = "\{x} + \{y} = \{x + y}";
 * List<Object> values = st.values();
 * }
 * {@code values} will be the equivalent of <code>List.of(x, y, x + y)</code>.
 * <p>
 * {@link StringTemplate StringTemplates} are primarily used in conjuction
 * with {@linkplain  ValidatingProcessor template processors} to produce meaningful
 * results. For example, if a user wants string interpolation, then they can use a string template
 * expression with the standard {@link StringTemplate#STR} processor.
 * {@snippet :
 * int x = 10;
 * int y = 20;
 * String result = STR."\{x} + \{y} = \{x + y}";
 * }
 * {@code result} will be equivalent to <code>"10 + 20 = 30"</code>.
 * <p>
 * The {@link StringTemplate#process(ValidatingProcessor)} method supplies an
 * alternative to using string template expressions.
 * {@snippet :
 * String result = "\{x} + \{y} = \{x + y}".process(STR);
 * }
 * In addition to string template expressions, the factory methods
 * {@link StringTemplate#of(String)} and {@link StringTemplate#of(List, List)}
 * can be used to construct {@link StringTemplate StringTemplates}.
 * <p>
 * The {@link StringTemplate#interpolate()} method provides a simple way to produce a
 * string interpolation of the {@link StringTemplate}.
 * <p>
 * {@linkplain ValidatingProcessor Template processors} typically use the following code
 * pattern to perform composition:
 * {@snippet :
 * List<String> fragments = st.fragments();
 * List<Object> values = st.values();
 * // check or manipulate the fragments and/or values
 * ...
 * String result = TemplateRuntime.interpolate(fragments, values);;
 * }
 *
 * @implSpec An instance of {@link StringTemplate} is immutatble. Also, the
 * fragment list size must be one more than the values list size.
 *
 * @see ValidatingProcessor
 * @see TemplateProcessor
 * @see StringProcessor
 * @see java.util.FormatProcessor
 *
 * @since 20
 */
@PreviewFeature(feature=PreviewFeature.Feature.STRING_TEMPLATES)
public interface StringTemplate {
    /**
     * Returns an immutable list of string fragments consisting of the string
     * of characters preceeding each of the embedded expressions plus the
     * string of characters following the last embedded expression. In the
     * example: {@snippet :
     * StringTemplate st = "The student \{student} is in \{teacher}'s class room.";
     * List<String> fragments = st.fragments(); // @highlight substring="fragments()"
     * }
     * <code>fragments</code> will be equivalent to
     * <code>List.of("The student ", " is in ", "'s class room.")</code>
     *
     * @return list of string fragments
     *
     * @implSpec The list returned is immutable.
     */
    List<String> fragments();

    /**
     * Returns an immutable list of embedded expression results. In the example:
     * {@snippet :
     * StringTemplate stringTemplate = "\{x} + \{y} = \{x + y}";
     * List<Object> values = stringTemplate.values(); // @highlight substring="values()"
     * }
     * <code>values</code> will be equivalent to <code>List.of(x, y, x + y)</code>
     *
     * @return list of expression values
     *
     * @implSpec The list returned is immutable.
     */
    List<Object> values();

    /**
     * {@return the interpolation of the StringTemplate}
     */
    default String interpolate() {
        return TemplateRuntime.interpolate(fragments(), values());
    }

    /**
     * Returns the result of applying the specified processor to this {@link StringTemplate}.
     * This method can be used as an alternative to string template expressions. For example,
     * {@snippet :
     * String result1 = STR."\{x} + \{y} = \{x + y}";
     * String result2 = "\{x} + \{y} = \{x + y}".process(STR); // @highlight substring="process"
     * }
     * produces an equivalent result for both {@code result1} and {@code result2}.
     *
     * @param processor the {@link ValidatingProcessor} instance to process
     *
     * @param <R>  Processor's process result type.
     * @param <E>  Exception thrown type.
     *
     * @return constructed object of type R
     *
     * @throws E exception thrown by the template processor when validation fails
     * @throws NullPointerException if processor is null
     *
     * @implNote The default implementation simply invokes the processor's process
     * method {@code processor.process(this)}.
     */
    default <R, E extends Throwable> R process(ValidatingProcessor<R, E> processor) throws E {
        Objects.requireNonNull(processor, "processor should not be null");

        return processor.process(this);
    }

    /**
     * Return the types of a {@link StringTemplate StringTemplate's} values.
     *
     * @return list of value types
     *
     * @implNote The default method determines if the {@link StringTemplate}
     * was synthesized by the compiler, then the types are precisely those of the
     * embedded expressions, otherwise this method returns the values list types.
     */
    default public List<Class<?>> valueTypes() {
        return TemplateRuntime.valueTypes(this);
    }

    /**
     * Produces a diagnostic string representing the supplied
     * {@link StringTemplate}.
     *
     * @param stringTemplate  the {@link StringTemplate} to represent
     *
     * @return diagnostic string representing the supplied templated string
     *
     * @throws NullPointerException if stringTemplate is null
     */
    public static String toString(StringTemplate stringTemplate) {
        Objects.requireNonNull(stringTemplate, "stringTemplate should not be null");
        return "StringTemplate{ fragments = [ \"" +
                String.join("\", \"", stringTemplate.fragments()) +
                "\" ], values = " +
                stringTemplate.values() +
                " }";
    }

    /**
     * Produces a hash code for the supplied {@link StringTemplate}.
     *
     * @param stringTemplate the {@link StringTemplate} to hash
     *
     * @return hash code for the supplied {@link StringTemplate}
     *
     * @throws NullPointerException if stringTemplate is null
     *
     * @implSpec The hashCode is the bit XOR of the fragments hash code and
     * the values hash code.
     */
    public static int hashCode(StringTemplate stringTemplate) {
        Objects.requireNonNull(stringTemplate, "stringTemplate should not be null");
        return Objects.hashCode(stringTemplate.fragments()) ^
               Objects.hashCode(stringTemplate.values());
    }

    /**
     * Tests for equality of two {@link StringTemplate}.
     *
     * @param a  first {@link StringTemplate}
     * @param b  second {@link StringTemplate}
     *
     * @return true if the two {@link StringTemplate StringTemplates} are equivalent
     *
     * @throws NullPointerException if either @link StringTemplate} is null
     *
     * @implSpec Equality is determined by testing equality of the fragments and
     * the values.
     */
    public static boolean equals(Object a, Object b) {
        Objects.requireNonNull(a, "StringTemplate a should not be null");
        Objects.requireNonNull(b, "StringTemplate b should not be null");
        return a instanceof StringTemplate aST && b instanceof StringTemplate bST &&
               Objects.equals(aST.fragments(), bST.fragments()) &&
               Objects.equals(aST.values(), bST.values());
    }

    /**
     * Returns a StringTemplate composed from a string.
     *
     * @param string  single string fragment
     *
     * @return StringTemplate composed from string
     *
     * @throws NullPointerException if string is null
     */
    public static StringTemplate of(String string) {
        Objects.requireNonNull(string, "string must not be null");
        return new SimpleStringTemplate(List.of(string), List.of());
    }

    /**
     * Returns a StringTemplate composed from fragments and values.
     *
     * @implSpec The {@code fragments} list size must be one more that the
     * {@code values} list size.
     *
     * @param fragments list of string fragments
     * @param values    list of expression values
     *
     * @return StringTemplate composed from string
     *
     * @throws IllegalArgumentException if fragments list size is not one more
     *         than values list size
     * @throws NullPointerException if fragments is null or values is null or if any fragment is null.
     *
     * @implNote Contents of both lists are copied to construct immutable lists.
     */
    public static StringTemplate of(List<String> fragments, List<Object> values) {
        Objects.requireNonNull(fragments, "fragments must not be null");
        Objects.requireNonNull(values, "values must not be null");
        if (values.size() + 1 != fragments.size()) {
            throw new IllegalArgumentException(
                    "fragments list size is not one more than values list size");
        }
        for (String fragment : fragments) {
            Objects.requireNonNull(fragment, "fragments elements must be non-null");
        }
        fragments = Collections.unmodifiableList(new ArrayList<>(fragments));
        values = Collections.unmodifiableList(new ArrayList<>(values));
        return new SimpleStringTemplate(fragments, values);
    }

     /**
     * Creates a string that interleaves the elements of values between the
     * elements of fragments.
     *
     * @param fragments  list of String fragments
     * @param values     list of expression values
     *
     * @return String interpolation of fragments and values
     *
     * @throws NullPointerException fragments or values is null or if any of the fragments is null
     */
    public static String interpolate(List<String> fragments, List<Object> values) {
        return TemplateRuntime.interpolate(fragments, values);
    }

    /**
      * Combine one or more {@link StringTemplate StringTemplates} to produce a combined {@link StringTemplate}.
      * {@snippet :
      * StringTemplate st = StringTemplate.combine("\{a}", "\{b}", "\{c}");
      * assert st.interpolate().equals("\{a}\{b}\{c}");
      * }
      *
      * @param sts  one or more {@link StringTemplate}
      *
      * @return combined {@link StringTemplate}
      *
      * @throws NullPointerException if sts is null or if any of the elements are null
      * @throws RuntimeException if sts has zero elements
      */
    public static StringTemplate combine(StringTemplate... sts) {
        return TemplateRuntime.combine(sts);
    }

    /**
     * Interpolation template processor instance.
     * Example: {@snippet :
     * int x = 10;
     * int y = 20;
     * String result = STR."\{x} + \{y} = \{x + y}"; // @highlight substring="STR"
     * }
     * @implNote The result of interpolation is not interned.
     */
    public static final StringProcessor STR = st -> st.interpolate();

    /**
     * No-op template processor. Used to highlight that non-processing of the StringTemplate
     * was intentional.
     * {@snippet :
     * // The string template before interpolation
     * System.out.println(RAW."\{x} = \{y} = \{x + y}");
     * // The string template after interpolation
     * System.out.println(STR."\{x} = \{y} = \{x + y}");
     * }
     */
    public static final TemplateProcessor<StringTemplate> RAW = st -> st;

}
