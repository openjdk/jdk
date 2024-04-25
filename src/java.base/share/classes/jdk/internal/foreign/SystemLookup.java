/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.foreign.*;
import java.lang.invoke.MethodHandles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import jdk.internal.loader.NativeLibrary;
import jdk.internal.loader.RawNativeLibraries;
import sun.security.action.GetPropertyAction;

import static java.lang.foreign.ValueLayout.ADDRESS;

public final class SystemLookup implements SymbolLookup {

    private SystemLookup() { }

    private static final SystemLookup INSTANCE = new SystemLookup();

    /* A fallback lookup, used when creation of system lookup fails. */
    private static final SymbolLookup FALLBACK_LOOKUP = name -> {
        Objects.requireNonNull(name);
        return Optional.empty();
    };

    /*
     * On POSIX systems, dlsym will allow us to lookup symbol in library dependencies; the same trick doesn't work
     * on Windows. For this reason, on Windows we do not generate any side-library, and load msvcrt.dll directly instead.
     */
    private static final SymbolLookup SYSTEM_LOOKUP = makeSystemLookup();

    private static SymbolLookup makeSystemLookup() {
        try {
            if (Utils.IS_WINDOWS) {
                return makeWindowsLookup();
            } else {
                return libLookup(libs -> libs.load(jdkLibraryPath("syslookup")));
            }
        } catch (Throwable ex) {
            // This can happen in the event of a library loading failure - e.g. if one of the libraries the
            // system lookup depends on cannot be loaded for some reason. In such extreme cases, rather than
            // fail, return a dummy lookup.
            return FALLBACK_LOOKUP;
        }
    }

    private static SymbolLookup makeWindowsLookup() {
        @SuppressWarnings("removal")
        String systemRoot = AccessController.doPrivileged(new PrivilegedAction<>() {
            @Override
            public String run() {
                return System.getenv("SystemRoot");
            }
        });
        Path system32 = Path.of(systemRoot, "System32");
        Path ucrtbase = system32.resolve("ucrtbase.dll");
        Path msvcrt = system32.resolve("msvcrt.dll");

        @SuppressWarnings("removal")
        boolean useUCRT = AccessController.doPrivileged(new PrivilegedAction<>() {
            @Override
            public Boolean run() {
                return Files.exists(ucrtbase);
            }
        });
        Path stdLib = useUCRT ? ucrtbase : msvcrt;
        SymbolLookup lookup = libLookup(libs -> libs.load(stdLib));

        if (useUCRT) {
            // use a fallback lookup to look up inline functions from fallback lib

            SymbolLookup fallbackLibLookup =
                    libLookup(libs -> libs.load(jdkLibraryPath("syslookup")));

            @SuppressWarnings("restricted")
            MemorySegment funcs = fallbackLibLookup.findOrThrow("funcs")
                    .reinterpret(WindowsFallbackSymbols.LAYOUT.byteSize());

            Function<String, Optional<MemorySegment>> fallbackLookup = name -> Optional.ofNullable(WindowsFallbackSymbols.valueOfOrNull(name))
                .map(symbol -> funcs.getAtIndex(ADDRESS, symbol.ordinal()));

            final SymbolLookup finalLookup = lookup;
            lookup = name -> {
                Objects.requireNonNull(name);
                if (Utils.containsNullChars(name)) return Optional.empty();
                return finalLookup.find(name).or(() -> fallbackLookup.apply(name));
            };
        }

        return lookup;
    }

    private static SymbolLookup libLookup(Function<RawNativeLibraries, NativeLibrary> loader) {
        NativeLibrary lib = loader.apply(RawNativeLibraries.newInstance(MethodHandles.lookup()));
        return name -> {
            Objects.requireNonNull(name);
            if (Utils.containsNullChars(name)) return Optional.empty();
            try {
                long addr = lib.lookup(name);
                return addr == 0 ?
                        Optional.empty() :
                        Optional.of(MemorySegment.ofAddress(addr));
            } catch (NoSuchMethodException e) {
                return Optional.empty();
            }
        };
    }

    /*
     * Returns the path of the given library name from JDK
     */
    private static Path jdkLibraryPath(String name) {
        Path javahome = Path.of(GetPropertyAction.privilegedGetProperty("java.home"));
        String lib = Utils.IS_WINDOWS ? "bin" : "lib";
        String libname = System.mapLibraryName(name);
        return javahome.resolve(lib).resolve(libname);
    }


    public static SystemLookup getInstance() {
        return INSTANCE;
    }

    @Override
    public Optional<MemorySegment> find(String name) {
        return SYSTEM_LOOKUP.find(name);
    }

    // fallback symbols missing from ucrtbase.dll
    // this list has to be kept in sync with the table in the companion native library
    private enum WindowsFallbackSymbols {
        // stdio
        fprintf,
        fprintf_s,
        fscanf,
        fscanf_s,
        fwprintf,
        fwprintf_s,
        fwscanf,
        fwscanf_s,
        printf,
        printf_s,
        scanf,
        scanf_s,
        snprintf,
        sprintf,
        sprintf_s,
        sscanf,
        sscanf_s,
        swprintf,
        swprintf_s,
        swscanf,
        swscanf_s,
        vfprintf,
        vfprintf_s,
        vfscanf,
        vfscanf_s,
        vfwprintf,
        vfwprintf_s,
        vfwscanf,
        vfwscanf_s,
        vprintf,
        vprintf_s,
        vscanf,
        vscanf_s,
        vsnprintf,
        vsnprintf_s,
        vsprintf,
        vsprintf_s,
        vsscanf,
        vsscanf_s,
        vswprintf,
        vswprintf_s,
        vswscanf,
        vswscanf_s,
        vwprintf,
        vwprintf_s,
        vwscanf,
        vwscanf_s,
        wprintf,
        wprintf_s,
        wscanf,
        wscanf_s,

        // time
        gmtime;

        static WindowsFallbackSymbols valueOfOrNull(String name) {
            try {
                return valueOf(name);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }

        static final SequenceLayout LAYOUT = MemoryLayout.sequenceLayout(
                values().length, ADDRESS);
    }
}
