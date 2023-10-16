/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

package java.lang;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.util.FormatProcessor;
import java.util.function.Function;
import java.util.List;
import java.util.Objects;

import jdk.internal.access.JavaTemplateAccess;
import jdk.internal.access.SharedSecrets;
import jdk.internal.javac.PreviewFeature;

/**
 * {@link StringTemplate} is the run-time representation of a string template or
 * text block template in a template expression.
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
 * first produces an instance of {@link StringTemplate}, representing the right hand side
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
 * {@code fragments} will be equivalent to {@code List.of("", " + ", " = ", "")},
 * which includes the empty first and last fragments. {@code values} will be the
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
 * processor uses these lists to yield an interpolated string. The value of {@code s} will
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
 * The {@link StringTemplate#process(Processor)} method, in conjunction with
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
 * @see Processor
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
     *
     * @implSpec the list returned is immutable
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
     *
     * @implSpec the list returned is immutable
     */
    List<Object> values();

    /**
     * Returns the string interpolation of the fragments and values for this
     * {@link StringTemplate}.
     * @apiNote For better visibility and when practical, it is recommended to use the
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
     * @param processor the {@link Processor} instance to process
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
    process(Processor<? extends R, ? extends E> processor) throws E {
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
        JavaTemplateAccess JTA = SharedSecrets.getJavaTemplateAccess();
        return JTA.of(List.of(string), List.of());
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
        JavaTemplateAccess JTA = SharedSecrets.getJavaTemplateAccess();
        return JTA.of(fragments, values);
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
        JavaTemplateAccess JTA = SharedSecrets.getJavaTemplateAccess();
        return JTA.interpolate(fragments, values);
    }

    /**
     * Combine zero or more {@link StringTemplate StringTemplates} into a single
     * {@link StringTemplate}.
     * {@snippet :
     * StringTemplate st = StringTemplate.combine(RAW."\{a}", RAW."\{b}", RAW."\{c}");
     * assert st.interpolate().equals(STR."\{a}\{b}\{c}");
     * }
     * Fragment lists from the {@link StringTemplate StringTemplates} are combined end to
     * end with the last fragment from each {@link StringTemplate} concatenated with the
     * first fragment of the next. To demonstrate, if we were to take two strings and we
     * combined them as follows: {@snippet lang = "java":
     * String s1 = "abc";
     * String s2 = "xyz";
     * String sc = s1 + s2;
     * assert Objects.equals(sc, "abcxyz");
     * }
     * the last character {@code "c"} from the first string is juxtaposed with the first
     * character {@code "x"} of the second string. The same would be true of combining
     * {@link StringTemplate StringTemplates}.
     * {@snippet lang ="java":
     * StringTemplate st1 = RAW."a\{}b\{}c";
     * StringTemplate st2 = RAW."x\{}y\{}z";
     * StringTemplate st3 = RAW."a\{}b\{}cx\{}y\{}z";
     * StringTemplate stc = StringTemplate.combine(st1, st2);
     *
     * assert Objects.equals(st1.fragments(), List.of("a", "b", "c"));
     * assert Objects.equals(st2.fragments(), List.of("x", "y", "z"));
     * assert Objects.equals(st3.fragments(), List.of("a", "b", "cx", "y", "z"));
     * assert Objects.equals(stc.fragments(), List.of("a", "b", "cx", "y", "z"));
     * }
     * Values lists are simply concatenated to produce a single values list.
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
        JavaTemplateAccess JTA = SharedSecrets.getJavaTemplateAccess();
        return JTA.combine(stringTemplates);
    }

    /**
     * Combine a list of {@link StringTemplate StringTemplates} into a single
     * {@link StringTemplate}.
     * {@snippet :
     * StringTemplate st = StringTemplate.combine(List.of(RAW."\{a}", RAW."\{b}", RAW."\{c}"));
     * assert st.interpolate().equals(STR."\{a}\{b}\{c}");
     * }
     * Fragment lists from the {@link StringTemplate StringTemplates} are combined end to
     * end with the last fragment from each {@link StringTemplate} concatenated with the
     * first fragment of the next. To demonstrate, if we were to take two strings and we
     * combined them as follows: {@snippet lang = "java":
     * String s1 = "abc";
     * String s2 = "xyz";
     * String sc = s1 + s2;
     * assert Objects.equals(sc, "abcxyz");
     * }
     * the last character {@code "c"} from the first string is juxtaposed with the first
     * character {@code "x"} of the second string. The same would be true of combining
     * {@link StringTemplate StringTemplates}.
     * {@snippet lang ="java":
     * StringTemplate st1 = RAW."a\{}b\{}c";
     * StringTemplate st2 = RAW."x\{}y\{}z";
     * StringTemplate st3 = RAW."a\{}b\{}cx\{}y\{}z";
     * StringTemplate stc = StringTemplate.combine(List.of(st1, st2));
     *
     * assert Objects.equals(st1.fragments(), List.of("a", "b", "c"));
     * assert Objects.equals(st2.fragments(), List.of("x", "y", "z"));
     * assert Objects.equals(st3.fragments(), List.of("a", "b", "cx", "y", "z"));
     * assert Objects.equals(stc.fragments(), List.of("a", "b", "cx", "y", "z"));
     * }
     * Values lists are simply concatenated to produce a single values list.
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
     * <code>StringTemplate.of("")</code> . If {@code stringTemplates.size() == 1}
     * then the first element of the list is returned unchanged.
     */
    static StringTemplate combine(List<StringTemplate> stringTemplates) {
        JavaTemplateAccess JTA = SharedSecrets.getJavaTemplateAccess();
        return JTA.combine(stringTemplates.toArray(new StringTemplate[0]));
    }

    /**
     * This {@link Processor} instance is conventionally used for the string interpolation
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
     * @apiNote {@link StringTemplate#STR} is statically imported implicitly into every
     * Java compilation unit.
     */
    Processor<String, RuntimeException> STR = StringTemplate::interpolate;

    /**
     * This {@link Processor} instance is conventionally used to indicate that the
     * processing of the {@link StringTemplate} is to be deferred to a later time. Deferred
     * processing can be resumed by invoking the
     * {@link StringTemplate#process(Processor)} or
     * {@link Processor#process(StringTemplate)} methods.
     * {@snippet :
     * import static java.lang.StringTemplate.RAW;
     * ...
     * StringTemplate st = RAW."\{x} + \{y} = \{x + y}";
     * ...other steps...
     * String result = STR.process(st);
     * }
     * @implNote Unlike {@link StringTemplate#STR}, {@link StringTemplate#RAW} must be
     * statically imported explicitly.
     */
    Processor<StringTemplate, RuntimeException> RAW = st -> st;

    /**
     * This interface describes the methods provided by a generalized string template processor. The
     * primary method {@link Processor#process(StringTemplate)} is used to validate
     * and compose a result using a {@link StringTemplate StringTemplate's} fragments and values lists.
     * <p>
     * For example:
     * {@snippet :
     * class MyProcessor implements Processor<String, IllegalArgumentException> {
     *     @Override
     *     public String process(StringTemplate st) throws IllegalArgumentException {
     *          StringBuilder sb = new StringBuilder();
     *          Iterator<String> fragmentsIter = st.fragments().iterator();
     *
     *          for (Object value : st.values()) {
     *              sb.append(fragmentsIter.next());
     *
     *              if (value instanceof Boolean) {
     *                  throw new IllegalArgumentException("I don't like Booleans");
     *              }
     *
     *              sb.append(value);
     *          }
     *
     *          sb.append(fragmentsIter.next());
     *
     *          return sb.toString();
     *     }
     * }
     *
     * MyProcessor myProcessor = new MyProcessor();
     * try {
     *     int x = 10;
     *     int y = 20;
     *     String result = myProcessor."\{x} + \{y} = \{x + y}";
     *     ...
     * } catch (IllegalArgumentException ex) {
     *     ...
     * }
     * }
     * Implementations of this interface may provide, but are not limited to, validating
     * inputs, composing inputs into a result, and transforming an intermediate string
     * result to a non-string value before delivering the final result.
     * <p>
     * The user has the option of validating inputs used in composition. For example an SQL
     * processor could prevent injection vulnerabilities by sanitizing inputs or throwing an
     * exception of type {@code E} if an SQL statement is a potential vulnerability.
     * <p>
     * Composing allows user control over how the result is assembled. Most often, a
     * user will construct a new string from the string template, with placeholders
     * replaced by string representations of value list elements. These string
     * representations are created as if invoking {@link String#valueOf}.
     * <p>
     * Transforming allows the processor to return something other than a string. For
     * instance, a JSON processor could return a JSON object, by parsing the string created
     * by composition, instead of the composed string.
     * <p>
     * {@link Processor} is a {@link FunctionalInterface}. This permits
     * declaration of a processor using lambda expressions;
     * {@snippet :
     * Processor<String, RuntimeException> processor = st -> {
     *     List<String> fragments = st.fragments();
     *     List<Object> values = st.values();
     *     // check or manipulate the fragments and/or values
     *     ...
     *     return StringTemplate.interpolate(fragments, values);
     * };
     * }
     * The {@link StringTemplate#interpolate()} method is available for those processors
     * that just need to work with the string interpolation;
     * {@snippet :
     * Processor<String, RuntimeException> processor = StringTemplate::interpolate;
     * }
     * or simply transform the string interpolation into something other than
     * {@link String};
     * {@snippet :
     * Processor<JSONObject, RuntimeException> jsonProcessor = st -> new JSONObject(st.interpolate());
     * }
     * @implNote The Java compiler automatically imports {@link StringTemplate#STR}
     *
     * @param <R>  Processor's process result type
     * @param <E>  Exception thrown type
     *
     * @see StringTemplate
     * @see java.util.FormatProcessor
     *
     * @since 21
     *
     * @jls 15.8.6 Process Template Expressions
     */
    @PreviewFeature(feature=PreviewFeature.Feature.STRING_TEMPLATES)
    @FunctionalInterface
    public interface Processor<R, E extends Throwable> {

        /**
         * Constructs a result based on the template fragments and values in the
         * supplied {@link StringTemplate stringTemplate} object.
         * @apiNote Processing of a {@link StringTemplate} may include validation according to the particular facts relating
         * to each situation. The {@code E} type parameter indicates the type of checked exception that is thrown by
         * {@link #process} if validation fails, ex. {@code java.sql.SQLException}. If no checked exception is expected
         * then {@link RuntimeException} may be used. Note that unchecked exceptions, such as {@link RuntimeException},
         * {@link NullPointerException} or {@link IllegalArgumentException} may be thrown as part of the normal
         * method arguments processing. Details of which exceptions are thrown will be found in the documentation
         * of the specific implementation.
         *
         * @param stringTemplate  a {@link StringTemplate} instance
         *
         * @return constructed object of type R
         *
         * @throws E exception thrown by the template processor when validation fails
         */
        R process(StringTemplate stringTemplate) throws E;

        /**
         * This factory method can be used to create a {@link Processor} containing a
         * {@link Processor#process} method derived from a lambda expression. As an example;
         * {@snippet :
         * Processor<String, RuntimeException> mySTR = Processor.of(StringTemplate::interpolate);
         * int x = 10;
         * int y = 20;
         * String str = mySTR."\{x} + \{y} = \{x + y}";
         * }
         * The result type of the constructed {@link Processor} may be derived from
         * the lambda expression, thus this method may be used in a var
         * statement. For example, {@code mySTR} from above can also be declared using;
         * {@snippet :
         * var mySTR = Processor.of(StringTemplate::interpolate);
         * }
         * {@link RuntimeException} is the assumed exception thrown type.
         *
         * @param process a function that takes a {@link StringTemplate} as an argument
         *                and returns the inferred result type
         *
         * @return a {@link Processor}
         *
         * @param <T>  Processor's process result type
         */
        static <T> Processor<T, RuntimeException> of(Function<? super StringTemplate, ? extends T> process) {
            return process::apply;
        }

        /**
         * Built-in policies using this additional interface have the flexibility to
         * specialize the composition of the templated string by returning a customized
         * {@link MethodHandle} from {@link Linkage#linkage linkage}.
         * These specializations are typically implemented to improve performance;
         * specializing value types or avoiding boxing and vararg arrays.
         *
         * @implNote This interface is sealed to only allow standard processors.
         *
         * @sealedGraph
         * @since 21
         */
        @PreviewFeature(feature=PreviewFeature.Feature.STRING_TEMPLATES)
        public sealed interface Linkage permits FormatProcessor {
            /**
             * This method creates a {@link MethodHandle} that when invoked with arguments of
             * those specified in {@code type} returns a result that equals that returned by
             * the template processor's process method. The difference being that this method
             * can preview the template's fragments and value types in advance of usage and
             * thereby has the opportunity to produce a specialized implementation.
             *
             * @param fragments  string template fragments
             * @param type       method type, includes the StringTemplate receiver as
             * well as the value types
             *
             * @return {@link MethodHandle} for the processor applied to template
             *
             * @throws NullPointerException if any of the arguments are null
             */
            MethodHandle linkage(List<String> fragments, MethodType type);
        }
    }

}
