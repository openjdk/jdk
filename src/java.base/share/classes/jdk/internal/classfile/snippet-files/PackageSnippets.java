/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.lang.invoke.MethodHandles;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

import java.lang.reflect.AccessFlag;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jdk.internal.classfile.ClassElement;
import jdk.internal.classfile.ClassHierarchyResolver;
import jdk.internal.classfile.ClassModel;
import jdk.internal.classfile.ClassTransform;
import jdk.internal.classfile.Classfile;
import jdk.internal.classfile.CodeElement;
import jdk.internal.classfile.CodeModel;
import jdk.internal.classfile.CodeTransform;
import jdk.internal.classfile.FieldModel;
import jdk.internal.classfile.MethodElement;
import jdk.internal.classfile.MethodModel;
import jdk.internal.classfile.Opcode;
import jdk.internal.classfile.TypeKind;
import jdk.internal.classfile.instruction.FieldInstruction;
import jdk.internal.classfile.instruction.InvokeInstruction;

import static java.util.stream.Collectors.toSet;
import jdk.internal.classfile.components.ClassRemapper;
import jdk.internal.classfile.components.CodeLocalsShifter;
import jdk.internal.classfile.components.CodeRelabeler;
import jdk.internal.classfile.instruction.ReturnInstruction;
import jdk.internal.classfile.instruction.StoreInstruction;

class PackageSnippets {
    void enumerateFieldsMethods1(byte[] bytes) {
        // @start region="enumerateFieldsMethods1"
        ClassModel cm = Classfile.of().parse(bytes);
        for (FieldModel fm : cm.fields())
            System.out.printf("Field %s%n", fm.fieldName().stringValue());
        for (MethodModel mm : cm.methods())
            System.out.printf("Method %s%n", mm.methodName().stringValue());
        // @end
    }

    void enumerateFieldsMethods2(byte[] bytes) {
        // @start region="enumerateFieldsMethods2"
        ClassModel cm = Classfile.of().parse(bytes);
        for (ClassElement ce : cm) {
            switch (ce) {
                case MethodModel mm -> System.out.printf("Method %s%n", mm.methodName().stringValue());
                case FieldModel fm -> System.out.printf("Field %s%n", fm.fieldName().stringValue());
                default -> { }
            }
        }
        // @end
    }

    void gatherDependencies1(byte[] bytes) {
        // @start region="gatherDependencies1"
        ClassModel cm = Classfile.of().parse(bytes);
        Set<ClassDesc> dependencies = new HashSet<>();

        for (ClassElement ce : cm) {
            if (ce instanceof MethodModel mm) {
                for (MethodElement me : mm) {
                    if (me instanceof CodeModel xm) {
                        for (CodeElement e : xm) {
                            switch (e) {
                                case InvokeInstruction i -> dependencies.add(i.owner().asSymbol());
                                case FieldInstruction i -> dependencies.add(i.owner().asSymbol());
                                default -> { }
                            }
                        }
                    }
                }
            }
        }
        // @end
    }

    void gatherDependencies2(byte[] bytes) {
        // @start region="gatherDependencies2"
        ClassModel cm = Classfile.of().parse(bytes);
        Set<ClassDesc> dependencies =
              cm.elementStream()
                .flatMap(ce -> ce instanceof MethodModel mm ? mm.elementStream() : Stream.empty())
                .flatMap(me -> me instanceof CodeModel com ? com.elementStream() : Stream.empty())
                .<ClassDesc>mapMulti((xe, c) -> {
                    switch (xe) {
                        case InvokeInstruction i -> c.accept(i.owner().asSymbol());
                        case FieldInstruction i -> c.accept(i.owner().asSymbol());
                        default -> { }
                    }
                })
                .collect(toSet());
        // @end
    }

    private static final ClassDesc CD_Hello = ClassDesc.of("Hello");
    private static final ClassDesc CD_Foo = ClassDesc.of("Foo");
    private static final ClassDesc CD_Bar = ClassDesc.of("Bar");
    private static final ClassDesc CD_System = ClassDesc.of("java.lang.System");
    private static final ClassDesc CD_PrintStream = ClassDesc.of("java.io.PrintStream");
    private static final MethodTypeDesc MTD_void_StringArray = MethodTypeDesc.of(ConstantDescs.CD_void, ConstantDescs.CD_String.arrayType());
    private static final MethodTypeDesc MTD_void_String = MethodTypeDesc.of(ConstantDescs.CD_void, ConstantDescs.CD_String);

    void writeHelloWorld1() {
        // @start region="helloWorld1"
        byte[] bytes = Classfile.of().build(CD_Hello,
                clb -> clb.withFlags(Classfile.ACC_PUBLIC)
                          .withMethod(ConstantDescs.INIT_NAME, ConstantDescs.MTD_void,
                                      Classfile.ACC_PUBLIC,
                                      mb -> mb.withCode(
                                              cob -> cob.aload(0)
                                                        .invokespecial(ConstantDescs.CD_Object,
                                                                       ConstantDescs.INIT_NAME, ConstantDescs.MTD_void)
                                                        .return_()))
                          .withMethod("main", MTD_void_StringArray, Classfile.ACC_PUBLIC + Classfile.ACC_STATIC,
                                      mb -> mb.withCode(
                                              cob -> cob.getstatic(CD_System, "out", CD_PrintStream)
                                                        .ldc("Hello World")
                                                        .invokevirtual(CD_PrintStream, "println", MTD_void_String)
                                                        .return_())));
        // @end
    }

    void writeHelloWorld2() {
        // @start region="helloWorld2"
        byte[] bytes = Classfile.of().build(CD_Hello,
                clb -> clb.withFlags(Classfile.ACC_PUBLIC)
                          .withMethodBody(ConstantDescs.INIT_NAME, ConstantDescs.MTD_void,
                                          Classfile.ACC_PUBLIC,
                                          cob -> cob.aload(0)
                                                    .invokespecial(ConstantDescs.CD_Object,
                                                                   ConstantDescs.INIT_NAME, ConstantDescs.MTD_void)
                                                    .return_())
                          .withMethodBody("main", MTD_void_StringArray, Classfile.ACC_PUBLIC + Classfile.ACC_STATIC,
                                          cob -> cob.getstatic(CD_System, "out", CD_PrintStream)
                                                    .ldc("Hello World")
                                                    .invokevirtual(CD_PrintStream, "println", MTD_void_String)
                                                    .return_()));
        // @end
    }

    void stripDebugMethods1(byte[] bytes) {
        // @start region="stripDebugMethods1"
        ClassModel classModel = Classfile.of().parse(bytes);
        byte[] newBytes = Classfile.of().build(classModel.thisClass().asSymbol(),
                classBuilder -> {
                    for (ClassElement ce : classModel) {
                        if (!(ce instanceof MethodModel mm
                                && mm.methodName().stringValue().startsWith("debug"))) {
                            classBuilder.with(ce);
                        }
                    }
                });
        // @end
    }

    void stripDebugMethods2(byte[] bytes) {
        // @start region="stripDebugMethods2"
        ClassTransform ct = (builder, element) -> {
            if (!(element instanceof MethodModel mm && mm.methodName().stringValue().startsWith("debug")))
                builder.with(element);
        };
        var cc = Classfile.of();
        byte[] newBytes = cc.transform(cc.parse(bytes), ct);
        // @end
    }

    void stripDebugMethods3(byte[] bytes) {
        // @start region="stripDebugMethods3"
        ClassTransform ct = ClassTransform.dropping(
                                    element -> element instanceof MethodModel mm
                                            && mm.methodName().stringValue().startsWith("debug"));
        // @end
    }

    void fooToBarTransform() {
        // @start region="fooToBarTransform"
        CodeTransform fooToBar = (b, e) -> {
            if (e instanceof InvokeInstruction i
                    && i.owner().asInternalName().equals("Foo")
                    && i.opcode() == Opcode.INVOKESTATIC)
                        b.invokeInstruction(i.opcode(), CD_Bar, i.name().stringValue(), i.typeSymbol(), i.isInterface());
            else b.with(e);
        };
        // @end
    }

    void instrumentCallsTransform() {
        // @start region="instrumentCallsTransform"
        CodeTransform instrumentCalls = (b, e) -> {
            if (e instanceof InvokeInstruction i) {
                b.getstatic(CD_System, "out", CD_PrintStream)
                 .ldc(i.name().stringValue())
                 .invokevirtual(CD_PrintStream, "println", MTD_void_String);
            }
            b.with(e);
        };
        // @end
    }

    void fooToBarUnrolled(ClassModel classModel) {
        // @start region="fooToBarUnrolled"
        byte[] newBytes = Classfile.of().build(classModel.thisClass().asSymbol(),
            classBuilder -> {
              for (ClassElement ce : classModel) {
                  if (ce instanceof MethodModel mm) {
                      classBuilder.withMethod(mm.methodName().stringValue(), mm.methodTypeSymbol(),
                                              mm.flags().flagsMask(),
                                              methodBuilder -> {
                                  for (MethodElement me : mm) {
                                      if (me instanceof CodeModel xm) {
                                          methodBuilder.withCode(codeBuilder -> {
                                              for (CodeElement e : xm) {
                                                  if (e instanceof InvokeInstruction i && i.owner().asInternalName().equals("Foo")
                                                                               && i.opcode() == Opcode.INVOKESTATIC)
                                                              codeBuilder.invokeInstruction(i.opcode(), CD_Bar,
                                                                                            i.name().stringValue(), i.typeSymbol(), i.isInterface());
                                                  else codeBuilder.with(e);
                                              }});
                                          }
                                          else
                                          methodBuilder.with(me);
                                      }
                                  });
                              }
                      else
                      classBuilder.with(ce);
                  }
              });
        // @end
    }

    void codeRelabeling(ClassModel classModel) {
        // @start region="codeRelabeling"
        byte[] newBytes = Classfile.of().transform(classModel,
                ClassTransform.transformingMethodBodies(
                        CodeTransform.ofStateful(CodeRelabeler::of)));
        // @end
    }

    // @start region="classInstrumentation"
    byte[] classInstrumentation(ClassModel target, ClassModel instrumentor, Predicate<MethodModel> instrumentedMethodsFilter) {
        var instrumentorCodeMap = instrumentor.methods().stream()
                                              .filter(instrumentedMethodsFilter)
                                              .collect(Collectors.toMap(mm -> mm.methodName().stringValue() + mm.methodType().stringValue(), mm -> mm.code().orElseThrow()));
        var targetFieldNames = target.fields().stream().map(f -> f.fieldName().stringValue()).collect(Collectors.toSet());
        var targetMethods = target.methods().stream().map(m -> m.methodName().stringValue() + m.methodType().stringValue()).collect(Collectors.toSet());
        var instrumentorClassRemapper = ClassRemapper.of(Map.of(instrumentor.thisClass().asSymbol(), target.thisClass().asSymbol()));
        return Classfile.of().transform(target,
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
                                                    var tk = TypeKind.from(pt);
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
                                            && !ConstantDescs.INIT_NAME.equals(mm.methodName().stringValue())
                                            && !targetMethods.contains(mm.methodName().stringValue() + mm.methodType().stringValue())))
                            //and instrumentor class references remapped to target class
                            .andThen(instrumentorClassRemapper)))));
    }
    // @end

    void resolverExample() {
        // @start region="lookup-class-hierarchy-resolver"
        MethodHandles.Lookup lookup = MethodHandles.lookup(); // @replace regex="MethodHandles\.lookup\(\)" replacement="..."
        ClassHierarchyResolver resolver = ClassHierarchyResolver.ofClassLoading(lookup).cached();
        // @end
    }
}
