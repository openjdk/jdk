/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package jdk.internal.foreign;

import jdk.incubator.foreign.SymbolLookup;
import jdk.incubator.foreign.MemoryAddress;
import jdk.internal.loader.NativeLibraries;
import jdk.internal.loader.NativeLibrary;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

public class SystemLookup implements SymbolLookup {

    private SystemLookup() { }

    final static SystemLookup INSTANCE = new SystemLookup();

    /*
     * On POSIX systems, dlsym will allow us to lookup symbol in library dependencies; the same trick doesn't work
     * on Windows. For this reason, on Windows we do not generate any side-library, and load msvcrt.dll directly instead.
     */
    final NativeLibrary syslookup = switch (CABI.current()) {
        case SysV, AArch64 -> NativeLibraries.rawNativeLibraries(SystemLookup.class, false).loadLibrary("syslookup");
        case Win64 -> {
            Path system32 = Path.of(System.getenv("SystemRoot"), "System32");
            Path ucrtbase = system32.resolve("ucrtbase.dll");
            Path msvcrt = system32.resolve("msvcrt.dll");

            Path stdLib = Files.exists(ucrtbase) ? ucrtbase : msvcrt;

            yield NativeLibraries.rawNativeLibraries(SystemLookup.class, false)
                    .loadLibrary(null, stdLib.toFile());
        }
    };

    @Override
    public Optional<MemoryAddress> lookup(String name) {
        Objects.requireNonNull(name);
        long addr = syslookup.find(name);
        return addr == 0 ?
                Optional.empty() : Optional.of(MemoryAddress.ofLong(addr));
    }

    public static SystemLookup getInstance() {
        return INSTANCE;
    }
}
