/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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

package propertiesparser.gen;

import propertiesparser.parser.Message;
import propertiesparser.parser.MessageFile;
import propertiesparser.parser.MessageInfo;
import propertiesparser.parser.MessageLine;
import propertiesparser.parser.MessageType;
import propertiesparser.parser.MessageType.CompoundType;
import propertiesparser.parser.MessageType.CustomType;
import propertiesparser.parser.MessageType.SimpleType;
import propertiesparser.parser.MessageType.UnionType;
import propertiesparser.parser.MessageType.Visitor;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ClassGenerator {

    /** Empty string - used to generate indentation padding. */
    private final static String INDENT_STRING = "                                                                   ";

    /** Default indentation step. */
    private final static int INDENT_WIDTH = 4;

    /** File-backed property file containing basic code stubs. */
    static Properties stubs;

    static {
        //init properties from file
        stubs = new Properties();
        String resourcePath = "/propertiesparser/resources/templates.properties";
        try (InputStream in = ClassGenerator.class.getResourceAsStream(resourcePath)) {
            stubs.load(in);
        } catch (IOException ex) {
            throw new AssertionError(ex);
        }
    }

    /**
     * Supported stubs in the property file.
     */
    enum StubKind {
        TOPLEVEL("toplevel.decl"),
        FACTORY_CLASS("nested.decl"),
        IMPORT("import.decl"),
        FACTORY_METHOD_DECL("factory.decl.method"),
        FACTORY_METHOD_ARG("factory.decl.method.arg"),
        FACTORY_METHOD_BODY("factory.decl.method.body"),
        FACTORY_FIELD("factory.decl.field"),
        FACTORY_METHOD_CONVERT_DIAG("factory.decl.convert.diag.method"),
        FACTORY_METHOD_CONVERT_THROWS("factory.decl.convert.throws"),
        FACTORY_METHOD_CONVERT_IDENTITY("factory.decl.convert.identity"),
        FACTORY_METHOD_CONVERT_ACCESS_METHOD("factory.decl.convert.access.method"),
        FACTORY_METHOD_CONVERT_ACCESS_FIELD("factory.decl.convert.access.field"),
        WILDCARDS_EXTENDS("wildcards.extends"),
        SUPPRESS_WARNINGS("suppress.warnings");

        /** stub key (as it appears in the property file) */
        String key;

        StubKind(String key) {
            this.key = key;
        }

        /**
         * Subst a list of arguments into a given stub.
         */
        String format(Object... args) {
            return MessageFormat.format((String)stubs.get(key), args);
        }
    }

    /**
     * Nested factory class kind. There are multiple sub-factories, one for each kind of commonly used
     * diagnostics (i.e. error, warnings, note, fragment). An additional category is defined for
     * those resource keys whose prefix doesn't match any predefined category.
     */
    enum FactoryKind {
        ERR("err", "Error", "Errors"),
        WARN("warn", "Warning", "Warnings"),
        MISC("misc", "Fragment", "Fragments"),
        NOTE("note", "Note", "Notes"),
        OTHER(null, null, null);

        /** The prefix for this factory kind (i.e. 'err'). */
        String prefix;

        /** The type of the factory method/fields in this class. */
        String keyClazz;

        /** The class name to be used for this factory. */
        String factoryClazz;

        FactoryKind(String prefix, String keyClazz, String factoryClazz) {
            this.prefix = prefix;
            this.keyClazz = keyClazz;
            this.factoryClazz = factoryClazz;
        }

        /**
         * Utility method for parsing a factory kind from a resource key prefix.
         */
        static FactoryKind parseFrom(String prefix) {
            for (FactoryKind k : FactoryKind.values()) {
                if (k.prefix == null || k.prefix.equals(prefix)) {
                    return k;
                }
            }
            return null;
        }
    }

    /**
     * Main entry-point: generate a Java enum-like set of nested factory classes into given output
     * folder. The factories are populated as mandated by the comments in the input resource file.
     */
    public void generateFactory(MessageFile messageFile, File outDir) {
        MessageIndex messageIndex = new MessageIndex(messageFile);
        //generate nested classes
        List<String> nestedDecls = new ArrayList<>();
        Set<String> importedTypes = new TreeSet<>();
        for (FactoryKind kind : FactoryKind.values()) {
            if (kind == FactoryKind.OTHER) continue;
            String members = "";
            for (MessageIndex.Entry entry : messageIndex.getEntries(kind)) {
                //emit members
                members += String.join("\n\n", generateFactoryMethodsAndFields(messageIndex, entry));
                //add imports
                importedTypes.addAll(importedTypes(entry.message().getMessageInfo().getTypes()));
            }
            //emit nested class
            String factoryDecl =
                    StubKind.FACTORY_CLASS.format(kind.factoryClazz, indent(members));
            nestedDecls.add(indent(factoryDecl));
        }
        String clazz = StubKind.TOPLEVEL.format(
                packageName(messageFile.file),
                String.join("\n", generateImports(importedTypes)),
                toplevelName(messageFile.file),
                String.join("\n", nestedDecls));
        try (FileWriter fw = new FileWriter(new File(outDir, toplevelName(messageFile.file) + ".java"))) {
            fw.append(clazz);
        } catch (Throwable ex) {
            throw new AssertionError(ex);
        }
    }

    /**
     * Indent a string one level deeper.
     */
    String indent(String s) {
        return indent(s, 1);
    }

    /**
     * Indent a string to a given depth level.
     */
    String indent(String s, int level) {
        return Stream.of(s.split("\n"))
                .map(sub -> INDENT_STRING.substring(0, level * INDENT_WIDTH) + sub)
                .collect(Collectors.joining("\n"));
    }

    /**
     * Retrieve package part of given file object.
     */
    String packageName(File file) {
        String path = file.getAbsolutePath();
        int begin = path.lastIndexOf(File.separatorChar + "com" + File.separatorChar);
        String packagePath = path.substring(begin + 1, path.lastIndexOf(File.separatorChar));
        String packageName =  packagePath.replace(File.separatorChar, '.');
        return packageName;
    }

    /**
     * Form the name of the toplevel factory class.
     */
    public static String toplevelName(File file) {
        return Stream.of(file.getName().split("\\."))
                .map(s -> Character.toUpperCase(s.charAt(0)) + s.substring(1))
                .collect(Collectors.joining(""));
    }

    /**
     * Generate a list of import declarations given a set of imported types.
     */
    List<String> generateImports(Set<String> importedTypes) {
        List<String> importDecls = new ArrayList<>();
        for (String it : importedTypes) {
            importDecls.add(StubKind.IMPORT.format(it));
        }
        return importDecls;
    }

    /**
     * Generate a list of factory methods/fields to be added to a given factory nested class.
     */
    List<String> generateFactoryMethodsAndFields(MessageIndex index, MessageIndex.Entry entry) {
        MessageInfo msgInfo = entry.message().getMessageInfo();
        List<MessageLine> lines = entry.message().getLines(false);
        String javadoc = lines.stream()
                .filter(ml -> !ml.isInfo() && !ml.isEmptyOrComment())
                .map(ml -> ml.text)
                .collect(Collectors.joining("\n *"));
        String factoryName = factoryName(entry.key());
        if (msgInfo.getTypes().isEmpty()) {
            //generate field
            String body = generateDiagnosticConversionMethodIfNeeded(index, entry.kind, entry, factoryName, null);
            String factoryField = StubKind.FACTORY_FIELD.format(entry.kind.keyClazz, factoryName,
                    "\"" + entry.prefix + "\"",
                    "\"" + entry.key + "\"",
                    javadoc, body);
            return Collections.singletonList(factoryField);
        } else {
            //generate method
            List<String> factoryMethods = new ArrayList<>();
            for (List<MessageType> msgTypes : normalizeTypes(0, msgInfo.getTypes())) {
                List<String> types = generateTypes(msgTypes);
                List<String> argNames = argNames(types.size());
                String body = generateDiagnosticConversionMethodIfNeeded(index, entry.kind, entry, factoryName, argNames);
                String suppressionString = needsSuppressWarnings(msgTypes) ?
                        StubKind.SUPPRESS_WARNINGS.format() : "";
                String factoryMethod = StubKind.FACTORY_METHOD_DECL.format(suppressionString, entry.kind.keyClazz,
                        factoryName, argDecls(types, argNames).stream().collect(Collectors.joining(", ")),
                        indent(StubKind.FACTORY_METHOD_BODY.format(entry.kind.keyClazz,
                                "\"" + entry.prefix + "\"",
                                "\"" + entry.key + "\"",
                                argNames.stream().collect(Collectors.joining(", ")), body)),
                        javadoc);
                factoryMethods.add(factoryMethod);
            }
            return factoryMethods;
        }
    }

    String generateDiagnosticConversionMethodIfNeeded(MessageIndex index, FactoryKind thisKind, MessageIndex.Entry entry,
                                                      String factoryName, List<String> argNames) {
        List<MessageIndex.Entry> duplicates = index.findOverloadedDiags(entry);
        if (duplicates.isEmpty()) {
            return "";
        }
        List<String> clauses = new ArrayList<>(FactoryKind.values().length);
        for (FactoryKind kind : FactoryKind.values()) {
            if (kind == thisKind) {
                clauses.add(StubKind.FACTORY_METHOD_CONVERT_IDENTITY.format());
            } else if (duplicates.stream().anyMatch(e -> e.kind == kind)) {
                if (argNames == null) {
                    // field
                    clauses.add(StubKind.FACTORY_METHOD_CONVERT_ACCESS_FIELD.format(
                            kind.factoryClazz, factoryName));
                } else {
                    // method
                    clauses.add(StubKind.FACTORY_METHOD_CONVERT_ACCESS_METHOD.format(
                            kind.factoryClazz, factoryName,
                            argNames.stream().collect(Collectors.joining(", "))));
                }
            } else {
                clauses.add(StubKind.FACTORY_METHOD_CONVERT_THROWS.format());
            }
        }
        return " {\n" + indent(StubKind.FACTORY_METHOD_CONVERT_DIAG.format(
                clauses.get(0), clauses.get(1), clauses.get(2), clauses.get(3))) + "\n}";
    }

    /**
     * Form the name of a factory method/field given a resource key.
     */
    String factoryName(String key) {
        return Stream.of(key.split("[\\.-]"))
                .map(s -> Character.toUpperCase(s.charAt(0)) + s.substring(1))
                .collect(Collectors.joining(""));
    }

    /**
     * Generate a formal parameter list given a list of types and names.
     */
    List<String> argDecls(List<String> types, List<String> args) {
        List<String> argNames = new ArrayList<>();
        for (int i = 0 ; i < types.size() ; i++) {
            argNames.add(types.get(i) + " " + args.get(i));
        }
        return argNames;
    }

    /**
     * Generate a list of formal parameter names given a size.
     */
    List<String> argNames(int size) {
        List<String> argNames = new ArrayList<>();
        for (int i = 0 ; i < size ; i++) {
            argNames.add(StubKind.FACTORY_METHOD_ARG.format(i));
        }
        return argNames;
    }

    /**
     * Convert a (normalized) parsed type into a string-based representation of some Java type.
     */
    List<String> generateTypes(List<MessageType> msgTypes) {
        return msgTypes.stream().map(t -> t.accept(stringVisitor, null)).collect(Collectors.toList());
    }
    //where
        Visitor<String, Void> stringVisitor = new Visitor<String, Void>() {
            @Override
            public String visitCustomType(CustomType t, Void aVoid) {
                String customType = t.typeString;
                return customType.substring(customType.lastIndexOf('.') + 1);
            }

            @Override
            public String visitSimpleType(SimpleType t, Void aVoid) {
                return t.clazz;
            }

            @Override
            public String visitCompoundType(CompoundType t, Void aVoid) {
                return StubKind.WILDCARDS_EXTENDS.format(t.kind.clazz.clazz,
                        t.elemtype.accept(this, null));
            }

            @Override
            public String visitUnionType(UnionType t, Void aVoid) {
                throw new AssertionError("Union types should have been denormalized!");
            }
        };

    /**
     * See if any of the parsed types in the given list needs warning suppression.
     */
    boolean needsSuppressWarnings(List<MessageType> msgTypes) {
        return msgTypes.stream().anyMatch(t -> t.accept(suppressWarningsVisitor, null));
    }
    //where
    Visitor<Boolean, Void> suppressWarningsVisitor = new Visitor<Boolean, Void>() {
        @Override
        public Boolean visitCustomType(CustomType t, Void aVoid) {
            //play safe
            return true;
        }
        @Override
        public Boolean visitSimpleType(SimpleType t, Void aVoid) {
            switch (t) {
                case LIST:
                case SET:
                    return true;
                default:
                    return false;
            }
        }

        @Override
        public Boolean visitCompoundType(CompoundType t, Void aVoid) {
            return t.elemtype.accept(this, null);
        }

        @Override
        public Boolean visitUnionType(UnionType t, Void aVoid) {
            return needsSuppressWarnings(Arrays.asList(t.choices));
        }
    };

    /**
     * Retrieve a list of types that need to be imported, so that the factory body can refer
     * to the types in the given list using simple names.
     */
    Set<String> importedTypes(List<MessageType> msgTypes) {
        Set<String> imports = new TreeSet<>();
        msgTypes.forEach(t -> t.accept(importVisitor, imports));
        return imports;
    }
    //where
    Visitor<Void, Set<String>> importVisitor = new Visitor<Void, Set<String>>() {
        @Override
        public Void visitCustomType(CustomType t, Set<String> imports) {
            imports.add(t.typeString);
            return null;
        }

        @Override
        public Void visitSimpleType(SimpleType t, Set<String> imports) {
            if (t.qualifier != null) {
                imports.add(t.qualifier + "." + t.clazz);
            }
            return null;
        }

        @Override
        public Void visitCompoundType(CompoundType t, Set<String> imports) {
            visitSimpleType(t.kind.clazz, imports);
            t.elemtype.accept(this, imports);
            return null;
        }

        @Override
        public Void visitUnionType(UnionType t, Set<String> imports) {
            Stream.of(t.choices).forEach(c -> c.accept(this, imports));
            return null;
        }
    };

    /**
     * Normalize parsed types in a comment line. If one or more types in the line contains alternatives,
     * this routine generate a list of 'overloaded' normalized signatures.
     */
    List<List<MessageType>> normalizeTypes(int idx, List<MessageType> msgTypes) {
        if (msgTypes.size() == idx) return Collections.singletonList(Collections.emptyList());
        MessageType head = msgTypes.get(idx);
        List<List<MessageType>> buf = new ArrayList<>();
        for (MessageType alternative : head.accept(normalizeVisitor, null)) {
            for (List<MessageType> rest : normalizeTypes(idx + 1, msgTypes)) {
                List<MessageType> temp = new ArrayList<>(rest);
                temp.add(0, alternative);
                buf.add(temp);
            }
        }
        return buf;
    }
    //where
    Visitor<List<MessageType>, Void> normalizeVisitor = new Visitor<List<MessageType>, Void>() {
        @Override
        public List<MessageType> visitCustomType(CustomType t, Void aVoid) {
            return Collections.singletonList(t);
        }

        @Override
        public List<MessageType> visitSimpleType(SimpleType t, Void aVoid) {
            return Collections.singletonList(t);
        }

        @Override
        public List<MessageType> visitCompoundType(CompoundType t, Void aVoid) {
            return t.elemtype.accept(this, null).stream()
                    .map(nt -> new CompoundType(t.kind, nt))
                    .collect(Collectors.toList());
        }

        @Override
        public List<MessageType> visitUnionType(UnionType t, Void aVoid) {
            return Stream.of(t.choices)
                    .flatMap(t2 -> t2.accept(this, null).stream())
                    .collect(Collectors.toList());
        }
    };

    static class MessageIndex {
        private final Map<String, List<Entry>> groupedEntries;

        MessageIndex(MessageFile messageFile) {
            groupedEntries = messageFile.messages.entrySet().stream()
                    .map(e -> Entry.fromKey(e.getKey(), e.getValue()))
                    .collect(Collectors.groupingBy(Entry::key));
        }

        record Entry(FactoryKind kind, String prefix, String key, Message message) {
            static Entry fromKey(String key, Message message) {
                String[] keyParts = key.split("\\.");
                String prefix = keyParts[0];
                FactoryKind kind = FactoryKind.parseFrom(keyParts[1]);
                String rest = Stream.of(keyParts).skip(2)
                        .collect(Collectors.joining("."));
                return new Entry(kind, prefix, rest, message);
            }
        }

        List<Entry> getEntries(FactoryKind kind) {
            return groupedEntries.values().stream()
                    .flatMap(e -> e.stream())
                    .filter(entry -> entry.kind() == kind).toList();
        }

        List<Entry> findOverloadedDiags(Entry entry) {
            return groupedEntries.get(entry.key).stream()
                    .filter(e -> e.kind != entry.kind &&
                            e.message.getMessageInfo().toString().equals(entry.message.getMessageInfo().toString())).toList();
        }
    }
}
