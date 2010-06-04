/*
 * Copyright (c) 2005, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.script.util;

import javax.script.Bindings;
import java.util.Map;
import java.util.AbstractMap;

/**
 * Abstract super class for Bindings implementations
 *
 * @author Mike Grogan
 * @since 1.6
 */
public abstract class BindingsBase extends AbstractMap<String, Object>
        implements Bindings {

    //AbstractMap methods
    public Object get(Object name) {
        checkKey(name);
        return getImpl((String)name);
    }

    public Object remove(Object key) {
        checkKey(key);
        return removeImpl((String)key);
    }

    public Object put(String key, Object value) {
        checkKey(key);
        return putImpl(key, value);
    }

    public void putAll(Map<? extends String, ? extends Object> toMerge) {
        for (Map.Entry<? extends String, ? extends Object> entry : toMerge.entrySet()) {
            String key = entry.getKey();
            checkKey(key);
            putImpl(entry.getKey(), entry.getValue());
        }
    }

    //BindingsBase methods
    public abstract Object putImpl(String name, Object value);
    public abstract Object getImpl(String name);
    public abstract Object removeImpl(String name);
    public abstract String[] getNames();

    protected void checkKey(Object key) {
        if (key == null) {
            throw new NullPointerException("key can not be null");
        }
        if (!(key instanceof String)) {
            throw new ClassCastException("key should be String");
        }
        if (key.equals("")) {
            throw new IllegalArgumentException("key can not be empty");
        }
    }
}
