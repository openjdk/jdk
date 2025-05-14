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
 * of tokens that are concatenated (i.e. rendered) to a String. The Templates can have "holes", which are
 * filled (replaced) by different values at each Template instantiation. For example, these "holes" can
 * be filled with different types, operators or constants. Templates can also be nested, allowing a modular
 * use of Templates.
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
 * var testTemplate = Template.make("typeName", "operator", "generator", (String typeName, String operator, MyGenerator generator) -> body(
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
 * To get an executable test, we define a Template that produces a class body with a main method. The Template
 * takes a list of types, and calls the {@code testTemplate} defined above for each type and operator. We use
 * the {@code TestFramework} to call our {@code @Test} methods.
 *
 * <p>
 * {@snippet lang=java :
 * var classTemplate = Template.make("types", (List<Type> types) -> body(
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
 *     // list of lists of FilledTemplate:
 *     types.stream().map((Type type) ->
 *         type.operators().stream().map((String operator) ->
 *             testTemplate.fillWith(type.name(), operator, type.generator())).toList()
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
 * return classTemplate.fillWith(types).render();
 * }
 *
 * <p>
 * Once we rendered the source code to a String, we can compile it with the {@code CompileFramework}.
 *
 * <p>
 * <strong>Details:</strong>
 * <p>
 * A Template can have zero or more arguments. A template can be created with {@code make} methods like
 * {@link Template#make(String, Function)}. At first, the Template is an {@link UnfilledTemplate}, i.e.
 * a Template where the arguments are not yet filled. For each number of arguments there is an implementation
 * (e.g. {@code UnfilledTemplate.TwoArgs} for two arguments). This allows the use of Generics for the
 * Template argument types, i.e. the Template arguments can be type checked.
 *
 * <p>
 * Given an {@link UnfilledTemplate}, one must apply the required number of arguments, i.e. fill
 * the Template, to arrive at a {@link FilledTemplate}. Note: {@link Template#make(Supplier)},
 * i.e. making a Template with zero arguments directly returns a {@link FilledTemplate},
 * because there are no arguments to be filled.
 *
 * <p>
 * The {@link FilledTemplate} can then be used to render to String, or for nesting inside other
 * Templates.
 *
 * <p>
 * Ideally, we would have used String Templates to inject these Template arguments into the strings.
 * But since String Templates are not (yet) available, the Templates provide <strong>hashtag replacements</strong>
 * in the Strings: the Template argument names are captured, and the argument values automatically replace any
 * {@code "#name"} in the Strings. See the different overloads of {@link #make} for examples. Additional hashtag
 * replacements can be defined with {@link #let}.
 *
 * <p>
 * When using nested Templates, there can be collisions with identifiers (e.g. variable names and method names).
 * For this, Templates provide <strong>dollar replacements</strong>, which automatically rename any
 * {@code "$name"} in the String with a {@code "name_ID"}, where the {@code "ID"} is unique for every use of
 * a Template. The dollar replacement can also be captured with {@link #$}, and passed to nested
 * Templates, which allows sharing of these identifier names between Templates.
 *
 * <p>
 * To render a Template to a {@link String}, one first has to apply the arguments (e.g. with
 * {@link UnfilledTemplate.TwoArgs#fillWith}) and then the resulting {@link FilledTemplate} can either be used as a
 * {@link Token} inside another {@link Template#body}, or rendered to a {@link String} with {@link FilledTemplate#render}.
 *
 * <p>
 * A {@link FilledTemplate} can be used directly as a {@link Token} inside the {@link Template#body} to
 * nest the Templates. Alternatively, code can be {@link Hook#insert}ed to where a {@link Hook}
 * was {@link Hook#set} earlier (in some outer scope of the code). For example, while generating code in
 * a method, one can reach out to the scope of the class, and insert a new field, or define a utility method.
 *
 * <p>
 * A {@link TemplateBinding} allows the recursive use of Templates. With the indirection of such a binding,
 * a Template can reference itself. To ensure the termination of recursion, the templates are rendered
 * with a certain amount of {@link #fuel}, which is decreased at each Template nesting by a certain amount
 * (can be changed with {@link #setFuelCost}). Recursive templates are supposed to terminate once the {@link #fuel}
 * is depleted (i.e. reaches zero).
 *
 * <p>
 * Code generation often involves defining fields and variables, which are then available inside a defined
 * scope, and can be sampled in any nested scope. To allow the use of names for multiple applications (e.g.
 * fields, variables, methods, etc.), we define a {@link Name}, which captures the {@link String} representation
 * to be used in code, as well as its type and if it is mutable. One can add such a {@link Name} to the
 * current code scope with {@link #addName}, and sample from the current or outer scopes with {@link #sampleName}.
 * When generating code, one might want to create {@link Name}s (variables, fields, etc.) in local scope, or
 * in some outer scope with the use of {@link Hook}s.
 *
 * <p>
 * More examples for these functionalities can be found in {@link TestTutorial}, {@link TestSimple}, and
 * {@link TestAdvanced}.
 */
public interface Template {

    /**
     * Creates a {@link FilledTemplate} with no arguments.
     * See {@link #body} for more details about how to construct a Template with {@link Token}s.
     *
     * <p>
     * Example:
     * {@snippet lang=java :
     * var template = Template.make(() -> body(
     *     """
     *     Multi-line string or other tokens.
     *     """
     * ));
     * }
     *
     * @param body The {@link TemplateBody} created by {@link Template#body}.
     * @return A {@link FilledTemplate} with zero arguments.
     */
    static FilledTemplate.ZeroArgs make(Supplier<TemplateBody> body) {
        return new UnfilledTemplate.ZeroArgs(body).fillWithNothing();
    }

    /**
     * Creates an {@link UnfilledTemplate} with one argument.
     * See {@link #body} for more details about how to construct a Template with {@link Token}s.
     *
     * <p>
     * Here is an example with template argument {@code 'a'}, captured once as string name
     * for use in hashtag replacements, and captured once as lambda argument with the corresponding type
     * of the generic argument.
     * {@snippet lang=java :
     * var template = Template.make("a", (Integer a) -> body(
     *     """
     *     Multi-line string or other tokens.
     *     We can use the hashtag replacement #a to directly insert the String value of a.
     *     """,
     *     "We can also use the captured parameter of a: " + a
     * ));
     * }
     *
     * @param body The {@link TemplateBody} created by {@link Template#body}.
     * @param <A> Type of the (first) argument.
     * @param arg0Name The name of the (first) argument for hashtag replacement.
     * @return An {@link UnfilledTemplate} with one argument.
     */
    static <A> UnfilledTemplate.OneArgs<A> make(String arg0Name, Function<A, TemplateBody> body) {
        return new UnfilledTemplate.OneArgs<>(arg0Name, body);
    }

    /**
     * Creates an {@link UnfilledTemplate} with two arguments.
     * See {@link #body} for more details about how to construct a Template with {@link Token}s.
     *
     * <p>
     * Here an example with template arguments {@code 'a'} and {@code 'b'}, captured once as string names
     * for use in hashtag replacements, and captured once as lambda arguments with the corresponding types
     * of the generic arguments.
     * {@snippet lang=java :
     * var template = Template.make("a", "b", (Integer a, String b) -> body(
     *     """
     *     Multi-line string or other tokens.
     *     We can use the hashtag replacement #a and #b to directly insert the String value of a and b.
     *     """,
     *     "We can also use the captured parameter of a and b: " + a + " and " + b
     * ));
     * }
     *
     * @param body The {@link TemplateBody} created by {@link Template#body}.
     * @param <A> Type of the first argument.
     * @param arg0Name The name of the first argument for hashtag replacement.
     * @param <B> Type of the second argument.
     * @param arg1Name The name of the second argument for hashtag replacement.
     * @return An {@link UnfilledTemplate} with two arguments.
     */
    static <A, B> UnfilledTemplate.TwoArgs<A, B> make(String arg0Name, String arg1Name, BiFunction<A, B, TemplateBody> body) {
        return new UnfilledTemplate.TwoArgs<>(arg0Name, arg1Name, body);
    }

    /**
     * Creates an {@link UnfilledTemplate} with three arguments.
     * See {@link #body} for more details about how to construct a Template with {@link Token}s.
     *
     * @param body The {@link TemplateBody} created by {@link Template#body}.
     * @param <A> Type of the first argument.
     * @param arg0Name The name of the first argument for hashtag replacement.
     * @param <B> Type of the second argument.
     * @param arg1Name The name of the second argument for hashtag replacement.
     * @param <C> Type of the third argument.
     * @param arg2Name The name of the third argument for hashtag replacement.
     * @return An {@link UnfilledTemplate} with three arguments.
     */
    static <A, B, C> UnfilledTemplate.ThreeArgs<A, B, C> make(String arg0Name, String arg1Name, String arg2Name, UnfilledTemplate.TriFunction<A, B, C, TemplateBody> body) {
        return new UnfilledTemplate.ThreeArgs<>(arg0Name, arg1Name, arg2Name, body);
    }

    /**
     * Creates a {@link TemplateBody} from a list of tokens, which can be {@link String}s,
     * boxed primitive types (e.g. {@link Integer} or auto-boxed {@code int}), any {@link Token},
     * or {@link List}s of any of these.
     *
     * {@snippet lang=java :
     * var template = Template.make(() -> body(
     *     """
     *     Multi-line string
     *     """,
     *     "normal string ", Integer.valueOf(3), 3, Float.valueOf(1.5f), 1.5f,
     *     List.of("abc", "def"),
     *     nestedTemplate.fillWith(42)
     * ));
     * }
     *
     * @param tokens A list of tokens, which can be {@link String}s, boxed primitive types
     *               (e.g. {@link Integer}), any {@link Token}, or {@link List}s
     *               of any of these.
     * @return The {@link TemplateBody} which captures the list of validated {@link Token}s.
     * @throws IllegalArgumentException if the list of tokens contains an unexpected object.
     */
    static TemplateBody body(Object... tokens) {
        return new TemplateBody(Token.parse(tokens));
    }

    /**
     * Retrieves the dollar replacement of the {@code 'name'} for the
     * current Template that is being instantiated. It returns the same
     * dollar replacement as the string use {@code "$name"}.
     *
     * Here an example where a Template creates a local variable {@code 'var'},
     * with an implicit dollar replacement, and then captures that dollar replacement
     * using {@link #$} for the use inside a nested template.
     * {@snippet lang=java :
     * var template = Template.make(() -> body(
     *     """
     *     int $var = 42;
     *     """,
     *     otherTemplate.fillWith($("var"))
     * ));
     * }
     *
     * @param name The {@link String} name of the name.
     * @return The dollar replacement for the {@code 'name'}.
     */
    static String $(String name) {
        return Renderer.getCurrent().$(name);
    }

    /**
     * Define a hashtag replacement for {@code "#key"}, with a specific value.
     *
     * {@snippet lang=java :
     * var template = Template.make("a", (Integer a) -> body(
     *     let("b", a * 5),
     *     """
     *     System.out.println("Use a and b with hashtag replacement: #a and #b");
     *     """
     * ));
     * }
     *
     * @param key Name for the hashtag replacement.
     * @param value The value that the hashtag is replaced with.
     * @return A token that does nothing, so that the {@link #let} can easily be put in a list of tokens
     *         inside a {@link Template#body}.
     * @throws RendererException if there is a duplicate hashtag {@code key}.
     */
    static Token let(String key, Object value) {
        Renderer.getCurrent().addHashtagReplacement(key, value);
        return new NothingToken();
    }

    /**
     * Define a hashtag replacement for {@code "#key"}, with a specific value, which is also captured
     * by the provided {@code 'function'} with type {@code <T>}.
     *
     * {@snippet lang=java :
     * var template = Template.make("a", (Integer a) -> let("b", a * 2, (Integer b) -> body(
     *     """
     *     System.out.println("Use a and b with hashtag replacement: #a and #b");
     *     """,
     *     "System.out.println(\"Use a and b as capture variables:\"" + a + " and " + b + ");\n"
     * )));
     * }
     *
     * @param key Name for the hashtag replacement.
     * @param value The value that the hashtag is replaced with.
     * @param <T> The type of the value.
     * @param function The function that is applied with the provided {@code 'value'}.
     * @return A {@link TemplateBody}.
     * @throws RendererException if there is a duplicate hashtag {@code key}.
     */
    static <T> TemplateBody let(String key, T value, Function<T, TemplateBody> function) {
        Renderer.getCurrent().addHashtagReplacement(key, value);
        return function.apply(value);
    }

    /**
     * Default amount of fuel for {@link FilledTemplate#render}. It guides the nesting depth of Templates.
     */
    public final static float DEFAULT_FUEL = 100.0f;

    /**
     * The default amount of fuel spent per Template. It is subtracted from the current {@link #fuel} at every
     * nesting level, and once the {@link #fuel} reaches zero, the nesting is supposed to terminate.
     */
    public final static float DEFAULT_FUEL_COST = 10.0f;

    /**
     * The current remaining fuel for nested Templates. Every level of Template nesting
     * subtracts a certain amount of fuel, and when it reaches zero, Templates are supposed to
     * stop nesting, if possible. This is not a hard rule, but a guide, and a mechanism to ensure
     * termination in recursive Template instantiations.
     *
     * <p>
     * Example of a recursive Template, which checks the remaining {@link #fuel} at every level,
     * and terminates if it reaches zero. It also demonstrates the use of {@link TemplateBinding} for
     * the recursive use of Templates. We {@link FilledTemplate#render} with {@code 30} total fuel, and spend {@code 5} fuel at each recursion level.
     * {@snippet lang=java :
     * var binding = new TemplateBinding<UnfilledTemplate.OneArgs<Integer>>();
     * var template = Template.make("depth", (Integer depth) -> body(
     *     setFuelCost(5.0f),
     *     let("fuel", fuel()),
     *     """
     *     System.out.println("Currently at depth #depth with fuel #fuel");
     *     """,
     *     (fuel() > 0) ? binding.get().fillWith(depth + 1)
     *                    "// terminate\n"
     * ));
     * binding.bind(template);
     * String code = template.fillWith(0).render(30.0f);
     * }
     *
     * @return The amount of fuel left for nested Template use.
     */
    static float fuel() {
        return Renderer.getCurrent().fuel();
    }

    /**
     * Changes the amount of fuel used for the current Template, where the default is
     * {@link Template#DEFAULT_FUEL_COST}.
     *
     * @param fuelCost The amount of fuel used for the current Template.
     * @return A token for convenient use in {@link Template#body}.
     */
    static Token setFuelCost(float fuelCost) {
        Renderer.getCurrent().setFuelCost(fuelCost);
        return new NothingToken();
    }

    /**
     * Add a {@link Name} in the current code frame.
     * Note that there can be duplicate definitions, and they simply increase
     * the {@link #weighNames} weight, and increase the probability of sampling
     * the name with {@link #sampleName}.
     *
     * @param name The {@link Name} to be added to the current code frame.
     * @return The token that performs the defining action.
     */
    static Token addName(Name name) {
        return new AddNameToken(name);
    }

    /**
     * Weight the {@link Name}s for the specified {@link Name.Type}.
     *
     * @param type The type of the names to weigh.
     * @param onlyMutable Determines if we weigh the mutable names or all.
     * @return The weight of names for the specified parameters.
     */
    static long weighNames(Name.Type type, boolean onlyMutable) {
        return Renderer.getCurrent().weighNames(type, onlyMutable);
    }

    /**
     * Sample a random name for the specified type.
     *
     * @param type The type of the names to sample from.
     * @param onlyMutable Determines if we sample from the mutable names or all.
     * @return The sampled name.
     */
    static Name sampleName(Name.Type type, boolean onlyMutable) {
        return Renderer.getCurrent().sampleName(type, onlyMutable);
    }
}
