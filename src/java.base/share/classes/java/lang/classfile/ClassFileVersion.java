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
package java.lang.classfile;

import java.lang.reflect.ClassFileFormatVersion;

import jdk.internal.classfile.impl.ClassFileVersionImpl;

/**
 * Models the minor and major version numbers of a {@code class} file (JVMS
 * {@jvms 4.1}).  The {@code class} file version appears exactly once in each
 * class, and is set to an unspecified default value if not explicitly provided.
 * <p>
 * The major versions of {@code class} file format begins at {@value
 * ClassFile#JAVA_1_VERSION} for Java Platform version 1.0.2, and is continuous
 * up to {@link ClassFile#latestMajorVersion()}.  In general, each major version
 * defines a new supported {@code class} file format, modeled by {@link
 * ClassFileFormatVersion}, and supports all previous formats.
 * <p>
 * For major versions up to {@value ClassFile#JAVA_11_VERSION} for Java SE
 * Platform 11, the minor version of any value is supported.  For major versions
 * {@value ClassFile#JAVA_12_VERSION} for Java SE Platform version 12 and above,
 * the minor version must be {@code 0} or {@value ClassFile#PREVIEW_MINOR_VERSION}.
 * The minor version {@code 0} is always supported, and represents the format
 * modeled by {@link ClassFileFormatVersion}.  The minor version {@code 65535}
 * indicates the {@code class} file uses preview features of the Java SE
 * Platform release represented by the major version.  A Java Virtual Machine
 * can only load such a {@code class} file if it has the same Java SE Platform
 * version and the JVM has preview features enabled.
 *
 * @see ClassModel#majorVersion()
 * @see ClassModel#minorVersion()
 * @see ClassFileFormatVersion
 * @jvms 4.1 The {@code ClassFile} Structure
 * @since 24
 */
public sealed interface ClassFileVersion
        extends ClassElement
        permits ClassFileVersionImpl {
    /**
     * {@return the major version}  It is in the range of unsigned short, {@code
     * [0, 65535]}.
     *
     * @apiNote
     * Constants in {@link ClassFile} named {@code Java_#_VERSION}, where # is
     * a release number, such as {@link ClassFile#JAVA_21_VERSION}, describe the
     * class major versions of the Java Platform SE.
     */
    int majorVersion();

    /**
     * {@return the minor version}  It is in the range of unsigned short, {@code
     * [0, 65535]}.
     */
    int minorVersion();

    /**
     * {@return a {@link ClassFileVersion} element}
     * @param majorVersion the major version
     * @param minorVersion the minor version
     */
    static ClassFileVersion of(int majorVersion, int minorVersion) {
        return new ClassFileVersionImpl(majorVersion, minorVersion);
    }
}
