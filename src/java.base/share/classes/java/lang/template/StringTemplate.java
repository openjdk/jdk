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

import java.lang.Object;
import java.util.List;
import java.util.Objects;

import jdk.internal.javac.PreviewFeature;

/**
 * {@link StringTemplate} is the run-time representation of a string template or
 * text block template in a template expression.
 *
 * <p>
 * In the source code of a Java program, a string template or text block template
 * contains an interleaved succession of <em>fragment literals</em> and <em>embedded
 * expressions</em>. The {@link StringTemplate#fragments()} method returns the
 * fragment literals, and the {@link StringTemplate#values()} method returns the
 * results of evaluating the embedded expressions. {@link StringTemplate} does not
 * provide access to the source code of the embedded expressions themselves; it is
 * not a compile-time representation of a string template or text block template.
 * <p>
 * {@link StringTemplate} is primarily used in conjunction with a template processor
 * to produce a string or other meaningful value. Evaluation of a template expression
 * first produces an instance of {@link StringTemplate}, representing the template
 * of the template expression, and then passes the instance to the template processor
 * given by the template expression.
 * <p>
 * For example, the following code contains a template expression that uses the template
 * processor {@code RAW}, which simply yields the {@link StringTemplate} passed to it:
 * {@snippet :
 * int x = 10;
 * int y = 20;
 * StringTemplate st = RAW."\{x} + \{y} = \{x + y}";
 * List<String> fragments = st.fragments();
 * List<Object> values = st.values();
 * }
 * The value of {@code fragments} will be equivalent to {@code List.of("", " + ", " = ", "")},
 * which includes the empty first and last fragments. The {@code values} will be the
 * equivalent of {@code List.of(10, 20, 30)}.
 * <p>
 * The following code contains a template expression with the same template but with a
 * different template processor, {@code STR}:
 * {@snippet :
 * int x = 10;
 * int y = 20;
 * String s = STR."\{x} + \{y} = \{x + y}";
 * }
 * When the template expression is evaluated, an instance of {@link StringTemplate} is
 * produced that returns the same lists from {@link StringTemplate#fragments()} and
 * {@link StringTemplate#values()} as shown above. The {@link StringTemplate#STR} template
 * processor uses these lists to yield an interpolated string. the value of {@code s} will
 * be equivalent to {@code "10 + 20 = 30"}.
 * <p>
 * The {@code interpolate()} method provides a direct way to perform string interpolation
 * of a {@link StringTemplate}. Template processors can use the following code pattern:
 * {@snippet :
 * List<String> fragments = st.fragments();
 * List<Object> values    = st.values();
 * ... check or manipulate the fragments and/or values ...
 * String result = StringTemplate.interpolate(fragments, values);
 * }
 * The {@link StringTemplate#process(ValidatingProcessor)} method, in conjunction with
 * the {@link StringTemplate#RAW} processor, may be used to defer processing of a
 * {@link StringTemplate}.
 * {@snippet :
 * StringTemplate st = RAW."\{x} + \{y} = \{x + y}";
 * ...other steps...
 * String result = st.process(STR);
 * }
 * The factory methods {@link StringTemplate#of(String)} and
 * {@link StringTemplate#of(List, List)} can be used to construct a {@link StringTemplate}.
 *
 * @see ValidatingProcessor
 * @see TemplateProcessor
 * @see StringProcessor
 * @see java.util.FormatProcessor
 *
 * @implNote Implementations of {@link StringTemplate} must minimally implement the
 * methods {@link StringTemplate#fragments()} and {@link StringTemplate#values()}.
 * Instances of {@link StringTemplate} are considered immutable. To preserve the
 * semantics of string templates and text block templates, the list returned by
 * {@link StringTemplate#fragments()} must be one element larger than the list returned
 * by {@link StringTemplate#values()}.
 *
 * @since 21
 *
 * @jls 15.8.6 Process Template Expressions
 */
@PreviewFeature(feature=PreviewFeature.Feature.STRING_TEMPLATES)
public interface StringTemplate {
    /**
     * Returns a list of fragment literals for this {@link StringTemplate}.
     * The fragment literals are the character sequences preceding each of the embedded
     * expressions in source code, plus the character sequence following the last
     * embedded expression. Such character sequences may be zero-length if an embedded
     * expression appears at the beginning or end of a template, or if two embedded
     * expressions are directly adjacent in a template.
     * In the example: {@snippet :
     * String student = "Mary";
     * String teacher = "Johnson";
     * StringTemplate st = RAW."The student \{student} is in \{teacher}'s classroom.";
     * List<String> fragments = st.fragments(); // @highlight substring="fragments()"
     * }
     * {@code fragments} will be equivalent to
     * {@code List.of("The student ", " is in ", "'s classroom.")}
     *
     * @return list of string fragments
     */
    List<String> fragments();

    /**
     * Returns a list of embedded expression results for this {@link StringTemplate}.
     * In the example:
     * {@snippet :
     * String student = "Mary";
     * String teacher = "Johnson";
     * StringTemplate st = RAW."The student \{student} is in \{teacher}'s classroom.";
     * List<Object> values = st.values(); // @highlight substring="values()"
     * }
     * {@code values} will be equivalent to {@code List.of(student, teacher)}
     *
     * @return list of expression values
     */
    List<Object> values();

    /**
     * Returns the string interpolation of the fragments and values for this
     * {@link StringTemplate}.
     * <p>
     * For better visibility and when practical, it is recommended to use the
     * {@link StringTemplate#STR} processor instead of invoking the
     * {@link StringTemplate#interpolate()} method.
     * {@snippet :
     * String student = "Mary";
     * String teacher = "Johnson";
     * StringTemplate st = RAW."The student \{student} is in \{teacher}'s classroom.";
     * String result = st.interpolate(); // @highlight substring="interpolate()"
     * }
     * In the above example, the value of  {@code result} will be
     * {@code "The student Mary is in Johnson's classroom."}. This is
     * produced by the interleaving concatenation of fragments and values from the supplied
     * {@link StringTemplate}. To accommodate concatenation, values are converted to strings
     * as if invoking {@link String#valueOf(Object)}.
     *
     * @return interpolation of this {@link StringTemplate}
     *
     * @implSpec The default implementation returns the result of invoking
     * {@code StringTemplate.interpolate(this.fragments(), this.values())}.
     */
    default String interpolate() {
        return StringTemplate.interpolate(fragments(), values());
    }

    /**
     * Returns the result of applying the specified processor to this {@link StringTemplate}.
     * This method can be used as an alternative to string template expressions. For example,
     * {@snippet :
     * String student = "Mary";
     * String teacher = "Johnson";
     * String result1 = STR."The student \{student} is in \{teacher}'s classroom.";
     * String result2 = RAW."The student \{student} is in \{teacher}'s classroom.".process(STR); // @highlight substring="process"
     * }
     * Produces an equivalent result for both {@code result1} and {@code result2}.
     *
     * @param processor the {@link ValidatingProcessor} instance to process
     *
     * @param <R>  Processor's process result type.
     * @param <E>  Exception thrown type.
     *
     * @return constructed object of type {@code R}
     *
     * @throws E exception thrown by the template processor when validation fails
     * @throws NullPointerException if processor is null
     *
     * @implSpec The default implementation returns the result of invoking
     * {@code processor.process(this)}. If the invocation throws an exception that
     * exception is forwarded to the caller.
     */
    default <R, E extends Throwable> R
    process(ValidatingProcessor<? extends R, ? extends E> processor) throws E {
        Objects.requireNonNull(processor, "processor should not be null");

        return processor.process(this);
    }

    /**
     * Produces a diagnostic string that describes the fragments and values of the supplied
     * {@link StringTemplate}.
     *
     * @param stringTemplate  the {@link StringTemplate} to represent
     *
     * @return diagnostic string representing the supplied string template
     *
     * @throws NullPointerException if stringTemplate is null
     */
    static String toString(StringTemplate stringTemplate) {
        Objects.requireNonNull(stringTemplate, "stringTemplate should not be null");
        return "StringTemplate{ fragments = [ \"" +
                String.join("\", \"", stringTemplate.fragments()) +
                "\" ], values = " +
                stringTemplate.values() +
                " }";
    }

    /**
     * Returns a {@link StringTemplate} as if constructed by invoking
     * {@code StringTemplate.of(List.of(string), List.of())}. That is, a {@link StringTemplate}
     * with one fragment and no values.
     *
     * @param string  single string fragment
     *
     * @return StringTemplate composed from string
     *
     * @throws NullPointerException if string is null
     */
    static StringTemplate of(String string) {
        Objects.requireNonNull(string, "string must not be null");
        return TemplateSupport.of(List.of(string), List.of());
    }

    /**
     * Returns a StringTemplate with the given fragments and values.
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
    static StringTemplate of(List<String> fragments, List<?> values) {
        Objects.requireNonNull(fragments, "fragments must not be null");
        Objects.requireNonNull(values, "values must not be null");
        if (values.size() + 1 != fragments.size()) {
            throw new IllegalArgumentException(
                    "fragments list size is not one more than values list size");
        }
        return TemplateSupport.of(fragments, values);
    }

    /**
     * Creates a string that interleaves the elements of values between the
     * elements of fragments. To accommodate interpolation, values are converted to strings
     * as if invoking {@link String#valueOf(Object)}.
     *
     * @param fragments  list of String fragments
     * @param values     list of expression values
     *
     * @return String interpolation of fragments and values
     *
     * @throws IllegalArgumentException if fragments list size is not one more
     *         than values list size
     * @throws NullPointerException fragments or values is null or if any of the fragments is null
     */
    static String interpolate(List<String> fragments, List<?> values) {
        Objects.requireNonNull(fragments, "fragments must not be null");
        Objects.requireNonNull(values, "values must not be null");
        int fragmentsSize = fragments.size();
        int valuesSize = values.size();
        if (fragmentsSize != valuesSize + 1) {
            throw new IllegalArgumentException("fragments must have one more element than values");
        }
        return TemplateSupport.interpolate(fragments, values);
    }

    /**
     * Combine zero or more {@link StringTemplate StringTemplates} into a single
     * {@link StringTemplate}.
     * {@snippet :
     * StringTemplate st = StringTemplate.combine(RAW."\{a}", RAW."\{b}", RAW."\{c}");
     * assert st.interpolate().equals(RAW."\{a}\{b}\{c}");
     * }
     * Fragment lists from each {@link StringTemplate} are merged such that the last fragment
     * from the previous {@link StringTemplate} is concatenated with the first fragment of the next
     * {@link StringTemplate}. Values lists are simply concatenated to produce a single values list.
     * The result is a well-formed {@link StringTemplate} with n+1 fragments and n values, where
     * n is the total of number of values across all the supplied
     * {@link StringTemplate StringTemplates}.
     *
     * @param stringTemplates  zero or more {@link StringTemplate}
     *
     * @return combined {@link StringTemplate}
     *
     * @throws NullPointerException if stringTemplates is null or if any of the
     * {@code stringTemplates} are null
     *
     * @implNote If zero {@link StringTemplate} arguments are provided then a
     * {@link StringTemplate} with an empty fragment and no values is returned, as if invoking
     * <code>StringTemplate.of("")</code> . If only one {@link StringTemplate} argument is provided
     * then it is returned unchanged.
     */
    static StringTemplate combine(StringTemplate... stringTemplates) {
        return TemplateSupport.combine(stringTemplates);
    }

    /**
     * Combine a list of {@link StringTemplate StringTemplates} into a single
     * {@link StringTemplate}.
     * {@snippet :
     * StringTemplate st = StringTemplate.combine(List.of(RAW."\{a}", RAW."\{b}", RAW."\{c}"));
     * assert st.interpolate().equals(RAW."\{a}\{b}\{c}");
     * }
     * Fragment lists from each {@link StringTemplate} are merged such that the last fragment
     * from the previous {@link StringTemplate} is concatenated with the first fragment of the next
     * {@link StringTemplate}. Values lists are simply concatenated to produce a single values list.
     * The result is a well-formed {@link StringTemplate} with n+1 fragments and n values, where
     * n is the total of number of values across all the supplied
     * {@link StringTemplate StringTemplates}.
     *
     * @param stringTemplates  list of {@link StringTemplate}
     *
     * @return combined {@link StringTemplate}
     *
     * @throws NullPointerException if stringTemplates is null or if any of the
     * its elements are null
     *
     * @implNote If {@code stringTemplates.size() == 0} then a {@link StringTemplate} with
     * an empty fragment and no values is returned, as if invoking
     * <code>StringTemplate.of("")</code> . If only {@code stringTemplates.size() == 1}
     * then that element is returned unchanged.
     */
    static StringTemplate combine(List<StringTemplate> stringTemplates) {
        return TemplateSupport.combine(stringTemplates.toArray(new StringTemplate[0]));
    }

    /**
     * This {@link StringProcessor} instance is conventionally used for the string interpolation
     * of a supplied {@link StringTemplate}.
     * <p>
     * For better visibility and when practical, it is recommended that users use the
     * {@link StringTemplate#STR} processor instead of invoking the
     * {@link StringTemplate#interpolate()} method.
     * Example: {@snippet :
     * int x = 10;
     * int y = 20;
     * String result = STR."\{x} + \{y} = \{x + y}"; // @highlight substring="STR"
     * }
     * In the above example, the value of {@code result} will be {@code "10 + 20 = 30"}. This is
     * produced by the interleaving concatenation of fragments and values from the supplied
     * {@link StringTemplate}. To accommodate concatenation, values are converted to strings
     * as if invoking {@link String#valueOf(Object)}.
     * @implNote {@link StringTemplate#STR} is statically imported implicitly into every
     * Java compilation unit.<p>The result of interpolation is not interned.
     */
    static final StringProcessor STR = StringTemplate::interpolate;

    /**
     * This {@link TemplateProcessor} instance is conventionally used to indicate that the
     * processing of the {@link StringTemplate} is to be deferred to a later time. Deferred
     * processing can be resumed by invoking the
     * {@link StringTemplate#process(ValidatingProcessor)} or
     * {@link ValidatingProcessor#process(StringTemplate)} methods.
     * {@snippet :
     * import static java.lang.template.StringTemplate.RAW;
     * ...
     * StringTemplate st = RAW."\{x} + \{y} = \{x + y}";
     * ...other steps...
     * String result = STR.process(st);
     * }
     * @implNote Unlike {@link StringTemplate#STR}, {@link StringTemplate#RAW} must be
     * statically imported explicitly.
     */
    static final TemplateProcessor<StringTemplate> RAW = st -> st;

}
