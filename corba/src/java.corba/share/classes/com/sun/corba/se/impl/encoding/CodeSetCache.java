/*
 * Copyright (c) 2001, 2004, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.corba.se.impl.encoding;

import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Thread local cache of sun.io code set converters for performance.
 *
 * The thread local class contains a single reference to a Map[]
 * containing two WeakHashMaps.  One for CharsetEncoders and
 * one for CharsetDecoders.  Constants are defined for indexing.
 *
 * This is used internally by CodeSetConversion.
 */
class CodeSetCache
{
    /**
     * The ThreadLocal data is a 2 element Map array indexed
     * by BTC_CACHE_MAP and CTB_CACHE_MAP.
     */
    private ThreadLocal converterCaches = new ThreadLocal() {
        public java.lang.Object initialValue() {
            return new Map[] { new WeakHashMap(), new WeakHashMap() };
        }
    };

    /**
     * Index in the thread local converterCaches array for
     * the byte to char converter Map.  A key is the Java
     * name corresponding to the desired code set.
     */
    private static final int BTC_CACHE_MAP = 0;

    /**
     * Index in the thread local converterCaches array for
     * the char to byte converter Map.  A key is the Java
     * name corresponding to the desired code set.
     */
    private static final int CTB_CACHE_MAP = 1;

    /**
     * Retrieve a CharsetDecoder from the Map using the given key.
     */
    CharsetDecoder getByteToCharConverter(Object key) {
        Map btcMap = ((Map[])converterCaches.get())[BTC_CACHE_MAP];

        return (CharsetDecoder)btcMap.get(key);
    }

    /**
     * Retrieve a CharsetEncoder from the Map using the given key.
     */
    CharsetEncoder getCharToByteConverter(Object key) {
        Map ctbMap = ((Map[])converterCaches.get())[CTB_CACHE_MAP];

        return (CharsetEncoder)ctbMap.get(key);
    }

    /**
     * Stores the given CharsetDecoder in the thread local cache,
     * and returns the same converter.
     */
    CharsetDecoder setConverter(Object key, CharsetDecoder converter) {
        Map btcMap = ((Map[])converterCaches.get())[BTC_CACHE_MAP];

        btcMap.put(key, converter);

        return converter;
    }

    /**
     * Stores the given CharsetEncoder in the thread local cache,
     * and returns the same converter.
     */
    CharsetEncoder setConverter(Object key, CharsetEncoder converter) {

        Map ctbMap = ((Map[])converterCaches.get())[CTB_CACHE_MAP];

        ctbMap.put(key, converter);

        return converter;
    }
}
