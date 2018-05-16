/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jfr.internal;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import jdk.internal.org.xml.sax.Attributes;
import jdk.internal.org.xml.sax.EntityResolver;
import jdk.internal.org.xml.sax.SAXException;
import jdk.internal.org.xml.sax.helpers.DefaultHandler;
import jdk.internal.util.xml.SAXParser;
import jdk.internal.util.xml.impl.SAXParserImpl;
import jdk.jfr.AnnotationElement;
import jdk.jfr.Category;
import jdk.jfr.Description;
import jdk.jfr.Enabled;
import jdk.jfr.Experimental;
import jdk.jfr.Label;
import jdk.jfr.Period;
import jdk.jfr.Relational;
import jdk.jfr.StackTrace;
import jdk.jfr.Threshold;
import jdk.jfr.TransitionFrom;
import jdk.jfr.TransitionTo;
import jdk.jfr.Unsigned;

final class MetadataHandler extends DefaultHandler implements EntityResolver {

    static class TypeElement {
        List<FieldElement> fields = new ArrayList<>();
        String name;
        String label;
        String description;
        String category;
        String superType;
        String period;
        boolean thread;
        boolean startTime;
        boolean stackTrace;
        boolean cutoff;
        boolean isEvent;
        boolean experimental;
        boolean valueType;
    }

    static class FieldElement {
        TypeElement referenceType;
        String name;
        String label;
        String description;
        String contentType;
        String typeName;
        String transition;
        String relation;
        boolean struct;
        boolean array;
        boolean experimental;
        boolean unsigned;
    }

    static class XmlType {
        String name;
        String javaType;
        String contentType;
        boolean unsigned;
    }

    final Map<String, TypeElement> types = new LinkedHashMap<>(200);
    final Map<String, XmlType> xmlTypes = new HashMap<>(20);
    final Map<String, AnnotationElement> xmlContentTypes = new HashMap<>(20);
    final List<String> relations = new ArrayList<>();
    long eventTypeId = 255;
    long structTypeId = 33;
    FieldElement currentField;
    TypeElement currentType;

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
        switch (qName) {
        case "XmlType":
            XmlType xmlType = new XmlType();
            xmlType.name = attributes.getValue("name");
            xmlType.javaType = attributes.getValue("javaType");
            xmlType.contentType = attributes.getValue("contentType");
            xmlType.unsigned = Boolean.valueOf(attributes.getValue("unsigned"));
            xmlTypes.put(xmlType.name, xmlType);
            break;
        case "Type":
        case "Event":
            currentType = new TypeElement();
            currentType.name = attributes.getValue("name");
            currentType.label = attributes.getValue("label");
            currentType.description = attributes.getValue("description");
            currentType.category = attributes.getValue("category");
            currentType.thread = getBoolean(attributes, "thread", false);
            currentType.stackTrace = getBoolean(attributes, "stackTrace", false);
            currentType.startTime = getBoolean(attributes, "startTime", true);
            currentType.period = attributes.getValue("period");
            currentType.cutoff = getBoolean(attributes, "cutoff", false);
            currentType.experimental = getBoolean(attributes, "experimental", false);
            currentType.isEvent = qName.equals("Event");
            break;
        case "Field":
            currentField = new FieldElement();
            currentField.struct = getBoolean(attributes, "struct", false);
            currentField.array = getBoolean(attributes, "array", false);
            currentField.name = attributes.getValue("name");
            currentField.label = attributes.getValue("label");
            currentField.typeName = attributes.getValue("type");
            currentField.description = attributes.getValue("description");
            currentField.experimental = getBoolean(attributes, "experimental", false);
            currentField.contentType = attributes.getValue("contentType");
            currentField.relation = attributes.getValue("relation");
            currentField.transition = attributes.getValue("transition");
            break;
        case "XmlContentType":
            String name = attributes.getValue("name");
            String type = attributes.getValue("annotationType");
            String value = attributes.getValue("annotationValue");
            Class<? extends Annotation> annotationType = createAnnotationClass(type);
            AnnotationElement ae = value == null ? new AnnotationElement(annotationType) : new AnnotationElement(annotationType, value);
            xmlContentTypes.put(name, ae);
            break;
        case "Relation":
            String n = attributes.getValue("name");
            relations.add(n);
            break;
        }
    }

    @SuppressWarnings("unchecked")
    private Class<? extends Annotation> createAnnotationClass(String type) {
        try {
            if (!type.startsWith("jdk.jfr.")) {
                throw new IllegalStateException("Incorrect type " + type + ". Annotation class must be located in jdk.jfr package.");
            }
            Class<?> c = Class.forName(type, true, null);
            return (Class<? extends Annotation>) c;
        } catch (ClassNotFoundException cne) {
            throw new IllegalStateException(cne);
        }
    }

    private boolean getBoolean(Attributes attributes, String name, boolean defaultValue) {
        String value = attributes.getValue(name);
        return value == null ? defaultValue : Boolean.valueOf(value);
    }

    @Override
    public void endElement(String uri, String localName, String qName) {
        switch (qName) {
        case "Type":
        case "Event":
            types.put(currentType.name, currentType);
            currentType = null;
            break;
        case "Field":
            currentType.fields.add(currentField);
            currentField = null;
            break;
        }
    }

    public static List<Type> createTypes() throws IOException {
        SAXParser parser = new SAXParserImpl();
        MetadataHandler t = new MetadataHandler();
        try (InputStream is = new BufferedInputStream(SecuritySupport.getResourceAsStream("/jdk/jfr/internal/types/metadata.xml"))) {
            Logger.log(LogTag.JFR_SYSTEM, LogLevel.DEBUG, () -> "Parsing metadata.xml");
            try {
                parser.parse(is, t);
                return t.buildTypes();
            } catch (Exception e) {
                e.printStackTrace();
                throw new IOException(e);
            }
        }
    }

    private List<Type> buildTypes() {
        removeXMLConvenience();
        Map<String, Type> typeMap = buildTypeMap();
        Map<String, AnnotationElement> relationMap = buildRelationMap(typeMap);
        addFields(typeMap, relationMap);
        return trimTypes(typeMap);
    }

    private Map<String, AnnotationElement> buildRelationMap(Map<String, Type> typeMap) {
        Map<String, AnnotationElement> relationMap = new HashMap<>();
        for (String relation : relations) {
            Type relationType = new Type(Type.TYPES_PREFIX + relation, Type.SUPER_TYPE_ANNOTATION, eventTypeId++);
            relationType.setAnnotations(Collections.singletonList(new AnnotationElement(Relational.class)));
            AnnotationElement ae = PrivateAccess.getInstance().newAnnotation(relationType, Collections.emptyList(), true);
            relationMap.put(relation, ae);
            typeMap.put(relationType.getName(), relationType);
        }
        return relationMap;
    }

    private List<Type> trimTypes(Map<String, Type> lookup) {
        List<Type> trimmedTypes = new ArrayList<>(lookup.size());
        for (Type t : lookup.values()) {
            t.trimFields();
            trimmedTypes.add(t);
        }
        return trimmedTypes;
    }

    private void addFields(Map<String, Type> lookup, Map<String, AnnotationElement> relationMap) {
        for (TypeElement te : types.values()) {
            Type type = lookup.get(te.name);
            if (te.isEvent) {
                boolean periodic = te.period!= null;
                TypeLibrary.addImplicitFields(type, periodic, te.startTime && !periodic, te.thread, te.stackTrace && !periodic, te.cutoff);
            }
            for (FieldElement f : te.fields) {
                Type fieldType = Type.getKnownType(f.typeName);
                if (fieldType == null) {
                    fieldType = Objects.requireNonNull(lookup.get(f.referenceType.name));
                }
                List<AnnotationElement> aes = new ArrayList<>();
                if (f.unsigned) {
                    aes.add(new AnnotationElement(Unsigned.class));
                }
                if (f.contentType != null) {
                    aes.add(Objects.requireNonNull(xmlContentTypes.get(f.contentType)));
                }
                if (f.relation != null) {
                    aes.add(Objects.requireNonNull(relationMap.get(f.relation)));
                }
                if (f.label != null) {
                    aes.add(new AnnotationElement(Label.class, f.label));
                }
                if (f.experimental) {
                    aes.add(new AnnotationElement(Experimental.class));
                }
                if (f.description != null) {
                    aes.add(new AnnotationElement(Description.class, f.description));
                }
                if ("from".equals(f.transition)) {
                    aes.add(new AnnotationElement(TransitionFrom.class));
                }
                if ("to".equals(f.transition)) {
                    aes.add(new AnnotationElement(TransitionTo.class));
                }
                boolean constantPool = !f.struct && f.referenceType != null;
                type.add(PrivateAccess.getInstance().newValueDescriptor(f.name, fieldType, aes, f.array ? 1 : 0, constantPool, null));
            }
        }
    }

    private Map<String, Type> buildTypeMap() {
        Map<String, Type> typeMap = new HashMap<>();
        for (Type type : Type.getKnownTypes()) {
            typeMap.put(type.getName(), type);
        }

        for (TypeElement t : types.values()) {
            List<AnnotationElement> aes = new ArrayList<>();
            if (t.category != null) {
                aes.add(new AnnotationElement(Category.class, buildCategoryArray(t.category)));
            }
            if (t.label != null) {
                aes.add(new AnnotationElement(Label.class, t.label));
            }
            if (t.description != null) {
                aes.add(new AnnotationElement(Description.class, t.description));
            }
            if (t.isEvent) {
                if (t.period != null) {
                    aes.add(new AnnotationElement(Period.class, t.period));
                } else {
                    if (t.startTime) {
                        aes.add(new AnnotationElement(Threshold.class, "0 ns"));
                    }
                    if (t.stackTrace) {
                        aes.add(new AnnotationElement(StackTrace.class, true));
                    }
                }
                if (t.cutoff) {
                    aes.add(new AnnotationElement(Cutoff.class, Cutoff.INIFITY));
                }
            }
            if (t.experimental) {
                aes.add(new AnnotationElement(Experimental.class));
            }
            Type type;
            if (t.isEvent) {
                aes.add(new AnnotationElement(Enabled.class, false));
                type = new PlatformEventType(t.name,  eventTypeId++, false, true);
            } else {
                // Struct types had their own XML-element in the past. To have id assigned in the
                // same order as generated .hpp file do some tweaks here.
                boolean valueType = t.name.endsWith("StackFrame") || t.valueType;
                type = new Type(t.name, null, valueType ?  eventTypeId++ : nextTypeId(t.name), false);
            }
            type.setAnnotations(aes);
            typeMap.put(t.name, type);
        }
        return typeMap;
    }

    private long nextTypeId(String name) {
        if (Type.THREAD.getName().equals(name)) {
            return Type.THREAD.getId();
        }
        if (Type.STRING.getName().equals(name)) {
            return Type.STRING.getId();
        }
        if (Type.CLASS.getName().equals(name)) {
            return Type.CLASS.getId();
        }
        for (Type type : Type.getKnownTypes()) {
            if (type.getName().equals(name)) {
                return type.getId();
            }
        }
        return structTypeId++;
    }

    private String[] buildCategoryArray(String category) {
        List<String> categories = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        for (char c : category.toCharArray()) {
            if (c == ',') {
                categories.add(sb.toString().trim());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        categories.add(sb.toString().trim());
        return categories.toArray(new String[0]);
    }

    private void removeXMLConvenience() {
        for (TypeElement t : types.values()) {
            XmlType xmlType = xmlTypes.get(t.name);
            if (xmlType != null && xmlType.javaType != null) {
                t.name = xmlType.javaType; // known type, i.e primitive
            } else {
                if (t.isEvent) {
                    t.name = Type.EVENT_NAME_PREFIX + t.name;
                } else {
                    t.name = Type.TYPES_PREFIX + t.name;
                }
            }
        }

        for (TypeElement t : types.values()) {
            for (FieldElement f : t.fields) {
                f.referenceType = types.get(f.typeName);
                XmlType xmlType = xmlTypes.get(f.typeName);
                if (xmlType != null) {
                    if (xmlType.javaType != null) {
                        f.typeName = xmlType.javaType;
                    }
                    if (xmlType.contentType != null) {
                        f.contentType = xmlType.contentType;
                    }
                    if (xmlType.unsigned) {
                        f.unsigned = true;
                    }
                }
                if (f.struct && f.referenceType != null) {
                    f.referenceType.valueType = true;
                }
            }
        }
    }
}
