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
package java.lang.classfile;

import java.lang.classfile.AttributeMapper.AttributeStability;
import java.lang.classfile.attribute.*;
import jdk.internal.classfile.impl.AbstractAttributeMapper.*;
import jdk.internal.javac.PreviewFeature;

/**
 * Attribute mappers for standard classfile attributes.
 * <p>
 * Unless otherwise specified, mappers returned by each method
 * do not permit multiple attribute instances in a given location.
 * <p>
 * The most stable {@link AttributeStability#STATELESS STATELESS} mappers are:
 * <ul>
 * <li>{@link #deprecated()}
 * <li>{@link #moduleResolution()}
 * <li>{@link #sourceDebugExtension()}
 * <li>{@link #synthetic()}
 * </ul>
 *
 * The mappers with {@link AttributeStability#CP_REFS CP_REFS} stability are:
 * <ul>
 * <li>{@link #annotationDefault()}
 * <li>{@link #bootstrapMethods()}
 * <li>{@link #code()}
 * <li>{@link #compilationId()}
 * <li>{@link #constantValue()}
 * <li>{@link #enclosingMethod()}
 * <li>{@link #exceptions()}
 * <li>{@link #innerClasses()}
 * <li>{@link #methodParameters()}
 * <li>{@link #module()}
 * <li>{@link #moduleHashes()}
 * <li>{@link #moduleMainClass()}
 * <li>{@link #modulePackages()}
 * <li>{@link #moduleTarget()}
 * <li>{@link #nestHost()}
 * <li>{@link #nestMembers()}
 * <li>{@link #permittedSubclasses()}
 * <li>{@link #record()}
 * <li>{@link #runtimeInvisibleAnnotations()}
 * <li>{@link #runtimeInvisibleParameterAnnotations()}
 * <li>{@link #runtimeVisibleAnnotations()}
 * <li>{@link #runtimeVisibleParameterAnnotations()}
 * <li>{@link #signature()}
 * <li>{@link #sourceFile()}
 * <li>{@link #sourceId()}
 * </ul>
 *
 * The mappers with {@link AttributeStability#LABELS LABELS} stability are:
 * <ul>
 * <li>{@link #characterRangeTable()}
 * <li>{@link #lineNumberTable()}
 * <li>{@link #localVariableTable()}
 * <li>{@link #localVariableTypeTable()}
 * </ul>
 *
 * The {@link AttributeStability#UNSTABLE UNSTABLE} mappers are:
 * <ul>
 * <li>{@link #runtimeInvisibleTypeAnnotations()}
 * <li>{@link #runtimeVisibleTypeAnnotations()}
 * </ul>
 *
 * @see AttributeMapper
 *
 * @since 22
 */
@PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
public final class Attributes {

    /** AnnotationDefault */
    public static final String NAME_ANNOTATION_DEFAULT = "AnnotationDefault";

    /** BootstrapMethods */
    public static final String NAME_BOOTSTRAP_METHODS = "BootstrapMethods";

    /** CharacterRangeTable */
    public static final String NAME_CHARACTER_RANGE_TABLE = "CharacterRangeTable";

    /** Code */
    public static final String NAME_CODE = "Code";

    /** CompilationID */
    public static final String NAME_COMPILATION_ID = "CompilationID";

    /** ConstantValue */
    public static final String NAME_CONSTANT_VALUE = "ConstantValue";

    /** Deprecated */
    public static final String NAME_DEPRECATED = "Deprecated";

    /** EnclosingMethod */
    public static final String NAME_ENCLOSING_METHOD = "EnclosingMethod";

    /** Exceptions */
    public static final String NAME_EXCEPTIONS = "Exceptions";

    /** InnerClasses */
    public static final String NAME_INNER_CLASSES = "InnerClasses";

    /** LineNumberTable */
    public static final String NAME_LINE_NUMBER_TABLE = "LineNumberTable";

    /** LocalVariableTable */
    public static final String NAME_LOCAL_VARIABLE_TABLE = "LocalVariableTable";

    /** LocalVariableTypeTable */
    public static final String NAME_LOCAL_VARIABLE_TYPE_TABLE = "LocalVariableTypeTable";

    /** MethodParameters */
    public static final String NAME_METHOD_PARAMETERS = "MethodParameters";

    /** Module */
    public static final String NAME_MODULE = "Module";

    /** ModuleHashes */
    public static final String NAME_MODULE_HASHES = "ModuleHashes";

    /** ModuleMainClass */
    public static final String NAME_MODULE_MAIN_CLASS = "ModuleMainClass";

    /** ModulePackages */
    public static final String NAME_MODULE_PACKAGES = "ModulePackages";

    /** ModuleResolution */
    public static final String NAME_MODULE_RESOLUTION = "ModuleResolution";

    /** ModuleTarget */
    public static final String NAME_MODULE_TARGET = "ModuleTarget";

    /** NestHost */
    public static final String NAME_NEST_HOST = "NestHost";

    /** NestMembers */
    public static final String NAME_NEST_MEMBERS = "NestMembers";

    /** PermittedSubclasses */
    public static final String NAME_PERMITTED_SUBCLASSES = "PermittedSubclasses";

    /** Record */
    public static final String NAME_RECORD = "Record";

    /** RuntimeInvisibleAnnotations */
    public static final String NAME_RUNTIME_INVISIBLE_ANNOTATIONS = "RuntimeInvisibleAnnotations";

    /** RuntimeInvisibleParameterAnnotations */
    public static final String NAME_RUNTIME_INVISIBLE_PARAMETER_ANNOTATIONS = "RuntimeInvisibleParameterAnnotations";

    /** RuntimeInvisibleTypeAnnotations */
    public static final String NAME_RUNTIME_INVISIBLE_TYPE_ANNOTATIONS = "RuntimeInvisibleTypeAnnotations";

    /** RuntimeVisibleAnnotations */
    public static final String NAME_RUNTIME_VISIBLE_ANNOTATIONS = "RuntimeVisibleAnnotations";

    /** RuntimeVisibleParameterAnnotations */
    public static final String NAME_RUNTIME_VISIBLE_PARAMETER_ANNOTATIONS = "RuntimeVisibleParameterAnnotations";

    /** RuntimeVisibleTypeAnnotations */
    public static final String NAME_RUNTIME_VISIBLE_TYPE_ANNOTATIONS = "RuntimeVisibleTypeAnnotations";

    /** Signature */
    public static final String NAME_SIGNATURE = "Signature";

    /** SourceDebugExtension */
    public static final String NAME_SOURCE_DEBUG_EXTENSION = "SourceDebugExtension";

    /** SourceFile */
    public static final String NAME_SOURCE_FILE = "SourceFile";

    /** SourceID */
    public static final String NAME_SOURCE_ID = "SourceID";

    /** StackMapTable */
    public static final String NAME_STACK_MAP_TABLE = "StackMapTable";

    /** Synthetic */
    public static final String NAME_SYNTHETIC = "Synthetic";

    private Attributes() {
    }

    /**
     * {@return Attribute mapper for the {@code AnnotationDefault} attribute}
     * @since 23
     */
    public static AttributeMapper<AnnotationDefaultAttribute> annotationDefault() {
        return AnnotationDefaultMapper.INSTANCE;
    }

    /**
     * {@return Attribute mapper for the {@code BootstrapMethods} attribute}
     * @since 23
     */
    public static AttributeMapper<BootstrapMethodsAttribute> bootstrapMethods() {
        return BootstrapMethodsMapper.INSTANCE;
    }

    /**
     * {@return Attribute mapper for the {@code CharacterRangeTable} attribute}
     * The mapper permits multiple instances in a given location.
     * @since 23
     */
    public static AttributeMapper<CharacterRangeTableAttribute> characterRangeTable() {
        return CharacterRangeTableMapper.INSTANCE;
    }

    /**
     * {@return Attribute mapper for the {@code Code} attribute}
     * @since 23
     */
    public static AttributeMapper<CodeAttribute> code() {
        return CodeMapper.INSTANCE;
    }

    /**
     * {@return Attribute mapper for the {@code CompilationID} attribute}
     * @since 23
     */
    public static AttributeMapper<CompilationIDAttribute> compilationId() {
        return CompilationIDMapper.INSTANCE;
    }

    /**
     * {@return Attribute mapper for the {@code ConstantValue} attribute}
     * @since 23
     */
    public static AttributeMapper<ConstantValueAttribute> constantValue() {
        return ConstantValueMapper.INSTANCE;
    }

    /**
     * {@return Attribute mapper for the {@code Deprecated} attribute}
     * The mapper permits multiple instances in a given location.
     * @since 23
     */
    public static AttributeMapper<DeprecatedAttribute> deprecated() {
        return DeprecatedMapper.INSTANCE;
    }

    /**
     * {@return Attribute mapper for the {@code EnclosingMethod} attribute}
     * @since 23
     */
    public static AttributeMapper<EnclosingMethodAttribute> enclosingMethod() {
        return EnclosingMethodMapper.INSTANCE;
    }

    /**
     * {@return Attribute mapper for the {@code Exceptions} attribute}
     * @since 23
     */
    public static AttributeMapper<ExceptionsAttribute> exceptions() {
        return ExceptionsMapper.INSTANCE;
    }

    /**
     * {@return Attribute mapper for the {@code InnerClasses} attribute}
     * @since 23
     */
    public static AttributeMapper<InnerClassesAttribute> innerClasses() {
        return InnerClassesMapper.INSTANCE;
    }

    /**
     * {@return Attribute mapper for the {@code LineNumberTable} attribute}
     * The mapper permits multiple instances in a given location.
     * @since 23
     */
    public static AttributeMapper<LineNumberTableAttribute> lineNumberTable() {
        return LineNumberTableMapper.INSTANCE;
    }

    /**
     * {@return Attribute mapper for the {@code LocalVariableTable} attribute}
     * The mapper permits multiple instances in a given location.
     * @since 23
     */
    public static AttributeMapper<LocalVariableTableAttribute> localVariableTable() {
        return LocalVariableTableMapper.INSTANCE;
    }

    /**
     * {@return Attribute mapper for the {@code LocalVariableTypeTable} attribute}
     * The mapper permits multiple instances in a given location.
     * @since 23
     */
    public static AttributeMapper<LocalVariableTypeTableAttribute> localVariableTypeTable() {
        return LocalVariableTypeTableMapper.INSTANCE;
    }

    /**
     * {@return Attribute mapper for the {@code MethodParameters} attribute}
     * @since 23
     */
    public static AttributeMapper<MethodParametersAttribute> methodParameters() {
        return MethodParametersMapper.INSTANCE;
    }

    /**
     * {@return Attribute mapper for the {@code Module} attribute}
     * @since 23
     */
    public static AttributeMapper<ModuleAttribute> module() {
        return ModuleMapper.INSTANCE;
    }

    /**
     * {@return Attribute mapper for the {@code ModuleHashes} attribute}
     * @since 23
     */
    public static AttributeMapper<ModuleHashesAttribute> moduleHashes() {
        return ModuleHashesMapper.INSTANCE;
    }

    /**
     * {@return Attribute mapper for the {@code ModuleMainClass} attribute}
     * @since 23
     */
    public static AttributeMapper<ModuleMainClassAttribute> moduleMainClass() {
        return ModuleMainClassMapper.INSTANCE;
    }

    /**
     * {@return Attribute mapper for the {@code ModulePackages} attribute}
     * @since 23
     */
    public static AttributeMapper<ModulePackagesAttribute> modulePackages() {
        return ModulePackagesMapper.INSTANCE;
    }

    /**
     * {@return Attribute mapper for the {@code ModuleResolution} attribute}
     * @since 23
     */
    public static AttributeMapper<ModuleResolutionAttribute> moduleResolution() {
        return ModuleResolutionMapper.INSTANCE;
    }

    /**
     * {@return Attribute mapper for the {@code ModuleTarget} attribute}
     * @since 23
     */
    public static AttributeMapper<ModuleTargetAttribute> moduleTarget() {
        return ModuleTargetMapper.INSTANCE;
    }

    /**
     * {@return Attribute mapper for the {@code NestHost} attribute}
     * @since 23
     */
    public static AttributeMapper<NestHostAttribute> nestHost() {
        return NestHostMapper.INSTANCE;
    }

    /**
     * {@return Attribute mapper for the {@code NestMembers} attribute}
     * @since 23
     */
    public static AttributeMapper<NestMembersAttribute> nestMembers() {
        return NestMembersMapper.INSTANCE;
    }

    /**
     * {@return Attribute mapper for the {@code PermittedSubclasses} attribute}
     * @since 23
     */
    public static AttributeMapper<PermittedSubclassesAttribute> permittedSubclasses() {
        return PermittedSubclassesMapper.INSTANCE;
    }

    /**
     * {@return Attribute mapper for the {@code Record} attribute}
     * @since 23
     */
    public static AttributeMapper<RecordAttribute> record() {
        return RecordMapper.INSTANCE;
    }

    /**
     * {@return Attribute mapper for the {@code RuntimeInvisibleAnnotations} attribute}
     * @since 23
     */
    public static AttributeMapper<RuntimeInvisibleAnnotationsAttribute> runtimeInvisibleAnnotations() {
        return RuntimeInvisibleAnnotationsMapper.INSTANCE;
    }

    /**
     * {@return Attribute mapper for the {@code RuntimeInvisibleParameterAnnotations} attribute}
     * @since 23
     */
    public static AttributeMapper<RuntimeInvisibleParameterAnnotationsAttribute> runtimeInvisibleParameterAnnotations() {
        return RuntimeInvisibleParameterAnnotationsMapper.INSTANCE;
    }

    /**
     * {@return Attribute mapper for the {@code RuntimeInvisibleTypeAnnotations} attribute}
     * @since 23
     */
    public static AttributeMapper<RuntimeInvisibleTypeAnnotationsAttribute> runtimeInvisibleTypeAnnotations() {
        return RuntimeInvisibleTypeAnnotationsMapper.INSTANCE;
    }

    /**
     * {@return Attribute mapper for the {@code RuntimeVisibleAnnotations} attribute}
     * @since 23
     */
    public static AttributeMapper<RuntimeVisibleAnnotationsAttribute> runtimeVisibleAnnotations() {
        return RuntimeVisibleAnnotationsMapper.INSTANCE;
    }

    /**
     * {@return Attribute mapper for the {@code RuntimeVisibleParameterAnnotations} attribute}
     * @since 23
     */
    public static AttributeMapper<RuntimeVisibleParameterAnnotationsAttribute> runtimeVisibleParameterAnnotations() {
        return RuntimeVisibleParameterAnnotationsMapper.INSTANCE;
    }

    /**
     * {@return Attribute mapper for the {@code RuntimeVisibleTypeAnnotations} attribute}
     * @since 23
     */
    public static AttributeMapper<RuntimeVisibleTypeAnnotationsAttribute> runtimeVisibleTypeAnnotations() {
        return RuntimeVisibleTypeAnnotationsMapper.INSTANCE;
    }

    /**
     * {@return Attribute mapper for the {@code Signature} attribute}
     * @since 23
     */
    public static AttributeMapper<SignatureAttribute> signature() {
        return SignatureMapper.INSTANCE;
    }

    /**
     * {@return Attribute mapper for the {@code SourceDebugExtension} attribute}
     * @since 23
     */
    public static AttributeMapper<SourceDebugExtensionAttribute> sourceDebugExtension() {
        return SourceDebugExtensionMapper.INSTANCE;
    }

    /**
     * {@return Attribute mapper for the {@code SourceFile} attribute}
     * @since 23
     */
    public static AttributeMapper<SourceFileAttribute> sourceFile() {
        return SourceFileMapper.INSTANCE;
    }

    /**
     * {@return Attribute mapper for the {@code SourceID} attribute}
     * @since 23
     */
    public static AttributeMapper<SourceIDAttribute> sourceId() {
        return SourceIDMapper.INSTANCE;
    }

    /**
     * {@return Attribute mapper for the {@code StackMapTable} attribute}
     * @since 23
     */
    public static AttributeMapper<StackMapTableAttribute> stackMapTable() {
        return StackMapTableMapper.INSTANCE;
    }

    /**
     * {@return Attribute mapper for the {@code Synthetic} attribute}
     * The mapper permits multiple instances in a given location.
     * @since 23
     */
    public static AttributeMapper<SyntheticAttribute> synthetic() {
        return SyntheticMapper.INSTANCE;
    }
}
