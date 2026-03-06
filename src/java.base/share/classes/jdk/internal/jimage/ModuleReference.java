/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.jimage;

import java.nio.IntBuffer;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Function;

/**
 * Represents the module entries stored in the buffer of {@code "/packages/xxx"}
 * image locations (package subdirectories). These entries use flags which are
 * similar to, but distinct from, the {@link ImageLocation} flags, so
 * encapsulating them here helps avoid confusion.
 *
 * @implNote This class needs to maintain JDK 8 source compatibility.
 *
 * It is used internally in the JDK to implement jimage/jrtfs access,
 * but also compiled and delivered as part of the jrtfs.jar to support access
 * to the jimage file provided by the shipped JDK by tools running on JDK 8.
 */
public final class ModuleReference implements Comparable<ModuleReference> {
    // These flags are additive (hence "has-content" rather than "is-empty").

    /** If set, this package exists in preview mode. */
    private static final int FLAGS_PKG_HAS_PREVIEW_VERSION = 0x1;
    /** If set, this package exists in non-preview mode. */
    private static final int FLAGS_PKG_HAS_NORMAL_VERSION = 0x2;
    /** If set, the associated module has resources (in normal or preview mode). */
    private static final int FLAGS_PKG_HAS_RESOURCES = 0x4;

    /**
     * References are ordered with preview versions first which permits early
     * exit when processing preview entries (it's reversed because the default
     * order for a boolean is {@code false < true}).
     */
    private static final Comparator<ModuleReference> PREVIEW_FIRST =
            Comparator.comparing(ModuleReference::hasPreviewVersion).reversed()
                    .thenComparing(ModuleReference::name);

    /**
     * Returns a reference for non-empty packages (those with resources) in a
     * given module.
     *
     * <p>The same reference can be used for multiple packages in the same module.
     */
    public static ModuleReference forPackage(String moduleName, boolean isPreview) {
        return new ModuleReference(moduleName, FLAGS_PKG_HAS_RESOURCES | previewFlag(isPreview));
    }

    /**
     * Returns a reference for empty packages in a given module.
     *
     * <p>The same reference can be used for multiple packages in the same module.
     */
    public static ModuleReference forEmptyPackage(String moduleName, boolean isPreview) {
        return new ModuleReference(moduleName, previewFlag(isPreview));
    }

    private static int previewFlag(boolean isPreview) {
        return isPreview ? FLAGS_PKG_HAS_PREVIEW_VERSION : FLAGS_PKG_HAS_NORMAL_VERSION;
    }

    /** Merges two references for the same module (combining their flags). */
    public ModuleReference merge(ModuleReference other) {
        if (!name.equals(other.name)) {
            throw new IllegalArgumentException("Cannot merge " + other + " with " + this);
        }
        // Because flags are additive, we can just OR them here.
        return new ModuleReference(name, flags | other.flags);
    }

    private final String name;
    private final int flags;

    private ModuleReference(String moduleName, int flags) {
        this.name = Objects.requireNonNull(moduleName);
        this.flags = flags;
    }

    /** Returns the module name of this reference. */
    public String name() {
        return name;
    }

    /**
     * Returns whether the package associated with this reference contains
     * resources in this reference's module.
     *
     * <p>An invariant of the module system is that while a package may exist
     * under many modules, it only has resources in one.
     */
    public boolean hasResources() {
        return (flags & FLAGS_PKG_HAS_RESOURCES) != 0;
    }

    /**
     * Returns whether the package associated with this reference has a preview
     * version (empty or otherwise) in this reference's module.
     */
    public boolean hasPreviewVersion() {
        return (flags & FLAGS_PKG_HAS_PREVIEW_VERSION) != 0;
    }

    /** Returns whether this reference exists only in preview mode. */
    public boolean isPreviewOnly() {
        return (flags & FLAGS_PKG_HAS_NORMAL_VERSION) == 0;
    }

    @Override
    public int compareTo(ModuleReference rhs) {
        return PREVIEW_FIRST.compare(this, rhs);
    }

    @Override
    public String toString() {
        return "ModuleReference{ module=" + name + ", flags=" + flags + " }";
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof ModuleReference)) {
            return false;
        }
        ModuleReference other = (ModuleReference) obj;
        return name.equals(other.name) && flags == other.flags;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, flags);
    }

    /**
     * Reads the content buffer of a package subdirectory to return a sequence
     * of module name offsets in the jimage.
     *
     * @param buffer the content buffer of an {@link ImageLocation} with type
     *     {@link ImageLocation.LocationType#PACKAGES_DIR PACKAGES_DIR}.
     * @param includeNormal whether to include name offsets for modules present
     *     in normal (non-preview) mode.
     * @param includePreview whether to include name offsets for modules present
     *     in preview mode.
     * @return an iterator of module name offsets.
     */
    public static Iterator<Integer> readNameOffsets(
            IntBuffer buffer, boolean includeNormal, boolean includePreview) {
        int bufferSize = buffer.capacity();
        if (bufferSize == 0 || (bufferSize & 0x1) != 0) {
            throw new IllegalArgumentException("Invalid buffer size");
        }
        int includeMask = (includeNormal ? FLAGS_PKG_HAS_NORMAL_VERSION : 0)
                + (includePreview ? FLAGS_PKG_HAS_PREVIEW_VERSION : 0);
        if (includeMask == 0) {
            throw new IllegalArgumentException("Invalid flags");
        }

        return new Iterator<Integer>() {
            private int idx = nextIdx(0);

            int nextIdx(int idx) {
                for (; idx < bufferSize; idx += 2) {
                    // If any of the test flags are set, include this entry.
                    if ((buffer.get(idx) & includeMask) != 0) {
                        return idx;
                    } else if (!includeNormal) {
                        // Preview entries are first in the offset buffer, so we
                        // can exit early (by returning the end index) if we are
                        // only iterating preview entries, and have run out.
                        break;
                    }
                }
                return bufferSize;
            }

            @Override
            public boolean hasNext() {
                return idx < bufferSize;
            }

            @Override
            public Integer next() {
                if (idx < bufferSize) {
                    int nameOffset = buffer.get(idx + 1);
                    idx = nextIdx(idx + 2);
                    return nameOffset;
                }
                throw new NoSuchElementException();
            }
        };
    }

    /**
     * Writes a list of module references to a given buffer. The given references
     * list is checked carefully to ensure the written buffer will be valid.
     *
     * <p>Entries are written in order, taking two integer slots per entry as
     * {@code [<flags>, <encoded-name>]}.
     *
     * @param refs the references to write, correctly ordered.
     * @param buffer destination buffer.
     * @param nameEncoder encoder for module names.
     * @throws IllegalArgumentException in the references are invalid in any way.
     */
    public static void write(
            List<ModuleReference> refs, IntBuffer buffer, Function<String, Integer> nameEncoder) {
        if (refs.isEmpty()) {
            throw new IllegalArgumentException("References list must be non-empty");
        }
        int expectedCapacity = 2 * refs.size();
        if (buffer.capacity() != expectedCapacity) {
            throw new IllegalArgumentException(
                    "Invalid buffer capacity: expected " + expectedCapacity + ", got " + buffer.capacity());
        }
        // This catches exact duplicates in the list.
        refs.stream().reduce((lhs, rhs) -> {
            if (lhs.compareTo(rhs) >= 0) {
                throw new IllegalArgumentException("References must be strictly ordered: " + refs);
            }
            return rhs;
        });
        // Distinct references can have the same name (but we don't allow this).
        if (refs.stream().map(ModuleReference::name).distinct().count() != refs.size()) {
            throw new IllegalArgumentException("Reference names must be unique: " + refs);
        }
        if (refs.stream().filter(ModuleReference::hasResources).count() > 1) {
            throw new IllegalArgumentException("At most one reference can have resources: " + refs);
        }
        for (ModuleReference modRef : refs) {
            buffer.put(modRef.flags);
            buffer.put(nameEncoder.apply(modRef.name));
        }
    }
}
