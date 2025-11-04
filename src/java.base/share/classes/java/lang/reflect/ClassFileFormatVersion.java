/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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

/**
 * Class file format versions of the Java virtual machine.
 *
 * See the appropriate edition of <cite>The Java Virtual Machine
 * Specification</cite> for information about a particular class file
 * format version.
 *
 * <p>Note that additional class file format version constants will be
 * added to model future releases of the Java Virtual Machine
 * Specification.
 *
 * @apiNote
 * The complete version used in a class file includes a major version
 * and a minor version; this enum only models the major version. A
 * Java virtual machine implementation is required to support a range
 * of major versions; see the corresponding edition of the <cite>The
 * Java Virtual Machine Specification</cite> for details.
 *
 * @since 20
 * @see System#getProperties System property {@code java.class.version}
 * @see java.compiler/javax.lang.model.SourceVersion
 */
@SuppressWarnings("doclint:reference") // cross-module links
public enum ClassFileFormatVersion {
    /*
     * Summary of class file format evolution; previews are listed for
     * convenience, but they are not modeled by this enum.
     * 1.1: InnerClasses, Synthetic, Deprecated attributes
     * 1.2: ACC_STRICT modifier
     * 1.3: no changes
     * 1.4: no changes
     * 1.5: Annotations (Runtime(Inv/V)isible(Parameter)Annotations attributes);
     *      Generics (Signature, LocalVariableTypeTable attributes);
     *      EnclosingMethod attribute
     * 1.6: Verification by type checking (StackMapTable attribute)
     * 1.7: Verification by type checking enforced (jsr and ret opcodes
     *      obsolete); java.lang.invoke support (JSR 292) (CONSTANT_MethodHandle,
     *      CONSTANT_MethodType, CONSTANT_InvokeDynamic constant pool entries,
     *      BoostrapMethods attribute); <clinit> method must be ACC_STATIC
     * 1.8: private, static, and non-abstract (default) methods in interfaces;
     *      Type Annotations (JEP 104) (Runtime(Inv/V)isibleTypeAnnotations
     *      attribute); MethodParameters attribute
     *   9: JSR 376 - modules (JSR 376, JEP 261) (Module, ModuleMainClass,
     *      ModulePackages attributes, CONSTANT_Module, CONSTANT_Package
     *      constant pool entries, ACC_MODULE modifier)
     *  10: minor tweak to requires_flags in Module attribute
     *  11: Nest mates (JEP 181) (NestHost, NestMembers attributes);
     *      CONSTANT_Dynamic (JEP 309) constant pool entry
     *  12: Preview Features (JEP 12) (minor version must be 0 or 65535)
     *  13: no changes
     *  14: no changes; (JEP 359 Records in Preview)
     *  15: no changes; (JEP 384 Records in 2nd Preview, JEP 360 Sealed Classes
     *      in Preview)
     *  16: Records (JEP 395) (Record attribute); (JEP 397 Sealed Classes in 2nd
     *      Preview)
     *  17: Sealed Classes (JEP 409) (PermittedSubclasses attribute); ACC_STRICT
     *      modifier obsolete (JEP 306)
     *  18: no changes
     *  19: no changes
     *  20: no changes
     *  21: no changes
     *  22: no changes
     *  23: no changes
     *  24: no changes
     */

    /**
     * The original version.
     *
     * The format described in <cite>The Java Virtual Specification,
     * First Edition</cite>.
     */
    RELEASE_0(45),

    /**
     * The version recognized by the Java Platform 1.1.
     *
     * @apiNote
     * While {@code RELEASE_0} and {@code RELEASE_1} have the same
     * {@linkplain #major() major version}, several additional
     * attributes were defined for {@code RELEASE_1} (JVMS {@jvms
     * 4.7}).
     *
     */
    RELEASE_1(45),

    /**
     * The version introduced by the Java 2 Platform, Standard Edition,
     * v 1.2.
     *
     * The format described in <cite>The Java Virtual Machine
     * Specification, Second Edition</cite>, which includes the {@link
     * AccessFlag#STRICT ACC_STRICT} access flag.
     */
    RELEASE_2(46),

    /**
     * The version introduced by the Java 2 Platform, Standard Edition,
     * v 1.3.
     */
    RELEASE_3(47),

    /**
     * The version introduced by the Java 2 Platform, Standard Edition,
     * v 1.4.
     */
    RELEASE_4(48),

    /**
     * The version introduced by the Java 2 Platform, Standard
     * Edition 5.0.
     *
     * @see <a
     * href="https://jcp.org/aboutJava/communityprocess/maintenance/jsr924/index.html">
     * <cite>The Java Virtual Machine Specification, Second Edition updated for Java SE 5.0</cite></a>
     * @see <a href="https://jcp.org/en/jsr/detail?id=14">
     * JSR 14: Add Generic Types To The Java&trade; Programming Language</a>
     * @see <a href="https://jcp.org/en/jsr/detail?id=175">
     * JSR 175: A Metadata Facility for the Java&trade; Programming Language</a>
     */
    RELEASE_5(49),

    /**
     * The version introduced by the Java Platform, Standard Edition
     * 6.
     *
     * @see <a
     * href="https://jcp.org/aboutJava/communityprocess/maintenance/jsr924/index2.html">
     * <cite>The Java Virtual Machine Specification, Java SE, Second Edition updated for Java SE 6</cite></a>
     */
    RELEASE_6(50),

    /**
     * The version introduced by the Java Platform, Standard Edition
     * 7.
     *
     * @see <a
     * href="https://docs.oracle.com/javase/specs/jvms/se7/html/index.html">
     * <cite>The Java Virtual Machine Specification, Java SE 7 Edition</cite></a>
     */
    RELEASE_7(51),

    /**
     * The version introduced by the Java Platform, Standard Edition
     * 8.
     *
     * @see <a
     * href="https://docs.oracle.com/javase/specs/jvms/se8/html/index.html">
     * <cite>The Java Virtual Machine Specification, Java SE 8 Edition</cite></a>
     * @see <a href="https://jcp.org/en/jsr/detail?id=335">
     * JSR 335: Lambda Expressions for the Java&trade; Programming Language</a>
     */
    RELEASE_8(52),

    /**
     * The version introduced by the Java Platform, Standard Edition
     * 9.
     *
     * @see <a
     * href="https://docs.oracle.com/javase/specs/jvms/se9/html/index.html">
     * <cite>The Java Virtual Machine Specification, Java SE 9 Edition</cite></a>
     * @see <a href="https://jcp.org/en/jsr/detail?id=376">
     * JSR 376: Java&trade; Platform Module System</a>
     */
     RELEASE_9(53),

    /**
     * The version introduced by the Java Platform, Standard Edition
     * 10.
     *
     * @see <a
     * href="https://docs.oracle.com/javase/specs/jvms/se10/html/index.html">
     * <cite>The Java Virtual Machine Specification, Java SE 10 Edition</cite></a>
     */
    RELEASE_10(54),

    /**
     * The version introduced by the Java Platform, Standard Edition
     * 11.
     *
     * @see <a
     * href="https://docs.oracle.com/javase/specs/jvms/se11/html/index.html">
     * <cite>The Java Virtual Machine Specification, Java SE 11 Edition</cite></a>
     * @see <a href="https://openjdk.org/jeps/181">
     * JEP 181: Nest-Based Access Control</a>
     */
    RELEASE_11(55),

    /**
     * The version introduced by the Java Platform, Standard Edition
     * 12.
     *
     * @see <a
     * href="https://docs.oracle.com/javase/specs/jvms/se12/html/index.html">
     * <cite>The Java Virtual Machine Specification, Java SE 12 Edition</cite></a>
     */
    RELEASE_12(56),

    /**
     * The version introduced by the Java Platform, Standard Edition
     * 13.
     *
     * @see <a
     * href="https://docs.oracle.com/javase/specs/jvms/se13/html/index.html">
     * <cite>The Java Virtual Machine Specification, Java SE 13 Edition</cite></a>
     */
    RELEASE_13(57),

    /**
     * The version introduced by the Java Platform, Standard Edition
     * 14.
     *
     * @see <a
     * href="https://docs.oracle.com/javase/specs/jvms/se14/html/index.html">
     * <cite>The Java Virtual Machine Specification, Java SE 14 Edition</cite></a>
     */
    RELEASE_14(58),

    /**
     * The version introduced by the Java Platform, Standard Edition
     * 15.
     *
     * @see <a
     * href="https://docs.oracle.com/javase/specs/jvms/se15/html/index.html">
     * <cite>The Java Virtual Machine Specification, Java SE 15 Edition</cite></a>
     * @see <a href="https://openjdk.org/jeps/371">
     * JEP 371: Hidden Classes</a>
     */
    RELEASE_15(59),

    /**
     * The version introduced by the Java Platform, Standard Edition
     * 16.
     *
     * @see <a
     * href="https://docs.oracle.com/javase/specs/jvms/se16/html/index.html">
     * <cite>The Java Virtual Machine Specification, Java SE 16 Edition</cite></a>
     */
    RELEASE_16(60),

    /**
     * The version introduced by the Java Platform, Standard Edition
     * 17.
     *
     * Additions in this release include sealed classes and
     * restoration of always-strict floating-point semantics.
     *
     * @see <a
     * href="https://docs.oracle.com/javase/specs/jvms/se17/html/index.html">
     * <cite>The Java Virtual Machine Specification, Java SE 17 Edition</cite></a>
     * @see <a href="https://openjdk.org/jeps/306">
     * JEP 306: Restore Always-Strict Floating-Point Semantics</a>
     * @see <a href="https://openjdk.org/jeps/409">
     * JEP 409: Sealed Classes</a>
     */
    RELEASE_17(61),

    /**
     * The version introduced by the Java Platform, Standard Edition
     * 18.
     *
     * @see <a
     * href="https://docs.oracle.com/javase/specs/jvms/se18/html/index.html">
     * <cite>The Java Virtual Machine Specification, Java SE 18 Edition</cite></a>
     */
    RELEASE_18(62),

    /**
     * The version introduced by the Java Platform, Standard Edition
     * 19.
     *
     * @see <a
     * href="https://docs.oracle.com/javase/specs/jvms/se19/html/index.html">
     * <cite>The Java Virtual Machine Specification, Java SE 19 Edition</cite></a>
     */
    RELEASE_19(63),

    /**
     * The version introduced by the Java Platform, Standard Edition
     * 20.
     *
     * @see <a
     * href="https://docs.oracle.com/javase/specs/jvms/se20/html/index.html">
     * <cite>The Java Virtual Machine Specification, Java SE 20 Edition</cite></a>
     */
    RELEASE_20(64),

    /**
     * The version introduced by the Java Platform, Standard Edition
     * 21.
     *
     * @since 21
     *
     * @see <a
     * href="https://docs.oracle.com/javase/specs/jvms/se21/html/index.html">
     * <cite>The Java Virtual Machine Specification, Java SE 21 Edition</cite></a>
     */
    RELEASE_21(65),

    /**
     * The version introduced by the Java Platform, Standard Edition
     * 22.
     *
     * @since 22
     *
     * @see <a
     * href="https://docs.oracle.com/javase/specs/jvms/se22/html/index.html">
     * <cite>The Java Virtual Machine Specification, Java SE 22 Edition</cite></a>
     */
    RELEASE_22(66),

    /**
     * The version introduced by the Java Platform, Standard Edition
     * 23.
     *
     * @since 23
     *
     * @see <a
     * href="https://docs.oracle.com/javase/specs/jvms/se23/html/index.html">
     * <cite>The Java Virtual Machine Specification, Java SE 23 Edition</cite></a>
     */
    RELEASE_23(67),

    /**
     * The version introduced by the Java Platform, Standard Edition
     * 24.
     *
     * @since 24
     *
     * @see <a
     * href="https://docs.oracle.com/javase/specs/jvms/se24/html/index.html">
     * <cite>The Java Virtual Machine Specification, Java SE 24 Edition</cite></a>
     */
    RELEASE_24(68),

    /**
     * The version introduced by the Java Platform, Standard Edition
     * 25.
     *
     * @since 25
     *
     * @see <a
     * href="https://docs.oracle.com/javase/specs/jvms/se25/html/index.html">
     * <cite>The Java Virtual Machine Specification, Java SE 25 Edition</cite></a>
     */
    RELEASE_25(69),

    /**
     * The version introduced by the Java Platform, Standard Edition
     * 26.
     *
     * @since 26
     *
     * @see <a
     * href="https://docs.oracle.com/javase/specs/jvms/se26/html/index.html">
     * <cite>The Java Virtual Machine Specification, Java SE 26 Edition</cite></a>
     */
    RELEASE_26(70),
    ; // Reduce code churn when appending new constants

    // Note to maintainers: when adding constants for newer releases,
    // the implementation of latest() must be updated too.

    private final int major;

    private ClassFileFormatVersion(int major) {
        this.major = major;
    }

    /**
     * {@return the latest class file format version}
     */
    public static ClassFileFormatVersion latest() {
        return RELEASE_26;
    }

    /**
     * {@return the major class file version as an integer}
     * @jvms 4.1 The {@code ClassFile} Structure
     */
    public int major() {
        return major;
    }

    /**
     * {@return the latest class file format version that is usable
     * under the runtime version argument} If the runtime version's
     * {@linkplain Runtime.Version#feature() feature} is greater than
     * the feature of the {@linkplain #runtimeVersion() runtime
     * version} of the {@linkplain #latest() latest class file format
     * version}, an {@code IllegalArgumentException} is thrown.
     *
     * <p>Because the class file format versions of the Java platform
     * have so far followed a linear progression, only the feature
     * component of a runtime version is queried to determine the
     * mapping to a class file format version. If that linearity
     * changes in the future, other components of the runtime version
     * may influence the result.
     *
     * @apiNote
     * An expression to convert from a string value, for example
     * {@code "17"}, to the corresponding class file format version,
     * {@code RELEASE_17}, is:
     *
     * {@snippet lang="java" :
     * ClassFileFormatVersion.valueOf(Runtime.Version.parse("17"))}
     *
     * @param rv runtime version to map to a class file format version
     * @throws IllegalArgumentException if the feature of version
     * argument is greater than the feature of the platform version.
     */
    public static ClassFileFormatVersion valueOf(Runtime.Version rv) {
        // Could also implement this as a switch where a case was
        // added with each new release.
        return valueOf("RELEASE_" + rv.feature());
    }

    /**
     * {@return the least runtime version that supports this class
     * file format version; otherwise {@code null}} The returned
     * runtime version has a {@linkplain Runtime.Version#feature()
     * feature} large enough to support this class file format version
     * and has no other elements set.
     *
     * Class file format versions greater than or equal to {@link
     * RELEASE_6} have non-{@code null} results.
     */
    public Runtime.Version runtimeVersion() {
        // Starting with Java SE 6, the leading digit was the primary
        // way of identifying the platform version.
        if (this.compareTo(RELEASE_6) >= 0) {
            return Runtime.Version.parse(Integer.toString(ordinal()));
        } else {
            return null;
        }
    }

    /**
     * {@return the latest class file format version whose major class
     * file version matches the argument}
     * @param major the major class file version as an integer
     * @throws IllegalArgumentException if the argument is outside of
     * the range of major class file versions
     */
    public static ClassFileFormatVersion fromMajor(int major) {
        if (major < 45  // RELEASE_0.major()
            || major > latest().major()) {
            throw new IllegalArgumentException("Out of range major class file version "
                                               + major);
        }
        // RELEASE_0 and RELEASE_1 both have a major version of 45;
        // return RELEASE_1 for an argument of 45.
        return values()[major-44];
    }
}
