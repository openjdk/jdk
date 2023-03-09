/*
 * Copyright (c) 2013, 2021, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jfr.internal.instrument;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.AccessFlag;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import jdk.internal.classfile.ClassModel;
import jdk.internal.classfile.ClassTransform;
import jdk.internal.classfile.Classfile;
import jdk.internal.classfile.CodeModel;
import jdk.internal.classfile.FieldModel;
import jdk.internal.classfile.MethodModel;
import jdk.internal.classfile.TypeKind;
import jdk.internal.classfile.instruction.BranchInstruction;
import jdk.internal.classfile.instruction.InvokeInstruction;
import jdk.internal.classfile.instruction.LookupSwitchInstruction;
import jdk.internal.classfile.instruction.StoreInstruction;
import jdk.internal.classfile.instruction.TableSwitchInstruction;
import jdk.internal.classfile.components.ClassRemapper;
import jdk.internal.classfile.components.CodeLocalsShifter;
import jdk.internal.classfile.components.CodeRelabeler;
import jdk.internal.classfile.instruction.ReturnInstruction;

import jdk.jfr.internal.SecuritySupport;

/**
 * This class will perform byte code instrumentation given an "instrumentor" class.
 *
 * @see JITracer
 *
 * @author Staffan Larsen
 */
@Deprecated
final class JIClassInstrumentation {
    private final Class<?> instrumentor;
    private final String targetName;
    private final String instrumentorName;
    private final byte[] newBytes;
    private final ClassModel targetClassModel;
    private final ClassModel instrClassModel;

    /**
     * Creates an instance and performs the instrumentation.
     *
     * @param instrumentor instrumentor class
     * @param target target class
     * @param old_target_bytes bytes in target
     *
     * @throws ClassNotFoundException
     * @throws IOException
     */
    JIClassInstrumentation(Class<?> instrumentor, Class<?> target, byte[] old_target_bytes) throws ClassNotFoundException, IOException {
        instrumentorName = instrumentor.getName();
        this.targetName = target.getName();
        this.instrumentor = instrumentor;
        this.targetClassModel = Classfile.parse(old_target_bytes);
        this.instrClassModel = Classfile.parse(getOriginalClassBytes(instrumentor));
        //target model have invalid stack maps, so it needs to be extra scanned to resolve all labels
        for (var m : targetClassModel.methods()) {
            m.code().ifPresent(c -> c.forEachElement(el -> {
                if (el instanceof BranchInstruction br) {
                    br.target();
                } else if (el instanceof TableSwitchInstruction ts) {
                    ts.defaultTarget();
                    ts.cases();
                } else if (el instanceof LookupSwitchInstruction ls) {
                    ls.defaultTarget();
                    ls.cases();
                }
            }));
        }
        this.newBytes = makeBytecode();
    }

    private static byte[] getOriginalClassBytes(Class<?> clazz) throws IOException {
        String name = "/" + clazz.getName().replace(".", "/") + ".class";
        try (InputStream is = SecuritySupport.getResourceAsStream(name)) {
            return is.readAllBytes();
        }
    }

    private byte[] makeBytecode() throws IOException, ClassNotFoundException {

        // Find the methods to instrument and inline

        final Set<String> instrumentationMethods = new HashSet<>();
        for (final Method m : instrumentor.getDeclaredMethods()) {
            JIInstrumentationMethod im = m.getAnnotation(JIInstrumentationMethod.class);
            if (im != null) {
                StringBuilder sb = new StringBuilder();
                sb.append(m.getName()).append('(');
                for (var parameter : m.getParameterTypes()) {
                    sb.append(parameter.descriptorString());
                }
                sb.append(')').append(m.getReturnType().descriptorString());
                instrumentationMethods.add(sb.toString());
            }
        }

        return instrument(targetClassModel, instrClassModel, mm -> instrumentationMethods.contains(mm.methodName().stringValue() + mm.methodType().stringValue()));
    }

    /**
     * Get the instrumented byte codes that can be used to retransform the class.
     *
     * @return bytes
     */
    public byte[] getNewBytes() {
        return newBytes.clone();
    }

    private static byte[] instrument(ClassModel target, ClassModel instrumentor, Predicate<MethodModel> instrumentedMethodsFilter) {
        var instrumentorCodeMap = instrumentor.methods().stream()
                                              .filter(instrumentedMethodsFilter)
                                              .collect(Collectors.toMap(mm -> mm.methodName().stringValue() + mm.methodType().stringValue(), mm -> mm.code().orElseThrow()));
        var targetFieldNames = target.fields().stream().map(f -> f.fieldName().stringValue()).collect(Collectors.toSet());
        var targetMethods = target.methods().stream().map(m -> m.methodName().stringValue() + m.methodType().stringValue()).collect(Collectors.toSet());
        var instrumentorClassRemapper = ClassRemapper.of(Map.of(instrumentor.thisClass().asSymbol(), target.thisClass().asSymbol()));
        return target.transform(
                ClassTransform.transformingMethods(
                        instrumentedMethodsFilter,
                        (mb, me) -> {
                            if (me instanceof CodeModel targetCodeModel) {
                                var mm = targetCodeModel.parent().get();
                                //instrumented methods code is taken from instrumentor
                                mb.transformCode(instrumentorCodeMap.get(mm.methodName().stringValue() + mm.methodType().stringValue()),
                                        //all references to the instrumentor class are remapped to target class
                                        instrumentorClassRemapper.asCodeTransform()
                                        .andThen((codeBuilder, instrumentorCodeElement) -> {
                                            //all invocations of target methods from instrumentor are inlined
                                            if (instrumentorCodeElement instanceof InvokeInstruction inv
                                                && target.thisClass().asInternalName().equals(inv.owner().asInternalName())
                                                && mm.methodName().stringValue().equals(inv.name().stringValue())
                                                && mm.methodType().stringValue().equals(inv.type().stringValue())) {

                                                //store stacked method parameters into locals
                                                var storeStack = new ArrayDeque<StoreInstruction>();
                                                int slot = 0;
                                                if (!mm.flags().has(AccessFlag.STATIC))
                                                    storeStack.push(StoreInstruction.of(TypeKind.ReferenceType, slot++));
                                                for (var pt : mm.methodTypeSymbol().parameterList()) {
                                                    var tk = TypeKind.fromDescriptor(pt.descriptorString());
                                                    storeStack.push(StoreInstruction.of(tk, slot));
                                                    slot += tk.slotSize();
                                                }
                                                storeStack.forEach(codeBuilder::with);

                                                //inlined target locals must be shifted based on the actual instrumentor locals
                                                codeBuilder.block(inlinedBlockBuilder -> inlinedBlockBuilder
                                                        .transform(targetCodeModel, CodeLocalsShifter.of(mm.flags(), mm.methodTypeSymbol())
                                                        .andThen(CodeRelabeler.of())
                                                        .andThen((innerBuilder, shiftedTargetCode) -> {
                                                            //returns must be replaced with jump to the end of the inlined method
                                                            if (shiftedTargetCode instanceof ReturnInstruction)
                                                                innerBuilder.goto_(inlinedBlockBuilder.breakLabel());
                                                            else
                                                                innerBuilder.with(shiftedTargetCode);
                                                        })));
                                            } else
                                                codeBuilder.with(instrumentorCodeElement);
                                        }));
                            } else
                                mb.with(me);
                        })
                .andThen(ClassTransform.endHandler(clb ->
                    //remaining instrumentor fields and methods are injected at the end
                    clb.transform(instrumentor,
                            ClassTransform.dropping(cle ->
                                    !(cle instanceof FieldModel fm
                                            && !targetFieldNames.contains(fm.fieldName().stringValue()))
                                    && !(cle instanceof MethodModel mm
                                            && !"<init>".equals(mm.methodName().stringValue())
                                            && !targetMethods.contains(mm.methodName().stringValue() + mm.methodType().stringValue())))
                            //and instrumentor class references remapped to target class
                            .andThen(instrumentorClassRemapper)))));
    }
}
