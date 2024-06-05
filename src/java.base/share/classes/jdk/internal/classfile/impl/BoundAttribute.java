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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import java.lang.classfile.*;
import java.lang.classfile.attribute.*;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.classfile.constantpool.ConstantPool;
import java.lang.classfile.constantpool.ConstantValueEntry;
import java.lang.classfile.constantpool.LoadableConstantEntry;
import java.lang.classfile.constantpool.ModuleEntry;
import java.lang.classfile.constantpool.NameAndTypeEntry;
import java.lang.classfile.constantpool.PackageEntry;
import java.lang.classfile.constantpool.Utf8Entry;
import jdk.internal.access.SharedSecrets;

import static java.lang.classfile.Attributes.*;

public abstract sealed class BoundAttribute<T extends Attribute<T>>
        extends AbstractElement
        implements Attribute<T> {

    static final int NAME_AND_LENGTH_PREFIX = 6;
    private final AttributeMapper<T> mapper;
    final ClassReaderImpl classReader;
    final int payloadStart;

    BoundAttribute(ClassReader classReader, AttributeMapper<T> mapper, int payloadStart) {
        this.mapper = mapper;
        this.classReader = (ClassReaderImpl)classReader;
        this.payloadStart = payloadStart;
    }

    public int payloadLen() {
        return classReader.readInt(payloadStart - 4);
    }

    @Override
    public String attributeName() {
        return mapper.name();
    }

    @Override
    public AttributeMapper<T> attributeMapper() {
        return mapper;
    }

    public byte[] contents() {
        return classReader.readBytes(payloadStart, payloadLen());
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
    @SuppressWarnings("unchecked")
    public void writeTo(BufWriter buf) {
        if (!buf.canWriteDirect(classReader))
            attributeMapper().writeAttribute(buf, (T) this);
        else
            classReader.copyBytesTo(buf, payloadStart - NAME_AND_LENGTH_PREFIX, payloadLen() + NAME_AND_LENGTH_PREFIX);
    }

    public ConstantPool constantPool() {
        return classReader;
    }

    @Override
    public String toString() {
        return String.format("Attribute[name=%s]", mapper.name());
    }

    <E> List<E> readEntryList(int p) {
        int cnt = classReader.readU2(p);
        p += 2;
        var entries = new Object[cnt];
        int end = p + (cnt * 2);
        for (int i = 0; p < end; i++, p += 2) {
            entries[i] = classReader.readEntry(p);
        }
        return SharedSecrets.getJavaUtilCollectionAccess().listFromTrustedArray(entries);
    }

    public static List<Attribute<?>> readAttributes(AttributedElement enclosing, ClassReader reader, int pos,
                                                                  Function<Utf8Entry, AttributeMapper<?>> customAttributes) {
        int size = reader.readU2(pos);
        var filled = new ArrayList<Attribute<?>>(size);
        int p = pos + 2;
        int cfLen = reader.classfileLength();
        var apo = ((ClassReaderImpl)reader).context().attributesProcessingOption();
        for (int i = 0; i < size; ++i) {
            Utf8Entry name = reader.readUtf8Entry(p);
            int len = reader.readInt(p + 2);
            p += 6;
            if (len < 0 || len > cfLen - p) {
                throw new IllegalArgumentException("attribute " + name.stringValue() + " too big to handle");
            }

            var mapper = standardAttribute(name);
            if (mapper == null) {
                mapper = customAttributes.apply(name);
            }
            if (mapper != null) {
                filled.add((Attribute)mapper.readAttribute(enclosing, reader, p));
            } else {
                AttributeMapper<UnknownAttribute> fakeMapper = new AttributeMapper<>() {
                    @Override
                    public String name() {
                        return name.stringValue();
                    }

                    @Override
                    public UnknownAttribute readAttribute(AttributedElement enclosing, ClassReader cf, int pos) {
                        // Will never get called
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public void writeAttribute(BufWriter buf, UnknownAttribute attr) {
                        buf.writeIndex(name);
                        var cont = attr.contents();
                        buf.writeInt(cont.length);
                        buf.writeBytes(cont);
                    }

                    @Override
                    public boolean allowMultiple() {
                        return true;
                    }

                    @Override
                    public AttributeMapper.AttributeStability stability() {
                        return AttributeStability.UNKNOWN;
                    }
                };
                filled.add(new BoundUnknownAttribute(reader, fakeMapper, p));
            }
            p += len;
        }
        return Collections.unmodifiableList(filled);
    }

    public static final class BoundUnknownAttribute extends BoundAttribute<UnknownAttribute>
            implements UnknownAttribute {
        public BoundUnknownAttribute(ClassReader cf, AttributeMapper<UnknownAttribute> mapper, int pos) {
            super(cf, mapper, pos);
        }
    }

    public static final class BoundStackMapTableAttribute
            extends BoundAttribute<StackMapTableAttribute>
            implements StackMapTableAttribute {
        final MethodModel method;
        final LabelContext ctx;
        List<StackMapFrameInfo> entries = null;

        public BoundStackMapTableAttribute(CodeImpl code, ClassReader cf, AttributeMapper<StackMapTableAttribute> mapper, int pos) {
            super(cf, mapper, pos);
            method = code.parent().orElseThrow();
            ctx = code;
        }

        @Override
        public List<StackMapFrameInfo> entries() {
            if (entries == null) {
                entries = new StackMapDecoder(classReader, payloadStart, ctx, StackMapDecoder.initFrameLocals(method)).entries();
            }
            return entries;
        }
    }

    public static final class BoundSyntheticAttribute extends BoundAttribute<SyntheticAttribute>
            implements SyntheticAttribute {
        public BoundSyntheticAttribute(ClassReader cf, AttributeMapper<SyntheticAttribute> mapper, int pos) {
            super(cf, mapper, pos);
        }
    }

    public static final class BoundLineNumberTableAttribute
            extends BoundAttribute<LineNumberTableAttribute>
            implements LineNumberTableAttribute {
        private List<LineNumberInfo> lineNumbers = null;

        public BoundLineNumberTableAttribute(ClassReader cf, AttributeMapper<LineNumberTableAttribute> mapper, int pos) {
            super(cf, mapper, pos);
        }

        @Override
        public List<LineNumberInfo> lineNumbers() {
            if (lineNumbers == null) {
                int nLn = classReader.readU2(payloadStart);
                LineNumberInfo[] elements = new LineNumberInfo[nLn];
                int p = payloadStart + 2;
                int pEnd = p + (nLn * 4);
                for (int i = 0; p < pEnd; p += 4, i++) {
                    int startPc = classReader.readU2(p);
                    int lineNumber = classReader.readU2(p + 2);
                    elements[i] = LineNumberInfo.of(startPc, lineNumber);
                }
                lineNumbers = List.of(elements);
            }
            return lineNumbers;
        }
    }

    public static final class BoundCharacterRangeTableAttribute extends BoundAttribute<CharacterRangeTableAttribute> implements CharacterRangeTableAttribute {
        private List<CharacterRangeInfo> characterRangeTable = null;

        public BoundCharacterRangeTableAttribute(ClassReader cf, AttributeMapper<CharacterRangeTableAttribute> mapper, int pos) {
            super(cf, mapper, pos);
        }

        @Override
        public List<CharacterRangeInfo> characterRangeTable() {
            if (characterRangeTable == null) {
                int nLn = classReader.readU2(payloadStart);
                CharacterRangeInfo[] elements = new CharacterRangeInfo[nLn];
                int p = payloadStart + 2;
                int pEnd = p + (nLn * 14);
                for (int i = 0; p < pEnd; p += 14, i++) {
                    int startPc = classReader.readU2(p);
                    int endPc = classReader.readU2(p + 2);
                    int characterRangeStart = classReader.readInt(p + 4);
                    int characterRangeEnd = classReader.readInt(p + 8);
                    int flags = classReader.readU2(p + 12);
                    elements[i] = CharacterRangeInfo.of(startPc, endPc, characterRangeStart, characterRangeEnd, flags);
                }
                characterRangeTable = List.of(elements);
            }
            return characterRangeTable;
        }
    }

    public static final class BoundLocalVariableTableAttribute
            extends BoundAttribute<LocalVariableTableAttribute>
            implements LocalVariableTableAttribute {
        private final CodeImpl codeAttribute;
        private List<LocalVariableInfo> localVars = null;

        public BoundLocalVariableTableAttribute(AttributedElement enclosing, ClassReader cf, AttributeMapper<LocalVariableTableAttribute> mapper, int pos) {
            super(cf, mapper, pos);
            codeAttribute = (CodeImpl) enclosing;
        }

        @Override
        public List<LocalVariableInfo> localVariables() {
            if (localVars == null) {
                int cnt = classReader.readU2(payloadStart);
                BoundLocalVariable[] elements = new BoundLocalVariable[cnt];
                int p = payloadStart + 2;
                int pEnd = p + (cnt * 10);
                for (int i = 0; p < pEnd; p += 10, i++) {
                    elements[i] = new BoundLocalVariable(codeAttribute, p);
                }
                localVars = List.of(elements);
            }
            return localVars;
        }
    }

    public static final class BoundLocalVariableTypeTableAttribute
            extends BoundAttribute<LocalVariableTypeTableAttribute>
            implements LocalVariableTypeTableAttribute {
        private final CodeImpl codeAttribute;
        private List<LocalVariableTypeInfo> localVars = null;

        public BoundLocalVariableTypeTableAttribute(AttributedElement enclosing, ClassReader cf, AttributeMapper<LocalVariableTypeTableAttribute> mapper, int pos) {
            super(cf, mapper, pos);
            this.codeAttribute = (CodeImpl) enclosing;
        }

        @Override
        public List<LocalVariableTypeInfo> localVariableTypes() {
            if (localVars == null) {
                final int cnt = classReader.readU2(payloadStart);
                BoundLocalVariableType[] elements = new BoundLocalVariableType[cnt];
                int p = payloadStart + 2;
                int pEnd = p + (cnt * 10);
                for (int i = 0; p < pEnd; p += 10, i++) {
                    elements[i] = new BoundLocalVariableType(codeAttribute, p);
                }
                localVars = List.of(elements);
            }
            return localVars;
        }
    }

    public static final class BoundMethodParametersAttribute extends BoundAttribute<MethodParametersAttribute>
            implements MethodParametersAttribute {
        private List<MethodParameterInfo> parameters = null;

        public BoundMethodParametersAttribute(ClassReader cf, AttributeMapper<MethodParametersAttribute> mapper, int pos) {
            super(cf, mapper, pos);
        }

        @Override
        public List<MethodParameterInfo> parameters() {
            if (parameters == null) {
                final int cnt = classReader.readU1(payloadStart);
                MethodParameterInfo[] elements = new MethodParameterInfo[cnt];
                int p = payloadStart + 1;
                int pEnd = p + (cnt * 4);
                for (int i = 0; p < pEnd; p += 4, i++) {
                    Utf8Entry name = classReader.readUtf8EntryOrNull(p);
                    int accessFlags = classReader.readU2(p + 2);
                    elements[i] = MethodParameterInfo.of(Optional.ofNullable(name), accessFlags);
                }
                parameters = List.of(elements);
            }
            return parameters;
        }
    }

    public static final class BoundModuleHashesAttribute extends BoundAttribute<ModuleHashesAttribute>
            implements ModuleHashesAttribute {
        private List<ModuleHashInfo> hashes = null;

        public BoundModuleHashesAttribute(ClassReader cf, AttributeMapper<ModuleHashesAttribute> mapper, int pos) {
            super(cf, mapper, pos);
        }

        @Override
        public Utf8Entry algorithm() {
            return classReader.readUtf8Entry(payloadStart);
        }

        @Override
        public List<ModuleHashInfo> hashes() {
            if (hashes == null) {
                final int cnt = classReader.readU2(payloadStart + 2);
                ModuleHashInfo[] elements = new ModuleHashInfo[cnt];
                int p = payloadStart + 4;
                //System.err.printf("%5d: ModuleHashesAttr alg = %s, cnt = %d%n", pos, algorithm(), cnt);
                for (int i = 0; i < cnt; ++i) {
                    ModuleEntry module = classReader.readModuleEntry(p);
                    int hashLength = classReader.readU2(p + 2);
                    //System.err.printf("%5d:     [%d] module = %s, hashLength = %d%n", p, i, module, hashLength);
                    p += 4;
                    elements[i] = ModuleHashInfo.of(module, classReader.readBytes(p, hashLength));
                    p += hashLength;
                }
                hashes = List.of(elements);
            }
            return hashes;
        }
    }

    public static final class BoundRecordAttribute extends BoundAttribute<RecordAttribute>
            implements RecordAttribute {
        private List<RecordComponentInfo> components = null;

        public BoundRecordAttribute(ClassReader cf, AttributeMapper<RecordAttribute> mapper, int pos) {
            super(cf, mapper, pos);
        }

        @Override
        public List<RecordComponentInfo> components() {
            if (components == null) {
                final int cnt = classReader.readU2(payloadStart);
                RecordComponentInfo[] elements = new RecordComponentInfo[cnt];
                int p = payloadStart + 2;
                for (int i = 0; i < cnt; i++) {
                    elements[i] = new BoundRecordComponentInfo(classReader, p);
                    p = classReader.skipAttributeHolder(p + 4);
                }
                components = List.of(elements);
            }
            return components;
        }
    }

    public static final class BoundDeprecatedAttribute extends BoundAttribute<DeprecatedAttribute>
            implements DeprecatedAttribute {
        public BoundDeprecatedAttribute(ClassReader cf, AttributeMapper<DeprecatedAttribute> mapper, int pos) {
            super(cf, mapper, pos);
        }
    }

    public static final class BoundSignatureAttribute extends BoundAttribute<SignatureAttribute>
            implements SignatureAttribute {
        public BoundSignatureAttribute(ClassReader cf, AttributeMapper<SignatureAttribute> mapper, int pos) {
            super(cf, mapper, pos);
        }

        @Override
        public Utf8Entry signature() {
            return classReader.readUtf8Entry(payloadStart);
        }
    }

    public static final class BoundSourceFileAttribute extends BoundAttribute<SourceFileAttribute>
            implements SourceFileAttribute {
        public BoundSourceFileAttribute(ClassReader cf, AttributeMapper<SourceFileAttribute> mapper, int pos) {
            super(cf, mapper, pos);
        }

        @Override
        public Utf8Entry sourceFile() {
            return classReader.readUtf8Entry(payloadStart);
        }

    }

    public static final class BoundModuleMainClassAttribute extends BoundAttribute<ModuleMainClassAttribute> implements ModuleMainClassAttribute {
        public BoundModuleMainClassAttribute(ClassReader cf, AttributeMapper<ModuleMainClassAttribute> mapper, int pos) {
            super(cf, mapper, pos);
        }

        @Override
        public ClassEntry mainClass() {
            return classReader.readClassEntry(payloadStart);
        }
    }

    public static final class BoundNestHostAttribute extends BoundAttribute<NestHostAttribute>
            implements NestHostAttribute {
        public BoundNestHostAttribute(ClassReader cf, AttributeMapper<NestHostAttribute> mapper, int pos) {
            super(cf, mapper, pos);
        }

        @Override
        public ClassEntry nestHost() {
            return classReader.readClassEntry(payloadStart);
        }
    }

    public static final class BoundSourceDebugExtensionAttribute extends BoundAttribute<SourceDebugExtensionAttribute>
            implements SourceDebugExtensionAttribute {
        public BoundSourceDebugExtensionAttribute(ClassReader cf, AttributeMapper<SourceDebugExtensionAttribute> mapper, int pos) {
            super(cf, mapper, pos);
        }
    }

    public static final class BoundConstantValueAttribute extends BoundAttribute<ConstantValueAttribute>
            implements ConstantValueAttribute {
        public BoundConstantValueAttribute(ClassReader cf, AttributeMapper<ConstantValueAttribute> mapper, int pos) {
            super(cf, mapper, pos);
        }

        @Override
        public ConstantValueEntry constant() {
            return classReader.readEntry(payloadStart, ConstantValueEntry.class);
        }

    }

    public static final class BoundModuleTargetAttribute extends BoundAttribute<ModuleTargetAttribute>
            implements ModuleTargetAttribute {
        public BoundModuleTargetAttribute(ClassReader cf, AttributeMapper<ModuleTargetAttribute> mapper, int pos) {
            super(cf, mapper, pos);
        }

        @Override
        public Utf8Entry targetPlatform() {
            return classReader.readUtf8Entry(payloadStart);
        }
    }

    public static final class BoundCompilationIDAttribute extends BoundAttribute<CompilationIDAttribute>
            implements CompilationIDAttribute {
        public BoundCompilationIDAttribute(ClassReader cf, AttributeMapper<CompilationIDAttribute> mapper, int pos) {
            super(cf, mapper, pos);
        }

        @Override
        public Utf8Entry compilationId() {
            return classReader.readUtf8Entry(payloadStart);
        }
    }

    public static final class BoundSourceIDAttribute extends BoundAttribute<SourceIDAttribute>
            implements SourceIDAttribute {
        public BoundSourceIDAttribute(ClassReader cf, AttributeMapper<SourceIDAttribute> mapper, int pos) {
            super(cf, mapper, pos);
        }

        @Override
        public Utf8Entry sourceId() {
            return classReader.readUtf8Entry(payloadStart);
        }
    }

    public static final class BoundModuleResolutionAttribute extends BoundAttribute<ModuleResolutionAttribute>
            implements ModuleResolutionAttribute {
        public BoundModuleResolutionAttribute(ClassReader cf, AttributeMapper<ModuleResolutionAttribute> mapper, int pos) {
            super(cf, mapper, pos);
        }

        @Override
        public int resolutionFlags() {
            return classReader.readU2(payloadStart);
        }
    }

    public static final class BoundExceptionsAttribute extends BoundAttribute<ExceptionsAttribute>
            implements ExceptionsAttribute {
        private List<ClassEntry> exceptions = null;

        public BoundExceptionsAttribute(ClassReader cf, AttributeMapper<ExceptionsAttribute> mapper, int pos) {
            super(cf, mapper, pos);
        }

        @Override
        public List<ClassEntry> exceptions() {
            if (exceptions == null) {
                exceptions = readEntryList(payloadStart);
            }
            return exceptions;
        }
    }

    public static final class BoundModuleAttribute extends BoundAttribute<ModuleAttribute>
            implements ModuleAttribute {
        private List<ModuleRequireInfo> requires = null;
        private List<ModuleExportInfo> exports = null;
        private List<ModuleOpenInfo> opens = null;
        private List<ClassEntry> uses = null;
        private List<ModuleProvideInfo> provides = null;

        public BoundModuleAttribute(ClassReader cf, AttributeMapper<ModuleAttribute> mapper, int pos) {
            super(cf, mapper, pos);
        }

        @Override
        public ModuleEntry moduleName() {
            return classReader.readModuleEntry(payloadStart);
        }

        @Override
        public int moduleFlagsMask() {
            return classReader.readU2(payloadStart + 2);
        }

        @Override
        public Optional<Utf8Entry> moduleVersion() {
            return Optional.ofNullable(classReader.readUtf8EntryOrNull(payloadStart + 4));
        }

        @Override
        public List<ModuleRequireInfo> requires() {
            if (requires == null) {
                structure();
            }
            return requires;
        }

        @Override
        public List<ModuleExportInfo> exports() {
            if (exports == null) {
                structure();
            }
            return exports;
        }

        @Override
        public List<ModuleOpenInfo> opens() {
            if (opens == null) {
                structure();
            }
            return opens;
        }

        @Override
        public List<ClassEntry> uses() {
            if (uses == null) {
                structure();
            }
            return uses;
        }

        @Override
        public List<ModuleProvideInfo> provides() {
            if (provides == null) {
                structure();
            }
            return provides;
        }

        private void structure() {
            int p = payloadStart + 8;

            {
                int cnt = classReader.readU2(payloadStart + 6);
                ModuleRequireInfo[] elements = new ModuleRequireInfo[cnt];
                int end = p + (cnt * 6);
                for (int i = 0; p < end; p += 6, i++) {
                    elements[i] = ModuleRequireInfo.of(classReader.readModuleEntry(p),
                            classReader.readU2(p + 2),
                            classReader.readEntryOrNull(p + 4, Utf8Entry.class));
                }
                requires = List.of(elements);
            }

            {
                int cnt = classReader.readU2(p);
                p += 2;
                ModuleExportInfo[] elements = new ModuleExportInfo[cnt];
                for (int i = 0; i < cnt; i++) {
                    PackageEntry pe = classReader.readPackageEntry(p);
                    int exportFlags = classReader.readU2(p + 2);
                    p += 4;
                    List<ModuleEntry> exportsTo = readEntryList(p);
                    p += 2 + exportsTo.size() * 2;
                    elements[i] = ModuleExportInfo.of(pe, exportFlags, exportsTo);
                }
                exports = List.of(elements);
            }

            {
                int cnt = classReader.readU2(p);
                p += 2;
                ModuleOpenInfo[] elements = new ModuleOpenInfo[cnt];
                for (int i = 0; i < cnt; i++) {
                    PackageEntry po = classReader.readPackageEntry(p);
                    int opensFlags = classReader.readU2(p + 2);
                    p += 4;
                    List<ModuleEntry> opensTo = readEntryList(p);
                    p += 2 + opensTo.size() * 2;
                    elements[i] = ModuleOpenInfo.of(po, opensFlags, opensTo);
                }
                opens = List.of(elements);
            }

            {
                uses = readEntryList(p);
                p += 2 + uses.size() * 2;
                int cnt = classReader.readU2(p);
                p += 2;
                ModuleProvideInfo[] elements = new ModuleProvideInfo[cnt];
                provides = new ArrayList<>(cnt);
                for (int i = 0; i < cnt; i++) {
                    ClassEntry c = classReader.readClassEntry(p);
                    p += 2;
                    List<ClassEntry> providesWith = readEntryList(p);
                    p += 2 + providesWith.size() * 2;
                    elements[i] = ModuleProvideInfo.of(c, providesWith);
                }
                provides = List.of(elements);
            }
        }
    }

    public static final class BoundModulePackagesAttribute extends BoundAttribute<ModulePackagesAttribute>
            implements ModulePackagesAttribute {
        private List<PackageEntry> packages = null;

        public BoundModulePackagesAttribute(ClassReader cf, AttributeMapper<ModulePackagesAttribute> mapper, int pos) {
            super(cf, mapper, pos);
        }

        @Override
        public List<PackageEntry> packages() {
            if (packages == null) {
                packages = readEntryList(payloadStart);
            }
            return packages;
        }
    }

    public static final class BoundNestMembersAttribute extends BoundAttribute<NestMembersAttribute>
            implements NestMembersAttribute {

        private List<ClassEntry> members = null;

        public BoundNestMembersAttribute(ClassReader cf, AttributeMapper<NestMembersAttribute> mapper, int pos) {
            super(cf, mapper, pos);
        }

        @Override
        public List<ClassEntry> nestMembers() {
            if (members == null) {
                members = readEntryList(payloadStart);
            }
            return members;
        }
    }

    public static final class BoundBootstrapMethodsAttribute extends BoundAttribute<BootstrapMethodsAttribute>
            implements BootstrapMethodsAttribute {

        private List<BootstrapMethodEntry> bootstraps = null;
        private final int size;

        public BoundBootstrapMethodsAttribute(ClassReader reader, AttributeMapper<BootstrapMethodsAttribute> mapper, int pos) {
            super(reader, mapper, pos);
            size = classReader.readU2(pos);
        }

        @Override
        public int bootstrapMethodsSize() {
            return size;
        }

        @Override
        public List<BootstrapMethodEntry> bootstrapMethods() {
            if (bootstraps == null) {
                BootstrapMethodEntry[] bs = new BootstrapMethodEntry[size];
                int p = payloadStart + 2;
                for (int i = 0; i < size; ++i) {
                    final AbstractPoolEntry.MethodHandleEntryImpl handle
                            = (AbstractPoolEntry.MethodHandleEntryImpl) classReader.readMethodHandleEntry(p);
                    final List<LoadableConstantEntry> args = readEntryList(p + 2);
                    p += 4 + args.size() * 2;
                    int hash = BootstrapMethodEntryImpl.computeHashCode(handle, args);
                    bs[i] = new BootstrapMethodEntryImpl(classReader, i, hash, handle, args);
                }
                bootstraps = List.of(bs);
            }
            return bootstraps;
        }
    }

    public static final class BoundInnerClassesAttribute extends BoundAttribute<InnerClassesAttribute>
            implements InnerClassesAttribute {
        private List<InnerClassInfo> classes;

        public BoundInnerClassesAttribute(ClassReader cf, AttributeMapper<InnerClassesAttribute> mapper, int pos) {
            super(cf, mapper, pos);
        }

        @Override
        public List<InnerClassInfo> classes() {
            if (classes == null) {
                final int cnt = classReader.readU2(payloadStart);
                int p = payloadStart + 2;
                InnerClassInfo[] elements = new InnerClassInfo[cnt];
                for (int i = 0; i < cnt; i++) {
                    ClassEntry innerClass = classReader.readClassEntry(p);
                    var outerClass = classReader.readEntryOrNull(p + 2, ClassEntry.class);
                    var innerName = classReader.readEntryOrNull(p + 4, Utf8Entry.class);
                    int flags = classReader.readU2(p + 6);
                    p += 8;
                    elements[i] = InnerClassInfo.of(innerClass, Optional.ofNullable(outerClass), Optional.ofNullable(innerName), flags);
                }
                classes = List.of(elements);
            }
            return classes;
        }
    }

    public static final class BoundEnclosingMethodAttribute extends BoundAttribute<EnclosingMethodAttribute>
            implements EnclosingMethodAttribute {
        public BoundEnclosingMethodAttribute(ClassReader cf, AttributeMapper<EnclosingMethodAttribute> mapper, int pos) {
            super(cf, mapper, pos);
        }

        @Override
        public ClassEntry enclosingClass() {
            return classReader.readClassEntry(payloadStart);
        }

        @Override
        public Optional<NameAndTypeEntry> enclosingMethod() {
            return Optional.ofNullable(classReader.readEntryOrNull(payloadStart + 2, NameAndTypeEntry.class));
        }
    }

    public static final class BoundAnnotationDefaultAttr
            extends BoundAttribute<AnnotationDefaultAttribute>
            implements AnnotationDefaultAttribute {
        private AnnotationValue annotationValue;

        public BoundAnnotationDefaultAttr(ClassReader cf, AttributeMapper<AnnotationDefaultAttribute> mapper, int pos) {
            super(cf, mapper, pos);
        }

        @Override
        public AnnotationValue defaultValue() {
            if (annotationValue == null)
                annotationValue = AnnotationReader.readElementValue(classReader, payloadStart);
            return annotationValue;
        }
    }

    public static final class BoundRuntimeVisibleTypeAnnotationsAttribute extends BoundAttribute<RuntimeVisibleTypeAnnotationsAttribute>
            implements RuntimeVisibleTypeAnnotationsAttribute {

        private final LabelContext labelContext;

        public BoundRuntimeVisibleTypeAnnotationsAttribute(AttributedElement enclosing, ClassReader cf, AttributeMapper<RuntimeVisibleTypeAnnotationsAttribute> mapper, int pos) {
            super(cf, mapper, pos);
            this.labelContext = (enclosing instanceof LabelContext lc) ? lc : null;
        }

        @Override
        public List<TypeAnnotation> annotations() {
            return AnnotationReader.readTypeAnnotations(classReader, payloadStart, labelContext);
        }
    }

    public static final class BoundRuntimeInvisibleTypeAnnotationsAttribute
            extends BoundAttribute<RuntimeInvisibleTypeAnnotationsAttribute>
            implements RuntimeInvisibleTypeAnnotationsAttribute {
        public BoundRuntimeInvisibleTypeAnnotationsAttribute(AttributedElement enclosing, ClassReader cf, AttributeMapper<RuntimeInvisibleTypeAnnotationsAttribute> mapper, int pos) {
            super(cf, mapper, pos);
            this.labelContext = (enclosing instanceof LabelContext lc) ? lc : null;
        }

        private final LabelContext labelContext;

        @Override
        public List<TypeAnnotation> annotations() {
            return AnnotationReader.readTypeAnnotations(classReader, payloadStart, labelContext);
        }
    }

    public static final class BoundRuntimeVisibleParameterAnnotationsAttribute
            extends BoundAttribute<RuntimeVisibleParameterAnnotationsAttribute>
            implements RuntimeVisibleParameterAnnotationsAttribute {

        public BoundRuntimeVisibleParameterAnnotationsAttribute(ClassReader cf, AttributeMapper<RuntimeVisibleParameterAnnotationsAttribute> mapper, int pos) {
            super(cf, mapper, pos);
        }

        @Override
        public List<List<Annotation>> parameterAnnotations() {
            return AnnotationReader.readParameterAnnotations(classReader, payloadStart);
        }
    }

    public static final class BoundRuntimeInvisibleParameterAnnotationsAttribute
            extends BoundAttribute<RuntimeInvisibleParameterAnnotationsAttribute>
            implements RuntimeInvisibleParameterAnnotationsAttribute {

        public BoundRuntimeInvisibleParameterAnnotationsAttribute(ClassReader cf, AttributeMapper<RuntimeInvisibleParameterAnnotationsAttribute> mapper, int pos) {
            super(cf, mapper, pos);
        }

        @Override
        public List<List<Annotation>> parameterAnnotations() {
            return AnnotationReader.readParameterAnnotations(classReader, payloadStart);
        }
    }

    public static final class BoundRuntimeInvisibleAnnotationsAttribute
            extends BoundAttribute<RuntimeInvisibleAnnotationsAttribute>
            implements RuntimeInvisibleAnnotationsAttribute {
        private List<Annotation> inflated;

        public BoundRuntimeInvisibleAnnotationsAttribute(ClassReader cf,
                                                         int payloadStart) {
            super(cf, Attributes.runtimeInvisibleAnnotations(), payloadStart);
        }

        @Override
        public List<Annotation> annotations() {
            if (inflated == null)
                inflated = AnnotationReader.readAnnotations(classReader, payloadStart);
            return inflated;
        }
    }

    public static final class BoundRuntimeVisibleAnnotationsAttribute
            extends BoundAttribute<RuntimeVisibleAnnotationsAttribute>
            implements RuntimeVisibleAnnotationsAttribute {
        private List<Annotation> inflated;

        public BoundRuntimeVisibleAnnotationsAttribute(ClassReader cf,
                                                       int payloadStart) {
            super(cf, Attributes.runtimeVisibleAnnotations(), payloadStart);
        }

        @Override
        public List<Annotation> annotations() {
            if (inflated == null)
                inflated = AnnotationReader.readAnnotations(classReader, payloadStart);
            return inflated;
        }
    }

    public static final class BoundPermittedSubclassesAttribute extends BoundAttribute<PermittedSubclassesAttribute>
            implements PermittedSubclassesAttribute {
        private List<ClassEntry> permittedSubclasses = null;

        public BoundPermittedSubclassesAttribute(ClassReader cf, AttributeMapper<PermittedSubclassesAttribute> mapper, int pos) {
            super(cf, mapper, pos);
        }

        @Override
        public List<ClassEntry> permittedSubclasses() {
            if (permittedSubclasses == null) {
                permittedSubclasses = readEntryList(payloadStart);
            }
            return permittedSubclasses;
        }
    }

    public abstract static sealed class BoundCodeAttribute
            extends BoundAttribute<CodeAttribute>
            implements CodeAttribute
            permits CodeImpl {
        protected final int codeStart;
        protected final int codeLength;
        protected final int codeEnd;
        protected final int attributePos;
        protected final int exceptionHandlerPos;
        protected final int exceptionHandlerCnt;
        protected final MethodModel enclosingMethod;

        public BoundCodeAttribute(AttributedElement enclosing,
                                  ClassReader reader,
                                  AttributeMapper<CodeAttribute> mapper,
                                  int payloadStart) {
            super(reader, mapper, payloadStart);
            this.codeLength = classReader.readInt(payloadStart + 4);
            this.enclosingMethod = (MethodModel) enclosing;
            this.codeStart = payloadStart + 8;
            this.codeEnd = codeStart + codeLength;
            this.exceptionHandlerPos = codeEnd;
            this.exceptionHandlerCnt = classReader.readU2(exceptionHandlerPos);
            this.attributePos = exceptionHandlerPos + 2 + exceptionHandlerCnt * 8;
        }

        // CodeAttribute

        @Override
        public int maxStack() {
            return classReader.readU2(payloadStart);
        }

        @Override
        public int maxLocals() {
            return classReader.readU2(payloadStart + 2);
        }

        @Override
        public int codeLength() {
            return codeLength;
        }

        @Override
        public byte[] codeArray() {
            return classReader.readBytes(payloadStart + 8, codeLength());
        }
    }

    /**
     * {@return the attribute mapper for a standard attribute}
     *
     * @param name the name of the attribute to find
     */
    public static AttributeMapper<?> standardAttribute(Utf8Entry name) {
        // critical bootstrap path, so no lambdas nor method handles here
        return switch (name.hashCode()) {
            case 0x78147009 ->
                name.equalsString(NAME_ANNOTATION_DEFAULT) ? annotationDefault() : null;
            case 0x665e3a3a ->
                name.equalsString(NAME_BOOTSTRAP_METHODS) ? bootstrapMethods() : null;
            case 0xcb7e162 ->
                name.equalsString(NAME_CHARACTER_RANGE_TABLE) ? characterRangeTable() : null;
            case 0x21e41e7e ->
                name.equalsString(NAME_CODE) ? code() : null;
            case 0x5a306b41 ->
                name.equalsString(NAME_COMPILATION_ID) ? compilationId() : null;
            case 0x3e191c7c ->
                name.equalsString(NAME_CONSTANT_VALUE) ? constantValue() : null;
            case 0x5e88ed0c ->
                name.equalsString(NAME_DEPRECATED) ? deprecated() : null;
            case 0x7284695e ->
                name.equalsString(NAME_ENCLOSING_METHOD) ? enclosingMethod() : null;
            case 0x21df25db ->
                name.equalsString(NAME_EXCEPTIONS) ? exceptions() : null;
            case 0x11392da9 ->
                name.equalsString(NAME_INNER_CLASSES) ? innerClasses() : null;
            case 0x167536fc ->
                name.equalsString(NAME_LINE_NUMBER_TABLE) ? lineNumberTable() : null;
            case 0x46939abc ->
                name.equalsString(NAME_LOCAL_VARIABLE_TABLE) ? localVariableTable() : null;
            case 0x63ee67f4 ->
                name.equalsString(NAME_LOCAL_VARIABLE_TYPE_TABLE) ? localVariableTypeTable() : null;
            case 0x2b597e15 ->
                name.equalsString(NAME_METHOD_PARAMETERS) ? methodParameters() : null;
            case 0x19f20ade ->
                name.equalsString(NAME_MODULE) ? module() : null;
            case 0x47f6395e ->
                name.equalsString(NAME_MODULE_HASHES) ? moduleHashes() : null;
            case 0x54db809 ->
                name.equalsString(NAME_MODULE_MAIN_CLASS) ? moduleMainClass() : null;
            case 0x1abd1c2c ->
                name.equalsString(NAME_MODULE_PACKAGES) ? modulePackages() : null;
            case 0x6ba46dd ->
                name.equalsString(NAME_MODULE_RESOLUTION) ? moduleResolution() : null;
            case 0x46f7d91d ->
                name.equalsString(NAME_MODULE_TARGET) ? moduleTarget() : null;
            case 0x5137f53 ->
                name.equalsString(NAME_NEST_HOST) ? nestHost() : null;
            case 0x4a8fa3b6 ->
                name.equalsString(NAME_NEST_MEMBERS) ? nestMembers() : null;
            case 0x55c73cb6 ->
                name.equalsString(NAME_PERMITTED_SUBCLASSES) ? permittedSubclasses() : null;
            case 0x3fe76d4e ->
                name.equalsString(NAME_RECORD) ? record() : null;
            case 0x180d6925 ->
                name.equalsString(NAME_RUNTIME_INVISIBLE_ANNOTATIONS) ? runtimeInvisibleAnnotations() : null;
            case 0x7be22752 ->
                name.equalsString(NAME_RUNTIME_INVISIBLE_PARAMETER_ANNOTATIONS) ? runtimeInvisibleParameterAnnotations() : null;
            case 0x5299824 ->
                name.equalsString(NAME_RUNTIME_INVISIBLE_TYPE_ANNOTATIONS) ? runtimeInvisibleTypeAnnotations() : null;
            case 0x3534786e ->
                name.equalsString(NAME_RUNTIME_VISIBLE_ANNOTATIONS) ? runtimeVisibleAnnotations() : null;
            case 0xb4b4ac6 ->
                name.equalsString(NAME_RUNTIME_VISIBLE_PARAMETER_ANNOTATIONS) ? runtimeVisibleParameterAnnotations() : null;
            case 0x6926482 ->
                name.equalsString(NAME_RUNTIME_VISIBLE_TYPE_ANNOTATIONS) ? runtimeVisibleTypeAnnotations() : null;
            case 0x16a42b7c ->
                name.equalsString(NAME_SIGNATURE) ? signature() : null;
            case 0x400ab245 ->
                name.equalsString(NAME_SOURCE_DEBUG_EXTENSION) ? sourceDebugExtension() : null;
            case 0x2af490d4 ->
                name.equalsString(NAME_SOURCE_FILE) ? sourceFile() : null;
            case 0x303e0c58 ->
                name.equalsString(NAME_SOURCE_ID) ? sourceId() : null;
            case 0x19c7d0cd ->
                name.equalsString(NAME_STACK_MAP_TABLE) ? stackMapTable() : null;
            case 0x3dc79b7a ->
                name.equalsString(NAME_SYNTHETIC) ? synthetic() : null;
            default -> null;
        };
    }
}
