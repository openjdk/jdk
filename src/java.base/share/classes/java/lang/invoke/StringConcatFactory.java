/*
 * Copyright (c) 2015, 2024, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.access.JavaLangAccess;
import jdk.internal.access.SharedSecrets;
import jdk.internal.constant.ConstantUtils;
import jdk.internal.misc.VM;
import jdk.internal.util.ClassFileDumper;
import jdk.internal.vm.annotation.Stable;
import sun.invoke.util.Wrapper;

import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassFile;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.TypeKind;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.AccessFlag;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

import static java.lang.invoke.MethodHandles.Lookup.ClassOption.STRONG;
import static java.lang.invoke.MethodType.methodType;

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

    private static final int HIGH_ARITY_THRESHOLD;

    static {
        String highArity = VM.getSavedProperty("java.lang.invoke.StringConcat.highArityThreshold");
        HIGH_ARITY_THRESHOLD = highArity != null ? Integer.parseInt(highArity) : 20;
    }

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

    private static final JavaLangAccess JLA = SharedSecrets.getJavaLangAccess();

    // StringConcatFactory bootstrap methods are startup sensitive, and may be
    // special cased in java.lang.invoke.BootstrapMethodInvoker to ensure
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
     *     input converted as per JLS {@jls 5.1.11} "String Conversion"; otherwise</li>
     *     <li>two or more inputs, the inputs are concatenated as per
     *     requirements stated in JLS {@jls 15.18.1} "String Concatenation Operator +".
     *     The inputs are converted as per JLS {@jls 5.1.11} "String Conversion",
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
     *                 {@linkplain MethodHandles.Lookup#hasFullPrivilegeAccess()
     *                 full privilege access}.
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
        // This bootstrap method is unlikely to be used in practice,
        // avoid optimizing it at the expense of makeConcatWithConstants

        // Mock the recipe to reuse the concat generator code
        String recipe = "\u0001".repeat(concatType.parameterCount());
        return makeConcatWithConstants(lookup, name, concatType, recipe);
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
     *     input converted as per JLS {@jls 5.1.11} "String Conversion"; otherwise</li>
     *     <li>two or more inputs, the inputs are concatenated as per
     *     requirements stated in JLS {@jls 15.18.1} "String Concatenation Operator +".
     *     The inputs are converted as per JLS {@jls 5.1.11} "String Conversion",
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
     *   <li>The parameter count in {@code concatType} is equal to number of \1 tags
     *   in {@code recipe}</li>
     *
     *   <li>The return type in {@code concatType} is assignable
     *   from {@link java.lang.String}, and matches the return type of the
     *   returned {@link MethodHandle}</li>
     *
     *   <li>The number of elements in {@code constants} is equal to number of \2
     *   tags in {@code recipe}</li>
     * </ul>
     *
     * @param lookup    Represents a lookup context with the accessibility
     *                  privileges of the caller. Specifically, the lookup
     *                  context must have
     *                  {@linkplain MethodHandles.Lookup#hasFullPrivilegeAccess()
     *                  full privilege access}.
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
                                                   Object... constants)
        throws StringConcatException
    {
        Objects.requireNonNull(lookup, "Lookup is null");
        Objects.requireNonNull(name, "Name is null");
        Objects.requireNonNull(recipe, "Recipe is null");
        Objects.requireNonNull(concatType, "Concat type is null");
        Objects.requireNonNull(constants, "Constants are null");

        for (Object o : constants) {
            Objects.requireNonNull(o, "Cannot accept null constants");
        }

        if ((lookup.lookupModes() & MethodHandles.Lookup.PRIVATE) == 0) {
            throw new StringConcatException("Invalid caller: " +
                    lookup.lookupClass().getName());
        }

        String[] constantStrings = parseRecipe(concatType, recipe, constants);

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

        try {
            if (concatType.parameterCount() <= HIGH_ARITY_THRESHOLD) {
                return new ConstantCallSite(
                        generateMHInlineCopy(concatType, constantStrings)
                                .viewAsType(concatType, true));
            } else {
                return new ConstantCallSite(
                        SimpleStringBuilderStrategy.generate(lookup, concatType, constantStrings));
            }
        } catch (Error e) {
            // Pass through any error
            throw e;
        } catch (Throwable t) {
            throw new StringConcatException("Generator failed", t);
        }
    }

    private static String[] parseRecipe(MethodType concatType,
                                        String recipe,
                                        Object[] constants)
        throws StringConcatException
    {

        Objects.requireNonNull(recipe, "Recipe is null");
        int paramCount = concatType.parameterCount();
        // Array containing interleaving String constants, starting with
        // the first prefix and ending with the final prefix:
        //
        //   consts[0] + arg0 + consts[1] + arg 1 + ... + consts[paramCount].
        //
        // consts will be null if there's no constant to insert at a position.
        // An empty String constant will be replaced by null.
        String[] consts = new String[paramCount + 1];

        int cCount = 0;
        int oCount = 0;

        StringBuilder acc = new StringBuilder();

        for (int i = 0; i < recipe.length(); i++) {
            char c = recipe.charAt(i);

            if (c == TAG_CONST) {
                if (cCount == constants.length) {
                    // Not enough constants
                    throw constantMismatch(constants, cCount);
                }
                // Accumulate constant args along with any constants encoded
                // into the recipe
                acc.append(constants[cCount++]);
            } else if (c == TAG_ARG) {
                // Check for overflow
                if (oCount >= paramCount) {
                    throw argumentMismatch(concatType, oCount);
                }

                // Flush any accumulated characters into a constant
                consts[oCount++] = acc.length() > 0 ? acc.toString() : null;
                acc.setLength(0);
            } else {
                // Not a special character, this is a constant embedded into
                // the recipe itself.
                acc.append(c);
            }
        }
        if (oCount != concatType.parameterCount()) {
            throw argumentMismatch(concatType, oCount);
        }
        if (cCount < constants.length) {
            throw constantMismatch(constants, cCount);
        }

        // Flush the remaining characters as constant:
        consts[oCount] = acc.length() > 0 ? acc.toString() : null;
        return consts;
    }

    private static StringConcatException argumentMismatch(MethodType concatType,
                                                          int oCount) {
        return new StringConcatException(
                "Mismatched number of concat arguments: recipe wants " +
                oCount +
                " arguments, but signature provides " +
                concatType.parameterCount());
    }

    private static StringConcatException constantMismatch(Object[] constants,
            int cCount) {
        return new StringConcatException(
                "Mismatched number of concat constants: recipe wants " +
                        cCount +
                        " constants, but only " +
                        constants.length +
                        " are passed");
    }

    /**
     * <p>This strategy replicates what StringBuilders are doing: it builds the
     * byte[] array on its own and passes that byte[] array to String
     * constructor. This strategy requires access to some private APIs in JDK,
     * most notably, the private String constructor that accepts byte[] arrays
     * without copying.
     */
    private static MethodHandle generateMHInlineCopy(MethodType mt, String[] constants) {
        int paramCount = mt.parameterCount();
        String suffix = constants[paramCount];

        // Fast-path trivial concatenations
        if (paramCount == 0) {
            return MethodHandles.insertArguments(newStringifier(), 0, suffix == null ? "" : suffix);
        }
        if (paramCount == 1) {
            String prefix = constants[0];
            // Empty constants will be
            if (prefix == null) {
                if (suffix == null) {
                    return unaryConcat(mt.parameterType(0));
                } else if (!mt.hasPrimitives()) {
                    return MethodHandles.insertArguments(simpleConcat(), 1, suffix);
                } // else fall-through
            } else if (suffix == null && !mt.hasPrimitives()) {
                // Non-primitive argument
                return MethodHandles.insertArguments(simpleConcat(), 0, prefix);
            } // fall-through if there's both a prefix and suffix
        }
        if (paramCount == 2 && !mt.hasPrimitives() && suffix == null
                && constants[0] == null && constants[1] == null) {
            // Two reference arguments, no surrounding constants
            return simpleConcat();
        }
        // else... fall-through to slow-path

        // Create filters and obtain filtered parameter types. Filters would be used in the beginning
        // to convert the incoming arguments into the arguments we can process (e.g. Objects -> Strings).
        // The filtered argument type list is used all over in the combinators below.

        Class<?>[] ptypes = mt.erase().parameterArray();
        MethodHandle[] objFilters = null;
        MethodHandle[] floatFilters = null;
        MethodHandle[] doubleFilters = null;
        for (int i = 0; i < ptypes.length; i++) {
            Class<?> cl = ptypes[i];
            // Use int as the logical type for subword integral types
            // (byte and short). char and boolean require special
            // handling so don't change the logical type of those
            ptypes[i] = promoteToIntType(ptypes[i]);
            // Object, float and double will be eagerly transformed
            // into a (non-null) String as a first step after invocation.
            // Set up to use String as the logical type for such arguments
            // internally.
            if (cl == Object.class) {
                if (objFilters == null) {
                    objFilters = new MethodHandle[ptypes.length];
                }
                objFilters[i] = objectStringifier();
                ptypes[i] = String.class;
            } else if (cl == float.class) {
                if (floatFilters == null) {
                    floatFilters = new MethodHandle[ptypes.length];
                }
                floatFilters[i] = floatStringifier();
                ptypes[i] = String.class;
            } else if (cl == double.class) {
                if (doubleFilters == null) {
                    doubleFilters = new MethodHandle[ptypes.length];
                }
                doubleFilters[i] = doubleStringifier();
                ptypes[i] = String.class;
            }
        }

        // Start building the combinator tree. The tree "starts" with (<parameters>)String, and "finishes"
        // with the (byte[], long)String shape to invoke newString in StringConcatHelper. The combinators are
        // assembled bottom-up, which makes the code arguably hard to read.

        // Drop all remaining parameter types, leave only helper arguments:
        MethodHandle mh = MethodHandles.dropArgumentsTrusted(newString(), 2, ptypes);

        // Calculate the initialLengthCoder value by looking at all constant values and summing up
        // their lengths and adjusting the encoded coder bit if needed
        long initialLengthCoder = INITIAL_CODER;

        for (String constant : constants) {
            if (constant != null) {
                initialLengthCoder = JLA.stringConcatMix(initialLengthCoder, constant);
            }
        }

        // Mix in prependers. This happens when (byte[], long) = (storage, indexCoder) is already
        // known from the combinators below. We are assembling the string backwards, so the index coded
        // into indexCoder is the *ending* index.
        mh = filterInPrependers(mh, constants, ptypes);

        // Fold in byte[] instantiation at argument 0
        MethodHandle newArrayCombinator;
        if (suffix == null || suffix.isEmpty()) {
            suffix = "";
        }
        // newArray variant that deals with prepending any trailing constant
        //
        // initialLengthCoder is adjusted to have the correct coder
        // and length: The newArrayWithSuffix method expects only the coder of the
        // suffix to be encoded into indexCoder
        initialLengthCoder -= suffix.length();
        newArrayCombinator = newArrayWithSuffix(suffix);

        mh = MethodHandles.foldArgumentsWithCombiner(mh, 0, newArrayCombinator,
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
        // The method handle shape before all mixers are combined in is:
        //   (long, <args>)String = ("indexCoder", <args>)
        //
        // We will bind the initialLengthCoder value to the last mixer (the one that will be
        // executed first), then fold that in. This leaves the shape after all mixers are
        // combined in as:
        //   (<args>)String = (<args>)

        mh = filterAndFoldInMixers(mh, initialLengthCoder, ptypes);

        // The method handle shape here is (<args>).

        // Apply filters, converting the arguments:
        if (objFilters != null) {
            mh = MethodHandles.filterArguments(mh, 0, objFilters);
        }
        if (floatFilters != null) {
            mh = MethodHandles.filterArguments(mh, 0, floatFilters);
        }
        if (doubleFilters != null) {
            mh = MethodHandles.filterArguments(mh, 0, doubleFilters);
        }

        return mh;
    }

    // We need one prepender per argument, but also need to fold in constants. We do so by greedily
    // creating prependers that fold in surrounding constants into the argument prepender. This reduces
    // the number of unique MH combinator tree shapes we'll create in an application.
    // Additionally we do this in chunks to reduce the number of combinators bound to the root tree,
    // which simplifies the shape and makes construction of similar trees use less unique LF classes
    private static MethodHandle filterInPrependers(MethodHandle mh, String[] constants, Class<?>[] ptypes) {
        int pos;
        int[] argPositions = null;
        MethodHandle prepend;
        for (pos = 0; pos < ptypes.length - 3; pos += 4) {
            prepend = prepender(pos, constants, ptypes, 4);
            argPositions = filterPrependArgPositions(argPositions, pos, 4);
            mh = MethodHandles.filterArgumentsWithCombiner(mh, 1, prepend, argPositions);
        }
        if (pos < ptypes.length) {
            int count = ptypes.length - pos;
            prepend = prepender(pos, constants, ptypes, count);
            argPositions = filterPrependArgPositions(argPositions, pos, count);
            mh = MethodHandles.filterArgumentsWithCombiner(mh, 1, prepend, argPositions);
        }
        return mh;
    }

    static int[] filterPrependArgPositions(int[] argPositions, int pos, int count) {
        if (argPositions == null || argPositions.length != count + 2) {
            argPositions = new int[count + 2];
            argPositions[0] = 1; // indexCoder
            argPositions[1] = 0; // storage
        }
        int limit = count + 2;
        for (int i = 2; i < limit; i++) {
            argPositions[i] = i + pos;
        }
        return argPositions;
    }


    // We need one mixer per argument.
    private static MethodHandle filterAndFoldInMixers(MethodHandle mh, long initialLengthCoder, Class<?>[] ptypes) {
        int pos;
        int[] argPositions = null;
        for (pos = 0; pos < ptypes.length - 4; pos += 4) {
            // Compute new "index" in-place pairwise using old value plus the appropriate arguments.
            MethodHandle mix = mixer(ptypes[pos], ptypes[pos + 1], ptypes[pos + 2], ptypes[pos + 3]);
            argPositions = filterMixerArgPositions(argPositions, pos, 4);
            mh = MethodHandles.filterArgumentsWithCombiner(mh, 0,
                    mix, argPositions);
        }

        if (pos < ptypes.length) {
            // Mix in the last 1 to 4 parameters, insert the initialLengthCoder into the final mixer and
            // fold the result into the main combinator
            mh = foldInLastMixers(mh, initialLengthCoder, pos, ptypes, ptypes.length - pos);
        } else if (ptypes.length == 0) {
            // No mixer (constants only concat), insert initialLengthCoder directly
            mh = MethodHandles.insertArguments(mh, 0, initialLengthCoder);
        }
        return mh;
    }

    static int[] filterMixerArgPositions(int[] argPositions, int pos, int count) {
        if (argPositions == null || argPositions.length != count + 2) {
            argPositions = new int[count + 1];
            argPositions[0] = 0; // indexCoder
        }
        int limit = count + 1;
        for (int i = 1; i < limit; i++) {
            argPositions[i] = i + pos;
        }
        return argPositions;
    }

    private static MethodHandle foldInLastMixers(MethodHandle mh, long initialLengthCoder, int pos, Class<?>[] ptypes, int count) {
        MethodHandle mix = switch (count) {
            case 1 -> mixer(ptypes[pos]);
            case 2 -> mixer(ptypes[pos], ptypes[pos + 1]);
            case 3 -> mixer(ptypes[pos], ptypes[pos + 1], ptypes[pos + 2]);
            case 4 -> mixer(ptypes[pos], ptypes[pos + 1], ptypes[pos + 2], ptypes[pos + 3]);
            default -> throw new IllegalArgumentException("Unexpected count: " + count);
        };
        mix = MethodHandles.insertArguments(mix,0, initialLengthCoder);
        // apply selected arguments on the 1-4 arg mixer and fold in the result
        return switch (count) {
            case 1 -> MethodHandles.foldArgumentsWithCombiner(mh, 0, mix,
                    1 + pos);
            case 2 -> MethodHandles.foldArgumentsWithCombiner(mh, 0, mix,
                    1 + pos, 2 + pos);
            case 3 -> MethodHandles.foldArgumentsWithCombiner(mh, 0, mix,
                    1 + pos, 2 + pos, 3 + pos);
            case 4 -> MethodHandles.foldArgumentsWithCombiner(mh, 0, mix,
                    1 + pos, 2 + pos, 3 + pos, 4 + pos);
            default -> throw new IllegalArgumentException();
        };
    }

    // Simple prependers, single argument. May be used directly or as a
    // building block for complex prepender combinators.
    private static MethodHandle prepender(String prefix, Class<?> cl) {
        if (prefix == null || prefix.isEmpty()) {
            return noPrefixPrepender(cl);
        } else {
            return MethodHandles.insertArguments(
                    prepender(cl), 3, prefix);
        }
    }

    private static MethodHandle prepender(Class<?> cl) {
        int idx = classIndex(cl);
        MethodHandle prepend = PREPENDERS[idx];
        if (prepend == null) {
            PREPENDERS[idx] = prepend = JLA.stringConcatHelper("prepend",
                    methodType(long.class, long.class, byte[].class,
                            Wrapper.asPrimitiveType(cl), String.class)).rebind();
        }
        return prepend;
    }

    private static MethodHandle noPrefixPrepender(Class<?> cl) {
        int idx = classIndex(cl);
        MethodHandle prepend = NO_PREFIX_PREPENDERS[idx];
        if (prepend == null) {
            NO_PREFIX_PREPENDERS[idx] = prepend = MethodHandles.insertArguments(prepender(cl), 3, "");
        }
        return prepend;
    }

    private static final int INT_IDX = 0,
            CHAR_IDX = 1,
            LONG_IDX = 2,
            BOOLEAN_IDX = 3,
            STRING_IDX = 4,
            TYPE_COUNT = 5;
    private static int classIndex(Class<?> cl) {
        if (cl == String.class)                          return STRING_IDX;
        if (cl == int.class)                             return INT_IDX;
        if (cl == boolean.class)                         return BOOLEAN_IDX;
        if (cl == char.class)                            return CHAR_IDX;
        if (cl == long.class)                            return LONG_IDX;
        throw new IllegalArgumentException("Unexpected class: " + cl);
    }

    // Constant argument lists used by the prepender MH builders
    private static final int[] PREPEND_FILTER_FIRST_ARGS  = new int[] { 0, 1, 2 };
    private static final int[] PREPEND_FILTER_SECOND_ARGS = new int[] { 0, 1, 3 };
    private static final int[] PREPEND_FILTER_THIRD_ARGS  = new int[] { 0, 1, 4 };
    private static final int[] PREPEND_FILTER_FIRST_PAIR_ARGS  = new int[] { 0, 1, 2, 3 };
    private static final int[] PREPEND_FILTER_SECOND_PAIR_ARGS = new int[] { 0, 1, 4, 5 };

    // Base MH for complex prepender combinators.
    private static @Stable MethodHandle PREPEND_BASE;
    private static MethodHandle prependBase() {
        MethodHandle base = PREPEND_BASE;
        if (base == null) {
            base = PREPEND_BASE = MethodHandles.dropArguments(
                    MethodHandles.identity(long.class), 1, byte[].class);
        }
        return base;
    }

    private static final @Stable MethodHandle[][] DOUBLE_PREPENDERS = new MethodHandle[TYPE_COUNT][TYPE_COUNT];

    private static MethodHandle prepender(String prefix, Class<?> cl, String prefix2, Class<?> cl2) {
        int idx1 = classIndex(cl);
        int idx2 = classIndex(cl2);
        MethodHandle prepend = DOUBLE_PREPENDERS[idx1][idx2];
        if (prepend == null) {
            prepend = DOUBLE_PREPENDERS[idx1][idx2] =
                    MethodHandles.dropArguments(prependBase(), 2, cl, cl2);
        }
        prepend = MethodHandles.filterArgumentsWithCombiner(prepend, 0, prepender(prefix, cl),
                PREPEND_FILTER_FIRST_ARGS);
        return MethodHandles.filterArgumentsWithCombiner(prepend, 0, prepender(prefix2, cl2),
                PREPEND_FILTER_SECOND_ARGS);
    }

    private static MethodHandle prepender(int pos, String[] constants, Class<?>[] ptypes, int count) {
        // build the simple cases directly
        if (count == 1) {
            return prepender(constants[pos], ptypes[pos]);
        }
        if (count == 2) {
            return prepender(constants[pos], ptypes[pos], constants[pos + 1], ptypes[pos + 1]);
        }
        // build a tree from an unbound prepender, allowing us to bind the constants in a batch as a final step
        MethodHandle prepend = prependBase();
        if (count == 3) {
            prepend = MethodHandles.dropArguments(prepend, 2,
                    ptypes[pos], ptypes[pos + 1], ptypes[pos + 2]);
            prepend = MethodHandles.filterArgumentsWithCombiner(prepend, 0,
                    prepender(constants[pos], ptypes[pos], constants[pos + 1], ptypes[pos + 1]),
                    PREPEND_FILTER_FIRST_PAIR_ARGS);
            return MethodHandles.filterArgumentsWithCombiner(prepend, 0,
                    prepender(constants[pos + 2], ptypes[pos + 2]),
                    PREPEND_FILTER_THIRD_ARGS);
        } else if (count == 4) {
            prepend = MethodHandles.dropArguments(prepend, 2,
                    ptypes[pos], ptypes[pos + 1], ptypes[pos + 2], ptypes[pos + 3]);
            prepend = MethodHandles.filterArgumentsWithCombiner(prepend, 0,
                    prepender(constants[pos], ptypes[pos], constants[pos + 1], ptypes[pos + 1]),
                    PREPEND_FILTER_FIRST_PAIR_ARGS);
            return MethodHandles.filterArgumentsWithCombiner(prepend, 0,
                    prepender(constants[pos + 2], ptypes[pos + 2], constants[pos + 3], ptypes[pos + 3]),
                    PREPEND_FILTER_SECOND_PAIR_ARGS);
        } else {
            throw new IllegalArgumentException("Unexpected count: " + count);
        }
    }

    // Constant argument lists used by the mixer MH builders
    private static final int[] MIX_FILTER_SECOND_ARGS = new int[] { 0, 2 };
    private static final int[] MIX_FILTER_THIRD_ARGS  = new int[] { 0, 3 };
    private static final int[] MIX_FILTER_SECOND_PAIR_ARGS = new int[] { 0, 3, 4 };
    private static MethodHandle mixer(Class<?> cl) {
        int index = classIndex(cl);
        MethodHandle mix = MIXERS[index];
        if (mix == null) {
            MIXERS[index] = mix = JLA.stringConcatHelper("mix",
                    methodType(long.class, long.class, Wrapper.asPrimitiveType(cl))).rebind();
        }
        return mix;
    }

    private static final @Stable MethodHandle[][] DOUBLE_MIXERS = new MethodHandle[TYPE_COUNT][TYPE_COUNT];
    private static MethodHandle mixer(Class<?> cl, Class<?> cl2) {
        int idx1 = classIndex(cl);
        int idx2 = classIndex(cl2);
        MethodHandle mix = DOUBLE_MIXERS[idx1][idx2];
        if (mix == null) {
            mix = mixer(cl);
            mix = MethodHandles.dropArguments(mix, 2, cl2);
            DOUBLE_MIXERS[idx1][idx2] = mix = MethodHandles.filterArgumentsWithCombiner(mix, 0,
                    mixer(cl2), MIX_FILTER_SECOND_ARGS);
        }
        return mix;
    }

    private static MethodHandle mixer(Class<?> cl, Class<?> cl2, Class<?> cl3) {
        MethodHandle mix = mixer(cl, cl2);
        mix = MethodHandles.dropArguments(mix, 3, cl3);
        return MethodHandles.filterArgumentsWithCombiner(mix, 0,
                mixer(cl3), MIX_FILTER_THIRD_ARGS);
    }

    private static MethodHandle mixer(Class<?> cl, Class<?> cl2, Class<?> cl3, Class<?> cl4) {
        MethodHandle mix = mixer(cl, cl2);
        mix = MethodHandles.dropArguments(mix, 3, cl3, cl4);
        return MethodHandles.filterArgumentsWithCombiner(mix, 0,
                mixer(cl3, cl4), MIX_FILTER_SECOND_PAIR_ARGS);
    }

    private @Stable static MethodHandle SIMPLE_CONCAT;
    private static MethodHandle simpleConcat() {
        MethodHandle mh = SIMPLE_CONCAT;
        if (mh == null) {
            MethodHandle simpleConcat = JLA.stringConcatHelper("simpleConcat",
                    methodType(String.class, Object.class, Object.class));
            SIMPLE_CONCAT = mh = simpleConcat.rebind();
        }
        return mh;
    }

    private @Stable static MethodHandle NEW_STRING;
    private static MethodHandle newString() {
        MethodHandle mh = NEW_STRING;
        if (mh == null) {
            MethodHandle newString = JLA.stringConcatHelper("newString",
                    methodType(String.class, byte[].class, long.class));
            NEW_STRING = mh = newString.rebind();
        }
        return mh;
    }

    private @Stable static MethodHandle NEW_ARRAY_SUFFIX;
    private static MethodHandle newArrayWithSuffix(String suffix) {
        MethodHandle mh = NEW_ARRAY_SUFFIX;
        if (mh == null) {
            MethodHandle newArrayWithSuffix = JLA.stringConcatHelper("newArrayWithSuffix",
                    methodType(byte[].class, String.class, long.class));
            NEW_ARRAY_SUFFIX = mh = newArrayWithSuffix.rebind();
        }
        return MethodHandles.insertArguments(mh, 0, suffix);
    }

    /**
     * Public gateways to public "stringify" methods. These methods have the
     * form String apply(T obj), and normally delegate to {@code String.valueOf},
     * depending on argument's type.
     */
    private @Stable static MethodHandle OBJECT_STRINGIFIER;
    private static MethodHandle objectStringifier() {
        MethodHandle mh = OBJECT_STRINGIFIER;
        if (mh == null) {
            OBJECT_STRINGIFIER = mh = JLA.stringConcatHelper("stringOf",
                    methodType(String.class, Object.class));
        }
        return mh;
    }
    private @Stable static MethodHandle FLOAT_STRINGIFIER;
    private static MethodHandle floatStringifier() {
        MethodHandle mh = FLOAT_STRINGIFIER;
        if (mh == null) {
            FLOAT_STRINGIFIER = mh = stringValueOf(float.class);
        }
        return mh;
    }
    private @Stable static MethodHandle DOUBLE_STRINGIFIER;
    private static MethodHandle doubleStringifier() {
        MethodHandle mh = DOUBLE_STRINGIFIER;
        if (mh == null) {
            DOUBLE_STRINGIFIER = mh = stringValueOf(double.class);
        }
        return mh;
    }

    private @Stable static MethodHandle INT_STRINGIFIER;
    private static MethodHandle intStringifier() {
        MethodHandle mh = INT_STRINGIFIER;
        if (mh == null) {
            INT_STRINGIFIER = mh = stringValueOf(int.class);
        }
        return mh;
    }

    private @Stable static MethodHandle LONG_STRINGIFIER;
    private static MethodHandle longStringifier() {
        MethodHandle mh = LONG_STRINGIFIER;
        if (mh == null) {
            LONG_STRINGIFIER = mh = stringValueOf(long.class);
        }
        return mh;
    }

    private @Stable static MethodHandle CHAR_STRINGIFIER;
    private static MethodHandle charStringifier() {
        MethodHandle mh = CHAR_STRINGIFIER;
        if (mh == null) {
            CHAR_STRINGIFIER = mh = stringValueOf(char.class);
        }
        return mh;
    }

    private @Stable static MethodHandle BOOLEAN_STRINGIFIER;
    private static MethodHandle booleanStringifier() {
        MethodHandle mh = BOOLEAN_STRINGIFIER;
        if (mh == null) {
            BOOLEAN_STRINGIFIER = mh = stringValueOf(boolean.class);
        }
        return mh;
    }

    private @Stable static MethodHandle NEW_STRINGIFIER;
    private static MethodHandle newStringifier() {
        MethodHandle mh = NEW_STRINGIFIER;
        if (mh == null) {
            NEW_STRINGIFIER = mh = JLA.stringConcatHelper("newStringOf",
                    methodType(String.class, Object.class));
        }
        return mh;
    }

    private static MethodHandle unaryConcat(Class<?> cl) {
        if (!cl.isPrimitive()) {
            return newStringifier();
        } else if (cl == int.class || cl == short.class || cl == byte.class) {
            return intStringifier();
        } else if (cl == long.class) {
            return longStringifier();
        } else if (cl == char.class) {
            return charStringifier();
        } else if (cl == boolean.class) {
            return booleanStringifier();
        } else if (cl == float.class) {
            return floatStringifier();
        } else if (cl == double.class) {
            return doubleStringifier();
        } else {
            throw new InternalError("Unhandled type for unary concatenation: " + cl);
        }
    }

    private static final @Stable MethodHandle[] NO_PREFIX_PREPENDERS = new MethodHandle[TYPE_COUNT];
    private static final @Stable MethodHandle[] PREPENDERS      = new MethodHandle[TYPE_COUNT];
    private static final @Stable MethodHandle[] MIXERS          = new MethodHandle[TYPE_COUNT];
    private static final long INITIAL_CODER = JLA.stringConcatInitialCoder();

    /**
     * Promote integral types to int.
     */
    private static Class<?> promoteToIntType(Class<?> t) {
        // use int for subword integral types; still need special mixers
        // and prependers for char, boolean
        return t == byte.class || t == short.class ? int.class : t;
    }

    /**
     * Returns a stringifier for references and floats/doubles only.
     * Always returns null for other primitives.
     *
     * @param t class to stringify
     * @return stringifier; null, if not available
     */
    private static MethodHandle stringifierFor(Class<?> t) {
        if (t == Object.class) {
            return objectStringifier();
        } else if (t == float.class) {
            return floatStringifier();
        } else if (t == double.class) {
            return doubleStringifier();
        }
        return null;
    }

    private static MethodHandle stringValueOf(Class<?> ptype) {
        try {
            return MethodHandles.publicLookup()
                .findStatic(String.class, "valueOf", MethodType.methodType(String.class, ptype));
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new AssertionError(e);
        }
    }

    private StringConcatFactory() {
        // no instantiation
    }

    /**
     * Bytecode StringBuilder strategy.
     *
     * <p>This strategy emits StringBuilder chains as similar as possible
     * to what javac would. No exact sizing of parameters or estimates.
     */
    private static final class SimpleStringBuilderStrategy {
        static final String METHOD_NAME = "concat";
        static final ClassDesc STRING_BUILDER = ClassDesc.ofDescriptor("Ljava/lang/StringBuilder;");
        static final ClassFileDumper DUMPER =
                ClassFileDumper.getInstance("java.lang.invoke.StringConcatFactory.dump", "stringConcatClasses");
        static final MethodTypeDesc APPEND_BOOLEAN_TYPE = MethodTypeDesc.of(STRING_BUILDER, ConstantDescs.CD_boolean);
        static final MethodTypeDesc APPEND_CHAR_TYPE = MethodTypeDesc.of(STRING_BUILDER, ConstantDescs.CD_char);
        static final MethodTypeDesc APPEND_DOUBLE_TYPE = MethodTypeDesc.of(STRING_BUILDER, ConstantDescs.CD_double);
        static final MethodTypeDesc APPEND_FLOAT_TYPE = MethodTypeDesc.of(STRING_BUILDER, ConstantDescs.CD_float);
        static final MethodTypeDesc APPEND_INT_TYPE = MethodTypeDesc.of(STRING_BUILDER, ConstantDescs.CD_int);
        static final MethodTypeDesc APPEND_LONG_TYPE = MethodTypeDesc.of(STRING_BUILDER, ConstantDescs.CD_long);
        static final MethodTypeDesc APPEND_OBJECT_TYPE = MethodTypeDesc.of(STRING_BUILDER, ConstantDescs.CD_Object);
        static final MethodTypeDesc APPEND_STRING_TYPE = MethodTypeDesc.of(STRING_BUILDER, ConstantDescs.CD_String);
        static final MethodTypeDesc INT_CONSTRUCTOR_TYPE = MethodTypeDesc.of(ConstantDescs.CD_void, ConstantDescs.CD_int);
        static final MethodTypeDesc TO_STRING_TYPE = MethodTypeDesc.of(ConstantDescs.CD_String);

        /**
         * Ensure a capacity in the initial StringBuilder to accommodate all
         * constants plus this factor times the number of arguments.
         */
        static final int ARGUMENT_SIZE_FACTOR = 4;

        static final Set<Lookup.ClassOption> SET_OF_STRONG = Set.of(STRONG);

        private SimpleStringBuilderStrategy() {
            // no instantiation
        }

        private static MethodHandle generate(Lookup lookup, MethodType args, String[] constants) throws Exception {
            String className = getClassName(lookup.lookupClass());

            byte[] classBytes = ClassFile.of().build(ConstantUtils.binaryNameToDesc(className),
                    new Consumer<ClassBuilder>() {
                        @Override
                        public void accept(ClassBuilder clb) {
                            clb.withFlags(AccessFlag.FINAL, AccessFlag.SUPER, AccessFlag.SYNTHETIC)
                                .withMethodBody(METHOD_NAME,
                                        ConstantUtils.methodTypeDesc(args),
                                        ClassFile.ACC_FINAL | ClassFile.ACC_PRIVATE | ClassFile.ACC_STATIC,
                                        generateMethod(constants, args));
                    }});
            try {
                Lookup hiddenLookup = lookup.makeHiddenClassDefiner(className, classBytes, SET_OF_STRONG, DUMPER)
                                            .defineClassAsLookup(true);
                Class<?> innerClass = hiddenLookup.lookupClass();
                return hiddenLookup.findStatic(innerClass, METHOD_NAME, args);
            } catch (Exception e) {
                throw new StringConcatException("Exception while spinning the class", e);
            }
        }

        private static Consumer<CodeBuilder> generateMethod(String[] constants, MethodType args) {
            return new Consumer<CodeBuilder>() {
                @Override
                public void accept(CodeBuilder cb) {
                    cb.new_(STRING_BUILDER);
                    cb.dup();

                    int len = 0;
                    for (String constant : constants) {
                        if (constant != null) {
                            len += constant.length();
                        }
                    }
                    len += args.parameterCount() * ARGUMENT_SIZE_FACTOR;
                    cb.loadConstant(len);
                    cb.invokespecial(STRING_BUILDER, "<init>", INT_CONSTRUCTOR_TYPE);

                    // At this point, we have a blank StringBuilder on stack, fill it in with .append calls.
                    {
                        int off = 0;
                        for (int c = 0; c < args.parameterCount(); c++) {
                            if (constants[c] != null) {
                                cb.ldc(constants[c]);
                                cb.invokevirtual(STRING_BUILDER, "append", APPEND_STRING_TYPE);
                            }
                            Class<?> cl = args.parameterType(c);
                            TypeKind kind = TypeKind.from(cl);
                            cb.loadLocal(kind, off);
                            off += kind.slotSize();
                            MethodTypeDesc desc = getSBAppendDesc(cl);
                            cb.invokevirtual(STRING_BUILDER, "append", desc);
                        }
                        if (constants[constants.length - 1] != null) {
                            cb.ldc(constants[constants.length - 1]);
                            cb.invokevirtual(STRING_BUILDER, "append", APPEND_STRING_TYPE);
                        }
                    }

                    cb.invokevirtual(STRING_BUILDER, "toString", TO_STRING_TYPE);
                    cb.areturn();
                }
            };
        }

        /**
         * The generated class is in the same package as the host class as
         * it's the implementation of the string concatenation for the host
         * class.
         */
        private static String getClassName(Class<?> hostClass) {
            String name = hostClass.isHidden() ? hostClass.getName().replace('/', '_')
                    : hostClass.getName();
            return name + "$$StringConcat";
        }

        private static MethodTypeDesc getSBAppendDesc(Class<?> cl) {
            if (cl.isPrimitive()) {
                if (cl == Integer.TYPE || cl == Byte.TYPE || cl == Short.TYPE) {
                    return APPEND_INT_TYPE;
                } else if (cl == Boolean.TYPE) {
                    return APPEND_BOOLEAN_TYPE;
                } else if (cl == Character.TYPE) {
                    return APPEND_CHAR_TYPE;
                } else if (cl == Double.TYPE) {
                    return APPEND_DOUBLE_TYPE;
                } else if (cl == Float.TYPE) {
                    return APPEND_FLOAT_TYPE;
                } else if (cl == Long.TYPE) {
                    return APPEND_LONG_TYPE;
                } else {
                    throw new IllegalStateException("Unhandled primitive StringBuilder.append: " + cl);
                }
            } else if (cl == String.class) {
                return APPEND_STRING_TYPE;
            } else {
                return APPEND_OBJECT_TYPE;
            }
        }
    }
}
