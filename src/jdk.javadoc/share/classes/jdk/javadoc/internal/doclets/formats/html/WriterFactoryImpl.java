/*
 * Copyright (c) 2003, 2022, Oracle and/or its affiliates. All rights reserved.
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

package jdk.javadoc.internal.doclets.formats.html;


import javax.lang.model.element.Element;
import javax.lang.model.element.ModuleElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;

import jdk.javadoc.internal.doclets.toolkit.DocFilesHandler;
import jdk.javadoc.internal.doclets.toolkit.util.ClassTree;
import jdk.javadoc.internal.doclets.toolkit.util.VisibleMemberTable;

/**
 * The factory that returns HTML writers.
 */
// TODO: be more consistent about using this factory
public class WriterFactoryImpl {

    private final HtmlConfiguration configuration;
    public WriterFactoryImpl(HtmlConfiguration configuration) {
        this.configuration = configuration;
    }

    public ConstantsSummaryWriterImpl getConstantsSummaryWriter() {
        return new ConstantsSummaryWriterImpl(configuration);
    }

    public PackageWriterImpl getPackageSummaryWriter(PackageElement packageElement) {
        return new PackageWriterImpl(configuration, packageElement);
    }

    public ModuleWriterImpl getModuleSummaryWriter(ModuleElement mdle) {
        return new ModuleWriterImpl(configuration, mdle);
    }

    public ClassWriterImpl getClassWriter(TypeElement typeElement, ClassTree classTree) {
        return new ClassWriterImpl(configuration, typeElement, classTree);
    }

    public AnnotationTypeMemberWriterImpl getAnnotationTypeMemberWriter(
            ClassWriterImpl classWriter) {
        TypeElement te = classWriter.getTypeElement();
        return new AnnotationTypeMemberWriterImpl(classWriter, te, AnnotationTypeMemberWriterImpl.Kind.ANY);
    }

    public AnnotationTypeMemberWriterImpl getAnnotationTypeOptionalMemberWriter(
            ClassWriterImpl classWriter) {
        TypeElement te = classWriter.getTypeElement();
        return new AnnotationTypeMemberWriterImpl(classWriter, te, AnnotationTypeMemberWriterImpl.Kind.OPTIONAL);
    }

    public AnnotationTypeMemberWriterImpl getAnnotationTypeRequiredMemberWriter(
            ClassWriterImpl classWriter) {
        TypeElement te = classWriter.getTypeElement();
        return new AnnotationTypeMemberWriterImpl(classWriter, te, AnnotationTypeMemberWriterImpl.Kind.REQUIRED);
    }

    public EnumConstantWriterImpl getEnumConstantWriter(ClassWriterImpl classWriter) {
        return new EnumConstantWriterImpl(classWriter);
    }

    public FieldWriterImpl getFieldWriter(ClassWriterImpl classWriter) {
        return new FieldWriterImpl(classWriter);
    }

    public PropertyWriterImpl getPropertyWriter(ClassWriterImpl classWriter) {
        return new PropertyWriterImpl(classWriter);
    }

    public MethodWriterImpl getMethodWriter(ClassWriterImpl classWriter) {
        return new MethodWriterImpl(classWriter);
    }

    public ConstructorWriterImpl getConstructorWriter(ClassWriterImpl classWriter) {
        return new ConstructorWriterImpl(classWriter);
    }

    public AbstractMemberWriter getMemberSummaryWriter(ClassWriterImpl classWriter,
            VisibleMemberTable.Kind memberType) {
        switch (memberType) {
            case CONSTRUCTORS:
                return getConstructorWriter(classWriter);
            case ENUM_CONSTANTS:
                return getEnumConstantWriter(classWriter);
            case ANNOTATION_TYPE_MEMBER_OPTIONAL:
                return getAnnotationTypeOptionalMemberWriter(classWriter);
            case ANNOTATION_TYPE_MEMBER_REQUIRED:
                return getAnnotationTypeRequiredMemberWriter(classWriter);
            case FIELDS:
                return getFieldWriter(classWriter);
            case PROPERTIES:
                return getPropertyWriter(classWriter);
            case NESTED_CLASSES:
                return new NestedClassWriterImpl(classWriter, classWriter.getTypeElement());
            case METHODS:
                return getMethodWriter(classWriter);
            default:
                return null;
        }
    }

    public SerializedFormWriterImpl getSerializedFormWriter() {
        return new SerializedFormWriterImpl(configuration);
    }

    public DocFilesHandler getDocFilesHandler(Element element) {
        return new DocFilesHandlerImpl(configuration, element);
    }
}
