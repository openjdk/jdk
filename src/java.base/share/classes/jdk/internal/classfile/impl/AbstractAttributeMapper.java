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
import java.lang.classfile.constantpool.Utf8Entry;
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
    public final void writeAttribute(BufWriter writer, T attr) {
        BufWriterImpl buf = (BufWriterImpl) writer;
        buf.writeIndex(buf.constantPool().utf8Entry(name));
        int lengthIndex = buf.skip(4);
        writeBody(buf, attr);
        int written = buf.size() - lengthIndex - 4;
        buf.patchInt(lengthIndex, written);
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
        public AnnotationDefaultAttribute readAttribute(Utf8Entry name, AttributedElement e, ClassReader cf, int p) {
            return new BoundAttribute.BoundAnnotationDefaultAttr(name, cf, this, p);
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
        public BootstrapMethodsAttribute readAttribute(Utf8Entry name, AttributedElement e, ClassReader cf, int p) {
            return new BoundAttribute.BoundBootstrapMethodsAttribute(name, cf, this, p);
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
        public CharacterRangeTableAttribute readAttribute(Utf8Entry name, AttributedElement e, ClassReader cf, int p) {
            return new BoundAttribute.BoundCharacterRangeTableAttribute(name, cf, this, p);
        }

        @Override
        protected void writeBody(BufWriter bufWriter, CharacterRangeTableAttribute attr) {
            List<CharacterRangeInfo> ranges = attr.characterRangeTable();
            BufWriterImpl buf = (BufWriterImpl) bufWriter;
            buf.writeU2(ranges.size());
            for (CharacterRangeInfo info : ranges) {
                buf.writeU2U2(info.startPc(), info.endPc());
                buf.writeIntInt(info.characterRangeStart(), info.characterRangeEnd());
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
        public CodeAttribute readAttribute(Utf8Entry name, AttributedElement e, ClassReader cf, int p) {
            return new CodeImpl(name, e, cf, this, p);
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
        public CompilationIDAttribute readAttribute(Utf8Entry name, AttributedElement e, ClassReader cf, int p) {
            return new BoundAttribute.BoundCompilationIDAttribute(name, cf, this, p);
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
        public ConstantValueAttribute readAttribute(Utf8Entry name, AttributedElement e, ClassReader cf, int p) {
            return new BoundAttribute.BoundConstantValueAttribute(name, cf, this, p);
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
        public DeprecatedAttribute readAttribute(Utf8Entry name, AttributedElement e, ClassReader cf, int p) {
            return new BoundAttribute.BoundDeprecatedAttribute(name, cf, this, p);
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
        public EnclosingMethodAttribute readAttribute(Utf8Entry name, AttributedElement e, ClassReader cf, int p) {
            return new BoundAttribute.BoundEnclosingMethodAttribute(name, cf, this, p);
        }

        @Override
        protected void writeBody(BufWriter bufWriter, EnclosingMethodAttribute attr) {
            BufWriterImpl buf = (BufWriterImpl) bufWriter;
            buf.writeU2U2(buf.cpIndex(attr.enclosingClass()),
                    buf.cpIndexOrZero(attr.enclosingMethod().orElse(null)));
        }
    }

    public static final class ExceptionsMapper extends AbstractAttributeMapper<ExceptionsAttribute> {
        public static final ExceptionsMapper INSTANCE = new ExceptionsMapper();

        private ExceptionsMapper() {
            super(NAME_EXCEPTIONS, AttributeStability.CP_REFS);
        }

        @Override
        public ExceptionsAttribute readAttribute(Utf8Entry name, AttributedElement e, ClassReader cf, int p) {
            return new BoundAttribute.BoundExceptionsAttribute(name, cf, this, p);
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
        public InnerClassesAttribute readAttribute(Utf8Entry name, AttributedElement e, ClassReader cf, int p) {
            return new BoundAttribute.BoundInnerClassesAttribute(name, cf, this, p);
        }

        @Override
        protected void writeBody(BufWriter bufWriter, InnerClassesAttribute attr) {
            List<InnerClassInfo> classes = attr.classes();
            BufWriterImpl buf = (BufWriterImpl) bufWriter;
            buf.writeU2(classes.size());
            for (InnerClassInfo ic : classes) {
                buf.writeU2U2U2(buf.cpIndex(ic.innerClass()),
                        buf.cpIndexOrZero(ic.outerClass().orElse(null)),
                        buf.cpIndexOrZero(ic.innerName().orElse(null)));
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
        public LineNumberTableAttribute readAttribute(Utf8Entry name, AttributedElement e, ClassReader cf, int p) {
            return new BoundAttribute.BoundLineNumberTableAttribute(name, cf, this, p);
        }

        @Override
        protected void writeBody(BufWriter bufWriter, LineNumberTableAttribute attr) {
            List<LineNumberInfo> lines = attr.lineNumbers();
            BufWriterImpl buf = (BufWriterImpl) bufWriter;
            buf.writeU2(lines.size());
            for (LineNumberInfo line : lines) {
                buf.writeU2U2(line.startPc(), line.lineNumber());
            }
        }
    }

    public static final class LocalVariableTableMapper extends AbstractAttributeMapper<LocalVariableTableAttribute> {
        public static final LocalVariableTableMapper INSTANCE = new LocalVariableTableMapper();

        private LocalVariableTableMapper() {
            super(NAME_LOCAL_VARIABLE_TABLE, AttributeStability.LABELS, true);
        }

        @Override
        public LocalVariableTableAttribute readAttribute(Utf8Entry name, AttributedElement e, ClassReader cf, int p) {
            return new BoundAttribute.BoundLocalVariableTableAttribute(name, e, cf, this, p);
        }

        @Override
        protected void writeBody(BufWriter bufWriter, LocalVariableTableAttribute attr) {
            List<LocalVariableInfo> infos = attr.localVariables();
            BufWriterImpl buf = (BufWriterImpl) bufWriter;
            buf.writeU2(infos.size());
            for (LocalVariableInfo info : infos) {
                buf.writeU2U2(info.startPc(), info.length());
                buf.writeU2U2U2(buf.cpIndex(info.name()), buf.cpIndex(info.type()), info.slot());
            }
        }
    }

    public static final class LocalVariableTypeTableMapper extends AbstractAttributeMapper<LocalVariableTypeTableAttribute> {
        public static final LocalVariableTypeTableMapper INSTANCE = new LocalVariableTypeTableMapper();

        private LocalVariableTypeTableMapper() {
            super(NAME_LOCAL_VARIABLE_TYPE_TABLE, AttributeStability.LABELS, true);
        }

        @Override
        public LocalVariableTypeTableAttribute readAttribute(Utf8Entry name, AttributedElement e, ClassReader cf, int p) {
            return new BoundAttribute.BoundLocalVariableTypeTableAttribute(name, e, cf, this, p);
        }

        @Override
        protected void writeBody(BufWriter bufWriter, LocalVariableTypeTableAttribute attr) {
            List<LocalVariableTypeInfo> infos = attr.localVariableTypes();
            BufWriterImpl buf = (BufWriterImpl) bufWriter;
            buf.writeU2(infos.size());
            for (LocalVariableTypeInfo info : infos) {
                buf.writeU2U2(info.startPc(), info.length());
                buf.writeU2U2U2(buf.cpIndex(info.name()), buf.cpIndex(info.signature()), info.slot());
            }
        }
    }

    public static final class MethodParametersMapper extends AbstractAttributeMapper<MethodParametersAttribute> {
        public static final MethodParametersMapper INSTANCE = new MethodParametersMapper();

        private MethodParametersMapper() {
            super(NAME_METHOD_PARAMETERS, AttributeStability.CP_REFS);
        }

        @Override
        public MethodParametersAttribute readAttribute(Utf8Entry name, AttributedElement e, ClassReader cf, int p) {
            return new BoundAttribute.BoundMethodParametersAttribute(name, cf, this, p);
        }

        @Override
        protected void writeBody(BufWriter bufWriter, MethodParametersAttribute attr) {
            List<MethodParameterInfo> parameters = attr.parameters();
            BufWriterImpl buf = (BufWriterImpl) bufWriter;
            buf.writeU1(parameters.size());
            for (MethodParameterInfo info : parameters) {
                buf.writeU2U2(buf.cpIndexOrZero(info.name().orElse(null)),
                        info.flagsMask());
            }
        }
    }

    public static final class ModuleMapper extends AbstractAttributeMapper<ModuleAttribute> {
        public static final ModuleMapper INSTANCE = new ModuleMapper();

        private ModuleMapper() {
            super(NAME_MODULE, AttributeStability.CP_REFS);
        }

        @Override
        public ModuleAttribute readAttribute(Utf8Entry name, AttributedElement e, ClassReader cf, int p) {
            return new BoundAttribute.BoundModuleAttribute(name, cf, this, p);
        }

        @Override
        protected void writeBody(BufWriter bufWriter, ModuleAttribute attr) {
            BufWriterImpl buf = (BufWriterImpl) bufWriter;
            buf.writeU2U2U2(buf.cpIndex(attr.moduleName()),
                    attr.moduleFlagsMask(),
                    buf.cpIndexOrZero(attr.moduleVersion().orElse(null)));
            buf.writeU2(attr.requires().size());
            for (ModuleRequireInfo require : attr.requires()) {
                buf.writeU2U2U2(buf.cpIndex(require.requires()),
                        require.requiresFlagsMask(),
                        buf.cpIndexOrZero(require.requiresVersion().orElse(null)));
            }
            buf.writeU2(attr.exports().size());
            for (ModuleExportInfo export : attr.exports()) {
                buf.writeU2U2(buf.cpIndex(export.exportedPackage()),
                        export.exportsFlagsMask());
                Util.writeListIndices(buf, export.exportsTo());
            }
            buf.writeU2(attr.opens().size());
            for (ModuleOpenInfo open : attr.opens()) {
                buf.writeU2U2(buf.cpIndex(open.openedPackage()),
                        open.opensFlagsMask());
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
        public ModuleHashesAttribute readAttribute(Utf8Entry name, AttributedElement e, ClassReader cf, int p) {
            return new BoundAttribute.BoundModuleHashesAttribute(name, cf, this, p);
        }

        @Override
        protected void writeBody(BufWriter bufWriter, ModuleHashesAttribute attr) {
            List<ModuleHashInfo> hashes = attr.hashes();
            BufWriterImpl buf = (BufWriterImpl) bufWriter;
            buf.writeU2U2(buf.cpIndex(attr.algorithm()), hashes.size());
            for (ModuleHashInfo hash : hashes) {
                buf.writeU2U2(buf.cpIndex(hash.moduleName()),
                        hash.hash().length);
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
        public ModuleMainClassAttribute readAttribute(Utf8Entry name, AttributedElement e, ClassReader cf, int p) {
            return new BoundAttribute.BoundModuleMainClassAttribute(name, cf, this, p);
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
        public ModulePackagesAttribute readAttribute(Utf8Entry name, AttributedElement e, ClassReader cf, int p) {
            return new BoundAttribute.BoundModulePackagesAttribute(name, cf, this, p);
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
        public ModuleResolutionAttribute readAttribute(Utf8Entry name, AttributedElement e, ClassReader cf, int p) {
            return new BoundAttribute.BoundModuleResolutionAttribute(name, cf, this, p);
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
        public ModuleTargetAttribute readAttribute(Utf8Entry name, AttributedElement e, ClassReader cf, int p) {
            return new BoundAttribute.BoundModuleTargetAttribute(name, cf, this, p);
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
        public NestHostAttribute readAttribute(Utf8Entry name, AttributedElement e, ClassReader cf, int p) {
            return new BoundAttribute.BoundNestHostAttribute(name, cf, this, p);
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
        public NestMembersAttribute readAttribute(Utf8Entry name, AttributedElement e, ClassReader cf, int p) {
            return new BoundAttribute.BoundNestMembersAttribute(name, cf, this, p);
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
        public PermittedSubclassesAttribute readAttribute(Utf8Entry name, AttributedElement e, ClassReader cf, int p) {
            return new BoundAttribute.BoundPermittedSubclassesAttribute(name, cf, this, p);
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
        public RecordAttribute readAttribute(Utf8Entry name, AttributedElement e, ClassReader cf, int p) {
            return new BoundAttribute.BoundRecordAttribute(name, cf, this, p);
        }

        @Override
        protected void writeBody(BufWriter bufWriter, RecordAttribute attr) {
            List<RecordComponentInfo> components = attr.components();
            BufWriterImpl buf = (BufWriterImpl) bufWriter;
            buf.writeU2(components.size());
            for (RecordComponentInfo info : components) {
                buf.writeU2U2(buf.cpIndex(info.name()),
                        buf.cpIndex(info.descriptor()));
                Util.writeAttributes(buf, info.attributes());
            }
        }
    }

    public static final class RuntimeInvisibleAnnotationsMapper extends AbstractAttributeMapper<RuntimeInvisibleAnnotationsAttribute> {
        public static final RuntimeInvisibleAnnotationsMapper INSTANCE = new RuntimeInvisibleAnnotationsMapper();

        private RuntimeInvisibleAnnotationsMapper() {
            super(NAME_RUNTIME_INVISIBLE_ANNOTATIONS, AttributeStability.CP_REFS);
        }

        @Override
        public RuntimeInvisibleAnnotationsAttribute readAttribute(Utf8Entry name, AttributedElement enclosing, ClassReader cf, int pos) {
            return new BoundAttribute.BoundRuntimeInvisibleAnnotationsAttribute(name, cf, pos);
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
        public RuntimeInvisibleParameterAnnotationsAttribute readAttribute(Utf8Entry name, AttributedElement e, ClassReader cf, int p) {
            return new BoundAttribute.BoundRuntimeInvisibleParameterAnnotationsAttribute(name, cf, this, p);
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
        public RuntimeInvisibleTypeAnnotationsAttribute readAttribute(Utf8Entry name, AttributedElement e, ClassReader cf, int p) {
            return new BoundAttribute.BoundRuntimeInvisibleTypeAnnotationsAttribute(name, e, cf, this, p);
        }

        @Override
        protected void writeBody(BufWriter buf, RuntimeInvisibleTypeAnnotationsAttribute attr) {
            AnnotationReader.writeTypeAnnotations(buf, attr.annotations());
        }
    }

    public static final class RuntimeVisibleAnnotationsMapper extends AbstractAttributeMapper<RuntimeVisibleAnnotationsAttribute> {
        public static final RuntimeVisibleAnnotationsMapper INSTANCE = new RuntimeVisibleAnnotationsMapper();

        private RuntimeVisibleAnnotationsMapper() {
            super(NAME_RUNTIME_VISIBLE_ANNOTATIONS, AttributeStability.CP_REFS);
        }

        @Override
        public RuntimeVisibleAnnotationsAttribute readAttribute(Utf8Entry name, AttributedElement enclosing, ClassReader cf, int pos) {
            return new BoundAttribute.BoundRuntimeVisibleAnnotationsAttribute(name, cf, pos);
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
        public RuntimeVisibleParameterAnnotationsAttribute readAttribute(Utf8Entry name, AttributedElement e, ClassReader cf, int p) {
            return new BoundAttribute.BoundRuntimeVisibleParameterAnnotationsAttribute(name, cf, this, p);
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
        public RuntimeVisibleTypeAnnotationsAttribute readAttribute(Utf8Entry name, AttributedElement e, ClassReader cf, int p) {
            return new BoundAttribute.BoundRuntimeVisibleTypeAnnotationsAttribute(name, e, cf, this, p);
        }

        @Override
        protected void writeBody(BufWriter buf, RuntimeVisibleTypeAnnotationsAttribute attr) {
            AnnotationReader.writeTypeAnnotations(buf, attr.annotations());
        }
    }

    public static final class SignatureMapper extends AbstractAttributeMapper<SignatureAttribute> {
        public static final SignatureMapper INSTANCE = new SignatureMapper();

        private SignatureMapper() {
            super(NAME_SIGNATURE, AttributeStability.CP_REFS);
        }

        @Override
        public SignatureAttribute readAttribute(Utf8Entry name, AttributedElement e, ClassReader cf, int p) {
            return new BoundAttribute.BoundSignatureAttribute(name, cf, this, p);
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
        public SourceDebugExtensionAttribute readAttribute(Utf8Entry name, AttributedElement e, ClassReader cf, int p) {
            return new BoundAttribute.BoundSourceDebugExtensionAttribute(name, cf, this, p);
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
        public SourceFileAttribute readAttribute(Utf8Entry name, AttributedElement e, ClassReader cf, int p) {
            return new BoundAttribute.BoundSourceFileAttribute(name, cf, this, p);
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
        public SourceIDAttribute readAttribute(Utf8Entry name, AttributedElement e, ClassReader cf, int p) {
            return new BoundAttribute.BoundSourceIDAttribute(name, cf, this, p);
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
        public StackMapTableAttribute readAttribute(Utf8Entry name, AttributedElement e, ClassReader cf, int p) {
            return new BoundAttribute.BoundStackMapTableAttribute(name, (CodeImpl)e, cf, this, p);
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
        public SyntheticAttribute readAttribute(Utf8Entry name, AttributedElement e, ClassReader cf, int p) {
            return new BoundAttribute.BoundSyntheticAttribute(name, cf, this, p);
        }

        @Override
        protected void writeBody(BufWriter buf, SyntheticAttribute attr) {
            // empty
        }
    }
}
