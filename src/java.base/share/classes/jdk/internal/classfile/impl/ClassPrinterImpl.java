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
package jdk.internal.classfile.impl;

import java.lang.constant.ConstantDesc;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.reflect.AccessFlag;
import java.util.AbstractList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.lang.classfile.Annotation;

import java.lang.classfile.AnnotationElement;
import java.lang.classfile.AnnotationValue;
import java.lang.classfile.AnnotationValue.*;
import java.lang.classfile.Attribute;
import java.lang.classfile.ClassModel;
import java.lang.classfile.components.ClassPrinter.*;
import java.lang.classfile.CodeModel;
import java.lang.classfile.Instruction;
import java.lang.classfile.MethodModel;
import java.lang.classfile.TypeAnnotation;
import java.lang.classfile.attribute.*;
import java.lang.classfile.attribute.StackMapFrameInfo.*;
import java.lang.classfile.constantpool.*;
import java.lang.classfile.instruction.*;

import static java.lang.classfile.ClassFile.*;
import java.lang.classfile.CompoundElement;
import java.lang.classfile.FieldModel;
import static jdk.internal.classfile.impl.ClassPrinterImpl.Style.*;

public final class ClassPrinterImpl {

    public enum Style { BLOCK, FLOW }

    public record LeafNodeImpl(ConstantDesc name, ConstantDesc value) implements LeafNode {

        @Override
        public Stream<Node> walk() {
            return Stream.of(this);
        }
    }

    public static final class ListNodeImpl extends AbstractList<Node> implements ListNode {

        private final Style style;
        private final ConstantDesc name;
        private final Node[] nodes;

        public ListNodeImpl(Style style, ConstantDesc name, Stream<Node> nodes) {
            this.style = style;
            this.name = name;
            this.nodes = nodes.toArray(Node[]::new);
        }

        @Override
        public ConstantDesc name() {
            return name;
        }

        @Override
        public Stream<Node> walk() {
            return Stream.concat(Stream.of(this), stream().flatMap(Node::walk));
        }

        public Style style() {
            return style;
        }

        @Override
        public Node get(int index) {
            Objects.checkIndex(index, nodes.length);
            return nodes[index];
        }

        @Override
        public int size() {
            return nodes.length;
        }
    }

    public static final class MapNodeImpl implements MapNode {

        private final Style style;
        private final ConstantDesc name;
        private final Map<ConstantDesc, Node> map;

        public MapNodeImpl(Style style, ConstantDesc name) {
            this.style = style;
            this.name = name;
            this.map = new LinkedHashMap<>();
        }

        @Override
        public ConstantDesc name() {
            return name;
        }

        @Override
        public Stream<Node> walk() {
            return Stream.concat(Stream.of(this), values().stream().flatMap(Node::walk));
        }

        public Style style() {
            return style;
        }

        @Override
        public int size() {
            return map.size();
        }
        @Override
        public boolean isEmpty() {
            return map.isEmpty();
        }
        @Override
        public boolean containsKey(Object key) {
            return map.containsKey(key);
        }
        @Override
        public boolean containsValue(Object value) {
            return map.containsValue(value);
        }

        @Override
        public Node get(Object key) {
            return map.get(key);
        }

        @Override
        public Node put(ConstantDesc key, Node value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Node remove(Object key) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void putAll(Map<? extends ConstantDesc, ? extends Node> m) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void clear() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Set<ConstantDesc> keySet() {
            return Collections.unmodifiableSet(map.keySet());
        }

        @Override
        public Collection<Node> values() {
            return Collections.unmodifiableCollection(map.values());
        }

        @Override
        public Set<Entry<ConstantDesc, Node>> entrySet() {
            return Collections.unmodifiableSet(map.entrySet());
        }


        MapNodeImpl with(Node... nodes) {
            for (var n : nodes)
                if (n != null && map.put(n.name(), n) != null)
                    throw new AssertionError("Double entry of " + n.name() + " into " + name);
            return this;
        }
    }

    private static Node leaf(ConstantDesc name, ConstantDesc value) {
        return new LeafNodeImpl(name, value);
    }

    private static Node[] leafs(ConstantDesc... namesAndValues) {
        if ((namesAndValues.length & 1) > 0)
            throw new AssertionError("Odd number of arguments: " + Arrays.toString(namesAndValues));
        var nodes = new Node[namesAndValues.length >> 1];
        for (int i = 0, j = 0; i < nodes.length; i ++) {
            nodes[i] = leaf(namesAndValues[j++], namesAndValues[j++]);
        }
        return nodes;
    }

    private static Node list(ConstantDesc listName, ConstantDesc itemsName, Stream<ConstantDesc> values) {
        return new ListNodeImpl(FLOW, listName, values.map(v -> leaf(itemsName, v)));
    }

    private static Node map(ConstantDesc mapName, ConstantDesc... keysAndValues) {
        return new MapNodeImpl(FLOW, mapName).with(leafs(keysAndValues));
    }

    private static final String NL = System.lineSeparator();

    private static final char[] DIGITS = "0123456789ABCDEF".toCharArray();

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
                    sb.append('\\').append('u').append(DIGITS[(c >> 12) & 0xf])
                            .append(DIGITS[(c >> 8) & 0xf]).append(DIGITS[(c >> 4) & 0xf]).append(DIGITS[(c) & 0xf]);
                }
            }
        }
    }

    public static void toYaml(Node node, Consumer<String> out) {
        toYaml(0, false, new ListNodeImpl(BLOCK, null, Stream.of(node)), out);
        out.accept(NL);
    }

    private static void toYaml(int indent, boolean skipFirstIndent, Node node, Consumer<String> out) {
        switch (node) {
            case LeafNode leaf -> {
                out.accept(quoteAndEscapeYaml(leaf.value()));
            }
            case ListNodeImpl list -> {
                switch (list.style()) {
                    case FLOW -> {
                        out.accept("[");
                        boolean first = true;
                        for (var n : list) {
                            if (first) first = false;
                            else out.accept(", ");
                            toYaml(0, false, n, out);
                        }
                        out.accept("]");
                    }
                    case BLOCK -> {
                        for (var n : list) {
                            out.accept(NL + "    ".repeat(indent) + "  - ");
                            toYaml(indent + 1, true, n, out);
                        }
                    }
                }
            }
            case MapNodeImpl map -> {
                switch (map.style()) {
                    case FLOW -> {
                        out.accept("{");
                        boolean first = true;
                        for (var n : map.values()) {
                            if (first) first = false;
                            else out.accept(", ");
                            out.accept(quoteAndEscapeYaml(n.name()) + ": ");
                            toYaml(0, false, n, out);
                        }
                        out.accept("}");
                    }
                    case BLOCK -> {
                        for (var n : map.values()) {
                            if (skipFirstIndent) {
                                skipFirstIndent = false;
                            } else {
                                out.accept(NL + "    ".repeat(indent));
                            }
                            out.accept(quoteAndEscapeYaml(n.name()) + ": ");
                            toYaml(n instanceof ListNodeImpl pl && pl.style() == BLOCK ? indent : indent + 1, false, n, out);
                        }
                    }
                }
            }
        }
    }

    private static String quoteAndEscapeYaml(ConstantDesc value) {
        String s = String.valueOf(value);
        if (value instanceof Number) return s;
        if (s.length() == 0) return "''";
        var sb = new StringBuilder(s.length() << 1);
        s.chars().forEach(c -> {
            switch (c) {
                case '\''  -> sb.append("''");
                default -> escape(c, sb);
            }});
        String esc = sb.toString();
        if (esc.length() != s.length()) return "'" + esc + "'";
        switch (esc.charAt(0)) {
            case '-', '?', ':', ',', '[', ']', '{', '}', '#', '&', '*', '!', '|', '>', '\'', '"', '%', '@', '`':
                return "'" + esc + "'";
        }
        for (int i = 1; i < esc.length(); i++) {
            switch (esc.charAt(i)) {
                case ',', '[', ']', '{', '}':
                    return "'" + esc + "'";
            }
        }
        return esc;
    }

    public static void toJson(Node node, Consumer<String> out) {
        toJson(1, true, node, out);
        out.accept(NL);
    }

    private static void toJson(int indent, boolean skipFirstIndent, Node node, Consumer<String> out) {
        switch (node) {
            case LeafNode leaf -> {
                out.accept(quoteAndEscapeJson(leaf.value()));
            }
            case ListNodeImpl list -> {
                out.accept("[");
                boolean first = true;
                switch (list.style()) {
                    case FLOW -> {
                        for (var n : list) {
                            if (first) first = false;
                            else out.accept(", ");
                            toJson(0, false, n, out);
                        }
                    }
                    case BLOCK -> {
                        for (var n : list) {
                            if (first) first = false;
                            else out.accept(",");
                            out.accept(NL + "    ".repeat(indent));
                            toJson(indent + 1, true, n, out);
                        }
                    }
                }
                out.accept("]");
            }
            case MapNodeImpl map -> {
                switch (map.style()) {
                    case FLOW -> {
                        out.accept("{");
                        boolean first = true;
                        for (var n : map.values()) {
                            if (first) first = false;
                            else out.accept(", ");
                            out.accept(quoteAndEscapeJson(n.name().toString()) + ": ");
                            toJson(0, false, n, out);
                        }
                    }
                    case BLOCK -> {
                        if (skipFirstIndent) out.accept("  { ");
                        else out.accept("{");
                        boolean first = true;
                        for (var n : map.values()) {
                            if (first) first = false;
                            else out.accept(",");
                            if (skipFirstIndent) skipFirstIndent = false;
                            else out.accept(NL + "    ".repeat(indent));
                            out.accept(quoteAndEscapeJson(n.name().toString()) + ": ");
                            toJson(indent + 1, false, n, out);
                        }
                    }
                }
                out.accept("}");
            }
        }
    }

    private static String quoteAndEscapeJson(ConstantDesc value) {
        String s = String.valueOf(value);
        if (value instanceof Number) return s;
        var sb = new StringBuilder(s.length() << 1);
        sb.append('"');
        s.chars().forEach(c -> escape(c, sb));
        sb.append('"');
        return sb.toString();
    }

    public static void toXml(Node node, Consumer<String> out) {
        out.accept("<?xml version = '1.0'?>");
        toXml(0, false, node, out);
        out.accept(NL);
    }

    private static void toXml(int indent, boolean skipFirstIndent, Node node, Consumer<String> out) {
        var name = toXmlName(node.name().toString());
        switch (node) {
            case LeafNode leaf -> {
                out.accept("<" + name + ">");
                out.accept(xmlEscape(leaf.value()));
            }
            case ListNodeImpl list -> {
                switch (list.style()) {
                    case FLOW -> {
                        out.accept("<" + name + ">");
                        for (var n : list) {
                            toXml(0, false, n, out);
                        }
                    }
                    case BLOCK -> {
                        if (!skipFirstIndent)
                            out.accept(NL + "    ".repeat(indent));
                        out.accept("<" + name + ">");
                        for (var n : list) {
                            out.accept(NL + "    ".repeat(indent + 1));
                            toXml(indent + 1, true, n, out);
                        }
                    }
                }
            }
            case MapNodeImpl map -> {
                switch (map.style()) {
                    case FLOW -> {
                        out.accept("<" + name + ">");
                        for (var n : map.values()) {
                            toXml(0, false, n, out);
                        }
                    }
                    case BLOCK -> {
                        if (!skipFirstIndent)
                            out.accept(NL + "    ".repeat(indent));
                        out.accept("<" + name + ">");
                        for (var n : map.values()) {
                            out.accept(NL + "    ".repeat(indent + 1));
                            toXml(indent + 1, true, n, out);
                        }
                    }
                }
            }
        }
        out.accept("</" + name + ">");
    }

    private static String xmlEscape(ConstantDesc value) {
        var s = String.valueOf(value);
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

    private static String toXmlName(String name) {
        if (Character.isDigit(name.charAt(0)))
            name = "_" + name;
        return name.replaceAll("[^A-Za-z_0-9]", "_");
    }

    private static Node[] elementValueToTree(AnnotationValue v) {
        return switch (v) {
            case OfString cv -> leafs("string", String.valueOf(cv.constantValue()));
            case OfDouble cv -> leafs("double", String.valueOf(cv.constantValue()));
            case OfFloat cv -> leafs("float", String.valueOf(cv.constantValue()));
            case OfLong cv -> leafs("long", String.valueOf(cv.constantValue()));
            case OfInteger cv -> leafs("int", String.valueOf(cv.constantValue()));
            case OfShort cv -> leafs("short", String.valueOf(cv.constantValue()));
            case OfCharacter cv -> leafs("char", String.valueOf(cv.constantValue()));
            case OfByte cv -> leafs("byte", String.valueOf(cv.constantValue()));
            case OfBoolean cv -> leafs("boolean", String.valueOf((int)cv.constantValue() != 0));
            case OfClass clv -> leafs("class", clv.className().stringValue());
            case OfEnum ev -> leafs("enum class", ev.className().stringValue(),
                                    "constant name", ev.constantName().stringValue());
            case OfAnnotation av -> leafs("annotation class", av.annotation().className().stringValue());
            case OfArray av -> new Node[]{new ListNodeImpl(FLOW, "array", av.values().stream().map(
                    ev -> new MapNodeImpl(FLOW, "value").with(elementValueToTree(ev))))};
        };
    }

    private static Node elementValuePairsToTree(List<AnnotationElement> evps) {
        return new ListNodeImpl(FLOW, "values", evps.stream().map(evp -> new MapNodeImpl(FLOW, "pair").with(
                leaf("name", evp.name().stringValue()),
                new MapNodeImpl(FLOW, "value").with(elementValueToTree(evp.value())))));
    }

    private static Stream<ConstantDesc> convertVTIs(CodeAttribute lr, List<VerificationTypeInfo> vtis) {
        return vtis.stream().mapMulti((vti, ret) -> {
            switch (vti) {
                case SimpleVerificationTypeInfo s -> {
                    switch (s) {
                        case ITEM_DOUBLE -> {
                            ret.accept("double");
                            ret.accept("double2");
                        }
                        case ITEM_FLOAT ->
                            ret.accept("float");
                        case ITEM_INTEGER ->
                            ret.accept("int");
                        case ITEM_LONG ->  {
                            ret.accept("long");
                            ret.accept("long2");
                        }
                        case ITEM_NULL -> ret.accept("null");
                        case ITEM_TOP -> ret.accept("?");
                        case ITEM_UNINITIALIZED_THIS -> ret.accept("THIS");
                    }
                }
                case ObjectVerificationTypeInfo o ->
                    ret.accept(o.className().name().stringValue());
                case UninitializedVerificationTypeInfo u ->
                    ret.accept("UNINITIALIZED @" + lr.labelToBci(u.newTarget()));
            }
        });
    }

    private record ExceptionHandler(int start, int end, int handler, String catchType) {}

    public static MapNode modelToTree(CompoundElement<?> model, Verbosity verbosity) {
        return switch(model) {
            case ClassModel cm -> classToTree(cm, verbosity);
            case FieldModel fm -> fieldToTree(fm, verbosity);
            case MethodModel mm -> methodToTree(mm, verbosity);
            case CodeModel com -> codeToTree((CodeAttribute)com, verbosity);
        };
    }

    private static MapNode classToTree(ClassModel clm, Verbosity verbosity) {
        return new MapNodeImpl(BLOCK, "class")
                .with(leaf("class name", clm.thisClass().asInternalName()),
                      leaf("version", clm.majorVersion() + "." + clm.minorVersion()),
                      list("flags", "flag", clm.flags().flags().stream().map(AccessFlag::name)),
                      leaf("superclass", clm.superclass().map(ClassEntry::asInternalName).orElse("")),
                      list("interfaces", "interface", clm.interfaces().stream().map(ClassEntry::asInternalName)),
                      list("attributes", "attribute", clm.attributes().stream().map(Attribute::attributeName)))
                .with(constantPoolToTree(clm.constantPool(), verbosity))
                .with(attributesToTree(clm.attributes(), verbosity))
                .with(new ListNodeImpl(BLOCK, "fields", clm.fields().stream().map(f ->
                    fieldToTree(f, verbosity))))
                .with(new ListNodeImpl(BLOCK, "methods", clm.methods().stream().map(mm ->
                    methodToTree(mm, verbosity))));
    }

    private static Node[] constantPoolToTree(ConstantPool cp, Verbosity verbosity) {
        if (verbosity == Verbosity.TRACE_ALL) {
            var cpNode = new MapNodeImpl(BLOCK, "constant pool");
            for (PoolEntry e : cp) {
                cpNode.with(new MapNodeImpl(FLOW, e.index())
                        .with(leaf("tag", switch (e.tag()) {
                            case TAG_UTF8 -> "Utf8";
                            case TAG_INTEGER -> "Integer";
                            case TAG_FLOAT -> "Float";
                            case TAG_LONG -> "Long";
                            case TAG_DOUBLE -> "Double";
                            case TAG_CLASS -> "Class";
                            case TAG_STRING -> "String";
                            case TAG_FIELDREF -> "Fieldref";
                            case TAG_METHODREF -> "Methodref";
                            case TAG_INTERFACEMETHODREF -> "InterfaceMethodref";
                            case TAG_NAMEANDTYPE -> "NameAndType";
                            case TAG_METHODHANDLE -> "MethodHandle";
                            case TAG_METHODTYPE -> "MethodType";
                            case TAG_CONSTANTDYNAMIC -> "Dynamic";
                            case TAG_INVOKEDYNAMIC -> "InvokeDynamic";
                            case TAG_MODULE -> "Module";
                            case TAG_PACKAGE -> "Package";
                            default -> throw new AssertionError("Unknown CP tag: " + e.tag());
                        }))
                        .with(switch (e) {
                            case ClassEntry ce -> leafs(
                                "class name index", ce.name().index(),
                                "class internal name", ce.asInternalName());
                            case ModuleEntry me -> leafs(
                                "module name index", me.name().index(),
                                "module name", me.name().stringValue());
                            case PackageEntry pe -> leafs(
                                "package name index", pe.name().index(),
                                "package name", pe.name().stringValue());
                            case StringEntry se -> leafs(
                                    "value index", se.utf8().index(),
                                    "value", se.stringValue());
                            case MemberRefEntry mre -> leafs(
                                    "owner index", mre.owner().index(),
                                    "name and type index", mre.nameAndType().index(),
                                    "owner", mre.owner().name().stringValue(),
                                    "name", mre.name().stringValue(),
                                    "type", mre.type().stringValue());
                            case NameAndTypeEntry nte -> leafs(
                                    "name index", nte.name().index(),
                                    "type index", nte.type().index(),
                                    "name", nte.name().stringValue(),
                                    "type", nte.type().stringValue());
                            case MethodHandleEntry mhe -> leafs(
                                    "reference kind", DirectMethodHandleDesc.Kind.valueOf(mhe.kind()).name(),
                                    "reference index", mhe.reference().index(),
                                    "owner", mhe.reference().owner().asInternalName(),
                                    "name", mhe.reference().name().stringValue(),
                                    "type", mhe.reference().type().stringValue());
                            case MethodTypeEntry mte -> leafs(
                                    "descriptor index", mte.descriptor().index(),
                                    "descriptor", mte.descriptor().stringValue());
                            case DynamicConstantPoolEntry dcpe -> new Node[] {
                                leaf("bootstrap method handle index", dcpe.bootstrap().bootstrapMethod().index()),
                                list("bootstrap method arguments indexes",
                                        "index", dcpe.bootstrap().arguments().stream().map(en -> en.index())),
                                leaf("name and type index", dcpe.nameAndType().index()),
                                leaf("name", dcpe.name().stringValue()),
                                leaf("type", dcpe.type().stringValue())};
                            case AnnotationConstantValueEntry ve -> leafs(
                                "value", String.valueOf(ve.constantValue())
                            );
                        }));
            }
            return new Node[]{cpNode};
        } else {
            return new Node[0];
        }
    }

    private static Node frameToTree(ConstantDesc name, CodeAttribute lr, StackMapFrameInfo f) {
        return new MapNodeImpl(FLOW, name).with(
                list("locals", "item", convertVTIs(lr, f.locals())),
                list("stack", "item", convertVTIs(lr, f.stack())));
    }

    private static MapNode fieldToTree(FieldModel f, Verbosity verbosity) {
        return new MapNodeImpl(BLOCK, "field")
                            .with(leaf("field name", f.fieldName().stringValue()),
                                  list("flags",
                                          "flag", f.flags().flags().stream().map(AccessFlag::name)),
                                  leaf("field type", f.fieldType().stringValue()),
                                  list("attributes",
                                          "attribute", f.attributes().stream().map(Attribute::attributeName)))
                            .with(attributesToTree(f.attributes(), verbosity));
    }

    public static MapNode methodToTree(MethodModel m, Verbosity verbosity) {
        return new MapNodeImpl(BLOCK, "method")
                .with(leaf("method name", m.methodName().stringValue()),
                      list("flags",
                              "flag", m.flags().flags().stream().map(AccessFlag::name)),
                      leaf("method type", m.methodType().stringValue()),
                      list("attributes",
                              "attribute", m.attributes().stream().map(Attribute::attributeName)))
                .with(attributesToTree(m.attributes(), verbosity))
                .with(codeToTree((CodeAttribute)m.code().orElse(null), verbosity));
    }

    private static MapNode codeToTree(CodeAttribute com, Verbosity verbosity) {
        if (verbosity != Verbosity.MEMBERS_ONLY && com != null) {
            var codeNode = new MapNodeImpl(BLOCK, "code");
            codeNode.with(leaf("max stack", com.maxStack()));
            codeNode.with(leaf("max locals", com.maxLocals()));
            codeNode.with(list("attributes",
                    "attribute", com.attributes().stream().map(Attribute::attributeName)));
            var stackMap = new MapNodeImpl(BLOCK, "stack map frames");
            var visibleTypeAnnos = new LinkedHashMap<Integer, List<TypeAnnotation>>();
            var invisibleTypeAnnos = new LinkedHashMap<Integer, List<TypeAnnotation>>();
            List<LocalVariableInfo> locals = List.of();
            for (var attr : com.attributes()) {
                if (attr instanceof StackMapTableAttribute smta) {
                    codeNode.with(stackMap);
                    for (var smf : smta.entries()) {
                        stackMap.with(frameToTree(com.labelToBci(smf.target()), com, smf));
                    }
                } else if (verbosity == Verbosity.TRACE_ALL && attr != null) switch (attr) {
                    case LocalVariableTableAttribute lvta -> {
                        locals = lvta.localVariables();
                        codeNode.with(new ListNodeImpl(BLOCK, "local variables",
                                IntStream.range(0, locals.size()).mapToObj(i -> {
                                    var lv = lvta.localVariables().get(i);
                                    return map(i + 1,
                                        "start", lv.startPc(),
                                        "end", lv.startPc() + lv.length(),
                                        "slot", lv.slot(),
                                        "name", lv.name().stringValue(),
                                        "type", lv.type().stringValue());
                                })));
                    }
                    case LocalVariableTypeTableAttribute lvtta -> {
                        codeNode.with(new ListNodeImpl(BLOCK, "local variable types",
                                IntStream.range(0, lvtta.localVariableTypes().size()).mapToObj(i -> {
                                    var lvt = lvtta.localVariableTypes().get(i);
                                    return map(i + 1,
                                        "start", lvt.startPc(),
                                        "end", lvt.startPc() + lvt.length(),
                                        "slot", lvt.slot(),
                                        "name", lvt.name().stringValue(),
                                        "signature", lvt.signature().stringValue());
                                })));
                    }
                    case LineNumberTableAttribute lnta -> {
                        codeNode.with(new ListNodeImpl(BLOCK, "line numbers",
                                IntStream.range(0, lnta.lineNumbers().size()).mapToObj(i -> {
                                    var ln = lnta.lineNumbers().get(i);
                                    return map(i + 1,
                                        "start", ln.startPc(),
                                        "line number", ln.lineNumber());
                                })));
                    }
                    case CharacterRangeTableAttribute crta -> {
                        codeNode.with(new ListNodeImpl(BLOCK, "character ranges",
                                IntStream.range(0, crta.characterRangeTable().size()).mapToObj(i -> {
                                    var cr = crta.characterRangeTable().get(i);
                                    return map(i + 1,
                                        "start", cr.startPc(),
                                        "end", cr.endPc(),
                                        "range start", cr.characterRangeStart(),
                                        "range end", cr.characterRangeEnd(),
                                        "flags", cr.flags());
                                })));
                    }
                    case RuntimeVisibleTypeAnnotationsAttribute rvtaa ->
                        rvtaa.annotations().forEach(a -> forEachOffset(a, com, (off, an) ->
                                visibleTypeAnnos.computeIfAbsent(off, o -> new LinkedList<>()).add(an)));
                    case RuntimeInvisibleTypeAnnotationsAttribute ritaa ->
                        ritaa.annotations().forEach(a -> forEachOffset(a, com, (off, an) ->
                                invisibleTypeAnnos.computeIfAbsent(off, o -> new LinkedList<>()).add(an)));
                    case Object o -> {}
                }
            }
            codeNode.with(attributesToTree(com.attributes(), verbosity));
            if (!stackMap.containsKey(0)) {
                codeNode.with(new MapNodeImpl(FLOW, "//stack map frame @0").with(
                    list("locals", "item", convertVTIs(com, StackMapDecoder.initFrameLocals(com.parent().get()))),
                    list("stack", "item", Stream.of())));
            }
            var excHandlers = com.exceptionHandlers().stream().map(exc -> new ExceptionHandler(
                    com.labelToBci(exc.tryStart()),
                    com.labelToBci(exc.tryEnd()),
                    com.labelToBci(exc.handler()),
                    exc.catchType().map(ct -> ct.name().stringValue()).orElse(null))).toList();
            int bci = 0;
            for (var coe : com) {
                if (coe instanceof Instruction ins) {
                    var frame = stackMap.get(bci);
                    if (frame != null) {
                        codeNode.with(new MapNodeImpl(FLOW, "//stack map frame @" + bci)
                                .with(((MapNodeImpl)frame).values().toArray(new Node[2])));
                    }
                    var annos = invisibleTypeAnnos.get(bci);
                    if (annos != null) {
                        codeNode.with(typeAnnotationsToTree(FLOW, "//invisible type annotations @" + bci, annos));
                    }
                    annos = visibleTypeAnnos.get(bci);
                    if (annos != null) {
                        codeNode.with(typeAnnotationsToTree(FLOW, "//visible type annotations @" + bci, annos));
                    }
                    for (int i = 0; i < excHandlers.size(); i++) {
                        var exc = excHandlers.get(i);
                        if (exc.start() == bci) {
                            codeNode.with(map("//try block " + (i + 1) + " start",
                                    "start", exc.start(),
                                    "end", exc.end(),
                                    "handler", exc.handler(),
                                    "catch type", exc.catchType()));
                        }
                        if (exc.end() == bci) {
                            codeNode.with(map("//try block " + (i + 1) + " end",
                                    "start", exc.start(),
                                    "end", exc.end(),
                                    "handler", exc.handler(),
                                    "catch type", exc.catchType()));
                        }
                        if (exc.handler() == bci) {
                            codeNode.with(map("//exception handler " + (i + 1) + " start",
                                    "start", exc.start(),
                                    "end", exc.end(),
                                    "handler", exc.handler(),
                                    "catch type", exc.catchType()));
                        }
                    }
                    var in = new MapNodeImpl(FLOW, bci).with(leaf("opcode", ins.opcode().name()));
                    codeNode.with(in);
                    switch (coe) {
                        case IncrementInstruction inc ->  in.with(leafs(
                                "slot", inc.slot(),
                                "const", inc.constant()))
                                .with(localInfoToTree(locals, inc.slot(), bci));
                        case LoadInstruction lv ->  in.with(leaf(
                                "slot", lv.slot()))
                                .with(localInfoToTree(locals, lv.slot(), bci));
                        case StoreInstruction lv ->  in.with(leaf(
                                "slot", lv.slot()))
                                .with(localInfoToTree(locals, lv.slot(), bci));
                        case FieldInstruction fa -> in.with(leafs(
                                "owner", fa.owner().name().stringValue(),
                                "field name", fa.name().stringValue(),
                                "field type", fa.type().stringValue()));
                        case InvokeInstruction inv -> in.with(leafs(
                                "owner", inv.owner().name().stringValue(),
                                "method name", inv.name().stringValue(),
                                "method type", inv.type().stringValue()));
                        case InvokeDynamicInstruction invd -> in.with(leafs(
                                "name", invd.name().stringValue(),
                                "descriptor", invd.type().stringValue(),
                                "kind", invd.bootstrapMethod().kind().name(),
                                "owner", invd.bootstrapMethod().owner().descriptorString(),
                                "method name", invd.bootstrapMethod().methodName(),
                                "invocation type", invd.bootstrapMethod().invocationType().descriptorString()));
                        case NewObjectInstruction newo -> in.with(leaf(
                                "type", newo.className().name().stringValue()));
                        case NewPrimitiveArrayInstruction newa -> in.with(leafs(
                                "dimensions", 1,
                                "descriptor", newa.typeKind().typeName()));
                        case NewReferenceArrayInstruction newa -> in.with(leafs(
                                "dimensions", 1,
                                "descriptor", newa.componentType().name().stringValue()));
                        case NewMultiArrayInstruction newa -> in.with(leafs(
                                "dimensions", newa.dimensions(),
                                "descriptor", newa.arrayType().name().stringValue()));
                        case TypeCheckInstruction tch -> in.with(leaf(
                                "type", tch.type().name().stringValue()));
                        case ConstantInstruction cons -> in.with(leaf(
                                "constant value", cons.constantValue()));
                        case BranchInstruction br -> in.with(leaf(
                                "target", com.labelToBci(br.target())));
                        case LookupSwitchInstruction si -> in.with(list(
                                "targets", "target", Stream.concat(Stream.of(si.defaultTarget())
                                        .map(com::labelToBci), si.cases().stream()
                                                .map(sc -> com.labelToBci(sc.target())))));
                        case TableSwitchInstruction si -> in.with(list(
                                "targets", "target", Stream.concat(Stream.of(si.defaultTarget())
                                        .map(com::labelToBci), si.cases().stream()
                                                .map(sc -> com.labelToBci(sc.target())))));
                        case DiscontinuedInstruction.JsrInstruction jsr -> in.with(leaf(
                                "target", com.labelToBci(jsr.target())));
                        case DiscontinuedInstruction.RetInstruction ret ->  in.with(leaf(
                                "slot", ret.slot()));
                        default -> {}
                    }
                    bci += ins.sizeInBytes();
                }
            }
            if (!excHandlers.isEmpty()) {
                var handlersNode = new MapNodeImpl(BLOCK, "exception handlers");
                codeNode.with(handlersNode);
                for (int i = 0; i < excHandlers.size(); i++) {
                    var exc = excHandlers.get(i);
                    handlersNode.with(map("handler " + (i + 1),
                            "start", exc.start(),
                            "end", exc.end(),
                            "handler", exc.handler(),
                            "type", exc.catchType()));
                }
            }
            return codeNode;
        }
        return null;
    }

    private static Node[] attributesToTree(List<Attribute<?>> attributes, Verbosity verbosity) {
        var nodes = new LinkedList<Node>();
        if (verbosity != Verbosity.MEMBERS_ONLY) for (var attr : attributes) {
            switch (attr) {
                case BootstrapMethodsAttribute bma ->
                    nodes.add(new ListNodeImpl(BLOCK, "bootstrap methods", bma.bootstrapMethods().stream().map(
                    bm -> {
                        var mh = bm.bootstrapMethod();
                        var mref = mh.reference();
                        return map("bm",
                                "kind", DirectMethodHandleDesc.Kind.valueOf(mh.kind(),
                                        mref instanceof InterfaceMethodRefEntry).name(),
                                "owner", mref.owner().name().stringValue(),
                                "name", mref.nameAndType().name().stringValue(),
                                "type", mref.nameAndType().type().stringValue());
                    })));
                case ConstantValueAttribute cva ->
                    nodes.add(leaf("constant value", cva.constant().constantValue()));
                case NestHostAttribute nha ->
                    nodes.add(leaf("nest host", nha.nestHost().name().stringValue()));
                case NestMembersAttribute nma ->
                    nodes.add(list("nest members", "member", nma.nestMembers().stream()
                            .map(mp -> mp.name().stringValue())));
                case PermittedSubclassesAttribute psa ->
                    nodes.add(list("permitted subclasses", "subclass", psa.permittedSubclasses().stream()
                            .map(e -> e.name().stringValue())));
                default -> {}
            }
            if (verbosity == Verbosity.TRACE_ALL) switch (attr) {
                case EnclosingMethodAttribute ema ->
                    nodes.add(map("enclosing method",
                            "class", ema.enclosingClass().name().stringValue(),
                            "method name", ema.enclosingMethodName()
                                    .map(Utf8Entry::stringValue).orElse("null"),
                            "method type", ema.enclosingMethodType()
                                    .map(Utf8Entry::stringValue).orElse("null")));
                case ExceptionsAttribute exa ->
                    nodes.add(list("exceptions", "exc", exa.exceptions().stream()
                            .map(e -> e.name().stringValue())));
                case InnerClassesAttribute ica ->
                    nodes.add(new ListNodeImpl(BLOCK, "inner classes", ica.classes().stream()
                            .map(ic -> new MapNodeImpl(FLOW, "cls").with(
                                leaf("inner class", ic.innerClass().name().stringValue()),
                                leaf("outer class", ic.outerClass()
                                        .map(cle -> cle.name().stringValue()).orElse("null")),
                                leaf("inner name", ic.innerName().map(Utf8Entry::stringValue).orElse("null")),
                                list("flags", "flag", ic.flags().stream().map(AccessFlag::name))))));
                case MethodParametersAttribute mpa -> {
                    var n = new MapNodeImpl(BLOCK, "method parameters");
                    for (int i = 0; i < mpa.parameters().size(); i++) {
                        var p = mpa.parameters().get(i);
                        n.with(new MapNodeImpl(FLOW, i + 1).with(
                                leaf("name", p.name().map(Utf8Entry::stringValue).orElse("null")),
                                list("flags", "flag", p.flags().stream().map(AccessFlag::name))));
                    }
                }
                case ModuleAttribute ma ->
                    nodes.add(new MapNodeImpl(BLOCK, "module")
                            .with(leaf("name", ma.moduleName().name().stringValue()),
                                  list("flags","flag", ma.moduleFlags().stream().map(AccessFlag::name)),
                                  leaf("version", ma.moduleVersion().map(Utf8Entry::stringValue).orElse("null")),
                                  list("uses", "class", ma.uses().stream().map(ce -> ce.name().stringValue())),
                                  new ListNodeImpl(BLOCK, "requires", ma.requires().stream().map(req ->
                                    new MapNodeImpl(FLOW, "req").with(
                                            leaf("name", req.requires().name().stringValue()),
                                            list("flags", "flag", req.requiresFlags().stream()
                                                    .map(AccessFlag::name)),
                                            leaf("version", req.requiresVersion()
                                                    .map(Utf8Entry::stringValue).orElse(null))))),
                                  new ListNodeImpl(BLOCK, "exports", ma.exports().stream().map(exp ->
                                    new MapNodeImpl(FLOW, "exp").with(
                                            leaf("package", exp.exportedPackage().asSymbol().name()),
                                            list("flags", "flag", exp.exportsFlags().stream()
                                                    .map(AccessFlag::name)),
                                            list("to", "module", exp.exportsTo().stream()
                                                    .map(me -> me.name().stringValue()))))),
                                  new ListNodeImpl(BLOCK, "opens", ma.opens().stream().map(opn ->
                                    new MapNodeImpl(FLOW, "opn").with(
                                            leaf("package", opn.openedPackage().asSymbol().name()),
                                            list("flags", "flag", opn.opensFlags().stream()
                                                    .map(AccessFlag::name)),
                                            list("to", "module", opn.opensTo().stream()
                                                    .map(me -> me.name().stringValue()))))),
                                  new ListNodeImpl(BLOCK, "provides", ma.provides().stream()
                                          .map(prov -> new MapNodeImpl(FLOW, "prov").with(
                                                  leaf("class", prov.provides().name().stringValue()),
                                                  list("with", "cls", prov.providesWith().stream()
                                                          .map(ce -> ce.name().stringValue())))))));
                case ModulePackagesAttribute mopa ->
                    nodes.add(list("module packages", "subclass", mopa.packages().stream()
                            .map(mp -> mp.asSymbol().name())));
                case ModuleMainClassAttribute mmca ->
                    nodes.add(leaf("module main class", mmca.mainClass().name().stringValue()));
                case RecordAttribute ra ->
                    nodes.add(new ListNodeImpl(BLOCK, "record components", ra.components().stream()
                            .map(rc -> new MapNodeImpl(BLOCK, "record")
                                    .with(leafs(
                                        "name", rc.name().stringValue(),
                                        "type", rc.descriptor().stringValue()))
                                    .with(list("attributes", "attribute", rc.attributes().stream()
                                            .map(Attribute::attributeName)))
                                    .with(attributesToTree(rc.attributes(), verbosity)))));
                case AnnotationDefaultAttribute ada ->
                    nodes.add(new MapNodeImpl(FLOW, "annotation default").with(elementValueToTree(ada.defaultValue())));
                case RuntimeInvisibleAnnotationsAttribute aa ->
                    nodes.add(annotationsToTree("invisible annotations", aa.annotations()));
                case RuntimeVisibleAnnotationsAttribute aa ->
                    nodes.add(annotationsToTree("visible annotations", aa.annotations()));
                case RuntimeInvisibleParameterAnnotationsAttribute aa ->
                    nodes.add(parameterAnnotationsToTree("invisible parameter annotations", aa.parameterAnnotations()));
                case RuntimeVisibleParameterAnnotationsAttribute aa ->
                    nodes.add(parameterAnnotationsToTree("visible parameter annotations", aa.parameterAnnotations()));
                case RuntimeInvisibleTypeAnnotationsAttribute aa ->
                    nodes.add(typeAnnotationsToTree(BLOCK, "invisible type annotations", aa.annotations()));
                case RuntimeVisibleTypeAnnotationsAttribute aa ->
                    nodes.add(typeAnnotationsToTree(BLOCK, "visible type annotations", aa.annotations()));
                case SignatureAttribute sa ->
                    nodes.add(leaf("signature", sa.signature().stringValue()));
                case SourceFileAttribute sfa ->
                    nodes.add(leaf("source file", sfa.sourceFile().stringValue()));
                default -> {}
            }
        }
        return nodes.toArray(Node[]::new);
    }

    private static Node annotationsToTree(String name, List<Annotation> annos) {
        return new ListNodeImpl(BLOCK, name, annos.stream().map(a ->
                new MapNodeImpl(FLOW, "anno")
                        .with(leaf("annotation class", a.className().stringValue()))
                        .with(elementValuePairsToTree(a.elements()))));

    }

    private static Node typeAnnotationsToTree(Style style, String name, List<TypeAnnotation> annos) {
        return new ListNodeImpl(style, name, annos.stream().map(a ->
                new MapNodeImpl(FLOW, "anno")
                        .with(leaf("annotation class", a.className().stringValue()),
                              leaf("target info", a.targetInfo().targetType().name()))
                        .with(elementValuePairsToTree(a.elements()))));

    }

    private static MapNodeImpl parameterAnnotationsToTree(String name, List<List<Annotation>> paramAnnotations) {
        var node = new MapNodeImpl(BLOCK, name);
        for (int i = 0; i < paramAnnotations.size(); i++) {
            var annos = paramAnnotations.get(i);
            if (!annos.isEmpty()) {
                node.with(new ListNodeImpl(FLOW, "parameter " + (i + 1), annos.stream().map(a ->
                                new MapNodeImpl(FLOW, "anno")
                                        .with(leaf("annotation class", a.className().stringValue()))
                                        .with(elementValuePairsToTree(a.elements())))));
            }
        }
        return node;
    }

    private static Node[] localInfoToTree(List<LocalVariableInfo> locals, int slot, int bci) {
        if (locals != null) {
            for (var l : locals) {
                if (l.slot() == slot && l.startPc() <= bci && l.length() + l.startPc() >= bci) {
                    return leafs("type", l.type().stringValue(),
                                 "variable name", l.name().stringValue());
                }
            }
        }
        return new Node[0];
    }

    private static void forEachOffset(TypeAnnotation ta, CodeAttribute lr, BiConsumer<Integer, TypeAnnotation> consumer) {
        switch (ta.targetInfo()) {
            case TypeAnnotation.OffsetTarget ot ->
                consumer.accept(lr.labelToBci(ot.target()), ta);
            case TypeAnnotation.TypeArgumentTarget tat ->
                consumer.accept(lr.labelToBci(tat.target()), ta);
            case TypeAnnotation.LocalVarTarget lvt ->
                lvt.table().forEach(lvti -> consumer.accept(lr.labelToBci(lvti.startLabel()), ta));
            default -> {}
        }
    }
}
