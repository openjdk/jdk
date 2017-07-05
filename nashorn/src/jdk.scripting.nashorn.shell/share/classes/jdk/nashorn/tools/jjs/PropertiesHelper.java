/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

package jdk.nashorn.tools.jjs;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.WeakHashMap;
import java.util.stream.Collectors;
import jdk.nashorn.internal.runtime.JSType;
import jdk.nashorn.internal.runtime.PropertyMap;
import jdk.nashorn.internal.runtime.ScriptObject;
import jdk.nashorn.internal.runtime.ScriptRuntime;
import jdk.nashorn.internal.objects.NativeJava;

/*
 * A helper class to get properties of a given object for source code completion.
 */
final class PropertiesHelper {
    private PropertiesHelper() {}

    // cached properties list
    private static final WeakHashMap<Object, List<String>> propsCache = new WeakHashMap<>();

    // returns the list of properties of the given object
    static List<String> getProperties(final Object obj) {
        assert obj != null && obj != ScriptRuntime.UNDEFINED;

        if (JSType.isPrimitive(obj)) {
            return getProperties(JSType.toScriptObject(obj));
        }

        if (obj instanceof ScriptObject) {
            final ScriptObject sobj = (ScriptObject)obj;
            final PropertyMap pmap = sobj.getMap();
            if (propsCache.containsKey(pmap)) {
                return propsCache.get(pmap);
            }
            final String[] keys = sobj.getAllKeys();
            List<String> props = Arrays.asList(keys);
            props = props.stream()
                         .filter(s -> Character.isJavaIdentifierStart(s.charAt(0)))
                         .collect(Collectors.toList());
            Collections.sort(props);
            // cache properties against the PropertyMap
            propsCache.put(pmap, props);
            return props;
        }

        if (NativeJava.isType(ScriptRuntime.UNDEFINED, obj)) {
            if (propsCache.containsKey(obj)) {
                return propsCache.get(obj);
            }
            final List<String> props = NativeJava.getProperties(obj);
            Collections.sort(props);
            // cache properties against the StaticClass representing the class
            propsCache.put(obj, props);
            return props;
        }

        final Class<?> clazz = obj.getClass();
        if (propsCache.containsKey(clazz)) {
            return propsCache.get(clazz);
        }

        final List<String> props = NativeJava.getProperties(obj);
        Collections.sort(props);
        // cache properties against the Class object
        propsCache.put(clazz, props);
        return props;
    }

    // returns the list of properties of the given object that start with the given prefix
    static List<String> getProperties(final Object obj, final String prefix) {
        assert prefix != null && !prefix.isEmpty();
        return getProperties(obj).stream()
                   .filter(s -> s.startsWith(prefix))
                   .collect(Collectors.toList());
    }
}
