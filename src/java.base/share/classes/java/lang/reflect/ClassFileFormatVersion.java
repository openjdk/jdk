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
 * @since 20
 * @see java.compiler/javax.lang.model.SourceVersion
 */
@SuppressWarnings("doclint:reference") // cross-module links
public enum ClassFileFormatVersion {

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
     */
    RELEASE_1(46),

    /**
     * The version recognized by the Java 2 Platform, Standard Edition,
     * v 1.2.
     *
     * The format described in <cite>The Java Virtual Machine
     * Specification, Second Edition</cite>, which includes the {@link
     * AccessFlag#STRICT ACC_STRICT} access flag.
     */
    RELEASE_2(47),

    /**
     * The version recognized by the Java 2 Platform, Standard Edition,
     * v 1.3.
     */
    RELEASE_3(48),

    /**
     * The version recognized by the Java 2 Platform, Standard Edition,
     * v 1.4.
     */
    RELEASE_4(49),

    /**
     * The version recognized by the Java 2 Platform, Standard
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
    RELEASE_5(50),

    /**
     * The version recognized by the Java Platform, Standard Edition
     * 6.
     *
     * @see <a
     * href="https://jcp.org/aboutJava/communityprocess/maintenance/jsr924/index2.html">
     * <cite>The Java Virtual Machine Specification, Java SE, Second Edition updated for Java SE 6</cite></a>
     */
    RELEASE_6(51),

    /**
     * The version recognized by the Java Platform, Standard Edition
     * 7.
     *
     * @see <a
     * href="https://docs.oracle.com/javase/specs/jvms/se7/html/index.html">
     * <cite>The Java Virtual Machine Specification, Java SE 7 Edition</cite></a>
     */
    RELEASE_7(52),

    /**
     * The version recognized by the Java Platform, Standard Edition
     * 8.
     *
     * @see <a
     * href="https://docs.oracle.com/javase/specs/jvms/se8/html/index.html">
     * <cite>The Java Virtual Machine Specification, Java SE 8 Edition</cite></a>
     * @see <a href="https://jcp.org/en/jsr/detail?id=335">
     * JSR 335: Lambda Expressions for the Java&trade; Programming Language</a>
     */
    RELEASE_8(53),

    /**
     * The version recognized by the Java Platform, Standard Edition
     * 9.
     *
     * @see <a
     * href="https://docs.oracle.com/javase/specs/jvms/se9/html/index.html">
     * <cite>The Java Virtual Machine Specification, Java SE 9 Edition</cite></a>
     * @see <a href="https://jcp.org/en/jsr/detail?id=376">
     * JSR 376: Java&trade; Platform Module System</a>
     */
     RELEASE_9(54),

    /**
     * The version recognized by the Java Platform, Standard Edition
     * 10.
     *
     * @see <a
     * href="https://docs.oracle.com/javase/specs/jvms/se10/html/index.html">
     * <cite>The Java Virtual Machine Specification, Java SE 10 Edition</cite></a>
     */
    RELEASE_10(55),

    /**
     * The version recognized by the Java Platform, Standard Edition
     * 11.
     *
     * @see <a
     * href="https://docs.oracle.com/javase/specs/jvms/se11/html/index.html">
     * <cite>The Java Virtual Machine Specification, Java SE 11 Edition</cite></a>
     */
    RELEASE_11(56),

    /**
     * The version recognized by the Java Platform, Standard Edition
     * 12.
     *
     * @see <a
     * href="https://docs.oracle.com/javase/specs/jvms/se12/html/index.html">
     * <cite>The Java Virtual Machine Specification, Java SE 12 Edition</cite></a>
     */
    RELEASE_12(57),

    /**
     * The version recognized by the Java Platform, Standard Edition
     * 13.
     *
     * @see <a
     * href="https://docs.oracle.com/javase/specs/jvms/se13/html/index.html">
     * <cite>The Java Virtual Machine Specification, Java SE 13 Edition</cite></a>
     */
    RELEASE_13(58),

    /**
     * The version recognized by the Java Platform, Standard Edition
     * 14.
     *
     * @see <a
     * href="https://docs.oracle.com/javase/specs/jvms/se14/html/index.html">
     * <cite>The Java Virtual Machine Specification, Java SE 14 Edition</cite></a>
     */
    RELEASE_14(59),

    /**
     * The version recognized by the Java Platform, Standard Edition
     * 15.
     *
     * @see <a
     * href="https://docs.oracle.com/javase/specs/jvms/se15/html/index.html">
     * <cite>The Java Virtual Machine Specification, Java SE 15 Edition</cite></a>
     */
    RELEASE_15(60),

    /**
     * The version recognized by the Java Platform, Standard Edition
     * 16.
     *
     * @see <a
     * href="https://docs.oracle.com/javase/specs/jvms/se16/html/index.html">
     * <cite>The Java Virtual Machine Specification, Java SE 16 Edition</cite></a>
     */
    RELEASE_16(61),

    /**
     * The version recognized by the Java Platform, Standard Edition
     * 17.
     *
     * Additions in this release include sealed classes and
     * restoration of always-strict floating-point semantics.
     *
     * @see <a
     * href="https://docs.oracle.com/javase/specs/jvms/se17/html/index.html">
     * <cite>The Java Virtual Machine Specification, Java SE 17 Edition</cite></a>
     * @see <a href="https://openjdk.java.net/jeps/306">
     * JEP 306: Restore Always-Strict Floating-Point Semantics</a>
     * @see <a href="https://openjdk.java.net/jeps/409">
     * JEP 409: Sealed Classes</a>
     */
    RELEASE_17(62),

    /**
     * The version recognized by the Java Platform, Standard Edition
     * 18.
     *
     * @see <a
     * href="https://docs.oracle.com/javase/specs/jvms/se18/html/index.html">
     * <cite>The Java Virtual Machine Specification, Java SE 18 Edition</cite></a>
     */
    RELEASE_18(63),

    /**
     * The version recognized by the Java Platform, Standard Edition
     * 19.
     */
    RELEASE_19(64),

    /**
     * The version recognized by the Java Platform, Standard Edition
     * 20.
     */
    RELEASE_20(65);

    private int major;

    private ClassFileFormatVersion(int major) {
        this.major = major;
    }

    /**
     * {@return the latest class file format version}
     */
    public static ClassFileFormatVersion latest() {
        return RELEASE_20;
    }

    /**
     * {@return the major class file version integer}
     * @jvms 4.1 The {@code ClassFile} Structure
     */
    int major() {
        return major;
    }

    /**
     * {@return the latest class file format version that is usable
     * under the runtime version argument} If the runtime version's
     * {@linkplain Runtime.Version#feature() feature} is greater than
     * the feature of the {@linkplain #runtimeVersion() runtime
     * version} of the {@linkplain #latest() latest source version},
     * an {@code IllegalArgumentException} is thrown.
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
     * {@code "17"}, to the corresponding source version, {@code
     * RELEASE_17}, is:
     *
     * <pre>{@code SourceVersion.valueOf(Runtime.Version.parse("17"))}</pre>
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
     * feature} large enough to support this source version and has no
     * other elements set.
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

}
