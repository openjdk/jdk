/*
 * Copyright (c) 2016, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.jfr.internal.util.Bytecode.classDesc;

import java.lang.classfile.Annotation;
import java.lang.classfile.AnnotationElement;
import java.lang.classfile.AnnotationValue;
import java.lang.classfile.Attribute;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.FieldModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.attribute.RuntimeVisibleAnnotationsAttribute;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.constant.ConstantDescs;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jdk.jfr.Enabled;
import jdk.jfr.Name;
import jdk.jfr.Registered;
import jdk.jfr.SettingControl;
import jdk.jfr.SettingDefinition;
import jdk.jfr.Throttle;
import jdk.jfr.internal.util.Bytecode;
import jdk.jfr.internal.util.ImplicitFields;
import jdk.jfr.internal.util.Bytecode.FieldDesc;
import jdk.jfr.internal.util.Bytecode.MethodDesc;
import jdk.jfr.internal.util.Bytecode.SettingDesc;
import jdk.jfr.internal.util.Utils;

final class ClassInspector {
    private static final ClassDesc TYPE_SETTING_DEFINITION = Bytecode.classDesc(SettingDefinition.class);
    private static final ClassDesc ANNOTATION_REGISTERED = classDesc(Registered.class);
    private static final ClassDesc ANNOTATION_NAME = classDesc(Name.class);
    private static final ClassDesc ANNOTATION_ENABLED = classDesc(Enabled.class);
    private static final ClassDesc ANNOTATION_REMOVE_FIELDS = classDesc(RemoveFields.class);
    private static final ClassDesc ANNOTATION_THROTTLE = classDesc(Throttle.class);
    private static final String[] EMPTY_STRING_ARRAY = {};

    private final ClassModel classModel;
    private final Class<?> superClass;
    private final boolean isJDK;
    private final ImplicitFields implicitFields;
    private final List<SettingDesc> settingsDescs = new ArrayList<>();
    private final List<FieldDesc> fieldDescs = new ArrayList<>();
    private final String className;

    ClassInspector(Class<?> superClass, byte[] bytes, boolean isJDK) {
        this.superClass = superClass;
        this.classModel = ClassFile.of().parse(bytes);
        this.isJDK = isJDK;
        this.className = classModel.thisClass().asInternalName().replace("/", ".");
        this.implicitFields = determineImplicitFields();
    }

    String getClassName() {
        return className;
    }

    MethodDesc findStaticCommitMethod() {
        if (!isJDK) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("(");
        for (FieldDesc field : fieldDescs) {
            sb.append(field.type().descriptorString());
        }
        sb.append(")V");
        MethodDesc m = MethodDesc.of("commit", sb.toString());
        for (MethodModel method : classModel.methods()) {
            if (m.matches(method)) {
                return m;
            }
        }
        return null;
    }

    String getEventName() {
        String name = annotationValue(ANNOTATION_NAME, String.class, null);
        return name == null ? getClassName() : name;
    }

    boolean isRegistered() {
        Boolean result = annotationValue(ANNOTATION_REGISTERED, Boolean.class, true);
        if (result != null) {
            return result.booleanValue();
        }
        if (superClass != null) {
            Registered r = superClass.getAnnotation(Registered.class);
            if (r != null) {
                return r.value();
            }
        }
        return true;
    }

    boolean isEnabled() {
        Boolean result = annotationValue(ANNOTATION_ENABLED, Boolean.class, true);
        if (result != null) {
            return result.booleanValue();
        }
        if (superClass != null) {
            Enabled e = superClass.getAnnotation(Enabled.class);
            if (e != null) {
                return e.value();
            }
        }
        return true;
    }

    boolean isThrottled() {
        String result = annotationValue(ANNOTATION_THROTTLE, String.class, "off");
        if (result != null) {
            return true;
        }
        if (superClass != null) {
            Throttle t = superClass.getAnnotation(Throttle.class);
            if (t != null) {
                return true;
            }
        }
        return false;
    }

    boolean hasStaticMethod(MethodDesc method) {
        for (MethodModel m : classModel.methods()) {
            if (Modifier.isStatic(m.flags().flagsMask())) {
                return method.matches(m);
            }
        }
        return false;
    }

    static boolean isValidField(int access, ClassDesc classDesc) {
        String className = classDesc.packageName();
        if (!className.isEmpty()) {
            className = className + ".";
        }
        className += classDesc.displayName();
        return isValidField(access, className);
    }

    static boolean isValidField(int access, String className) {
        if (Modifier.isTransient(access) || Modifier.isStatic(access)) {
            return false;
        }
        return Type.isValidJavaFieldType(className);
    }

    List<SettingDesc> getSettings() {
        return settingsDescs;
    }

    List<FieldDesc> getFields() {
        return fieldDescs;
    }

    boolean hasDuration() {
        return implicitFields.hasDuration();
    }

    boolean hasStackTrace() {
        return implicitFields.hasStackTrace();
    }

    boolean hasEventThread() {
        return implicitFields.hasEventThread();
    }

    ClassDesc getClassDesc() {
        return classModel.thisClass().asSymbol();
    }

    ClassModel getClassModel() {
        return classModel;
    }

    boolean isJDK() {
        return isJDK;
    }

    private ImplicitFields determineImplicitFields() {
        if (isJDK) {
            Class<?> eventClass = MirrorEvents.find(isJDK, getClassName());
            if (eventClass != null) {
                return new ImplicitFields(eventClass);
            }
        }
        ImplicitFields ifs = new ImplicitFields(superClass);
        String[] value = annotationValue(ANNOTATION_REMOVE_FIELDS, String[].class, EMPTY_STRING_ARRAY);
        if (value != null) {
            ifs.removeFields(value);
        }
        return ifs;
    }

    private Annotation getFirstAnnotation(ClassDesc classDesc) {
        for (RuntimeVisibleAnnotationsAttribute attribute : classModel.findAttributes(Attributes.runtimeVisibleAnnotations())) {
            for (Annotation a : attribute.annotations()) {
                if (a.classSymbol().equals(classDesc)) {
                    return a;
                }
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    // Only supports String, String[] and Boolean values
    private <T> T annotationValue(ClassDesc classDesc, Class<T> type, T defaultValue) {
        Annotation annotation = getFirstAnnotation(classDesc);
        if (annotation == null) {
            return null;
        }
        // Default values are not stored in the annotation element, so if the
        // element-value pair is empty, return the default value.
        if (annotation.elements().isEmpty()) {
            return defaultValue;
        }

        AnnotationElement ae = annotation.elements().getFirst();
        if (!ae.name().equalsString("value")) {
            return null;
        }
        AnnotationValue a = ae.value();
        if (a instanceof AnnotationValue.OfBoolean ofb && type.equals(Boolean.class)) {
            Boolean b = ofb.booleanValue();
            return (T) b;
        }
        if (a instanceof AnnotationValue.OfString ofs && type.equals(String.class)) {
            String s = ofs.stringValue();
            return (T) s;
        }
        if (a instanceof AnnotationValue.OfArray ofa && type.equals(String[].class)) {
            List<AnnotationValue> list = ofa.values();
            String[] array = new String[list.size()];
            int index = 0;
            for (AnnotationValue av : list) {
                var avs = (AnnotationValue.OfString) av;
                array[index++] = avs.stringValue();
            }
            return (T) array;
        }
        return null;
    }

    void buildSettings() {
        Set<String> foundMethods = new HashSet<>();
        buildClassSettings(foundMethods);
        buildSuperClassSettings(foundMethods);
    }

    private void buildClassSettings(Set<String> foundMethods) {
        for (MethodModel m : classModel.methods()) {
            for (Attribute<?> attribute : m.attributes()) {
                if (attribute instanceof RuntimeVisibleAnnotationsAttribute rvaa) {
                    for (Annotation a : rvaa.annotations()) {
                        // We can't really validate the method at this
                        // stage. We would need to check that the parameter
                        // is an instance of SettingControl.
                        if (a.classSymbol().equals(TYPE_SETTING_DEFINITION)) {
                            String name = m.methodName().stringValue();
                            // Use @Name if it exists
                            for (Annotation nameCandidate : rvaa.annotations()) {
                                if (nameCandidate.className().equalsString(ANNOTATION_NAME.descriptorString())) {
                                    if (nameCandidate.elements().size() == 1) {
                                        AnnotationElement ae = nameCandidate.elements().getFirst();
                                        if (ae.name().equalsString("value")) {
                                            if (ae.value() instanceof AnnotationValue.OfString s) {
                                                name = Utils.validJavaIdentifier(s.stringValue(), name);
                                            }
                                        }
                                    }
                                }
                            }
                            // Add setting if method returns boolean and has one parameter
                            MethodTypeDesc mtd = m.methodTypeSymbol();
                            if (ConstantDescs.CD_boolean.equals(mtd.returnType())) {
                                if (mtd.parameterList().size() == 1) {
                                    ClassDesc type = mtd.parameterList().getFirst();
                                    if (type.isClassOrInterface()) {
                                        String methodName = m.methodName().stringValue();
                                        foundMethods.add(methodName);
                                        settingsDescs.add(new SettingDesc(type, methodName));
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private void buildSuperClassSettings(Set<String> foundMethods) {
        for (Class<?> c = superClass; jdk.internal.event.Event.class != c; c = c.getSuperclass()) {
            for (java.lang.reflect.Method method : c.getDeclaredMethods()) {
                if (!foundMethods.contains(method.getName())) {
                    buildSettingsMethod(foundMethods, method);
                }
            }
        }
    }

    private void buildSettingsMethod(Set<String> foundMethods, java.lang.reflect.Method method) {
        // Skip private methods in base classes
        if (!Modifier.isPrivate(method.getModifiers())) {
            if (method.getReturnType().equals(Boolean.TYPE)) {
                if (method.getParameterCount() == 1) {
                    Class<?> type = method.getParameters()[0].getType();
                    if (SettingControl.class.isAssignableFrom(type)) {
                        ClassDesc paramType = Bytecode.classDesc(type);
                        foundMethods.add(method.getName());
                        settingsDescs.add(new SettingDesc(paramType, method.getName()));
                    }
                }
            }
        }
    }

    void buildFields() {
        Set<String> foundFields = new HashSet<>();
        // These two fields are added by native as 'transient' so they will be
        // ignored by the loop below.
        // The benefit of adding them manually is that we can
        // control in which order they occur and we can add @Name, @Description
        // in Java, instead of in native. It also means code for adding implicit
        // fields for native can be reused by Java.
        fieldDescs.add(ImplicitFields.FIELD_START_TIME);
        if (implicitFields.hasDuration()) {
            fieldDescs.add(ImplicitFields.FIELD_DURATION);
        }
        for (FieldModel field : classModel.fields()) {
            if (!foundFields.contains(field.fieldName().stringValue()) && isValidField(field.flags().flagsMask(), field.fieldTypeSymbol())) {
                fieldDescs.add(FieldDesc.of(field.fieldTypeSymbol(), field.fieldName().stringValue()));
                foundFields.add(field.fieldName().stringValue());
            }
        }
        for (Class<?> c = superClass; jdk.internal.event.Event.class != c; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                // Skip private fields in base classes
                if (!Modifier.isPrivate(field.getModifiers())) {
                    if (isValidField(field.getModifiers(), field.getType().getName())) {
                        String fieldName = field.getName();
                        if (!foundFields.contains(fieldName)) {
                            fieldDescs.add(FieldDesc.of(field.getType(), fieldName));
                            foundFields.add(fieldName);
                        }
                    }
                }
            }
        }
    }
}
