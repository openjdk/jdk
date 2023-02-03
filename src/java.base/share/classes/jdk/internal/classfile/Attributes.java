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
package jdk.internal.classfile;

import java.util.List;
import java.util.Map;

import jdk.internal.classfile.attribute.AnnotationDefaultAttribute;
import jdk.internal.classfile.attribute.BootstrapMethodsAttribute;
import jdk.internal.classfile.attribute.CharacterRangeInfo;
import jdk.internal.classfile.attribute.CharacterRangeTableAttribute;
import jdk.internal.classfile.attribute.CodeAttribute;
import jdk.internal.classfile.attribute.CompilationIDAttribute;
import jdk.internal.classfile.attribute.ConstantValueAttribute;
import jdk.internal.classfile.attribute.DeprecatedAttribute;
import jdk.internal.classfile.attribute.EnclosingMethodAttribute;
import jdk.internal.classfile.attribute.ExceptionsAttribute;
import jdk.internal.classfile.attribute.InnerClassInfo;
import jdk.internal.classfile.attribute.InnerClassesAttribute;
import jdk.internal.classfile.attribute.LineNumberInfo;
import jdk.internal.classfile.attribute.LineNumberTableAttribute;
import jdk.internal.classfile.attribute.LocalVariableInfo;
import jdk.internal.classfile.attribute.LocalVariableTableAttribute;
import jdk.internal.classfile.attribute.LocalVariableTypeInfo;
import jdk.internal.classfile.attribute.LocalVariableTypeTableAttribute;
import jdk.internal.classfile.attribute.MethodParameterInfo;
import jdk.internal.classfile.attribute.MethodParametersAttribute;
import jdk.internal.classfile.attribute.ModuleAttribute;
import jdk.internal.classfile.attribute.ModuleExportInfo;
import jdk.internal.classfile.attribute.ModuleHashInfo;
import jdk.internal.classfile.attribute.ModuleHashesAttribute;
import jdk.internal.classfile.attribute.ModuleMainClassAttribute;
import jdk.internal.classfile.attribute.ModuleOpenInfo;
import jdk.internal.classfile.attribute.ModulePackagesAttribute;
import jdk.internal.classfile.attribute.ModuleProvideInfo;
import jdk.internal.classfile.attribute.ModuleRequireInfo;
import jdk.internal.classfile.attribute.ModuleResolutionAttribute;
import jdk.internal.classfile.attribute.ModuleTargetAttribute;
import jdk.internal.classfile.attribute.NestHostAttribute;
import jdk.internal.classfile.attribute.NestMembersAttribute;
import jdk.internal.classfile.attribute.PermittedSubclassesAttribute;
import jdk.internal.classfile.attribute.RecordAttribute;
import jdk.internal.classfile.attribute.RecordComponentInfo;
import jdk.internal.classfile.attribute.RuntimeInvisibleAnnotationsAttribute;
import jdk.internal.classfile.attribute.RuntimeInvisibleParameterAnnotationsAttribute;
import jdk.internal.classfile.attribute.RuntimeInvisibleTypeAnnotationsAttribute;
import jdk.internal.classfile.attribute.RuntimeVisibleAnnotationsAttribute;
import jdk.internal.classfile.attribute.RuntimeVisibleParameterAnnotationsAttribute;
import jdk.internal.classfile.attribute.RuntimeVisibleTypeAnnotationsAttribute;
import jdk.internal.classfile.attribute.SignatureAttribute;
import jdk.internal.classfile.attribute.SourceDebugExtensionAttribute;
import jdk.internal.classfile.attribute.SourceFileAttribute;
import jdk.internal.classfile.attribute.SourceIDAttribute;
import jdk.internal.classfile.attribute.StackMapTableAttribute;
import jdk.internal.classfile.attribute.SyntheticAttribute;
import jdk.internal.classfile.constantpool.Utf8Entry;
import jdk.internal.classfile.impl.AbstractAttributeMapper;
import jdk.internal.classfile.impl.BoundAttribute;
import jdk.internal.classfile.impl.CodeImpl;
import jdk.internal.classfile.impl.ConcreteEntry;

import static java.util.Map.entry;
import static jdk.internal.classfile.AttributedElement.Kind.CLASS_ONLY;
import static jdk.internal.classfile.AttributedElement.Kind.CODE_ONLY;
import static jdk.internal.classfile.AttributedElement.Kind.EVERYWHERE;
import static jdk.internal.classfile.AttributedElement.Kind.FIELD_ONLY;
import static jdk.internal.classfile.AttributedElement.Kind.METHOD_ONLY;
import jdk.internal.classfile.impl.StackMapDecoder;

/**
 * Attribute mappers for standard classfile attributes.
 *
 * @see AttributeMapper
 */
public class Attributes {
    public static final String NAME_ANNOTATION_DEFAULT = "AnnotationDefault";
    public static final String NAME_BOOTSTRAP_METHODS = "BootstrapMethods";
    public static final String NAME_CHARACTER_RANGE_TABLE = "CharacterRangeTable";
    public static final String NAME_CODE = "Code";
    public static final String NAME_COMPILATION_ID = "CompilationID";
    public static final String NAME_CONSTANT_VALUE = "ConstantValue";
    public static final String NAME_DEPRECATED = "Deprecated";
    public static final String NAME_ENCLOSING_METHOD = "EnclosingMethod";
    public static final String NAME_EXCEPTIONS = "Exceptions";
    public static final String NAME_INNER_CLASSES = "InnerClasses";
    public static final String NAME_LINE_NUMBER_TABLE = "LineNumberTable";
    public static final String NAME_LOCAL_VARIABLE_TABLE = "LocalVariableTable";
    public static final String NAME_LOCAL_VARIABLE_TYPE_TABLE = "LocalVariableTypeTable";
    public static final String NAME_METHOD_PARAMETERS = "MethodParameters";
    public static final String NAME_MODULE = "Module";
    public static final String NAME_MODULE_HASHES = "ModuleHashes";
    public static final String NAME_MODULE_MAIN_CLASS = "ModuleMainClass";
    public static final String NAME_MODULE_PACKAGES = "ModulePackages";
    public static final String NAME_MODULE_RESOLUTION = "ModuleResolution";
    public static final String NAME_MODULE_TARGET = "ModuleTarget";
    public static final String NAME_NEST_HOST = "NestHost";
    public static final String NAME_NEST_MEMBERS = "NestMembers";
    public static final String NAME_PERMITTED_SUBCLASSES = "PermittedSubclasses";
    public static final String NAME_RECORD = "Record";
    public static final String NAME_RUNTIME_INVISIBLE_ANNOTATIONS = "RuntimeInvisibleAnnotations";
    public static final String NAME_RUNTIME_INVISIBLE_PARAMETER_ANNOTATIONS = "RuntimeInvisibleParameterAnnotations";
    public static final String NAME_RUNTIME_INVISIBLE_TYPE_ANNOTATIONS = "RuntimeInvisibleTypeAnnotations";
    public static final String NAME_RUNTIME_VISIBLE_ANNOTATIONS = "RuntimeVisibleAnnotations";
    public static final String NAME_RUNTIME_VISIBLE_PARAMETER_ANNOTATIONS = "RuntimeVisibleParameterAnnotations";
    public static final String NAME_RUNTIME_VISIBLE_TYPE_ANNOTATIONS = "RuntimeVisibleTypeAnnotations";
    public static final String NAME_SIGNATURE = "Signature";
    public static final String NAME_SOURCE_DEBUG_EXTENSION = "SourceDebugExtension";
    public static final String NAME_SOURCE_FILE = "SourceFile";
    public static final String NAME_SOURCE_ID = "SourceID";
    public static final String NAME_STACK_MAP_TABLE = "StackMapTable";
    public static final String NAME_SYNTHETIC = "Synthetic";

    private static final int HASH_ANNOTATION_DEFAULT = ConcreteEntry.hashString(NAME_ANNOTATION_DEFAULT.hashCode());
    private static final int HASH_BOOTSTRAP_METHODS = ConcreteEntry.hashString(NAME_BOOTSTRAP_METHODS.hashCode());
    private static final int HASH_CHARACTER_RANGE_TABLE = ConcreteEntry.hashString(NAME_CHARACTER_RANGE_TABLE.hashCode());
    private static final int HASH_CODE = ConcreteEntry.hashString(NAME_CODE.hashCode());
    private static final int HASH_COMPILATION_ID = ConcreteEntry.hashString(NAME_COMPILATION_ID.hashCode());
    private static final int HASH_CONSTANT_VALUE = ConcreteEntry.hashString(NAME_CONSTANT_VALUE.hashCode());
    private static final int HASH_DEPRECATED = ConcreteEntry.hashString(NAME_DEPRECATED.hashCode());
    private static final int HASH_ENCLOSING_METHOD = ConcreteEntry.hashString(NAME_ENCLOSING_METHOD.hashCode());
    private static final int HASH_EXCEPTIONS = ConcreteEntry.hashString(NAME_EXCEPTIONS.hashCode());
    private static final int HASH_INNER_CLASSES = ConcreteEntry.hashString(NAME_INNER_CLASSES.hashCode());
    private static final int HASH_LINE_NUMBER_TABLE = ConcreteEntry.hashString(NAME_LINE_NUMBER_TABLE.hashCode());
    private static final int HASH_LOCAL_VARIABLE_TABLE = ConcreteEntry.hashString(NAME_LOCAL_VARIABLE_TABLE.hashCode());
    private static final int HASH_LOCAL_VARIABLE_TYPE_TABLE = ConcreteEntry.hashString(NAME_LOCAL_VARIABLE_TYPE_TABLE.hashCode());
    private static final int HASH_METHOD_PARAMETERS = ConcreteEntry.hashString(NAME_METHOD_PARAMETERS.hashCode());
    private static final int HASH_MODULE = ConcreteEntry.hashString(NAME_MODULE.hashCode());
    private static final int HASH_MODULE_HASHES = ConcreteEntry.hashString(NAME_MODULE_HASHES.hashCode());
    private static final int HASH_MODULE_MAIN_CLASS = ConcreteEntry.hashString(NAME_MODULE_MAIN_CLASS.hashCode());
    private static final int HASH_MODULE_PACKAGES = ConcreteEntry.hashString(NAME_MODULE_PACKAGES.hashCode());
    private static final int HASH_MODULE_RESOLUTION = ConcreteEntry.hashString(NAME_MODULE_RESOLUTION.hashCode());
    private static final int HASH_MODULE_TARGET = ConcreteEntry.hashString(NAME_MODULE_TARGET.hashCode());
    private static final int HASH_NEST_HOST = ConcreteEntry.hashString(NAME_NEST_HOST.hashCode());
    private static final int HASH_NEST_MEMBERS = ConcreteEntry.hashString(NAME_NEST_MEMBERS.hashCode());
    private static final int HASH_PERMITTED_SUBCLASSES = ConcreteEntry.hashString(NAME_PERMITTED_SUBCLASSES.hashCode());
    private static final int HASH_RECORD = ConcreteEntry.hashString(NAME_RECORD.hashCode());
    private static final int HASH_RUNTIME_INVISIBLE_ANNOTATIONS = ConcreteEntry.hashString(NAME_RUNTIME_INVISIBLE_ANNOTATIONS.hashCode());
    private static final int HASH_RUNTIME_INVISIBLE_PARAMETER_ANNOTATIONS = ConcreteEntry.hashString(NAME_RUNTIME_INVISIBLE_PARAMETER_ANNOTATIONS.hashCode());
    private static final int HASH_RUNTIME_INVISIBLE_TYPE_ANNOTATIONS = ConcreteEntry.hashString(NAME_RUNTIME_INVISIBLE_TYPE_ANNOTATIONS.hashCode());
    private static final int HASH_RUNTIME_VISIBLE_ANNOTATIONS = ConcreteEntry.hashString(NAME_RUNTIME_VISIBLE_ANNOTATIONS.hashCode());
    private static final int HASH_RUNTIME_VISIBLE_PARAMETER_ANNOTATIONS = ConcreteEntry.hashString(NAME_RUNTIME_VISIBLE_PARAMETER_ANNOTATIONS.hashCode());
    private static final int HASH_RUNTIME_VISIBLE_TYPE_ANNOTATIONS = ConcreteEntry.hashString(NAME_RUNTIME_VISIBLE_TYPE_ANNOTATIONS.hashCode());
    private static final int HASH_SIGNATURE = ConcreteEntry.hashString(NAME_SIGNATURE.hashCode());
    private static final int HASH_SOURCE_DEBUG_EXTENSION = ConcreteEntry.hashString(NAME_SOURCE_DEBUG_EXTENSION.hashCode());
    private static final int HASH_SOURCE_FILE = ConcreteEntry.hashString(NAME_SOURCE_FILE.hashCode());
    private static final int HASH_SOURCE_ID = ConcreteEntry.hashString(NAME_SOURCE_ID.hashCode());
    private static final int HASH_STACK_MAP_TABLE = ConcreteEntry.hashString(NAME_STACK_MAP_TABLE.hashCode());
    private static final int HASH_SYNTHETIC = ConcreteEntry.hashString(NAME_SYNTHETIC.hashCode());

    private Attributes() {
    }

    /** Attribute mapper for the {@code AnnotationDefault} attribute */
    public static final AttributeMapper<AnnotationDefaultAttribute>
            ANNOTATION_DEFAULT = new AbstractAttributeMapper<>(NAME_ANNOTATION_DEFAULT, METHOD_ONLY, Classfile.JAVA_5_VERSION) {
                @Override
                public AnnotationDefaultAttribute readAttribute(AttributedElement e, ClassReader cf, int p) {
                    return new BoundAttribute.BoundAnnotationDefaultAttr(e, cf, this, p);
                }

                @Override
                protected void writeBody(BufWriter buf, AnnotationDefaultAttribute attr) {
                    attr.defaultValue().writeTo(buf);
                }
            };

    /** Attribute mapper for the {@code BootstrapMethods} attribute */
    public static final AttributeMapper<BootstrapMethodsAttribute>
            BOOTSTRAP_METHODS = new AbstractAttributeMapper<>(NAME_BOOTSTRAP_METHODS, CLASS_ONLY, Classfile.JAVA_17_VERSION) {
                @Override
                public BootstrapMethodsAttribute readAttribute(AttributedElement e, ClassReader cf, int p) {
                    return new BoundAttribute.BoundBootstrapMethodsAttribute(e, cf, this, p);
                }

                @Override
                protected void writeBody(BufWriter buf, BootstrapMethodsAttribute attr) {
                    buf.writeList(attr.bootstrapMethods());
                }
            };

    /** Attribute mapper for the {@code CharacterRangeTable} attribute */
    public static final AttributeMapper<CharacterRangeTableAttribute>
            CHARACTER_RANGE_TABLE = new AbstractAttributeMapper<>(NAME_CHARACTER_RANGE_TABLE, CODE_ONLY, true, Classfile.JAVA_4_VERSION) {
                @Override
                public CharacterRangeTableAttribute readAttribute(AttributedElement e, ClassReader cf, int p) {
                    return new BoundAttribute.BoundCharacterRangeTableAttribute(e, cf, this, p);
                }

                @Override
                protected void writeBody(BufWriter buf, CharacterRangeTableAttribute attr) {
                    List<CharacterRangeInfo> ranges = attr.characterRangeTable();
                    buf.writeU2(ranges.size());
                    for (CharacterRangeInfo info : ranges) {
                        buf.writeU2(info.startPc());
                        buf.writeU2(info.endPc());
                        buf.writeInt(info.characterRangeStart());
                        buf.writeInt(info.characterRangeEnd());
                        buf.writeU2(info.flags());
                    }
                }
            };

    /** Attribute mapper for the {@code Code} attribute */
    public static final AttributeMapper<CodeAttribute>
            CODE = new AbstractAttributeMapper<>(NAME_CODE, METHOD_ONLY) {
                @Override
                public CodeAttribute readAttribute(AttributedElement e, ClassReader cf, int p) {
                    return new CodeImpl(e, cf, this, p);
                }

                @Override
                protected void writeBody(BufWriter buf, CodeAttribute attr) {
                    throw new UnsupportedOperationException("Code attribute does not support direct write");
                }
            };


    /** Attribute mapper for the {@code CompilationID} attribute */
    public static final AttributeMapper<CompilationIDAttribute>
            COMPILATION_ID = new AbstractAttributeMapper<>(NAME_COMPILATION_ID, CLASS_ONLY, true) {
                @Override
                public CompilationIDAttribute readAttribute(AttributedElement e, ClassReader cf, int p) {
                    return new BoundAttribute.BoundCompilationIDAttribute(e, cf, this, p);
                }

                @Override
                protected void writeBody(BufWriter buf, CompilationIDAttribute attr) {
                    buf.writeIndex(attr.compilationId());
                }
            };

    /** Attribute mapper for the {@code ConstantValue} attribute */
    public static final AttributeMapper<ConstantValueAttribute>
            CONSTANT_VALUE = new AbstractAttributeMapper<>(NAME_CONSTANT_VALUE, FIELD_ONLY) {
                @Override
                public ConstantValueAttribute readAttribute(AttributedElement e, ClassReader cf, int p) {
                    return new BoundAttribute.BoundConstantValueAttribute(e, cf, this, p);
                }

                @Override
                protected void writeBody(BufWriter buf, ConstantValueAttribute attr) {
                    buf.writeIndex(attr.constant());
                }
            };

    /** Attribute mapper for the {@code Deprecated} attribute */
    public static final AttributeMapper<DeprecatedAttribute>
            DEPRECATED = new AbstractAttributeMapper<>(NAME_DEPRECATED, EVERYWHERE, true) {
                @Override
                public DeprecatedAttribute readAttribute(AttributedElement e, ClassReader cf, int p) {
                    return new BoundAttribute.BoundDeprecatedAttribute(e, cf, this, p);
                }

                @Override
                protected void writeBody(BufWriter buf, DeprecatedAttribute attr) {
                    // empty
                }
            };

    /** Attribute mapper for the {@code EnclosingMethod} attribute */
    public static final AttributeMapper<EnclosingMethodAttribute>
            ENCLOSING_METHOD = new AbstractAttributeMapper<>(NAME_ENCLOSING_METHOD, CLASS_ONLY, Classfile.JAVA_5_VERSION) {
                @Override
                public EnclosingMethodAttribute readAttribute(AttributedElement e, ClassReader cf, int p) {
                    return new BoundAttribute.BoundEnclosingMethodAttribute(e, cf, this, p);
                }

                @Override
                protected void writeBody(BufWriter buf, EnclosingMethodAttribute attr) {
                    buf.writeIndex(attr.enclosingClass());
                    buf.writeIndexOrZero(attr.enclosingMethod().orElse(null));
                }
            };

    /** Attribute mapper for the {@code Exceptions} attribute */
    public static final AttributeMapper<ExceptionsAttribute>
            EXCEPTIONS = new AbstractAttributeMapper<>(NAME_EXCEPTIONS, METHOD_ONLY) {
                @Override
                public ExceptionsAttribute readAttribute(AttributedElement e, ClassReader cf, int p) {
                    return new BoundAttribute.BoundExceptionsAttribute(e, cf, this, p);
                }

                @Override
                protected void writeBody(BufWriter buf, ExceptionsAttribute attr) {
                    buf.writeListIndices(attr.exceptions());
                }
            };

    /** Attribute mapper for the {@code InnerClasses} attribute */
    public static final AttributeMapper<InnerClassesAttribute>
            INNER_CLASSES = new AbstractAttributeMapper<>(NAME_INNER_CLASSES, CLASS_ONLY) {
                @Override
                public InnerClassesAttribute readAttribute(AttributedElement e, ClassReader cf, int p) {
                    return new BoundAttribute.BoundInnerClassesAttribute(e, cf, this, p);
                }

                @Override
                protected void writeBody(BufWriter buf, InnerClassesAttribute attr) {
                    List<InnerClassInfo> classes = attr.classes();
                    buf.writeU2(classes.size());
                    for (InnerClassInfo ic : classes) {
                        buf.writeIndex(ic.innerClass());
                        buf.writeIndexOrZero(ic.outerClass().orElse(null));
                        buf.writeIndexOrZero(ic.innerName().orElse(null));
                        buf.writeU2(ic.flagsMask());
                    }
                }
            };

    /** Attribute mapper for the {@code LineNumberTable} attribute */
    public static final AttributeMapper<LineNumberTableAttribute>
            LINE_NUMBER_TABLE = new AbstractAttributeMapper<>(NAME_LINE_NUMBER_TABLE, CODE_ONLY, true) {
                @Override
                public LineNumberTableAttribute readAttribute(AttributedElement e, ClassReader cf, int p) {
                    return new BoundAttribute.BoundLineNumberTableAttribute(e, cf, this, p);
                }

                @Override
                protected void writeBody(BufWriter buf, LineNumberTableAttribute attr) {
                    List<LineNumberInfo> lines = attr.lineNumbers();
                    buf.writeU2(lines.size());
                    for (LineNumberInfo line : lines) {
                        buf.writeU2(line.startPc());
                        buf.writeU2(line.lineNumber());
                    }
                }
            };

    /** Attribute mapper for the {@code LocalVariableTable} attribute */
    public static final AttributeMapper<LocalVariableTableAttribute>
            LOCAL_VARIABLE_TABLE = new AbstractAttributeMapper<>(NAME_LOCAL_VARIABLE_TABLE, CODE_ONLY, true) {
                @Override
                public LocalVariableTableAttribute readAttribute(AttributedElement e, ClassReader cf, int p) {
                    return new BoundAttribute.BoundLocalVariableTableAttribute(e, cf, this, p);
                }

                @Override
                protected void writeBody(BufWriter buf, LocalVariableTableAttribute attr) {
                    List<LocalVariableInfo> infos = attr.localVariables();
                    buf.writeU2(infos.size());
                    for (LocalVariableInfo info : infos) {
                        buf.writeU2(info.startPc());
                        buf.writeU2(info.length());
                        buf.writeIndex(info.name());
                        buf.writeIndex(info.type());
                        buf.writeU2(info.slot());
                    }
                }
            };

    /** Attribute mapper for the {@code LocalVariableTypeTable} attribute */
    public static final AttributeMapper<LocalVariableTypeTableAttribute>
            LOCAL_VARIABLE_TYPE_TABLE = new AbstractAttributeMapper<>(NAME_LOCAL_VARIABLE_TYPE_TABLE, CODE_ONLY, true, Classfile.JAVA_5_VERSION) {
                @Override
                public LocalVariableTypeTableAttribute readAttribute(AttributedElement e, ClassReader cf, int p) {
                    return new BoundAttribute.BoundLocalVariableTypeTableAttribute(e, cf, this, p);
                }

                @Override
                protected void writeBody(BufWriter buf, LocalVariableTypeTableAttribute attr) {
                    List<LocalVariableTypeInfo> infos = attr.localVariableTypes();
                    buf.writeU2(infos.size());
                    for (LocalVariableTypeInfo info : infos) {
                        buf.writeU2(info.startPc());
                        buf.writeU2(info.length());
                        buf.writeIndex(info.name());
                        buf.writeIndex(info.signature());
                        buf.writeU2(info.slot());
                    }
                }
            };

    /** Attribute mapper for the {@code MethodParameters} attribute */
    public static final AttributeMapper<MethodParametersAttribute>
            METHOD_PARAMETERS = new AbstractAttributeMapper<>(NAME_METHOD_PARAMETERS, METHOD_ONLY, Classfile.JAVA_8_VERSION) {
                @Override
                public MethodParametersAttribute readAttribute(AttributedElement e, ClassReader cf, int p) {
                    return new BoundAttribute.BoundMethodParametersAttribute(e, cf, this, p);
                }

                @Override
                protected void writeBody(BufWriter buf, MethodParametersAttribute attr) {
                    List<MethodParameterInfo> parameters = attr.parameters();
                    buf.writeU1(parameters.size());
                    for (MethodParameterInfo info : parameters) {
                        buf.writeIndexOrZero(info.name().orElse(null));
                        buf.writeU2(info.flagsMask());
                    }
                }
            };

    /** Attribute mapper for the {@code Module} attribute */
    public static final AttributeMapper<ModuleAttribute>
            MODULE = new AbstractAttributeMapper<>(NAME_MODULE, CLASS_ONLY, Classfile.JAVA_9_VERSION) {
        @Override
        public ModuleAttribute readAttribute(AttributedElement e, ClassReader cf, int p) {
            return new BoundAttribute.BoundModuleAttribute(e, cf, this, p);
        }

        @Override
        protected void writeBody(BufWriter buf, ModuleAttribute attr) {
            buf.writeIndex(attr.moduleName());
            buf.writeU2(attr.moduleFlagsMask());
            buf.writeIndexOrZero(attr.moduleVersion().orElse(null));
            buf.writeU2(attr.requires().size());
            for (ModuleRequireInfo require : attr.requires()) {
                buf.writeIndex(require.requires());
                buf.writeU2(require.requiresFlagsMask());
                buf.writeIndexOrZero(require.requiresVersion().orElse(null));
            }
            buf.writeU2(attr.exports().size());
            for (ModuleExportInfo export : attr.exports()) {
                buf.writeIndex(export.exportedPackage());
                buf.writeU2(export.exportsFlagsMask());
                buf.writeListIndices(export.exportsTo());
            }
            buf.writeU2(attr.opens().size());
            for (ModuleOpenInfo open : attr.opens()) {
                buf.writeIndex(open.openedPackage());
                buf.writeU2(open.opensFlagsMask());
                buf.writeListIndices(open.opensTo());
            }
            buf.writeListIndices(attr.uses());
            buf.writeU2(attr.provides().size());
            for (ModuleProvideInfo provide : attr.provides()) {
                buf.writeIndex(provide.provides());
                buf.writeListIndices(provide.providesWith());
            }
        }
    };

    /** Attribute mapper for the {@code ModuleHashes} attribute */
    public static final AttributeMapper<ModuleHashesAttribute>
            MODULE_HASHES = new AbstractAttributeMapper<>(NAME_MODULE_HASHES, CLASS_ONLY, Classfile.JAVA_9_VERSION) {
                @Override
                public ModuleHashesAttribute readAttribute(AttributedElement e, ClassReader cf, int p) {
                    return new BoundAttribute.BoundModuleHashesAttribute(e, cf, this, p);
                }

                @Override
                protected void writeBody(BufWriter buf, ModuleHashesAttribute attr) {
                    buf.writeIndex(attr.algorithm());
                    List<ModuleHashInfo> hashes = attr.hashes();
                    buf.writeU2(hashes.size());
                    for (ModuleHashInfo hash : hashes) {
                        buf.writeIndex(hash.moduleName());
                        buf.writeU2(hash.hash().length);
                        buf.writeBytes(hash.hash());
                    }
                }
            };

    /** Attribute mapper for the {@code ModuleMainClass} attribute */
    public static final AttributeMapper<ModuleMainClassAttribute>
            MODULE_MAIN_CLASS = new AbstractAttributeMapper<>(NAME_MODULE_MAIN_CLASS, CLASS_ONLY, Classfile.JAVA_9_VERSION) {
                @Override
                public ModuleMainClassAttribute readAttribute(AttributedElement e, ClassReader cf, int p) {
                    return new BoundAttribute.BoundModuleMainClassAttribute(e, cf, this, p);
                }

                @Override
                protected void writeBody(BufWriter buf, ModuleMainClassAttribute attr) {
                    buf.writeIndex(attr.mainClass());
                }
            };

    /** Attribute mapper for the {@code ModulePackages} attribute */
    public static final AttributeMapper<ModulePackagesAttribute>
            MODULE_PACKAGES = new AbstractAttributeMapper<>(NAME_MODULE_PACKAGES, CLASS_ONLY, Classfile.JAVA_9_VERSION) {
                @Override
                public ModulePackagesAttribute readAttribute(AttributedElement e, ClassReader cf, int p) {
                    return new BoundAttribute.BoundModulePackagesAttribute(e, cf, this, p);
                }

                @Override
                protected void writeBody(BufWriter buf, ModulePackagesAttribute attr) {
                    buf.writeListIndices(attr.packages());
                }
            };

    /** Attribute mapper for the {@code ModuleResolution} attribute */
    public static final AttributeMapper<ModuleResolutionAttribute>
            MODULE_RESOLUTION = new AbstractAttributeMapper<>(NAME_MODULE_RESOLUTION, CLASS_ONLY, true, Classfile.JAVA_9_VERSION) {
                @Override
                public ModuleResolutionAttribute readAttribute(AttributedElement e, ClassReader cf, int p) {
                    return new BoundAttribute.BoundModuleResolutionAttribute(e, cf, this, p);
                }

                @Override
                protected void writeBody(BufWriter buf, ModuleResolutionAttribute attr) {
                    buf.writeU2(attr.resolutionFlags());
                }
            };

    /** Attribute mapper for the {@code ModuleTarget} attribute */
    public static final AttributeMapper<ModuleTargetAttribute>
            MODULE_TARGET = new AbstractAttributeMapper<>(NAME_MODULE_TARGET, CLASS_ONLY, true, Classfile.JAVA_9_VERSION) {
                @Override
                public ModuleTargetAttribute readAttribute(AttributedElement e, ClassReader cf, int p) {
                    return new BoundAttribute.BoundModuleTargetAttribute(e, cf, this, p);
                }

                @Override
                protected void writeBody(BufWriter buf, ModuleTargetAttribute attr) {
                    buf.writeIndex(attr.targetPlatform());
                }
            };

    /** Attribute mapper for the {@code NestHost} attribute */
    public static final AttributeMapper<NestHostAttribute>
            NEST_HOST = new AbstractAttributeMapper<>(NAME_NEST_HOST, CLASS_ONLY, Classfile.JAVA_11_VERSION) {
                @Override
                public NestHostAttribute readAttribute(AttributedElement e, ClassReader cf, int p) {
                    return new BoundAttribute.BoundNestHostAttribute(e, cf, this, p);
                }

                @Override
                protected void writeBody(BufWriter buf, NestHostAttribute attr) {
                    buf.writeIndex(attr.nestHost());
                }
            };

    /** Attribute mapper for the {@code NestMembers} attribute */
    public static final AttributeMapper<NestMembersAttribute>
            NEST_MEMBERS = new AbstractAttributeMapper<>(NAME_NEST_MEMBERS, CLASS_ONLY, Classfile.JAVA_11_VERSION) {
                @Override
                public NestMembersAttribute readAttribute(AttributedElement e, ClassReader cf, int p) {
                    return new BoundAttribute.BoundNestMembersAttribute(e, cf, this, p);
                }

                @Override
                protected void writeBody(BufWriter buf, NestMembersAttribute attr) {
                    buf.writeListIndices(attr.nestMembers());
                }
            };

    /** Attribute mapper for the {@code PermittedSubclasses} attribute */
    public static final AttributeMapper<PermittedSubclassesAttribute>
            PERMITTED_SUBCLASSES = new AbstractAttributeMapper<>(NAME_PERMITTED_SUBCLASSES, CLASS_ONLY, Classfile.JAVA_15_VERSION) {
                @Override
                public PermittedSubclassesAttribute readAttribute(AttributedElement e, ClassReader cf, int p) {
                    return new BoundAttribute.BoundPermittedSubclassesAttribute(e, cf, this, p);
                }

                @Override
                protected void writeBody(BufWriter buf, PermittedSubclassesAttribute attr) {
                    buf.writeListIndices(attr.permittedSubclasses());
                }
            };

    /** Attribute mapper for the {@code Record} attribute */
    public static final AttributeMapper<RecordAttribute>
            RECORD = new AbstractAttributeMapper<>(NAME_RECORD, CLASS_ONLY, Classfile.JAVA_16_VERSION) {
                @Override
                public RecordAttribute readAttribute(AttributedElement e, ClassReader cf, int p) {
                    return new BoundAttribute.BoundRecordAttribute(e, cf, this, p);
                }

                @Override
                protected void writeBody(BufWriter buf, RecordAttribute attr) {
                    List<RecordComponentInfo> components = attr.components();
                    buf.writeU2(components.size());
                    for (RecordComponentInfo info : components) {
                        buf.writeIndex(info.name());
                        buf.writeIndex(info.descriptor());
                        buf.writeList(info.attributes());
                    }
                }
            };

    /** Attribute mapper for the {@code RuntimeInvisibleAnnotations} attribute */
    public static final AttributeMapper<RuntimeInvisibleAnnotationsAttribute>
            RUNTIME_INVISIBLE_ANNOTATIONS = new AbstractAttributeMapper<>(NAME_RUNTIME_INVISIBLE_ANNOTATIONS, EVERYWHERE, Classfile.JAVA_5_VERSION) {
                @Override
                public RuntimeInvisibleAnnotationsAttribute readAttribute(AttributedElement enclosing, ClassReader cf, int pos) {
                    return new BoundAttribute.BoundRuntimeInvisibleAnnotationsAttribute(cf, pos);
                }

                @Override
                protected void writeBody(BufWriter buf, RuntimeInvisibleAnnotationsAttribute attr) {
                    buf.writeList(attr.annotations());
                }
    };

    /** Attribute mapper for the {@code RuntimeInvisibleParameterAnnotations} attribute */
    public static final AttributeMapper<RuntimeInvisibleParameterAnnotationsAttribute>
            RUNTIME_INVISIBLE_PARAMETER_ANNOTATIONS = new AbstractAttributeMapper<>(NAME_RUNTIME_INVISIBLE_PARAMETER_ANNOTATIONS, EVERYWHERE, Classfile.JAVA_5_VERSION) {
                @Override
                public RuntimeInvisibleParameterAnnotationsAttribute readAttribute(AttributedElement e, ClassReader cf, int p) {
                    return new BoundAttribute.BoundRuntimeInvisibleParameterAnnotationsAttribute(e, cf, this, p);
                }

                @Override
                protected void writeBody(BufWriter buf, RuntimeInvisibleParameterAnnotationsAttribute attr) {
                    List<List<Annotation>> lists = attr.parameterAnnotations();
                    buf.writeU1(lists.size());
                    for (List<Annotation> list : lists)
                        buf.writeList(list);
                }
            };

    /** Attribute mapper for the {@code RuntimeInvisibleTypeAnnotations} attribute */
    public static final AttributeMapper<RuntimeInvisibleTypeAnnotationsAttribute>
            RUNTIME_INVISIBLE_TYPE_ANNOTATIONS = new AbstractAttributeMapper<>(NAME_RUNTIME_INVISIBLE_TYPE_ANNOTATIONS, EVERYWHERE, Classfile.JAVA_8_VERSION) {
                @Override
                public RuntimeInvisibleTypeAnnotationsAttribute readAttribute(AttributedElement e, ClassReader cf, int p) {
                    return new BoundAttribute.BoundRuntimeInvisibleTypeAnnotationsAttribute(e, cf, this, p);
                }

                @Override
                protected void writeBody(BufWriter buf, RuntimeInvisibleTypeAnnotationsAttribute attr) {
                    buf.writeList(attr.annotations());
                }
            };

    /** Attribute mapper for the {@code RuntimeVisibleAnnotations} attribute */
    public static final AttributeMapper<RuntimeVisibleAnnotationsAttribute>
            RUNTIME_VISIBLE_ANNOTATIONS = new AbstractAttributeMapper<>(NAME_RUNTIME_VISIBLE_ANNOTATIONS, EVERYWHERE, Classfile.JAVA_5_VERSION) {
        @Override
        public RuntimeVisibleAnnotationsAttribute readAttribute(AttributedElement enclosing, ClassReader cf, int pos) {
            return new BoundAttribute.BoundRuntimeVisibleAnnotationsAttribute(cf, pos);
        }

        @Override
        protected void writeBody(BufWriter buf, RuntimeVisibleAnnotationsAttribute attr) {
            buf.writeList(attr.annotations());
        }
    };

    /** Attribute mapper for the {@code RuntimeVisibleParameterAnnotations} attribute */
    public static final AttributeMapper<RuntimeVisibleParameterAnnotationsAttribute>
            RUNTIME_VISIBLE_PARAMETER_ANNOTATIONS = new AbstractAttributeMapper<>(NAME_RUNTIME_VISIBLE_PARAMETER_ANNOTATIONS, EVERYWHERE, Classfile.JAVA_5_VERSION) {
                @Override
                public RuntimeVisibleParameterAnnotationsAttribute readAttribute(AttributedElement e, ClassReader cf, int p) {
                    return new BoundAttribute.BoundRuntimeVisibleParameterAnnotationsAttribute(e, cf, this, p);
                }

                @Override
                protected void writeBody(BufWriter buf, RuntimeVisibleParameterAnnotationsAttribute attr) {
                    List<List<Annotation>> lists = attr.parameterAnnotations();
                    buf.writeU1(lists.size());
                    for (List<Annotation> list : lists)
                        buf.writeList(list);
                }
            };

    /** Attribute mapper for the {@code RuntimeVisibleTypeAnnotations} attribute */
    public static final AttributeMapper<RuntimeVisibleTypeAnnotationsAttribute>
            RUNTIME_VISIBLE_TYPE_ANNOTATIONS = new AbstractAttributeMapper<>(NAME_RUNTIME_VISIBLE_TYPE_ANNOTATIONS, EVERYWHERE, Classfile.JAVA_8_VERSION) {
                @Override
                public RuntimeVisibleTypeAnnotationsAttribute readAttribute(AttributedElement e, ClassReader cf, int p) {
                    return new BoundAttribute.BoundRuntimeVisibleTypeAnnotationsAttribute(e, cf, this, p);
                }

                @Override
                protected void writeBody(BufWriter buf, RuntimeVisibleTypeAnnotationsAttribute attr) {
                    buf.writeList(attr.annotations());
                }
            };

    /** Attribute mapper for the {@code Signature} attribute */
    public static final AttributeMapper<SignatureAttribute>
            SIGNATURE = new AbstractAttributeMapper<>(NAME_SIGNATURE, EVERYWHERE, Classfile.JAVA_5_VERSION) {
                @Override
                public SignatureAttribute readAttribute(AttributedElement e, ClassReader cf, int p) {
                    return new BoundAttribute.BoundSignatureAttribute(e, cf, this, p);
                }

                @Override
                protected void writeBody(BufWriter buf, SignatureAttribute attr) {
                    buf.writeIndex(attr.signature());
                }
            };

    /** Attribute mapper for the {@code SourceDebug} attribute */
    public static final AttributeMapper<SourceDebugExtensionAttribute>
            SOURCE_DEBUG_EXTENSION = new AbstractAttributeMapper<>(NAME_SOURCE_DEBUG_EXTENSION, CLASS_ONLY, Classfile.JAVA_5_VERSION) {
                @Override
                public SourceDebugExtensionAttribute readAttribute(AttributedElement e, ClassReader cf, int p) {
                    return new BoundAttribute.BoundSourceDebugExtensionAttribute(e, cf, this, p);
                }

                @Override
                protected void writeBody(BufWriter buf, SourceDebugExtensionAttribute attr) {
                    buf.writeBytes(attr.contents());
                }
            };

    /** Attribute mapper for the {@code SourceFile} attribute */
    public static final AttributeMapper<SourceFileAttribute>
            SOURCE_FILE = new AbstractAttributeMapper<>(NAME_SOURCE_FILE, CLASS_ONLY) {
                @Override
                public SourceFileAttribute readAttribute(AttributedElement e, ClassReader cf, int p) {
                    return new BoundAttribute.BoundSourceFileAttribute(e, cf, this, p);
                }

                @Override
                protected void writeBody(BufWriter buf, SourceFileAttribute attr) {
                    buf.writeIndex(attr.sourceFile());
                }
            };

    /** Attribute mapper for the {@code SourceID} attribute */
    public static final AttributeMapper<SourceIDAttribute>
            SOURCE_ID = new AbstractAttributeMapper<>(NAME_SOURCE_ID, CLASS_ONLY) {
                @Override
                public SourceIDAttribute readAttribute(AttributedElement e, ClassReader cf, int p) {
                    return new BoundAttribute.BoundSourceIDAttribute(e, cf, this, p);
                }

                @Override
                protected void writeBody(BufWriter buf, SourceIDAttribute attr) {
                    buf.writeIndex(attr.sourceId());
                }
            };

    /** Attribute mapper for the {@code StackMapTable} attribute */
    public static final AttributeMapper<StackMapTableAttribute>
            STACK_MAP_TABLE = new AbstractAttributeMapper<>(NAME_STACK_MAP_TABLE, CODE_ONLY, Classfile.JAVA_6_VERSION) {
                @Override
                public StackMapTableAttribute readAttribute(AttributedElement e, ClassReader cf, int p) {
                    return new BoundAttribute.BoundStackMapTableAttribute((CodeImpl)e, cf, this, p);
                }

                @Override
                protected void writeBody(BufWriter b, StackMapTableAttribute attr) {
                    StackMapDecoder.writeFrames(b, attr.entries());
                }
            };


    /** Attribute mapper for the {@code Synthetic} attribute */
    public static final AttributeMapper<SyntheticAttribute>
            SYNTHETIC = new AbstractAttributeMapper<>(NAME_SYNTHETIC, EVERYWHERE) {
                @Override
                public SyntheticAttribute readAttribute(AttributedElement e, ClassReader cf, int p) {
                    return new BoundAttribute.BoundSyntheticAttribute(e, cf, this, p);
                }

                @Override
                protected void writeBody(BufWriter buf, SyntheticAttribute attr) {
                    // empty
                }
            };

    /**
     * {@return the attribute mapper for a standard attribute}
     *
     * @param name the name of the attribute to find
     */
    public static AttributeMapper<?> standardAttribute(Utf8Entry name) {
        int hash = name.hashCode();
        switch (name.length()) {
            case 4:
                if (hash == HASH_CODE && name.equalsString(NAME_CODE))
                    return CODE;
                break;
            case 6:
                if (hash == HASH_MODULE && name.equalsString(NAME_MODULE))
                    return MODULE;
                else if (hash == HASH_RECORD && name.equalsString(NAME_RECORD))
                    return RECORD;
                break;
            case 8:
                if (hash == HASH_NEST_HOST && name.equalsString(NAME_NEST_HOST))
                    return NEST_HOST;
                else if (hash == HASH_SOURCE_ID && name.equalsString(NAME_SOURCE_ID))
                    return SOURCE_ID;
                break;
            case 9:
                if (hash == HASH_SIGNATURE && name.equalsString(NAME_SIGNATURE))
                    return SIGNATURE;
                else if (hash == HASH_SYNTHETIC && name.equalsString(NAME_SYNTHETIC))
                    return SYNTHETIC;
                break;
            case 10:
                if (hash == HASH_DEPRECATED && name.equalsString(NAME_DEPRECATED))
                    return DEPRECATED;
                else if (hash == HASH_EXCEPTIONS && name.equalsString(NAME_EXCEPTIONS))
                    return EXCEPTIONS;
                else if (hash == HASH_SOURCE_FILE && name.equalsString(NAME_SOURCE_FILE))
                    return SOURCE_FILE;
                break;
            case 11:
                if (hash == HASH_NEST_MEMBERS && name.equalsString(NAME_NEST_MEMBERS))
                    return NEST_MEMBERS;
                break;
            case 12:
                if (hash == HASH_INNER_CLASSES && name.equalsString(NAME_INNER_CLASSES))
                    return INNER_CLASSES;
                else if (hash == HASH_MODULE_HASHES && name.equalsString(NAME_MODULE_HASHES))
                    return MODULE_HASHES;
                else if (hash == HASH_MODULE_TARGET && name.equalsString(NAME_MODULE_TARGET))
                    return MODULE_TARGET;
                break;
            case 13:
                if (hash == HASH_COMPILATION_ID && name.equalsString(NAME_COMPILATION_ID))
                    return COMPILATION_ID;
                else if (hash == HASH_CONSTANT_VALUE && name.equalsString(NAME_CONSTANT_VALUE))
                    return CONSTANT_VALUE;
                else if (hash == HASH_STACK_MAP_TABLE && name.equalsString(NAME_STACK_MAP_TABLE))
                    return STACK_MAP_TABLE;
                break;
            case 14:
                if (hash == HASH_MODULE_PACKAGES && name.equalsString(NAME_MODULE_PACKAGES))
                    return MODULE_PACKAGES;
                break;
            case 15:
                if (hash == HASH_ENCLOSING_METHOD && name.equalsString(NAME_ENCLOSING_METHOD))
                    return ENCLOSING_METHOD;
                else if (hash == HASH_LINE_NUMBER_TABLE && name.equalsString(NAME_LINE_NUMBER_TABLE))
                    return LINE_NUMBER_TABLE;
                else if (hash == HASH_MODULE_MAIN_CLASS && name.equalsString(NAME_MODULE_MAIN_CLASS))
                    return MODULE_MAIN_CLASS;
                break;
            case 16:
                if (hash == HASH_BOOTSTRAP_METHODS && name.equalsString(NAME_BOOTSTRAP_METHODS))
                    return BOOTSTRAP_METHODS;
                else if (hash == HASH_METHOD_PARAMETERS && name.equalsString(NAME_METHOD_PARAMETERS))
                    return METHOD_PARAMETERS;
                else if (hash == HASH_MODULE_RESOLUTION && name.equalsString(NAME_MODULE_RESOLUTION))
                    return MODULE_RESOLUTION;
                break;
            case 17:
                if (hash == HASH_ANNOTATION_DEFAULT && name.equalsString(NAME_ANNOTATION_DEFAULT))
                    return ANNOTATION_DEFAULT;
                break;
            case 18:
                if (hash == HASH_LOCAL_VARIABLE_TABLE && name.equalsString(NAME_LOCAL_VARIABLE_TABLE))
                    return LOCAL_VARIABLE_TABLE;
                break;
            case 19:
                if (hash == HASH_CHARACTER_RANGE_TABLE && name.equalsString(NAME_CHARACTER_RANGE_TABLE))
                    return CHARACTER_RANGE_TABLE;
                else if (hash == HASH_PERMITTED_SUBCLASSES && name.equalsString(NAME_PERMITTED_SUBCLASSES))
                    return PERMITTED_SUBCLASSES;
                break;
            case 20:
                if (hash == HASH_SOURCE_DEBUG_EXTENSION && name.equalsString(NAME_SOURCE_DEBUG_EXTENSION))
                    return SOURCE_DEBUG_EXTENSION;
                break;
            case 22:
                if (hash == HASH_LOCAL_VARIABLE_TYPE_TABLE && name.equalsString(NAME_LOCAL_VARIABLE_TYPE_TABLE))
                    return LOCAL_VARIABLE_TYPE_TABLE;
                break;
            case 25:
                if (hash == HASH_RUNTIME_VISIBLE_ANNOTATIONS && name.equalsString(NAME_RUNTIME_VISIBLE_ANNOTATIONS))
                    return RUNTIME_VISIBLE_ANNOTATIONS;
                break;
            case 27:
                if (hash == HASH_RUNTIME_INVISIBLE_ANNOTATIONS && name.equalsString(NAME_RUNTIME_INVISIBLE_ANNOTATIONS))
                    return RUNTIME_INVISIBLE_ANNOTATIONS;
                break;
            case 29:
                if (hash == HASH_RUNTIME_VISIBLE_TYPE_ANNOTATIONS && name.equalsString(NAME_RUNTIME_VISIBLE_TYPE_ANNOTATIONS))
                    return RUNTIME_VISIBLE_TYPE_ANNOTATIONS;
                break;
            case 31:
                if (hash == HASH_RUNTIME_INVISIBLE_TYPE_ANNOTATIONS && name.equalsString(NAME_RUNTIME_INVISIBLE_TYPE_ANNOTATIONS))
                    return RUNTIME_INVISIBLE_TYPE_ANNOTATIONS;
                break;
            case 34:
                if (hash == HASH_RUNTIME_VISIBLE_PARAMETER_ANNOTATIONS && name.equalsString(NAME_RUNTIME_VISIBLE_PARAMETER_ANNOTATIONS))
                    return RUNTIME_VISIBLE_PARAMETER_ANNOTATIONS;
                break;
            case 36:
                if (hash == HASH_RUNTIME_INVISIBLE_PARAMETER_ANNOTATIONS && name.equalsString(NAME_RUNTIME_INVISIBLE_PARAMETER_ANNOTATIONS))
                    return RUNTIME_INVISIBLE_PARAMETER_ANNOTATIONS;
                break;
        }
        return null;
    }

    /**
     * Map from names to attribute mappers for all standard attributes.
     */
    public static final Map<String, ? extends AttributeMapper<?>> PREDEFINED_ATTRIBUTES = Map.ofEntries(
            entry(NAME_CONSTANT_VALUE, CONSTANT_VALUE),
            entry(NAME_CODE, CODE),
            entry(NAME_STACK_MAP_TABLE, STACK_MAP_TABLE),
            entry(NAME_EXCEPTIONS, EXCEPTIONS),
            entry(NAME_INNER_CLASSES, INNER_CLASSES),
            entry(NAME_ENCLOSING_METHOD, ENCLOSING_METHOD),
            entry(NAME_SYNTHETIC, SYNTHETIC),
            entry(NAME_SIGNATURE, SIGNATURE),
            entry(NAME_SOURCE_FILE, SOURCE_FILE),
            entry(NAME_SOURCE_DEBUG_EXTENSION, SOURCE_DEBUG_EXTENSION),
            entry(NAME_LINE_NUMBER_TABLE, LINE_NUMBER_TABLE),
            entry(NAME_LOCAL_VARIABLE_TABLE, LOCAL_VARIABLE_TABLE),
            entry(NAME_LOCAL_VARIABLE_TYPE_TABLE, LOCAL_VARIABLE_TYPE_TABLE),
            entry(NAME_DEPRECATED, DEPRECATED),
            entry(NAME_RUNTIME_VISIBLE_ANNOTATIONS, RUNTIME_VISIBLE_ANNOTATIONS),
            entry(NAME_RUNTIME_INVISIBLE_ANNOTATIONS, RUNTIME_INVISIBLE_ANNOTATIONS),
            entry(NAME_RUNTIME_VISIBLE_PARAMETER_ANNOTATIONS, RUNTIME_VISIBLE_PARAMETER_ANNOTATIONS),
            entry(NAME_RUNTIME_INVISIBLE_PARAMETER_ANNOTATIONS, RUNTIME_INVISIBLE_PARAMETER_ANNOTATIONS),
            entry(NAME_RUNTIME_VISIBLE_TYPE_ANNOTATIONS, RUNTIME_VISIBLE_TYPE_ANNOTATIONS),
            entry(NAME_RUNTIME_INVISIBLE_TYPE_ANNOTATIONS, RUNTIME_INVISIBLE_TYPE_ANNOTATIONS),
            entry(NAME_ANNOTATION_DEFAULT, ANNOTATION_DEFAULT),
            entry(NAME_BOOTSTRAP_METHODS, BOOTSTRAP_METHODS),
            entry(NAME_METHOD_PARAMETERS, METHOD_PARAMETERS),
            entry(NAME_MODULE, MODULE),
            entry(NAME_MODULE_PACKAGES, MODULE_PACKAGES),
            entry(NAME_MODULE_MAIN_CLASS, MODULE_MAIN_CLASS),
            entry(NAME_NEST_HOST, NEST_HOST),
            entry(NAME_NEST_MEMBERS, NEST_MEMBERS),
            entry(NAME_RECORD, RECORD),
            entry(NAME_PERMITTED_SUBCLASSES, PERMITTED_SUBCLASSES));

}