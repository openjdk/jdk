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
    public String attributeName() {
        return mapper.name();
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

        private final ConstantValueEntry entry;

        public UnboundConstantValueAttribute(ConstantValueEntry entry) {
            super(Attributes.constantValue());
            this.entry = requireNonNull(entry);
        }

        @Override
        public ConstantValueEntry constant() {
            return entry;
        }

    }

    public static final class UnboundDeprecatedAttribute
            extends UnboundAttribute<DeprecatedAttribute>
            implements DeprecatedAttribute {
        public UnboundDeprecatedAttribute() {
            super(Attributes.deprecated());
        }
    }

    public static final class UnboundSyntheticAttribute
            extends UnboundAttribute<SyntheticAttribute>
            implements SyntheticAttribute {
        public UnboundSyntheticAttribute() {
            super(Attributes.synthetic());
        }
    }

    public static final class UnboundSignatureAttribute
            extends UnboundAttribute<SignatureAttribute>
            implements SignatureAttribute {
        private final Utf8Entry signature;

        public UnboundSignatureAttribute(Utf8Entry signature) {
            super(Attributes.signature());
            this.signature = requireNonNull(signature);
        }

        @Override
        public Utf8Entry signature() {
            return signature;
        }
    }

    public static final class UnboundExceptionsAttribute
            extends UnboundAttribute<ExceptionsAttribute>
            implements ExceptionsAttribute {
        private final List<ClassEntry> exceptions;

        public UnboundExceptionsAttribute(List<ClassEntry> exceptions) {
            super(Attributes.exceptions());
            this.exceptions = List.copyOf(exceptions);
        }

        @Override
        public List<ClassEntry> exceptions() {
            return exceptions;
        }
    }

    public static final class UnboundAnnotationDefaultAttribute
            extends UnboundAttribute<AnnotationDefaultAttribute>
            implements AnnotationDefaultAttribute {
        private final AnnotationValue annotationDefault;

        public UnboundAnnotationDefaultAttribute(AnnotationValue annotationDefault) {
            super(Attributes.annotationDefault());
            this.annotationDefault = requireNonNull(annotationDefault);
        }

        @Override
        public AnnotationValue defaultValue() {
            return annotationDefault;
        }
    }

    public static final class UnboundSourceFileAttribute extends UnboundAttribute<SourceFileAttribute>
            implements SourceFileAttribute {
        private final Utf8Entry sourceFile;

        public UnboundSourceFileAttribute(Utf8Entry sourceFile) {
            super(Attributes.sourceFile());
            this.sourceFile = requireNonNull(sourceFile);
        }

        @Override
        public Utf8Entry sourceFile() {
            return sourceFile;
        }

    }

    public static final class UnboundStackMapTableAttribute extends UnboundAttribute<StackMapTableAttribute>
            implements StackMapTableAttribute {
        private final List<StackMapFrameInfo> entries;

        public UnboundStackMapTableAttribute(List<StackMapFrameInfo> entries) {
            super(Attributes.stackMapTable());
            this.entries = List.copyOf(entries);
        }

        @Override
        public List<StackMapFrameInfo> entries() {
            return entries;
        }
    }

    public static final class UnboundInnerClassesAttribute
            extends UnboundAttribute<InnerClassesAttribute>
            implements InnerClassesAttribute {
        private final List<InnerClassInfo> innerClasses;

        public UnboundInnerClassesAttribute(List<InnerClassInfo> innerClasses) {
            super(Attributes.innerClasses());
            this.innerClasses = List.copyOf(innerClasses);
        }

        @Override
        public List<InnerClassInfo> classes() {
            return innerClasses;
        }
    }

    public static final class UnboundRecordAttribute
            extends UnboundAttribute<RecordAttribute>
            implements RecordAttribute {
        private final List<RecordComponentInfo> components;

        public UnboundRecordAttribute(List<RecordComponentInfo> components) {
            super(Attributes.record());
            this.components = List.copyOf(components);
        }

        @Override
        public List<RecordComponentInfo> components() {
            return components;
        }
    }

    public static final class UnboundEnclosingMethodAttribute
            extends UnboundAttribute<EnclosingMethodAttribute>
            implements EnclosingMethodAttribute {
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
    }

    public static final class UnboundMethodParametersAttribute
            extends UnboundAttribute<MethodParametersAttribute>
            implements MethodParametersAttribute {
        private final List<MethodParameterInfo> parameters;

        public UnboundMethodParametersAttribute(List<MethodParameterInfo> parameters) {
            super(Attributes.methodParameters());
            this.parameters = List.copyOf(parameters);
        }

        @Override
        public List<MethodParameterInfo> parameters() {
            return parameters;
        }
    }

    public static final class UnboundModuleTargetAttribute
            extends UnboundAttribute<ModuleTargetAttribute>
            implements ModuleTargetAttribute {
        final Utf8Entry moduleTarget;

        public UnboundModuleTargetAttribute(Utf8Entry moduleTarget) {
            super(Attributes.moduleTarget());
            this.moduleTarget = requireNonNull(moduleTarget);
        }

        @Override
        public Utf8Entry targetPlatform() {
            return moduleTarget;
        }
    }

    public static final class UnboundModuleMainClassAttribute
            extends UnboundAttribute<ModuleMainClassAttribute>
            implements ModuleMainClassAttribute {
        final ClassEntry mainClass;

        public UnboundModuleMainClassAttribute(ClassEntry mainClass) {
            super(Attributes.moduleMainClass());
            this.mainClass = requireNonNull(mainClass);
        }

        @Override
        public ClassEntry mainClass() {
            return mainClass;
        }
    }

    public static final class UnboundModuleHashesAttribute
            extends UnboundAttribute<ModuleHashesAttribute>
            implements ModuleHashesAttribute {
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
    }

    public static final class UnboundModulePackagesAttribute
            extends UnboundAttribute<ModulePackagesAttribute>
            implements ModulePackagesAttribute {
        private final Collection<PackageEntry> packages;

        public UnboundModulePackagesAttribute(Collection<PackageEntry> packages) {
            super(Attributes.modulePackages());
            this.packages = List.copyOf(packages);
        }

        @Override
        public List<PackageEntry> packages() {
            return List.copyOf(packages);
        }
    }

    public static final class UnboundModuleResolutionAttribute
            extends UnboundAttribute<ModuleResolutionAttribute>
            implements ModuleResolutionAttribute {
        private final int resolutionFlags;

        public UnboundModuleResolutionAttribute(int flags) {
            super(Attributes.moduleResolution());
            resolutionFlags = flags;
        }

        @Override
        public int resolutionFlags() {
            return resolutionFlags;
        }
    }

    public static final class UnboundPermittedSubclassesAttribute
            extends UnboundAttribute<PermittedSubclassesAttribute>
            implements PermittedSubclassesAttribute {
        private final List<ClassEntry> permittedSubclasses;

        public UnboundPermittedSubclassesAttribute(List<ClassEntry> permittedSubclasses) {
            super(Attributes.permittedSubclasses());
            this.permittedSubclasses = List.copyOf(permittedSubclasses);
        }

        @Override
        public List<ClassEntry> permittedSubclasses() {
            return permittedSubclasses;
        }
    }

    public static final class UnboundNestMembersAttribute
            extends UnboundAttribute<NestMembersAttribute>
            implements NestMembersAttribute {
        private final List<ClassEntry> memberEntries;

        public UnboundNestMembersAttribute(List<ClassEntry> memberEntries) {
            super(Attributes.nestMembers());
            this.memberEntries = List.copyOf(memberEntries);
        }

        @Override
        public List<ClassEntry> nestMembers() {
            return memberEntries;
        }
    }

    public static final class UnboundNestHostAttribute
            extends UnboundAttribute<NestHostAttribute>
            implements NestHostAttribute {
        private final ClassEntry hostEntry;

        public UnboundNestHostAttribute(ClassEntry hostEntry) {
            super(Attributes.nestHost());
            this.hostEntry = requireNonNull(hostEntry);
        }

        @Override
        public ClassEntry nestHost() {
            return hostEntry;
        }
    }

    public static final class UnboundCompilationIDAttribute
            extends UnboundAttribute<CompilationIDAttribute>
            implements CompilationIDAttribute {
        private final Utf8Entry idEntry;

        public UnboundCompilationIDAttribute(Utf8Entry idEntry) {
            super(Attributes.compilationId());
            this.idEntry = requireNonNull(idEntry);
        }

        @Override
        public Utf8Entry compilationId() {
            return idEntry;
        }
    }

    public static final class UnboundSourceIDAttribute
            extends UnboundAttribute<SourceIDAttribute>
            implements SourceIDAttribute {
        private final Utf8Entry idEntry;

        public UnboundSourceIDAttribute(Utf8Entry idEntry) {
            super(Attributes.sourceId());
            this.idEntry = requireNonNull(idEntry);
        }

        @Override
        public Utf8Entry sourceId() {
            return idEntry;
        }
    }

    public static final class UnboundSourceDebugExtensionAttribute
        extends UnboundAttribute<SourceDebugExtensionAttribute>
            implements SourceDebugExtensionAttribute {
        private final byte[] contents;

        public UnboundSourceDebugExtensionAttribute(byte[] contents) {
            super(Attributes.sourceDebugExtension());
            this.contents = requireNonNull(contents);
        }

        @Override
        public byte[] contents() {
            return contents;
        }
    }

    public static final class UnboundCharacterRangeTableAttribute
        extends UnboundAttribute<CharacterRangeTableAttribute>
            implements CharacterRangeTableAttribute {
        private final List<CharacterRangeInfo> ranges;

        public UnboundCharacterRangeTableAttribute(List<CharacterRangeInfo> ranges) {
            super(Attributes.characterRangeTable());
            this.ranges = List.copyOf(ranges);
        }

        @Override
        public List<CharacterRangeInfo> characterRangeTable() {
            return ranges;
        }
    }

    public static final class UnboundLineNumberTableAttribute
        extends UnboundAttribute<LineNumberTableAttribute>
            implements LineNumberTableAttribute {
        private final List<LineNumberInfo> lines;

        public UnboundLineNumberTableAttribute(List<LineNumberInfo> lines) {
            super(Attributes.lineNumberTable());
            this.lines = List.copyOf(lines);
        }

        @Override
        public List<LineNumberInfo> lineNumbers() {
            return lines;
        }
    }

    public static final class UnboundLocalVariableTableAttribute
        extends UnboundAttribute<LocalVariableTableAttribute>
            implements LocalVariableTableAttribute {
        private final List<LocalVariableInfo> locals;

        public UnboundLocalVariableTableAttribute(List<LocalVariableInfo> locals) {
            super(Attributes.localVariableTable());
            this.locals = List.copyOf(locals);
        }

        @Override
        public List<LocalVariableInfo> localVariables() {
            return locals;
        }
    }

    public static final class UnboundLocalVariableTypeTableAttribute
        extends UnboundAttribute<LocalVariableTypeTableAttribute>
            implements LocalVariableTypeTableAttribute {
        private final List<LocalVariableTypeInfo> locals;

        public UnboundLocalVariableTypeTableAttribute(List<LocalVariableTypeInfo> locals) {
            super(Attributes.localVariableTypeTable());
            this.locals = List.copyOf(locals);
        }

        @Override
        public List<LocalVariableTypeInfo> localVariableTypes() {
            return locals;
        }
    }

    public static final class UnboundRuntimeVisibleAnnotationsAttribute
            extends UnboundAttribute<RuntimeVisibleAnnotationsAttribute>
            implements RuntimeVisibleAnnotationsAttribute {
        private final List<Annotation> elements;

        public UnboundRuntimeVisibleAnnotationsAttribute(List<Annotation> elements) {
            super(Attributes.runtimeVisibleAnnotations());
            this.elements = List.copyOf(elements);
        }

        @Override
        public List<Annotation> annotations() {
            return elements;
        }
    }

    public static final class UnboundRuntimeInvisibleAnnotationsAttribute
            extends UnboundAttribute<RuntimeInvisibleAnnotationsAttribute>
            implements RuntimeInvisibleAnnotationsAttribute {
        private final List<Annotation> elements;

        public UnboundRuntimeInvisibleAnnotationsAttribute(List<Annotation> elements) {
            super(Attributes.runtimeInvisibleAnnotations());
            this.elements = List.copyOf(elements);
        }

        @Override
        public List<Annotation> annotations() {
            return elements;
        }
    }

    public static final class UnboundRuntimeVisibleParameterAnnotationsAttribute
            extends UnboundAttribute<RuntimeVisibleParameterAnnotationsAttribute>
            implements RuntimeVisibleParameterAnnotationsAttribute {
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
    }

    public static final class UnboundRuntimeInvisibleParameterAnnotationsAttribute
            extends UnboundAttribute<RuntimeInvisibleParameterAnnotationsAttribute>
            implements RuntimeInvisibleParameterAnnotationsAttribute {
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
    }

    public static final class UnboundRuntimeVisibleTypeAnnotationsAttribute
            extends UnboundAttribute<RuntimeVisibleTypeAnnotationsAttribute>
            implements RuntimeVisibleTypeAnnotationsAttribute {
        private final List<TypeAnnotation> elements;

        public UnboundRuntimeVisibleTypeAnnotationsAttribute(List<TypeAnnotation> elements) {
            super(Attributes.runtimeVisibleTypeAnnotations());
            this.elements = List.copyOf(elements);
        }

        @Override
        public List<TypeAnnotation> annotations() {
            return elements;
        }
    }

    public static final class UnboundRuntimeInvisibleTypeAnnotationsAttribute
            extends UnboundAttribute<RuntimeInvisibleTypeAnnotationsAttribute>
            implements RuntimeInvisibleTypeAnnotationsAttribute {
        private final List<TypeAnnotation> elements;

        public UnboundRuntimeInvisibleTypeAnnotationsAttribute(List<TypeAnnotation> elements) {
            super(Attributes.runtimeInvisibleTypeAnnotations());
            this.elements = List.copyOf(elements);
        }

        @Override
        public List<TypeAnnotation> annotations() {
            return elements;
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
    }
}
