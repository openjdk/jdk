/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.reflect;

import java.lang.reflect.AccessFlag;
import java.lang.reflect.ClassFileFormatVersion;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

import jdk.internal.vm.annotation.Stable;

import static java.lang.classfile.ClassFile.*;
import static java.lang.reflect.AccessFlag.*;

/// Support for [AccessFlag] reflection for the preview VM features supported by
/// the current Java SE release.
///
/// These preview features appear when VM is running with --enable-preview, or
/// in x.65535 class files.  Tools must handle x.65535 class files when their
/// own VM is not running in preview, so this class may be used by tools when
///  preview features are not enabled,
public final class PreviewAccessFlags {

    public static final @Stable AccessFlag[]
            CLASS_PREVIEW_FLAGS = AccessFlagSet.createDefinition(PUBLIC, FINAL, IDENTITY, INTERFACE, ABSTRACT, SYNTHETIC, ANNOTATION, ENUM, MODULE),
            FIELD_PREVIEW_FLAGS = AccessFlagSet.createDefinition(PUBLIC, PRIVATE, PROTECTED, STATIC, FINAL, VOLATILE, TRANSIENT, SYNTHETIC, ENUM, STRICT_INIT),
            INNER_CLASS_PREVIEW_FLAGS = AccessFlagSet.createDefinition(PUBLIC, PRIVATE, PROTECTED, IDENTITY, STATIC, FINAL, INTERFACE, ABSTRACT, SYNTHETIC, ANNOTATION, ENUM);

    /// Preview variant of [Location#flagsMask()].
    public static int flagsMask(Location location) {
        return switch (location) {
            case FIELD -> ACC_PUBLIC | ACC_PRIVATE | ACC_PROTECTED |
                    ACC_STATIC | ACC_FINAL | ACC_VOLATILE |
                    ACC_TRANSIENT | ACC_SYNTHETIC | ACC_ENUM | ACC_STRICT_INIT; // strict init
            case INNER_CLASS -> ACC_PUBLIC | ACC_PRIVATE | ACC_PROTECTED | ACC_IDENTITY |
                    ACC_STATIC | ACC_FINAL | ACC_INTERFACE | ACC_ABSTRACT |
                    ACC_SYNTHETIC | ACC_ANNOTATION | ACC_ENUM; // identity
            default -> location.flagsMask();
        };
    }

    /// Preview variant of [AccessFlag#locations()].
    public static Set<Location> locations(AccessFlag flag) {
        return switch (flag) {
            case SUPER -> Set.of();
            case IDENTITY -> Set.of(Location.CLASS, Location.INNER_CLASS);
            case STRICT_INIT -> Set.of(Location.FIELD);
            default -> flag.locations();
        };
    }

    /// Preview variant of [AccessFlagSet#findDefinition].
    public static AccessFlag[] findDefinition(Location location) {
        return switch (location) {
            case CLASS -> CLASS_PREVIEW_FLAGS;
            case FIELD -> FIELD_PREVIEW_FLAGS;
            case INNER_CLASS -> INNER_CLASS_PREVIEW_FLAGS;
            default -> AccessFlagSet.findDefinition(location);
        };
    }

    /// Preview variant of [AccessFlag#maskToAccessFlags].
    /// The method body should be in parity.
    /// @throws IllegalArgumentException if there is unrecognized flag bit
    public static Set<AccessFlag> maskToAccessFlags(int mask, Location location) {
        var definition = findDefinition(location);  // null checks location
        int unmatchedMask = mask & (~flagsMask(location));
        if (unmatchedMask != 0) {
            throw new IllegalArgumentException("Unmatched bit position 0x" +
                    Integer.toHexString(unmatchedMask) +
                    " for location " + location +
                    " for preview class files");
        }
        return AccessFlagSet.ofValidated(definition, mask);
    }

    private PreviewAccessFlags() {
    }
}
