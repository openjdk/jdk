/*
 * Copyright (c) 2005, 2023, Oracle and/or its affiliates. All rights reserved.
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

package javax.lang.model;

import java.util.Collections;
import java.util.Set;
import java.util.HashSet;

/**
 * Source versions of the Java programming language.
 *
 * See the appropriate edition of
 * <cite>The Java Language Specification</cite>
 * for information about a particular source version.
 *
 * <p>Note that additional source version constants will be added to
 * model future releases of the language.
 *
 * @since 1.6
 * @see java.lang.reflect.ClassFileFormatVersion
 */
public enum SourceVersion {
    /*
     * Summary of language evolution
     * 1.1: nested classes
     * 1.2: strictfp
     * 1.3: no changes
     * 1.4: assert
     * 1.5: annotations, generics, autoboxing, var-args...
     * 1.6: no changes
     * 1.7: diamond syntax, try-with-resources, etc.
     * 1.8: lambda expressions and default methods
     *   9: modules, small cleanups to 1.7 and 1.8 changes
     *  10: local-variable type inference (var)
     *  11: local-variable syntax for lambda parameters
     *  12: no changes (switch expressions in preview)
     *  13: no changes (text blocks in preview; switch expressions in
     *      second preview)
     *  14: switch expressions (pattern matching and records in
     *      preview; text blocks in second preview)
     *  15: text blocks (sealed classes in preview; records and pattern
     *      matching in second preview)
     *  16: records and pattern matching (sealed classes in second preview)
     *  17: sealed classes, floating-point always strict (pattern
     *      matching for switch in preview)
     *  18: no changes (pattern matching for switch in second preview)
     *  19: no changes (pattern matching for switch in third preview,
     *      record patterns in preview)
     *  20: no changes (pattern matching for switch in fourth preview,
     *      record patterns in second preview)
     *  21: pattern matching for switch and record patterns (string
     *      templates in preview, unnamed patterns and variables in
     *      preview, unnamed classes and instance main methods in preview)
     *  22: Unnamed Variables & Patterns (Statements before super(...)
     *      in Preview)
     */

    /**
     * The original version.
     *
     * The language described in
     * <cite>The Java Language Specification, First Edition</cite>.
     */
    RELEASE_0,

    /**
     * The version introduced by the Java Platform 1.1.
     *
     * The language is {@code RELEASE_0} augmented with nested classes
     * as described in the 1.1 update to <cite>The Java Language
     * Specification, First Edition</cite>.
     */
    RELEASE_1,

    /**
     * The version introduced by the Java 2 Platform, Standard Edition,
     * v 1.2.
     *
     * The language described in
     * <cite>The Java Language Specification,
     * Second Edition</cite>, which includes the {@code
     * strictfp} modifier.
     */
    RELEASE_2,

    /**
     * The version introduced by the Java 2 Platform, Standard Edition,
     * v 1.3.
     *
     * No major changes from {@code RELEASE_2}.
     */
    RELEASE_3,

    /**
     * The version introduced by the Java 2 Platform, Standard Edition,
     * v 1.4.
     *
     * Added a simple assertion facility.
     *
     * @see <a href="https://jcp.org/en/jsr/detail?id=41">
     * JSR 41: A Simple Assertion Facility</a>
     */
    RELEASE_4,

    /**
     * The version introduced by the Java 2 Platform, Standard
     * Edition 5.0.
     *
     * The language described in
     * <cite>The Java Language Specification,
     * Third Edition</cite>.  First release to support
     * generics, annotations, autoboxing, var-args, enhanced {@code
     * for} loop, and hexadecimal floating-point literals.
     *
     * @see <a href="https://jcp.org/en/jsr/detail?id=14">
     * JSR 14: Add Generic Types To The Java&trade; Programming Language</a>
     * @see <a href="https://jcp.org/en/jsr/detail?id=175">
     * JSR 175: A Metadata Facility for the Java&trade; Programming Language</a>
     * @see <a href="https://jcp.org/en/jsr/detail?id=201">
     * JSR 201: Extending the Java&trade; Programming Language with Enumerations,
     * Autoboxing, Enhanced for loops and Static Import</a>
     */
    RELEASE_5,

    /**
     * The version introduced by the Java Platform, Standard Edition
     * 6.
     *
     * No major changes from {@code RELEASE_5}.
     *
     * @see <a
     * href="https://docs.oracle.com/javase/specs/jls/se6/html/j3TOC.html">
     * <cite>The Java Language Specification, Third Edition</cite></a>
     */
    RELEASE_6,

    /**
     * The version introduced by the Java Platform, Standard Edition
     * 7.
     *
     * Additions in this release include diamond syntax for
     * constructors, {@code try}-with-resources, strings in switch,
     * binary literals, and multi-catch.
     * @since 1.7
     *
     * @see <a
     * href="https://docs.oracle.com/javase/specs/jls/se7/html/index.html">
     * <cite>The Java Language Specification, Java SE 7 Edition</cite></a>
     * @see <a href="https://jcp.org/en/jsr/detail?id=334">
     * JSR 334: Small Enhancements to the Java&trade; Programming Language</a>
     */
    RELEASE_7,

    /**
     * The version introduced by the Java Platform, Standard Edition
     * 8.
     *
     * Additions in this release include lambda expressions and default methods.
     * @since 1.8
     *
     * @see <a
     * href="https://docs.oracle.com/javase/specs/jls/se8/html/index.html">
     * <cite>The Java Language Specification, Java SE 8 Edition</cite></a>
     * @see <a href="https://jcp.org/en/jsr/detail?id=335">
     * JSR 335: Lambda Expressions for the Java&trade; Programming Language</a>
     */
    RELEASE_8,

    /**
     * The version introduced by the Java Platform, Standard Edition
     * 9.
     *
     * Additions in this release include modules and removal of a
     * single underscore from the set of legal identifier names.
     *
     * @since 9
     *
     * @see <a
     * href="https://docs.oracle.com/javase/specs/jls/se9/html/index.html">
     * <cite>The Java Language Specification, Java SE 9 Edition</cite></a>
     * @see <a href="https://jcp.org/en/jsr/detail?id=376">
     * JSR 376: Java&trade; Platform Module System</a>
     * @see <a href="https://openjdk.org/jeps/213">
     * JEP 213: Milling Project Coin</a>
     */
     RELEASE_9,

    /**
     * The version introduced by the Java Platform, Standard Edition
     * 10.
     *
     * Additions in this release include local-variable type inference
     * ({@code var}).
     *
     * @since 10
     *
     * @see <a
     * href="https://docs.oracle.com/javase/specs/jls/se10/html/index.html">
     * <cite>The Java Language Specification, Java SE 10 Edition</cite></a>
     * @see <a href="https://openjdk.org/jeps/286">
     * JEP 286: Local-Variable Type Inference</a>
     */
    RELEASE_10,

    /**
     * The version introduced by the Java Platform, Standard Edition
     * 11.
     *
     * Additions in this release include local-variable syntax for
     * lambda parameters.
     *
     * @since 11
     *
     * @see <a
     * href="https://docs.oracle.com/javase/specs/jls/se11/html/index.html">
     * <cite>The Java Language Specification, Java SE 11 Edition</cite></a>
     * @see <a href="https://openjdk.org/jeps/323">
     * JEP 323: Local-Variable Syntax for Lambda Parameters</a>
     */
    RELEASE_11,

    /**
     * The version introduced by the Java Platform, Standard Edition
     * 12.
     * No major changes from the prior release.
     *
     * @since 12
     *
     * @see <a
     * href="https://docs.oracle.com/javase/specs/jls/se12/html/index.html">
     * <cite>The Java Language Specification, Java SE 12 Edition</cite></a>
     */
    RELEASE_12,

    /**
     * The version introduced by the Java Platform, Standard Edition
     * 13.
     * No major changes from the prior release.
     *
     * @since 13
     *
     * @see <a
     * href="https://docs.oracle.com/javase/specs/jls/se13/html/index.html">
     * <cite>The Java Language Specification, Java SE 13 Edition</cite></a>
     */
    RELEASE_13,

    /**
     * The version introduced by the Java Platform, Standard Edition
     * 14.
     *
     * Additions in this release include switch expressions.
     *
     * @since 14
     *
     * @see <a
     * href="https://docs.oracle.com/javase/specs/jls/se14/html/index.html">
     * <cite>The Java Language Specification, Java SE 14 Edition</cite></a>
     * @see <a href="https://openjdk.org/jeps/361">
     * JEP 361: Switch Expressions</a>
     */
    RELEASE_14,

    /**
     * The version introduced by the Java Platform, Standard Edition
     * 15.
     *
     * Additions in this release include text blocks.
     *
     * @since 15
     *
     * @see <a
     * href="https://docs.oracle.com/javase/specs/jls/se15/html/index.html">
     * <cite>The Java Language Specification, Java SE 15 Edition</cite></a>
     * @see <a href="https://openjdk.org/jeps/378">
     * JEP 378: Text Blocks</a>
     */
    RELEASE_15,

    /**
     * The version introduced by the Java Platform, Standard Edition
     * 16.
     *
     * Additions in this release include records and pattern matching
     * for {@code instanceof}.
     *
     * @since 16
     *
     * @see <a
     * href="https://docs.oracle.com/javase/specs/jls/se16/html/index.html">
     * <cite>The Java Language Specification, Java SE 16 Edition</cite></a>
     * @see <a href="https://openjdk.org/jeps/394">
     * JEP 394: Pattern Matching for instanceof</a>
     * @see <a href="https://openjdk.org/jeps/395">
     * JEP 395: Records</a>
     */
    RELEASE_16,

    /**
     * The version introduced by the Java Platform, Standard Edition
     * 17.
     *
     * Additions in this release include sealed classes and
     * restoration of always-strict floating-point semantics.
     *
     * @since 17
     *
     * @see <a
     * href="https://docs.oracle.com/javase/specs/jls/se17/html/index.html">
     * <cite>The Java Language Specification, Java SE 17 Edition</cite></a>
     * @see <a href="https://openjdk.org/jeps/306">
     * JEP 306: Restore Always-Strict Floating-Point Semantics</a>
     * @see <a href="https://openjdk.org/jeps/409">
     * JEP 409: Sealed Classes</a>
     */
    RELEASE_17,

    /**
     * The version introduced by the Java Platform, Standard Edition
     * 18.
     *
     * No major changes from the prior release.
     *
     * @since 18
     *
     * @see <a
     * href="https://docs.oracle.com/javase/specs/jls/se18/html/index.html">
     * <cite>The Java Language Specification, Java SE 18 Edition</cite></a>
     */
    RELEASE_18,

    /**
     * The version introduced by the Java Platform, Standard Edition
     * 19.
     *
     * No major changes from the prior release.
     *
     * @since 19
     *
     * @see <a
     * href="https://docs.oracle.com/javase/specs/jls/se19/html/index.html">
     * <cite>The Java Language Specification, Java SE 19 Edition</cite></a>
     */
    RELEASE_19,

    /**
     * The version introduced by the Java Platform, Standard Edition
     * 20.
     *
     * No major changes from the prior release.
     *
     * @since 20
     *
     * @see <a
     * href="https://docs.oracle.com/javase/specs/jls/se20/html/index.html">
     * <cite>The Java Language Specification, Java SE 20 Edition</cite></a>
     */
    RELEASE_20,

    /**
     * The version introduced by the Java Platform, Standard Edition
     * 21.
     *
     * Additions in this release include record patterns and pattern
     * matching for {@code switch}.
     *
     * @since 21
     *
     * @see <a
     * href="https://docs.oracle.com/javase/specs/jls/se21/html/index.html">
     * <cite>The Java Language Specification, Java SE 21 Edition</cite></a>
     * @see <a href="https://openjdk.org/jeps/440">
     * JEP 440: Record Patterns</a>
     * @see <a href="https://openjdk.org/jeps/441">
     * JEP 441: Pattern Matching for switch</a>
     */
    RELEASE_21,

    /**
     * The version introduced by the Java Platform, Standard Edition
     * 22.
     *
     * Additions in this release include unnamed variables and unnamed
     * patterns.
     *
     * @since 22
     *
     * @see <a
     * href="https://docs.oracle.com/javase/specs/jls/se22/html/index.html">
     * <cite>The Java Language Specification, Java SE 22 Edition</cite></a>
     * @see <a href="https://openjdk.org/jeps/456">
     * JEP 456: Unnamed Variables &amp; Patterns</a>
     */
    RELEASE_22,

    /**
     * The version introduced by the Java Platform, Standard Edition
     * 23.
     *
     * @since 23
     *
     * @see <a
     * href="https://docs.oracle.com/javase/specs/jls/se23/html/index.html">
     * <cite>The Java Language Specification, Java SE 23 Edition</cite></a>
     */
    RELEASE_23,
    ; // Reduce code churn when appending new constants

    // Note that when adding constants for newer releases, the
    // behavior of latest() and latestSupported() must be updated too.

    /**
     * {@return the latest source version that can be modeled}
     */
    public static SourceVersion latest() {
        return RELEASE_23;
    }

    private static final SourceVersion latestSupported = getLatestSupported();

    /*
     * The integer version to enum constant mapping implemented by
     * this method assumes the JEP 322: "Time-Based Release
     * Versioning" scheme is in effect. This scheme began in JDK
     * 10. If the JDK versioning scheme is revised, this method may
     * need to be updated accordingly.
     */
    private static SourceVersion getLatestSupported() {
        int intVersion = Runtime.version().feature();
        return (intVersion >= 11) ?
            valueOf("RELEASE_" + Math.min(23, intVersion)):
            RELEASE_10;
    }

    /**
     * {@return the latest source version fully supported by the
     * current execution environment}  {@code RELEASE_9} or later must
     * be returned.
     *
     * @apiNote This method is included alongside {@link latest} to
     * allow identification of situations where the language model API
     * is running on a platform version different than the latest
     * version modeled by the API. One way that sort of situation can
     * occur is if an IDE or similar tool is using the API to model
     * source version <i>N</i> while running on platform version
     * (<i>N</i>&nbsp;-&nbsp;1). Running in this configuration is
     * supported by the API. Running an API on platform versions
     * earlier than (<i>N</i>&nbsp;-&nbsp;1) or later than <i>N</i>
     * may or may not work as an implementation detail. If an
     * annotation processor was generating code to run under the
     * current execution environment, the processor should only use
     * platform features up to the {@code latestSupported} release,
     * which may be earlier than the {@code latest} release.
     */
    public static SourceVersion latestSupported() {
        return latestSupported;
    }

    /**
     * Returns whether or not {@code name} is a syntactically valid
     * identifier (simple name) or keyword in the latest source
     * version.  The method returns {@code true} if the name consists
     * of an initial character for which {@link
     * Character#isJavaIdentifierStart(int)} returns {@code true},
     * followed only by characters for which {@link
     * Character#isJavaIdentifierPart(int)} returns {@code true}.
     * This pattern matches regular identifiers, keywords, contextual
     * keywords, and the literals {@code "true"},
     * {@code "false"}, {@code "null"}.
     *
     * The method returns {@code false} for all other strings.
     *
     * @param name the string to check
     * @return {@code true} if this string is a
     * syntactically valid identifier or keyword, {@code false}
     * otherwise.
     *
     * @jls 3.8 Identifiers
     */
    public static boolean isIdentifier(CharSequence name) {
        String id = name.toString();

        if (id.length() == 0) {
            return false;
        }
        int cp = id.codePointAt(0);
        if (!Character.isJavaIdentifierStart(cp)) {
            return false;
        }
        for (int i = Character.charCount(cp);
                i < id.length();
                i += Character.charCount(cp)) {
            cp = id.codePointAt(i);
            if (!Character.isJavaIdentifierPart(cp)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns whether or not {@code name} is a syntactically valid
     * qualified name in the latest source version.
     *
     * Syntactically, a qualified name is a sequence of identifiers
     * separated by period characters ("{@code .}"). This method
     * splits the input string into period-separated segments and
     * applies checks to each segment in turn.
     *
     * Unlike {@link #isIdentifier isIdentifier}, this method returns
     * {@code false} for keywords, boolean literals, and the null
     * literal in any segment.
     *
     * This method returns {@code true} for <i>contextual
     * keywords</i>.
     *
     * @param name the string to check
     * @return {@code true} if this string is a
     * syntactically valid name, {@code false} otherwise.
     * @jls 3.9 Keywords
     * @jls 6.2 Names and Identifiers
     */
    public static boolean isName(CharSequence name) {
        return isName(name, latest());
    }

    /**
     * Returns whether or not {@code name} is a syntactically valid
     * qualified name in the given source version.
     *
     * Syntactically, a qualified name is a sequence of identifiers
     * separated by period characters ("{@code .}"). This method
     * splits the input string into period-separated segments and
     * applies checks to each segment in turn.
     *
     * Unlike {@link #isIdentifier isIdentifier}, this method returns
     * {@code false} for keywords, boolean literals, and the null
     * literal in any segment.
     *
     * This method returns {@code true} for <i>contextual
     * keywords</i>.
     *
     * @param name the string to check
     * @param version the version to use
     * @return {@code true} if this string is a
     * syntactically valid name, {@code false} otherwise.
     * @jls 3.9 Keywords
     * @jls 6.2 Names and Identifiers
     * @since 9
     */
    public static boolean isName(CharSequence name, SourceVersion version) {
        String id = name.toString();

        for(String s : id.split("\\.", -1)) {
            if (!isIdentifier(s) || isKeyword(s, version))
                return false;
        }
        return true;
    }

    /**
     * Returns whether or not {@code s} is a keyword, boolean literal,
     * or null literal in the latest source version.
     * This method returns {@code false} for <i>contextual
     * keywords</i>.
     *
     * @param s the string to check
     * @return {@code true} if {@code s} is a keyword, or boolean
     * literal, or null literal, {@code false} otherwise.
     * @jls 3.9 Keywords
     * @jls 3.10.3 Boolean Literals
     * @jls 3.10.8 The Null Literal
     */
    public static boolean isKeyword(CharSequence s) {
        return isKeyword(s, latest());
    }

    /**
     * Returns whether or not {@code s} is a keyword, boolean literal,
     * or null literal in the given source version.
     * This method returns {@code false} for <i>contextual
     * keywords</i>.
     *
     * @param s the string to check
     * @param version the version to use
     * @return {@code true} if {@code s} is a keyword, or boolean
     * literal, or null literal, {@code false} otherwise.
     * @jls 3.9 Keywords
     * @jls 3.10.3 Boolean Literals
     * @jls 3.10.8 The Null Literal
     * @since 9
     */
    public static boolean isKeyword(CharSequence s, SourceVersion version) {
        String id = s.toString();
        switch(id) {
            // A trip through history
        case "strictfp":
            return version.compareTo(RELEASE_2) >= 0;

        case "assert":
            return version.compareTo(RELEASE_4) >= 0;

        case "enum":
            return version.compareTo(RELEASE_5) >= 0;

        case "_":
            return version.compareTo(RELEASE_9) >= 0;

     // case "non-sealed": can be added once it is a keyword only
     // dependent on release and not also preview features being
     // enabled.

            // Keywords common across versions

            // Modifiers
        case "public":    case "protected": case "private":
        case "abstract":  case "static":    case "final":
        case "transient": case "volatile":  case "synchronized":
        case "native":

            // Declarations
        case "class":     case "interface": case "extends":
        case "package":   case "throws":    case "implements":

            // Primitive types and void
        case "boolean":   case "byte":      case "char":
        case "short":     case "int":       case "long":
        case "float":     case "double":
        case "void":

            // Control flow
        case "if":      case "else":
        case "try":     case "catch":    case "finally":
        case "do":      case "while":
        case "for":     case "continue":
        case "switch":  case "case":     case "default":
        case "break":   case "throw":    case "return":

            // Other keywords
        case  "this":   case "new":      case "super":
        case "import":  case "instanceof":

            // Forbidden!
        case "goto":        case "const":

            // literals
        case "null":         case "true":       case "false":
            return true;

        default:
            return false;
        }
    }

    /**
     * {@return the latest source version that is usable under the
     * runtime version argument} If the runtime version's {@linkplain
     * Runtime.Version#feature() feature} is greater than the feature
     * of the {@linkplain #runtimeVersion() runtime version} of the
     * {@linkplain #latest() latest source version}, an {@code
     * IllegalArgumentException} is thrown.
     *
     * <p>Because the source versions of the Java programming language
     * have so far followed a linear progression, only the feature
     * component of a runtime version is queried to determine the
     * mapping to a source version. If that linearity changes in the
     * future, other components of the runtime version may influence
     * the result.
     *
     * @apiNote
     * An expression to convert from a string value, for example
     * {@code "17"}, to the corresponding source version, {@code
     * RELEASE_17}, is:
     *
     * {@snippet lang="java" :
     * SourceVersion.valueOf(Runtime.Version.parse("17"))}
     *
     * @param rv runtime version to map to a source version
     * @throws IllegalArgumentException if the feature of version
     * argument is greater than the feature of the platform version.
     * @since 18
     */
    public static SourceVersion valueOf(Runtime.Version rv) {
        // Could also implement this as a switch where a case was
        // added with each new release.
        return valueOf("RELEASE_" + rv.feature());
    }

    /**
     * {@return the least runtime version that supports this source
     * version; otherwise {@code null}} The returned runtime version
     * has a {@linkplain Runtime.Version#feature() feature} large
     * enough to support this source version and has no other elements
     * set.
     *
     * Source versions greater than or equal to {@link RELEASE_6}
     * have non-{@code null} results.
     * @since 18
     */
    public Runtime.Version runtimeVersion() {
        // The javax.lang.model API was added in JDK 6; for now,
        // limiting supported range to 6 and up.
        if (this.compareTo(RELEASE_6) >= 0) {
            return Runtime.Version.parse(Integer.toString(ordinal()));
        } else {
            return null;
        }
    }
}
