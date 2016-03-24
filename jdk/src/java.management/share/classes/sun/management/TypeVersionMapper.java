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
package sun.management;

import java.lang.management.ThreadInfo;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import javax.management.openmbean.ArrayType;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.OpenDataException;
import javax.management.openmbean.OpenType;
import javax.management.openmbean.SimpleType;
import javax.management.openmbean.TabularType;
import static sun.management.Util.toStringArray;

/**
 * Provides simplistic support for versioning of {@linkplain CompositeType} instances
 * based on the latest version and filtering out certain items.
 */
final class TypeVersionMapper {
    private static final class Singleton {
        private final static TypeVersionMapper INSTANCE = new TypeVersionMapper();
    }

    final static String V5 = "J2SE 5.0";
    final static String V6 = "Java SE 6";

    private final Map<String, Map<String, Predicate<String>>> filterMap;

    private TypeVersionMapper() {
        filterMap = new HashMap<>();
        setupStackTraceElement();
        setupThreadInfo();
    }

    public static TypeVersionMapper getInstance() {
        return Singleton.INSTANCE;
    }

    private void setupStackTraceElement() {
        Map<String, Predicate<String>> filter = new HashMap<>();
        filterMap.put(StackTraceElement.class.getName(), filter);
        filter.put(V5, StackTraceElementCompositeData::isV6Attribute);
        filter.put(V6, StackTraceElementCompositeData::isV6Attribute);
    }

    private void setupThreadInfo() {
        Map<String, Predicate<String>> filter = new HashMap<>();
        filterMap.put(ThreadInfo.class.getName(), filter);
        filter.put(V5, ThreadInfoCompositeData::isV5Attribute);
        filter.put(V6, ThreadInfoCompositeData::isV6Attribute);
    }

    /**
     * Retrieves the specified version of a {@linkplain CompositeType} instance.
     * @param type The current (latest) version of {@linkplain CompositeType}
     * @param version The version identifier (eg. {@linkplain TypeVersionMapper#V5})
     * @return Returns the {@linkplain CompositeType} corresponding to the requested
     *         version.
     * @throws OpenDataException
     */
    CompositeType getVersionedCompositeType(CompositeType type, String version)
        throws OpenDataException
    {
        Predicate<String> filter = getFilter(type.getTypeName(), version);
        if (filter == null) {
            return type;
        }

        List<String> itemNames = new ArrayList<>();
        List<String> itemDesc = new ArrayList<>();
        List<OpenType<?>> itemTypes = new ArrayList<>();

        for(String item : type.keySet()) {
            if (filter.test(item)) {
                itemNames.add(item);
                itemDesc.add(type.getDescription(item));
                itemTypes.add(getVersionedType(
                    type.getType(item),
                    version
                ));
            }
        }
        return new CompositeType(
            type.getTypeName(),
            version != null ? version + " " + type.getDescription() : type.getDescription(),
            itemNames.toArray(new String[itemNames.size()]),
            itemDesc.toArray(new String[itemDesc.size()]),
            itemTypes.toArray(new OpenType<?>[itemTypes.size()])
        );
    }

    private OpenType<?> getVersionedType(OpenType<?> type, String version)
        throws OpenDataException
    {
        if (type instanceof ArrayType) {
            return getVersionedArrayType((ArrayType)type, version);
        }
        if (type instanceof CompositeType) {
            return getVersionedCompositeType((CompositeType)type, version);
        }
        if (type instanceof TabularType) {
            return getVersionedTabularType((TabularType)type, version);
        }
        return type;
    }

    private ArrayType<?> getVersionedArrayType(ArrayType<?> type, String version)
        throws OpenDataException
    {
        if (type.isPrimitiveArray()) {
            return type;
        }
        OpenType<?> ot = getVersionedType(
            type.getElementOpenType(),
            version
        );
        if (ot instanceof SimpleType) {
            return new ArrayType<>((SimpleType<?>)ot, type.isPrimitiveArray());
        } else {
            return new ArrayType<>(type.getDimension(), ot);
        }
    }

    private TabularType getVersionedTabularType(TabularType type, String version)
        throws OpenDataException
    {
        CompositeType ct = getVersionedCompositeType(
            type.getRowType(),
            version
        );

        if (ct != null) {
            return new TabularType(
                type.getTypeName(), type.getDescription(), ct,
                toStringArray(type.getIndexNames()));
        }
        return null;
    }

    private Predicate<String> getFilter(String type, String version) {
        Map<String, Predicate<String>> versionMap = filterMap.get(type);
        if (versionMap == null) {
            return null;
        }

        return versionMap.get(version);
    }
}
