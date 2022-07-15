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
package jdk.classfile.impl;

import java.lang.constant.DirectMethodHandleDesc;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jdk.classfile.AnnotationElement;
import jdk.classfile.AnnotationValue;
import jdk.classfile.AnnotationValue.*;
import jdk.classfile.Attribute;
import jdk.classfile.ClassModel;
import jdk.classfile.constantpool.AnnotationConstantValueEntry;
import jdk.classfile.constantpool.ClassEntry;
import jdk.classfile.constantpool.ConstantDynamicEntry;
import jdk.classfile.constantpool.DoubleEntry;
import jdk.classfile.constantpool.DynamicConstantPoolEntry;
import jdk.classfile.constantpool.FloatEntry;
import jdk.classfile.constantpool.IntegerEntry;
import jdk.classfile.constantpool.InvokeDynamicEntry;
import jdk.classfile.constantpool.LongEntry;
import jdk.classfile.constantpool.MemberRefEntry;
import jdk.classfile.constantpool.MethodHandleEntry;
import jdk.classfile.constantpool.MethodTypeEntry;
import jdk.classfile.constantpool.ModuleEntry;
import jdk.classfile.constantpool.NameAndTypeEntry;
import jdk.classfile.constantpool.PackageEntry;
import jdk.classfile.constantpool.PoolEntry;
import jdk.classfile.constantpool.StringEntry;
import jdk.classfile.constantpool.Utf8Entry;
import jdk.classfile.util.ClassPrinter;
import jdk.classfile.Instruction;
import jdk.classfile.instruction.*;
import jdk.classfile.MethodModel;
import jdk.classfile.TypeAnnotation;
import jdk.classfile.TypeKind;
import jdk.classfile.attribute.*;
import jdk.classfile.attribute.StackMapTableAttribute.*;

import static jdk.classfile.Classfile.TAG_CLASS;
import static jdk.classfile.Classfile.TAG_CONSTANTDYNAMIC;
import static jdk.classfile.Classfile.TAG_DOUBLE;
import static jdk.classfile.Classfile.TAG_FIELDREF;
import static jdk.classfile.Classfile.TAG_FLOAT;
import static jdk.classfile.Classfile.TAG_INTEGER;
import static jdk.classfile.Classfile.TAG_INTERFACEMETHODREF;
import static jdk.classfile.Classfile.TAG_INVOKEDYNAMIC;
import static jdk.classfile.Classfile.TAG_LONG;
import static jdk.classfile.Classfile.TAG_METHODHANDLE;
import static jdk.classfile.Classfile.TAG_METHODREF;
import static jdk.classfile.Classfile.TAG_METHODTYPE;
import static jdk.classfile.Classfile.TAG_MODULE;
import static jdk.classfile.Classfile.TAG_NAMEANDTYPE;
import static jdk.classfile.Classfile.TAG_PACKAGE;
import static jdk.classfile.Classfile.TAG_STRING;
import static jdk.classfile.Classfile.TAG_UTF8;

/**
 * ClassPrinterImpl
 */
public final class ClassPrinterImpl implements ClassPrinter {

    public record Format(char quotes, boolean quoteFlagsAndAttrs, boolean quoteTypes, String mandatoryDelimiter, String inlineDelimiter,
        Block classForm, Block constantPool, String valueEntry, String stringEntry, String namedEntry, String memberEntry,
        String nameAndTypeEntry, String methodHandleEntry, String methodTypeEntry, String dynamicEntry,
        String fieldsHeader, Block field, String methodsHeader, Block method, String simpleAttr, String simpleQuotedAttr,
        String annotationDefault, Table annotations, Table typeAnnotations, String typeAnnotationInline, Table parameterAnnotations,
        Table annotationValuePair, Table bootstrapMethods, String enclosingMethod, Table innerClasses, Table methodParameters,
        Table recordComponents, String recordComponentTail, Block module, Table requires, Table exports, Table opens, Table provides,
        String modulePackages, String moduleMain, String nestHost, String nestMembers,
        Block code, String plainInstruction, String localVariableInstruction, String incInstruction, String memberInstruction, String invokeDynamicInstruction,
        String branchInstruction, String switchInstruction, String newArrayInstruction, String typeInstruction, String constantInstruction,
        Table exceptionHandlers, String tryStartInline, String tryEndInline, String handlerInline, Table localVariableTable, String localVariableInline,
        String frameInline, Table stackMapTable, Table lineNumberTable, Table characterRangeTable, Table localVariableTypeTable,
        Function<String, String> escapeFunction) {}

    public record Block(String header, String footer){}

    public record Table(String header, String footer, String element){}

    private record ExceptionHandler(int start, int end, int handler, String catchType) {}

    public static final Format JSON = new Format('"', true, true, ",", ", ",
            new Block("  { \"class name\": \"%s\",%n    \"version\": \"%d.%d\",%n    \"flags\": %s,%n    \"superclass\": \"%s\",%n    \"interfaces\": %s,%n    \"attributes\": %s", "]%n  }"),
            new Block(",%n    \"constant pool\": {", " }"),
            "%n        \"%d\": [\"%s\", \"%s\"]",
            "%n        \"%d\": [\"%s\", { \"value index:\": %d, \"value:\": \"%s\" }]",
            "%n        \"%d\": [\"%s\", { \"name index:\": %d, \"name:\": \"%s\" }]",
            "%n        \"%d\": [\"%s\", { \"owner index:\": %d, \"name and type index:\": %d, \"owner:\": \"%s\", \"name:\": \"%s\", \"type:\": \"%s\" }]",
            "%n        \"%d\": [\"%s\", { \"name index:\": %d, \"type index:\": %d, \"name:\": \"%s\", \"type:\": \"%s\" }]",
            "%n        \"%d\": [\"%s\", { \"reference kind:\": \"%s\", \"reference index:\": %d, \"owner:\": \"%s\", \"name:\": \"%s\", \"type:\": \"%s\" }]",
            "%n        \"%d\": [\"%s\", { \"descriptor index:\": %d, \"descriptor:\": \"%s\" }]",
            "%n        \"%d\": [\"%s\", { \"bootstrap method handle index:\": %d, \"bootstrap method arguments indexes:\": %s, \"name and type index:\": %d, \"name:\": \"%s\", \"type:\": \"%s\" }]",
            ",%n    \"fields\": [",
            new Block("%n      { \"field name\": \"%s\",%n        \"flags\": %s,%n        \"descriptor\": \"%s\",%n        \"attributes\": %s", " }"),
            "],%n    \"methods\": [",
            new Block("%n      { \"method name\": \"%s\",%n        \"flags\": %s,%n        \"descriptor\": \"%s\",%n        \"attributes\": %s", " }"),
            ",%n%s\"%s\": %s",
            ",%n%s\"%s\": \"%s\"",
            ",%n%s\"annotation default\": \"%s\"",
            new Table(",%n%s\"%s annotations\": [", "]", "%n%s    {\"class\": \"%s\"%s}"),
            new Table(",%n%s\"%s type annotations\": [", "]", "%n%s    {\"class\": \"%s\", \"target type\": \"%s\"%s}"),
            "",//JSON does not allow comments
            new Table(",%n%s\"%s parameter %d annotations\": [", "]", "%n%s    {\"class\": \"%s\"%s}"),
            new Table(", \"values\": {", "}", "\"%s\": \"%s\""),
            new Table(",%n    \"bootstrap methods\": [", "]", "%n      { \"bootstrap method kind\": \"%s\", \"owner\": \"%s\", \"method name\": \"%s\", \"invocation type\": \"%s\", \"is interface\": %b, \"is methods\": %b }"),
            ",%n%s\"enclosing method\": { \"class\": \"%s\", \"method name\": \"%s\", \"type\": \"s\" }",
            new Table(",%n    \"inner classes\": [", "]", "%n      { \"inner class\": \"%s\", \"outer class\": \"%s\", \"inner class entry\": \"%s\", \"flags\": %s }"),
            new Table(",%n        \"method parameters\": [", "]", "%n          { \"parameter name\": \"%s\", \"flags\": %s }"),
            new Table(",%n    \"record components\": [", "]", "%n      { \"name\": \"%s\", \"type\": \"%s\", \"attributes\": %s"),
            " }",
            new Block(",%n    \"module\":  {%n        \"name\": \"%s\",%n        \"flags\": %s,%n        \"version\": \"%s\",%n        \"uses\": %s", " }"),
            new Table(",%n        \"requires\": [", "]", "%n          { \"name\": \"%s\", \"flags\": %s, \"version\": \"%s\" }"),
            new Table(",%n        \"exports\": [", "]", "%n          { \"package\": \"%s\", \"flags\": %s, \"to\": %s }"),
            new Table(",%n        \"opens\": [", "]", "%n          { \"package\": \"%s\", \"flags\": %s, \"to\": %s }"),
            new Table(",%n        \"provides\": [", "]", "%n          { \"class\": \"%s\", \"with\": %s }"),
            ",%n    \"module packages\": %s",
            ",%n    \"module main class\": \"%s\"",
            ",%n    \"nest host\": \"%s\"",
            ",%n    \"nest members\": %s",
            new Block(",%n        \"code\": {%n            \"max stack\": %d,%n            \"max locals\": %d,%n            \"attributes\": %s", " }"),
            "%n            \"%d\": [\"%s\"]",
            "%n            \"%d\": [\"%s\", { \"slot\": %d%s }]",
            "%n            \"%d\": [\"%s\" , { \"slot\": %d, \"const\": \"%+d\"%s }]",
            "%n            \"%d\": [\"%s\", { \"owner\": \"%s\", \"name\": \"%s\", \"descriptor\": \"%s\" }]",
            "%n            \"%d\": [\"%s\", { \"name\": \"%s\", \"descriptor\": \"%s\", \"bootstrap method kind\": \"%s\", \"owner\": \"%s\", \"method name\": \"%s\", \"invocation type\": \"%s\" }]",
            "%n            \"%d\": [\"%s\", { \"target\": %d }]",
            "%n            \"%d\": [\"%s\", { \"targets\": %s }]",
            "%n            \"%d\": [\"%s\", { \"dimensions\": %d, \"descriptor\": \"%s\" }]",
            "%n            \"%d\": [\"%s\", { \"type\": \"%s\" }]",
            "%n            \"%d\": [\"%s\", { \"constant value\": \"%s\" }]",
            new Table(",%n            \"exception handlers\": [", "]", "%n                  [%d, %d, %d, \"%s\"]"),
            "", //JSON does not allow comments
            "",
            "",
            new Table(",%n            \"local variables%s\": [", "]", "%n                  [%d, %d, %d, \"%s\", \"%s\"]"),
            ", \"type\": \"%s\", \"name\": \"%s\"",
            "",
            new Table(",%n            \"stack map frames\": {", " }", "%n                \"%d\": { \"locals\": %s, \"stack\": %s }"),
            new Table(",%n            \"line numbers%s\": [", "]", "%n                  [%d, %d]"),
            new Table(",%n            \"character ranges\": [", "]", "%n                  [%d, %d, %d, %d, %d]"),
            new Table(",%n            \"local variable types%s\": [", "]", "%n                  [%d, %d, %d, \"%s\", \"%s\"]"),
            ClassPrinterImpl::escapeJson);

    public static final Format XML = new Format('\'', false, false, "", "",
            new Block("<?xml version = '1.0'?>%n  <class name='%s'%n    version='%d.%d'%n    flags='%s'%n    superclass='%s'%n    interfaces='%s'%n    attributes='%s'>",  "</methods>%n</class>"),
            new Block("%n    <constant_pool>", "</constant_pool>"),
            "%n        <:>%d</:><%s>%s</%2$s>",
            "%n        <:>%d</:><%s value_index='%d' value='%s'/>",
            "%n        <:>%d</:><%s name_index='%d' name='%s'/>",
            "%n        <:>%d</:><%s owner_index='%d' name_and_type_index='%d' owner='%s' name='%s' type='%s'/>",
            "%n        <:>%d</:><%s name_index='%d' type_index='%d' name='%s' type='%s'/>",
            "%n        <:>%d</:><%s reference_kind='%s' reference_index='%d' owner='%s' name='%s' type='%s'/>",
            "%n        <:>%d</:><%s descriptor_index='%d' descriptor='%s'/>",
            "%n        <:>%d</:><%s bootstrap_method_handle_index='%d' bootstrap_method_arguments_indexes:='%s' name_and_type_index='%d' name='%s' type='%s'/>",
            "%n    <fields>",
            new Block("%n      <field name='%s'%n        flags='%s'%n        descriptor='%s'%n        attributes='%s'>", "</field>"),
            "</fields>%n    <methods>",
            new Block("%n      <method name='%s'%n        flags='%s'%n        descriptor='%s'%n        attributes='%s'>", "</method>"),
            "%n%s<%s>%s</%2$s>",
            "%n%s<%s>%s</%2$s>",
            "%n%s<annotation_default>'%s'</annotation_default>",
            new Table(",%n%s<%s_annotations>", "</%2$s_annotations>", "%n%s  <annotation class='%s'>%s</annotation>"),
            new Table(",%n%s<%s_type_annotations>", "</%2$s_type_annotations>", "%n%s  <annotation class='%s' target_type='%s'>%s</annotation>"),
            "%n            <!-- %s type annotation class='%s' target_type='%s'%s-->",
            new Table(",%n%s<%s_parameter_%d_annotations>", "</%2$s_parameter_%3$d_annotations>", "%n%s  <annotation class='%s'>%s</annotation>"),
            new Table("<values>", "</values>", "<value name='%s'>%s</value>"),
            new Table(",%n    <bootstrap_methods>", "</bootstrap_methods>", "%n      <bootstrap_method kind='%s' owner='%s' method_name='%s' invocation_type='%s' is_interface='%b' is_method='%b'/>"),
            "%n%s<enclosing_method class='%s' name='%s' type='s'/>",
            new Table(",%n    <inner_classes>", "</inner_classes>", "%n      <inner class='%s' outer='%s' inner_entry='%s' flags='%s'/>"),
            new Table(",%n        <method_parameters>", "</method_parameters>", "%n          <parameter name='%s' flags='%s'/>"),
            new Table(",%n    <record_components>", "</record_components>", "%n      <component name='%s' type='%s' attributes='%s'>"),
            "</component>",
            new Block(",%n    <module name='%s'%n        flags='%s'%n        version='%s'%n        uses='%s'>", "</module>"),
            new Table(",%n        <requires>", "</requires>", "%n          <module name='%s' flags='%s' version='%s'/>"),
            new Table(",%n        <exports>", "</exports>", "%n          <package name='%s' flags='%s' to='%s'/>"),
            new Table(",%n        <opens>", "</opens>", "%n          <package name='%s' flags='%s' to='%s'/>"),
            new Table(",%n        <provides>", "</provides>", "%n          <class name='%s' with='%s'/>"),
            ",%n    <module_packages>%s</module_packages>",
            ",%n    <module_main_class>%s</module_main_class>",
            ",%n    <nest_host>%s</nest_host>",
            ",%n    <nest_members>%s</nest_members>",
            new Block("%n        <code max_stack='%d' max_locals='%d' attributes='%s'>", "</code>"),
            "%n            <:>%d</:><%s/>",
            "%n            <:>%d</:><%s slot='%d'%s/>",
            "%n            <:>%d</:><%s slot='%d' const='%+d'%s/>",
            "%n            <:>%d</:><%s owner='%s' name='%s' descriptor='%s'/>",
            "%n            <:>%d</:><%s name='%s' descriptor='%s' bootstrap_method_kind='%s' owner='%s' method_name='%s' invocation_type='%s'/>",
            "%n            <:>%d</:><%s target='%d'/>",
            "%n            <:>%d</:><%s targets='%s'/>",
            "%n            <:>%d</:><%s dimensions='%d' descriptor:='%s'/>",
            "%n            <:>%d</:><%s type='%s'/>",
            "%n            <:>%d</:><%s constant_value='%s'/>",
            new Table("%n            <exception_handlers>", "</exception_handlers>", "%n                  <exc start='%d' end='%d' handler='%d' catch_type='%s'/>"),
            "%n            <!-- try block start (start='%d' end='%d' handler='%d' catch_type='%s'/) -->",
            "%n            <!-- try block end  (start='%d' end='%d' handler='%d' catch_type='%s'/) -->",
            "%n            <!-- exception handler (start='%d' end='%d' handler='%d' catch_type='%s'/) -->",
            new Table("%n            <local_variables>", "</local_variables>", "%n                <var start='%d' end='%d' name='%s' type='%s'/>"),
            " type='%s' variable_name='%s'",
            "%n            <!-- stack map frame locals: %s, stack: %s -->",
            new Table("%n            <stack_map_frames>", "</stack_map_frames>", "%n                <:>%d</:><frame locals='%s' stack='%s'/>"),
            new Table("%n            <line_numbers>", "</line_numbers>", "%n                <line start_pc='%d' line_number='%d'/>"),
            new Table("%n            <character_ranges>", "</character_ranges>", "%n                <var start='%d' end='%d' range_start='%d' range_end='%d' flags='%d'/>"),
            new Table("%n            <local_variable_types>", "</local_variable_types>", "%n                <var start='%d' end='%d' name='%s' signature='%s'/>"),
            ClassPrinterImpl::escapeXml);

    public static final Format YAML = new Format('\'', false, true, "", ", ",
            new Block("  - class name: '%s'%n    version: '%d.%d'%n    flags: %s%n    superclass: '%s'%n    interfaces: %s%n    attributes: %s", "%n"),
            new Block("%n    constant pool:", ""),
            "%n        %d: [%s, '%s']",
            "%n        %d: [%s, {value index: %d, value: '%s'}]",
            "%n        %d: [%s, {name index: %d, name: '%s'}]",
            "%n        %d: [%s, {owner index: %d, name and type index: %d, owner: '%s', name: '%s', type: '%s'}]",
            "%n        %d: [%s, {name index: %d, type index: %d, name: '%s', type: '%s'}]",
            "%n        %d: [%s, {reference kind: '%s', reference index: %d, owner: '%s', name: '%s', type: '%s'}]",
            "%n        %d: [%s, {descriptor index: %d, descriptor: '%s'}]",
            "%n        %d: [%s, {bootstrap method handle index: %d, bootstrap method arguments indexes: %s, name and type index: '%d', name: '%s', type: '%s'}]",
            "%n    fields:",
            new Block("%n      - field name: '%s'%n        flags: %s%n        descriptor: '%s'%n        attributes: %s", ""),
            "%n    methods:",
            new Block("%n      - method name: '%s'%n        flags: %s%n        descriptor: '%s'%n        attributes: %s", ""),
            "%n%s%s: %s",
            "%n%s%s: '%s'",
            "%n%sannotation default: '%s'",
            new Table("%n%s%s annotations:", "", "%n%s  - {class: '%s'%s}"),
            new Table("%n%s%s type annotations:", "", "%n%s  -  {class: '%s', target type: '%s'%s}"),
            "%n            #%s type annotation: {class: '%s', target type: '%s'%s}",
            new Table("%n%s%s parameter %d annotations:", "", "%n%s  -  {class: '%s'%s}"),
            new Table(", values: {", "}", "'%s': '%s'"),
            new Table("%n    bootstrap methods: #[<method kind>, <owner>, <method name>, <invocation type>, <is interface>, <is methods>]", "", "%n      - ['%s', '%s', '%s', '%s', %b, %b]"),
            "%n%senclosing method: {class: '%s', method name: '%s', type: 's'}",
            new Table("%n    inner classes: #[<inner class>, <outer class>, <inner class entry>, <flags>]", "", "%n      - ['%s', '%s', '%s', %s]"),
            new Table("%n        method parameters: #[<parameter name>, <flags>]", "", "%n          - ['%s', %s]"),
            new Table("%n    record components:", "", "%n      - name: '%s'%n        type: '%s'%n        attributes: %s"),
            "",
            new Block("%n    module:%n        name: '%s'%n        flags: %s%n        version: '%s'%n        uses: %s", ""),
            new Table("%n        requires:", "", "%n          - { name: '%s', flags: %s, version: '%s' }"),
            new Table("%n        exports:", "", "%n          - { package: '%s', flags: %s, to: %s }"),
            new Table("%n        opens:", "", "%n          - { package: '%s', flags: %s, to: %s }"),
            new Table("%n        provides:", "", "%n          - { class: '%s', with: %s }"),
            "%n    module packages: %s",
            "%n    module main class: '%s'",
            "%n    nest host: '%s'",
            "%n    nest members: %s",
            new Block("%n        code:%n            max stack: %d%n            max locals: %d%n            attributes: %s", ""),
            "%n            %d: [%s]",
            "%n            %d: [%s, {slot: %d%s}]",
            "%n            %d: [%s, {slot: %d, const: %+d%s}]",
            "%n            %d: [%s, {owner: '%s', name: '%s', descriptor: '%s'}]",
            "%n            %d: [%s, {name: '%s', descriptor: '%s', bootstrap method kind: %s, owner: '%s', method name: '%s', invocation type: '%s'}]",
            "%n            %d: [%s, {target: %d}]",
            "%n            %d: [%s, {targets: %s}]",
            "%n            %d: [%s, {dimensions: %d, descriptor: '%s'}]",
            "%n            %d: [%s, {type: '%s'}]",
            "%n            %d: [%s, {constant value: '%s'}]",
            new Table("%n            exception handlers: #[<try start pc>, <try end pc>, <handler pc>, <catch type>]", "", "%n                - [%d, %d, %d, %s]"),
            "%n            #try block start: {start: %d, end: %d, handler: %d, catch type: %s}",
            "%n            #try block end: {start: %d, end: %d, handler: %d, catch type: %s}",
            "%n            #exception handler start: {start: %d, end: %d, handler: %d, catch type: %s}",
            new Table("%n            local variables: #[<start pc>, <end pc>, '<name>', <type>]", "", "%n                - [%d, %d, %d, '%s', '%s']"),
            ", type: '%s', variable name: '%s'",
            "%n            #stack map frame locals: %s, stack: %s",
            new Table("%n            stack map frames:", "", "%n                %d: {locals: %s, stack: %s}"),
            new Table("%n            line numbers: #[<start pc>, <line number>]", "", "%n                - [%d, %d]"),
            new Table("%n            character ranges: #[<start pc>, <end pc>, <range start>, <range end>, <flags>]", "", "%n                - [%d, %d, %d, '%s', '%s']"),
            new Table("%n            local variable types: #[<start pc>, <end pc>, '<name>', <signature>]", "", "%n                - [%d, %d, %d, '%s', '%s']"),
            ClassPrinterImpl::escapeYaml);

    private static final char[] DIGITS = "0123456789ABCDEF".toCharArray();

    private List<String> quoteFlags(Set<? extends Enum<?>> flags) {
        return flags.stream().map(f -> format.quoteFlagsAndAttrs ? format.quotes + f.name() + format.quotes : f.name()).toList();
    }

    private String escape(String s) {
        return format.escapeFunction.apply(s);
    }

    private String typesToString(Stream<String> strings) {
        return strings.map(s -> format.quoteTypes ? format.quotes + s + format.quotes : s).collect(Collectors.joining(", ", "[", "]"));
    }

    private String attributeNames(List<Attribute<?>> attributes) {
        return attributes.stream().map(a -> format.quoteFlagsAndAttrs ? format.quotes + a.attributeName() + format.quotes : a.attributeName()).collect(Collectors.joining(", ", "[", "]"));
    }

    private String elementValueToString(AnnotationValue v) {
        return switch (v) {
            case OfConstant cv -> format.escapeFunction().apply(v.tag() == 'Z' ? String.valueOf((int)cv.constantValue() != 0) : String.valueOf(cv.constantValue()));
            case OfClass clv -> clv.className().stringValue();
            case OfEnum ev -> ev.className().stringValue() + "." + ev.constantName().stringValue();
            case OfAnnotation av -> v.tag() + av.annotation().className().stringValue();
            case OfArray av -> av.values().stream().map(ev -> elementValueToString(ev)).collect(Collectors.joining(", ", "[", "]"));
        };
    }

    private String elementValuePairsToString(List<AnnotationElement> evps) {
        return evps.isEmpty() ? "" : evps.stream().map(evp -> format.annotationValuePair.element.formatted(evp.name().stringValue(), elementValueToString(evp.value())))
                .collect(Collectors.joining(format.inlineDelimiter, format.annotationValuePair.header, format.annotationValuePair.footer));
    }

    private static String escapeXml(String s) {
        var sb = new StringBuilder(s.length() << 1);
        s.chars().forEach(c -> {
            switch (c) {
                case '<'  -> sb.append("&lt;");
                case '>'  -> sb.append("&gt;");
                case '"'  -> sb.append("&quot;");
                case '&'  -> sb.append("&amp;");
                case '\''  -> sb.append("&apos;");
                default -> escape(c, sb);
            }});
        return sb.toString();
    }

    private static String escapeYaml(String s) {
        var sb = new StringBuilder(s.length() << 1);
        s.chars().forEach(c -> {
            switch (c) {
                case '\''  -> sb.append("''");
                default -> escape(c, sb);
            }});
        return sb.toString();
    }

    private static String escapeJson(String s) {
        var sb = new StringBuilder(s.length() << 1);
        s.chars().forEach(c -> escape(c, sb));
        return sb.toString();
    }

    private static void escape(int c, StringBuilder sb) {
        switch (c) {
            case '\\'  -> sb.append('\\').append('\\');
            case '"' -> sb.append('\\').append('"');
            case '\b' -> sb.append('\\').append('b');
            case '\n' -> sb.append('\\').append('n');
            case '\t' -> sb.append('\\').append('t');
            case '\f' -> sb.append('\\').append('f');
            case '\r' -> sb.append('\\').append('r');
            default -> {
                if (c >= 0x20 && c < 0x7f) {
                    sb.append((char)c);
                } else {
                    sb.append('\\').append('u').append(DIGITS[(c >> 12) & 0xf]).append(DIGITS[(c >> 8) & 0xf]).append(DIGITS[(c >> 4) & 0xf]).append(DIGITS[(c) & 0xf]);
                }
            }
        }
    }

    private static String formatDescriptor(String desc) {
        int i = desc.lastIndexOf('[');
        if (i >= 0) desc = desc.substring(i + 1);
        desc = switch (desc) {
            case "I", "B", "Z", "F", "S", "C", "J", "D" -> TypeKind.fromDescriptor(desc).typeName();
            default -> desc = Util.descriptorToClass(desc);
        };
        if (i >= 0) {
            var ret = new StringBuilder(desc.length() + 2*i + 2).append(desc);
            while (i-- >= 0) ret.append('[').append(']');
            return ret.toString();
        }
        return desc;
    }

    private static Stream<String> convertVTIs(List<StackMapTableAttribute.VerificationTypeInfo> vtis) {
        return vtis.stream().mapMulti((vti, ret) -> {
            var s = formatDescriptor(vti.toString());
            ret.accept(s);
            if (vti.type() == StackMapTableAttribute.VerificationType.ITEM_DOUBLE || vti.type() == StackMapTableAttribute.VerificationType.ITEM_LONG)
                ret.accept(s + "2");
        });
    }

    private final Format format;
    private final VerbosityLevel verbosity;
    private final Consumer<String> out;

    public ClassPrinterImpl(Format format, VerbosityLevel verbosity, Consumer<String> out) {
        this.format = format;
        this.verbosity = verbosity;
        this.out = out;
    }

    @Override
    public void printClass(ClassModel clm) {
        out.accept(format.classForm.header().formatted(clm.thisClass().asInternalName(), clm.majorVersion(), clm.minorVersion(), quoteFlags(clm.flags().flags()), clm.superclass().map(ClassEntry::asInternalName).orElse(null), typesToString(clm.interfaces().stream().map(ClassEntry::asInternalName)), attributeNames(clm.attributes())));
        if (verbosity == VerbosityLevel.TRACE_ALL) {
            out.accept(format.constantPool.header.formatted());
            for (int i = 1; i < clm.constantPool().entryCount();) {
                if (i > 1) out.accept(format.mandatoryDelimiter);
                var e = clm.constantPool().entryByIndex(i);
                printCPEntry(e);
                i += e.poolEntries();
            }
            out.accept(format.constantPool.footer.formatted());
        }
        if (verbosity != VerbosityLevel.MEMBERS_ONLY) printAttributes("    ", clm.attributes());
        out.accept(format.fieldsHeader.formatted());
        boolean first = true;
        for (var f : clm.fields()) {
            if (first) first = false; else out.accept(format.mandatoryDelimiter);
            out.accept(format.field.header().formatted(f.fieldName().stringValue(), quoteFlags(f.flags().flags()), f.fieldType().stringValue(), attributeNames(f.attributes())));
            if (verbosity != VerbosityLevel.MEMBERS_ONLY) printAttributes("        ", f.attributes());
            out.accept(format.field.footer().formatted());
        }
        out.accept(format.methodsHeader.formatted());
        first = true;
        for (var m : clm.methods()) {
            if (first) first = false; else out.accept(format.mandatoryDelimiter);
            printMethod(m);
        }
        out.accept(format.classForm.footer().formatted());
    }


    public static String tagName(byte tag) {
        return switch (tag) {
            case TAG_UTF8 -> "CONSTANT_Utf8";
            case TAG_INTEGER -> "CONSTANT_Integer";
            case TAG_FLOAT -> "CONSTANT_Float";
            case TAG_LONG -> "CONSTANT_Long";
            case TAG_DOUBLE -> "CONSTANT_Double";
            case TAG_CLASS -> "CONSTANT_Class";
            case TAG_STRING -> "CONSTANT_String";
            case TAG_FIELDREF -> "CONSTANT_Fieldref";
            case TAG_METHODREF -> "CONSTANT_Methodref";
            case TAG_INTERFACEMETHODREF -> "CONSTANT_InterfaceMethodref";
            case TAG_NAMEANDTYPE -> "CONSTANT_NameAndType";
            case TAG_METHODHANDLE -> "CONSTANT_MethodHandle";
            case TAG_METHODTYPE -> "CONSTANT_MethodType";
            case TAG_CONSTANTDYNAMIC -> "CONSTANT_Dynamic";
            case TAG_INVOKEDYNAMIC -> "CONSTANT_InvokeDynamic";
            case TAG_MODULE -> "CONSTANT_Module";
            case TAG_PACKAGE -> "CONSTANT_Package";
            default -> null;
        };
    }

    private void printCPEntry(PoolEntry e) {
        out.accept(switch (e) {
            case Utf8Entry ve -> printValueEntry(ve);
            case IntegerEntry ve -> printValueEntry(ve);
            case FloatEntry ve -> printValueEntry(ve);
            case LongEntry ve -> printValueEntry(ve);
            case DoubleEntry ve -> printValueEntry(ve);
            case ClassEntry ce -> printNamedEntry(e, ce.name());
            case StringEntry se -> format.stringEntry.formatted(e.index(), tagName(e.tag()), se.utf8().index(), escape(se.stringValue()));
            case MemberRefEntry mre -> format.memberEntry.formatted(e.index(), tagName(e.tag()), mre.owner().index(), mre.nameAndType().index(), escape(mre.owner().asInternalName()), escape(mre.name().stringValue()), escape(mre.type().stringValue()));
            case NameAndTypeEntry nte -> format.nameAndTypeEntry.formatted(e.index(), tagName(e.tag()), nte.name().index(), nte.type().index(), escape(nte.name().stringValue()), escape(nte.type().stringValue()));
            case MethodHandleEntry mhe -> format.methodHandleEntry.formatted(e.index(), tagName(e.tag()), DirectMethodHandleDesc.Kind.valueOf(mhe.kind()), mhe.reference().index(), escape(mhe.reference().owner().asInternalName()), escape(mhe.reference().name().stringValue()), escape(mhe.reference().type().stringValue()));
            case MethodTypeEntry mte -> format.methodTypeEntry.formatted(e.index(), tagName(e.tag()), mte.descriptor().index(), escape(mte.descriptor().stringValue()));
            case ConstantDynamicEntry cde -> printDynamicEntry(cde);
            case InvokeDynamicEntry ide -> printDynamicEntry(ide);
            case ModuleEntry me -> printNamedEntry(e, me.name());
            case PackageEntry pe -> printNamedEntry(e, pe.name());
        });
    }

    private String printValueEntry(AnnotationConstantValueEntry e) {
        return format.valueEntry.formatted(e.index(), tagName(e.tag()), escape(String.valueOf(e.constantValue())));
    }

    private String printNamedEntry(PoolEntry e, Utf8Entry name) {
        return format.namedEntry.formatted(e.index(), tagName(e.tag()), name.index(), escape(name.stringValue()));
    }

    private String printDynamicEntry(DynamicConstantPoolEntry dcpe) {
        return format.dynamicEntry.formatted(dcpe.index(), tagName(dcpe.tag()), dcpe.bootstrap().bootstrapMethod().index(),
                dcpe.bootstrap().arguments().stream().map(en -> en.index()).toList(),
                dcpe.nameAndType().index(), escape(dcpe.name().stringValue()), escape(dcpe.type().stringValue()));
    }

    private void printAttributes(String indentSpace, List<Attribute<?>> attributes) {
        for (var attr : attributes) {
            switch (attr) {
                case BootstrapMethodsAttribute bma ->
                    printTable(format.bootstrapMethods, bma.bootstrapMethods(), bm -> {
                        var mh = bm.bootstrapMethod();
                        var mref = mh.reference();
                        return new Object[] {DirectMethodHandleDesc.Kind.valueOf(mh.kind(), mref.isInterface()), mref.owner().asInternalName(), mref.nameAndType().name().stringValue(), mref.nameAndType().type().stringValue(), mref.isInterface(), mref.isMethod()};
                    });
                case ConstantValueAttribute cva ->
                    out.accept(format.simpleQuotedAttr.formatted(indentSpace, "value", escape(cva.constant().constantValue().toString())));
                case NestHostAttribute nha ->
                    out.accept(format.nestHost.formatted(nha.nestHost().asInternalName()));
                case NestMembersAttribute nma ->
                    out.accept(format.nestMembers.formatted(typesToString(nma.nestMembers().stream().map(mp -> mp.asInternalName()))));
                case PermittedSubclassesAttribute psa ->
                    out.accept(format.simpleAttr.formatted(indentSpace, "subclasses", typesToString(psa.permittedSubclasses().stream().map(e -> e.asInternalName()))));
                default -> {}
            }
            if (verbosity == VerbosityLevel.TRACE_ALL) switch (attr) {
                case EnclosingMethodAttribute ema ->
                    out.accept(format.enclosingMethod.formatted(indentSpace, ema.enclosingClass().asInternalName(), ema.enclosingMethod().map(e -> escape(e.name().stringValue())).orElse(null), ema.enclosingMethod().map(e -> escape(e.type().stringValue())).orElse(null)));
                case ExceptionsAttribute exa ->
                    out.accept(format.simpleAttr.formatted(indentSpace, "exceptions", typesToString(exa.exceptions().stream().map(e -> e.asInternalName()))));
                case InnerClassesAttribute ica ->
                    printTable(format.innerClasses, ica.classes(), ic -> new Object[] {ic.innerClass().asInternalName(), ic.outerClass().map(e -> escape(e.asInternalName())).orElse(null), ic.innerName().map(e -> escape(e.stringValue())).orElse(null), quoteFlags(ic.flags())});
                case MethodParametersAttribute mpa ->
                    printTable(format.methodParameters, mpa.parameters(), mp -> new Object[]{mp.name().map(e -> escape(e.stringValue())).orElse(null), quoteFlags(mp.flags())});
                case ModuleAttribute ma -> {
                    out.accept(format.module.header.formatted(ma.moduleName().name().stringValue(), quoteFlags(ma.moduleFlags()), ma.moduleVersion().map(Utf8Entry::stringValue).orElse(""), typesToString(ma.uses().stream().map(ce -> ce.asInternalName()))));
                    printTable(format.requires, ma.requires(), req -> new Object[] {req.requires().name().stringValue(), quoteFlags(req.requiresFlags()), req.requiresVersion().map(Utf8Entry::stringValue).orElse(null)});
                    printTable(format.exports, ma.exports(), exp -> new Object[] {exp.exportedPackage().name().stringValue(), quoteFlags(exp.exportsFlags()), typesToString(exp.exportsTo().stream().map(me -> me.name().stringValue()))});
                    printTable(format.opens, ma.opens(), open -> new Object[] {open.openedPackage().name().stringValue(), quoteFlags(open.opensFlags()), typesToString(open.opensTo().stream().map(me -> me.name().stringValue()))});
                    printTable(format.provides, ma.provides(), provide -> new Object[] {provide.provides().asInternalName(), typesToString(provide.providesWith().stream().map(me -> me.asInternalName()))});
                    out.accept(format.module.footer.formatted());
                }
                case ModulePackagesAttribute mopa ->
                    out.accept(format.modulePackages.formatted(typesToString(mopa.packages().stream().map(mp -> mp.name().stringValue()))));
                case ModuleMainClassAttribute mmca ->
                    out.accept(format.moduleMain.formatted(mmca.mainClass().asInternalName()));
                case RecordAttribute ra ->
                    printTable(format.recordComponents, ra.components(), rc -> new Object[]{rc.name().stringValue(), rc.descriptor().stringValue(), attributeNames(rc.attributes())}, rc -> {
                        printAttributes("        ", rc.attributes());
                        out.accept(format.recordComponentTail);
                    });
                case AnnotationDefaultAttribute ada ->
                    out.accept(format.annotationDefault.formatted(indentSpace, elementValueToString(ada.defaultValue())));
                case RuntimeInvisibleAnnotationsAttribute riaa ->
                    printTable(format.annotations, riaa.annotations(), a -> new Object[]{indentSpace, a.className().stringValue(), elementValuePairsToString(a.elements())}, indentSpace, "invisible");
                case RuntimeVisibleAnnotationsAttribute rvaa ->
                    printTable(format.annotations, rvaa.annotations(), a -> new Object[]{indentSpace, a.className().stringValue(), elementValuePairsToString(a.elements())}, indentSpace, "visible");
                case RuntimeInvisibleParameterAnnotationsAttribute ripaa -> {
                    int i = 0;
                    for (var pa : ripaa.parameterAnnotations()) {
                        i++;
                        if (!pa.isEmpty()) printTable(format.parameterAnnotations, pa, a -> new Object[]{indentSpace, a.className().stringValue(), elementValuePairsToString(a.elements())}, indentSpace, "invisible", i);
                    }
                }
                case RuntimeVisibleParameterAnnotationsAttribute rvpaa -> {
                    int i = 0;
                    for (var pa : rvpaa.parameterAnnotations()) {
                        i++;
                        if (!pa.isEmpty()) printTable(format.parameterAnnotations, pa, a -> new Object[]{indentSpace, a.className().stringValue(), elementValuePairsToString(a.elements())}, indentSpace, "visible", i);
                    }
                }
                case RuntimeInvisibleTypeAnnotationsAttribute ritaa ->
                    printTable(format.typeAnnotations, ritaa.annotations(), a -> new Object[]{indentSpace, a.className().stringValue(), a.targetInfo().targetType(), elementValuePairsToString(a.elements())}, indentSpace, "invisible");
                case RuntimeVisibleTypeAnnotationsAttribute rvtaa ->
                    printTable(format.typeAnnotations, rvtaa.annotations(), a -> new Object[]{indentSpace, a.className().stringValue(), a.targetInfo().targetType(), elementValuePairsToString(a.elements())}, indentSpace, "visible");
                case SignatureAttribute sa ->
                    out.accept(format.simpleQuotedAttr.formatted(indentSpace, "signature", escape(sa.signature().stringValue())));
                case SourceFileAttribute sfa ->
                    out.accept(format.simpleQuotedAttr.formatted(indentSpace, "source", escape(sfa.sourceFile().stringValue())));
                default -> {}
            }
        }
    }

    private String findLocal(List<LocalVariableInfo> locals, int slot, int bci) {
        if (locals != null) {
            for (var l : locals) {
                if (l.slot() == slot && l.startPc() <= bci && l.length() + l.startPc() >= bci) {
                    return format.localVariableInline.formatted(formatDescriptor(l.type().stringValue()), l.name().stringValue());
                }
            }
        }
        return "";
    }

    private <Elem> void printTable(Table tfmt, Collection<Elem> elements, Function<Elem, Object[]> arguments, Object ... headerArgs) {
        printTable(tfmt, elements, arguments, null, headerArgs);
    }

    private <Elem> void printTable(Table tfmt, Collection<Elem> elements, Function<Elem, Object[]> arguments, Consumer<Elem> afterElement, Object ... headerAndFooterArgs) {
        out.accept(tfmt.header().formatted(headerAndFooterArgs));
        boolean first = true;
        for (var e : elements) {
            if (first) first = false; else out.accept(format.mandatoryDelimiter);
            out.accept(tfmt.element().formatted(arguments.apply(e)));
            if (afterElement != null) afterElement.accept(e);
        }
        out.accept(tfmt.footer().formatted(headerAndFooterArgs));
    }

    private void forEachOffset(TypeAnnotation ta, LabelResolver lr, BiConsumer<Integer, TypeAnnotation> consumer) {
        switch (ta.targetInfo()) {
            case TypeAnnotation.OffsetTarget ot -> consumer.accept(lr.labelToBci(ot.target()), ta);
            case TypeAnnotation.TypeArgumentTarget tat -> consumer.accept(lr.labelToBci(tat.target()), ta);
            case TypeAnnotation.LocalVarTarget lvt -> lvt.table().forEach(lvti -> consumer.accept(lr.labelToBci(lvti.startLabel()), ta));
            default -> {}
        }
    }

    @Override
    public void printMethod(MethodModel m) {
        out.accept(format.method.header().formatted(escape(m.methodName().stringValue()), quoteFlags(m.flags().flags()), m.methodType().stringValue(), attributeNames(m.attributes())));
        if (verbosity != VerbosityLevel.MEMBERS_ONLY) {
            printAttributes("        ", m.attributes());
            m.code().ifPresent(com -> {
                out.accept(format.code.header().formatted(((CodeAttribute)com).maxStack(), ((CodeAttribute)com).maxLocals(), attributeNames(com.attributes())));
                var stackMap = new LinkedHashMap<Integer, StackMapFrame>();
                var visibleTypeAnnos = new LinkedHashMap<Integer, TypeAnnotation>();
                var invisibleTypeAnnos = new LinkedHashMap<Integer, TypeAnnotation>();
                List<LocalVariableInfo> locals = List.of();
                int lnc =0, lvc = 0, lvtc = 0;
                for (var attr : com.attributes()) {
                    if (attr instanceof StackMapTableAttribute smta) {
                        for (var smf : smta.entries()) {
                            stackMap.put(smf.absoluteOffset(), smf);
                        }
                        printTable(format.stackMapTable, stackMap.values(), f -> new Object[]{f.absoluteOffset(), typesToString(convertVTIs(f.effectiveLocals())), typesToString(convertVTIs(f.effectiveStack()))});
                    } else if (verbosity == VerbosityLevel.TRACE_ALL) switch (attr) {
                        case LocalVariableTableAttribute lvta ->
                            printTable(format.localVariableTable, locals = lvta.localVariables(), lv -> new Object[]{lv.startPc(), lv.startPc() + lv.length(), lv.slot(), lv.name().stringValue(), formatDescriptor(lv.type().stringValue())}, ++lvc < 2 ? "" : " #" + lvc);
                        case LineNumberTableAttribute lnta ->
                            printTable(format.lineNumberTable, lnta.lineNumbers(), lni -> new Object[] {lni.startPc(), lni.lineNumber()}, ++lnc < 2 ? "" : " #"+lnc);
                        case CharacterRangeTableAttribute crta ->
                            printTable(format.characterRangeTable, crta.characterRangeTable(), chr -> new Object[] {chr.startPc(), chr.endPc(), chr.characterRangeStart(), chr.characterRangeEnd(), chr.flags()});
                        case LocalVariableTypeTableAttribute lvtta ->
                            printTable(format.localVariableTypeTable, lvtta.localVariableTypes(), lvt -> new Object[]{lvt.startPc(), lvt.startPc() + lvt.length(), lvt.slot(), lvt.name().stringValue(), formatDescriptor(lvt.signature().stringValue())}, ++lvtc < 2 ? "" : " #" + lvtc);
                        case RuntimeVisibleTypeAnnotationsAttribute rvtaa ->
                            rvtaa.annotations().forEach(a -> forEachOffset(a, com, visibleTypeAnnos::put));
                        case RuntimeInvisibleTypeAnnotationsAttribute ritaa ->
                            ritaa.annotations().forEach(a -> forEachOffset(a, com, invisibleTypeAnnos::put));
                        case Object o -> {}
                    }
                }
                printAttributes("            ", com.attributes());
                stackMap.putIfAbsent(0, StackMapDecoder.initFrame(m));
                var excHandlers = com.exceptionHandlers().stream().map(exc -> new ExceptionHandler(com.labelToBci(exc.tryStart()), com.labelToBci(exc.tryEnd()), com.labelToBci(exc.handler()), exc.catchType().map(ct -> ct.asInternalName()).orElse(null))).toList();
                int bci = 0;
                for (var coe : com) {
                    if (coe instanceof Instruction ins) {
                        var frame = stackMap.get(bci);
                        if (frame != null) {
                            out.accept(format.frameInline.formatted(typesToString(convertVTIs(frame.effectiveLocals())), typesToString(convertVTIs(frame.effectiveStack()))));
                        }
                        var a = invisibleTypeAnnos.get(bci);
                        if (a != null) {
                            out.accept(format.typeAnnotationInline.formatted("invisible", a.className().stringValue(), a.targetInfo().targetType(), elementValuePairsToString(a.elements())));
                        }
                        a = visibleTypeAnnos.get(bci);
                        if (a != null) {
                            out.accept(format.typeAnnotationInline.formatted("visible", a.className().stringValue(), a.targetInfo().targetType(), elementValuePairsToString(a.elements())));
                        }
                        for (var exc : excHandlers) {
                            if (exc.start() == bci) {
                                out.accept(format.tryStartInline.formatted(exc.start(), exc.end(), exc.handler(), exc.catchType()));
                            }
                            if (exc.end() == bci) {
                                out.accept(format.tryEndInline.formatted(exc.start(), exc.end(), exc.handler(), exc.catchType()));
                            }
                            if (exc.handler() == bci) {
                                out.accept(format.handlerInline.formatted(exc.start(), exc.end(), exc.handler(), exc.catchType()));
                            }
                        }
                        out.accept(format.mandatoryDelimiter);
                        switch (coe) {
                            case IncrementInstruction inc ->
                                out.accept(format.incInstruction.formatted(bci, ins.opcode().name(), inc.slot(), inc.constant(), findLocal(locals, inc.slot(), bci)));
                            case LoadInstruction lv ->
                                out.accept(format.localVariableInstruction.formatted(bci, ins.opcode().name(), lv.slot(), findLocal(locals, lv.slot(), bci)));
                            case StoreInstruction lv ->
                                out.accept(format.localVariableInstruction.formatted(bci, ins.opcode().name(), lv.slot(), findLocal(locals, lv.slot(), bci)));
                            case FieldInstruction fa ->
                                out.accept(format.memberInstruction.formatted(bci, ins.opcode().name(), fa.owner().asInternalName(), escape(fa.name().stringValue()), fa.type().stringValue()));
                            case InvokeInstruction inv ->
                                out.accept(format.memberInstruction.formatted(bci, ins.opcode().name(), inv.owner().asInternalName(), escape(inv.name().stringValue()), inv.type().stringValue()));
                            case InvokeDynamicInstruction invd -> {
                                var bm = invd.bootstrapMethod();
                                out.accept(format.invokeDynamicInstruction.formatted(bci, ins.opcode().name(), invd.name().stringValue(), invd.type().stringValue(), bm.kind(), bm.owner().descriptorString(), escape(bm.methodName()), bm.invocationType().descriptorString()));
                            }
                            case NewObjectInstruction newo ->
                                out.accept(format.typeInstruction.formatted(bci, ins.opcode().name(), newo.className().asInternalName()));
                            case NewPrimitiveArrayInstruction newa -> out.accept(format.newArrayInstruction.formatted(bci, ins.opcode().name(), 1, newa.typeKind().descriptor()));
                            case NewReferenceArrayInstruction newa -> out.accept(format.newArrayInstruction.formatted(bci, ins.opcode().name(), 1, newa.componentType().asInternalName()));
                            case NewMultiArrayInstruction newa -> out.accept(format.newArrayInstruction.formatted(bci, ins.opcode().name(), newa.dimensions(), newa.arrayType().asInternalName()));
                            case TypeCheckInstruction tch ->
                                out.accept(format.typeInstruction.formatted(bci, ins.opcode().name(), tch.type().asInternalName()));
                            case ConstantInstruction cons ->
                                out.accept(format.constantInstruction.formatted(bci, ins.opcode().name(), escape(String.valueOf(cons.constantValue()))));
                            case BranchInstruction br ->
                                out.accept(format.branchInstruction.formatted(bci, ins.opcode().name(), com.labelToBci(br.target())));
                            case LookupSwitchInstruction ls ->
                                out.accept(format.switchInstruction.formatted(bci, ins.opcode().name(), Stream.concat(Stream.of(ls.defaultTarget()).map(com::labelToBci), ls.cases().stream().map(c -> com.labelToBci(c.target()))).toList()));
                            case TableSwitchInstruction ts ->
                                out.accept(format.switchInstruction.formatted(bci, ins.opcode().name(), Stream.concat(Stream.of(ts.defaultTarget()).map(com::labelToBci), ts.cases().stream().map(c -> com.labelToBci(c.target()))).toList()));
                            default ->
                                out.accept(format.plainInstruction.formatted(bci, ins.opcode().name()));
                        }
                        bci += ins.sizeInBytes();
                    }
                }
                out.accept(format.code.footer().formatted());
                if (excHandlers.size() > 0) {
                    printTable(format.exceptionHandlers, excHandlers, exc -> new Object[]{exc.start(), exc.end(), exc.handler(), exc.catchType()});
                }
            });
        }
        out.accept(format.method.footer().formatted());
    }
}
