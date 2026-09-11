/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * Represents links to modules stored in the buffer of {@code "/packages/xxx"}
 * image locations (package subdirectories).
 *
 * <p>Package subdirectories store their data differently to all other jimage
 * entries. Instead of storing a sequence of offsets to their child entries,
 * they store a flattened representation of the child's data in an interleaved
 * buffer. These entries also use flags which are similar to, but distinct from,
 * the {@link ImageLocation} flags.
 *
 * <p>This class encapsulates that complexity to help avoid confusion.
 *
 * @implNote This class needs to maintain JDK 8 source compatibility.
 *
 * It is used internally in the JDK to implement jimage/jrtfs access,
 * but also compiled and delivered as part of the jrtfs.jar to support access
 * to the jimage file provided by the shipped JDK by tools running on JDK 8.
 */
public final class ModuleLink implements Comparable<ModuleLink> {
    // These flags are additive (hence "has-content" rather than "is-empty").

    /** If set, this package exists in preview mode. */
    private static final int FLAGS_PKG_HAS_PREVIEW_VERSION = 0x1;
    /** If set, this package exists in non-preview mode. */
    private static final int FLAGS_PKG_HAS_NORMAL_VERSION = 0x2;
    /** If set, the associated module has resources (in normal or preview mode). */
    private static final int FLAGS_PKG_HAS_RESOURCES = 0x4;

    /**
     * Links are ordered with preview versions first, which permits early
     * exit when processing preview entries (it's reversed because the default
     * order for a boolean is {@code false < true}).
     */
    private static final Comparator<ModuleLink> PREVIEW_FIRST =
            Comparator.comparing(ModuleLink::hasPreviewVersion).reversed()
                    .thenComparing(ModuleLink::name);

    /**
     * Returns a link for non-empty packages (those with resources) in a
     * given module.
     *
     * <p>The same link can be used for multiple packages in the same module.
     *
     * @param moduleName the name of the module in which this package exits.
     * @param isPreview whether the associated package is defined for preview mode.
     */
    public static ModuleLink forPackage(String moduleName, boolean isPreview) {
        return new ModuleLink(moduleName, FLAGS_PKG_HAS_RESOURCES | previewFlag(isPreview));
    }

    /**
     * Returns a link for empty packages in a given module.
     *
     * <p>The same link can be used for multiple packages in the same module.
     *
     * @param moduleName the name of the module in which this package exits.
     * @param isPreview whether the associated package is defined for preview mode.
     */
    public static ModuleLink forEmptyPackage(String moduleName, boolean isPreview) {
        return new ModuleLink(moduleName, previewFlag(isPreview));
    }

    /**
     * Returns the appropriate FLAGS_PKG_HAS_XXX_VERSION constant according to
     * whether the associated package is defined for preview mode.
     */
    private static int previewFlag(boolean isPreview) {
        return isPreview ? FLAGS_PKG_HAS_PREVIEW_VERSION : FLAGS_PKG_HAS_NORMAL_VERSION;
    }

    /** Merges two links for the same module (combining their flags). */
    public ModuleLink merge(ModuleLink other) {
        if (!name.equals(other.name)) {
            throw new IllegalArgumentException("Cannot merge " + other + " with " + this);
        }
        // Because flags are additive, we can just OR them here.
        return new ModuleLink(name, flags | other.flags);
    }

    private final String name;
    private final int flags;

    private ModuleLink(String moduleName, int flags) {
        this.name = Objects.requireNonNull(moduleName);
        this.flags = flags;
    }

    /** Returns the module name of this link. */
    public String name() {
        return name;
    }

    /**
     * Returns whether the package associated with this link contains resources
     * in its module.
     *
     * <p>An invariant of the module system is that while a package may exist
     * under many modules, it only has resources in one.
     */
    public boolean hasResources() {
        return (flags & FLAGS_PKG_HAS_RESOURCES) != 0;
    }

    /**
     * Returns whether the package associated with this module link has a
     * preview version (empty or otherwise) in this link's module.
     */
    public boolean hasPreviewVersion() {
        return (flags & FLAGS_PKG_HAS_PREVIEW_VERSION) != 0;
    }

    /** Returns whether this module link exists only in preview mode. */
    public boolean isPreviewOnly() {
        return (flags & FLAGS_PKG_HAS_NORMAL_VERSION) == 0;
    }

    @Override
    public int compareTo(ModuleLink rhs) {
        return PREVIEW_FIRST.compare(this, rhs);
    }

    @Override
    public String toString() {
        return "ModuleLink{ module=" + name + ", flags=" + flags + " }";
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof ModuleLink)) {
            return false;
        }
        ModuleLink other = (ModuleLink) obj;
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
     * <p>Package subdirectories store their entries using pairs of integers in
     * an interleaved buffer:
     * <pre>
     *     ...
     *     [ entry-N flags ]
     *     [ entry-N name offset ]
     *     [ entry-(N+1) flags ]
     *     [ entry-(N+1) name offset ]
     *     ...
     * </pre>
     *
     * <p>Entry flags control whether an entry name should be included by the
     * returned iterator, depending on the given include-flags.
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
     * Writes a list of module links to a given buffer. The given entry list is
     * checked carefully to ensure the written buffer will be valid.
     *
     * <p>Entries are written in order, taking two integer slots per entry as
     * {@code [<flags>, <encoded-name>]}.
     *
     * @param links the module links to write, correctly ordered.
     * @param buffer destination buffer.
     * @param nameEncoder encoder for module names.
     * @throws IllegalArgumentException in the link entries are invalid in any way.
     */
    public static void write(
            List<ModuleLink> links, IntBuffer buffer, Function<String, Integer> nameEncoder) {
        if (links.isEmpty()) {
            throw new IllegalArgumentException("References list must be non-empty");
        }
        int expectedCapacity = 2 * links.size();
        if (buffer.capacity() != expectedCapacity) {
            throw new IllegalArgumentException(
                    "Invalid buffer capacity: expected " + expectedCapacity + ", got " + buffer.capacity());
        }
        // This catches exact duplicates in the list.
        links.stream().reduce((lhs, rhs) -> {
            if (lhs.compareTo(rhs) >= 0) {
                throw new IllegalArgumentException("References must be strictly ordered: " + links);
            }
            return rhs;
        });
        // Distinct references can have the same name (but we don't allow this).
        if (links.stream().map(ModuleLink::name).distinct().count() != links.size()) {
            throw new IllegalArgumentException("Module links names must be unique: " + links);
        }
        if (links.stream().filter(ModuleLink::hasResources).count() > 1) {
            throw new IllegalArgumentException("At most one module link can have resources: " + links);
        }
        for (ModuleLink link : links) {
            buffer.put(link.flags);
            buffer.put(nameEncoder.apply(link.name));
        }
    }
}
