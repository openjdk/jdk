/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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

package java.lang.invoke;

import jdk.internal.misc.Unsafe;
import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.Label;
import jdk.internal.org.objectweb.asm.MethodVisitor;
import jdk.internal.org.objectweb.asm.Opcodes;
import jdk.internal.vm.annotation.ForceInline;
import sun.invoke.util.Wrapper;
import sun.security.action.GetPropertyAction;

import java.lang.invoke.MethodHandles.Lookup;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

import static jdk.internal.org.objectweb.asm.Opcodes.*;

/**
 * <p>Methods to facilitate the creation of String concatenation methods, that
 * can be used to efficiently concatenate a known number of arguments of known
 * types, possibly after type adaptation and partial evaluation of arguments.
 * These methods are typically used as <em>bootstrap methods</em> for {@code
 * invokedynamic} call sites, to support the <em>string concatenation</em>
 * feature of the Java Programming Language.
 *
 * <p>Indirect access to the behavior specified by the provided {@code
 * MethodHandle} proceeds in order through two phases:
 *
 * <ol>
 *     <li><em>Linkage</em> occurs when the methods in this class are invoked.
 * They take as arguments a method type describing the concatenated arguments
 * count and types, and optionally the String <em>recipe</em>, plus the
 * constants that participate in the String concatenation. The details on
 * accepted recipe shapes are described further below. Linkage may involve
 * dynamically loading a new class that implements the expected concatenation
 * behavior. The {@code CallSite} holds the {@code MethodHandle} pointing to the
 * exact concatenation method. The concatenation methods may be shared among
 * different {@code CallSite}s, e.g. if linkage methods produce them as pure
 * functions.</li>
 *
 * <li><em>Invocation</em> occurs when a generated concatenation method is
 * invoked with the exact dynamic arguments. This may occur many times for a
 * single concatenation method. The method referenced by the behavior {@code
 * MethodHandle} is invoked with the static arguments and any additional dynamic
 * arguments provided on invocation, as if by {@link MethodHandle#invoke(Object...)}.</li>
 * </ol>
 *
 * <p> This class provides two forms of linkage methods: a simple version
 * ({@link #makeConcat(java.lang.invoke.MethodHandles.Lookup, String,
 * MethodType)}) using only the dynamic arguments, and an advanced version
 * ({@link #makeConcatWithConstants(java.lang.invoke.MethodHandles.Lookup,
 * String, MethodType, String, Object...)} using the advanced forms of capturing
 * the constant arguments. The advanced strategy can produce marginally better
 * invocation bytecode, at the expense of exploding the number of shapes of
 * string concatenation methods present at runtime, because those shapes would
 * include constant static arguments as well.
 *
 * @author Aleksey Shipilev
 * @author Remi Forax
 * @author Peter Levart
 *
 * @apiNote
 * <p>There is a JVM limit (classfile structural constraint): no method
 * can call with more than 255 slots. This limits the number of static and
 * dynamic arguments one can pass to bootstrap method. Since there are potential
 * concatenation strategies that use {@code MethodHandle} combinators, we need
 * to reserve a few empty slots on the parameter lists to capture the
 * temporal results. This is why bootstrap methods in this factory do not accept
 * more than 200 argument slots. Users requiring more than 200 argument slots in
 * concatenation are expected to split the large concatenation in smaller
 * expressions.
 *
 * @since 9
 */
public final class StringConcatFactory {

    /**
     * Tag used to demarcate an ordinary argument.
     */
    private static final char TAG_ARG = '\u0001';

    /**
     * Tag used to demarcate a constant.
     */
    private static final char TAG_CONST = '\u0002';

    /**
     * Maximum number of argument slots in String Concat call.
     *
     * While the maximum number of argument slots that indy call can handle is 253,
     * we do not use all those slots, to let the strategies with MethodHandle
     * combinators to use some arguments.
     */
    private static final int MAX_INDY_CONCAT_ARG_SLOTS = 200;

    /**
     * Concatenation strategy to use. See {@link Strategy} for possible options.
     * This option is controllable with -Djava.lang.invoke.stringConcat JDK option.
     */
    private static Strategy STRATEGY;

    /**
     * Default strategy to use for concatenation.
     */
    private static final Strategy DEFAULT_STRATEGY = Strategy.MH_INLINE_SIZED_EXACT;

    private enum Strategy {
        /**
         * Bytecode generator, calling into {@link java.lang.StringBuilder}.
         */
        BC_SB,

        /**
         * Bytecode generator, calling into {@link java.lang.StringBuilder};
         * but trying to estimate the required storage.
         */
        BC_SB_SIZED,

        /**
         * Bytecode generator, calling into {@link java.lang.StringBuilder};
         * but computing the required storage exactly.
         */
        BC_SB_SIZED_EXACT,

        /**
         * MethodHandle-based generator, that in the end calls into {@link java.lang.StringBuilder}.
         * This strategy also tries to estimate the required storage.
         */
        MH_SB_SIZED,

        /**
         * MethodHandle-based generator, that in the end calls into {@link java.lang.StringBuilder}.
         * This strategy also estimate the required storage exactly.
         */
        MH_SB_SIZED_EXACT,

        /**
         * MethodHandle-based generator, that constructs its own byte[] array from
         * the arguments. It computes the required storage exactly.
         */
        MH_INLINE_SIZED_EXACT
    }

    /**
     * Enables debugging: this may print debugging messages, perform additional (non-neutral for performance)
     * checks, etc.
     */
    private static final boolean DEBUG;

    /**
     * Enables caching of strategy stubs. This may improve the linkage time by reusing the generated
     * code, at the expense of contaminating the profiles.
     */
    private static final boolean CACHE_ENABLE;

    private static final ConcurrentMap<Key, MethodHandle> CACHE;

    /**
     * Dump generated classes to disk, for debugging purposes.
     */
    private static final ProxyClassesDumper DUMPER;

    static {
        // In case we need to double-back onto the StringConcatFactory during this
        // static initialization, make sure we have the reasonable defaults to complete
        // the static initialization properly. After that, actual users would use
        // the proper values we have read from the properties.
        STRATEGY = DEFAULT_STRATEGY;
        // CACHE_ENABLE = false; // implied
        // CACHE = null;         // implied
        // DEBUG = false;        // implied
        // DUMPER = null;        // implied

        Properties props = GetPropertyAction.privilegedGetProperties();
        final String strategy =
                props.getProperty("java.lang.invoke.stringConcat");
        CACHE_ENABLE = Boolean.parseBoolean(
                props.getProperty("java.lang.invoke.stringConcat.cache"));
        DEBUG = Boolean.parseBoolean(
                props.getProperty("java.lang.invoke.stringConcat.debug"));
        final String dumpPath =
                props.getProperty("java.lang.invoke.stringConcat.dumpClasses");

        STRATEGY = (strategy == null) ? DEFAULT_STRATEGY : Strategy.valueOf(strategy);
        CACHE = CACHE_ENABLE ? new ConcurrentHashMap<>() : null;
        DUMPER = (dumpPath == null) ? null : ProxyClassesDumper.getInstance(dumpPath);
    }

    /**
     * Cache key is a composite of:
     *   - class name, that lets to disambiguate stubs, to avoid excess sharing
     *   - method type, describing the dynamic arguments for concatenation
     *   - concat recipe, describing the constants and concat shape
     */
    private static final class Key {
        final String className;
        final MethodType mt;
        final Recipe recipe;

        public Key(String className, MethodType mt, Recipe recipe) {
            this.className = className;
            this.mt = mt;
            this.recipe = recipe;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Key key = (Key) o;

            if (!className.equals(key.className)) return false;
            if (!mt.equals(key.mt)) return false;
            if (!recipe.equals(key.recipe)) return false;
            return true;
        }

        @Override
        public int hashCode() {
            int result = className.hashCode();
            result = 31 * result + mt.hashCode();
            result = 31 * result + recipe.hashCode();
            return result;
        }
    }

    /**
     * Parses the recipe string, and produces the traversable collection of
     * {@link java.lang.invoke.StringConcatFactory.RecipeElement}-s for generator
     * strategies. Notably, this class parses out the constants from the recipe
     * and from other static arguments.
     */
    private static final class Recipe {
        private final List<RecipeElement> elements;

        public Recipe(String src, Object[] constants) {
            List<RecipeElement> el = new ArrayList<>();

            int constC = 0;
            int argC = 0;

            StringBuilder acc = new StringBuilder();

            for (int i = 0; i < src.length(); i++) {
                char c = src.charAt(i);

                if (c == TAG_CONST || c == TAG_ARG) {
                    // Detected a special tag, flush all accumulated characters
                    // as a constant first:
                    if (acc.length() > 0) {
                        el.add(new RecipeElement(acc.toString()));
                        acc.setLength(0);
                    }
                    if (c == TAG_CONST) {
                        Object cnst = constants[constC++];
                        el.add(new RecipeElement(cnst));
                    } else if (c == TAG_ARG) {
                        el.add(new RecipeElement(argC++));
                    }
                } else {
                    // Not a special character, this is a constant embedded into
                    // the recipe itself.
                    acc.append(c);
                }
            }

            // Flush the remaining characters as constant:
            if (acc.length() > 0) {
                el.add(new RecipeElement(acc.toString()));
            }

            elements = el;
        }

        public List<RecipeElement> getElements() {
            return elements;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Recipe recipe = (Recipe) o;
            return elements.equals(recipe.elements);
        }

        @Override
        public String toString() {
            return "Recipe{" +
                    "elements=" + elements +
                    '}';
        }

        @Override
        public int hashCode() {
            return elements.hashCode();
        }
    }

    private static final class RecipeElement {
        private final String value;
        private final int argPos;
        private final char tag;

        public RecipeElement(Object cnst) {
            this.value = String.valueOf(Objects.requireNonNull(cnst));
            this.argPos = -1;
            this.tag = TAG_CONST;
        }

        public RecipeElement(int arg) {
            this.value = null;
            this.argPos = arg;
            this.tag = TAG_ARG;
        }

        public String getValue() {
            assert (tag == TAG_CONST);
            return value;
        }

        public int getArgPos() {
            assert (tag == TAG_ARG);
            return argPos;
        }

        public char getTag() {
            return tag;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            RecipeElement that = (RecipeElement) o;

            if (this.tag != that.tag) return false;
            if (this.tag == TAG_CONST && (!value.equals(that.value))) return false;
            if (this.tag == TAG_ARG && (argPos != that.argPos)) return false;
            return true;
        }

        @Override
        public String toString() {
            return "RecipeElement{" +
                    "value='" + value + '\'' +
                    ", argPos=" + argPos +
                    ", tag=" + tag +
                    '}';
        }

        @Override
        public int hashCode() {
            return (int)tag;
        }
    }

    // StringConcatFactory bootstrap methods are startup sensitive, and may be
    // special cased in java.lang.invokeBootstrapMethodInvoker to ensure
    // methods are invoked with exact type information to avoid generating
    // code for runtime checks. Take care any changes or additions here are
    // reflected there as appropriate.

    /**
     * Facilitates the creation of optimized String concatenation methods, that
     * can be used to efficiently concatenate a known number of arguments of
     * known types, possibly after type adaptation and partial evaluation of
     * arguments. Typically used as a <em>bootstrap method</em> for {@code
     * invokedynamic} call sites, to support the <em>string concatenation</em>
     * feature of the Java Programming Language.
     *
     * <p>When the target of the {@code CallSite} returned from this method is
     * invoked, it returns the result of String concatenation, taking all
     * function arguments passed to the linkage method as inputs for
     * concatenation. The target signature is given by {@code concatType}.
     * For a target accepting:
     * <ul>
     *     <li>zero inputs, concatenation results in an empty string;</li>
     *     <li>one input, concatenation results in the single
     *     input converted as per JLS 5.1.11 "String Conversion"; otherwise</li>
     *     <li>two or more inputs, the inputs are concatenated as per
     *     requirements stated in JLS 15.18.1 "String Concatenation Operator +".
     *     The inputs are converted as per JLS 5.1.11 "String Conversion",
     *     and combined from left to right.</li>
     * </ul>
     *
     * <p>Assume the linkage arguments are as follows:
     *
     * <ul>
     *     <li>{@code concatType}, describing the {@code CallSite} signature</li>
     * </ul>
     *
     * <p>Then the following linkage invariants must hold:
     *
     * <ul>
     *     <li>The number of parameter slots in {@code concatType} is
     *         less than or equal to 200</li>
     *     <li>The return type in {@code concatType} is assignable from {@link java.lang.String}</li>
     * </ul>
     *
     * @param lookup   Represents a lookup context with the accessibility
     *                 privileges of the caller. Specifically, the lookup
     *                 context must have
     *                 <a href="MethodHandles.Lookup.html#privacc">private access</a>
     *                 privileges.
     *                 When used with {@code invokedynamic}, this is stacked
     *                 automatically by the VM.
     * @param name     The name of the method to implement. This name is
     *                 arbitrary, and has no meaning for this linkage method.
     *                 When used with {@code invokedynamic}, this is provided by
     *                 the {@code NameAndType} of the {@code InvokeDynamic}
     *                 structure and is stacked automatically by the VM.
     * @param concatType The expected signature of the {@code CallSite}.  The
     *                   parameter types represent the types of concatenation
     *                   arguments; the return type is always assignable from {@link
     *                   java.lang.String}.  When used with {@code invokedynamic},
     *                   this is provided by the {@code NameAndType} of the {@code
     *                   InvokeDynamic} structure and is stacked automatically by
     *                   the VM.
     * @return a CallSite whose target can be used to perform String
     * concatenation, with dynamic concatenation arguments described by the given
     * {@code concatType}.
     * @throws StringConcatException If any of the linkage invariants described
     *                               here are violated, or the lookup context
     *                               does not have private access privileges.
     * @throws NullPointerException If any of the incoming arguments is null.
     *                              This will never happen when a bootstrap method
     *                              is called with invokedynamic.
     *
     * @jls  5.1.11 String Conversion
     * @jls 15.18.1 String Concatenation Operator +
     */
    public static CallSite makeConcat(MethodHandles.Lookup lookup,
                                      String name,
                                      MethodType concatType) throws StringConcatException {
        if (DEBUG) {
            System.out.println("StringConcatFactory " + STRATEGY + " is here for " + concatType);
        }

        return doStringConcat(lookup, name, concatType, true, null);
    }

    /**
     * Facilitates the creation of optimized String concatenation methods, that
     * can be used to efficiently concatenate a known number of arguments of
     * known types, possibly after type adaptation and partial evaluation of
     * arguments. Typically used as a <em>bootstrap method</em> for {@code
     * invokedynamic} call sites, to support the <em>string concatenation</em>
     * feature of the Java Programming Language.
     *
     * <p>When the target of the {@code CallSite} returned from this method is
     * invoked, it returns the result of String concatenation, taking all
     * function arguments and constants passed to the linkage method as inputs for
     * concatenation. The target signature is given by {@code concatType}, and
     * does not include constants.
     * For a target accepting:
     * <ul>
     *     <li>zero inputs, concatenation results in an empty string;</li>
     *     <li>one input, concatenation results in the single
     *     input converted as per JLS 5.1.11 "String Conversion"; otherwise</li>
     *     <li>two or more inputs, the inputs are concatenated as per
     *     requirements stated in JLS 15.18.1 "String Concatenation Operator +".
     *     The inputs are converted as per JLS 5.1.11 "String Conversion",
     *     and combined from left to right.</li>
     * </ul>
     *
     * <p>The concatenation <em>recipe</em> is a String description for the way to
     * construct a concatenated String from the arguments and constants. The
     * recipe is processed from left to right, and each character represents an
     * input to concatenation. Recipe characters mean:
     *
     * <ul>
     *
     *   <li><em>\1 (Unicode point 0001)</em>: an ordinary argument. This
     *   input is passed through dynamic argument, and is provided during the
     *   concatenation method invocation. This input can be null.</li>
     *
     *   <li><em>\2 (Unicode point 0002):</em> a constant. This input passed
     *   through static bootstrap argument. This constant can be any value
     *   representable in constant pool. If necessary, the factory would call
     *   {@code toString} to perform a one-time String conversion.</li>
     *
     *   <li><em>Any other char value:</em> a single character constant.</li>
     * </ul>
     *
     * <p>Assume the linkage arguments are as follows:
     *
     * <ul>
     *   <li>{@code concatType}, describing the {@code CallSite} signature</li>
     *   <li>{@code recipe}, describing the String recipe</li>
     *   <li>{@code constants}, the vararg array of constants</li>
     * </ul>
     *
     * <p>Then the following linkage invariants must hold:
     *
     * <ul>
     *   <li>The number of parameter slots in {@code concatType} is less than
     *       or equal to 200</li>
     *
     *   <li>The parameter count in {@code concatType} equals to number of \1 tags
     *   in {@code recipe}</li>
     *
     *   <li>The return type in {@code concatType} is assignable
     *   from {@link java.lang.String}, and matches the return type of the
     *   returned {@link MethodHandle}</li>
     *
     *   <li>The number of elements in {@code constants} equals to number of \2
     *   tags in {@code recipe}</li>
     * </ul>
     *
     * @param lookup    Represents a lookup context with the accessibility
     *                  privileges of the caller. Specifically, the lookup
     *                  context must have
     *                  <a href="MethodHandles.Lookup.html#privacc">private access</a>
     *                  privileges.
     *                  When used with {@code invokedynamic}, this is stacked
     *                  automatically by the VM.
     * @param name      The name of the method to implement. This name is
     *                  arbitrary, and has no meaning for this linkage method.
     *                  When used with {@code invokedynamic}, this is provided
     *                  by the {@code NameAndType} of the {@code InvokeDynamic}
     *                  structure and is stacked automatically by the VM.
     * @param concatType The expected signature of the {@code CallSite}.  The
     *                  parameter types represent the types of dynamic concatenation
     *                  arguments; the return type is always assignable from {@link
     *                  java.lang.String}.  When used with {@code
     *                  invokedynamic}, this is provided by the {@code
     *                  NameAndType} of the {@code InvokeDynamic} structure and
     *                  is stacked automatically by the VM.
     * @param recipe    Concatenation recipe, described above.
     * @param constants A vararg parameter representing the constants passed to
     *                  the linkage method.
     * @return a CallSite whose target can be used to perform String
     * concatenation, with dynamic concatenation arguments described by the given
     * {@code concatType}.
     * @throws StringConcatException If any of the linkage invariants described
     *                               here are violated, or the lookup context
     *                               does not have private access privileges.
     * @throws NullPointerException If any of the incoming arguments is null, or
     *                              any constant in {@code recipe} is null.
     *                              This will never happen when a bootstrap method
     *                              is called with invokedynamic.
     * @apiNote Code generators have three distinct ways to process a constant
     * string operand S in a string concatenation expression.  First, S can be
     * materialized as a reference (using ldc) and passed as an ordinary argument
     * (recipe '\1'). Or, S can be stored in the constant pool and passed as a
     * constant (recipe '\2') . Finally, if S contains neither of the recipe
     * tag characters ('\1', '\2') then S can be interpolated into the recipe
     * itself, causing its characters to be inserted into the result.
     *
     * @jls  5.1.11 String Conversion
     * @jls 15.18.1 String Concatenation Operator +
     */
    public static CallSite makeConcatWithConstants(MethodHandles.Lookup lookup,
                                                   String name,
                                                   MethodType concatType,
                                                   String recipe,
                                                   Object... constants) throws StringConcatException {
        if (DEBUG) {
            System.out.println("StringConcatFactory " + STRATEGY + " is here for " + concatType + ", {" + recipe + "}, " + Arrays.toString(constants));
        }

        return doStringConcat(lookup, name, concatType, false, recipe, constants);
    }

    private static CallSite doStringConcat(MethodHandles.Lookup lookup,
                                           String name,
                                           MethodType concatType,
                                           boolean generateRecipe,
                                           String recipe,
                                           Object... constants) throws StringConcatException {
        Objects.requireNonNull(lookup, "Lookup is null");
        Objects.requireNonNull(name, "Name is null");
        Objects.requireNonNull(concatType, "Concat type is null");
        Objects.requireNonNull(constants, "Constants are null");

        for (Object o : constants) {
            Objects.requireNonNull(o, "Cannot accept null constants");
        }

        if ((lookup.lookupModes() & MethodHandles.Lookup.PRIVATE) == 0) {
            throw new StringConcatException("Invalid caller: " +
                    lookup.lookupClass().getName());
        }

        int cCount = 0;
        int oCount = 0;
        if (generateRecipe) {
            // Mock the recipe to reuse the concat generator code
            char[] value = new char[concatType.parameterCount()];
            Arrays.fill(value, TAG_ARG);
            recipe = new String(value);
            oCount = concatType.parameterCount();
        } else {
            Objects.requireNonNull(recipe, "Recipe is null");

            for (int i = 0; i < recipe.length(); i++) {
                char c = recipe.charAt(i);
                if (c == TAG_CONST) cCount++;
                if (c == TAG_ARG)   oCount++;
            }
        }

        if (oCount != concatType.parameterCount()) {
            throw new StringConcatException(
                    "Mismatched number of concat arguments: recipe wants " +
                            oCount +
                            " arguments, but signature provides " +
                            concatType.parameterCount());
        }

        if (cCount != constants.length) {
            throw new StringConcatException(
                    "Mismatched number of concat constants: recipe wants " +
                            cCount +
                            " constants, but only " +
                            constants.length +
                            " are passed");
        }

        if (!concatType.returnType().isAssignableFrom(String.class)) {
            throw new StringConcatException(
                    "The return type should be compatible with String, but it is " +
                            concatType.returnType());
        }

        if (concatType.parameterSlotCount() > MAX_INDY_CONCAT_ARG_SLOTS) {
            throw new StringConcatException("Too many concat argument slots: " +
                    concatType.parameterSlotCount() +
                    ", can only accept " +
                    MAX_INDY_CONCAT_ARG_SLOTS);
        }

        String className = getClassName(lookup.lookupClass());
        MethodType mt = adaptType(concatType);
        Recipe rec = new Recipe(recipe, constants);

        MethodHandle mh;
        if (CACHE_ENABLE) {
            Key key = new Key(className, mt, rec);
            mh = CACHE.get(key);
            if (mh == null) {
                mh = generate(lookup, className, mt, rec);
                CACHE.put(key, mh);
            }
        } else {
            mh = generate(lookup, className, mt, rec);
        }
        return new ConstantCallSite(mh.asType(concatType));
    }

    /**
     * Adapt method type to an API we are going to use.
     *
     * This strips the concrete classes from the signatures, thus preventing
     * class leakage when we cache the concatenation stubs.
     *
     * @param args actual argument types
     * @return argument types the strategy is going to use
     */
    private static MethodType adaptType(MethodType args) {
        Class<?>[] ptypes = null;
        for (int i = 0; i < args.parameterCount(); i++) {
            Class<?> ptype = args.parameterType(i);
            if (!ptype.isPrimitive() &&
                    ptype != String.class &&
                    ptype != Object.class) { // truncate to Object
                if (ptypes == null) {
                    ptypes = args.parameterArray();
                }
                ptypes[i] = Object.class;
            }
            // else other primitives or String or Object (unchanged)
        }
        return (ptypes != null)
                ? MethodType.methodType(args.returnType(), ptypes)
                : args;
    }

    private static String getClassName(Class<?> hostClass) throws StringConcatException {
        /*
          When cache is enabled, we want to cache as much as we can.

          However, there are two peculiarities:

           a) The generated class should stay within the same package as the
              host class, to allow Unsafe.defineAnonymousClass access controls
              to work properly. JDK may choose to fail with IllegalAccessException
              when accessing a VM anonymous class with non-privileged callers,
              see JDK-8058575.

           b) If we mark the stub with some prefix, say, derived from the package
              name because of (a), we can technically use that stub in other packages.
              But the call stack traces would be extremely puzzling to unsuspecting users
              and profiling tools: whatever stub wins the race, would be linked in all
              similar callsites.

           Therefore, we set the class prefix to match the host class package, and use
           the prefix as the cache key too. This only affects BC_* strategies, and only when
           cache is enabled.
         */

        switch (STRATEGY) {
            case BC_SB:
            case BC_SB_SIZED:
            case BC_SB_SIZED_EXACT: {
                if (CACHE_ENABLE) {
                    String pkgName = hostClass.getPackageName();
                    return (pkgName != null && !pkgName.isEmpty() ? pkgName.replace('.', '/') + "/" : "") + "Stubs$$StringConcat";
                } else {
                    return hostClass.getName().replace('.', '/') + "$$StringConcat";
                }
            }
            case MH_SB_SIZED:
            case MH_SB_SIZED_EXACT:
            case MH_INLINE_SIZED_EXACT:
                // MethodHandle strategies do not need a class name.
                return "";
            default:
                throw new StringConcatException("Concatenation strategy " + STRATEGY + " is not implemented");
        }
    }

    private static MethodHandle generate(Lookup lookup, String className, MethodType mt, Recipe recipe) throws StringConcatException {
        try {
            switch (STRATEGY) {
                case BC_SB:
                    return BytecodeStringBuilderStrategy.generate(lookup, className, mt, recipe, Mode.DEFAULT);
                case BC_SB_SIZED:
                    return BytecodeStringBuilderStrategy.generate(lookup, className, mt, recipe, Mode.SIZED);
                case BC_SB_SIZED_EXACT:
                    return BytecodeStringBuilderStrategy.generate(lookup, className, mt, recipe, Mode.SIZED_EXACT);
                case MH_SB_SIZED:
                    return MethodHandleStringBuilderStrategy.generate(mt, recipe, Mode.SIZED);
                case MH_SB_SIZED_EXACT:
                    return MethodHandleStringBuilderStrategy.generate(mt, recipe, Mode.SIZED_EXACT);
                case MH_INLINE_SIZED_EXACT:
                    return MethodHandleInlineCopyStrategy.generate(mt, recipe);
                default:
                    throw new StringConcatException("Concatenation strategy " + STRATEGY + " is not implemented");
            }
        } catch (Error | StringConcatException e) {
            // Pass through any error or existing StringConcatException
            throw e;
        } catch (Throwable t) {
            throw new StringConcatException("Generator failed", t);
        }
    }

    private enum Mode {
        DEFAULT(false, false),
        SIZED(true, false),
        SIZED_EXACT(true, true);

        private final boolean sized;
        private final boolean exact;

        Mode(boolean sized, boolean exact) {
            this.sized = sized;
            this.exact = exact;
        }

        boolean isSized() {
            return sized;
        }

        boolean isExact() {
            return exact;
        }
    }

    /**
     * Bytecode StringBuilder strategy.
     *
     * <p>This strategy operates in three modes, gated by {@link Mode}.
     *
     * <p><b>{@link Strategy#BC_SB}: "bytecode StringBuilder".</b>
     *
     * <p>This strategy spins up the bytecode that has the same StringBuilder
     * chain javac would otherwise emit. This strategy uses only the public API,
     * and comes as the baseline for the current JDK behavior. On other words,
     * this strategy moves the javac generated bytecode to runtime. The
     * generated bytecode is loaded via Unsafe.defineAnonymousClass, but with
     * the caller class coming from the BSM -- in other words, the protection
     * guarantees are inherited from the method where invokedynamic was
     * originally called. This means, among other things, that the bytecode is
     * verified for all non-JDK uses.
     *
     * <p><b>{@link Strategy#BC_SB_SIZED}: "bytecode StringBuilder, but
     * sized".</b>
     *
     * <p>This strategy acts similarly to {@link Strategy#BC_SB}, but it also
     * tries to guess the capacity required for StringBuilder to accept all
     * arguments without resizing. This strategy only makes an educated guess:
     * it only guesses the space required for known types (e.g. primitives and
     * Strings), but does not otherwise convert arguments. Therefore, the
     * capacity estimate may be wrong, and StringBuilder may have to
     * transparently resize or trim when doing the actual concatenation. While
     * this does not constitute a correctness issue (in the end, that what BC_SB
     * has to do anyway), this does pose a potential performance problem.
     *
     * <p><b>{@link Strategy#BC_SB_SIZED_EXACT}: "bytecode StringBuilder, but
     * sized exactly".</b>
     *
     * <p>This strategy improves on @link Strategy#BC_SB_SIZED}, by first
     * converting all arguments to String in order to get the exact capacity
     * StringBuilder should have. The conversion is done via the public
     * String.valueOf and/or Object.toString methods, and does not touch any
     * private String API.
     */
    private static final class BytecodeStringBuilderStrategy {
        static final Unsafe UNSAFE = Unsafe.getUnsafe();
        static final int CLASSFILE_VERSION = 52;
        static final String METHOD_NAME = "concat";

        private BytecodeStringBuilderStrategy() {
            // no instantiation
        }

        private static MethodHandle generate(Lookup lookup, String className, MethodType args, Recipe recipe, Mode mode) throws Exception {
            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS + ClassWriter.COMPUTE_FRAMES);

            cw.visit(CLASSFILE_VERSION,
                    ACC_SUPER + ACC_PUBLIC + ACC_FINAL + ACC_SYNTHETIC,
                    className,  // Unsafe.defineAnonymousClass would append an unique ID
                    null,
                    "java/lang/Object",
                    null
            );

            MethodVisitor mv = cw.visitMethod(
                    ACC_PUBLIC + ACC_STATIC + ACC_FINAL,
                    METHOD_NAME,
                    args.toMethodDescriptorString(),
                    null,
                    null);

            mv.visitAnnotation("Ljdk/internal/vm/annotation/ForceInline;", true);
            mv.visitCode();

            Class<?>[] arr = args.parameterArray();
            boolean[] guaranteedNonNull = new boolean[arr.length];

            if (mode.isExact()) {
                /*
                    In exact mode, we need to convert all arguments to their String representations,
                    as this allows to compute their String sizes exactly. We cannot use private
                    methods for primitives in here, therefore we need to convert those as well.

                    We also record what arguments are guaranteed to be non-null as the result
                    of the conversion. String.valueOf does the null checks for us. The only
                    corner case to take care of is String.valueOf(Object) returning null itself.

                    Also, if any conversion happened, then the slot indices in the incoming
                    arguments are not equal to the final local maps. The only case this may break
                    is when converting 2-slot long/double argument to 1-slot String. Therefore,
                    we get away with tracking modified offset, since no conversion can overwrite
                    the upcoming the argument.
                 */

                int off = 0;
                int modOff = 0;
                for (int c = 0; c < arr.length; c++) {
                    Class<?> cl = arr[c];
                    if (cl == String.class) {
                        if (off != modOff) {
                            mv.visitIntInsn(getLoadOpcode(cl), off);
                            mv.visitIntInsn(ASTORE, modOff);
                        }
                    } else {
                        mv.visitIntInsn(getLoadOpcode(cl), off);
                        mv.visitMethodInsn(
                                INVOKESTATIC,
                                "java/lang/String",
                                "valueOf",
                                getStringValueOfDesc(cl),
                                false
                        );
                        mv.visitIntInsn(ASTORE, modOff);
                        arr[c] = String.class;
                        guaranteedNonNull[c] = cl.isPrimitive();
                    }
                    off += getParameterSize(cl);
                    modOff += getParameterSize(String.class);
                }
            }

            if (mode.isSized()) {
                /*
                    When operating in sized mode (this includes exact mode), it makes sense to make
                    StringBuilder append chains look familiar to OptimizeStringConcat. For that, we
                    need to do null-checks early, not make the append chain shape simpler.
                 */

                int off = 0;
                for (RecipeElement el : recipe.getElements()) {
                    switch (el.getTag()) {
                        case TAG_CONST:
                            // Guaranteed non-null, no null check required.
                            break;
                        case TAG_ARG:
                            // Null-checks are needed only for String arguments, and when a previous stage
                            // did not do implicit null-checks. If a String is null, we eagerly replace it
                            // with "null" constant. Note, we omit Objects here, because we don't call
                            // .length() on them down below.
                            int ac = el.getArgPos();
                            Class<?> cl = arr[ac];
                            if (cl == String.class && !guaranteedNonNull[ac]) {
                                Label l0 = new Label();
                                mv.visitIntInsn(ALOAD, off);
                                mv.visitJumpInsn(IFNONNULL, l0);
                                mv.visitLdcInsn("null");
                                mv.visitIntInsn(ASTORE, off);
                                mv.visitLabel(l0);
                            }
                            off += getParameterSize(cl);
                            break;
                        default:
                            throw new StringConcatException("Unhandled tag: " + el.getTag());
                    }
                }
            }

            // Prepare StringBuilder instance
            mv.visitTypeInsn(NEW, "java/lang/StringBuilder");
            mv.visitInsn(DUP);

            if (mode.isSized()) {
                /*
                    Sized mode requires us to walk through the arguments, and estimate the final length.
                    In exact mode, this will operate on Strings only. This code would accumulate the
                    final length on stack.
                 */
                int len = 0;
                int off = 0;

                mv.visitInsn(ICONST_0);

                for (RecipeElement el : recipe.getElements()) {
                    switch (el.getTag()) {
                        case TAG_CONST:
                            len += el.getValue().length();
                            break;
                        case TAG_ARG:
                            /*
                                If an argument is String, then we can call .length() on it. Sized/Exact modes have
                                converted arguments for us. If an argument is primitive, we can provide a guess
                                for its String representation size.
                            */
                            Class<?> cl = arr[el.getArgPos()];
                            if (cl == String.class) {
                                mv.visitIntInsn(ALOAD, off);
                                mv.visitMethodInsn(
                                        INVOKEVIRTUAL,
                                        "java/lang/String",
                                        "length",
                                        "()I",
                                        false
                                );
                                mv.visitInsn(IADD);
                            } else if (cl.isPrimitive()) {
                                len += estimateSize(cl);
                            }
                            off += getParameterSize(cl);
                            break;
                        default:
                            throw new StringConcatException("Unhandled tag: " + el.getTag());
                    }
                }

                // Constants have non-zero length, mix in
                if (len > 0) {
                    iconst(mv, len);
                    mv.visitInsn(IADD);
                }

                mv.visitMethodInsn(
                        INVOKESPECIAL,
                        "java/lang/StringBuilder",
                        "<init>",
                        "(I)V",
                        false
                );
            } else {
                mv.visitMethodInsn(
                        INVOKESPECIAL,
                        "java/lang/StringBuilder",
                        "<init>",
                        "()V",
                        false
                );
            }

            // At this point, we have a blank StringBuilder on stack, fill it in with .append calls.
            {
                int off = 0;
                for (RecipeElement el : recipe.getElements()) {
                    String desc;
                    switch (el.getTag()) {
                        case TAG_CONST:
                            mv.visitLdcInsn(el.getValue());
                            desc = getSBAppendDesc(String.class);
                            break;
                        case TAG_ARG:
                            Class<?> cl = arr[el.getArgPos()];
                            mv.visitVarInsn(getLoadOpcode(cl), off);
                            off += getParameterSize(cl);
                            desc = getSBAppendDesc(cl);
                            break;
                        default:
                            throw new StringConcatException("Unhandled tag: " + el.getTag());
                    }

                    mv.visitMethodInsn(
                            INVOKEVIRTUAL,
                            "java/lang/StringBuilder",
                            "append",
                            desc,
                            false
                    );
                }
            }

            if (DEBUG && mode.isExact()) {
                /*
                    Exactness checks compare the final StringBuilder.capacity() with a resulting
                    String.length(). If these values disagree, that means StringBuilder had to perform
                    storage trimming, which defeats the purpose of exact strategies.
                 */

                /*
                   The logic for this check is as follows:

                     Stack before:     Op:
                      (SB)              dup, dup
                      (SB, SB, SB)      capacity()
                      (int, SB, SB)     swap
                      (SB, int, SB)     toString()
                      (S, int, SB)      length()
                      (int, int, SB)    if_icmpeq
                      (SB)              <end>

                   Note that it leaves the same StringBuilder on exit, like the one on enter.
                 */

                mv.visitInsn(DUP);
                mv.visitInsn(DUP);

                mv.visitMethodInsn(
                        INVOKEVIRTUAL,
                        "java/lang/StringBuilder",
                        "capacity",
                        "()I",
                        false
                );

                mv.visitInsn(SWAP);

                mv.visitMethodInsn(
                        INVOKEVIRTUAL,
                        "java/lang/StringBuilder",
                        "toString",
                        "()Ljava/lang/String;",
                        false
                );

                mv.visitMethodInsn(
                        INVOKEVIRTUAL,
                        "java/lang/String",
                        "length",
                        "()I",
                        false
                );

                Label l0 = new Label();
                mv.visitJumpInsn(IF_ICMPEQ, l0);

                mv.visitTypeInsn(NEW, "java/lang/AssertionError");
                mv.visitInsn(DUP);
                mv.visitLdcInsn("Failed exactness check");
                mv.visitMethodInsn(INVOKESPECIAL,
                        "java/lang/AssertionError",
                        "<init>",
                        "(Ljava/lang/Object;)V",
                        false);
                mv.visitInsn(ATHROW);

                mv.visitLabel(l0);
            }

            mv.visitMethodInsn(
                    INVOKEVIRTUAL,
                    "java/lang/StringBuilder",
                    "toString",
                    "()Ljava/lang/String;",
                    false
            );

            mv.visitInsn(ARETURN);

            mv.visitMaxs(-1, -1);
            mv.visitEnd();
            cw.visitEnd();

            byte[] classBytes = cw.toByteArray();
            try {
                Class<?> hostClass = lookup.lookupClass();
                Class<?> innerClass = UNSAFE.defineAnonymousClass(hostClass, classBytes, null);
                UNSAFE.ensureClassInitialized(innerClass);
                dumpIfEnabled(innerClass.getName(), classBytes);
                return Lookup.IMPL_LOOKUP.findStatic(innerClass, METHOD_NAME, args);
            } catch (Exception e) {
                dumpIfEnabled(className + "$$FAILED", classBytes);
                throw new StringConcatException("Exception while spinning the class", e);
            }
        }

        private static void dumpIfEnabled(String name, byte[] bytes) {
            if (DUMPER != null) {
                DUMPER.dumpClass(name, bytes);
            }
        }

        private static String getSBAppendDesc(Class<?> cl) {
            if (cl.isPrimitive()) {
                if (cl == Integer.TYPE || cl == Byte.TYPE || cl == Short.TYPE) {
                    return "(I)Ljava/lang/StringBuilder;";
                } else if (cl == Boolean.TYPE) {
                    return "(Z)Ljava/lang/StringBuilder;";
                } else if (cl == Character.TYPE) {
                    return "(C)Ljava/lang/StringBuilder;";
                } else if (cl == Double.TYPE) {
                    return "(D)Ljava/lang/StringBuilder;";
                } else if (cl == Float.TYPE) {
                    return "(F)Ljava/lang/StringBuilder;";
                } else if (cl == Long.TYPE) {
                    return "(J)Ljava/lang/StringBuilder;";
                } else {
                    throw new IllegalStateException("Unhandled primitive StringBuilder.append: " + cl);
                }
            } else if (cl == String.class) {
                return "(Ljava/lang/String;)Ljava/lang/StringBuilder;";
            } else {
                return "(Ljava/lang/Object;)Ljava/lang/StringBuilder;";
            }
        }

        private static String getStringValueOfDesc(Class<?> cl) {
            if (cl.isPrimitive()) {
                if (cl == Integer.TYPE || cl == Byte.TYPE || cl == Short.TYPE) {
                    return "(I)Ljava/lang/String;";
                } else if (cl == Boolean.TYPE) {
                    return "(Z)Ljava/lang/String;";
                } else if (cl == Character.TYPE) {
                    return "(C)Ljava/lang/String;";
                } else if (cl == Double.TYPE) {
                    return "(D)Ljava/lang/String;";
                } else if (cl == Float.TYPE) {
                    return "(F)Ljava/lang/String;";
                } else if (cl == Long.TYPE) {
                    return "(J)Ljava/lang/String;";
                } else {
                    throw new IllegalStateException("Unhandled String.valueOf: " + cl);
                }
            } else if (cl == String.class) {
                return "(Ljava/lang/String;)Ljava/lang/String;";
            } else {
                return "(Ljava/lang/Object;)Ljava/lang/String;";
            }
        }

        /**
         * The following method is copied from
         * org.objectweb.asm.commons.InstructionAdapter. Part of ASM: a very small
         * and fast Java bytecode manipulation framework.
         * Copyright (c) 2000-2005 INRIA, France Telecom All rights reserved.
         */
        private static void iconst(MethodVisitor mv, final int cst) {
            if (cst >= -1 && cst <= 5) {
                mv.visitInsn(Opcodes.ICONST_0 + cst);
            } else if (cst >= Byte.MIN_VALUE && cst <= Byte.MAX_VALUE) {
                mv.visitIntInsn(Opcodes.BIPUSH, cst);
            } else if (cst >= Short.MIN_VALUE && cst <= Short.MAX_VALUE) {
                mv.visitIntInsn(Opcodes.SIPUSH, cst);
            } else {
                mv.visitLdcInsn(cst);
            }
        }

        private static int getLoadOpcode(Class<?> c) {
            if (c == Void.TYPE) {
                throw new InternalError("Unexpected void type of load opcode");
            }
            return ILOAD + getOpcodeOffset(c);
        }

        private static int getOpcodeOffset(Class<?> c) {
            if (c.isPrimitive()) {
                if (c == Long.TYPE) {
                    return 1;
                } else if (c == Float.TYPE) {
                    return 2;
                } else if (c == Double.TYPE) {
                    return 3;
                }
                return 0;
            } else {
                return 4;
            }
        }

        private static int getParameterSize(Class<?> c) {
            if (c == Void.TYPE) {
                return 0;
            } else if (c == Long.TYPE || c == Double.TYPE) {
                return 2;
            }
            return 1;
        }
    }

    /**
     * MethodHandle StringBuilder strategy.
     *
     * <p>This strategy operates in two modes, gated by {@link Mode}.
     *
     * <p><b>{@link Strategy#MH_SB_SIZED}: "MethodHandles StringBuilder,
     * sized".</b>
     *
     * <p>This strategy avoids spinning up the bytecode by building the
     * computation on MethodHandle combinators. The computation is built with
     * public MethodHandle APIs, resolved from a public Lookup sequence, and
     * ends up calling the public StringBuilder API. Therefore, this strategy
     * does not use any private API at all, even the Unsafe.defineAnonymousClass,
     * since everything is handled under cover by java.lang.invoke APIs.
     *
     * <p><b>{@link Strategy#MH_SB_SIZED_EXACT}: "MethodHandles StringBuilder,
     * sized exactly".</b>
     *
     * <p>This strategy improves on @link Strategy#MH_SB_SIZED}, by first
     * converting all arguments to String in order to get the exact capacity
     * StringBuilder should have. The conversion is done via the public
     * String.valueOf and/or Object.toString methods, and does not touch any
     * private String API.
     */
    private static final class MethodHandleStringBuilderStrategy {

        private MethodHandleStringBuilderStrategy() {
            // no instantiation
        }

        private static MethodHandle generate(MethodType mt, Recipe recipe, Mode mode) throws Exception {
            int pc = mt.parameterCount();

            Class<?>[] ptypes = mt.parameterArray();
            MethodHandle[] filters = new MethodHandle[ptypes.length];
            for (int i = 0; i < ptypes.length; i++) {
                MethodHandle filter;
                switch (mode) {
                    case SIZED:
                        // In sized mode, we convert all references and floats/doubles
                        // to String: there is no specialization for different
                        // classes in StringBuilder API, and it will convert to
                        // String internally anyhow.
                        filter = Stringifiers.forMost(ptypes[i]);
                        break;
                    case SIZED_EXACT:
                        // In exact mode, we convert everything to String:
                        // this helps to compute the storage exactly.
                        filter = Stringifiers.forAny(ptypes[i]);
                        break;
                    default:
                        throw new StringConcatException("Not supported");
                }
                if (filter != null) {
                    filters[i] = filter;
                    ptypes[i] = filter.type().returnType();
                }
            }

            MethodHandle[] lengthers = new MethodHandle[pc];

            // Figure out lengths: constants' lengths can be deduced on the spot.
            // All reference arguments were filtered to String in the combinators below, so we can
            // call the usual String.length(). Primitive values string sizes can be estimated.
            int initial = 0;
            for (RecipeElement el : recipe.getElements()) {
                switch (el.getTag()) {
                    case TAG_CONST:
                        initial += el.getValue().length();
                        break;
                    case TAG_ARG:
                        final int i = el.getArgPos();
                        Class<?> type = ptypes[i];
                        if (type.isPrimitive()) {
                            MethodHandle est = MethodHandles.constant(int.class, estimateSize(type));
                            est = MethodHandles.dropArguments(est, 0, type);
                            lengthers[i] = est;
                        } else {
                            lengthers[i] = STRING_LENGTH;
                        }
                        break;
                    default:
                        throw new StringConcatException("Unhandled tag: " + el.getTag());
                }
            }

            // Create (StringBuilder, <args>) shape for appending:
            MethodHandle builder = MethodHandles.dropArguments(MethodHandles.identity(StringBuilder.class), 1, ptypes);

            // Compose append calls. This is done in reverse because the application order is
            // reverse as well.
            List<RecipeElement> elements = recipe.getElements();
            for (int i = elements.size() - 1; i >= 0; i--) {
                RecipeElement el = elements.get(i);
                MethodHandle appender;
                switch (el.getTag()) {
                    case TAG_CONST:
                        MethodHandle mh = appender(adaptToStringBuilder(String.class));
                        appender = MethodHandles.insertArguments(mh, 1, el.getValue());
                        break;
                    case TAG_ARG:
                        int ac = el.getArgPos();
                        appender = appender(ptypes[ac]);

                        // Insert dummy arguments to match the prefix in the signature.
                        // The actual appender argument will be the ac-ith argument.
                        if (ac != 0) {
                            appender = MethodHandles.dropArguments(appender, 1, Arrays.copyOf(ptypes, ac));
                        }
                        break;
                    default:
                        throw new StringConcatException("Unhandled tag: " + el.getTag());
                }
                builder = MethodHandles.foldArguments(builder, appender);
            }

            // Build the sub-tree that adds the sizes and produces a StringBuilder:

            // a) Start with the reducer that accepts all arguments, plus one
            //    slot for the initial value. Inject the initial value right away.
            //    This produces (<ints>)int shape:
            MethodHandle sum = getReducerFor(pc + 1);
            MethodHandle adder = MethodHandles.insertArguments(sum, 0, initial);

            // b) Apply lengthers to transform arguments to lengths, producing (<args>)int
            adder = MethodHandles.filterArguments(adder, 0, lengthers);

            // c) Instantiate StringBuilder (<args>)int -> (<args>)StringBuilder
            MethodHandle newBuilder = MethodHandles.filterReturnValue(adder, NEW_STRING_BUILDER);

            // d) Fold in StringBuilder constructor, this produces (<args>)StringBuilder
            MethodHandle mh = MethodHandles.foldArguments(builder, newBuilder);

            // Convert non-primitive arguments to Strings
            mh = MethodHandles.filterArguments(mh, 0, filters);

            // Convert (<args>)StringBuilder to (<args>)String
            if (DEBUG && mode.isExact()) {
                mh = MethodHandles.filterReturnValue(mh, BUILDER_TO_STRING_CHECKED);
            } else {
                mh = MethodHandles.filterReturnValue(mh, BUILDER_TO_STRING);
            }

            return mh;
        }

        private static MethodHandle getReducerFor(int cnt) {
            return SUMMERS.computeIfAbsent(cnt, SUMMER);
        }

        private static MethodHandle appender(Class<?> appendType) {
            MethodHandle appender = lookupVirtual(MethodHandles.publicLookup(), StringBuilder.class, "append",
                    StringBuilder.class, adaptToStringBuilder(appendType));

            // appenders should return void, this would not modify the target signature during folding
            MethodType nt = MethodType.methodType(void.class, StringBuilder.class, appendType);
            return appender.asType(nt);
        }

        private static String toStringChecked(StringBuilder sb) {
            String s = sb.toString();
            if (s.length() != sb.capacity()) {
                throw new AssertionError("Exactness check failed: result length = " + s.length() + ", buffer capacity = " + sb.capacity());
            }
            return s;
        }

        private static int sum(int v1, int v2) {
            return v1 + v2;
        }

        private static int sum(int v1, int v2, int v3) {
            return v1 + v2 + v3;
        }

        private static int sum(int v1, int v2, int v3, int v4) {
            return v1 + v2 + v3 + v4;
        }

        private static int sum(int v1, int v2, int v3, int v4, int v5) {
            return v1 + v2 + v3 + v4 + v5;
        }

        private static int sum(int v1, int v2, int v3, int v4, int v5, int v6) {
            return v1 + v2 + v3 + v4 + v5 + v6;
        }

        private static int sum(int v1, int v2, int v3, int v4, int v5, int v6, int v7) {
            return v1 + v2 + v3 + v4 + v5 + v6 + v7;
        }

        private static int sum(int v1, int v2, int v3, int v4, int v5, int v6, int v7, int v8) {
            return v1 + v2 + v3 + v4 + v5 + v6 + v7 + v8;
        }

        private static int sum(int initial, int[] vs) {
            int sum = initial;
            for (int v : vs) {
                sum += v;
            }
            return sum;
        }

        private static final ConcurrentMap<Integer, MethodHandle> SUMMERS;

        // This one is deliberately non-lambdified to optimize startup time:
        private static final Function<Integer, MethodHandle> SUMMER = new Function<Integer, MethodHandle>() {
            @Override
            public MethodHandle apply(Integer cnt) {
                if (cnt == 1) {
                    return MethodHandles.identity(int.class);
                } else if (cnt <= 8) {
                    // Variable-arity collectors are not as efficient as small-count methods,
                    // unroll some initial sizes.
                    Class<?>[] cls = new Class<?>[cnt];
                    Arrays.fill(cls, int.class);
                    return lookupStatic(Lookup.IMPL_LOOKUP, MethodHandleStringBuilderStrategy.class, "sum", int.class, cls);
                } else {
                    return lookupStatic(Lookup.IMPL_LOOKUP, MethodHandleStringBuilderStrategy.class, "sum", int.class, int.class, int[].class)
                            .asCollector(int[].class, cnt - 1);
                }
            }
        };

        private static final MethodHandle NEW_STRING_BUILDER, STRING_LENGTH, BUILDER_TO_STRING, BUILDER_TO_STRING_CHECKED;

        static {
            SUMMERS = new ConcurrentHashMap<>();
            Lookup publicLookup = MethodHandles.publicLookup();
            NEW_STRING_BUILDER = lookupConstructor(publicLookup, StringBuilder.class, int.class);
            STRING_LENGTH = lookupVirtual(publicLookup, String.class, "length", int.class);
            BUILDER_TO_STRING = lookupVirtual(publicLookup, StringBuilder.class, "toString", String.class);
            if (DEBUG) {
                BUILDER_TO_STRING_CHECKED = lookupStatic(MethodHandles.Lookup.IMPL_LOOKUP,
                        MethodHandleStringBuilderStrategy.class, "toStringChecked", String.class, StringBuilder.class);
            } else {
                BUILDER_TO_STRING_CHECKED = null;
            }
        }

    }


    /**
     * <p><b>{@link Strategy#MH_INLINE_SIZED_EXACT}: "MethodHandles inline,
     * sized exactly".</b>
     *
     * <p>This strategy replicates what StringBuilders are doing: it builds the
     * byte[] array on its own and passes that byte[] array to String
     * constructor. This strategy requires access to some private APIs in JDK,
     * most notably, the read-only Integer/Long.stringSize methods that measure
     * the character length of the integers, and the private String constructor
     * that accepts byte[] arrays without copying. While this strategy assumes a
     * particular implementation details for String, this opens the door for
     * building a very optimal concatenation sequence. This is the only strategy
     * that requires porting if there are private JDK changes occur.
     */
    private static final class MethodHandleInlineCopyStrategy {
        static final Unsafe UNSAFE = Unsafe.getUnsafe();

        private MethodHandleInlineCopyStrategy() {
            // no instantiation
        }

        static MethodHandle generate(MethodType mt, Recipe recipe) throws Throwable {

            // Create filters and obtain filtered parameter types. Filters would be used in the beginning
            // to convert the incoming arguments into the arguments we can process (e.g. Objects -> Strings).
            // The filtered argument type list is used all over in the combinators below.
            Class<?>[] ptypes = mt.parameterArray();
            MethodHandle[] filters = null;
            for (int i = 0; i < ptypes.length; i++) {
                MethodHandle filter = Stringifiers.forMost(ptypes[i]);
                if (filter != null) {
                    if (filters == null) {
                        filters = new MethodHandle[ptypes.length];
                    }
                    filters[i] = filter;
                    ptypes[i] = filter.type().returnType();
                }
            }

            // Start building the combinator tree. The tree "starts" with (<parameters>)String, and "finishes"
            // with the (byte[], long)String shape to invoke newString in StringConcatHelper. The combinators are
            // assembled bottom-up, which makes the code arguably hard to read.

            // Drop all remaining parameter types, leave only helper arguments:
            MethodHandle mh;

            mh = MethodHandles.dropArguments(NEW_STRING, 2, ptypes);

            // Mix in prependers. This happens when (byte[], long) = (storage, indexCoder) is already
            // known from the combinators below. We are assembling the string backwards, so the index coded
            // into indexCoder is the *ending* index.
            for (RecipeElement el : recipe.getElements()) {
                // Do the prepend, and put "new" index at index 1
                switch (el.getTag()) {
                    case TAG_CONST: {
                        MethodHandle prepender = MethodHandles.insertArguments(prepender(String.class), 2, el.getValue());
                        mh = MethodHandles.filterArgumentsWithCombiner(mh, 1, prepender,
                                1, 0 // indexCoder, storage
                        );
                        break;
                    }
                    case TAG_ARG: {
                        int pos = el.getArgPos();
                        MethodHandle prepender = prepender(ptypes[pos]);
                        mh = MethodHandles.filterArgumentsWithCombiner(mh, 1, prepender,
                                1, 0, // indexCoder, storage
                                2 + pos  // selected argument
                        );
                        break;
                    }
                    default:
                        throw new StringConcatException("Unhandled tag: " + el.getTag());
                }
            }

            // Fold in byte[] instantiation at argument 0
            mh = MethodHandles.foldArgumentsWithCombiner(mh, 0, NEW_ARRAY,
                    1 // index
            );

            // Start combining length and coder mixers.
            //
            // Length is easy: constant lengths can be computed on the spot, and all non-constant
            // shapes have been either converted to Strings, or explicit methods for getting the
            // string length out of primitives are provided.
            //
            // Coders are more interesting. Only Object, String and char arguments (and constants)
            // can have non-Latin1 encoding. It is easier to blindly convert constants to String,
            // and deduce the coder from there. Arguments would be either converted to Strings
            // during the initial filtering, or handled by specializations in MIXERS.
            //
            // The method handle shape before and after all mixers are combined in is:
            //   (long, <args>)String = ("indexCoder", <args>)
            long initialLengthCoder = INITIAL_CODER;
            for (RecipeElement el : recipe.getElements()) {
                switch (el.getTag()) {
                    case TAG_CONST:
                        String constant = el.getValue();
                        initialLengthCoder = (long)mixer(String.class).invoke(initialLengthCoder, constant);
                        break;
                    case TAG_ARG:
                        int ac = el.getArgPos();

                        Class<?> argClass = ptypes[ac];
                        MethodHandle mix = mixer(argClass);

                        // Compute new "index" in-place using old value plus the appropriate argument.
                        mh = MethodHandles.filterArgumentsWithCombiner(mh, 0, mix,
                                0, // old-index
                                1 + ac // selected argument
                        );

                        break;
                    default:
                        throw new StringConcatException("Unhandled tag: " + el.getTag());
                }
            }

            // Insert initial length and coder value here.
            // The method handle shape here is (<args>).
            mh = MethodHandles.insertArguments(mh, 0, initialLengthCoder);

            // Apply filters, converting the arguments:
            if (filters != null) {
                mh = MethodHandles.filterArguments(mh, 0, filters);
            }

            return mh;
        }

        @ForceInline
        private static byte[] newArray(long indexCoder) {
            byte coder = (byte)(indexCoder >> 32);
            int index = (int)indexCoder;
            return (byte[]) UNSAFE.allocateUninitializedArray(byte.class, index << coder);
        }

        private static MethodHandle prepender(Class<?> cl) {
            return PREPENDERS.computeIfAbsent(cl, PREPEND);
        }

        private static MethodHandle mixer(Class<?> cl) {
            return MIXERS.computeIfAbsent(cl, MIX);
        }

        // This one is deliberately non-lambdified to optimize startup time:
        private static final Function<Class<?>, MethodHandle> PREPEND = new Function<Class<?>, MethodHandle>() {
            @Override
            public MethodHandle apply(Class<?> c) {
                return lookupStatic(Lookup.IMPL_LOOKUP, STRING_HELPER, "prepend", long.class, long.class, byte[].class,
                        Wrapper.asPrimitiveType(c));
            }
        };

        // This one is deliberately non-lambdified to optimize startup time:
        private static final Function<Class<?>, MethodHandle> MIX = new Function<Class<?>, MethodHandle>() {
            @Override
            public MethodHandle apply(Class<?> c) {
                return lookupStatic(Lookup.IMPL_LOOKUP, STRING_HELPER, "mix", long.class, long.class,
                        Wrapper.asPrimitiveType(c));
            }
        };

        private static final MethodHandle NEW_STRING;
        private static final MethodHandle NEW_ARRAY;
        private static final ConcurrentMap<Class<?>, MethodHandle> PREPENDERS;
        private static final ConcurrentMap<Class<?>, MethodHandle> MIXERS;
        private static final long INITIAL_CODER;
        static final Class<?> STRING_HELPER;

        static {
            try {
                STRING_HELPER = Class.forName("java.lang.StringConcatHelper");
                MethodHandle initCoder = lookupStatic(Lookup.IMPL_LOOKUP, STRING_HELPER, "initialCoder", long.class);
                INITIAL_CODER = (long) initCoder.invoke();
            } catch (Throwable e) {
                throw new AssertionError(e);
            }

            PREPENDERS = new ConcurrentHashMap<>();
            MIXERS = new ConcurrentHashMap<>();

            NEW_STRING = lookupStatic(Lookup.IMPL_LOOKUP, STRING_HELPER, "newString", String.class, byte[].class, long.class);
            NEW_ARRAY  = lookupStatic(Lookup.IMPL_LOOKUP, MethodHandleInlineCopyStrategy.class, "newArray", byte[].class, long.class);
        }
    }

    /**
     * Public gateways to public "stringify" methods. These methods have the form String apply(T obj), and normally
     * delegate to {@code String.valueOf}, depending on argument's type.
     */
    private static final class Stringifiers {
        private Stringifiers() {
            // no instantiation
        }

        private static class ObjectStringifier {

            // We need some additional conversion for Objects in general, because String.valueOf(Object)
            // may return null. String conversion rules in Java state we need to produce "null" String
            // in this case, so we provide a customized version that deals with this problematic corner case.
            private static String valueOf(Object value) {
                String s;
                return (value == null || (s = value.toString()) == null) ? "null" : s;
            }

            // Could have used MethodHandles.lookup() instead of Lookup.IMPL_LOOKUP, if not for the fact
            // java.lang.invoke Lookups are explicitly forbidden to be retrieved using that API.
            private static final MethodHandle INSTANCE =
                    lookupStatic(Lookup.IMPL_LOOKUP, ObjectStringifier.class, "valueOf", String.class, Object.class);

        }

        private static class FloatStringifiers {
            private static final MethodHandle FLOAT_INSTANCE =
                    lookupStatic(MethodHandles.publicLookup(), String.class, "valueOf", String.class, float.class);

            private static final MethodHandle DOUBLE_INSTANCE =
                    lookupStatic(MethodHandles.publicLookup(), String.class, "valueOf", String.class, double.class);
        }

        private static class StringifierAny extends ClassValue<MethodHandle> {

            private static final ClassValue<MethodHandle> INSTANCE = new StringifierAny();

            @Override
            protected MethodHandle computeValue(Class<?> cl) {
                if (cl == byte.class || cl == short.class || cl == int.class) {
                    return lookupStatic(MethodHandles.publicLookup(), String.class, "valueOf", String.class, int.class);
                } else if (cl == boolean.class) {
                    return lookupStatic(MethodHandles.publicLookup(), String.class, "valueOf", String.class, boolean.class);
                } else if (cl == char.class) {
                    return lookupStatic(MethodHandles.publicLookup(), String.class, "valueOf", String.class, char.class);
                } else if (cl == long.class) {
                    return lookupStatic(MethodHandles.publicLookup(), String.class, "valueOf", String.class, long.class);
                } else {
                    MethodHandle mh = forMost(cl);
                    if (mh != null) {
                        return mh;
                    } else {
                        throw new IllegalStateException("Unknown class: " + cl);
                    }
                }
            }
        }

        /**
         * Returns a stringifier for references and floats/doubles only.
         * Always returns null for other primitives.
         *
         * @param t class to stringify
         * @return stringifier; null, if not available
         */
        static MethodHandle forMost(Class<?> t) {
            if (!t.isPrimitive()) {
                return ObjectStringifier.INSTANCE;
            } else if (t == float.class) {
                return FloatStringifiers.FLOAT_INSTANCE;
            } else if (t == double.class) {
                return FloatStringifiers.DOUBLE_INSTANCE;
            }
            return null;
        }

        /**
         * Returns a stringifier for any type. Never returns null.
         *
         * @param t class to stringify
         * @return stringifier
         */
        static MethodHandle forAny(Class<?> t) {
            return StringifierAny.INSTANCE.get(t);
        }
    }

    /* ------------------------------- Common utilities ------------------------------------ */

    static MethodHandle lookupStatic(Lookup lookup, Class<?> refc, String name, Class<?> rtype, Class<?>... ptypes) {
        try {
            return lookup.findStatic(refc, name, MethodType.methodType(rtype, ptypes));
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new AssertionError(e);
        }
    }

    static MethodHandle lookupVirtual(Lookup lookup, Class<?> refc, String name, Class<?> rtype, Class<?>... ptypes) {
        try {
            return lookup.findVirtual(refc, name, MethodType.methodType(rtype, ptypes));
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new AssertionError(e);
        }
    }

    static MethodHandle lookupConstructor(Lookup lookup, Class<?> refc, Class<?> ptypes) {
        try {
            return lookup.findConstructor(refc, MethodType.methodType(void.class, ptypes));
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new AssertionError(e);
        }
    }

    static int estimateSize(Class<?> cl) {
        if (cl == Integer.TYPE) {
            return 11; // "-2147483648"
        } else if (cl == Boolean.TYPE) {
            return 5; // "false"
        } else if (cl == Byte.TYPE) {
            return 4; // "-128"
        } else if (cl == Character.TYPE) {
            return 1; // duh
        } else if (cl == Short.TYPE) {
            return 6; // "-32768"
        } else if (cl == Double.TYPE) {
            return 26; // apparently, no larger than this, see FloatingDecimal.BinaryToASCIIBuffer.buffer
        } else if (cl == Float.TYPE) {
            return 26; // apparently, no larger than this, see FloatingDecimal.BinaryToASCIIBuffer.buffer
        } else if (cl == Long.TYPE)  {
            return 20; // "-9223372036854775808"
        } else {
            throw new IllegalArgumentException("Cannot estimate the size for " + cl);
        }
    }

    static Class<?> adaptToStringBuilder(Class<?> c) {
        if (c.isPrimitive()) {
            if (c == Byte.TYPE || c == Short.TYPE) {
                return int.class;
            }
        } else {
            if (c != String.class) {
                return Object.class;
            }
        }
        return c;
    }

    private StringConcatFactory() {
        // no instantiation
    }

}
