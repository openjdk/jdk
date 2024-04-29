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

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import java.lang.classfile.attribute.AnnotationDefaultAttribute;
import java.lang.classfile.attribute.BootstrapMethodsAttribute;
import java.lang.classfile.attribute.CharacterRangeInfo;
import java.lang.classfile.attribute.CharacterRangeTableAttribute;
import java.lang.classfile.attribute.CodeAttribute;
import java.lang.classfile.attribute.CompilationIDAttribute;
import java.lang.classfile.attribute.ConstantValueAttribute;
import java.lang.classfile.attribute.DeprecatedAttribute;
import java.lang.classfile.attribute.EnclosingMethodAttribute;
import java.lang.classfile.attribute.ExceptionsAttribute;
import java.lang.classfile.attribute.InnerClassInfo;
import java.lang.classfile.attribute.InnerClassesAttribute;
import java.lang.classfile.attribute.LineNumberInfo;
import java.lang.classfile.attribute.LineNumberTableAttribute;
import java.lang.classfile.attribute.LocalVariableInfo;
import java.lang.classfile.attribute.LocalVariableTableAttribute;
import java.lang.classfile.attribute.LocalVariableTypeInfo;
import java.lang.classfile.attribute.LocalVariableTypeTableAttribute;
import java.lang.classfile.attribute.MethodParameterInfo;
import java.lang.classfile.attribute.MethodParametersAttribute;
import java.lang.classfile.attribute.ModuleAttribute;
import java.lang.classfile.attribute.ModuleExportInfo;
import java.lang.classfile.attribute.ModuleHashInfo;
import java.lang.classfile.attribute.ModuleHashesAttribute;
import java.lang.classfile.attribute.ModuleMainClassAttribute;
import java.lang.classfile.attribute.ModuleOpenInfo;
import java.lang.classfile.attribute.ModulePackagesAttribute;
import java.lang.classfile.attribute.ModuleProvideInfo;
import java.lang.classfile.attribute.ModuleRequireInfo;
import java.lang.classfile.attribute.ModuleResolutionAttribute;
import java.lang.classfile.attribute.ModuleTargetAttribute;
import java.lang.classfile.attribute.NestHostAttribute;
import java.lang.classfile.attribute.NestMembersAttribute;
import java.lang.classfile.attribute.PermittedSubclassesAttribute;
import java.lang.classfile.attribute.RecordAttribute;
import java.lang.classfile.attribute.RecordComponentInfo;
import java.lang.classfile.attribute.RuntimeInvisibleAnnotationsAttribute;
import java.lang.classfile.attribute.RuntimeInvisibleParameterAnnotationsAttribute;
import java.lang.classfile.attribute.RuntimeInvisibleTypeAnnotationsAttribute;
import java.lang.classfile.attribute.RuntimeVisibleAnnotationsAttribute;
import java.lang.classfile.attribute.RuntimeVisibleParameterAnnotationsAttribute;
import java.lang.classfile.attribute.RuntimeVisibleTypeAnnotationsAttribute;
import java.lang.classfile.attribute.SignatureAttribute;
import java.lang.classfile.attribute.SourceDebugExtensionAttribute;
import java.lang.classfile.attribute.SourceFileAttribute;
import java.lang.classfile.attribute.SourceIDAttribute;
import java.lang.classfile.attribute.StackMapTableAttribute;
import java.lang.classfile.attribute.SyntheticAttribute;
import java.lang.classfile.constantpool.Utf8Entry;
import jdk.internal.classfile.impl.AbstractAttributeMapper;
import jdk.internal.classfile.impl.BoundAttribute;
import jdk.internal.classfile.impl.CodeImpl;
import jdk.internal.classfile.impl.AbstractPoolEntry;
import jdk.internal.classfile.impl.StackMapDecoder;
import jdk.internal.javac.PreviewFeature;

/**
 * Attribute mappers for standard classfile attributes.
 *
 * @see AttributeMapper
 *
 * @since 22
 */
@PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
public class Attributes {

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

    /** {@return Attribute mapper for the {@code AnnotationDefault} attribute}
     * @since 23
     */
    public static AttributeMapper<AnnotationDefaultAttribute> annotationDefault() {
        return AnnotationDefaultMapper.INSTANCE;
    }

    private static final class AnnotationDefaultMapper extends AbstractAttributeMapper<AnnotationDefaultAttribute> {
        private static AnnotationDefaultMapper INSTANCE = new AnnotationDefaultMapper();

        private AnnotationDefaultMapper() {
            super(NAME_ANNOTATION_DEFAULT);
        }

        @Override
        public AnnotationDefaultAttribute readAttribute(AttributedElement e, ClassReader cf, int p) {
            return new BoundAttribute.BoundAnnotationDefaultAttr(cf, this, p);
        }

        @Override
        protected void writeBody(BufWriter buf, AnnotationDefaultAttribute attr) {
            attr.defaultValue().writeTo(buf);
        }

        @Override
        public AttributeMapper.AttributeStability stability() {
            return AttributeStability.CP_REFS;
        }
    };
    /**
     * {@return Attribute mapper for the {@code BootstrapMethods} attribute}
     * @since 23
     */
    public static AttributeMapper<BootstrapMethodsAttribute> bootstrapMethods() {
        return BootstrapMethodsMapper.INSTANCE;
    }

    private static final class BootstrapMethodsMapper extends AbstractAttributeMapper<BootstrapMethodsAttribute> {
        private static BootstrapMethodsMapper INSTANCE = new BootstrapMethodsMapper();

        private BootstrapMethodsMapper() {
            super(NAME_BOOTSTRAP_METHODS);
        }

        @Override
        public BootstrapMethodsAttribute readAttribute(AttributedElement e, ClassReader cf, int p) {
            return new BoundAttribute.BoundBootstrapMethodsAttribute(cf, this, p);
        }

        @Override
        protected void writeBody(BufWriter buf, BootstrapMethodsAttribute attr) {
            buf.writeList(attr.bootstrapMethods());
        }

        @Override
        public AttributeMapper.AttributeStability stability() {
            return AttributeStability.CP_REFS;
        }
    }

    /**
     * {@return Attribute mapper for the {@code CharacterRangeTable} attribute}
     * @since 23
     */
    public static AttributeMapper<CharacterRangeTableAttribute> characterRangeTable() {
        return CharacterRangeTableMapper.INSTANCE;
    }

    private static final class CharacterRangeTableMapper extends AbstractAttributeMapper<CharacterRangeTableAttribute> {
        private static CharacterRangeTableMapper INSTANCE = new CharacterRangeTableMapper();

        private CharacterRangeTableMapper() {
            super(NAME_CHARACTER_RANGE_TABLE, true);
        }

        @Override
        public CharacterRangeTableAttribute readAttribute(AttributedElement e, ClassReader cf, int p) {
            return new BoundAttribute.BoundCharacterRangeTableAttribute(cf, this, p);
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

        @Override
        public AttributeMapper.AttributeStability stability() {
            return AttributeStability.LABELS;
        }
    }

    /**
     * {@return Attribute mapper for the {@code Code} attribute}
     * @since 23
     */
    public static AttributeMapper<CodeAttribute> code() {
        return CodeMapper.INSTANCE;
    }

    private static final class CodeMapper extends AbstractAttributeMapper<CodeAttribute> {
        private static CodeMapper INSTANCE = new CodeMapper();

        private CodeMapper() {
            super(NAME_CODE);
        }

        @Override
        public CodeAttribute readAttribute(AttributedElement e, ClassReader cf, int p) {
            return new CodeImpl(e, cf, this, p);
        }

        @Override
        protected void writeBody(BufWriter buf, CodeAttribute attr) {
            throw new UnsupportedOperationException("Code attribute does not support direct write");
        }

        @Override
        public AttributeMapper.AttributeStability stability() {
            return AttributeStability.CP_REFS;
        }
    }


    /**
     * {@return Attribute mapper for the {@code CompilationID} attribute}
     * @since 23
     */
    public static AttributeMapper<CompilationIDAttribute> compilationId() {
        return CompilationIDMapper.INSTANCE;
    }

    private static final class CompilationIDMapper extends AbstractAttributeMapper<CompilationIDAttribute> {
        private static CompilationIDMapper INSTANCE = new CompilationIDMapper();

        private CompilationIDMapper() {
            super(NAME_COMPILATION_ID, true);
        }

        @Override
        public CompilationIDAttribute readAttribute(AttributedElement e, ClassReader cf, int p) {
            return new BoundAttribute.BoundCompilationIDAttribute(cf, this, p);
        }

        @Override
        protected void writeBody(BufWriter buf, CompilationIDAttribute attr) {
            buf.writeIndex(attr.compilationId());
        }

        @Override
        public AttributeMapper.AttributeStability stability() {
            return AttributeStability.CP_REFS;
        }
    }

    /**
     * {@return Attribute mapper for the {@code ConstantValue} attribute}
     * @since 23
     */
    public static AttributeMapper<ConstantValueAttribute> constantValue() {
        return ConstantValueMapper.INSTANCE;
    }

    private static final class ConstantValueMapper extends AbstractAttributeMapper<ConstantValueAttribute> {
        private static ConstantValueMapper INSTANCE = new ConstantValueMapper();

        private ConstantValueMapper() {
            super(NAME_CONSTANT_VALUE);
        }

        @Override
        public ConstantValueAttribute readAttribute(AttributedElement e, ClassReader cf, int p) {
            return new BoundAttribute.BoundConstantValueAttribute(cf, this, p);
        }

        @Override
        protected void writeBody(BufWriter buf, ConstantValueAttribute attr) {
            buf.writeIndex(attr.constant());
        }

        @Override
        public AttributeMapper.AttributeStability stability() {
            return AttributeStability.CP_REFS;
        }
    }

    /**
     * {@return Attribute mapper for the {@code Deprecated} attribute}
     * @since 23
     */
    public static AttributeMapper<DeprecatedAttribute> deprecated() {
        return DeprecatedMapper.INSTANCE;
    }

    private static final class DeprecatedMapper extends AbstractAttributeMapper<DeprecatedAttribute> {
        private static DeprecatedMapper INSTANCE = new DeprecatedMapper();

        private DeprecatedMapper() {
            super(NAME_DEPRECATED, true);
        }

        @Override
        public DeprecatedAttribute readAttribute(AttributedElement e, ClassReader cf, int p) {
            return new BoundAttribute.BoundDeprecatedAttribute(cf, this, p);
        }

        @Override
        protected void writeBody(BufWriter buf, DeprecatedAttribute attr) {
            // empty
        }

        @Override
        public AttributeMapper.AttributeStability stability() {
            return AttributeStability.STATELESS;
        }
    }

    /**
     * {@return Attribute mapper for the {@code EnclosingMethod} attribute}
     * @since 23
     */
    public static AttributeMapper<EnclosingMethodAttribute> enclosingMethod() {
        return EnclosingMethodMapper.INSTANCE;
    }

    private static final class EnclosingMethodMapper extends AbstractAttributeMapper<EnclosingMethodAttribute> {
        private static EnclosingMethodMapper INSTANCE = new EnclosingMethodMapper();

        private EnclosingMethodMapper() {
            super(NAME_ENCLOSING_METHOD);
        }

        @Override
        public EnclosingMethodAttribute readAttribute(AttributedElement e, ClassReader cf, int p) {
            return new BoundAttribute.BoundEnclosingMethodAttribute(cf, this, p);
        }

        @Override
        protected void writeBody(BufWriter buf, EnclosingMethodAttribute attr) {
            buf.writeIndex(attr.enclosingClass());
            buf.writeIndexOrZero(attr.enclosingMethod().orElse(null));
        }

        @Override
        public AttributeMapper.AttributeStability stability() {
            return AttributeStability.CP_REFS;
        }
    }

    /**
     * {@return Attribute mapper for the {@code Exceptions} attribute}
     * @since 23
     */
    public static AttributeMapper<ExceptionsAttribute> exceptions() {
        return ExceptionsMapper.INSTANCE;
    }

    private static final class ExceptionsMapper extends AbstractAttributeMapper<ExceptionsAttribute> {
        private static ExceptionsMapper INSTANCE = new ExceptionsMapper();

        private ExceptionsMapper() {
            super(NAME_EXCEPTIONS);
        }

        @Override
        public ExceptionsAttribute readAttribute(AttributedElement e, ClassReader cf, int p) {
            return new BoundAttribute.BoundExceptionsAttribute(cf, this, p);
        }

        @Override
        protected void writeBody(BufWriter buf, ExceptionsAttribute attr) {
            buf.writeListIndices(attr.exceptions());
        }

        @Override
        public AttributeMapper.AttributeStability stability() {
            return AttributeStability.CP_REFS;
        }
    }

    /**
     * {@return Attribute mapper for the {@code InnerClasses} attribute}
     * @since 23
     */
    public static AttributeMapper<InnerClassesAttribute> innerClasses() {
        return InnerClassesMapper.INSTANCE;
    }

    private static final class InnerClassesMapper extends AbstractAttributeMapper<InnerClassesAttribute> {
        private static InnerClassesMapper INSTANCE = new InnerClassesMapper();

        private InnerClassesMapper() {
            super(NAME_INNER_CLASSES);
        }

        @Override
        public InnerClassesAttribute readAttribute(AttributedElement e, ClassReader cf, int p) {
            return new BoundAttribute.BoundInnerClassesAttribute(cf, this, p);
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

        @Override
        public AttributeMapper.AttributeStability stability() {
            return AttributeStability.CP_REFS;
        }
    }

    /**
     * {@return Attribute mapper for the {@code LineNumberTable} attribute}
     * @since 23
     */
    public static AttributeMapper<LineNumberTableAttribute> lineNumberTable() {
        return LineNumberTableMapper.INSTANCE;
    }

    private static final class LineNumberTableMapper extends AbstractAttributeMapper<LineNumberTableAttribute> {
        private static LineNumberTableMapper INSTANCE = new LineNumberTableMapper();

        private LineNumberTableMapper() {
            super(NAME_LINE_NUMBER_TABLE, true);
        }

        @Override
        public LineNumberTableAttribute readAttribute(AttributedElement e, ClassReader cf, int p) {
            return new BoundAttribute.BoundLineNumberTableAttribute(cf, this, p);
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

        @Override
        public AttributeMapper.AttributeStability stability() {
            return AttributeStability.LABELS;
        }
    }

    /**
     * {@return Attribute mapper for the {@code LocalVariableTable} attribute}
     * @since 23
     */
    public static AttributeMapper<LocalVariableTableAttribute> localVariableTable() {
        return LocalVariableTableMapper.INSTANCE;
    }

    private static final class LocalVariableTableMapper extends AbstractAttributeMapper<LocalVariableTableAttribute> {
        private static LocalVariableTableMapper INSTANCE = new LocalVariableTableMapper();

        private LocalVariableTableMapper() {
            super(NAME_LOCAL_VARIABLE_TABLE, true);
        }

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

        @Override
        public AttributeMapper.AttributeStability stability() {
            return AttributeStability.LABELS;
        }
    }

    /**
     * {@return Attribute mapper for the {@code LocalVariableTypeTable} attribute}
     * @since 23
     */
    public static AttributeMapper<LocalVariableTypeTableAttribute> localVariableTypeTable() {
        return LocalVariableTypeTableMapper.INSTANCE;
    }

    private static final class LocalVariableTypeTableMapper extends AbstractAttributeMapper<LocalVariableTypeTableAttribute> {
        private static LocalVariableTypeTableMapper INSTANCE = new LocalVariableTypeTableMapper();

        private LocalVariableTypeTableMapper() {
            super(NAME_LOCAL_VARIABLE_TYPE_TABLE, true);
        }

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

        @Override
        public AttributeMapper.AttributeStability stability() {
            return AttributeStability.LABELS;
        }
    }

    /**
     * {@return Attribute mapper for the {@code MethodParameters} attribute}
     * @since 23
     */
    public static AttributeMapper<MethodParametersAttribute> methodParameters() {
        return MethodParametersMapper.INSTANCE;
    }

    private static final class MethodParametersMapper extends AbstractAttributeMapper<MethodParametersAttribute> {
        private static MethodParametersMapper INSTANCE = new MethodParametersMapper();

        private MethodParametersMapper() {
            super(NAME_METHOD_PARAMETERS);
        }

        @Override
        public MethodParametersAttribute readAttribute(AttributedElement e, ClassReader cf, int p) {
            return new BoundAttribute.BoundMethodParametersAttribute(cf, this, p);
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

        @Override
        public AttributeMapper.AttributeStability stability() {
            return AttributeStability.CP_REFS;
        }
    }

    /**
     * {@return Attribute mapper for the {@code Module} attribute}
     * @since 23
     */
    public static AttributeMapper<ModuleAttribute> module() {
        return ModuleMapper.INSTANCE;
    }

    private static final class ModuleMapper extends AbstractAttributeMapper<ModuleAttribute> {
        private static ModuleMapper INSTANCE = new ModuleMapper();

        private ModuleMapper() {
            super(NAME_MODULE);
        }

        @Override
        public ModuleAttribute readAttribute(AttributedElement e, ClassReader cf, int p) {
            return new BoundAttribute.BoundModuleAttribute(cf, this, p);
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

        @Override
        public AttributeMapper.AttributeStability stability() {
            return AttributeStability.CP_REFS;
        }
    }

    /**
     * {@return Attribute mapper for the {@code ModuleHashes} attribute}
     * @since 23
     */
    public static AttributeMapper<ModuleHashesAttribute> moduleHashes() {
        return ModuleHashesMapper.INSTANCE;
    }

    private static final class ModuleHashesMapper extends AbstractAttributeMapper<ModuleHashesAttribute> {
        private static ModuleHashesMapper INSTANCE = new ModuleHashesMapper();

        private ModuleHashesMapper() {
            super(NAME_MODULE_HASHES);
        }

        @Override
        public ModuleHashesAttribute readAttribute(AttributedElement e, ClassReader cf, int p) {
            return new BoundAttribute.BoundModuleHashesAttribute(cf, this, p);
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

        @Override
        public AttributeMapper.AttributeStability stability() {
            return AttributeStability.CP_REFS;
        }
    }

    /**
     * {@return Attribute mapper for the {@code ModuleMainClass} attribute}
     * @since 23
     */
    public static AttributeMapper<ModuleMainClassAttribute> moduleMainClass() {
        return ModuleMainClassMapper.INSTANCE;
    }

    private static final class ModuleMainClassMapper extends AbstractAttributeMapper<ModuleMainClassAttribute> {
        private static ModuleMainClassMapper INSTANCE = new ModuleMainClassMapper();

        private ModuleMainClassMapper() {
            super(NAME_MODULE_MAIN_CLASS);
        }

        @Override
        public ModuleMainClassAttribute readAttribute(AttributedElement e, ClassReader cf, int p) {
            return new BoundAttribute.BoundModuleMainClassAttribute(cf, this, p);
        }

        @Override
        protected void writeBody(BufWriter buf, ModuleMainClassAttribute attr) {
            buf.writeIndex(attr.mainClass());
        }

        @Override
        public AttributeMapper.AttributeStability stability() {
            return AttributeStability.CP_REFS;
        }
    }

    /**
     * {@return Attribute mapper for the {@code ModulePackages} attribute}
     * @since 23
     */
    public static AttributeMapper<ModulePackagesAttribute> modulePackages() {
        return ModulePackagesMapper.INSTANCE;
    }

    private static final class ModulePackagesMapper extends AbstractAttributeMapper<ModulePackagesAttribute> {
        private static ModulePackagesMapper INSTANCE = new ModulePackagesMapper();

        private ModulePackagesMapper() {
            super(NAME_MODULE_PACKAGES);
        }

        @Override
        public ModulePackagesAttribute readAttribute(AttributedElement e, ClassReader cf, int p) {
            return new BoundAttribute.BoundModulePackagesAttribute(cf, this, p);
        }

        @Override
        protected void writeBody(BufWriter buf, ModulePackagesAttribute attr) {
            buf.writeListIndices(attr.packages());
        }

        @Override
        public AttributeMapper.AttributeStability stability() {
            return AttributeStability.CP_REFS;
        }
    }

    /**
     * {@return Attribute mapper for the {@code ModuleResolution} attribute}
     * @since 23
     */
    public static AttributeMapper<ModuleResolutionAttribute> moduleResolution() {
        return ModuleResolutionMapper.INSTANCE;
    }

    private static final class ModuleResolutionMapper extends AbstractAttributeMapper<ModuleResolutionAttribute> {
        private static ModuleResolutionMapper INSTANCE = new ModuleResolutionMapper();

        private ModuleResolutionMapper() {
            super(NAME_MODULE_RESOLUTION);
        }

        @Override
        public ModuleResolutionAttribute readAttribute(AttributedElement e, ClassReader cf, int p) {
            return new BoundAttribute.BoundModuleResolutionAttribute(cf, this, p);
        }

        @Override
        protected void writeBody(BufWriter buf, ModuleResolutionAttribute attr) {
            buf.writeU2(attr.resolutionFlags());
        }

        @Override
        public AttributeMapper.AttributeStability stability() {
            return AttributeStability.STATELESS;
        }
    }

    /**
     * {@return Attribute mapper for the {@code ModuleTarget} attribute}
     * @since 23
     */
    public static AttributeMapper<ModuleTargetAttribute> moduleTarget() {
        return ModuleTargetMapper.INSTANCE;
    }

    private static final class ModuleTargetMapper extends AbstractAttributeMapper<ModuleTargetAttribute> {
        private static ModuleTargetMapper INSTANCE = new ModuleTargetMapper();

        private ModuleTargetMapper() {
            super(NAME_MODULE_TARGET);
        }

        @Override
        public ModuleTargetAttribute readAttribute(AttributedElement e, ClassReader cf, int p) {
            return new BoundAttribute.BoundModuleTargetAttribute(cf, this, p);
        }

        @Override
        protected void writeBody(BufWriter buf, ModuleTargetAttribute attr) {
            buf.writeIndex(attr.targetPlatform());
        }

        @Override
        public AttributeMapper.AttributeStability stability() {
            return AttributeStability.CP_REFS;
        }
    }

    /**
     * {@return Attribute mapper for the {@code NestHost} attribute}
     * @since 23
     */
    public static AttributeMapper<NestHostAttribute> nestHost() {
        return NestHostMapper.INSTANCE;
    }

    private static final class NestHostMapper extends AbstractAttributeMapper<NestHostAttribute> {
        private static NestHostMapper INSTANCE = new NestHostMapper();

        private NestHostMapper() {
            super(NAME_NEST_HOST);
        }

        @Override
        public NestHostAttribute readAttribute(AttributedElement e, ClassReader cf, int p) {
            return new BoundAttribute.BoundNestHostAttribute(cf, this, p);
        }

        @Override
        protected void writeBody(BufWriter buf, NestHostAttribute attr) {
            buf.writeIndex(attr.nestHost());
        }

        @Override
        public AttributeMapper.AttributeStability stability() {
            return AttributeStability.CP_REFS;
        }
    }

    /**
     * {@return Attribute mapper for the {@code NestMembers} attribute}
     * @since 23
     */
    public static AttributeMapper<NestMembersAttribute> nestMembers() {
        return NestMembersMapper.INSTANCE;
    }

    private static final class NestMembersMapper extends AbstractAttributeMapper<NestMembersAttribute> {
        private static NestMembersMapper INSTANCE = new NestMembersMapper();

        private NestMembersMapper() {
            super(NAME_NEST_MEMBERS);
        }

        @Override
        public NestMembersAttribute readAttribute(AttributedElement e, ClassReader cf, int p) {
            return new BoundAttribute.BoundNestMembersAttribute(cf, this, p);
        }

        @Override
        protected void writeBody(BufWriter buf, NestMembersAttribute attr) {
            buf.writeListIndices(attr.nestMembers());
        }

        @Override
        public AttributeMapper.AttributeStability stability() {
            return AttributeStability.CP_REFS;
        }
    }

    /**
     * {@return Attribute mapper for the {@code PermittedSubclasses} attribute}
     * @since 23
     */
    public static AttributeMapper<PermittedSubclassesAttribute> permittedSubclasses() {
        return PermittedSubclassesMapper.INSTANCE;
    }

    private static final class PermittedSubclassesMapper extends AbstractAttributeMapper<PermittedSubclassesAttribute> {
        private static PermittedSubclassesMapper INSTANCE = new PermittedSubclassesMapper();

        private PermittedSubclassesMapper() {
            super(NAME_PERMITTED_SUBCLASSES);
        }

        @Override
        public PermittedSubclassesAttribute readAttribute(AttributedElement e, ClassReader cf, int p) {
            return new BoundAttribute.BoundPermittedSubclassesAttribute(cf, this, p);
        }

        @Override
        protected void writeBody(BufWriter buf, PermittedSubclassesAttribute attr) {
            buf.writeListIndices(attr.permittedSubclasses());
        }

        @Override
        public AttributeMapper.AttributeStability stability() {
            return AttributeStability.CP_REFS;
        }
    }

    /**
     * {@return Attribute mapper for the {@code Record} attribute}
     * @since 23
     */
    public static AttributeMapper<RecordAttribute> record() {
        return RecordMapper.INSTANCE;
    }

    private static final class RecordMapper extends AbstractAttributeMapper<RecordAttribute> {
        private static RecordMapper INSTANCE = new RecordMapper();

        private RecordMapper() {
            super(NAME_RECORD);
        }

        @Override
        public RecordAttribute readAttribute(AttributedElement e, ClassReader cf, int p) {
            return new BoundAttribute.BoundRecordAttribute(cf, this, p);
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

        @Override
        public AttributeMapper.AttributeStability stability() {
            return AttributeStability.CP_REFS;
        }
    }

    /**
     * {@return Attribute mapper for the {@code RuntimeInvisibleAnnotations} attribute}
     * @since 23
     */
    public static AttributeMapper<RuntimeInvisibleAnnotationsAttribute> runtimeInvisibleAnnotations() {
        return RuntimeInvisibleAnnotationsMapper.INSTANCE;
    }

    private static final class RuntimeInvisibleAnnotationsMapper extends AbstractAttributeMapper<RuntimeInvisibleAnnotationsAttribute> {
        private static RuntimeInvisibleAnnotationsMapper INSTANCE = new RuntimeInvisibleAnnotationsMapper();

        private RuntimeInvisibleAnnotationsMapper() {
            super(NAME_RUNTIME_INVISIBLE_ANNOTATIONS);
        }

        @Override
        public RuntimeInvisibleAnnotationsAttribute readAttribute(AttributedElement enclosing, ClassReader cf, int pos) {
            return new BoundAttribute.BoundRuntimeInvisibleAnnotationsAttribute(cf, pos);
        }

        @Override
        protected void writeBody(BufWriter buf, RuntimeInvisibleAnnotationsAttribute attr) {
            buf.writeList(attr.annotations());
        }

        @Override
        public AttributeMapper.AttributeStability stability() {
            return AttributeStability.CP_REFS;
        }
    }

    /**
     * {@return Attribute mapper for the {@code RuntimeInvisibleParameterAnnotations} attribute}
     * @since 23
     */
    public static AttributeMapper<RuntimeInvisibleParameterAnnotationsAttribute> runtimeInvisibleParameterAnnotations() {
        return RuntimeInvisibleParameterAnnotationsMapper.INSTANCE;
    }

    private static final class RuntimeInvisibleParameterAnnotationsMapper extends AbstractAttributeMapper<RuntimeInvisibleParameterAnnotationsAttribute> {
        private static RuntimeInvisibleParameterAnnotationsMapper INSTANCE = new RuntimeInvisibleParameterAnnotationsMapper();

        private RuntimeInvisibleParameterAnnotationsMapper() {
            super(NAME_RUNTIME_INVISIBLE_PARAMETER_ANNOTATIONS);
        }

        @Override
        public RuntimeInvisibleParameterAnnotationsAttribute readAttribute(AttributedElement e, ClassReader cf, int p) {
            return new BoundAttribute.BoundRuntimeInvisibleParameterAnnotationsAttribute(cf, this, p);
        }

        @Override
        protected void writeBody(BufWriter buf, RuntimeInvisibleParameterAnnotationsAttribute attr) {
            List<List<Annotation>> lists = attr.parameterAnnotations();
            buf.writeU1(lists.size());
            for (List<Annotation> list : lists)
                buf.writeList(list);
        }

        @Override
        public AttributeMapper.AttributeStability stability() {
            return AttributeStability.CP_REFS;
        }
    }

    /**
     * {@return Attribute mapper for the {@code RuntimeInvisibleTypeAnnotations} attribute}
     * @since 23
     */
    public static AttributeMapper<RuntimeInvisibleTypeAnnotationsAttribute> runtimeInvisibleTypeAnnotations() {
        return RuntimeInvisibleTypeAnnotationsMapper.INSTANCE;
    }

    private static final class RuntimeInvisibleTypeAnnotationsMapper extends AbstractAttributeMapper<RuntimeInvisibleTypeAnnotationsAttribute> {
        private static RuntimeInvisibleTypeAnnotationsMapper INSTANCE = new RuntimeInvisibleTypeAnnotationsMapper();

        private RuntimeInvisibleTypeAnnotationsMapper() {
            super(NAME_RUNTIME_INVISIBLE_TYPE_ANNOTATIONS);
        }

        @Override
        public RuntimeInvisibleTypeAnnotationsAttribute readAttribute(AttributedElement e, ClassReader cf, int p) {
            return new BoundAttribute.BoundRuntimeInvisibleTypeAnnotationsAttribute(e, cf, this, p);
        }

        @Override
        protected void writeBody(BufWriter buf, RuntimeInvisibleTypeAnnotationsAttribute attr) {
            buf.writeList(attr.annotations());
        }

        @Override
        public AttributeMapper.AttributeStability stability() {
            return AttributeStability.UNSTABLE;
        }
    }

    /**
     * {@return Attribute mapper for the {@code RuntimeVisibleAnnotations} attribute}
     * @since 23
     */
    public static AttributeMapper<RuntimeVisibleAnnotationsAttribute> runtimeVisibleAnnotations() {
        return RuntimeVisibleAnnotationsMapper.INSTANCE;
    }

    private static final class RuntimeVisibleAnnotationsMapper extends AbstractAttributeMapper<RuntimeVisibleAnnotationsAttribute> {
        private static RuntimeVisibleAnnotationsMapper INSTANCE = new RuntimeVisibleAnnotationsMapper();

        private RuntimeVisibleAnnotationsMapper() {
            super(NAME_RUNTIME_VISIBLE_ANNOTATIONS);
        }

        @Override
        public RuntimeVisibleAnnotationsAttribute readAttribute(AttributedElement enclosing, ClassReader cf, int pos) {
            return new BoundAttribute.BoundRuntimeVisibleAnnotationsAttribute(cf, pos);
        }

        @Override
        protected void writeBody(BufWriter buf, RuntimeVisibleAnnotationsAttribute attr) {
            buf.writeList(attr.annotations());
        }

        @Override
        public AttributeMapper.AttributeStability stability() {
            return AttributeStability.CP_REFS;
        }
    }

    /**
     * {@return Attribute mapper for the {@code RuntimeVisibleParameterAnnotations} attribute}
     * @since 23
     */
    public static AttributeMapper<RuntimeVisibleParameterAnnotationsAttribute> runtimeVisibleParameterAnnotations() {
        return RuntimeVisibleParameterAnnotationsMapper.INSTANCE;
    }

    private static final class RuntimeVisibleParameterAnnotationsMapper extends AbstractAttributeMapper<RuntimeVisibleParameterAnnotationsAttribute> {
        private static RuntimeVisibleParameterAnnotationsMapper INSTANCE = new RuntimeVisibleParameterAnnotationsMapper();

        private RuntimeVisibleParameterAnnotationsMapper() {
            super(NAME_RUNTIME_VISIBLE_PARAMETER_ANNOTATIONS);
        }

        @Override
        public RuntimeVisibleParameterAnnotationsAttribute readAttribute(AttributedElement e, ClassReader cf, int p) {
            return new BoundAttribute.BoundRuntimeVisibleParameterAnnotationsAttribute(cf, this, p);
        }

        @Override
        protected void writeBody(BufWriter buf, RuntimeVisibleParameterAnnotationsAttribute attr) {
            List<List<Annotation>> lists = attr.parameterAnnotations();
            buf.writeU1(lists.size());
            for (List<Annotation> list : lists)
                buf.writeList(list);
        }

        @Override
        public AttributeMapper.AttributeStability stability() {
            return AttributeStability.CP_REFS;
        }
    }

    /**
     * {@return Attribute mapper for the {@code RuntimeVisibleTypeAnnotations} attribute}
     * @since 23
     */
    public static AttributeMapper<RuntimeVisibleTypeAnnotationsAttribute> runtimeVisibleTypeAnnotations() {
        return RuntimeVisibleTypeAnnotationsMapper.INSTANCE;
    }

    private static final class RuntimeVisibleTypeAnnotationsMapper extends AbstractAttributeMapper<RuntimeVisibleTypeAnnotationsAttribute> {
        private static RuntimeVisibleTypeAnnotationsMapper INSTANCE = new RuntimeVisibleTypeAnnotationsMapper();

        private RuntimeVisibleTypeAnnotationsMapper() {
            super(NAME_RUNTIME_VISIBLE_TYPE_ANNOTATIONS);
        }

        @Override
        public RuntimeVisibleTypeAnnotationsAttribute readAttribute(AttributedElement e, ClassReader cf, int p) {
            return new BoundAttribute.BoundRuntimeVisibleTypeAnnotationsAttribute(e, cf, this, p);
        }

        @Override
        protected void writeBody(BufWriter buf, RuntimeVisibleTypeAnnotationsAttribute attr) {
            buf.writeList(attr.annotations());
        }

        @Override
        public AttributeMapper.AttributeStability stability() {
            return AttributeStability.UNSTABLE;
        }
    }

    /**
     * {@return Attribute mapper for the {@code Signature} attribute}
     * @since 23
     */
    public static AttributeMapper<SignatureAttribute> signature() {
        return SignatureMapper.INSTANCE;
    }

    private static final class SignatureMapper extends AbstractAttributeMapper<SignatureAttribute> {
        private static SignatureMapper INSTANCE = new SignatureMapper();

        private SignatureMapper() {
            super(NAME_SIGNATURE);
        }

        @Override
        public SignatureAttribute readAttribute(AttributedElement e, ClassReader cf, int p) {
            return new BoundAttribute.BoundSignatureAttribute(cf, this, p);
        }

        @Override
        protected void writeBody(BufWriter buf, SignatureAttribute attr) {
            buf.writeIndex(attr.signature());
        }

        @Override
        public AttributeMapper.AttributeStability stability() {
            return AttributeStability.CP_REFS;
        }
    }

    /**
     * {@return Attribute mapper for the {@code SourceDebugExtension} attribute}
     * @since 23
     */
    public static AttributeMapper<SourceDebugExtensionAttribute> sourceDebugExtension() {
        return SourceDebugExtensionMapper.INSTANCE;
    }

    private static final class SourceDebugExtensionMapper extends AbstractAttributeMapper<SourceDebugExtensionAttribute> {
        private static SourceDebugExtensionMapper INSTANCE = new SourceDebugExtensionMapper();

        private SourceDebugExtensionMapper() {
            super(NAME_SOURCE_DEBUG_EXTENSION);
        }

        @Override
        public SourceDebugExtensionAttribute readAttribute(AttributedElement e, ClassReader cf, int p) {
            return new BoundAttribute.BoundSourceDebugExtensionAttribute(cf, this, p);
        }

        @Override
        protected void writeBody(BufWriter buf, SourceDebugExtensionAttribute attr) {
            buf.writeBytes(attr.contents());
        }

        @Override
        public AttributeMapper.AttributeStability stability() {
            return AttributeStability.STATELESS;
        }
    }

    /**
     * {@return Attribute mapper for the {@code SourceFile} attribute}
     * @since 23
     */
    public static AttributeMapper<SourceFileAttribute> sourceFile() {
        return SourceFileMapper.INSTANCE;
    }

    private static final class SourceFileMapper extends AbstractAttributeMapper<SourceFileAttribute> {
        private static SourceFileMapper INSTANCE = new SourceFileMapper();

        private SourceFileMapper() {
            super(NAME_SOURCE_FILE);
        }

        @Override
        public SourceFileAttribute readAttribute(AttributedElement e, ClassReader cf, int p) {
            return new BoundAttribute.BoundSourceFileAttribute(cf, this, p);
        }

        @Override
        protected void writeBody(BufWriter buf, SourceFileAttribute attr) {
            buf.writeIndex(attr.sourceFile());
        }

        @Override
        public AttributeMapper.AttributeStability stability() {
            return AttributeStability.CP_REFS;
        }
    }

    /**
     * {@return Attribute mapper for the {@code SourceID} attribute}
     * @since 23
     */
    public static AttributeMapper<SourceIDAttribute> sourceId() {
        return SourceIDMapper.INSTANCE;
    }

    private static final class SourceIDMapper extends AbstractAttributeMapper<SourceIDAttribute> {
        private static SourceIDMapper INSTANCE = new SourceIDMapper();

        private SourceIDMapper() {
            super(NAME_SOURCE_ID);
        }

        @Override
        public SourceIDAttribute readAttribute(AttributedElement e, ClassReader cf, int p) {
            return new BoundAttribute.BoundSourceIDAttribute(cf, this, p);
        }

        @Override
        protected void writeBody(BufWriter buf, SourceIDAttribute attr) {
            buf.writeIndex(attr.sourceId());
        }

        @Override
        public AttributeMapper.AttributeStability stability() {
            return AttributeStability.CP_REFS;
        }
    }

    /**
     * {@return Attribute mapper for the {@code StackMapTable} attribute}
     * @since 23
     */
    public static AttributeMapper<StackMapTableAttribute> stackMapTable() {
        return StackMapTableMapper.INSTANCE;
    }

    private static final class StackMapTableMapper extends AbstractAttributeMapper<StackMapTableAttribute> {
        private static StackMapTableMapper INSTANCE = new StackMapTableMapper();

        private StackMapTableMapper() {
            super(NAME_STACK_MAP_TABLE);
        }

        @Override
        public StackMapTableAttribute readAttribute(AttributedElement e, ClassReader cf, int p) {
            return new BoundAttribute.BoundStackMapTableAttribute((CodeImpl)e, cf, this, p);
        }

        @Override
        protected void writeBody(BufWriter b, StackMapTableAttribute attr) {
            StackMapDecoder.writeFrames(b, attr.entries());
        }

        @Override
        public AttributeMapper.AttributeStability stability() {
            return AttributeStability.LABELS;
        }
    }


    /**
     * {@return Attribute mapper for the {@code Synthetic} attribute}
     * @since 23
     */
    public static AttributeMapper<SyntheticAttribute> synthetic() {
        return SyntheticMapper.INSTANCE;
    }

    private static final class SyntheticMapper extends AbstractAttributeMapper<SyntheticAttribute> {
        private static SyntheticMapper INSTANCE = new SyntheticMapper();

        private SyntheticMapper() {
            super(NAME_SYNTHETIC, true);
        }

        @Override
        public SyntheticAttribute readAttribute(AttributedElement e, ClassReader cf, int p) {
            return new BoundAttribute.BoundSyntheticAttribute(cf, this, p);
        }

        @Override
        protected void writeBody(BufWriter buf, SyntheticAttribute attr) {
            // empty
        }

        @Override
        public AttributeMapper.AttributeStability stability() {
            return AttributeStability.STATELESS;
        }
    }

    /**
     * {@return the attribute mapper for a standard attribute}
     *
     * @param name the name of the attribute to find
     */
    public static AttributeMapper<?> standardAttribute(Utf8Entry name) {
        return PredefinedAttributes._ATTR_MAP.get(name);
    }

    /**
     * {@return All standard attribute mappers.}
     * @since 23
     */
    public static Set<AttributeMapper<?>> predefinedAttributes() {
        return PredefinedAttributes._ATTRS;
    }

    private static final class PredefinedAttributes {
        private static final Set<AttributeMapper<?>> _ATTRS = Set.of(
            annotationDefault(),
            bootstrapMethods(),
            characterRangeTable(),
            code(),
            compilationId(),
            constantValue(),
            deprecated(),
            enclosingMethod(),
            exceptions(),
            innerClasses(),
            lineNumberTable(),
            localVariableTable(),
            localVariableTypeTable(),
            methodParameters(),
            module(),
            moduleHashes(),
            moduleMainClass(),
            modulePackages(),
            moduleResolution(),
            moduleTarget(),
            nestHost(),
            nestMembers(),
            permittedSubclasses(),
            record(),
            runtimeInvisibleAnnotations(),
            runtimeInvisibleParameterAnnotations(),
            runtimeInvisibleTypeAnnotations(),
            runtimeVisibleAnnotations(),
            runtimeVisibleParameterAnnotations(),
            runtimeVisibleTypeAnnotations(),
            signature(),
            sourceDebugExtension(),
            sourceFile(),
            sourceId(),
            stackMapTable(),
            synthetic());

        private static final Map<Utf8Entry, AttributeMapper<?>> _ATTR_MAP;
        static {
            var map = new HashMap<Utf8Entry, AttributeMapper<?>>(64);
            for (var am : _ATTRS) {
                map.put(AbstractPoolEntry.rawUtf8EntryFromStandardAttributeName(am.name()), am);
            }
            _ATTR_MAP = Collections.unmodifiableMap(map);
        }
    }
}
