/*
 * Copyright (c) 2007, 2024, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.javap;

import java.lang.reflect.AccessFlag;
import java.net.URI;
import java.text.DateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import java.lang.constant.ClassDesc;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.lang.classfile.AccessFlags;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassModel;
import java.lang.classfile.ClassSignature;
import java.lang.classfile.ClassFile;
import static java.lang.classfile.ClassFile.*;
import java.lang.classfile.ClassHierarchyResolver;
import java.lang.classfile.constantpool.*;
import java.lang.classfile.FieldModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.MethodSignature;
import java.lang.classfile.Signature;
import java.lang.classfile.attribute.CodeAttribute;
import java.lang.classfile.attribute.SignatureAttribute;

/*
 *  The main javap class to write the contents of a class file as text.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class ClassWriter extends BasicWriter {
    static ClassWriter instance(Context context) {
        ClassWriter instance = context.get(ClassWriter.class);
        if (instance == null)
            instance = new ClassWriter(context);
        return instance;
    }

    protected ClassWriter(Context context) {
        super(context);
        context.put(ClassWriter.class, this);
        options = Options.instance(context);
        attrWriter = AttributeWriter.instance(context);
        codeWriter = CodeWriter.instance(context);
        constantWriter = ConstantWriter.instance(context);
        sigPrinter = new SignaturePrinter(options.verbose);
    }

    void setDigest(String name, byte[] digest) {
        this.digestName = name;
        this.digest = digest;
    }

    void setFile(URI uri) {
        this.uri = uri;
    }

    void setFileSize(int size) {
        this.size = size;
    }

    void setLastModified(long lastModified) {
        this.lastModified = lastModified;
    }

    protected ClassModel getClassModel() {
        return classModel;
    }

    protected void setClassFile(ClassModel cm) {
        classModel = cm;
    }

    protected MethodModel getMethod() {
        return method;
    }

    protected void setMethod(MethodModel m) {
        method = m;
    }

    public boolean write(ClassModel cm) {
        errorReported = false;
        setClassFile(cm);

        if (options.sysInfo || options.verbose) {
            if (uri != null) {
                if (uri.getScheme().equals("file"))
                    println("Classfile " + uri.getPath());
                else
                    println("Classfile " + uri);
            }
            indent(+1);
            if (lastModified != -1) {
                Date lm = new Date(lastModified);
                DateFormat df = DateFormat.getDateInstance();
                if (size > 0) {
                    println("Last modified " + df.format(lm) + "; size " + size
                            + " bytes");
                } else {
                    println("Last modified " + df.format(lm));
                }
            } else if (size > 0) {
                println("Size " + size + " bytes");
            }
            if (digestName != null && digest != null) {
                StringBuilder sb = new StringBuilder();
                for (byte b: digest)
                    sb.append(String.format("%02x", b));
                println(digestName + " checksum " + sb);
            }
        }

        cm.findAttribute(Attributes.sourceFile()).ifPresent(sfa ->
            println("Compiled from \"" + sfa.sourceFile().stringValue() + "\""));

        if (options.sysInfo || options.verbose) {
            indent(-1);
        }

        writeModifiers(getClassModifiers(cm.flags()));

        if ((classModel.flags().flagsMask() & ACC_MODULE) != 0) {
            var attr = classModel.findAttribute(Attributes.module());
            if (attr.isPresent()) {
                var modAttr = attr.get();
                if ((modAttr.moduleFlagsMask() & ACC_OPEN) != 0) {
                    print("open ");
                }
                print("module ");
                print(() -> modAttr.moduleName().name().stringValue());
                if (modAttr.moduleVersion().isPresent()) {
                    print("@");
                    print(() -> modAttr.moduleVersion().get().stringValue());
                }
            } else {
                // fallback for malformed class files
                print("class ");
                print(() -> getJavaName(classModel.thisClass().asInternalName()));
            }
        } else {
            if ((classModel.flags().flagsMask() & ACC_INTERFACE) == 0)
                print("class ");
            else
                print("interface ");

            print(() -> getJavaName(classModel.thisClass().asInternalName()));
        }

        try {
            var sigAttr = classModel.findAttribute(Attributes.signature()).orElse(null);
            if (sigAttr == null) {
                // use info from class file header
                if ((classModel.flags().flagsMask() & ACC_INTERFACE) == 0
                        && classModel.superclass().isPresent()) {
                    String sn = getJavaName(classModel.superclass().get().asInternalName());
                    if (!sn.equals("java.lang.Object")) {
                        print(" extends ");
                        print(sn);
                    }
                }
                var interfaces = classModel.interfaces();
                for (int i = 0; i < interfaces.size(); i++) {
                    print(i == 0 ? ((classModel.flags().flagsMask() & ACC_INTERFACE) == 0
                            ? " implements " : " extends ") : ",");
                    print(getJavaName(interfaces.get(i).asInternalName()));
                }
            } else {
                var t = sigAttr.asClassSignature();
                print(sigPrinter.print(t, (classModel.flags().flagsMask() & ACC_INTERFACE) != 0));
            }
        } catch (IllegalArgumentException e) {
            report(e);
        }

        if (options.verbose) {
            println();
            indent(+1);
            println("minor version: " + classModel.minorVersion());
            println("major version: " + classModel.majorVersion());
            writeList(String.format("flags: (0x%04x) ", cm.flags().flagsMask()),
                    getClassFlags(cm.flags()), "\n");
            print("this_class: #");print(() -> classModel.thisClass().index());
            tab();
            print(() -> "// " + classModel.thisClass().asInternalName());
            println();
            print("super_class: #");print(() -> classModel.superclass()
                    .map(ClassEntry::index).orElse(0));
            try {
                if (classModel.superclass().isPresent()) {
                    tab();
                    print(() -> "// " + classModel.superclass().get().asInternalName());
                }
            } catch (IllegalArgumentException e) {
                report(e);
            }
            println();
            print("interfaces: ");print(() -> classModel.interfaces().size());
            print(", fields: " + classModel.fields().size());
            print(", methods: " + classModel.methods().size());
            println(", attributes: " + classModel.attributes().size());
            indent(-1);
            constantWriter.writeConstantPool();
        } else {
            print(" ");
        }

        println("{");
        indent(+1);
        if ((cm.flags().flagsMask() & ACC_MODULE) != 0 && !options.verbose) {
            writeDirectives();
        }
        writeFields();
        writeMethods();
        indent(-1);
        println("}");

        if (options.verbose) {
            attrWriter.write(classModel.attributes());
        }

        if (options.verify) {
            var vErrors = VERIFIER.verify(classModel);
            if (!vErrors.isEmpty()) {
                println();
                for (var ve : vErrors) {
                    println(ve.getMessage());
                }
                errorReported = true;
            }
        }
        return !errorReported;
    }
    // where

    private static final ClassFile VERIFIER = ClassFile.of(ClassFile.ClassHierarchyResolverOption.of(
            ClassHierarchyResolver.defaultResolver().orElse(new ClassHierarchyResolver() {
                @Override
                public ClassHierarchyResolver.ClassHierarchyInfo getClassInfo(ClassDesc classDesc) {
                    // mark all unresolved classes as interfaces to exclude them from assignability verification
                    return ClassHierarchyInfo.ofInterface();
                }
            })));

    final SignaturePrinter sigPrinter;

    public static record SignaturePrinter(boolean verbose) {

        public String print(ClassSignature cs, boolean isInterface) {
            var sb = new StringBuilder();
            print(sb, cs.typeParameters());
            if (isInterface) {
                String sep = " extends ";
                for (var is : cs.superinterfaceSignatures()) {
                    sb.append(sep);
                    print(sb, is);
                    sep = ", ";
                }
            } else {
                if (cs.superclassSignature() != null
                        && (verbose || !isObject(cs.superclassSignature()))) {
                    sb.append(" extends ");
                    print(sb, cs.superclassSignature());
                }
                String sep = " implements ";
                for (var is : cs.superinterfaceSignatures()) {
                    sb.append(sep);
                    print(sb, is);
                    sep = ", ";
                }
            }
            return sb.toString();
        }

        public String print(Signature sig) {
            var sb = new StringBuilder();
            print(sb, sig);
            return sb.toString();
        }

        public String printTypeParams(List<Signature.TypeParam> tps) {
            var sb = new StringBuilder();
            print(sb, tps);
            return sb.toString();
        }

        public String printList(String prefix, List<? extends Signature> args,
                String postfix) {
            var sb = new StringBuilder();
            sb.append(prefix);
            String sep = "";
            for (var arg : args) {
                sb.append(sep);
                print(sb, arg);
                sep = ", ";
            }
            return sb.append(postfix).toString();
        }

        private boolean isObject(Signature sig) {
            return (sig instanceof Signature.ClassTypeSig cts)
                    && cts.outerType().isEmpty()
                    && cts.className().equals("java/lang/Object")
                    && (cts.typeArgs().isEmpty());
        }

        private void print(StringBuilder sb, List<Signature.TypeParam> tps) {
            if (!tps.isEmpty()) {
                sb.append('<');
                String sep = "";
                for (var tp : tps) {
                    sb.append(sep).append(tp.identifier());
                    sep = " extends ";
                    if (tp.classBound().isPresent()
                            && (verbose || !isObject(tp.classBound().get()))) {
                        sb.append(sep);
                        print(sb, tp.classBound().get());
                        sep = " & ";
                    }
                    for (var bound: tp.interfaceBounds()) {
                        sb.append(sep);
                        print(sb, bound);
                        sep = " & ";
                    }
                    sep = ", ";
                }
                sb.append('>');
            }
        }

        private void print(StringBuilder sb, Signature sig) {
            if (sig instanceof Signature.BaseTypeSig bts) {
                    sb.append(ClassDesc.ofDescriptor("" + bts.baseType()).displayName());
            } else if (sig instanceof Signature.ClassTypeSig cts) {
                if (cts.outerType().isPresent()) {
                    print(sb, cts.outerType().get());
                    sb.append(".");
                }
                sb.append(getJavaName(cts.className()));
                if (!cts.typeArgs().isEmpty()) {
                    String sep = "";
                    sb.append('<');
                    for (var ta : cts.typeArgs()) {
                        sb.append(sep);
                        print(sb, ta);
                        sep = ", ";
                    }
                    sb.append('>');
                }
            } else if (sig instanceof Signature.TypeVarSig tvs) {
                sb.append(tvs.identifier());
            } else if (sig instanceof Signature.ArrayTypeSig ats) {
                print(sb, ats.componentSignature());
                sb.append("[]");
            }
        }

        private void print(StringBuilder sb, Signature.TypeArg ta) {
            switch (ta) {
                case Signature.TypeArg.Unbounded _ -> sb.append('?');
                case Signature.TypeArg.Bounded bta -> {
                    switch (bta.wildcardIndicator()) {
                        case NONE -> print(sb, bta.boundType());
                        case EXTENDS -> {
                            sb.append("? extends ");
                            print(sb, bta.boundType());
                        }
                        case SUPER -> {
                            sb.append("? super ");
                            print(sb, bta.boundType());
                        }
                    }
                }
            }
        }
    }

    protected void writeFields() {
        for (var f: classModel.fields()) {
            writeField(f);
        }
    }

    protected void writeField(FieldModel f) {
        if (!options.checkAccess(f.flags().flagsMask()))
            return;

        var flags = f.flags();
        writeModifiers(flagsReportUnknown(flags).stream().filter(fl -> fl.sourceModifier())
                .map(fl -> Modifier.toString(fl.mask())).toList());
        print(() -> sigPrinter.print(
                f.findAttribute(Attributes.signature())
                        .map(SignatureAttribute::asTypeSignature)
                        .orElseGet(() -> Signature.of(f.fieldTypeSymbol()))));
        print(" ");
        print(() -> f.fieldName().stringValue());
        if (options.showConstants) {
            var a = f.findAttribute(Attributes.constantValue());
            if (a.isPresent()) {
                print(" = ");
                var cv = a.get();
                print(() -> getConstantValue(f.fieldTypeSymbol(), cv.constant()));
            }
        }
        print(";");
        println();

        indent(+1);

        boolean showBlank = false;

        if (options.showDescriptors) {
            print("descriptor: ");println(() -> f.fieldType().stringValue());
        }

        if (options.verbose)
            writeList(String.format("flags: (0x%04x) ", flags.flagsMask()),
                    flagsReportUnknown(flags).stream().map(fl -> "ACC_" + fl.name()).toList(),
                    "\n");

        if (options.showAllAttrs) {
            attrWriter.write(f.attributes());
            showBlank = true;
        }

        indent(-1);

        if (showBlank || options.showDisassembled || options.showLineAndLocalVariableTables)
            println();
    }

    protected void writeMethods() {
        for (MethodModel m: classModel.methods())
            writeMethod(m);
        setPendingNewline(false);
    }

    private static final int DEFAULT_ALLOWED_MAJOR_VERSION = 52;
    private static final int DEFAULT_ALLOWED_MINOR_VERSION = 0;

    protected void writeMethod(MethodModel m) {
        if (!options.checkAccess(m.flags().flagsMask()))
            return;

        method = m;

        int flags = m.flags().flagsMask();

        var modifiers = new ArrayList<String>();
        for (var f : flagsReportUnknown(m.flags()))
            if (f.sourceModifier()) modifiers.add(Modifier.toString(f.mask()));

        String name = "???";
        try {
            name = m.methodName().stringValue();
        } catch (IllegalArgumentException e) {
            report(e);
        }

        if ((classModel.flags().flagsMask() & ACC_INTERFACE) != 0 &&
                ((flags & ACC_ABSTRACT) == 0) && !name.equals("<clinit>")) {
            if (classModel.majorVersion() > DEFAULT_ALLOWED_MAJOR_VERSION ||
                    (classModel.majorVersion() == DEFAULT_ALLOWED_MAJOR_VERSION
                    && classModel.minorVersion() >= DEFAULT_ALLOWED_MINOR_VERSION)) {
                if ((flags & (ACC_STATIC | ACC_PRIVATE)) == 0) {
                    modifiers.add("default");
                }
            }
        }
        writeModifiers(modifiers);

        try {
            var sigAttr = m.findAttribute(Attributes.signature());
            MethodSignature d;
            if (sigAttr.isEmpty()) {
                d = MethodSignature.parseFrom(m.methodType().stringValue());
            } else {
                d = sigAttr.get().asMethodSignature();
            }

            if (!d.typeParameters().isEmpty()) {
                print(sigPrinter.printTypeParams(d.typeParameters()) + " ");
            }
            switch (name) {
                case "<init>":
                    print(getJavaName(classModel.thisClass().asInternalName()));
                    print(getJavaParameterTypes(d, flags));
                    break;
                case "<clinit>":
                    print("{}");
                    break;
                default:
                    print(getJavaName(sigPrinter.print(d.result())));
                    print(" ");
                    print(name);
                    print(getJavaParameterTypes(d, flags));
                    break;
            }

            var e_attr = m.findAttribute(Attributes.exceptions());
            // if there are generic exceptions, there must be erased exceptions
            if (e_attr.isPresent()) {
                var exceptions = e_attr.get();
                print(" throws ");
                if (d != null && !d.throwableSignatures().isEmpty()) { // use generic list if available
                    print(() -> sigPrinter.printList("", d.throwableSignatures(), ""));
                } else {
                    var exNames = exceptions.exceptions();
                    for (int i = 0; i < exNames.size(); i++) {
                        if (i > 0)
                            print(", ");
                        int ii = i;
                        print(() -> getJavaName(exNames.get(ii).asInternalName()));
                    }
                }
            }
        } catch (IllegalArgumentException e) {
            report(e);
        }

        println(";");

        indent(+1);

        if (options.showDescriptors) {
            print("descriptor: ");println(() -> m.methodType().stringValue());
        }

        if (options.verbose) {
            StringBuilder sb = new StringBuilder();
            String sep = "";
            sb.append(String.format("flags: (0x%04x) ", flags));
            for (var f : flagsReportUnknown(m.flags())) {
                sb.append(sep).append("ACC_").append(f.name());
                sep = ", ";
            }
            println(sb.toString());
        }

        var code = (CodeAttribute)m.code().orElse(null);

        if (options.showAllAttrs) {
            attrWriter.write(m.attributes());
        } else if (code != null) {
            if (options.showDisassembled) {
                println("Code:");
                codeWriter.writeInstrs(code);
                codeWriter.writeExceptionTable(code);
            }

            if (options.showLineAndLocalVariableTables) {
                code.findAttribute(Attributes.lineNumberTable())
                        .ifPresent(a -> attrWriter.write(a, code));
                code.findAttribute(Attributes.localVariableTable())
                        .ifPresent(a -> attrWriter.write(a, code));
            }
        }

        indent(-1);

        // set pendingNewline to write a newline before the next method (if any)
        // if a separator is desired
        setPendingNewline(
                options.showDisassembled ||
                options.showAllAttrs ||
                options.showDescriptors ||
                options.showLineAndLocalVariableTables ||
                options.verbose);
    }

    void writeModifiers(Collection<String> items) {
        for (Object item: items) {
            print(item);
            print(" ");
        }
    }

    public static final int ACC_TRANSITIVE = 0x0020;
    public static final int ACC_STATIC_PHASE = 0x0040;

    void writeDirectives() {
        var attr = classModel.findAttribute(Attributes.module());
        if (attr.isEmpty())
            return;

        var m = attr.get();
        for (var entry: m.requires()) {
            print("requires");
            if ((entry.requiresFlagsMask() & ACC_STATIC_PHASE) != 0)
                print(" static");
            if ((entry.requiresFlagsMask() & ACC_TRANSITIVE) != 0)
                print(" transitive");
            print(" ");
            String mname;
            print(entry.requires().name().stringValue());
            println(";");
        }

        for (var entry: m.exports()) {
            print("exports");
            print(" ");
            print(entry.exportedPackage().name().stringValue().replace('/', '.'));
            boolean first = true;
            for (var mod: entry.exportsTo()) {
                if (first) {
                    println(" to");
                    indent(+1);
                    first = false;
                } else {
                    println(",");
                }
                print(mod.name().stringValue());
            }
            println(";");
            if (!first)
                indent(-1);
        }

        for (var entry: m.opens()) {
            print("opens");
            print(" ");
            print(entry.openedPackage().name().stringValue().replace('/', '.'));
            boolean first = true;
            for (var mod: entry.opensTo()) {
                if (first) {
                    println(" to");
                    indent(+1);
                    first = false;
                } else {
                    println(",");
                }
                print(mod.name().stringValue());
            }
            println(";");
            if (!first)
                indent(-1);
        }

        for (var entry: m.uses()) {
            print("uses ");
            print(entry.asInternalName().replace('/', '.'));
            println(";");
        }

        for (var entry: m.provides()) {
            print("provides  ");
            print(entry.provides().asInternalName().replace('/', '.'));
            boolean first = true;
            for (var ce: entry.providesWith()) {
                if (first) {
                    println(" with");
                    indent(+1);
                    first = false;
                } else {
                    println(",");
                }
                print(ce.asInternalName().replace('/', '.'));
            }
            println(";");
            if (!first)
                indent(-1);
        }
    }

    void writeList(String prefix, Collection<?> items, String suffix) {
        print(prefix);
        String sep = "";
        for (Object item: items) {
            print(sep);
            print(item);
            sep = ", ";
        }
        print(suffix);
    }

    void writeListIfNotEmpty(String prefix, List<?> items, String suffix) {
        if (items != null && items.size() > 0)
            writeList(prefix, items, suffix);
    }

    String adjustVarargs(int flags, String params) {
        if ((flags & ACC_VARARGS) != 0) {
            int i = params.lastIndexOf("[]");
            if (i > 0)
                return params.substring(0, i) + "..." + params.substring(i+2);
        }

        return params;
    }

    String getJavaParameterTypes(MethodSignature d, int flags) {
        return getJavaName(adjustVarargs(flags,
                sigPrinter.printList("(", d.arguments(), ")")));
    }

    static String getJavaName(String name) {
        return name.replace('/', '.');
    }

    /**
     * Get the value of an entry in the constant pool as a Java constant.
     * Characters and booleans are represented by CONSTANT_Intgere entries.
     * Character and string values are processed to escape characters outside
     * the basic printable ASCII set.
     * @param d the descriptor, giving the expected type of the constant
     * @param index the index of the value in the constant pool
     * @return a printable string containing the value of the constant.
     */
    String getConstantValue(ClassDesc d, ConstantValueEntry cpInfo) {
        switch (cpInfo.tag()) {
            case ClassFile.TAG_INTEGER: {
                var val = (Integer)cpInfo.constantValue();
                switch (d.descriptorString()) {
                    case "C":
                        // character
                        return getConstantCharValue((char)val.intValue());
                    case "Z":
                        // boolean
                        return String.valueOf(val == 1);
                    default:
                        // other: assume integer
                        return String.valueOf(val);
                }
            }
            case ClassFile.TAG_STRING:
                return getConstantStringValue(cpInfo.constantValue().toString());
            default:
                return constantWriter.stringValue(cpInfo);
        }
    }

    private String getConstantCharValue(char c) {
        StringBuilder sb = new StringBuilder();
        sb.append('\'');
        sb.append(esc(c, '\''));
        sb.append('\'');
        return sb.toString();
    }

    private String getConstantStringValue(String s) {
        StringBuilder sb = new StringBuilder();
        sb.append("\"");
        for (int i = 0; i < s.length(); i++) {
            sb.append(esc(s.charAt(i), '"'));
        }
        sb.append("\"");
        return sb.toString();
    }

    private String esc(char c, char quote) {
        if (32 <= c && c <= 126 && c != quote && c != '\\')
            return String.valueOf(c);
        else switch (c) {
            case '\b': return "\\b";
            case '\n': return "\\n";
            case '\t': return "\\t";
            case '\f': return "\\f";
            case '\r': return "\\r";
            case '\\': return "\\\\";
            case '\'': return "\\'";
            case '\"': return "\\\"";
            default:   return String.format("\\u%04x", (int) c);
        }
    }

    private Set<String> getClassModifiers(AccessFlags flags) {
        var flagSet = flagsReportUnknown(flags);
        Set<AccessFlag> set;
        if (flagSet.contains(AccessFlag.INTERFACE)) {
            set = EnumSet.copyOf(flagSet);
            set.remove(AccessFlag.ABSTRACT);
        } else {
            set = flagSet;
        }
        return getModifiers(set);
    }

    private static Set<String> getModifiers(Set<java.lang.reflect.AccessFlag> flags) {
        Set<String> s = new LinkedHashSet<>();
        for (var f : flags)
            if (f.sourceModifier()) s.add(Modifier.toString(f.mask()));
        return s;
    }

    private Set<String> getClassFlags(AccessFlags flags) {
        return getFlags(flags.flagsMask(), flagsReportUnknown(flags));
    }

    private static Set<String> getFlags(int mask, Set<java.lang.reflect.AccessFlag> flags) {
        Set<String> s = new LinkedHashSet<>();
        for (var f: flags) {
            s.add("ACC_" + f.name());
            mask = mask & ~f.mask();
        }
        while (mask != 0) {
            int bit = Integer.highestOneBit(mask);
            s.add("0x" + Integer.toHexString(bit));
            mask = mask & ~bit;
        }
        return s;
    }

    private final Options options;
    private final AttributeWriter attrWriter;
    private final CodeWriter codeWriter;
    private final ConstantWriter constantWriter;
    private ClassModel classModel;
    private URI uri;
    private long lastModified;
    private String digestName;
    private byte[] digest;
    private int size;
    private MethodModel method;
}
