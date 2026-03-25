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
import jdk.internal.jimage.PreviewMode;
import jdk.internal.jrtfs.SystemImage;
import jdk.test.lib.compiler.InMemoryJavaCompiler;
import jdk.test.lib.util.JarBuilder;
import jdk.tools.jlink.internal.LinkableRuntimeImage;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.Parameter;
import org.junit.jupiter.params.ParameterizedClass;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import tests.Helper;
import tests.JImageGenerator;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toSet;
import static jdk.internal.jimage.PreviewMode.DISABLED;
import static jdk.internal.jimage.PreviewMode.ENABLED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/*
 * @test
 * @summary Tests for SystemImage to ensure parity between ImageReader and ExplodedImage.
 * @modules java.base/jdk.internal.jimage
 *          java.base/jdk.internal.jrtfs
 *          jdk.jlink/jdk.tools.jimage
 *          jdk.jlink/jdk.tools.jlink.internal
 * @library /test/jdk/tools/lib
 *          /test/lib
 * @build tests.*
 * @run junit/othervm -esa -ea SystemImageTest
 */
// FIXME: Currently the test output in Jtreg does not show the implementation.
// This is due to using both @ParameterizedClass and @ParameterizedTest to
// create a cross-product of test parameters. The parameters of parameterized
// tests are shown, but not the class level implementation choice.
//
// If you are debugging a failure in this test, change the @EnumSource line to
// include 'names = {"[SYSTEM|EXPLODED]"}' to test a single implementation.
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ParameterizedClass
@EnumSource(SystemImageTest.ImageType.class)
class SystemImageTest {
    // Selects the underlying implementation to be tested.
    enum ImageType {SYSTEM, EXPLODED}

    // The '@' prefix marks the entry as a preview entry which will be placed in
    // the '/modules/<module>/META-INF/preview/...' path.
    private static final Map<String, List<String>> IMAGE_ENTRIES = Map.of(
            "modfoo", Arrays.asList(
                    "com.foo.HasPreviewVersion",
                    "com.foo.NormalFoo",
                    "com.foo.bar.NormalBar",
                    // Replaces original class in preview mode.
                    "@com.foo.HasPreviewVersion",
                    // New class in existing package in preview mode.
                    "@com.foo.bar.IsPreviewOnly"),
            "modbar", Arrays.asList(
                    "com.bar.One",
                    "com.bar.Two",
                    // Two new packages in preview mode (new symbolic links).
                    "@com.bar.preview.stuff.Foo",
                    "@com.bar.preview.stuff.Bar"),
            "modgus", Arrays.asList(
                    // A second module with a preview-only empty package (preview).
                    "@com.bar.preview.other.Gus"));

    // Test data paths, built once for all tests.
    private Path jimageFile;
    private Path explodedModulesDir;

    // The injected implementation type from @EnumSource.
    @Parameter(0)
    private ImageType implType;

    @BeforeAll
    public void buildTestData(@TempDir Path modulesRoot) throws IOException {
        Helper helper = getHelper();
        // Compile into the helper's jar directory so jlink will include it.
        Path jarDir = compileModules(helper.getJarDir(), IMAGE_ENTRIES);
        this.jimageFile = buildJimage(helper, IMAGE_ENTRIES.keySet());
        explodeTestModules(jarDir, modulesRoot);
        this.explodedModulesDir = modulesRoot;
    }

    // Make new images for each test based on the injected implementation type.
    private SystemImage getImage(PreviewMode mode) throws IOException {
        return switch (implType) {
            case SYSTEM -> SystemImage.fromJimage(jimageFile, mode);
            case EXPLODED -> SystemImage.fromDirectory(explodedModulesDir, mode);
        };
    }

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
        try (var image = getImage(DISABLED)) {
            assertDir(image, name);
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
            "/modules/modfoo/com/",
            "/modules/modfoo/com/foo/.",
            "/modules/modfoo/com/foo/./",
            "/modules/modfoo/com/foo/./bar",
            "/modules/modfoo/com/foo/..",
            "/modules/modfoo/com/foo/../",
            "/modules/modfoo/com/bar/../foo",
            "/modules/../packages",
            "/modules/../.."})
    public void testModuleNodes_absent(String name) throws IOException {
        try (var image = getImage(DISABLED)) {
            assertAbsent(image, name);
        }
    }

    @Test
    public void testModuleResources() throws IOException {
        try (var image = getImage(DISABLED)) {
            assertNode(image, "/modules/modfoo/com/foo/HasPreviewVersion.class");
            assertNode(image, "/modules/modbar/com/bar/One.class");

            ImageClassLoader loader = loader(image);
            loader.assertNonPreviewVersion("modfoo", "com.foo.HasPreviewVersion");
            loader.assertNonPreviewVersion("modfoo", "com.foo.NormalFoo");
            loader.assertNonPreviewVersion("modfoo", "com.foo.bar.NormalBar");
            loader.assertNonPreviewVersion("modbar", "com.bar.One");
        }
    }

    @Test
    public void testPackageDirectories() throws IOException {
        try (var image = getImage(DISABLED)) {
            ImageReader.Node root = assertDir(image, "/packages");
            Set<String> pkgNames = root.getChildNames().collect(toSet());
            assertTrue(pkgNames.contains("/packages/com"));
            assertTrue(pkgNames.contains("/packages/com.foo"));
            assertTrue(pkgNames.contains("/packages/com.bar"));

            // Even though no classes exist directly in the "com" package, it still
            // creates a directory with links back to all the modules which contain it.
            Set<String> comLinks = assertDir(image, "/packages/com").getChildNames().collect(Collectors.toSet());
            assertTrue(comLinks.contains("/packages/com/modfoo"));
            assertTrue(comLinks.contains("/packages/com/modbar"));
        }
    }

    @Test
    public void testPackageLinks() throws IOException {
        try (var image = getImage(DISABLED)) {
            ImageReader.Node moduleFoo = assertDir(image, "/modules/modfoo");
            ImageReader.Node moduleBar = assertDir(image, "/modules/modbar");
            assertSame(assertLink(image, "/packages/com.foo/modfoo").resolveLink(), moduleFoo);
            assertSame(assertLink(image, "/packages/com.bar/modbar").resolveLink(), moduleBar);
        }
    }

    @Test
    public void testPreviewResources_disabled() throws IOException {
        try (var image = getImage(DISABLED)) {
            // No preview classes visible.
            ImageClassLoader loader = loader(image);
            loader.assertNonPreviewVersion("modfoo", "com.foo.HasPreviewVersion");
            loader.assertNonPreviewVersion("modfoo", "com.foo.NormalFoo");
            loader.assertNonPreviewVersion("modfoo", "com.foo.bar.NormalBar");

            // NormalBar exists but IsPreviewOnly doesn't.
            assertResource(image, "/modules/modfoo/com/foo/bar/NormalBar.class");
            assertAbsent(image, "/modules/modfoo/com/foo/bar/IsPreviewOnly.class");
            assertDirContents(image, "/modules/modfoo/com/foo", "HasPreviewVersion.class", "NormalFoo.class", "bar");
            assertDirContents(image, "/modules/modfoo/com/foo/bar", "NormalBar.class");
        }
    }

    @Test
    public void testPreviewResources_enabled() throws IOException {
        try (var image = getImage(ENABLED)) {
            // Preview version of classes either overwrite existing entries or are added to directories.
            ImageClassLoader loader = loader(image);
            loader.assertPreviewVersion("modfoo", "com.foo.HasPreviewVersion");
            loader.assertNonPreviewVersion("modfoo", "com.foo.NormalFoo");
            loader.assertNonPreviewVersion("modfoo", "com.foo.bar.NormalBar");
            loader.assertPreviewVersion("modfoo", "com.foo.bar.IsPreviewOnly");

            // Both NormalBar and IsPreviewOnly exist (direct lookup and as child nodes).
            assertResource(image, "/modules/modfoo/com/foo/bar/NormalBar.class");
            assertResource(image, "/modules/modfoo/com/foo/bar/IsPreviewOnly.class");
            assertDirContents(image, "/modules/modfoo/com/foo", "HasPreviewVersion.class", "NormalFoo.class", "bar");
            assertDirContents(image, "/modules/modfoo/com/foo/bar", "NormalBar.class", "IsPreviewOnly.class");
        }
    }

    @Test
    public void testPreviewOnlyPackages_disabled() throws IOException {
        try (var image = getImage(DISABLED)) {
            // No 'preview' package or anything inside it.
            assertDirContents(image, "/modules/modbar/com/bar", "One.class", "Two.class");
            assertAbsent(image, "/modules/modbar/com/bar/preview");
            assertAbsent(image, "/modules/modbar/com/bar/preview/stuff/Foo.class");

            // And no package link.
            assertAbsent(image, "/packages/com.bar.preview");
        }
    }

    @Test
    public void testPreviewOnlyPackages_enabled() throws IOException {
        try (var image = getImage(ENABLED)) {
            // In preview mode 'preview' package exists with preview only content.
            assertDirContents(image, "/modules/modbar/com/bar", "One.class", "Two.class", "preview");
            assertDirContents(image, "/modules/modbar/com/bar/preview/stuff", "Foo.class", "Bar.class");
            assertResource(image, "/modules/modbar/com/bar/preview/stuff/Foo.class");

            // And package links exists.
            assertDirContents(image, "/packages/com.bar.preview", "modbar", "modgus");
        }
    }

    @Test
    public void testPreviewModeLinks_disabled() throws IOException {
        try (var image = getImage(DISABLED)) {
            assertDirContents(image, "/packages/com.bar", "modbar");
            // Missing symbolic link and directory when not in preview mode.
            assertAbsent(image, "/packages/com.bar.preview");
            assertAbsent(image, "/packages/com.bar.preview.stuff");
            assertAbsent(image, "/modules/modbar/com/bar/preview");
            assertAbsent(image, "/modules/modbar/com/bar/preview/stuff");
        }
    }

    @Test
    public void testPreviewModeLinks_enabled() throws IOException {
        try (var image = getImage(ENABLED)) {
            // In preview mode there is a new preview-only module visible.
            assertDirContents(image, "/packages/com.bar", "modbar", "modgus");
            // And additional packages are present.
            assertDirContents(image, "/packages/com.bar.preview", "modbar", "modgus");
            assertDirContents(image, "/packages/com.bar.preview.stuff", "modbar");
            assertDirContents(image, "/packages/com.bar.preview.other", "modgus");
            // And the preview-only content appears as we expect.
            assertDirContents(image, "/modules/modbar/com/bar", "One.class", "Two.class", "preview");
            assertDirContents(image, "/modules/modbar/com/bar/preview", "stuff");
            assertDirContents(image, "/modules/modbar/com/bar/preview/stuff", "Foo.class", "Bar.class");
            // In both modules in which it was added.
            assertDirContents(image, "/modules/modgus/com/bar", "preview");
            assertDirContents(image, "/modules/modgus/com/bar/preview", "other");
            assertDirContents(image, "/modules/modgus/com/bar/preview/other", "Gus.class");
        }
    }

    @ParameterizedTest
    @EnumSource(value = PreviewMode.class, names = {"DISABLED", "ENABLED"})
    public void testPreviewEntriesAlwaysHidden(PreviewMode mode) throws IOException {
        try (var image = getImage(mode)) {
            // The META-INF directory exists, but does not contain the preview directory.
            ImageReader.Node dir = assertDir(image, "/modules/modfoo/META-INF");
            assertEquals(0, dir.getChildNames().filter(n -> n.endsWith("/preview")).count());
            // Neither the preview directory, nor anything in it, can be looked-up directly.
            assertAbsent(image, "/modules/modfoo/META-INF/preview");
            assertAbsent(image, "/modules/modfoo/META-INF/preview/com/foo");
            // HasPreviewVersion.class is a preview class in the test data, and thus appears in
            // two places in the jimage). Ensure the preview version is always hidden.
            String previewPath = "com/foo/HasPreviewVersion.class";
            assertNode(image, "/modules/modfoo/" + previewPath);
            assertAbsent(image, "/modules/modfoo/META-INF/preview/" + previewPath);
        }
    }

    // ======== Helper assertions with better error reporting ========

    private static ImageReader.Node assertNode(SystemImage image, String name) throws IOException {
        ImageReader.Node node = image.findNode(name);
        assertNotNull(node, "Could not find node: " + name);
        return node;
    }

    private static void assertResource(SystemImage image, String name) throws IOException {
        ImageReader.Node node = assertNode(image, name);
        assertTrue(node.isResource(), "Node was not a resource: " + name);
    }

    private static ImageReader.Node assertDir(SystemImage image, String name) throws IOException {
        ImageReader.Node dir = assertNode(image, name);
        assertTrue(dir.isDirectory(), "Node was not a directory: " + name);
        return dir;
    }

    private static void assertDirContents(SystemImage image, String name, String... expectedChildNames)
            throws IOException {
        ImageReader.Node dir = assertDir(image, name);
        // Use a list (not a set) to avoid hiding duplicate entries.
        List<String> localChildNames = dir.getChildNames()
                .peek(s -> assertTrue(s.startsWith(name + "/")))
                .map(s -> s.substring(name.length() + 1))
                .sorted()
                .toList();
        assertEquals(
                Stream.of(expectedChildNames).sorted().toList(),
                localChildNames,
                String.format("Unexpected child names in directory '%s'", name));
    }

    private static ImageReader.Node assertLink(SystemImage image, String name) throws IOException {
        ImageReader.Node link = assertNode(image, name);
        assertTrue(link.isLink(), "Node should be a symbolic link: " + link.getName());
        return link;
    }

    private static void assertAbsent(SystemImage image, String name) throws IOException {
        assertNull(image.findNode(name), "Should not be able to find node: " + name);
    }

    /// Returns a custom class loader for loading instances from a given image
    /// and making assertions about the class implementation.
    private static ImageClassLoader loader(SystemImage image) {
        return new ImageClassLoader(image, IMAGE_ENTRIES.keySet());
    }

    // ======== Test data creation ========

    /// Builds a jimage file with the specified class entries. The classes in
    /// the built image can be loaded and executed to return their names via
    /// `toString()` to confirm the correct bytes were returned.
    private static Path buildJimage(Helper helper, Set<String> moduleNames) {
        Path outDir = helper.createNewImageDir("test");
        // The default module path contains the directory we compiled the jars into.
        JImageGenerator.JLinkTask jlink = JImageGenerator.getJLinkTask()
                .modulePath(helper.defaultModulePath())
                .output(outDir);
        moduleNames.forEach(jlink::addMods);
        return jlink.call().assertSuccess().resolve("lib", "modules");
    }

    /// Compiles a set of synthetic modules, as separate Jar files, in the given
    /// directory.
    private static Path compileModules(Path jarDir, Map<String, List<String>> entries) {
        entries.forEach((module, classes) -> compileModuleJar(module, classes, jarDir));
        return jarDir;
    }

    /// Compiles a synthetic module containing test classes into a single Jar
    /// file named {@code <module>.jar} in the given directory. Test classes can
    /// be instantiated and have their {@code toString()} method called to
    /// return a status string for testing.
    ///
    /// If a fully qualified class name is prefixed with {@code @} then it is
    /// compiled as a preview version of the class, with different
    /// {@code toString()} representation.
    private static void compileModuleJar(String module, List<String> classNames, Path jarDir) {
        JarBuilder jar = new JarBuilder(jarDir.resolve(module + ".jar").toString());
        String moduleInfo = "module " + module + " {}";
        jar.addEntry("module-info.class", InMemoryJavaCompiler.compile("module-info", moduleInfo));

        classNames.forEach(fqn -> {
            boolean isPreviewEntry = fqn.startsWith("@");
            if (isPreviewEntry) {
                fqn = fqn.substring(1);
            }
            int lastDot = fqn.lastIndexOf('.');
            String pkg = fqn.substring(0, lastDot);
            String cls = fqn.substring(lastDot + 1);
            String source = String.format(
                    """
                    package %s;
                    public class %s {
                        public String toString() {
                            return "%s: %s";
                        }
                    }
                    """, pkg, cls, isPreviewEntry ? "Preview" : "Class", fqn);
            String path = (isPreviewEntry ? "META-INF/preview/" : "") + fqn.replace('.', '/') + ".class";
            jar.addEntry(path, InMemoryJavaCompiler.compile(fqn, source));
        });
        try {
            jar.build();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /// Unpacks Jar files in a given directory to construct an "exploded" view
    /// of the jimage content. Modules are unpacked into a directory named after
    /// the Jar file's base name, and synthetic "marker" files, designed to
    /// mimic build artifacts which should be ignored, are added.
    private static void explodeTestModules(Path jarDir, Path modulesRoot) throws IOException {
        try (var jars = Files.list(jarDir).filter(SystemImageTest::isJarFile)) {
            jars.forEach(jar -> explodeModuleJar(jar, modulesRoot));
        }
    }

    private static boolean isJarFile(Path p) {
        return p.getFileName().toString().endsWith(".jar");
    }

    /// Unpacks the content of a single Jar file into a modules directory, and
    /// adds synthetic marker files to mimic build artifacts (which should be
    /// ignored).
    private static void explodeModuleJar(Path jar, Path modulesRoot) {
        String modName = jar.getFileName().toString();
        if (!modName.endsWith(".jar")) {
            throw new IllegalArgumentException("Bad jar file: " + jar);
        }
        modName = modName.substring(0, modName.length() - 4);
        Path modDir = modulesRoot.resolve(modName);
        try (FileSystem zipfs = FileSystems.newFileSystem(jar, Map.of("accessMode", "readOnly"))) {
            Path rootDir = zipfs.getRootDirectories().iterator().next();
            Set<Path> dstDirs = new HashSet<>();
            try (var files = Files.walk(rootDir)) {
                files.filter(Files::isRegularFile).forEach(path -> {
                    try {
                        // Construct equivalent destination path in modules dir.
                        Path dst = StreamSupport.stream(path.spliterator(), false)
                                .reduce(modDir, (d, p) -> d.resolve(p.toString()));
                        if (dstDirs.add(dst.getParent())) {
                            Files.createDirectories(dst.getParent());
                        }
                        Files.copy(path, dst);
                    } catch (IOException ex) {
                        throw new UncheckedIOException(ex);
                    }
                });
            }
            // Add a marker file in each directory and the module root.
            dstDirs.forEach(SystemImageTest::writeIgnoredBuildMarker);
            writeIgnoredBuildMarker(modDir);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    /// Writes a "marker" file in a given directory to mimic build artifacts
    /// which must be ignored when using "exploded" images.
    private static void writeIgnoredBuildMarker(Path dir) {
        try {
            Files.writeString(dir.resolve("_the.ignored.marker"), "Ignored", UTF_8);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    /// Returns the helper for building JAR and jimage files.
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

    /// Provides assertions for classes loaded from a specified `SystemImage`.
    private static class ImageClassLoader extends ClassLoader {
        private final SystemImage image;
        private final Set<String> testModules;

        private ImageClassLoader(SystemImage image, Set<String> testModules) {
            this.image = image;
            this.testModules = testModules;
        }

        /// Asserts that a synthetic test class, loaded from a given module, is
        /// the *non-preview* version.
        ///
        /// @param module module name
        /// @param fqn fully qualified class name
        public void assertNonPreviewVersion(String module, String fqn) throws IOException {
            assertEquals("Class: " + fqn, loadAndGetToString(module, fqn));
        }

        /// Asserts that a synthetic test class, loaded from a given module, is
        /// the *preview* version.
        ///
        /// @param module module name
        /// @param fqn fully qualified class name
        public void assertPreviewVersion(String module, String fqn) throws IOException {
            assertEquals("Preview: " + fqn, loadAndGetToString(module, fqn));
        }

        private String loadAndGetToString(String module, String fqn) {
            return loadAndCall(module, fqn, c -> c.getDeclaredConstructor().newInstance().toString());
        }

        @FunctionalInterface
        public interface ClassAction<R, T extends Exception> {
            R call(Class<?> cls) throws T;
        }

        private <R> R loadAndCall(String module, String fqn, ClassAction<R, ?> action) {
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
                    ImageReader.Node node = image.findNode(name);
                    if (node != null && node.isResource()) {
                        byte[] classBytes = image.getResource(node);
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
