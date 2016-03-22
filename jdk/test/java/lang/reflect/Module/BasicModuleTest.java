/*
 * Copyright (c) 2014, 2016, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.module.ModuleDescriptor.Exports;
import java.lang.module.ResolvedModule;
import java.lang.reflect.Layer;
import java.lang.reflect.Module;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.testng.annotations.Test;
import static org.testng.Assert.*;

/*
 * @test
 * @summary Basic test of java.lang.reflect.Module
 * @modules java.desktop java.xml
 * @run testng BasicModuleTest
 */

public class BasicModuleTest {

    /**
     * Tests that the given module reads all modules in the boot Layer.
     */
    private void testReadsAllBootModules(Module m) {
        Layer bootLayer = Layer.boot();
        bootLayer.configuration()
            .modules()
            .stream()
            .map(ResolvedModule::name)
            .map(bootLayer::findModule)
            .forEach(target -> assertTrue(m.canRead(target.get())));
    }

    /**
     * Returns {@code true} if the array contains the given object.
     */
    private <T> boolean contains(T[] array, T obj) {
        return Stream.of(array).anyMatch(obj::equals);
    }

    /**
     * Returns a {@code Predicate} to test if a package is exported.
     */
    private Predicate<Exports> doesExport(String pn) {
        return e -> (e.source().equals(pn) && !e.isQualified());
    }



    @Test
    public void testThisModule() {
        Module thisModule = BasicModuleTest.class.getModule();
        Module baseModule = Object.class.getModule();

        assertFalse(thisModule.isNamed());
        assertTrue(thisModule.getName() == null);
        assertTrue(thisModule.getDescriptor() == null);
        assertTrue(thisModule.getLayer() == null);
        assertTrue(thisModule.toString().startsWith("unnamed module "));

        ClassLoader thisLoader = BasicModuleTest.class.getClassLoader();
        assertTrue(thisLoader == thisModule.getClassLoader());
        assertTrue(thisLoader.getUnnamedModule() == thisModule);

        // unnamed modules read all other modules
        ClassLoader cl;
        cl = ClassLoader.getPlatformClassLoader();
        assertTrue(thisModule.canRead(cl.getUnnamedModule()));
        cl = ClassLoader.getSystemClassLoader();
        assertTrue(thisModule.canRead(cl.getUnnamedModule()));
        testReadsAllBootModules(thisModule);

        // unnamed modules export all packages
        assertTrue(thisModule.isExported(""));
        assertTrue(thisModule.isExported("", thisModule));
        assertTrue(thisModule.isExported("", baseModule));
        assertTrue(thisModule.isExported("p"));
        assertTrue(thisModule.isExported("p", thisModule));
        assertTrue(thisModule.isExported("p", baseModule));

        // this test is in the unnamed package
        assertTrue(contains(thisModule.getPackages(), ""));
    }


    @Test
    public void testUnnamedModules() {
        Module thisModule = BasicModuleTest.class.getModule();
        Module baseModule = Object.class.getModule();

        ClassLoader loader1 = ClassLoader.getSystemClassLoader();
        ClassLoader loader2 = loader1.getParent();

        Module m1 = loader1.getUnnamedModule();
        Module m2 = loader2.getUnnamedModule();

        assertTrue(m1 != m2);

        assertFalse(m1.isNamed());
        assertFalse(m2.isNamed());

        assertTrue(m1.getLayer() == null);
        assertTrue(m2.getLayer() == null);

        assertTrue(m1.toString().startsWith("unnamed module "));
        assertTrue(m2.toString().startsWith("unnamed module "));

        // unnamed module reads all modules
        assertTrue(m1.canRead(m2));
        assertTrue(m2.canRead(m1));

        testReadsAllBootModules(m1);
        testReadsAllBootModules(m2);

        assertTrue(m1.isExported(""));
        assertTrue(m1.isExported("", thisModule));
        assertTrue(m1.isExported("", baseModule));
        assertTrue(m1.isExported("p"));
        assertTrue(m1.isExported("p", thisModule));
        assertTrue(m1.isExported("p", baseModule));
    }



    @Test
    public void testBaseModule() {
        Module base = Object.class.getModule();
        Module thisModule = BasicModuleTest.class.getModule();

        // getName
        assertTrue(base.getName().equals("java.base"));

        // getDescriptor
        assertTrue(base.getDescriptor().exports().stream()
                .anyMatch(doesExport("java.lang")));

        // getClassLoader
        assertTrue(base.getClassLoader() == null);

        // getLayer
        assertTrue(base.getLayer() == Layer.boot());

        // toString
        assertEquals(base.toString(), "module java.base");

        // getPackages
        assertTrue(contains(base.getPackages(), "java.lang"));

        // canRead
        assertTrue(base.canRead(base));

        // isExported
        assertTrue(base.isExported("java.lang"));
        assertTrue(base.isExported("java.lang", thisModule));
        assertFalse(base.isExported("java.wombat"));
        assertFalse(base.isExported("java.wombat", thisModule));
    }


    @Test
    public void testDesktopModule() {
        Module desktop = java.awt.Component.class.getModule();
        Module base = Object.class.getModule();
        Module xml = javax.xml.XMLConstants.class.getModule();
        Module thisModule = BasicModuleTest.class.getModule();

        // name
        assertTrue(desktop.getName().equals("java.desktop"));

        // descriptor
        assertTrue(desktop.getDescriptor().exports().stream()
                   .anyMatch(doesExport("java.awt")));

        // getClassLoader
        assertTrue(desktop.getClassLoader() == null);

        // getLayer
        assertTrue(desktop.getLayer() == Layer.boot());

        // toString
        assertEquals(desktop.toString(), "module java.desktop");

        // getPackages
        assertTrue(contains(desktop.getPackages(), "java.awt"));
        assertTrue(contains(desktop.getPackages(), "sun.awt"));

        // canRead
        assertTrue(desktop.canRead(base));
        assertTrue(desktop.canRead(xml));

        // isExported
        assertTrue(desktop.isExported("java.awt"));
        assertTrue(desktop.isExported("java.awt", thisModule));
        assertFalse(desktop.isExported("java.wombat"));
        assertFalse(desktop.isExported("java.wombat", thisModule));
    }


    @Test(expectedExceptions = { NullPointerException.class })
    public void testIsExportedNull() {
        Module thisModule = this.getClass().getModule();
        thisModule.isExported(null, thisModule);
    }


    @Test(expectedExceptions = { NullPointerException.class })
    public void testIsExportedToNull() {
        Module thisModule = this.getClass().getModule();
        thisModule.isExported("", null);
    }


}
