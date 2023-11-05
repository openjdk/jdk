/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
package jdk.tools.jlink.internal.plugins;

import jdk.internal.classfile.*;
import jdk.internal.classfile.attribute.*;
import jdk.internal.classfile.constantpool.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import jdk.internal.jimage.decompressor.CompressIndexes;
import jdk.internal.jimage.decompressor.SignatureParser;
import jdk.internal.jimage.decompressor.StringSharingDecompressor;
import jdk.tools.jlink.internal.ResourcePoolManager.ResourcePoolImpl;
import jdk.tools.jlink.plugin.PluginException;
import jdk.tools.jlink.plugin.ResourcePool;
import jdk.tools.jlink.plugin.ResourcePoolBuilder;
import jdk.tools.jlink.plugin.ResourcePoolEntry;
import jdk.tools.jlink.internal.ResourcePoolManager;
import jdk.tools.jlink.internal.ResourcePrevisitor;
import jdk.tools.jlink.internal.StringTable;

/**
 *
 * A Plugin that stores the image classes constant pool UTF_8 entries into the
 * Image StringsTable.
 */
public class StringSharingPlugin extends AbstractPlugin implements ResourcePrevisitor {

    private static final int[] SIZES;

    static {
        SIZES = StringSharingDecompressor.getSizes();
    }

    private static final class CompactCPHelper {

        private static final class DescriptorsScanner {

            private final ClassModel cm;

            private DescriptorsScanner(ClassModel cm) {
                this.cm = cm;
            }

            private Set<Integer> scan() throws Exception {
                Set<Integer> utf8Descriptors = new HashSet<>();
                scanConstantPool(utf8Descriptors);

                scanFields(utf8Descriptors);

                scanMethods(utf8Descriptors);

                scanAttributes(cm.attributes(), utf8Descriptors);

                return utf8Descriptors;
            }

            private void scanAttributes(List<Attribute<?>> attributes,
                    Set<Integer> utf8Descriptors) throws Exception {
                for (Attribute<?> a : attributes) {
                    switch (a) {
                        case SignatureAttribute sig -> {
                            utf8Descriptors.add(sig.signature().index());
                        }
                        case RuntimeVisibleAnnotationsAttribute an -> {
                            for (Annotation annotation : an.annotations())
                                scanAnnotation(annotation, utf8Descriptors);
                        }
                        case RuntimeInvisibleAnnotationsAttribute an -> {
                            for (Annotation annotation : an.annotations())
                                scanAnnotation(annotation, utf8Descriptors);
                        }
                        case RuntimeVisibleParameterAnnotationsAttribute rap -> {
                            for (List<Annotation> arr : rap.parameterAnnotations()) {
                                for (Annotation an : arr)
                                    scanAnnotation(an, utf8Descriptors);
                            }
                        }
                        case RuntimeInvisibleParameterAnnotationsAttribute rap -> {
                            for (List<Annotation> arr : rap.parameterAnnotations()) {
                                for (Annotation an : arr)
                                    scanAnnotation(an, utf8Descriptors);
                            }
                        }
                        case LocalVariableTableAttribute lvt -> {
                            for (LocalVariableInfo entry: lvt.localVariables())
                                utf8Descriptors.add(entry.name().index());
                        }
                        case LocalVariableTypeTableAttribute lvt -> {
                            for (LocalVariableTypeInfo entry: lvt.localVariableTypes())
                                utf8Descriptors.add(entry.signature().index());
                        }
                        default -> {}
                    }
                }
            }

            private void scanAnnotation(Annotation annotation,
                    Set<Integer> utf8Descriptors) throws Exception {
                utf8Descriptors.add(annotation.className().index());
                for (AnnotationElement evp : annotation.elements()) {
                    utf8Descriptors.add(evp.name().index());
                    scanElementValue(evp.value(), utf8Descriptors);
                }
            }

            private void scanElementValue(AnnotationValue value,
                    Set<Integer> utf8Descriptors) throws Exception {
                switch (value) {
                    case AnnotationValue.OfEnum eev ->
                        utf8Descriptors.add(eev.className().index());
                    case AnnotationValue.OfClass eev ->
                        utf8Descriptors.add(eev.className().index());
                    case AnnotationValue.OfAnnotation aev ->
                        scanAnnotation(aev.annotation(), utf8Descriptors);
                    case AnnotationValue.OfArray aev -> {
                        for (AnnotationValue v : aev.values())
                            scanElementValue(v, utf8Descriptors);
                    }
                    default -> {}
                }
            }

            private void scanFields(Set<Integer> utf8Descriptors)
                    throws Exception {
                for (FieldModel field : cm.fields()) {
                    int descriptorIndex = field.fieldType().index();
                    utf8Descriptors.add(descriptorIndex);
                    scanAttributes(field.attributes(), utf8Descriptors);
                }

            }

            private void scanMethods(Set<Integer> utf8Descriptors)
                    throws Exception {
                for (MethodModel m : cm.methods()) {
                    int descriptorIndex = m.methodType().index();
                    utf8Descriptors.add(descriptorIndex);
                    scanAttributes(m.attributes(), utf8Descriptors);
                }
            }

            private void scanConstantPool(Set<Integer> utf8Descriptors)
                    throws Exception {
                try {
                    for (PoolEntry info : cm.constantPool()) {
                        switch (info) {
                            case NameAndTypeEntry nameAndType ->
                                utf8Descriptors.add(nameAndType.type().index());
                            case MethodTypeEntry mt ->
                                utf8Descriptors.add(mt.descriptor().index());
                            default -> {}
                        }
                    }
                } catch (ConstantPoolException ex) {
                    throw new IOException(ex);
                }
            }
        }

        public byte[] transform(ResourcePoolEntry resource, ResourcePoolBuilder out,
                StringTable strings) throws IOException, Exception {
            byte[] content = resource.contentBytes();
            ClassModel cf = Classfile.of().parse(content);
            DescriptorsScanner scanner = new DescriptorsScanner(cf);
            return optimize(resource, out, strings, scanner.scan(), content);
        }

        @SuppressWarnings("fallthrough")
        private byte[] optimize(ResourcePoolEntry resource, ResourcePoolBuilder resources,
                StringTable strings,
                Set<Integer> descriptorIndexes, byte[] content) throws Exception {
            DataInputStream stream = new DataInputStream(new ByteArrayInputStream(content));
            ByteArrayOutputStream outStream = new ByteArrayOutputStream(content.length);
            DataOutputStream out = new DataOutputStream(outStream);
            byte[] header = new byte[8]; //magic/4, minor/2, major/2
            stream.readFully(header);
            out.write(header);
            int count = stream.readUnsignedShort();
            out.writeShort(count);
            for (int i = 1; i < count; i++) {
                int tag = stream.readUnsignedByte();
                byte[] arr;
                switch (tag) {
                    case Classfile.TAG_UTF8: {
                        String original = stream.readUTF();
                        // 2 cases, a Descriptor or a simple String
                        if (descriptorIndexes.contains(i)) {
                            SignatureParser.ParseResult parseResult
                                    = SignatureParser.parseSignatureDescriptor(original);
                            List<Integer> indexes
                                    = parseResult.types.stream().map(strings::addString).toList();
                            if (!indexes.isEmpty()) {
                                out.write(StringSharingDecompressor.EXTERNALIZED_STRING_DESCRIPTOR);
                                int sigIndex = strings.addString(parseResult.formatted);
                                byte[] compressed
                                        = CompressIndexes.compress(sigIndex);
                                out.write(compressed, 0, compressed.length);

                                writeDescriptorReference(out, indexes);
                                continue;
                            }
                        }
                        // Put all strings in strings table.
                        writeUTF8Reference(out, strings.addString(original));

                        break;
                    }
                    case Classfile.TAG_LONG:
                    case Classfile.TAG_DOUBLE:
                        i++;
                    default: {
                        out.write(tag);
                        int size = SIZES[tag];
                        arr = new byte[size];
                        stream.readFully(arr);
                        out.write(arr);
                    }
                }
            }
            out.write(content, content.length - stream.available(),
                    stream.available());
            out.flush();

            return outStream.toByteArray();
        }

        private void writeDescriptorReference(DataOutputStream out,
                List<Integer> indexes) throws IOException {
            List<byte[]> buffers = new ArrayList<>();
            int l = 0;
            for (Integer index : indexes) {
                byte[] buffer = CompressIndexes.compress(index);
                l += buffer.length;
                buffers.add(buffer);
            }
            ByteBuffer bb = ByteBuffer.allocate(l);
            buffers.forEach(bb::put);
            byte[] compressed_indices = bb.array();
            byte[] compressed_size = CompressIndexes.
                    compress(compressed_indices.length);
            out.write(compressed_size, 0, compressed_size.length);
            out.write(compressed_indices, 0, compressed_indices.length);
        }

        private void writeUTF8Reference(DataOutputStream out, int index)
                throws IOException {
            out.write(StringSharingDecompressor.EXTERNALIZED_STRING);
            byte[] compressed = CompressIndexes.compress(index);
            out.write(compressed, 0, compressed.length);
        }
    }

    private Predicate<String> predicate;

    public StringSharingPlugin() {
        this((path) -> true);
    }

    StringSharingPlugin(Predicate<String> predicate) {
        super("compact-cp");
        this.predicate = predicate;
    }

    @Override
    public Category getType() {
        return Category.COMPRESSOR;
    }

    @Override
    public ResourcePool transform(ResourcePool in, ResourcePoolBuilder result) {
        CompactCPHelper visit = new CompactCPHelper();
        in.transformAndCopy((resource) -> {
            ResourcePoolEntry res = resource;
            if (predicate.test(resource.path()) && resource.path().endsWith(".class")) {
                byte[] compressed = null;
                try {
                    compressed = visit.transform(resource, result, ((ResourcePoolImpl)in).getStringTable());
                } catch (Exception ex) {
                    throw new PluginException(ex);
                }
                res = ResourcePoolManager.newCompressedResource(resource,
                        ByteBuffer.wrap(compressed), getName(), null,
                        ((ResourcePoolImpl)in).getStringTable(), in.byteOrder());
            }
            return res;
        }, result);

        return result.build();
    }

    @Override
    public boolean hasArguments() {
        return true;
    }

    @Override
    public void configure(Map<String, String> config) {
        predicate = ResourceFilter.includeFilter(config.get(getName()));
    }

    @Override
    public void previsit(ResourcePool resources, StringTable strings) {
        CompactCPHelper preVisit = new CompactCPHelper();
        resources.entries().forEach(resource -> {
            if (resource.type().equals(ResourcePoolEntry.Type.CLASS_OR_RESOURCE)
                    && resource.path().endsWith(".class") && predicate.test(resource.path())) {
                try {
                    preVisit.transform(resource, null, strings);
                } catch (Exception ex) {
                    throw new PluginException(ex);
                }
            }
        });
    }
}
