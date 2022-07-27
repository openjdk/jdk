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
import java.util.function.Consumer;

import jdk.internal.org.objectweb.asm.ClassReader;
import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.Label;
import jdk.internal.org.objectweb.asm.MethodVisitor;
import jdk.internal.org.objectweb.asm.Opcodes;
import jdk.internal.org.objectweb.asm.Type;
import jdk.internal.org.objectweb.asm.commons.Method;
import jdk.internal.org.objectweb.asm.tree.AnnotationNode;
import jdk.internal.org.objectweb.asm.tree.ClassNode;
import jdk.internal.org.objectweb.asm.tree.FieldNode;
import jdk.internal.org.objectweb.asm.tree.MethodNode;
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

    record SettingInfo(String fieldName, int index, Type paramType, String methodName, SettingControl settingControl) {
        /**
         * A malicious user must never be able to run a callback in the wrong
         * context. Methods on SettingControl must therefore never be invoked directly
         * by JFR, instead use jdk.jfr.internal.Control.
         */
        public SettingControl settingControl() {
            return this.settingControl;
        }
    }

    record FieldInfo(String fieldName, String fieldDescriptor, String internalClassName) {
    }

    public static final String FIELD_EVENT_THREAD = "eventThread";
    public static final String FIELD_STACK_TRACE = "stackTrace";
    public static final String FIELD_DURATION = "duration";

    static final String FIELD_EVENT_CONFIGURATION = "eventConfiguration";
    static final String FIELD_START_TIME = "startTime";

    private static final String ANNOTATION_NAME_DESCRIPTOR = Type.getDescriptor(Name.class);
    private static final String ANNOTATION_REGISTERED_DESCRIPTOR = Type.getDescriptor(Registered.class);
    private static final String ANNOTATION_ENABLED_DESCRIPTOR = Type.getDescriptor(Enabled.class);
    private static final Type TYPE_EVENT_CONFIGURATION = Type.getType(EventConfiguration.class);
    private static final Type TYPE_EVENT_WRITER = Type.getType(EventWriter.class);
    private static final Type TYPE_EVENT_WRITER_FACTORY = Type.getType("Ljdk/jfr/internal/event/EventWriterFactory;");
    private static final Type TYPE_SETTING_CONTROL = Type.getType(SettingControl.class);
    private static final String TYPE_OBJECT_DESCRIPTOR = Type.getDescriptor(Object.class);
    private static final String TYPE_EVENT_CONFIGURATION_DESCRIPTOR = TYPE_EVENT_CONFIGURATION.getDescriptor();
    private static final String TYPE_SETTING_DEFINITION_DESCRIPTOR = Type.getDescriptor(SettingDefinition.class);
    private static final Method METHOD_COMMIT = new Method("commit", Type.VOID_TYPE, new Type[0]);
    private static final Method METHOD_BEGIN = new Method("begin", Type.VOID_TYPE, new Type[0]);
    private static final Method METHOD_END = new Method("end", Type.VOID_TYPE, new Type[0]);
    private static final Method METHOD_IS_ENABLED = new Method("isEnabled", Type.BOOLEAN_TYPE, new Type[0]);
    private static final Method METHOD_TIME_STAMP = new Method("timestamp", Type.LONG_TYPE, new Type[0]);
    private static final Method METHOD_GET_EVENT_WRITER_KEY = new Method("getEventWriter", TYPE_EVENT_WRITER, new Type[] { Type.LONG_TYPE });
    private static final Method METHOD_EVENT_SHOULD_COMMIT = new Method("shouldCommit", Type.BOOLEAN_TYPE, new Type[0]);
    private static final Method METHOD_EVENT_CONFIGURATION_SHOULD_COMMIT = new Method("shouldCommit", Type.BOOLEAN_TYPE, new Type[] { Type.LONG_TYPE });
    private static final Method METHOD_EVENT_CONFIGURATION_GET_SETTING = new Method("getSetting", TYPE_SETTING_CONTROL, new Type[] { Type.INT_TYPE });
    private static final Method METHOD_DURATION = new Method("duration", Type.LONG_TYPE, new Type[] { Type.LONG_TYPE });
    private static final Method METHOD_RESET = new Method("reset", "()V");
    private static final Method METHOD_ENABLED = new Method("enabled", Type.BOOLEAN_TYPE, new Type[0]);
    private static final Method METHOD_SHOULD_COMMIT_LONG = new Method("shouldCommit", Type.BOOLEAN_TYPE, new Type[] { Type.LONG_TYPE });

    private final ClassNode classNode;
    private final List<SettingInfo> settingInfos;
    private final List<FieldInfo> fieldInfos;;
    private final String eventName;
    private final Class<?> superClass;
    private final boolean untypedEventConfiguration;
    private final Method staticCommitMethod;
    private final long eventTypeId;
    private final boolean guardEventConfiguration;
    private final boolean isJDK;

    EventInstrumentation(Class<?> superClass, byte[] bytes, long id, boolean isJDK, boolean guardEventConfiguration) {
        this.eventTypeId = id;
        this.superClass = superClass;
        this.classNode = createClassNode(bytes);
        this.settingInfos = buildSettingInfos(superClass, classNode);
        this.fieldInfos = buildFieldInfos(superClass, classNode);
        String n = annotationValue(classNode, ANNOTATION_NAME_DESCRIPTOR, String.class);
        this.eventName = n == null ? classNode.name.replace("/", ".") : n;
        this.staticCommitMethod = isJDK ? findStaticCommitMethod(classNode, fieldInfos) : null;
        this.untypedEventConfiguration = hasUntypedConfiguration();
        // Corner case when we are forced to generate bytecode (bytesForEagerInstrumentation)
        // We can't reference EventConfiguration::isEnabled() before event class has been registered,
        // so we add a guard against a null reference.
        this.guardEventConfiguration = guardEventConfiguration;
        this.isJDK = isJDK;
    }

    public static Method findStaticCommitMethod(ClassNode classNode, List<FieldInfo> fields) {
        StringBuilder sb = new StringBuilder();
        sb.append("(");
        for (FieldInfo v : fields) {
            sb.append(v.fieldDescriptor);
        }
        sb.append(")V");
        Method m = new Method("commit", sb.toString());
        for (MethodNode method : classNode.methods) {
            if ("commit".equals(method.name) && m.getDescriptor().equals(method.desc)) {
                return m;
            }
        }
        return null;
    }

    private boolean hasUntypedConfiguration() {
        for (FieldNode field : classNode.fields) {
            if (FIELD_EVENT_CONFIGURATION.equals(field.name)) {
                return field.desc.equals(TYPE_OBJECT_DESCRIPTOR);
            }
        }
        throw new InternalError("Class missing configuration field");
    }

    public String getClassName() {
        return classNode.name.replace("/", ".");
    }

    private ClassNode createClassNode(byte[] bytes) {
        ClassNode classNode = new ClassNode();
        ClassReader classReader = new ClassReader(bytes);
        classReader.accept(classNode, 0);
        return classNode;
    }

    boolean isRegistered() {
        Boolean result = annotationValue(classNode, ANNOTATION_REGISTERED_DESCRIPTOR, Boolean.class);
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
        Boolean result = annotationValue(classNode, ANNOTATION_ENABLED_DESCRIPTOR, Boolean.class);
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
    private static <T> T annotationValue(ClassNode classNode, String typeDescriptor, Class<?> type) {
        if (classNode.visibleAnnotations != null) {
            for (AnnotationNode a : classNode.visibleAnnotations) {
                if (typeDescriptor.equals(a.desc)) {
                    List<Object> values = a.values;
                    if (values != null && values.size() == 2) {
                        Object key = values.get(0);
                        Object value = values.get(1);
                        if (key instanceof String keyName && value != null) {
                            if (type == value.getClass()) {
                                if ("value".equals(keyName)) {
                                    return (T) value;
                                }
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    private static List<SettingInfo> buildSettingInfos(Class<?> superClass, ClassNode classNode) {
        Set<String> methodSet = new HashSet<>();
        List<SettingInfo> settingInfos = new ArrayList<>();
        for (MethodNode m : classNode.methods) {
            if (m.visibleAnnotations != null) {
                for (AnnotationNode an : m.visibleAnnotations) {
                    // We can't really validate the method at this
                    // stage. We would need to check that the parameter
                    // is an instance of SettingControl.
                    if (TYPE_SETTING_DEFINITION_DESCRIPTOR.equals(an.desc)) {
                        String name = m.name;
                        for (AnnotationNode nameCandidate : m.visibleAnnotations) {
                            if (ANNOTATION_NAME_DESCRIPTOR.equals(nameCandidate.desc)) {
                                List<Object> values = nameCandidate.values;
                                if (values.size() == 1 && values.get(0)instanceof String s) {
                                    name = Utils.validJavaIdentifier(s, name);
                                }
                            }
                        }
                        Type returnType = Type.getReturnType(m.desc);
                        if (returnType.equals(Type.getType(Boolean.TYPE))) {
                            Type[] args = Type.getArgumentTypes(m.desc);
                            if (args.length == 1) {
                                Type paramType = args[0];
                                String fieldName = EventControl.FIELD_SETTING_PREFIX + settingInfos.size();
                                int index = settingInfos.size();
                                methodSet.add(m.name);
                                settingInfos.add(new SettingInfo(fieldName, index, paramType, m.name, null));
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
                                Type paramType = Type.getType(param.getType());
                                String fieldName = EventControl.FIELD_SETTING_PREFIX + settingInfos.size();
                                int index = settingInfos.size();
                                methodSet.add(method.getName());
                                settingInfos.add(new SettingInfo(fieldName, index, paramType, method.getName(), null));
                            }
                        }
                    }
                }
            }
        }
        return settingInfos;
    }

    private static List<FieldInfo> buildFieldInfos(Class<?> superClass, ClassNode classNode) {
        Set<String> fieldSet = new HashSet<>();
        List<FieldInfo> fieldInfos = new ArrayList<>(classNode.fields.size());
        // These two fields are added by native as 'transient' so they will be
        // ignored by the loop below.
        // The benefit of adding them manually is that we can
        // control in which order they occur and we can add @Name, @Description
        // in Java, instead of in native. It also means code for adding implicit
        // fields for native can be reused by Java.
        fieldInfos.add(new FieldInfo("startTime", Type.LONG_TYPE.getDescriptor(), classNode.name));
        fieldInfos.add(new FieldInfo("duration", Type.LONG_TYPE.getDescriptor(), classNode.name));
        for (FieldNode field : classNode.fields) {
            if (!fieldSet.contains(field.name) && isValidField(field.access, Type.getType(field.desc).getClassName())) {
                FieldInfo fi = new FieldInfo(field.name, field.desc, classNode.name);
                fieldInfos.add(fi);
                fieldSet.add(field.name);
            }
        }
        for (Class<?> c = superClass; c != jdk.internal.event.Event.class; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                // skip private field in base classes
                if (!Modifier.isPrivate(field.getModifiers())) {
                    if (isValidField(field.getModifiers(), field.getType().getName())) {
                        String fieldName = field.getName();
                        if (!fieldSet.contains(fieldName)) {
                            Type fieldType = Type.getType(field.getType());
                            String internalClassName = ASMToolkit.getInternalName(c.getName());
                            fieldInfos.add(new FieldInfo(fieldName, fieldType.getDescriptor(), internalClassName));
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
        makeInstrumented();
        return toByteArray();
    }

    private byte[] toByteArray() {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        classNode.accept(cw);
        cw.visitEnd();
        return cw.toByteArray();
    }

    public byte[] buildUninstrumented() {
        makeUninstrumented();
        return toByteArray();
    }

    private void makeInstrumented() {
        // MyEvent#isEnabled()
        updateEnabledMethod(METHOD_IS_ENABLED);

        // MyEvent#begin()
        updateMethod(METHOD_BEGIN, methodVisitor -> {
            methodVisitor.visitIntInsn(Opcodes.ALOAD, 0);
            invokeStatic(methodVisitor, TYPE_EVENT_CONFIGURATION.getInternalName(), METHOD_TIME_STAMP);
            methodVisitor.visitFieldInsn(Opcodes.PUTFIELD, getInternalClassName(), FIELD_START_TIME, "J");
            methodVisitor.visitInsn(Opcodes.RETURN);
        });

        // MyEvent#end()
        updateMethod(METHOD_END, methodVisitor -> {
            methodVisitor.visitIntInsn(Opcodes.ALOAD, 0);
            methodVisitor.visitIntInsn(Opcodes.ALOAD, 0);
            methodVisitor.visitFieldInsn(Opcodes.GETFIELD, getInternalClassName(), FIELD_START_TIME, "J");
            invokeStatic(methodVisitor, TYPE_EVENT_CONFIGURATION.getInternalName(), METHOD_DURATION);
            methodVisitor.visitFieldInsn(Opcodes.PUTFIELD, getInternalClassName(), FIELD_DURATION, "J");
            methodVisitor.visitInsn(Opcodes.RETURN);
            methodVisitor.visitMaxs(0, 0);
        });

        // MyEvent#commit() or static MyEvent#commit(...)
        if (staticCommitMethod != null) {
            updateExistingWithEmptyVoidMethod(METHOD_COMMIT);
            updateMethod(staticCommitMethod, mv -> {
                // indexes the argument type array, the argument type array does not include
                // 'this'
                int argIndex = 0;
                // indexes the proper slot in the local variable table, takes type size into
                // account, therefore sometimes argIndex != slotIndex
                int slotIndex = 0;
                int fieldIndex = 0;
                Type[] argumentTypes = Type.getArgumentTypes(staticCommitMethod.getDescriptor());
                mv.visitCode();
                Label start = new Label();
                Label endTryBlock = new Label();
                Label exceptionHandler = new Label();
                mv.visitTryCatchBlock(start, endTryBlock, exceptionHandler, "java/lang/Throwable");
                mv.visitLabel(start);
                getEventWriter(mv);
                // stack: [EW]
                mv.visitInsn(Opcodes.DUP);
                // stack: [EW], [EW]
                // write begin event
                getEventConfiguration(mv);
                // stack: [EW], [EW], [EventConfiguration]
                mv.visitLdcInsn(eventTypeId);
                // stack: [EW], [EW], [EventConfiguration] [long]
                visitMethod(mv, Opcodes.INVOKEVIRTUAL, TYPE_EVENT_WRITER, EventWriterMethod.BEGIN_EVENT.asASM());
                // stack: [EW], [integer]
                Label excluded = new Label();
                mv.visitJumpInsn(Opcodes.IFEQ, excluded);
                // stack: [EW]
                // write startTime
                mv.visitInsn(Opcodes.DUP);
                // stack: [EW], [EW]
                mv.visitVarInsn(argumentTypes[argIndex].getOpcode(Opcodes.ILOAD), slotIndex);
                // stack: [EW], [EW], [long]
                slotIndex += argumentTypes[argIndex++].getSize();
                visitMethod(mv, Opcodes.INVOKEVIRTUAL, TYPE_EVENT_WRITER, EventWriterMethod.PUT_LONG.asASM());
                // stack: [EW]
                fieldIndex++;
                // write duration
                mv.visitInsn(Opcodes.DUP);
                // stack: [EW], [EW]
                mv.visitVarInsn(argumentTypes[argIndex].getOpcode(Opcodes.ILOAD), slotIndex);
                // stack: [EW], [EW], [long]
                slotIndex += argumentTypes[argIndex++].getSize();
                visitMethod(mv, Opcodes.INVOKEVIRTUAL, TYPE_EVENT_WRITER, EventWriterMethod.PUT_LONG.asASM());
                // stack: [EW]
                fieldIndex++;
                // write eventThread
                mv.visitInsn(Opcodes.DUP);
                // stack: [EW], [EW]
                visitMethod(mv, Opcodes.INVOKEVIRTUAL, TYPE_EVENT_WRITER, EventWriterMethod.PUT_EVENT_THREAD.asASM());
                // stack: [EW]
                // write stackTrace
                mv.visitInsn(Opcodes.DUP);
                // stack: [EW], [EW]
                visitMethod(mv, Opcodes.INVOKEVIRTUAL, TYPE_EVENT_WRITER, EventWriterMethod.PUT_STACK_TRACE.asASM());
                // stack: [EW]
                // write custom fields
                while (fieldIndex < fieldInfos.size()) {
                    mv.visitInsn(Opcodes.DUP);
                    // stack: [EW], [EW]
                    mv.visitVarInsn(argumentTypes[argIndex].getOpcode(Opcodes.ILOAD), slotIndex);
                    // stack:[EW], [EW], [field]
                    slotIndex += argumentTypes[argIndex++].getSize();
                    FieldInfo field = fieldInfos.get(fieldIndex);
                    EventWriterMethod eventMethod = EventWriterMethod.lookupMethod(field);
                    visitMethod(mv, Opcodes.INVOKEVIRTUAL, TYPE_EVENT_WRITER, eventMethod.asASM());
                    // stack: [EW]
                    fieldIndex++;
                }
                // stack: [EW]
                // write end event (writer already on stack)
                visitMethod(mv, Opcodes.INVOKEVIRTUAL, TYPE_EVENT_WRITER, EventWriterMethod.END_EVENT.asASM());
                // stack [integer]
                // notified -> restart event write attempt
                mv.visitJumpInsn(Opcodes.IFEQ, start);
                // stack:
                mv.visitLabel(endTryBlock);
                Label end = new Label();
                mv.visitJumpInsn(Opcodes.GOTO, end);
                mv.visitLabel(exceptionHandler);
                // stack: [ex]
                mv.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[] { "java/lang/Throwable" });
                getEventWriter(mv);
                // stack: [ex] [EW]
                mv.visitInsn(Opcodes.DUP);
                // stack: [ex] [EW] [EW]
                Label rethrow = new Label();
                mv.visitJumpInsn(Opcodes.IFNULL, rethrow);
                // stack: [ex] [EW]
                mv.visitInsn(Opcodes.DUP);
                // stack: [ex] [EW] [EW]
                visitMethod(mv, Opcodes.INVOKEVIRTUAL, TYPE_EVENT_WRITER, METHOD_RESET);
                mv.visitLabel(rethrow);
                // stack:[ex] [EW]
                mv.visitFrame(Opcodes.F_SAME, 0, null, 2, new Object[] { "java/lang/Throwable", TYPE_EVENT_WRITER.getInternalName() });
                mv.visitInsn(Opcodes.POP);
                // stack:[ex]
                mv.visitInsn(Opcodes.ATHROW);
                mv.visitLabel(excluded);
                // stack: [EW]
                mv.visitFrame(Opcodes.F_SAME, 0, null, 1, new Object[] { TYPE_EVENT_WRITER.getInternalName() });
                mv.visitInsn(Opcodes.POP);
                mv.visitLabel(end);
                // stack:
                mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
                mv.visitInsn(Opcodes.RETURN);
                mv.visitMaxs(0, 0);
                mv.visitEnd();
            });
        } else {
            updateMethod(METHOD_COMMIT, methodVisitor -> {
                // if (!isEnable()) {
                // return;
                // }
                methodVisitor.visitCode();
                Label start = new Label();
                Label endTryBlock = new Label();
                Label exceptionHandler = new Label();
                methodVisitor.visitTryCatchBlock(start, endTryBlock, exceptionHandler, "java/lang/Throwable");
                methodVisitor.visitLabel(start);
                methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
                methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, getInternalClassName(), METHOD_IS_ENABLED.getName(), METHOD_IS_ENABLED.getDescriptor(), false);
                Label l0 = new Label();
                methodVisitor.visitJumpInsn(Opcodes.IFNE, l0);
                methodVisitor.visitInsn(Opcodes.RETURN);
                methodVisitor.visitLabel(l0);
                methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
                // if (startTime == 0) {
                // startTime = EventWriter.timestamp();
                // } else {
                methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
                methodVisitor.visitFieldInsn(Opcodes.GETFIELD, getInternalClassName(), FIELD_START_TIME, "J");
                methodVisitor.visitInsn(Opcodes.LCONST_0);
                methodVisitor.visitInsn(Opcodes.LCMP);
                Label durationalEvent = new Label();
                methodVisitor.visitJumpInsn(Opcodes.IFNE, durationalEvent);
                methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
                methodVisitor.visitMethodInsn(Opcodes.INVOKESTATIC, TYPE_EVENT_CONFIGURATION.getInternalName(), METHOD_TIME_STAMP.getName(), METHOD_TIME_STAMP.getDescriptor(), false);
                methodVisitor.visitFieldInsn(Opcodes.PUTFIELD, getInternalClassName(), FIELD_START_TIME, "J");
                Label commit = new Label();
                methodVisitor.visitJumpInsn(Opcodes.GOTO, commit);
                // if (duration == 0) {
                // duration = EventWriter.timestamp() - startTime;
                // }
                // }
                methodVisitor.visitLabel(durationalEvent);
                methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
                methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
                methodVisitor.visitFieldInsn(Opcodes.GETFIELD, getInternalClassName(), FIELD_DURATION, "J");
                methodVisitor.visitInsn(Opcodes.LCONST_0);
                methodVisitor.visitInsn(Opcodes.LCMP);
                methodVisitor.visitJumpInsn(Opcodes.IFNE, commit);
                methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
                methodVisitor.visitMethodInsn(Opcodes.INVOKESTATIC, TYPE_EVENT_CONFIGURATION.getInternalName(), METHOD_TIME_STAMP.getName(), METHOD_TIME_STAMP.getDescriptor(), false);
                methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
                methodVisitor.visitFieldInsn(Opcodes.GETFIELD, getInternalClassName(), FIELD_START_TIME, "J");
                methodVisitor.visitInsn(Opcodes.LSUB);
                methodVisitor.visitFieldInsn(Opcodes.PUTFIELD, getInternalClassName(), FIELD_DURATION, "J");
                methodVisitor.visitLabel(commit);
                // if (shouldCommit()) {
                methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
                methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
                invokeVirtual(methodVisitor, getInternalClassName(), METHOD_EVENT_SHOULD_COMMIT);
                Label end = new Label();
                methodVisitor.visitJumpInsn(Opcodes.IFEQ, end);
                getEventWriter(methodVisitor);
                // stack: [EW]
                methodVisitor.visitInsn(Opcodes.DUP);
                // stack: [EW] [EW]
                getEventConfiguration(methodVisitor);
                // stack: [EW] [EW] [EC]
                methodVisitor.visitLdcInsn(eventTypeId);
                invokeVirtual(methodVisitor, TYPE_EVENT_WRITER, EventWriterMethod.BEGIN_EVENT.asmMethod);
                Label excluded = new Label();
                // stack: [EW] [int]
                methodVisitor.visitJumpInsn(Opcodes.IFEQ, excluded);
                // stack: [EW]
                int fieldIndex = 0;
                methodVisitor.visitInsn(Opcodes.DUP);
                // stack: [EW] [EW]
                methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
                // stack: [EW] [EW] [this]
                methodVisitor.visitFieldInsn(Opcodes.GETFIELD, getInternalClassName(), FIELD_START_TIME, "J");
                // stack: [EW] [EW] [long]
                invokeVirtual(methodVisitor, TYPE_EVENT_WRITER, EventWriterMethod.PUT_LONG.asmMethod);
                // stack: [EW]
                fieldIndex++;
                methodVisitor.visitInsn(Opcodes.DUP);
                // stack: [EW] [EW]
                methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
                // stack: [EW] [EW] [this]
                methodVisitor.visitFieldInsn(Opcodes.GETFIELD, getInternalClassName(), FIELD_DURATION, "J");
                // stack: [EW] [EW] [long]
                invokeVirtual(methodVisitor, TYPE_EVENT_WRITER, EventWriterMethod.PUT_LONG.asmMethod);
                // stack: [EW]
                fieldIndex++;
                methodVisitor.visitInsn(Opcodes.DUP);
                // stack: [EW] [EW]
                invokeVirtual(methodVisitor, TYPE_EVENT_WRITER, EventWriterMethod.PUT_EVENT_THREAD.asASM());
                // stack: [EW]
                methodVisitor.visitInsn(Opcodes.DUP);
                // stack: [EW] [EW]
                invokeVirtual(methodVisitor, TYPE_EVENT_WRITER, EventWriterMethod.PUT_STACK_TRACE.asASM());
                // stack: [EW]
                while (fieldIndex < fieldInfos.size()) {
                    FieldInfo field = fieldInfos.get(fieldIndex);
                    methodVisitor.visitInsn(Opcodes.DUP);
                    // stack: [EW] [EW]
                    methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
                    // stack: [EW] [EW] [this]
                    methodVisitor.visitFieldInsn(Opcodes.GETFIELD, getInternalClassName(), field.fieldName, field.fieldDescriptor);
                    // stack: [EW] [EW] <T>
                    EventWriterMethod eventMethod = EventWriterMethod.lookupMethod(field);
                    invokeVirtual(methodVisitor, TYPE_EVENT_WRITER, eventMethod.asmMethod);
                    // stack: [EW]
                    fieldIndex++;
                }
                // stack:[EW]
                invokeVirtual(methodVisitor, TYPE_EVENT_WRITER, EventWriterMethod.END_EVENT.asASM());
                // stack [int]
                // notified -> restart event write attempt
                methodVisitor.visitJumpInsn(Opcodes.IFEQ, start);
                methodVisitor.visitLabel(endTryBlock);
                methodVisitor.visitJumpInsn(Opcodes.GOTO, end);
                methodVisitor.visitLabel(exceptionHandler);
                // stack: [ex]
                methodVisitor.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[] { "java/lang/Throwable" });
                getEventWriter(methodVisitor);
                // stack: [ex] [EW]
                methodVisitor.visitInsn(Opcodes.DUP);
                // stack: [ex] [EW] [EW]
                Label rethrow = new Label();
                methodVisitor.visitJumpInsn(Opcodes.IFNULL, rethrow);
                // stack: [ex] [EW]
                methodVisitor.visitInsn(Opcodes.DUP);
                // stack: [ex] [EW] [EW]
                visitMethod(methodVisitor, Opcodes.INVOKEVIRTUAL, TYPE_EVENT_WRITER, METHOD_RESET);
                methodVisitor.visitLabel(rethrow);
                // stack:[ex] [EW]
                methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 2, new Object[] { "java/lang/Throwable", TYPE_EVENT_WRITER.getInternalName() });
                methodVisitor.visitInsn(Opcodes.POP);
                // stack:[ex]
                methodVisitor.visitInsn(Opcodes.ATHROW);
                methodVisitor.visitLabel(excluded);
                // stack: [EW]
                methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 1, new Object[] { TYPE_EVENT_WRITER.getInternalName() });
                methodVisitor.visitInsn(Opcodes.POP);
                methodVisitor.visitLabel(end);
                // stack:
                methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
                methodVisitor.visitInsn(Opcodes.RETURN);
                methodVisitor.visitMaxs(0, 0);
                methodVisitor.visitEnd();
            });
        }

        // MyEvent#shouldCommit()
        updateMethod(METHOD_EVENT_SHOULD_COMMIT, methodVisitor -> {
            Label fail = new Label();
            if (guardEventConfiguration) {
                getEventConfiguration(methodVisitor);
                methodVisitor.visitJumpInsn(Opcodes.IFNULL, fail);
            }
            // if (!eventConfiguration.shouldCommit(duration) goto fail;
            getEventConfiguration(methodVisitor);
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
            methodVisitor.visitFieldInsn(Opcodes.GETFIELD, getInternalClassName(), FIELD_DURATION, "J");
            invokeVirtual(methodVisitor, TYPE_EVENT_CONFIGURATION, METHOD_EVENT_CONFIGURATION_SHOULD_COMMIT);
            methodVisitor.visitJumpInsn(Opcodes.IFEQ, fail);
            int index = 0;
            for (SettingInfo si : settingInfos) {
                // if (!settingsMethod(eventConfiguration.settingX)) goto fail;
                methodVisitor.visitIntInsn(Opcodes.ALOAD, 0);
                if (untypedEventConfiguration) {
                    methodVisitor.visitFieldInsn(Opcodes.GETSTATIC, getInternalClassName(), FIELD_EVENT_CONFIGURATION, TYPE_OBJECT_DESCRIPTOR);
                } else {
                    methodVisitor.visitFieldInsn(Opcodes.GETSTATIC, getInternalClassName(), FIELD_EVENT_CONFIGURATION, TYPE_EVENT_CONFIGURATION_DESCRIPTOR);
                }
                methodVisitor.visitTypeInsn(Opcodes.CHECKCAST, TYPE_EVENT_CONFIGURATION.getInternalName());
                methodVisitor.visitLdcInsn(index);
                invokeVirtual(methodVisitor, TYPE_EVENT_CONFIGURATION, METHOD_EVENT_CONFIGURATION_GET_SETTING);
                methodVisitor.visitTypeInsn(Opcodes.CHECKCAST, si.paramType().getInternalName());
                methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, getInternalClassName(), si.methodName, "(" + si.paramType().getDescriptor() + ")Z", false);
                methodVisitor.visitJumpInsn(Opcodes.IFEQ, fail);
                index++;
            }
            // return true
            methodVisitor.visitInsn(Opcodes.ICONST_1);
            methodVisitor.visitInsn(Opcodes.IRETURN);
            // return false
            methodVisitor.visitLabel(fail);
            methodVisitor.visitInsn(Opcodes.ICONST_0);
            methodVisitor.visitInsn(Opcodes.IRETURN);
        });

        if (isJDK) {
            if (hasStaticMethod(METHOD_ENABLED)) {
                updateEnabledMethod(METHOD_ENABLED);
            };
            updateIfStaticMethodExists(METHOD_SHOULD_COMMIT_LONG, methodVisitor -> {
                Label fail = new Label();
                if (guardEventConfiguration) {
                    // if (eventConfiguration == null) goto fail;
                    getEventConfiguration(methodVisitor);
                    methodVisitor.visitJumpInsn(Opcodes.IFNULL, fail);
                }
                // return eventConfiguration.shouldCommit(duration);
                getEventConfiguration(methodVisitor);
                methodVisitor.visitVarInsn(Opcodes.LLOAD, 0);
                invokeVirtual(methodVisitor, TYPE_EVENT_CONFIGURATION, METHOD_EVENT_CONFIGURATION_SHOULD_COMMIT);
                methodVisitor.visitInsn(Opcodes.IRETURN);
                // fail:
                methodVisitor.visitLabel(fail);
                // return false
                methodVisitor.visitInsn(Opcodes.ICONST_0);
                methodVisitor.visitInsn(Opcodes.IRETURN);
                methodVisitor.visitMaxs(0, 0);
                methodVisitor.visitEnd();
            });
            updateIfStaticMethodExists(METHOD_TIME_STAMP, methodVisitor -> {
                invokeStatic(methodVisitor, TYPE_EVENT_CONFIGURATION.getInternalName(), METHOD_TIME_STAMP);
                methodVisitor.visitInsn(Opcodes.LRETURN);
                methodVisitor.visitMaxs(0, 0);
                methodVisitor.visitEnd();
            });
        }
    }

    private void updateEnabledMethod(Method method) {
        updateMethod(method, methodVisitor -> {
            Label nullLabel = new Label();
            if (guardEventConfiguration) {
                getEventConfiguration(methodVisitor);
                methodVisitor.visitJumpInsn(Opcodes.IFNULL, nullLabel);
            }
            getEventConfiguration(methodVisitor);
            invokeVirtual(methodVisitor, TYPE_EVENT_CONFIGURATION, METHOD_IS_ENABLED);
            methodVisitor.visitInsn(Opcodes.IRETURN);
            if (guardEventConfiguration) {
                methodVisitor.visitLabel(nullLabel);
                methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
                methodVisitor.visitInsn(Opcodes.ICONST_0);
                methodVisitor.visitInsn(Opcodes.IRETURN);
            }
            methodVisitor.visitMaxs(0, 0);
            methodVisitor.visitEnd();
        });
    }

    private void updateIfStaticMethodExists(Method method, Consumer<MethodVisitor> code) {
        if (hasStaticMethod(method)) {
            updateMethod(method, code);
        }
    }

    private boolean hasStaticMethod(Method method) {
        for (MethodNode m : classNode.methods) {
            if (m.name.equals(method.getName()) && m.desc.equals(method.getDescriptor())) {
                return Modifier.isStatic(m.access);
            }
        }
        return false;
    }

    private void getEventWriter(MethodVisitor mv) {
        mv.visitLdcInsn(EventWriterKey.getKey());
        visitMethod(mv, Opcodes.INVOKESTATIC, TYPE_EVENT_WRITER_FACTORY, METHOD_GET_EVENT_WRITER_KEY);
    }

    private void visitMethod(final MethodVisitor mv, final int opcode, final Type type, final Method method) {
        mv.visitMethodInsn(opcode, type.getInternalName(), method.getName(), method.getDescriptor(), false);
    }

    private static void invokeStatic(MethodVisitor methodVisitor, String className, Method m) {
        methodVisitor.visitMethodInsn(Opcodes.INVOKESTATIC, className, m.getName(), m.getDescriptor(), false);
    }

    private static void invokeVirtual(MethodVisitor methodVisitor, String className, Method m) {
        methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, className, m.getName(), m.getDescriptor(), false);
    }

    private void invokeVirtual(MethodVisitor methodVisitor, Type type, Method method) {
        methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, type.getInternalName(), method.getName(), method.getDescriptor(), false);
    }

    private void getEventConfiguration(MethodVisitor methodVisitor) {
        if (untypedEventConfiguration) {
            methodVisitor.visitFieldInsn(Opcodes.GETSTATIC, getInternalClassName(), FIELD_EVENT_CONFIGURATION, TYPE_OBJECT_DESCRIPTOR);
        } else {
            methodVisitor.visitFieldInsn(Opcodes.GETSTATIC, getInternalClassName(), FIELD_EVENT_CONFIGURATION, TYPE_EVENT_CONFIGURATION_DESCRIPTOR);
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

    private final void updateExistingWithEmptyVoidMethod(Method voidMethod) {
        updateMethod(voidMethod, methodVisitor -> {
            methodVisitor.visitInsn(Opcodes.RETURN);
        });
    }

    private final void updateExistingWithReturnFalse(Method voidMethod) {
        updateMethod(voidMethod, methodVisitor -> {
            methodVisitor.visitInsn(Opcodes.ICONST_0);
            methodVisitor.visitInsn(Opcodes.IRETURN);
        });
    }

    private MethodNode getMethodNode(Method method) {
        for (MethodNode m : classNode.methods) {
            if (m.name.equals(method.getName()) && m.desc.equals(method.getDescriptor())) {
                return m;
            }
        }
        return null;
    }

    private final void updateMethod(Method method, Consumer<MethodVisitor> code) {
        MethodNode old = getMethodNode(method);
        int index = classNode.methods.indexOf(old);
        classNode.methods.remove(old);
        MethodVisitor mv = classNode.visitMethod(old.access, old.name, old.desc, null, null);
        mv.visitCode();
        code.accept(mv);
        mv.visitMaxs(0, 0);
        MethodNode newMethod = getMethodNode(method);
        classNode.methods.remove(newMethod);
        classNode.methods.add(index, newMethod);
    }

    private String getInternalClassName() {
        return classNode.name;
    }

    public String getEventName() {
        return eventName;
    }
}
