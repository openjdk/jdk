/*
 * Copyright (c) 2014, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * @implNote This class needs to maintain JDK 8 source compatibility.
 *
 * It is used internally in the JDK to implement jimage/jrtfs access,
 * but also compiled and delivered as part of the jrtfs.jar to support access
 * to the jimage file provided by the shipped JDK by tools running on JDK 8.
 */
public class ImageLocation {
    // Also defined in src/java.base/share/native/libjimage/imageFile.hpp

    /** End of attribute stream marker. */
    public static final int ATTRIBUTE_END = 0;
    /** String table offset of module name. */
    public static final int ATTRIBUTE_MODULE = 1;
    /** String table offset of resource path parent. */
    public static final int ATTRIBUTE_PARENT = 2;
    /** String table offset of resource path base. */
    public static final int ATTRIBUTE_BASE = 3;
    /** String table offset of resource path extension. */
    public static final int ATTRIBUTE_EXTENSION = 4;
    /** Container byte offset of resource. */
    public static final int ATTRIBUTE_OFFSET = 5;
    /** In-image byte size of the compressed resource. */
    public static final int ATTRIBUTE_COMPRESSED = 6;
    /** In-memory byte size of the uncompressed resource. */
    public static final int ATTRIBUTE_UNCOMPRESSED = 7;
    /** Flags relating to preview mode resources. */
    public static final int ATTRIBUTE_PREVIEW_FLAGS = 8;
    /** Number of attribute kinds. */
    public static final int ATTRIBUTE_COUNT = 9;

    // Flag masks for the ATTRIBUTE_PREVIEW_FLAGS attribute. Defined so
    // that zero is the overwhelmingly common case for normal resources.

    /**
     * Indicates that a non-preview location is associated with preview
     * resources.
     *
     * <p>This can apply to both resources and directories in the
     * {@code /modules/xxx/...} namespace, as well as {@code /packages/xxx}
     * directories.
     *
     * <p>For {@code /packages/xxx} directories, it indicates that the package
     * has preview resources in one of the modules in which it exists.
     */
    private static final int FLAGS_HAS_PREVIEW_VERSION = 0x1;
    /**
     * Set on all locations in the {@code /modules/xxx/META-INF/preview/...}
     * namespace.
     *
     * <p>This flag is mutually exclusive with {@link #FLAGS_HAS_PREVIEW_VERSION}.
     */
    private static final int FLAGS_IS_PREVIEW_VERSION = 0x2;
    /**
     * Indicates that a location only exists due to preview resources.
     *
     * <p>This can apply to both resources and directories in the
     * {@code /modules/xxx/...} namespace, as well as {@code /packages/xxx}
     * directories.
     *
     * <p>For {@code /packages/xxx} directories it indicates that, for every
     * module in which the package exists, it is preview only.
     *
     * <p>This flag is mutually exclusive with {@link #FLAGS_HAS_PREVIEW_VERSION}
     * and need not imply that {@link #FLAGS_IS_PREVIEW_VERSION} is set (i.e.
     * for {@code /packages/xxx} directories).
     */
    private static final int FLAGS_IS_PREVIEW_ONLY = 0x4;

    // Also used in ImageReader.
    static final String MODULES_PREFIX = "/modules";
    static final String PACKAGES_PREFIX = "/packages";
    static final String PREVIEW_INFIX = "/META-INF/preview";

    /**
     * Helper function to calculate preview flags (ATTRIBUTE_PREVIEW_FLAGS).
     *
     * <p>Since preview flags are calculated separately for resource nodes and
     * directory nodes (in two quite different places) it's useful to have a
     * common helper.
     *
     * <p>Based on the entry name, the flags are:
     * <ul>
     *     <li>{@code "[/modules]/<module>/<path>"} normal resource or directory:<br>
     *     Zero, or {@code FLAGS_HAS_PREVIEW_VERSION} if a preview entry exists.
     *     <li>{@code "[/modules]/<module>/META-INF/preview/<path>"} preview
     *     resource or directory:<br>
     *     {@code FLAGS_IS_PREVIEW_VERSION}, and additionally {@code
     *     FLAGS_IS_PREVIEW_ONLY} if no normal version of the resource exists.
     *     <li>In all other cases, returned flags are zero (note that {@code
     *     "/packages/xxx"} entries may have flags, but these are calculated
     *     elsewhere).
     * </ul>
     *
     * @param name the jimage name of the resource or directory.
     * @param hasEntry a predicate for jimage names returning whether an entry
     *     is present.
     * @return flags for the ATTRIBUTE_PREVIEW_FLAGS attribute.
     */
    public static int getFlags(String name, Predicate<String> hasEntry) {
        if (name.startsWith(PACKAGES_PREFIX + "/")) {
            throw new IllegalArgumentException(
                    "Package sub-directory flags handled separately: " + name);
        }
        // Find suffix for either '/modules/xxx/suffix' or '/xxx/suffix' paths.
        int idx = name.startsWith(MODULES_PREFIX + "/") ? MODULES_PREFIX.length() + 1 : 1;
        int suffixStart = name.indexOf('/', idx);
        if (suffixStart == -1) {
            // No flags for '[/modules]/xxx' paths (esp. '/modules', '/packages').
            // '/packages/xxx' entries have flags, but not calculated here.
            return 0;
        }
        // Prefix is either '/modules/xxx' or '/xxx', and suffix starts with '/'.
        String prefix = name.substring(0, suffixStart);
        String suffix = name.substring(suffixStart);
        if (suffix.startsWith(PREVIEW_INFIX + "/")) {
            // Preview resources/directories.
            String nonPreviewName = prefix + suffix.substring(PREVIEW_INFIX.length());
            return FLAGS_IS_PREVIEW_VERSION
                    | (hasEntry.test(nonPreviewName) ? 0 : FLAGS_IS_PREVIEW_ONLY);
        } else if (!suffix.startsWith("/META-INF/")) {
            // Non-preview resources/directories.
            String previewName = prefix + PREVIEW_INFIX + suffix;
            return hasEntry.test(previewName) ? FLAGS_HAS_PREVIEW_VERSION : 0;
        } else {
            // Suffix is '/META-INF/xxx' and no preview version is even possible.
            return 0;
        }
    }

    /**
     * Helper function to calculate package flags for {@code "/packages/xxx"}
     * directory entries.
     *
     * <p>Based on the module references, the flags are:
     * <ul>
     *     <li>{@code FLAGS_HAS_PREVIEW_VERSION} if <em>any</em> referenced
     *     package has a preview version.
     *     <li>{@code FLAGS_IS_PREVIEW_ONLY} if <em>all</em> referenced packages
     *     are preview only.
     * </ul>
     *
     * @return package flags for {@code "/packages/xxx"} directory entries.
     */
    public static int getPackageFlags(List<ModuleReference> moduleReferences) {
        boolean hasPreviewVersion =
                moduleReferences.stream().anyMatch(ModuleReference::hasPreviewVersion);
        boolean isPreviewOnly =
                moduleReferences.stream().allMatch(ModuleReference::isPreviewOnly);
        return (hasPreviewVersion ? ImageLocation.FLAGS_HAS_PREVIEW_VERSION : 0)
                | (isPreviewOnly ? ImageLocation.FLAGS_IS_PREVIEW_ONLY : 0);
    }

    /**
     * Tests a non-preview image location's flags to see if it has preview
     * content associated with it.
     */
    public static boolean hasPreviewVersion(int flags) {
        return (flags & FLAGS_HAS_PREVIEW_VERSION) != 0;
    }

    /**
     * Tests an image location's flags to see if it only exists in preview mode.
     */
    public static boolean isPreviewOnly(int flags) {
        return (flags & FLAGS_IS_PREVIEW_ONLY) != 0;
    }

    public enum LocationType {
        RESOURCE, MODULES_ROOT, MODULES_DIR, PACKAGES_ROOT, PACKAGES_DIR;
    }

    protected final long[] attributes;

    protected final ImageStrings strings;

    public ImageLocation(long[] attributes, ImageStrings strings) {
        this.attributes = Objects.requireNonNull(attributes);
        this.strings = Objects.requireNonNull(strings);
    }

    ImageStrings getStrings() {
        return strings;
    }

    static long[] decompress(ByteBuffer bytes, int offset) {
        Objects.requireNonNull(bytes);
        long[] attributes = new long[ATTRIBUTE_COUNT];

        int limit = bytes.limit();
        while (offset < limit) {
            int data = bytes.get(offset++) & 0xFF;
            if (data <= 0x7) { // ATTRIBUTE_END
                break;
            }
            int kind = data >>> 3;
            if (ATTRIBUTE_COUNT <= kind) {
                throw new InternalError(
                    "Invalid jimage attribute kind: " + kind);
            }

            int length = (data & 0x7) + 1;
            attributes[kind] = readValue(length, bytes, offset, limit);
            offset += length;
        }
        return attributes;
    }

    public static byte[] compress(long[] attributes) {
        Objects.requireNonNull(attributes);
        ImageStream stream = new ImageStream(16);

        for (int kind = ATTRIBUTE_END + 1; kind < ATTRIBUTE_COUNT; kind++) {
            long value = attributes[kind];

            if (value != 0) {
                int n = (63 - Long.numberOfLeadingZeros(value)) >> 3;
                stream.put((kind << 3) | n);

                for (int i = n; i >= 0; i--) {
                    stream.put((int)(value >> (i << 3)));
                }
            }
        }

        stream.put(ATTRIBUTE_END << 3);

        return stream.toArray();
     }

    public boolean verify(String name) {
        return verify(name, attributes, strings);
    }

    /**
     * A simpler verification would be {@code name.equals(getFullName())}, but
     * by not creating the full name and enabling early returns we allocate
     * fewer objects.
     */
    static boolean verify(String name, long[] attributes, ImageStrings strings) {
        Objects.requireNonNull(name);
        final int length = name.length();
        int index = 0;
        int moduleOffset = (int)attributes[ATTRIBUTE_MODULE];
        if (moduleOffset != 0 && length >= 1) {
            int moduleLen = strings.match(moduleOffset, name, 1);
            index = moduleLen + 1;
            if (moduleLen < 0
                    || length <= index
                    || name.charAt(0) != '/'
                    || name.charAt(index++) != '/') {
                return false;
            }
        }
        return verifyName(null, name, index, length, 0,
                (int) attributes[ATTRIBUTE_PARENT],
                (int) attributes[ATTRIBUTE_BASE],
                (int) attributes[ATTRIBUTE_EXTENSION],
                strings);
    }

    static boolean verify(String module, String name, ByteBuffer locations,
                          int locationOffset, ImageStrings strings) {
        int moduleOffset = 0;
        int parentOffset = 0;
        int baseOffset = 0;
        int extOffset = 0;

        int limit = locations.limit();
        while (locationOffset < limit) {
            int data = locations.get(locationOffset++) & 0xFF;
            if (data <= 0x7) { // ATTRIBUTE_END
                break;
            }
            int kind = data >>> 3;
            if (ATTRIBUTE_COUNT <= kind) {
                throw new InternalError(
                        "Invalid jimage attribute kind: " + kind);
            }

            int length = (data & 0x7) + 1;
            switch (kind) {
                case ATTRIBUTE_MODULE:
                    moduleOffset = (int) readValue(length, locations, locationOffset, limit);
                    break;
                case ATTRIBUTE_BASE:
                    baseOffset = (int) readValue(length, locations, locationOffset, limit);
                    break;
                case ATTRIBUTE_PARENT:
                    parentOffset = (int) readValue(length, locations, locationOffset, limit);
                    break;
                case ATTRIBUTE_EXTENSION:
                    extOffset = (int) readValue(length, locations, locationOffset, limit);
                    break;
            }
            locationOffset += length;
        }
        return verifyName(module, name, 0, name.length(),
                moduleOffset, parentOffset, baseOffset, extOffset, strings);
    }

    private static long readValue(int length, ByteBuffer buffer, int offset, int limit) {
        long value = 0;
        for (int j = 0; j < length; j++) {
            value <<= 8;
            if (offset >= limit) {
                throw new InternalError("Missing jimage attribute data");
            }
            value |= buffer.get(offset++) & 0xFF;
        }
        return value;
    }

    static boolean verify(String module, String name, long[] attributes,
            ImageStrings strings) {
        Objects.requireNonNull(module);
        Objects.requireNonNull(name);
        return verifyName(module, name, 0, name.length(),
                (int) attributes[ATTRIBUTE_MODULE],
                (int) attributes[ATTRIBUTE_PARENT],
                (int) attributes[ATTRIBUTE_BASE],
                (int) attributes[ATTRIBUTE_EXTENSION],
                strings);
    }

    private static boolean verifyName(String module, String name, int index, int length,
            int moduleOffset, int parentOffset, int baseOffset, int extOffset, ImageStrings strings) {

        if (moduleOffset != 0) {
            if (strings.match(moduleOffset, module, 0) != module.length()) {
                return false;
            }
        }
        if (parentOffset != 0) {
            int parentLen = strings.match(parentOffset, name, index);
            if (parentLen < 0) {
                return false;
            }
            index += parentLen;
            if (length <= index || name.charAt(index++) != '/') {
                return false;
            }
        }
        int baseLen = strings.match(baseOffset, name, index);
        if (baseLen < 0) {
            return false;
        }
        index += baseLen;
        if (extOffset != 0) {
            if (length <= index
                    || name.charAt(index++) != '.') {
                return false;
            }

            int extLen = strings.match(extOffset, name, index);
            if (extLen < 0) {
                return false;
            }
            index += extLen;
        }
        return length == index;
    }

    long getAttribute(int kind) {
        if (kind < ATTRIBUTE_END || ATTRIBUTE_COUNT <= kind) {
            throw new InternalError(
                "Invalid jimage attribute kind: " + kind);
        }
        return attributes[kind];
    }

    String getAttributeString(int kind) {
        if (kind < ATTRIBUTE_END || ATTRIBUTE_COUNT <= kind) {
            throw new InternalError(
                "Invalid jimage attribute kind: " + kind);
        }
        return getStrings().get((int)attributes[kind]);
    }

    public String getModule() {
        return getAttributeString(ATTRIBUTE_MODULE);
    }

    public int getModuleOffset() {
        return (int)getAttribute(ATTRIBUTE_MODULE);
    }

    public String getBase() {
        return getAttributeString(ATTRIBUTE_BASE);
    }

    public int getBaseOffset() {
        return (int)getAttribute(ATTRIBUTE_BASE);
    }

    public String getParent() {
        return getAttributeString(ATTRIBUTE_PARENT);
    }

    public int getParentOffset() {
        return (int)getAttribute(ATTRIBUTE_PARENT);
    }

    public String getExtension() {
        return getAttributeString(ATTRIBUTE_EXTENSION);
    }

    public int getExtensionOffset() {
        return (int)getAttribute(ATTRIBUTE_EXTENSION);
    }

    public int getFlags() {
        return (int) getAttribute(ATTRIBUTE_PREVIEW_FLAGS);
    }

    public String getFullName() {
        return getFullName(false);
    }

    public String getFullName(boolean modulesPrefix) {
        StringBuilder builder = new StringBuilder();

        if (getModuleOffset() != 0) {
            if (modulesPrefix) {
                builder.append(MODULES_PREFIX);
            }

            builder.append('/');
            builder.append(getModule());
            builder.append('/');
        }

        if (getParentOffset() != 0) {
            builder.append(getParent());
            builder.append('/');
        }

        builder.append(getBase());

        if (getExtensionOffset() != 0) {
            builder.append('.');
            builder.append(getExtension());
        }

        return builder.toString();
    }

    public long getContentOffset() {
        return getAttribute(ATTRIBUTE_OFFSET);
    }

    public long getCompressedSize() {
        return getAttribute(ATTRIBUTE_COMPRESSED);
    }

    public long getUncompressedSize() {
        return getAttribute(ATTRIBUTE_UNCOMPRESSED);
    }

    // Fast (zero allocation) type determination for locations.
    public LocationType getType() {
        switch (getModuleOffset()) {
            case ImageStrings.MODULES_STRING_OFFSET:
                // Locations in /modules/... namespace are directory entries.
                return LocationType.MODULES_DIR;
            case ImageStrings.PACKAGES_STRING_OFFSET:
                // Locations in /packages/... namespace are always 2-level
                // "/packages/xxx" directories.
                return LocationType.PACKAGES_DIR;
            case ImageStrings.EMPTY_STRING_OFFSET:
                // Only 2 choices, either the "/modules" or "/packages" root.
                assert isRootDir() : "Invalid root directory: " + getFullName();
                return getBase().charAt(1) == 'p'
                        ? LocationType.PACKAGES_ROOT
                        : LocationType.MODULES_ROOT;
            default:
                // Anything else is /<module>/<path> and references a resource.
                return LocationType.RESOURCE;
        }
    }

    private boolean isRootDir() {
        if (getModuleOffset() == 0 && getParentOffset() == 0) {
            String name = getFullName();
            return name.equals(MODULES_PREFIX) || name.equals(PACKAGES_PREFIX);
        }
        return false;
    }

    @Override
    public String toString() {
        // Cannot use String.format() (too early in startup for locale code).
        return "ImageLocation[name='" + getFullName() + "', type=" + getType() + ", flags=" + getFlags() + "]";
    }

    static ImageLocation readFrom(BasicImageReader reader, int offset) {
        Objects.requireNonNull(reader);
        long[] attributes = reader.getAttributes(offset);
        ImageStringsReader strings = reader.getStrings();

        return new ImageLocation(attributes, strings);
    }
}
