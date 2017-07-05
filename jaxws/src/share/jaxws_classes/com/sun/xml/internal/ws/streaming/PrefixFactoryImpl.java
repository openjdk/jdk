/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.ws.streaming;

import java.util.HashMap;
import java.util.Map;

/**
 * <p> A prefix factory that caches the prefixes it creates. </p>
 *
 * @author WS Development Team
 */
public class PrefixFactoryImpl implements PrefixFactory {

    public PrefixFactoryImpl(String base) {
        _base = base;
        _next = 1;
    }

    public String getPrefix(String uri) {
        String prefix = null;

        if (_cachedUriToPrefixMap == null) {
            _cachedUriToPrefixMap = new HashMap();
        } else {
            prefix = (String) _cachedUriToPrefixMap.get(uri);
        }

        if (prefix == null) {
            prefix = _base + Integer.toString(_next++);
            _cachedUriToPrefixMap.put(uri, prefix);
        }

        return prefix;
    }

    private String _base;
    private int _next;
    private Map _cachedUriToPrefixMap;
}
