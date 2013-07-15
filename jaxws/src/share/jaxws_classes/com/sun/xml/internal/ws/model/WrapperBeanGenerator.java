/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.ws.model;

import com.sun.xml.internal.ws.model.AbstractWrapperBeanGenerator.BeanMemberFactory;
import com.sun.xml.internal.bind.v2.model.annotation.AnnotationReader;
import com.sun.xml.internal.bind.v2.model.annotation.RuntimeInlineAnnotationReader;
import com.sun.xml.internal.bind.v2.model.nav.Navigator;
import com.sun.xml.internal.ws.org.objectweb.asm.*;
import static com.sun.xml.internal.ws.org.objectweb.asm.Opcodes.*;
import com.sun.xml.internal.ws.org.objectweb.asm.Type;

import javax.xml.bind.annotation.XmlAttachmentRef;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlList;
import javax.xml.bind.annotation.XmlMimeType;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import javax.xml.namespace.QName;
import javax.xml.ws.Holder;
import javax.xml.ws.WebServiceException;
import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Runtime Wrapper and exception bean generator implementation.
 * It uses ASM to generate request, response and exception beans.
 *
 * @author Jitendra Kotamraju
 */
public class WrapperBeanGenerator {

    private static final Logger LOGGER = Logger.getLogger(WrapperBeanGenerator.class.getName());

    private static final FieldFactory FIELD_FACTORY = new FieldFactory();

    private static final AbstractWrapperBeanGenerator RUNTIME_GENERATOR =
            new RuntimeWrapperBeanGenerator(new RuntimeInlineAnnotationReader(),
                    Navigator.REFLECTION, FIELD_FACTORY);

    private static final class RuntimeWrapperBeanGenerator extends AbstractWrapperBeanGenerator<java.lang.reflect.Type, Class, java.lang.reflect.Method, Field> {

        protected RuntimeWrapperBeanGenerator(AnnotationReader<java.lang.reflect.Type, Class, ?, Method> annReader, Navigator<java.lang.reflect.Type, Class, ?, Method> nav, BeanMemberFactory<java.lang.reflect.Type, Field> beanMemberFactory) {
            super(annReader, nav, beanMemberFactory);
        }

        @Override
        protected java.lang.reflect.Type getSafeType(java.lang.reflect.Type type) {
            return type;
        }

        @Override
        protected java.lang.reflect.Type getHolderValueType(java.lang.reflect.Type paramType) {
            if (paramType instanceof ParameterizedType) {
                ParameterizedType p = (ParameterizedType)paramType;
                if (p.getRawType().equals(Holder.class)) {
                    return p.getActualTypeArguments()[0];
                }
            }
            return null;
        }

        @Override
        protected boolean isVoidType(java.lang.reflect.Type type) {
            return type == Void.TYPE;
        }

    }

    private static final class FieldFactory implements BeanMemberFactory<java.lang.reflect.Type, Field> {
        @Override
        public Field createWrapperBeanMember(java.lang.reflect.Type paramType,
                String paramName, List<Annotation> jaxb) {
            return new Field(paramName, paramType, getASMType(paramType), jaxb);
        }
    }

    // Creates class's bytes
    private static byte[] createBeanImage(String className,
                               String rootName, String rootNS,
                               String typeName, String typeNS,
                               Collection<Field> fields) throws Exception {

        ClassWriter cw = new ClassWriter(0);
        //org.objectweb.asm.util.TraceClassVisitor cw = new org.objectweb.asm.util.TraceClassVisitor(actual, new java.io.PrintWriter(System.out));

        cw.visit(V1_5, ACC_PUBLIC + ACC_SUPER, replaceDotWithSlash(className), null, "java/lang/Object", null);

        AnnotationVisitor root = cw.visitAnnotation("Ljavax/xml/bind/annotation/XmlRootElement;", true);
        root.visit("name", rootName);
        root.visit("namespace", rootNS);
        root.visitEnd();

        AnnotationVisitor type = cw.visitAnnotation("Ljavax/xml/bind/annotation/XmlType;", true);
        type.visit("name", typeName);
        type.visit("namespace", typeNS);
        if (fields.size() > 1) {
            AnnotationVisitor propVisitor = type.visitArray("propOrder");
            for(Field field : fields) {
                propVisitor.visit("propOrder", field.fieldName);
            }
            propVisitor.visitEnd();
        }
        type.visitEnd();

        for(Field field : fields) {
            FieldVisitor fv = cw.visitField(ACC_PUBLIC, field.fieldName, field.asmType.getDescriptor(), field.getSignature(), null);

            for(Annotation ann : field.jaxbAnnotations) {
                if (ann instanceof XmlMimeType) {
                    AnnotationVisitor mime = fv.visitAnnotation("Ljavax/xml/bind/annotation/XmlMimeType;", true);
                    mime.visit("value", ((XmlMimeType)ann).value());
                    mime.visitEnd();
                } else if (ann instanceof XmlJavaTypeAdapter) {
                    AnnotationVisitor ada = fv.visitAnnotation("Ljavax/xml/bind/annotation/adapters/XmlJavaTypeAdapter;", true);
                    ada.visit("value", getASMType(((XmlJavaTypeAdapter)ann).value()));
                    // XmlJavaTypeAdapter.type() is for package only. No need to copy.
                    // ada.visit("type", ((XmlJavaTypeAdapter)ann).type());
                    ada.visitEnd();
                } else if (ann instanceof XmlAttachmentRef) {
                    AnnotationVisitor att = fv.visitAnnotation("Ljavax/xml/bind/annotation/XmlAttachmentRef;", true);
                    att.visitEnd();
                } else if (ann instanceof XmlList) {
                    AnnotationVisitor list = fv.visitAnnotation("Ljavax/xml/bind/annotation/XmlList;", true);
                    list.visitEnd();
                } else if (ann instanceof XmlElement) {
                    AnnotationVisitor elem = fv.visitAnnotation("Ljavax/xml/bind/annotation/XmlElement;", true);
                    XmlElement xmlElem = (XmlElement)ann;
                    elem.visit("name", xmlElem.name());
                    elem.visit("namespace", xmlElem.namespace());
                    if (xmlElem.nillable()) {
                        elem.visit("nillable", true);
                    }
                    if (xmlElem.required()) {
                        elem.visit("required", true);
                    }
                    elem.visitEnd();
                } else {
                    throw new WebServiceException("Unknown JAXB annotation " + ann);
                }
            }

            fv.visitEnd();
        }

        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V");
        mv.visitInsn(RETURN);
        mv.visitMaxs(1, 1);
        mv.visitEnd();

        cw.visitEnd();

        if (LOGGER.isLoggable(Level.FINE)) {
            // Class's @XmlRootElement
            StringBuilder sb = new StringBuilder();
            sb.append("\n");
            sb.append("@XmlRootElement(name=").append(rootName)
                    .append(", namespace=").append(rootNS).append(")");

            // Class's @XmlType
            sb.append("\n");
            sb.append("@XmlType(name=").append(typeName)
                    .append(", namespace=").append(typeNS);
            if (fields.size() > 1) {
                sb.append(", propOrder={");
                for(Field field : fields) {
                    sb.append(" ");
                    sb.append(field.fieldName);
                }
                sb.append(" }");
            }
            sb.append(")");

            // class declaration
            sb.append("\n");
            sb.append("public class ").append(className).append(" {");

            // fields declaration
            for(Field field : fields) {
                sb.append("\n");

                // Field's other JAXB annotations
                for(Annotation ann : field.jaxbAnnotations) {
                    sb.append("\n    ");

                    if (ann instanceof XmlMimeType) {
                        sb.append("@XmlMimeType(value=").append(((XmlMimeType)ann).value()).append(")");
                    } else if (ann instanceof XmlJavaTypeAdapter) {
                        sb.append("@XmlJavaTypeAdapter(value=").append(getASMType(((XmlJavaTypeAdapter)ann).value())).append(")");
                    } else if (ann instanceof XmlAttachmentRef) {
                        sb.append("@XmlAttachmentRef");
                    } else if (ann instanceof XmlList) {
                        sb.append("@XmlList");
                    } else if (ann instanceof XmlElement) {
                        XmlElement xmlElem = (XmlElement)ann;
                        sb.append("\n    ");
                        sb.append("@XmlElement(name=").append(xmlElem.name())
                                .append(", namespace=").append(xmlElem.namespace());
                        if (xmlElem.nillable()) {
                            sb.append(", nillable=true");
                        }
                        if (xmlElem.required()) {
                            sb.append(", required=true");
                        }
                        sb.append(")");
                    } else {
                        throw new WebServiceException("Unknown JAXB annotation " + ann);
                    }
                }

                // Field declaration
                sb.append("\n    ");
                sb.append("public ");
                if (field.getSignature() == null) {
                    sb.append(field.asmType.getDescriptor());
                } else {
                    sb.append(field.getSignature());
                }
                sb.append(" ");
                sb.append(field.fieldName);
            }

            sb.append("\n\n}");
            LOGGER.fine(sb.toString());
        }

        return cw.toByteArray();
    }

    private static String replaceDotWithSlash(String name) {
        return name.replace('.', '/');
    }

    static Class createRequestWrapperBean(String className, Method method, QName reqElemName, ClassLoader cl) {

        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.log(Level.FINE, "Request Wrapper Class : {0}", className);
        }

        List<Field> requestMembers = RUNTIME_GENERATOR.collectRequestBeanMembers(
                method);

        byte[] image;
        try {
            image = createBeanImage(className, reqElemName.getLocalPart(), reqElemName.getNamespaceURI(),
                reqElemName.getLocalPart(), reqElemName.getNamespaceURI(),
                requestMembers);
        } catch(Exception e) {
            throw new WebServiceException(e);
        }
//        write(image, className);
        return Injector.inject(cl, className, image);
    }

    static Class createResponseWrapperBean(String className, Method method, QName resElemName, ClassLoader cl) {

        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.log(Level.FINE, "Response Wrapper Class : {0}", className);
        }

        List<Field> responseMembers = RUNTIME_GENERATOR.collectResponseBeanMembers(method);

        byte[] image;
        try {
            image = createBeanImage(className, resElemName.getLocalPart(), resElemName.getNamespaceURI(),
                resElemName.getLocalPart(), resElemName.getNamespaceURI(),
                responseMembers);
        } catch(Exception e) {
            throw new WebServiceException(e);
        }
//      write(image, className);

        return Injector.inject(cl, className, image);
    }


    private static Type getASMType(java.lang.reflect.Type t) {
        assert t!=null;

        if (t instanceof Class) {
            return Type.getType((Class)t);
        }

        if (t instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType)t;
            if (pt.getRawType() instanceof Class) {
                return Type.getType((Class)pt.getRawType());
            }
        }
        if (t instanceof GenericArrayType) {
            return Type.getType(FieldSignature.vms(t));
        }

        if (t instanceof WildcardType) {
            return Type.getType(FieldSignature.vms(t));
        }

        if (t instanceof TypeVariable) {
            TypeVariable tv = (TypeVariable)t;
            if (tv.getBounds()[0] instanceof Class) {
                return Type.getType((Class)tv.getBounds()[0]);
            }
        }

        throw new IllegalArgumentException("Not creating ASM Type for type = "+t);
    }


    static Class createExceptionBean(String className, Class exception, String typeNS, String elemName, String elemNS, ClassLoader cl) {
        return createExceptionBean(className, exception, typeNS, elemName, elemNS, cl, true);
    }

    static Class createExceptionBean(String className, Class exception, String typeNS, String elemName, String elemNS, ClassLoader cl, boolean decapitalizeExceptionBeanProperties) {

        Collection<Field> fields = RUNTIME_GENERATOR.collectExceptionBeanMembers(exception, decapitalizeExceptionBeanProperties);

        byte[] image;
        try {
            image = createBeanImage(className, elemName, elemNS,
                exception.getSimpleName(), typeNS,
                fields);
        } catch(Exception e) {
            throw new WebServiceException(e);
        }

        return Injector.inject(cl, className, image);
    }

    /**
     * Note: this class has a natural ordering that is inconsistent with equals.
     */
    private static class Field implements Comparable<Field> {
        private final java.lang.reflect.Type reflectType;
        private final Type asmType;
        private final String fieldName;
        private final List<Annotation> jaxbAnnotations;

        Field(String paramName, java.lang.reflect.Type paramType, Type asmType,
              List<Annotation> jaxbAnnotations) {
            this.reflectType = paramType;
            this.asmType = asmType;
            this.fieldName = paramName;
            this.jaxbAnnotations = jaxbAnnotations;
        }

        String getSignature() {
            if (reflectType instanceof Class) {
                return null;
            }
            if (reflectType instanceof TypeVariable) {
                return null;
            }
            return FieldSignature.vms(reflectType);
        }

        @Override
        public int compareTo(Field o) {
            return fieldName.compareTo(o.fieldName);
        }
    }

    static void write(byte[] b, String className) {
        className = className.substring(className.lastIndexOf(".")+1);
        try {
            java.io.FileOutputStream fo = new java.io.FileOutputStream(className + ".class");
            fo.write(b);
            fo.flush();
            fo.close();
        } catch (java.io.IOException e) {
            LOGGER.log(Level.INFO, "Error Writing class", e);
        }
    }

}
