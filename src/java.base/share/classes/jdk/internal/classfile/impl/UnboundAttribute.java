/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.classfile.*;
import java.lang.classfile.attribute.*;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.classfile.constantpool.ConstantValueEntry;
import java.lang.classfile.constantpool.ModuleEntry;
import java.lang.classfile.constantpool.NameAndTypeEntry;
import java.lang.classfile.constantpool.PackageEntry;
import java.lang.classfile.constantpool.Utf8Entry;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jdk.internal.access.SharedSecrets;

import static java.util.Objects.requireNonNull;

public abstract sealed class UnboundAttribute<T extends Attribute<T>>
        extends AbstractElement
        implements Attribute<T>, Util.Writable {
    protected final AttributeMapper<T> mapper;

    public UnboundAttribute(AttributeMapper<T> mapper) {
        this.mapper = mapper;
    }

    @Override
    public AttributeMapper<T> attributeMapper() {
        return mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void writeTo(BufWriterImpl buf) {
        mapper.writeAttribute(buf, (T) this);
    }

    @Override
    public void writeTo(DirectClassBuilder builder) {
        builder.writeAttribute(this);
    }

    @Override
    public void writeTo(DirectCodeBuilder builder) {
        builder.writeAttribute(this);
    }

    @Override
    public void writeTo(DirectMethodBuilder builder) {
        builder.writeAttribute(this);
    }

    @Override
    public void writeTo(DirectFieldBuilder builder) {
        builder.writeAttribute(this);
    }

    @Override
    public String toString() {
        return String.format("Attribute[name=%s]", mapper.name());
    }
    public static final class UnboundConstantValueAttribute
            extends UnboundAttribute<ConstantValueAttribute>
            implements ConstantValueAttribute {

        private static final Utf8Entry NAME = TemporaryConstantPool.INSTANCE.utf8Entry(Attributes.NAME_CONSTANT_VALUE);

        private final ConstantValueEntry entry;

        public UnboundConstantValueAttribute(ConstantValueEntry entry) {
            super(Attributes.constantValue());
            this.entry = requireNonNull(entry);
        }

        @Override
        public ConstantValueEntry constant() {
            return entry;
        }

        @Override
        public Utf8Entry attributeName() {
            return NAME;
        }
    }

    public static final class UnboundDeprecatedAttribute
            extends UnboundAttribute<DeprecatedAttribute>
            implements DeprecatedAttribute {

        private static final Utf8Entry NAME = TemporaryConstantPool.INSTANCE.utf8Entry(Attributes.NAME_DEPRECATED);

        public UnboundDeprecatedAttribute() {
            super(Attributes.deprecated());
        }

        @Override
        public Utf8Entry attributeName() {
            return NAME;
        }
    }

    public static final class UnboundSyntheticAttribute
            extends UnboundAttribute<SyntheticAttribute>
            implements SyntheticAttribute {

        private static final Utf8Entry NAME = TemporaryConstantPool.INSTANCE.utf8Entry(Attributes.NAME_SYNTHETIC);

        public UnboundSyntheticAttribute() {
            super(Attributes.synthetic());
        }

        @Override
        public Utf8Entry attributeName() {
            return NAME;
        }
    }

    public static final class UnboundSignatureAttribute
            extends UnboundAttribute<SignatureAttribute>
            implements SignatureAttribute {

        private static final Utf8Entry NAME = TemporaryConstantPool.INSTANCE.utf8Entry(Attributes.NAME_SIGNATURE);

        private final Utf8Entry signature;

        public UnboundSignatureAttribute(Utf8Entry signature) {
            super(Attributes.signature());
            this.signature = requireNonNull(signature);
        }

        @Override
        public Utf8Entry signature() {
            return signature;
        }

        @Override
        public Utf8Entry attributeName() {
            return NAME;
        }
    }

    public static final class UnboundExceptionsAttribute
            extends UnboundAttribute<ExceptionsAttribute>
            implements ExceptionsAttribute {

        private static final Utf8Entry NAME = TemporaryConstantPool.INSTANCE.utf8Entry(Attributes.NAME_EXCEPTIONS);

        private final List<ClassEntry> exceptions;

        public UnboundExceptionsAttribute(List<ClassEntry> exceptions) {
            super(Attributes.exceptions());
            this.exceptions = List.copyOf(exceptions);
        }

        @Override
        public List<ClassEntry> exceptions() {
            return exceptions;
        }

        @Override
        public Utf8Entry attributeName() {
            return NAME;
        }
    }

    public static final class UnboundAnnotationDefaultAttribute
            extends UnboundAttribute<AnnotationDefaultAttribute>
            implements AnnotationDefaultAttribute {

        private static final Utf8Entry NAME = TemporaryConstantPool.INSTANCE.utf8Entry(Attributes.NAME_ANNOTATION_DEFAULT);

        private final AnnotationValue annotationDefault;

        public UnboundAnnotationDefaultAttribute(AnnotationValue annotationDefault) {
            super(Attributes.annotationDefault());
            this.annotationDefault = requireNonNull(annotationDefault);
        }

        @Override
        public AnnotationValue defaultValue() {
            return annotationDefault;
        }

        @Override
        public Utf8Entry attributeName() {
            return NAME;
        }
    }

    public static final class UnboundSourceFileAttribute extends UnboundAttribute<SourceFileAttribute>
            implements SourceFileAttribute {

        private static final Utf8Entry NAME = TemporaryConstantPool.INSTANCE.utf8Entry(Attributes.NAME_SOURCE_FILE);

        private final Utf8Entry sourceFile;

        public UnboundSourceFileAttribute(Utf8Entry sourceFile) {
            super(Attributes.sourceFile());
            this.sourceFile = requireNonNull(sourceFile);
        }

        @Override
        public Utf8Entry sourceFile() {
            return sourceFile;
        }

        @Override
        public Utf8Entry attributeName() {
            return NAME;
        }
    }

    public static final class UnboundStackMapTableAttribute extends UnboundAttribute<StackMapTableAttribute>
            implements StackMapTableAttribute {

        private static final Utf8Entry NAME = TemporaryConstantPool.INSTANCE.utf8Entry(Attributes.NAME_STACK_MAP_TABLE);

        private final List<StackMapFrameInfo> entries;

        public UnboundStackMapTableAttribute(List<StackMapFrameInfo> entries) {
            super(Attributes.stackMapTable());
            this.entries = List.copyOf(entries);
        }

        @Override
        public List<StackMapFrameInfo> entries() {
            return entries;
        }

        @Override
        public Utf8Entry attributeName() {
            return NAME;
        }
    }

    public static final class UnboundInnerClassesAttribute
            extends UnboundAttribute<InnerClassesAttribute>
            implements InnerClassesAttribute {

        private static final Utf8Entry NAME = TemporaryConstantPool.INSTANCE.utf8Entry(Attributes.NAME_INNER_CLASSES);

        private final List<InnerClassInfo> innerClasses;

        public UnboundInnerClassesAttribute(List<InnerClassInfo> innerClasses) {
            super(Attributes.innerClasses());
            this.innerClasses = List.copyOf(innerClasses);
        }

        @Override
        public List<InnerClassInfo> classes() {
            return innerClasses;
        }

        @Override
        public Utf8Entry attributeName() {
            return NAME;
        }
    }

    public static final class UnboundRecordAttribute
            extends UnboundAttribute<RecordAttribute>
            implements RecordAttribute {

        private static final Utf8Entry NAME = TemporaryConstantPool.INSTANCE.utf8Entry(Attributes.NAME_RECORD);

        private final List<RecordComponentInfo> components;

        public UnboundRecordAttribute(List<RecordComponentInfo> components) {
            super(Attributes.record());
            this.components = List.copyOf(components);
        }

        @Override
        public List<RecordComponentInfo> components() {
            return components;
        }

        @Override
        public Utf8Entry attributeName() {
            return NAME;
        }
    }

    public static final class UnboundEnclosingMethodAttribute
            extends UnboundAttribute<EnclosingMethodAttribute>
            implements EnclosingMethodAttribute {

        private static final Utf8Entry NAME = TemporaryConstantPool.INSTANCE.utf8Entry(Attributes.NAME_ENCLOSING_METHOD);

        private final ClassEntry classEntry;
        private final NameAndTypeEntry method;

        public UnboundEnclosingMethodAttribute(ClassEntry classEntry, NameAndTypeEntry method) {
            super(Attributes.enclosingMethod());
            this.classEntry = requireNonNull(classEntry);
            this.method = method;
        }

        @Override
        public ClassEntry enclosingClass() {
            return classEntry;
        }

        @Override
        public Optional<NameAndTypeEntry> enclosingMethod() {
            return Optional.ofNullable(method);
        }

        @Override
        public Utf8Entry attributeName() {
            return NAME;
        }
    }

    public static final class UnboundMethodParametersAttribute
            extends UnboundAttribute<MethodParametersAttribute>
            implements MethodParametersAttribute {

        private static final Utf8Entry NAME = TemporaryConstantPool.INSTANCE.utf8Entry(Attributes.NAME_METHOD_PARAMETERS);

        private final List<MethodParameterInfo> parameters;

        public UnboundMethodParametersAttribute(List<MethodParameterInfo> parameters) {
            super(Attributes.methodParameters());
            this.parameters = List.copyOf(parameters);
        }

        @Override
        public List<MethodParameterInfo> parameters() {
            return parameters;
        }

        @Override
        public Utf8Entry attributeName() {
            return NAME;
        }
    }

    public static final class UnboundModuleTargetAttribute
            extends UnboundAttribute<ModuleTargetAttribute>
            implements ModuleTargetAttribute {

        private static final Utf8Entry NAME = TemporaryConstantPool.INSTANCE.utf8Entry(Attributes.NAME_MODULE_TARGET);

        final Utf8Entry moduleTarget;

        public UnboundModuleTargetAttribute(Utf8Entry moduleTarget) {
            super(Attributes.moduleTarget());
            this.moduleTarget = requireNonNull(moduleTarget);
        }

        @Override
        public Utf8Entry targetPlatform() {
            return moduleTarget;
        }

        @Override
        public Utf8Entry attributeName() {
            return NAME;
        }
    }

    public static final class UnboundModuleMainClassAttribute
            extends UnboundAttribute<ModuleMainClassAttribute>
            implements ModuleMainClassAttribute {

        private static final Utf8Entry NAME = TemporaryConstantPool.INSTANCE.utf8Entry(Attributes.NAME_MODULE_MAIN_CLASS);

        final ClassEntry mainClass;

        public UnboundModuleMainClassAttribute(ClassEntry mainClass) {
            super(Attributes.moduleMainClass());
            this.mainClass = requireNonNull(mainClass);
        }

        @Override
        public ClassEntry mainClass() {
            return mainClass;
        }

        @Override
        public Utf8Entry attributeName() {
            return NAME;
        }
    }

    public static final class UnboundModuleHashesAttribute
            extends UnboundAttribute<ModuleHashesAttribute>
            implements ModuleHashesAttribute {

        private static final Utf8Entry NAME = TemporaryConstantPool.INSTANCE.utf8Entry(Attributes.NAME_MODULE_HASHES);

        private final Utf8Entry algorithm;
        private final List<ModuleHashInfo> hashes;

        public UnboundModuleHashesAttribute(Utf8Entry algorithm, List<ModuleHashInfo> hashes) {
            super(Attributes.moduleHashes());
            this.algorithm = requireNonNull(algorithm);
            this.hashes = List.copyOf(hashes);
        }

        @Override
        public Utf8Entry algorithm() {
            return algorithm;
        }

        @Override
        public List<ModuleHashInfo> hashes() {
            return hashes;
        }

        @Override
        public Utf8Entry attributeName() {
            return NAME;
        }
    }

    public static final class UnboundModulePackagesAttribute
            extends UnboundAttribute<ModulePackagesAttribute>
            implements ModulePackagesAttribute {

        private static final Utf8Entry NAME = TemporaryConstantPool.INSTANCE.utf8Entry(Attributes.NAME_MODULE_PACKAGES);

        private final Collection<PackageEntry> packages;

        public UnboundModulePackagesAttribute(Collection<PackageEntry> packages) {
            super(Attributes.modulePackages());
            this.packages = List.copyOf(packages);
        }

        @Override
        public List<PackageEntry> packages() {
            return List.copyOf(packages);
        }

        @Override
        public Utf8Entry attributeName() {
            return NAME;
        }
    }

    public static final class UnboundModuleResolutionAttribute
            extends UnboundAttribute<ModuleResolutionAttribute>
            implements ModuleResolutionAttribute {

        private static final Utf8Entry NAME = TemporaryConstantPool.INSTANCE.utf8Entry(Attributes.NAME_MODULE_RESOLUTION);

        private final int resolutionFlags;

        public UnboundModuleResolutionAttribute(int flags) {
            super(Attributes.moduleResolution());
            resolutionFlags = flags;
        }

        @Override
        public int resolutionFlags() {
            return resolutionFlags;
        }

        @Override
        public Utf8Entry attributeName() {
            return NAME;
        }
    }

    public static final class UnboundPermittedSubclassesAttribute
            extends UnboundAttribute<PermittedSubclassesAttribute>
            implements PermittedSubclassesAttribute {

        private static final Utf8Entry NAME = TemporaryConstantPool.INSTANCE.utf8Entry(Attributes.NAME_PERMITTED_SUBCLASSES);

        private final List<ClassEntry> permittedSubclasses;

        public UnboundPermittedSubclassesAttribute(List<ClassEntry> permittedSubclasses) {
            super(Attributes.permittedSubclasses());
            this.permittedSubclasses = List.copyOf(permittedSubclasses);
        }

        @Override
        public List<ClassEntry> permittedSubclasses() {
            return permittedSubclasses;
        }

        @Override
        public Utf8Entry attributeName() {
            return NAME;
        }
    }

    public static final class UnboundNestMembersAttribute
            extends UnboundAttribute<NestMembersAttribute>
            implements NestMembersAttribute {

        private static final Utf8Entry NAME = TemporaryConstantPool.INSTANCE.utf8Entry(Attributes.NAME_NEST_MEMBERS);

        private final List<ClassEntry> memberEntries;

        public UnboundNestMembersAttribute(List<ClassEntry> memberEntries) {
            super(Attributes.nestMembers());
            this.memberEntries = List.copyOf(memberEntries);
        }

        @Override
        public List<ClassEntry> nestMembers() {
            return memberEntries;
        }

        @Override
        public Utf8Entry attributeName() {
            return NAME;
        }
    }

    public static final class UnboundNestHostAttribute
            extends UnboundAttribute<NestHostAttribute>
            implements NestHostAttribute {

        private static final Utf8Entry NAME = TemporaryConstantPool.INSTANCE.utf8Entry(Attributes.NAME_NEST_HOST);

        private final ClassEntry hostEntry;

        public UnboundNestHostAttribute(ClassEntry hostEntry) {
            super(Attributes.nestHost());
            this.hostEntry = requireNonNull(hostEntry);
        }

        @Override
        public ClassEntry nestHost() {
            return hostEntry;
        }

        @Override
        public Utf8Entry attributeName() {
            return NAME;
        }
    }

    public static final class UnboundCompilationIDAttribute
            extends UnboundAttribute<CompilationIDAttribute>
            implements CompilationIDAttribute {

        private static final Utf8Entry NAME = TemporaryConstantPool.INSTANCE.utf8Entry(Attributes.NAME_COMPILATION_ID);

        private final Utf8Entry idEntry;

        public UnboundCompilationIDAttribute(Utf8Entry idEntry) {
            super(Attributes.compilationId());
            this.idEntry = requireNonNull(idEntry);
        }

        @Override
        public Utf8Entry compilationId() {
            return idEntry;
        }

        @Override
        public Utf8Entry attributeName() {
            return NAME;
        }
    }

    public static final class UnboundSourceIDAttribute
            extends UnboundAttribute<SourceIDAttribute>
            implements SourceIDAttribute {

        private static final Utf8Entry NAME = TemporaryConstantPool.INSTANCE.utf8Entry(Attributes.NAME_SOURCE_ID);

        private final Utf8Entry idEntry;

        public UnboundSourceIDAttribute(Utf8Entry idEntry) {
            super(Attributes.sourceId());
            this.idEntry = requireNonNull(idEntry);
        }

        @Override
        public Utf8Entry sourceId() {
            return idEntry;
        }

        @Override
        public Utf8Entry attributeName() {
            return NAME;
        }
    }

    public static final class UnboundSourceDebugExtensionAttribute
        extends UnboundAttribute<SourceDebugExtensionAttribute>
            implements SourceDebugExtensionAttribute {

        private static final Utf8Entry NAME = TemporaryConstantPool.INSTANCE.utf8Entry(Attributes.NAME_SOURCE_DEBUG_EXTENSION);

        private final byte[] contents;

        public UnboundSourceDebugExtensionAttribute(byte[] contents) {
            super(Attributes.sourceDebugExtension());
            this.contents = requireNonNull(contents);
        }

        @Override
        public byte[] contents() {
            return contents;
        }

        @Override
        public Utf8Entry attributeName() {
            return NAME;
        }
    }

    public static final class UnboundCharacterRangeTableAttribute
        extends UnboundAttribute<CharacterRangeTableAttribute>
            implements CharacterRangeTableAttribute {

        private static final Utf8Entry NAME = TemporaryConstantPool.INSTANCE.utf8Entry(Attributes.NAME_CHARACTER_RANGE_TABLE);

        private final List<CharacterRangeInfo> ranges;

        public UnboundCharacterRangeTableAttribute(List<CharacterRangeInfo> ranges) {
            super(Attributes.characterRangeTable());
            this.ranges = List.copyOf(ranges);
        }

        @Override
        public List<CharacterRangeInfo> characterRangeTable() {
            return ranges;
        }

        @Override
        public Utf8Entry attributeName() {
            return NAME;
        }
    }

    public static final class UnboundLineNumberTableAttribute
        extends UnboundAttribute<LineNumberTableAttribute>
            implements LineNumberTableAttribute {

        private static final Utf8Entry NAME = TemporaryConstantPool.INSTANCE.utf8Entry(Attributes.NAME_LINE_NUMBER_TABLE);

        private final List<LineNumberInfo> lines;

        public UnboundLineNumberTableAttribute(List<LineNumberInfo> lines) {
            super(Attributes.lineNumberTable());
            this.lines = List.copyOf(lines);
        }

        @Override
        public List<LineNumberInfo> lineNumbers() {
            return lines;
        }

        @Override
        public Utf8Entry attributeName() {
            return NAME;
        }
    }

    public static final class UnboundLocalVariableTableAttribute
        extends UnboundAttribute<LocalVariableTableAttribute>
            implements LocalVariableTableAttribute {

        private static final Utf8Entry NAME = TemporaryConstantPool.INSTANCE.utf8Entry(Attributes.NAME_LOCAL_VARIABLE_TABLE);

        private final List<LocalVariableInfo> locals;

        public UnboundLocalVariableTableAttribute(List<LocalVariableInfo> locals) {
            super(Attributes.localVariableTable());
            this.locals = List.copyOf(locals);
        }

        @Override
        public List<LocalVariableInfo> localVariables() {
            return locals;
        }

        @Override
        public Utf8Entry attributeName() {
            return NAME;
        }
    }

    public static final class UnboundLocalVariableTypeTableAttribute
        extends UnboundAttribute<LocalVariableTypeTableAttribute>
            implements LocalVariableTypeTableAttribute {

        private static final Utf8Entry NAME = TemporaryConstantPool.INSTANCE.utf8Entry(Attributes.NAME_LOCAL_VARIABLE_TYPE_TABLE);

        private final List<LocalVariableTypeInfo> locals;

        public UnboundLocalVariableTypeTableAttribute(List<LocalVariableTypeInfo> locals) {
            super(Attributes.localVariableTypeTable());
            this.locals = List.copyOf(locals);
        }

        @Override
        public List<LocalVariableTypeInfo> localVariableTypes() {
            return locals;
        }

        @Override
        public Utf8Entry attributeName() {
            return NAME;
        }
    }

    public static final class UnboundRuntimeVisibleAnnotationsAttribute
            extends UnboundAttribute<RuntimeVisibleAnnotationsAttribute>
            implements RuntimeVisibleAnnotationsAttribute {

        private static final Utf8Entry NAME = TemporaryConstantPool.INSTANCE.utf8Entry(Attributes.NAME_RUNTIME_VISIBLE_ANNOTATIONS);

        private final List<Annotation> elements;

        public UnboundRuntimeVisibleAnnotationsAttribute(List<Annotation> elements) {
            super(Attributes.runtimeVisibleAnnotations());
            this.elements = List.copyOf(elements);
        }

        @Override
        public List<Annotation> annotations() {
            return elements;
        }

        @Override
        public Utf8Entry attributeName() {
            return NAME;
        }
    }

    public static final class UnboundRuntimeInvisibleAnnotationsAttribute
            extends UnboundAttribute<RuntimeInvisibleAnnotationsAttribute>
            implements RuntimeInvisibleAnnotationsAttribute {

        private static final Utf8Entry NAME = TemporaryConstantPool.INSTANCE.utf8Entry(Attributes.NAME_RUNTIME_INVISIBLE_ANNOTATIONS);

        private final List<Annotation> elements;

        public UnboundRuntimeInvisibleAnnotationsAttribute(List<Annotation> elements) {
            super(Attributes.runtimeInvisibleAnnotations());
            this.elements = List.copyOf(elements);
        }

        @Override
        public List<Annotation> annotations() {
            return elements;
        }

        @Override
        public Utf8Entry attributeName() {
            return NAME;
        }
    }

    public static final class UnboundRuntimeVisibleParameterAnnotationsAttribute
            extends UnboundAttribute<RuntimeVisibleParameterAnnotationsAttribute>
            implements RuntimeVisibleParameterAnnotationsAttribute {

        private static final Utf8Entry NAME = TemporaryConstantPool.INSTANCE.utf8Entry(Attributes.NAME_RUNTIME_VISIBLE_PARAMETER_ANNOTATIONS);

        private final List<List<Annotation>> elements;

        public UnboundRuntimeVisibleParameterAnnotationsAttribute(List<List<Annotation>> elements) {
            super(Attributes.runtimeVisibleParameterAnnotations());
            // deep copy
            var array = elements.toArray().clone();
            for (int i = 0; i < array.length; i++) {
                array[i] = List.copyOf((List<?>) array[i]);
            }

            this.elements = SharedSecrets.getJavaUtilCollectionAccess().listFromTrustedArray(array);
        }

        @Override
        public List<List<Annotation>> parameterAnnotations() {
            return elements;
        }

        @Override
        public Utf8Entry attributeName() {
            return NAME;
        }
    }

    public static final class UnboundRuntimeInvisibleParameterAnnotationsAttribute
            extends UnboundAttribute<RuntimeInvisibleParameterAnnotationsAttribute>
            implements RuntimeInvisibleParameterAnnotationsAttribute {

        private static final Utf8Entry NAME = TemporaryConstantPool.INSTANCE.utf8Entry(Attributes.NAME_RUNTIME_INVISIBLE_PARAMETER_ANNOTATIONS);

        private final List<List<Annotation>> elements;

        public UnboundRuntimeInvisibleParameterAnnotationsAttribute(List<List<Annotation>> elements) {
            super(Attributes.runtimeInvisibleParameterAnnotations());
            // deep copy
            var array = elements.toArray().clone();
            for (int i = 0; i < array.length; i++) {
                array[i] = List.copyOf((List<?>) array[i]);
            }

            this.elements = SharedSecrets.getJavaUtilCollectionAccess().listFromTrustedArray(array);
        }

        @Override
        public List<List<Annotation>> parameterAnnotations() {
            return elements;
        }

        @Override
        public Utf8Entry attributeName() {
            return NAME;
        }
    }

    public static final class UnboundRuntimeVisibleTypeAnnotationsAttribute
            extends UnboundAttribute<RuntimeVisibleTypeAnnotationsAttribute>
            implements RuntimeVisibleTypeAnnotationsAttribute {

        private static final Utf8Entry NAME = TemporaryConstantPool.INSTANCE.utf8Entry(Attributes.NAME_RUNTIME_VISIBLE_TYPE_ANNOTATIONS);

        private final List<TypeAnnotation> elements;

        public UnboundRuntimeVisibleTypeAnnotationsAttribute(List<TypeAnnotation> elements) {
            super(Attributes.runtimeVisibleTypeAnnotations());
            this.elements = List.copyOf(elements);
        }

        @Override
        public List<TypeAnnotation> annotations() {
            return elements;
        }

        @Override
        public Utf8Entry attributeName() {
            return NAME;
        }
    }

    public static final class UnboundRuntimeInvisibleTypeAnnotationsAttribute
            extends UnboundAttribute<RuntimeInvisibleTypeAnnotationsAttribute>
            implements RuntimeInvisibleTypeAnnotationsAttribute {

        private static final Utf8Entry NAME = TemporaryConstantPool.INSTANCE.utf8Entry(Attributes.NAME_RUNTIME_INVISIBLE_TYPE_ANNOTATIONS);

        private final List<TypeAnnotation> elements;

        public UnboundRuntimeInvisibleTypeAnnotationsAttribute(List<TypeAnnotation> elements) {
            super(Attributes.runtimeInvisibleTypeAnnotations());
            this.elements = List.copyOf(elements);
        }

        @Override
        public List<TypeAnnotation> annotations() {
            return elements;
        }

        @Override
        public Utf8Entry attributeName() {
            return NAME;
        }
    }

    public record UnboundCharacterRangeInfo(int startPc, int endPc,
                                            int characterRangeStart,
                                            int characterRangeEnd,
                                            int flags)
            implements CharacterRangeInfo { }

    public record UnboundInnerClassInfo(ClassEntry innerClass,
                                        Optional<ClassEntry> outerClass,
                                        Optional<Utf8Entry> innerName,
                                        int flagsMask)
            implements InnerClassInfo {
        public UnboundInnerClassInfo {
            requireNonNull(innerClass);
            requireNonNull(outerClass);
            requireNonNull(innerName);
        }
    }

    public record UnboundLineNumberInfo(int startPc, int lineNumber)
            implements LineNumberInfo { }

    public record UnboundLocalVariableInfo(int startPc, int length,
                                           Utf8Entry name,
                                           Utf8Entry type,
                                           int slot)
            implements LocalVariableInfo {
        public UnboundLocalVariableInfo {
            requireNonNull(name);
            requireNonNull(type);
        }
    }

    public record UnboundLocalVariableTypeInfo(int startPc, int length,
                                               Utf8Entry name,
                                               Utf8Entry signature,
                                               int slot)
            implements LocalVariableTypeInfo {
        public UnboundLocalVariableTypeInfo {
            requireNonNull(name);
            requireNonNull(signature);
        }
    }

    public record UnboundMethodParameterInfo(Optional<Utf8Entry> name, int flagsMask)
            implements MethodParameterInfo {
        public UnboundMethodParameterInfo {
            requireNonNull(name);
        }
    }

    public record UnboundModuleExportInfo(PackageEntry exportedPackage,
                                          int exportsFlagsMask,
                                          List<ModuleEntry> exportsTo)
            implements ModuleExportInfo {
        public UnboundModuleExportInfo {
            requireNonNull(exportedPackage);
            exportsTo = List.copyOf(exportsTo);
        }
    }

    public record UnboundModuleHashInfo(ModuleEntry moduleName,
                                        byte[] hash) implements ModuleHashInfo {
        public UnboundModuleHashInfo {
            requireNonNull(moduleName);
            requireNonNull(hash);
        }
    }

    public record UnboundModuleOpenInfo(PackageEntry openedPackage, int opensFlagsMask,
                                        List<ModuleEntry> opensTo)
            implements ModuleOpenInfo {
        public UnboundModuleOpenInfo {
            requireNonNull(openedPackage);
            opensTo = List.copyOf(opensTo);
        }
    }

    public record UnboundModuleProvideInfo(ClassEntry provides,
                                           List<ClassEntry> providesWith)
            implements ModuleProvideInfo {
        public UnboundModuleProvideInfo {
            requireNonNull(provides);
            providesWith = List.copyOf(providesWith);
        }
    }

    public record UnboundModuleRequiresInfo(ModuleEntry requires, int requiresFlagsMask,
                                            Optional<Utf8Entry> requiresVersion)
            implements ModuleRequireInfo {
        public UnboundModuleRequiresInfo {
            requireNonNull(requires);
            requireNonNull(requiresVersion);
        }
    }

    public record UnboundRecordComponentInfo(Utf8Entry name,
                                             Utf8Entry descriptor,
                                             List<Attribute<?>> attributes)
            implements RecordComponentInfo {
        public UnboundRecordComponentInfo {
            requireNonNull(name);
            requireNonNull(descriptor);
            attributes = List.copyOf(attributes);
        }
    }

    public record UnboundTypeAnnotation(TargetInfo targetInfo,
                                        List<TypePathComponent> targetPath,
                                        Annotation annotation) implements TypeAnnotation {

        public UnboundTypeAnnotation {
            requireNonNull(targetInfo);
            targetPath = List.copyOf(targetPath);
            requireNonNull(annotation);
        }
    }

    public record TypePathComponentImpl(TypeAnnotation.TypePathComponent.Kind typePathKind, int typeArgumentIndex)
            implements TypeAnnotation.TypePathComponent {}

    public static final class UnboundModuleAttribute extends UnboundAttribute<ModuleAttribute> implements ModuleAttribute {

        private static final Utf8Entry NAME = TemporaryConstantPool.INSTANCE.utf8Entry(Attributes.NAME_MODULE);

        private final ModuleEntry moduleName;
        private final int moduleFlags;
        private final Utf8Entry moduleVersion;
        private final List<ModuleRequireInfo> requires;
        private final List<ModuleExportInfo> exports;
        private final List<ModuleOpenInfo> opens;
        private final List<ClassEntry> uses;
        private final List<ModuleProvideInfo> provides;

        public UnboundModuleAttribute(ModuleEntry moduleName,
                                      int moduleFlags,
                                      Utf8Entry moduleVersion,
                                      Collection<ModuleRequireInfo> requires,
                                      Collection<ModuleExportInfo> exports,
                                      Collection<ModuleOpenInfo> opens,
                                      Collection<ClassEntry> uses,
                                      Collection<ModuleProvideInfo> provides)
        {
            super(Attributes.module());
            this.moduleName = requireNonNull(moduleName);
            this.moduleFlags = moduleFlags;
            this.moduleVersion = moduleVersion;
            this.requires = List.copyOf(requires);
            this.exports = List.copyOf(exports);
            this.opens = List.copyOf(opens);
            this.uses = List.copyOf(uses);
            this.provides = List.copyOf(provides);
        }

        @Override
        public ModuleEntry moduleName() {
            return moduleName;
        }

        @Override
        public int moduleFlagsMask() {
            return moduleFlags;
        }

        @Override
        public Optional<Utf8Entry> moduleVersion() {
            return Optional.ofNullable(moduleVersion);
        }

        @Override
        public List<ModuleRequireInfo> requires() {
            return requires;
        }

        @Override
        public List<ModuleExportInfo> exports() {
            return exports;
        }

        @Override
        public List<ModuleOpenInfo> opens() {
            return opens;
        }

        @Override
        public List<ClassEntry> uses() {
            return uses;
        }

        @Override
        public List<ModuleProvideInfo> provides() {
            return provides;
        }

        @Override
        public Utf8Entry attributeName() {
            return NAME;
        }
    }

    public abstract static non-sealed class AdHocAttribute<T extends Attribute<T>>
            extends UnboundAttribute<T> {

        public AdHocAttribute(AttributeMapper<T> mapper) {
            super(mapper);
        }

        public abstract void writeBody(BufWriterImpl b);

        @Override
        public void writeTo(BufWriterImpl b) {
            b.writeIndex(b.constantPool().utf8Entry(mapper.name()));
            int lengthIndex = b.skip(4);
            writeBody(b);
            int written = b.size() - lengthIndex - 4;
            b.patchInt(lengthIndex, written);
        }
    }

    public static final class EmptyBootstrapAttribute
            extends UnboundAttribute<BootstrapMethodsAttribute>
            implements BootstrapMethodsAttribute {

        private static final Utf8Entry NAME = TemporaryConstantPool.INSTANCE.utf8Entry(Attributes.NAME_BOOTSTRAP_METHODS);

        public EmptyBootstrapAttribute() {
            super(Attributes.bootstrapMethods());
        }

        @Override
        public int bootstrapMethodsSize() {
            return 0;
        }

        @Override
        public List<BootstrapMethodEntry> bootstrapMethods() {
            return List.of();
        }

        @Override
        public Utf8Entry attributeName() {
            return NAME;
        }
    }
}
