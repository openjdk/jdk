/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @library /lib/testlibrary
 * @build AutomaticModulesTest ModuleUtils JarUtils
 * @run testng AutomaticModulesTest
 * @summary Basic tests for automatic modules
 */

import java.io.IOException;
import java.lang.module.Configuration;
import java.lang.module.FindException;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleDescriptor.Exports;
import java.lang.module.ModuleDescriptor.Requires.Modifier;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.lang.module.ResolvedModule;
import java.lang.reflect.Layer;
import java.lang.reflect.Module;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

@Test
public class AutomaticModulesTest {

    private static final Path USER_DIR
         = Paths.get(System.getProperty("user.dir"));

    @DataProvider(name = "names")
    public Object[][] createNames() {
        return new Object[][] {

            // JAR file name                module-name[/version]

            { "foo.jar",                    "foo" },

            { "foo-1.jar",                  "foo/1" },
            { "foo-1.2.jar",                "foo/1.2" },
            { "foo-1.2.3.jar",              "foo/1.2.3" },
            { "foo-1.2.3.4.jar",            "foo/1.2.3.4" },

            { "foo-10.jar",                 "foo/10" },
            { "foo-10.20.jar",              "foo/10.20" },
            { "foo-10.20.30.jar",           "foo/10.20.30" },
            { "foo-10.20.30.40.jar",        "foo/10.20.30.40" },

            { "foo-bar.jar",                "foo.bar" },
            { "foo-bar-1.jar",              "foo.bar/1" },
            { "foo-bar-1.2.jar",            "foo.bar/1.2"},
            { "foo-bar-10.jar",             "foo.bar/10" },
            { "foo-bar-10.20.jar",          "foo.bar/10.20" },

            { "foo-1.2-SNAPSHOT.jar",       "foo/1.2-SNAPSHOT" },
            { "foo-bar-1.2-SNAPSHOT.jar",   "foo.bar/1.2-SNAPSHOT" },

            { "foo--bar-1.0.jar",           "foo.bar/1.0" },
            { "-foo-bar-1.0.jar",           "foo.bar/1.0" },
            { "foo-bar--1.0.jar",           "foo.bar/1.0" },

        };
    }

    // JAR file names that do not map to a legal module name
    @DataProvider(name = "badnames")
    public Object[][] createBadNames() {
        return new Object[][]{

            { ".jar",     null },
            { "_.jar",    null }

        };
    }

    /**
     * Test mapping of JAR file names to module names
     */
    @Test(dataProvider = "names")
    public void testNames(String fn, String mid) throws IOException {
        String[] s = mid.split("/");
        String mn = s[0];
        String vs = (s.length == 2) ? s[1] : null;

        Path dir = Files.createTempDirectory(USER_DIR, "mods");
        Path jf = dir.resolve(fn);

        // create empty JAR file
        createDummyJarFile(jf);

        // create a ModuleFinder to find modules in the directory
        ModuleFinder finder = ModuleFinder.of(dir);

        // a module with the expected name should be found
        Optional<ModuleReference> mref = finder.find(mn);
        assertTrue(mref.isPresent(), mn + " not found");

        ModuleDescriptor descriptor = mref.get().descriptor();
        assertEquals(descriptor.name(), mn);
        if (vs == null) {
            assertFalse(descriptor.version().isPresent());
        } else {
            assertEquals(descriptor.version().get().toString(), vs);
        }
    }


    /**
     * Test impossible mapping of JAR files to modules names
     */
    @Test(dataProvider = "badnames", expectedExceptions = FindException.class)
    public void testBadNames(String fn, String ignore) throws IOException {
        Path dir = Files.createTempDirectory(USER_DIR, "mods");
        Path jf = dir.resolve(fn);

        // create empty JAR file
        createDummyJarFile(jf);

        // should throw FindException
        ModuleFinder.of(dir).findAll();
    }


    /**
     * Test all packages are exported
     */
    public void testPackages() throws IOException {
        Path dir = Files.createTempDirectory(USER_DIR, "mods");
        createDummyJarFile(dir.resolve("m1.jar"),
                           "p/C1.class", "p/C2.class", "q/C1.class");

        ModuleFinder finder = ModuleFinder.of(dir);

        Configuration parent = Layer.boot().configuration();
        Configuration cf = resolve(parent, finder, "m1");

        ModuleDescriptor m1 = findDescriptor(cf, "m1");

        Set<String> exports
            = m1.exports().stream().map(Exports::source).collect(Collectors.toSet());

        assertTrue(exports.size() == 2);
        assertTrue(exports.contains("p"));
        assertTrue(exports.contains("q"));
        assertTrue(m1.conceals().isEmpty());
    }


    /**
     * Test class file in JAR file where the entry does not correspond to a
     * legal package name.
     */
    @Test(expectedExceptions = FindException.class)
    public void testBadPackage() throws IOException {
        Path dir = Files.createTempDirectory(USER_DIR, "mods");
        createDummyJarFile(dir.resolve("m1.jar"), "p-/T.class");

        // should throw FindException
        ModuleFinder.of(dir).findAll();
    }


    /**
     * Test JAR file with META-INF/services configuration file
     */
    public void testServicesConfiguration() throws IOException {
        String service = "p.S";
        String provider = "p.S1";

        Path tmpdir = Files.createTempDirectory(USER_DIR, "tmp");
        Path services = tmpdir.resolve("META-INF").resolve("services");
        Files.createDirectories(services);
        Files.write(services.resolve(service), Set.of(provider));
        Path dir = Files.createTempDirectory(USER_DIR, "mods");
        JarUtils.createJarFile(dir.resolve("m1.jar"), tmpdir);

        ModuleFinder finder = ModuleFinder.of(dir);

        Optional<ModuleReference> mref = finder.find("m1");
        assertTrue(mref.isPresent(), "m1 not found");

        ModuleDescriptor descriptor = mref.get().descriptor();
        assertTrue(descriptor.provides().size() == 1);
        assertTrue(descriptor.provides().containsKey(service));
        ModuleDescriptor.Provides provides = descriptor.provides().get(service);
        assertTrue(provides.providers().size() == 1);
        assertTrue(provides.providers().contains((provider)));
    }


    // META-INF/services configuration file/entries that are not legal
    @DataProvider(name = "badproviders")
    public Object[][] createProviders() {
        return new Object[][] {

                // service type         provider type

                { "p.S",                "-" },
                { "p.S",                ".S1" },
                { "p.S",                "S1." },

                { "-",                  "p.S1" },
                { ".S",                 "p.S1" },
        };
    }

    /**
     * Test JAR file with META-INF/services configuration file with bad
     * values or names.
     */
    @Test(dataProvider = "badproviders", expectedExceptions = FindException.class)
    public void testBadServicesConfiguration(String service, String provider)
        throws IOException
    {
        Path tmpdir = Files.createTempDirectory(USER_DIR, "tmp");
        Path services = tmpdir.resolve("META-INF").resolve("services");
        Files.createDirectories(services);
        Files.write(services.resolve(service), Set.of(provider));
        Path dir = Files.createTempDirectory(USER_DIR, "mods");
        JarUtils.createJarFile(dir.resolve("m1.jar"), tmpdir);

        // should throw FindException
        ModuleFinder.of(dir).findAll();
    }


    /**
     * Test that a JAR file with a Main-Class attribute results
     * in a module with a main class.
     */
    public void testMainClass() throws IOException {
        String mainClass = "p.Main";

        Manifest man = new Manifest();
        Attributes attrs = man.getMainAttributes();
        attrs.put(Attributes.Name.MANIFEST_VERSION, "1.0.0");
        attrs.put(Attributes.Name.MAIN_CLASS, mainClass);

        Path dir = Files.createTempDirectory(USER_DIR, "mods");
        createDummyJarFile(dir.resolve("m1.jar"), man);

        ModuleFinder finder = ModuleFinder.of(dir);

        Configuration parent = Layer.boot().configuration();
        Configuration cf = resolve(parent, finder, "m1");

        ModuleDescriptor m1 = findDescriptor(cf, "m1");

        assertTrue(m1.mainClass().isPresent());
        assertEquals(m1.mainClass().get(), mainClass);
    }


    // Main-Class files that do not map to a legal Java identifier
    @DataProvider(name = "badmainclass")
    public Object[][] createBadMainClass() {
        return new Object[][]{

            { "p-.Main",     null },
            { ".Main",       null }

        };
    }

    /**
     * Test that a JAR file with a Main-Class attribute that is not a valid
     * Java identifier
     */
    @Test(dataProvider = "badmainclass", expectedExceptions = FindException.class)
    public void testBadMainClass(String mainClass, String ignore) throws IOException {
        Manifest man = new Manifest();
        Attributes attrs = man.getMainAttributes();
        attrs.put(Attributes.Name.MANIFEST_VERSION, "1.0.0");
        attrs.put(Attributes.Name.MAIN_CLASS, mainClass);

        Path dir = Files.createTempDirectory(USER_DIR, "mods");
        createDummyJarFile(dir.resolve("m1.jar"), man);

        // should throw FindException
        ModuleFinder.of(dir).findAll();
    }


    /**
     * Basic test of a configuration created with automatic modules.
     *   m1 requires m2*
     *   m1 requires m3*
     *   m2*
     *   m3*
     */
    public void testConfiguration1() throws Exception {
        ModuleDescriptor descriptor1
            = new ModuleDescriptor.Builder("m1")
                .requires("m2")
                .requires("m3")
                .requires("java.base")
                .build();

        // m2 and m3 are automatic modules
        Path dir = Files.createTempDirectory(USER_DIR, "mods");
        createDummyJarFile(dir.resolve("m2.jar"), "p/T.class");
        createDummyJarFile(dir.resolve("m3.jar"), "q/T.class");

        // module finder locates m1 and the modules in the directory
        ModuleFinder finder
            = ModuleFinder.compose(ModuleUtils.finderOf(descriptor1),
                ModuleFinder.of(dir));

        Configuration parent = Layer.boot().configuration();
        Configuration cf = resolve(parent, finder, "m1");

        assertTrue(cf.modules().size() == 3);
        assertTrue(cf.findModule("m1").isPresent());
        assertTrue(cf.findModule("m2").isPresent());
        assertTrue(cf.findModule("m3").isPresent());

        ResolvedModule base = cf.findModule("java.base").get();
        assertTrue(base.configuration() == Layer.boot().configuration());
        ResolvedModule m1 = cf.findModule("m1").get();
        ResolvedModule m2 = cf.findModule("m2").get();
        ResolvedModule m3 = cf.findModule("m3").get();

        // m2 && m3 only require java.base
        assertTrue(m2.reference().descriptor().requires().size() == 1);
        assertTrue(m3.reference().descriptor().requires().size() == 1);

        // readability

        assertTrue(m1.reads().size() == 3);
        assertTrue(m1.reads().contains(base));
        assertTrue(m1.reads().contains(m2));
        assertTrue(m1.reads().contains(m3));

        assertTrue(m2.reads().contains(m1));
        assertTrue(m2.reads().contains(m3));
        testReadAllBootModules(cf, "m2");  // m2 reads all modules in boot layer

        assertTrue(m3.reads().contains(m1));
        assertTrue(m3.reads().contains(m2));
        testReadAllBootModules(cf, "m3");  // m3 reads all modules in boot layer

    }

    /**
     * Basic test of a configuration created with automatic modules
     *   m1 requires m2
     *   m2 requires m3*
     *   m3*
     *   m4*
     */
    public void testInConfiguration2() throws IOException {
        ModuleDescriptor descriptor1
            =  new ModuleDescriptor.Builder("m1")
                .requires("m2")
                .requires("java.base")
                .build();

        ModuleDescriptor descriptor2
            =  new ModuleDescriptor.Builder("m2")
                .requires("m3")
                .requires("java.base")
                .build();

        // m3 and m4 are automatic modules
        Path dir = Files.createTempDirectory(USER_DIR, "mods");
        createDummyJarFile(dir.resolve("m3.jar"), "p/T.class");
        createDummyJarFile(dir.resolve("m4.jar"), "q/T.class");

        // module finder locates m1 and the modules in the directory
        ModuleFinder finder
            = ModuleFinder.compose(ModuleUtils.finderOf(descriptor1, descriptor2),
                                   ModuleFinder.of(dir));

        Configuration parent = Layer.boot().configuration();
        Configuration cf = resolve(parent, finder, "m1", "m4");

        assertTrue(cf.modules().size() == 4);
        assertTrue(cf.findModule("m1").isPresent());
        assertTrue(cf.findModule("m2").isPresent());
        assertTrue(cf.findModule("m3").isPresent());
        assertTrue(cf.findModule("m4").isPresent());

        // m3 && m4 should only require java.base
        assertTrue(findDescriptor(cf, "m3").requires().size() == 1);
        assertTrue(findDescriptor(cf, "m4").requires().size() == 1);

        // readability

        ResolvedModule base = cf.findModule("java.base").get();
        assertTrue(base.configuration() == Layer.boot().configuration());
        ResolvedModule m1 = cf.findModule("m1").get();
        ResolvedModule m2 = cf.findModule("m2").get();
        ResolvedModule m3 = cf.findModule("m3").get();
        ResolvedModule m4 = cf.findModule("m4").get();

        assertTrue(m1.reads().size() == 2);
        assertTrue(m1.reads().contains(m2));
        assertTrue(m1.reads().contains(base));

        assertTrue(m2.reads().size() == 3);
        assertTrue(m2.reads().contains(m3));
        assertTrue(m2.reads().contains(m4));
        assertTrue(m2.reads().contains(base));

        assertTrue(m3.reads().contains(m1));
        assertTrue(m3.reads().contains(m2));
        assertTrue(m3.reads().contains(m4));
        testReadAllBootModules(cf, "m3");   // m3 reads all modules in boot layer

        assertTrue(m4.reads().contains(m1));
        assertTrue(m4.reads().contains(m2));
        assertTrue(m4.reads().contains(m3));
        testReadAllBootModules(cf, "m4");    // m4 reads all modules in boot layer
    }


    /**
     * Basic test of a configuration created with automatic modules
     *   m1 requires m2
     *   m2 requires public m3*
     *   m3*
     *   m4*
     */
    public void testInConfiguration3() throws IOException {
        ModuleDescriptor descriptor1
            =  new ModuleDescriptor.Builder("m1")
                .requires("m2")
                .requires("java.base")
                .build();

        ModuleDescriptor descriptor2
            =  new ModuleDescriptor.Builder("m2")
                .requires(Modifier.PUBLIC, "m3")
                .requires("java.base")
                .build();

        // m3 and m4 are automatic modules
        Path dir = Files.createTempDirectory(USER_DIR, "mods");
        createDummyJarFile(dir.resolve("m3.jar"), "p/T.class");
        createDummyJarFile(dir.resolve("m4.jar"), "q/T.class");

        // module finder locates m1 and the modules in the directory
        ModuleFinder finder
            = ModuleFinder.compose(ModuleUtils.finderOf(descriptor1, descriptor2),
                ModuleFinder.of(dir));

        Configuration parent = Layer.boot().configuration();
        Configuration cf = resolve(parent, finder, "m1", "m4");

        assertTrue(cf.modules().size() == 4);
        assertTrue(cf.findModule("m1").isPresent());
        assertTrue(cf.findModule("m2").isPresent());
        assertTrue(cf.findModule("m3").isPresent());
        assertTrue(cf.findModule("m4").isPresent());

        ResolvedModule base = cf.findModule("java.base").get();
        assertTrue(base.configuration() == Layer.boot().configuration());
        ResolvedModule m1 = cf.findModule("m1").get();
        ResolvedModule m2 = cf.findModule("m2").get();
        ResolvedModule m3 = cf.findModule("m3").get();
        ResolvedModule m4 = cf.findModule("m4").get();

        // m3 && m4 should only require java.base
        assertTrue(findDescriptor(cf, "m3").requires().size() == 1);
        assertTrue(findDescriptor(cf, "m4").requires().size() == 1);

        // readability

        assertTrue(m1.reads().size() == 4);
        assertTrue(m1.reads().contains(m2));
        assertTrue(m1.reads().contains(m3));
        assertTrue(m1.reads().contains(m4));
        assertTrue(m1.reads().contains(base));

        assertTrue(m2.reads().size() == 3);
        assertTrue(m2.reads().contains(m3));
        assertTrue(m2.reads().contains(m4));
        assertTrue(m2.reads().contains(base));

        assertTrue(reads(cf, "m2", "m3"));
        assertTrue(reads(cf, "m2", "m4"));
        assertTrue(reads(cf, "m2", "java.base"));

        assertTrue(m3.reads().contains(m1));
        assertTrue(m3.reads().contains(m2));
        assertTrue(m3.reads().contains(m4));
        testReadAllBootModules(cf, "m3");   // m3 reads all modules in boot layer

        assertTrue(m4.reads().contains(m1));
        assertTrue(m4.reads().contains(m2));
        assertTrue(m4.reads().contains(m3));
        testReadAllBootModules(cf, "m4");    // m4 reads all modules in boot layer
    }


    /**
     * Basic test of Layer containing automatic modules
     */
    public void testInLayer() throws IOException {
        ModuleDescriptor descriptor
            =  new ModuleDescriptor.Builder("m1")
                .requires("m2")
                .requires("m3")
                .build();

        // m2 and m3 are simple JAR files
        Path dir = Files.createTempDirectory(USER_DIR, "mods");
        createDummyJarFile(dir.resolve("m2.jar"), "p/T.class");
        createDummyJarFile(dir.resolve("m3.jar"), "q/T2.class");

        // module finder locates m1 and the modules in the directory
        ModuleFinder finder
            = ModuleFinder.compose(ModuleUtils.finderOf(descriptor),
                ModuleFinder.of(dir));

        Configuration parent = Layer.boot().configuration();
        Configuration cf = resolve(parent, finder, "m1");
        assertTrue(cf.modules().size() == 3);

        // each module gets its own loader
        Layer layer = Layer.boot().defineModules(cf, mn -> new ClassLoader() { });

        // an unnamed module
        Module unnamed = (new ClassLoader() { }).getUnnamedModule();

        Module m2 = layer.findModule("m2").get();
        assertTrue(m2.isNamed());
        assertTrue(m2.canRead(unnamed));
        testsReadsAll(m2, layer);

        Module m3 = layer.findModule("m3").get();
        assertTrue(m3.isNamed());
        assertTrue(m2.canRead(unnamed));
        testsReadsAll(m3, layer);
    }


    /**
     * Test miscellaneous methods.
     */
    public void testMisc() throws IOException {
        Path dir = Files.createTempDirectory(USER_DIR, "mods");
        Path m1_jar = createDummyJarFile(dir.resolve("m1.jar"), "p/T.class");

        ModuleFinder finder = ModuleFinder.of(m1_jar);

        assertTrue(finder.find("m1").isPresent());
        ModuleDescriptor m1 = finder.find("m1").get().descriptor();

        // test miscellaneous methods
        assertTrue(m1.isAutomatic());
        assertFalse(m1.isSynthetic());
        assertFalse(m1.osName().isPresent());
        assertFalse(m1.osArch().isPresent());
        assertFalse(m1.osVersion().isPresent());
    }


    /**
     * Invokes parent.resolveRequires to resolve the given root modules.
     */
    static Configuration resolve(Configuration parent,
                                 ModuleFinder finder,
                                 String... roots) {
        return parent.resolveRequires(finder, ModuleFinder.of(), Set.of(roots));
    }

    /**
     * Finds a module in the given configuration or its parents, returning
     * the module descriptor (or null if not found)
     */
    static ModuleDescriptor findDescriptor(Configuration cf, String name) {
        Optional<ResolvedModule> om = cf.findModule(name);
        if (om.isPresent()) {
            return om.get().reference().descriptor();
        } else {
            return null;
        }
    }

    /**
     * Test that a module in a configuration reads all modules in the boot
     * configuration.
     */
    static void testReadAllBootModules(Configuration cf, String mn) {

        Set<String> bootModules = Layer.boot().modules().stream()
                .map(Module::getName)
                .collect(Collectors.toSet());

        bootModules.forEach(other -> assertTrue(reads(cf, mn, other)));

    }

    /**
     * Test that the given Module reads all module in the given Layer
     * and its parent Layers.
     */
    static void testsReadsAll(Module m, Layer layer) {
        while (layer != Layer.empty()) {

            // check that m reads all module in the layer
            layer.configuration().modules().stream()
                .map(ResolvedModule::name)
                .map(layer::findModule)
                .map(Optional::get)
                .forEach(m2 -> assertTrue(m.canRead(m2)));

            layer = layer.parent().get();
        }
    }

    /**
     * Returns {@code true} if the configuration contains module mn1
     * that reads module mn2.
     */
    static boolean reads(Configuration cf, String mn1, String mn2) {
        Optional<ResolvedModule> om1 = cf.findModule(mn1);
        if (!om1.isPresent())
            return false;

        return om1.get().reads().stream()
                .map(ResolvedModule::name)
                .anyMatch(mn2::equals);
    }

    /**
     * Creates a JAR file, optionally with a manifest, and with the given
     * entries. The entries will be empty in the resulting JAR file.
     */
    static Path createDummyJarFile(Path jarfile, Manifest man, String... entries)
        throws IOException
    {
        Path dir = Files.createTempDirectory(USER_DIR, "tmp");

        for (String entry : entries) {
            Path file = dir.resolve(entry);
            Path parent = file.getParent();
            if (parent != null)
                Files.createDirectories(parent);
            Files.createFile(file);
        }

        Path[] paths = Stream.of(entries).map(Paths::get).toArray(Path[]::new);
        JarUtils.createJarFile(jarfile, man, dir, paths);
        return jarfile;
    }

    /**
     * Creates a JAR file and with the given entries. The entries will be empty
     * in the resulting JAR file.
     */
    static Path createDummyJarFile(Path jarfile, String... entries)
        throws IOException
    {
        return createDummyJarFile(jarfile, null, entries);
    }

}
