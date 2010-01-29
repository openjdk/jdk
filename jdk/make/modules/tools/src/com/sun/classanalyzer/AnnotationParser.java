/*
 * Copyright 2009 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */
package com.sun.classanalyzer;

import com.sun.tools.classfile.*;
import com.sun.tools.classfile.Annotation;
import com.sun.tools.classfile.ExtendedAnnotation;
import com.sun.tools.classfile.Annotation.Annotation_element_value;
import com.sun.tools.classfile.Annotation.Array_element_value;
import com.sun.tools.classfile.Annotation.Class_element_value;
import com.sun.tools.classfile.Annotation.Enum_element_value;
import com.sun.tools.classfile.Annotation.Primitive_element_value;
import com.sun.tools.classfile.ConstantPoolException;
import com.sun.tools.classfile.Descriptor;
import com.sun.tools.classfile.Descriptor.InvalidDescriptor;
import java.util.ArrayList;
import java.util.List;

import com.sun.classanalyzer.AnnotatedDependency.*;
import java.io.File;
import java.io.PrintWriter;
import java.util.Map;
import java.util.Set;

/**
 *
 * @author Mandy Chung
 */
public class AnnotationParser {

    static boolean parseAnnotation = false;
    static void setParseAnnotation(boolean newValue) {
        parseAnnotation = newValue;
    }

    private final ClassFileParser cfparser;
    public AnnotationParser(ClassFileParser cfparser) {
        this.cfparser = cfparser;
    }

    private AnnotatedDependency addAnnotation(Annotation annot, Klass.Method method) {
        String type = getType(annot.type_index);
        AnnotatedDependency dep = AnnotatedDependency.newAnnotatedDependency(type, cfparser.this_klass);
        if (dep != null) {
            for (int i = 0; i < annot.num_element_value_pairs; i++) {
                Element element = getElement(annot.element_value_pairs[i]);
                dep.addElement(element.name, element.value);
            }
            dep.setMethod(method);
        }
        return dep;
    }

    private AnnotatedDependency addAnnotation(ExtendedAnnotation annot, Klass.Method method) {
        return addAnnotation(annot.annotation, method);
    }

    class Element {

        String name;
        List<String> value;

        Element(String name) {
            this.name = name;
            this.value = new ArrayList<String>();
        }

        void add(String v) {
            value.add(v);
        }
    }

    Element getElement(Annotation.element_value_pair pair) {
        Element element = new Element(getName(pair.element_name_index));
        evp.parse(pair.value, element);
        return element;
    }

    private String getType(int index) {
        try {
            Descriptor d = new Descriptor(index);
            return d.getFieldType(cfparser.classfile.constant_pool);
        } catch (ConstantPoolException ignore) {
        } catch (InvalidDescriptor ignore) {
        }
        return "Unknown";
    }

    private String getName(int index) {
        return cfparser.constantPoolParser.stringValue(index);
    }
    element_value_Parser evp = new element_value_Parser();

    class element_value_Parser implements Annotation.element_value.Visitor<Void, Element> {

        public Void parse(Annotation.element_value value, Element element) {
            value.accept(this, element);
            return null;
        }

        public Void visitPrimitive(Primitive_element_value ev, Element element) {
            String value = getName(ev.const_value_index);
            element.add(value);
            return null;
        }

        public Void visitEnum(Enum_element_value ev, Element element) {
            String value = getName(ev.type_name_index) + "." + getName(ev.const_name_index);
            element.add(value);
            return null;
        }

        public Void visitClass(Class_element_value ev, Element element) {
            String value = getName(ev.class_info_index) + ".class";
            element.add(value);
            return null;
        }

        public Void visitAnnotation(Annotation_element_value ev, Element element) {
            // AnnotationParser.this.addAnnotation(ev.annotation_value);
            throw new UnsupportedOperationException("Not supported: " + ev);
        }

        public Void visitArray(Array_element_value ev, Element element) {
            for (int i = 0; i < ev.num_values; i++) {
                parse(ev.values[i], element);
            }
            return null;
        }
    }

    void parseAttributes(Attributes attributes, Klass.Method method) {
        if (!parseAnnotation) {
            return;
        }

        visitRuntimeAnnotations((RuntimeVisibleAnnotations_attribute) attributes.get(Attribute.RuntimeVisibleAnnotations), method);
        visitRuntimeAnnotations((RuntimeInvisibleAnnotations_attribute) attributes.get(Attribute.RuntimeInvisibleAnnotations), method);
        visitRuntimeTypeAnnotations((RuntimeVisibleTypeAnnotations_attribute) attributes.get(Attribute.RuntimeVisibleTypeAnnotations), method);
        visitRuntimeTypeAnnotations((RuntimeInvisibleTypeAnnotations_attribute) attributes.get(Attribute.RuntimeInvisibleTypeAnnotations), method);
        visitRuntimeParameterAnnotations((RuntimeVisibleParameterAnnotations_attribute) attributes.get(Attribute.RuntimeVisibleParameterAnnotations), method);
        visitRuntimeParameterAnnotations((RuntimeInvisibleParameterAnnotations_attribute) attributes.get(Attribute.RuntimeInvisibleParameterAnnotations), method);
    }

    public void visitRuntimeAnnotations(RuntimeAnnotations_attribute attr, Klass.Method method) {
        if (attr == null) {
            return;
        }

        for (int i = 0; i < attr.annotations.length; i++) {
            addAnnotation(attr.annotations[i], method);
        }
    }

    public void visitRuntimeTypeAnnotations(RuntimeTypeAnnotations_attribute attr, Klass.Method method) {
        if (attr == null) {
            return;
        }

        for (int i = 0; i < attr.annotations.length; i++) {
            addAnnotation(attr.annotations[i], method);
        }
    }

    public void visitRuntimeParameterAnnotations(RuntimeParameterAnnotations_attribute attr, Klass.Method method) {
        if (attr == null) {
            return;
        }

        for (int param = 0; param < attr.parameter_annotations.length; param++) {
            for (int i = 0; i < attr.parameter_annotations[param].length; i++) {
                addAnnotation(attr.parameter_annotations[param][i], method);
            }
        }
    }

    void parseAttributes(Attributes attributes) {
        parseAttributes(attributes, null);
    }

    public static void main(String[] args) throws Exception {
        String jdkhome = null;
        String output = ".";

        // process arguments
        int i = 0;
        while (i < args.length) {
            String arg = args[i++];
            if (arg.equals("-jdkhome")) {
                if (i < args.length) {
                    jdkhome = args[i++];
                } else {
                    usage();
                }
            } else if (arg.equals("-output")) {
                output = args[i++];
            } else {
                usage();
            }
        }
        if (jdkhome == null) {
            usage();
        }

        // parse annotation and code attribute to find all references
        // to Class.forName etc
        CodeAttributeParser.setParseCodeAttribute(true);
        AnnotationParser.setParseAnnotation(true);

        ClassPath.setJDKHome(jdkhome);
        ClassPath.parseAllClassFiles();

        PrintWriter writer = new PrintWriter(new File(output, "jdk7.depconfig"));

        try {
            for (Klass k : Klass.getAllClasses()) {
                for (AnnotatedDependency dep : k.getAnnotatedDeps()) {
                    if (dep.isEmpty()) {
                        continue;
                    }
                    writer.format("# %s \n", dep.method == null ? dep.from : dep.method);
                    writer.format("%s\n\n", dep);
                }
            }
        } finally {
            writer.close();
        }

        writer = new PrintWriter(new File(output, "optional.depconfig"));
        try {
            AnnotatedDependency prev = null;
            for (AnnotatedDependency dep : AnnotatedDependency.optionalDependencies) {
                if (prev != null && !dep.equals(prev)) {
                    writer.format("%s\n\n", prev);
                }
                writer.format("# %s \n", dep.method == null ? dep.from : dep.method);
                prev = dep;
            }
            if (prev != null) {
                writer.format("%s\n\n", prev);
            }
        } finally {
            writer.close();
        }

        writer = new PrintWriter(new File(output, "runtime.references"));
        try {
            for (Map.Entry<String, Set<Klass.Method>> entry : CodeAttributeParser.runtimeReferences.entrySet()) {
                writer.format("References to %s\n", entry.getKey());
                Klass prev = null;
                for (Klass.Method m : entry.getValue()) {
                    if (prev == null || prev != m.getKlass()) {
                        writer.format("  %-50s # %s\n", m.getKlass(), m);
                    } else if (prev == m.getKlass()) {
                        writer.format("  %-50s # %s\n", "", m);
                    }
                    prev = m.getKlass();
                }
            }
        } finally {
            writer.close();
        }
    }

    private static void usage() {
        System.out.println("Usage: AnnotationParser <options>");
        System.out.println("Options: ");
        System.out.println("\t-jdkhome <JDK home> where all jars will be parsed");
        System.out.println("\t-depconfig <output file for annotated dependencies>");
        System.out.println("\t-optional <output file for optional dependencies>");
        System.exit(-1);
    }
}
