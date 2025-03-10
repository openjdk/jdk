/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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

/**
 * Attribute mappers for predefined (JVMS {@jvms 4.7}) and JDK-specific
 * nonstandard attributes.
 * <p>
 * Unless otherwise specified, each mapper returned by methods in this class:
 * <ul>
 * <li>is predefined in the JVMS instead of JDK-specific;
 * <li>does not permit {@linkplain AttributeMapper#allowMultiple() multiple
 * attribute instances} in the same structure;
 * <li>the attribute has a {@linkplain AttributeMapper#stability() data
 * dependency} on the {@linkplain AttributeStability#CP_REFS constant pool}.
 * </ul>
 *
 * @see AttributeMapper
 * @see java.lang.classfile.attribute
 * @since 24
 */
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
     * {@return the mapper for the {@code AnnotationDefault} attribute}
     */
    public static AttributeMapper<AnnotationDefaultAttribute> annotationDefault() {
        return AnnotationDefaultMapper.INSTANCE;
    }

    /**
     * {@return the mapper for the {@code BootstrapMethods} attribute}
     */
    public static AttributeMapper<BootstrapMethodsAttribute> bootstrapMethods() {
        return BootstrapMethodsMapper.INSTANCE;
    }

    /**
     * {@return the mapper for the {@code CharacterRangeTable} attribute}
     * This is a JDK-specific attribute.
     * The mapper permits multiple instances in a {@code Code} attribute, but this
     * attribute should be only emitted once.
     * This has a data dependency on {@linkplain AttributeStability#LABELS labels}.
     */
    public static AttributeMapper<CharacterRangeTableAttribute> characterRangeTable() {
        return CharacterRangeTableMapper.INSTANCE;
    }

    /**
     * {@return the mapper for the {@code Code} attribute}
     */
    public static AttributeMapper<CodeAttribute> code() {
        return CodeMapper.INSTANCE;
    }

    /**
     * {@return the mapper for the {@code CompilationID} attribute}
     * This is a JDK-specific attribute.
     */
    public static AttributeMapper<CompilationIDAttribute> compilationId() {
        return CompilationIDMapper.INSTANCE;
    }

    /**
     * {@return the mapper for the {@code ConstantValue} attribute}
     */
    public static AttributeMapper<ConstantValueAttribute> constantValue() {
        return ConstantValueMapper.INSTANCE;
    }

    /**
     * {@return the mapper for the {@code Deprecated} attribute}
     * The mapper permits multiple instances in a given location.
     * This has {@linkplain AttributeStability#STATELESS no data dependency}.
     */
    public static AttributeMapper<DeprecatedAttribute> deprecated() {
        return DeprecatedMapper.INSTANCE;
    }

    /**
     * {@return the mapper for the {@code EnclosingMethod} attribute}
     */
    public static AttributeMapper<EnclosingMethodAttribute> enclosingMethod() {
        return EnclosingMethodMapper.INSTANCE;
    }

    /**
     * {@return the mapper for the {@code Exceptions} attribute}
     */
    public static AttributeMapper<ExceptionsAttribute> exceptions() {
        return ExceptionsMapper.INSTANCE;
    }

    /**
     * {@return the mapper for the {@code InnerClasses} attribute}
     */
    public static AttributeMapper<InnerClassesAttribute> innerClasses() {
        return InnerClassesMapper.INSTANCE;
    }

    /**
     * {@return the mapper for the {@code LineNumberTable} attribute}
     * The mapper permits multiple instances in a {@code Code} attribute.
     * This has a data dependency on {@linkplain AttributeStability#LABELS labels}.
     */
    public static AttributeMapper<LineNumberTableAttribute> lineNumberTable() {
        return LineNumberTableMapper.INSTANCE;
    }

    /**
     * {@return the mapper for the {@code LocalVariableTable} attribute}
     * The mapper permits multiple instances in a {@code Code} attribute.
     * This has a data dependency on {@linkplain AttributeStability#LABELS labels}.
     */
    public static AttributeMapper<LocalVariableTableAttribute> localVariableTable() {
        return LocalVariableTableMapper.INSTANCE;
    }

    /**
     * {@return the mapper for the {@code LocalVariableTypeTable} attribute}
     * The mapper permits multiple instances in a given location.
     * This has a data dependency on {@linkplain AttributeStability#LABELS labels}.
     */
    public static AttributeMapper<LocalVariableTypeTableAttribute> localVariableTypeTable() {
        return LocalVariableTypeTableMapper.INSTANCE;
    }

    /**
     * {@return the mapper for the {@code MethodParameters} attribute}
     */
    public static AttributeMapper<MethodParametersAttribute> methodParameters() {
        return MethodParametersMapper.INSTANCE;
    }

    /**
     * {@return the mapper for the {@code Module} attribute}
     */
    public static AttributeMapper<ModuleAttribute> module() {
        return ModuleMapper.INSTANCE;
    }

    /**
     * {@return the mapper for the {@code ModuleHashes} attribute}
     * This is a JDK-specific attribute.
     */
    public static AttributeMapper<ModuleHashesAttribute> moduleHashes() {
        return ModuleHashesMapper.INSTANCE;
    }

    /**
     * {@return the mapper for the {@code ModuleMainClass} attribute}
     */
    public static AttributeMapper<ModuleMainClassAttribute> moduleMainClass() {
        return ModuleMainClassMapper.INSTANCE;
    }

    /**
     * {@return the mapper for the {@code ModulePackages} attribute}
     */
    public static AttributeMapper<ModulePackagesAttribute> modulePackages() {
        return ModulePackagesMapper.INSTANCE;
    }

    /**
     * {@return the mapper for the {@code ModuleResolution} attribute}
     * This is a JDK-specific attribute.
     * This has {@linkplain AttributeStability#STATELESS no data dependency}.
     */
    public static AttributeMapper<ModuleResolutionAttribute> moduleResolution() {
        return ModuleResolutionMapper.INSTANCE;
    }

    /**
     * {@return the mapper for the {@code ModuleTarget} attribute}
     * This is a JDK-specific attribute.
     */
    public static AttributeMapper<ModuleTargetAttribute> moduleTarget() {
        return ModuleTargetMapper.INSTANCE;
    }

    /**
     * {@return the mapper for the {@code NestHost} attribute}
     */
    public static AttributeMapper<NestHostAttribute> nestHost() {
        return NestHostMapper.INSTANCE;
    }

    /**
     * {@return the mapper for the {@code NestMembers} attribute}
     */
    public static AttributeMapper<NestMembersAttribute> nestMembers() {
        return NestMembersMapper.INSTANCE;
    }

    /**
     * {@return the mapper for the {@code PermittedSubclasses} attribute}
     */
    public static AttributeMapper<PermittedSubclassesAttribute> permittedSubclasses() {
        return PermittedSubclassesMapper.INSTANCE;
    }

    /**
     * {@return the mapper for the {@code Record} attribute}
     */
    public static AttributeMapper<RecordAttribute> record() {
        return RecordMapper.INSTANCE;
    }

    /**
     * {@return the mapper for the {@code RuntimeInvisibleAnnotations} attribute}
     */
    public static AttributeMapper<RuntimeInvisibleAnnotationsAttribute> runtimeInvisibleAnnotations() {
        return RuntimeInvisibleAnnotationsMapper.INSTANCE;
    }

    /**
     * {@return the mapper for the {@code RuntimeInvisibleParameterAnnotations} attribute}
     */
    public static AttributeMapper<RuntimeInvisibleParameterAnnotationsAttribute> runtimeInvisibleParameterAnnotations() {
        return RuntimeInvisibleParameterAnnotationsMapper.INSTANCE;
    }

    /**
     * {@return the mapper for the {@code RuntimeInvisibleTypeAnnotations} attribute}
     * This has a data dependency on {@linkplain AttributeStability#UNSTABLE
     * arbitrary indices} in the {@code class} file format.
     */
    public static AttributeMapper<RuntimeInvisibleTypeAnnotationsAttribute> runtimeInvisibleTypeAnnotations() {
        return RuntimeInvisibleTypeAnnotationsMapper.INSTANCE;
    }

    /**
     * {@return the mapper for the {@code RuntimeVisibleAnnotations} attribute}
     */
    public static AttributeMapper<RuntimeVisibleAnnotationsAttribute> runtimeVisibleAnnotations() {
        return RuntimeVisibleAnnotationsMapper.INSTANCE;
    }

    /**
     * {@return the mapper for the {@code RuntimeVisibleParameterAnnotations} attribute}
     */
    public static AttributeMapper<RuntimeVisibleParameterAnnotationsAttribute> runtimeVisibleParameterAnnotations() {
        return RuntimeVisibleParameterAnnotationsMapper.INSTANCE;
    }

    /**
     * {@return the mapper for the {@code RuntimeVisibleTypeAnnotations} attribute}
     * This has a data dependency on {@linkplain AttributeStability#UNSTABLE
     * arbitrary indices} in the {@code class} file format.
     */
    public static AttributeMapper<RuntimeVisibleTypeAnnotationsAttribute> runtimeVisibleTypeAnnotations() {
        return RuntimeVisibleTypeAnnotationsMapper.INSTANCE;
    }

    /**
     * {@return the mapper for the {@code Signature} attribute}
     */
    public static AttributeMapper<SignatureAttribute> signature() {
        return SignatureMapper.INSTANCE;
    }

    /**
     * {@return the mapper for the {@code SourceDebugExtension} attribute}
     * This has {@linkplain AttributeStability#STATELESS no data dependency}.
     */
    public static AttributeMapper<SourceDebugExtensionAttribute> sourceDebugExtension() {
        return SourceDebugExtensionMapper.INSTANCE;
    }

    /**
     * {@return the mapper for the {@code SourceFile} attribute}
     */
    public static AttributeMapper<SourceFileAttribute> sourceFile() {
        return SourceFileMapper.INSTANCE;
    }

    /**
     * {@return the mapper for the {@code SourceID} attribute}
     * This is a JDK-specific attribute.
     */
    public static AttributeMapper<SourceIDAttribute> sourceId() {
        return SourceIDMapper.INSTANCE;
    }

    /**
     * {@return the mapper for the {@code StackMapTable} attribute}
     * This has a data dependency on {@linkplain AttributeStability#LABELS labels}.
     */
    public static AttributeMapper<StackMapTableAttribute> stackMapTable() {
        return StackMapTableMapper.INSTANCE;
    }

    /**
     * {@return the mapper for the {@code Synthetic} attribute}
     * The mapper permits multiple instances in a given location.
     * This has {@linkplain AttributeStability#STATELESS no data dependency}.
     */
    public static AttributeMapper<SyntheticAttribute> synthetic() {
        return SyntheticMapper.INSTANCE;
    }
}
