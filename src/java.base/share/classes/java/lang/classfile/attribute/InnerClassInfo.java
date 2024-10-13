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
package java.lang.classfile.attribute;

import java.lang.constant.ClassDesc;
import java.util.Optional;
import java.util.Set;

import java.lang.classfile.constantpool.ClassEntry;
import java.lang.classfile.constantpool.Utf8Entry;
import java.lang.reflect.AccessFlag;

import jdk.internal.classfile.impl.TemporaryConstantPool;
import jdk.internal.classfile.impl.UnboundAttribute;
import jdk.internal.classfile.impl.Util;
import jdk.internal.javac.PreviewFeature;

/**
 * Models a single inner class in the {@link InnerClassesAttribute}.
 *
 * @since 22
 */
@PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
public sealed interface InnerClassInfo
        permits UnboundAttribute.UnboundInnerClassInfo {

    /**
     * {@return the class described by this inner class description}
     */
    ClassEntry innerClass();

    /**
     * {@return the class or interface of which this class is a member, if it is a
     * member of a class or interface}
     */
    Optional<ClassEntry> outerClass();

    /**
     * {@return the simple name of this class, or empty if this class is anonymous}
     */
    Optional<Utf8Entry> innerName();

    /**
     * {@return a bit mask of flags denoting access permissions and properties
     * of the inner class}
     */
    int flagsMask();

    /**
     * {@return a set of flag enums denoting access permissions and properties
     * of the inner class}
     */
    default Set<AccessFlag> flags() {
        return AccessFlag.maskToAccessFlags(flagsMask(), AccessFlag.Location.INNER_CLASS);
    }

    /**
     * {@return whether a specific access flag is set}
     * @param flag the access flag
     */
    default boolean has(AccessFlag flag) {
        return Util.has(AccessFlag.Location.INNER_CLASS, flagsMask(), flag);
    }

    /**
     * {@return an inner class description}
     * @param innerClass the inner class being described
     * @param outerClass the class containing the inner class, if any
     * @param innerName the name of the inner class, if it is not anonymous
     * @param flags the inner class access flags
     */
    static InnerClassInfo of(ClassEntry innerClass, Optional<ClassEntry> outerClass,
                             Optional<Utf8Entry> innerName, int flags) {
        return new UnboundAttribute.UnboundInnerClassInfo(innerClass, outerClass, innerName, flags);
    }

    /**
     * {@return an inner class description}
     * @param innerClass the inner class being described
     * @param outerClass the class containing the inner class, if any
     * @param innerName the name of the inner class, if it is not anonymous
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
     * {@return an inner class description}
     * @param innerClass the inner class being described
     * @param outerClass the class containing the inner class, if any
     * @param innerName the name of the inner class, if it is not anonymous
     * @param flags the inner class access flags
     * @throws IllegalArgumentException if {@code innerClass} or {@code outerClass} represents a primitive type
     */
    static InnerClassInfo of(ClassDesc innerClass, Optional<ClassDesc> outerClass, Optional<String> innerName, AccessFlag... flags) {
        return of(innerClass, outerClass, innerName, Util.flagsToBits(AccessFlag.Location.INNER_CLASS, flags));
    }
}
