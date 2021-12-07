/*
 * Copyright (C) 2021 THL A29 Limited, a Tencent company. All rights reserved.
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

package jdk.jfr.internal.instrument;

import jdk.jfr.events.Handlers;
import jdk.jfr.internal.handlers.EventHandler;
import jdk.jfr.events.JavaNativeAllocationEvent;

@JIInstrumentationTarget("jdk.internal.misc.Unsafe")
final class UnsafeInstrumentor {
    private UnsafeInstrumentor() {
    }

    @SuppressWarnings("deprecation")
    @JIInstrumentationMethod
    public long allocateMemory(long bytes) {
        EventHandler handler = Handlers.JAVA_NATIVE_ALLOCATION;
        if (!handler.isEnabled()) {
            return allocateMemory(bytes);
        }
        long addr = 0;
        long start = 0;
        try {
            start = EventHandler.timestamp();
            addr = allocateMemory(bytes);
        } finally {
            if (addr != 0) {
                handler.write(start, 0L, addr, bytes);
            }
        }
        return addr;
    }

    @SuppressWarnings("deprecation")
    @JIInstrumentationMethod
    public long reallocateMemory(long address, long bytes) {
        EventHandler handler = Handlers.JAVA_NATIVE_REALLOCATION;
        if (!handler.isEnabled()) {
            return reallocateMemory(address, bytes);
        }
        long addr = 0;
        long start = 0;
        try {
            start = EventHandler.timestamp();
            addr = reallocateMemory(address, bytes);
        } finally {
            if (addr != 0) {
                handler.write(start, 0L, address, addr, bytes);
            }
        }
        return addr;
    }

    @SuppressWarnings("deprecation")
    @JIInstrumentationMethod
    public void freeMemory(long address) {
        EventHandler handler = Handlers.JAVA_NATIVE_FREE;
        if (!handler.isEnabled()) {
            freeMemory(address);
            return;
        }
        long start = 0;
        try {
            start = EventHandler.timestamp();
            freeMemory(address);
        } finally {
            if (address != 0) {
                handler.write(start, 0L, address);
            }
        }
    }
}
