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

import java.lang.classfile.Annotation;
import java.lang.classfile.Attribute;
import java.lang.classfile.AttributeMapper;
import java.lang.classfile.AttributedElement;
import java.lang.classfile.BufWriter;
import java.lang.classfile.ClassReader;
import java.lang.classfile.attribute.*;
import java.util.List;

import static java.lang.classfile.Attributes.*;

public sealed abstract class AbstractAttributeMapper<T extends Attribute<T>>
        implements AttributeMapper<T> {

    private final String name;
    private final AttributeMapper.AttributeStability stability;
    private final boolean allowMultiple;

    protected abstract void writeBody(BufWriter buf, T attr);

    public AbstractAttributeMapper(String name, AttributeMapper.AttributeStability stability) {
        this(name, stability, false);
    }

    public AbstractAttributeMapper(String name,
                                   AttributeMapper.AttributeStability stability,
                                   boolean allowMultiple) {
        this.name = name;
        this.stability = stability;
        this.allowMultiple = allowMultiple;
    }

    @Override
    public final String name() {
        return name;
    }

    @Override
    public final void writeAttribute(BufWriter buf, T attr) {
        buf.writeIndex(buf.constantPool().utf8Entry(name));
        buf.writeInt(0);
        int start = buf.size();
        writeBody(buf, attr);
        int written = buf.size() - start;
        buf.patchInt(start - 4, 4, written);
    }

    @Override
    public AttributeMapper.AttributeStability stability() {
        return stability;
    }

    @Override
    public boolean allowMultiple() {
        return allowMultiple;
    }

    @Override
    public String toString() {
        return String.format("AttributeMapper[name=%s, allowMultiple=%b, stability=%s]",
                name, allowMultiple, stability());
    }

    public static final class AnnotationDefaultMapper extends AbstractAttributeMapper<AnnotationDefaultAttribute> {
        public static final AnnotationDefaultMapper INSTANCE = new AnnotationDefaultMapper();

        private AnnotationDefaultMapper() {
            super(NAME_ANNOTATION_DEFAULT, AttributeStability.CP_REFS);
        }

        @Override
        public AnnotationDefaultAttribute readAttribute(AttributedElement e, ClassReader cf, int p) {
            return new BoundAttribute.BoundAnnotationDefaultAttr(cf, this, p);
        }

        @Override
        protected void writeBody(BufWriter buf, AnnotationDefaultAttribute attr) {
            AnnotationReader.writeAnnotationValue((BufWriterImpl) buf, attr.defaultValue());
        }
    }

    public static final class BootstrapMethodsMapper extends AbstractAttributeMapper<BootstrapMethodsAttribute> {
        public static final BootstrapMethodsMapper INSTANCE = new BootstrapMethodsMapper();

        private BootstrapMethodsMapper() {
            super(NAME_BOOTSTRAP_METHODS, AttributeStability.CP_REFS);
        }

        @Override
        public BootstrapMethodsAttribute readAttribute(AttributedElement e, ClassReader cf, int p) {
            return new BoundAttribute.BoundBootstrapMethodsAttribute(cf, this, p);
        }

        @Override
        protected void writeBody(BufWriter buf, BootstrapMethodsAttribute attr) {
            var b = (BufWriterImpl) buf;
            b.writeU2(attr.bootstrapMethodsSize());
            for (var bsm : attr.bootstrapMethods()) {
                ((BootstrapMethodEntryImpl) bsm).writeTo(b);
            }
        }
    }

    public static final class CharacterRangeTableMapper extends AbstractAttributeMapper<CharacterRangeTableAttribute> {
        public static final CharacterRangeTableMapper INSTANCE = new CharacterRangeTableMapper();

        private CharacterRangeTableMapper() {
            super(NAME_CHARACTER_RANGE_TABLE, AttributeStability.LABELS, true);
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
    }

    public static final class CodeMapper extends AbstractAttributeMapper<CodeAttribute> {
        public static final CodeMapper INSTANCE = new CodeMapper();

        private CodeMapper() {
            super(NAME_CODE, AttributeStability.CP_REFS);
        }

        @Override
        public CodeAttribute readAttribute(AttributedElement e, ClassReader cf, int p) {
            return new CodeImpl(e, cf, this, p);
        }

        @Override
        protected void writeBody(BufWriter buf, CodeAttribute attr) {
            throw new UnsupportedOperationException("Code attribute does not support direct write");
        }
    }

    public static final class CompilationIDMapper extends AbstractAttributeMapper<CompilationIDAttribute> {
        public static final CompilationIDMapper INSTANCE = new CompilationIDMapper();

        private CompilationIDMapper() {
            super(NAME_COMPILATION_ID, AttributeStability.CP_REFS);
        }

        @Override
        public CompilationIDAttribute readAttribute(AttributedElement e, ClassReader cf, int p) {
            return new BoundAttribute.BoundCompilationIDAttribute(cf, this, p);
        }

        @Override
        protected void writeBody(BufWriter buf, CompilationIDAttribute attr) {
            buf.writeIndex(attr.compilationId());
        }
    }

    public static final class ConstantValueMapper extends AbstractAttributeMapper<ConstantValueAttribute> {
        public static final ConstantValueMapper INSTANCE = new ConstantValueMapper();

        private ConstantValueMapper() {
            super(NAME_CONSTANT_VALUE, AttributeStability.CP_REFS);
        }

        @Override
        public ConstantValueAttribute readAttribute(AttributedElement e, ClassReader cf, int p) {
            return new BoundAttribute.BoundConstantValueAttribute(cf, this, p);
        }

        @Override
        protected void writeBody(BufWriter buf, ConstantValueAttribute attr) {
            buf.writeIndex(attr.constant());
        }
    }

    public static final class DeprecatedMapper extends AbstractAttributeMapper<DeprecatedAttribute> {
        public static final DeprecatedMapper INSTANCE = new DeprecatedMapper();

        private DeprecatedMapper() {
            super(NAME_DEPRECATED, AttributeStability.STATELESS, true);
        }

        @Override
        public DeprecatedAttribute readAttribute(AttributedElement e, ClassReader cf, int p) {
            return new BoundAttribute.BoundDeprecatedAttribute(cf, this, p);
        }

        @Override
        protected void writeBody(BufWriter buf, DeprecatedAttribute attr) {
            // empty
        }
    }

    public static final class EnclosingMethodMapper extends AbstractAttributeMapper<EnclosingMethodAttribute> {
        public static final EnclosingMethodMapper INSTANCE = new EnclosingMethodMapper();

        private EnclosingMethodMapper() {
            super(NAME_ENCLOSING_METHOD, AttributeStability.CP_REFS);
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
    }

    public static final class ExceptionsMapper extends AbstractAttributeMapper<ExceptionsAttribute> {
        public static final ExceptionsMapper INSTANCE = new ExceptionsMapper();

        private ExceptionsMapper() {
            super(NAME_EXCEPTIONS, AttributeStability.CP_REFS);
        }

        @Override
        public ExceptionsAttribute readAttribute(AttributedElement e, ClassReader cf, int p) {
            return new BoundAttribute.BoundExceptionsAttribute(cf, this, p);
        }

        @Override
        protected void writeBody(BufWriter buf, ExceptionsAttribute attr) {
            Util.writeListIndices(buf, attr.exceptions());
        }
    }

    public static final class InnerClassesMapper extends AbstractAttributeMapper<InnerClassesAttribute> {
        public static final InnerClassesMapper INSTANCE = new InnerClassesMapper();

        private InnerClassesMapper() {
            super(NAME_INNER_CLASSES, AttributeStability.CP_REFS);
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
    }

    public static final class LineNumberTableMapper extends AbstractAttributeMapper<LineNumberTableAttribute> {
        public static final LineNumberTableMapper INSTANCE = new LineNumberTableMapper();

        private LineNumberTableMapper() {
            super(NAME_LINE_NUMBER_TABLE, AttributeStability.LABELS, true);
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
    }

    public static final class LocalVariableTableMapper extends AbstractAttributeMapper<LocalVariableTableAttribute> {
        public static final LocalVariableTableMapper INSTANCE = new LocalVariableTableMapper();

        private LocalVariableTableMapper() {
            super(NAME_LOCAL_VARIABLE_TABLE, AttributeStability.LABELS, true);
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
    }

    public static final class LocalVariableTypeTableMapper extends AbstractAttributeMapper<LocalVariableTypeTableAttribute> {
        public static final LocalVariableTypeTableMapper INSTANCE = new LocalVariableTypeTableMapper();

        private LocalVariableTypeTableMapper() {
            super(NAME_LOCAL_VARIABLE_TYPE_TABLE, AttributeStability.LABELS, true);
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
    }

    public static final class MethodParametersMapper extends AbstractAttributeMapper<MethodParametersAttribute> {
        public static final MethodParametersMapper INSTANCE = new MethodParametersMapper();

        private MethodParametersMapper() {
            super(NAME_METHOD_PARAMETERS, AttributeStability.CP_REFS);
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
    }

    public static final class ModuleMapper extends AbstractAttributeMapper<ModuleAttribute> {
        public static final ModuleMapper INSTANCE = new ModuleMapper();

        private ModuleMapper() {
            super(NAME_MODULE, AttributeStability.CP_REFS);
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
                Util.writeListIndices(buf, export.exportsTo());
            }
            buf.writeU2(attr.opens().size());
            for (ModuleOpenInfo open : attr.opens()) {
                buf.writeIndex(open.openedPackage());
                buf.writeU2(open.opensFlagsMask());
                Util.writeListIndices(buf, open.opensTo());
            }
            Util.writeListIndices(buf, attr.uses());
            buf.writeU2(attr.provides().size());
            for (ModuleProvideInfo provide : attr.provides()) {
                buf.writeIndex(provide.provides());
                Util.writeListIndices(buf, provide.providesWith());
            }
        }
    }

    public static final class ModuleHashesMapper extends AbstractAttributeMapper<ModuleHashesAttribute> {
        public static final ModuleHashesMapper INSTANCE = new ModuleHashesMapper();

        private ModuleHashesMapper() {
            super(NAME_MODULE_HASHES, AttributeStability.CP_REFS);
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
    }

    public static final class ModuleMainClassMapper extends AbstractAttributeMapper<ModuleMainClassAttribute> {
        public static final ModuleMainClassMapper INSTANCE = new ModuleMainClassMapper();

        private ModuleMainClassMapper() {
            super(NAME_MODULE_MAIN_CLASS, AttributeStability.CP_REFS);
        }

        @Override
        public ModuleMainClassAttribute readAttribute(AttributedElement e, ClassReader cf, int p) {
            return new BoundAttribute.BoundModuleMainClassAttribute(cf, this, p);
        }

        @Override
        protected void writeBody(BufWriter buf, ModuleMainClassAttribute attr) {
            buf.writeIndex(attr.mainClass());
        }
    }

    public static final class ModulePackagesMapper extends AbstractAttributeMapper<ModulePackagesAttribute> {
        public static final ModulePackagesMapper INSTANCE = new ModulePackagesMapper();

        private ModulePackagesMapper() {
            super(NAME_MODULE_PACKAGES, AttributeStability.CP_REFS);
        }

        @Override
        public ModulePackagesAttribute readAttribute(AttributedElement e, ClassReader cf, int p) {
            return new BoundAttribute.BoundModulePackagesAttribute(cf, this, p);
        }

        @Override
        protected void writeBody(BufWriter buf, ModulePackagesAttribute attr) {
            Util.writeListIndices(buf, attr.packages());
        }
    }

    public static final class ModuleResolutionMapper extends AbstractAttributeMapper<ModuleResolutionAttribute> {
        public static final ModuleResolutionMapper INSTANCE = new ModuleResolutionMapper();

        private ModuleResolutionMapper() {
            super(NAME_MODULE_RESOLUTION, AttributeStability.STATELESS);
        }

        @Override
        public ModuleResolutionAttribute readAttribute(AttributedElement e, ClassReader cf, int p) {
            return new BoundAttribute.BoundModuleResolutionAttribute(cf, this, p);
        }

        @Override
        protected void writeBody(BufWriter buf, ModuleResolutionAttribute attr) {
            buf.writeU2(attr.resolutionFlags());
        }
    }

    public static final class ModuleTargetMapper extends AbstractAttributeMapper<ModuleTargetAttribute> {
        public static final ModuleTargetMapper INSTANCE = new ModuleTargetMapper();

        private ModuleTargetMapper() {
            super(NAME_MODULE_TARGET, AttributeStability.CP_REFS);
        }

        @Override
        public ModuleTargetAttribute readAttribute(AttributedElement e, ClassReader cf, int p) {
            return new BoundAttribute.BoundModuleTargetAttribute(cf, this, p);
        }

        @Override
        protected void writeBody(BufWriter buf, ModuleTargetAttribute attr) {
            buf.writeIndex(attr.targetPlatform());
        }
    }

    public static final class NestHostMapper extends AbstractAttributeMapper<NestHostAttribute> {
        public static final NestHostMapper INSTANCE = new NestHostMapper();

        private NestHostMapper() {
            super(NAME_NEST_HOST, AttributeStability.CP_REFS);
        }

        @Override
        public NestHostAttribute readAttribute(AttributedElement e, ClassReader cf, int p) {
            return new BoundAttribute.BoundNestHostAttribute(cf, this, p);
        }

        @Override
        protected void writeBody(BufWriter buf, NestHostAttribute attr) {
            buf.writeIndex(attr.nestHost());
        }
    }

    public static final class NestMembersMapper extends AbstractAttributeMapper<NestMembersAttribute> {
        public static final NestMembersMapper INSTANCE = new NestMembersMapper();

        private NestMembersMapper() {
            super(NAME_NEST_MEMBERS, AttributeStability.CP_REFS);
        }

        @Override
        public NestMembersAttribute readAttribute(AttributedElement e, ClassReader cf, int p) {
            return new BoundAttribute.BoundNestMembersAttribute(cf, this, p);
        }

        @Override
        protected void writeBody(BufWriter buf, NestMembersAttribute attr) {
            Util.writeListIndices(buf, attr.nestMembers());
        }
    }

    public static final class PermittedSubclassesMapper extends AbstractAttributeMapper<PermittedSubclassesAttribute> {
        public static final PermittedSubclassesMapper INSTANCE = new PermittedSubclassesMapper();

        private PermittedSubclassesMapper() {
            super(NAME_PERMITTED_SUBCLASSES, AttributeStability.CP_REFS);
        }

        @Override
        public PermittedSubclassesAttribute readAttribute(AttributedElement e, ClassReader cf, int p) {
            return new BoundAttribute.BoundPermittedSubclassesAttribute(cf, this, p);
        }

        @Override
        protected void writeBody(BufWriter buf, PermittedSubclassesAttribute attr) {
            Util.writeListIndices(buf, attr.permittedSubclasses());
        }
    }

    public static final class RecordMapper extends AbstractAttributeMapper<RecordAttribute> {
        public static final RecordMapper INSTANCE = new RecordMapper();

        private RecordMapper() {
            super(NAME_RECORD, AttributeStability.CP_REFS);
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
                Util.writeAttributes((BufWriterImpl) buf, info.attributes());
            }
        }
    }

    public static final class RuntimeInvisibleAnnotationsMapper extends AbstractAttributeMapper<RuntimeInvisibleAnnotationsAttribute> {
        public static final RuntimeInvisibleAnnotationsMapper INSTANCE = new RuntimeInvisibleAnnotationsMapper();

        private RuntimeInvisibleAnnotationsMapper() {
            super(NAME_RUNTIME_INVISIBLE_ANNOTATIONS, AttributeStability.CP_REFS);
        }

        @Override
        public RuntimeInvisibleAnnotationsAttribute readAttribute(AttributedElement enclosing, ClassReader cf, int pos) {
            return new BoundAttribute.BoundRuntimeInvisibleAnnotationsAttribute(cf, pos);
        }

        @Override
        protected void writeBody(BufWriter buf, RuntimeInvisibleAnnotationsAttribute attr) {
            AnnotationReader.writeAnnotations(buf, attr.annotations());
        }
    }

    public static final class RuntimeInvisibleParameterAnnotationsMapper extends AbstractAttributeMapper<RuntimeInvisibleParameterAnnotationsAttribute> {
        public static final RuntimeInvisibleParameterAnnotationsMapper INSTANCE = new RuntimeInvisibleParameterAnnotationsMapper();

        private RuntimeInvisibleParameterAnnotationsMapper() {
            super(NAME_RUNTIME_INVISIBLE_PARAMETER_ANNOTATIONS, AttributeStability.CP_REFS);
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
                AnnotationReader.writeAnnotations(buf, list);
        }
    }

    public static final class RuntimeInvisibleTypeAnnotationsMapper extends AbstractAttributeMapper<RuntimeInvisibleTypeAnnotationsAttribute> {
        public static final RuntimeInvisibleTypeAnnotationsMapper INSTANCE = new RuntimeInvisibleTypeAnnotationsMapper();

        private RuntimeInvisibleTypeAnnotationsMapper() {
            super(NAME_RUNTIME_INVISIBLE_TYPE_ANNOTATIONS, AttributeStability.UNSTABLE);
        }

        @Override
        public RuntimeInvisibleTypeAnnotationsAttribute readAttribute(AttributedElement e, ClassReader cf, int p) {
            return new BoundAttribute.BoundRuntimeInvisibleTypeAnnotationsAttribute(e, cf, this, p);
        }

        @Override
        protected void writeBody(BufWriter buf, RuntimeInvisibleTypeAnnotationsAttribute attr) {
            AnnotationReader.writeAnnotations(buf, attr.annotations());
        }
    }

    public static final class RuntimeVisibleAnnotationsMapper extends AbstractAttributeMapper<RuntimeVisibleAnnotationsAttribute> {
        public static final RuntimeVisibleAnnotationsMapper INSTANCE = new RuntimeVisibleAnnotationsMapper();

        private RuntimeVisibleAnnotationsMapper() {
            super(NAME_RUNTIME_VISIBLE_ANNOTATIONS, AttributeStability.CP_REFS);
        }

        @Override
        public RuntimeVisibleAnnotationsAttribute readAttribute(AttributedElement enclosing, ClassReader cf, int pos) {
            return new BoundAttribute.BoundRuntimeVisibleAnnotationsAttribute(cf, pos);
        }

        @Override
        protected void writeBody(BufWriter buf, RuntimeVisibleAnnotationsAttribute attr) {
            AnnotationReader.writeAnnotations(buf, attr.annotations());
        }
    }

    public static final class RuntimeVisibleParameterAnnotationsMapper extends AbstractAttributeMapper<RuntimeVisibleParameterAnnotationsAttribute> {
        public static final RuntimeVisibleParameterAnnotationsMapper INSTANCE = new RuntimeVisibleParameterAnnotationsMapper();

        private RuntimeVisibleParameterAnnotationsMapper() {
            super(NAME_RUNTIME_VISIBLE_PARAMETER_ANNOTATIONS, AttributeStability.CP_REFS);
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
                AnnotationReader.writeAnnotations(buf, list);
        }
    }

    public static final class RuntimeVisibleTypeAnnotationsMapper extends AbstractAttributeMapper<RuntimeVisibleTypeAnnotationsAttribute> {
        public static final RuntimeVisibleTypeAnnotationsMapper INSTANCE = new RuntimeVisibleTypeAnnotationsMapper();

        private RuntimeVisibleTypeAnnotationsMapper() {
            super(NAME_RUNTIME_VISIBLE_TYPE_ANNOTATIONS, AttributeStability.UNSTABLE);
        }

        @Override
        public RuntimeVisibleTypeAnnotationsAttribute readAttribute(AttributedElement e, ClassReader cf, int p) {
            return new BoundAttribute.BoundRuntimeVisibleTypeAnnotationsAttribute(e, cf, this, p);
        }

        @Override
        protected void writeBody(BufWriter buf, RuntimeVisibleTypeAnnotationsAttribute attr) {
            AnnotationReader.writeAnnotations(buf, attr.annotations());
        }
    }

    public static final class SignatureMapper extends AbstractAttributeMapper<SignatureAttribute> {
        public static final SignatureMapper INSTANCE = new SignatureMapper();

        private SignatureMapper() {
            super(NAME_SIGNATURE, AttributeStability.CP_REFS);
        }

        @Override
        public SignatureAttribute readAttribute(AttributedElement e, ClassReader cf, int p) {
            return new BoundAttribute.BoundSignatureAttribute(cf, this, p);
        }

        @Override
        protected void writeBody(BufWriter buf, SignatureAttribute attr) {
            buf.writeIndex(attr.signature());
        }
    }

    public static final class SourceDebugExtensionMapper extends AbstractAttributeMapper<SourceDebugExtensionAttribute> {
        public static final SourceDebugExtensionMapper INSTANCE = new SourceDebugExtensionMapper();

        private SourceDebugExtensionMapper() {
            super(NAME_SOURCE_DEBUG_EXTENSION, AttributeStability.STATELESS);
        }

        @Override
        public SourceDebugExtensionAttribute readAttribute(AttributedElement e, ClassReader cf, int p) {
            return new BoundAttribute.BoundSourceDebugExtensionAttribute(cf, this, p);
        }

        @Override
        protected void writeBody(BufWriter buf, SourceDebugExtensionAttribute attr) {
            buf.writeBytes(attr.contents());
        }
    }

    public static final class SourceFileMapper extends AbstractAttributeMapper<SourceFileAttribute> {
        public static final SourceFileMapper INSTANCE = new SourceFileMapper();

        private SourceFileMapper() {
            super(NAME_SOURCE_FILE, AttributeStability.CP_REFS);
        }

        @Override
        public SourceFileAttribute readAttribute(AttributedElement e, ClassReader cf, int p) {
            return new BoundAttribute.BoundSourceFileAttribute(cf, this, p);
        }

        @Override
        protected void writeBody(BufWriter buf, SourceFileAttribute attr) {
            buf.writeIndex(attr.sourceFile());
        }
    }

    public static final class SourceIDMapper extends AbstractAttributeMapper<SourceIDAttribute> {
        public static final SourceIDMapper INSTANCE = new SourceIDMapper();

        private SourceIDMapper() {
            super(NAME_SOURCE_ID, AttributeStability.CP_REFS);
        }

        @Override
        public SourceIDAttribute readAttribute(AttributedElement e, ClassReader cf, int p) {
            return new BoundAttribute.BoundSourceIDAttribute(cf, this, p);
        }

        @Override
        protected void writeBody(BufWriter buf, SourceIDAttribute attr) {
            buf.writeIndex(attr.sourceId());
        }
    }

    public static final class StackMapTableMapper extends AbstractAttributeMapper<StackMapTableAttribute> {
        public static final StackMapTableMapper INSTANCE = new StackMapTableMapper();

        private StackMapTableMapper() {
            super(NAME_STACK_MAP_TABLE, AttributeStability.LABELS);
        }

        @Override
        public StackMapTableAttribute readAttribute(AttributedElement e, ClassReader cf, int p) {
            return new BoundAttribute.BoundStackMapTableAttribute((CodeImpl)e, cf, this, p);
        }

        @Override
        protected void writeBody(BufWriter b, StackMapTableAttribute attr) {
            StackMapDecoder.writeFrames(b, attr.entries());
        }
    }

    public static final class SyntheticMapper extends AbstractAttributeMapper<SyntheticAttribute> {
        public static final SyntheticMapper INSTANCE = new SyntheticMapper();

        private SyntheticMapper() {
            super(NAME_SYNTHETIC, AttributeStability.STATELESS, true);
        }

        @Override
        public SyntheticAttribute readAttribute(AttributedElement e, ClassReader cf, int p) {
            return new BoundAttribute.BoundSyntheticAttribute(cf, this, p);
        }

        @Override
        protected void writeBody(BufWriter buf, SyntheticAttribute attr) {
            // empty
        }
    }
}
