/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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
import java.lang.module.ModuleDescriptor.Requires.Modifier;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.lang.module.ResolutionException;
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
            { "foo4j.jar",                  "foo4j", },

            { "foo1.jar",                   "foo" },
            { "foo1.2.jar",                 "foo" },
            { "foo1.2.3.jar",               "foo" },

            { "foo10.jar",                  "foo" },
            { "foo10.20.jar",               "foo" },
            { "foo10.20.30.jar",            "foo" },

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
        assertTrue(descriptor.isAutomatic());
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
        createDummyJarFile(dir.resolve("m.jar"),
                           "p/C1.class", "p/C2.class", "q/C1.class");

        ModuleFinder finder = ModuleFinder.of(dir);
        Optional<ModuleReference> mref = finder.find("m");
        assertTrue(mref.isPresent(), "m not found");

        ModuleDescriptor descriptor = mref.get().descriptor();
        assertTrue(descriptor.isAutomatic());

        assertTrue(descriptor.packages().size() == 2);
        assertTrue(descriptor.packages().contains("p"));
        assertTrue(descriptor.packages().contains("q"));

        assertTrue(descriptor.exports().isEmpty());
        assertTrue(descriptor.opens().isEmpty());
    }

    /**
     * Test class files in JAR file where the entry does not correspond to a
     * legal package name.
     */
    public void testBadPackage() throws IOException {
        Path dir = Files.createTempDirectory(USER_DIR, "mods");
        createDummyJarFile(dir.resolve("m.jar"), "p/C1.class", "p-/C2.class");

        ModuleFinder finder = ModuleFinder.of(dir);
        Optional<ModuleReference> mref = finder.find("m");
        assertTrue(mref.isPresent(), "m not found");

        ModuleDescriptor descriptor = mref.get().descriptor();
        assertTrue(descriptor.isAutomatic());

        assertTrue(descriptor.packages().size() == 1);
        assertTrue(descriptor.packages().contains("p"));

        assertTrue(descriptor.exports().isEmpty());
        assertTrue(descriptor.opens().isEmpty());
    }

    /**
     * Test non-class resources in a JAR file.
     */
    public void testNonClassResources() throws IOException {
        Path dir = Files.createTempDirectory(USER_DIR, "mods");
        createDummyJarFile(dir.resolve("m.jar"),
                "LICENSE",
                "README",
                "WEB-INF/tags",
                "p/Type.class",
                "p/resources/m.properties");

        ModuleFinder finder = ModuleFinder.of(dir);
        Optional<ModuleReference> mref = finder.find("m");
        assertTrue(mref.isPresent(), "m not found");

        ModuleDescriptor descriptor = mref.get().descriptor();
        assertTrue(descriptor.isAutomatic());

        assertTrue(descriptor.packages().size() == 1);
        assertTrue(descriptor.packages().contains("p"));
    }

    /**
     * Test .class file in unnamed package (top-level directory)
     */
    @Test(expectedExceptions = FindException.class)
    public void testClassInUnnamedPackage() throws IOException {
        Path dir = Files.createTempDirectory(USER_DIR, "mods");
        createDummyJarFile(dir.resolve("m.jar"), "Mojo.class");
        ModuleFinder finder = ModuleFinder.of(dir);
        finder.findAll();
    }

    /**
     * Test JAR file with META-INF/services configuration file
     */
    public void testServicesConfiguration() throws IOException {
        String service = "p.S";
        String provider = "p.S1";

        Path tmpdir = Files.createTempDirectory(USER_DIR, "tmp");

        // provider class
        Path providerClass = tmpdir.resolve(provider.replace('.', '/') + ".class");
        Files.createDirectories(providerClass.getParent());
        Files.createFile(providerClass);

        // services configuration file
        Path services = tmpdir.resolve("META-INF").resolve("services");
        Files.createDirectories(services);
        Files.write(services.resolve(service), Set.of(provider));

        Path dir = Files.createTempDirectory(USER_DIR, "mods");
        JarUtils.createJarFile(dir.resolve("m.jar"), tmpdir);

        ModuleFinder finder = ModuleFinder.of(dir);

        Optional<ModuleReference> mref = finder.find("m");
        assertTrue(mref.isPresent(), "m not found");

        ModuleDescriptor descriptor = mref.get().descriptor();
        assertTrue(descriptor.provides().size() == 1);
        ModuleDescriptor.Provides provides = descriptor.provides().iterator().next();
        assertEquals(provides.service(), service);
        assertTrue(provides.providers().size() == 1);
        assertTrue(provides.providers().contains((provider)));
    }


    // META-INF/services files that don't map to legal service names
    @DataProvider(name = "badservices")
    public Object[][] createBadServices() {
        return new Object[][] {

                // service type         provider type
                { "-",                  "p.S1" },
                { ".S",                 "p.S1" },
        };
    }

    /**
     * Test JAR file with META-INF/services configuration file with bad
     * values or names.
     */
    @Test(dataProvider = "badservices")
    public void testBadServicesNames(String service, String provider)
        throws IOException
    {
        Path tmpdir = Files.createTempDirectory(USER_DIR, "tmp");
        Path services = tmpdir.resolve("META-INF").resolve("services");
        Files.createDirectories(services);
        Files.write(services.resolve(service), Set.of(provider));
        Path dir = Files.createTempDirectory(USER_DIR, "mods");
        JarUtils.createJarFile(dir.resolve("m.jar"), tmpdir);

        Optional<ModuleReference> omref = ModuleFinder.of(dir).find("m");
        assertTrue(omref.isPresent());
        ModuleDescriptor descriptor = omref.get().descriptor();
        assertTrue(descriptor.provides().isEmpty());
    }


    // META-INF/services configuration file entries that are not legal
    @DataProvider(name = "badproviders")
    public Object[][] createBadProviders() {
        return new Object[][] {

                // service type         provider type
                { "p.S",                "-" },
                { "p.S",                "p..S1" },
                { "p.S",                "S1." },
        };
    }

    /**
     * Test JAR file with META-INF/services configuration file with bad
     * values or names.
     */
    @Test(dataProvider = "badproviders", expectedExceptions = FindException.class)
    public void testBadProviderNames(String service, String provider)
        throws IOException
    {
        Path tmpdir = Files.createTempDirectory(USER_DIR, "tmp");

        // provider class
        Path providerClass = tmpdir.resolve(provider.replace('.', '/') + ".class");
        Files.createDirectories(providerClass.getParent());
        Files.createFile(providerClass);

        // services configuration file
        Path services = tmpdir.resolve("META-INF").resolve("services");
        Files.createDirectories(services);
        Files.write(services.resolve(service), Set.of(provider));

        Path dir = Files.createTempDirectory(USER_DIR, "mods");
        JarUtils.createJarFile(dir.resolve("m.jar"), tmpdir);

        // should throw FindException
        ModuleFinder.of(dir).findAll();
    }

    /**
     * Test JAR file with META-INF/services configuration file listing a
     * provider that is not in the module.
     */
    @Test(expectedExceptions = FindException.class)
    public void testMissingProviderPackage() throws IOException {
        Path tmpdir = Files.createTempDirectory(USER_DIR, "tmp");

        // services configuration file
        Path services = tmpdir.resolve("META-INF").resolve("services");
        Files.createDirectories(services);
        Files.write(services.resolve("p.S"), Set.of("q.P"));

        Path dir = Files.createTempDirectory(USER_DIR, "mods");
        JarUtils.createJarFile(dir.resolve("m.jar"), tmpdir);

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
        String entry = mainClass.replace('.', '/') + ".class";
        createDummyJarFile(dir.resolve("m.jar"), man, entry);

        ModuleFinder finder = ModuleFinder.of(dir);

        Configuration parent = Layer.boot().configuration();
        Configuration cf = resolve(parent, finder, "m");

        ModuleDescriptor descriptor = findDescriptor(cf, "m");

        assertTrue(descriptor.mainClass().isPresent());
        assertEquals(descriptor.mainClass().get(), mainClass);
    }


    // Main-Class files that do not map to a legal qualified type name
    @DataProvider(name = "badmainclass")
    public Object[][] createBadMainClass() {
        return new Object[][]{

            { "Main",        null },
            { "p..Main",     null },
            { "p-.Main",     null },

        };
    }

    /**
     * Test that a JAR file with a Main-Class attribute that is not a qualified
     * type name.
     */
    @Test(dataProvider = "badmainclass", expectedExceptions = FindException.class)
    public void testBadMainClass(String mainClass, String ignore) throws IOException {
        Manifest man = new Manifest();
        Attributes attrs = man.getMainAttributes();
        attrs.put(Attributes.Name.MANIFEST_VERSION, "1.0.0");
        attrs.put(Attributes.Name.MAIN_CLASS, mainClass);

        Path dir = Files.createTempDirectory(USER_DIR, "mods");
        String entry = mainClass.replace('.', '/') + ".class";
        createDummyJarFile(dir.resolve("m.jar"), man, entry);

        // should throw FindException
        ModuleFinder.of(dir).findAll();
    }

    /**
     * Test that a JAR file with a Main-Class attribute that is not in the module
     */
    @Test(expectedExceptions = FindException.class)
    public void testMissingMainClassPackage() throws IOException {
        Manifest man = new Manifest();
        Attributes attrs = man.getMainAttributes();
        attrs.put(Attributes.Name.MANIFEST_VERSION, "1.0.0");
        attrs.put(Attributes.Name.MAIN_CLASS, "p.Main");

        Path dir = Files.createTempDirectory(USER_DIR, "mods");
        createDummyJarFile(dir.resolve("m.jar"), man);

        // should throw FindException
        ModuleFinder.of(dir).findAll();
    }


    /**
     * Basic test of a configuration created with automatic modules.
     *   a requires b*
     *   a requires c*
     *   b*
     *   c*
     */
    public void testConfiguration1() throws Exception {
        ModuleDescriptor descriptor1
            = ModuleDescriptor.newModule("a")
                .requires("b")
                .requires("c")
                .requires("java.base")
                .build();

        // b and c are automatic modules
        Path dir = Files.createTempDirectory(USER_DIR, "mods");
        createDummyJarFile(dir.resolve("b.jar"), "p/T.class");
        createDummyJarFile(dir.resolve("c.jar"), "q/T.class");

        // module finder locates a and the modules in the directory
        ModuleFinder finder
            = ModuleFinder.compose(ModuleUtils.finderOf(descriptor1),
                ModuleFinder.of(dir));

        Configuration parent = Layer.boot().configuration();
        Configuration cf = resolve(parent, finder, "a");

        assertTrue(cf.modules().size() == 3);
        assertTrue(cf.findModule("a").isPresent());
        assertTrue(cf.findModule("b").isPresent());
        assertTrue(cf.findModule("c").isPresent());

        ResolvedModule base = cf.findModule("java.base").get();
        assertTrue(base.configuration() == Layer.boot().configuration());
        ResolvedModule a = cf.findModule("a").get();
        ResolvedModule b = cf.findModule("b").get();
        ResolvedModule c = cf.findModule("c").get();

        // b && c only require java.base
        assertTrue(b.reference().descriptor().requires().size() == 1);
        assertTrue(c.reference().descriptor().requires().size() == 1);

        // readability

        assertTrue(a.reads().size() == 3);
        assertTrue(a.reads().contains(base));
        assertTrue(a.reads().contains(b));
        assertTrue(a.reads().contains(c));

        assertTrue(b.reads().contains(a));
        assertTrue(b.reads().contains(c));
        testReadAllBootModules(cf, "b");  // b reads all modules in boot layer

        assertTrue(c.reads().contains(a));
        assertTrue(c.reads().contains(b));
        testReadAllBootModules(cf, "c");  // c reads all modules in boot layer

    }

    /**
     * Basic test of a configuration created with automatic modules
     *   a requires b
     *   b requires c*
     *   c*
     *   d*
     */
    public void testInConfiguration2() throws IOException {
        ModuleDescriptor descriptor1
            = ModuleDescriptor.newModule("a")
                .requires("b")
                .requires("java.base")
                .build();

        ModuleDescriptor descriptor2
            = ModuleDescriptor.newModule("b")
                .requires("c")
                .requires("java.base")
                .build();

        // c and d are automatic modules
        Path dir = Files.createTempDirectory(USER_DIR, "mods");
        createDummyJarFile(dir.resolve("c.jar"), "p/T.class");
        createDummyJarFile(dir.resolve("d.jar"), "q/T.class");

        // module finder locates a and the modules in the directory
        ModuleFinder finder
            = ModuleFinder.compose(ModuleUtils.finderOf(descriptor1, descriptor2),
                                   ModuleFinder.of(dir));

        Configuration parent = Layer.boot().configuration();
        Configuration cf = resolve(parent, finder, "a", "d");

        assertTrue(cf.modules().size() == 4);
        assertTrue(cf.findModule("a").isPresent());
        assertTrue(cf.findModule("b").isPresent());
        assertTrue(cf.findModule("c").isPresent());
        assertTrue(cf.findModule("d").isPresent());

        // c && d should only require java.base
        assertTrue(findDescriptor(cf, "c").requires().size() == 1);
        assertTrue(findDescriptor(cf, "d").requires().size() == 1);

        // readability

        ResolvedModule base = cf.findModule("java.base").get();
        assertTrue(base.configuration() == Layer.boot().configuration());
        ResolvedModule a = cf.findModule("a").get();
        ResolvedModule b = cf.findModule("b").get();
        ResolvedModule c = cf.findModule("c").get();
        ResolvedModule d = cf.findModule("d").get();

        assertTrue(a.reads().size() == 2);
        assertTrue(a.reads().contains(b));
        assertTrue(a.reads().contains(base));

        assertTrue(b.reads().size() == 3);
        assertTrue(b.reads().contains(c));
        assertTrue(b.reads().contains(d));
        assertTrue(b.reads().contains(base));

        assertTrue(c.reads().contains(a));
        assertTrue(c.reads().contains(b));
        assertTrue(c.reads().contains(d));
        testReadAllBootModules(cf, "c");   // c reads all modules in boot layer

        assertTrue(d.reads().contains(a));
        assertTrue(d.reads().contains(b));
        assertTrue(d.reads().contains(c));
        testReadAllBootModules(cf, "d");    // d reads all modules in boot layer
    }


    /**
     * Basic test of a configuration created with automatic modules
     *   a requires b
     *   b requires transitive c*
     *   c*
     *   d*
     */
    public void testInConfiguration3() throws IOException {
        ModuleDescriptor descriptor1
            = ModuleDescriptor.newModule("a")
                .requires("b")
                .requires("java.base")
                .build();

        ModuleDescriptor descriptor2
            = ModuleDescriptor.newModule("b")
                .requires(Set.of(Modifier.TRANSITIVE), "c")
                .requires("java.base")
                .build();

        // c and d are automatic modules
        Path dir = Files.createTempDirectory(USER_DIR, "mods");
        createDummyJarFile(dir.resolve("c.jar"), "p/T.class");
        createDummyJarFile(dir.resolve("d.jar"), "q/T.class");

        // module finder locates a and the modules in the directory
        ModuleFinder finder
            = ModuleFinder.compose(ModuleUtils.finderOf(descriptor1, descriptor2),
                ModuleFinder.of(dir));

        Configuration parent = Layer.boot().configuration();
        Configuration cf = resolve(parent, finder, "a", "d");

        assertTrue(cf.modules().size() == 4);
        assertTrue(cf.findModule("a").isPresent());
        assertTrue(cf.findModule("b").isPresent());
        assertTrue(cf.findModule("c").isPresent());
        assertTrue(cf.findModule("d").isPresent());

        ResolvedModule base = cf.findModule("java.base").get();
        assertTrue(base.configuration() == Layer.boot().configuration());
        ResolvedModule a = cf.findModule("a").get();
        ResolvedModule b = cf.findModule("b").get();
        ResolvedModule c = cf.findModule("c").get();
        ResolvedModule d = cf.findModule("d").get();

        // c && d should only require java.base
        assertTrue(findDescriptor(cf, "c").requires().size() == 1);
        assertTrue(findDescriptor(cf, "d").requires().size() == 1);

        // readability

        assertTrue(a.reads().size() == 4);
        assertTrue(a.reads().contains(b));
        assertTrue(a.reads().contains(c));
        assertTrue(a.reads().contains(d));
        assertTrue(a.reads().contains(base));

        assertTrue(b.reads().size() == 3);
        assertTrue(b.reads().contains(c));
        assertTrue(b.reads().contains(d));
        assertTrue(b.reads().contains(base));

        assertTrue(reads(cf, "b", "c"));
        assertTrue(reads(cf, "b", "d"));
        assertTrue(reads(cf, "b", "java.base"));

        assertTrue(c.reads().contains(a));
        assertTrue(c.reads().contains(b));
        assertTrue(c.reads().contains(d));
        testReadAllBootModules(cf, "c");   // c reads all modules in boot layer

        assertTrue(d.reads().contains(a));
        assertTrue(d.reads().contains(b));
        assertTrue(d.reads().contains(c));
        testReadAllBootModules(cf, "d");    // d reads all modules in boot layer
    }


    /**
     * Basic test of a configuration created with automatic modules
     *   a requires b* and c*
     *   b* contains p
     *   c* contains p
     */
    @Test(expectedExceptions = { ResolutionException.class })
    public void testDuplicateSuppliers1() throws IOException {
        ModuleDescriptor descriptor
            = ModuleDescriptor.newModule("a")
                .requires("b")
                .requires("c")
                .build();

        // c and d are automatic modules with the same package
        Path dir = Files.createTempDirectory(USER_DIR, "mods");
        createDummyJarFile(dir.resolve("b.jar"), "p/T.class");
        createDummyJarFile(dir.resolve("c.jar"), "p/T.class");

        // module finder locates 'a' and the modules in the directory
        ModuleFinder finder
            = ModuleFinder.compose(ModuleUtils.finderOf(descriptor),
                                   ModuleFinder.of(dir));

        Configuration parent = Layer.boot().configuration();
        resolve(parent, finder, "a");
    }


    /**
     * Basic test of a configuration created with automatic modules
     *   a contains p, requires b*
     *   b* contains p
     */
    @Test(expectedExceptions = { ResolutionException.class })
    public void testDuplicateSuppliers2() throws IOException {
        ModuleDescriptor descriptor
            = ModuleDescriptor.newModule("a")
                .packages(Set.of("p"))
                .requires("b")
                .build();

        // c and d are automatic modules with the same package
        Path dir = Files.createTempDirectory(USER_DIR, "mods");
        createDummyJarFile(dir.resolve("b.jar"), "p/T.class");

        // module finder locates 'a' and the modules in the directory
        ModuleFinder finder
            = ModuleFinder.compose(ModuleUtils.finderOf(descriptor),
                                   ModuleFinder.of(dir));

        Configuration parent = Layer.boot().configuration();
        resolve(parent, finder, "a");
    }


    /**
     * Basic test of Layer containing automatic modules
     */
    public void testInLayer() throws IOException {
        ModuleDescriptor descriptor
            = ModuleDescriptor.newModule("a")
                .requires("b")
                .requires("c")
                .build();

        // b and c are simple JAR files
        Path dir = Files.createTempDirectory(USER_DIR, "mods");
        createDummyJarFile(dir.resolve("b.jar"), "p/T.class");
        createDummyJarFile(dir.resolve("c.jar"), "q/T2.class");

        // module finder locates a and the modules in the directory
        ModuleFinder finder
            = ModuleFinder.compose(ModuleUtils.finderOf(descriptor),
                ModuleFinder.of(dir));

        Configuration parent = Layer.boot().configuration();
        Configuration cf = resolve(parent, finder, "a");
        assertTrue(cf.modules().size() == 3);

        // each module gets its own loader
        Layer layer = Layer.boot().defineModules(cf, mn -> new ClassLoader() { });

        // an unnamed module
        Module unnamed = (new ClassLoader() { }).getUnnamedModule();

        Module b = layer.findModule("b").get();
        assertTrue(b.isNamed());
        assertTrue(b.canRead(unnamed));
        testsReadsAll(b, layer);

        Module c = layer.findModule("c").get();
        assertTrue(c.isNamed());
        assertTrue(b.canRead(unnamed));
        testsReadsAll(c, layer);
    }


    /**
     * Test miscellaneous methods.
     */
    public void testMisc() throws IOException {
        Path dir = Files.createTempDirectory(USER_DIR, "mods");
        Path m_jar = createDummyJarFile(dir.resolve("m.jar"), "p/T.class");

        ModuleFinder finder = ModuleFinder.of(m_jar);

        assertTrue(finder.find("m").isPresent());
        ModuleDescriptor m = finder.find("m").get().descriptor();

        // test miscellaneous methods
        assertTrue(m.isAutomatic());
        assertFalse(m.modifiers().contains(ModuleDescriptor.Modifier.SYNTHETIC));
        assertFalse(m.osName().isPresent());
        assertFalse(m.osArch().isPresent());
        assertFalse(m.osVersion().isPresent());
    }


    /**
     * Invokes parent.resolve to resolve the given root modules.
     */
    static Configuration resolve(Configuration parent,
                                 ModuleFinder finder,
                                 String... roots) {
        return parent.resolve(finder, ModuleFinder.of(), Set.of(roots));
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
        // check that m reads all modules in the layer
        layer.configuration().modules().stream()
            .map(ResolvedModule::name)
            .map(layer::findModule)
            .map(Optional::get)
            .forEach(other -> assertTrue(m.canRead(other)));

        // also check parent layers
        layer.parents().forEach(l -> testsReadsAll(m, l));
    }

    /**
     * Returns {@code true} if the configuration contains module mn1
     * that reads module mn2.
     */
    static boolean reads(Configuration cf, String mn1, String mn2) {
        Optional<ResolvedModule> om = cf.findModule(mn1);
        if (!om.isPresent())
            return false;

        return om.get().reads().stream()
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
