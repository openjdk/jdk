/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @requires ((os.arch == "amd64" | os.arch == "x86_64") & sun.arch.data.model == "64") | os.arch == "aarch64"
 * @modules jdk.incubator.foreign/jdk.internal.foreign
 * @run testng/othervm -Dforeign.restricted=permit TestLibraryLookup
 */

import jdk.incubator.foreign.LibraryLookup;
import jdk.incubator.foreign.MemoryAddress;
import jdk.internal.foreign.LibrariesHelper;
import org.testng.annotations.Test;

import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.*;

// FYI this test is run on 64-bit platforms only for now,
// since the windows 32-bit linker fails and there
// is some fallback behaviour to use the 64-bit linker,
// where cygwin gets in the way and we accidentally pick up its
// link.exe
public class TestLibraryLookup {

    @Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp = "Library not found.*")
    public void testInvalidLookupName() {
        LibraryLookup.ofLibrary("NonExistent");
    }

    @Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp = "Not an absolute path.*")
    public void testNoAbsoluteLookupPath() {
        LibraryLookup.ofPath(Path.of("NonExistent"));
    }

    @Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp = "Library not found.*")
    public void testInvalidLookupPath() {
        LibraryLookup.ofPath(Path.of("NonExistent").toAbsolutePath());
    }

    @Test
    public void testSimpleLookup() throws Throwable {
        LibraryLookup.Symbol symbol = null;
        LibraryLookup lookup = LibraryLookup.ofLibrary("LookupTest");
        symbol = lookup.lookup("f").get();
        assertEquals(symbol.name(), "f");
        assertEquals(LibrariesHelper.numLoadedLibraries(), 1);
        lookup = null;
        symbol = null;
        waitUnload();
    }

    @Test
    public void testInvalidSymbolLookup() throws Throwable {
        LibraryLookup.Symbol symbol = null;
        LibraryLookup lookup = LibraryLookup.ofLibrary("LookupTest");
        assertTrue(lookup.lookup("nonExistent").isEmpty());
        assertEquals(LibrariesHelper.numLoadedLibraries(), 1);
        lookup = null;
        symbol = null;
        waitUnload();
    }

    @Test
    public void testMultiLookupSameLoader() throws Throwable {
        List<LibraryLookup.Symbol> symbols = new ArrayList<>();
        List<LibraryLookup> lookups = new ArrayList<>();
        for (int i = 0 ; i < 5 ; i++) {
            LibraryLookup lookup = LibraryLookup.ofLibrary("LookupTest");
            LibraryLookup.Symbol symbol = lookup.lookup("f").get();
            lookups.add(lookup);
            symbols.add(symbol);
            assertEquals(LibrariesHelper.numLoadedLibraries(), 1);
        }
        lookups = null;
        symbols = null;
        waitUnload();
    }

    @Test
    public void testMultiLookupDifferentLoaders() throws Throwable {
        List<URLClassLoader> loaders = new ArrayList<>();
        for (int i = 0 ; i < 5 ; i++) {
            URLClassLoader loader = new LocalLoader();
            Class<?> clazz = loader.loadClass("TestLibraryLookup$Holder");
            Field field = clazz.getField("lookup");
            field.setAccessible(true);
            field.get(null); //make sure <clinit> is run
            loaders.add(loader);
        }
        loaders.forEach(loader -> {
            try {
                loader.close();
            } catch (Throwable ex) {
                throw new AssertionError(ex);
            }
        });
        loaders = null;
        waitUnload();
    }

    static class LocalLoader extends URLClassLoader {
        public LocalLoader() throws Exception {
            super(new URL[] { Path.of(System.getProperty("test.classes")).toUri().toURL() });
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            Class clazz = findLoadedClass(name);
            if (clazz == null) {
                //try local first
                try {
                    clazz = findClass(name);
                } catch (ClassNotFoundException e) {
                    // Swallow exception - does not exist locally
                }
                //then try parent loader
                if (clazz == null) {
                    clazz = super.loadClass(name);
                }
            }
            return clazz;
        }
    }

    static class Holder {
        public static LibraryLookup lookup;
        public static LibraryLookup.Symbol symbol;

        static {
            try {
                lookup = LibraryLookup.ofLibrary("LookupTest");
                symbol = lookup.lookup("f").get();
            } catch (Throwable ex) {
                throw new ExceptionInInitializerError();
            }
        }
    }

    private static void waitUnload() throws InterruptedException {
        while (LibrariesHelper.numLoadedLibraries() != 0) {
            System.gc();
            Object o = new Object[1000];
            Thread.sleep(1);
        }
    }
}
