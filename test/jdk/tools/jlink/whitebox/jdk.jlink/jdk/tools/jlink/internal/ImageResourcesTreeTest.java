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

package jdk.tools.jlink.internal;

import jdk.internal.jimage.ModuleReference;
import jdk.tools.jlink.internal.ImageResourcesTree.Node;
import jdk.tools.jlink.internal.ImageResourcesTree.PackageNode;
import jdk.tools.jlink.internal.ImageResourcesTree.ResourceNode;
import jdk.tools.jlink.internal.ImageResourcesTree.Tree;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ImageResourcesTreeTest {

    private static final String MODULES_PREFIX = "/modules/";
    private static final String PACKAGES_PREFIX = "/packages/";

    @Test
    public void directoryNodes() {
        List<String> paths = List.of(
                "/java.base/java/util/SomeClass.class",
                "/java.base/java/util/SomeOtherClass.class",
                "/java.base/java/util/resource.txt",
                "/java.logging/java/util/logging/LoggingClass.class",
                "/java.logging/java/util/logging/OtherLoggingClass.class");

        Tree tree = new Tree(paths);
        Map<String, Node> nodes = tree.getMap();

        // All paths from the root (but not the root itself).
        assertTrue(nodes.containsKey("/modules"));
        assertTrue(nodes.containsKey("/modules/java.base"));
        assertTrue(nodes.containsKey("/modules/java.base/java"));
        assertTrue(nodes.containsKey("/modules/java.base/java/util"));
        assertFalse(nodes.containsKey("/"));

        // Check for mismatched modules.
        assertTrue(nodes.containsKey("/modules/java.logging/java/util/logging"));
        assertFalse(nodes.containsKey("/modules/java.base/java/util/logging"));

        Set<String> dirPaths = nodes.keySet().stream()
                .filter(p -> p.startsWith(MODULES_PREFIX))
                .collect(Collectors.toSet());
        for (String path : dirPaths) {
            Node dir = nodes.get(path);
            assertFalse(dir instanceof ResourceNode, "Unexpected resource: " + dir);
            assertEquals(path, dir.getPath());
            assertTrue(path.endsWith("/" + dir.getName()), "Unexpected directory name: " + dir);
        }
    }

    @Test
    public void resourceNodes() {
        List<String> paths = List.of(
                "/java.base/module-info.class",
                "/java.base/java/util/SomeClass.class",
                "/java.base/java/util/SomeOtherClass.class",
                "/java.base/java/util/resource.txt",
                "/java.logging/module-info.class",
                "/java.logging/java/util/logging/LoggingClass.class",
                "/java.logging/java/util/logging/OtherLoggingClass.class");

        Tree tree = new Tree(paths);
        // This map *does not* contain the resources, only the "directory" nodes.
        Map<String, Node> nodes = tree.getMap();

        assertContainsResources(
                nodes.get("/modules/java.base/java/util"),
                "SomeClass.class", "SomeOtherClass.class", "resource.txt");
        assertContainsResources(nodes.get("/modules/java.base"), "module-info.class");

        assertContainsResources(
                nodes.get("/modules/java.logging/java/util/logging"),
                "LoggingClass.class", "OtherLoggingClass.class");
        assertContainsResources(nodes.get("/modules/java.logging"), "module-info.class");
    }

    @Test
    public void expectedPackages() {
        // Paths are only to resources. Packages are inferred.
        List<String> paths = List.of(
                "/java.base/java/util/SomeClass.class",
                "/java.logging/java/util/logging/SomeClass.class");

        Tree tree = new Tree(paths);
        Map<String, Node> nodes = tree.getMap();
        Node packages = nodes.get("/packages");
        List<String> pkgNames = nodes.keySet().stream()
                .filter(p -> p.startsWith(PACKAGES_PREFIX))
                .map(p -> p.substring(PACKAGES_PREFIX.length()))
                .sorted()
                .toList();

        assertEquals(List.of("java", "java.util", "java.util.logging"), pkgNames);
        for (String pkgName : pkgNames) {
            PackageNode pkgNode = assertInstanceOf(PackageNode.class, packages.getChildren(pkgName));
            assertSame(nodes.get(PACKAGES_PREFIX + pkgNode.getName()), pkgNode);
        }
    }

    @Test
    public void expectedPackageEntries() {
        List<String> paths = List.of(
                "/java.base/java/util/SomeClass.class",
                "/java.logging/java/util/logging/SomeClass.class");

        Tree tree = new Tree(paths);
        Map<String, Node> nodes = tree.getMap();
        PackageNode pkgUtil = getPackageNode(nodes, "java.util");
        List<ModuleReference> modRefs = pkgUtil.getModuleReferences();
        assertEquals(2, modRefs.size());

        List<String> modNames = modRefs.stream().map(ModuleReference::name).toList();
        assertEquals(List.of("java.base", "java.logging"), modNames);

        // Ordered by name.
        assertNonEmptyRef(modRefs.get(0), "java.base");
        assertEmptyRef(modRefs.get(1), "java.logging");
    }

    @Test
    public void expectedPackageEntries_withPreviewResources() {
        List<String> paths = List.of(
                "/java.base/java/util/SomeClass.class",
                "/java.base/java/util/OtherClass.class",
                "/java.base/META-INF/preview/java/util/OtherClass.class",
                "/java.logging/java/util/logging/SomeClass.class");

        Tree tree = new Tree(paths);
        Map<String, Node> nodes = tree.getMap();
        PackageNode pkgUtil = getPackageNode(nodes, "java.util");
        List<ModuleReference> modRefs = pkgUtil.getModuleReferences();

        ModuleReference baseRef = modRefs.get(0);
        assertNonEmptyRef(baseRef, "java.base");
        assertTrue(baseRef.hasPreviewVersion());
    }

    @Test
    public void expectedPackageEntries_withPreviewOnlyPackages() {
        List<String> paths = List.of(
                "/java.base/java/util/SomeClass.class",
                "/java.base/META-INF/preview/java/util/preview/only/PreviewClass.class");

        Tree tree = new Tree(paths);
        Map<String, Node> nodes = tree.getMap();

        // Preview only package (with content).
        PackageNode nonEmptyPkg = getPackageNode(nodes, "java.util.preview.only");
        ModuleReference nonEmptyRef = nonEmptyPkg.getModuleReferences().getFirst();
        assertNonEmptyPreviewOnlyRef(nonEmptyRef, "java.base");

        // Preview only packages can be empty.
        PackageNode emptyPkg = getPackageNode(nodes, "java.util.preview");
        ModuleReference emptyRef = emptyPkg.getModuleReferences().getFirst();
        assertEmptyPreviewOnlyRef(emptyRef, "java.base");
    }

    @Test
    public void expectedPackageOrder_sharedPackage() {
        // Resource in many modules define the same package (java.shared), but
        // this only has content in one module (java.content).
        // Order of test data is shuffled to show reordering in entry list.
        // "java.moduleN" would sort before after "java.previewN" if it were
        // only sorted by name, but preview entries come first.
        // Expect: preview{1..3) -> content -> module{1..3}
        List<String> paths = List.of(
                // Module with content in "java.shared".
                "/java.content/java/shared/MainPackageClass.class",
                // Other resources (in other modules) which implicitly define "java.shared".
                "/java.module3/java/shared/three/SomeClass.class",
                "/java.module2/java/shared/two/SomeClass.class",
                "/java.module1/java/shared/one/SomeClass.class",
                // Preview resources in other modules which implicitly define "java.shared".
                "/java.preview3/META-INF/preview/java/shared/baz/SomeClass.class",
                "/java.preview2/META-INF/preview/java/shared/bar/SomeClass.class",
                "/java.preview1/META-INF/preview/java/shared/foo/SomeClass.class");

        Tree tree = new Tree(paths);
        Map<String, Node> nodes = tree.getMap();

        PackageNode sharedPkg = getPackageNode(nodes, "java.shared");
        List<ModuleReference> refs = sharedPkg.getModuleReferences();

        // Preview packages first, by name.
        int n = 1;
        for (ModuleReference ref : refs.subList(0, 3)) {
            assertEmptyPreviewOnlyRef(ref, "java.preview" + (n++));
        }
        // The content package (simply due to its name).
        assertNonEmptyRef(refs.get(3), "java.content");
        // And the non-preview empty packages after.
        n = 1;
        for (ModuleReference ref : refs.subList(4, 7)) {
            assertEmptyRef(ref, "java.module" + (n++));
        }
    }

    static PackageNode getPackageNode(Map<String, Node> nodes, String pkgName) {
        return assertInstanceOf(PackageNode.class, nodes.get(PACKAGES_PREFIX + pkgName));
    }

    static void assertContainsResources(Node dirNode, String... resourceNames) {
        for (String name : resourceNames) {
            Node node = assertInstanceOf(ResourceNode.class, dirNode.getChildren(name));
            assertEquals(name, node.getName());
            assertEquals(dirNode.getPath() + "/" + name, node.getPath());
        }
    }

    static void assertNonEmptyRef(ModuleReference ref, String modName) {
        assertEquals(modName, ref.name(), "Unexpected module name: " + ref);
        assertTrue(ref.hasResources(), "Expected non-empty reference: " + ref);
        assertFalse(ref.isPreviewOnly(), "Expected not preview-only: " + ref);
    }

    static void assertEmptyRef(ModuleReference ref, String modName) {
        assertEquals(modName, ref.name(), "Unexpected module name: " + ref);
        assertFalse(ref.hasResources(), "Expected empty reference: " + ref);
        assertFalse(ref.isPreviewOnly(), "Expected not preview-only: " + ref);
    }

    static void assertNonEmptyPreviewOnlyRef(ModuleReference ref, String modName) {
        assertEquals(modName, ref.name(), "Unexpected module name: " + ref);
        assertTrue(ref.hasResources(), "Expected empty reference: " + ref);
        assertTrue(ref.isPreviewOnly(), "Expected preview-only: " + ref);
    }

    static void assertEmptyPreviewOnlyRef(ModuleReference ref, String modName) {
        assertEquals(modName, ref.name(), "Unexpected module name: " + ref);
        assertFalse(ref.hasResources(), "Expected empty reference: " + ref);
        assertTrue(ref.isPreviewOnly(), "Expected preview-only: " + ref);
    }
}
