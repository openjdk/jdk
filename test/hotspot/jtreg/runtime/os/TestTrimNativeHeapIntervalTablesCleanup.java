/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8375125
 * @summary Trigger StringTable::clean_dead_entries or SymbolTable::clean_dead_entries
 *          with -XX:TrimNativeHeapInterval enabled,should not violate lock ordering.
 * @requires vm.debug
 * @requires vm.gc != "Epsilon"
 * @library /test/lib
 * @modules java.compiler
 * @run main/othervm -Xms128m -Xmx128m
 *                   -XX:TrimNativeHeapInterval=300000
 *                   TestTrimNativeHeapIntervalTablesCleanup string
 * @run main/othervm -Xms128m -Xmx128m
 *                   -XX:TrimNativeHeapInterval=300000
 *                   TestTrimNativeHeapIntervalTablesCleanup symbol
 */

import java.util.LinkedList;
import jdk.test.lib.compiler.InMemoryJavaCompiler;

public class TestTrimNativeHeapIntervalTablesCleanup {

    public static void main(String[] args) throws Exception{
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected 1 argument: string|symbol");
        }
        switch (args[0]) {
            case "string":
                testStringTableCleanup();
                break;
            case "symbol":
                testSymbolTableCleanup();
                break;
            default:
                throw new IllegalArgumentException("Unknown mode: " + args[0]);
        }
        System.out.println("passed: " + args[0]);
    }

    static void testStringTableCleanup() throws Exception{
        final int rounds = 30;
        final int maxSize = 200_000;
        final int pruneEvery = 50_000;
        final int pruneCount = 25_000;
        long stringNum = 0;

        for (int round = 0; round < rounds; round++) {
            LinkedList<String> list = new LinkedList<>();
            for (int i = 0; i < maxSize; i++, stringNum++) {
                if (i != 0 && (i % pruneEvery) == 0) {
                    int toRemove = Math.min(pruneCount, list.size());
                    list.subList(0, toRemove).clear();
                }
                list.push(Long.toString(stringNum).intern());
            }
            System.gc();
            Thread.sleep(1000);
        }
    }

    static void testSymbolTableCleanup() throws Exception {
        final int rounds = 10;
        final int classesPerRound = 100;

        for (int r = 0; r < rounds; r++) {
            for (int i = 0; i < classesPerRound; i++) {
                String cn = "C" + r + "_" + i;
                byte[] bytes = InMemoryJavaCompiler.compile(
                               cn,
                               "public class " + cn + " { int m" + i + "() { return " + i + "; } }"
                               );
                new ClassLoader(null) {
                    @Override
                    protected Class<?> findClass(String name) throws ClassNotFoundException {
                        if (!name.equals(cn)) throw new ClassNotFoundException(name);
                        return defineClass(name, bytes, 0, bytes.length);
                    }
                }.loadClass(cn).getDeclaredConstructor().newInstance();
            }
            System.gc();
            Thread.sleep(1000);
        }
    }
}