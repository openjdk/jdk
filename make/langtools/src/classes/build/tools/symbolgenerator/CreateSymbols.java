/*
 * Copyright (c) 2006, 2015, Oracle and/or its affiliates. All rights reserved.
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

package build.tools.symbolgenerator;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.stream.Stream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.sun.tools.classfile.AccessFlags;
import com.sun.tools.classfile.Annotation;
import com.sun.tools.classfile.Annotation.Annotation_element_value;
import com.sun.tools.classfile.Annotation.Array_element_value;
import com.sun.tools.classfile.Annotation.Class_element_value;
import com.sun.tools.classfile.Annotation.Enum_element_value;
import com.sun.tools.classfile.Annotation.Primitive_element_value;
import com.sun.tools.classfile.Annotation.element_value;
import com.sun.tools.classfile.Annotation.element_value_pair;
import com.sun.tools.classfile.AnnotationDefault_attribute;
import com.sun.tools.classfile.Attribute;
import com.sun.tools.classfile.Attributes;
import com.sun.tools.classfile.ClassFile;
import com.sun.tools.classfile.ClassWriter;
import com.sun.tools.classfile.ConstantPool;
import com.sun.tools.classfile.ConstantPool.CONSTANT_Class_info;
import com.sun.tools.classfile.ConstantPool.CONSTANT_Double_info;
import com.sun.tools.classfile.ConstantPool.CONSTANT_Float_info;
import com.sun.tools.classfile.ConstantPool.CONSTANT_Integer_info;
import com.sun.tools.classfile.ConstantPool.CONSTANT_Long_info;
import com.sun.tools.classfile.ConstantPool.CONSTANT_String_info;
import com.sun.tools.classfile.ConstantPool.CONSTANT_Utf8_info;
import com.sun.tools.classfile.ConstantPool.CPInfo;
import com.sun.tools.classfile.ConstantPool.InvalidIndex;
import com.sun.tools.classfile.ConstantPoolException;
import com.sun.tools.classfile.ConstantValue_attribute;
import com.sun.tools.classfile.Deprecated_attribute;
import com.sun.tools.classfile.Descriptor;
import com.sun.tools.classfile.Exceptions_attribute;
import com.sun.tools.classfile.Field;
import com.sun.tools.classfile.InnerClasses_attribute;
import com.sun.tools.classfile.InnerClasses_attribute.Info;
import com.sun.tools.classfile.Method;
import com.sun.tools.classfile.RuntimeAnnotations_attribute;
import com.sun.tools.classfile.RuntimeInvisibleAnnotations_attribute;
import com.sun.tools.classfile.RuntimeInvisibleParameterAnnotations_attribute;
import com.sun.tools.classfile.RuntimeParameterAnnotations_attribute;
import com.sun.tools.classfile.RuntimeVisibleAnnotations_attribute;
import com.sun.tools.classfile.RuntimeVisibleParameterAnnotations_attribute;
import com.sun.tools.classfile.Signature_attribute;
import com.sun.tools.javac.jvm.Target;
import com.sun.tools.javac.util.Assert;
import com.sun.tools.javac.util.Pair;

/**
 * A tool for processing the .sym.txt files. It allows to:
 *  * convert the .sym.txt into class/sig files for ct.sym
 *  * in cooperation with the adjacent history Probe, construct .sym.txt files for previous platforms
 *
 * To convert the .sym.txt files to class/sig files from ct.sym, run:
 *     java build.tool.symbolgenerator.CreateSymbols build-ctsym [JOINED_VERSIONS|SEPARATE] <platform-description-file> <target-directory>
 *
 * The <platform-description-file> is a file of this format:
 *     generate platforms <platform-ids-to-generate separate with ':'>
 *     platform version <platform-id1> files <.sym.txt files containing history data for given platform, separate with ':'>
 *     platform version <platform-id2> base <base-platform-id> files <.sym.txt files containing history data for given platform, separate with ':'>
 *
 * The content of platform "<base-platform-id>" is also automatically added to the content of
 * platform "<platform-id2>", unless explicitly excluded in "<platform-id2>"'s .sym.txt files.
 *
 * To create the .sym.txt files, first run the history Probe for all the previous platforms:
 *     <jdk-N>/bin/java build.tools.symbolgenerator.Probe <classes-for-N>
 *
 * Where <classes-for-N> is a name of a file into which the classes from the bootclasspath of <jdk-N>
 * will be written.
 *
 * Then create the <platform-description-file> file and the .sym.txt files like this:
 *     java build.tools.symbolgenerator.CreateSymbols build-description <target-directory> <path-to-a-JDK-root> <include-list-file>
 *                                                    <platform-id1> <target-file-for-platform1> "<none>"
 *                                                    <platform-id2> <target-file-for-platform2> <diff-against-platform2>
 *                                                    <platform-id3> <target-file-for-platform3> <diff-against-platform3>
 *                                                    ...
 *
 * The <include-list-file> is a file that specifies classes that should be included/excluded.
 * Lines that start with '+' represent class or package that should be included, '-' class or package
 * that should be excluded. '/' should be used as package name delimiter, packages should end with '/'.
 * Several include list files may be specified, separated by File.pathSeparator.
 *
 * When <diff-against-platformN> is specified, the .sym.txt files for platform N will only contain
 * differences between platform N and the specified platform. The first platform (denoted F further)
 * that is specified should use literal value "<none>", to have all the APIs of the platform written to
 * the .sym.txt files. If there is an existing platform with full .sym.txt files in the repository,
 * that platform should be used as the first platform to avoid unnecessary changes to the .sym.txt
 * files. The <diff-against-platformN> for platform N should be determined as follows: if N < F, then
 * <diff-against-platformN> should be N + 1. If F < N, then <diff-against-platformN> should be N - 1.
 * If N is a custom/specialized sub-version of another platform N', then <diff-against-platformN> should be N'.
 *
 * To generate the .sym.txt files for OpenJDK 7 and 8:
 *     <jdk-7>/bin/java build.tools.symbolgenerator.Probe OpenJDK7.classes
 *     <jdk-8>/bin/java build.tools.symbolgenerator.Probe OpenJDK8.classes
 *     java build.tools.symbolgenerator.CreateSymbols build-description langtools/make/data/symbols $TOPDIR langtools/make/data/symbols/include.list
 *                                                    8 OpenJDK8.classes '<none>'
 *                                                    7 OpenJDK7.classes 8
 *
 * Note: the versions are expected to be a single character.
 */
public class CreateSymbols {

    //<editor-fold defaultstate="collapsed" desc="ct.sym construction">
    /**Create sig files for ct.sym reading the classes description from the directory that contains
     * {@code ctDescriptionFile}, using the file as a recipe to create the sigfiles.
     */
    @SuppressWarnings("unchecked")
    public void createSymbols(String ctDescriptionFile, String ctSymLocation, CtSymKind ctSymKind) throws IOException {
        ClassList classes = load(Paths.get(ctDescriptionFile));

        splitHeaders(classes);

        for (ClassDescription classDescription : classes) {
            for (ClassHeaderDescription header : classDescription.header) {
                switch (ctSymKind) {
                    case JOINED_VERSIONS:
                        Set<String> jointVersions = new HashSet<>();
                        jointVersions.add(header.versions);
                        limitJointVersion(jointVersions, classDescription.fields);
                        limitJointVersion(jointVersions, classDescription.methods);
                        writeClassesForVersions(ctSymLocation, classDescription, header, jointVersions);
                        break;
                    case SEPARATE:
                        Set<String> versions = new HashSet<>();
                        for (char v : header.versions.toCharArray()) {
                            versions.add("" + v);
                        }
                        writeClassesForVersions(ctSymLocation, classDescription, header, versions);
                        break;
                }
            }
        }
    }

    public static String EXTENSION = ".sig";

    ClassList load(Path ctDescription) throws IOException {
        List<PlatformInput> platforms = new ArrayList<>();
        Set<String> generatePlatforms = null;

        try (LineBasedReader reader = new LineBasedReader(ctDescription)) {
            while (reader.hasNext()) {
                switch (reader.lineKey) {
                    case "generate":
                        String[] platformsAttr = reader.attributes.get("platforms").split(":");
                        generatePlatforms = new HashSet<>(Arrays.asList(platformsAttr));
                        reader.moveNext();
                        break;
                    case "platform":
                        platforms.add(PlatformInput.load(reader));
                        reader.moveNext();
                        break;
                    default:
                        throw new IllegalStateException("Unknown key: " + reader.lineKey);
                }
            }
        }

        Map<String, ClassDescription> classes = new LinkedHashMap<>();

        for (PlatformInput platform: platforms) {
            for (ClassDescription cd : classes.values()) {
                addNewVersion(cd.header, platform.basePlatform, platform.version);
                addNewVersion(cd.fields, platform.basePlatform, platform.version);
                addNewVersion(cd.methods, platform.basePlatform, platform.version);
            }
            for (String input : platform.files) {
                Path inputFile = ctDescription.getParent().resolve(input);
                try (LineBasedReader reader = new LineBasedReader(inputFile)) {
                    while (reader.hasNext()) {
                        String nameAttr = reader.attributes.get("name");
                        ClassDescription cd =
                                classes.computeIfAbsent(nameAttr, n -> new ClassDescription());
                        if ("-class".equals(reader.lineKey)) {
                            removeVersion(cd.header, h -> true, platform.version);
                            reader.moveNext();
                            continue;
                        }
                        cd.read(reader, platform.basePlatform, platform.version);
                    }
                }
            }
        }

        ClassList result = new ClassList();

        for (ClassDescription desc : classes.values()) {
            for (Iterator<ClassHeaderDescription> chdIt = desc.header.iterator(); chdIt.hasNext();) {
                ClassHeaderDescription chd = chdIt.next();

                chd.versions = reduce(chd.versions, generatePlatforms);
                if (chd.versions.isEmpty())
                    chdIt.remove();
            }

            if (desc.header.isEmpty()) {
                continue;
            }

            for (Iterator<MethodDescription> methodIt = desc.methods.iterator(); methodIt.hasNext();) {
                MethodDescription method = methodIt.next();

                method.versions = reduce(method.versions, generatePlatforms);
                if (method.versions.isEmpty())
                    methodIt.remove();
            }

            for (Iterator<FieldDescription> fieldIt = desc.fields.iterator(); fieldIt.hasNext();) {
                FieldDescription field = fieldIt.next();

                field.versions = reduce(field.versions, generatePlatforms);
                if (field.versions.isEmpty())
                    fieldIt.remove();
            }

            result.add(desc);
        }

        return result;
    }

    static final class LineBasedReader implements AutoCloseable {
        private final BufferedReader input;
        public String lineKey;
        public Map<String, String> attributes = new HashMap<>();

        public LineBasedReader(Path input) throws IOException {
            this.input = Files.newBufferedReader(input);
            moveNext();
        }

        public void moveNext() throws IOException {
            String line = input.readLine();

            if (line == null) {
                lineKey = null;
                return ;
            }

            if (line.trim().isEmpty() || line.startsWith("#")) {
                moveNext();
                return ;
            }

            String[] parts = line.split(" ");

            lineKey = parts[0];
            attributes.clear();

            for (int i = 1; i < parts.length; i += 2) {
                attributes.put(parts[i], unquote(parts[i + 1]));
            }
        }

        public boolean hasNext() {
            return lineKey != null;
        }

        @Override
        public void close() throws IOException {
            input.close();
        }
    }

    private static String reduce(String original, String other) {
        Set<String> otherSet = new HashSet<>();

        for (char v : other.toCharArray()) {
            otherSet.add("" + v);
        }

        return reduce(original, otherSet);
    }

    private static String reduce(String original, Set<String> generate) {
        StringBuilder sb = new StringBuilder();

        for (char v : original.toCharArray()) {
            if (generate.contains("" + v)) {
                sb.append(v);
            }
        }
        return sb.toString();
    }

    private static class PlatformInput {
        public final String version;
        public final String basePlatform;
        public final List<String> files;
        public PlatformInput(String version, String basePlatform, List<String> files) {
            this.version = version;
            this.basePlatform = basePlatform;
            this.files = files;
        }

        public static PlatformInput load(LineBasedReader in) throws IOException {
            return new PlatformInput(in.attributes.get("version"),
                                     in.attributes.get("base"),
                                     Arrays.asList(in.attributes.get("files").split(":")));
        }
    }

    static void addNewVersion(Collection<? extends FeatureDescription> features,
                       String baselineVersion,
                       String version) {
        features.stream()
                .filter(f -> f.versions.contains(baselineVersion))
                .forEach(f -> f.versions += version);
    }

    static <T extends FeatureDescription> void removeVersion(Collection<T> features,
                                                             Predicate<T> shouldRemove,
                                                             String version) {
        for (T existing : features) {
            if (shouldRemove.test(existing) && existing.versions.endsWith(version)) {
                existing.versions = existing.versions.replace(version, "");
                return;
            }
        }
    }

    /**Changes to class header of an outer class (like adding a new type parameter) may affect
     * its innerclasses. So if the outer class's header is different for versions A and B, need to
     * split its innerclasses headers to also be different for versions A and B.
     */
    static void splitHeaders(ClassList classes) {
        Set<String> ctVersions = new HashSet<>();

        for (ClassDescription cd : classes) {
            for (ClassHeaderDescription header : cd.header) {
                for (char c : header.versions.toCharArray()) {
                    ctVersions.add("" + c);
                }
            }
        }

        classes.sort();

        for (ClassDescription cd : classes) {
            Map<String, String> outerSignatures2Version = new HashMap<>();

            for (String version : ctVersions) { //XXX
                ClassDescription outer = cd;
                String outerSignatures = "";

                while ((outer = classes.enclosingClass(outer)) != null) {
                    for (ClassHeaderDescription outerHeader : outer.header) {
                        if (outerHeader.versions.contains(version)) {
                            outerSignatures += outerHeader.signature;
                        }
                    }
                }

                outerSignatures2Version.compute(outerSignatures,
                                                 (key, value) -> value != null ? value + version : version);
            }

            List<ClassHeaderDescription> newHeaders = new ArrayList<>();

            HEADER_LOOP: for (ClassHeaderDescription header : cd.header) {
                for (String versions : outerSignatures2Version.values()) {
                    if (containsAll(versions, header.versions)) {
                        newHeaders.add(header);
                        continue HEADER_LOOP;
                    }
                    if (disjoint(versions, header.versions)) {
                        continue;
                    }
                    ClassHeaderDescription newHeader = new ClassHeaderDescription();
                    newHeader.classAnnotations = header.classAnnotations;
                    newHeader.deprecated = header.deprecated;
                    newHeader.extendsAttr = header.extendsAttr;
                    newHeader.flags = header.flags;
                    newHeader.implementsAttr = header.implementsAttr;
                    newHeader.innerClasses = header.innerClasses;
                    newHeader.runtimeAnnotations = header.runtimeAnnotations;
                    newHeader.signature = header.signature;
                    newHeader.versions = reduce(versions, header.versions);

                    newHeaders.add(newHeader);
                }
            }

            cd.header = newHeaders;
        }
    }

    void limitJointVersion(Set<String> jointVersions, List<? extends FeatureDescription> features) {
        for (FeatureDescription feature : features) {
            for (String version : jointVersions) {
                if (!containsAll(feature.versions, version) &&
                    !disjoint(feature.versions, version)) {
                    StringBuilder featurePart = new StringBuilder();
                    StringBuilder otherPart = new StringBuilder();
                    for (char v : version.toCharArray()) {
                        if (feature.versions.indexOf(v) != (-1)) {
                            featurePart.append(v);
                        } else {
                            otherPart.append(v);
                        }
                    }
                    jointVersions.remove(version);
                    if (featurePart.length() == 0 || otherPart.length() == 0) {
                        throw new AssertionError();
                    }
                    jointVersions.add(featurePart.toString());
                    jointVersions.add(otherPart.toString());
                    break;
                }
            }
        }
    }

    private static boolean containsAll(String versions, String subVersions) {
        for (char c : subVersions.toCharArray()) {
            if (versions.indexOf(c) == (-1))
                return false;
        }
        return true;
    }

    private static boolean disjoint(String version1, String version2) {
        for (char c : version2.toCharArray()) {
            if (version1.indexOf(c) != (-1))
                return false;
        }
        return true;
    }

    void writeClassesForVersions(String ctSymLocation,
                                 ClassDescription classDescription,
                                 ClassHeaderDescription header,
                                 Iterable<String> versions) throws IOException {
        for (String ver : versions) {
            writeClass(ctSymLocation, classDescription, header, ver);
        }
    }

    public enum CtSymKind {
        JOINED_VERSIONS,
        SEPARATE;
    }

    //<editor-fold defaultstate="collapsed" desc="Class Writing">
    void writeClass(String ctSymLocation,
                    ClassDescription classDescription,
                    ClassHeaderDescription header,
                    String version) throws IOException {
        List<CPInfo> constantPool = new ArrayList<>();
        constantPool.add(null);
        List<Method> methods = new ArrayList<>();
        for (MethodDescription methDesc : classDescription.methods) {
            if (disjoint(methDesc.versions, version))
                continue;
            Descriptor descriptor = new Descriptor(addString(constantPool, methDesc.descriptor));
            //TODO: LinkedHashMap to avoid param annotations vs. Signature problem in javac's ClassReader:
            Map<String, Attribute> attributesMap = new LinkedHashMap<>();
            addAttributes(methDesc, constantPool, attributesMap);
            Attributes attributes = new Attributes(attributesMap);
            AccessFlags flags = new AccessFlags(methDesc.flags);
            int nameString = addString(constantPool, methDesc.name);
            methods.add(new Method(flags, nameString, descriptor, attributes));
        }
        List<Field> fields = new ArrayList<>();
        for (FieldDescription fieldDesc : classDescription.fields) {
            if (disjoint(fieldDesc.versions, version))
                continue;
            Descriptor descriptor = new Descriptor(addString(constantPool, fieldDesc.descriptor));
            Map<String, Attribute> attributesMap = new HashMap<>();
            addAttributes(fieldDesc, constantPool, attributesMap);
            Attributes attributes = new Attributes(attributesMap);
            AccessFlags flags = new AccessFlags(fieldDesc.flags);
            int nameString = addString(constantPool, fieldDesc.name);
            fields.add(new Field(flags, nameString, descriptor, attributes));
        }
        int currentClass = addClass(constantPool, classDescription.name);
        int superclass = header.extendsAttr != null ? addClass(constantPool, header.extendsAttr) : 0;
        int[] interfaces = new int[header.implementsAttr.size()];
        int i = 0;
        for (String intf : header.implementsAttr) {
            interfaces[i++] = addClass(constantPool, intf);
        }
        AccessFlags flags = new AccessFlags(header.flags);
        Map<String, Attribute> attributesMap = new HashMap<>();
        addAttributes(header, constantPool, attributesMap);
        Attributes attributes = new Attributes(attributesMap);
        ConstantPool cp = new ConstantPool(constantPool.toArray(new CPInfo[constantPool.size()]));
        ClassFile classFile = new ClassFile(0xCAFEBABE,
                Target.DEFAULT.minorVersion,
                Target.DEFAULT.majorVersion,
                cp,
                flags,
                currentClass,
                superclass,
                interfaces,
                fields.toArray(new Field[0]),
                methods.toArray(new Method[0]),
                attributes);

        Path outputClassFile = Paths.get(ctSymLocation, version, classDescription.name + EXTENSION);

        Files.createDirectories(outputClassFile.getParent());

        try (OutputStream out = Files.newOutputStream(outputClassFile)) {
            ClassWriter w = new ClassWriter();

            w.write(classFile, out);
        }
    }

    private void addAttributes(ClassHeaderDescription header, List<CPInfo> constantPool, Map<String, Attribute> attributes) {
        addGenericAttributes(header, constantPool, attributes);
        if (header.innerClasses != null && !header.innerClasses.isEmpty()) {
            Info[] innerClasses = new Info[header.innerClasses.size()];
            int i = 0;
            for (InnerClassInfo info : header.innerClasses) {
                innerClasses[i++] =
                        new Info(info.innerClass == null ? 0 : addClass(constantPool, info.innerClass),
                                 info.outerClass == null ? 0 : addClass(constantPool, info.outerClass),
                                 info.innerClassName == null ? 0 : addString(constantPool, info.innerClassName),
                                 new AccessFlags(info.innerClassFlags));
            }
            int attributeString = addString(constantPool, Attribute.InnerClasses);
            attributes.put(Attribute.InnerClasses,
                           new InnerClasses_attribute(attributeString, innerClasses));
        }
    }

    private void addAttributes(MethodDescription desc, List<CPInfo> constantPool, Map<String, Attribute> attributes) {
        addGenericAttributes(desc, constantPool, attributes);
        if (desc.thrownTypes != null && !desc.thrownTypes.isEmpty()) {
            int[] exceptions = new int[desc.thrownTypes.size()];
            int i = 0;
            for (String exc : desc.thrownTypes) {
                exceptions[i++] = addClass(constantPool, exc);
            }
            int attributeString = addString(constantPool, Attribute.Exceptions);
            attributes.put(Attribute.Exceptions,
                           new Exceptions_attribute(attributeString, exceptions));
        }
        if (desc.annotationDefaultValue != null) {
            int attributeString = addString(constantPool, Attribute.AnnotationDefault);
            element_value attributeValue = createAttributeValue(constantPool,
                                                                desc.annotationDefaultValue);
            attributes.put(Attribute.AnnotationDefault,
                           new AnnotationDefault_attribute(attributeString, attributeValue));
        }
        if (desc.classParameterAnnotations != null && !desc.classParameterAnnotations.isEmpty()) {
            int attributeString =
                    addString(constantPool, Attribute.RuntimeInvisibleParameterAnnotations);
            Annotation[][] annotations =
                    createParameterAnnotations(constantPool, desc.classParameterAnnotations);
            attributes.put(Attribute.RuntimeInvisibleParameterAnnotations,
                           new RuntimeInvisibleParameterAnnotations_attribute(attributeString,
                                   annotations));
        }
        if (desc.runtimeParameterAnnotations != null && !desc.runtimeParameterAnnotations.isEmpty()) {
            int attributeString =
                    addString(constantPool, Attribute.RuntimeVisibleParameterAnnotations);
            Annotation[][] annotations =
                    createParameterAnnotations(constantPool, desc.runtimeParameterAnnotations);
            attributes.put(Attribute.RuntimeVisibleParameterAnnotations,
                           new RuntimeVisibleParameterAnnotations_attribute(attributeString,
                                   annotations));
        }
    }

    private void addAttributes(FieldDescription desc, List<CPInfo> constantPool, Map<String, Attribute> attributes) {
        addGenericAttributes(desc, constantPool, attributes);
        if (desc.constantValue != null) {
            Pair<Integer, Character> constantPoolEntry =
                    addConstant(constantPool, desc.constantValue, false);
            Assert.checkNonNull(constantPoolEntry);
            int constantValueString = addString(constantPool, Attribute.ConstantValue);
            attributes.put(Attribute.ConstantValue,
                           new ConstantValue_attribute(constantValueString, constantPoolEntry.fst));
        }
    }

    private void addGenericAttributes(FeatureDescription desc, List<CPInfo> constantPool, Map<String, Attribute> attributes) {
        if (desc.deprecated) {
            int attributeString = addString(constantPool, Attribute.Deprecated);
            attributes.put(Attribute.Deprecated,
                           new Deprecated_attribute(attributeString));
        }
        if (desc.signature != null) {
            int attributeString = addString(constantPool, Attribute.Signature);
            int signatureString = addString(constantPool, desc.signature);
            attributes.put(Attribute.Signature,
                           new Signature_attribute(attributeString, signatureString));
        }
        if (desc.classAnnotations != null && !desc.classAnnotations.isEmpty()) {
            int attributeString = addString(constantPool, Attribute.RuntimeInvisibleAnnotations);
            Annotation[] annotations = createAnnotations(constantPool, desc.classAnnotations);
            attributes.put(Attribute.RuntimeInvisibleAnnotations,
                           new RuntimeInvisibleAnnotations_attribute(attributeString, annotations));
        }
        if (desc.runtimeAnnotations != null && !desc.runtimeAnnotations.isEmpty()) {
            int attributeString = addString(constantPool, Attribute.RuntimeVisibleAnnotations);
            Annotation[] annotations = createAnnotations(constantPool, desc.runtimeAnnotations);
            attributes.put(Attribute.RuntimeVisibleAnnotations,
                           new RuntimeVisibleAnnotations_attribute(attributeString, annotations));
        }
    }

    private Annotation[] createAnnotations(List<CPInfo> constantPool, List<AnnotationDescription> desc) {
        Annotation[] result = new Annotation[desc.size()];
        int i = 0;

        for (AnnotationDescription ad : desc) {
            result[i++] = createAnnotation(constantPool, ad);
        }

        return result;
    }

    private Annotation[][] createParameterAnnotations(List<CPInfo> constantPool, List<List<AnnotationDescription>> desc) {
        Annotation[][] result = new Annotation[desc.size()][];
        int i = 0;

        for (List<AnnotationDescription> paramAnnos : desc) {
            result[i++] = createAnnotations(constantPool, paramAnnos);
        }

        return result;
    }

    private Annotation createAnnotation(List<CPInfo> constantPool, AnnotationDescription desc) {
        return new Annotation(null,
                              addString(constantPool, desc.annotationType),
                              createElementPairs(constantPool, desc.values));
    }

    private element_value_pair[] createElementPairs(List<CPInfo> constantPool, Map<String, Object> annotationAttributes) {
        element_value_pair[] pairs = new element_value_pair[annotationAttributes.size()];
        int i = 0;

        for (Entry<String, Object> e : annotationAttributes.entrySet()) {
            int elementNameString = addString(constantPool, e.getKey());
            element_value value = createAttributeValue(constantPool, e.getValue());
            pairs[i++] = new element_value_pair(elementNameString, value);
        }

        return pairs;
    }

    private element_value createAttributeValue(List<CPInfo> constantPool, Object value) {
        Pair<Integer, Character> constantPoolEntry = addConstant(constantPool, value, true);
        if (constantPoolEntry != null) {
            return new Primitive_element_value(constantPoolEntry.fst, constantPoolEntry.snd);
        } else if (value instanceof EnumConstant) {
            EnumConstant ec = (EnumConstant) value;
            return new Enum_element_value(addString(constantPool, ec.type),
                                          addString(constantPool, ec.constant),
                                          'e');
        } else if (value instanceof ClassConstant) {
            ClassConstant cc = (ClassConstant) value;
            return new Class_element_value(addString(constantPool, cc.type), 'c');
        } else if (value instanceof AnnotationDescription) {
            Annotation annotation = createAnnotation(constantPool, ((AnnotationDescription) value));
            return new Annotation_element_value(annotation, '@');
        } else if (value instanceof Collection) {
            @SuppressWarnings("unchecked")
                    Collection<Object> array = (Collection<Object>) value;
            element_value[] values = new element_value[array.size()];
            int i = 0;

            for (Object elem : array) {
                values[i++] = createAttributeValue(constantPool, elem);
            }

            return new Array_element_value(values, '[');
        }
        throw new IllegalStateException(value.getClass().getName());
    }

    private static Pair<Integer, Character> addConstant(List<CPInfo> constantPool, Object value, boolean annotation) {
        if (value instanceof Boolean) {
            return Pair.of(addToCP(constantPool, new CONSTANT_Integer_info(((Boolean) value) ? 1 : 0)), 'Z');
        } else if (value instanceof Byte) {
            return Pair.of(addToCP(constantPool, new CONSTANT_Integer_info((byte) value)), 'B');
        } else if (value instanceof Character) {
            return Pair.of(addToCP(constantPool, new CONSTANT_Integer_info((char) value)), 'C');
        } else if (value instanceof Short) {
            return Pair.of(addToCP(constantPool, new CONSTANT_Integer_info((short) value)), 'S');
        } else if (value instanceof Integer) {
            return Pair.of(addToCP(constantPool, new CONSTANT_Integer_info((int) value)), 'I');
        } else if (value instanceof Long) {
            return Pair.of(addToCP(constantPool, new CONSTANT_Long_info((long) value)), 'J');
        } else if (value instanceof Float) {
            return Pair.of(addToCP(constantPool, new CONSTANT_Float_info((float) value)), 'F');
        } else if (value instanceof Double) {
            return Pair.of(addToCP(constantPool, new CONSTANT_Double_info((double) value)), 'D');
        } else if (value instanceof String) {
            int stringIndex = addString(constantPool, (String) value);
            if (annotation) {
                return Pair.of(stringIndex, 's');
            } else {
                return Pair.of(addToCP(constantPool, new CONSTANT_String_info(null, stringIndex)), 's');
            }
        }

        return null;
    }

    private static int addString(List<CPInfo> constantPool, String string) {
        Assert.checkNonNull(string);

        int i = 0;
        for (CPInfo info : constantPool) {
            if (info instanceof CONSTANT_Utf8_info) {
                if (((CONSTANT_Utf8_info) info).value.equals(string)) {
                    return i;
                }
            }
            i++;
        }

        return addToCP(constantPool, new CONSTANT_Utf8_info(string));
    }

    private static int addToCP(List<CPInfo> constantPool, CPInfo entry) {
        int result = constantPool.size();

        constantPool.add(entry);

        if (entry.size() > 1) {
            constantPool.add(null);
        }

        return result;
    }

    private static int addClass(List<CPInfo> constantPool, String className) {
        int classNameIndex = addString(constantPool, className);

        int i = 0;
        for (CPInfo info : constantPool) {
            if (info instanceof CONSTANT_Class_info) {
                if (((CONSTANT_Class_info) info).name_index == classNameIndex) {
                    return i;
                }
            }
            i++;
        }

        return addToCP(constantPool, new CONSTANT_Class_info(null, classNameIndex));
    }
    //</editor-fold>
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="Create Symbol Description">
    public void createBaseLine(List<VersionDescription> versions, ExcludeIncludeList excludesIncludes, Path descDest, Path jdkRoot) throws IOException {
        ClassList classes = new ClassList();

        for (VersionDescription desc : versions) {
            ClassList currentVersionClasses = new ClassList();
            try (BufferedReader descIn = Files.newBufferedReader(Paths.get(desc.classes))) {
                String classFileData;

                while ((classFileData = descIn.readLine()) != null) {
                    ByteArrayOutputStream data = new ByteArrayOutputStream();
                    for (int i = 0; i < classFileData.length(); i += 2) {
                        data.write(Integer.parseInt(classFileData.substring(i, i + 2), 16));
                    }
                    try (InputStream in = new ByteArrayInputStream(data.toByteArray())) {
                        inspectClassFile(in, currentVersionClasses, excludesIncludes, desc.version);
                    } catch (IOException | ConstantPoolException ex) {
                        throw new IllegalStateException(ex);
                    }
                }
            }

            Set<String> includedClasses = new HashSet<>();
            boolean modified;

            do {
                modified = false;

                for (ClassDescription clazz : currentVersionClasses) {
                    ClassHeaderDescription header = clazz.header.get(0);

                    if (includeEffectiveAccess(currentVersionClasses, clazz)) {
                        modified |= include(includedClasses, currentVersionClasses, clazz.name);
                    }

                    if (includedClasses.contains(clazz.name)) {
                        modified |= include(includedClasses, currentVersionClasses, header.extendsAttr);
                        for (String i : header.implementsAttr) {
                            modified |= include(includedClasses, currentVersionClasses, i);
                        }

                        modified |= includeOutputType(Collections.singleton(header),
                                                      h -> "",
                                                      includedClasses,
                                                      currentVersionClasses);
                        modified |= includeOutputType(clazz.fields,
                                                      f -> f.descriptor,
                                                      includedClasses,
                                                      currentVersionClasses);
                        modified |= includeOutputType(clazz.methods,
                                                      m -> m.descriptor,
                                                      includedClasses,
                                                      currentVersionClasses);
                    }
                }
            } while (modified);

            for (ClassDescription clazz : currentVersionClasses) {
                if (!includedClasses.contains(clazz.name)) {
                    continue;
                }

                ClassHeaderDescription header = clazz.header.get(0);

                if (header.innerClasses != null) {
                    Iterator<InnerClassInfo> innerClassIt = header.innerClasses.iterator();

                    while(innerClassIt.hasNext()) {
                        InnerClassInfo ici = innerClassIt.next();
                        if (!includedClasses.contains(ici.innerClass))
                            innerClassIt.remove();
                    }
                }

                ClassDescription existing = classes.find(clazz.name, true);

                if (existing != null) {
                    addClassHeader(existing, header, desc.version);
                    for (MethodDescription currentMethod : clazz.methods) {
                        addMethod(existing, currentMethod, desc.version);
                    }
                    for (FieldDescription currentField : clazz.fields) {
                        addField(existing, currentField, desc.version);
                    }
                } else {
                    classes.add(clazz);
                }
            }
        }

        classes.sort();

        Map<String, String> package2Modules = buildPackage2Modules(jdkRoot);
        Map<String, List<ClassDescription>> module2Classes = new HashMap<>();

        for (ClassDescription clazz : classes) {
            String pack;
            int lastSlash = clazz.name.lastIndexOf('/');
            if (lastSlash != (-1)) {
                pack = clazz.name.substring(0, lastSlash).replace('/', '.');
            } else {
                pack = "";
            }
            String module = package2Modules.get(pack);

            if (module == null) {
                module = "java.base";

                OUTER: while (!pack.isEmpty()) {
                    for (Entry<String, String> p2M : package2Modules.entrySet()) {
                        if (p2M.getKey().startsWith(pack)) {
                            module = p2M.getValue();
                            break OUTER;
                        }
                    }
                    int dot = pack.lastIndexOf('.');
                    if (dot == (-1))
                        break;
                    pack = pack.substring(0, dot);
                }
            }
            module2Classes.computeIfAbsent(module, m -> new ArrayList<>())
                    .add(clazz);
        }

        Path symbolsFile = descDest.resolve("symbols");

        Files.createDirectories(symbolsFile.getParent());

        try (Writer symbolsOut = Files.newBufferedWriter(symbolsFile)) {
            Map<VersionDescription, List<Path>> outputFiles = new LinkedHashMap<>();

            for (Entry<String, List<ClassDescription>> e : module2Classes.entrySet()) {
                for (VersionDescription desc : versions) {
                    Path f = descDest.resolve(e.getKey() + "-" + desc.version + ".sym.txt");
                    try (Writer out = Files.newBufferedWriter(f)) {
                        for (ClassDescription clazz : e.getValue()) {
                            clazz.write(out, desc.primaryBaseline, desc.version);
                        }
                    }
                    outputFiles.computeIfAbsent(desc, d -> new ArrayList<>())
                               .add(f);
                }
            }
            symbolsOut.append("generate platforms ")
                      .append(versions.stream()
                                      .map(v -> v.version)
                                      .collect(Collectors.joining(":")))
                      .append("\n");
            for (Entry<VersionDescription, List<Path>> versionFileEntry : outputFiles.entrySet()) {
                symbolsOut.append("platform version ")
                          .append(versionFileEntry.getKey().version);
                if (versionFileEntry.getKey().primaryBaseline != null) {
                    symbolsOut.append(" base ")
                              .append(versionFileEntry.getKey().primaryBaseline);
                }
                symbolsOut.append(" files ")
                          .append(versionFileEntry.getValue()
                                                  .stream()
                                                  .map(p -> p.getFileName().toString())
                                                  .sorted()
                                                  .collect(Collectors.joining(":")))
                          .append("\n");
            }
        }
    }

    //<editor-fold defaultstate="collapsed" desc="Class Reading">
    //non-final for tests:
    public static String PROFILE_ANNOTATION = "Ljdk/Profile+Annotation;";
    public static boolean ALLOW_NON_EXISTING_CLASSES = false;

    private void inspectClassFile(InputStream in, ClassList classes, ExcludeIncludeList excludesIncludes, String version) throws IOException, ConstantPoolException {
        ClassFile cf = ClassFile.read(in);

        if (!excludesIncludes.accepts(cf.getName())) {
            return ;
        }

        ClassHeaderDescription headerDesc = new ClassHeaderDescription();

        headerDesc.flags = cf.access_flags.flags;

        if (cf.super_class != 0) {
            headerDesc.extendsAttr = cf.getSuperclassName();
        }
        List<String> interfaces = new ArrayList<>();
        for (int i = 0; i < cf.interfaces.length; i++) {
            interfaces.add(cf.getInterfaceName(i));
        }
        headerDesc.implementsAttr = interfaces;
        for (Attribute attr : cf.attributes) {
            if (!readAttribute(cf, headerDesc, attr))
                return ;
        }

        ClassDescription clazzDesc = null;

        for (ClassDescription cd : classes) {
            if (cd.name.equals(cf.getName())) {
                clazzDesc = cd;
                break;
            }
        }

        if (clazzDesc == null) {
            clazzDesc = new ClassDescription();
            clazzDesc.name = cf.getName();
            classes.add(clazzDesc);
        }

        addClassHeader(clazzDesc, headerDesc, version);

        for (Method m : cf.methods) {
            if (!include(m.access_flags.flags))
                continue;
            MethodDescription methDesc = new MethodDescription();
            methDesc.flags = m.access_flags.flags;
            methDesc.name = m.getName(cf.constant_pool);
            methDesc.descriptor = m.descriptor.getValue(cf.constant_pool);
            for (Attribute attr : m.attributes) {
                readAttribute(cf, methDesc, attr);
            }
            addMethod(clazzDesc, methDesc, version);
        }
        for (Field f : cf.fields) {
            if (!include(f.access_flags.flags))
                continue;
            FieldDescription fieldDesc = new FieldDescription();
            fieldDesc.flags = f.access_flags.flags;
            fieldDesc.name = f.getName(cf.constant_pool);
            fieldDesc.descriptor = f.descriptor.getValue(cf.constant_pool);
            for (Attribute attr : f.attributes) {
                readAttribute(cf, fieldDesc, attr);
            }
            addField(clazzDesc, fieldDesc, version);
        }
    }

    private boolean include(int accessFlags) {
        return (accessFlags & (AccessFlags.ACC_PUBLIC | AccessFlags.ACC_PROTECTED)) != 0;
    }

    private void addClassHeader(ClassDescription clazzDesc, ClassHeaderDescription headerDesc, String version) {
        //normalize:
        boolean existed = false;
        for (ClassHeaderDescription existing : clazzDesc.header) {
            if (existing.equals(headerDesc)) {
                headerDesc = existing;
                existed = true;
            } else {
                //check if the only difference between the 7 and 8 version is the Profile annotation
                //if so, copy it to the pre-8 version, so save space
                List<AnnotationDescription> annots = headerDesc.classAnnotations;

                if (annots != null) {
                    for (AnnotationDescription ad : annots) {
                        if (PROFILE_ANNOTATION.equals(ad.annotationType)) {
                            annots.remove(ad);
                            if (existing.equals(headerDesc)) {
                                headerDesc = existing;
                                annots = headerDesc.classAnnotations;
                                if (annots == null) {
                                    headerDesc.classAnnotations = annots = new ArrayList<>();
                                }
                                annots.add(ad);
                                existed = true;
                            } else {
                                annots.add(ad);
                            }
                            break;
                        }
                    }
                }
            }
        }

        headerDesc.versions += version;

        if (!existed) {
            clazzDesc.header.add(headerDesc);
        }
    }

    private void addMethod(ClassDescription clazzDesc, MethodDescription methDesc, String version) {
        //normalize:
        boolean methodExisted = false;
        for (MethodDescription existing : clazzDesc.methods) {
            if (existing.equals(methDesc)) {
                methodExisted = true;
                methDesc = existing;
                break;
            }
        }
        methDesc.versions += version;
        if (!methodExisted) {
            clazzDesc.methods.add(methDesc);
        }
    }

    private void addField(ClassDescription clazzDesc, FieldDescription fieldDesc, String version) {
        boolean fieldExisted = false;
        for (FieldDescription existing : clazzDesc.fields) {
            if (existing.equals(fieldDesc)) {
                fieldExisted = true;
                fieldDesc = existing;
                break;
            }
        }
        fieldDesc.versions += version;
        if (!fieldExisted) {
            clazzDesc.fields.add(fieldDesc);
        }
    }

    private boolean readAttribute(ClassFile cf, FeatureDescription feature, Attribute attr) throws ConstantPoolException {
        String attrName = attr.getName(cf.constant_pool);
        switch (attrName) {
            case Attribute.AnnotationDefault:
                assert feature instanceof MethodDescription;
                element_value defaultValue = ((AnnotationDefault_attribute) attr).default_value;
                ((MethodDescription) feature).annotationDefaultValue =
                        convertElementValue(cf.constant_pool, defaultValue);
                break;
            case "Deprecated":
                feature.deprecated = true;
                break;
            case "Exceptions":
                assert feature instanceof MethodDescription;
                List<String> thrownTypes = new ArrayList<>();
                Exceptions_attribute exceptionAttr = (Exceptions_attribute) attr;
                for (int i = 0; i < exceptionAttr.exception_index_table.length; i++) {
                    thrownTypes.add(exceptionAttr.getException(i, cf.constant_pool));
                }
                ((MethodDescription) feature).thrownTypes = thrownTypes;
                break;
            case Attribute.InnerClasses:
                assert feature instanceof ClassHeaderDescription;
                List<InnerClassInfo> innerClasses = new ArrayList<>();
                InnerClasses_attribute innerClassesAttr = (InnerClasses_attribute) attr;
                for (int i = 0; i < innerClassesAttr.number_of_classes; i++) {
                    CONSTANT_Class_info outerClassInfo =
                            innerClassesAttr.classes[i].getOuterClassInfo(cf.constant_pool);
                    InnerClassInfo info = new InnerClassInfo();
                    CONSTANT_Class_info innerClassInfo =
                            innerClassesAttr.classes[i].getInnerClassInfo(cf.constant_pool);
                    info.innerClass = innerClassInfo != null ? innerClassInfo.getName() : null;
                    info.outerClass = outerClassInfo != null ? outerClassInfo.getName() : null;
                    info.innerClassName = innerClassesAttr.classes[i].getInnerName(cf.constant_pool);
                    info.innerClassFlags = innerClassesAttr.classes[i].inner_class_access_flags.flags;
                    innerClasses.add(info);
                }
                ((ClassHeaderDescription) feature).innerClasses = innerClasses;
                break;
            case "RuntimeInvisibleAnnotations":
                feature.classAnnotations = annotations2Description(cf.constant_pool, attr);
                break;
            case "RuntimeVisibleAnnotations":
                feature.runtimeAnnotations = annotations2Description(cf.constant_pool, attr);
                break;
            case "Signature":
                feature.signature = ((Signature_attribute) attr).getSignature(cf.constant_pool);
                break;
            case "ConstantValue":
                assert feature instanceof FieldDescription;
                Object value = convertConstantValue(cf.constant_pool.get(((ConstantValue_attribute) attr).constantvalue_index), ((FieldDescription) feature).descriptor);
                if (((FieldDescription) feature).descriptor.equals("C")) {
                    value = (char) (int) value;
                }
                ((FieldDescription) feature).constantValue = value;
                break;
            case "SourceFile":
                //ignore, not needed
                break;
            case "BootstrapMethods":
                //ignore, not needed
                break;
            case "Code":
                //ignore, not needed
                break;
            case "EnclosingMethod":
                return false;
            case "Synthetic":
                break;
            case "RuntimeVisibleParameterAnnotations":
                assert feature instanceof MethodDescription;
                ((MethodDescription) feature).runtimeParameterAnnotations =
                        parameterAnnotations2Description(cf.constant_pool, attr);
                break;
            case "RuntimeInvisibleParameterAnnotations":
                assert feature instanceof MethodDescription;
                ((MethodDescription) feature).classParameterAnnotations =
                        parameterAnnotations2Description(cf.constant_pool, attr);
                break;
            default:
                throw new IllegalStateException("Unhandled attribute: " + attrName);
        }

        return true;
    }

    Object convertConstantValue(CPInfo info, String descriptor) throws ConstantPoolException {
        if (info instanceof CONSTANT_Integer_info) {
            if ("Z".equals(descriptor))
                return ((CONSTANT_Integer_info) info).value == 1;
            else
                return ((CONSTANT_Integer_info) info).value;
        } else if (info instanceof CONSTANT_Long_info) {
            return ((CONSTANT_Long_info) info).value;
        } else if (info instanceof CONSTANT_Float_info) {
            return ((CONSTANT_Float_info) info).value;
        } else if (info instanceof CONSTANT_Double_info) {
            return ((CONSTANT_Double_info) info).value;
        } else if (info instanceof CONSTANT_String_info) {
            return ((CONSTANT_String_info) info).getString();
        }
        throw new IllegalStateException(info.getClass().getName());
    }

    Object convertElementValue(ConstantPool cp, element_value val) throws InvalidIndex, ConstantPoolException {
        switch (val.tag) {
            case 'Z':
                return ((CONSTANT_Integer_info) cp.get(((Primitive_element_value) val).const_value_index)).value != 0;
            case 'B':
                return (byte) ((CONSTANT_Integer_info) cp.get(((Primitive_element_value) val).const_value_index)).value;
            case 'C':
                return (char) ((CONSTANT_Integer_info) cp.get(((Primitive_element_value) val).const_value_index)).value;
            case 'S':
                return (short) ((CONSTANT_Integer_info) cp.get(((Primitive_element_value) val).const_value_index)).value;
            case 'I':
                return ((CONSTANT_Integer_info) cp.get(((Primitive_element_value) val).const_value_index)).value;
            case 'J':
                return ((CONSTANT_Long_info) cp.get(((Primitive_element_value) val).const_value_index)).value;
            case 'F':
                return ((CONSTANT_Float_info) cp.get(((Primitive_element_value) val).const_value_index)).value;
            case 'D':
                return ((CONSTANT_Double_info) cp.get(((Primitive_element_value) val).const_value_index)).value;
            case 's':
                return ((CONSTANT_Utf8_info) cp.get(((Primitive_element_value) val).const_value_index)).value;

            case 'e':
                return new EnumConstant(cp.getUTF8Value(((Enum_element_value) val).type_name_index),
                        cp.getUTF8Value(((Enum_element_value) val).const_name_index));
            case 'c':
                return new ClassConstant(cp.getUTF8Value(((Class_element_value) val).class_info_index));

            case '@':
                return annotation2Description(cp, ((Annotation_element_value) val).annotation_value);

            case '[':
                List<Object> values = new ArrayList<>();
                for (element_value elem : ((Array_element_value) val).values) {
                    values.add(convertElementValue(cp, elem));
                }
                return values;
            default:
                throw new IllegalStateException("Currently unhandled tag: " + val.tag);
        }
    }

    private List<AnnotationDescription> annotations2Description(ConstantPool cp, Attribute attr) throws ConstantPoolException {
        RuntimeAnnotations_attribute annotationsAttr = (RuntimeAnnotations_attribute) attr;
        List<AnnotationDescription> descs = new ArrayList<>();
        for (Annotation a : annotationsAttr.annotations) {
            descs.add(annotation2Description(cp, a));
        }
        return descs;
    }

    private List<List<AnnotationDescription>> parameterAnnotations2Description(ConstantPool cp, Attribute attr) throws ConstantPoolException {
        RuntimeParameterAnnotations_attribute annotationsAttr =
                (RuntimeParameterAnnotations_attribute) attr;
        List<List<AnnotationDescription>> descs = new ArrayList<>();
        for (Annotation[] attrAnnos : annotationsAttr.parameter_annotations) {
            List<AnnotationDescription> paramDescs = new ArrayList<>();
            for (Annotation ann : attrAnnos) {
                paramDescs.add(annotation2Description(cp, ann));
            }
            descs.add(paramDescs);
        }
        return descs;
    }

    private AnnotationDescription annotation2Description(ConstantPool cp, Annotation a) throws ConstantPoolException {
        String annotationType = cp.getUTF8Value(a.type_index);
        Map<String, Object> values = new HashMap<>();

        for (element_value_pair e : a.element_value_pairs) {
            values.put(cp.getUTF8Value(e.element_name_index), convertElementValue(cp, e.value));
        }

        return new AnnotationDescription(annotationType, values);
    }
    //</editor-fold>

    protected boolean includeEffectiveAccess(ClassList classes, ClassDescription clazz) {
        if (!include(clazz.header.get(0).flags))
            return false;
        for (ClassDescription outer : classes.enclosingClasses(clazz)) {
            if (!include(outer.header.get(0).flags))
                return false;
        }
        return true;
    }

    boolean include(Set<String> includedClasses, ClassList classes, String clazzName) {
        if (clazzName == null)
            return false;

        boolean modified = includedClasses.add(clazzName);

        for (ClassDescription outer : classes.enclosingClasses(classes.find(clazzName, true))) {
            modified |= includedClasses.add(outer.name);
        }

        return modified;
    }

    <T extends FeatureDescription> boolean includeOutputType(Iterable<T> features,
                                                             Function<T, String> feature2Descriptor,
                                                             Set<String> includedClasses,
                                                             ClassList classes) {
        boolean modified = false;

        for (T feature : features) {
            CharSequence sig =
                    feature.signature != null ? feature.signature : feature2Descriptor.apply(feature);
            Matcher m = OUTPUT_TYPE_PATTERN.matcher(sig);
            while (m.find()) {
                modified |= include(includedClasses, classes, m.group(1));
            }
        }

        return modified;
    }

    static final Pattern OUTPUT_TYPE_PATTERN = Pattern.compile("L([^;<]+)(;|<)");

    Map<String, String> buildPackage2Modules(Path jdkRoot) throws IOException {
        if (jdkRoot == null) //in tests
            return Collections.emptyMap();

        Map<String, String> result = new HashMap<>();
        try (DirectoryStream<Path> repositories = Files.newDirectoryStream(jdkRoot)) {
            for (Path repository : repositories) {
                Path src = repository.resolve("src");
                if (!Files.isDirectory(src))
                    continue;
                try (DirectoryStream<Path> modules = Files.newDirectoryStream(src)) {
                    for (Path module : modules) {
                        Path shareClasses = module.resolve("share/classes");

                        if (!Files.isDirectory(shareClasses))
                            continue;

                        Set<String> packages = new HashSet<>();

                        packages(shareClasses, new StringBuilder(), packages);

                        for (String p : packages) {
                            if (result.containsKey(p))
                                throw new IllegalStateException("Duplicate package mapping.");
                            result.put(p, module.getFileName().toString());
                        }
                    }
                }
            }
        }

        return result;
    }

    void packages(Path dir, StringBuilder soFar, Set<String> packages) throws IOException {
        try (DirectoryStream<Path> c = Files.newDirectoryStream(dir)) {
            for (Path f : c) {
                if (Files.isReadable(f) && f.getFileName().toString().endsWith(".java")) {
                    packages.add(soFar.toString());
                }
                if (Files.isDirectory(f)) {
                    int len = soFar.length();
                    if (len > 0) soFar.append(".");
                    soFar.append(f.getFileName().toString());
                    packages(f, soFar, packages);
                    soFar.delete(len, soFar.length());
                }
            }
        }
    }

    public static class VersionDescription {
        public final String classes;
        public final String version;
        public final String primaryBaseline;

        public VersionDescription(String classes, String version, String primaryBaseline) {
            this.classes = classes;
            this.version = version;
            this.primaryBaseline = "<none>".equals(primaryBaseline) ? null : primaryBaseline;
        }

    }

    public static class ExcludeIncludeList {
        public final Set<String> includeList;
        public final Set<String> excludeList;

        protected ExcludeIncludeList(Set<String> includeList, Set<String> excludeList) {
            this.includeList = includeList;
            this.excludeList = excludeList;
        }

        public static ExcludeIncludeList create(String files) throws IOException {
            Set<String> includeList = new HashSet<>();
            Set<String> excludeList = new HashSet<>();
            for (String file : files.split(File.pathSeparator)) {
                try (Stream<String> lines = Files.lines(Paths.get(file))) {
                    lines.map(l -> l.substring(0, l.indexOf('#') != (-1) ? l.indexOf('#') : l.length()))
                         .filter(l -> !l.trim().isEmpty())
                         .forEach(l -> {
                             Set<String> target = l.startsWith("+") ? includeList : excludeList;
                             target.add(l.substring(1));
                         });
                }
            }
            return new ExcludeIncludeList(includeList, excludeList);
        }

        public boolean accepts(String className) {
            return matches(includeList, className) && !matches(excludeList, className);
        }

        private static boolean matches(Set<String> list, String className) {
            if (list.contains(className))
                return true;
            String pack = className.substring(0, className.lastIndexOf('/') + 1);
            return list.contains(pack);
        }
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="Class Data Structures">
    static abstract class FeatureDescription {
        int flags;
        boolean deprecated;
        String signature;
        String versions = "";
        List<AnnotationDescription> classAnnotations;
        List<AnnotationDescription> runtimeAnnotations;

        protected void writeAttributes(Appendable output) throws IOException {
            if (flags != 0)
                output.append(" flags " + Integer.toHexString(flags));
            if (deprecated) {
                output.append(" deprecated true");
            }
            if (signature != null) {
                output.append(" signature " + quote(signature, false));
            }
            if (classAnnotations != null && !classAnnotations.isEmpty()) {
                output.append(" classAnnotations ");
                for (AnnotationDescription a : classAnnotations) {
                    output.append(quote(a.toString(), false));
                }
            }
            if (runtimeAnnotations != null && !runtimeAnnotations.isEmpty()) {
                output.append(" runtimeAnnotations ");
                for (AnnotationDescription a : runtimeAnnotations) {
                    output.append(quote(a.toString(), false));
                }
            }
        }

        protected boolean shouldIgnore(String baselineVersion, String version) {
            return (!versions.contains(version) &&
                    (baselineVersion == null || !versions.contains(baselineVersion))) ||
                   (baselineVersion != null &&
                    versions.contains(baselineVersion) && versions.contains(version));
        }

        public abstract void write(Appendable output, String baselineVersion, String version) throws IOException;

        protected void readAttributes(LineBasedReader reader) {
            String inFlags = reader.attributes.get("flags");
            if (inFlags != null && !inFlags.isEmpty()) {
                flags = Integer.parseInt(inFlags, 16);
            }
            String inDeprecated = reader.attributes.get("deprecated");
            if ("true".equals(inDeprecated)) {
                deprecated = true;
            }
            signature = reader.attributes.get("signature");
            String inClassAnnotations = reader.attributes.get("classAnnotations");
            if (inClassAnnotations != null) {
                classAnnotations = parseAnnotations(unquote(inClassAnnotations), new int[1]);
            }
            String inRuntimeAnnotations = reader.attributes.get("runtimeAnnotations");
            if (inRuntimeAnnotations != null) {
                runtimeAnnotations = parseAnnotations(unquote(inRuntimeAnnotations), new int[1]);
            }
        }

        public abstract boolean read(LineBasedReader reader) throws IOException;

        @Override
        public int hashCode() {
            int hash = 3;
            hash = 89 * hash + this.flags;
            hash = 89 * hash + (this.deprecated ? 1 : 0);
            hash = 89 * hash + Objects.hashCode(this.signature);
            hash = 89 * hash + listHashCode(this.classAnnotations);
            hash = 89 * hash + listHashCode(this.runtimeAnnotations);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final FeatureDescription other = (FeatureDescription) obj;
            if (this.flags != other.flags) {
                return false;
            }
            if (this.deprecated != other.deprecated) {
                return false;
            }
            if (!Objects.equals(this.signature, other.signature)) {
                return false;
            }
            if (!listEquals(this.classAnnotations, other.classAnnotations)) {
                return false;
            }
            if (!listEquals(this.runtimeAnnotations, other.runtimeAnnotations)) {
                return false;
            }
            return true;
        }

    }

    public static class ClassDescription {
        String name;
        List<ClassHeaderDescription> header = new ArrayList<>();
        List<MethodDescription> methods = new ArrayList<>();
        List<FieldDescription> fields = new ArrayList<>();

        public void write(Appendable output, String baselineVersion, String version) throws IOException {
            boolean inBaseline = false;
            boolean inVersion = false;
            for (ClassHeaderDescription chd : header) {
                if (baselineVersion != null && chd.versions.contains(baselineVersion)) {
                    inBaseline = true;
                }
                if (chd.versions.contains(version)) {
                    inVersion = true;
                }
            }
            if (!inVersion && !inBaseline)
                return ;
            if (!inVersion) {
                output.append("-class name " + name + "\n\n");
                return;
            }
            boolean hasChange = hasChange(header, version, baselineVersion) ||
                                hasChange(fields, version, baselineVersion) ||
                                hasChange(methods, version, baselineVersion);
            if (!hasChange)
                return;

            output.append("class name " + name + "\n");
            for (ClassHeaderDescription header : header) {
                header.write(output, baselineVersion, version);
            }
            for (FieldDescription field : fields) {
                field.write(output, baselineVersion, version);
            }
            for (MethodDescription method : methods) {
                method.write(output, baselineVersion, version);
            }
            output.append("\n");
        }

        boolean hasChange(List<? extends FeatureDescription> hasChange, String version, String baselineVersion) {
            return hasChange.stream()
                            .map(fd -> fd.versions)
                            .anyMatch(versions -> versions.contains(version) ^
                                                  (baselineVersion != null &&
                                                   versions.contains(baselineVersion)));
        }

        public void read(LineBasedReader reader, String baselineVersion, String version) throws IOException {
            if (!"class".equals(reader.lineKey))
                return ;

            name = reader.attributes.get("name");

            reader.moveNext();

            OUTER: while (reader.hasNext()) {
                switch (reader.lineKey) {
                    case "header":
                        removeVersion(header, h -> true, version);
                        ClassHeaderDescription chd = new ClassHeaderDescription();
                        chd.read(reader);
                        chd.versions = version;
                        header.add(chd);
                        break;
                    case "field":
                        FieldDescription field = new FieldDescription();
                        field.read(reader);
                        field.versions += version;
                        fields.add(field);
                        break;
                    case "-field": {
                        removeVersion(fields,
                                      f -> Objects.equals(f.name, reader.attributes.get("name")) &&
                                           Objects.equals(f.descriptor, reader.attributes.get("descriptor")),
                                      version);
                        reader.moveNext();
                        break;
                    }
                    case "method":
                        MethodDescription method = new MethodDescription();
                        method.read(reader);
                        method.versions += version;
                        methods.add(method);
                        break;
                    case "-method": {
                        removeVersion(methods,
                                      m -> Objects.equals(m.name, reader.attributes.get("name")) &&
                                           Objects.equals(m.descriptor, reader.attributes.get("descriptor")),
                                      version);
                        reader.moveNext();
                        break;
                    }
                    case "class":
                    case "-class":
                        break OUTER;
                    default:
                        throw new IllegalStateException(reader.lineKey);
                }
            }
        }
    }

    static class ClassHeaderDescription extends FeatureDescription {
        String extendsAttr;
        List<String> implementsAttr;
        List<InnerClassInfo> innerClasses;

        @Override
        public int hashCode() {
            int hash = super.hashCode();
            hash = 17 * hash + Objects.hashCode(this.extendsAttr);
            hash = 17 * hash + Objects.hashCode(this.implementsAttr);
            hash = 17 * hash + Objects.hashCode(this.innerClasses);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }
            if (!super.equals(obj)) {
                return false;
            }
            final ClassHeaderDescription other = (ClassHeaderDescription) obj;
            if (!Objects.equals(this.extendsAttr, other.extendsAttr)) {
                return false;
            }
            if (!Objects.equals(this.implementsAttr, other.implementsAttr)) {
                return false;
            }
            if (!listEquals(this.innerClasses, other.innerClasses)) {
                return false;
            }
            return true;
        }

        @Override
        public void write(Appendable output, String baselineVersion, String version) throws IOException {
            if (!versions.contains(version) ||
                (baselineVersion != null && versions.contains(baselineVersion) && versions.contains(version)))
                return ;
            output.append("header");
            if (extendsAttr != null)
                output.append(" extends " + extendsAttr);
            if (implementsAttr != null && !implementsAttr.isEmpty())
                output.append(" implements " + serializeList(implementsAttr));
            writeAttributes(output);
            output.append("\n");
            if (innerClasses != null && !innerClasses.isEmpty()) {
                for (InnerClassInfo ici : innerClasses) {
                    output.append("innerclass");
                    output.append(" innerClass " + ici.innerClass);
                    output.append(" outerClass " + ici.outerClass);
                    output.append(" innerClassName " + ici.innerClassName);
                    output.append(" flags " + Integer.toHexString(ici.innerClassFlags));
                    output.append("\n");
                }
            }
        }

        @Override
        public boolean read(LineBasedReader reader) throws IOException {
            if (!"header".equals(reader.lineKey))
                return false;

            extendsAttr = reader.attributes.get("extends");
            implementsAttr = deserializeList(reader.attributes.get("implements"));

            readAttributes(reader);

            innerClasses = new ArrayList<>();

            reader.moveNext();

            while ("innerclass".equals(reader.lineKey)) {
                InnerClassInfo info = new InnerClassInfo();

                info.innerClass = reader.attributes.get("innerClass");
                info.outerClass = reader.attributes.get("outerClass");
                info.innerClassName = reader.attributes.get("innerClassName");

                String inFlags = reader.attributes.get("flags");
                if (inFlags != null && !inFlags.isEmpty())
                    info.innerClassFlags = Integer.parseInt(inFlags, 16);

                innerClasses.add(info);

                reader.moveNext();
            }

            return true;
        }

    }

    static class MethodDescription extends FeatureDescription {
        String name;
        String descriptor;
        List<String> thrownTypes;
        Object annotationDefaultValue;
        List<List<AnnotationDescription>> classParameterAnnotations;
        List<List<AnnotationDescription>> runtimeParameterAnnotations;

        @Override
        public int hashCode() {
            int hash = super.hashCode();
            hash = 59 * hash + Objects.hashCode(this.name);
            hash = 59 * hash + Objects.hashCode(this.descriptor);
            hash = 59 * hash + Objects.hashCode(this.thrownTypes);
            hash = 59 * hash + Objects.hashCode(this.annotationDefaultValue);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }
            if (!super.equals(obj)) {
                return false;
            }
            final MethodDescription other = (MethodDescription) obj;
            if (!Objects.equals(this.name, other.name)) {
                return false;
            }
            if (!Objects.equals(this.descriptor, other.descriptor)) {
                return false;
            }
            if (!Objects.equals(this.thrownTypes, other.thrownTypes)) {
                return false;
            }
            if (!Objects.equals(this.annotationDefaultValue, other.annotationDefaultValue)) {
                return false;
            }
            return true;
        }

        @Override
        public void write(Appendable output, String baselineVersion, String version) throws IOException {
            if (shouldIgnore(baselineVersion, version))
                return ;
            if (!versions.contains(version)) {
                output.append("-method");
                output.append(" name " + quote(name, false));
                output.append(" descriptor " + quote(descriptor, false));
                output.append("\n");
                return ;
            }
            output.append("method");
            output.append(" name " + quote(name, false));
            output.append(" descriptor " + quote(descriptor, false));
            if (thrownTypes != null)
                output.append(" thrownTypes " + serializeList(thrownTypes));
            if (annotationDefaultValue != null)
                output.append(" annotationDefaultValue " + quote(AnnotationDescription.dumpAnnotationValue(annotationDefaultValue), false));
            writeAttributes(output);
            if (classParameterAnnotations != null && !classParameterAnnotations.isEmpty()) {
                output.append(" classParameterAnnotations ");
                for (List<AnnotationDescription> pa : classParameterAnnotations) {
                    for (AnnotationDescription a : pa) {
                        output.append(quote(a.toString(), false));
                    }
                    output.append(";");
                }
            }
            if (runtimeParameterAnnotations != null && !runtimeParameterAnnotations.isEmpty()) {
                output.append(" runtimeParameterAnnotations ");
                for (List<AnnotationDescription> pa : runtimeParameterAnnotations) {
                    for (AnnotationDescription a : pa) {
                        output.append(quote(a.toString(), false));
                    }
                    output.append(";");
                }
            }
            output.append("\n");
        }

        @Override
        public boolean read(LineBasedReader reader) throws IOException {
            if (!"method".equals(reader.lineKey))
                return false;

            name = reader.attributes.get("name");
            descriptor = reader.attributes.get("descriptor");

            thrownTypes = deserializeList(reader.attributes.get("thrownTypes"));

            String inAnnotationDefaultValue = reader.attributes.get("annotationDefaultValue");

            if (inAnnotationDefaultValue != null) {
                annotationDefaultValue = parseAnnotationValue(inAnnotationDefaultValue, new int[1]);
            }

            readAttributes(reader);

            String inClassParamAnnotations = reader.attributes.get("classParameterAnnotations");
            if (inClassParamAnnotations != null) {
                List<List<AnnotationDescription>> annos = new ArrayList<>();
                int[] pointer = new int[1];
                do {
                    annos.add(parseAnnotations(inClassParamAnnotations, pointer));
                    assert pointer[0] == inClassParamAnnotations.length() || inClassParamAnnotations.charAt(pointer[0]) == ';';
                } while (++pointer[0] < inClassParamAnnotations.length());
                classParameterAnnotations = annos;
            }

            String inRuntimeParamAnnotations = reader.attributes.get("runtimeParameterAnnotations");
            if (inRuntimeParamAnnotations != null) {
                List<List<AnnotationDescription>> annos = new ArrayList<>();
                int[] pointer = new int[1];
                do {
                    annos.add(parseAnnotations(inRuntimeParamAnnotations, pointer));
                    assert pointer[0] == inRuntimeParamAnnotations.length() || inRuntimeParamAnnotations.charAt(pointer[0]) == ';';
                } while (++pointer[0] < inRuntimeParamAnnotations.length());
                runtimeParameterAnnotations = annos;
            }

            reader.moveNext();

            return true;
        }

    }

    static class FieldDescription extends FeatureDescription {
        String name;
        String descriptor;
        Object constantValue;

        @Override
        public int hashCode() {
            int hash = super.hashCode();
            hash = 59 * hash + Objects.hashCode(this.name);
            hash = 59 * hash + Objects.hashCode(this.descriptor);
            hash = 59 * hash + Objects.hashCode(this.constantValue);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }
            if (!super.equals(obj)) {
                return false;
            }
            final FieldDescription other = (FieldDescription) obj;
            if (!Objects.equals(this.name, other.name)) {
                return false;
            }
            if (!Objects.equals(this.descriptor, other.descriptor)) {
                return false;
            }
            if (!Objects.equals(this.constantValue, other.constantValue)) {
                return false;
            }
            return true;
        }

        @Override
        public void write(Appendable output, String baselineVersion, String version) throws IOException {
            if (shouldIgnore(baselineVersion, version))
                return ;
            if (!versions.contains(version)) {
                output.append("-field");
                output.append(" name " + quote(name, false));
                output.append(" descriptor " + quote(descriptor, false));
                output.append("\n");
                return ;
            }
            output.append("field");
            output.append(" name " + name);
            output.append(" descriptor " + descriptor);
            if (constantValue != null) {
                output.append(" constantValue " + quote(constantValue.toString(), false));
            }
            writeAttributes(output);
            output.append("\n");
        }

        @Override
        public boolean read(LineBasedReader reader) throws IOException {
            if (!"field".equals(reader.lineKey))
                return false;

            name = reader.attributes.get("name");
            descriptor = reader.attributes.get("descriptor");

            String inConstantValue = reader.attributes.get("constantValue");

            if (inConstantValue != null) {
                switch (descriptor) {
                    case "Z": constantValue = "true".equals(inConstantValue); break;
                    case "B": constantValue = Byte.parseByte(inConstantValue); break;
                    case "C": constantValue = inConstantValue.charAt(0); break;
                    case "S": constantValue = Short.parseShort(inConstantValue); break;
                    case "I": constantValue = Integer.parseInt(inConstantValue); break;
                    case "J": constantValue = Long.parseLong(inConstantValue); break;
                    case "F": constantValue = Float.parseFloat(inConstantValue); break;
                    case "D": constantValue = Double.parseDouble(inConstantValue); break;
                    case "Ljava/lang/String;": constantValue = inConstantValue; break;
                    default:
                        throw new IllegalStateException("Unrecognized field type: " + descriptor);
                }
            }

            readAttributes(reader);

            reader.moveNext();

            return true;
        }

    }

    static final class AnnotationDescription {
        String annotationType;
        Map<String, Object> values;

        public AnnotationDescription(String annotationType, Map<String, Object> values) {
            this.annotationType = annotationType;
            this.values = values;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 47 * hash + Objects.hashCode(this.annotationType);
            hash = 47 * hash + Objects.hashCode(this.values);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final AnnotationDescription other = (AnnotationDescription) obj;
            if (!Objects.equals(this.annotationType, other.annotationType)) {
                return false;
            }
            if (!Objects.equals(this.values, other.values)) {
                return false;
            }
            return true;
        }

        @Override
        public String toString() {
            StringBuilder result = new StringBuilder();
            result.append("@" + annotationType);
            if (!values.isEmpty()) {
                result.append("(");
                boolean first = true;
                for (Entry<String, Object> e : values.entrySet()) {
                    if (!first) {
                        result.append(",");
                    }
                    first = false;
                    result.append(e.getKey());
                    result.append("=");
                    result.append(dumpAnnotationValue(e.getValue()));
                    result.append("");
                }
                result.append(")");
            }
            return result.toString();
        }

        private static String dumpAnnotationValue(Object value) {
            if (value instanceof List) {
                StringBuilder result = new StringBuilder();

                result.append("{");

                for (Object element : ((List) value)) {
                    result.append(dumpAnnotationValue(element));
                }

                result.append("}");

                return result.toString();
            }

            if (value instanceof String) {
                return "\"" + quote((String) value, true) + "\"";
            } else if (value instanceof Boolean) {
                return "Z" + value;
            } else if (value instanceof Byte) {
                return "B" + value;
            } if (value instanceof Character) {
                return "C" + value;
            } if (value instanceof Short) {
                return "S" + value;
            } if (value instanceof Integer) {
                return "I" + value;
            } if (value instanceof Long) {
                return "J" + value;
            } if (value instanceof Float) {
                return "F" + value;
            } if (value instanceof Double) {
                return "D" + value;
            } else {
                return value.toString();
            }
        }
    }

    static final class EnumConstant {
        String type;
        String constant;

        public EnumConstant(String type, String constant) {
            this.type = type;
            this.constant = constant;
        }

        @Override
        public String toString() {
            return "e" + type + constant + ";";
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 19 * hash + Objects.hashCode(this.type);
            hash = 19 * hash + Objects.hashCode(this.constant);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final EnumConstant other = (EnumConstant) obj;
            if (!Objects.equals(this.type, other.type)) {
                return false;
            }
            if (!Objects.equals(this.constant, other.constant)) {
                return false;
            }
            return true;
        }

    }

    static final class ClassConstant {
        String type;

        public ClassConstant(String type) {
            this.type = type;
        }

        @Override
        public String toString() {
            return "c" + type;
        }

        @Override
        public int hashCode() {
            int hash = 3;
            hash = 53 * hash + Objects.hashCode(this.type);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final ClassConstant other = (ClassConstant) obj;
            if (!Objects.equals(this.type, other.type)) {
                return false;
            }
            return true;
        }

    }

    static final class InnerClassInfo {
        String innerClass;
        String outerClass;
        String innerClassName;
        int    innerClassFlags;

        @Override
        public int hashCode() {
            int hash = 3;
            hash = 11 * hash + Objects.hashCode(this.innerClass);
            hash = 11 * hash + Objects.hashCode(this.outerClass);
            hash = 11 * hash + Objects.hashCode(this.innerClassName);
            hash = 11 * hash + Objects.hashCode(this.innerClassFlags);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final InnerClassInfo other = (InnerClassInfo) obj;
            if (!Objects.equals(this.innerClass, other.innerClass)) {
                return false;
            }
            if (!Objects.equals(this.outerClass, other.outerClass)) {
                return false;
            }
            if (!Objects.equals(this.innerClassName, other.innerClassName)) {
                return false;
            }
            if (!Objects.equals(this.innerClassFlags, other.innerClassFlags)) {
                return false;
            }
            return true;
        }

    }

    public static final class ClassList implements Iterable<ClassDescription> {
        private final List<ClassDescription> classes = new ArrayList<>();
        private final Map<String, ClassDescription> name2Class = new HashMap<>();
        private final Map<ClassDescription, ClassDescription> inner2Outter = new HashMap<>();

        @Override
        public Iterator<ClassDescription> iterator() {
            return classes.iterator();
        }

        public void add(ClassDescription desc) {
            classes.add(desc);
            name2Class.put(desc.name, desc);
        }

        public ClassDescription find(String name) {
            return find(name, ALLOW_NON_EXISTING_CLASSES);
        }

        public ClassDescription find(String name, boolean allowNull) {
            ClassDescription desc = name2Class.get(name);

            if (desc != null || allowNull)
                return desc;

            throw new IllegalStateException("Cannot find: " + name);
        }

        private static final ClassDescription NONE = new ClassDescription();

        public ClassDescription enclosingClass(ClassDescription clazz) {
            if (clazz == null)
                return null;
            ClassDescription desc = inner2Outter.computeIfAbsent(clazz, c -> {
                ClassHeaderDescription header = clazz.header.get(0);

                if (header.innerClasses != null) {
                    for (InnerClassInfo ici : header.innerClasses) {
                        if (ici.innerClass.equals(clazz.name)) {
                            return find(ici.outerClass);
                        }
                    }
                }

                return NONE;
            });

            return desc != NONE ? desc : null;
        }

        public Iterable<ClassDescription> enclosingClasses(ClassDescription clazz) {
            List<ClassDescription> result = new ArrayList<>();
            ClassDescription outer = enclosingClass(clazz);

            while (outer != null) {
                result.add(outer);
                outer = enclosingClass(outer);
            }

            return result;
        }

        public void sort() {
            Collections.sort(classes, (cd1, cd2) -> cd1.name.compareTo(cd2.name));
        }
    }

    private static int listHashCode(Collection<?> c) {
        return c == null || c.isEmpty() ? 0 : c.hashCode();
    }

    private static boolean listEquals(Collection<?> c1, Collection<?> c2) {
        if (c1 == c2) return true;
        if (c1 == null && c2.isEmpty()) return true;
        if (c2 == null && c1.isEmpty()) return true;
        return Objects.equals(c1, c2);
    }

    private static String serializeList(List<String> list) {
        StringBuilder result = new StringBuilder();
        String sep = "";

        for (Object o : list) {
            result.append(sep);
            result.append(o);
            sep = ",";
        }

        return quote(result.toString(), false);
    }

    private static List<String> deserializeList(String serialized) {
        serialized = unquote(serialized);
        if (serialized == null)
            return new ArrayList<>();
        return new ArrayList<>(Arrays.asList(serialized.split(",")));
    }

    private static String quote(String value, boolean quoteQuotes) {
        StringBuilder result = new StringBuilder();

        for (char c : value.toCharArray()) {
            if (c <= 32 || c >= 127 || c == '\\' || (quoteQuotes && c == '"')) {
                result.append("\\u" + String.format("%04X", (int) c) + ";");
            } else {
                result.append(c);
            }
        }

        return result.toString();
    }

    private static final Pattern unicodePattern =
            Pattern.compile("\\\\u([0-9a-fA-F][0-9a-fA-F][0-9a-fA-F][0-9a-fA-F])");

    private static String unquote(String value) {
        if (value == null)
            return null;

        StringBuilder result = new StringBuilder();
        Matcher m = unicodePattern.matcher(value);
        int lastStart = 0;

        while (m.find(lastStart)) {
            result.append(value.substring(lastStart, m.start()));
            result.append((char) Integer.parseInt(m.group(1), 16));
            lastStart = m.end() + 1;
        }

        result.append(value.substring(lastStart, value.length()));

        return result.toString();
    }

    private static String readDigits(String value, int[] valuePointer) {
        int start = valuePointer[0];

        if (value.charAt(valuePointer[0]) == '-')
            valuePointer[0]++;

        while (valuePointer[0] < value.length() && Character.isDigit(value.charAt(valuePointer[0])))
            valuePointer[0]++;

        return value.substring(start, valuePointer[0]);
    }

    private static String className(String value, int[] valuePointer) {
        int start = valuePointer[0];
        while (value.charAt(valuePointer[0]++) != ';')
            ;
        return value.substring(start, valuePointer[0]);
    }

    private static Object parseAnnotationValue(String value, int[] valuePointer) {
        switch (value.charAt(valuePointer[0]++)) {
            case 'Z':
                if ("true".equals(value.substring(valuePointer[0], valuePointer[0] + 4))) {
                    valuePointer[0] += 4;
                    return true;
                } else if ("false".equals(value.substring(valuePointer[0], valuePointer[0] + 5))) {
                    valuePointer[0] += 5;
                    return false;
                } else {
                    throw new IllegalStateException("Unrecognized boolean structure: " + value);
                }
            case 'B': return Byte.parseByte(readDigits(value, valuePointer));
            case 'C': return value.charAt(valuePointer[0]++);
            case 'S': return Short.parseShort(readDigits(value, valuePointer));
            case 'I': return Integer.parseInt(readDigits(value, valuePointer));
            case 'J': return Long.parseLong(readDigits(value, valuePointer));
            case 'F': return Float.parseFloat(readDigits(value, valuePointer));
            case 'D': return Double.parseDouble(readDigits(value, valuePointer));
            case 'c':
                return new ClassConstant(className(value, valuePointer));
            case 'e':
                return new EnumConstant(className(value, valuePointer), className(value, valuePointer).replaceFirst(";$", ""));
            case '{':
                List<Object> elements = new ArrayList<>(); //TODO: a good test for this would be highly desirable
                while (value.charAt(valuePointer[0]) != '}') {
                    elements.add(parseAnnotationValue(value, valuePointer));
                }
                valuePointer[0]++;
                return elements;
            case '"':
                int start = valuePointer[0];
                while (value.charAt(valuePointer[0]) != '"')
                    valuePointer[0]++;
                return unquote(value.substring(start, valuePointer[0]++));
            case '@':
                return parseAnnotation(value, valuePointer);
            default:
                throw new IllegalStateException("Unrecognized signature type: " + value.charAt(valuePointer[0] - 1) + "; value=" + value);
        }
    }

    public static List<AnnotationDescription> parseAnnotations(String encoded, int[] pointer) {
        ArrayList<AnnotationDescription> result = new ArrayList<>();

        while (pointer[0] < encoded.length() && encoded.charAt(pointer[0]) == '@') {
            pointer[0]++;
            result.add(parseAnnotation(encoded, pointer));
        }

        return result;
    }

    private static AnnotationDescription parseAnnotation(String value, int[] valuePointer) {
        String className = className(value, valuePointer);
        Map<String, Object> attribute2Value = new HashMap<>();

        if (valuePointer[0] < value.length() && value.charAt(valuePointer[0]) == '(') {
            while (value.charAt(valuePointer[0]) != ')') {
                int nameStart = ++valuePointer[0];

                while (value.charAt(valuePointer[0]++) != '=');

                String name = value.substring(nameStart, valuePointer[0] - 1);

                attribute2Value.put(name, parseAnnotationValue(value, valuePointer));
            }

            valuePointer[0]++;
        }

        return new AnnotationDescription(className, attribute2Value);
    }
    //</editor-fold>

    private static void help() {
        System.err.println("Help...");
    }

    public static void main(String... args) throws IOException {
        if (args.length < 1) {
            help();
            return ;
        }

        switch (args[0]) {
            case "build-description":
                if (args.length < 4) {
                    help();
                    return ;
                }

                Path descDest = Paths.get(args[1]);
                List<VersionDescription> versions = new ArrayList<>();

                for (int i = 4; i + 2 < args.length; i += 3) {
                    versions.add(new VersionDescription(args[i + 1], args[i], args[i + 2]));
                }

                Files.walkFileTree(descDest, new FileVisitor<Path>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                        return FileVisitResult.CONTINUE;
                    }
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        Files.delete(file);
                        return FileVisitResult.CONTINUE;
                    }
                    @Override
                    public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                        return FileVisitResult.CONTINUE;
                    }
                    @Override public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                        Files.delete(dir);
                        return FileVisitResult.CONTINUE;
                    }
                });

                new CreateSymbols().createBaseLine(versions, ExcludeIncludeList.create(args[3]), descDest, Paths.get(args[2]));
                break;
            case "build-ctsym":
                if (args.length < 3 || args.length > 4) {
                    help();
                    return ;
                }

                CtSymKind createKind = CtSymKind.JOINED_VERSIONS;
                int argIndex = 1;

                if (args.length == 4) {
                    createKind = CtSymKind.valueOf(args[1]);
                    argIndex++;
                }

                new CreateSymbols().createSymbols(args[argIndex], args[argIndex + 1], createKind);
                break;
        }
    }

}
