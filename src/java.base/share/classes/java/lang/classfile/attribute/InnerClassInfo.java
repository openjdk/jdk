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
package java.lang.classfile.attribute;

import java.lang.classfile.constantpool.ClassEntry;
import java.lang.classfile.constantpool.Utf8Entry;
import java.lang.constant.ClassDesc;
import java.lang.reflect.AccessFlag;
import java.util.Optional;
import java.util.Set;

import jdk.internal.classfile.impl.TemporaryConstantPool;
import jdk.internal.classfile.impl.UnboundAttribute;
import jdk.internal.classfile.impl.Util;

/**
 * Models a single entry in the {@link InnerClassesAttribute}.
 *
 * @see InnerClassesAttribute#classes()
 * @jvms 4.7.6 The {@code InnerClasses} Attribute
 * @since 24
 */
public sealed interface InnerClassInfo
        permits UnboundAttribute.UnboundInnerClassInfo {

    /**
     * {@return the nested class described by this entry}
     */
    ClassEntry innerClass();

    /**
     * {@return the class or interface of which this class is a member, if it is
     * a member of a class or interface}  This may be empty if this class is
     * local or anonymous.
     *
     * @see Class#getDeclaringClass()
     */
    Optional<ClassEntry> outerClass();

    /**
     * {@return the simple name of this class, or empty if this class is anonymous}
     *
     * @see Class#getSimpleName()
     */
    Optional<Utf8Entry> innerName();

    /**
     * {@return a bit mask of flags denoting access permissions and properties
     * of the inner class}
     *
     * @see Class#getModifiers()
     * @see AccessFlag.Location#INNER_CLASS
     */
    int flagsMask();

    /**
     * {@return a set of flag enums denoting access permissions and properties
     * of the nested class}
     *
     * @throws IllegalArgumentException if the flags mask has any undefined bit set
     * @see Class#accessFlags()
     * @see AccessFlag.Location#INNER_CLASS
     */
    default Set<AccessFlag> flags() {
        return AccessFlag.maskToAccessFlags(flagsMask(), AccessFlag.Location.INNER_CLASS);
    }

    /**
     * {@return whether a specific access flag is set}
     *
     * @param flag the access flag
     * @see AccessFlag.Location#INNER_CLASS
     */
    default boolean has(AccessFlag flag) {
        return Util.has(AccessFlag.Location.INNER_CLASS, flagsMask(), flag);
    }

    /**
     * {@return a nested class description}
     * @param innerClass the nested class being described
     * @param outerClass the class that has the nested class as a member, if it exists
     * @param innerName the simple name of the nested class, if it is not anonymous
     * @param flags the inner class access flags
     */
    static InnerClassInfo of(ClassEntry innerClass, Optional<ClassEntry> outerClass,
                             Optional<Utf8Entry> innerName, int flags) {
        return new UnboundAttribute.UnboundInnerClassInfo(innerClass, outerClass, innerName, flags);
    }

    /**
     * {@return a nested class description}
     * @param innerClass the nested class being described
     * @param outerClass the class that has the nested class as a member, if it exists
     * @param innerName the simple name of the nested class, if it is not anonymous
     * @param flags the inner class access flags
     * @throws IllegalArgumentException if {@code innerClass} or {@code outerClass} represents a primitive type
     */
    static InnerClassInfo of(ClassDesc innerClass, Optional<ClassDesc> outerClass, Optional<String> innerName, int flags) {
        return new UnboundAttribute.UnboundInnerClassInfo(TemporaryConstantPool.INSTANCE.classEntry(innerClass),
                                                          outerClass.map(TemporaryConstantPool.INSTANCE::classEntry),
                                                          innerName.map(TemporaryConstantPool.INSTANCE::utf8Entry),
                                                          flags);
    }

    /**
     * {@return a nested class description}
     * @param innerClass the nested class being described
     * @param outerClass the class that has the nested class as a member, if it exists
     * @param innerName the name of the nested class, if it is not anonymous
     * @param flags the inner class access flags
     * @throws IllegalArgumentException if {@code innerClass} or {@code outerClass}
     *         represents a primitive type, or if any flag cannot be applied to
     *         the {@link AccessFlag.Location#INNER_CLASS} location
     */
    static InnerClassInfo of(ClassDesc innerClass, Optional<ClassDesc> outerClass, Optional<String> innerName, AccessFlag... flags) {
        return of(innerClass, outerClass, innerName, Util.flagsToBits(AccessFlag.Location.INNER_CLASS, flags));
    }
}
