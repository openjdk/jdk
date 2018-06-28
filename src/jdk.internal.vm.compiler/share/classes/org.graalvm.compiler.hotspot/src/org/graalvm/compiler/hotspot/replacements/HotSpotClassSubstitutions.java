/*
 * Copyright (c) 2012, 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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


package org.graalvm.compiler.hotspot.replacements;

import static org.graalvm.compiler.hotspot.GraalHotSpotVMConfig.INJECTED_VMCONFIG;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.ARRAY_KLASS_COMPONENT_MIRROR;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.KLASS_ACCESS_FLAGS_LOCATION;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.KLASS_MODIFIER_FLAGS_LOCATION;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.KLASS_SUPER_KLASS_LOCATION;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.arrayKlassComponentMirrorOffset;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.klassAccessFlagsOffset;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.klassIsArray;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.klassModifierFlagsOffset;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.klassSuperKlassOffset;

import java.lang.reflect.Modifier;

import org.graalvm.compiler.api.replacements.ClassSubstitution;
import org.graalvm.compiler.api.replacements.MethodSubstitution;
import org.graalvm.compiler.hotspot.word.KlassPointer;
import org.graalvm.compiler.nodes.PiNode;
import org.graalvm.compiler.nodes.SnippetAnchorNode;

// JaCoCo Exclude

/**
 * Substitutions for {@link java.lang.Class} methods.
 */
@ClassSubstitution(Class.class)
public class HotSpotClassSubstitutions {

    @MethodSubstitution(isStatic = false)
    public static int getModifiers(final Class<?> thisObj) {
        KlassPointer klass = ClassGetHubNode.readClass(thisObj);
        if (klass.isNull()) {
            // Class for primitive type
            return Modifier.ABSTRACT | Modifier.FINAL | Modifier.PUBLIC;
        } else {
            return klass.readInt(klassModifierFlagsOffset(INJECTED_VMCONFIG), KLASS_MODIFIER_FLAGS_LOCATION);
        }
    }

    @MethodSubstitution(isStatic = false)
    public static boolean isInterface(final Class<?> thisObj) {
        KlassPointer klass = ClassGetHubNode.readClass(thisObj);
        if (klass.isNull()) {
            // Class for primitive type
            return false;
        } else {
            int accessFlags = klass.readInt(klassAccessFlagsOffset(INJECTED_VMCONFIG), KLASS_ACCESS_FLAGS_LOCATION);
            return (accessFlags & Modifier.INTERFACE) != 0;
        }
    }

    @MethodSubstitution(isStatic = false)
    public static boolean isArray(final Class<?> thisObj) {
        KlassPointer klass = ClassGetHubNode.readClass(thisObj);
        if (klass.isNull()) {
            // Class for primitive type
            return false;
        } else {
            KlassPointer klassNonNull = ClassGetHubNode.piCastNonNull(klass, SnippetAnchorNode.anchor());
            return klassIsArray(klassNonNull);
        }
    }

    @MethodSubstitution(isStatic = false)
    public static boolean isPrimitive(final Class<?> thisObj) {
        KlassPointer klass = ClassGetHubNode.readClass(thisObj);
        return klass.isNull();
    }

    @MethodSubstitution(isStatic = false)
    public static Class<?> getSuperclass(final Class<?> thisObj) {
        KlassPointer klass = ClassGetHubNode.readClass(thisObj);
        if (!klass.isNull()) {
            KlassPointer klassNonNull = ClassGetHubNode.piCastNonNull(klass, SnippetAnchorNode.anchor());
            int accessFlags = klassNonNull.readInt(klassAccessFlagsOffset(INJECTED_VMCONFIG), KLASS_ACCESS_FLAGS_LOCATION);
            if ((accessFlags & Modifier.INTERFACE) == 0) {
                if (klassIsArray(klassNonNull)) {
                    return Object.class;
                } else {
                    KlassPointer superKlass = klassNonNull.readKlassPointer(klassSuperKlassOffset(INJECTED_VMCONFIG), KLASS_SUPER_KLASS_LOCATION);
                    if (superKlass.isNull()) {
                        return null;
                    } else {
                        KlassPointer superKlassNonNull = ClassGetHubNode.piCastNonNull(superKlass, SnippetAnchorNode.anchor());
                        return HubGetClassNode.readClass(superKlassNonNull);
                    }
                }
            }
        } else {
            // Class for primitive type
        }
        return null;
    }

    @MethodSubstitution(isStatic = false)
    public static Class<?> getComponentType(final Class<?> thisObj) {
        KlassPointer klass = ClassGetHubNode.readClass(thisObj);
        if (!klass.isNull()) {
            KlassPointer klassNonNull = ClassGetHubNode.piCastNonNull(klass, SnippetAnchorNode.anchor());
            if (klassIsArray(klassNonNull)) {
                return PiNode.asNonNullClass(klassNonNull.readObject(arrayKlassComponentMirrorOffset(INJECTED_VMCONFIG), ARRAY_KLASS_COMPONENT_MIRROR));
            }
        } else {
            // Class for primitive type
        }
        return null;
    }
}
