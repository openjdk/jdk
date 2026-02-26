/*
 * Copyright (c) 2015, 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2024, Alibaba Group Holding Limited. All Rights Reserved.
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
import jdk.internal.constant.ClassOrInterfaceDescImpl;
import jdk.internal.constant.ConstantUtils;
import jdk.internal.constant.MethodTypeDescImpl;
import jdk.internal.misc.VM;
import jdk.internal.util.ClassFileDumper;
import jdk.internal.util.ReferenceKey;
import jdk.internal.util.ReferencedKeyMap;
import jdk.internal.vm.annotation.AOTSafeClassInitializer;
import jdk.internal.vm.annotation.Stable;

import java.lang.classfile.Annotation;
import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassFile;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.MethodBuilder;
import java.lang.classfile.TypeKind;
import java.lang.classfile.attribute.RuntimeVisibleAnnotationsAttribute;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.ref.SoftReference;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static java.lang.classfile.ClassFile.*;
import static java.lang.constant.ConstantDescs.*;
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
@AOTSafeClassInitializer
public final class StringConcatFactory {
    private static final int CACHE_THRESHOLD;
    private static final int FORCE_INLINE_THRESHOLD;

    static {
        String cacheThreshold = VM.getSavedProperty("java.lang.invoke.StringConcat.cacheThreshold");
        CACHE_THRESHOLD = cacheThreshold != null ? Integer.parseInt(cacheThreshold) : 256;

        String inlineThreshold = VM.getSavedProperty("java.lang.invoke.StringConcat.inlineThreshold");
        FORCE_INLINE_THRESHOLD = inlineThreshold != null ? Integer.parseInt(inlineThreshold) : 16;
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
            MethodHandle mh = makeSimpleConcat(concatType, constantStrings);

            if (mh == null) {
                mh = InlineHiddenClassStrategy.generate(lookup, concatType, constantStrings);
            }
            mh = mh.viewAsType(concatType, true);

            return new ConstantCallSite(mh);
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
                consts[oCount++] = acc.length() > 0 ? acc.toString() : "";
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
        consts[oCount] = acc.length() > 0 ? acc.toString() : "";
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

    private static MethodHandle makeSimpleConcat(MethodType mt, String[] constants) {
        int paramCount = mt.parameterCount();
        String suffix = constants[paramCount];

        // Fast-path trivial concatenations
        if (paramCount == 0) {
            return MethodHandles.insertArguments(newStringifier(), 0, suffix == null ? "" : suffix);
        }
        if (paramCount == 1) {
            String prefix = constants[0];
            // Empty constants will be
            if (prefix.isEmpty()) {
                if (suffix.isEmpty()) {
                    return unaryConcat(mt.parameterType(0));
                } else if (!mt.hasPrimitives()) {
                    return MethodHandles.insertArguments(simpleConcat(), 1, suffix);
                } // else fall-through
            } else if (suffix.isEmpty() && !mt.hasPrimitives()) {
                // Non-primitive argument
                return MethodHandles.insertArguments(simpleConcat(), 0, prefix);
            } // fall-through if there's both a prefix and suffix
        } else if (paramCount == 2 && !mt.hasPrimitives() && suffix.isEmpty()
                && constants[0].isEmpty() && constants[1].isEmpty()) {
            // Two reference arguments, no surrounding constants
            return simpleConcat();
        }

        return null;
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

    /**
     * Public gateways to public "stringify" methods. These methods have the
     * form String apply(T obj), and normally delegate to {@code String.valueOf},
     * depending on argument's type.
     */
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
     * Implement efficient hidden class strategy for String concatenation
     *
     * <p>This strategy replicates based on the bytecode what StringBuilders are doing: it builds the
     * byte[] array on its own and passes that byte[] array to String
     * constructor. This strategy requires access to some private APIs in JDK,
     * most notably, the private String constructor that accepts byte[] arrays
     * without copying.
     */
    private static final class InlineHiddenClassStrategy {
        // The CLASS_NAME prefix must be the same as used by HeapShared::is_string_concat_klass()
        // in the HotSpot code.
        static final String CLASS_NAME   = "java.lang.String$$StringConcat";
        static final String METHOD_NAME  = "concat";

        static final ClassFileDumper DUMPER =
                ClassFileDumper.getInstance("java.lang.invoke.StringConcatFactory.dump", "stringConcatClasses");
        static final MethodHandles.Lookup STR_LOOKUP = new MethodHandles.Lookup(String.class);

        static final ClassDesc CD_CONCAT             = ConstantUtils.binaryNameToDesc(CLASS_NAME);
        static final ClassDesc CD_StringConcatHelper = ClassOrInterfaceDescImpl.ofValidated("Ljava/lang/StringConcatHelper;");
        static final ClassDesc CD_StringConcatBase   = ClassOrInterfaceDescImpl.ofValidated("Ljava/lang/StringConcatHelper$StringConcatBase;");
        static final ClassDesc CD_Array_byte         = CD_byte.arrayType();
        static final ClassDesc CD_Array_String       = CD_String.arrayType();

        static final MethodTypeDesc MTD_byte_char       = MethodTypeDescImpl.ofValidated(CD_byte, CD_char);
        static final MethodTypeDesc MTD_byte            = MethodTypeDescImpl.ofValidated(CD_byte);
        static final MethodTypeDesc MTD_int             = MethodTypeDescImpl.ofValidated(CD_int);
        static final MethodTypeDesc MTD_int_int_boolean = MethodTypeDescImpl.ofValidated(CD_int, CD_int, CD_boolean);
        static final MethodTypeDesc MTD_int_int_char    = MethodTypeDescImpl.ofValidated(CD_int, CD_int, CD_char);
        static final MethodTypeDesc MTD_int_int_int     = MethodTypeDescImpl.ofValidated(CD_int, CD_int, CD_int);
        static final MethodTypeDesc MTD_int_int_long    = MethodTypeDescImpl.ofValidated(CD_int, CD_int, CD_long);
        static final MethodTypeDesc MTD_int_int_String  = MethodTypeDescImpl.ofValidated(CD_int, CD_int, CD_String);
        static final MethodTypeDesc MTD_String_float    = MethodTypeDescImpl.ofValidated(CD_String, CD_float);
        static final MethodTypeDesc MTD_String_double   = MethodTypeDescImpl.ofValidated(CD_String, CD_double);
        static final MethodTypeDesc MTD_String_Object   = MethodTypeDescImpl.ofValidated(CD_String, CD_Object);

        static final MethodTypeDesc MTD_INIT             = MethodTypeDescImpl.ofValidated(CD_void, CD_Array_String);
        static final MethodTypeDesc MTD_NEW_ARRAY_SUFFIX = MethodTypeDescImpl.ofValidated(CD_Array_byte, CD_String, CD_int, CD_byte);
        static final MethodTypeDesc MTD_STRING_INIT      = MethodTypeDescImpl.ofValidated(CD_void, CD_Array_byte, CD_byte);

        static final MethodTypeDesc PREPEND_int     = MethodTypeDescImpl.ofValidated(CD_int, CD_int, CD_byte, CD_Array_byte, CD_int, CD_String);
        static final MethodTypeDesc PREPEND_long    = MethodTypeDescImpl.ofValidated(CD_int, CD_int, CD_byte, CD_Array_byte, CD_long, CD_String);
        static final MethodTypeDesc PREPEND_boolean = MethodTypeDescImpl.ofValidated(CD_int, CD_int, CD_byte, CD_Array_byte, CD_boolean, CD_String);
        static final MethodTypeDesc PREPEND_char    = MethodTypeDescImpl.ofValidated(CD_int, CD_int, CD_byte, CD_Array_byte, CD_char, CD_String);
        static final MethodTypeDesc PREPEND_String  = MethodTypeDescImpl.ofValidated(CD_int, CD_int, CD_byte, CD_Array_byte, CD_String, CD_String);

        static final RuntimeVisibleAnnotationsAttribute FORCE_INLINE = RuntimeVisibleAnnotationsAttribute.of(Annotation.of(ClassDesc.ofDescriptor("Ljdk/internal/vm/annotation/ForceInline;")));

        static final MethodType CONSTRUCTOR_METHOD_TYPE        = MethodType.methodType(void.class, String[].class);
        static final Consumer<CodeBuilder> CONSTRUCTOR_BUILDER = new Consumer<CodeBuilder>() {
            @Override
            public void accept(CodeBuilder cb) {
                /*
                 * super(constants);
                 */
                int thisSlot      = cb.receiverSlot(),
                    constantsSlot = cb.parameterSlot(0);
                cb.aload(thisSlot)
                  .aload(constantsSlot)
                  .invokespecial(CD_StringConcatBase, INIT_NAME, MTD_INIT, false)
                  .return_();
            }
        };

        static final ReferencedKeyMap<MethodType, SoftReference<MethodHandlePair>> CACHE =
                ReferencedKeyMap.create(true,
                        new Supplier<>() {
                            @Override
                            public Map<ReferenceKey<MethodType>, SoftReference<MethodHandlePair>> get() {
                                return new ConcurrentHashMap<>(64);
                            }
                        });

        private InlineHiddenClassStrategy() {
            // no instantiation
        }

        private record MethodHandlePair(MethodHandle constructor, MethodHandle concatenator) { };

        /**
         * The parameter types are normalized into 7 types: int,long,boolean,char,float,double,Object
         */
        private static MethodType erasedArgs(MethodType args) {
            int parameterCount = args.parameterCount();
            var paramTypes = new Class<?>[parameterCount];
            boolean changed = false;
            for (int i = 0; i < parameterCount; i++) {
                Class<?> cl = args.parameterType(i);
                // Use int as the logical type for subword integral types
                // (byte and short). char and boolean require special
                // handling so don't change the logical type of those
                if (cl == byte.class || cl == short.class) {
                    cl = int.class;
                    changed = true;
                } else if (cl != Object.class && !cl.isPrimitive()) {
                    cl = Object.class;
                    changed = true;
                }
                paramTypes[i] = cl;
            }
            return changed ? MethodType.methodType(args.returnType(), paramTypes, true) : args;
        }

        /**
         * Construct the MethodType of the prepend method, The parameters only support 5 types:
         * int/long/char/boolean/String. Not int/long/char/boolean type, use String type<p>
         *
         * The following is an example of the generated target code:
         * <blockquote><pre>
         *  int prepend(int length, byte coder, byte[] buff,  String[] constants
         *      int arg0, long arg1, boolean arg2, char arg3, String arg5)
         * </pre></blockquote>
         */
        private static MethodTypeDesc prependArgs(MethodType concatArgs, boolean staticConcat) {
            int parameterCount = concatArgs.parameterCount();
            int prefixArgs = staticConcat ? 3 : 4;
            var paramTypes = new ClassDesc[parameterCount + prefixArgs];
            paramTypes[0] = CD_int;          // length
            paramTypes[1] = CD_byte;         // coder
            paramTypes[2] = CD_Array_byte;   // buff

            if (!staticConcat) {
                paramTypes[3] = CD_Array_String; // constants
            }

            for (int i = 0; i < parameterCount; i++) {
                var cl = concatArgs.parameterType(i);
                paramTypes[i + prefixArgs] = needStringOf(cl) ? CD_String : ConstantUtils.classDesc(cl);
            }
            return MethodTypeDescImpl.ofValidated(CD_int, paramTypes);
        }

        /**
         * Construct the MethodType of the coder method. The first parameter is the initialized coder.
         * Only parameter types which can be UTF16 are added.
         * Returns null if no such parameter exists or CompactStrings is off.
         */
        private static MethodTypeDesc coderArgsIfMaybeUTF16(MethodType concatArgs) {
            if (JLA.stringInitCoder() != 0) {
                return null;
            }

            int parameterCount = concatArgs.parameterCount();

            int maybeUTF16Count = 0;
            for (int i = 0; i < parameterCount; i++) {
                if (maybeUTF16(concatArgs.parameterType(i))) {
                    maybeUTF16Count++;
                }
            }

            if (maybeUTF16Count == 0) {
                return null;
            }

            var paramTypes = new ClassDesc[maybeUTF16Count + 1];
            paramTypes[0] = CD_int; // init coder
            for (int i = 0, paramIndex = 1; i < parameterCount; i++) {
                var cl = concatArgs.parameterType(i);
                if (maybeUTF16(cl)) {
                    paramTypes[paramIndex++] = cl == char.class ? CD_char : CD_String;
                }
            }
            return MethodTypeDescImpl.ofValidated(CD_int, paramTypes);
        }

        /**
         * Construct the MethodType of the length method,
         * The first parameter is the initialized length
         */
        private static MethodTypeDesc lengthArgs(MethodType concatArgs) {
            int parameterCount = concatArgs.parameterCount();
            var paramTypes = new ClassDesc[parameterCount + 1];
            paramTypes[0] = CD_int; // init long
            for (int i = 0; i < parameterCount; i++) {
                var cl = concatArgs.parameterType(i);
                paramTypes[i + 1] = needStringOf(cl) ? CD_String : ConstantUtils.classDesc(cl);
            }
            return MethodTypeDescImpl.ofValidated(CD_int, paramTypes);
        }

        private static MethodHandle generate(Lookup lookup, MethodType args, String[] constants) throws Exception {
            lookup = STR_LOOKUP;
            final MethodType concatArgs = erasedArgs(args);

            // 1 argument use built-in method
            if (args.parameterCount() == 1) {
                Object concat1 = JLA.uncheckedStringConcat1(constants);
                var handle = lookup.findVirtual(concat1.getClass(), METHOD_NAME, concatArgs);
                return handle.bindTo(concat1);
            }

            boolean forceInline  = concatArgs.parameterCount() <  FORCE_INLINE_THRESHOLD;
            boolean staticConcat = concatArgs.parameterCount() >= CACHE_THRESHOLD;

            if (!staticConcat) {
                var weakConstructorHandle = CACHE.get(concatArgs);
                if (weakConstructorHandle != null) {
                    MethodHandlePair handlePair = weakConstructorHandle.get();
                    if (handlePair != null) {
                        try {
                            var instance = handlePair.constructor.invokeBasic((Object)constants);
                            return handlePair.concatenator.bindTo(instance);
                        } catch (Throwable e) {
                            throw new StringConcatException("Exception while utilizing the hidden class", e);
                        }
                    }
                }
            }

            MethodTypeDesc lengthArgs  = lengthArgs(concatArgs),
                           coderArgs   = coderArgsIfMaybeUTF16(concatArgs),
                           prependArgs = prependArgs(concatArgs, staticConcat);

            byte[] classBytes = ClassFile.of().build(CD_CONCAT,
                    new Consumer<ClassBuilder>() {
                        @Override
                        public void accept(ClassBuilder clb) {
                            if (staticConcat) {
                                clb.withSuperclass(CD_Object)
                                   .withFlags(ACC_ABSTRACT | ACC_SUPER | ACC_SYNTHETIC);
                            } else {
                                clb.withSuperclass(CD_StringConcatBase)
                                   .withFlags(ACC_FINAL | ACC_SUPER | ACC_SYNTHETIC)
                                   .withMethodBody(INIT_NAME, MTD_INIT, 0, CONSTRUCTOR_BUILDER);
                            }

                            clb.withMethod("length",
                                        lengthArgs,
                                        ACC_STATIC | ACC_PRIVATE,
                                        new Consumer<MethodBuilder>() {
                                            public void accept(MethodBuilder mb) {
                                                if (forceInline) {
                                                    mb.with(FORCE_INLINE);
                                                }
                                                mb.withCode(generateLengthMethod(lengthArgs));
                                            }
                                        })
                                .withMethod("prepend",
                                        prependArgs,
                                        ACC_STATIC | ACC_PRIVATE,
                                        new Consumer<MethodBuilder>() {
                                            public void accept(MethodBuilder mb) {
                                                if (forceInline) {
                                                    mb.with(FORCE_INLINE);
                                                }
                                                mb.withCode(generatePrependMethod(prependArgs, staticConcat, constants));
                                            }
                                        })
                                .withMethod(METHOD_NAME,
                                        ConstantUtils.methodTypeDesc(concatArgs),
                                        staticConcat ? ACC_STATIC | ACC_FINAL : ACC_FINAL,
                                        new Consumer<MethodBuilder>() {
                                            public void accept(MethodBuilder mb) {
                                                if (forceInline) {
                                                    mb.with(FORCE_INLINE);
                                                }
                                                mb.withCode(generateConcatMethod(
                                                        staticConcat,
                                                        constants,
                                                        CD_CONCAT,
                                                        concatArgs,
                                                        lengthArgs,
                                                        coderArgs,
                                                        prependArgs));
                                            }
                                        });

                            if (coderArgs != null) {
                                clb.withMethod("coder",
                                        coderArgs,
                                        ACC_STATIC | ACC_PRIVATE,
                                        new Consumer<MethodBuilder>() {
                                            public void accept(MethodBuilder mb) {
                                                if (forceInline) {
                                                    mb.with(FORCE_INLINE);
                                                }
                                                mb.withCode(generateCoderMethod(coderArgs));
                                            }
                                        });
                            }
                    }});
            try {
                var hiddenClass = lookup.makeHiddenClassDefiner(CLASS_NAME, classBytes, DUMPER)
                                        .defineClass(true, null);

                if (staticConcat) {
                    return lookup.findStatic(hiddenClass, METHOD_NAME, concatArgs);
                }

                var constructor = lookup.findConstructor(hiddenClass, CONSTRUCTOR_METHOD_TYPE);
                var concatenator = lookup.findVirtual(hiddenClass, METHOD_NAME, concatArgs);
                CACHE.put(concatArgs, new SoftReference<>(new MethodHandlePair(constructor, concatenator)));
                var instance = constructor.invokeBasic((Object)constants);
                return concatenator.bindTo(instance);
            } catch (Throwable e) {
                throw new StringConcatException("Exception while spinning the class", e);
            }
        }

        /**
         * Generate InlineCopy-based code. <p>
         *
         * The following is an example of the generated target code:
         *
         * <blockquote><pre>
         *  import static java.lang.StringConcatHelper.newArrayWithSuffix;
         *  import static java.lang.StringConcatHelper.prepend;
         *  import static java.lang.StringConcatHelper.stringCoder;
         *  import static java.lang.StringConcatHelper.stringSize;
         *
         *  class StringConcat extends java.lang.StringConcatHelper.StringConcatBase {
         *      // super class defines
         *      // String[] constants;
         *      // int length;
         *      // byte coder;
         *
         *      StringConcat(String[] constants) {
         *          super(constants);
         *      }
         *
         *      String concat(int arg0, long arg1, boolean arg2, char arg3, String arg4,
         *          float arg5, double arg6, Object arg7
         *      ) {
         *          // Types other than byte/short/int/long/boolean/String require a local variable to store
         *          String str4 = stringOf(arg4);
         *          String str5 = stringOf(arg5);
         *          String str6 = stringOf(arg6);
         *          String str7 = stringOf(arg7);
         *
         *          int coder  = coder(this.coder, arg0, arg1, arg2, arg3, str4, str5, str6, str7);
         *          int length = length(this.length, arg0, arg1, arg2, arg3, arg4, arg5, arg6, arg7);
         *          String[] constants = this.constants;
         *          byte[] buf = newArrayWithSuffix(constants[paramCount], length. coder);
         *
         *          prepend(length, coder, buf, constants, arg0, arg1, arg2, arg3, str4, str5, str6, str7);
         *
         *          return new String(buf, coder);
         *      }
         *
         *      static int length(int length, int arg0, long arg1, boolean arg2, char arg3,
         *                       String arg4, String arg5, String arg6, String arg7) {
         *          return stringSize(stringSize(stringSize(stringSize(stringSize(stringSize(stringSize(stringSize(
         *                      length, arg0), arg1), arg2), arg3), arg4), arg5), arg6), arg7);
         *      }
         *
         *      static int cocder(int coder, char arg3, String str4, String str5, String str6, String str7) {
         *          return coder | stringCoder(arg3) | str4.coder() | str5.coder() | str6.coder() | str7.coder();
         *      }
         *
         *      static int prepend(int length, int coder, byte[] buf, String[] constants,
         *                     int arg0, long arg1, boolean arg2, char arg3,
         *                     String str4, String str5, String str6, String str7) {
         *          // StringConcatHelper.prepend
         *          return prepend(prepend(prepend(prepend(
         *                  prepend(apppend(prepend(prepend(length,
         *                       buf, str7, constant[7]), buf, str6, constant[6]),
         *                       buf, str5, constant[5]), buf, str4, constant[4]),
         *                       buf, arg3, constant[3]), buf, arg2, constant[2]),
         *                       buf, arg1, constant[1]), buf, arg0, constant[0]);
         *      }
         *  }
         * </pre></blockquote>
         */
        private static Consumer<CodeBuilder> generateConcatMethod(
                boolean        staticConcat,
                String[]       constants,
                ClassDesc      concatClass,
                MethodType     concatArgs,
                MethodTypeDesc lengthArgs,
                MethodTypeDesc coderArgs,
                MethodTypeDesc prependArgs
        ) {
            return new Consumer<CodeBuilder>() {
                @Override
                public void accept(CodeBuilder cb) {
                    // Compute parameter variable slots
                    int paramCount    = concatArgs.parameterCount(),
                        thisSlot      = staticConcat ? 0 : cb.receiverSlot(),
                        lengthSlot    = cb.allocateLocal(TypeKind.INT),
                        coderSlot     = cb.allocateLocal(TypeKind.BYTE),
                        bufSlot       = cb.allocateLocal(TypeKind.REFERENCE),
                        constantsSlot = cb.allocateLocal(TypeKind.REFERENCE),
                        suffixSlot    = cb.allocateLocal(TypeKind.REFERENCE);

                    /*
                     * Types other than int/long/char/boolean require local variables to store the result of stringOf.
                     *
                     * stringSlots stores the slots of parameters relative to local variables
                     *
                     * str0 = stringOf(arg0);
                     * str1 = stringOf(arg1);
                     * ...
                     * strN = toString(argN);
                     */
                    int[] stringSlots = new int[paramCount];
                    for (int i = 0; i < paramCount; i++) {
                        var cl = concatArgs.parameterType(i);
                        if (needStringOf(cl)) {
                            MethodTypeDesc methodTypeDesc;
                            if (cl == float.class) {
                                methodTypeDesc = MTD_String_float;
                            } else if (cl == double.class) {
                                methodTypeDesc = MTD_String_double;
                            } else {
                                methodTypeDesc = MTD_String_Object;
                            }
                            stringSlots[i] = cb.allocateLocal(TypeKind.REFERENCE);
                            cb.loadLocal(TypeKind.from(cl), cb.parameterSlot(i))
                              .invokestatic(CD_StringConcatHelper, "stringOf", methodTypeDesc)
                              .astore(stringSlots[i]);
                        }
                    }

                    int coder  = JLA.stringInitCoder(),
                        length = 0;
                    if (staticConcat) {
                        for (var constant : constants) {
                            coder |= JLA.stringCoder(constant);
                            length += constant.length();
                        }
                    }

                    /*
                     * coder = coder(this.coder, arg0, arg1, ... argN);
                     */
                    if (staticConcat) {
                        // coder can only be 0 or 1
                        if (coder == 0) {
                            cb.iconst_0();
                        } else {
                            cb.iconst_1();
                        }
                    } else {
                        cb.aload(thisSlot)
                          .getfield(concatClass, "coder", CD_byte);
                    }

                    if (coderArgs != null) {
                        for (int i = 0; i < paramCount; i++) {
                            var cl = concatArgs.parameterType(i);
                            if (maybeUTF16(cl)) {
                                if (cl == char.class) {
                                    cb.loadLocal(TypeKind.CHAR, cb.parameterSlot(i));
                                } else {
                                    cb.aload(stringSlots[i]);
                                }
                            }
                        }
                        cb.invokestatic(concatClass, "coder", coderArgs);
                    }
                    cb.istore(coderSlot);

                    /*
                     * length = length(this.length, arg0, arg1, ..., argN);
                     */
                    if (staticConcat) {
                        cb.loadConstant(length);
                    } else {
                        cb.aload(thisSlot)
                          .getfield(concatClass, "length", CD_int);
                    }

                    for (int i = 0; i < paramCount; i++) {
                        var cl        = concatArgs.parameterType(i);
                        int paramSlot = cb.parameterSlot(i);
                        if (needStringOf(cl)) {
                            paramSlot = stringSlots[i];
                            cl = String.class;
                        }
                        cb.loadLocal(TypeKind.from(cl), paramSlot);
                    }
                    cb.invokestatic(concatClass, "length", lengthArgs);

                    /*
                     * String[] constants = this.constants;
                     * suffix  = constants[paramCount];
                     * length -= suffix.length();
                     */
                    if (staticConcat) {
                        cb.loadConstant(constants[paramCount].length())
                          .isub()
                          .istore(lengthSlot);
                    } else {
                        cb.aload(thisSlot)
                          .getfield(concatClass, "constants", CD_Array_String)
                          .dup()
                          .astore(constantsSlot)
                          .loadConstant(paramCount)
                          .aaload()
                          .dup()
                          .astore(suffixSlot)
                          .invokevirtual(CD_String, "length", MTD_int)
                          .isub()
                          .istore(lengthSlot);
                    }

                    /*
                     * Allocate buffer :
                     *
                     *  buf = newArrayWithSuffix(suffix, length, coder)
                     */
                    if (staticConcat) {
                        cb.loadConstant(constants[paramCount]);
                    } else {
                        cb.aload(suffixSlot);
                    }
                    cb.iload(lengthSlot)
                      .iload(coderSlot)
                      .invokestatic(CD_StringConcatHelper, "newArrayWithSuffix", MTD_NEW_ARRAY_SUFFIX)
                      .astore(bufSlot);

                    /*
                     * prepend(length, coder, buf, constants, ar0, ar1, ..., argN);
                     */
                    cb.iload(lengthSlot)
                      .iload(coderSlot)
                      .aload(bufSlot);
                    if (!staticConcat) {
                        cb.aload(constantsSlot);
                    }
                    for (int i = 0; i < paramCount; i++) {
                        var cl = concatArgs.parameterType(i);
                        int paramSlot = cb.parameterSlot(i);
                        var kind = TypeKind.from(cl);
                        if (needStringOf(cl)) {
                            paramSlot = stringSlots[i];
                            kind = TypeKind.REFERENCE;
                        }
                        cb.loadLocal(kind, paramSlot);
                    }
                    cb.invokestatic(concatClass, "prepend", prependArgs);

                    // return new String(buf, coder);
                    cb.new_(CD_String)
                      .dup()
                      .aload(bufSlot)
                      .iload(coderSlot)
                      .invokespecial(CD_String, INIT_NAME, MTD_STRING_INIT)
                      .areturn();
                }
            };
        }

        /**
         * Generate length method. <p>
         *
         * The following is an example of the generated target code:
         *
         * <blockquote><pre>
         * import static java.lang.StringConcatHelper.stringSize;
         *
         * static int length(int length, int arg0, long arg1, boolean arg2, char arg3,
         *                  String arg4, String arg5, String arg6, String arg7) {
         *     return stringSize(stringSize(stringSize(length, arg0), arg1), ..., arg7);
         * }
         * </pre></blockquote>
         */
        private static Consumer<CodeBuilder> generateLengthMethod(MethodTypeDesc lengthArgs) {
            return new Consumer<CodeBuilder>() {
                @Override
                public void accept(CodeBuilder cb) {
                    int lengthSlot = cb.parameterSlot(0);
                    cb.iload(lengthSlot);
                    for (int i = 1; i < lengthArgs.parameterCount(); i++) {
                        var cl = lengthArgs.parameterType(i);
                        MethodTypeDesc methodTypeDesc;
                        if (cl == CD_char) {
                            methodTypeDesc = MTD_int_int_char;
                        } else if (cl == CD_int) {
                            methodTypeDesc = MTD_int_int_int;
                        } else if (cl == CD_long) {
                            methodTypeDesc = MTD_int_int_long;
                        } else if (cl == CD_boolean) {
                            methodTypeDesc = MTD_int_int_boolean;
                        } else {
                            methodTypeDesc = MTD_int_int_String;
                        }
                        cb.loadLocal(TypeKind.from(cl), cb.parameterSlot(i))
                          .invokestatic(CD_StringConcatHelper, "stringSize", methodTypeDesc);
                    }
                    cb.ireturn();
                }
            };
        }

        /**
         * Generate coder method. <p>
         *
         * The following is an example of the generated target code:
         *
         * <blockquote><pre>
         * import static java.lang.StringConcatHelper.stringCoder;
         *
         * static int cocder(int coder, char arg3, String str4, String str5, String str6, String str7) {
         *     return coder | stringCoder(arg3) | str4.coder() | str5.coder() | str6.coder() | str7.coder();
         * }
         * </pre></blockquote>
         */
        private static Consumer<CodeBuilder> generateCoderMethod(MethodTypeDesc coderArgs) {
            return new Consumer<CodeBuilder>() {
                @Override
                public void accept(CodeBuilder cb) {
                    /*
                     * return coder | stringCoder(argN) | ... | arg1.coder() | arg0.coder();
                     */
                    int coderSlot = cb.parameterSlot(0);
                    cb.iload(coderSlot);
                    for (int i = 1; i < coderArgs.parameterCount(); i++) {
                        var cl = coderArgs.parameterType(i);
                        cb.loadLocal(TypeKind.from(cl), cb.parameterSlot(i));
                        if (cl == CD_char) {
                            cb.invokestatic(CD_StringConcatHelper, "stringCoder", MTD_byte_char);
                        } else {
                            cb.invokevirtual(CD_String, "coder", MTD_byte);
                        }
                        cb.ior();
                    }
                    cb.ireturn();
                }
            };
        }

        /**
         * Generate prepend method. <p>
         *
         * The following is an example of the generated target code:
         *
         * <blockquote><pre>
         * import static java.lang.StringConcatHelper.prepend;
         *
         * static int prepend(int length, int coder, byte[] buf, String[] constants,
         *                int arg0, long arg1, boolean arg2, char arg3,
         *                String str4, String str5, String str6, String str7) {
         *
         *     return prepend(prepend(prepend(prepend(
         *             prepend(prepend(prepend(prepend(length,
         *                  buf, str7, constant[7]), buf, str6, constant[6]),
         *                  buf, str5, constant[5]), buf, str4, constant[4]),
         *                  buf, arg3, constant[3]), buf, arg2, constant[2]),
         *                  buf, arg1, constant[1]), buf, arg0, constant[0]);
         * }
         * </pre></blockquote>
         */
        private static Consumer<CodeBuilder> generatePrependMethod(
                MethodTypeDesc prependArgs,
                boolean staticConcat, String[] constants
        ) {
            return new Consumer<CodeBuilder>() {
                @Override
                public void accept(CodeBuilder cb) {
                    // Compute parameter variable slots
                    int lengthSlot    = cb.parameterSlot(0),
                        coderSlot     = cb.parameterSlot(1),
                        bufSlot       = cb.parameterSlot(2),
                        constantsSlot = cb.parameterSlot(3);
                    /*
                     * // StringConcatHelper.prepend
                     * return prepend(prepend(prepend(prepend(
                     *         prepend(apppend(prepend(prepend(length,
                     *              buf, str7, constant[7]), buf, str6, constant[6]),
                     *              buf, str5, constant[5]), buf, arg4, constant[4]),
                     *              buf, arg3, constant[3]), buf, arg2, constant[2]),
                     *              buf, arg1, constant[1]), buf, arg0, constant[0]);
                     */
                    cb.iload(lengthSlot);
                    for (int i = prependArgs.parameterCount() - 1, end = staticConcat ? 3 : 4; i >= end; i--) {
                        var cl   = prependArgs.parameterType(i);
                        var kind = TypeKind.from(cl);

                        // There are only 5 types of parameters: int, long, boolean, char, String
                        MethodTypeDesc methodTypeDesc;
                        if (cl == CD_int) {
                            methodTypeDesc = PREPEND_int;
                        } else if (cl == CD_long) {
                            methodTypeDesc = PREPEND_long;
                        } else if (cl == CD_boolean) {
                            methodTypeDesc = PREPEND_boolean;
                        } else if (cl == CD_char) {
                            methodTypeDesc = PREPEND_char;
                        } else {
                            kind = TypeKind.REFERENCE;
                            methodTypeDesc = PREPEND_String;
                        }

                        cb.iload(coderSlot)
                          .aload(bufSlot)
                          .loadLocal(kind, cb.parameterSlot(i));

                        if (staticConcat) {
                            cb.loadConstant(constants[i - 3]);
                        } else {
                            cb.aload(constantsSlot)
                              .loadConstant(i - 4)
                              .aaload();
                        }

                        cb.invokestatic(CD_StringConcatHelper, "prepend", methodTypeDesc);
                    }
                    cb.ireturn();
                }
            };
        }

        static boolean needStringOf(Class<?> cl) {
            return cl != int.class && cl != long.class && cl != boolean.class && cl != char.class;
        }

        static boolean maybeUTF16(Class<?> cl) {
            return cl == char.class || !cl.isPrimitive();
        }
    }
}
