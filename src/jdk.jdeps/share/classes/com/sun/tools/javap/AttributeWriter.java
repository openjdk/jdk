/*
 * Copyright (c) 2007, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.lang.classfile.*;
import java.lang.reflect.AccessFlag;
import java.lang.classfile.constantpool.*;
import java.lang.classfile.attribute.*;
import static java.lang.classfile.ClassFile.*;
import static java.lang.classfile.attribute.StackMapFrameInfo.*;

/*
 *  A writer for writing Attributes as text.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class AttributeWriter extends BasicWriter {

    public static AttributeWriter instance(Context context) {
        AttributeWriter instance = context.get(AttributeWriter.class);
        if (instance == null)
            instance = new AttributeWriter(context);
        return instance;
    }

    protected AttributeWriter(Context context) {
        super(context);
        context.put(AttributeWriter.class, this);
        annotationWriter = AnnotationWriter.instance(context);
        codeWriter = CodeWriter.instance(context);
        constantWriter = ConstantWriter.instance(context);
        options = Options.instance(context);
    }

    public void write(List<Attribute<?>> attrs) {
        write(attrs, null);
    }

    public void write(List<Attribute<?>> attrs, CodeAttribute lr) {
        if (attrs != null) {
            for (var attr : attrs) try {
                write(attr, lr);
            } catch (IllegalArgumentException e) {
                report(e);
            }
        }
    }

    public void write(Attribute<?> a, CodeAttribute lr) {
        switch (a) {
            case UnknownAttribute attr -> {
                byte[] data = attr.contents();
                int i = 0;
                int j = 0;
                print("  ");
                print(attr.attributeName());
                print(": ");
                print("length = 0x" + toHex(data.length));
                print(" (unknown attribute)");
                println();
                print("   ");
                while (i < data.length) {
                    print(toHex(data[i], 2));

                    j++;
                    if (j == 16) {
                        println();
                        print("   ");
                        j = 0;
                    } else {
                        print(" ");
                    }
                    i++;
                }
                println();
            }
            case AnnotationDefaultAttribute attr -> {
                println("AnnotationDefault:");
                indent(+1);
                print("default_value: ");
                annotationWriter.write(attr.defaultValue());
                indent(-1);
                println();
            }
            case BootstrapMethodsAttribute attr -> {
                println("BootstrapMethods:");
                for (int i = 0; i < attr.bootstrapMethodsSize() ; i++) {
                    var bsm = attr.bootstrapMethods().get(i);
                    indent(+1);
                    print(i + ": #" + bsm.bootstrapMethod().index() + " ");
                    println(constantWriter.stringValue(bsm.bootstrapMethod()));
                    indent(+1);
                    println("Method arguments:");
                    indent(+1);
                    for (var arg : bsm.arguments()) {
                        print("#" + arg.index() + " ");
                        println(constantWriter.stringValue(arg));
                    }
                    indent(-3);
                }
            }
            case CharacterRangeTableAttribute attr -> {
                println("CharacterRangeTable:");
                indent(+1);
                for (var e : attr.characterRangeTable()) {
                    print(String.format("    %2d, %2d, %6x, %6x, %4x",
                            e.startPc(), e.endPc(),
                            e.characterRangeStart(), e.characterRangeEnd(),
                            e.flags()));
                    tab();
                    print(String.format("// %2d, %2d, %4d:%02d, %4d:%02d",
                            e.startPc(), e.endPc(),
                            (e.characterRangeStart() >> 10),
                            (e.characterRangeStart() & 0x3ff),
                            (e.characterRangeEnd() >> 10),
                            (e.characterRangeEnd() & 0x3ff)));
                    if ((e.flags() & CRT_STATEMENT) != 0)
                        print(", statement");
                    if ((e.flags() & CRT_BLOCK) != 0)
                        print(", block");
                    if ((e.flags() & CRT_ASSIGNMENT) != 0)
                        print(", assignment");
                    if ((e.flags() & CRT_FLOW_CONTROLLER) != 0)
                        print(", flow-controller");
                    if ((e.flags() & CRT_FLOW_TARGET) != 0)
                        print(", flow-target");
                    if ((e.flags() & CRT_INVOKE) != 0)
                        print(", invoke");
                    if ((e.flags() & CRT_CREATE) != 0)
                        print(", create");
                    if ((e.flags() & CRT_BRANCH_TRUE) != 0)
                        print(", branch-true");
                    if ((e.flags() & CRT_BRANCH_FALSE) != 0)
                        print(", branch-false");
                    println();
                }
                indent(-1);
            }
            case CodeAttribute attr -> codeWriter.write(attr);
            case CompilationIDAttribute attr ->
                constantWriter.write(attr.compilationId().index());
            case ConstantValueAttribute attr -> {
                print("ConstantValue: ");
                constantWriter.write(attr.constant().index());
                println();
            }
            case DeprecatedAttribute attr -> println("Deprecated: true");
            case EnclosingMethodAttribute attr -> {
                print("EnclosingMethod: #" + attr.enclosingClass().index() + ".#"
                        +  attr.enclosingMethod().map(PoolEntry::index).orElse(0));
                tab();
                print("// " + getJavaName(attr.enclosingClass().asInternalName()));
                if (attr.enclosingMethod().isPresent())
                    print("." + attr.enclosingMethod().get().name().stringValue());
                println();
            }
            case ExceptionsAttribute attr -> {
                println("Exceptions:");
                indent(+1);
                print("throws ");
                var exc = attr.exceptions();
                for (int i = 0; i < exc.size(); i++) {
                    if (i > 0)
                        print(", ");
                    print(getJavaName(exc.get(i).asInternalName()));
                }
                println();
                indent(-1);
            }
            case InnerClassesAttribute attr -> {
                boolean first = true;
                for (var info : attr.classes()) {
                    //access
                    int access_flags = info.flagsMask();
                    if (options.checkAccess(access_flags)) {
                        if (first) {
                            println("InnerClasses:");
                            indent(+1);
                            first = false;
                        }
                        for (var flag : info.flags()) {
                            if (flag.sourceModifier() && (flag != AccessFlag.ABSTRACT
                                    || !info.has(AccessFlag.INTERFACE))) {
                                print(Modifier.toString(flag.mask()) + " ");
                            }
                        }
                        if (info.innerName().isPresent()) {
                            print("#" + info.innerName().get().index() + "= ");
                        }
                        print("#" + info.innerClass().index());
                        if (info.outerClass().isPresent()) {
                            print(" of #" + info.outerClass().get().index());
                        }
                        print(";");
                        tab();
                        print("// ");
                        if (info.innerName().isPresent()) {
                            print(info.innerName().get().stringValue() + "=");
                        }
                        constantWriter.write(info.innerClass().index());
                        if (info.outerClass().isPresent()) {
                            print(" of ");
                            constantWriter.write(info.outerClass().get().index());
                        }
                        println();
                    }
                }
                if (!first)
                    indent(-1);
            }
            case LineNumberTableAttribute attr -> {
                println("LineNumberTable:");
                indent(+1);
                for (var entry: attr.lineNumbers()) {
                    println("line " + entry.lineNumber() + ": " + entry.startPc());
                }
                indent(-1);
            }
            case LocalVariableTableAttribute attr -> {
                println("LocalVariableTable:");
                indent(+1);
                println("Start  Length  Slot  Name   Signature");
                for (var entry : attr.localVariables()) {
                    println(String.format("%5d %7d %5d %5s   %s",
                            entry.startPc(), entry.length(), entry.slot(),
                            constantWriter.stringValue(entry.name()),
                            constantWriter.stringValue(entry.type())));
                }
                indent(-1);
            }
            case LocalVariableTypeTableAttribute attr -> {
                println("LocalVariableTypeTable:");
                indent(+1);
                println("Start  Length  Slot  Name   Signature");
                for (var entry : attr.localVariableTypes()) {
                    println(String.format("%5d %7d %5d %5s   %s",
                            entry.startPc(), entry.length(), entry.slot(),
                            constantWriter.stringValue(entry.name()),
                            constantWriter.stringValue(entry.signature())));
                }
                indent(-1);
            }
            case NestHostAttribute attr -> {
                print("NestHost: ");
                constantWriter.write(attr.nestHost().index());
                println();
            }
            case MethodParametersAttribute attr -> {
                final String header = String.format(format, "Name", "Flags");
                println("MethodParameters:");
                indent(+1);
                println(header);
                for (var entry : attr.parameters()) {
                    String namestr =
                        entry.name().isPresent() ?
                        constantWriter.stringValue(entry.name().get()) : "<no name>";
                    String flagstr =
                        (entry.has(AccessFlag.FINAL) ? "final " : "") +
                        (entry.has(AccessFlag.MANDATED) ? "mandated " : "") +
                        (entry.has(AccessFlag.SYNTHETIC) ? "synthetic" : "");
                    println(String.format(format, namestr, flagstr));
                }
                indent(-1);
            }
            case ModuleAttribute attr -> {
                println("Module:");
                indent(+1);

                print("#" + attr.moduleName().index());
                print(",");
                print(String.format("%x", attr.moduleFlagsMask()));
                tab();
                print("// " + constantWriter.stringValue(attr.moduleName()));
                if (attr.has(AccessFlag.OPEN))
                    print(" ACC_OPEN");
                if (attr.has(AccessFlag.MANDATED))
                    print(" ACC_MANDATED");
                if (attr.has(AccessFlag.SYNTHETIC))
                    print(" ACC_SYNTHETIC");
                println();
                var ver = attr.moduleVersion();
                print("#" + ver.map(Utf8Entry::index).orElse(0));
                if (ver.isPresent()) {
                    tab();
                    print("// " + constantWriter.stringValue(ver.get()));
                }
                println();
                {
                    var entries = attr.requires();
                    print(entries.size());
                    tab();
                    println("// " + "requires");
                    indent(+1);
                    for (var e: entries) {
                        print("#" + e.requires().index() + ","
                                + String.format("%x", e.requiresFlagsMask()));
                        tab();
                        print("// " + constantWriter.stringValue(e.requires()));
                        if (e.has(AccessFlag.TRANSITIVE))
                            print(" ACC_TRANSITIVE");
                        if (e.has(AccessFlag.STATIC_PHASE))
                            print(" ACC_STATIC_PHASE");
                        if (e.has(AccessFlag.SYNTHETIC))
                            print(" ACC_SYNTHETIC");
                        if (e.has(AccessFlag.MANDATED))
                            print(" ACC_MANDATED");
                        println();
                        var reqVer = e.requiresVersion();
                        print("#" + reqVer.map(Utf8Entry::index).orElse(0));
                        if (reqVer.isPresent()) {
                            tab();
                            print("// " + constantWriter.stringValue(reqVer.get()));
                        }
                        println();
                    }
                    indent(-1);
                }
                {
                    var entries = attr.exports();
                    print(entries.size());
                    tab();
                    println("// exports");
                    indent(+1);
                    for (var e: entries) {
                        printExportOpenEntry(e.exportedPackage().index(),
                                e.exportsFlagsMask(), e.exportsTo());
                    }
                    indent(-1);
                }
                {
                    var entries = attr.opens();
                    print(entries.size());
                    tab();
                    println("// opens");
                    indent(+1);
                    for (var e: entries) {
                        printExportOpenEntry(e.openedPackage().index(),
                                e.opensFlagsMask(), e.opensTo());
                    }
                    indent(-1);
                }
                {
                    var entries = attr.uses();
                    print(entries.size());
                    tab();
                    println("// " + "uses");
                    indent(+1);
                    for (var e: entries) {
                        print("#" + e.index());
                        tab();
                        println("// " + constantWriter.stringValue(e));
                    }
                    indent(-1);
                }
                {
                    var entries = attr.provides();
                    print(entries.size());
                    tab();
                    println("// " + "provides");
                    indent(+1);
                    for (var e: entries) {
                        print("#" + e.provides().index());
                        tab();
                        print("// ");
                        print(constantWriter.stringValue(e.provides()));
                        println(" with ... " + e.providesWith().size());
                        indent(+1);
                        for (var with : e.providesWith()) {
                            print("#" + with.index());
                            tab();
                            println("// ... with " + constantWriter.stringValue(with));
                        }
                        indent(-1);
                    }
                    indent(-1);
                }
                indent(-1);
            }
            case ModuleHashesAttribute attr -> {
                println("ModuleHashes:");
                indent(+1);
                print("algorithm: #" + attr.algorithm().index());
                tab();
                println("// " + attr.algorithm().stringValue());
                print(attr.hashes().size());
                tab();
                println("// hashes");
                for (var e : attr.hashes()) {
                    print("#" + e.moduleName().index());
                    tab();
                    println("// " + e.moduleName().name().stringValue());
                    println("hash_length: " + e.hash().length);
                    println("hash: [" + toHex(e.hash()) + "]");
                }
                indent(-1);
            }
            case ModuleMainClassAttribute attr -> {
                print("ModuleMainClass: #" + attr.mainClass().index());
                tab();
                print("// " + getJavaName(attr.mainClass().asInternalName()));
                println();
            }
            case ModulePackagesAttribute attr -> {
                println("ModulePackages: ");
                indent(+1);
                for (var p : attr.packages()) {
                    print("#" + p.index());
                    tab();
                    println("// " + getJavaName(p.name().stringValue()));
                }
                indent(-1);
            }
            case ModuleResolutionAttribute attr -> {
                println("ModuleResolution:");
                indent(+1);
                print(String.format("%x", attr.resolutionFlags()));
                tab();
                print("// ");
                int flags = attr.resolutionFlags();
                if ((flags & DO_NOT_RESOLVE_BY_DEFAULT) != 0)
                    print(" DO_NOT_RESOLVE_BY_DEFAULT");
                if ((flags & WARN_DEPRECATED) != 0)
                    print(" WARN_DEPRECATED");
                if ((flags & WARN_DEPRECATED_FOR_REMOVAL) != 0)
                    print(" WARN_DEPRECATED_FOR_REMOVAL");
                if ((flags & WARN_INCUBATING) != 0)
                    print(" WARN_INCUBATING");
                println();
                indent(-1);
            }
            case ModuleTargetAttribute attr -> {
                println("ModuleTarget:");
                indent(+1);
                print("target_platform: #" + attr.targetPlatform().index());
                tab();
                println("// " + attr.targetPlatform().stringValue());
                indent(-1);
            }
            case NestMembersAttribute attr -> {
                println("NestMembers:");
                indent(+1);
                for (var m : attr.nestMembers()) {
                    println(constantWriter.stringValue(m));
                }
                indent(-1);
            }
            case RecordAttribute attr -> {
                println("Record:");
                indent(+1);
                for (var componentInfo : attr.components()) {
                    var sigAttr = componentInfo.findAttribute(Attributes.SIGNATURE);
                    print(getJavaName(
                            new ClassWriter.SignaturePrinter(options.verbose).print(
                                    sigAttr.map(SignatureAttribute::asTypeSignature)
                                            .orElse(Signature.of(
                                                    componentInfo.descriptorSymbol())))));
                    print(" ");
                    print(componentInfo.name().stringValue());
                    print(";");
                    println();
                    indent(+1);
                    if (options.showDescriptors) {
                        println("descriptor: " + componentInfo.descriptor().stringValue());
                    }
                    if (options.showAllAttrs) {
                        write(componentInfo.attributes());
                        println();
                    }
                    indent(-1);
                }
                indent(-1);
            }
            case RuntimeVisibleAnnotationsAttribute attr ->
                printAnnotations("RuntimeVisibleAnnotations:", attr.annotations());
            case RuntimeInvisibleAnnotationsAttribute attr ->
                printAnnotations("RuntimeInvisibleAnnotations:", attr.annotations());
            case RuntimeVisibleTypeAnnotationsAttribute attr ->
                printTypeAnnotations("RuntimeVisibleTypeAnnotations:",
                        attr.annotations(), lr);
            case RuntimeInvisibleTypeAnnotationsAttribute attr ->
                printTypeAnnotations("RuntimeInvisibleTypeAnnotations:",
                        attr.annotations(), lr);
            case RuntimeVisibleParameterAnnotationsAttribute attr ->
                printParameterAnnotations("RuntimeVisibleParameterAnnotations:",
                        attr.parameterAnnotations());
            case RuntimeInvisibleParameterAnnotationsAttribute attr ->
                printParameterAnnotations("RuntimeInvisibleParameterAnnotations:",
                        attr.parameterAnnotations());
            case PermittedSubclassesAttribute attr -> {
                println("PermittedSubclasses:");
                indent(+1);
                for (var sc : attr.permittedSubclasses()) {
                    println(constantWriter.stringValue(sc));
                }
                indent(-1);
            }
            case SignatureAttribute attr -> {
                print("Signature: #" + attr.signature().index());
                tab();
                println("// " + attr.signature().stringValue());
            }
            case SourceDebugExtensionAttribute attr -> {
                println("SourceDebugExtension:");
                indent(+1);
                for (String s: new String(attr.contents(), StandardCharsets.UTF_8)
                        .split("[\r\n]+")) {
                    println(s);
                }
                indent(-1);
            }
            case SourceFileAttribute attr ->
                println("SourceFile: \"" + attr.sourceFile().stringValue() + "\"");
            case SourceIDAttribute attr ->
                constantWriter.write(attr.sourceId().index());
            case StackMapTableAttribute attr -> {
                var entries = attr.entries();
                println("StackMapTable: number_of_entries = " + entries.size());
                indent(+1);
                int lastOffset = -1;
                for (var frame : entries) {
                    int frameType = frame.frameType();
                    if (frameType < 64) {
                        printHeader(frameType, "/* same */");
                    } else if (frameType < 128) {
                        printHeader(frameType, "/* same_locals_1_stack_item */");
                        indent(+1);
                        printMap("stack", frame.stack(), lr);
                        indent(-1);
                    } else {
                        int offsetDelta = lr.labelToBci(frame.target()) - lastOffset - 1;
                        switch (frameType) {
                            case 247 -> {
                                printHeader(frameType, "/* same_locals_1_stack_item_frame_extended */");
                                indent(+1);
                                println("offset_delta = " + offsetDelta);
                                printMap("stack", frame.stack(), lr);
                                indent(-1);
                            }
                            case 248, 249, 250 -> {
                                printHeader(frameType, "/* chop */");
                                indent(+1);
                                println("offset_delta = " + offsetDelta);
                                indent(-1);
                            }
                            case 251 -> {
                                printHeader(frameType, "/* same_frame_extended */");
                                indent(+1);
                                println("offset_delta = " + offsetDelta);
                                indent(-1);
                            }
                            case 252, 253, 254 -> {
                                printHeader(frameType, "/* append */");
                                indent(+1);
                                println("offset_delta = " + offsetDelta);
                                var locals = frame.locals();
                                printMap("locals", locals.subList(locals.size()
                                        - frameType + 251, locals.size()), lr);
                                indent(-1);
                            }
                            case 255 -> {
                                printHeader(frameType, "/* full_frame */");
                                indent(+1);
                                println("offset_delta = " + offsetDelta);
                                printMap("locals", frame.locals(), lr);
                                printMap("stack", frame.stack(), lr);
                                indent(-1);
                            }
                        }
                    }
                    lastOffset = lr.labelToBci(frame.target());
                }
                indent(-1);
            }
            case SyntheticAttribute attr ->
                println("Synthetic: true");
            default -> {}
        }
    }

    //ToDo move somewhere to Bytecode API
    public static final int DO_NOT_RESOLVE_BY_DEFAULT   = 0x0001;
    public static final int WARN_DEPRECATED             = 0x0002;
    public static final int WARN_DEPRECATED_FOR_REMOVAL = 0x0004;
    public static final int WARN_INCUBATING             = 0x0008;

    private static final String format = "%-31s%s";

    protected void printExportOpenEntry(int index, int flags, List<ModuleEntry> to_index) {
        print("#" + index + "," + String.format("%x", flags));
        tab();
        print("// ");
        print(constantWriter.stringValue(index));
        if ((flags & ACC_MANDATED) != 0)
            print(" ACC_MANDATED");
        if ((flags & ACC_SYNTHETIC) != 0)
            print(" ACC_SYNTHETIC");
        if (to_index.size() == 0) {
            println();
        } else {
            println(" to ... " + to_index.size());
            indent(+1);
            for (var to: to_index) {
                print("#" + to.index());
                tab();
                println("// ... to " + constantWriter.stringValue(to));
            }
            indent(-1);
        }
    }

    private void printAnnotations(String message, List<? extends Annotation> anno) {
        println(message);
        indent(+1);
        for (int i = 0; i < anno.size(); i++) {
            print(i + ": ");
            annotationWriter.write(anno.get(i));
            println();
        }
        indent(-1);
    }

    private void printTypeAnnotations(String message,
            List<? extends TypeAnnotation> anno, CodeAttribute lr) {
        println(message);
        indent(+1);
        for (int i = 0; i < anno.size(); i++) {
            print(i + ": ");
            annotationWriter.write(anno.get(i), lr);
            println();
        }
        indent(-1);
    }

    private void printParameterAnnotations(String message, List<List<Annotation>> paramsAnno) {
        println(message);
        indent(+1);
        for (int param = 0; param < paramsAnno.size(); param++) {
            println("parameter " + param + ": ");
            indent(+1);
            var annos = paramsAnno.get(param);
            for (int i = 0; i < annos.size(); i++) {
                print(i + ": ");
                annotationWriter.write(annos.get(i));
                println();
            }
            indent(-1);
        }
        indent(-1);
    }

    void printHeader(int frameType, String extra) {
        print("frame_type = " + frameType + " ");
        println(extra);
    }

    void printMap(String name, List<VerificationTypeInfo> map, CodeAttribute lr) {
        print(name + " = [");
        for (int i = 0; i < map.size(); i++) {
            var info = map.get(i);
            switch (info) {
                case ObjectVerificationTypeInfo obj -> {
                    print(" ");
                    constantWriter.write(obj.className().index());
                }
                case UninitializedVerificationTypeInfo u -> {
                    print(" uninitialized " + lr.labelToBci(u.newTarget()));
                }
                case SimpleVerificationTypeInfo s ->
                    print(" " + mapTypeName(s));
            }
            print(i == (map.size() - 1) ? " " : ",");
        }
        println("]");
    }

    String mapTypeName(SimpleVerificationTypeInfo type) {
        return switch (type) {
            case ITEM_TOP -> "top";
            case ITEM_INTEGER -> "int";
            case ITEM_FLOAT -> "float";
            case ITEM_LONG -> "long";
            case ITEM_DOUBLE -> "double";
            case ITEM_NULL -> "null";
            case ITEM_UNINITIALIZED_THIS -> "this";
        };
    }

    static String getJavaName(String name) {
        return name.replace('/', '.');
    }

    String toHex(byte b, int w) {
        return toHex(b & 0xff, w);
    }

    static String toHex(int i) {
        return Integer.toString(i, 16).toUpperCase(Locale.US);
    }

    static String toHex(int i, int w) {
        String s = Integer.toHexString(i).toUpperCase(Locale.US);
        while (s.length() < w)
            s = "0" + s;
        return s;
    }

    static String toHex(byte[] ba) {
        StringBuilder sb = new StringBuilder(ba.length);
        for (byte b: ba) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }

    private final AnnotationWriter annotationWriter;
    private final CodeWriter codeWriter;
    private final ConstantWriter constantWriter;
    private final Options options;
}
