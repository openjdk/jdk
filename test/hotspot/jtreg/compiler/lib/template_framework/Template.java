/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

package compiler.lib.template_framework;

import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import java.util.List;

import compiler.lib.compile_framework.CompileFramework;
import compiler.lib.ir_framework.TestFramework;

/**
 * The Template Framework allows the generation of code with Templates. The goal is that these Templates are
 * easy to write, and allow regression tests to cover a larger scope, and to make template based fuzzing easy
 * to extend.
 *
 * <p>
 * <strong>Motivation:</strong> We want to make it easy to generate variants of tests. Often, we would like to
 * have a set of tests, corresponding to a set of types, a set of operators, a set of constants, etc. Writing all
 * the tests by hand is cumbersome or even impossible. When generating such tests with scripts, it would be
 * preferable if the code generation happens automatically, and the generator script was checked into the code
 * base. Code generation can go beyond simple regression tests, and one might want to generate random code from
 * a list of possible templates, to fuzz individual Java features and compiler optimizations.
 *
 * <p>
 * The Template Framework provides a facility to generate code with Templates. A Template is essentially a list
 * of tokens that are concatenated (i.e. rendered) to a {@link String}. The Templates can have "holes", which are
 * filled (replaced) by different values at each Template instantiation. For example, these "holes" can
 * be filled with different types, operators or constants. Templates can also be nested, allowing a modular
 * use of Templates.
 *
 * <p>
 * Once we rendered the source code to a {@link String}, we can compile it with the {@link CompileFramework}.
 *
 * <p>
 * <strong>Example:</strong>
 * The following snippets are from the example test {@code TestAdvanced.java}.
 * First, we define a template that generates a {@code @Test} method for a given type, operator and
 * constant generator. We define two constants {@code con1} and {@code con2}, and then use a multiline
 * string with hashtags {@code #} (i.e. "holes")  that are then replaced by the template arguments and the
 * {@link #let} definitions.
 *
 * <p>
 * {@snippet lang=java :
 * var testTemplate = Template.make("typeName", "operator", "generator", (String typeName, String operator, MyGenerator generator) -> scope(
 *     let("con1", generator.next()),
 *     let("con2", generator.next()),
 *     """
 *     // #typeName #operator #con1 #con2
 *     public static #typeName $GOLD = $test();
 *
 *     @Test
 *     public static #typeName $test() {
 *         return (#typeName)(#con1 #operator #con2);
 *     }
 *
 *     @Check(test = "$test")
 *     public static void $check(#typeName result) {
 *         Verify.checkEQ(result, $GOLD);
 *     }
 *     """
 * ));
 * }
 *
 * <p>
 * To get an executable test, we define a {@link Template} that produces a class scope with a main method. The Template
 * takes a list of types, and calls the {@code testTemplate} defined above for each type and operator. We use
 * the {@link TestFramework} to call our {@code @Test} methods.
 *
 * <p>
 * {@snippet lang=java :
 * var classTemplate = Template.make("types", (List<Type> types) -> scope(
 *     let("classpath", comp.getEscapedClassPathOfCompiledClasses()),
 *     """
 *     package p.xyz;
 *
 *     import compiler.lib.ir_framework.*;
 *     import compiler.lib.verify.*;
 *
 *     public class InnerTest {
 *         public static void main() {
 *             // Set the classpath, so that the TestFramework test VM knows where
 *             // the CompileFramework put the class files of the compiled source code.
 *             TestFramework framework = new TestFramework(InnerTest.class);
 *             framework.addFlags("-classpath", "#classpath");
 *             framework.start();
 *         }
 *
 *     """,
 *     // Call the testTemplate for each type and operator, generating a
 *     // list of lists of TemplateToken:
 *     types.stream().map((Type type) ->
 *         type.operators().stream().map((String operator) ->
 *             testTemplate.asToken(type.name(), operator, type.generator())).toList()
 *     ).toList(),
 *     """
 *     }
 *     """
 * ));
 * }
 *
 * <p>
 * Finally, we generate the list of types, and pass it to the class template:
 *
 * <p>
 * {@snippet lang=java :
 * List<Type> types = List.of(
 *     new Type("byte",   GEN_BYTE::next,   List.of("+", "-", "*", "&", "|", "^")),
 *     new Type("char",   GEN_CHAR::next,   List.of("+", "-", "*", "&", "|", "^")),
 *     new Type("short",  GEN_SHORT::next,  List.of("+", "-", "*", "&", "|", "^")),
 *     new Type("int",    GEN_INT::next,    List.of("+", "-", "*", "&", "|", "^")),
 *     new Type("long",   GEN_LONG::next,   List.of("+", "-", "*", "&", "|", "^")),
 *     new Type("float",  GEN_FLOAT::next,  List.of("+", "-", "*", "/")),
 *     new Type("double", GEN_DOUBLE::next, List.of("+", "-", "*", "/"))
 * );
 *
 * // Use the template with one argument, and render it to a String.
 * return classTemplate.render(types);
 * }
 *
 * <p>
 * <strong>Details:</strong>
 * <p>
 * A {@link Template} can have zero or more arguments. A template can be created with {@code make} methods like
 * {@link Template#make(String, Function)}. For each number of arguments there is an implementation
 * (e.g. {@link Template.TwoArgs} for two arguments). This allows the use of generics for the
 * {@link Template} argument types which enables type checking of the {@link Template} arguments.
 * It is currently only allowed to use up to three arguments.
 *
 * <p>
 * A {@link Template} can be rendered to a {@link String} (e.g. {@link Template.ZeroArgs#render()}).
 * Alternatively, we can generate a {@link Token} (more specifically, a {@link TemplateToken}) with {@code asToken()}
 * (e.g. {@link Template.ZeroArgs#asToken()}), and use the {@link Token} inside another {@link Template#scope}.
 *
 * <p>
 * Ideally, we would have used <a href="https://openjdk.org/jeps/430">string templates</a> to inject these Template
 * arguments into the strings. But since string templates are not (yet) available, the Templates provide
 * <strong>hashtag replacements</strong> in the {@link String}s: the Template argument names are captured, and
 * the argument values automatically replace any {@code "#name"} in the {@link String}s. See the different overloads
 * of {@link #make} for examples. Additional hashtag replacements can be defined with {@link #let}.
 * We have decided to keep hashtag replacements constrained to the scope of one Template. They
 * do not escape to outer or inner Template uses. If one needs to pass values to inner Templates,
 * this can be done with Template arguments. Keeping hashtag replacements local to Templates
 * has the benefit that there is no conflict in recursive templates, where outer and inner Templates
 * define the same hashtag replacement.
 *
 * <p>
 * When using nested Templates, there can be collisions with identifiers (e.g. variable names and method names).
 * For this, Templates provide <strong>dollar replacements</strong>, which automatically rename any
 * {@code "$name"} in the {@link String} with a {@code "name_ID"}, where the {@code "ID"} is unique for every use of
 * a Template. The dollar replacement can also be captured with {@link #$}, and passed to nested
 * Templates, which allows sharing of these identifier names between Templates.
 *
 * <p>
 * The dollar and hashtag names must have at least one character. The first character must be a letter
 * or underscore (i.e. {@code a-zA-Z_}), the other characters can also be digits (i.e. {@code a-zA-Z0-9_}).
 * One can use them with or without curly braces, e.g. {@code #name}, {@code #{name}}, {@code $name}, or
 * {@code #{name}}.
 *
 * <p>
 * Code generation can involve keeping track of fields and variables, as well as the scopes in which they
 * are available, and if they are mutable or immutable. We model fields and variables with {@link DataName}s,
 * which we can add to the current scope with {@link #addDataName}. We can access the {@link DataName}s with
 * {@link #dataNames}. We can filter for {@link DataName}s of specific {@link DataName.Type}s, and then
 * we can call {@link DataName.FilteredSet#count}, {@link DataName.FilteredSet#sample},
 * {@link DataName.FilteredSet#toList}, etc. There are many use-cases for this mechanism, especially
 * facilitating communication between the code of outer and inner {@link Template}s. Especially for fuzzing,
 * it may be useful to be able to add fields and variables, and sample them randomly, to create a random data
 * flow graph.
 *
 * <p>
 * Similarly, we may want to model method and class names, and possibly other structural names. We model
 * these names with {@link StructuralName}, which works analogously to {@link DataName}, except that they
 * are not concerned about mutability.
 *
 * <p>
 * Code generation can involve keeping track of scopes in the code (e.g. liveness and availability of
 * {@link DataName}s) and of the hashtag replacements in the templates. The {@link ScopeToken} serves
 * this purpose, and allows the definition of transparent scopes (e.g. {@link #transparentScope}) and
 * non-transparent scopes (e.g. {@link #scope}).
 *
 * <table border="1">
 *   <caption>Scopes and (non-)transparency</caption>
 *   <tr>
 *     <th>                           </th><th> hashtag         </th><th> {@link DataName} and {@link StructuralName} </th><th> {@link #setFuelCost} </th>
 *   </tr>
 *   <tr>
 *     <th> {@link #scope}            </th><th> non-transparent </th><th> non-transparent                             </th><th> non-transparent     </th>
 *   </tr>
 *   <tr>
 *     <th> {@link #hashtagScope}     </th><th> non-transparent </th><th> transparent                                 </th><th> transparent         </th>
 *   </tr>
 *   <tr>
 *     <th> {@link #nameScope}        </th><th> transparent     </th><th> non-transparent                             </th><th> transparent         </th>
 *   </tr>
 *   <tr>
 *     <th> {@link #setFuelCostScope} </th><th> transparent     </th><th> transparent                                 </th><th> non-transparent     </th>
 *   </tr>
 *   <tr>
 *     <th> {@link #transparentScope} </th><th> transparent     </th><th> transparent                                 </th><th> transparent         </th>
 *   </tr>
 * </table>
 *
 * <p>
 * In some cases, we may be deeper nested in templates and scopes, and would like to reach "back" or
 * to outer scopes. This is possible with {@link Hook#anchor}ing in some outer scope, and later
 * {@link Hook#insert}ing from an inner scope to the scope of the anchoring. For example, while
 * generating code in a method, one can reach out to the scope of the class, and insert a new field,
 * or define a utility method.
 *
 * <p>
 * A {@link TemplateBinding} allows the recursive use of Templates. With the indirection of such a binding,
 * a Template can reference itself.
 *
 * <p>
 * The writer of recursive {@link Template}s must ensure that this recursion terminates. To unify the
 * approach across {@link Template}s, we introduce the concept of {@link #fuel}. Templates are rendered starting
 * with a limited amount of {@link #fuel} (default: 100, see {@link #DEFAULT_FUEL}), which is decreased at each
 * Template nesting by a certain amount (default: 10, see {@link #DEFAULT_FUEL_COST}). The default fuel for a
 * template can be changed when we {@code render()} it (e.g. {@link ZeroArgs#render(float)}) and the default
 * fuel cost with {@link #setFuelCost}) when defining the {@link #scope(Object...)}. Recursive templates are
 * supposed to terminate once the {@link #fuel} is depleted (i.e. reaches zero).
 *
 * <p>
 * A note from the implementor to the user: We have decided to implement the Template Framework using
 * a functional (lambdas) and data-oriented (tokens) model. The consequence is that there are three
 * orders in template rendering: (1) the execution order in lambdas, where we usually assemble the
 * tokens and pass them to some scope ({@link ScopeToken}) as arguments. (2) the token evaluation
 * order, which occurs in the order of how tokens are listed in a scope. By design, the token order
 * is the same order as execution in lambdas. To keep the lambda and token order in sync, most of the
 * queries about the state of code generation, such as {@link DataName}s and {@link Hook}s cannot
 * return the values immediately, but have to be expressed as tokens. If we had a mix of tokens and
 * immediate queries, then the immediate queries would "float" by the tokens, because the immediate
 * queries are executed during the lambda execution, but the tokens are only executed later. Having
 * to express everything as tokens can be a little more cumbersome (e.g. sample requires a lambda
 * that captures the {@link DataName}, and sample does not return the {@link DataName} directly).
 * But this ensures that reasoning about execution order is relatively straight forward, namely in
 * the order of the specified tokens. (3) the final code order is the same as the lambda and token
 * order, except when using {@link Hook#insert}, which places the code at the innermost {@link Hook#anchor}.
 *
 * <p>
 * More examples for these functionalities can be found in {@code TestTutorial.java}, {@code TestSimple.java},
 * and {@code TestAdvanced.java}, which all produce compilable Java code. Additional examples can be found in
 * the tests, such as {@code TestTemplate.java} and {@code TestFormat.java}, which do not necessarily generate
 * valid Java code, but generate deterministic Strings which are easier to verify, and may also serve as a
 * reference when learning about these functionalities.
 */
public sealed interface Template permits Template.ZeroArgs,
                                         Template.OneArg,
                                         Template.TwoArgs,
                                         Template.ThreeArgs {

    /**
     * A {@link Template} with no arguments.
     *
     * @param function The {@link Supplier} that creates the {@link ScopeToken}.
     */
    record ZeroArgs(Supplier<ScopeToken> function) implements Template {
        ScopeToken instantiate() {
            return function.get();
        }

        /**
         * Creates a {@link TemplateToken} which can be used as a {@link Token} inside
         * a {@link Template} for nested code generation.
         *
         * @return The {@link TemplateToken} to use the {@link Template} inside another
         *         {@link Template}.
         */
        public TemplateToken asToken() {
            return new TemplateToken.ZeroArgs(this);
        }

        /**
         * Renders the {@link Template} to a {@link String}.
         *
         * @return The {@link String}, resulting from rendering the {@link Template}.
         */
        public String render() {
            return new TemplateToken.ZeroArgs(this).render();
        }

        /**
         * Renders the {@link Template} to a {@link String}.
         *
         * @param fuel The amount of fuel provided for recursive Template instantiations.
         * @return The {@link String}, resulting from rendering the {@link Template}.
         */
        public String render(float fuel) {
            return new TemplateToken.ZeroArgs(this).render(fuel);
        }
    }

    /**
     * A {@link Template} with one argument.
     *
     * @param arg1Name The name of the (first) argument, used for hashtag replacements in the {@link Template}.
     * @param <T1> The type of the (first) argument.
     * @param function The {@link Function} that creates the {@link ScopeToken} given the template argument.
     */
    record OneArg<T1>(String arg1Name, Function<T1, ScopeToken> function) implements Template {
        ScopeToken instantiate(T1 arg1) {
            return function.apply(arg1);
        }

        /**
         * Creates a {@link TemplateToken} which can be used as a {@link Token} inside
         * a {@link Template} for nested code generation.
         *
         * @param arg1 The value for the (first) argument.
         * @return The {@link TemplateToken} to use the {@link Template} inside another
         *         {@link Template}.
         */
        public TemplateToken asToken(T1 arg1) {
            return new TemplateToken.OneArg<>(this, arg1);
        }

        /**
         * Renders the {@link Template} to a {@link String}.
         *
         * @param arg1 The value for the first argument.
         * @return The {@link String}, resulting from rendering the {@link Template}.
         */
        public String render(T1 arg1) {
            return new TemplateToken.OneArg<>(this, arg1).render();
        }

        /**
         * Renders the {@link Template} to a {@link String}.
         *
         * @param arg1 The value for the first argument.
         * @param fuel The amount of fuel provided for recursive Template instantiations.
         * @return The {@link String}, resulting from rendering the {@link Template}.
         */
        public String render(float fuel, T1 arg1) {
            return new TemplateToken.OneArg<>(this, arg1).render(fuel);
        }
    }

    /**
     * A {@link Template} with two arguments.
     *
     * @param arg1Name The name of the first argument, used for hashtag replacements in the {@link Template}.
     * @param arg2Name The name of the second argument, used for hashtag replacements in the {@link Template}.
     * @param <T1> The type of the first argument.
     * @param <T2> The type of the second argument.
     * @param function The {@link BiFunction} that creates the {@link ScopeToken} given the template arguments.
     */
    record TwoArgs<T1, T2>(String arg1Name, String arg2Name, BiFunction<T1, T2, ScopeToken> function) implements Template {
        ScopeToken instantiate(T1 arg1, T2 arg2) {
            return function.apply(arg1, arg2);
        }

        /**
         * Creates a {@link TemplateToken} which can be used as a {@link Token} inside
         * a {@link Template} for nested code generation.
         *
         * @param arg1 The value for the first argument.
         * @param arg2 The value for the second argument.
         * @return The {@link TemplateToken} to use the {@link Template} inside another
         *         {@link Template}.
         */
        public TemplateToken asToken(T1 arg1, T2 arg2) {
            return new TemplateToken.TwoArgs<>(this, arg1, arg2);
        }

        /**
         * Renders the {@link Template} to a {@link String}.
         *
         * @param arg1 The value for the first argument.
         * @param arg2 The value for the second argument.
         * @return The {@link String}, resulting from rendering the {@link Template}.
         */
        public String render(T1 arg1, T2 arg2) {
            return new TemplateToken.TwoArgs<>(this, arg1, arg2).render();
        }

        /**
         * Renders the {@link Template} to a {@link String}.
         *
         * @param arg1 The value for the first argument.
         * @param arg2 The value for the second argument.
         * @param fuel The amount of fuel provided for recursive Template instantiations.
         * @return The {@link String}, resulting from rendering the {@link Template}.
         */
        public String render(float fuel, T1 arg1, T2 arg2) {
            return new TemplateToken.TwoArgs<>(this, arg1, arg2).render(fuel);
        }
    }

    /**
     * Interface for function with three arguments.
     *
     * @param <T> Type of the first argument.
     * @param <U> Type of the second argument.
     * @param <V> Type of the third argument.
     * @param <R> Type of the return value.
     */
    @FunctionalInterface
    interface TriFunction<T, U, V, R> {

        /**
         * Function definition for the three argument functions.
         *
         * @param t The first argument.
         * @param u The second argument.
         * @param v The third argument.
         * @return Return value of the three argument function.
         */
        R apply(T t, U u, V v);
    }

    /**
     * A {@link Template} with three arguments.
     *
     * @param arg1Name The name of the first argument, used for hashtag replacements in the {@link Template}.
     * @param arg2Name The name of the second argument, used for hashtag replacements in the {@link Template}.
     * @param arg3Name The name of the third argument, used for hashtag replacements in the {@link Template}.
     * @param <T1> The type of the first argument.
     * @param <T2> The type of the second argument.
     * @param <T3> The type of the third argument.
     * @param function The function with three arguments that creates the {@link ScopeToken} given the template arguments.
     */
    record ThreeArgs<T1, T2, T3>(String arg1Name, String arg2Name, String arg3Name, TriFunction<T1, T2, T3, ScopeToken> function) implements Template {
        ScopeToken instantiate(T1 arg1, T2 arg2, T3 arg3) {
            return function.apply(arg1, arg2, arg3);
        }

        /**
         * Creates a {@link TemplateToken} which can be used as a {@link Token} inside
         * a {@link Template} for nested code generation.
         *
         * @param arg1 The value for the first argument.
         * @param arg2 The value for the second argument.
         * @param arg3 The value for the third argument.
         * @return The {@link TemplateToken} to use the {@link Template} inside another
         *         {@link Template}.
         */
        public TemplateToken asToken(T1 arg1, T2 arg2, T3 arg3) {
            return new TemplateToken.ThreeArgs<>(this, arg1, arg2, arg3);
        }

        /**
         * Renders the {@link Template} to a {@link String}.
         *
         * @param arg1 The value for the first argument.
         * @param arg2 The value for the second argument.
         * @param arg3 The value for the third argument.
         * @return The {@link String}, resulting from rendering the {@link Template}.
         */
        public String render(T1 arg1, T2 arg2, T3 arg3) {
            return new TemplateToken.ThreeArgs<>(this, arg1, arg2, arg3).render();
        }

        /**
         * Renders the {@link Template} to a {@link String}.
         *
         * @param arg1 The value for the first argument.
         * @param arg2 The value for the second argument.
         * @param arg3 The value for the third argument.
         * @param fuel The amount of fuel provided for recursive Template instantiations.
         * @return The {@link String}, resulting from rendering the {@link Template}.
         */
        public String render(float fuel, T1 arg1, T2 arg2, T3 arg3) {
            return new TemplateToken.ThreeArgs<>(this, arg1, arg2, arg3).render(fuel);
        }
    }

    /**
     * Creates a {@link Template} with no arguments.
     * See {@link #scope} for more details about how to construct a Template with {@link Token}s.
     *
     * <p>
     * Example:
     * {@snippet lang=java :
     * var template = Template.make(() -> scope(
     *     """
     *     Multi-line string or other tokens.
     *     """
     * ));
     * }
     *
     * @param scope The {@link ScopeToken} created by {@link Template#scope}.
     * @return A {@link Template} with zero arguments.
     */
    static Template.ZeroArgs make(Supplier<ScopeToken> scope) {
        return new Template.ZeroArgs(scope);
    }

    /**
     * Creates a {@link Template} with one argument.
     * See {@link #scope} for more details about how to construct a Template with {@link Token}s.
     * Good practice but not enforced but not enforced: {@code arg1Name} should match the lambda argument name.
     *
     * <p>
     * Here is an example with template argument {@code 'a'}, captured once as string name
     * for use in hashtag replacements, and captured once as lambda argument with the corresponding type
     * of the generic argument.
     * {@snippet lang=java :
     * var template = Template.make("a", (Integer a) -> scope(
     *     """
     *     Multi-line string or other tokens.
     *     We can use the hashtag replacement #a to directly insert the String value of a.
     *     """,
     *     "We can also use the captured parameter of a: " + a
     * ));
     * }
     *
     * @param scope The {@link ScopeToken} created by {@link Template#scope}.
     * @param <T1> Type of the (first) argument.
     * @param arg1Name The name of the (first) argument for hashtag replacement.
     * @return A {@link Template} with one argument.
     */
    static <T1> Template.OneArg<T1> make(String arg1Name, Function<T1, ScopeToken> scope) {
        return new Template.OneArg<>(arg1Name, scope);
    }

    /**
     * Creates a {@link Template} with two arguments.
     * See {@link #scope} for more details about how to construct a Template with {@link Token}s.
     * Good practice but not enforced: {@code arg1Name} and {@code arg2Name} should match the lambda argument names.
     *
     * <p>
     * Here is an example with template arguments {@code 'a'} and {@code 'b'}, captured once as string names
     * for use in hashtag replacements, and captured once as lambda arguments with the corresponding types
     * of the generic arguments.
     * {@snippet lang=java :
     * var template = Template.make("a", "b", (Integer a, String b) -> scope(
     *     """
     *     Multi-line string or other tokens.
     *     We can use the hashtag replacement #a and #b to directly insert the String value of a and b.
     *     """,
     *     "We can also use the captured parameter of a and b: " + a + " and " + b
     * ));
     * }
     *
     * @param scope The {@link ScopeToken} created by {@link Template#scope}.
     * @param <T1> Type of the first argument.
     * @param arg1Name The name of the first argument for hashtag replacement.
     * @param <T2> Type of the second argument.
     * @param arg2Name The name of the second argument for hashtag replacement.
     * @return A {@link Template} with two arguments.
     */
    static <T1, T2> Template.TwoArgs<T1, T2> make(String arg1Name, String arg2Name, BiFunction<T1, T2, ScopeToken> scope) {
        return new Template.TwoArgs<>(arg1Name, arg2Name, scope);
    }

    /**
     * Creates a {@link Template} with three arguments.
     * See {@link #scope} for more details about how to construct a Template with {@link Token}s.
     * Good practice but not enforced: {@code arg1Name}, {@code arg2Name}, and {@code arg3Name} should match the lambda argument names.
     *
     * @param scope The {@link ScopeToken} created by {@link Template#scope}.
     * @param <T1> Type of the first argument.
     * @param arg1Name The name of the first argument for hashtag replacement.
     * @param <T2> Type of the second argument.
     * @param arg2Name The name of the second argument for hashtag replacement.
     * @param <T3> Type of the third argument.
     * @param arg3Name The name of the third argument for hashtag replacement.
     * @return A {@link Template} with three arguments.
     */
    static <T1, T2, T3> Template.ThreeArgs<T1, T2, T3> make(String arg1Name, String arg2Name, String arg3Name, Template.TriFunction<T1, T2, T3, ScopeToken> scope) {
        return new Template.ThreeArgs<>(arg1Name, arg2Name, arg3Name, scope);
    }

    /**
     * Creates a {@link ScopeToken} that represents a scope that is completely
     * non-transparent, <strong>not</strong> allowing anything to escape. This
     * means that no {@link DataName}, {@link StructuralName}s, hashtag-replacement
     * or {@link #setFuelCost} defined inside the scope is available outside. All
     * these usages are only local to the defining scope here.
     *
     * <p>
     * The scope is formed from a list of tokens, which can be {@link String}s,
     * boxed primitive types (for example {@link Integer} or auto-boxed {@code int}),
     * any {@link Token}, or {@link List}s of any of these.
     *
     * <p>
     * If you require a scope that is either fully transparent (i.e. everything escapes)
     * or only restricts a specific kind to not escape, consider using one of the other
     * provided scopes: {@link #transparentScope}, {@link #nameScope}, {@link #hashtagScope},
     * or {@link #setFuelCostScope}. A "scope-transparency-matrix" can also be found in
     * the interface comment for {@link Template}.
     *
     * <p>
     * The most common use of {@link #scope} is in the construction of templates:
     *
     * <p>
     * {@snippet lang=java :
     * var template = Template.make(() -> scope(
     *     """
     *     Multi-line string
     *     """,
     *     "normal string ", Integer.valueOf(3), 3, Float.valueOf(1.5f), 1.5f,
     *     List.of("abc", "def"),
     *     nestedTemplate.asToken(42)
     * ));
     * }
     *
     * <p>
     * Note that regardless of the chosen scope for {@code Template.make},
     * hashtag-replacements and {@link #setFuelCost} are always implicitly
     * non-transparent (i.e. non-escaping). For example, {@link #let} will
     * not escape the template scope even when using {@link #transparentScope}.
     * As a default, it is recommended to use {@link #scope} for
     * {@code Template.make} since in most cases template scopes align with
     * code scopes that are non-transparent for fields, variables, etc. In
     * rare cases, where the scope of the template needs to be transparent
     * (e.g. because we need to insert a variable or field into an outer scope),
     * it is recommended to use {@link #transparentScope}. This allows to make
     * {@link DataName}s and {@link StructuralName}s available outside this
     * template crossing the template boundary.
     *
     * <p>
     * We can also use nested scopes inside of templates:
     *
     * <p>
     * {@snippet lang=java :
     * var template = Template.make(() -> scope(
     *     // CODE1: some code in the outer scope
     *     scope(
     *       // CODE2: some code in the inner scope. Names, hashtags and setFuelCost
     *       //        do not escape the inner scope.
     *     ),
     *     // CODE3: more code in the outer scope, names and hashtags from CODE2 are
     *     //        not available anymore because of the non-transparent "scope".
     *     transparentScope(
     *       // CODE4: some code in the inner "transparentScope". Names, hashtags and setFuelCost
     *       //        escape the "transparentScope" and are still available after the "transparentScope"
     *       //        closes.
     *     )
     *     // CODE5: we still have access to names and hashtags from CODE4.
     * ));
     * }
     *
     * @param tokens A list of tokens, which can be {@link String}s, boxed primitive types
     *               (for example {@link Integer}), any {@link Token}, or {@link List}s
     *               of any of these.
     * @return The {@link ScopeToken} which captures the list of validated {@link Token}s.
     * @throws IllegalArgumentException if the list of tokens contains an unexpected object.
     */
    static ScopeToken scope(Object... tokens) {
        return new ScopeTokenImpl(TokenParser.parse(tokens), false, false, false);
    }

    /**
     * Creates a {@link ScopeToken} that represents a completely transparent scope.
     * This means that {@link DataName}s, {@link StructuralName}s,
     * hashtag-replacements and {@link #setFuelCost} declared inside the scope will be available
     * in the outer scope.
     * The scope is formed from a list of tokens, which can be {@link String}s,
     * boxed primitive types (for example {@link Integer} or auto-boxed {@code int}),
     * any {@link Token}, or {@link List}s of any of these.
     *
     * <p>
     * If you require a scope that is non-transparent (i.e. nothing escapes) or only restricts
     * a specific kind to not escape, consider using one of the other provided scopes:
     * {@link #scope}, {@link #nameScope}, {@link #hashtagScope}, or {@link #setFuelCostScope}.
     * A "scope-transparency-matrix" can also be found in the interface comment for {@link Template}.
     *
     * @param tokens A list of tokens, which can be {@link String}s, boxed primitive types
     *               (for example {@link Integer}), any {@link Token}, or {@link List}s
     *               of any of these.
     * @return The {@link ScopeToken} which captures the list of validated {@link Token}s.
     * @throws IllegalArgumentException if the list of tokens contains an unexpected object.
     */
    static ScopeToken transparentScope(Object... tokens) {
        return new ScopeTokenImpl(TokenParser.parse(tokens), true, true, true);
    }

    /**
     * Creates a {@link ScopeToken} that represents a scope that is non-transparent for
     * {@link DataName}s and {@link StructuralName}s (i.e. cannot escape), but
     * transparent for hashtag-replacements and {@link #setFuelCost} (i.e. available
     * in outer scope).
     *
     * <p>
     * The scope is formed from a list of tokens, which can be {@link String}s,
     * boxed primitive types (for example {@link Integer} or auto-boxed {@code int}),
     * any {@link Token}, or {@link List}s of any of these.
     *
     * <p>
     * If you require a scope that is transparent or uses a different restriction, consider
     * using one of the other provided scopes: {@link #scope}, {@link #transparentScope},
     * {@link #hashtagScope}, or {@link #setFuelCostScope}. A "scope-transparency-matrix" can
     * also be found in the interface comment for {@link Template}.
     *
     * @param tokens A list of tokens, which can be {@link String}s, boxed primitive types
     *               (for example {@link Integer}), any {@link Token}, or {@link List}s
     *               of any of these.
     * @return The {@link ScopeToken} which captures the list of validated {@link Token}s.
     * @throws IllegalArgumentException if the list of tokens contains an unexpected object.
     */
    static ScopeToken nameScope(Object... tokens) {
        return new ScopeTokenImpl(TokenParser.parse(tokens), false, true, true);
    }

    /**
     * Creates a {@link ScopeToken} that represents a scope that is non-transparent for
     * hashtag-replacements (i.e. cannot escape), but transparent for {@link DataName}s
     * and {@link StructuralName}s and {@link #setFuelCost} (i.e. available in outer scope).
     *
     * <p>
     * The scope is formed from a list of tokens, which can be {@link String}s,
     * boxed primitive types (for example {@link Integer} or auto-boxed {@code int}),
     * any {@link Token}, or {@link List}s of any of these.
     *
     * <p>
     * If you require a scope that is transparent or uses a different restriction, consider
     * using one of the other provided scopes: {@link #scope}, {@link #transparentScope},
     * {@link #nameScope}, or {@link #setFuelCostScope}. A "scope-transparency-matrix" can
     * also be found in the interface comment for {@link Template}.
     *
     * <p>
     * Keeping hashtag-replacements local but letting {@link DataName}s escape can be
     * useful in cases like the following, where we may want to reuse the hashtag
     * multiple times:
     *
     * <p>
     * {@snippet lang=java :
     * var template = Template.make(() -> scope(
     *     List.of("a", "b", "c").stream().map(name -> hashtagScope(
     *         let("name", name), // assumes values: a, b, c
     *         addDataName(name, PrimitiveType.INTS, MUTABLE), // escapes
     *         """
     *         int #name = 42;
     *         """
     *     ))
     *     // We still have access to the three DataNames.
     * ));
     * }
     *
     * @param tokens A list of tokens, which can be {@link String}s, boxed primitive types
     *               (for example {@link Integer}), any {@link Token}, or {@link List}s
     *               of any of these.
     * @return The {@link ScopeToken} which captures the list of validated {@link Token}s.
     * @throws IllegalArgumentException if the list of tokens contains an unexpected object.
     */
    static ScopeToken hashtagScope(Object... tokens) {
        return new ScopeTokenImpl(TokenParser.parse(tokens), true, false, true);
    }

    /**
     * Creates a {@link ScopeToken} that represents a scope that is non-transparent for
     * {@link #setFuelCost} (i.e. cannot escape), but transparent for hashtag-replacements,
     * {@link DataName}s and {@link StructuralName}s (i.e. available in outer scope).
     * The scope is formed from a list of tokens, which can be {@link String}s,
     * boxed primitive types (for example {@link Integer} or auto-boxed {@code int}),
     * any {@link Token}, or {@link List}s of any of these.
     *
     * <p>
     * If you require a scope that is transparent or uses a different restriction, consider
     * using one of the other provided scopes: {@link #scope}, {@link #transparentScope},
     * {@link #hashtagScope}, or {@link #nameScope}. A "scope-transparency-matrix" can
     * also be found in the interface comment for {@link Template}.
     *
     * <p>
     * In some cases, it can be helpful to have different {@link #setFuelCost} within
     * a single template, depending on the code nesting depth. Example:
     *
     * <p>
     * {@snippet lang=java :
     * var template = Template.make(() -> scope(
     *     setFuelCost(1),
     *     // CODE1: some shallow code, allowing recursive template uses here
     *     //        to use more fuel.
     *     """
     *     for (int i = 0; i < 1000; i++) {
     *     """,
     *     setFuelCostScope(
     *         setFuelCost(100)
     *         // CODE2: with the for-loop, we already have a deeper nesting
     *         //        depth, and recursive template uses should not get
     *         //        as much fuel as in CODE1.
     *     ),
     *     """
     *     }
     *     """
     *     // CODE3: we are back in the outer scope of CODE1, and can use
     *     //        more fuel again in nested template uses. setFuelCost
     *     //        is automatically restored to what was set before the
     *     //        inner scope.
     * ));
     * }
     *
     * @param tokens A list of tokens, which can be {@link String}s, boxed primitive types
     *               (for example {@link Integer}), any {@link Token}, or {@link List}s
     *               of any of these.
     * @return The {@link ScopeToken} which captures the list of validated {@link Token}s.
     * @throws IllegalArgumentException if the list of tokens contains an unexpected object.
     */
    static ScopeToken setFuelCostScope(Object... tokens) {
        return new ScopeTokenImpl(TokenParser.parse(tokens), true, true, false);
    }

    /**
     * Retrieves the dollar replacement of the {@code 'name'} for the
     * current Template that is being instantiated. It returns the same
     * dollar replacement as the string use {@code "$name"}.
     *
     * <p>
     * Here is an example where a Template creates a local variable {@code 'var'},
     * with an implicit dollar replacement, and then captures that dollar replacement
     * using {@link #$} for the use inside a nested template.
     * {@snippet lang=java :
     * var template = Template.make(() -> scope(
     *     """
     *     int $var = 42;
     *     """,
     *     otherTemplate.asToken($("var"))
     * ));
     * }
     *
     * @param name The {@link String} name of the name.
     * @return The dollar replacement for the {@code 'name'}.
     */
    static String $(String name) {
        // Note, since the dollar replacements do not change within a template
        // and the retrieval has no side effects, we can return the value immediately,
        // and do not need a token.
        return Renderer.getCurrent().$(name);
    }

    /**
     * Define a hashtag replacement for {@code "#key"}, with a specific value.
     *
     * <p>
     * {@snippet lang=java :
     * var template = Template.make("a", (Integer a) -> scope(
     *     let("b", a * 5),
     *     """
     *     System.out.println("Use a and b with hashtag replacement: #a and #b");
     *     """
     * ));
     * }
     *
     * <p>
     * Note that a {@code let} definition makes the hashtag replacement available
     * for anything that follows it, until the the end of the next outer scope
     * that is non-transparent for hashtag replacements. Additionally, hashtag
     * replacements are limited to the template they were defined in.
     * If you want to pass values from an outer to an inner template, this cannot
     * be done with hashtags directly. Instead, one has to pass the values via
     * template arguments.
     *
     * @param key Name for the hashtag replacement.
     * @param value The value that the hashtag is replaced with.
     * @return A token that represents the hashtag replacement definition.
     */
    static Token let(String key, Object value) {
        return new LetToken(key, value, v -> transparentScope());
    }

    /**
     * Define a hashtag replacement for {@code "#key"}, with a specific value, which is also captured
     * by the provided {@code function} with type {@code <T>}. While the argument of the lambda that
     * captures the value is naturally bounded to the scope of the lambda, the hashtag replacement
     * may be bound to the scope or escape it, depending on the choice of scope, see {@link #scope}
     * and {@link #transparentScope}.
     *
     * <p>
     * {@snippet lang=java :
     * var template = Template.make("a", (Integer a) -> scope(
     *     let("b", a * 2, (Integer b) -> scope(
     *         """
     *         System.out.println("Use a and b with hashtag replacement: #a and #b");
     *         """,
     *         "System.out.println(\"Use a and b as capture variables:\"" + a + " and " + b + ");\n"
     *     ))
     * ));
     * }
     *
     * @param key Name for the hashtag replacement.
     * @param value The value that the hashtag is replaced with.
     * @param <T> The type of the value.
     * @param function The function that is applied with the provided {@code value}.
     * @return A {@link Token} representing the hashtag replacement definition and inner scope.
     */
    static <T> Token let(String key, T value, Function<T, ScopeToken> function) {
        return new LetToken(key, value, function);
    }

    /**
     * Default amount of fuel for Template rendering. It guides the nesting depth of Templates. Can be changed when
     * rendering a template with {@code render(fuel)} (e.g. {@link ZeroArgs#render(float)}).
     */
    float DEFAULT_FUEL = 100.0f;

    /**
     * The default amount of fuel spent per Template. It is subtracted from the current {@link #fuel} at every
     * nesting level, and once the {@link #fuel} reaches zero, the nesting is supposed to terminate. Can be changed
     * with {@link #setFuelCost(float)} inside {@link #scope(Object...)}.
     */
    float DEFAULT_FUEL_COST = 10.0f;

    /**
     * The current remaining fuel for nested Templates. Every level of Template nesting
     * subtracts a certain amount of fuel, and when it reaches zero, Templates are supposed to
     * stop nesting, if possible. This is not a hard rule, but a guide, and a mechanism to ensure
     * termination in recursive Template instantiations.
     *
     * <p>
     * Example of a recursive Template, which checks the remaining {@link #fuel} at every level,
     * and terminates if it reaches zero. It also demonstrates the use of {@link TemplateBinding} for
     * the recursive use of Templates. We {@link Template.OneArg#render} with {@code 30} total fuel,
     * and spend {@code 5} fuel at each recursion level.
     *
     * <p>
     * {@snippet lang=java :
     * var binding = new TemplateBinding<Template.OneArg<Integer>>();
     * var template = Template.make("depth", (Integer depth) -> scope(
     *     setFuelCost(5.0f),
     *     let("fuel", fuel()),
     *     """
     *     System.out.println("Currently at depth #depth with fuel #fuel");
     *     """,
     *     (fuel() > 0) ? binding.get().asToken(depth + 1) :
     *                    "// terminate\n"
     * ));
     * binding.bind(template);
     * String code = template.render(30.0f, 0);
     * }
     *
     * @return The amount of fuel left for nested Template use.
     */
    static float fuel() {
        // Note, since the fuel amount does not change within a template
        // and the retrieval has no side effects, we can return the value immediately,
        // and do not need a token.
        return Renderer.getCurrent().fuel();
    }

    /**
     * Changes the amount of fuel used for the current Template, where the default is
     * {@link Template#DEFAULT_FUEL_COST}.
     *
     * @param fuelCost The amount of fuel used for the current Template.
     * @return A token for convenient use in {@link Template#scope}.
     */
    static Token setFuelCost(float fuelCost) {
        return new SetFuelCostToken(fuelCost);
    }

    /**
     * Add a {@link DataName} in the current {@link #scope}.
     * If the current scope is transparent to {@link DataName}s, it escapes to the next
     * outer scope that is non-transparent, and is available for everything that follows
     * the {@code addDataName} until the end of that non-transparent scope.
     *
     * @param name The name of the {@link DataName}, i.e. the {@link String} used in code.
     * @param type The type of the {@link DataName}.
     * @param mutability Indicates if the {@link DataName} is to be mutable or immutable,
     *                   i.e. if we intend to use the {@link DataName} only for reading
     *                   or if we also allow it to be mutated.
     * @param weight The weight of the {@link DataName}, which correlates to the probability
     *               of this {@link DataName} being chosen when we sample.
     *               Must be a value from 1 to 1000.
     * @return The token that performs the defining action.
     */
    static Token addDataName(String name, DataName.Type type, DataName.Mutability mutability, int weight) {
        if (mutability != DataName.Mutability.MUTABLE &&
            mutability != DataName.Mutability.IMMUTABLE) {
            throw new IllegalArgumentException("Unexpected mutability: " + mutability);
        }
        boolean mutable = mutability == DataName.Mutability.MUTABLE;
        if (weight <= 0 || 1000 < weight) {
            throw new IllegalArgumentException("Unexpected weight: " + weight);
        }
        return new AddNameToken(new DataName(name, type, mutable, weight));
    }

    /**
     * Add a {@link DataName} in the current {@link #scope}, with a {@code weight} of 1.
     * If the current scope is transparent to {@link DataName}s, it escapes to the next
     * outer scope that is non-transparent, and is available for everything that follows
     * the {@code addDataName} until the end of that non-transparent scope.
     *
     * @param name The name of the {@link DataName}, i.e. the {@link String} used in code.
     * @param type The type of the {@link DataName}.
     * @param mutability Indicates if the {@link DataName} is to be mutable or immutable,
     *                   i.e. if we intend to use the {@link DataName} only for reading
     *                   or if we also allow it to be mutated.
     * @return The token that performs the defining action.
     */
    static Token addDataName(String name, DataName.Type type, DataName.Mutability mutability) {
        return addDataName(name, type, mutability, 1);
    }

    /**
     * Access the set of {@link DataName}s, for sampling, counting, etc.
     *
     * @param mutability Indicates if we only sample from mutable, immutable or either {@link DataName}s.
     * @return A view on the {@link DataName}s, on which we can sample, count, etc.
     */
    static DataName.FilteredSet dataNames(DataName.Mutability mutability) {
        return new DataName.FilteredSet(mutability);
    }

    /**
     * Add a {@link StructuralName} in the current {@link #scope}.
     * If the current scope is transparent to {@link StructuralName}s, it escapes to the next
     * outer scope that is non-transparent, and is available for everything that follows
     * the {@code addStructuralName} until the end of that non-transparent scope.
     *
     * @param name The name of the {@link StructuralName}, i.e. the {@link String} used in code.
     * @param type The type of the {@link StructuralName}.
     * @param weight The weight of the {@link StructuralName}, which correlates to the probability
     *               of this {@link StructuralName} being chosen when we sample.
     *               Must be a value from 1 to 1000.
     * @return The token that performs the defining action.
     */
    static Token addStructuralName(String name, StructuralName.Type type, int weight) {
        if (weight <= 0 || 1000 < weight) {
            throw new IllegalArgumentException("Unexpected weight: " + weight);
        }
        return new AddNameToken(new StructuralName(name, type, weight));
    }

    /**
     * Add a {@link StructuralName} in the current {@link #scope}, with a {@code weight} of 1.
     * If the current scope is transparent to {@link StructuralName}s, it escapes to the next
     * outer scope that is non-transparent, and is available for everything that follows
     * the {@code addStructuralName} until the end of that non-transparent scope.
     *
     * @param name The name of the {@link StructuralName}, i.e. the {@link String} used in code.
     * @param type The type of the {@link StructuralName}.
     * @return The token that performs the defining action.
     */
    static Token addStructuralName(String name, StructuralName.Type type) {
        return addStructuralName(name, type, 1);
    }

    /**
     * Access the set of {@link StructuralName}s, for sampling, counting, etc.
     *
     * @return A view on the {@link StructuralName}s, on which we can sample, count, etc.
     */
    static StructuralName.FilteredSet structuralNames() {
        return new StructuralName.FilteredSet();
    }
}
