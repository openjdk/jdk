/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.core.test;

import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;

import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionDescriptor;
import org.graalvm.compiler.options.OptionDescriptors;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.util.EconomicMap;
import org.graalvm.util.MapCursor;

/**
 * An implementation of {@link OptionDescriptor} that uses reflection to create descriptors from a
 * list of field name and help text pairs. We cannot use the {@link Option} annotation as it has a
 * {@link RetentionPolicy#SOURCE} retention policy.
 *
 * This class is useful for working with {@link OptionKey} and {@link OptionValues} but without
 * having to rely on {@link Option} and its associated annotation processor.
 */
public class ReflectionOptionDescriptors implements OptionDescriptors {

    /**
     * Extracts name/value entries from a set of properties based on a given name prefix.
     *
     * @param properties the properties set to extract from
     * @param prefix entries whose names start with this prefix are extracted
     * @param stripPrefix specifies whether to remove the prefix from the names in the returned map
     */
    public static EconomicMap<String, String> extractEntries(Properties properties, String prefix, boolean stripPrefix) {
        EconomicMap<String, String> matches = EconomicMap.create();
        for (Map.Entry<Object, Object> e : properties.entrySet()) {
            String name = (String) e.getKey();
            if (name.startsWith(prefix)) {
                String value = (String) e.getValue();
                if (stripPrefix) {
                    name = name.substring(prefix.length());
                }
                matches.put(name, value);
            }
        }
        return matches;
    }

    private final EconomicMap<String, OptionDescriptor> descriptors = EconomicMap.create();

    public ReflectionOptionDescriptors(Class<?> declaringClass, String... fieldsAndHelp) {
        assert fieldsAndHelp.length % 2 == 0;
        for (int i = 0; i < fieldsAndHelp.length; i += 2) {
            String fieldName = fieldsAndHelp[i];
            String help = fieldsAndHelp[i + 1];
            addOption(declaringClass, fieldName, help);
        }
    }

    public ReflectionOptionDescriptors(Class<?> declaringClass, EconomicMap<String, String> fieldsAndHelp) {
        MapCursor<String, String> cursor = fieldsAndHelp.getEntries();
        while (cursor.advance()) {
            String fieldName = cursor.getKey();
            String help = cursor.getValue();
            addOption(declaringClass, fieldName, help);
        }
    }

    private void addOption(Class<?> declaringClass, String fieldName, String help) {
        try {
            Field f = declaringClass.getDeclaredField(fieldName);
            if (!OptionKey.class.isAssignableFrom(f.getType())) {
                throw new IllegalArgumentException(String.format("Option field must be of type %s: %s", OptionKey.class.getName(), f));
            }
            if (!Modifier.isStatic(f.getModifiers())) {
                throw new IllegalArgumentException(String.format("Option field must be static: %s", f));
            }
            f.setAccessible(true);
            Type declaredType = f.getAnnotatedType().getType();
            if (!(declaredType instanceof ParameterizedType)) {
                throw new IllegalArgumentException(String.format("Option field must have a parameterized type: %s", f));
            }
            ParameterizedType pt = (ParameterizedType) declaredType;
            Type[] actualTypeArguments = pt.getActualTypeArguments();
            assert actualTypeArguments.length == 1;
            Class<?> optionType = (Class<?>) actualTypeArguments[0];
            descriptors.put(fieldName, OptionDescriptor.create(fieldName, optionType, help, declaringClass, fieldName, (OptionKey<?>) f.get(null)));
        } catch (IllegalAccessException | NoSuchFieldException e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Override
    public Iterator<OptionDescriptor> iterator() {
        return descriptors.getValues().iterator();
    }

    @Override
    public OptionDescriptor get(String value) {
        return descriptors.get(value);
    }
}
