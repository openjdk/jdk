/*
 * Copyright (c) 2003, 2012, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.doclets.formats.html;

import java.io.IOException;

import com.sun.javadoc.*;
import com.sun.tools.doclets.internal.toolkit.*;
import com.sun.tools.doclets.internal.toolkit.util.*;

/**
 * The factory that returns HTML writers.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @author Jamie Ho
 * @since 1.5
 */
public class WriterFactoryImpl implements WriterFactory {

    private final ConfigurationImpl configuration;

    public WriterFactoryImpl(ConfigurationImpl configuration) {
        this.configuration = configuration;
    }

    /**
     * {@inheritDoc}
     */
    public ConstantsSummaryWriter getConstantsSummaryWriter() throws Exception {
        return new ConstantsSummaryWriterImpl(configuration);
    }

    /**
     * {@inheritDoc}
     */
    public PackageSummaryWriter getPackageSummaryWriter(PackageDoc packageDoc,
        PackageDoc prevPkg, PackageDoc nextPkg) throws Exception {
        return new PackageWriterImpl(configuration, packageDoc,
            prevPkg, nextPkg);
    }

    /**
     * {@inheritDoc}
     */
    public ClassWriter getClassWriter(ClassDoc classDoc, ClassDoc prevClass,
            ClassDoc nextClass, ClassTree classTree) throws IOException {
        return new ClassWriterImpl(configuration, classDoc,
                prevClass, nextClass, classTree);
    }

    /**
     * {@inheritDoc}
     */
    public AnnotationTypeWriter getAnnotationTypeWriter(
        AnnotationTypeDoc annotationType, Type prevType, Type nextType)
    throws Exception {
        return new AnnotationTypeWriterImpl(configuration,
                annotationType, prevType, nextType);
    }

    /**
     * {@inheritDoc}
     */
    public AnnotationTypeOptionalMemberWriter
            getAnnotationTypeOptionalMemberWriter(
        AnnotationTypeWriter annotationTypeWriter) throws Exception {
        return new AnnotationTypeOptionalMemberWriterImpl(
            (SubWriterHolderWriter) annotationTypeWriter,
            annotationTypeWriter.getAnnotationTypeDoc());
    }

    /**
     * {@inheritDoc}
     */
    public AnnotationTypeRequiredMemberWriter
            getAnnotationTypeRequiredMemberWriter(AnnotationTypeWriter annotationTypeWriter) throws Exception {
        return new AnnotationTypeRequiredMemberWriterImpl(
            (SubWriterHolderWriter) annotationTypeWriter,
            annotationTypeWriter.getAnnotationTypeDoc());
    }

    /**
     * {@inheritDoc}
     */
    public EnumConstantWriterImpl getEnumConstantWriter(ClassWriter classWriter)
            throws Exception {
        return new EnumConstantWriterImpl((SubWriterHolderWriter) classWriter,
            classWriter.getClassDoc());
    }

    /**
     * {@inheritDoc}
     */
    public FieldWriterImpl getFieldWriter(ClassWriter classWriter)
            throws Exception {
        return new FieldWriterImpl((SubWriterHolderWriter) classWriter,
            classWriter.getClassDoc());
    }

    /**
     * {@inheritDoc}
     */
    public MethodWriterImpl getMethodWriter(ClassWriter classWriter)
            throws Exception {
        return new MethodWriterImpl((SubWriterHolderWriter) classWriter,
            classWriter.getClassDoc());
    }

    /**
     * {@inheritDoc}
     */
    public ConstructorWriterImpl getConstructorWriter(ClassWriter classWriter)
            throws Exception {
        return new ConstructorWriterImpl((SubWriterHolderWriter) classWriter,
            classWriter.getClassDoc());
    }

    /**
     * {@inheritDoc}
     */
    public MemberSummaryWriter getMemberSummaryWriter(
            ClassWriter classWriter, int memberType)
            throws Exception {
        switch (memberType) {
            case VisibleMemberMap.CONSTRUCTORS:
                return getConstructorWriter(classWriter);
            case VisibleMemberMap.ENUM_CONSTANTS:
                return getEnumConstantWriter(classWriter);
            case VisibleMemberMap.FIELDS:
                return getFieldWriter(classWriter);
            case VisibleMemberMap.INNERCLASSES:
                return new NestedClassWriterImpl((SubWriterHolderWriter)
                    classWriter, classWriter.getClassDoc());
            case VisibleMemberMap.METHODS:
                return getMethodWriter(classWriter);
            default:
                return null;
        }
    }

    /**
     * {@inheritDoc}
     */
    public MemberSummaryWriter getMemberSummaryWriter(
        AnnotationTypeWriter annotationTypeWriter, int memberType)
    throws Exception {
        switch (memberType) {
            case VisibleMemberMap.ANNOTATION_TYPE_MEMBER_OPTIONAL:
                return (AnnotationTypeOptionalMemberWriterImpl)
                    getAnnotationTypeOptionalMemberWriter(annotationTypeWriter);
            case VisibleMemberMap.ANNOTATION_TYPE_MEMBER_REQUIRED:
                return (AnnotationTypeRequiredMemberWriterImpl)
                    getAnnotationTypeRequiredMemberWriter(annotationTypeWriter);
            default:
                return null;
        }
    }

    /**
     * {@inheritDoc}
     */
    public SerializedFormWriter getSerializedFormWriter() throws Exception {
        return new SerializedFormWriterImpl(configuration);
    }
}
