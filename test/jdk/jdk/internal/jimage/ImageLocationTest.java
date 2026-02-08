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

import jdk.internal.jimage.ImageLocation;
import jdk.internal.jimage.ModuleReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/*
 * @test
 * @summary Tests for ImageLocation.
 * @modules java.base/jdk.internal.jimage
 * @run junit/othervm -esa ImageLocationTest
 */
public class ImageLocationTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "/modules/modfoo/com",
            "/modules/modfoo/com/foo/Foo.class"})
    public void getFlags_resourceNames(String name) {
        String previewName = previewName(name);

        int noPreviewFlags =
                ImageLocation.getFlags(name, Set.of(name)::contains);
        assertEquals(0, noPreviewFlags);
        assertFalse(ImageLocation.hasPreviewVersion(noPreviewFlags));
        assertFalse(ImageLocation.isPreviewOnly(noPreviewFlags));

        int withPreviewFlags =
                ImageLocation.getFlags(name, Set.of(name, previewName)::contains);
        assertTrue(ImageLocation.hasPreviewVersion(withPreviewFlags));
        assertFalse(ImageLocation.isPreviewOnly(withPreviewFlags));

        int previewOnlyFlags = ImageLocation.getFlags(previewName, Set.of(previewName)::contains);
        assertFalse(ImageLocation.hasPreviewVersion(previewOnlyFlags));
        assertTrue(ImageLocation.isPreviewOnly(previewOnlyFlags));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/modules",
            "/packages",
            "/modules/modfoo",
            "/modules/modfoo/META-INF",
            "/modules/modfoo/META-INF/module-info.class"})
    public void getFlags_zero(String name) {
        assertEquals(0, ImageLocation.getFlags(name, Set.of(name)::contains));
    }

    @Test
    public void getFlags_packageFlags() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ImageLocation.getFlags("/packages/pkgname", p -> true));
    }

    @Test
    public void getPackageFlags_noPreview() {
        List<ModuleReference> refs = List.of(
                ModuleReference.forPackage("modfoo", false),
                ModuleReference.forEmptyPackage("modbar", false),
                ModuleReference.forEmptyPackage("modbaz", false));
        int noPreviewFlags = ImageLocation.getPackageFlags(refs);
        assertEquals(0, noPreviewFlags);
        assertFalse(ImageLocation.hasPreviewVersion(noPreviewFlags));
        assertFalse(ImageLocation.isPreviewOnly(noPreviewFlags));
    }

    @Test
    public void getPackageFlags_withPreview() {
        List<ModuleReference> refs = List.of(
                ModuleReference.forPackage("modfoo", true),
                ModuleReference.forEmptyPackage("modbar", false),
                ModuleReference.forEmptyPackage("modbaz", true));
        int withPreviewFlags = ImageLocation.getPackageFlags(refs);
        assertTrue(ImageLocation.hasPreviewVersion(withPreviewFlags));
        assertFalse(ImageLocation.isPreviewOnly(withPreviewFlags));
    }

    @Test
    public void getPackageFlags_previewOnly() {
        List<ModuleReference> refs = List.of(
                ModuleReference.forPackage("modfoo", true),
                ModuleReference.forEmptyPackage("modbar", true),
                ModuleReference.forEmptyPackage("modbaz", true));
        int previewOnlyFlags = ImageLocation.getPackageFlags(refs);
        // Note the asymmetry between this and the getFlags() case. Unlike
        // module resources, there is no concept of a separate package directory
        // existing in the preview namespace, so a single entry serves both
        // purposes, and hasPreviewVersion() and isPreviewOnly() can both be set.
        assertTrue(ImageLocation.hasPreviewVersion(previewOnlyFlags));
        assertTrue(ImageLocation.isPreviewOnly(previewOnlyFlags));
    }

    private static final Pattern MODULES_PATH = Pattern.compile("/modules/([^/]+)/(.+)");

    private static String previewName(String name) {
        var m = MODULES_PATH.matcher(name);
        if (m.matches() && !m.group(2).startsWith("/META-INF/preview/")) {
            return "/modules/" + m.group(1) + "/META-INF/preview/" + m.group(2);
        }
        throw new IllegalStateException("Invalid modules name: " + name);
    }
}
