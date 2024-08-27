/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @summary Testing ClassFile ClassPrinter.
 * @run junit ClassPrinterTest
 */
import java.io.IOException;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.util.List;
import java.util.Optional;
import java.lang.classfile.*;
import java.lang.classfile.attribute.*;
import java.lang.classfile.components.ClassPrinter;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.DynamicCallSiteDesc;
import java.lang.constant.MethodHandleDesc;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClassPrinterTest {

    ClassModel getClassModel() {
        var cc = ClassFile.of();
        return cc.parse(cc.build(ClassDesc.of("Foo"), clb ->
            clb.withVersion(61, 0)
                .withFlags(ClassFile.ACC_PUBLIC)
                .with(SourceFileAttribute.of("Foo.java"))
                .withSuperclass(ClassDesc.of("Boo"))
                .withInterfaceSymbols(ClassDesc.of("Phee"), ClassDesc.of("Phoo"))
                .with(InnerClassesAttribute.of(
                        InnerClassInfo.of(ClassDesc.of("Phee"), Optional.of(ClassDesc.of("Phoo")), Optional.of("InnerName"), ClassFile.ACC_PROTECTED),
                        InnerClassInfo.of(ClassDesc.of("Phoo"), Optional.empty(), Optional.empty(), ClassFile.ACC_PRIVATE)))
                .with(EnclosingMethodAttribute.of(ClassDesc.of("Phee"), Optional.of("enclosingMethod"), Optional.of(MethodTypeDesc.of(ConstantDescs.CD_Double, ConstantDescs.CD_Collection))))
                .with(SyntheticAttribute.of())
                .with(SignatureAttribute.of(ClassSignature.of(Signature.ClassTypeSig.of(ClassDesc.of("Boo")), Signature.ClassTypeSig.of(ClassDesc.of("Phee")), Signature.ClassTypeSig.of(ClassDesc.of("Phoo")))))
                .with(DeprecatedAttribute.of())
                .with(NestHostAttribute.of(ClassDesc.of("Phee")))
                .with(NestMembersAttribute.ofSymbols(ClassDesc.of("Phoo"), ClassDesc.of("Boo"), ClassDesc.of("Bee")))
                .with(RecordAttribute.of(RecordComponentInfo.of("fee", ClassDesc.of("Phoo"), List.of(
                        SignatureAttribute.of(Signature.of(ClassDesc.of("Phoo"))),
                        RuntimeInvisibleTypeAnnotationsAttribute.of(
                                TypeAnnotation.of(TypeAnnotation.TargetInfo.ofField(),
                                                  List.of(TypeAnnotation.TypePathComponent.WILDCARD),
                                                  ClassDesc.of("Boo"), List.of()))))))
                .with(RuntimeInvisibleAnnotationsAttribute.of(Annotation.of(ClassDesc.of("Phoo"), AnnotationElement.ofFloat("flfl", 2),  AnnotationElement.ofFloat("frfl", 3))))
                .with(PermittedSubclassesAttribute.ofSymbols(ClassDesc.of("Boo"), ClassDesc.of("Phoo")))
                .withField("f", ConstantDescs.CD_String, fb -> fb
                        .withFlags(ClassFile.ACC_PRIVATE)
                        .with(RuntimeVisibleAnnotationsAttribute.of(Annotation.of(ClassDesc.of("Phoo"), AnnotationElement.ofFloat("flfl", 0),  AnnotationElement.ofFloat("frfl", 1)))))
                .withMethod("m", MethodTypeDesc.of(ConstantDescs.CD_Void, ConstantDescs.CD_boolean, ConstantDescs.CD_Throwable), ClassFile.ACC_PROTECTED, mb -> mb
                        .with(AnnotationDefaultAttribute.of(AnnotationValue.ofArray(
                            AnnotationValue.ofBoolean(true),
                            AnnotationValue.ofByte((byte)12),
                            AnnotationValue.ofChar('c'),
                            AnnotationValue.ofClass(ClassDesc.of("Phee")),
                            AnnotationValue.ofDouble(1.3),
                            AnnotationValue.ofEnum(ClassDesc.of("Boo"), "BOO"),
                            AnnotationValue.ofFloat((float)3.7),
                            AnnotationValue.ofInt(33),
                            AnnotationValue.ofLong(3333),
                            AnnotationValue.ofShort((short)25),
                            AnnotationValue.ofString("BOO"),
                            AnnotationValue.ofAnnotation(Annotation.of(ClassDesc.of("Phoo"), AnnotationElement.of("param", AnnotationValue.ofInt(3)))))))
                        .with(RuntimeVisibleParameterAnnotationsAttribute.of(List.of(List.of(Annotation.of(ClassDesc.of("Phoo"), AnnotationElement.ofFloat("flfl", 22),  AnnotationElement.ofFloat("frfl", 11))))))
                        .with(RuntimeInvisibleParameterAnnotationsAttribute.of(List.of(List.of(Annotation.of(ClassDesc.of("Phoo"), AnnotationElement.ofFloat("flfl", -22),  AnnotationElement.ofFloat("frfl", -11))))))
                        .with(ExceptionsAttribute.ofSymbols(ClassDesc.of("Phoo"), ClassDesc.of("Boo"), ClassDesc.of("Bee")))
                        .withCode(cob ->
                            cob.trying(tryb -> {
                                tryb.lineNumber(1);
                                tryb.iload(1);
                                tryb.lineNumber(2);
                                tryb.ifThen(thb -> thb.aload(2).athrow());
                                tryb.lineNumber(3);
                                tryb.localVariable(2, "variable", ClassDesc.of("Phoo"), tryb.startLabel(), tryb.endLabel());
                                tryb.localVariableType(2, "variable", Signature.of(ClassDesc.of("Phoo")), tryb.startLabel(), tryb.endLabel());
                                tryb.with(RuntimeInvisibleTypeAnnotationsAttribute.of(
                                        TypeAnnotation.of(TypeAnnotation.TargetInfo.ofField(),
                                                List.of(TypeAnnotation.TypePathComponent.WILDCARD),
                                                ClassDesc.of("Boo"), List.of())));
                                tryb.invokedynamic(DynamicCallSiteDesc.of(
                                        MethodHandleDesc.ofMethod(DirectMethodHandleDesc.Kind.STATIC, ClassDesc.of("Phoo"), "phee", MethodTypeDesc.of(ClassDesc.of("Boo"))),
                                        "intfMethod",
                                        MethodTypeDesc.of(ClassDesc.of("Boo")),
                                        "bootstrap argument 1",
                                        "bootstrap argument 2"));
                                tryb.return_();
                            }, catchb -> catchb.catching(ClassDesc.of("Phee"), cb -> {
                                cb.lineNumber(4);
                                cb.athrow();
                            }))
                            .with(RuntimeVisibleTypeAnnotationsAttribute.of(
                                    TypeAnnotation.of(TypeAnnotation.TargetInfo.ofField(),
                                          List.of(TypeAnnotation.TypePathComponent.ARRAY),
                                          ClassDesc.of("Fee"), List.of(AnnotationElement.ofBoolean("yes", false)))))
                        ))));
    }

    @Test
    void testPrintYamlTraceAll() throws IOException {
        var out = new StringBuilder();
        ClassPrinter.toYaml(getClassModel(), ClassPrinter.Verbosity.TRACE_ALL, out::append);
        assertOut(out,
                """
                  - class name: Foo
                    version: 61.0
                    flags: [PUBLIC]
                    superclass: Boo
                    interfaces: [Phee, Phoo]
                    attributes: [SourceFile, InnerClasses, EnclosingMethod, Synthetic, Signature, Deprecated, NestHost, NestMembers, Record, RuntimeInvisibleAnnotations, PermittedSubclasses, BootstrapMethods]
                    constant pool:
                        1: {tag: Utf8, value: Foo}
                        2: {tag: Class, class name index: 1, class internal name: Foo}
                        3: {tag: Utf8, value: Boo}
                        4: {tag: Class, class name index: 3, class internal name: Boo}
                        5: {tag: Utf8, value: f}
                        6: {tag: Utf8, value: Ljava/lang/String;}
                        7: {tag: Utf8, value: m}
                        8: {tag: Utf8, value: (ZLjava/lang/Throwable;)Ljava/lang/Void;}
                        9: {tag: Utf8, value: variable}
                        10: {tag: Utf8, value: LPhoo;}
                        11: {tag: Utf8, value: Phoo}
                        12: {tag: Class, class name index: 11, class internal name: Phoo}
                        13: {tag: Utf8, value: phee}
                        14: {tag: Utf8, value: ()LBoo;}
                        15: {tag: NameAndType, name index: 13, type index: 14, name: phee, type: ()LBoo;}
                        16: {tag: Methodref, owner index: 12, name and type index: 15, owner: Phoo, name: phee, type: ()LBoo;}
                        17: {tag: MethodHandle, reference kind: STATIC, reference index: 16, owner: Phoo, name: phee, type: ()LBoo;}
                        18: {tag: Utf8, value: bootstrap argument 1}
                        19: {tag: String, value index: 18, value: bootstrap argument 1}
                        20: {tag: Utf8, value: bootstrap argument 2}
                        21: {tag: String, value index: 20, value: bootstrap argument 2}
                        22: {tag: Utf8, value: intfMethod}
                        23: {tag: NameAndType, name index: 22, type index: 14, name: intfMethod, type: ()LBoo;}
                        24: {tag: InvokeDynamic, bootstrap method handle index: 17, bootstrap method arguments indexes: [19, 21], name and type index: 23, name: intfMethod, type: ()LBoo;}
                        25: {tag: Utf8, value: Phee}
                        26: {tag: Class, class name index: 25, class internal name: Phee}
                        27: {tag: Utf8, value: RuntimeVisibleAnnotations}
                        28: {tag: Utf8, value: flfl}
                        29: {tag: Float, value: 0.0}
                        30: {tag: Utf8, value: frfl}
                        31: {tag: Float, value: 1.0}
                        32: {tag: Utf8, value: AnnotationDefault}
                        33: {tag: Integer, value: 1}
                        34: {tag: Integer, value: 12}
                        35: {tag: Integer, value: 99}
                        36: {tag: Utf8, value: LPhee;}
                        37: {tag: Double, value: 1.3}
                        39: {tag: Utf8, value: LBoo;}
                        40: {tag: Utf8, value: BOO}
                        41: {tag: Float, value: 3.7}
                        42: {tag: Integer, value: 33}
                        43: {tag: Long, value: 3333}
                        45: {tag: Integer, value: 25}
                        46: {tag: Utf8, value: param}
                        47: {tag: Integer, value: 3}
                        48: {tag: Utf8, value: RuntimeVisibleParameterAnnotations}
                        49: {tag: Float, value: 22.0}
                        50: {tag: Float, value: 11.0}
                        51: {tag: Utf8, value: RuntimeInvisibleParameterAnnotations}
                        52: {tag: Float, value: '-22.0'}
                        53: {tag: Float, value: '-11.0'}
                        54: {tag: Utf8, value: Exceptions}
                        55: {tag: Utf8, value: Bee}
                        56: {tag: Class, class name index: 55, class internal name: Bee}
                        57: {tag: Utf8, value: Code}
                        58: {tag: Utf8, value: RuntimeInvisibleTypeAnnotations}
                        59: {tag: Utf8, value: RuntimeVisibleTypeAnnotations}
                        60: {tag: Utf8, value: LFee;}
                        61: {tag: Utf8, value: yes}
                        62: {tag: Integer, value: 0}
                        63: {tag: Utf8, value: LocalVariableTable}
                        64: {tag: Utf8, value: LocalVariableTypeTable}
                        65: {tag: Utf8, value: LineNumberTable}
                        66: {tag: Utf8, value: StackMapTable}
                        67: {tag: Utf8, value: SourceFile}
                        68: {tag: Utf8, value: Foo.java}
                        69: {tag: Utf8, value: InnerClasses}
                        70: {tag: Utf8, value: InnerName}
                        71: {tag: Utf8, value: EnclosingMethod}
                        72: {tag: Utf8, value: enclosingMethod}
                        73: {tag: Utf8, value: (Ljava/util/Collection;)Ljava/lang/Double;}
                        74: {tag: NameAndType, name index: 72, type index: 73, name: enclosingMethod, type: (Ljava/util/Collection;)Ljava/lang/Double;}
                        75: {tag: Utf8, value: Synthetic}
                        76: {tag: Utf8, value: Signature}
                        77: {tag: Utf8, value: LBoo;LPhee;LPhoo;}
                        78: {tag: Utf8, value: Deprecated}
                        79: {tag: Utf8, value: NestHost}
                        80: {tag: Utf8, value: NestMembers}
                        81: {tag: Utf8, value: Record}
                        82: {tag: Utf8, value: fee}
                        83: {tag: Utf8, value: RuntimeInvisibleAnnotations}
                        84: {tag: Float, value: 2.0}
                        85: {tag: Float, value: 3.0}
                        86: {tag: Utf8, value: PermittedSubclasses}
                        87: {tag: Utf8, value: BootstrapMethods}
                    source file: Foo.java
                    inner classes:
                      - {inner class: Phee, outer class: Phoo, inner name: InnerName, flags: [PROTECTED]}
                      - {inner class: Phoo, outer class: null, inner name: null, flags: [PRIVATE]}
                    enclosing method: {class: Phee, method name: enclosingMethod, method type: (Ljava/util/Collection;)Ljava/lang/Double;}
                    signature: LBoo;LPhee;LPhoo;
                    nest host: Phee
                    nest members: [Phoo, Boo, Bee]
                    record components:
                      - name: fee
                        type: LPhoo;
                        attributes: [Signature, RuntimeInvisibleTypeAnnotations]
                        signature: LPhoo;
                        invisible type annotations:
                          - {annotation class: LBoo;, target info: FIELD, values: []}
                    invisible annotations:
                      - {annotation class: LPhoo;, values: [{name: flfl, value: {float: 2.0}}, {name: frfl, value: {float: 3.0}}]}
                    permitted subclasses: [Boo, Phoo]
                    bootstrap methods:
                      - {index: 0, kind: STATIC, owner: Phoo, name: phee, args: [bootstrap argument 1, bootstrap argument 2]}
                    fields:
                      - field name: f
                        flags: [PRIVATE]
                        field type: Ljava/lang/String;
                        attributes: [RuntimeVisibleAnnotations]
                        visible annotations:
                          - {annotation class: LPhoo;, values: [{name: flfl, value: {float: 0.0}}, {name: frfl, value: {float: 1.0}}]}
                    methods:
                      - method name: m
                        flags: [PROTECTED]
                        method type: (ZLjava/lang/Throwable;)Ljava/lang/Void;
                        attributes: [AnnotationDefault, RuntimeVisibleParameterAnnotations, RuntimeInvisibleParameterAnnotations, Exceptions, Code]
                        annotation default: {array: [{boolean: true}, {byte: 12}, {char: 99}, {class: LPhee;}, {double: 1.3}, {enum class: LBoo;, constant name: BOO}, {float: 3.7}, {int: 33}, {long: 3333}, {short: 25}, {string: BOO}, {annotation class: LPhoo;}]}
                        visible parameter annotations:
                            parameter 1: [{annotation class: LPhoo;, values: [{name: flfl, value: {float: 22.0}}, {name: frfl, value: {float: 11.0}}]}]
                        invisible parameter annotations:
                            parameter 1: [{annotation class: LPhoo;, values: [{name: flfl, value: {float: '-22.0'}}, {name: frfl, value: {float: '-11.0'}}]}]
                        exceptions: [Phoo, Boo, Bee]
                        code:
                            max stack: 1
                            max locals: 3
                            attributes: [RuntimeInvisibleTypeAnnotations, RuntimeVisibleTypeAnnotations, LocalVariableTable, LocalVariableTypeTable, LineNumberTable, StackMapTable]
                            local variables:
                              - {start: 0, end: 12, slot: 2, name: variable, type: LPhoo;}
                            local variable types:
                              - {start: 0, end: 12, slot: 2, name: variable, signature: LPhoo;}
                            line numbers:
                              - {start: 0, line number: 1}
                              - {start: 1, line number: 2}
                              - {start: 6, line number: 3}
                              - {start: 12, line number: 4}
                            stack map frames:
                                6: {locals: [Foo, int, java/lang/Throwable], stack: []}
                                12: {locals: [Foo, int, java/lang/Throwable], stack: [Phee]}
                            invisible type annotations:
                              - {annotation class: LBoo;, target info: FIELD, values: []}
                            visible type annotations:
                              - {annotation class: LFee;, target info: FIELD, values: [{name: yes, value: {boolean: false}}]}
                            //stack map frame @0: {locals: [Foo, int, java/lang/Throwable], stack: []}
                            //try block 1 start: {start: 0, end: 12, handler: 12, catch type: Phee}
                            0: {opcode: ILOAD_1, slot: 1}
                            1: {opcode: IFEQ, target: 6}
                            4: {opcode: ALOAD_2, slot: 2, type: LPhoo;, variable name: variable}
                            5: {opcode: ATHROW}
                            //stack map frame @6: {locals: [Foo, int, java/lang/Throwable], stack: []}
                            6: {opcode: INVOKEDYNAMIC, name: intfMethod, descriptor: ()LBoo;, bootstrap method: STATIC Phoo::phee, arguments: [bootstrap argument 1, bootstrap argument 2]}
                            11: {opcode: RETURN}
                            //stack map frame @12: {locals: [Foo, int, java/lang/Throwable], stack: [Phee]}
                            //try block 1 end: {start: 0, end: 12, handler: 12, catch type: Phee}
                            //exception handler 1 start: {start: 0, end: 12, handler: 12, catch type: Phee}
                            12: {opcode: ATHROW}
                            exception handlers:
                                handler 1: {start: 0, end: 12, handler: 12, type: Phee}
                """);
    }

    @Test
    void testPrintYamlCriticalAttributes() throws IOException {
        var out = new StringBuilder();
        ClassPrinter.toYaml(getClassModel(), ClassPrinter.Verbosity.CRITICAL_ATTRIBUTES, out::append);
        assertOut(out,
                """
                  - class name: Foo
                    version: 61.0
                    flags: [PUBLIC]
                    superclass: Boo
                    interfaces: [Phee, Phoo]
                    attributes: [SourceFile, InnerClasses, EnclosingMethod, Synthetic, Signature, Deprecated, NestHost, NestMembers, Record, RuntimeInvisibleAnnotations, PermittedSubclasses, BootstrapMethods]
                    nest host: Phee
                    nest members: [Phoo, Boo, Bee]
                    permitted subclasses: [Boo, Phoo]
                    bootstrap methods:
                      - {index: 0, kind: STATIC, owner: Phoo, name: phee, args: [bootstrap argument 1, bootstrap argument 2]}
                    fields:
                      - field name: f
                        flags: [PRIVATE]
                        field type: Ljava/lang/String;
                        attributes: [RuntimeVisibleAnnotations]
                    methods:
                      - method name: m
                        flags: [PROTECTED]
                        method type: (ZLjava/lang/Throwable;)Ljava/lang/Void;
                        attributes: [AnnotationDefault, RuntimeVisibleParameterAnnotations, RuntimeInvisibleParameterAnnotations, Exceptions, Code]
                        code:
                            max stack: 1
                            max locals: 3
                            attributes: [RuntimeInvisibleTypeAnnotations, RuntimeVisibleTypeAnnotations, LocalVariableTable, LocalVariableTypeTable, LineNumberTable, StackMapTable]
                            stack map frames:
                                6: {locals: [Foo, int, java/lang/Throwable], stack: []}
                                12: {locals: [Foo, int, java/lang/Throwable], stack: [Phee]}
                            //stack map frame @0: {locals: [Foo, int, java/lang/Throwable], stack: []}
                            //try block 1 start: {start: 0, end: 12, handler: 12, catch type: Phee}
                            0: {opcode: ILOAD_1, slot: 1}
                            1: {opcode: IFEQ, target: 6}
                            4: {opcode: ALOAD_2, slot: 2}
                            5: {opcode: ATHROW}
                            //stack map frame @6: {locals: [Foo, int, java/lang/Throwable], stack: []}
                            6: {opcode: INVOKEDYNAMIC, name: intfMethod, descriptor: ()LBoo;, bootstrap method: STATIC Phoo::phee, arguments: [bootstrap argument 1, bootstrap argument 2]}
                            11: {opcode: RETURN}
                            //stack map frame @12: {locals: [Foo, int, java/lang/Throwable], stack: [Phee]}
                            //try block 1 end: {start: 0, end: 12, handler: 12, catch type: Phee}
                            //exception handler 1 start: {start: 0, end: 12, handler: 12, catch type: Phee}
                            12: {opcode: ATHROW}
                            exception handlers:
                                handler 1: {start: 0, end: 12, handler: 12, type: Phee}
                """);
    }

    @Test
    void testPrintYamlMembersOnly() throws IOException {
        var out = new StringBuilder();
        ClassPrinter.toYaml(getClassModel(), ClassPrinter.Verbosity.MEMBERS_ONLY, out::append);
        assertOut(out,
                """
                  - class name: Foo
                    version: 61.0
                    flags: [PUBLIC]
                    superclass: Boo
                    interfaces: [Phee, Phoo]
                    attributes: [SourceFile, InnerClasses, EnclosingMethod, Synthetic, Signature, Deprecated, NestHost, NestMembers, Record, RuntimeInvisibleAnnotations, PermittedSubclasses, BootstrapMethods]
                    fields:
                      - field name: f
                        flags: [PRIVATE]
                        field type: Ljava/lang/String;
                        attributes: [RuntimeVisibleAnnotations]
                    methods:
                      - method name: m
                        flags: [PROTECTED]
                        method type: (ZLjava/lang/Throwable;)Ljava/lang/Void;
                        attributes: [AnnotationDefault, RuntimeVisibleParameterAnnotations, RuntimeInvisibleParameterAnnotations, Exceptions, Code]
                """);
    }

    @Test
    void testPrintJsonTraceAll() throws IOException {
        var out = new StringBuilder();
        ClassPrinter.toJson(getClassModel(), ClassPrinter.Verbosity.TRACE_ALL, out::append);
        assertOut(out,
                """
                  { "class name": "Foo",
                    "version": "61.0",
                    "flags": ["PUBLIC"],
                    "superclass": "Boo",
                    "interfaces": ["Phee", "Phoo"],
                    "attributes": ["SourceFile", "InnerClasses", "EnclosingMethod", "Synthetic", "Signature", "Deprecated", "NestHost", "NestMembers", "Record", "RuntimeInvisibleAnnotations", "PermittedSubclasses", "BootstrapMethods"],
                    "constant pool": {
                        "1": {"tag": "Utf8", "value": "Foo"},
                        "2": {"tag": "Class", "class name index": 1, "class internal name": "Foo"},
                        "3": {"tag": "Utf8", "value": "Boo"},
                        "4": {"tag": "Class", "class name index": 3, "class internal name": "Boo"},
                        "5": {"tag": "Utf8", "value": "f"},
                        "6": {"tag": "Utf8", "value": "Ljava/lang/String;"},
                        "7": {"tag": "Utf8", "value": "m"},
                        "8": {"tag": "Utf8", "value": "(ZLjava/lang/Throwable;)Ljava/lang/Void;"},
                        "9": {"tag": "Utf8", "value": "variable"},
                        "10": {"tag": "Utf8", "value": "LPhoo;"},
                        "11": {"tag": "Utf8", "value": "Phoo"},
                        "12": {"tag": "Class", "class name index": 11, "class internal name": "Phoo"},
                        "13": {"tag": "Utf8", "value": "phee"},
                        "14": {"tag": "Utf8", "value": "()LBoo;"},
                        "15": {"tag": "NameAndType", "name index": 13, "type index": 14, "name": "phee", "type": "()LBoo;"},
                        "16": {"tag": "Methodref", "owner index": 12, "name and type index": 15, "owner": "Phoo", "name": "phee", "type": "()LBoo;"},
                        "17": {"tag": "MethodHandle", "reference kind": "STATIC", "reference index": 16, "owner": "Phoo", "name": "phee", "type": "()LBoo;"},
                        "18": {"tag": "Utf8", "value": "bootstrap argument 1"},
                        "19": {"tag": "String", "value index": 18, "value": "bootstrap argument 1"},
                        "20": {"tag": "Utf8", "value": "bootstrap argument 2"},
                        "21": {"tag": "String", "value index": 20, "value": "bootstrap argument 2"},
                        "22": {"tag": "Utf8", "value": "intfMethod"},
                        "23": {"tag": "NameAndType", "name index": 22, "type index": 14, "name": "intfMethod", "type": "()LBoo;"},
                        "24": {"tag": "InvokeDynamic", "bootstrap method handle index": 17, "bootstrap method arguments indexes": [19, 21], "name and type index": 23, "name": "intfMethod", "type": "()LBoo;"},
                        "25": {"tag": "Utf8", "value": "Phee"},
                        "26": {"tag": "Class", "class name index": 25, "class internal name": "Phee"},
                        "27": {"tag": "Utf8", "value": "RuntimeVisibleAnnotations"},
                        "28": {"tag": "Utf8", "value": "flfl"},
                        "29": {"tag": "Float", "value": "0.0"},
                        "30": {"tag": "Utf8", "value": "frfl"},
                        "31": {"tag": "Float", "value": "1.0"},
                        "32": {"tag": "Utf8", "value": "AnnotationDefault"},
                        "33": {"tag": "Integer", "value": "1"},
                        "34": {"tag": "Integer", "value": "12"},
                        "35": {"tag": "Integer", "value": "99"},
                        "36": {"tag": "Utf8", "value": "LPhee;"},
                        "37": {"tag": "Double", "value": "1.3"},
                        "39": {"tag": "Utf8", "value": "LBoo;"},
                        "40": {"tag": "Utf8", "value": "BOO"},
                        "41": {"tag": "Float", "value": "3.7"},
                        "42": {"tag": "Integer", "value": "33"},
                        "43": {"tag": "Long", "value": "3333"},
                        "45": {"tag": "Integer", "value": "25"},
                        "46": {"tag": "Utf8", "value": "param"},
                        "47": {"tag": "Integer", "value": "3"},
                        "48": {"tag": "Utf8", "value": "RuntimeVisibleParameterAnnotations"},
                        "49": {"tag": "Float", "value": "22.0"},
                        "50": {"tag": "Float", "value": "11.0"},
                        "51": {"tag": "Utf8", "value": "RuntimeInvisibleParameterAnnotations"},
                        "52": {"tag": "Float", "value": "-22.0"},
                        "53": {"tag": "Float", "value": "-11.0"},
                        "54": {"tag": "Utf8", "value": "Exceptions"},
                        "55": {"tag": "Utf8", "value": "Bee"},
                        "56": {"tag": "Class", "class name index": 55, "class internal name": "Bee"},
                        "57": {"tag": "Utf8", "value": "Code"},
                        "58": {"tag": "Utf8", "value": "RuntimeInvisibleTypeAnnotations"},
                        "59": {"tag": "Utf8", "value": "RuntimeVisibleTypeAnnotations"},
                        "60": {"tag": "Utf8", "value": "LFee;"},
                        "61": {"tag": "Utf8", "value": "yes"},
                        "62": {"tag": "Integer", "value": "0"},
                        "63": {"tag": "Utf8", "value": "LocalVariableTable"},
                        "64": {"tag": "Utf8", "value": "LocalVariableTypeTable"},
                        "65": {"tag": "Utf8", "value": "LineNumberTable"},
                        "66": {"tag": "Utf8", "value": "StackMapTable"},
                        "67": {"tag": "Utf8", "value": "SourceFile"},
                        "68": {"tag": "Utf8", "value": "Foo.java"},
                        "69": {"tag": "Utf8", "value": "InnerClasses"},
                        "70": {"tag": "Utf8", "value": "InnerName"},
                        "71": {"tag": "Utf8", "value": "EnclosingMethod"},
                        "72": {"tag": "Utf8", "value": "enclosingMethod"},
                        "73": {"tag": "Utf8", "value": "(Ljava/util/Collection;)Ljava/lang/Double;"},
                        "74": {"tag": "NameAndType", "name index": 72, "type index": 73, "name": "enclosingMethod", "type": "(Ljava/util/Collection;)Ljava/lang/Double;"},
                        "75": {"tag": "Utf8", "value": "Synthetic"},
                        "76": {"tag": "Utf8", "value": "Signature"},
                        "77": {"tag": "Utf8", "value": "LBoo;LPhee;LPhoo;"},
                        "78": {"tag": "Utf8", "value": "Deprecated"},
                        "79": {"tag": "Utf8", "value": "NestHost"},
                        "80": {"tag": "Utf8", "value": "NestMembers"},
                        "81": {"tag": "Utf8", "value": "Record"},
                        "82": {"tag": "Utf8", "value": "fee"},
                        "83": {"tag": "Utf8", "value": "RuntimeInvisibleAnnotations"},
                        "84": {"tag": "Float", "value": "2.0"},
                        "85": {"tag": "Float", "value": "3.0"},
                        "86": {"tag": "Utf8", "value": "PermittedSubclasses"},
                        "87": {"tag": "Utf8", "value": "BootstrapMethods"}},
                    "source file": "Foo.java",
                    "inner classes": [
                        {"inner class": "Phee", "outer class": "Phoo", "inner name": "InnerName", "flags": ["PROTECTED"]},
                        {"inner class": "Phoo", "outer class": "null", "inner name": "null", "flags": ["PRIVATE"]}],
                    "enclosing method": {"class": "Phee", "method name": "enclosingMethod", "method type": "(Ljava/util/Collection;)Ljava/lang/Double;"},
                    "signature": "LBoo;LPhee;LPhoo;",
                    "nest host": "Phee",
                    "nest members": ["Phoo", "Boo", "Bee"],
                    "record components": [
                          { "name": "fee",
                            "type": "LPhoo;",
                            "attributes": ["Signature", "RuntimeInvisibleTypeAnnotations"],
                            "signature": "LPhoo;",
                            "invisible type annotations": [
                                {"annotation class": "LBoo;", "target info": "FIELD", "values": []}]}],
                    "invisible annotations": [
                        {"annotation class": "LPhoo;", "values": [{"name": "flfl", "value": {"float": "2.0"}}, {"name": "frfl", "value": {"float": "3.0"}}]}],
                    "permitted subclasses": ["Boo", "Phoo"],
                    "bootstrap methods": [
                        {"index": 0, "kind": "STATIC", "owner": "Phoo", "name": "phee", "args": ["bootstrap argument 1", "bootstrap argument 2"]}],
                    "fields": [
                          { "field name": "f",
                            "flags": ["PRIVATE"],
                            "field type": "Ljava/lang/String;",
                            "attributes": ["RuntimeVisibleAnnotations"],
                            "visible annotations": [
                                {"annotation class": "LPhoo;", "values": [{"name": "flfl", "value": {"float": "0.0"}}, {"name": "frfl", "value": {"float": "1.0"}}]}]}],
                    "methods": [
                          { "method name": "m",
                            "flags": ["PROTECTED"],
                            "method type": "(ZLjava/lang/Throwable;)Ljava/lang/Void;",
                            "attributes": ["AnnotationDefault", "RuntimeVisibleParameterAnnotations", "RuntimeInvisibleParameterAnnotations", "Exceptions", "Code"],
                            "annotation default": {"array": [{"boolean": "true"}, {"byte": "12"}, {"char": "99"}, {"class": "LPhee;"}, {"double": "1.3"}, {"enum class": "LBoo;", "constant name": "BOO"}, {"float": "3.7"}, {"int": "33"}, {"long": "3333"}, {"short": "25"}, {"string": "BOO"}, {"annotation class": "LPhoo;"}]},
                            "visible parameter annotations": {
                                "parameter 1": [{"annotation class": "LPhoo;", "values": [{"name": "flfl", "value": {"float": "22.0"}}, {"name": "frfl", "value": {"float": "11.0"}}]}]},
                            "invisible parameter annotations": {
                                "parameter 1": [{"annotation class": "LPhoo;", "values": [{"name": "flfl", "value": {"float": "-22.0"}}, {"name": "frfl", "value": {"float": "-11.0"}}]}]},
                            "exceptions": ["Phoo", "Boo", "Bee"],
                            "code": {
                                "max stack": 1,
                                "max locals": 3,
                                "attributes": ["RuntimeInvisibleTypeAnnotations", "RuntimeVisibleTypeAnnotations", "LocalVariableTable", "LocalVariableTypeTable", "LineNumberTable", "StackMapTable"],
                                "local variables": [
                                    {"start": 0, "end": 12, "slot": 2, "name": "variable", "type": "LPhoo;"}],
                                "local variable types": [
                                    {"start": 0, "end": 12, "slot": 2, "name": "variable", "signature": "LPhoo;"}],
                                "line numbers": [
                                    {"start": 0, "line number": 1},
                                    {"start": 1, "line number": 2},
                                    {"start": 6, "line number": 3},
                                    {"start": 12, "line number": 4}],
                                "stack map frames": {
                                    "6": {"locals": ["Foo", "int", "java/lang/Throwable"], "stack": []},
                                    "12": {"locals": ["Foo", "int", "java/lang/Throwable"], "stack": ["Phee"]}},
                                "invisible type annotations": [
                                    {"annotation class": "LBoo;", "target info": "FIELD", "values": []}],
                                "visible type annotations": [
                                    {"annotation class": "LFee;", "target info": "FIELD", "values": [{"name": "yes", "value": {"boolean": "false"}}]}],
                                "//stack map frame @0": {"locals": ["Foo", "int", "java/lang/Throwable"], "stack": []},
                                "//try block 1 start": {"start": 0, "end": 12, "handler": 12, "catch type": "Phee"},
                                "0": {"opcode": "ILOAD_1", "slot": 1},
                                "1": {"opcode": "IFEQ", "target": 6},
                                "4": {"opcode": "ALOAD_2", "slot": 2, "type": "LPhoo;", "variable name": "variable"},
                                "5": {"opcode": "ATHROW"},
                                "//stack map frame @6": {"locals": ["Foo", "int", "java/lang/Throwable"], "stack": []},
                                "6": {"opcode": "INVOKEDYNAMIC", "name": "intfMethod", "descriptor": "()LBoo;", "bootstrap method": "STATIC Phoo::phee", "arguments": ["bootstrap argument 1", "bootstrap argument 2"]},
                                "11": {"opcode": "RETURN"},
                                "//stack map frame @12": {"locals": ["Foo", "int", "java/lang/Throwable"], "stack": ["Phee"]},
                                "//try block 1 end": {"start": 0, "end": 12, "handler": 12, "catch type": "Phee"},
                                "//exception handler 1 start": {"start": 0, "end": 12, "handler": 12, "catch type": "Phee"},
                                "12": {"opcode": "ATHROW"},
                                "exception handlers": {
                                    "handler 1": {"start": 0, "end": 12, "handler": 12, "type": "Phee"}}}}]}
                """);
    }

    @Test
    void testPrintJsonCriticalAttributes() throws IOException {
        var out = new StringBuilder();
        ClassPrinter.toJson(getClassModel(), ClassPrinter.Verbosity.CRITICAL_ATTRIBUTES, out::append);
        assertOut(out,
                """
                  { "class name": "Foo",
                    "version": "61.0",
                    "flags": ["PUBLIC"],
                    "superclass": "Boo",
                    "interfaces": ["Phee", "Phoo"],
                    "attributes": ["SourceFile", "InnerClasses", "EnclosingMethod", "Synthetic", "Signature", "Deprecated", "NestHost", "NestMembers", "Record", "RuntimeInvisibleAnnotations", "PermittedSubclasses", "BootstrapMethods"],
                    "nest host": "Phee",
                    "nest members": ["Phoo", "Boo", "Bee"],
                    "permitted subclasses": ["Boo", "Phoo"],
                    "bootstrap methods": [
                        {"index": 0, "kind": "STATIC", "owner": "Phoo", "name": "phee", "args": ["bootstrap argument 1", "bootstrap argument 2"]}],
                    "fields": [
                          { "field name": "f",
                            "flags": ["PRIVATE"],
                            "field type": "Ljava/lang/String;",
                            "attributes": ["RuntimeVisibleAnnotations"]}],
                    "methods": [
                          { "method name": "m",
                            "flags": ["PROTECTED"],
                            "method type": "(ZLjava/lang/Throwable;)Ljava/lang/Void;",
                            "attributes": ["AnnotationDefault", "RuntimeVisibleParameterAnnotations", "RuntimeInvisibleParameterAnnotations", "Exceptions", "Code"],
                            "code": {
                                "max stack": 1,
                                "max locals": 3,
                                "attributes": ["RuntimeInvisibleTypeAnnotations", "RuntimeVisibleTypeAnnotations", "LocalVariableTable", "LocalVariableTypeTable", "LineNumberTable", "StackMapTable"],
                                "stack map frames": {
                                    "6": {"locals": ["Foo", "int", "java/lang/Throwable"], "stack": []},
                                    "12": {"locals": ["Foo", "int", "java/lang/Throwable"], "stack": ["Phee"]}},
                                "//stack map frame @0": {"locals": ["Foo", "int", "java/lang/Throwable"], "stack": []},
                                "//try block 1 start": {"start": 0, "end": 12, "handler": 12, "catch type": "Phee"},
                                "0": {"opcode": "ILOAD_1", "slot": 1},
                                "1": {"opcode": "IFEQ", "target": 6},
                                "4": {"opcode": "ALOAD_2", "slot": 2},
                                "5": {"opcode": "ATHROW"},
                                "//stack map frame @6": {"locals": ["Foo", "int", "java/lang/Throwable"], "stack": []},
                                "6": {"opcode": "INVOKEDYNAMIC", "name": "intfMethod", "descriptor": "()LBoo;", "bootstrap method": "STATIC Phoo::phee", "arguments": ["bootstrap argument 1", "bootstrap argument 2"]},
                                "11": {"opcode": "RETURN"},
                                "//stack map frame @12": {"locals": ["Foo", "int", "java/lang/Throwable"], "stack": ["Phee"]},
                                "//try block 1 end": {"start": 0, "end": 12, "handler": 12, "catch type": "Phee"},
                                "//exception handler 1 start": {"start": 0, "end": 12, "handler": 12, "catch type": "Phee"},
                                "12": {"opcode": "ATHROW"},
                                "exception handlers": {
                                    "handler 1": {"start": 0, "end": 12, "handler": 12, "type": "Phee"}}}}]}
                """);
    }

    @Test
    void testPrintJsonMembersOnly() throws IOException {
        var out = new StringBuilder();
        ClassPrinter.toJson(getClassModel(), ClassPrinter.Verbosity.MEMBERS_ONLY, out::append);
        assertOut(out,
                """
                  { "class name": "Foo",
                    "version": "61.0",
                    "flags": ["PUBLIC"],
                    "superclass": "Boo",
                    "interfaces": ["Phee", "Phoo"],
                    "attributes": ["SourceFile", "InnerClasses", "EnclosingMethod", "Synthetic", "Signature", "Deprecated", "NestHost", "NestMembers", "Record", "RuntimeInvisibleAnnotations", "PermittedSubclasses", "BootstrapMethods"],
                    "fields": [
                          { "field name": "f",
                            "flags": ["PRIVATE"],
                            "field type": "Ljava/lang/String;",
                            "attributes": ["RuntimeVisibleAnnotations"]}],
                    "methods": [
                          { "method name": "m",
                            "flags": ["PROTECTED"],
                            "method type": "(ZLjava/lang/Throwable;)Ljava/lang/Void;",
                            "attributes": ["AnnotationDefault", "RuntimeVisibleParameterAnnotations", "RuntimeInvisibleParameterAnnotations", "Exceptions", "Code"]}]}
                """);
    }

    @Test
    void testPrintXmlTraceAll() throws IOException {
        var out = new StringBuilder();
        ClassPrinter.toXml(getClassModel(), ClassPrinter.Verbosity.TRACE_ALL, out::append);
        assertOut(out,
                """
                <?xml version = '1.0'?>
                <class>
                    <class_name>Foo</class_name>
                    <version>61.0</version>
                    <flags><flag>PUBLIC</flag></flags>
                    <superclass>Boo</superclass>
                    <interfaces><interface>Phee</interface><interface>Phoo</interface></interfaces>
                    <attributes><attribute>SourceFile</attribute><attribute>InnerClasses</attribute><attribute>EnclosingMethod</attribute><attribute>Synthetic</attribute><attribute>Signature</attribute><attribute>Deprecated</attribute><attribute>NestHost</attribute><attribute>NestMembers</attribute><attribute>Record</attribute><attribute>RuntimeInvisibleAnnotations</attribute><attribute>PermittedSubclasses</attribute><attribute>BootstrapMethods</attribute></attributes>
                    <constant_pool>
                        <_1><tag>Utf8</tag><value>Foo</value></_1>
                        <_2><tag>Class</tag><class_name_index>1</class_name_index><class_internal_name>Foo</class_internal_name></_2>
                        <_3><tag>Utf8</tag><value>Boo</value></_3>
                        <_4><tag>Class</tag><class_name_index>3</class_name_index><class_internal_name>Boo</class_internal_name></_4>
                        <_5><tag>Utf8</tag><value>f</value></_5>
                        <_6><tag>Utf8</tag><value>Ljava/lang/String;</value></_6>
                        <_7><tag>Utf8</tag><value>m</value></_7>
                        <_8><tag>Utf8</tag><value>(ZLjava/lang/Throwable;)Ljava/lang/Void;</value></_8>
                        <_9><tag>Utf8</tag><value>variable</value></_9>
                        <_10><tag>Utf8</tag><value>LPhoo;</value></_10>
                        <_11><tag>Utf8</tag><value>Phoo</value></_11>
                        <_12><tag>Class</tag><class_name_index>11</class_name_index><class_internal_name>Phoo</class_internal_name></_12>
                        <_13><tag>Utf8</tag><value>phee</value></_13>
                        <_14><tag>Utf8</tag><value>()LBoo;</value></_14>
                        <_15><tag>NameAndType</tag><name_index>13</name_index><type_index>14</type_index><name>phee</name><type>()LBoo;</type></_15>
                        <_16><tag>Methodref</tag><owner_index>12</owner_index><name_and_type_index>15</name_and_type_index><owner>Phoo</owner><name>phee</name><type>()LBoo;</type></_16>
                        <_17><tag>MethodHandle</tag><reference_kind>STATIC</reference_kind><reference_index>16</reference_index><owner>Phoo</owner><name>phee</name><type>()LBoo;</type></_17>
                        <_18><tag>Utf8</tag><value>bootstrap argument 1</value></_18>
                        <_19><tag>String</tag><value_index>18</value_index><value>bootstrap argument 1</value></_19>
                        <_20><tag>Utf8</tag><value>bootstrap argument 2</value></_20>
                        <_21><tag>String</tag><value_index>20</value_index><value>bootstrap argument 2</value></_21>
                        <_22><tag>Utf8</tag><value>intfMethod</value></_22>
                        <_23><tag>NameAndType</tag><name_index>22</name_index><type_index>14</type_index><name>intfMethod</name><type>()LBoo;</type></_23>
                        <_24><tag>InvokeDynamic</tag><bootstrap_method_handle_index>17</bootstrap_method_handle_index><bootstrap_method_arguments_indexes><index>19</index><index>21</index></bootstrap_method_arguments_indexes><name_and_type_index>23</name_and_type_index><name>intfMethod</name><type>()LBoo;</type></_24>
                        <_25><tag>Utf8</tag><value>Phee</value></_25>
                        <_26><tag>Class</tag><class_name_index>25</class_name_index><class_internal_name>Phee</class_internal_name></_26>
                        <_27><tag>Utf8</tag><value>RuntimeVisibleAnnotations</value></_27>
                        <_28><tag>Utf8</tag><value>flfl</value></_28>
                        <_29><tag>Float</tag><value>0.0</value></_29>
                        <_30><tag>Utf8</tag><value>frfl</value></_30>
                        <_31><tag>Float</tag><value>1.0</value></_31>
                        <_32><tag>Utf8</tag><value>AnnotationDefault</value></_32>
                        <_33><tag>Integer</tag><value>1</value></_33>
                        <_34><tag>Integer</tag><value>12</value></_34>
                        <_35><tag>Integer</tag><value>99</value></_35>
                        <_36><tag>Utf8</tag><value>LPhee;</value></_36>
                        <_37><tag>Double</tag><value>1.3</value></_37>
                        <_39><tag>Utf8</tag><value>LBoo;</value></_39>
                        <_40><tag>Utf8</tag><value>BOO</value></_40>
                        <_41><tag>Float</tag><value>3.7</value></_41>
                        <_42><tag>Integer</tag><value>33</value></_42>
                        <_43><tag>Long</tag><value>3333</value></_43>
                        <_45><tag>Integer</tag><value>25</value></_45>
                        <_46><tag>Utf8</tag><value>param</value></_46>
                        <_47><tag>Integer</tag><value>3</value></_47>
                        <_48><tag>Utf8</tag><value>RuntimeVisibleParameterAnnotations</value></_48>
                        <_49><tag>Float</tag><value>22.0</value></_49>
                        <_50><tag>Float</tag><value>11.0</value></_50>
                        <_51><tag>Utf8</tag><value>RuntimeInvisibleParameterAnnotations</value></_51>
                        <_52><tag>Float</tag><value>-22.0</value></_52>
                        <_53><tag>Float</tag><value>-11.0</value></_53>
                        <_54><tag>Utf8</tag><value>Exceptions</value></_54>
                        <_55><tag>Utf8</tag><value>Bee</value></_55>
                        <_56><tag>Class</tag><class_name_index>55</class_name_index><class_internal_name>Bee</class_internal_name></_56>
                        <_57><tag>Utf8</tag><value>Code</value></_57>
                        <_58><tag>Utf8</tag><value>RuntimeInvisibleTypeAnnotations</value></_58>
                        <_59><tag>Utf8</tag><value>RuntimeVisibleTypeAnnotations</value></_59>
                        <_60><tag>Utf8</tag><value>LFee;</value></_60>
                        <_61><tag>Utf8</tag><value>yes</value></_61>
                        <_62><tag>Integer</tag><value>0</value></_62>
                        <_63><tag>Utf8</tag><value>LocalVariableTable</value></_63>
                        <_64><tag>Utf8</tag><value>LocalVariableTypeTable</value></_64>
                        <_65><tag>Utf8</tag><value>LineNumberTable</value></_65>
                        <_66><tag>Utf8</tag><value>StackMapTable</value></_66>
                        <_67><tag>Utf8</tag><value>SourceFile</value></_67>
                        <_68><tag>Utf8</tag><value>Foo.java</value></_68>
                        <_69><tag>Utf8</tag><value>InnerClasses</value></_69>
                        <_70><tag>Utf8</tag><value>InnerName</value></_70>
                        <_71><tag>Utf8</tag><value>EnclosingMethod</value></_71>
                        <_72><tag>Utf8</tag><value>enclosingMethod</value></_72>
                        <_73><tag>Utf8</tag><value>(Ljava/util/Collection;)Ljava/lang/Double;</value></_73>
                        <_74><tag>NameAndType</tag><name_index>72</name_index><type_index>73</type_index><name>enclosingMethod</name><type>(Ljava/util/Collection;)Ljava/lang/Double;</type></_74>
                        <_75><tag>Utf8</tag><value>Synthetic</value></_75>
                        <_76><tag>Utf8</tag><value>Signature</value></_76>
                        <_77><tag>Utf8</tag><value>LBoo;LPhee;LPhoo;</value></_77>
                        <_78><tag>Utf8</tag><value>Deprecated</value></_78>
                        <_79><tag>Utf8</tag><value>NestHost</value></_79>
                        <_80><tag>Utf8</tag><value>NestMembers</value></_80>
                        <_81><tag>Utf8</tag><value>Record</value></_81>
                        <_82><tag>Utf8</tag><value>fee</value></_82>
                        <_83><tag>Utf8</tag><value>RuntimeInvisibleAnnotations</value></_83>
                        <_84><tag>Float</tag><value>2.0</value></_84>
                        <_85><tag>Float</tag><value>3.0</value></_85>
                        <_86><tag>Utf8</tag><value>PermittedSubclasses</value></_86>
                        <_87><tag>Utf8</tag><value>BootstrapMethods</value></_87></constant_pool>
                    <source_file>Foo.java</source_file>
                    <inner_classes>
                        <cls><inner_class>Phee</inner_class><outer_class>Phoo</outer_class><inner_name>InnerName</inner_name><flags><flag>PROTECTED</flag></flags></cls>
                        <cls><inner_class>Phoo</inner_class><outer_class>null</outer_class><inner_name>null</inner_name><flags><flag>PRIVATE</flag></flags></cls></inner_classes>
                    <enclosing_method><class>Phee</class><method_name>enclosingMethod</method_name><method_type>(Ljava/util/Collection;)Ljava/lang/Double;</method_type></enclosing_method>
                    <signature>LBoo;LPhee;LPhoo;</signature>
                    <nest_host>Phee</nest_host>
                    <nest_members><member>Phoo</member><member>Boo</member><member>Bee</member></nest_members>
                    <record_components>
                        <component>
                            <name>fee</name>
                            <type>LPhoo;</type>
                            <attributes><attribute>Signature</attribute><attribute>RuntimeInvisibleTypeAnnotations</attribute></attributes>
                            <signature>LPhoo;</signature>
                            <invisible_type_annotations>
                                <anno><annotation_class>LBoo;</annotation_class><target_info>FIELD</target_info><values></values></anno></invisible_type_annotations></component></record_components>
                    <invisible_annotations>
                        <anno><annotation_class>LPhoo;</annotation_class><values><pair><name>flfl</name><value><float>2.0</float></value></pair><pair><name>frfl</name><value><float>3.0</float></value></pair></values></anno></invisible_annotations>
                    <permitted_subclasses><subclass>Boo</subclass><subclass>Phoo</subclass></permitted_subclasses>
                    <bootstrap_methods>
                        <bm><index>0</index><kind>STATIC</kind><owner>Phoo</owner><name>phee</name><args><arg>bootstrap argument 1</arg><arg>bootstrap argument 2</arg></args></bm></bootstrap_methods>
                    <fields>
                        <field>
                            <field_name>f</field_name>
                            <flags><flag>PRIVATE</flag></flags>
                            <field_type>Ljava/lang/String;</field_type>
                            <attributes><attribute>RuntimeVisibleAnnotations</attribute></attributes>
                            <visible_annotations>
                                <anno><annotation_class>LPhoo;</annotation_class><values><pair><name>flfl</name><value><float>0.0</float></value></pair><pair><name>frfl</name><value><float>1.0</float></value></pair></values></anno></visible_annotations></field></fields>
                    <methods>
                        <method>
                            <method_name>m</method_name>
                            <flags><flag>PROTECTED</flag></flags>
                            <method_type>(ZLjava/lang/Throwable;)Ljava/lang/Void;</method_type>
                            <attributes><attribute>AnnotationDefault</attribute><attribute>RuntimeVisibleParameterAnnotations</attribute><attribute>RuntimeInvisibleParameterAnnotations</attribute><attribute>Exceptions</attribute><attribute>Code</attribute></attributes>
                            <annotation_default><array><value><boolean>true</boolean></value><value><byte>12</byte></value><value><char>99</char></value><value><class>LPhee;</class></value><value><double>1.3</double></value><value><enum_class>LBoo;</enum_class><constant_name>BOO</constant_name></value><value><float>3.7</float></value><value><int>33</int></value><value><long>3333</long></value><value><short>25</short></value><value><string>BOO</string></value><value><annotation_class>LPhoo;</annotation_class></value></array></annotation_default>
                            <visible_parameter_annotations>
                                <parameter_1><anno><annotation_class>LPhoo;</annotation_class><values><pair><name>flfl</name><value><float>22.0</float></value></pair><pair><name>frfl</name><value><float>11.0</float></value></pair></values></anno></parameter_1></visible_parameter_annotations>
                            <invisible_parameter_annotations>
                                <parameter_1><anno><annotation_class>LPhoo;</annotation_class><values><pair><name>flfl</name><value><float>-22.0</float></value></pair><pair><name>frfl</name><value><float>-11.0</float></value></pair></values></anno></parameter_1></invisible_parameter_annotations>
                            <exceptions><exc>Phoo</exc><exc>Boo</exc><exc>Bee</exc></exceptions>
                            <code>
                                <max_stack>1</max_stack>
                                <max_locals>3</max_locals>
                                <attributes><attribute>RuntimeInvisibleTypeAnnotations</attribute><attribute>RuntimeVisibleTypeAnnotations</attribute><attribute>LocalVariableTable</attribute><attribute>LocalVariableTypeTable</attribute><attribute>LineNumberTable</attribute><attribute>StackMapTable</attribute></attributes>
                                <local_variables>
                                    <_1><start>0</start><end>12</end><slot>2</slot><name>variable</name><type>LPhoo;</type></_1></local_variables>
                                <local_variable_types>
                                    <_1><start>0</start><end>12</end><slot>2</slot><name>variable</name><signature>LPhoo;</signature></_1></local_variable_types>
                                <line_numbers>
                                    <_1><start>0</start><line_number>1</line_number></_1>
                                    <_2><start>1</start><line_number>2</line_number></_2>
                                    <_3><start>6</start><line_number>3</line_number></_3>
                                    <_4><start>12</start><line_number>4</line_number></_4></line_numbers>
                                <stack_map_frames>
                                    <_6><locals><item>Foo</item><item>int</item><item>java/lang/Throwable</item></locals><stack></stack></_6>
                                    <_12><locals><item>Foo</item><item>int</item><item>java/lang/Throwable</item></locals><stack><item>Phee</item></stack></_12></stack_map_frames>
                                <invisible_type_annotations>
                                    <anno><annotation_class>LBoo;</annotation_class><target_info>FIELD</target_info><values></values></anno></invisible_type_annotations>
                                <visible_type_annotations>
                                    <anno><annotation_class>LFee;</annotation_class><target_info>FIELD</target_info><values><pair><name>yes</name><value><boolean>false</boolean></value></pair></values></anno></visible_type_annotations>
                                <__stack_map_frame__0><locals><item>Foo</item><item>int</item><item>java/lang/Throwable</item></locals><stack></stack></__stack_map_frame__0>
                                <__try_block_1_start><start>0</start><end>12</end><handler>12</handler><catch_type>Phee</catch_type></__try_block_1_start>
                                <_0><opcode>ILOAD_1</opcode><slot>1</slot></_0>
                                <_1><opcode>IFEQ</opcode><target>6</target></_1>
                                <_4><opcode>ALOAD_2</opcode><slot>2</slot><type>LPhoo;</type><variable_name>variable</variable_name></_4>
                                <_5><opcode>ATHROW</opcode></_5>
                                <__stack_map_frame__6><locals><item>Foo</item><item>int</item><item>java/lang/Throwable</item></locals><stack></stack></__stack_map_frame__6>
                                <_6><opcode>INVOKEDYNAMIC</opcode><name>intfMethod</name><descriptor>()LBoo;</descriptor><bootstrap_method>STATIC Phoo::phee</bootstrap_method><arguments><arg>bootstrap argument 1</arg><arg>bootstrap argument 2</arg></arguments></_6>
                                <_11><opcode>RETURN</opcode></_11>
                                <__stack_map_frame__12><locals><item>Foo</item><item>int</item><item>java/lang/Throwable</item></locals><stack><item>Phee</item></stack></__stack_map_frame__12>
                                <__try_block_1_end><start>0</start><end>12</end><handler>12</handler><catch_type>Phee</catch_type></__try_block_1_end>
                                <__exception_handler_1_start><start>0</start><end>12</end><handler>12</handler><catch_type>Phee</catch_type></__exception_handler_1_start>
                                <_12><opcode>ATHROW</opcode></_12>
                                <exception_handlers>
                                    <handler_1><start>0</start><end>12</end><handler>12</handler><type>Phee</type></handler_1></exception_handlers></code></method></methods></class>
                """);
    }

    @Test
    void testPrintXmlCriticalAttributes() throws IOException {
        var out = new StringBuilder();
        ClassPrinter.toXml(getClassModel(), ClassPrinter.Verbosity.CRITICAL_ATTRIBUTES, out::append);
        assertOut(out,
                """
                <?xml version = '1.0'?>
                <class>
                    <class_name>Foo</class_name>
                    <version>61.0</version>
                    <flags><flag>PUBLIC</flag></flags>
                    <superclass>Boo</superclass>
                    <interfaces><interface>Phee</interface><interface>Phoo</interface></interfaces>
                    <attributes><attribute>SourceFile</attribute><attribute>InnerClasses</attribute><attribute>EnclosingMethod</attribute><attribute>Synthetic</attribute><attribute>Signature</attribute><attribute>Deprecated</attribute><attribute>NestHost</attribute><attribute>NestMembers</attribute><attribute>Record</attribute><attribute>RuntimeInvisibleAnnotations</attribute><attribute>PermittedSubclasses</attribute><attribute>BootstrapMethods</attribute></attributes>
                    <nest_host>Phee</nest_host>
                    <nest_members><member>Phoo</member><member>Boo</member><member>Bee</member></nest_members>
                    <permitted_subclasses><subclass>Boo</subclass><subclass>Phoo</subclass></permitted_subclasses>
                    <bootstrap_methods>
                        <bm><index>0</index><kind>STATIC</kind><owner>Phoo</owner><name>phee</name><args><arg>bootstrap argument 1</arg><arg>bootstrap argument 2</arg></args></bm></bootstrap_methods>
                    <fields>
                        <field>
                            <field_name>f</field_name>
                            <flags><flag>PRIVATE</flag></flags>
                            <field_type>Ljava/lang/String;</field_type>
                            <attributes><attribute>RuntimeVisibleAnnotations</attribute></attributes></field></fields>
                    <methods>
                        <method>
                            <method_name>m</method_name>
                            <flags><flag>PROTECTED</flag></flags>
                            <method_type>(ZLjava/lang/Throwable;)Ljava/lang/Void;</method_type>
                            <attributes><attribute>AnnotationDefault</attribute><attribute>RuntimeVisibleParameterAnnotations</attribute><attribute>RuntimeInvisibleParameterAnnotations</attribute><attribute>Exceptions</attribute><attribute>Code</attribute></attributes>
                            <code>
                                <max_stack>1</max_stack>
                                <max_locals>3</max_locals>
                                <attributes><attribute>RuntimeInvisibleTypeAnnotations</attribute><attribute>RuntimeVisibleTypeAnnotations</attribute><attribute>LocalVariableTable</attribute><attribute>LocalVariableTypeTable</attribute><attribute>LineNumberTable</attribute><attribute>StackMapTable</attribute></attributes>
                                <stack_map_frames>
                                    <_6><locals><item>Foo</item><item>int</item><item>java/lang/Throwable</item></locals><stack></stack></_6>
                                    <_12><locals><item>Foo</item><item>int</item><item>java/lang/Throwable</item></locals><stack><item>Phee</item></stack></_12></stack_map_frames>
                                <__stack_map_frame__0><locals><item>Foo</item><item>int</item><item>java/lang/Throwable</item></locals><stack></stack></__stack_map_frame__0>
                                <__try_block_1_start><start>0</start><end>12</end><handler>12</handler><catch_type>Phee</catch_type></__try_block_1_start>
                                <_0><opcode>ILOAD_1</opcode><slot>1</slot></_0>
                                <_1><opcode>IFEQ</opcode><target>6</target></_1>
                                <_4><opcode>ALOAD_2</opcode><slot>2</slot></_4>
                                <_5><opcode>ATHROW</opcode></_5>
                                <__stack_map_frame__6><locals><item>Foo</item><item>int</item><item>java/lang/Throwable</item></locals><stack></stack></__stack_map_frame__6>
                                <_6><opcode>INVOKEDYNAMIC</opcode><name>intfMethod</name><descriptor>()LBoo;</descriptor><bootstrap_method>STATIC Phoo::phee</bootstrap_method><arguments><arg>bootstrap argument 1</arg><arg>bootstrap argument 2</arg></arguments></_6>
                                <_11><opcode>RETURN</opcode></_11>
                                <__stack_map_frame__12><locals><item>Foo</item><item>int</item><item>java/lang/Throwable</item></locals><stack><item>Phee</item></stack></__stack_map_frame__12>
                                <__try_block_1_end><start>0</start><end>12</end><handler>12</handler><catch_type>Phee</catch_type></__try_block_1_end>
                                <__exception_handler_1_start><start>0</start><end>12</end><handler>12</handler><catch_type>Phee</catch_type></__exception_handler_1_start>
                                <_12><opcode>ATHROW</opcode></_12>
                                <exception_handlers>
                                    <handler_1><start>0</start><end>12</end><handler>12</handler><type>Phee</type></handler_1></exception_handlers></code></method></methods></class>
                """);
    }

    @Test
    void testPrintXmlMembersOnly() throws IOException {
        var out = new StringBuilder();
        ClassPrinter.toXml(getClassModel(), ClassPrinter.Verbosity.MEMBERS_ONLY, out::append);
        assertOut(out,
                """
                <?xml version = '1.0'?>
                <class>
                    <class_name>Foo</class_name>
                    <version>61.0</version>
                    <flags><flag>PUBLIC</flag></flags>
                    <superclass>Boo</superclass>
                    <interfaces><interface>Phee</interface><interface>Phoo</interface></interfaces>
                    <attributes><attribute>SourceFile</attribute><attribute>InnerClasses</attribute><attribute>EnclosingMethod</attribute><attribute>Synthetic</attribute><attribute>Signature</attribute><attribute>Deprecated</attribute><attribute>NestHost</attribute><attribute>NestMembers</attribute><attribute>Record</attribute><attribute>RuntimeInvisibleAnnotations</attribute><attribute>PermittedSubclasses</attribute><attribute>BootstrapMethods</attribute></attributes>
                    <fields>
                        <field>
                            <field_name>f</field_name>
                            <flags><flag>PRIVATE</flag></flags>
                            <field_type>Ljava/lang/String;</field_type>
                            <attributes><attribute>RuntimeVisibleAnnotations</attribute></attributes></field></fields>
                    <methods>
                        <method>
                            <method_name>m</method_name>
                            <flags><flag>PROTECTED</flag></flags>
                            <method_type>(ZLjava/lang/Throwable;)Ljava/lang/Void;</method_type>
                            <attributes><attribute>AnnotationDefault</attribute><attribute>RuntimeVisibleParameterAnnotations</attribute><attribute>RuntimeInvisibleParameterAnnotations</attribute><attribute>Exceptions</attribute><attribute>Code</attribute></attributes></method></methods></class>
                """);
    }

    @Test
    void testWalkTraceAll() throws IOException {
        var node = ClassPrinter.toTree(getClassModel(), ClassPrinter.Verbosity.TRACE_ALL);
        assertEquals(node.walk().count(), 588);
    }

    @Test
    void testWalkCriticalAttributes() throws IOException {
        var node = ClassPrinter.toTree(getClassModel(), ClassPrinter.Verbosity.CRITICAL_ATTRIBUTES);
        assertEquals(node.walk().count(), 146);
    }

    @Test
    void testWalkMembersOnly() throws IOException {
        var node = ClassPrinter.toTree(getClassModel(), ClassPrinter.Verbosity.MEMBERS_ONLY);
        assertEquals(node.walk().count(), 42);
    }

    private static void assertOut(StringBuilder out, String expected) {
//        System.out.println("-----------------");
//        System.out.println(out.toString());
//        System.out.println("-----------------");
        assertArrayEquals(out.toString().trim().split(" *\r?\n"), expected.trim().split("\n"));
    }
}
