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

import jdk.classfile.*;
import jdk.classfile.attribute.*;
import jdk.classfile.constantpool.ClassEntry;
import jdk.classfile.constantpool.ConstantDynamicEntry;
import jdk.classfile.constantpool.ConstantPool;
import jdk.classfile.constantpool.DoubleEntry;
import jdk.classfile.constantpool.FieldRefEntry;
import jdk.classfile.constantpool.FloatEntry;
import jdk.classfile.constantpool.IntegerEntry;
import jdk.classfile.constantpool.InterfaceMethodRefEntry;
import jdk.classfile.constantpool.InvokeDynamicEntry;
import jdk.classfile.constantpool.LongEntry;
import jdk.classfile.constantpool.MethodHandleEntry;
import jdk.classfile.constantpool.MethodRefEntry;
import jdk.classfile.constantpool.MethodTypeEntry;
import jdk.classfile.constantpool.ModuleEntry;
import jdk.classfile.constantpool.NameAndTypeEntry;
import jdk.classfile.constantpool.PackageEntry;
import jdk.classfile.constantpool.PoolEntry;
import jdk.classfile.constantpool.StringEntry;
import jdk.classfile.constantpool.Utf8Entry;
import jdk.classfile.instruction.*;

import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import static jdk.classfile.Classfile.*;
import static jdk.classfile.Attributes.*;
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
    public static ClassRecord ofStreamingElements(int majorVersion, int minorVersion, String thisClass, String superClass, Set<String> interfaces, int flags, ConstantPool cp, Supplier<Stream<? extends ClassfileElement>> elements, CompatibilityFilter... compatibilityFilter) {
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

    public static ClassRecord ofClassFile(com.sun.tools.classfile.ClassFile cf, CompatibilityFilter... compatibilityFilter) {
        return wrapException(() -> new ClassRecord(
                cf.major_version,
                cf.minor_version,
                cf.getName(),
                cf.super_class == 0 ? null : cf.getSuperclassName(),
                IntStream.range(0, cf.interfaces.length).boxed().map(wrapException(i -> cf.getInterfaceName(i))).collect(toSet()),
                Flags.toString(cf.access_flags.flags, false),
                Stream.of(cf.fields).collect(toMap(wrapException(f -> f.getName(cf.constant_pool) + f.descriptor.getValue(cf.constant_pool)), f
                        -> FieldRecord.ofClassFileField(f, cf.constant_pool, compatibilityFilter))),
                Stream.of(cf.methods).collect(toMap(wrapException(m -> m.getName(cf.constant_pool) + m.descriptor.getValue(cf.constant_pool)), m
                        -> MethodRecord.ofClassFileMethod(m, cf.constant_pool, compatibilityFilter))),
                AttributesRecord.ofClassFileAttributes(cf.attributes, cf.constant_pool, compatibilityFilter)));
    }

    public record FieldRecord(
            String fieldName,
            String fieldType,
            String fieldFlags,
            AttributesRecord fieldAttributes) {

        public static FieldRecord ofStreamingElements(String fieldName, String fieldType, int flags, Supplier<Stream<? extends ClassfileElement>> elements, CompatibilityFilter... compatibilityFilter) {
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

        public static FieldRecord ofClassFileField(com.sun.tools.classfile.Field f, com.sun.tools.classfile.ConstantPool p, CompatibilityFilter... compatibilityFilter) {
            return wrapException(() -> new FieldRecord(
                    f.getName(p),
                    f.descriptor.getValue(p),
                    Flags.toString(f.access_flags.flags, false),
                    AttributesRecord.ofClassFileAttributes(f.attributes, p, compatibilityFilter)));
        }
    }

    public record MethodRecord(
            String methodName,
            String methodType,
            String methodFlags,
            AttributesRecord methodAttributes) {

        public static MethodRecord ofStreamingElements(String methodName, String methodType, int flags, Supplier<Stream<? extends ClassfileElement>> elements, CompatibilityFilter... compatibilityFilter) {
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

        public static MethodRecord ofClassFileMethod(com.sun.tools.classfile.Method m, com.sun.tools.classfile.ConstantPool p, CompatibilityFilter... compatibilityFilter) {
            return wrapException(() -> new MethodRecord(
                    m.getName(p),
                    m.descriptor.getValue(p),
                    Flags.toString(m.access_flags.flags, true),
                    AttributesRecord.ofClassFileAttributes(m.attributes, p, compatibilityFilter)));
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

    private static Stream<com.sun.tools.classfile.Attribute> getAllByName(com.sun.tools.classfile.Attributes attrs, com.sun.tools.classfile.ConstantPool p, String name) {
        return Stream.of(attrs.attrs).filter(a -> name.equals(wrapException( () -> a.getName(p))));
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

        public static AttributesRecord ofStreamingElements(Supplier<Stream<? extends ClassfileElement>> elements, ConstantPool cp, CompatibilityFilter... cf) {
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

        public static AttributesRecord ofClassFileAttributes(com.sun.tools.classfile.Attributes attrs, com.sun.tools.classfile.ConstantPool p, CompatibilityFilter... cf) {
            return new AttributesRecord(
                    map(attrs.get(com.sun.tools.classfile.Attribute.AnnotationDefault), a -> ElementValueRecord.ofClassFileElementValue(((com.sun.tools.classfile.AnnotationDefault_attribute) a).default_value, p)),
                    map(attrs.get(com.sun.tools.classfile.Attribute.BootstrapMethods), a -> Stream.of(((com.sun.tools.classfile.BootstrapMethods_attribute) a).bootstrap_method_specifiers)
                            .map(bm -> BootstrapMethodRecord.ofClassFileBootstrapMethodSpecifier(bm, p)).collect(toSet())),
                    map(attrs.get(com.sun.tools.classfile.Attribute.Code), a -> CodeRecord.ofClassFileCodeAttribute((com.sun.tools.classfile.Code_attribute) a, p, cf)),
                    map(attrs.get(com.sun.tools.classfile.Attribute.CompilationID), a -> p.getUTF8Value(((com.sun.tools.classfile.CompilationID_attribute) a).compilationID_index)),
                    map(attrs.get(com.sun.tools.classfile.Attribute.ConstantValue), a -> ConstantPoolEntryRecord.ofClassFileCPInfo(p.get(((com.sun.tools.classfile.ConstantValue_attribute) a).constantvalue_index), p)),
                    map(attrs.get(com.sun.tools.classfile.Attribute.Deprecated), a -> DefinedValue.DEFINED),
                    map(attrs.get(com.sun.tools.classfile.Attribute.EnclosingMethod), a -> EnclosingMethodRecord.ofClassFileEnclosingMethodAttribute((com.sun.tools.classfile.EnclosingMethod_attribute) a, p)),
                    map(attrs.get(com.sun.tools.classfile.Attribute.Exceptions), a -> IntStream.range(0, ((com.sun.tools.classfile.Exceptions_attribute)a).number_of_exceptions).boxed()
                            .map(wrapException(i -> ((com.sun.tools.classfile.Exceptions_attribute)a).getException(i, p))).collect(toSet())),
                    map(attrs.get(com.sun.tools.classfile.Attribute.InnerClasses), a -> Stream.of(((com.sun.tools.classfile.InnerClasses_attribute) a).classes)
                            .collect(toMap(wrapException(innerClassInfo -> innerClassInfo.getInnerClassInfo(p).getName()), innerClassInfo -> InnerClassRecord.ofClassFileICInfo(innerClassInfo, p)))),
                    map(attrs.get(com.sun.tools.classfile.Attribute.MethodParameters), a -> Stream.of(((com.sun.tools.classfile.MethodParameters_attribute) a).method_parameter_table).map(mp -> MethodParameterRecord.ofClassFileMPEntry(mp, p)).toList()),
                    map(attrs.get(com.sun.tools.classfile.Attribute.Module), a -> ModuleRecord.ofClassFileModuleAttribute((com.sun.tools.classfile.Module_attribute) a, p)),
                    map(attrs.get(com.sun.tools.classfile.Attribute.ModuleHashes), a -> ModuleHashesRecord.ofClassFileModuleHashesAttribute((com.sun.tools.classfile.ModuleHashes_attribute) a, p)),
                    map(attrs.get(com.sun.tools.classfile.Attribute.ModuleMainClass), a -> ((com.sun.tools.classfile.ModuleMainClass_attribute) a).getMainClassName(p)),
                    map(attrs.get(com.sun.tools.classfile.Attribute.ModulePackages), a -> Arrays.stream(((com.sun.tools.classfile.ModulePackages_attribute) a).packages_index).boxed()
                            .map(wrapException(i -> p.getPackageInfo(i).getName())).collect(toSet())),
                    map(attrs.get(com.sun.tools.classfile.Attribute.ModuleResolution), a -> ((com.sun.tools.classfile.ModuleResolution_attribute) a).resolution_flags),
                    map(attrs.get(com.sun.tools.classfile.Attribute.ModuleTarget), a -> p.getUTF8Value(((com.sun.tools.classfile.ModuleTarget_attribute) a).target_platform_index)),
                    map(attrs.get(com.sun.tools.classfile.Attribute.NestHost), a -> ((com.sun.tools.classfile.NestHost_attribute) a).getNestTop(p).getName()),
                    map(attrs.get(com.sun.tools.classfile.Attribute.NestMembers), a -> IntStream.of(((com.sun.tools.classfile.NestMembers_attribute) a).members_indexes).boxed()
                            .map(wrapException(i -> p.getClassInfo(i).getName())).collect(toSet())),
                    map(attrs.get(com.sun.tools.classfile.Attribute.PermittedSubclasses), a -> IntStream.range(0, ((com.sun.tools.classfile.PermittedSubclasses_attribute)a).subtypes.length).boxed()
                            .map(wrapException(i -> p.getClassInfo(((com.sun.tools.classfile.PermittedSubclasses_attribute)a).subtypes[i]).getName())).collect(toSet())),
                    map(attrs.get(com.sun.tools.classfile.Attribute.Record), a -> Stream.of(((com.sun.tools.classfile.Record_attribute) a).component_info_arr)
                            .map(ci -> RecordComponentRecord.ofClassFileComponentInfo(ci, p, cf)).toList()),
                    getAllByName(attrs, p, com.sun.tools.classfile.Attribute.RuntimeVisibleAnnotations).flatMap(a -> Stream.of(((com.sun.tools.classfile.RuntimeVisibleAnnotations_attribute) a).annotations))
                            .map(ann -> AnnotationRecord.ofClassFileAnnotation(ann, p)).collect(toSetOrNull()),
                    getAllByName(attrs, p, com.sun.tools.classfile.Attribute.RuntimeInvisibleAnnotations).flatMap(a -> Stream.of(((com.sun.tools.classfile.RuntimeInvisibleAnnotations_attribute) a).annotations))
                            .map(ann -> AnnotationRecord.ofClassFileAnnotation(ann, p)).collect(toSetOrNull()),
                    map(attrs.get(com.sun.tools.classfile.Attribute.RuntimeVisibleParameterAnnotations), a -> Stream.of(((com.sun.tools.classfile.RuntimeVisibleParameterAnnotations_attribute) a).parameter_annotations)
                            .map(list -> Stream.of(list).map(ann -> AnnotationRecord.ofClassFileAnnotation(ann, p)).collect(toSet())).toList()),
                    map(attrs.get(com.sun.tools.classfile.Attribute.RuntimeInvisibleParameterAnnotations), a -> Stream.of(((com.sun.tools.classfile.RuntimeInvisibleParameterAnnotations_attribute) a).parameter_annotations)
                            .map(list -> Stream.of(list).map(ann -> AnnotationRecord.ofClassFileAnnotation(ann, p)).collect(toSet())).toList()),
                    map(attrs.get(com.sun.tools.classfile.Attribute.RuntimeVisibleTypeAnnotations), a -> Stream.of(((com.sun.tools.classfile.RuntimeVisibleTypeAnnotations_attribute) a).annotations)
                            .map(ann -> TypeAnnotationRecord.ofClassFileTypeAnnotation(ann, p, null)).collect(toSet())),
                    map(attrs.get(com.sun.tools.classfile.Attribute.RuntimeInvisibleTypeAnnotations), a -> Stream.of(((com.sun.tools.classfile.RuntimeInvisibleTypeAnnotations_attribute) a).annotations)
                            .map(ann -> TypeAnnotationRecord.ofClassFileTypeAnnotation(ann, p, null)).collect(toSet())),
                    map(attrs.get(com.sun.tools.classfile.Attribute.Signature), a -> ((com.sun.tools.classfile.Signature_attribute) a).getSignature(p)),
                    map(attrs.get(com.sun.tools.classfile.Attribute.SourceDebugExtension), a -> ((com.sun.tools.classfile.SourceDebugExtension_attribute) a).getValue()),
                    map(attrs.get(com.sun.tools.classfile.Attribute.SourceFile), a -> ((com.sun.tools.classfile.SourceFile_attribute) a).getSourceFile(p)),
                    map(attrs.get(com.sun.tools.classfile.Attribute.SourceID), a -> p.getUTF8Value(((com.sun.tools.classfile.SourceID_attribute) a).sourceID_index)),
                    map(attrs.get(com.sun.tools.classfile.Attribute.Synthetic), a -> DefinedValue.DEFINED));
        }
    }

    public record CodeAttributesRecord(
            Set<CharacterRangeRecord> characterRangeTableAttribute,
            Set<LineNumberRecord> lineNumbersTableAttribute,
            Set<LocalVariableRecord> localVariableTableAttribute,
            Set<LocalVariableTypeRecord> localVariableTypeTableAttribute,
            Set<TypeAnnotationRecord> runtimeVisibleTypeAnnotationsAttribute,
            Set<TypeAnnotationRecord> runtimeInvisibleTypeAnnotationsAttribute) {

        static CodeAttributesRecord ofStreamingElements(Supplier<Stream<? extends ClassfileElement>> elements, CodeAttribute lc, CodeNormalizerHelper code, CompatibilityFilter... cf) {
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

        static CodeAttributesRecord ofClassFileAttributes(com.sun.tools.classfile.Attributes attrs, com.sun.tools.classfile.ConstantPool p, CodeNormalizerHelper code, CompatibilityFilter... cf) {
            return new CodeAttributesRecord(
                    getAllByName(attrs, p, com.sun.tools.classfile.Attribute.CharacterRangeTable).flatMap(a -> Stream.of(((com.sun.tools.classfile.CharacterRangeTable_attribute) a).character_range_table))
                            .map(cr -> CharacterRangeRecord.ofClassFileCRTEntry(cr, code)).collect(toSetOrNull()),
                    getAllByName(attrs, p, com.sun.tools.classfile.Attribute.LineNumberTable).flatMap(a -> Stream.of(((com.sun.tools.classfile.LineNumberTable_attribute)a).line_number_table))
                            .map(ln -> new LineNumberRecord(ln.line_number, code.targetIndex(ln.start_pc))).collect(toSetOrNull()),
                    getAllByName(attrs, p, com.sun.tools.classfile.Attribute.LocalVariableTable).flatMap(a -> Stream.of(((com.sun.tools.classfile.LocalVariableTable_attribute) a).local_variable_table))
                            .map(lv -> LocalVariableRecord.ofClassFileLVTEntry(lv, code, p)).collect(toSetOrNull()),
                    getAllByName(attrs, p, com.sun.tools.classfile.Attribute.LocalVariableTypeTable).flatMap(a -> Stream.of(((com.sun.tools.classfile.LocalVariableTypeTable_attribute) a).local_variable_table))
                            .map(lv -> LocalVariableTypeRecord.ofClassFileLVTTEntry(lv, code, p)).collect(toSetOrNull()),
                    map(attrs.get(com.sun.tools.classfile.Attribute.RuntimeVisibleTypeAnnotations), a -> Stream.of(((com.sun.tools.classfile.RuntimeVisibleTypeAnnotations_attribute) a).annotations)
                            .map(ann -> TypeAnnotationRecord.ofClassFileTypeAnnotation(ann, p, code)).collect(toSet())),
                    map(attrs.get(com.sun.tools.classfile.Attribute.RuntimeInvisibleTypeAnnotations), a -> Stream.of(((com.sun.tools.classfile.RuntimeInvisibleTypeAnnotations_attribute) a).annotations)
                            .map(ann -> TypeAnnotationRecord.ofClassFileTypeAnnotation(ann, p, code)).collect(toSet())));
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

        public static AnnotationRecord ofClassFileAnnotation(com.sun.tools.classfile.Annotation ann, com.sun.tools.classfile.ConstantPool p) {
            return new AnnotationRecord(
                    wrapException(() -> p.getUTF8Value(ann.type_index)),
                    Stream.of(ann.element_value_pairs).collect(toMap(wrapException(evp -> p.getUTF8Value(evp.element_name_index)), evp -> ElementValueRecord.ofClassFileElementValue(evp.value, p))));
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

        public static BootstrapMethodRecord ofClassFileBootstrapMethodSpecifier(com.sun.tools.classfile.BootstrapMethods_attribute.BootstrapMethodSpecifier bm, com.sun.tools.classfile.ConstantPool p) {
            return new BootstrapMethodRecord(
                    ConstantPoolEntryRecord.ofClassFileCPInfo(wrapException(() -> p.get(bm.bootstrap_method_ref)), p),
                    IntStream.of(bm.bootstrap_arguments).boxed().map(wrapException(i -> ConstantPoolEntryRecord.ofClassFileCPInfo(p.get(i), p))).toList());
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

        public static CharacterRangeRecord ofClassFileCRTEntry(com.sun.tools.classfile.CharacterRangeTable_attribute.Entry cre, CodeNormalizerHelper code) {
            return new CharacterRangeRecord(code.targetIndex(cre.start_pc), code.targetIndex(cre.end_pc + 1), cre.character_range_start, cre.character_range_end, cre.flags);
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

        private static List<String> instructions(Supplier<Stream<? extends ClassfileElement>> elements, CodeNormalizerHelper code, CodeAttribute lr) {
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

        public static CodeRecord ofStreamingElements(int maxStack, int maxLocals, int codeLength, Supplier<Stream<? extends ClassfileElement>> elements, CodeAttribute lc, CodeNormalizerHelper codeHelper, CompatibilityFilter... cf) {
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

        public static CodeRecord ofClassFileCodeAttribute(com.sun.tools.classfile.Code_attribute a, com.sun.tools.classfile.ConstantPool p, CompatibilityFilter... cf) {
            var codeHelper = new CodeNormalizerHelper(a.code);
            return new CodeRecord(
                    By_ClassBuilder.isNotDirectlyComparable(cf, a.max_stack),
                    By_ClassBuilder.isNotDirectlyComparable(cf, a.max_locals),
                    By_ClassBuilder.isNotDirectlyComparable(cf, a.code_length),
                    ((Supplier<List<String>>) (() -> {
                        ArrayList<String> insList = new ArrayList<>();
                        for (com.sun.tools.classfile.Instruction ins : a.getInstructions()) {
                            String opCode[] = {opcodeMask(ins.getOpcode().name())};
                            Integer hash = ins.accept(new com.sun.tools.classfile.Instruction.KindVisitor<Integer, Void>() {

                                private int parametersHash(com.sun.tools.classfile.Instruction instr) {
                                    return codeHelper.hash(instr.getPC() + 1, instr.length());
                                }

                                @Override
                                public Integer visitNoOperands(com.sun.tools.classfile.Instruction instr, Void v) {
                                    return null;
                                }

                                @Override
                                public Integer visitArrayType(com.sun.tools.classfile.Instruction instr, com.sun.tools.classfile.Instruction.TypeKind kind, Void v) {
                                    return new ConstantPoolEntryRecord.CpClassRecord("[" + ("    ZCFDBSIJ".charAt(kind.value))).hashCode() + 1;
                                }

                                @Override
                                public Integer visitBranch(com.sun.tools.classfile.Instruction instr, int offset, Void v) {
                                    return codeHelper.targetIndex(instr.getPC() + offset);
                                }

                                @Override
                                public Integer visitConstantPoolRef(com.sun.tools.classfile.Instruction instr, int index, Void v) {
                                    var cper = ConstantPoolEntryRecord.ofClassFileCPInfo(wrapException(() -> p.get(index)), p);
                                    String altOpcode;
                                    if (opCode[0].startsWith("LDC") && (altOpcode = cper.altOpcode()) != null) {
                                        opCode[0] = altOpcode;
                                        return null;
                                    } else if (opCode[0].startsWith("NEWARRAY")) {
                                        String type = ((ConstantPoolEntryRecord.CpClassRecord)cper).cpClass;
                                        if (!type.startsWith("[")) type = "L" + type + ";";
                                        return new ConstantPoolEntryRecord.CpClassRecord("[" + type).hashCode() + 1;
                                    } else {
                                        return cper.hashCode();
                                    }
                                }

                                @Override
                                public Integer visitConstantPoolRefAndValue(com.sun.tools.classfile.Instruction instr, int index, int value, Void v) {
                                    return ConstantPoolEntryRecord.ofClassFileCPInfo(wrapException(() -> p.get(index)), p).hashCode() + (instr.getOpcode() == com.sun.tools.classfile.Opcode.INVOKEINTERFACE ? 0 : value);
                                }

                                @Override
                                public Integer visitLocal(com.sun.tools.classfile.Instruction instr, int index, Void v) {
                                    return index;
                                }

                                @Override
                                public Integer visitLocalAndValue(com.sun.tools.classfile.Instruction instr, int index, int value, Void v) {
                                    return parametersHash(instr);
                                }

                                @Override
                                public Integer visitLookupSwitch(com.sun.tools.classfile.Instruction instr, int default_, int npairs, int[] matches, int[] offsets, Void v) {
                                    return codeHelper.multipleTargetsHash(instr.getPC(), default_, offsets, matches);
                                }

                                @Override
                                public Integer visitTableSwitch(com.sun.tools.classfile.Instruction instr, int default_, int low, int high, int[] offsets, Void v) {
                                    return codeHelper.multipleTargetsHash(instr.getPC(), default_, Arrays.stream(offsets).filter(o -> o != default_).toArray(), low, high);
                                }

                                @Override
                                public Integer visitValue(com.sun.tools.classfile.Instruction instr, int value, Void v) {
                                    return value;
                                }

                                @Override
                                public Integer visitUnknown(com.sun.tools.classfile.Instruction instr, Void v) {
                                    return parametersHash(instr);
                                }
                            }, null);
                            insList.add(opCode[0] + (hash != null ? '#' + Integer.toHexString(hash & 0xfff) : ""));
                        }
                        return insList;
                    })).get(),
                    Stream.of(a.exception_table).map(eh -> ExceptionHandlerRecord.ofClassFileExceptionData(eh, codeHelper, p)).collect(toSet()),
                    CodeAttributesRecord.ofClassFileAttributes(a.attributes, p, codeHelper, cf));
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

            public static ExceptionHandlerRecord ofClassFileExceptionData(com.sun.tools.classfile.Code_attribute.Exception_data ed, CodeNormalizerHelper code, com.sun.tools.classfile.ConstantPool p) {
                return new ExceptionHandlerRecord(
                        code.targetIndex(ed.start_pc),
                        code.targetIndex(ed.end_pc),
                        code.targetIndex(ed.handler_pc),
                        wrapException(() -> ed.catch_type == 0 ? null : ConstantPoolEntryRecord.ofClassFileCPInfo(p.get(ed.catch_type), p)));
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

        public static EnclosingMethodRecord ofClassFileEnclosingMethodAttribute(com.sun.tools.classfile.EnclosingMethod_attribute ema, com.sun.tools.classfile.ConstantPool p) {
            return wrapException(() -> new EnclosingMethodRecord(
                    ema.getClassName(p),
                    ema.method_index == 0 ? null : ConstantPoolEntryRecord.ofClassFileCPInfo(p.get(ema.method_index), p)));
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

        public static InnerClassRecord ofClassFileICInfo(com.sun.tools.classfile.InnerClasses_attribute.Info ic, com.sun.tools.classfile.ConstantPool p) {
            return wrapException(() -> new InnerClassRecord(
                    ic.getInnerClassInfo(p).getName(),
                    ic.getInnerName(p),
                    map(ic.getOuterClassInfo(p), ici -> ici.getName()),
                    Flags.toString(ic.inner_class_access_flags.flags, false)));
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

        public static LocalVariableRecord ofClassFileLVTEntry(com.sun.tools.classfile.LocalVariableTable_attribute.Entry lv, CodeNormalizerHelper code, com.sun.tools.classfile.ConstantPool p) {
            return wrapException(() -> new LocalVariableRecord(
                    code.targetIndex(lv.start_pc),
                    code.targetIndex(lv.start_pc + lv.length),
                    p.getUTF8Value(lv.name_index),
                    p.getUTF8Value(lv.descriptor_index),
                    lv.index));
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

        public static LocalVariableTypeRecord ofClassFileLVTTEntry(com.sun.tools.classfile.LocalVariableTypeTable_attribute.Entry lv, CodeNormalizerHelper code, com.sun.tools.classfile.ConstantPool p) {
            return wrapException(() -> new LocalVariableTypeRecord(
                    code.targetIndex(lv.start_pc),
                    code.targetIndex(lv.start_pc + lv.length),
                    p.getUTF8Value(lv.name_index),
                    p.getUTF8Value(lv.signature_index),
                    lv.index));
        }
    }

    public record MethodParameterRecord(
            String name,
            int accessFlags) {

        public static MethodParameterRecord ofMethodParameter(MethodParameterInfo mp) {
            return new MethodParameterRecord(mp.name().map(Utf8Entry::stringValue).orElse(null), mp.flagsMask());
        }

        public static MethodParameterRecord ofClassFileMPEntry(com.sun.tools.classfile.MethodParameters_attribute.Entry mp, com.sun.tools.classfile.ConstantPool p) {
            return new MethodParameterRecord(mp.name_index == 0 ? null : wrapException(() -> p.getUTF8Value(mp.name_index)), mp.flags);
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

        public static ModuleRecord ofClassFileModuleAttribute(com.sun.tools.classfile.Module_attribute m, com.sun.tools.classfile.ConstantPool p) {
            return wrapException(() -> new ModuleRecord(
                    p.getModuleInfo(m.module_name).getName(),
                    m.module_flags,
                    m.module_version_index == 0 ? null : p.getUTF8Value(m.module_version_index),
                    Stream.of(m.requires).map(re -> RequiresRecord.ofClassFileRequiresEntry(re, p)).collect(toSet()),
                    Stream.of(m.exports).map(ee -> ExportsRecord.ofClassFileExportsEntry(ee, p)).collect(toSet()),
                    Stream.of(m.opens).map(oe -> OpensRecord.ofClassFileOpensEntry(oe, p)).collect(toSet()),
                    Arrays.stream(m.uses_index).boxed().map(wrapException(ui -> p.getClassInfo(ui).getName())).collect(toSet()),
                    Stream.of(m.provides).map(pe -> ProvidesRecord.ofClassFileProvidesEntry(pe, p)).collect(toSet())));
        }

        public record RequiresRecord(
                String requires,
                int requiresFlags,
                String requiresVersion) {

            public static RequiresRecord ofRequire(ModuleRequireInfo r) {
                return new RequiresRecord(r.requires().name().stringValue(), r.requiresFlagsMask(), r.requiresVersion().map(v -> v.stringValue()).orElse(null));
            }

            public static RequiresRecord ofClassFileRequiresEntry(com.sun.tools.classfile.Module_attribute.RequiresEntry re, com.sun.tools.classfile.ConstantPool p) {
                return wrapException(() -> new RequiresRecord(
                        p.getModuleInfo(re.requires_index).getName(),
                        re.requires_flags,
                        re.requires_version_index == 0 ? null : p.getUTF8Value(re.requires_version_index)));
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

            public static ExportsRecord ofClassFileExportsEntry(com.sun.tools.classfile.Module_attribute.ExportsEntry ee, com.sun.tools.classfile.ConstantPool p) {
                return new ExportsRecord(
                        wrapException(() -> p.getPackageInfo(ee.exports_index).getName()),
                        ee.exports_flags,
                        Arrays.stream(ee.exports_to_index).boxed().map(wrapException(i -> p.getModuleInfo(i).getName())).collect(toSet())
                );
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

            public static OpensRecord ofClassFileOpensEntry(com.sun.tools.classfile.Module_attribute.OpensEntry oe, com.sun.tools.classfile.ConstantPool p) {
                return new OpensRecord(
                        wrapException(() -> p.getPackageInfo(oe.opens_index).getName()),
                        oe.opens_flags,
                        Arrays.stream(oe.opens_to_index).boxed().map(wrapException(i -> p.getModuleInfo(i).getName())).collect(toSet())
                );
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

            public static ProvidesRecord ofClassFileProvidesEntry(com.sun.tools.classfile.Module_attribute.ProvidesEntry pe, com.sun.tools.classfile.ConstantPool p) {
                return new ProvidesRecord(
                        wrapException(() -> p.getClassInfo(pe.provides_index).getName()),
                        Arrays.stream(pe.with_index).boxed().map(wrapException(w -> p.getClassInfo(w).getName())).collect(toSet()));
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

        public static ModuleHashesRecord ofClassFileModuleHashesAttribute(com.sun.tools.classfile.ModuleHashes_attribute mh, com.sun.tools.classfile.ConstantPool p) {
            return new ModuleHashesRecord(
                    wrapException(() -> p.getUTF8Value(mh.algorithm_index)),
                    Stream.of(mh.hashes_table).collect(toMap(wrapException(e -> p.getModuleInfo(e.module_name_index).getName()), e -> new BigInteger(1, e.hash).toString(16))));
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

        public static RecordComponentRecord ofClassFileComponentInfo(com.sun.tools.classfile.Record_attribute.ComponentInfo ci, com.sun.tools.classfile.ConstantPool p, CompatibilityFilter... compatibilityFilter) {
            return wrapException(() -> new RecordComponentRecord(
                    ci.getName(p),
                    ci.descriptor.getValue(p),
                    AttributesRecord.ofClassFileAttributes(ci.attributes, p, compatibilityFilter)));
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

        public static TypeAnnotationRecord ofClassFileTypeAnnotation(com.sun.tools.classfile.TypeAnnotation ann, com.sun.tools.classfile.ConstantPool p, CodeNormalizerHelper code) {
            return new TypeAnnotationRecord(
                    ann.position.type.targetTypeValue(),
                    TargetInfoRecord.ofClassFilePossition(ann.position, code),
                    ann.position.location.stream().map(tpe -> TypePathRecord.ofClassFileTypePathEntry(tpe)).collect(toSet()),
                    AnnotationRecord.ofClassFileAnnotation(ann.annotation, p));
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

            public static TargetInfoRecord ofClassFilePossition(com.sun.tools.classfile.TypeAnnotation.Position pos, CodeNormalizerHelper code) {
                return switch (pos.type) {
                    case INSTANCEOF:
                    case NEW:
                    case CONSTRUCTOR_REFERENCE:
                    case METHOD_REFERENCE:
                        yield new OffsetTargetRecord(code.targetIndex(pos.offset));
                    case LOCAL_VARIABLE:
                    case RESOURCE_VARIABLE:
                        yield new LocalVarTargetRecord(IntStream.range(0, pos.lvarOffset.length).mapToObj(i
                                -> new LocalVarTargetRecord.EntryRecord(code.targetIndex(pos.lvarOffset[i]), code.targetIndex(pos.lvarOffset[i] + pos.lvarLength[i]), pos.lvarIndex[i])).collect(toSet()));
                    case EXCEPTION_PARAMETER:
                        yield new CatchTargetRecord(pos.exception_index);
                    case CLASS_TYPE_PARAMETER:
                    case METHOD_TYPE_PARAMETER:
                        yield new TypeParameterTargetRecord(pos.parameter_index);
                    case CLASS_TYPE_PARAMETER_BOUND:
                    case METHOD_TYPE_PARAMETER_BOUND:
                        yield new TypeParameterBoundTargetRecord(pos.parameter_index, pos.bound_index);
                    case CLASS_EXTENDS:
                        yield new SupertypeTargetRecord(pos.type_index);
                    case THROWS:
                        yield new ThrowsTargetRecord(pos.type_index);
                    case METHOD_FORMAL_PARAMETER:
                        yield new FormalParameterTargetRecord(pos.parameter_index);
                    case CAST:
                    case CONSTRUCTOR_INVOCATION_TYPE_ARGUMENT:
                    case METHOD_INVOCATION_TYPE_ARGUMENT:
                    case CONSTRUCTOR_REFERENCE_TYPE_ARGUMENT:
                    case METHOD_REFERENCE_TYPE_ARGUMENT:
                        yield new TypeArgumentTargetRecord(code.targetIndex(pos.offset), pos.type_index);
                    case METHOD_RECEIVER:
                    case METHOD_RETURN:
                    case FIELD:
                        yield new EmptyTargetRecord();
                    case UNKNOWN:
                        throw new IllegalArgumentException(pos.toString());
                };
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

            public static TypePathRecord ofClassFileTypePathEntry(com.sun.tools.classfile.TypeAnnotation.Position.TypePathEntry tpe) {
                return new TypePathRecord(tpe.tag.tag, tpe.arg);
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

        public static ConstantPoolEntryRecord ofClassFileCPInfo(com.sun.tools.classfile.ConstantPool.CPInfo cpInfo, com.sun.tools.classfile.ConstantPool p) {
            return cpInfo.accept(new com.sun.tools.classfile.ConstantPool.Visitor<ConstantPoolEntryRecord, Void>() {
                @Override
                public ConstantPoolEntryRecord visitClass(com.sun.tools.classfile.ConstantPool.CONSTANT_Class_info info, Void v) {
                    return wrapException(() -> new CpClassRecord(info.getName()));
                }

                @Override
                public ConstantPoolEntryRecord visitDouble(com.sun.tools.classfile.ConstantPool.CONSTANT_Double_info info, Void v) {
                    return new CpDoubleRecord(info.value);
                }

                @Override
                public ConstantPoolEntryRecord visitFieldref(com.sun.tools.classfile.ConstantPool.CONSTANT_Fieldref_info info, Void v) {
                    return wrapException(() -> new CpFieldRefRecord(
                            info.getClassName(),
                            info.getNameAndTypeInfo().getName(),
                            info.getNameAndTypeInfo().getType()));
                }

                @Override
                public ConstantPoolEntryRecord visitFloat(com.sun.tools.classfile.ConstantPool.CONSTANT_Float_info info, Void v) {
                    return new CpFloatRecord(info.value);
                }

                @Override
                public ConstantPoolEntryRecord visitInteger(com.sun.tools.classfile.ConstantPool.CONSTANT_Integer_info info, Void v) {
                    return new CpIntegerRecord(info.value);
                }

                @Override
                public ConstantPoolEntryRecord visitInterfaceMethodref(com.sun.tools.classfile.ConstantPool.CONSTANT_InterfaceMethodref_info info, Void v) {
                    return wrapException(() -> new CpInterfaceMethodRefRecord(
                            info.getClassName(),
                            info.getNameAndTypeInfo().getName(),
                            info.getNameAndTypeInfo().getType()));
                }

                @Override
                public ConstantPoolEntryRecord visitInvokeDynamic(com.sun.tools.classfile.ConstantPool.CONSTANT_InvokeDynamic_info info, Void v) {
                    return wrapException(() -> new CpInvokeDynamicRecord(
                            info.getNameAndTypeInfo().getName(),
                            info.getNameAndTypeInfo().getType()));
                }

                @Override
                public ConstantPoolEntryRecord visitDynamicConstant(com.sun.tools.classfile.ConstantPool.CONSTANT_Dynamic_info info, Void v) {
                    return wrapException(() -> new CpConstantDynamicRecord(
                            info.getNameAndTypeInfo().getName(),
                            info.getNameAndTypeInfo().getType()));
                }

                @Override
                public ConstantPoolEntryRecord visitLong(com.sun.tools.classfile.ConstantPool.CONSTANT_Long_info info, Void v) {
                    return new CpLongRecord(info.value);
                }

                @Override
                public ConstantPoolEntryRecord visitMethodref(com.sun.tools.classfile.ConstantPool.CONSTANT_Methodref_info info, Void v) {
                    return wrapException(() -> new CpMethodRefRecord(
                            info.getClassName(),
                            info.getNameAndTypeInfo().getName(),
                            info.getNameAndTypeInfo().getType()));
                }

                @Override
                public ConstantPoolEntryRecord visitMethodHandle(com.sun.tools.classfile.ConstantPool.CONSTANT_MethodHandle_info info, Void v) {
                    return wrapException(() -> new CpMethodHandleRecord(
                            ConstantPoolEntryRecord.ofClassFileCPInfo(info.getCPRefInfo(), p),
                            info.reference_kind.tag));
                }

                @Override
                public ConstantPoolEntryRecord visitMethodType(com.sun.tools.classfile.ConstantPool.CONSTANT_MethodType_info info, Void v) {
                    return wrapException(() -> new CpMethodTypeRecord(info.getType()));
                }

                @Override
                public ConstantPoolEntryRecord visitModule(com.sun.tools.classfile.ConstantPool.CONSTANT_Module_info info, Void v) {
                    return wrapException(() -> new CpModuleRecord(info.getName()));
                }

                @Override
                public ConstantPoolEntryRecord visitNameAndType(com.sun.tools.classfile.ConstantPool.CONSTANT_NameAndType_info info, Void v) {
                    return wrapException(() -> new CpNameAndTypeRecord(
                            info.getName(),
                            info.getType()));
                }

                @Override
                public ConstantPoolEntryRecord visitPackage(com.sun.tools.classfile.ConstantPool.CONSTANT_Package_info info, Void v) {
                    return wrapException(() -> new CpPackageRecord(info.getName()));
                }

                @Override
                public ConstantPoolEntryRecord visitString(com.sun.tools.classfile.ConstantPool.CONSTANT_String_info info, Void v) {
                    return wrapException(() -> new CpStringRecord(info.getString()));
                }

                @Override
                public ConstantPoolEntryRecord visitUtf8(com.sun.tools.classfile.ConstantPool.CONSTANT_Utf8_info info, Void v) {
                    return new CpUTF8Record(info.value);
                }
            }, null);
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

        public static ElementValueRecord ofClassFileElementValue(com.sun.tools.classfile.Annotation.element_value ev, com.sun.tools.classfile.ConstantPool p) {
            return ev.accept(new com.sun.tools.classfile.Annotation.element_value.Visitor<ElementValueRecord, Void>() {
                @Override
                public ElementValueRecord visitPrimitive(com.sun.tools.classfile.Annotation.Primitive_element_value ev, Void v) {
                    return new EvConstRecord(ev.tag, ConstantPoolEntryRecord.ofClassFileCPInfo(wrapException(() -> p.get(ev.const_value_index)), p));
                }

                @Override
                public ElementValueRecord visitEnum(com.sun.tools.classfile.Annotation.Enum_element_value ev, Void v) {
                    return wrapException(() -> new EvEnumConstRecord(ev.tag, p.getUTF8Value(ev.type_name_index), p.getUTF8Value(ev.const_name_index)));
                }

                @Override
                public ElementValueRecord visitClass(com.sun.tools.classfile.Annotation.Class_element_value ev, Void v) {
                    return new EvClassRecord(ev.tag, wrapException(() -> p.getUTF8Value(ev.class_info_index)));
                }

                @Override
                public ElementValueRecord visitAnnotation(com.sun.tools.classfile.Annotation.Annotation_element_value ev, Void v) {
                    return new EvAnnotationRecord(ev.tag, AnnotationRecord.ofClassFileAnnotation(ev.annotation_value, p));
                }

                @Override
                public ElementValueRecord visitArray(com.sun.tools.classfile.Annotation.Array_element_value ev, Void v) {
                    return new EvArrayRecord(ev.tag, Stream.of(ev.values).map(e -> ElementValueRecord.ofClassFileElementValue(e, p)).toList());
                }
            }, null);
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

    private static ClassRecord readClassRecord(Path path, String thisSwitch, String otherClassSwitch) throws IOException, com.sun.tools.classfile.ConstantPoolException {
        var filter = "-b".equals(otherClassSwitch) ? CompatibilityFilter.By_ClassBuilder : CompatibilityFilter.Read_all;
        return switch (thisSwitch) {
            case "-m" -> ClassRecord.ofClassModel(Classfile.parse(path), filter);
            case "-b" -> ClassRecord.ofClassModel(Classfile.parse(path), CompatibilityFilter.By_ClassBuilder);
            case "-s" -> ClassRecord.ofStreamingElements(Classfile.parse(path), filter);
            case "-f" -> ClassRecord.ofClassFile(com.sun.tools.classfile.ClassFile.read(path), filter);
            default -> throw new IllegalArgumentException("Unknown command line option: " + thisSwitch);
        };
    }

    public static void main(String args[]) throws Exception {
        try {
            Iterator<String> cmd = Arrays.asList(args).iterator();
            var switches = new String[2];
            var paths = new Path[2];
            for (int i=0; i < 2; i++) {
                switches[i] = cmd.next();
                if (switches[i].charAt(0) == '-') {
                    paths[i] = Path.of(cmd.next());
                } else {
                    paths[i] = Path.of(switches[i]);
                    switches[i] = "-m";
                }
            }
            assertEqualsDeep(
                    readClassRecord(paths[0], switches[0], switches[1]),
                    readClassRecord(paths[1], switches[1], switches[0]),
                    paths[0].getFileName().toString() + " [" + switches[0] + "] (actual) vs " + paths[1].getFileName().toString() + " [" + switches[1] + "] (expected)");
            System.out.println("no differences found");
        } catch (AssertionError e) {
            System.err.println(e.getLocalizedMessage());
            System.exit(1);
        } catch (NoSuchElementException e) {
            System.err.println(
                    """
                    missing argument(s)
                    usage: java --enable-preview -jar bytecode-lib-1.0-SNAPSHOT-tests.jar [-m | -b | -s | -f] <class_file> [-m | -b | -s | -f] <expected_class_file>

                    options:
                        -m      following class is analyzed from ClassModel (default)
                        -b      following class is analyzed from ClassModel, filtering only features comparable after passing through ClassBuilder
                        -s      following class is analyzed from streaming Elements
                        -f      following class is analyzed from com.sun.tools.classfile.ClassFile (requires JVM option --add-exports jdk.jdeps/com.sun.tools.classfile=ALL-UNNAMED)

                    examples:
                        java --enable-preview -jar bytecode-lib-1.0-SNAPSHOT-tests.jar MyClass.class MySecondClass.class
                            - analyzes MyClass.class and MySecondClass.class files by ClassModel, asserts their equality and prints differences

                        java --enable-preview --add-exports jdk.jdeps/com.sun.tools.classfile=ALL-UNNAMED -jar bytecode-lib-1.0-SNAPSHOT-tests.jar -s MyClass.class -f MyClass.class
                            - analyzes MyClass.class file from streaming Elements and compares it with the MyClass.class analyzed from com.sun.tools.classfile.ClassFile
                    """);
            System.exit(1);
        }
    }
}
