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

/*
 * @test
 * @summary Testing Classfile ClassPrinter.
 * @run testng ClassPrinterTest
 */
import java.io.IOException;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import jdk.classfile.ClassModel;
import jdk.classfile.Classfile;
import jdk.classfile.attribute.SourceFileAttribute;
import jdk.classfile.util.ClassPrinter;
import org.testng.annotations.Test;
import static org.testng.Assert.assertEquals;

public class ClassPrinterTest {

    ClassModel getClassModel() {
        return Classfile.parse(Classfile.build(ClassDesc.of("Foo"), clb ->
            clb.withVersion(61, 0)
                .withFlags(Classfile.ACC_PUBLIC)
                .with(SourceFileAttribute.of("Foo.java"))
                .withSuperclass(ClassDesc.of("Boo"))
                .withInterfaceSymbols(ClassDesc.of("Phee"), ClassDesc.of("Phoo"))
                .withField("f", ConstantDescs.CD_String, Classfile.ACC_PRIVATE)
                .withMethod("m", MethodTypeDesc.of(ConstantDescs.CD_Void, ConstantDescs.CD_boolean, ConstantDescs.CD_Throwable), Classfile.ACC_PROTECTED, mb -> mb.withCode(cob -> {
                    cob.iload(1);
                    cob.ifThen(thb -> thb.aload(2).athrow());
                    cob.return_();
                }))
        ));
    }

    @Test
    public void testPrintYamlTraceAll() throws IOException {
        var out = new StringBuilder();
        ClassPrinter.yamlPrinter(ClassPrinter.VerbosityLevel.TRACE_ALL, out::append).printClass(getClassModel());
        assertOut(out,
                """
                  - class name: 'Foo'
                    version: '61.0'
                    flags: [PUBLIC]
                    superclass: 'Boo'
                    interfaces: ['Phee', 'Phoo']
                    attributes: [SourceFile]
                    constant pool:
                        1: [CONSTANT_Utf8, 'Foo']
                        2: [CONSTANT_Class, {name index: 1, name: 'Foo'}]
                        3: [CONSTANT_Utf8, 'Boo']
                        4: [CONSTANT_Class, {name index: 3, name: 'Boo'}]
                        5: [CONSTANT_Utf8, 'f']
                        6: [CONSTANT_Utf8, 'Ljava/lang/String;']
                        7: [CONSTANT_Utf8, 'm']
                        8: [CONSTANT_Utf8, '(ZLjava/lang/Throwable;)Ljava/lang/Void;']
                        9: [CONSTANT_Utf8, 'Phee']
                        10: [CONSTANT_Class, {name index: 9, name: 'Phee'}]
                        11: [CONSTANT_Utf8, 'Phoo']
                        12: [CONSTANT_Class, {name index: 11, name: 'Phoo'}]
                        13: [CONSTANT_Utf8, 'Code']
                        14: [CONSTANT_Utf8, 'StackMapTable']
                        15: [CONSTANT_Utf8, 'SourceFile']
                        16: [CONSTANT_Utf8, 'Foo.java']
                    source: 'Foo.java'
                    fields:
                      - field name: 'f'
                        flags: [PRIVATE]
                        descriptor: 'Ljava/lang/String;'
                        attributes: []
                    methods:
                      - method name: 'm'
                        flags: [PROTECTED]
                        descriptor: '(ZLjava/lang/Throwable;)Ljava/lang/Void;'
                        attributes: [Code]
                        code:
                            max stack: 1
                            max locals: 3
                            attributes: [StackMapTable]
                            stack map frames:
                                6: {locals: ['Foo', 'int', 'java/lang/Throwable'], stack: []}
                            #stack map frame locals: ['Foo', 'int', 'java/lang/Throwable'], stack: []
                            0: [ILOAD_1, {slot: 1}]
                            1: [IFEQ, {target: 6}]
                            4: [ALOAD_2, {slot: 2}]
                            5: [ATHROW]
                            #stack map frame locals: ['Foo', 'int', 'java/lang/Throwable'], stack: []
                            6: [RETURN]
                """);
    }

    @Test
    public void testPrintYamlCriticalAttributes() throws IOException {
        var out = new StringBuilder();
        ClassPrinter.yamlPrinter(ClassPrinter.VerbosityLevel.CRITICAL_ATTRIBUTES, out::append).printClass(getClassModel());
        assertOut(out,
                """
                  - class name: 'Foo'
                    version: '61.0'
                    flags: [PUBLIC]
                    superclass: 'Boo'
                    interfaces: ['Phee', 'Phoo']
                    attributes: [SourceFile]
                    fields:
                      - field name: 'f'
                        flags: [PRIVATE]
                        descriptor: 'Ljava/lang/String;'
                        attributes: []
                    methods:
                      - method name: 'm'
                        flags: [PROTECTED]
                        descriptor: '(ZLjava/lang/Throwable;)Ljava/lang/Void;'
                        attributes: [Code]
                        code:
                            max stack: 1
                            max locals: 3
                            attributes: [StackMapTable]
                            stack map frames:
                                6: {locals: ['Foo', 'int', 'java/lang/Throwable'], stack: []}
                            #stack map frame locals: ['Foo', 'int', 'java/lang/Throwable'], stack: []
                            0: [ILOAD_1, {slot: 1}]
                            1: [IFEQ, {target: 6}]
                            4: [ALOAD_2, {slot: 2}]
                            5: [ATHROW]
                            #stack map frame locals: ['Foo', 'int', 'java/lang/Throwable'], stack: []
                            6: [RETURN]
                """);
    }

    @Test
    public void testPrintYamlMembersOnly() throws IOException {
        var out = new StringBuilder();
        ClassPrinter.yamlPrinter(ClassPrinter.VerbosityLevel.MEMBERS_ONLY, out::append).printClass(getClassModel());
        assertOut(out,
                """
                  - class name: 'Foo'
                    version: '61.0'
                    flags: [PUBLIC]
                    superclass: 'Boo'
                    interfaces: ['Phee', 'Phoo']
                    attributes: [SourceFile]
                    fields:
                      - field name: 'f'
                        flags: [PRIVATE]
                        descriptor: 'Ljava/lang/String;'
                        attributes: []
                    methods:
                      - method name: 'm'
                        flags: [PROTECTED]
                        descriptor: '(ZLjava/lang/Throwable;)Ljava/lang/Void;'
                        attributes: [Code]
                """);
    }

    @Test
    public void testPrintJsonTraceAll() throws IOException {
        var out = new StringBuilder();
        ClassPrinter.jsonPrinter(ClassPrinter.VerbosityLevel.TRACE_ALL, out::append).printClass(getClassModel());
        assertOut(out,
                """
                { "class name": "Foo",
                    "version": "61.0",
                    "flags": ["PUBLIC"],
                    "superclass": "Boo",
                    "interfaces": ["Phee", "Phoo"],
                    "attributes": ["SourceFile"],
                    "constant pool": {
                        "1": ["CONSTANT_Utf8", "Foo"],
                        "2": ["CONSTANT_Class", { "name index:": 1, "name:": "Foo" }],
                        "3": ["CONSTANT_Utf8", "Boo"],
                        "4": ["CONSTANT_Class", { "name index:": 3, "name:": "Boo" }],
                        "5": ["CONSTANT_Utf8", "f"],
                        "6": ["CONSTANT_Utf8", "Ljava/lang/String;"],
                        "7": ["CONSTANT_Utf8", "m"],
                        "8": ["CONSTANT_Utf8", "(ZLjava/lang/Throwable;)Ljava/lang/Void;"],
                        "9": ["CONSTANT_Utf8", "Phee"],
                        "10": ["CONSTANT_Class", { "name index:": 9, "name:": "Phee" }],
                        "11": ["CONSTANT_Utf8", "Phoo"],
                        "12": ["CONSTANT_Class", { "name index:": 11, "name:": "Phoo" }],
                        "13": ["CONSTANT_Utf8", "Code"],
                        "14": ["CONSTANT_Utf8", "StackMapTable"],
                        "15": ["CONSTANT_Utf8", "SourceFile"],
                        "16": ["CONSTANT_Utf8", "Foo.java"] },
                    "source": "Foo.java",
                    "fields": [
                      { "field name": "f",
                        "flags": ["PRIVATE"],
                        "descriptor": "Ljava/lang/String;",
                        "attributes": [] }],
                    "methods": [
                      { "method name": "m",
                        "flags": ["PROTECTED"],
                        "descriptor": "(ZLjava/lang/Throwable;)Ljava/lang/Void;",
                        "attributes": ["Code"],
                        "code": {
                            "max stack": 1,
                            "max locals": 3,
                            "attributes": ["StackMapTable"],
                            "stack map frames": {
                                "6": { "locals": ["Foo", "int", "java/lang/Throwable"], "stack": [] } },
                            "0": ["ILOAD_1", { "slot": 1 }],
                            "1": ["IFEQ", { "target": 6 }],
                            "4": ["ALOAD_2", { "slot": 2 }],
                            "5": ["ATHROW"],
                            "6": ["RETURN"] } }]
                  }
                """);
    }

    @Test
    public void testPrintJsonCriticalAttributes() throws IOException {
        var out = new StringBuilder();
        ClassPrinter.jsonPrinter(ClassPrinter.VerbosityLevel.CRITICAL_ATTRIBUTES, out::append).printClass(getClassModel());
        assertOut(out,
                """
                  { "class name": "Foo",
                    "version": "61.0",
                    "flags": ["PUBLIC"],
                    "superclass": "Boo",
                    "interfaces": ["Phee", "Phoo"],
                    "attributes": ["SourceFile"],
                    "fields": [
                      { "field name": "f",
                        "flags": ["PRIVATE"],
                        "descriptor": "Ljava/lang/String;",
                        "attributes": [] }],
                    "methods": [
                      { "method name": "m",
                        "flags": ["PROTECTED"],
                        "descriptor": "(ZLjava/lang/Throwable;)Ljava/lang/Void;",
                        "attributes": ["Code"],
                        "code": {
                            "max stack": 1,
                            "max locals": 3,
                            "attributes": ["StackMapTable"],
                            "stack map frames": {
                                "6": { "locals": ["Foo", "int", "java/lang/Throwable"], "stack": [] } },
                            "0": ["ILOAD_1", { "slot": 1 }],
                            "1": ["IFEQ", { "target": 6 }],
                            "4": ["ALOAD_2", { "slot": 2 }],
                            "5": ["ATHROW"],
                            "6": ["RETURN"] } }]
                  }
                """);
    }

    @Test
    public void testPrintJsonMembersOnly() throws IOException {
        var out = new StringBuilder();
        ClassPrinter.jsonPrinter(ClassPrinter.VerbosityLevel.MEMBERS_ONLY, out::append).printClass(getClassModel());
        assertOut(out,
                """
                  { "class name": "Foo",
                    "version": "61.0",
                    "flags": ["PUBLIC"],
                    "superclass": "Boo",
                    "interfaces": ["Phee", "Phoo"],
                    "attributes": ["SourceFile"],
                    "fields": [
                      { "field name": "f",
                        "flags": ["PRIVATE"],
                        "descriptor": "Ljava/lang/String;",
                        "attributes": [] }],
                    "methods": [
                      { "method name": "m",
                        "flags": ["PROTECTED"],
                        "descriptor": "(ZLjava/lang/Throwable;)Ljava/lang/Void;",
                        "attributes": ["Code"] }]
                  }
                """);
    }

    @Test
    public void testPrintXmlTraceAll() throws IOException {
        var out = new StringBuilder();
        ClassPrinter.xmlPrinter(ClassPrinter.VerbosityLevel.TRACE_ALL, out::append).printClass(getClassModel());
        assertOut(out,
                """
                <?xml version = '1.0'?>
                  <class name='Foo'
                    version='61.0'
                    flags='[PUBLIC]'
                    superclass='Boo'
                    interfaces='[Phee, Phoo]'
                    attributes='[SourceFile]'>
                    <constant_pool>
                        <:>1</:><CONSTANT_Utf8>Foo</CONSTANT_Utf8>
                        <:>2</:><CONSTANT_Class name_index='1' name='Foo'/>
                        <:>3</:><CONSTANT_Utf8>Boo</CONSTANT_Utf8>
                        <:>4</:><CONSTANT_Class name_index='3' name='Boo'/>
                        <:>5</:><CONSTANT_Utf8>f</CONSTANT_Utf8>
                        <:>6</:><CONSTANT_Utf8>Ljava/lang/String;</CONSTANT_Utf8>
                        <:>7</:><CONSTANT_Utf8>m</CONSTANT_Utf8>
                        <:>8</:><CONSTANT_Utf8>(ZLjava/lang/Throwable;)Ljava/lang/Void;</CONSTANT_Utf8>
                        <:>9</:><CONSTANT_Utf8>Phee</CONSTANT_Utf8>
                        <:>10</:><CONSTANT_Class name_index='9' name='Phee'/>
                        <:>11</:><CONSTANT_Utf8>Phoo</CONSTANT_Utf8>
                        <:>12</:><CONSTANT_Class name_index='11' name='Phoo'/>
                        <:>13</:><CONSTANT_Utf8>Code</CONSTANT_Utf8>
                        <:>14</:><CONSTANT_Utf8>StackMapTable</CONSTANT_Utf8>
                        <:>15</:><CONSTANT_Utf8>SourceFile</CONSTANT_Utf8>
                        <:>16</:><CONSTANT_Utf8>Foo.java</CONSTANT_Utf8></constant_pool>
                    <source>Foo.java</source>
                    <fields>
                      <field name='f'
                        flags='[PRIVATE]'
                        descriptor='Ljava/lang/String;'
                        attributes='[]'></field></fields>
                    <methods>
                      <method name='m'
                        flags='[PROTECTED]'
                        descriptor='(ZLjava/lang/Throwable;)Ljava/lang/Void;'
                        attributes='[Code]'>
                        <code max_stack='1' max_locals='3' attributes='[StackMapTable]'>
                            <stack_map_frames>
                                <:>6</:><frame locals='[Foo, int, java/lang/Throwable]' stack='[]'/></stack_map_frames>
                            <!-- stack map frame locals: [Foo, int, java/lang/Throwable], stack: [] -->
                            <:>0</:><ILOAD_1 slot='1'/>
                            <:>1</:><IFEQ target='6'/>
                            <:>4</:><ALOAD_2 slot='2'/>
                            <:>5</:><ATHROW/>
                            <!-- stack map frame locals: [Foo, int, java/lang/Throwable], stack: [] -->
                            <:>6</:><RETURN/></code></method></methods>
                </class>
                """);
    }

    @Test
    public void testPrintXmlCriticalAttributes() throws IOException {
        var out = new StringBuilder();
        ClassPrinter.xmlPrinter(ClassPrinter.VerbosityLevel.CRITICAL_ATTRIBUTES, out::append).printClass(getClassModel());
        assertOut(out,
                """
                <?xml version = '1.0'?>
                  <class name='Foo'
                    version='61.0'
                    flags='[PUBLIC]'
                    superclass='Boo'
                    interfaces='[Phee, Phoo]'
                    attributes='[SourceFile]'>
                    <fields>
                      <field name='f'
                        flags='[PRIVATE]'
                        descriptor='Ljava/lang/String;'
                        attributes='[]'></field></fields>
                    <methods>
                      <method name='m'
                        flags='[PROTECTED]'
                        descriptor='(ZLjava/lang/Throwable;)Ljava/lang/Void;'
                        attributes='[Code]'>
                        <code max_stack='1' max_locals='3' attributes='[StackMapTable]'>
                            <stack_map_frames>
                                <:>6</:><frame locals='[Foo, int, java/lang/Throwable]' stack='[]'/></stack_map_frames>
                            <!-- stack map frame locals: [Foo, int, java/lang/Throwable], stack: [] -->
                            <:>0</:><ILOAD_1 slot='1'/>
                            <:>1</:><IFEQ target='6'/>
                            <:>4</:><ALOAD_2 slot='2'/>
                            <:>5</:><ATHROW/>
                            <!-- stack map frame locals: [Foo, int, java/lang/Throwable], stack: [] -->
                            <:>6</:><RETURN/></code></method></methods>
                </class>
                """);
    }

    @Test
    public void testPrintXmlMembersOnly() throws IOException {
        var out = new StringBuilder();
        ClassPrinter.xmlPrinter(ClassPrinter.VerbosityLevel.MEMBERS_ONLY, out::append).printClass(getClassModel());
        assertOut(out,
                """
                <?xml version = '1.0'?>
                  <class name='Foo'
                    version='61.0'
                    flags='[PUBLIC]'
                    superclass='Boo'
                    interfaces='[Phee, Phoo]'
                    attributes='[SourceFile]'>
                    <fields>
                      <field name='f'
                        flags='[PRIVATE]'
                        descriptor='Ljava/lang/String;'
                        attributes='[]'></field></fields>
                    <methods>
                      <method name='m'
                        flags='[PROTECTED]'
                        descriptor='(ZLjava/lang/Throwable;)Ljava/lang/Void;'
                        attributes='[Code]'></method></methods>
                </class>
                """);
    }

    private static void assertOut(StringBuilder out, String expected) {
        assertEquals(out.toString().replaceAll("\\\r", "").trim(), expected.trim());
    }
}
