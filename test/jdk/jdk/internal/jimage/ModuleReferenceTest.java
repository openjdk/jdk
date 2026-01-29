/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.jimage.ModuleReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;

import static jdk.internal.jimage.ModuleReference.forEmptyPackage;
import static jdk.internal.jimage.ModuleReference.forPackage;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/*
 * @test
 * @summary Tests for ModuleReference.
 * @modules java.base/jdk.internal.jimage
 * @run junit/othervm -esa ModuleReferenceTest
 */
public final class ModuleReferenceTest {
    // Copied (not referenced) for testing.
    private static final int FLAGS_HAS_PREVIEW_VERSION = 0x1;
    private static final int FLAGS_HAS_NORMAL_VERSION = 0x2;
    private static final int FLAGS_HAS_CONTENT = 0x4;

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    public void emptyRefs(boolean isPreview) {
        ModuleReference ref = forEmptyPackage("module", isPreview);

        assertEquals("module", ref.name());
        assertFalse(ref.hasResources());
        assertEquals(isPreview, ref.hasPreviewVersion());
        assertEquals(isPreview, ref.isPreviewOnly());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    public void resourceRefs(boolean isPreview) {
        ModuleReference ref = forPackage("module", isPreview);

        assertEquals("module", ref.name());
        assertTrue(ref.hasResources());
        assertEquals(isPreview, ref.hasPreviewVersion());
        assertEquals(isPreview, ref.isPreviewOnly());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    public void mergedRefs(boolean isPreview) {
        ModuleReference emptyRef = forEmptyPackage("module", true);
        ModuleReference resourceRef = forPackage("module", isPreview);
        ModuleReference merged = emptyRef.merge(resourceRef);

        // Merging preserves whether there's content.
        assertTrue(merged.hasResources());
        // And clears the preview-only status unless it was set in both.
        assertEquals(isPreview, merged.isPreviewOnly());
    }

    @Test
    public void writeBuffer() {
        List<ModuleReference> refs = Arrays.asList(
                forEmptyPackage("alpha", true),
                forEmptyPackage("beta", false).merge(forEmptyPackage("beta", true)),
                forPackage("gamma", false),
                forEmptyPackage("zeta", false));
        IntBuffer buffer = IntBuffer.allocate(2 * refs.size());
        ModuleReference.write(refs, buffer, fakeEncoder());
        assertArrayEquals(
                new int[]{
                        FLAGS_HAS_PREVIEW_VERSION, 100,
                        FLAGS_HAS_NORMAL_VERSION | FLAGS_HAS_PREVIEW_VERSION, 101,
                        FLAGS_HAS_NORMAL_VERSION | FLAGS_HAS_CONTENT, 102,
                        FLAGS_HAS_NORMAL_VERSION, 103},
                buffer.array());
    }

    @Test
    public void writeBuffer_emptyList() {
        IntBuffer buffer = IntBuffer.allocate(0);
        var err = assertThrows(
                IllegalArgumentException.class,
                () -> ModuleReference.write(List.of(), buffer, null));
        assertTrue(err.getMessage().contains("non-empty"));
    }

    @Test
    public void writeBuffer_badCapacity() {
        List<ModuleReference> refs = Arrays.asList(
                forPackage("first", false),
                forEmptyPackage("alpha", false));
        IntBuffer buffer = IntBuffer.allocate(10);
        var err = assertThrows(
                IllegalArgumentException.class,
                () -> ModuleReference.write(refs, buffer, null));
        assertTrue(err.getMessage().contains("buffer capacity"));
    }

    @Test
    public void writeBuffer_multiplePackagesWithResources() {
        // Only one module reference (at most) can have resources.
        List<ModuleReference> refs = Arrays.asList(
                forPackage("alpha", false),
                forPackage("beta", false));
        IntBuffer buffer = IntBuffer.allocate(2 * refs.size());
        var err = assertThrows(
                IllegalArgumentException.class,
                () -> ModuleReference.write(refs, buffer, null));
        assertTrue(err.getMessage().contains("resources"));
    }

    @Test
    public void writeBuffer_badOrdering() {
        // Badly ordered because preview references should come first.
        List<ModuleReference> refs = Arrays.asList(
                forEmptyPackage("alpha", false),
                forEmptyPackage("beta", true));
        IntBuffer buffer = IntBuffer.allocate(2 * refs.size());
        var err = assertThrows(
                IllegalArgumentException.class,
                () -> ModuleReference.write(refs, buffer, null));
        assertTrue(err.getMessage().contains("strictly ordered"));
    }

    @Test
    public void writeBuffer_duplicateRef() {
        // Technically distinct, and correctly sorted, but with duplicate names.
        List<ModuleReference> refs = Arrays.asList(
                forEmptyPackage("duplicate", true),
                forEmptyPackage("duplicate", false));
        IntBuffer buffer = IntBuffer.allocate(2 * refs.size());
        var err = assertThrows(
                IllegalArgumentException.class,
                () -> ModuleReference.write(refs, buffer, null));
        assertTrue(err.getMessage().contains("unique"));
    }

    @Test
    public void readNameOffsets() {
        // Preview versions must be first (important for early exit).
        IntBuffer buffer = IntBuffer.wrap(new int[]{
                FLAGS_HAS_NORMAL_VERSION | FLAGS_HAS_PREVIEW_VERSION, 100,
                FLAGS_HAS_PREVIEW_VERSION, 101,
                FLAGS_HAS_NORMAL_VERSION | FLAGS_HAS_CONTENT, 102,
                FLAGS_HAS_NORMAL_VERSION, 103});

        List<Integer> normalOffsets = asList(ModuleReference.readNameOffsets(buffer, true, false));
        List<Integer> previewOffsets = asList(ModuleReference.readNameOffsets(buffer, false, true));
        List<Integer> allOffsets = asList(ModuleReference.readNameOffsets(buffer, true, true));

        assertEquals(List.of(100, 102, 103), normalOffsets);
        assertEquals(List.of(100, 101), previewOffsets);
        assertEquals(List.of(100, 101, 102, 103), allOffsets);
    }

    @Test
    public void readNameOffsets_badBufferSize() {
        var err = assertThrows(
                IllegalArgumentException.class,
                () -> ModuleReference.readNameOffsets(IntBuffer.allocate(3), true, false));
        assertTrue(err.getMessage().contains("buffer size"));
    }

    @Test
    public void readNameOffsets_badFlags() {
        IntBuffer buffer = IntBuffer.wrap(new int[]{FLAGS_HAS_CONTENT, 100});
        var err = assertThrows(
                IllegalArgumentException.class,
                () -> ModuleReference.readNameOffsets(buffer, false, false));
        assertTrue(err.getMessage().contains("flags"));
    }

    @Test
    public void sortOrder_previewFirst() {
        List<ModuleReference> refs = Arrays.asList(
                forEmptyPackage("normal.beta", false),
                forPackage("preview.beta", true),
                forEmptyPackage("preview.alpha", true),
                forEmptyPackage("normal.alpha", false));
        refs.sort(Comparator.naturalOrder());
        // Non-empty first with remaining sorted by name.
        assertEquals(
                List.of("preview.alpha", "preview.beta", "normal.alpha", "normal.beta"),
                refs.stream().map(ModuleReference::name).toList());
    }

    private static <T> List<T> asList(Iterator<T> src) {
        List<T> list = new ArrayList<>();
        src.forEachRemaining(list::add);
        return list;
    }

    // Encodes strings sequentially starting from index 100.
    private static Function<String, Integer> fakeEncoder() {
        List<String> cache = new ArrayList<>();
        return s -> {
            int i = cache.indexOf(s);
            if (i == -1) {
                cache.add(s);
                return 100 + (cache.size() - 1);
            } else {
                return 100 + i;
            }
        };
    }
}
