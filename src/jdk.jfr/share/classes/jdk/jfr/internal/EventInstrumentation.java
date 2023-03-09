/*
 * Copyright (c) 2016, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.lang.constant.ClassDesc;
import static java.lang.constant.ConstantDescs.*;
import java.lang.constant.MethodTypeDesc;
import java.util.function.Predicate;

import jdk.internal.classfile.*;
import jdk.internal.classfile.attribute.RuntimeVisibleAnnotationsAttribute;

import jdk.jfr.Enabled;
import jdk.jfr.Event;
import jdk.jfr.Name;
import jdk.jfr.Registered;
import jdk.jfr.SettingControl;
import jdk.jfr.SettingDefinition;
import jdk.jfr.internal.event.EventConfiguration;
import jdk.jfr.internal.event.EventWriter;

/**
 * Class responsible for adding instrumentation to a subclass of {@link Event}.
 *
 */
public final class EventInstrumentation {

    record SettingInfo(ClassDesc paramType, String methodName) {
    }

    record FieldInfo(String name, String descriptor) {
    }

    public static final String FIELD_EVENT_THREAD = "eventThread";
    public static final String FIELD_STACK_TRACE = "stackTrace";
    public static final String FIELD_DURATION = "duration";

    static final String FIELD_EVENT_CONFIGURATION = "eventConfiguration";
    static final String FIELD_START_TIME = "startTime";

    private static final String ANNOTATION_NAME_DESCRIPTOR = Name.class.descriptorString();
    private static final String ANNOTATION_REGISTERED_DESCRIPTOR = Registered.class.descriptorString();
    private static final String ANNOTATION_ENABLED_DESCRIPTOR = Enabled.class.descriptorString();
    private static final ClassDesc TYPE_EVENT_CONFIGURATION = ClassDesc.ofDescriptor(EventConfiguration.class.descriptorString());
    private static final ClassDesc TYPE_EVENT_WRITER = ClassDesc.ofDescriptor(EventWriter.class.descriptorString());
    private static final ClassDesc TYPE_EVENT_WRITER_FACTORY = ClassDesc.ofDescriptor("Ljdk/jfr/internal/event/EventWriterFactory;");
    private static final ClassDesc TYPE_SETTING_CONTROL = ClassDesc.ofDescriptor(SettingControl.class.descriptorString());
    private static final String TYPE_OBJECT_DESCRIPTOR = Object.class.descriptorString();
    private static final String TYPE_EVENT_CONFIGURATION_DESCRIPTOR = TYPE_EVENT_CONFIGURATION.descriptorString();
    private static final String TYPE_SETTING_DEFINITION_DESCRIPTOR = SettingDefinition.class.descriptorString();
    private static final String METHOD_COMMIT = "commit";
    private static final MethodTypeDesc METHOD_COMMIT_DESC = MethodTypeDesc.of(CD_void);
    private static final String METHOD_BEGIN = "begin";
    private static final MethodTypeDesc METHOD_BEGIN_DESC = MethodTypeDesc.of(CD_void);
    private static final String METHOD_END = "end";
    private static final MethodTypeDesc METHOD_END_DESC = MethodTypeDesc.of(CD_void);
    private static final String METHOD_IS_ENABLED = "isEnabled";
    private static final MethodTypeDesc METHOD_IS_ENABLED_DESC = MethodTypeDesc.of(CD_boolean);
    private static final String METHOD_TIME_STAMP = "timestamp";
    private static final MethodTypeDesc METHOD_TIME_STAMP_DESC =  MethodTypeDesc.of(CD_long);
    private static final String METHOD_GET_EVENT_WRITER_KEY = "getEventWriter";
    private static final MethodTypeDesc METHOD_GET_EVENT_WRITER_KEY_DESC = MethodTypeDesc.of(TYPE_EVENT_WRITER, CD_long);
    private static final String METHOD_EVENT_SHOULD_COMMIT = "shouldCommit";
    private static final MethodTypeDesc METHOD_EVENT_SHOULD_COMMIT_DESC = MethodTypeDesc.of(CD_boolean);
    private static final String METHOD_EVENT_CONFIGURATION_SHOULD_COMMIT = "shouldCommit";
    private static final MethodTypeDesc METHOD_EVENT_CONFIGURATION_SHOULD_COMMIT_DESC = MethodTypeDesc.of(CD_boolean, CD_long);
    private static final String METHOD_EVENT_CONFIGURATION_GET_SETTING = "getSetting";
    private static final MethodTypeDesc METHOD_EVENT_CONFIGURATION_GET_SETTING_DESC = MethodTypeDesc.of(TYPE_SETTING_CONTROL, CD_int);
    private static final String METHOD_DURATION = "duration";
    private static final MethodTypeDesc METHOD_DURATION_DESC = MethodTypeDesc.of(CD_long, CD_long);
    private static final String METHOD_RESET = "reset";
    private static final MethodTypeDesc METHOD_RESET_DESC = MethodTypeDesc.of(CD_void);
    private static final String METHOD_ENABLED = "enabled";
    private static final MethodTypeDesc METHOD_ENABLED_DESC = MethodTypeDesc.of(CD_boolean);
    private static final String METHOD_SHOULD_COMMIT_LONG = "shouldCommit";
    private static final MethodTypeDesc METHOD_SHOULD_COMMIT_LONG_DESC = MethodTypeDesc.of(CD_boolean, CD_long);

    private final ClassModel classNode;
    private final ClassDesc className;
    private final List<SettingInfo> settingInfos;
    private final List<FieldInfo> fieldInfos;;
    private final String eventName;
    private final Class<?> superClass;
    private final boolean untypedEventConfiguration;
    private final MethodTypeDesc staticCommitMethodDesc;
    private final long eventTypeId;
    private final boolean guardEventConfiguration;
    private final boolean isJDK;

    EventInstrumentation(Class<?> superClass, byte[] bytes, long id, boolean isJDK, boolean guardEventConfiguration) {
        this.eventTypeId = id;
        this.superClass = superClass;
        this.classNode = Classfile.parse(bytes);
        this.className = classNode.thisClass().asSymbol();
        this.settingInfos = buildSettingInfos(superClass, classNode);
        this.fieldInfos = buildFieldInfos(superClass, classNode);
        String n = annotationValue(classNode, ANNOTATION_NAME_DESCRIPTOR, 's');
        this.eventName = n == null ? classNode.thisClass().asInternalName().replace("/", ".") : n;
        this.staticCommitMethodDesc = isJDK ? findStaticCommitMethodDesc(classNode, fieldInfos) : null;
        this.untypedEventConfiguration = hasUntypedConfiguration();
        // Corner case when we are forced to generate bytecode (bytesForEagerInstrumentation)
        // We can't reference EventConfiguration::isEnabled() before event class has been registered,
        // so we add a guard against a null reference.
        this.guardEventConfiguration = guardEventConfiguration;
        this.isJDK = isJDK;
    }

    public static MethodTypeDesc findStaticCommitMethodDesc(ClassModel classNode, List<FieldInfo> fields) {
        StringBuilder sb = new StringBuilder();
        sb.append("(");
        for (FieldInfo field : fields) {
            sb.append(field.descriptor);
        }
        sb.append(")V");
        String desc = sb.toString();
        for (var method : classNode.methods()) {
            if ("commit".equals(method.methodName().stringValue()) && method.methodType().stringValue().equals(desc)) {
                return method.methodTypeSymbol();
            }
        }
        return null;
    }

    private boolean hasUntypedConfiguration() {
        for (var field : classNode.fields()) {
            if (FIELD_EVENT_CONFIGURATION.equals(field.fieldName().stringValue())) {
                return field.fieldType().stringValue().equals(TYPE_OBJECT_DESCRIPTOR);
            }
        }
        throw new InternalError("Class missing configuration field");
    }

    public String getClassName() {
        return classNode.thisClass().name().stringValue().replace("/",".");
    }

    boolean isRegistered() {
        Integer result = annotationValue(classNode, ANNOTATION_REGISTERED_DESCRIPTOR, 'Z');
        if (result != null) {
            return result != 0;
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
        Integer result = annotationValue(classNode, ANNOTATION_ENABLED_DESCRIPTOR, 'Z');
        if (result != null) {
            return result != 0;
        }
        if (superClass != null) {
            Enabled e = superClass.getAnnotation(Enabled.class);
            if (e != null) {
                return e.value();
            }
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private static <T> T annotationValue(ClassModel classNode, String typeDescriptor, char tag) {
        for (var attr : classNode.attributes()) {
            if (attr instanceof RuntimeVisibleAnnotationsAttribute aa) {
                for (var a : aa.annotations()) {
                    if (typeDescriptor.equals(a.className().stringValue())) {
                        var values = a.elements();
                        if (values != null && values.size() == 1) {
                            var vp = values.get(0);
                            if (vp.name().stringValue().equals("value") && (vp.value() instanceof AnnotationValue.OfConstant con) && con.tag() == tag) {
                                return (T) con.constantValue();
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    private static List<SettingInfo> buildSettingInfos(Class<?> superClass, ClassModel classNode) {
        Set<String> methodSet = new HashSet<>();
        List<SettingInfo> settingInfos = new ArrayList<>();
        for (var m : classNode.methods()) {
            for (var attr : m.attributes()) {
                if (attr instanceof RuntimeVisibleAnnotationsAttribute aa) {
                    for (var an : aa.annotations()) {
                        // We can't really validate the method at this
                        // stage. We would need to check that the parameter
                        // is an instance of SettingControl.
                        if (TYPE_SETTING_DEFINITION_DESCRIPTOR.equals(an.className().stringValue())) {
                            String name = m.methodName().stringValue();
                            for (var nameCandidate : aa.annotations()) {
                                if (ANNOTATION_NAME_DESCRIPTOR.equals(nameCandidate.className().stringValue())) {
                                    var values = nameCandidate.elements();
                                    if (values != null && values.size() == 1) {
                                        var vp = values.get(0);
                                        if (vp.name().stringValue().equals("value") && vp.value() instanceof AnnotationValue.OfString s) {
                                            name = Utils.validJavaIdentifier(s.stringValue(), name);
                                        }
                                    }
                                }
                            }
                            var mDesc = m.methodTypeSymbol();
                            if (mDesc.returnType().descriptorString().equals("Z")) {
                                if (mDesc.parameterCount() == 1) {
                                    var paramType = mDesc.parameterType(0);
                                    methodSet.add(m.methodName().stringValue());
                                    settingInfos.add(new SettingInfo(paramType, m.methodName().stringValue()));
                                }
                            }
                        }
                    }
                }
            }
        }
        for (Class<?> c = superClass; c != jdk.internal.event.Event.class; c = c.getSuperclass()) {
            for (java.lang.reflect.Method method : c.getDeclaredMethods()) {
                if (!methodSet.contains(method.getName())) {
                    // skip private method in base classes
                    if (!Modifier.isPrivate(method.getModifiers())) {
                        if (method.getReturnType().equals(Boolean.TYPE)) {
                            if (method.getParameterCount() == 1) {
                                Parameter param = method.getParameters()[0];
                                ClassDesc paramType = ClassDesc.ofDescriptor(param.getType().descriptorString());
                                methodSet.add(method.getName());
                                settingInfos.add(new SettingInfo(paramType, method.getName()));
                            }
                        }
                    }
                }
            }
        }
        return settingInfos;
    }

    private static List<FieldInfo> buildFieldInfos(Class<?> superClass, ClassModel classNode) {
        Set<String> fieldSet = new HashSet<>();
        List<FieldInfo> fieldInfos = new ArrayList<>(classNode.fields().size());
        // These two fields are added by native as 'transient' so they will be
        // ignored by the loop below.
        // The benefit of adding them manually is that we can
        // control in which order they occur and we can add @Name, @Description
        // in Java, instead of in native. It also means code for adding implicit
        // fields for native can be reused by Java.
        fieldInfos.add(new FieldInfo("startTime", CD_long.descriptorString()));
        fieldInfos.add(new FieldInfo("duration", CD_long.descriptorString()));
        for (var field : classNode.fields()) {
            if (!fieldSet.contains(field.fieldName().stringValue()) && isValidField(field.flags().flagsMask(), className(field.fieldType().stringValue()))) {
                FieldInfo fi = new FieldInfo(field.fieldName().stringValue(), field.fieldType().stringValue());
                fieldInfos.add(fi);
                fieldSet.add(field.fieldName().stringValue());
            }
        }
        for (Class<?> c = superClass; c != jdk.internal.event.Event.class; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                // skip private field in base classes
                if (!Modifier.isPrivate(field.getModifiers())) {
                    if (isValidField(field.getModifiers(), field.getType().getName())) {
                        String fieldName = field.getName();
                        if (!fieldSet.contains(fieldName)) {
                            String fieldType = field.getType().descriptorString();
                            fieldInfos.add(new FieldInfo(fieldName, fieldType));
                            fieldSet.add(fieldName);
                        }
                    }
                }
            }
        }
        return fieldInfos;
    }

    public static boolean isValidField(int access, String className) {
        if (Modifier.isTransient(access) || Modifier.isStatic(access)) {
            return false;
        }
        return jdk.jfr.internal.Type.isValidJavaFieldType(className);
    }

    public byte[] buildInstrumented() {
        return classNode.transform(makeInstrumented());
    }

    public byte[] buildUninstrumented() {
        return classNode.transform(makeUninstrumented());
    }

    private Predicate<MethodModel> adaptPredicate = new Predicate<MethodModel>() {
        @Override
        public boolean test(MethodModel methodModel) {
            String methodName = methodModel.methodName().stringValue();
            MethodTypeDesc methodDesc = methodModel.methodTypeSymbol();
            if (methodName.equals(METHOD_IS_ENABLED) && methodDesc.equals(METHOD_IS_ENABLED_DESC))
                return true;
            else if (methodName.equals(METHOD_BEGIN) && methodDesc.equals(METHOD_BEGIN_DESC))
                return true;
            else if (methodName.equals(METHOD_END) && methodDesc.equals(METHOD_END_DESC))
                return true;
            else if (methodName.equals(METHOD_COMMIT) && (methodDesc.equals(METHOD_COMMIT_DESC) || methodDesc.equals(staticCommitMethodDesc)))
                return true;
            else if (methodName.equals(METHOD_EVENT_SHOULD_COMMIT) && methodDesc.equals(METHOD_EVENT_SHOULD_COMMIT_DESC))
                return true;
            else if (isJDK)
                if (methodName.equals(METHOD_ENABLED) && methodDesc.equals(METHOD_ENABLED_DESC))
                    return true;
                else if (methodName.equals(METHOD_SHOULD_COMMIT_LONG) && methodDesc.equals(METHOD_SHOULD_COMMIT_LONG_DESC))
                    return true;
                else if (methodName.equals(METHOD_TIME_STAMP) && methodDesc.equals(METHOD_TIME_STAMP_DESC))
                    return true;
            return false;
        }
    };

    private ClassTransform makeInstrumented() {
        return ClassTransform.transformingMethods(adaptPredicate, (mb, me) -> {
            if (!(me instanceof CodeModel)) mb.accept(me);
            MethodModel mm = mb.original().orElseThrow();
            String methodName = mm.methodName().stringValue();
            MethodTypeDesc methodDesc = mm.methodTypeSymbol();

            // MyEvent#isEnabled()
            if (methodName.equals(METHOD_IS_ENABLED) && methodDesc.equals(METHOD_IS_ENABLED_DESC)) {
                updateEnabledMethod(mb);

            // MyEvent#begin()
            } else if (methodName.equals(METHOD_BEGIN) && methodDesc.equals(METHOD_BEGIN_DESC)) {
                mb.withCode(cob -> {
                    cob.aload(0);
                    cob.invokestatic(TYPE_EVENT_CONFIGURATION, METHOD_TIME_STAMP, METHOD_TIME_STAMP_DESC);
                    cob.putfield(className, FIELD_START_TIME, CD_long);
                    cob.return_();
                });

            // MyEvent#end()
            } else if (methodName.equals(METHOD_END) && methodDesc.equals(METHOD_END_DESC)) {
                mb.withCode(cob -> {
                    cob.aload(0);
                    cob.aload(0);
                    cob.getfield(className, FIELD_START_TIME, CD_long);
                    cob.invokestatic(TYPE_EVENT_CONFIGURATION, METHOD_DURATION, METHOD_DURATION_DESC);
                    cob.putfield(className, FIELD_DURATION, CD_long);
                    cob.return_();
                });

            // MyEvent#commit() or static MyEvent#commit(...)
            } else if (methodName.equals(METHOD_COMMIT)) {
                if (staticCommitMethodDesc != null) {
                    if (methodDesc.equals(METHOD_COMMIT_DESC)) {
                        updateExistingWithEmptyVoidMethod(mb);
                    } else if (methodDesc.equals(staticCommitMethodDesc)) {
                        mb.withCode(cob -> {
                            // indexes the argument type array, the argument type array does not include
                            // 'this'
                            int argIndex = 0;
                            // indexes the proper slot in the local variable table, takes type size into
                            // account, therefore sometimes argIndex != slotIndex
                            int slotIndex = 0;
                            int fieldIndex = 0;
                            ClassDesc[] argumentTypes = staticCommitMethodDesc.parameterArray();
                            Label start = cob.newLabel();
                            Label endTryBlock = cob.newLabel();
                            Label exceptionHandler = cob.newLabel();
                            cob.exceptionCatch(start, endTryBlock, exceptionHandler, CD_Throwable);
                            cob.labelBinding(start);
                            getEventWriter(cob);
                            // stack: [EW]
                            cob.dup();
                            // stack: [EW], [EW]
                            // write begin event
                            getEventConfiguration(cob);
                            // stack: [EW], [EW], [EventConfiguration]
                            cob.constantInstruction(eventTypeId);
                            // stack: [EW], [EW], [EventConfiguration] [long]
                            cob.invokevirtual(TYPE_EVENT_WRITER, EventWriterMethod.BEGIN_EVENT.methodName, EventWriterMethod.BEGIN_EVENT.methodDesc);
                            // stack: [EW], [integer]
                            Label excluded = cob.newLabel();
                            cob.ifeq(excluded);
                            // stack: [EW]
                            // write startTime
                            cob.dup();
                            // stack: [EW], [EW]
                            var argk = TypeKind.fromDescriptor(argumentTypes[argIndex++].descriptorString());
                            cob.loadInstruction(argk, slotIndex);
                            // stack: [EW], [EW], [long]
                            slotIndex += argk.slotSize();
                            cob.invokevirtual(TYPE_EVENT_WRITER, EventWriterMethod.PUT_LONG.methodName, EventWriterMethod.PUT_LONG.methodDesc);
                            // stack: [EW]
                            fieldIndex++;
                            // write duration
                            cob.dup();
                            // stack: [EW], [EW]
                            argk = TypeKind.fromDescriptor(argumentTypes[argIndex++].descriptorString());
                            cob.loadInstruction(argk, slotIndex);
                            // stack: [EW], [EW], [long]
                            slotIndex += argk.slotSize();
                            cob.invokevirtual(TYPE_EVENT_WRITER, EventWriterMethod.PUT_LONG.methodName, EventWriterMethod.PUT_LONG.methodDesc);
                            // stack: [EW]
                            fieldIndex++;
                            // write eventThread
                            cob.dup();
                            // stack: [EW], [EW]
                            cob.invokevirtual(TYPE_EVENT_WRITER, EventWriterMethod.PUT_EVENT_THREAD.methodName, EventWriterMethod.PUT_EVENT_THREAD.methodDesc);
                            // stack: [EW]
                            // write stackTrace
                            cob.dup();
                            // stack: [EW], [EW]
                            cob.invokevirtual(TYPE_EVENT_WRITER, EventWriterMethod.PUT_STACK_TRACE.methodName, EventWriterMethod.PUT_STACK_TRACE.methodDesc);
                            // stack: [EW]
                            // write custom fields
                            while (fieldIndex < fieldInfos.size()) {
                                cob.dup();
                                // stack: [EW], [EW]
                                argk = TypeKind.fromDescriptor(argumentTypes[argIndex++].descriptorString());
                                cob.loadInstruction(argk, slotIndex);
                                // stack:[EW], [EW], [field]
                                slotIndex += argk.slotSize();
                                FieldInfo field = fieldInfos.get(fieldIndex);
                                EventWriterMethod eventMethod = EventWriterMethod.lookupMethod(field);
                                cob.invokevirtual(TYPE_EVENT_WRITER, eventMethod.methodName, eventMethod.methodDesc);
                                // stack: [EW]
                                fieldIndex++;
                            }
                            // stack: [EW]
                            // write end event (writer already on stack)
                            cob.invokevirtual(TYPE_EVENT_WRITER, EventWriterMethod.END_EVENT.methodName, EventWriterMethod.END_EVENT.methodDesc);
                            // stack [integer]
                            // notified -> restart event write attempt
                            cob.ifeq(start);
                            // stack:
                            cob.labelBinding(endTryBlock);
                            Label end = cob.newLabel();
                            cob.goto_(end);
                            cob.labelBinding(exceptionHandler);
                            // stack: [ex]
                            getEventWriter(cob);
                            // stack: [ex] [EW]
                            cob.dup();
                            // stack: [ex] [EW] [EW]
                            Label rethrow = cob.newLabel();
                            cob.if_null(rethrow);
                            // stack: [ex] [EW]
                            cob.dup();
                            // stack: [ex] [EW] [EW]
                            cob.invokevirtual(TYPE_EVENT_WRITER, METHOD_RESET, METHOD_RESET_DESC);
                            cob.labelBinding(rethrow);
                            // stack:[ex] [EW]
                            cob.pop();
                            // stack:[ex]
                            cob.athrow();
                            cob.labelBinding(excluded);
                            // stack: [EW]
                            cob.pop();
                            cob.labelBinding(end);
                            // stack:
                            cob.return_();
                        });
                    } else {
                        mb.accept(me);
                    }
                } else if (methodDesc.equals(METHOD_COMMIT_DESC)) {
                    mb.withCode(cob -> {
                        // if (!isEnable()) {
                        // return;
                        // }
                        Label start = cob.newLabel();
                        Label endTryBlock = cob.newLabel();
                        Label exceptionHandler = cob.newLabel();
                        cob.exceptionCatch(start, endTryBlock, exceptionHandler, CD_Throwable);
                        cob.labelBinding(start);
                        cob.aload(0);
                        cob.invokevirtual(className, METHOD_IS_ENABLED, METHOD_IS_ENABLED_DESC);
                        Label l0 = cob.newLabel();
                        cob.ifne(l0);
                        cob.return_();
                        cob.labelBinding(l0);
                        // long startTime = this.startTime
                        cob.aload(0);
                        cob.getfield(className, FIELD_START_TIME, CD_long);
                        cob.lstore(1);
                        // if (startTime == 0) {
                        // startTime = EventWriter.timestamp();
                        // } else {
                        cob.lload(1);
                        cob.lconst_0();
                        cob.lcmp();
                        Label durationalEvent = cob.newLabel();
                        cob.ifne(durationalEvent);
                        cob.invokestatic(TYPE_EVENT_CONFIGURATION, METHOD_TIME_STAMP, METHOD_TIME_STAMP_DESC);
                        cob.lstore(1);
                        Label commit = cob.newLabel();
                        cob.goto_(commit);
                        // if (duration == 0) {
                        // duration = EventWriter.timestamp() - startTime;
                        // }
                        // }
                        cob.labelBinding(durationalEvent);
                        cob.aload(0);
                        cob.getfield(className, FIELD_DURATION, CD_long);
                        cob.lconst_0();
                        cob.lcmp();
                        cob.ifne(commit);
                        cob.aload(0);
                        cob.invokestatic(TYPE_EVENT_CONFIGURATION, METHOD_TIME_STAMP, METHOD_TIME_STAMP_DESC);
                        cob.lload(1);
                        cob.lsub();
                        cob.putfield(className, FIELD_DURATION, CD_long);
                        cob.labelBinding(commit);
                        // if (shouldCommit()) {
                        cob.aload(0);
                        cob.invokevirtual(className, METHOD_EVENT_SHOULD_COMMIT, METHOD_EVENT_SHOULD_COMMIT_DESC);
                        Label end = cob.newLabel();
                        cob.ifeq(end);
                        getEventWriter(cob);
                        // stack: [EW]
                        cob.dup();
                        // stack: [EW] [EW]
                        getEventConfiguration(cob);
                        // stack: [EW] [EW] [EC]
                        cob.constantInstruction(eventTypeId);
                        cob.invokevirtual(TYPE_EVENT_WRITER, EventWriterMethod.BEGIN_EVENT.methodName, EventWriterMethod.BEGIN_EVENT.methodDesc);
                        Label excluded = cob.newLabel();
                        // stack: [EW] [int]
                        cob.ifeq(excluded);
                        // stack: [EW]
                        int fieldIndex = 0;
                        cob.dup();
                        // stack: [EW] [EW]
                        cob.lload(1);
                        // stack: [EW] [EW] [long]
                        cob.invokevirtual(TYPE_EVENT_WRITER, EventWriterMethod.PUT_LONG.methodName, EventWriterMethod.PUT_LONG.methodDesc);
                        // stack: [EW]
                        fieldIndex++;
                        cob.dup();
                        // stack: [EW] [EW]
                        cob.aload(0);
                        // stack: [EW] [EW] [this]
                        cob.getfield(className, FIELD_DURATION, CD_long);
                        // stack: [EW] [EW] [long]
                        cob.invokevirtual(TYPE_EVENT_WRITER, EventWriterMethod.PUT_LONG.methodName, EventWriterMethod.PUT_LONG.methodDesc);
                        // stack: [EW]
                        fieldIndex++;
                        cob.dup();
                        // stack: [EW] [EW]
                        cob.invokevirtual(TYPE_EVENT_WRITER, EventWriterMethod.PUT_EVENT_THREAD.methodName, EventWriterMethod.PUT_EVENT_THREAD.methodDesc);
                        // stack: [EW]
                        cob.dup();
                        // stack: [EW] [EW]
                        cob.invokevirtual(TYPE_EVENT_WRITER, EventWriterMethod.PUT_STACK_TRACE.methodName, EventWriterMethod.PUT_STACK_TRACE.methodDesc);
                        // stack: [EW]
                        while (fieldIndex < fieldInfos.size()) {
                            FieldInfo field = fieldInfos.get(fieldIndex);
                            cob.dup();
                            // stack: [EW] [EW]
                            cob.aload(0);
                            // stack: [EW] [EW] [this]
                            cob.getfield(className, field.name, ClassDesc.ofDescriptor(field.descriptor));
                            // stack: [EW] [EW] <T>
                            EventWriterMethod eventMethod = EventWriterMethod.lookupMethod(field);
                            cob.invokevirtual(TYPE_EVENT_WRITER, eventMethod.methodName, eventMethod.methodDesc);
                            // stack: [EW]
                            fieldIndex++;
                        }
                        // stack:[EW]
                        cob.invokevirtual(TYPE_EVENT_WRITER, EventWriterMethod.END_EVENT.methodName, EventWriterMethod.END_EVENT.methodDesc);
                        // stack [int]
                        // notified -> restart event write attempt
                        cob.ifeq(start);
                        cob.labelBinding(endTryBlock);
                        cob.goto_(end);
                        cob.labelBinding(exceptionHandler);
                        // stack: [ex]
                        getEventWriter(cob);
                        // stack: [ex] [EW]
                        cob.dup();
                        // stack: [ex] [EW] [EW]
                        Label rethrow = cob.newLabel();
                        cob.if_null(rethrow);
                        // stack: [ex] [EW]
                        cob.dup();
                        // stack: [ex] [EW] [EW]
                        cob.invokevirtual(TYPE_EVENT_WRITER, METHOD_RESET, METHOD_RESET_DESC);
                        cob.labelBinding(rethrow);
                        // stack:[ex] [EW]
                        cob.pop();
                        // stack:[ex]
                        cob.athrow();
                        cob.labelBinding(excluded);
                        // stack: [EW]
                        cob.pop();
                        cob.labelBinding(end);
                        // stack:
                        cob.return_();
                    });
                } else {
                    mb.accept(me);
                }
            // MyEvent#shouldCommit()
            } else if (methodName.equals(METHOD_EVENT_SHOULD_COMMIT) && methodDesc.equals(METHOD_EVENT_SHOULD_COMMIT_DESC)) {
                mb.withCode(cob -> {
                    Label fail = cob.newLabel();
                    if (guardEventConfiguration) {
                        getEventConfiguration(cob);
                        cob.if_null(fail);
                    }
                    // if (!eventHandler.shouldCommit(duration) goto fail;
                    getEventConfiguration(cob);
                    cob.aload(0);
                    cob.getfield(className, FIELD_DURATION, CD_long);
                    cob.invokevirtual(TYPE_EVENT_CONFIGURATION, METHOD_EVENT_CONFIGURATION_SHOULD_COMMIT, METHOD_EVENT_CONFIGURATION_SHOULD_COMMIT_DESC);
                    cob.ifeq(fail);
                    for (int index = 0; index < settingInfos.size(); index++) {
                        SettingInfo si = settingInfos.get(index);
                        // if (!settingsMethod(eventHandler.settingX)) goto fail;
                        cob.aload(0);
                        if (untypedEventConfiguration) {
                            cob.getstatic(className, FIELD_EVENT_CONFIGURATION, ClassDesc.ofDescriptor(TYPE_OBJECT_DESCRIPTOR));
                        } else {
                            cob.getstatic(className, FIELD_EVENT_CONFIGURATION, ClassDesc.ofDescriptor(TYPE_EVENT_CONFIGURATION_DESCRIPTOR));
                        }
                        cob.checkcast(TYPE_EVENT_CONFIGURATION);
                        cob.constantInstruction(index);
                        cob.invokevirtual(TYPE_EVENT_CONFIGURATION, METHOD_EVENT_CONFIGURATION_GET_SETTING, METHOD_EVENT_CONFIGURATION_GET_SETTING_DESC);
                        cob.checkcast(si.paramType());
                        cob.invokevirtual(className, si.methodName, MethodTypeDesc.of(CD_boolean, si.paramType()));
                        cob.ifeq(fail);
                    }
                    // return true
                    cob.iconst_1();
                    cob.ireturn();
                    // return false
                    cob.labelBinding(fail);
                    cob.iconst_0();
                    cob.ireturn();
                });
            } else if (isJDK) {
                if (methodName.equals(METHOD_ENABLED) && methodDesc.equals(METHOD_ENABLED_DESC)) {
                    updateEnabledMethod(mb);
                } else if (methodName.equals(METHOD_SHOULD_COMMIT_LONG) && methodDesc.equals(METHOD_SHOULD_COMMIT_LONG_DESC)) {
                    mb.withCode(cob -> {
                        Label fail = cob.newLabel();
                        if (guardEventConfiguration) {
                            // if (eventConfiguration == null) goto fail;
                            getEventConfiguration(cob);
                            cob.if_null(fail);
                        }
                        // return eventConfiguration.shouldCommit(duration);
                        getEventConfiguration(cob);
                        cob.lload(0);
                        cob.invokevirtual(TYPE_EVENT_CONFIGURATION, METHOD_EVENT_CONFIGURATION_SHOULD_COMMIT, METHOD_EVENT_CONFIGURATION_SHOULD_COMMIT_DESC);
                        cob.ireturn();
                        // fail:
                        cob.labelBinding(fail);
                        // return false
                        cob.iconst_0();
                        cob.ireturn();
                    });
                } else if (methodName.equals(METHOD_TIME_STAMP) && methodDesc.equals(METHOD_TIME_STAMP_DESC)) {
                    mb.withCode(cob -> {
                        cob.invokestatic(TYPE_EVENT_CONFIGURATION, METHOD_TIME_STAMP, METHOD_TIME_STAMP_DESC);
                        cob.lreturn();
                    });
                } else {
                   mb.accept(me);
                }
            } else {
                mb.accept(me);
            }
        });
    }

    private void updateEnabledMethod(MethodBuilder mb) {
        mb.withCode(cob -> {
            Label nullLabel = cob.newLabel();
            if (guardEventConfiguration) {
                getEventConfiguration(cob);
                cob.if_null(nullLabel);
            }
            getEventConfiguration(cob);
            cob.invokevirtual(TYPE_EVENT_CONFIGURATION, METHOD_IS_ENABLED, METHOD_IS_ENABLED_DESC);
            cob.ireturn();
            if (guardEventConfiguration) {
                cob.labelBinding(nullLabel);
                cob.iconst_0();
                cob.ireturn();
            }
        });
    }

    private void getEventWriter(CodeBuilder cob) {
        cob.constantInstruction(EventWriterKey.getKey());
        cob.invokestatic(TYPE_EVENT_WRITER_FACTORY, METHOD_GET_EVENT_WRITER_KEY, METHOD_GET_EVENT_WRITER_KEY_DESC);
    }

    private void getEventConfiguration(CodeBuilder cob) {
        if (untypedEventConfiguration) {
            cob.getstatic(className, FIELD_EVENT_CONFIGURATION, CD_Object);
        } else {
            cob.getstatic(className, FIELD_EVENT_CONFIGURATION, TYPE_EVENT_CONFIGURATION);
        }
    }

    private ClassTransform makeUninstrumented() {
        return ClassTransform.transformingMethods(adaptPredicate, (mb, me) -> {
            MethodModel mm = mb.original().orElseThrow();
            String methodName = mm.methodName().stringValue();
            MethodTypeDesc methodDesc = mm.methodTypeSymbol();

            if ((methodName.equals(METHOD_EVENT_SHOULD_COMMIT) && methodDesc.equals(METHOD_EVENT_SHOULD_COMMIT_DESC))
                        || (methodName.equals(METHOD_IS_ENABLED) && methodDesc.equals(METHOD_IS_ENABLED_DESC))) {
                    mb.withCode(cob ->
                            cob.iconst_0().ireturn());
                } else if ((methodName.equals(METHOD_COMMIT) && (methodDesc.equals(METHOD_COMMIT_DESC) || methodDesc.equals(staticCommitMethodDesc)))
                        || (methodName.equals(METHOD_BEGIN) && methodDesc.equals(METHOD_BEGIN_DESC))
                        || (methodName.equals(METHOD_END) && methodDesc.equals(METHOD_END_DESC))) {
                    mb.withCode(cob ->
                            cob.return_());
                } else {
                    mb.accept(me);
                }
        });
    }

    private final void updateExistingWithEmptyVoidMethod(MethodBuilder voidMethod) {
        voidMethod.withCode(cob -> cob.return_());
    }

    public String getEventName() {
        return eventName;
    }

    private static String className(String descriptor) {
        return switch (descriptor.charAt(0)) {
            case 'L' -> descriptor.substring(1, descriptor.length()-1).replaceAll("/", ".");
            case '[' -> descriptor;
            default -> TypeKind.fromDescriptor(descriptor).typeName();
        };
    }
}
