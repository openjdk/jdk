/*
* Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
* @summary Tests class unloading on virtual threads
*
* @compile --enable-preview -source ${jdk.version} ClassUnloading.java
* @run main/othervm --enable-preview -XX:-UseCompressedOops ClassUnloading
* @run main/othervm --enable-preview -XX:+UseCompressedOops ClassUnloading
* @run main/othervm --enable-preview -Xcomp -XX:-TieredCompilation -XX:CompileOnly=jdk/internal/vm/Continuation,ClassUnloading ClassUnloading
*/

// @run testng/othervm -Xcomp -XX:-TieredCompilation -XX:CompileOnly=jdk/internal/vm/Continuation,Basic Basic

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;

public class ClassUnloading {
    public static void main(String[] args) throws Throwable {
        System.out.println(Thread.currentThread());
        test();
        System.out.println();
        // repeat test in virtual thread
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            executor.submit(() -> {
                System.out.println(Thread.currentThread());
                test();
                return null;
            }).get();
        }
    }

    static void test() throws Exception {
        // class bytes for Dummy class
        URI uri = ClassUnloading.class.getProtectionDomain().getCodeSource().getLocation().toURI();
        Path file = Path.of(uri).resolve("Dummy.class");
        byte[] classBytes = Files.readAllBytes(file);
        // define hidden class in same run-time package as this class
        Lookup lookup = MethodHandles.lookup();
        Class<?> clazz = lookup.defineHiddenClass(classBytes, false).lookupClass();
        String cn = clazz.getName();
        var ref = new WeakReference<Class<?>>(clazz);
        // try to cause hidden class to be unloaded, should fail due to strong ref
        System.out.println("try unload " + cn);
        boolean unloaded = tryUnload(ref);
        if (unloaded)
            throw new RuntimeException(cn + " unloaded!!!");
        Reference.reachabilityFence(clazz);
        clazz = null;
        // try to cause hidden class to be unloaded, should succeed
        System.out.println("unload " + cn);
        unload(ref);
    }

    static boolean tryUnload(WeakReference<Class<?>> ref) {
        return gc(() -> ref.get() == null);
    }

    static void unload(WeakReference<Class<?>> ref) {
        boolean cleared = gc(() -> ref.get() == null);
        if (!cleared)
            throw new RuntimeException("weak reference not cleared!!");
        Class<?> clazz = ref.get();
        if (clazz != null)
            throw new RuntimeException(clazz + " not unloaded");
    }

    static boolean gc(BooleanSupplier s) {
        try {
            for (int i = 0; i < 10; i++) {
                if (s.getAsBoolean())
                    return true;
                System.out.print(".");
                System.gc();
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            return false;
        } finally {
            System.out.println();
        }
    }
}

class Dummy { }
