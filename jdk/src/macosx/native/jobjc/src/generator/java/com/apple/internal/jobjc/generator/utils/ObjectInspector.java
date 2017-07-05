/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.apple.internal.jobjc.generator.utils;

import java.io.StringWriter;
import java.lang.reflect.Field;

public abstract class ObjectInspector {
    /**
     * @return  a string representation of object internals.
     */
    public static String inspect(Object obj) {
        StringWriter sw = new StringWriter();
        inspectForClass(obj, obj.getClass(), sw);
        return sw.toString();
    }

    private static void inspectForClass(Object instance, Class clazz, StringWriter sw){
        boolean willGoSuper = clazz.getSuperclass() != null && !clazz.getSuperclass().getName().equals("java.lang.Object");

        sw.append(clazz.getSimpleName());
        sw.append("{");
        Field[] fs = clazz.getDeclaredFields();
        for(int i = 0; i < fs.length; i++){
            Field f = fs[i];
            f.setAccessible(true);
            sw.append(f.getName());
            sw.append(": ");
            try {
                Object o = f.get(instance);
                sw.append(o == null ? "null" : o.toString());
            } catch (IllegalArgumentException ex) {
                throw new RuntimeException(ex);
            } catch (IllegalAccessException ex) {
                throw new RuntimeException(ex);
            }
            if(i < fs.length - 1 || willGoSuper)
                sw.append(", ");
        }

        if(willGoSuper){
            sw.append("super: ");
            inspectForClass(instance, clazz.getSuperclass(), sw);
        }

        sw.append("}");
    }
}
