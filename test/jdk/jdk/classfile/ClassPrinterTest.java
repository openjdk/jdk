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
 * @summary Testing Classfile ClassPrinter.
 * @run junit ClassPrinterTest
 */
import java.io.IOException;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.util.List;
import java.util.Optional;
import jdk.internal.classfile.*;
import jdk.internal.classfile.attribute.*;
import jdk.internal.classfile.components.ClassPrinter;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClassPrinterTest {

    ClassModel getClassModel() {
        var cc = Classfile.of();
        return cc.parse(cc.build(ClassDesc.of("Foo"), clb ->
            clb.withVersion(61, 0)
                .withFlags(Classfile.ACC_PUBLIC)
                .with(SourceFileAttribute.of("Foo.java"))
                .withSuperclass(ClassDesc.of("Boo"))
                .withInterfaceSymbols(ClassDesc.of("Phee"), ClassDesc.of("Phoo"))
                .with(InnerClassesAttribute.of(
                        InnerClassInfo.of(ClassDesc.of("Phee"), Optional.of(ClassDesc.of("Phoo")), Optional.of("InnerName"), Classfile.ACC_PROTECTED),
                        InnerClassInfo.of(ClassDesc.of("Phoo"), Optional.empty(), Optional.empty(), Classfile.ACC_PRIVATE)))
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
                        .withFlags(Classfile.ACC_PRIVATE)
                        .with(RuntimeVisibleAnnotationsAttribute.of(Annotation.of(ClassDesc.of("Phoo"), AnnotationElement.ofFloat("flfl", 0),  AnnotationElement.ofFloat("frfl", 1)))))
                .withMethod("m", MethodTypeDesc.of(ConstantDescs.CD_Void, ConstantDescs.CD_boolean, ConstantDescs.CD_Throwable), Classfile.ACC_PROTECTED, mb -> mb
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
                    attributes: [SourceFile, InnerClasses, EnclosingMethod, Synthetic, Signature, Deprecated, NestHost, NestMembers, Record, RuntimeInvisibleAnnotations, PermittedSubclasses]
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
                        11: {tag: Utf8, value: Phee}
                        12: {tag: Class, class name index: 11, class internal name: Phee}
                        13: {tag: Utf8, value: Phoo}
                        14: {tag: Class, class name index: 13, class internal name: Phoo}
                        15: {tag: Utf8, value: RuntimeVisibleAnnotations}
                        16: {tag: Utf8, value: flfl}
                        17: {tag: Float, value: 0.0}
                        18: {tag: Utf8, value: frfl}
                        19: {tag: Float, value: 1.0}
                        20: {tag: Utf8, value: AnnotationDefault}
                        21: {tag: Integer, value: 1}
                        22: {tag: Integer, value: 12}
                        23: {tag: Integer, value: 99}
                        24: {tag: Utf8, value: LPhee;}
                        25: {tag: Double, value: 1.3}
                        27: {tag: Utf8, value: LBoo;}
                        28: {tag: Utf8, value: BOO}
                        29: {tag: Float, value: 3.7}
                        30: {tag: Integer, value: 33}
                        31: {tag: Long, value: 3333}
                        33: {tag: Integer, value: 25}
                        34: {tag: Utf8, value: param}
                        35: {tag: Integer, value: 3}
                        36: {tag: Utf8, value: RuntimeVisibleParameterAnnotations}
                        37: {tag: Float, value: 22.0}
                        38: {tag: Float, value: 11.0}
                        39: {tag: Utf8, value: RuntimeInvisibleParameterAnnotations}
                        40: {tag: Float, value: '-22.0'}
                        41: {tag: Float, value: '-11.0'}
                        42: {tag: Utf8, value: Exceptions}
                        43: {tag: Utf8, value: Bee}
                        44: {tag: Class, class name index: 43, class internal name: Bee}
                        45: {tag: Utf8, value: Code}
                        46: {tag: Utf8, value: RuntimeInvisibleTypeAnnotations}
                        47: {tag: Utf8, value: RuntimeVisibleTypeAnnotations}
                        48: {tag: Utf8, value: LFee;}
                        49: {tag: Utf8, value: yes}
                        50: {tag: Integer, value: 0}
                        51: {tag: Utf8, value: LocalVariableTable}
                        52: {tag: Utf8, value: LocalVariableTypeTable}
                        53: {tag: Utf8, value: LineNumberTable}
                        54: {tag: Utf8, value: StackMapTable}
                        55: {tag: Utf8, value: SourceFile}
                        56: {tag: Utf8, value: Foo.java}
                        57: {tag: Utf8, value: InnerClasses}
                        58: {tag: Utf8, value: InnerName}
                        59: {tag: Utf8, value: EnclosingMethod}
                        60: {tag: Utf8, value: enclosingMethod}
                        61: {tag: Utf8, value: (Ljava/util/Collection;)Ljava/lang/Double;}
                        62: {tag: NameAndType, name index: 60, type index: 61, name: enclosingMethod, type: (Ljava/util/Collection;)Ljava/lang/Double;}
                        63: {tag: Utf8, value: Synthetic}
                        64: {tag: Utf8, value: Signature}
                        65: {tag: Utf8, value: LBoo;LPhee;LPhoo;}
                        66: {tag: Utf8, value: Deprecated}
                        67: {tag: Utf8, value: NestHost}
                        68: {tag: Utf8, value: NestMembers}
                        69: {tag: Utf8, value: Record}
                        70: {tag: Utf8, value: fee}
                        71: {tag: Utf8, value: RuntimeInvisibleAnnotations}
                        72: {tag: Float, value: 2.0}
                        73: {tag: Float, value: 3.0}
                        74: {tag: Utf8, value: PermittedSubclasses}
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
                              - {start: 0, end: 7, slot: 2, name: variable, type: LPhoo;}
                            local variable types:
                              - {start: 0, end: 7, slot: 2, name: variable, signature: LPhoo;}
                            line numbers:
                              - {start: 0, line number: 1}
                              - {start: 1, line number: 2}
                              - {start: 6, line number: 3}
                              - {start: 7, line number: 4}
                            stack map frames:
                                6: {locals: [Foo, int, java/lang/Throwable], stack: []}
                                7: {locals: [Foo, int, java/lang/Throwable], stack: [Phee]}
                            invisible type annotations:
                              - {annotation class: LBoo;, target info: FIELD, values: []}
                            visible type annotations:
                              - {annotation class: LFee;, target info: FIELD, values: [{name: yes, value: {boolean: false}}]}
                            //stack map frame @0: {locals: [Foo, int, java/lang/Throwable], stack: []}
                            //try block 1 start: {start: 0, end: 7, handler: 7, catch type: Phee}
                            0: {opcode: ILOAD_1, slot: 1}
                            1: {opcode: IFEQ, target: 6}
                            4: {opcode: ALOAD_2, slot: 2, type: LPhoo;, variable name: variable}
                            5: {opcode: ATHROW}
                            //stack map frame @6: {locals: [Foo, int, java/lang/Throwable], stack: []}
                            6: {opcode: RETURN}
                            //stack map frame @7: {locals: [Foo, int, java/lang/Throwable], stack: [Phee]}
                            //try block 1 end: {start: 0, end: 7, handler: 7, catch type: Phee}
                            //exception handler 1 start: {start: 0, end: 7, handler: 7, catch type: Phee}
                            7: {opcode: ATHROW}
                            exception handlers:
                                handler 1: {start: 0, end: 7, handler: 7, type: Phee}
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
                    attributes: [SourceFile, InnerClasses, EnclosingMethod, Synthetic, Signature, Deprecated, NestHost, NestMembers, Record, RuntimeInvisibleAnnotations, PermittedSubclasses]
                    nest host: Phee
                    nest members: [Phoo, Boo, Bee]
                    permitted subclasses: [Boo, Phoo]
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
                                7: {locals: [Foo, int, java/lang/Throwable], stack: [Phee]}
                            //stack map frame @0: {locals: [Foo, int, java/lang/Throwable], stack: []}
                            //try block 1 start: {start: 0, end: 7, handler: 7, catch type: Phee}
                            0: {opcode: ILOAD_1, slot: 1}
                            1: {opcode: IFEQ, target: 6}
                            4: {opcode: ALOAD_2, slot: 2}
                            5: {opcode: ATHROW}
                            //stack map frame @6: {locals: [Foo, int, java/lang/Throwable], stack: []}
                            6: {opcode: RETURN}
                            //stack map frame @7: {locals: [Foo, int, java/lang/Throwable], stack: [Phee]}
                            //try block 1 end: {start: 0, end: 7, handler: 7, catch type: Phee}
                            //exception handler 1 start: {start: 0, end: 7, handler: 7, catch type: Phee}
                            7: {opcode: ATHROW}
                            exception handlers:
                                handler 1: {start: 0, end: 7, handler: 7, type: Phee}
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
                    attributes: [SourceFile, InnerClasses, EnclosingMethod, Synthetic, Signature, Deprecated, NestHost, NestMembers, Record, RuntimeInvisibleAnnotations, PermittedSubclasses]
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
                    "attributes": ["SourceFile", "InnerClasses", "EnclosingMethod", "Synthetic", "Signature", "Deprecated", "NestHost", "NestMembers", "Record", "RuntimeInvisibleAnnotations", "PermittedSubclasses"],
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
                        "11": {"tag": "Utf8", "value": "Phee"},
                        "12": {"tag": "Class", "class name index": 11, "class internal name": "Phee"},
                        "13": {"tag": "Utf8", "value": "Phoo"},
                        "14": {"tag": "Class", "class name index": 13, "class internal name": "Phoo"},
                        "15": {"tag": "Utf8", "value": "RuntimeVisibleAnnotations"},
                        "16": {"tag": "Utf8", "value": "flfl"},
                        "17": {"tag": "Float", "value": "0.0"},
                        "18": {"tag": "Utf8", "value": "frfl"},
                        "19": {"tag": "Float", "value": "1.0"},
                        "20": {"tag": "Utf8", "value": "AnnotationDefault"},
                        "21": {"tag": "Integer", "value": "1"},
                        "22": {"tag": "Integer", "value": "12"},
                        "23": {"tag": "Integer", "value": "99"},
                        "24": {"tag": "Utf8", "value": "LPhee;"},
                        "25": {"tag": "Double", "value": "1.3"},
                        "27": {"tag": "Utf8", "value": "LBoo;"},
                        "28": {"tag": "Utf8", "value": "BOO"},
                        "29": {"tag": "Float", "value": "3.7"},
                        "30": {"tag": "Integer", "value": "33"},
                        "31": {"tag": "Long", "value": "3333"},
                        "33": {"tag": "Integer", "value": "25"},
                        "34": {"tag": "Utf8", "value": "param"},
                        "35": {"tag": "Integer", "value": "3"},
                        "36": {"tag": "Utf8", "value": "RuntimeVisibleParameterAnnotations"},
                        "37": {"tag": "Float", "value": "22.0"},
                        "38": {"tag": "Float", "value": "11.0"},
                        "39": {"tag": "Utf8", "value": "RuntimeInvisibleParameterAnnotations"},
                        "40": {"tag": "Float", "value": "-22.0"},
                        "41": {"tag": "Float", "value": "-11.0"},
                        "42": {"tag": "Utf8", "value": "Exceptions"},
                        "43": {"tag": "Utf8", "value": "Bee"},
                        "44": {"tag": "Class", "class name index": 43, "class internal name": "Bee"},
                        "45": {"tag": "Utf8", "value": "Code"},
                        "46": {"tag": "Utf8", "value": "RuntimeInvisibleTypeAnnotations"},
                        "47": {"tag": "Utf8", "value": "RuntimeVisibleTypeAnnotations"},
                        "48": {"tag": "Utf8", "value": "LFee;"},
                        "49": {"tag": "Utf8", "value": "yes"},
                        "50": {"tag": "Integer", "value": "0"},
                        "51": {"tag": "Utf8", "value": "LocalVariableTable"},
                        "52": {"tag": "Utf8", "value": "LocalVariableTypeTable"},
                        "53": {"tag": "Utf8", "value": "LineNumberTable"},
                        "54": {"tag": "Utf8", "value": "StackMapTable"},
                        "55": {"tag": "Utf8", "value": "SourceFile"},
                        "56": {"tag": "Utf8", "value": "Foo.java"},
                        "57": {"tag": "Utf8", "value": "InnerClasses"},
                        "58": {"tag": "Utf8", "value": "InnerName"},
                        "59": {"tag": "Utf8", "value": "EnclosingMethod"},
                        "60": {"tag": "Utf8", "value": "enclosingMethod"},
                        "61": {"tag": "Utf8", "value": "(Ljava/util/Collection;)Ljava/lang/Double;"},
                        "62": {"tag": "NameAndType", "name index": 60, "type index": 61, "name": "enclosingMethod", "type": "(Ljava/util/Collection;)Ljava/lang/Double;"},
                        "63": {"tag": "Utf8", "value": "Synthetic"},
                        "64": {"tag": "Utf8", "value": "Signature"},
                        "65": {"tag": "Utf8", "value": "LBoo;LPhee;LPhoo;"},
                        "66": {"tag": "Utf8", "value": "Deprecated"},
                        "67": {"tag": "Utf8", "value": "NestHost"},
                        "68": {"tag": "Utf8", "value": "NestMembers"},
                        "69": {"tag": "Utf8", "value": "Record"},
                        "70": {"tag": "Utf8", "value": "fee"},
                        "71": {"tag": "Utf8", "value": "RuntimeInvisibleAnnotations"},
                        "72": {"tag": "Float", "value": "2.0"},
                        "73": {"tag": "Float", "value": "3.0"},
                        "74": {"tag": "Utf8", "value": "PermittedSubclasses"}},
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
                                    {"start": 0, "end": 7, "slot": 2, "name": "variable", "type": "LPhoo;"}],
                                "local variable types": [
                                    {"start": 0, "end": 7, "slot": 2, "name": "variable", "signature": "LPhoo;"}],
                                "line numbers": [
                                    {"start": 0, "line number": 1},
                                    {"start": 1, "line number": 2},
                                    {"start": 6, "line number": 3},
                                    {"start": 7, "line number": 4}],
                                "stack map frames": {
                                    "6": {"locals": ["Foo", "int", "java/lang/Throwable"], "stack": []},
                                    "7": {"locals": ["Foo", "int", "java/lang/Throwable"], "stack": ["Phee"]}},
                                "invisible type annotations": [
                                    {"annotation class": "LBoo;", "target info": "FIELD", "values": []}],
                                "visible type annotations": [
                                    {"annotation class": "LFee;", "target info": "FIELD", "values": [{"name": "yes", "value": {"boolean": "false"}}]}],
                                "//stack map frame @0": {"locals": ["Foo", "int", "java/lang/Throwable"], "stack": []},
                                "//try block 1 start": {"start": 0, "end": 7, "handler": 7, "catch type": "Phee"},
                                "0": {"opcode": "ILOAD_1", "slot": 1},
                                "1": {"opcode": "IFEQ", "target": 6},
                                "4": {"opcode": "ALOAD_2", "slot": 2, "type": "LPhoo;", "variable name": "variable"},
                                "5": {"opcode": "ATHROW"},
                                "//stack map frame @6": {"locals": ["Foo", "int", "java/lang/Throwable"], "stack": []},
                                "6": {"opcode": "RETURN"},
                                "//stack map frame @7": {"locals": ["Foo", "int", "java/lang/Throwable"], "stack": ["Phee"]},
                                "//try block 1 end": {"start": 0, "end": 7, "handler": 7, "catch type": "Phee"},
                                "//exception handler 1 start": {"start": 0, "end": 7, "handler": 7, "catch type": "Phee"},
                                "7": {"opcode": "ATHROW"},
                                "exception handlers": {
                                    "handler 1": {"start": 0, "end": 7, "handler": 7, "type": "Phee"}}}}]}
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
                    "attributes": ["SourceFile", "InnerClasses", "EnclosingMethod", "Synthetic", "Signature", "Deprecated", "NestHost", "NestMembers", "Record", "RuntimeInvisibleAnnotations", "PermittedSubclasses"],
                    "nest host": "Phee",
                    "nest members": ["Phoo", "Boo", "Bee"],
                    "permitted subclasses": ["Boo", "Phoo"],
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
                                    "7": {"locals": ["Foo", "int", "java/lang/Throwable"], "stack": ["Phee"]}},
                                "//stack map frame @0": {"locals": ["Foo", "int", "java/lang/Throwable"], "stack": []},
                                "//try block 1 start": {"start": 0, "end": 7, "handler": 7, "catch type": "Phee"},
                                "0": {"opcode": "ILOAD_1", "slot": 1},
                                "1": {"opcode": "IFEQ", "target": 6},
                                "4": {"opcode": "ALOAD_2", "slot": 2},
                                "5": {"opcode": "ATHROW"},
                                "//stack map frame @6": {"locals": ["Foo", "int", "java/lang/Throwable"], "stack": []},
                                "6": {"opcode": "RETURN"},
                                "//stack map frame @7": {"locals": ["Foo", "int", "java/lang/Throwable"], "stack": ["Phee"]},
                                "//try block 1 end": {"start": 0, "end": 7, "handler": 7, "catch type": "Phee"},
                                "//exception handler 1 start": {"start": 0, "end": 7, "handler": 7, "catch type": "Phee"},
                                "7": {"opcode": "ATHROW"},
                                "exception handlers": {
                                    "handler 1": {"start": 0, "end": 7, "handler": 7, "type": "Phee"}}}}]}
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
                    "attributes": ["SourceFile", "InnerClasses", "EnclosingMethod", "Synthetic", "Signature", "Deprecated", "NestHost", "NestMembers", "Record", "RuntimeInvisibleAnnotations", "PermittedSubclasses"],
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
                    <attributes><attribute>SourceFile</attribute><attribute>InnerClasses</attribute><attribute>EnclosingMethod</attribute><attribute>Synthetic</attribute><attribute>Signature</attribute><attribute>Deprecated</attribute><attribute>NestHost</attribute><attribute>NestMembers</attribute><attribute>Record</attribute><attribute>RuntimeInvisibleAnnotations</attribute><attribute>PermittedSubclasses</attribute></attributes>
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
                        <_11><tag>Utf8</tag><value>Phee</value></_11>
                        <_12><tag>Class</tag><class_name_index>11</class_name_index><class_internal_name>Phee</class_internal_name></_12>
                        <_13><tag>Utf8</tag><value>Phoo</value></_13>
                        <_14><tag>Class</tag><class_name_index>13</class_name_index><class_internal_name>Phoo</class_internal_name></_14>
                        <_15><tag>Utf8</tag><value>RuntimeVisibleAnnotations</value></_15>
                        <_16><tag>Utf8</tag><value>flfl</value></_16>
                        <_17><tag>Float</tag><value>0.0</value></_17>
                        <_18><tag>Utf8</tag><value>frfl</value></_18>
                        <_19><tag>Float</tag><value>1.0</value></_19>
                        <_20><tag>Utf8</tag><value>AnnotationDefault</value></_20>
                        <_21><tag>Integer</tag><value>1</value></_21>
                        <_22><tag>Integer</tag><value>12</value></_22>
                        <_23><tag>Integer</tag><value>99</value></_23>
                        <_24><tag>Utf8</tag><value>LPhee;</value></_24>
                        <_25><tag>Double</tag><value>1.3</value></_25>
                        <_27><tag>Utf8</tag><value>LBoo;</value></_27>
                        <_28><tag>Utf8</tag><value>BOO</value></_28>
                        <_29><tag>Float</tag><value>3.7</value></_29>
                        <_30><tag>Integer</tag><value>33</value></_30>
                        <_31><tag>Long</tag><value>3333</value></_31>
                        <_33><tag>Integer</tag><value>25</value></_33>
                        <_34><tag>Utf8</tag><value>param</value></_34>
                        <_35><tag>Integer</tag><value>3</value></_35>
                        <_36><tag>Utf8</tag><value>RuntimeVisibleParameterAnnotations</value></_36>
                        <_37><tag>Float</tag><value>22.0</value></_37>
                        <_38><tag>Float</tag><value>11.0</value></_38>
                        <_39><tag>Utf8</tag><value>RuntimeInvisibleParameterAnnotations</value></_39>
                        <_40><tag>Float</tag><value>-22.0</value></_40>
                        <_41><tag>Float</tag><value>-11.0</value></_41>
                        <_42><tag>Utf8</tag><value>Exceptions</value></_42>
                        <_43><tag>Utf8</tag><value>Bee</value></_43>
                        <_44><tag>Class</tag><class_name_index>43</class_name_index><class_internal_name>Bee</class_internal_name></_44>
                        <_45><tag>Utf8</tag><value>Code</value></_45>
                        <_46><tag>Utf8</tag><value>RuntimeInvisibleTypeAnnotations</value></_46>
                        <_47><tag>Utf8</tag><value>RuntimeVisibleTypeAnnotations</value></_47>
                        <_48><tag>Utf8</tag><value>LFee;</value></_48>
                        <_49><tag>Utf8</tag><value>yes</value></_49>
                        <_50><tag>Integer</tag><value>0</value></_50>
                        <_51><tag>Utf8</tag><value>LocalVariableTable</value></_51>
                        <_52><tag>Utf8</tag><value>LocalVariableTypeTable</value></_52>
                        <_53><tag>Utf8</tag><value>LineNumberTable</value></_53>
                        <_54><tag>Utf8</tag><value>StackMapTable</value></_54>
                        <_55><tag>Utf8</tag><value>SourceFile</value></_55>
                        <_56><tag>Utf8</tag><value>Foo.java</value></_56>
                        <_57><tag>Utf8</tag><value>InnerClasses</value></_57>
                        <_58><tag>Utf8</tag><value>InnerName</value></_58>
                        <_59><tag>Utf8</tag><value>EnclosingMethod</value></_59>
                        <_60><tag>Utf8</tag><value>enclosingMethod</value></_60>
                        <_61><tag>Utf8</tag><value>(Ljava/util/Collection;)Ljava/lang/Double;</value></_61>
                        <_62><tag>NameAndType</tag><name_index>60</name_index><type_index>61</type_index><name>enclosingMethod</name><type>(Ljava/util/Collection;)Ljava/lang/Double;</type></_62>
                        <_63><tag>Utf8</tag><value>Synthetic</value></_63>
                        <_64><tag>Utf8</tag><value>Signature</value></_64>
                        <_65><tag>Utf8</tag><value>LBoo;LPhee;LPhoo;</value></_65>
                        <_66><tag>Utf8</tag><value>Deprecated</value></_66>
                        <_67><tag>Utf8</tag><value>NestHost</value></_67>
                        <_68><tag>Utf8</tag><value>NestMembers</value></_68>
                        <_69><tag>Utf8</tag><value>Record</value></_69>
                        <_70><tag>Utf8</tag><value>fee</value></_70>
                        <_71><tag>Utf8</tag><value>RuntimeInvisibleAnnotations</value></_71>
                        <_72><tag>Float</tag><value>2.0</value></_72>
                        <_73><tag>Float</tag><value>3.0</value></_73>
                        <_74><tag>Utf8</tag><value>PermittedSubclasses</value></_74></constant_pool>
                    <source_file>Foo.java</source_file>
                    <inner_classes>
                        <cls><inner_class>Phee</inner_class><outer_class>Phoo</outer_class><inner_name>InnerName</inner_name><flags><flag>PROTECTED</flag></flags></cls>
                        <cls><inner_class>Phoo</inner_class><outer_class>null</outer_class><inner_name>null</inner_name><flags><flag>PRIVATE</flag></flags></cls></inner_classes>
                    <enclosing_method><class>Phee</class><method_name>enclosingMethod</method_name><method_type>(Ljava/util/Collection;)Ljava/lang/Double;</method_type></enclosing_method>
                    <signature>LBoo;LPhee;LPhoo;</signature>
                    <nest_host>Phee</nest_host>
                    <nest_members><member>Phoo</member><member>Boo</member><member>Bee</member></nest_members>
                    <record_components>
                        <record>
                            <name>fee</name>
                            <type>LPhoo;</type>
                            <attributes><attribute>Signature</attribute><attribute>RuntimeInvisibleTypeAnnotations</attribute></attributes>
                            <signature>LPhoo;</signature>
                            <invisible_type_annotations>
                                <anno><annotation_class>LBoo;</annotation_class><target_info>FIELD</target_info><values></values></anno></invisible_type_annotations></record></record_components>
                    <invisible_annotations>
                        <anno><annotation_class>LPhoo;</annotation_class><values><pair><name>flfl</name><value><float>2.0</float></value></pair><pair><name>frfl</name><value><float>3.0</float></value></pair></values></anno></invisible_annotations>
                    <permitted_subclasses><subclass>Boo</subclass><subclass>Phoo</subclass></permitted_subclasses>
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
                                    <_1><start>0</start><end>7</end><slot>2</slot><name>variable</name><type>LPhoo;</type></_1></local_variables>
                                <local_variable_types>
                                    <_1><start>0</start><end>7</end><slot>2</slot><name>variable</name><signature>LPhoo;</signature></_1></local_variable_types>
                                <line_numbers>
                                    <_1><start>0</start><line_number>1</line_number></_1>
                                    <_2><start>1</start><line_number>2</line_number></_2>
                                    <_3><start>6</start><line_number>3</line_number></_3>
                                    <_4><start>7</start><line_number>4</line_number></_4></line_numbers>
                                <stack_map_frames>
                                    <_6><locals><item>Foo</item><item>int</item><item>java/lang/Throwable</item></locals><stack></stack></_6>
                                    <_7><locals><item>Foo</item><item>int</item><item>java/lang/Throwable</item></locals><stack><item>Phee</item></stack></_7></stack_map_frames>
                                <invisible_type_annotations>
                                    <anno><annotation_class>LBoo;</annotation_class><target_info>FIELD</target_info><values></values></anno></invisible_type_annotations>
                                <visible_type_annotations>
                                    <anno><annotation_class>LFee;</annotation_class><target_info>FIELD</target_info><values><pair><name>yes</name><value><boolean>false</boolean></value></pair></values></anno></visible_type_annotations>
                                <__stack_map_frame__0><locals><item>Foo</item><item>int</item><item>java/lang/Throwable</item></locals><stack></stack></__stack_map_frame__0>
                                <__try_block_1_start><start>0</start><end>7</end><handler>7</handler><catch_type>Phee</catch_type></__try_block_1_start>
                                <_0><opcode>ILOAD_1</opcode><slot>1</slot></_0>
                                <_1><opcode>IFEQ</opcode><target>6</target></_1>
                                <_4><opcode>ALOAD_2</opcode><slot>2</slot><type>LPhoo;</type><variable_name>variable</variable_name></_4>
                                <_5><opcode>ATHROW</opcode></_5>
                                <__stack_map_frame__6><locals><item>Foo</item><item>int</item><item>java/lang/Throwable</item></locals><stack></stack></__stack_map_frame__6>
                                <_6><opcode>RETURN</opcode></_6>
                                <__stack_map_frame__7><locals><item>Foo</item><item>int</item><item>java/lang/Throwable</item></locals><stack><item>Phee</item></stack></__stack_map_frame__7>
                                <__try_block_1_end><start>0</start><end>7</end><handler>7</handler><catch_type>Phee</catch_type></__try_block_1_end>
                                <__exception_handler_1_start><start>0</start><end>7</end><handler>7</handler><catch_type>Phee</catch_type></__exception_handler_1_start>
                                <_7><opcode>ATHROW</opcode></_7>
                                <exception_handlers>
                                    <handler_1><start>0</start><end>7</end><handler>7</handler><type>Phee</type></handler_1></exception_handlers></code></method></methods></class>
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
                    <attributes><attribute>SourceFile</attribute><attribute>InnerClasses</attribute><attribute>EnclosingMethod</attribute><attribute>Synthetic</attribute><attribute>Signature</attribute><attribute>Deprecated</attribute><attribute>NestHost</attribute><attribute>NestMembers</attribute><attribute>Record</attribute><attribute>RuntimeInvisibleAnnotations</attribute><attribute>PermittedSubclasses</attribute></attributes>
                    <nest_host>Phee</nest_host>
                    <nest_members><member>Phoo</member><member>Boo</member><member>Bee</member></nest_members>
                    <permitted_subclasses><subclass>Boo</subclass><subclass>Phoo</subclass></permitted_subclasses>
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
                                    <_7><locals><item>Foo</item><item>int</item><item>java/lang/Throwable</item></locals><stack><item>Phee</item></stack></_7></stack_map_frames>
                                <__stack_map_frame__0><locals><item>Foo</item><item>int</item><item>java/lang/Throwable</item></locals><stack></stack></__stack_map_frame__0>
                                <__try_block_1_start><start>0</start><end>7</end><handler>7</handler><catch_type>Phee</catch_type></__try_block_1_start>
                                <_0><opcode>ILOAD_1</opcode><slot>1</slot></_0>
                                <_1><opcode>IFEQ</opcode><target>6</target></_1>
                                <_4><opcode>ALOAD_2</opcode><slot>2</slot></_4>
                                <_5><opcode>ATHROW</opcode></_5>
                                <__stack_map_frame__6><locals><item>Foo</item><item>int</item><item>java/lang/Throwable</item></locals><stack></stack></__stack_map_frame__6>
                                <_6><opcode>RETURN</opcode></_6>
                                <__stack_map_frame__7><locals><item>Foo</item><item>int</item><item>java/lang/Throwable</item></locals><stack><item>Phee</item></stack></__stack_map_frame__7>
                                <__try_block_1_end><start>0</start><end>7</end><handler>7</handler><catch_type>Phee</catch_type></__try_block_1_end>
                                <__exception_handler_1_start><start>0</start><end>7</end><handler>7</handler><catch_type>Phee</catch_type></__exception_handler_1_start>
                                <_7><opcode>ATHROW</opcode></_7>
                                <exception_handlers>
                                    <handler_1><start>0</start><end>7</end><handler>7</handler><type>Phee</type></handler_1></exception_handlers></code></method></methods></class>
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
                    <attributes><attribute>SourceFile</attribute><attribute>InnerClasses</attribute><attribute>EnclosingMethod</attribute><attribute>Synthetic</attribute><attribute>Signature</attribute><attribute>Deprecated</attribute><attribute>NestHost</attribute><attribute>NestMembers</attribute><attribute>Record</attribute><attribute>RuntimeInvisibleAnnotations</attribute><attribute>PermittedSubclasses</attribute></attributes>
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
        assertEquals(node.walk().count(), 509);
    }

    @Test
    void testWalkCriticalAttributes() throws IOException {
        var node = ClassPrinter.toTree(getClassModel(), ClassPrinter.Verbosity.CRITICAL_ATTRIBUTES);
        assertEquals(node.walk().count(), 128);
    }

    @Test
    void testWalkMembersOnly() throws IOException {
        var node = ClassPrinter.toTree(getClassModel(), ClassPrinter.Verbosity.MEMBERS_ONLY);
        assertEquals(node.walk().count(), 41);
    }

    private static void assertOut(StringBuilder out, String expected) {
//        System.out.println("-----------------");
//        System.out.println(out.toString());
//        System.out.println("-----------------");
        assertArrayEquals(out.toString().trim().split(" *\r?\n"), expected.trim().split("\n"));
    }
}
