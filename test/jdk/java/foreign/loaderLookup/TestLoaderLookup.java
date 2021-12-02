/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @compile --add-modules jdk.incubator.foreign lookup/Lookup.java
 * @compile --add-modules jdk.incubator.foreign invoker/Invoker.java
 * @run main/othervm --enable-native-access=ALL-UNNAMED TestLoaderLookup
 */

import java.lang.reflect.*;
import jdk.incubator.foreign.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Paths;

public class TestLoaderLookup {
    public static void main(String[] args) throws ReflectiveOperationException {
        ClassLoader loader1 = newClassLoader("lookup");
        Class<?> lookup = loader1.loadClass("lookup.Lookup");
        Method fooSymbol = lookup.getDeclaredMethod("fooSymbol");
        NativeSymbol foo = (NativeSymbol)fooSymbol.invoke(null);

        ClassLoader loader2 = newClassLoader("invoker");
        Class<?> invoker = loader2.loadClass("invoker.Invoker");
        Method invoke = invoker.getDeclaredMethod("invoke", NativeSymbol.class);
        invoke.invoke(null, foo);

        loader1 = null;
        lookup = null;
        fooSymbol = null;
        // Make sure that the loader is kept reachable
        for (int i = 0 ; i < 1000 ; i++) {
            invoke.invoke(null, foo); // might crash if loader1 is GC'ed
            System.gc();
        }
    }

    public static ClassLoader newClassLoader(String path) {
        try {
            return new URLClassLoader(new URL[] {
                    Paths.get(System.getProperty("test.classes", path)).toUri().toURL(),
            }, null);
        } catch (MalformedURLException e){
            throw new RuntimeException("Unexpected URL conversion failure", e);
        }
    }
}
