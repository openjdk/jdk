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
import static jdk.jfr.internal.util.Bytecode.getfield;
import static jdk.jfr.internal.util.Bytecode.invokestatic;
import static jdk.jfr.internal.util.Bytecode.invokevirtual;
import static jdk.jfr.internal.util.Bytecode.putfield;

import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassElement;
import java.lang.classfile.ClassFile;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.CodeBuilder.BlockCodeBuilder;
import java.lang.classfile.FieldModel;
import java.lang.classfile.Label;
import java.lang.classfile.MethodModel;
import java.lang.classfile.MethodTransform;
import java.lang.classfile.TypeKind;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.List;
import java.util.function.Consumer;

import jdk.jfr.Event;
import jdk.jfr.SettingControl;
import jdk.jfr.internal.event.EventConfiguration;
import jdk.jfr.internal.event.EventWriter;
import jdk.jfr.internal.util.Bytecode;
import jdk.jfr.internal.util.Bytecode.FieldDesc;
import jdk.jfr.internal.util.Bytecode.MethodDesc;
import jdk.jfr.internal.util.Bytecode.SettingDesc;
import jdk.jfr.internal.util.ImplicitFields;

/**
 * Class responsible for adding instrumentation to a subclass of {@link Event}.
 *
 */
public final class EventInstrumentation {
    public static final long MASK_THROTTLE               = 1L << 62;
    public static final long MASK_THROTTLE_CHECK         = 1L << 63;
    public static final long MASK_THROTTLE_BITS          = MASK_THROTTLE | MASK_THROTTLE_CHECK;
    public static final long MASK_THROTTLE_CHECK_SUCCESS = MASK_THROTTLE_CHECK | MASK_THROTTLE;
    public static final long MASK_THROTTLE_CHECK_FAIL    = MASK_THROTTLE_CHECK | 0;
    public static final long MASK_NON_THROTTLE_BITS      = ~MASK_THROTTLE_BITS;

    private static final FieldDesc FIELD_EVENT_CONFIGURATION = FieldDesc.of(Object.class, "eventConfiguration");

    private static final ClassDesc TYPE_EVENT_CONFIGURATION = classDesc(EventConfiguration.class);
    private static final ClassDesc TYPE_ISE = classDesc(IllegalStateException.class);
    private static final ClassDesc TYPE_EVENT_WRITER = classDesc(EventWriter.class);
    private static final ClassDesc TYPE_OBJECT = classDesc(Object.class);

    private static final MethodDesc METHOD_BEGIN = MethodDesc.of("begin", "()V");
    private static final MethodDesc METHOD_COMMIT = MethodDesc.of("commit", "()V");
    private static final MethodDesc METHOD_DURATION = MethodDesc.of("duration", "(J)J");
    private static final MethodDesc METHOD_THROTTLE = MethodDesc.of("throttle", "(JJ)J");
    private static final MethodDesc METHOD_ENABLED = MethodDesc.of("enabled", "()Z");
    private static final MethodDesc METHOD_END = MethodDesc.of("end", "()V");
    private static final MethodDesc METHOD_EVENT_CONFIGURATION_SHOULD_COMMIT_LONG = MethodDesc.of("shouldCommit", "(J)Z");
    private static final MethodDesc METHOD_EVENT_CONFIGURATION_SHOULD_THROTTLE_COMMIT_LONG_LONG = MethodDesc.of("shouldThrottleCommit", "(JJ)Z");
    private static final MethodDesc METHOD_EVENT_CONFIGURATION_SHOULD_THROTTLE_COMMIT_LONG = MethodDesc.of("shouldThrottleCommit", "(J)Z");

    private static final MethodDesc METHOD_EVENT_CONFIGURATION_GET_SETTING = MethodDesc.of("getSetting", SettingControl.class, int.class);
    private static final MethodDesc METHOD_EVENT_SHOULD_COMMIT = MethodDesc.of("shouldCommit", "()Z");
    private static final MethodDesc METHOD_EVENT_SHOULD_THROTTLE_COMMIT_LONG_LONG = MethodDesc.of("shouldThrottleCommit", "(JJ)Z");
    private static final MethodDesc METHOD_EVENT_SHOULD_THROTTLE_COMMIT_LONG = MethodDesc.of("shouldThrottleCommit", "(J)Z");
    private static final MethodDesc METHOD_GET_EVENT_WRITER = MethodDesc.of("getEventWriter", "()" + TYPE_EVENT_WRITER.descriptorString());
    private static final MethodDesc METHOD_IS_ENABLED = MethodDesc.of("isEnabled", "()Z");
    private static final MethodDesc METHOD_RESET = MethodDesc.of("reset", "()V");
    private static final MethodDesc METHOD_SHOULD_COMMIT_LONG = MethodDesc.of("shouldCommit", "(J)Z");
    private static final MethodDesc METHOD_TIME_STAMP = MethodDesc.of("timestamp", "()J");

    private final ClassInspector inspector;
    private final long eventTypeId;
    private final ClassDesc eventClassDesc;
    private final MethodDesc staticCommitMethod;
    private final boolean untypedEventConfiguration;
    private final boolean guardEventConfiguration;
    private final boolean throttled;

    /**
     * Creates an EventInstrumentation object.
     *
     * @param inspector               class inspector
     * @param id                      the event type ID to use
     * @param guardEventConfiguration guard against event configuration being null.
     *                                Needed when instrumentation is added before
     *                                registration (bytesForEagerInstrumentation)
     */
    EventInstrumentation(ClassInspector inspector, long id, boolean guardEventConfiguration) {
        inspector.buildFields();
        if (!inspector.isJDK()) {
            // Only user-defined events have custom settings.
            inspector.buildSettings();
        }
        this.inspector = inspector;
        this.eventTypeId = id;
        this.guardEventConfiguration = guardEventConfiguration;
        this.eventClassDesc = inspector.getClassDesc();
        this.staticCommitMethod = inspector.findStaticCommitMethod();
        this.untypedEventConfiguration = hasUntypedConfiguration();
        if (inspector.isJDK()) {
            this.throttled = inspector.hasStaticMethod(METHOD_EVENT_SHOULD_THROTTLE_COMMIT_LONG_LONG);
        } else {
            this.throttled = inspector.isThrottled();
        }
    }

    byte[] buildInstrumented() {
        return ClassFile.of().transformClass(inspector.getClassModel(), this::transform);
    }

    private void transform(ClassBuilder clb, ClassElement cle) {
        if (cle instanceof MethodModel method && instrumentable(method) instanceof Consumer<CodeBuilder> modification) {
            clb.transformMethod(method, MethodTransform.transformingCode((codeBuilder, _) -> modification.accept(codeBuilder)));
        } else {
            clb.with(cle);
        }
    }

    private Consumer<CodeBuilder> instrumentable(MethodModel method) {
        if (isMethod(method, METHOD_IS_ENABLED)) {
            return this::methodIsEnabled;
        }
        if (isMethod(method, METHOD_BEGIN)) {
            return this::methodBegin;
        }
        if (isMethod(method, METHOD_END)) {
            return this::methodEnd;
        }
        if (isMethod(method, METHOD_EVENT_SHOULD_COMMIT)) {
            return this::methodShouldCommit;
        }
        if (staticCommitMethod == null && isMethod(method, METHOD_COMMIT)) {
            return this::methodCommit;
        }
        if (inspector.isJDK() && isStatic(method)) {
            if (isMethod(method, METHOD_ENABLED)) {
                return this::methodEnabledStatic;
            }
            if (isMethod(method, METHOD_SHOULD_COMMIT_LONG)) {
                return this::methodShouldCommitStatic;
            }
            if (isMethod(method, METHOD_EVENT_SHOULD_THROTTLE_COMMIT_LONG_LONG)) {
                return this::methodShouldCommitThrottleStaticLongLong;
            }
            if (isMethod(method, METHOD_EVENT_SHOULD_THROTTLE_COMMIT_LONG)) {
                return this::methodShouldCommitThrottleStaticLong;
            }
            if (isMethod(method, METHOD_TIME_STAMP)) {
                return this::methodTimestamp;
            }
            if (staticCommitMethod != null && isMethod(method, staticCommitMethod)) {
                return this::methodCommit;
            }
        }
        return null;
    }

    private void methodIsEnabled(CodeBuilder codeBuilder) {
        Label nullLabel = codeBuilder.newLabel();
        if (guardEventConfiguration) {
            getEventConfiguration(codeBuilder);
            codeBuilder.ifnull(nullLabel);
        }
        getEventConfiguration(codeBuilder);
        invokevirtual(codeBuilder, TYPE_EVENT_CONFIGURATION, METHOD_IS_ENABLED);
        codeBuilder.ireturn();
        if (guardEventConfiguration) {
            codeBuilder.labelBinding(nullLabel);
            codeBuilder.iconst_0();
            codeBuilder.ireturn();
        }
    }

    private void methodBegin(CodeBuilder codeBuilder) {
        if (!inspector.hasDuration()) {
            throwMissingDuration(codeBuilder, "begin");
        } else {
            codeBuilder.aload(0);
            invokestatic(codeBuilder, TYPE_EVENT_CONFIGURATION, METHOD_TIME_STAMP);
            putfield(codeBuilder, eventClassDesc, ImplicitFields.FIELD_START_TIME);
            codeBuilder.return_();
        }
    }

    private void methodEnd(CodeBuilder codeBuilder) {
        if (!inspector.hasDuration()) {
            throwMissingDuration(codeBuilder, "end");
        } else {
            setDuration(codeBuilder, cb -> {
                codeBuilder.aload(0);
                getfield(codeBuilder, eventClassDesc, ImplicitFields.FIELD_START_TIME);
                invokestatic(codeBuilder, TYPE_EVENT_CONFIGURATION, METHOD_DURATION);
            });
            codeBuilder.return_();
        }
    }

    private void methodShouldCommit(CodeBuilder codeBuilder) {
        Label fail = codeBuilder.newLabel();
        if (guardEventConfiguration) {
            getEventConfiguration(codeBuilder);
            codeBuilder.ifnull(fail);
        }
        // if (!eventConfiguration.shouldCommit(duration) goto fail;
        getEventConfiguration(codeBuilder);
        getDuration(codeBuilder);
        invokevirtual(codeBuilder, TYPE_EVENT_CONFIGURATION, METHOD_EVENT_CONFIGURATION_SHOULD_COMMIT_LONG);
        codeBuilder.ifeq(fail);
        List<SettingDesc> settingDescs = inspector.getSettings();
        for (int index = 0; index < settingDescs.size(); index++) {
            SettingDesc sd = settingDescs.get(index);
            // if (!settingsMethod(eventConfiguration.settingX)) goto fail;
            codeBuilder.aload(0);
            getEventConfiguration(codeBuilder);
            codeBuilder.loadConstant(index);
            invokevirtual(codeBuilder, TYPE_EVENT_CONFIGURATION, METHOD_EVENT_CONFIGURATION_GET_SETTING);
            MethodTypeDesc mdesc = MethodTypeDesc.ofDescriptor("(" + sd.paramType().descriptorString() + ")Z");
            codeBuilder.checkcast(sd.paramType());
            codeBuilder.invokevirtual(eventClassDesc, sd.methodName(), mdesc);
            codeBuilder.ifeq(fail);
        }
        if (throttled) {
            // long d =  eventConfiguration.throttle(this.duration);
            // this.duration = d;
            // if (d & MASK_THROTTLE_BIT == 0) {
            //   goto fail;
            // }
            getEventConfiguration(codeBuilder);
            codeBuilder.aload(0);
            getfield(codeBuilder, eventClassDesc, ImplicitFields.FIELD_START_TIME);
            codeBuilder.aload(0);
            getfield(codeBuilder, eventClassDesc, ImplicitFields.FIELD_DURATION);
            Bytecode.invokevirtual(codeBuilder, TYPE_EVENT_CONFIGURATION, METHOD_THROTTLE);
            int result = codeBuilder.allocateLocal(TypeKind.LONG);
            codeBuilder.lstore(result);
            codeBuilder.aload(0);
            codeBuilder.lload(result);
            putfield(codeBuilder, eventClassDesc, ImplicitFields.FIELD_DURATION);
            codeBuilder.lload(result);
            codeBuilder.ldc(MASK_THROTTLE);
            codeBuilder.land();
            codeBuilder.lconst_0();
            codeBuilder.lcmp();
            codeBuilder.ifeq(fail);
         }
        // return true
        codeBuilder.iconst_1();
        codeBuilder.ireturn();
        // return false
        codeBuilder.labelBinding(fail);
        codeBuilder.iconst_0();
        codeBuilder.ireturn();
    }

    private void methodCommit(CodeBuilder codeBuilder) {
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
                catchAllHandler.ifnull(rethrow);
                // stack: [ex] [EW]
                catchAllHandler.dup();
                // stack: [ex] [EW] [EW]
                invokevirtual(catchAllHandler, TYPE_EVENT_WRITER, METHOD_RESET);
                catchAllHandler.labelBinding(rethrow);
                // stack:[ex] [EW]
                catchAllHandler.pop();
                // stack:[ex]
                catchAllHandler.athrow();
            });
        });
        codeBuilder.labelBinding(excluded);
        // stack: [EW]
        codeBuilder.pop();
        codeBuilder.labelBinding(end);
        // stack: []
        codeBuilder.return_();
    }

    private void methodEnabledStatic(CodeBuilder codeBuilder) {
        Label nullLabel = codeBuilder.newLabel();
        if (guardEventConfiguration) {
            getEventConfiguration(codeBuilder);
            codeBuilder.ifnull(nullLabel);
        }
        getEventConfiguration(codeBuilder);
        invokevirtual(codeBuilder, TYPE_EVENT_CONFIGURATION, METHOD_IS_ENABLED);
        codeBuilder.ireturn();
        if (guardEventConfiguration) {
            codeBuilder.labelBinding(nullLabel);
            codeBuilder.iconst_0();
            codeBuilder.ireturn();
        }
    }

    private void methodTimestamp(CodeBuilder codeBuilder) {
        invokestatic(codeBuilder, TYPE_EVENT_CONFIGURATION, METHOD_TIME_STAMP);
        codeBuilder.lreturn();
    }

    private void methodShouldCommitStatic(CodeBuilder codeBuilder) {
        methodShouldCommitStatic(codeBuilder, METHOD_EVENT_CONFIGURATION_SHOULD_COMMIT_LONG);
    }

    private void methodShouldCommitThrottleStaticLongLong(CodeBuilder codeBuilder) {
        methodShouldCommitStatic(codeBuilder, METHOD_EVENT_CONFIGURATION_SHOULD_THROTTLE_COMMIT_LONG_LONG);
    }
    private void methodShouldCommitThrottleStaticLong(CodeBuilder codeBuilder) {
        methodShouldCommitStatic(codeBuilder, METHOD_EVENT_CONFIGURATION_SHOULD_THROTTLE_COMMIT_LONG);
    }

    private void methodShouldCommitStatic(CodeBuilder codeBuilder, MethodDesc method) {
        Label fail = codeBuilder.newLabel();
        if (guardEventConfiguration) {
            // if (eventConfiguration == null) goto fail;
            getEventConfiguration(codeBuilder);
            codeBuilder.ifnull(fail);
        }
        // return eventConfiguration.shouldCommit(duration);
        getEventConfiguration(codeBuilder);
        for (int i = 0 ; i < method.descriptor().parameterCount(); i++) {
            codeBuilder.lload(2 * i);
        }
        codeBuilder.invokevirtual(TYPE_EVENT_CONFIGURATION, method.name(), method.descriptor());
        codeBuilder.ireturn();
        // fail:
        codeBuilder.labelBinding(fail);
        // return false
        codeBuilder.iconst_0();
        codeBuilder.ireturn();
    }

    private void throwMissingDuration(CodeBuilder codeBuilder, String method) {
        String message = "Cannot use method " + method + " when event lacks duration field";
        Bytecode.throwException(codeBuilder, TYPE_ISE, message);
    }

    private void updateStaticCommit(BlockCodeBuilder blockCodeBuilder, Label excluded) {
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
        blockCodeBuilder.loadConstant(eventTypeId);
        // stack: [EW], [EW], [EventConfiguration] [long]
        invokevirtual(blockCodeBuilder, TYPE_EVENT_WRITER, EventWriterMethod.BEGIN_EVENT.method());
        // stack: [EW], [integer]
        blockCodeBuilder.ifeq(excluded);
        // stack: [EW]
        // write startTime
        blockCodeBuilder.dup();
        // stack: [EW], [EW]
        tk = TypeKind.from(argumentTypes[argIndex++]);
        blockCodeBuilder.loadLocal(tk, slotIndex);
        // stack: [EW], [EW], [long]
        slotIndex += tk.slotSize();
        invokevirtual(blockCodeBuilder, TYPE_EVENT_WRITER, EventWriterMethod.PUT_LONG.method());
        fieldIndex++;
        // stack: [EW]
        if (inspector.hasDuration()) {
            // write duration
            blockCodeBuilder.dup();
            // stack: [EW], [EW]
            tk = TypeKind.from(argumentTypes[argIndex++]);
            blockCodeBuilder.loadLocal(tk, slotIndex);
            // stack: [EW], [EW], [long]
            slotIndex += tk.slotSize();
            invokevirtual(blockCodeBuilder, TYPE_EVENT_WRITER, EventWriterMethod.PUT_LONG.method());
            fieldIndex++;
        }
        // stack: [EW]
        if (inspector.hasEventThread()) {
            // write eventThread
            blockCodeBuilder.dup();
            // stack: [EW], [EW]
            invokevirtual(blockCodeBuilder, TYPE_EVENT_WRITER, EventWriterMethod.PUT_EVENT_THREAD.method());
        }
        // stack: [EW]
        if (inspector.hasStackTrace()) {
            // write stackTrace
            blockCodeBuilder.dup();
            // stack: [EW], [EW]
            invokevirtual(blockCodeBuilder, TYPE_EVENT_WRITER, EventWriterMethod.PUT_STACK_TRACE.method());
        }
        // stack: [EW]
        // write custom fields
        List<FieldDesc> fieldDescs = inspector.getFields();
        while (fieldIndex < fieldDescs.size()) {
            blockCodeBuilder.dup();
            // stack: [EW], [EW]
            tk = TypeKind.from(argumentTypes[argIndex++]);
            blockCodeBuilder.loadLocal(tk, slotIndex);
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

    private void updateInstanceCommit(BlockCodeBuilder blockCodeBuilder, Label end, Label excluded) {
        // if (!isEnable()) {
        // return;
        // }
        blockCodeBuilder.aload(0);
        invokevirtual(blockCodeBuilder, eventClassDesc, METHOD_IS_ENABLED);
        Label l0 = blockCodeBuilder.newLabel();
        blockCodeBuilder.ifne(l0);
        blockCodeBuilder.return_();
        blockCodeBuilder.labelBinding(l0);
        // long startTime = this.startTime
        blockCodeBuilder.aload(0);
        getfield(blockCodeBuilder, eventClassDesc, ImplicitFields.FIELD_START_TIME);
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
        if (throttled) {
            blockCodeBuilder.aload(0);
            blockCodeBuilder.lload(1);
            putfield(blockCodeBuilder, eventClassDesc, ImplicitFields.FIELD_START_TIME);
        }
        Label commit = blockCodeBuilder.newLabel();
        blockCodeBuilder.goto_(commit);
        //   if (duration == 0) {
        //     duration = EventWriter.timestamp() - startTime;
        //   }
        // }
        blockCodeBuilder.labelBinding(durationEvent);
        blockCodeBuilder.aload(0);
        getfield(blockCodeBuilder, eventClassDesc, ImplicitFields.FIELD_DURATION);
        blockCodeBuilder.lconst_0(); // also blocks throttled event
        blockCodeBuilder.lcmp();
        blockCodeBuilder.ifne(commit);
        blockCodeBuilder.aload(0);
        invokestatic(blockCodeBuilder, TYPE_EVENT_CONFIGURATION, METHOD_TIME_STAMP);
        blockCodeBuilder.lload(1);
        blockCodeBuilder.lsub();
        putfield(blockCodeBuilder, eventClassDesc, ImplicitFields.FIELD_DURATION);
        blockCodeBuilder.labelBinding(commit);
        // if (shouldCommit()) {
        blockCodeBuilder.aload(0);
        invokevirtual(blockCodeBuilder, eventClassDesc, METHOD_EVENT_SHOULD_COMMIT);
        blockCodeBuilder.ifeq(end);
        getEventWriter(blockCodeBuilder);
        // stack: [EW]
        blockCodeBuilder.dup();
        // stack: [EW] [EW]
        getEventConfiguration(blockCodeBuilder);
        // stack: [EW] [EW] [EC]
        blockCodeBuilder.loadConstant(eventTypeId);
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
        fieldIndex++;
        // stack: [EW]
        if (inspector.hasDuration()) {
            // write duration
            blockCodeBuilder.dup();
            // stack: [EW] [EW]
            getDuration(blockCodeBuilder);
            // stack: [EW] [EW] [long]
            invokevirtual(blockCodeBuilder, TYPE_EVENT_WRITER, EventWriterMethod.PUT_LONG.method());
            fieldIndex++;
        }
        // stack: [EW]
        if (inspector.hasEventThread()) {
            // write eventThread
            blockCodeBuilder.dup();
            // stack: [EW] [EW]
            invokevirtual(blockCodeBuilder, TYPE_EVENT_WRITER, EventWriterMethod.PUT_EVENT_THREAD.method());
        }
        // stack: [EW]
        if (inspector.hasStackTrace()) {
            // write stack trace
            blockCodeBuilder.dup();
            // stack: [EW] [EW]
            invokevirtual(blockCodeBuilder, TYPE_EVENT_WRITER, EventWriterMethod.PUT_STACK_TRACE.method());
        }
        // stack: [EW]
        List<FieldDesc> fieldDescs = inspector.getFields();
        while (fieldIndex < fieldDescs.size()) {
            FieldDesc field = fieldDescs.get(fieldIndex);
            blockCodeBuilder.dup();
            // stack: [EW] [EW]
            blockCodeBuilder.aload(0);
            // stack: [EW] [EW] [this]
            getfield(blockCodeBuilder, eventClassDesc, field);
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

    private static boolean isStatic(MethodModel method) {
        return (method.flags().flagsMask() & ClassFile.ACC_STATIC) != 0;
    }

    private static boolean isMethod(MethodModel m, MethodDesc desc) {
        return desc.matches(m);
    }

    private void getDuration(CodeBuilder codeBuilder) {
        codeBuilder.aload(0);
        getfield(codeBuilder, eventClassDesc, ImplicitFields.FIELD_DURATION);
        if (throttled) {
            codeBuilder.loadConstant(MASK_NON_THROTTLE_BITS);
            codeBuilder.land();
        }
    }

    private void setDuration(CodeBuilder codeBuilder, Consumer<CodeBuilder> expression) {
        codeBuilder.aload(0);
        expression.accept(codeBuilder);
        if (throttled) {
            codeBuilder.aload(0);
            getfield(codeBuilder, eventClassDesc, ImplicitFields.FIELD_DURATION);
            codeBuilder.loadConstant(MASK_THROTTLE_BITS);
            codeBuilder.land();
            codeBuilder.lor();
        }
        putfield(codeBuilder, eventClassDesc, ImplicitFields.FIELD_DURATION);
    }

    private static void getEventWriter(CodeBuilder codeBuilder) {
        invokestatic(codeBuilder, TYPE_EVENT_WRITER, METHOD_GET_EVENT_WRITER);
    }

    private void getEventConfiguration(CodeBuilder codeBuilder) {
        if (untypedEventConfiguration) {
            codeBuilder.getstatic(eventClassDesc, FIELD_EVENT_CONFIGURATION.name(), TYPE_OBJECT);
            codeBuilder.checkcast(TYPE_EVENT_CONFIGURATION);
        } else {
            codeBuilder.getstatic(eventClassDesc, FIELD_EVENT_CONFIGURATION.name(), TYPE_EVENT_CONFIGURATION);
        }
    }

    private boolean hasUntypedConfiguration() {
        for (FieldModel f : inspector.getClassModel().fields()) {
            if (f.fieldName().equalsString(FIELD_EVENT_CONFIGURATION.name())) {
                return f.fieldType().equalsString(TYPE_OBJECT.descriptorString());
            }
        }
        throw new InternalError("Class missing configuration field");
    }
}
