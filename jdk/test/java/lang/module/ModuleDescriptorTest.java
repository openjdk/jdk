/*
 * Copyright (c) 2013, 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @run testng ModuleDescriptorTest
 * @summary Basic test for java.lang.module.ModuleDescriptor and its builder
 */

import java.io.IOException;
import java.io.InputStream;
import java.lang.module.InvalidModuleDescriptorException;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleDescriptor.Builder;
import java.lang.module.ModuleDescriptor.Exports;
import java.lang.module.ModuleDescriptor.Requires;
import java.lang.module.ModuleDescriptor.Provides;
import java.lang.module.ModuleDescriptor.Requires.Modifier;
import java.lang.module.ModuleDescriptor.Version;
import java.lang.reflect.Module;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static java.lang.module.ModuleDescriptor.Requires.Modifier.*;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

@Test
public class ModuleDescriptorTest {

    @DataProvider(name = "invalidjavaidentifiers")
    public Object[][] invalidJavaIdentifiers() {
        return new Object[][]{

            { null,         null },
            { ".foo",       null },
            { "foo.",       null },
            { "[foo]",      null },

        };
    }


    // requires

    private Requires requires(Set<Modifier> mods, String mn) {
        return new Builder("m")
            .requires(mods, mn)
            .build()
            .requires()
            .iterator()
            .next();
    }

    public void testRequiresWithRequires() {
        Requires r1 = requires(null, "foo");
        ModuleDescriptor descriptor = new Builder("m").requires(r1).build();
        Requires r2 = descriptor.requires().iterator().next();
        assertEquals(r1, r2);
    }

    public void testRequiresWithNullModifiers() {
        Requires r = requires(null, "foo");
        assertEquals(r, r);
        assertTrue(r.compareTo(r) == 0);
        assertTrue(r.modifiers().isEmpty());
        assertEquals(r.name(), "foo");
    }

    public void testRequiresWithNoModifiers() {
        Requires r = requires(EnumSet.noneOf(Requires.Modifier.class), "foo");
        assertEquals(r, r);
        assertTrue(r.compareTo(r) == 0);
        assertTrue(r.modifiers().isEmpty());
        assertEquals(r.name(), "foo");
    }

    public void testRequiresWithOneModifier() {
        Requires r = requires(EnumSet.of(PUBLIC), "foo");
        assertEquals(r, r);
        assertTrue(r.compareTo(r) == 0);
        assertEquals(r.modifiers(), EnumSet.of(PUBLIC));
        assertEquals(r.name(), "foo");
    }

    public void testRequiresWithTwoModifiers() {
        Requires r = requires(EnumSet.of(PUBLIC, SYNTHETIC), "foo");
        assertEquals(r, r);
        assertTrue(r.compareTo(r) == 0);
        assertEquals(r.modifiers(), EnumSet.of(PUBLIC, SYNTHETIC));
        assertEquals(r.name(), "foo");
    }

    public void testRequiresWithAllModifiers() {
        Requires r = requires(EnumSet.allOf(Modifier.class), "foo");
        assertEquals(r, r);
        assertTrue(r.compareTo(r) == 0);
        assertEquals(r.modifiers(), EnumSet.allOf(Modifier.class));
        assertEquals(r.name(), "foo");
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void testRequiresWithDuplicatesRequires() {
        Requires r = requires(null, "foo");
        new Builder("m").requires(r).requires(r);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testRequiresSelfWithRequires() {
        Requires r = requires(null, "m");
        new Builder("m").requires(r);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testRequiresSelfWithNoModifier() {
        new Builder("m").requires("m");
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testRequiresSelfWithOneModifier() {
        new Builder("m").requires(PUBLIC, "m");
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testRequiresSelfWithAllModifiers() {
        new Builder("m").requires(EnumSet.allOf(Modifier.class), "m");
    }

    @Test(dataProvider = "invalidjavaidentifiers",
          expectedExceptions = IllegalArgumentException.class )
    public void testRequiresWithBadModuleName(String mn, String ignore) {
        requires(EnumSet.noneOf(Modifier.class), mn);
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testRequiresWithNullRequires() {
        new Builder("m").requires((Requires) null);
    }

    public void testRequiresCompare() {
        Requires r1 = requires(EnumSet.noneOf(Modifier.class), "foo");
        Requires r2 = requires(EnumSet.noneOf(Modifier.class), "bar");
        int n = "foo".compareTo("bar");
        assertTrue(r1.compareTo(r2) == n);
        assertTrue(r2.compareTo(r1) == -n);
    }

    public void testRequiresToString() {
        Requires r = requires(EnumSet.noneOf(Modifier.class), "foo");
        assertTrue(r.toString().contains("foo"));
    }


    // exports

    private Exports exports(String pn) {
        return new Builder("foo")
            .exports(pn)
            .build()
            .exports()
            .iterator()
            .next();
    }

    private Exports exports(String pn, String target) {
        return new Builder("foo")
            .exports(pn, target)
            .build()
            .exports()
            .iterator()
            .next();
    }

    public void testExportsExports() {
        Exports e1 = exports("p");
        ModuleDescriptor descriptor = new Builder("m").exports(e1).build();
        Exports e2 = descriptor.exports().iterator().next();
        assertEquals(e1, e2);
    }

    public void testExportsToAll() {
        Exports e = exports("p");
        assertEquals(e, e);
        assertEquals(e.source(), "p");
        assertFalse(e.isQualified());
        assertTrue(e.targets().isEmpty());
    }

    public void testExportsToTarget() {
        Exports e = exports("p", "bar");
        assertEquals(e, e);
        assertEquals(e.source(), "p");
        assertTrue(e.isQualified());
        assertTrue(e.targets().size() == 1);
        assertTrue(e.targets().contains("bar"));
    }

    public void testExportsToTargets() {
        Set<String> targets = new HashSet<>();
        targets.add("bar");
        targets.add("gus");
        Exports e
            = new Builder("foo")
                .exports("p", targets)
                .build()
                .exports()
                .iterator()
                .next();
        assertEquals(e, e);
        assertEquals(e.source(), "p");
        assertTrue(e.isQualified());
        assertTrue(e.targets().size() == 2);
        assertTrue(e.targets().contains("bar"));
        assertTrue(e.targets().contains("gus"));
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void testExportsWithDuplicate1() {
        Exports e = exports("p");
        new Builder("foo").exports(e).exports(e);
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void testExportsWithDuplicate2() {
        new Builder("foo").exports("p").exports("p");
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void testExportsWithConcealedPackage() {
        new Builder("foo").conceals("p").exports("p");
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void testExportsToTargetWithConcealedPackage() {
        new Builder("foo").conceals("p").exports("p", "bar");
    }

    @Test(expectedExceptions = IllegalArgumentException.class )
    public void testExportsWithEmptySet() {
        new Builder("foo").exports("p", Collections.emptySet());
    }

    @Test(dataProvider = "invalidjavaidentifiers",
          expectedExceptions = IllegalArgumentException.class )
    public void testExportsWithBadName(String pn, String ignore) {
        new Builder("foo").exports(pn);
    }

    @Test(expectedExceptions = NullPointerException.class )
    public void testExportsWithNullExports() {
        new Builder("foo").exports((Exports)null);
    }

    @Test(expectedExceptions = IllegalArgumentException.class )
    public void testExportsWithNullTarget() {
        new Builder("foo").exports("p", (String) null);
    }

    @Test(expectedExceptions = NullPointerException.class )
    public void testExportsWithNullTargets() {
        new Builder("foo").exports("p", (Set<String>) null);
    }

    public void testExportsToString() {
        String s = new Builder("foo")
            .exports("p1", "bar")
            .build()
            .exports()
            .iterator()
            .next()
            .toString();
        assertTrue(s.contains("p1"));
        assertTrue(s.contains("bar"));
    }


    // uses

    public void testUses() {
        Set<String> uses
            = new Builder("foo")
                .uses("p.S")
                .uses("q.S")
                .build()
                .uses();
        assertTrue(uses.size() == 2);
        assertTrue(uses.contains("p.S"));
        assertTrue(uses.contains("q.S"));
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void testUsesWithDuplicate() {
        new Builder("foo").uses("p.S").uses("p.S");
    }

    @Test(dataProvider = "invalidjavaidentifiers",
          expectedExceptions = IllegalArgumentException.class )
    public void testUsesWithBadName(String service, String ignore) {
        new Builder("foo").uses(service);
    }


    // provides

    private Provides provides(String st, String pc) {
        return new Builder("foo")
            .provides("p.S", pc)
            .build()
            .provides()
            .values()
            .iterator()
            .next();
    }

    public void testProvidesWithProvides() {
        Provides p1 = provides("p.S", "q.S1");
        ModuleDescriptor descriptor = new Builder("m").provides(p1).build();
        Provides p2 = descriptor.provides().get("p.S");
        assertEquals(p1, p2);
    }

    public void testProvides() {
        Set<String> pns = new HashSet<>();
        pns.add("q.P1");
        pns.add("q.P2");

        Map<String, Provides> map
            = new Builder("foo")
                .provides("p.S", pns)
                .build()
                .provides();
        assertTrue(map.size() == 1);

        Provides p = map.values().iterator().next();
        assertEquals(p, p);
        assertTrue(p.providers().size() == 2);
        assertTrue(p.providers().contains("q.P1"));
        assertTrue(p.providers().contains("q.P2"));
    }

    @Test(expectedExceptions = IllegalStateException.class )
    public void testProvidesWithDuplicateProvides() {
        Provides p = provides("p.S", "q.S2");
        new Builder("m").provides("p.S", "q.S1").provides(p);
    }

    @Test(expectedExceptions = IllegalArgumentException.class )
    public void testProvidesWithEmptySet() {
        new Builder("foo").provides("p.Service", Collections.emptySet());
    }

    @Test(dataProvider = "invalidjavaidentifiers",
          expectedExceptions = IllegalArgumentException.class )
    public void testProvidesWithBadService(String service, String ignore) {
        new Builder("foo").provides(service, "p.Provider");
    }

    @Test(dataProvider = "invalidjavaidentifiers",
          expectedExceptions = IllegalArgumentException.class )
    public void testProvidesWithBadProvider(String provider, String ignore) {
        new Builder("foo").provides("p.Service", provider);
    }

    @Test(expectedExceptions = NullPointerException.class )
    public void testProvidesWithNullProvides() {
        new Builder("foo").provides((Provides)null);
    }

    @Test(expectedExceptions = NullPointerException.class )
    public void testProvidesWithNullProviders() {
        new Builder("foo").provides("p.S", (Set<String>) null);
    }


    // conceals

    public void testConceals() {
        Set<String> conceals
            = new Builder("foo").conceals("p").conceals("q").build().conceals();
        assertTrue(conceals.size() == 2);
        assertTrue(conceals.contains("p"));
        assertTrue(conceals.contains("q"));
    }

    public void testConcealsWithEmptySet() {
        Set<String> conceals
            = new Builder("foo").conceals(Collections.emptySet()).build().conceals();
        assertTrue(conceals.size() == 0);
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void testConcealsWithDuplicate() {
        new Builder("foo").conceals("p").conceals("p");
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void testConcealsWithExportedPackage() {
        new Builder("foo").exports("p").conceals("p");
    }

    @Test(dataProvider = "invalidjavaidentifiers",
          expectedExceptions = IllegalArgumentException.class )
    public void testConcealsWithBadName(String pn, String ignore) {
        new Builder("foo").conceals(pn);
    }


    // packages

    public void testPackages() {
        Set<String> packages
            = new Builder("foo").exports("p").conceals("q").build().packages();
        assertTrue(packages.size() == 2);
        assertTrue(packages.contains("p"));
        assertTrue(packages.contains("q"));
    }


    // name

    public void testModuleName() {
        String mn = new Builder("foo").build().name();
        assertEquals(mn, "foo");
    }

    @Test(dataProvider = "invalidjavaidentifiers",
          expectedExceptions = IllegalArgumentException.class )
    public void testBadModuleName(String mn, String ignore) {
        new Builder(mn);
    }


    // version

    public void testVersion1() {
        Version v1 = Version.parse("1.0");
        Version v2 = new Builder("foo").version(v1).build().version().get();
        assertEquals(v1, v2);
    }

    public void testVersion2() {
        String vs = "1.0";
        Version v1 = new Builder("foo").version(vs).build().version().get();
        Version v2 = Version.parse(vs);
        assertEquals(v1, v2);
    }

    @Test(expectedExceptions = NullPointerException.class )
    public void testNullVersion1() {
        new Builder("foo").version((Version)null);
    }

    @Test(expectedExceptions = IllegalArgumentException.class )
    public void testNullVersion2() {
        new Builder("foo").version((String)null);
    }

    @Test(expectedExceptions = IllegalArgumentException.class )
    public void testEmptyVersion() {
        new Builder("foo").version("");
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void testDuplicateVersion1() {
        Version v = Version.parse("2.0");
        new Builder("foo").version("1.0").version(v);
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void testDuplicateVersion2() {
        new Builder("foo").version("1.0").version("2.0");
    }


    // toNameAndVersion

    public void testToNameAndVersion() {
        ModuleDescriptor md1 = new Builder("foo").build();
        assertEquals(md1.toNameAndVersion(), "foo");

        ModuleDescriptor md2 = new Builder("foo").version("1.0").build();
        assertEquals(md2.toNameAndVersion(), "foo@1.0");
    }


    // isAutomatic
    public void testIsAutomatic() {
        ModuleDescriptor descriptor = new Builder("foo").build();
        assertFalse(descriptor.isAutomatic());
    }

    // isSynthetic
    public void testIsSynthetic() {
        assertFalse(Object.class.getModule().getDescriptor().isSynthetic());

        ModuleDescriptor descriptor = new Builder("foo").build();
        assertFalse(descriptor.isSynthetic());
    }


    // mainClass

    public void testMainClass() {
        String mainClass
            = new Builder("foo").mainClass("p.Main").build().mainClass().get();
        assertEquals(mainClass, "p.Main");
    }

    @Test(dataProvider = "invalidjavaidentifiers",
          expectedExceptions = IllegalArgumentException.class )
    public void testMainClassWithBadName(String mainClass, String ignore) {
        Builder builder = new Builder("foo");
        builder.mainClass(mainClass);
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void testDuplicateMainClass() {
        new Builder("foo").mainClass("p.Main").mainClass("p.Main");
    }


    // osName

    public void testOsName() {
        String osName = new Builder("foo").osName("Linux").build().osName().get();
        assertEquals(osName, "Linux");
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testNullOsName() {
        new Builder("foo").osName(null);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testEmptyOsName() {
        new Builder("foo").osName("");
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void testDuplicateOsName() {
        new Builder("foo").osName("Linux").osName("Linux");
    }


    // osArch

    public void testOsArch() {
        String osArch = new Builder("foo").osName("arm").build().osName().get();
        assertEquals(osArch, "arm");
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testNullOsArch() {
        new Builder("foo").osArch(null);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testEmptyOsArch() {
        new Builder("foo").osArch("");
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void testDuplicateOsArch() {
        new Builder("foo").osArch("arm").osArch("arm");
    }


    // osVersion

    public void testOsVersion() {
        String osVersion = new Builder("foo").osName("11.2").build().osName().get();
        assertEquals(osVersion, "11.2");
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testNullOsVersion() {
        new Builder("foo").osVersion(null);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testEmptyOsVersion() {
        new Builder("foo").osVersion("");
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void testDuplicateOsVersion() {
        new Builder("foo").osVersion("11.2").osVersion("11.2");
    }


    // reads

    private static InputStream EMPTY_INPUT_STREAM = new InputStream() {
        @Override
        public int read() {
            return -1;
        }
    };

    private static InputStream FAILING_INPUT_STREAM = new InputStream() {
        @Override
        public int read() throws IOException {
            throw new IOException();
        }
    };

    public void testRead() throws Exception {
        Module base = Object.class.getModule();

        try (InputStream in = base.getResourceAsStream("module-info.class")) {
            ModuleDescriptor descriptor = ModuleDescriptor.read(in);
            assertTrue(in.read() == -1); // all bytes read
            assertEquals(descriptor.name(), "java.base");
        }

        try (InputStream in = base.getResourceAsStream("module-info.class")) {
            ByteBuffer bb = ByteBuffer.wrap(in.readAllBytes());
            ModuleDescriptor descriptor = ModuleDescriptor.read(bb);
            assertFalse(bb.hasRemaining()); // no more remaining bytes
            assertEquals(descriptor.name(), "java.base");
        }
    }

    public void testReadsWithPackageFinder() {
        // TBD: Need way to write a module-info.class without a
        // ConcealedPackages attribute
    }

    @Test(expectedExceptions = InvalidModuleDescriptorException.class)
    public void testReadFromEmptyInputStream() throws Exception {
        ModuleDescriptor.read(EMPTY_INPUT_STREAM);
    }

    @Test(expectedExceptions = IOException.class)
    public void testReadFromFailingInputStream() throws Exception {
        ModuleDescriptor.read(FAILING_INPUT_STREAM);
    }

    @Test(expectedExceptions = InvalidModuleDescriptorException.class)
    public void testReadFromEmptyBuffer() {
        ByteBuffer bb = ByteBuffer.allocate(0);
        ModuleDescriptor.read(bb);
    }

    public void testReadWithNull() throws Exception {
        Module base = Object.class.getModule();

        try {
            ModuleDescriptor.read((InputStream)null);
            assertTrue(false);
        } catch (NullPointerException expected) { }


        try (InputStream in = base.getResourceAsStream("module-info.class")) {
            try {
                ModuleDescriptor.read(in, null);
                assertTrue(false);
            } catch (NullPointerException expected) { }
        }

        try {
            ModuleDescriptor.read((ByteBuffer)null);
            assertTrue(false);
        } catch (NullPointerException expected) { }


        try (InputStream in = base.getResourceAsStream("module-info.class")) {
            ByteBuffer bb = ByteBuffer.wrap(in.readAllBytes());
            try {
                ModuleDescriptor.read(bb, null);
                assertTrue(false);
            } catch (NullPointerException expected) { }
        }
    }


    // equals/hashCode/compareTo/toString

    public void testEqualsAndHashCode() {
        ModuleDescriptor md1 = new Builder("foo").build();
        ModuleDescriptor md2 = new Builder("foo").build();
        assertEquals(md1, md1);
        assertEquals(md1.hashCode(), md2.hashCode());
    }

    public void testCompare() {
        ModuleDescriptor md1 = new Builder("foo").build();
        ModuleDescriptor md2 = new Builder("bar").build();
        int n = "foo".compareTo("bar");
        assertTrue(md1.compareTo(md2) == n);
        assertTrue(md2.compareTo(md1) == -n);
    }

    public void testToString() {
        String s = new Builder("m1").requires("m2").exports("p1").build().toString();
        assertTrue(s.contains("m1"));
        assertTrue(s.contains("m2"));
        assertTrue(s.contains("p1"));
    }

}
