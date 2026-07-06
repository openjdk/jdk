/*
 * Copyright (c) 1996, 2026, Oracle and/or its affiliates. All rights reserved.
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

package java.lang.reflect;

import java.util.StringJoiner;

/**
 * Provides {@code static} methods and constants to decode {@linkplain
 * AccessFlag classfile access and property flags} with corresponding
 * {@linkplain java.compiler/javax.lang.model.element.Modifier Java
 * language modifiers}.
 * <p>
 * Modifier interpretation is context-sensitive: for example, the {@link
 * #isSynchronized(int) isSynchronized} check is only meaningful for method
 * access flags, representing the {@code synchronized} modifier on methods.
 * A {@code true} return on a field access flags does not indicate that field
 * has the {@code synchronized} modifier.
 *
 * @apiNote
 * The mappings from classfile access flags to Java language modifiers have
 * {@linkplain java.lang.reflect##LanguageJvmModel diverged} during the
 * evolution of the Java SE Platform.  Many access flags and Java language
 * modifiers are not represented in this class; the mappings represented in this
 * class are not sufficient to reconstruct Java langugage modifiers from access
 * flags, and vice versa.
 *
 * @see AccessFlag
 * @see java.compiler/javax.lang.model.element.Modifier
 * @see java.lang.reflect##LanguageJvmModel
 *      Java programming language and JVM modeling in core reflection
 * @see Class#getModifiers()
 * @see Member#getModifiers()
 * @see Parameter#getModifiers()
 *
 * @author Nakul Saraiya
 * @author Kenneth Russell
 * @since 1.1
 */
@SuppressWarnings("doclint:reference") // cross-module link
public final class Modifier {
    /**
     * Do not call.
     */
    private Modifier() {throw new AssertionError();}


    /**
     * Return {@code true} if the integer argument includes the
     * {@code public} modifier, {@code false} otherwise.
     *
     * @param   mod a set of modifiers
     * @return {@code true} if {@code mod} includes the
     * {@code public} modifier; {@code false} otherwise.
     */
    public static boolean isPublic(int mod) {
        return (mod & PUBLIC) != 0;
    }

    /**
     * Return {@code true} if the integer argument includes the
     * {@code private} modifier, {@code false} otherwise.
     *
     * @param   mod a set of modifiers
     * @return {@code true} if {@code mod} includes the
     * {@code private} modifier; {@code false} otherwise.
     */
    public static boolean isPrivate(int mod) {
        return (mod & PRIVATE) != 0;
    }

    /**
     * Return {@code true} if the integer argument includes the
     * {@code protected} modifier, {@code false} otherwise.
     *
     * @param   mod a set of modifiers
     * @return {@code true} if {@code mod} includes the
     * {@code protected} modifier; {@code false} otherwise.
     */
    public static boolean isProtected(int mod) {
        return (mod & PROTECTED) != 0;
    }

    /**
     * Return {@code true} if the integer argument includes the
     * {@code static} modifier, {@code false} otherwise.
     *
     * @param   mod a set of modifiers
     * @return {@code true} if {@code mod} includes the
     * {@code static} modifier; {@code false} otherwise.
     */
    public static boolean isStatic(int mod) {
        return (mod & STATIC) != 0;
    }

    /**
     * Return {@code true} if the integer argument includes the
     * {@code final} modifier, {@code false} otherwise.
     *
     * @param   mod a set of modifiers
     * @return {@code true} if {@code mod} includes the
     * {@code final} modifier; {@code false} otherwise.
     */
    public static boolean isFinal(int mod) {
        return (mod & FINAL) != 0;
    }

    /**
     * Return {@code true} if the integer argument includes the
     * {@code synchronized} modifier, {@code false} otherwise.
     *
     * @param   mod a set of modifiers
     * @return {@code true} if {@code mod} includes the
     * {@code synchronized} modifier; {@code false} otherwise.
     */
    public static boolean isSynchronized(int mod) {
        return (mod & SYNCHRONIZED) != 0;
    }

    /**
     * Return {@code true} if the integer argument includes the
     * {@code volatile} modifier, {@code false} otherwise.
     *
     * @param   mod a set of modifiers
     * @return {@code true} if {@code mod} includes the
     * {@code volatile} modifier; {@code false} otherwise.
     */
    public static boolean isVolatile(int mod) {
        return (mod & VOLATILE) != 0;
    }

    /**
     * Return {@code true} if the integer argument includes the
     * {@code transient} modifier, {@code false} otherwise.
     *
     * @param   mod a set of modifiers
     * @return {@code true} if {@code mod} includes the
     * {@code transient} modifier; {@code false} otherwise.
     */
    public static boolean isTransient(int mod) {
        return (mod & TRANSIENT) != 0;
    }

    /**
     * Return {@code true} if the integer argument includes the
     * {@code native} modifier, {@code false} otherwise.
     *
     * @param   mod a set of modifiers
     * @return {@code true} if {@code mod} includes the
     * {@code native} modifier; {@code false} otherwise.
     */
    public static boolean isNative(int mod) {
        return (mod & NATIVE) != 0;
    }

    /**
     * Return {@code true} if the integer argument includes the
     * {@code interface} modifier, {@code false} otherwise.
     *
     * @param   mod a set of modifiers
     * @return {@code true} if {@code mod} includes the
     * {@code interface} modifier; {@code false} otherwise.
     */
    public static boolean isInterface(int mod) {
        return (mod & INTERFACE) != 0;
    }

    /**
     * Return {@code true} if the integer argument includes the
     * {@code abstract} modifier, {@code false} otherwise.
     *
     * @param   mod a set of modifiers
     * @return {@code true} if {@code mod} includes the
     * {@code abstract} modifier; {@code false} otherwise.
     */
    public static boolean isAbstract(int mod) {
        return (mod & ABSTRACT) != 0;
    }

    /**
     * Return {@code true} if the integer argument includes the
     * {@code strictfp} modifier, {@code false} otherwise.
     *
     * @param   mod a set of modifiers
     * @return {@code true} if {@code mod} includes the
     * {@code strictfp} modifier; {@code false} otherwise.
     */
    public static boolean isStrict(int mod) {
        return (mod & STRICT) != 0;
    }

    /**
     * Return a string describing the access modifier flags in
     * the specified modifier. For example:
     * <blockquote><pre>
     *    public final synchronized strictfp
     * </pre></blockquote>
     * The modifier names are returned in an order consistent with the
     * suggested modifier orderings given in sections 8.1.1, 8.3.1, 8.4.3, 8.8.3, and 9.1.1 of
     * <cite>The Java Language Specification</cite>.
     * The full modifier ordering used by this method is:
     * <blockquote> {@code
     * public protected private abstract static final transient
     * volatile synchronized native strictfp
     * interface } </blockquote>
     *
     * The {@code interface} modifier discussed in this class is
     * not a true modifier in the Java language and it appears after
     * all other modifiers listed by this method.  This method may
     * return a string of modifiers that are not valid modifiers of a
     * Java entity; in other words, no checking is done on the
     * possible validity of the combination of modifiers represented
     * by the input.  This method also omits all access flags without
     * a corresponding source modifier.
     *
     * @deprecated
     * Modifier interpretation is context-sensitive; this API may report an
     * incomplete or incorrect list of Java language modifiers.  The mappings
     * from {@code class} file access flags to Java language modifiers have
     * {@linkplain java.lang.reflect##LanguageJvmModel diverged} during the
     * evolution of the Java SE Platform.
     * <p>
     * Use {@link AccessFlag} to examine access flags; {@code toGenericString}
     * methods on reflective objects print Java language modifiers.
     *
     * @param   mod a set of modifiers
     * @return  a string representation of the set of modifiers
     * represented by {@code mod}
     */
    @Deprecated(since = "27")
    public static String toString(int mod) {
        StringJoiner sj = new StringJoiner(" ");

        if ((mod & PUBLIC) != 0)        sj.add("public");
        if ((mod & PROTECTED) != 0)     sj.add("protected");
        if ((mod & PRIVATE) != 0)       sj.add("private");

        /* Canonical order */
        if ((mod & ABSTRACT) != 0)      sj.add("abstract");
        if ((mod & STATIC) != 0)        sj.add("static");
        if ((mod & FINAL) != 0)         sj.add("final");
        if ((mod & TRANSIENT) != 0)     sj.add("transient");
        if ((mod & VOLATILE) != 0)      sj.add("volatile");
        if ((mod & SYNCHRONIZED) != 0)  sj.add("synchronized");
        if ((mod & NATIVE) != 0)        sj.add("native");
        if ((mod & STRICT) != 0)        sj.add("strictfp");
        if ((mod & INTERFACE) != 0)     sj.add("interface");

        return sj.toString();
    }

    /*
     * Access modifier flag constants from tables 4.1, 4.4, 4.5, and 4.7 of
     * <cite>The Java Virtual Machine Specification</cite>
     */

    /**
     * The {@code int} value representing the {@code public}
     * modifier.
     * @see AccessFlag#PUBLIC
     */
    public static final int PUBLIC           = 0x00000001;

    /**
     * The {@code int} value representing the {@code private}
     * modifier.
     * @see AccessFlag#PRIVATE
     */
    public static final int PRIVATE          = 0x00000002;

    /**
     * The {@code int} value representing the {@code protected}
     * modifier.
     * @see AccessFlag#PROTECTED
     */
    public static final int PROTECTED        = 0x00000004;

    /**
     * The {@code int} value representing the {@code static}
     * modifier.
     * @see AccessFlag#STATIC
     */
    public static final int STATIC           = 0x00000008;

    /**
     * The {@code int} value representing the {@code final}
     * modifier.
     * @see AccessFlag#FINAL
     */
    public static final int FINAL            = 0x00000010;

    /**
     * The {@code int} value representing the {@code synchronized}
     * modifier.
     * @see AccessFlag#SYNCHRONIZED
     */
    public static final int SYNCHRONIZED     = 0x00000020;

    /**
     * The {@code int} value representing the {@code volatile}
     * modifier.
     * @see AccessFlag#VOLATILE
     */
    public static final int VOLATILE         = 0x00000040;

    /**
     * The {@code int} value representing the {@code transient}
     * modifier.
     * @see AccessFlag#TRANSIENT
     */
    public static final int TRANSIENT        = 0x00000080;

    /**
     * The {@code int} value representing the {@code native}
     * modifier.
     * @see AccessFlag#NATIVE
     */
    public static final int NATIVE           = 0x00000100;

    /**
     * The {@code int} value representing the {@code interface}
     * modifier.
     * @see AccessFlag#INTERFACE
     */
    public static final int INTERFACE        = 0x00000200;

    /**
     * The {@code int} value representing the {@code abstract}
     * modifier.
     * @see AccessFlag#ABSTRACT
     */
    public static final int ABSTRACT         = 0x00000400;

    /**
     * The {@code int} value representing the {@code strictfp}
     * modifier.
     * @see AccessFlag#STRICT
     */
    public static final int STRICT           = 0x00000800;

    // Bits not (yet) exposed in the public API either because they
    // have different meanings for fields and methods and there is no
    // way to distinguish between the two in this class, or because
    // they are not Java programming language keywords
    static final int BRIDGE    = 0x00000040;
    static final int VARARGS   = 0x00000080;
    static final int SYNTHETIC = 0x00001000;
    static final int ANNOTATION  = 0x00002000;
    static final int ENUM      = 0x00004000;
    static final int MANDATED  = 0x00008000;
    static boolean isSynthetic(int mod) {
      return (mod & SYNTHETIC) != 0;
    }

    static boolean isMandated(int mod) {
      return (mod & MANDATED) != 0;
    }

    // Note on the FOO_MODIFIERS fields and fooModifiers() methods:
    // the sets of modifiers are not guaranteed to be constants
    // across time and Java SE releases. Therefore, it would not be
    // appropriate to expose an external interface to this information
    // that would allow the values to be treated as Java-level
    // constants since the values could be constant folded and updates
    // to the sets of modifiers missed. Thus, the fooModifiers()
    // methods return an unchanging values for a given release, but a
    // value that can potentially change over time.

    /**
     * The Java source modifiers that can be applied to a class.
     * @jls 8.1.1 Class Modifiers
     */
    private static final int CLASS_MODIFIERS =
        Modifier.PUBLIC         | Modifier.PROTECTED    | Modifier.PRIVATE |
        Modifier.ABSTRACT       | Modifier.STATIC       | Modifier.FINAL   |
        Modifier.STRICT;

    /**
     * The Java source modifiers that can be applied to an interface.
     * @jls 9.1.1 Interface Modifiers
     */
    private static final int INTERFACE_MODIFIERS =
        Modifier.PUBLIC         | Modifier.PROTECTED    | Modifier.PRIVATE |
        Modifier.ABSTRACT       | Modifier.STATIC       | Modifier.STRICT;


    /**
     * The Java source modifiers that can be applied to a constructor.
     * @jls 8.8.3 Constructor Modifiers
     */
    private static final int CONSTRUCTOR_MODIFIERS =
        Modifier.PUBLIC         | Modifier.PROTECTED    | Modifier.PRIVATE;

    /**
     * The Java source modifiers that can be applied to a method.
     * @jls 8.4.3  Method Modifiers
     */
    private static final int METHOD_MODIFIERS =
        Modifier.PUBLIC         | Modifier.PROTECTED    | Modifier.PRIVATE |
        Modifier.ABSTRACT       | Modifier.STATIC       | Modifier.FINAL   |
        Modifier.SYNCHRONIZED   | Modifier.NATIVE       | Modifier.STRICT;

    /**
     * The Java source modifiers that can be applied to a field.
     * @jls 8.3.1 Field Modifiers
     */
    private static final int FIELD_MODIFIERS =
        Modifier.PUBLIC         | Modifier.PROTECTED    | Modifier.PRIVATE |
        Modifier.STATIC         | Modifier.FINAL        | Modifier.TRANSIENT |
        Modifier.VOLATILE;

    /**
     * The Java source modifiers that can be applied to a method or constructor parameter.
     * @jls 8.4.1 Formal Parameters
     */
    private static final int PARAMETER_MODIFIERS =
        Modifier.FINAL;

    /**
     * Return an {@code int} value OR-ing together the source language
     * modifiers that can be applied to a class.
     * @return an {@code int} value OR-ing together the source language
     * modifiers that can be applied to a class.
     *
     * @deprecated
     * This method was originally created to support the now-deprecated
     * {@link #toString(int) Modifier::toString(int)} method.
     * Use {@link AccessFlag.Location} to inspect structure-specific
     * access modifier properties.
     *
     * @see AccessFlag.Location#CLASS
     * @see AccessFlag.Location#INNER_CLASS
     * @jls 8.1.1 Class Modifiers
     * @since 1.7
     */
    @Deprecated(since = "27")
    public static int classModifiers() {
        return CLASS_MODIFIERS;
    }

    /**
     * Return an {@code int} value OR-ing together the source language
     * modifiers that can be applied to an interface.
     * @return an {@code int} value OR-ing together the source language
     * modifiers that can be applied to an interface.
     *
     * @deprecated
     * This method was originally created to support the now-deprecated
     * {@link #toString(int) Modifier::toString(int)} method.
     * Use {@link AccessFlag.Location} to inspect structure-specific
     * access modifier properties.
     *
     * @see AccessFlag.Location#CLASS
     * @see AccessFlag.Location#INNER_CLASS
     * @jls 9.1.1 Interface Modifiers
     * @since 1.7
     */
    @Deprecated(since = "27")
    public static int interfaceModifiers() {
        return INTERFACE_MODIFIERS;
    }

    /**
     * Return an {@code int} value OR-ing together the source language
     * modifiers that can be applied to a constructor.
     * @return an {@code int} value OR-ing together the source language
     * modifiers that can be applied to a constructor.
     *
     * @deprecated
     * This method was originally created to support the now-deprecated
     * {@link #toString(int) Modifier::toString(int)} method.
     * Use {@link AccessFlag.Location} to inspect structure-specific
     * access modifier properties.
     *
     * @see AccessFlag.Location#METHOD
     * @jls 8.8.3 Constructor Modifiers
     * @since 1.7
     */
    @Deprecated(since = "27")
    public static int constructorModifiers() {
        return CONSTRUCTOR_MODIFIERS;
    }

    /**
     * Return an {@code int} value OR-ing together the source language
     * modifiers that can be applied to a method.
     * @return an {@code int} value OR-ing together the source language
     * modifiers that can be applied to a method.
     *
     * @deprecated
     * This method was originally created to support the now-deprecated
     * {@link #toString(int) Modifier::toString(int)} method.
     * Use {@link AccessFlag.Location} to inspect structure-specific
     * access modifier properties.
     *
     * @see AccessFlag.Location#METHOD
     * @jls 8.4.3 Method Modifiers
     * @since 1.7
     */
    @Deprecated(since = "27")
    public static int methodModifiers() {
        return METHOD_MODIFIERS;
    }

    /**
     * Return an {@code int} value OR-ing together the source language
     * modifiers that can be applied to a field.
     * @return an {@code int} value OR-ing together the source language
     * modifiers that can be applied to a field.
     *
     * @deprecated
     * This method was originally created to support the now-deprecated
     * {@link #toString(int) Modifier::toString(int)} method.
     * Use {@link AccessFlag.Location} to inspect structure-specific
     * access modifier properties.
     *
     * @see AccessFlag.Location#FIELD
     * @jls 8.3.1 Field Modifiers
     * @since 1.7
     */
    @Deprecated(since = "27")
    public static int fieldModifiers() {
        return FIELD_MODIFIERS;
    }

    /**
     * Return an {@code int} value OR-ing together the source language
     * modifiers that can be applied to a parameter.
     * @return an {@code int} value OR-ing together the source language
     * modifiers that can be applied to a parameter.
     *
     * @deprecated
     * This method was originally created to support the now-deprecated
     * {@link #toString(int) Modifier::toString(int)} method.
     * Use {@link AccessFlag.Location} to inspect structure-specific
     * access modifier properties.
     *
     * @see AccessFlag.Location#METHOD_PARAMETER
     * @jls 8.4.1 Formal Parameters
     * @since 1.8
     */
    @Deprecated(since = "27")
    public static int parameterModifiers() {
        return PARAMETER_MODIFIERS;
    }
}
