/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.loader;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

public class NativeLibrariesTest implements Runnable {
    public static final String LIB_NAME = "nativeLibrariesTest";
    // increments when JNI_OnLoad and JNI_OnUnload is invoked.
    // This is only for JNI native library
    private static int loadedCount = 0;
    private static int unloadedCount = 0;
    /*
     * Called by JNI_OnLoad when the native library is unloaded
     */
    static void nativeLibraryLoaded() {
        loadedCount++;
    }

    /*
     * Called by JNI_OnUnload when the native library is unloaded
     */
    static void nativeLibraryUnloaded() {
        unloadedCount++;
    }

    private final RawNativeLibraries nativeLibraries;
    private final Set<NativeLibrary> loadedLibraries = new HashSet<>();

    public NativeLibrariesTest() {
        this.nativeLibraries = RawNativeLibraries.newInstance(MethodHandles.lookup());
    }

    /*
     * Invoke by p.Test to load the same native library from different class loader
     */
    public void run() {
        loadTestLibrary(); // expect loading of native library succeed
    }

    static Path libraryPath() {
        Path lib = Path.of(System.getProperty("test.nativepath"));
        return lib.resolve(System.mapLibraryName(LIB_NAME));
    }

    public void runTest() throws Exception {
        Path lib = libraryPath();
        NativeLibrary nl1 = nativeLibraries.load(lib);
        NativeLibrary nl2 = nativeLibraries.load(lib);
        assertTrue(nl1 != null && nl2 != null, "fail to load library");
        assertTrue(nl1 != nl2, "Expected different NativeLibrary instances");
        assertTrue(loadedCount == 0, "Native library loaded.  Expected: JNI_OnUnload not invoked");
        assertTrue(unloadedCount == 0, "native library never unloaded");

        // load successfully even from another loader
        loadWithCustomLoader();

        // unload the native library
        nativeLibraries.unload(nl1);
        assertTrue(unloadedCount == 0, "Native library unloaded.  Expected: JNI_OnUnload not invoked");

        try {
            nativeLibraries.unload(nl1);
            throw new RuntimeException("Expect to fail as the library has already been unloaded");
        } catch (IllegalArgumentException e) { }

        // load the native library and expect new NativeLibrary instance
        NativeLibrary nl3 = nativeLibraries.load(lib);
        assertTrue(nl1 != nl3, nl1 + " == " + nl3);
        assertTrue(loadedCount == 0, "Native library loaded.  Expected: JNI_OnUnload not invoked");

        // load successfully even from another loader
        loadWithCustomLoader();

        // keep the loaded NativeLibrary instances
        loadedLibraries.add(nl2);
        loadedLibraries.add(nl3);
    }

    /*
     * Unloads all loaded NativeLibrary instance
     */
    public void unload() {
        System.out.println("Unloading " + loadedLibraries.size() + " NativeLibrary instances");
        for (NativeLibrary nl : loadedLibraries) {
            nativeLibraries.unload(nl);
            assertTrue(unloadedCount == 0, "Native library unloaded.  Expected: JNI_OnUnload not invoked");
        }
        loadedLibraries.clear();
    }

    public void loadTestLibrary() {
        NativeLibrary nl = nativeLibraries.load(libraryPath());
        assertTrue(nl != null, "fail to load " + libraryPath());
        loadedLibraries.add(nl);
    }

    public void load(String pathname, boolean succeed) {
        NativeLibrary nl = nativeLibraries.load(pathname);
        if (succeed) {
            assertTrue(nl != null, "fail to load " + pathname);
        } else {
            assertTrue(nl == null, "expect to return null for " + pathname);
        }
    }

    /*
     * Loads p.Test class with a new class loader and invokes the run() method.
     * p.Test::run invokes NativeLibrariesTest::run
     */
    private void loadWithCustomLoader() throws Exception {
        TestLoader loader = new TestLoader();
        Class<?> c = Class.forName("p.Test", true, loader);
        Constructor<?> ctr = c.getConstructor(Runnable.class);
        Runnable r = (Runnable) ctr.newInstance(this);
        r.run();
    }

    static class TestLoader extends URLClassLoader {
        static URL[] toURLs() {
            try {
                return new URL[] { Paths.get("classes").toUri().toURL() };
            } catch (MalformedURLException e) {
                throw new Error(e);
            }
        }

        TestLoader() {
            super("testloader", toURLs(), ClassLoader.getSystemClassLoader());
        }
    }

    static void assertTrue(boolean value, String msg) {
        if (!value) {
            throw new AssertionError(msg);
        }
    }
}
