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
package helpers;

import java.io.IOException;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import java.lang.classfile.*;
import java.lang.classfile.attribute.*;
import java.lang.classfile.constantpool.*;
import java.lang.classfile.instruction.*;

import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import static java.lang.classfile.ClassFile.*;
import static java.lang.classfile.Attributes.*;
import static helpers.ClassRecord.CompatibilityFilter.By_ClassBuilder;

/**
 * ClassRecord
 */
public record ClassRecord(
        int majorVersion,
        int minorVersion,
        String thisClass,
        String superClass,
        Set<String> interfaces,
        String classFlags,
        Map<String, FieldRecord> fields,
        Map<String, MethodRecord> methods,
        AttributesRecord attributes) {

    public enum CompatibilityFilter {
        Read_all, By_ClassBuilder;

        private <T> T isNotDirectlyComparable(CompatibilityFilter compatibilityFilter[], T value) {
            for (CompatibilityFilter p : compatibilityFilter) {
                if (p == this) return null;
            }
            return value;
        }
    }

    public enum DefinedValue {
        DEFINED
    }

    public static ClassRecord ofStreamingElements(ClassModel cl, CompatibilityFilter... compatibilityFilter) {
        return ofStreamingElements(
                cl.majorVersion(),
                cl.minorVersion(),
                cl.thisClass().asInternalName(),
                cl.superclass().map(ClassEntry::asInternalName).orElse(null),
                cl.interfaces().stream().map(ClassEntry::asInternalName).collect(toSet()),
                cl.flags().flagsMask(),
                cl.constantPool(),
                cl::elementStream, compatibilityFilter);
    }
    public static ClassRecord ofStreamingElements(int majorVersion, int minorVersion, String thisClass, String superClass, Set<String> interfaces, int flags, ConstantPool cp, Supplier<Stream<? extends ClassFileElement>> elements, CompatibilityFilter... compatibilityFilter) {
        return new ClassRecord(
                majorVersion,
                minorVersion,
                thisClass,
                superClass,
                interfaces,
                Flags.toString(flags, false),
                elements.get().filter(e -> e instanceof FieldModel).map(e -> (FieldModel)e).collect(toMap(
                        fm -> fm.fieldName().stringValue() + fm.fieldType().stringValue(),
                        fm -> FieldRecord.ofStreamingElements(fm.fieldName().stringValue(), fm.fieldType().stringValue(), fm.flags().flagsMask(), fm::elementStream, compatibilityFilter))),
                elements.get().filter(e -> e instanceof MethodModel).map(e -> (MethodModel)e).collect(toMap(
                        mm -> mm.methodName().stringValue() + mm.methodType().stringValue(),
                        mm -> MethodRecord.ofStreamingElements(mm.methodName().stringValue(), mm.methodType().stringValue(), mm.flags().flagsMask(), mm::elementStream, compatibilityFilter))),
                AttributesRecord.ofStreamingElements(elements, cp, compatibilityFilter));
    }

    public static ClassRecord ofClassModel(ClassModel cl, CompatibilityFilter... compatibilityFilter) {
        return new ClassRecord(
                cl.majorVersion(),
                cl.minorVersion(),
                cl.thisClass().asInternalName(),
                cl.superclass().map(ClassEntry::asInternalName).orElse(null),
                cl.interfaces().stream().map(ci -> ci.asInternalName()).collect(toSet()),
                Flags.toString(cl.flags().flagsMask(), false),
                cl.fields().stream().collect(toMap(f -> f.fieldName().stringValue() + f.fieldType().stringValue(), f -> FieldRecord.ofFieldModel(f, compatibilityFilter))),
                cl.methods().stream().collect(toMap(m -> m.methodName().stringValue() + m.methodType().stringValue(), m -> MethodRecord.ofMethodModel(m, compatibilityFilter))),
                AttributesRecord.ofAttributes(cl::attributes, compatibilityFilter));
    }

    public record FieldRecord(
            String fieldName,
            String fieldType,
            String fieldFlags,
            AttributesRecord fieldAttributes) {

        public static FieldRecord ofStreamingElements(String fieldName, String fieldType, int flags, Supplier<Stream<? extends ClassFileElement>> elements, CompatibilityFilter... compatibilityFilter) {
            return new FieldRecord(
                    fieldName,
                    fieldType,
                    Flags.toString(flags, false),
                    AttributesRecord.ofStreamingElements(elements, null, compatibilityFilter));
        }

        public static FieldRecord ofFieldModel(FieldModel f, CompatibilityFilter... compatibilityFilter) {
            return new FieldRecord(
                    f.fieldName().stringValue(),
                    f.fieldType().stringValue(),
                    Flags.toString(f.flags().flagsMask(), false),
                    AttributesRecord.ofAttributes(f::attributes, compatibilityFilter));
        }
    }

    public record MethodRecord(
            String methodName,
            String methodType,
            String methodFlags,
            AttributesRecord methodAttributes) {

        public static MethodRecord ofStreamingElements(String methodName, String methodType, int flags, Supplier<Stream<? extends ClassFileElement>> elements, CompatibilityFilter... compatibilityFilter) {
            return new MethodRecord(
                    methodName,
                    methodType,
                    Flags.toString(flags, true),
                    AttributesRecord.ofStreamingElements(elements, null, compatibilityFilter));
        }

        public static MethodRecord ofMethodModel(MethodModel m, CompatibilityFilter... compatibilityFilter) {
            return new MethodRecord(
                    m.methodName().stringValue(),
                    m.methodType().stringValue(),
                    Flags.toString(m.flags().flagsMask(), true),
                    AttributesRecord.ofAttributes(m::attributes, compatibilityFilter));
        }
    }

    private static<T extends Attribute<T>, U> U mapAttr(Map<String, Attribute<?>> attrs, AttributeMapper<T> mapper, Function<T, U> f) {
        return mapAttr(attrs, mapper, f, null);
    }

    private static<T extends Attribute<T>, U> U mapAttr(Map<String, Attribute<?>> attrs, AttributeMapper<T> mapper, Function<T, U> f, U defaultReturn) {
        @SuppressWarnings("unchecked")
        var attr = (T) attrs.get(mapper.name());
        return map(attr, a -> f.apply(a), defaultReturn);
    }

    interface AttributeFinder extends Supplier<List<Attribute<?>>> {

        @SuppressWarnings("unchecked")
        default <T extends Attribute<T>, R> R findAndMap(AttributeMapper<T> m, Function<T, R> mapping) {
            for (Attribute<?> a : get()) {
                if (a.attributeMapper() == m) {
                    return mapping.apply((T) a);
                }
            }
            return null;
        }

        @SuppressWarnings("unchecked")
        default <T extends Attribute<T>> Stream<T> findAll(AttributeMapper<T> m) {
            return get().stream().filter(a -> a.attributeMapper() == m).map(a -> (T)a);
        }
    }

    public static <T> Collector<T,?,Set<T>> toSetOrNull() {
        return Collectors.collectingAndThen(Collectors.toSet(), (Set<T> s) -> s.isEmpty() ? null : s);
    }

    public record AttributesRecord(
            ElementValueRecord annotationDefaultAttribute,
            Set<BootstrapMethodRecord> bootstrapMethodsAttribute,
            CodeRecord codeAttribute,
            String compilationIDAttribute,
            ConstantPoolEntryRecord constantValueAttribute,
            DefinedValue deprecated,
            EnclosingMethodRecord enclosingMethodAttribute,
            Set<String> exceptionsAttribute,
            Map<String, InnerClassRecord> innerClassesAttribute,
            List<MethodParameterRecord> methodParametersAttribute,
            ModuleRecord moduleAttribute,
            ModuleHashesRecord moduleHashesAttribute,
            String moduleMainClassAttribute,
            Set<String> modulePackagesAttribute,
            Integer moduleResolutionAttribute,
            String moduleTargetAttribute,
            String nestHostAttribute,
            Set<String> nestMembersAttribute,
            Set<String> permittedSubclassesAttribute,
            List<RecordComponentRecord> recordAttribute,
            Set<AnnotationRecord> runtimeVisibleAnnotationsAttribute,
            Set<AnnotationRecord> runtimeInvisibleAnnotationsAttribute,
            List<Set<AnnotationRecord>> runtimeVisibleParameterAnnotationsAttribute,
            List<Set<AnnotationRecord>> runtimeInvisibleParameterAnnotationsAttribute,
            Set<TypeAnnotationRecord> runtimeVisibleTypeAnnotationsAttribute,
            Set<TypeAnnotationRecord> runtimeInvisibleTypeAnnotationsAttribute,
            String signatureAttribute,
            String sourceDebugExtensionAttribute,
            String sourceFileAttribute,
            String sourceIDAttribute,
            DefinedValue syntheticAttribute) {

        public static AttributesRecord ofStreamingElements(Supplier<Stream<? extends ClassFileElement>> elements, ConstantPool cp, CompatibilityFilter... cf) {
            Map<String, Attribute<?>> attrs = elements.get().filter(e -> e instanceof Attribute<?>)
                    .map(e -> (Attribute<?>) e)
                    .collect(toMap(Attribute::attributeName, e -> e));
            return new AttributesRecord(
                    mapAttr(attrs, ANNOTATION_DEFAULT, a -> ElementValueRecord.ofElementValue(a.defaultValue())),
                    cp == null ? null : IntStream.range(0, cp.bootstrapMethodCount()).mapToObj(i -> BootstrapMethodRecord.ofBootstrapMethodEntry(cp.bootstrapMethodEntry(i))).collect(toSetOrNull()),
                    mapAttr(attrs, CODE, a -> CodeRecord.ofStreamingElements(a.maxStack(), a.maxLocals(), a.codeLength(), a::elementStream, a, new CodeNormalizerHelper(a.codeArray()), cf)),
                    mapAttr(attrs, COMPILATION_ID, a -> a.compilationId().stringValue()),
                    mapAttr(attrs, CONSTANT_VALUE, a -> ConstantPoolEntryRecord.ofCPEntry(a.constant())),
                    mapAttr(attrs, DEPRECATED, a -> DefinedValue.DEFINED),
                    mapAttr(attrs, ENCLOSING_METHOD, a -> EnclosingMethodRecord.ofEnclosingMethodAttribute(a)),
                    mapAttr(attrs, EXCEPTIONS, a -> new HashSet<>(a.exceptions().stream().map(e -> e.asInternalName()).toList())),
                    mapAttr(attrs, INNER_CLASSES, a -> a.classes().stream().collect(toMap(ic -> ic.innerClass().asInternalName(), ic -> InnerClassRecord.ofInnerClassInfo(ic)))),
                    mapAttr(attrs, METHOD_PARAMETERS, a -> a.parameters().stream().map(mp -> MethodParameterRecord.ofMethodParameter(mp)).toList()),
                    mapAttr(attrs, MODULE, a -> ModuleRecord.ofModuleAttribute(a)),
                    mapAttr(attrs, MODULE_HASHES, a -> ModuleHashesRecord.ofModuleHashesAttribute(a)),
                    mapAttr(attrs, MODULE_MAIN_CLASS, a -> a.mainClass().asInternalName()),
                    mapAttr(attrs, MODULE_PACKAGES, a -> a.packages().stream().map(p -> p.name().stringValue()).collect(toSet())),
                    mapAttr(attrs, MODULE_RESOLUTION, a -> a.resolutionFlags()),
                    mapAttr(attrs, MODULE_TARGET, a -> a.targetPlatform().stringValue()),
                    mapAttr(attrs, NEST_HOST, a -> a.nestHost().asInternalName()),
                    mapAttr(attrs, NEST_MEMBERS, a -> a.nestMembers().stream().map(m -> m.asInternalName()).collect(toSet())),
                    mapAttr(attrs, PERMITTED_SUBCLASSES, a -> new HashSet<>(a.permittedSubclasses().stream().map(e -> e.asInternalName()).toList())),
                    mapAttr(attrs, RECORD, a -> a.components().stream().map(rc -> RecordComponentRecord.ofRecordComponent(rc, cf)).toList()),
                    elements.get().filter(e -> e instanceof RuntimeVisibleAnnotationsAttribute).map(e -> (RuntimeVisibleAnnotationsAttribute) e).flatMap(a -> a.annotations().stream())
                            .map(AnnotationRecord::ofAnnotation).collect(toSetOrNull()),
                    elements.get().filter(e -> e instanceof RuntimeInvisibleAnnotationsAttribute).map(e -> (RuntimeInvisibleAnnotationsAttribute) e).flatMap(a -> a.annotations().stream())
                            .map(AnnotationRecord::ofAnnotation).collect(toSetOrNull()),
                    mapAttr(attrs, RUNTIME_VISIBLE_PARAMETER_ANNOTATIONS, a -> a.parameterAnnotations().stream().map(list -> list.stream().map(AnnotationRecord::ofAnnotation).collect(toSet())).toList()),
                    mapAttr(attrs, RUNTIME_INVISIBLE_PARAMETER_ANNOTATIONS, a -> a.parameterAnnotations().stream().map(list -> list.stream().map(AnnotationRecord::ofAnnotation).collect(toSet())).toList()),
                    mapAttr(attrs, RUNTIME_VISIBLE_TYPE_ANNOTATIONS, a -> a.annotations().stream().map(TypeAnnotationRecord::ofTypeAnnotation).collect(toSet())),
                    mapAttr(attrs, RUNTIME_INVISIBLE_TYPE_ANNOTATIONS, a -> a.annotations().stream().map(TypeAnnotationRecord::ofTypeAnnotation).collect(toSet())),
                    mapAttr(attrs, SIGNATURE, a -> a.signature().stringValue()),
                    mapAttr(attrs, SOURCE_DEBUG_EXTENSION, a -> new String(a.contents(), StandardCharsets.UTF_8)),
                    mapAttr(attrs, SOURCE_FILE, a -> a.sourceFile().stringValue()),
                    mapAttr(attrs, SOURCE_ID, a -> a.sourceId().stringValue()),
                    mapAttr(attrs, SYNTHETIC, a -> DefinedValue.DEFINED)
            );
        }

        public static AttributesRecord ofAttributes(AttributeFinder af, CompatibilityFilter... cf) {
            return new AttributesRecord(
                    af.findAndMap(Attributes.ANNOTATION_DEFAULT, a -> ElementValueRecord.ofElementValue(a.defaultValue())),
                    af.findAndMap(Attributes.BOOTSTRAP_METHODS, a -> a.bootstrapMethods().stream().map(bm -> BootstrapMethodRecord.ofBootstrapMethodEntry(bm)).collect(toSet())),
                    af.findAndMap(Attributes.CODE, a -> CodeRecord.ofCodeAttribute(a, cf)),
                    af.findAndMap(Attributes.COMPILATION_ID, a -> a.compilationId().stringValue()),
                    af.findAndMap(Attributes.CONSTANT_VALUE, a -> ConstantPoolEntryRecord.ofCPEntry(a.constant())),
                    af.findAndMap(Attributes.DEPRECATED, a -> DefinedValue.DEFINED),
                    af.findAndMap(Attributes.ENCLOSING_METHOD, a -> EnclosingMethodRecord.ofEnclosingMethodAttribute(a)),
                    af.findAndMap(Attributes.EXCEPTIONS, a -> a.exceptions().stream().map(e -> e.asInternalName()).collect(toSet())),
                    af.findAndMap(Attributes.INNER_CLASSES, a -> a.classes().stream().collect(toMap(ic -> ic.innerClass().asInternalName(), ic -> InnerClassRecord.ofInnerClassInfo(ic)))),
                    af.findAndMap(Attributes.METHOD_PARAMETERS, a -> a.parameters().stream().map(mp -> MethodParameterRecord.ofMethodParameter(mp)).toList()),
                    af.findAndMap(Attributes.MODULE, a -> ModuleRecord.ofModuleAttribute(a)),
                    af.findAndMap(Attributes.MODULE_HASHES, a -> ModuleHashesRecord.ofModuleHashesAttribute(a)),
                    af.findAndMap(Attributes.MODULE_MAIN_CLASS, a -> a.mainClass().asInternalName()),
                    af.findAndMap(Attributes.MODULE_PACKAGES, a -> a.packages().stream().map(p -> p.name().stringValue()).collect(toSet())),
                    af.findAndMap(Attributes.MODULE_RESOLUTION, a -> a.resolutionFlags()),
                    af.findAndMap(Attributes.MODULE_TARGET, a -> a.targetPlatform().stringValue()),
                    af.findAndMap(Attributes.NEST_HOST, a -> a.nestHost().asInternalName()),
                    af.findAndMap(Attributes.NEST_MEMBERS, a -> a.nestMembers().stream().map(m -> m.asInternalName()).collect(toSet())),
                    af.findAndMap(Attributes.PERMITTED_SUBCLASSES, a -> a.permittedSubclasses().stream().map(e -> e.asInternalName()).collect(toSet())),
                    af.findAndMap(RECORD, a -> a.components().stream().map(rc -> RecordComponentRecord.ofRecordComponent(rc, cf)).toList()),
                    af.findAll(Attributes.RUNTIME_VISIBLE_ANNOTATIONS).flatMap(a -> a.annotations().stream()).map(AnnotationRecord::ofAnnotation).collect(toSetOrNull()),
                    af.findAll(Attributes.RUNTIME_INVISIBLE_ANNOTATIONS).flatMap(a -> a.annotations().stream()).map(AnnotationRecord::ofAnnotation).collect(toSetOrNull()),
                    af.findAndMap(Attributes.RUNTIME_VISIBLE_PARAMETER_ANNOTATIONS, a -> a.parameterAnnotations().stream().map(list -> list.stream().map(AnnotationRecord::ofAnnotation).collect(toSet())).toList()),
                    af.findAndMap(Attributes.RUNTIME_INVISIBLE_PARAMETER_ANNOTATIONS, a -> a.parameterAnnotations().stream().map(list -> list.stream().map(AnnotationRecord::ofAnnotation).collect(toSet())).toList()),
                    af.findAndMap(Attributes.RUNTIME_VISIBLE_TYPE_ANNOTATIONS, a -> a.annotations().stream().map(TypeAnnotationRecord::ofTypeAnnotation).collect(toSet())),
                    af.findAndMap(Attributes.RUNTIME_INVISIBLE_TYPE_ANNOTATIONS, a -> a.annotations().stream().map(TypeAnnotationRecord::ofTypeAnnotation).collect(toSet())),
                    af.findAndMap(Attributes.SIGNATURE, a -> a.signature().stringValue()),
                    af.findAndMap(Attributes.SOURCE_DEBUG_EXTENSION, a -> new String(a.contents(), StandardCharsets.UTF_8)),
                    af.findAndMap(Attributes.SOURCE_FILE, a -> a.sourceFile().stringValue()),
                    af.findAndMap(Attributes.SOURCE_ID, a -> a.sourceId().stringValue()),
                    af.findAndMap(Attributes.SYNTHETIC, a -> DefinedValue.DEFINED));
        }
    }

    public record CodeAttributesRecord(
            Set<CharacterRangeRecord> characterRangeTableAttribute,
            Set<LineNumberRecord> lineNumbersTableAttribute,
            Set<LocalVariableRecord> localVariableTableAttribute,
            Set<LocalVariableTypeRecord> localVariableTypeTableAttribute,
            Set<TypeAnnotationRecord> runtimeVisibleTypeAnnotationsAttribute,
            Set<TypeAnnotationRecord> runtimeInvisibleTypeAnnotationsAttribute) {

        static CodeAttributesRecord ofStreamingElements(Supplier<Stream<? extends ClassFileElement>> elements, CodeAttribute lc, CodeNormalizerHelper code, CompatibilityFilter... cf) {
            int[] p = {0};
            var characterRanges = new HashSet<CharacterRangeRecord>();
            var lineNumbers = new HashSet<LineNumberRecord>();
            var localVariables = new HashSet<LocalVariableRecord>();
            var localVariableTypes = new HashSet<LocalVariableTypeRecord>();
            var visibleTypeAnnos = new HashSet<TypeAnnotationRecord>();
            var invisibleTypeAnnos = new HashSet<TypeAnnotationRecord>();
            elements.get().forEach(e -> {
                switch (e) {
                    case Instruction ins -> p[0] += ins.sizeInBytes();
                    case CharacterRange cr -> characterRanges.add(CharacterRangeRecord.ofCharacterRange(cr, lc, code));
                    case LineNumber ln -> lineNumbers.add(new LineNumberRecord(ln.line(), code.targetIndex(p[0])));
                    case LocalVariable lv -> localVariables.add(LocalVariableRecord.ofLocalVariable(lv, lc, code));
                    case LocalVariableType lvt -> localVariableTypes.add(LocalVariableTypeRecord.ofLocalVariableType(lvt, lc, code));
                    case RuntimeVisibleTypeAnnotationsAttribute taa -> taa.annotations().forEach(ann -> visibleTypeAnnos.add(TypeAnnotationRecord.ofTypeAnnotation(ann, lc, code)));
                    case RuntimeInvisibleTypeAnnotationsAttribute taa -> taa.annotations().forEach(ann -> invisibleTypeAnnos.add(TypeAnnotationRecord.ofTypeAnnotation(ann, lc, code)));
                    default -> {}
                }});
            return new CodeAttributesRecord(
                    characterRanges.isEmpty() ? null : characterRanges,
                    lineNumbers.isEmpty() ? null : lineNumbers,
                    localVariables.isEmpty() ? null : localVariables,
                    localVariableTypes.isEmpty() ? null : localVariableTypes,
                    visibleTypeAnnos.isEmpty() ? null : visibleTypeAnnos,
                    invisibleTypeAnnos.isEmpty() ? null : invisibleTypeAnnos);
        }

        static CodeAttributesRecord ofAttributes(AttributeFinder af, CodeNormalizerHelper code, CodeAttribute lr, CompatibilityFilter... cf) {
            return new CodeAttributesRecord(
                    af.findAll(Attributes.CHARACTER_RANGE_TABLE).flatMap(a -> a.characterRangeTable().stream()).map(cr -> CharacterRangeRecord.ofCharacterRange(cr, code)).collect(toSetOrNull()),
                    af.findAll(Attributes.LINE_NUMBER_TABLE).flatMap(a -> a.lineNumbers().stream()).map(ln -> new LineNumberRecord(ln.lineNumber(), code.targetIndex(ln.startPc()))).collect(toSetOrNull()),
                    af.findAll(Attributes.LOCAL_VARIABLE_TABLE).flatMap(a -> a.localVariables().stream()).map(lv -> LocalVariableRecord.ofLocalVariableInfo(lv, code)).collect(toSetOrNull()),
                    af.findAll(Attributes.LOCAL_VARIABLE_TYPE_TABLE).flatMap(a -> a.localVariableTypes().stream()).map(lv -> LocalVariableTypeRecord.ofLocalVariableTypeInfo(lv, code)).collect(toSetOrNull()),
                    af.findAndMap(Attributes.RUNTIME_VISIBLE_TYPE_ANNOTATIONS, a -> a.annotations().stream().map(ann -> TypeAnnotationRecord.ofTypeAnnotation(ann, lr, code)).collect(toSet())),
                    af.findAndMap(Attributes.RUNTIME_INVISIBLE_TYPE_ANNOTATIONS, a -> a.annotations().stream().map(ann -> TypeAnnotationRecord.ofTypeAnnotation(ann, lr, code)).collect(toSet())));
        }
    }

    public record AnnotationRecord(
            String type,
            Map<String, ElementValueRecord> elementValues) {

        public static AnnotationRecord ofAnnotation(Annotation ann) {
            return new AnnotationRecord(
                    ann.className().stringValue(),
                    ann.elements().stream().collect(toMap(evp -> evp.name().stringValue(), evp -> ElementValueRecord.ofElementValue(evp.value()))));
        }
    }

    public record BootstrapMethodRecord(
            ConstantPoolEntryRecord methodHandle,
            List<ConstantPoolEntryRecord> arguments) {

        public static BootstrapMethodRecord ofBootstrapMethodEntry(BootstrapMethodEntry bm) {
            return new BootstrapMethodRecord(
                    ConstantPoolEntryRecord.ofCPEntry(bm.bootstrapMethod()),
                    bm.arguments().stream().map(arg -> ConstantPoolEntryRecord.ofCPEntry(arg)).toList());
        }
    }

    public record CharacterRangeRecord(
            int startIndex,
            int endIndex,
            int characterRangeStart,
            int characterRangeEnd,
            int flags) {

        public static CharacterRangeRecord ofCharacterRange(CharacterRange cr, CodeAttribute lc, CodeNormalizerHelper code) {
            return new CharacterRangeRecord(code.targetIndex(lc.labelToBci(cr.startScope())), code.targetIndex(lc.labelToBci(cr.endScope())), cr.characterRangeStart(), cr.characterRangeEnd(), cr.flags());
        }

        public static CharacterRangeRecord ofCharacterRange(CharacterRangeInfo cr, CodeNormalizerHelper code) {
            return new CharacterRangeRecord(
                    code.targetIndex(cr.startPc()),
                    code.targetIndex(cr.endPc() + 1), cr.characterRangeStart(), cr.characterRangeEnd(), cr.flags());
        }
    }

    private static String opcodeMask(String opcode) {
        return switch (opcode) {
            case "BIPUSH", "SIPUSH" -> "IPUSH";
            case "ICONST_M1" -> "IPUSH#fff";
            case "ICONST_0" -> "IPUSH#0";
            case "ICONST_1" -> "IPUSH#1";
            case "ICONST_2" -> "IPUSH#2";
            case "ICONST_3" -> "IPUSH#3";
            case "ICONST_4" -> "IPUSH#4";
            case "ICONST_5" -> "IPUSH#5";
            case "MULTIANEWARRAY" -> "NEWARRAY";
            case "ANEWARRAY" -> "NEWARRAY";
            default -> {
                if (opcode.endsWith("_W")) {
                    yield opcode.substring(0, opcode.length() - 2);
                } else if (opcode.contains("LOAD_") || opcode.contains("STORE_")) {
                    yield opcode.replace('_', '#');
                } else {
                    yield opcode;
                }
            }
        };
    }

    private static final class CodeNormalizerHelper {

        private static final byte[] LENGTHS = new byte[] {
            1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 3, 2, 3, 3, 2 | (4 << 4), 2 | (4 << 4), 2 | (4 << 4), 2 | (4 << 4), 2 | (4 << 4), 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
            2 | (4 << 4), 2 | (4 << 4), 2 | (4 << 4), 2 | (4 << 4), 2 | (4 << 4), 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
            1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 3 | (6 << 4), 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 2 | (4 << 4), 0, 0, 1, 1, 1,
            1, 1, 1, 3, 3, 3, 3, 3, 3, 3, 5, 5, 3, 2, 3, 1, 1, 3, 3, 1, 1, 0, 4, 3, 3, 5, 5, 0, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 1, 4, 4, 4, 2, 4, 3, 3, 0, 0, 1, 3, 2, 3, 3, 3, 1, 2, 1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1
        };

        private static int instrLen(byte[] code, int pc) {
            int op = code[pc] & 0xff;
            int aligned = (pc + 4) & ~3;
            int len = switch (op) {
                case WIDE -> LENGTHS[code[pc + 1] & 0xff] >> 4;
                case TABLESWITCH -> aligned - pc + (3 + getInt(code, aligned + 2 * 4) - getInt(code, aligned + 1 * 4) + 1) * 4;
                case LOOKUPSWITCH -> aligned - pc + (2 + 2 * getInt(code, aligned + 4)) * 4;
                default -> LENGTHS[op] & 0xf;
            };
            if (len < 1) throw new AssertionError(pc +": " + op);
            return len;
        }

        private static int getInt(byte[] bytes, int off) {
            return bytes[off] << 24 | (bytes[off + 1] & 0xFF) << 16 | (bytes[off + 2] & 0xFF) << 8 | (bytes[off + 3] & 0xFF);
        }


        private final int[] codeToIndexMap;

        CodeNormalizerHelper(byte[] code) {
            this.codeToIndexMap = new int[code.length + 1];
            for (int pc = 1; pc < code.length; pc++) codeToIndexMap[pc] = -pc;
            int index = 0;
            for (int pc = 0; pc < code.length; pc += instrLen(code, pc)) {
                codeToIndexMap[pc] = index++;
            }
            codeToIndexMap[code.length] = index;
        }

        int targetIndex(int pc) {
            if (pc < 0) return pc;
            if (pc > codeToIndexMap.length) return -pc;
            return codeToIndexMap[pc];
        }

        int multipleTargetsHash(int pc, int firstOffset, int[] otherOffsets, int... otherHashes) {
            int hash = targetIndex(pc + firstOffset);
            for (var off : otherOffsets) {
                hash = 31*hash + targetIndex(pc + off);
            }
            for (var other : otherHashes) {
                hash = 31*hash + other;
            }
            return hash;
        }

        int hash(int from, int length) {
            int result = 1;
            for (int i = from; i < length; i++) {
                int elementHash = (codeToIndexMap[i] ^ (codeToIndexMap[i] >>> 32));
                result = 31 * result + elementHash;
            }
            return result;
        }
    }

    public record CodeRecord(
            Integer maxStack,
            Integer maxLocals,
            Integer  codeLength,
            List<String> instructionsSequence,
            Set<ExceptionHandlerRecord> exceptionHandlers,
            CodeAttributesRecord codeAttributes) {

        private static List<String> instructions(Supplier<Stream<? extends ClassFileElement>> elements, CodeNormalizerHelper code, CodeAttribute lr) {
            int[] p = {0};
            return elements.get().filter(e -> e instanceof Instruction).map(e -> {
                var ins = (Instruction)e;
                String opCode = opcodeMask(ins.opcode().name());
                Integer hash = switch (ins) {
                    case FieldInstruction cins ->
                        ConstantPoolEntryRecord.ofCPEntry(cins.field()).hashCode();
                    case InvokeInstruction cins ->
                        ConstantPoolEntryRecord.ofCPEntry(cins.method()).hashCode();
                    case NewObjectInstruction cins ->
                        ConstantPoolEntryRecord.ofCPEntry(cins.className()).hashCode();
                    case NewReferenceArrayInstruction cins -> {
                        String type = cins.componentType().asInternalName();
                        if (!type.startsWith("["))
                            type = "L" + type + ";";
                        yield new ConstantPoolEntryRecord.CpClassRecord("[" + type).hashCode() + 1;
                    }
                    case NewPrimitiveArrayInstruction cins ->
                        new ConstantPoolEntryRecord.CpClassRecord("[" + cins.typeKind().descriptor()).hashCode() + 1;
                    case TypeCheckInstruction cins ->
                        ConstantPoolEntryRecord.ofCPEntry(cins.type()).hashCode();
                    case ConstantInstruction.LoadConstantInstruction cins -> {
                        var cper = ConstantPoolEntryRecord.ofCPEntry(cins.constantEntry());
                        String altOpcode = cper.altOpcode();
                        if (altOpcode != null) {
                            opCode = altOpcode;
                            yield null;
                        }
                        else {
                            yield cper.hashCode();
                        }
                    }
                    case InvokeDynamicInstruction cins ->
                        ConstantPoolEntryRecord.ofCPEntry(cins.invokedynamic()).hashCode();
                    case NewMultiArrayInstruction cins ->
                            ConstantPoolEntryRecord.ofCPEntry(cins.arrayType()).hashCode() + cins.dimensions();
                    case BranchInstruction cins ->
                        code.targetIndex(lr.labelToBci(cins.target()));
                    case LookupSwitchInstruction cins ->
                        code.multipleTargetsHash(p[0], lr.labelToBci(cins.defaultTarget()) - p[0], cins.cases().stream().mapToInt(sc -> lr.labelToBci(sc.target()) - p[0]).toArray(), cins.cases().stream().mapToInt(SwitchCase::caseValue).toArray());
                    case TableSwitchInstruction cins ->
                        code.multipleTargetsHash(p[0], lr.labelToBci(cins.defaultTarget()) - p[0], cins.cases().stream().mapToInt(sc -> lr.labelToBci(sc.target()) - p[0]).toArray(), cins.lowValue(), cins.highValue());
                    case ConstantInstruction.ArgumentConstantInstruction cins ->
                        cins.constantValue();
                    default -> {
                        if (ins.sizeInBytes() <= 1) {
                            yield null;
                        }
                        else if ((ins instanceof LoadInstruction local)) {
                            yield local.slot();
                        }
                        else if ((ins instanceof StoreInstruction local)) {
                            yield local.slot();
                        }
                        else {
                            yield code.hash(p[0] + 1, ins.sizeInBytes());
                        }
                    }
                };
                p[0] += ins.sizeInBytes();
                return opCode + (hash != null ? '#' + Integer.toHexString(hash & 0xfff) : "");
            }).toList();
        }

        public static CodeRecord ofStreamingElements(int maxStack, int maxLocals, int codeLength, Supplier<Stream<? extends ClassFileElement>> elements, CodeAttribute lc, CodeNormalizerHelper codeHelper, CompatibilityFilter... cf) {
            return new CodeRecord(
                    By_ClassBuilder.isNotDirectlyComparable(cf, maxStack),
                    By_ClassBuilder.isNotDirectlyComparable(cf, maxLocals),
                    By_ClassBuilder.isNotDirectlyComparable(cf, codeLength),
                    instructions(elements, codeHelper, lc),
                    elements.get().filter(e -> e instanceof ExceptionCatch).map(eh -> ExceptionHandlerRecord.ofExceptionCatch((ExceptionCatch)eh, codeHelper, lc)).collect(toSet()),
                    CodeAttributesRecord.ofStreamingElements(elements, lc, codeHelper, cf));
        }

        public static CodeRecord ofCodeAttribute(CodeAttribute a, CompatibilityFilter... cf) {
            var codeHelper = new CodeNormalizerHelper(a.codeArray());
            return new CodeRecord(
                    By_ClassBuilder.isNotDirectlyComparable(cf, a.maxStack()),
                    By_ClassBuilder.isNotDirectlyComparable(cf, a.maxLocals()),
                    By_ClassBuilder.isNotDirectlyComparable(cf, a.codeLength()),
                    instructions(a::elementStream, codeHelper, a),
                    a.exceptionHandlers().stream().map(eh -> ExceptionHandlerRecord.ofExceptionCatch(eh, codeHelper, a)).collect(toSet()),
                    CodeAttributesRecord.ofAttributes(a::attributes, codeHelper, a, cf));
        }

        public record ExceptionHandlerRecord(
                int startIndex,
                int endIndex,
                int handlerIndex,
                ConstantPoolEntryRecord catchType) {

            public static ExceptionHandlerRecord ofExceptionCatch(ExceptionCatch et, CodeNormalizerHelper code, CodeAttribute labelContext) {
                return new ExceptionHandlerRecord(
                        code.targetIndex(labelContext.labelToBci(et.tryStart())),
                        code.targetIndex(labelContext.labelToBci(et.tryEnd())),
                        code.targetIndex(labelContext.labelToBci(et.handler())),
                        et.catchType().map(ct -> ConstantPoolEntryRecord.ofCPEntry(ct)).orElse(null));
            }
        }
    }

    public record EnclosingMethodRecord(
            String className,
            ConstantPoolEntryRecord method) {

        public static EnclosingMethodRecord ofEnclosingMethodAttribute(EnclosingMethodAttribute ema) {
            return new EnclosingMethodRecord(
                    ema.enclosingClass().asInternalName(),
                    ema.enclosingMethod().map(m -> ConstantPoolEntryRecord.ofCPEntry(m)).orElse(null));
        }
    }

    public record InnerClassRecord(
            String innerClass,
            String innerName,
            String outerClass,
            String accessFlags) {

        public static InnerClassRecord ofInnerClassInfo(InnerClassInfo ic) {
            return new InnerClassRecord(
                    ic.innerClass().asInternalName(),
                    ic.innerName().map(Utf8Entry::stringValue).orElse(null),
                    ic.outerClass().map(ClassEntry::asInternalName).orElse(null),
                    Flags.toString(ic.flagsMask(), false));
        }
    }

    public record LineNumberRecord(
            int lineNumber,
            int startIndex) {}

    public record LocalVariableRecord(
            int startIndex,
            int endIndex,
            String name,
            String descriptor,
            int slot) {

        public static LocalVariableRecord ofLocalVariable(LocalVariable lv, CodeAttribute lc, CodeNormalizerHelper code) {
            return new LocalVariableRecord(
                    code.targetIndex(lc.labelToBci(lv.startScope())),
                    code.targetIndex(lc.labelToBci(lv.endScope())),
                    lv.name().stringValue(),
                    lv.type().stringValue(),
                    lv.slot());
        }

        public static LocalVariableRecord ofLocalVariableInfo(LocalVariableInfo lv, CodeNormalizerHelper code) {
            return new LocalVariableRecord(
                    code.targetIndex(lv.startPc()),
                    code.targetIndex(lv.startPc() + lv.length()),
                    lv.name().stringValue(),
                    lv.type().stringValue(),
                    lv.slot());
        }
    }

    public record LocalVariableTypeRecord(
            int startIndex,
            int endIndex,
            String name,
            String signature,
            int index) {

        public static LocalVariableTypeRecord ofLocalVariableType(LocalVariableType lvt, CodeAttribute lc, CodeNormalizerHelper code) {
            return new LocalVariableTypeRecord(
                    code.targetIndex(lc.labelToBci(lvt.startScope())),
                    code.targetIndex(lc.labelToBci(lvt.endScope())),
                    lvt.name().stringValue(),
                    lvt.signature().stringValue(),
                    lvt.slot());
        }

        public static LocalVariableTypeRecord ofLocalVariableTypeInfo(LocalVariableTypeInfo lvt, CodeNormalizerHelper code) {
            return new LocalVariableTypeRecord(
                    code.targetIndex(lvt.startPc()),
                    code.targetIndex(lvt.startPc() + lvt.length()),
                    lvt.name().stringValue(),
                    lvt.signature().stringValue(),
                    lvt.slot());
        }
    }

    public record MethodParameterRecord(
            String name,
            int accessFlags) {

        public static MethodParameterRecord ofMethodParameter(MethodParameterInfo mp) {
            return new MethodParameterRecord(mp.name().map(Utf8Entry::stringValue).orElse(null), mp.flagsMask());
        }
    }

    public record ModuleRecord(
            String moduleName,
            int moduleFlags,
            String moduleVersion,
            Set<RequiresRecord> requires,
            Set<ExportsRecord> exports,
            Set<OpensRecord> opens,
            Set<String> uses,
            Set<ProvidesRecord> provides) {

        public static ModuleRecord ofModuleAttribute(ModuleAttribute m) {
            return new ModuleRecord(
                    m.moduleName().name().stringValue(),
                    m.moduleFlagsMask(),
                    m.moduleVersion().map(mv -> mv.stringValue()).orElse(null),
                    m.requires().stream().map(r -> RequiresRecord.ofRequire(r)).collect(toSet()),
                    m.exports().stream().map(e -> ExportsRecord.ofExport(e)).collect(toSet()),
                    m.opens().stream().map(o -> OpensRecord.ofOpen(o)).collect(toSet()),
                    m.uses().stream().map(u -> u.asInternalName()).collect(toSet()),
                    m.provides().stream().map(p -> ProvidesRecord.ofProvide(p)).collect(toSet()));
        }

        public record RequiresRecord(
                String requires,
                int requiresFlags,
                String requiresVersion) {

            public static RequiresRecord ofRequire(ModuleRequireInfo r) {
                return new RequiresRecord(r.requires().name().stringValue(), r.requiresFlagsMask(), r.requiresVersion().map(v -> v.stringValue()).orElse(null));
            }
        }

        public record ExportsRecord(
                String exports,
                int exportFlag,
                Set<String> exportsTo) {

            public static ExportsRecord ofExport(ModuleExportInfo e) {
                return new ExportsRecord(
                        e.exportedPackage().name().stringValue(),
                        e.exportsFlagsMask(),
                        e.exportsTo().stream().map(to -> to.name().stringValue()).collect(toSet()));
            }
        }

        public record OpensRecord(
                String opens,
                int opensFlag,
                Set<String> opensTo) {

            public static OpensRecord ofOpen(ModuleOpenInfo o) {
                return new OpensRecord(
                        o.openedPackage().name().stringValue(),
                        o.opensFlagsMask(),
                        o.opensTo().stream().map(to -> to.name().stringValue()).collect(toSet()));
            }
        }

        public record ProvidesRecord(
                String provides,
                Set<String> providesWith) {

            public static ProvidesRecord ofProvide(ModuleProvideInfo p) {
                return new ProvidesRecord(
                        p.provides().asInternalName(),
                        p.providesWith().stream().map(w -> w.asInternalName()).collect(toSet()));
            }
        }

    }

    public record ModuleHashesRecord(
            String algorithm,
            Map<String, String> hashes) {

        public static ModuleHashesRecord ofModuleHashesAttribute(ModuleHashesAttribute mh) {
            return new ModuleHashesRecord(
                    mh.algorithm().stringValue(),
                    mh.hashes().stream().collect(toMap(e -> e.moduleName().name().stringValue(), e -> new BigInteger(1, e.hash()).toString(16))));
        }
    }

    public record RecordComponentRecord(
            String name,
            String descriptor,
            AttributesRecord attributes) {

        public static RecordComponentRecord ofRecordComponent(RecordComponentInfo rc, CompatibilityFilter... compatibilityFilter) {
            return new RecordComponentRecord(rc.name().stringValue(), rc.descriptor().stringValue(),
                                             AttributesRecord.ofAttributes(rc::attributes, compatibilityFilter));
        }
    }

    public enum FrameTypeEnum {
        SAME(0, 63),
        SAME_LOCALS_1_STACK_ITEM(64, 127),
        RESERVED_FOR_FUTURE_USE(128, 246),
        SAME_LOCALS_1_STACK_ITEM_EXTENDED(247, 247),
        CHOP(248, 250),
        SAME_FRAME_EXTENDED(251, 251),
        APPEND(252, 254),
        FULL_FRAME(255, 255);

        int start;
        int end;

        public static FrameTypeEnum of(int frameType) {
            for (var e : FrameTypeEnum.values()) {
                if (e.start <= frameType && e.end >= frameType) return e;
            }
            throw new IllegalArgumentException("Invalid frame type: " + frameType);
        }

        FrameTypeEnum(int start, int end) {
            this.start = start;
            this.end = end;
        }
    }

    public record TypeAnnotationRecord(
            int targetType,
            TargetInfoRecord targetInfo,
            Set<TypePathRecord> targetPath,
            AnnotationRecord annotation) {

        public static TypeAnnotationRecord ofTypeAnnotation(TypeAnnotation ann) {
            return ofTypeAnnotation(ann, null, null);
        }

        public static TypeAnnotationRecord ofTypeAnnotation(TypeAnnotation ann, CodeAttribute lr, CodeNormalizerHelper code) {
            return new TypeAnnotationRecord(
                    ann.targetInfo().targetType().targetTypeValue(),
                    TargetInfoRecord.ofTargetInfo(ann.targetInfo(), lr, code),
                    ann.targetPath().stream().map(tpc -> TypePathRecord.ofTypePathComponent(tpc)).collect(toSet()),
                    AnnotationRecord.ofAnnotation(ann));
        }

        public interface TargetInfoRecord {

            public static TargetInfoRecord ofTargetInfo(TypeAnnotation.TargetInfo tiu, CodeAttribute lr, CodeNormalizerHelper code) {
                if (tiu instanceof TypeAnnotation.CatchTarget ct) {
                    return new CatchTargetRecord(ct.exceptionTableIndex());
                } else if (tiu instanceof TypeAnnotation.EmptyTarget et) {
                    return new EmptyTargetRecord();
                } else if (tiu instanceof TypeAnnotation.FormalParameterTarget fpt) {
                    return new FormalParameterTargetRecord(fpt.formalParameterIndex());
                } else if (tiu instanceof TypeAnnotation.LocalVarTarget lvt) {
                    return new LocalVarTargetRecord(lvt.table().stream().map(ent
                            -> new LocalVarTargetRecord.EntryRecord(code.targetIndex(lr.labelToBci(ent.startLabel())), code.targetIndex(lr.labelToBci(ent.endLabel())), ent.index())).collect(toSet()));
                } else if (tiu instanceof TypeAnnotation.OffsetTarget ot) {
                    return new OffsetTargetRecord(code.targetIndex(lr.labelToBci(ot.target())));
                } else if (tiu instanceof TypeAnnotation.SupertypeTarget st) {
                    return new SupertypeTargetRecord(st.supertypeIndex());
                } else if (tiu instanceof TypeAnnotation.ThrowsTarget tt) {
                    return new ThrowsTargetRecord(tt.throwsTargetIndex());
                } else if (tiu instanceof TypeAnnotation.TypeArgumentTarget tat) {
                    return new TypeArgumentTargetRecord(code.targetIndex(lr.labelToBci(tat.target())), tat.typeArgumentIndex());
                } else if (tiu instanceof TypeAnnotation.TypeParameterBoundTarget tpbt) {
                    return new TypeParameterBoundTargetRecord(tpbt.typeParameterIndex(), tpbt.boundIndex());
                } else if (tiu instanceof TypeAnnotation.TypeParameterTarget tpt) {
                    return new TypeParameterTargetRecord(tpt.typeParameterIndex());
                } else {
                    throw new IllegalArgumentException(tiu.getClass().getName());
                }
            }

            public record CatchTargetRecord(int exceptionTableIndex) implements TargetInfoRecord{}

            public record EmptyTargetRecord() implements TargetInfoRecord {}

            public record FormalParameterTargetRecord(int formalParameterIndex) implements TargetInfoRecord {}

            public record LocalVarTargetRecord(Set<LocalVarTargetRecord.EntryRecord> table) implements TargetInfoRecord {

                public record EntryRecord(int startPC, int length, int index) {}
            }

            public record OffsetTargetRecord(int offset) implements TargetInfoRecord {}

            public record SupertypeTargetRecord(int supertypeIndex) implements TargetInfoRecord {}

            public record ThrowsTargetRecord(int throwsTargetIndex) implements TargetInfoRecord {}

            public record TypeArgumentTargetRecord(int offset, int typeArgumentIndex) implements TargetInfoRecord {}

            public record TypeParameterBoundTargetRecord(int typeParameterIndex, int boundIndex) implements TargetInfoRecord {}

            public record TypeParameterTargetRecord(int typeParameterIndex) implements TargetInfoRecord {}
        }

        public record TypePathRecord(
                int typePathKind,
                int typeArgumentIndex) {

            public static TypePathRecord ofTypePathComponent(TypeAnnotation.TypePathComponent tpc) {
                return new TypePathRecord(tpc.typePathKind().tag(), tpc.typeArgumentIndex());
            }
        }
    }

    public interface ConstantPoolEntryRecord {

        public static ConstantPoolEntryRecord ofCPEntry(PoolEntry cpInfo) {
            return switch (cpInfo.tag()) {
                case TAG_UTF8 ->
                    new CpUTF8Record(((Utf8Entry) cpInfo).stringValue());
                case TAG_INTEGER ->
                    new CpIntegerRecord(((IntegerEntry) cpInfo).intValue());
                case TAG_FLOAT ->
                    new CpFloatRecord(((FloatEntry) cpInfo).floatValue());
                case TAG_LONG ->
                    new CpLongRecord(((LongEntry) cpInfo).longValue());
                case TAG_DOUBLE ->
                    new CpDoubleRecord(((DoubleEntry) cpInfo).doubleValue());
                case TAG_CLASS ->
                    new CpClassRecord(((ClassEntry) cpInfo).asInternalName());
                case TAG_STRING ->
                    new CpStringRecord(((StringEntry) cpInfo).stringValue());
                case TAG_FIELDREF ->
                    CpFieldRefRecord.ofFieldRefEntry((FieldRefEntry) cpInfo);
                case TAG_METHODREF ->
                    CpMethodRefRecord.ofMethodRefEntry((MethodRefEntry) cpInfo);
                case TAG_INTERFACEMETHODREF ->
                    CpInterfaceMethodRefRecord.ofInterfaceMethodRefEntry((InterfaceMethodRefEntry) cpInfo);
                case TAG_NAMEANDTYPE ->
                    CpNameAndTypeRecord.ofNameAndTypeEntry((NameAndTypeEntry) cpInfo);
                case TAG_METHODHANDLE ->
                    CpMethodHandleRecord.ofMethodHandleEntry((MethodHandleEntry) cpInfo);
                case TAG_METHODTYPE ->
                    new CpMethodTypeRecord(((MethodTypeEntry) cpInfo).descriptor().stringValue());
                case TAG_CONSTANTDYNAMIC ->
                    CpConstantDynamicRecord.ofConstantDynamicEntry((ConstantDynamicEntry) cpInfo);
                case TAG_INVOKEDYNAMIC ->
                    CpInvokeDynamicRecord.ofInvokeDynamicEntry((InvokeDynamicEntry) cpInfo);
                case TAG_MODULE ->
                    new CpModuleRecord(((ModuleEntry) cpInfo).name().stringValue());
                case TAG_PACKAGE ->
                    new CpPackageRecord(((PackageEntry) cpInfo).name().stringValue());
                default -> throw new IllegalArgumentException(Integer.toString(cpInfo.tag()));
            };
        }

        default String altOpcode() {
            return null;
        }

        public record CpUTF8Record(String cpUTF8) implements ConstantPoolEntryRecord {}

        public record CpIntegerRecord(int cpInteger) implements ConstantPoolEntryRecord {
            @Override
            public String altOpcode() {
                return "IPUSH#" + Integer.toHexString(cpInteger & 0xfff);
            }
        }

        public record CpFloatRecord(float cpFloat) implements ConstantPoolEntryRecord {
            @Override
            public String altOpcode() {
                return cpFloat == 0.0f ? "FCONST_0" :
                          cpFloat == 1.0f ? "FCONST_1" :
                          cpFloat == 2.0f ? "FCONST_2" : null;
            }
        }

        public record CpLongRecord(long cpLong) implements ConstantPoolEntryRecord {
            @Override
            public String altOpcode() {
                return cpLong == 0 ? "LCONST_0" :
                          cpLong == 1 ? "LCONST_1" : null;
            }
        }

        public record CpDoubleRecord(double cpDouble) implements ConstantPoolEntryRecord {
            @Override
            public String altOpcode() {
                return cpDouble == 0.0 ? "DCONST_0" :
                          cpDouble == 1.0 ? "DCONST_1" : null;
            }
        }

        public record CpClassRecord(String cpClass) implements ConstantPoolEntryRecord {}

        public record CpStringRecord(String cpString) implements ConstantPoolEntryRecord {}

        public record CpFieldRefRecord(
                String cpFieldRefClass,
                String cpFieldRefName,
                String cpFieldRefType) implements ConstantPoolEntryRecord {

            public static CpFieldRefRecord ofFieldRefEntry(FieldRefEntry cpInfo) {
                return new CpFieldRefRecord(cpInfo.owner().asInternalName(), cpInfo.nameAndType().name().stringValue(), cpInfo.nameAndType().type().stringValue());
            }
        }

        public record CpMethodRefRecord(
                String cpMethodRefClass,
                String cpMethodRefName,
                String cpMethodRefType) implements ConstantPoolEntryRecord {

            public static CpMethodRefRecord ofMethodRefEntry(MethodRefEntry cpInfo) {
                return new CpMethodRefRecord(cpInfo.owner().asInternalName(), cpInfo.nameAndType().name().stringValue(), cpInfo.nameAndType().type().stringValue());
            }
        }

        public record CpInterfaceMethodRefRecord(
                String cpInterfaceMethodRefClass,
                String cpInterfaceMethodRefName,
                String cpInterfaceMethodRefType) implements ConstantPoolEntryRecord {

            public static CpInterfaceMethodRefRecord ofInterfaceMethodRefEntry(InterfaceMethodRefEntry cpInfo) {
                return new CpInterfaceMethodRefRecord(cpInfo.owner().asInternalName(), cpInfo.nameAndType().name().stringValue(), cpInfo.nameAndType().type().stringValue());
            }
        }

        public record CpNameAndTypeRecord(
                String cpNameAndTypeName,
                String cpNameAndTypeType) implements ConstantPoolEntryRecord {

            public static CpNameAndTypeRecord ofNameAndTypeEntry(NameAndTypeEntry cpInfo) {
                return new CpNameAndTypeRecord(cpInfo.name().stringValue(), cpInfo.type().stringValue());
            }
        }

        public record CpMethodHandleRecord(
                ConstantPoolEntryRecord cpHandleReference,
                int cpHandleKind) implements ConstantPoolEntryRecord {

            public static CpMethodHandleRecord ofMethodHandleEntry(MethodHandleEntry cpInfo) {
                return new CpMethodHandleRecord(ConstantPoolEntryRecord.ofCPEntry(cpInfo.reference()), cpInfo.kind());
            }
        }

        public record CpMethodTypeRecord(String cpMethodType) implements ConstantPoolEntryRecord {}

        public record CpConstantDynamicRecord(
                String cpConstantDynamicName,
                String cpConstantDynamicType) implements ConstantPoolEntryRecord {

            public static CpConstantDynamicRecord ofConstantDynamicEntry(ConstantDynamicEntry cpInfo) {
                return new CpConstantDynamicRecord(cpInfo.name().stringValue(), cpInfo.type().stringValue());
            }
        }

        public record CpInvokeDynamicRecord(
                String cpInvokeDynamicName,
                String cpInvokeDynamicType) implements ConstantPoolEntryRecord {

            public static CpInvokeDynamicRecord ofInvokeDynamicEntry(InvokeDynamicEntry cpInfo) {
                return new CpInvokeDynamicRecord(cpInfo.name().stringValue(), cpInfo.type().stringValue());
            }
        }

        public record CpModuleRecord(String cpModule) implements ConstantPoolEntryRecord {}

        public record CpPackageRecord(String cpPackage) implements ConstantPoolEntryRecord {}

    }

    public interface ElementValueRecord {

        public int tag();

        public static ElementValueRecord ofElementValue(AnnotationValue ev) {
            return switch (ev) {
                case AnnotationValue.OfConstant evc -> new EvConstRecord(ev.tag(), ConstantPoolEntryRecord.ofCPEntry(evc.constant()));
                case AnnotationValue.OfEnum enumVal -> new EvEnumConstRecord(ev.tag(), enumVal.className().stringValue(), enumVal.constantName().stringValue());
                case AnnotationValue.OfClass classVal -> new EvClassRecord(ev.tag(), classVal.className().stringValue());
                case AnnotationValue.OfAnnotation ann -> new EvAnnotationRecord(ev.tag(), AnnotationRecord.ofAnnotation(ann.annotation()));
                case AnnotationValue.OfArray evav -> new EvArrayRecord(ev.tag(), evav.values().stream().map(ElementValueRecord::ofElementValue).toList());
                case null, default -> throw new IllegalArgumentException(ev.getClass().getName());
            };
        }

        public record EvAnnotationRecord(
                int tag,
                AnnotationRecord annotation) implements ElementValueRecord {}

        public record EvArrayRecord(
                int tag,
                List<ElementValueRecord> values) implements ElementValueRecord {}

        public record EvClassRecord(
                int tag,
                String classInfo) implements ElementValueRecord {}

        public record EvEnumConstRecord(
                int tag,
                String typeName,
                String constName) implements ElementValueRecord {}

        public record EvConstRecord(
                int tag,
                ConstantPoolEntryRecord constValue) implements ElementValueRecord {
        }
    }

    private enum Flags {
        PUBLIC, PRIVATE, PROTECTED,  STATIC, FINAL, SUPER ("SYNCHRONIZED"), VOLATILE ("BRIDGE"), TRANSIENT ("VARARGS"),
        NATIVE,  INTERFACE, ABSTRACT,  STRICT,  SYNTHETIC,  ANNOTATION,  ENUM,  MODULE ("MANDATED");

        private String alt;

        Flags() {
            this.alt = name();
        }

        Flags(String alt) {
            this.alt = alt;
        }

        public static String toString(int flags, boolean methodFlags) {
            int i=1;
            StringBuilder sb = new StringBuilder();
            for (var cf : values()) {
                if ((flags & i) != 0) {
                    if (sb.length() > 0) sb.append(',');
                    sb.append(methodFlags ? cf.alt : cf.name());
                }
                i <<= 1;
            }
            return sb.toString();
        }
    }

    public static void assertEquals(ClassModel actual, ClassModel expected) {
        assertEqualsDeep(ClassRecord.ofClassModel(actual, By_ClassBuilder),
                ClassRecord.ofClassModel(expected, By_ClassBuilder));
    }

    public static void assertEqualsDeep(Object actual, Object expected) {
        assertEqualsDeep(actual, expected, null, true);
    }

    public static void assertEqualsDeep(Object actual, Object expected, String message) {
        assertEqualsDeep(actual, expected, message, true);
    }

    public static void assertEqualsDeep(Object actual, Object expected, String message, boolean printValues) {
        assertEqualsDeepImpl(actual, expected, message == null ? "" : message + " ", "$", printValues);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void assertEqualsDeepImpl(Object actual, Object expected, String message, String path, boolean printValues) {
        if (actual instanceof Record && expected instanceof Record) {
            assertEqualsDeepImpl(actual.getClass(), expected.getClass(), message, path, printValues);
            for (RecordComponent rc : actual.getClass().getRecordComponents()) {
                try {
                    assertEqualsDeepImpl(rc.getAccessor().invoke(actual), rc.getAccessor().invoke(expected), message, path + "." + rc.getName(), printValues);
                } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
                    throw new AssertionError(message + ex.getLocalizedMessage(), ex);
                }
            }
        } else if (actual instanceof Map actualMap && expected instanceof Map expectedMap) {
            assertEqualsDeepImpl(actualMap.keySet(), expectedMap.keySet(), message, path + "(keys)", printValues);
            actualMap.forEach((key, actualValue) -> {
                assertEqualsDeepImpl(actualValue, expectedMap.get(key), message, path + "." + key, printValues);
            });
        } else if (actual instanceof List actualList && expected instanceof List expectedList) {
            assertEqualsDeepImpl(actualList.size(), expectedList.size(), message, path + "(size)", printValues);
            IntStream.range(0, actualList.size()).forEach(i -> assertEqualsDeepImpl(actualList.get(i), expectedList.get(i), message, path + "[" + i +"]", printValues));
        } else {
            if (actual instanceof Set actualSet && expected instanceof Set expectedSet) {
                actual = actualSet.stream().filter(e -> !expectedSet.contains(e)).collect(toSet());
                expected = expectedSet.stream().filter(e -> !actualSet.contains(e)).collect(toSet());
            }
            if (!Objects.equals(actual, expected)) {
                throw new AssertionError(message + "not equal on path [" + path + "]");
//                        + (printValues ? "\nexpected: " + prettyPrintToJson(expected) + "\nbut found: " + prettyPrintToJson(actual) : ""));
            }
        }
    }

//    @Override
//    public String toString() {
//        return prettyPrintToJson(this);
//    }
//
//    private static final JsonWriterFactory JWF = Json.createWriterFactory(Map.of(JsonGenerator.PRETTY_PRINTING, true));
//
//    public static String prettyPrintToJson(Object o) {
//        var stringWriter = new StringWriter();
//        try ( var jsonWriter = JWF.createWriter(stringWriter)) {
//            jsonWriter.write(toJson(o));
//        }
//        return stringWriter.toString();
//    }
//
//    @SuppressWarnings({"unchecked", "rawtypes"})
//    private static JsonValue toJson(Object o) {
//        if (o == null) {
//            return JsonValue.NULL;
//        } else if (o instanceof Record) {
//            var b = Json.createObjectBuilder();
//            for (RecordComponent rc : o.getClass().getRecordComponents()) try {
//                var val = rc.getAccessor().invoke(o);
//                if (val != null) {
//                    b.add(rc.getName(), toJson(val));
//                }
//            } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
//                throw new RuntimeException(ex);
//            }
//            return b.build();
//        }
//        if (o instanceof Map map) {
//            var b = Json.createObjectBuilder();
//            map.forEach((k, v) -> b.add(String.valueOf(k), toJson(v)));
//            return b.build();
//        }
//        if (o instanceof Collection col) {
//            var b = Json.createArrayBuilder();
//            col.forEach(e -> b.add(toJson(e)));
//            return b.build();
//        } else if (o instanceof String s) {
//            return Json.createValue(s);
//        } else if (o instanceof Double d) {
//            return Json.createValue(d);
//        } else if (o instanceof Integer i) {
//            return Json.createValue(i);
//        } else if (o instanceof Long l) {
//            return Json.createValue(l);
//        } else {
//            return Json.createValue(o.toString());
//        }
//    }

    private interface SupplierThrowingException<R> {
        R get() throws Exception;
    }

    private static <R> R wrapException(SupplierThrowingException<R> supplier) {
        try {
            return supplier.get();
        } catch (RuntimeException re) {
            throw re;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private interface FunctionThrowingException<P, R> {
        R apply(P p) throws Exception;
    }

    private static <P, R> R map(P value, FunctionThrowingException<P, R> mapper) {
        return map(value, mapper, null);
    }

    private static <P, R> R map(P value, FunctionThrowingException<P, R> mapper, R defaultReturn) {
        try {
            return value == null ? defaultReturn : mapper.apply(value);
        } catch (RuntimeException re) {
            throw re;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private static <P, R> Function<P, R> wrapException(FunctionThrowingException<P, R> function) {
        return p -> {
            try {
                return function.apply(p);
            } catch (RuntimeException re) {
                throw re;
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        };
    }
}
