/*
 * Copyright (c) 2016, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import jdk.internal.classfile.Annotation;
import jdk.internal.classfile.AnnotationElement;
import jdk.internal.classfile.AnnotationValue;
import jdk.internal.classfile.ClassElement;
import jdk.internal.classfile.ClassModel;
import jdk.internal.classfile.Classfile;
import jdk.internal.classfile.CodeBuilder;
import jdk.internal.classfile.CodeBuilder.BlockCodeBuilder;
import jdk.internal.classfile.FieldModel;
import jdk.internal.classfile.Label;
import jdk.internal.classfile.MethodModel;
import jdk.internal.classfile.Opcode;
import jdk.internal.classfile.TypeKind;
import jdk.internal.classfile.attribute.RuntimeVisibleAnnotationsAttribute;
import jdk.jfr.internal.event.EventConfiguration;
import jdk.jfr.internal.event.EventWriter;
import jdk.jfr.Enabled;
import jdk.jfr.Name;
import jdk.jfr.Registered;
import jdk.jfr.SettingControl;
import jdk.jfr.SettingDefinition;
import jdk.jfr.internal.util.Utils;
import jdk.jfr.internal.util.Bytecode;
import jdk.jfr.internal.util.Bytecode.FieldDesc;
import jdk.jfr.internal.util.Bytecode.MethodDesc;
import static jdk.jfr.internal.util.Bytecode.invokevirtual;
import static jdk.jfr.internal.util.Bytecode.invokestatic;
import static jdk.jfr.internal.util.Bytecode.getfield;
import static jdk.jfr.internal.util.Bytecode.putfield;
import static jdk.jfr.internal.util.Bytecode.classDesc;

/**
 * Class responsible for adding instrumentation to a subclass of {@link Event}.
 *
 */
final class EventInstrumentation {

    private record SettingDesc(ClassDesc paramType, String methodName) {
    }

    private static final FieldDesc FIELD_DURATION = FieldDesc.of(long.class, Utils.FIELD_DURATION);
    private static final FieldDesc FIELD_EVENT_CONFIGURATION = FieldDesc.of(Object.class, "eventConfiguration");;
    private static final FieldDesc FIELD_START_TIME = FieldDesc.of(long.class, Utils.FIELD_START_TIME);
    private static final ClassDesc ANNOTATION_ENABLED = classDesc(Enabled.class);
    private static final ClassDesc ANNOTATION_NAME = classDesc(Name.class);
    private static final ClassDesc ANNOTATION_REGISTERED = classDesc(Registered.class);
    private static final ClassDesc TYPE_EVENT_CONFIGURATION = classDesc(EventConfiguration.class);
    private static final ClassDesc TYPE_EVENT_WRITER = classDesc(EventWriter.class);
    private static final ClassDesc TYPE_EVENT_WRITER_FACTORY = ClassDesc.of("jdk.jfr.internal.event.EventWriterFactory");
    private static final ClassDesc TYPE_OBJECT = Bytecode.classDesc(Object.class);
    private static final ClassDesc TYPE_SETTING_DEFINITION = Bytecode.classDesc(SettingDefinition.class);
    private static final MethodDesc METHOD_BEGIN = MethodDesc.of("begin", "()V");
    private static final MethodDesc METHOD_COMMIT = MethodDesc.of("commit", "()V");
    private static final MethodDesc METHOD_DURATION = MethodDesc.of("duration", "(J)J");
    private static final MethodDesc METHOD_ENABLED = MethodDesc.of("enabled", "()Z");
    private static final MethodDesc METHOD_END = MethodDesc.of("end", "()V");
    private static final MethodDesc METHOD_EVENT_CONFIGURATION_SHOULD_COMMIT = MethodDesc.of("shouldCommit", "(J)Z");
    private static final MethodDesc METHOD_EVENT_CONFIGURATION_GET_SETTING = MethodDesc.of("getSetting", SettingControl.class, int.class);
    private static final MethodDesc METHOD_EVENT_SHOULD_COMMIT = MethodDesc.of("shouldCommit", "()Z");
    private static final MethodDesc METHOD_GET_EVENT_WRITER_KEY = MethodDesc.of("getEventWriter", "(J)" + TYPE_EVENT_WRITER.descriptorString());
    private static final MethodDesc METHOD_IS_ENABLED = MethodDesc.of("isEnabled", "()Z");
    private static final MethodDesc METHOD_RESET = MethodDesc.of("reset", "()V");
    private static final MethodDesc METHOD_SHOULD_COMMIT_LONG = MethodDesc.of("shouldCommit", "(J)Z");
    private static final MethodDesc METHOD_TIME_STAMP = MethodDesc.of("timestamp", "()J");

    private final ClassModel classModel;
    private final List<SettingDesc> settingDescs;
    private final List<FieldDesc> fieldDescs;;
    private final String eventName;
    private final Class<?> superClass;
    private final boolean untypedEventConfiguration;
    private final MethodDesc staticCommitMethod;
    private final long eventTypeId;
    private final boolean guardEventConfiguration;
    private final boolean isJDK;
    private final Map<MethodDesc, Consumer<CodeBuilder>> methodUpdates = new LinkedHashMap<>();

    EventInstrumentation(Class<?> superClass, byte[] bytes, long id, boolean isJDK, boolean guardEventConfiguration) {
        this.eventTypeId = id;
        this.superClass = superClass;
        this.classModel = createClassModel(bytes);
        this.settingDescs = buildSettingDescs(superClass, classModel);
        this.fieldDescs = buildFieldDescs(superClass, classModel);
        String n = annotationValue(classModel, ANNOTATION_NAME, String.class);
        this.eventName = n == null ? classModel.thisClass().asInternalName().replace("/", ".") : n;
        this.staticCommitMethod = isJDK ? findStaticCommitMethod(classModel, fieldDescs) : null;
        this.untypedEventConfiguration = hasUntypedConfiguration();
        // Corner case when we are forced to generate bytecode
        // (bytesForEagerInstrumentation)
        // We can't reference EventConfiguration::isEnabled() before event class has
        // been registered,
        // so we add a guard against a null reference.
        this.guardEventConfiguration = guardEventConfiguration;
        this.isJDK = isJDK;
    }

    static MethodDesc findStaticCommitMethod(ClassModel classModel, List<FieldDesc> fields) {
        StringBuilder sb = new StringBuilder();
        sb.append("(");
        for (FieldDesc field : fields) {
            sb.append(field.type().descriptorString());
        }
        sb.append(")V");
        MethodDesc m = MethodDesc.of("commit", sb.toString());
        for (MethodModel method : classModel.methods()) {
            String d = method.methodTypeSymbol().descriptorString();
            if (method.methodName().equalsString("commit") && m.descriptor().descriptorString().equals(d)) {
                return m;
            }
        }
        return null;
    }

    private boolean hasUntypedConfiguration() {
        for (FieldModel f : classModel.fields()) {
            if (f.fieldName().equalsString(FIELD_EVENT_CONFIGURATION.name())) {
                return f.fieldType().equalsString(TYPE_OBJECT.descriptorString());
            }
        }
        throw new InternalError("Class missing configuration field");
    }

    public String getClassName() {
        return classModel.thisClass().asInternalName().replace("/", ".");
    }

    private ClassModel createClassModel(byte[] bytes) {
        return Classfile.of().parse(bytes);
    }

    boolean isRegistered() {
        Boolean result = annotationValue(classModel, ANNOTATION_REGISTERED, Boolean.class);
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
        Boolean result = annotationValue(classModel, ANNOTATION_ENABLED, Boolean.class);
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

    @SuppressWarnings("unchecked")
    // Only supports String and Boolean values
    private static <T> T annotationValue(ClassModel classModel, ClassDesc classDesc, Class<T> type) {
        String typeDescriptor = classDesc.descriptorString();
        for (ClassElement ce : classModel.elements()) {
            if (ce instanceof RuntimeVisibleAnnotationsAttribute rvaa) {
                for (Annotation a : rvaa.annotations()) {
                    if (a.className().equalsString(typeDescriptor)) {
                        if (a.elements().size() == 1) {
                            AnnotationElement ae = a.elements().getFirst();
                            if (ae.name().equalsString("value")) {
                                if (ae.value() instanceof AnnotationValue.OfBoolean ofb && type.equals(Boolean.class)) {
                                    Boolean b = ofb.booleanValue();
                                    return (T)b;
                                }
                                if (ae.value() instanceof AnnotationValue.OfString ofs && type.equals(String.class)) {
                                    String s = ofs.stringValue();
                                    return (T)s;
                                }
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    private static List<SettingDesc> buildSettingDescs(Class<?> superClass, ClassModel classModel) {
        Set<String> methodSet = new HashSet<>();
        List<SettingDesc> settingDescs = new ArrayList<>();
        for (MethodModel m : classModel.methods()) {
            for (var me : m.elements()) {
                if (me instanceof RuntimeVisibleAnnotationsAttribute rvaa) {
                    for (Annotation a : rvaa.annotations()) {
                        // We can't really validate the method at this
                        // stage. We would need to check that the parameter
                        // is an instance of SettingControl.
                        if (a.className().equalsString(TYPE_SETTING_DEFINITION.descriptorString())) {
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
                            if ("Z".equals(mtd.returnType().descriptorString())) {
                                if (mtd.parameterList().size() == 1) {
                                    ClassDesc type = mtd.parameterList().getFirst();
                                    if (type.isClassOrInterface()) {
                                        String methodName = m.methodName().stringValue();
                                        methodSet.add(methodName);
                                        settingDescs.add(new SettingDesc(type, methodName));
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        for (Class<?> c = superClass; jdk.internal.event.Event.class != c; c = c.getSuperclass()) {
            for (java.lang.reflect.Method method : c.getDeclaredMethods()) {
                if (!methodSet.contains(method.getName())) {
                    // skip private method in base classes
                    if (!Modifier.isPrivate(method.getModifiers())) {
                        if (method.getReturnType().equals(Boolean.TYPE)) {
                            if (method.getParameterCount() == 1) {
                                Class<?> type = method.getParameters()[0].getType();
                                if (SettingControl.class.isAssignableFrom(type)) {
                                    ClassDesc paramType = Bytecode.classDesc(type);
                                    methodSet.add(method.getName());
                                    settingDescs.add(new SettingDesc(paramType, method.getName()));
                                }
                            }
                        }
                    }
                }
            }
        }
        return settingDescs;
    }

    private static List<FieldDesc> buildFieldDescs(Class<?> superClass, ClassModel classModel) {
        Set<String> fieldSet = new HashSet<>();
        List<FieldDesc> fieldDescs = new ArrayList<>(classModel.fields().size());
        // These two fields are added by native as 'transient' so they will be
        // ignored by the loop below.
        // The benefit of adding them manually is that we can
        // control in which order they occur and we can add @Name, @Description
        // in Java, instead of in native. It also means code for adding implicit
        // fields for native can be reused by Java.
        fieldDescs.add(FIELD_START_TIME);
        fieldDescs.add(FIELD_DURATION);
        for (FieldModel field : classModel.fields()) {
            if (!fieldSet.contains(field.fieldName().stringValue()) && isValidField(field.flags().flagsMask(), field.fieldTypeSymbol())) {
                FieldDesc fi = FieldDesc.of(field.fieldTypeSymbol(), field.fieldName().stringValue());
                fieldDescs.add(fi);
                fieldSet.add(field.fieldName().stringValue());
            }
        }
        for (Class<?> c = superClass; jdk.internal.event.Event.class != c; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                // skip private field in base classes
                if (!Modifier.isPrivate(field.getModifiers())) {
                    if (isValidField(field.getModifiers(), field.getType().getName())) {
                        String fieldName = field.getName();
                        if (!fieldSet.contains(fieldName)) {
                            fieldDescs.add(FieldDesc.of(field.getType(), fieldName));
                            fieldSet.add(fieldName);
                        }
                    }
                }
            }
        }
        return fieldDescs;
    }

    public static boolean isValidField(int access, ClassDesc classDesc) {
        String className = classDesc.packageName();
        if (!className.isEmpty()) {
            className = className + ".";
        }
        className += classDesc.displayName();
        return isValidField(access, className);
    }

    public static boolean isValidField(int access, String className) {
        if (Modifier.isTransient(access) || Modifier.isStatic(access)) {
            return false;
        }
        return Type.isValidJavaFieldType(className);
    }

    public byte[] buildInstrumented() {
        makeInstrumented();
        return toByteArray();
    }

    byte[] toByteArray() {
        return Classfile.of().build(classModel.thisClass().asSymbol(), classBuilder -> {
            for (ClassElement ce : classModel) {
                boolean updated = false;
                if (ce instanceof MethodModel method) {
                    Consumer<CodeBuilder> methodUpdate = findMethodUpdate(method);
                    if (methodUpdate != null) {
                        classBuilder.withMethod(method.methodName().stringValue(), method.methodTypeSymbol(), method.flags().flagsMask(), methodBuilder -> {
                            methodBuilder.withCode(methodUpdate);
                        });
                        updated = true;
                    }
                }
                if (!updated) {
                    classBuilder.with(ce);
                }
            }
        });
    }

    public byte[] buildUninstrumented() {
        makeUninstrumented();
        return toByteArray();
    }

    private void makeInstrumented() {
        // MyEvent#isEnabled()
        updateEnabledMethod(METHOD_IS_ENABLED);

        // MyEvent#begin()
        updateMethod(METHOD_BEGIN, codeBuilder -> {
            codeBuilder.aload(0);
            invokestatic(codeBuilder, TYPE_EVENT_CONFIGURATION, METHOD_TIME_STAMP);
            putfield(codeBuilder, getEventClassDesc(), FIELD_START_TIME);
            codeBuilder.return_();
        });

        // MyEvent#end()
        updateMethod(METHOD_END, codeBuilder -> {
            codeBuilder.aload(0);
            codeBuilder.aload(0);
            getfield(codeBuilder, getEventClassDesc(), FIELD_START_TIME);
            invokestatic(codeBuilder, TYPE_EVENT_CONFIGURATION, METHOD_DURATION);
            putfield(codeBuilder, getEventClassDesc(), FIELD_DURATION);
            codeBuilder.return_();
        });

        // MyEvent#commit() or static MyEvent#commit(...)
        MethodDesc m = staticCommitMethod == null ? METHOD_COMMIT : staticCommitMethod;
        updateMethod(m, codeBuilder -> {
            Label excluded = codeBuilder.newLabel();
            Label end = codeBuilder.newLabel();
            codeBuilder.trying(blockCodeBuilder -> {
                if (staticCommitMethod != null) {
                    updateStaticCommit(blockCodeBuilder, excluded);
                } else {
                    updateInstanceCommit(blockCodeBuilder, end, excluded);
                }
                // stack: [integer]
                // notified -> restart event write attempt
                blockCodeBuilder.ifeq(blockCodeBuilder.startLabel());
                // stack: []
                blockCodeBuilder.goto_(end);
            }, catchBuilder -> {
                catchBuilder.catchingAll(catchAllHandler -> {
                    getEventWriter(catchAllHandler);
                    // stack: [ex] [EW]
                    catchAllHandler.dup();
                    // stack: [ex] [EW] [EW]
                    Label rethrow = catchAllHandler.newLabel();
                    catchAllHandler.if_null(rethrow);
                    // stack: [ex] [EW]
                    catchAllHandler.dup();
                    // stack: [ex] [EW] [EW]
                    invokevirtual(catchAllHandler, TYPE_EVENT_WRITER, METHOD_RESET);
                    catchAllHandler.labelBinding(rethrow);
                    // stack:[ex] [EW]
                    catchAllHandler.pop();
                    // stack:[ex]
                    catchAllHandler.throwInstruction();
                });
            });
            codeBuilder.labelBinding(excluded);
            // stack: [EW]
            codeBuilder.pop();
            codeBuilder.labelBinding(end);
            // stack: []
            codeBuilder.return_();
        });

        // MyEvent#shouldCommit()
        updateMethod(METHOD_EVENT_SHOULD_COMMIT, codeBuilder -> {
            Label fail = codeBuilder.newLabel();
            if (guardEventConfiguration) {
                getEventConfiguration(codeBuilder);
                codeBuilder.if_null(fail);
            }
            // if (!eventConfiguration.shouldCommit(duration) goto fail;
            getEventConfiguration(codeBuilder);
            codeBuilder.aload(0);
            getfield(codeBuilder, getEventClassDesc(), FIELD_DURATION);
            invokevirtual(codeBuilder, TYPE_EVENT_CONFIGURATION, METHOD_EVENT_CONFIGURATION_SHOULD_COMMIT);
            codeBuilder.ifeq(fail);
            for (int index = 0; index < settingDescs.size(); index++) {
                SettingDesc sd = settingDescs.get(index);
                // if (!settingsMethod(eventConfiguration.settingX)) goto fail;
                codeBuilder.aload(0);
                getEventConfiguration(codeBuilder);
                codeBuilder.checkcast(TYPE_EVENT_CONFIGURATION);
                codeBuilder.ldc(index);
                invokevirtual(codeBuilder, TYPE_EVENT_CONFIGURATION, METHOD_EVENT_CONFIGURATION_GET_SETTING);
                MethodTypeDesc mdesc = MethodTypeDesc.ofDescriptor("(" + sd.paramType().descriptorString() + ")Z");
                codeBuilder.checkcast(sd.paramType());
                codeBuilder.invokevirtual(getEventClassDesc(), sd.methodName(), mdesc);
                codeBuilder.ifeq(fail);
            }
            // return true
            codeBuilder.iconst_1();
            codeBuilder.ireturn();
            // return false
            codeBuilder.labelBinding(fail);
            codeBuilder.iconst_0();
            codeBuilder.ireturn();
        });

        if (isJDK) {
            if (hasStaticMethod(METHOD_ENABLED)) {
                updateEnabledMethod(METHOD_ENABLED);
            }

            updateIfStaticMethodExists(METHOD_SHOULD_COMMIT_LONG, codeBuilder -> {
                Label fail = codeBuilder.newLabel();
                if (guardEventConfiguration) {
                    // if (eventConfiguration == null) goto fail;
                    getEventConfiguration(codeBuilder);
                    codeBuilder.if_null(fail);
                }
                // return eventConfiguration.shouldCommit(duration);
                getEventConfiguration(codeBuilder);
                codeBuilder.lload(0);
                codeBuilder.invokevirtual(TYPE_EVENT_CONFIGURATION, METHOD_EVENT_CONFIGURATION_SHOULD_COMMIT.name(), METHOD_EVENT_CONFIGURATION_SHOULD_COMMIT.descriptor());
                codeBuilder.ireturn();
                // fail:
                codeBuilder.labelBinding(fail);
                // return false
                codeBuilder.iconst_0();
                codeBuilder.ireturn();
            });
            updateIfStaticMethodExists(METHOD_TIME_STAMP, codeBuilder -> {
                invokestatic(codeBuilder, TYPE_EVENT_CONFIGURATION, METHOD_TIME_STAMP);
                codeBuilder.lreturn();
            });
        }
    }

    void updateStaticCommit(BlockCodeBuilder blockCodeBuilder, Label excluded) {
        // indexes the argument type array, the argument type array does not include
        // 'this'
        int argIndex = 0;
        // indexes the proper slot in the local variable table, takes type size into
        // account, therefore sometimes argIndex != slotIndex
        int slotIndex = 0;
        int fieldIndex = 0;
        ClassDesc[] argumentTypes = staticCommitMethod.descriptor().parameterArray();
        TypeKind tk = null;
        getEventWriter(blockCodeBuilder);
        // stack: [EW],
        blockCodeBuilder.dup();
        // stack: [EW], [EW]
        // write begin event
        getEventConfiguration(blockCodeBuilder);
        // stack: [EW], [EW], [EventConfiguration]
        blockCodeBuilder.constantInstruction(Opcode.LDC2_W, eventTypeId);
        // stack: [EW], [EW], [EventConfiguration] [long]
        invokevirtual(blockCodeBuilder, TYPE_EVENT_WRITER, EventWriterMethod.BEGIN_EVENT.method());
        // stack: [EW], [integer]
        blockCodeBuilder.ifeq(excluded);
        // stack: [EW]
        // write startTime
        blockCodeBuilder.dup();
        // stack: [EW], [EW]
        tk = TypeKind.from(argumentTypes[argIndex++]);
        blockCodeBuilder.loadInstruction(tk, slotIndex);
        // stack: [EW], [EW], [long]
        slotIndex += tk.slotSize();
        invokevirtual(blockCodeBuilder, TYPE_EVENT_WRITER, EventWriterMethod.PUT_LONG.method());
        // stack: [EW]
        fieldIndex++;
        // write duration
        blockCodeBuilder.dup();
        // stack: [EW], [EW]
        tk = TypeKind.from(argumentTypes[argIndex++]);
        blockCodeBuilder.loadInstruction(tk, slotIndex);
        // stack: [EW], [EW], [long]
        slotIndex += tk.slotSize();
        invokevirtual(blockCodeBuilder, TYPE_EVENT_WRITER, EventWriterMethod.PUT_LONG.method());
        // stack: [EW]
        fieldIndex++;
        // write eventThread
        blockCodeBuilder.dup();
        // stack: [EW], [EW]
        invokevirtual(blockCodeBuilder, TYPE_EVENT_WRITER, EventWriterMethod.PUT_EVENT_THREAD.method());
        // stack: [EW]
        // write stackTrace
        blockCodeBuilder.dup();
        // stack: [EW], [EW]
        invokevirtual(blockCodeBuilder, TYPE_EVENT_WRITER, EventWriterMethod.PUT_STACK_TRACE.method());
        // stack: [EW]
        // write custom fields
        while (fieldIndex < fieldDescs.size()) {
            blockCodeBuilder.dup();
            // stack: [EW], [EW]
            tk = TypeKind.from(argumentTypes[argIndex++]);
            blockCodeBuilder.loadInstruction(tk, slotIndex);
            // stack:[EW], [EW], [field]
            slotIndex += tk.slotSize();
            FieldDesc field = fieldDescs.get(fieldIndex);
            EventWriterMethod eventMethod = EventWriterMethod.lookupMethod(field);
            invokevirtual(blockCodeBuilder, TYPE_EVENT_WRITER, eventMethod.method());
            // stack: [EW]
            fieldIndex++;
        }
        // stack: [EW]
        // write end event (writer already on stack)
        invokevirtual(blockCodeBuilder, TYPE_EVENT_WRITER, EventWriterMethod.END_EVENT.method());
        // stack: [int]
    }

    void updateInstanceCommit(BlockCodeBuilder blockCodeBuilder, Label end, Label excluded) {
        // if (!isEnable()) {
        // return;
        // }
        blockCodeBuilder.aload(0);
        invokevirtual(blockCodeBuilder, getEventClassDesc(), METHOD_IS_ENABLED);
        Label l0 = blockCodeBuilder.newLabel();
        blockCodeBuilder.ifne(l0);
        blockCodeBuilder.return_();
        blockCodeBuilder.labelBinding(l0);
        // long startTime = this.startTime
        blockCodeBuilder.aload(0);
        getfield(blockCodeBuilder, getEventClassDesc(), FIELD_START_TIME);
        blockCodeBuilder.lstore(1);
        // if (startTime == 0) {
        //   startTime = EventWriter.timestamp();
        // } else {
        blockCodeBuilder.lload(1);
        blockCodeBuilder.lconst_0();
        blockCodeBuilder.lcmp();
        Label durationEvent = blockCodeBuilder.newLabel();
        blockCodeBuilder.ifne(durationEvent);
        invokestatic(blockCodeBuilder, TYPE_EVENT_CONFIGURATION, METHOD_TIME_STAMP);
        blockCodeBuilder.lstore(1);
        Label commit = blockCodeBuilder.newLabel();
        blockCodeBuilder.goto_(commit);
        //   if (duration == 0) {
        //     duration = EventWriter.timestamp() - startTime;
        //   }
        // }
        blockCodeBuilder.labelBinding(durationEvent);
        blockCodeBuilder.aload(0);
        getfield(blockCodeBuilder, getEventClassDesc(), FIELD_DURATION);
        blockCodeBuilder.lconst_0();
        blockCodeBuilder.lcmp();
        blockCodeBuilder.ifne(commit);
        blockCodeBuilder.aload(0);
        invokestatic(blockCodeBuilder, TYPE_EVENT_CONFIGURATION, METHOD_TIME_STAMP);
        blockCodeBuilder.lload(1);
        blockCodeBuilder.lsub();
        putfield(blockCodeBuilder, getEventClassDesc(), FIELD_DURATION);
        blockCodeBuilder.labelBinding(commit);
        // if (shouldCommit()) {
        blockCodeBuilder.aload(0);
        invokevirtual(blockCodeBuilder, getEventClassDesc(), METHOD_EVENT_SHOULD_COMMIT);
        blockCodeBuilder.ifeq(end);
        getEventWriter(blockCodeBuilder);
        // stack: [EW]
        blockCodeBuilder.dup();
        // stack: [EW] [EW]
        getEventConfiguration(blockCodeBuilder);
        // stack: [EW] [EW] [EC]
        blockCodeBuilder.constantInstruction(Opcode.LDC2_W, eventTypeId);
        invokevirtual(blockCodeBuilder, TYPE_EVENT_WRITER, EventWriterMethod.BEGIN_EVENT.method());
        // stack: [EW] [int]
        blockCodeBuilder.ifeq(excluded);
        // stack: [EW]
        int fieldIndex = 0;
        blockCodeBuilder.dup();
        // stack: [EW] [EW]
        blockCodeBuilder.lload(1);
        // stack: [EW] [EW] [long]
        invokevirtual(blockCodeBuilder, TYPE_EVENT_WRITER, EventWriterMethod.PUT_LONG.method());
        // stack: [EW]
        fieldIndex++;
        blockCodeBuilder.dup();
        // stack: [EW] [EW]
        blockCodeBuilder.aload(0);
        // stack: [EW] [EW] [this]
        getfield(blockCodeBuilder, getEventClassDesc(), FIELD_DURATION);
        // stack: [EW] [EW] [long]
        invokevirtual(blockCodeBuilder, TYPE_EVENT_WRITER, EventWriterMethod.PUT_LONG.method());
        // stack: [EW]
        fieldIndex++;
        blockCodeBuilder.dup();
        // stack: [EW] [EW]
        invokevirtual(blockCodeBuilder, TYPE_EVENT_WRITER, EventWriterMethod.PUT_EVENT_THREAD.method());
        // stack: [EW]
        blockCodeBuilder.dup();
        // stack: [EW] [EW]
        invokevirtual(blockCodeBuilder, TYPE_EVENT_WRITER, EventWriterMethod.PUT_STACK_TRACE.method());
        // stack: [EW]
        while (fieldIndex < fieldDescs.size()) {
            FieldDesc field = fieldDescs.get(fieldIndex);
            blockCodeBuilder.dup();
            // stack: [EW] [EW]
            blockCodeBuilder.aload(0);
            // stack: [EW] [EW] [this]
            getfield(blockCodeBuilder, getEventClassDesc(), field);
            // stack: [EW] [EW] <T>
            EventWriterMethod eventMethod = EventWriterMethod.lookupMethod(field);
            invokevirtual(blockCodeBuilder, TYPE_EVENT_WRITER, eventMethod.method());
            // stack: [EW]
            fieldIndex++;
        }
        // stack:[EW]
        invokevirtual(blockCodeBuilder, TYPE_EVENT_WRITER, EventWriterMethod.END_EVENT.method());
        // stack:[int]
    }

    private void updateEnabledMethod(MethodDesc method) {
        updateMethod(method, codeBuilder -> {
            Label nullLabel = codeBuilder.newLabel();
            if (guardEventConfiguration) {
                getEventConfiguration(codeBuilder);
                codeBuilder.branchInstruction(Opcode.IFNULL, nullLabel);
            }
            getEventConfiguration(codeBuilder);
            invokevirtual(codeBuilder, TYPE_EVENT_CONFIGURATION, METHOD_IS_ENABLED);
            codeBuilder.ireturn();
            if (guardEventConfiguration) {
                codeBuilder.labelBinding(nullLabel);
                codeBuilder.iconst_0();
                codeBuilder.ireturn();
            }
        });
    }

    private void updateIfStaticMethodExists(MethodDesc method, Consumer<CodeBuilder> code) {
        if (hasStaticMethod(method)) {
            updateMethod(method, code);
        }
    }

    private boolean hasStaticMethod(MethodDesc method) {
        for (MethodModel m : classModel.methods()) {
            if (m.methodName().equalsString(method.name()) && m.methodTypeSymbol().equals(method.descriptor())) {
                return Modifier.isStatic(m.flags().flagsMask());
            }
        }
        return false;
    }

    private void getEventWriter(CodeBuilder codeBuilder) {
        codeBuilder.ldc(EventWriterKey.getKey());
        invokestatic(codeBuilder, TYPE_EVENT_WRITER_FACTORY, METHOD_GET_EVENT_WRITER_KEY);
    }

    private void getEventConfiguration(CodeBuilder codeBuilder) {
        if (untypedEventConfiguration) {
            codeBuilder.getstatic(getEventClassDesc(), FIELD_EVENT_CONFIGURATION.name(), TYPE_OBJECT);
        } else {
            codeBuilder.getstatic(getEventClassDesc(), FIELD_EVENT_CONFIGURATION.name(), TYPE_EVENT_CONFIGURATION);
        }
    }

    private void makeUninstrumented() {
        updateExistingWithReturnFalse(METHOD_EVENT_SHOULD_COMMIT);
        updateExistingWithReturnFalse(METHOD_IS_ENABLED);
        updateExistingWithEmptyVoidMethod(METHOD_COMMIT);
        if (staticCommitMethod != null) {
            updateExistingWithEmptyVoidMethod(staticCommitMethod);
        }
        updateExistingWithEmptyVoidMethod(METHOD_BEGIN);
        updateExistingWithEmptyVoidMethod(METHOD_END);
    }

    private final void updateExistingWithEmptyVoidMethod(MethodDesc voidMethod) {
        updateMethod(voidMethod, codeBuilder -> {
            codeBuilder.return_();
        });
    }

    private final void updateExistingWithReturnFalse(MethodDesc voidMethod) {
        updateMethod(voidMethod, codeBuilder -> {
            codeBuilder.iconst_0();
            codeBuilder.ireturn();
        });
    }

    private Consumer<CodeBuilder> findMethodUpdate(MethodModel mm) {
        MethodDesc m = MethodDesc.of(mm.methodName().stringValue(), mm.methodType().stringValue());
        return methodUpdates.get(m);
    }

    private void updateMethod(MethodDesc method, Consumer<CodeBuilder> codeBuilder) {
        methodUpdates.put(method, codeBuilder);
    }

    private ClassDesc getEventClassDesc() {
        return classModel.thisClass().asSymbol();
    }

    public String getEventName() {
        return eventName;
    }
}
