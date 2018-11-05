/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jfr.internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import jdk.jfr.AnnotationElement;
import jdk.jfr.Event;
import jdk.jfr.SettingControl;
import jdk.jfr.ValueDescriptor;

/**
 * Internal data structure that describes a type,
 *
 * Used to create event types, value descriptor and annotations.
 *
 */
public class Type implements Comparable<Type> {
    public static final String SUPER_TYPE_ANNOTATION = java.lang.annotation.Annotation.class.getName();
    public static final String SUPER_TYPE_SETTING = SettingControl.class.getName();
    public static final String SUPER_TYPE_EVENT = Event.class.getName();
    public static final String EVENT_NAME_PREFIX = "jdk.";
    public static final String TYPES_PREFIX = "jdk.types.";
    public static final String SETTINGS_PREFIX = "jdk.settings.";


    // Initialization of known types
    private final static Map<Type, Class<?>> knownTypes = new HashMap<>();
    static final Type BOOLEAN = register(boolean.class, new Type("boolean", null, 4));
    static final Type CHAR = register(char.class, new Type("char", null, 5));
    static final Type FLOAT = register(float.class, new Type("float", null, 6));
    static final Type DOUBLE = register(double.class, new Type("double", null, 7));
    static final Type BYTE = register(byte.class, new Type("byte", null, 8));
    static final Type SHORT = register(short.class, new Type("short", null, 9));
    static final Type INT = register(int.class, new Type("int", null, 10));
    static final Type LONG = register(long.class, new Type("long", null, 11));
    static final Type CLASS = register(Class.class, new Type("java.lang.Class", null, 20));
    static final Type STRING = register(String.class, new Type("java.lang.String", null, 21));
    static final Type THREAD = register(Thread.class, new Type("java.lang.Thread", null, 22));
    static final Type STACK_TRACE = register(null, new Type(TYPES_PREFIX + "StackTrace", null, 23));

    private final AnnotationConstruct annos = new AnnotationConstruct();
    private final String name;
    private final String superType;
    private final boolean constantPool;
    private final ArrayList<ValueDescriptor> fields = new ArrayList<>();
    private Boolean simpleType; // calculated lazy
    private boolean remove = true;
    private long id;

    /**
     * Creates a type
     *
     * @param javaTypeName i.e "java.lang.String"
     * @param superType i.e "java.lang.Annotation"
     * @param id the class id that represents the class in the JVM
     *
     */
    public Type(String javaTypeName, String superType, long typeId) {
        this(javaTypeName, superType, typeId, false);
    }

    Type(String javaTypeName, String superType, long typeId, boolean contantPool) {
        this(javaTypeName, superType, typeId, contantPool, null);
    }

    Type(String javaTypeName, String superType, long typeId, boolean contantPool, Boolean simpleType) {
        Objects.requireNonNull(javaTypeName);

        if (!isValidJavaIdentifier(javaTypeName)) {
            throw new IllegalArgumentException(javaTypeName + " is not a valid Java identifier");
        }
        this.constantPool = contantPool;
        this.superType = superType;
        this.name = javaTypeName;
        this.id = typeId;
        this.simpleType = simpleType;
    }

    static boolean isDefinedByJVM(long id) {
        return id < JVM.RESERVED_CLASS_ID_LIMIT;
    }

    public static long getTypeId(Class<?> clazz) {
        Type type = Type.getKnownType(clazz);
        return type == null ? JVM.getJVM().getTypeId(clazz) : type.getId();
    }

    static Collection<Type> getKnownTypes() {
        return knownTypes.keySet();
    }

    public static boolean isValidJavaIdentifier(String identifier) {
        if (identifier.isEmpty()) {
            return false;
        }
        if (!Character.isJavaIdentifierStart(identifier.charAt(0))) {
            return false;
        }
        for (int i = 1; i < identifier.length(); i++) {
            char c = identifier.charAt(i);
            if (c != '.') {
                if (!Character.isJavaIdentifierPart(c)) {
                    return false;
                }
            }
        }
        return true;
    }

    public static boolean isValidJavaFieldType(String name) {
        for (Map.Entry<Type, Class<?>> entry : knownTypes.entrySet()) {
            Class<?> clazz = entry.getValue();
            if (clazz != null && name.equals(clazz.getName())) {
                return true;
            }
        }
        return false;
    }

    public static Type getKnownType(String typeName) {
        for (Type type : knownTypes.keySet()) {
            if (type.getName().equals(typeName)) {
                return type;
            }
        }
        return null;
    }

    static boolean isKnownType(Class<?> type) {
        if (type.isPrimitive()) {
            return true;
        }
        if (type.equals(Class.class) || type.equals(Thread.class) || type.equals(String.class)) {
            return true;
        }
        return false;
    }

    public static Type getKnownType(Class<?> clazz) {
        for (Map.Entry<Type, Class<?>> entry : knownTypes.entrySet()) {
            if (clazz != null && clazz.equals(entry.getValue())) {
                return entry.getKey();
            }
        }
        return null;
    }

    public String getName() {
        return name;
    }

    public String getLogName() {
       return getName() + "(" + getId() + ")";
    }

    public List<ValueDescriptor> getFields() {
        return fields;
    }

    public boolean isSimpleType() {
        if (simpleType == null) {
            simpleType = calculateSimpleType();
        }
        return simpleType.booleanValue();
    }

    private boolean calculateSimpleType() {
        if (fields.size() != 1) {
            return false;
        }
        // annotation, settings and event can never be simple types
        return superType == null;
    }

    public boolean isDefinedByJVM() {
        return id < JVM.RESERVED_CLASS_ID_LIMIT;
    }

    private static Type register(Class<?> clazz, Type type) {
        knownTypes.put(type, clazz);
        return type;
    }

    public void add(ValueDescriptor valueDescriptor) {
        Objects.requireNonNull(valueDescriptor);
        fields.add(valueDescriptor);
    }

    void trimFields() {
        fields.trimToSize();
    }

    void setAnnotations(List<AnnotationElement> annotations) {
        annos.setAnnotationElements(annotations);
    }

    public String getSuperType() {
        return superType;
    }

    public long getId() {
        return id;
    }

    public boolean isConstantPool() {
        return constantPool;
    }

    public String getLabel() {
        return annos.getLabel();
    }

    public List<AnnotationElement> getAnnotationElements() {
        return annos.getUnmodifiableAnnotationElements();
    }

    public <T> T getAnnotation(Class<? extends java.lang.annotation.Annotation> clazz) {
        return annos.getAnnotation(clazz);
    }

    public String getDescription() {
        return annos.getDescription();
    }

    @Override
    public int hashCode() {
        return Long.hashCode(id);
    }

    @Override
    public boolean equals(Object object) {
        if (object instanceof Type) {
            Type that = (Type) object;
            return that.id == this.id;
        }
        return false;
    }

    @Override
    public int compareTo(Type that) {
        return Long.compare(this.id, that.id);
    }

    void log(String action, LogTag logTag, LogLevel level) {
        if (Logger.shouldLog(logTag, level) && !isSimpleType()) {
            Logger.log(logTag, LogLevel.TRACE, action + " " + typeText() + " " + getLogName() + " {");
            for (ValueDescriptor v : getFields()) {
                String array = v.isArray() ? "[]" : "";
                Logger.log(logTag, LogLevel.TRACE, "  " + v.getTypeName() + array + " " + v.getName() + ";");
            }
            Logger.log(logTag, LogLevel.TRACE, "}");
        } else {
            if (Logger.shouldLog(logTag, LogLevel.INFO) && !isSimpleType()) {
                Logger.log(logTag, LogLevel.INFO, action + " " + typeText() + " " + getLogName());
            }
        }
    }

    private String typeText() {
        if (this instanceof PlatformEventType) {
            return "event type";
        }
        if (Type.SUPER_TYPE_SETTING.equals(superType)) {
            return "setting type";
        }
        if (Type.SUPER_TYPE_ANNOTATION.equals(superType)) {
            return "annotation type";
        }
        return "type";
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getLogName());
        if (!getFields().isEmpty()) {
            sb.append(" {\n");
            for (ValueDescriptor td : getFields()) {
                sb.append("  type=" + td.getTypeName() + "(" + td.getTypeId() + ") name=" + td.getName() + "\n");
            }
            sb.append("}\n");
        }
        return sb.toString();
    }

    public void setRemove(boolean remove) {
       this.remove = remove;
    }

    public boolean getRemove() {
        return remove;
    }

    public void setId(long id) {
        this.id = id;
    }
}
