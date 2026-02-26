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

import jdk.internal.jimage.ImageReader;
import jdk.internal.jimage.ImageReader.Node;
import jdk.test.lib.compiler.InMemoryJavaCompiler;
import jdk.test.lib.util.JarBuilder;
import jdk.tools.jlink.internal.LinkableRuntimeImage;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import tests.Helper;
import tests.JImageGenerator;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

/*
 * @test
 * @summary Tests for ImageReader.
 * @modules java.base/jdk.internal.jimage
 *          jdk.jlink/jdk.tools.jlink.internal
 *          jdk.jlink/jdk.tools.jimage
 * @library /test/jdk/tools/lib
 *          /test/lib
 * @build tests.*
 * @run junit/othervm ImageReaderTest
 */

/// Using PER_CLASS lifecycle means the (expensive) image file is only build once.
/// There is no mutable test instance state to worry about.
@TestInstance(PER_CLASS)
public class ImageReaderTest {

    private static final Map<String, List<String>> IMAGE_ENTRIES = Map.of(
            "modfoo", Arrays.asList(
                    "com.foo.Alpha",
                    "com.foo.Beta",
                    "com.foo.bar.Gamma"),
            "modbar", Arrays.asList(
                    "com.bar.One",
                    "com.bar.Two"));
    private final Path image = buildJImage(IMAGE_ENTRIES);

    @ParameterizedTest
    @ValueSource(strings = {
            "/",
            "/modules",
            "/modules/modfoo",
            "/modules/modbar",
            "/modules/modfoo/com",
            "/modules/modfoo/com/foo",
            "/modules/modfoo/com/foo/bar"})
    public void testModuleDirectories_expected(String name) throws IOException {
        try (ImageReader reader = ImageReader.open(image)) {
            assertDir(reader, name);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "//",
            "/modules/",
            "/modules/unknown",
            "/modules/modbar/",
            "/modules/modfoo//com",
            "/modules/modfoo/com/"})
    public void testModuleNodes_absent(String name) throws IOException {
        try (ImageReader reader = ImageReader.open(image)) {
            assertAbsent(reader, name);
        }
    }

    @Test
    public void testModuleResources() throws IOException {
        try (ImageReader reader = ImageReader.open(image)) {
            assertNode(reader, "/modules/modfoo/com/foo/Alpha.class");
            assertNode(reader, "/modules/modbar/com/bar/One.class");

            ImageClassLoader loader = new ImageClassLoader(reader, IMAGE_ENTRIES.keySet());
            assertEquals("Class: com.foo.Alpha", loader.loadAndGetToString("modfoo", "com.foo.Alpha"));
            assertEquals("Class: com.foo.Beta", loader.loadAndGetToString("modfoo", "com.foo.Beta"));
            assertEquals("Class: com.foo.bar.Gamma", loader.loadAndGetToString("modfoo", "com.foo.bar.Gamma"));
            assertEquals("Class: com.bar.One", loader.loadAndGetToString("modbar", "com.bar.One"));
        }
    }

    @ParameterizedTest
    @CsvSource(delimiter = ':', value = {
            "modfoo:com/foo/Alpha.class",
            "modbar:com/bar/One.class",
    })
    public void testResource_present(String modName, String resPath) throws IOException {
        try (ImageReader reader = ImageReader.open(image)) {
            assertNotNull(reader.findResourceNode(modName, resPath));
            assertTrue(reader.containsResource(modName, resPath));

            String canonicalNodeName = "/modules/" + modName + "/" + resPath;
            Node node = reader.findNode(canonicalNodeName);
            assertTrue(node != null && node.isResource());
        }
    }

    @ParameterizedTest
    @CsvSource(delimiter = ':', value = {
            // Absolute resource names are not allowed.
            "modfoo:/com/bar/One.class",
            // Resource in wrong module.
            "modfoo:com/bar/One.class",
            "modbar:com/foo/Alpha.class",
            // Directories are not returned.
            "modfoo:com/foo",
            "modbar:com/bar",
            // JImage entries exist for these, but they are not resources.
            "modules:modfoo/com/foo/Alpha.class",
            "packages:com.foo/modfoo",
            // Empty module names/paths do not find resources.
            "'':modfoo/com/foo/Alpha.class",
            "modfoo:''"})
    public void testResource_absent(String modName, String resPath) throws IOException {
        try (ImageReader reader = ImageReader.open(image)) {
            assertNull(reader.findResourceNode(modName, resPath));
            assertFalse(reader.containsResource(modName, resPath));

            // Non-existent resources names should either not be found,
            // or (in the case of directory nodes) not be resources.
            String canonicalNodeName = "/modules/" + modName + "/" + resPath;
            Node node = reader.findNode(canonicalNodeName);
            assertTrue(node == null || !node.isResource());
        }
    }

    @ParameterizedTest
    @CsvSource(delimiter = ':', value = {
            // Don't permit module names to contain paths.
            "modfoo/com/bar:One.class",
            "modfoo/com:bar/One.class",
            "modules/modfoo/com:foo/Alpha.class",
    })
    public void testResource_invalid(String modName, String resPath) throws IOException {
        try (ImageReader reader = ImageReader.open(image)) {
            assertThrows(IllegalArgumentException.class, () -> reader.containsResource(modName, resPath));
            assertThrows(IllegalArgumentException.class, () -> reader.findResourceNode(modName, resPath));
        }
    }

    @Test
    public void testPackageDirectories() throws IOException {
        try (ImageReader reader = ImageReader.open(image)) {
            Node root = assertDir(reader, "/packages");
            Set<String> pkgNames = root.getChildNames().collect(Collectors.toSet());
            assertTrue(pkgNames.contains("/packages/com"));
            assertTrue(pkgNames.contains("/packages/com.foo"));
            assertTrue(pkgNames.contains("/packages/com.bar"));

            // Even though no classes exist directly in the "com" package, it still
            // creates a directory with links back to all the modules which contain it.
            Set<String> comLinks = assertDir(reader, "/packages/com").getChildNames().collect(Collectors.toSet());
            assertTrue(comLinks.contains("/packages/com/modfoo"));
            assertTrue(comLinks.contains("/packages/com/modbar"));
        }
    }

    @Test
    public void testPackageLinks() throws IOException {
        try (ImageReader reader = ImageReader.open(image)) {
            Node moduleFoo = assertDir(reader, "/modules/modfoo");
            Node moduleBar = assertDir(reader, "/modules/modbar");
            assertSame(assertLink(reader, "/packages/com.foo/modfoo").resolveLink(), moduleFoo);
            assertSame(assertLink(reader, "/packages/com.bar/modbar").resolveLink(), moduleBar);
        }
    }

    private static ImageReader.Node assertNode(ImageReader reader, String name) throws IOException {
        ImageReader.Node node = reader.findNode(name);
        assertNotNull(node, "Could not find node: " + name);
        return node;
    }

    private static ImageReader.Node assertDir(ImageReader reader, String name) throws IOException {
        ImageReader.Node dir = assertNode(reader, name);
        assertTrue(dir.isDirectory(), "Node was not a directory: " + name);
        return dir;
    }

    private static ImageReader.Node assertLink(ImageReader reader, String name) throws IOException {
        ImageReader.Node link = assertNode(reader, name);
        assertTrue(link.isLink(), "Node was not a symbolic link: " + name);
        return link;
    }

    private static void assertAbsent(ImageReader reader, String name) throws IOException {
        assertNull(reader.findNode(name), "Should not be able to find node: " + name);
    }

    /// Builds a jimage file with the specified class entries. The classes in the built
    /// image can be loaded and executed to return their names via `toString()` to confirm
    /// the correct bytes were returned.
    public static Path buildJImage(Map<String, List<String>> entries) {
        Helper helper = getHelper();
        Path outDir = helper.createNewImageDir("test");
        JImageGenerator.JLinkTask jlink = JImageGenerator.getJLinkTask()
                .modulePath(helper.defaultModulePath())
                .output(outDir);

        Path jarDir = helper.getJarDir();
        entries.forEach((module, classes) -> {
            JarBuilder jar = new JarBuilder(jarDir.resolve(module + ".jar").toString());
            String moduleInfo = "module " + module + " {}";
            jar.addEntry("module-info.class", InMemoryJavaCompiler.compile("module-info", moduleInfo));

            classes.forEach(fqn -> {
                int lastDot = fqn.lastIndexOf('.');
                String pkg = fqn.substring(0, lastDot);
                String cls = fqn.substring(lastDot + 1);

                String path = fqn.replace('.', '/') + ".class";
                String source = String.format(
                        """
                        package %s;
                        public class %s {
                            public String toString() {
                                return "Class: %s";
                            }
                        }
                        """, pkg, cls, fqn);
                jar.addEntry(path, InMemoryJavaCompiler.compile(fqn, source));
            });
            try {
                jar.build();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            jlink.addMods(module);
        });
        return jlink.call().assertSuccess().resolve("lib", "modules");
    }

    ///  Returns the helper for building JAR and jimage files.
    private static Helper getHelper() {
        Helper helper;
        try {
            boolean isLinkableRuntime = LinkableRuntimeImage.isLinkableRuntime();
            helper = Helper.newHelper(isLinkableRuntime);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        Assumptions.assumeTrue(helper != null, "Cannot create test helper, skipping test!");
        return helper;
    }

    /// Loads and performs actions on classes stored in a given `ImageReader`.
    private static class ImageClassLoader extends ClassLoader {
        private final ImageReader reader;
        private final Set<String> testModules;

        private ImageClassLoader(ImageReader reader, Set<String> testModules) {
            this.reader = reader;
            this.testModules = testModules;
        }

        @FunctionalInterface
        public interface ClassAction<R, T extends Exception> {
            R call(Class<?> cls) throws T;
        }

        String loadAndGetToString(String module, String fqn) {
            return loadAndCall(module, fqn, c -> c.getDeclaredConstructor().newInstance().toString());
        }

        <R> R loadAndCall(String module, String fqn, ClassAction<R, ?> action) {
            Class<?> cls = findClass(module, fqn);
            assertNotNull(cls, "Could not load class: " + module + "/" + fqn);
            try {
                return action.call(cls);
            } catch (Exception e) {
                fail("Class loading failed", e);
                return null;
            }
        }

        @Override
        protected Class<?> findClass(String module, String fqn) {
            assumeTrue(testModules.contains(module), "Can only load classes in modules: " + testModules);
            String name = "/modules/" + module + "/" + fqn.replace('.', '/') + ".class";
            Class<?> cls = findLoadedClass(fqn);
            if (cls == null) {
                try {
                    ImageReader.Node node = reader.findNode(name);
                    if (node != null && node.isResource()) {
                        byte[] classBytes = reader.getResource(node);
                        cls = defineClass(fqn, classBytes, 0, classBytes.length);
                        resolveClass(cls);
                        return cls;
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            return null;
        }
    }
}
