/*
 * Copyright (c) 2003, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.doclets.internal.toolkit.builders;

import com.sun.tools.doclets.internal.toolkit.*;
import com.sun.tools.doclets.internal.toolkit.util.*;
import com.sun.javadoc.*;

/**
 * The factory for constructing builders.
 *
 * This code is not part of an API.
 * It is implementation that is subject to change.
 * Do not use it as an API
 *
 * @author Jamie Ho
 * @since 1.4
 */

public class BuilderFactory {

    /**
     * The current configuration of the doclet.
     */
    private Configuration configuration;

    /**
     * The factory to retrieve the required writers from.
     */
    private WriterFactory writerFactory;

    /**
     * Construct a builder factory using the given configuration.
     * @param configuration the configuration for the current doclet
     * being executed.
     */
    public BuilderFactory (Configuration configuration) {
        this.configuration = configuration;
        this.writerFactory = configuration.getWriterFactory();
    }

    /**
     * Return the builder that builds the constant summary.
     * @return the builder that builds the constant summary.
     */
    public AbstractBuilder getConstantsSummaryBuider() throws Exception {
        return ConstantsSummaryBuilder.getInstance(configuration,
            writerFactory.getConstantsSummaryWriter());
    }

    /**
     * Return the builder that builds the package summary.
     *
     * @param pkg the package being documented.
     * @param prevPkg the previous package being documented.
     * @param nextPkg the next package being documented.
     * @return the builder that builds the constant summary.
     */
    public AbstractBuilder getPackageSummaryBuilder(PackageDoc pkg, PackageDoc prevPkg,
            PackageDoc nextPkg) throws Exception {
        return PackageSummaryBuilder.getInstance(configuration, pkg,
            writerFactory.getPackageSummaryWriter(pkg, prevPkg, nextPkg));
    }

    /**
     * Return the builder for the class.
     *
     * @param classDoc the class being documented.
     * @param prevClass the previous class that was documented.
     * @param nextClass the next class being documented.
     * @param classTree the class tree.
     * @return the writer for the class.  Return null if this
     * writer is not supported by the doclet.
     */
    public AbstractBuilder getClassBuilder(ClassDoc classDoc,
        ClassDoc prevClass, ClassDoc nextClass, ClassTree classTree)
            throws Exception {
        return ClassBuilder.getInstance(configuration, classDoc,
            writerFactory.getClassWriter(classDoc, prevClass, nextClass,
                classTree));
    }

    /**
     * Return the builder for the annotation type.
     *
     * @param annotationType the annotation type being documented.
     * @param prevType the previous type that was documented.
     * @param nextType the next type being documented.
     * @return the writer for the annotation type.  Return null if this
     * writer is not supported by the doclet.
     */
    public AbstractBuilder getAnnotationTypeBuilder(
        AnnotationTypeDoc annotationType,
        Type prevType, Type nextType)
            throws Exception {
        return AnnotationTypeBuilder.getInstance(configuration, annotationType,
            writerFactory.getAnnotationTypeWriter(annotationType, prevType,
            nextType));
    }

    /**
     * Return an instance of the method builder for the given class.
     *
     * @return an instance of the method builder for the given class.
     */
    public AbstractBuilder getMethodBuilder(ClassWriter classWriter)
           throws Exception {
        return MethodBuilder.getInstance(configuration,
            classWriter.getClassDoc(),
            writerFactory.getMethodWriter(classWriter));
    }

    /**
     * Return an instance of the annotation type member builder for the given
     * class.
     *
     * @return an instance of the annotation type memebr builder for the given
     *         annotation type.
     */
    public AbstractBuilder getAnnotationTypeOptionalMemberBuilder(
            AnnotationTypeWriter annotationTypeWriter)
    throws Exception {
        return AnnotationTypeOptionalMemberBuilder.getInstance(configuration,
            annotationTypeWriter.getAnnotationTypeDoc(),
            writerFactory.getAnnotationTypeOptionalMemberWriter(
                annotationTypeWriter));
    }

    /**
     * Return an instance of the annotation type member builder for the given
     * class.
     *
     * @return an instance of the annotation type memebr builder for the given
     *         annotation type.
     */
    public AbstractBuilder getAnnotationTypeRequiredMemberBuilder(
            AnnotationTypeWriter annotationTypeWriter)
    throws Exception {
        return AnnotationTypeRequiredMemberBuilder.getInstance(configuration,
            annotationTypeWriter.getAnnotationTypeDoc(),
            writerFactory.getAnnotationTypeRequiredMemberWriter(
                annotationTypeWriter));
    }

    /**
     * Return an instance of the enum constants builder for the given class.
     *
     * @return an instance of the enum constants builder for the given class.
     */
    public AbstractBuilder getEnumConstantsBuilder(ClassWriter classWriter)
            throws Exception {
        return EnumConstantBuilder.getInstance(configuration, classWriter.getClassDoc(),
            writerFactory.getEnumConstantWriter(classWriter));
    }

    /**
     * Return an instance of the field builder for the given class.
     *
     * @return an instance of the field builder for the given class.
     */
    public AbstractBuilder getFieldBuilder(ClassWriter classWriter)
            throws Exception {
        return FieldBuilder.getInstance(configuration, classWriter.getClassDoc(),
            writerFactory.getFieldWriter(classWriter));
    }

    /**
     * Return an instance of the constructor builder for the given class.
     *
     * @return an instance of the constructor builder for the given class.
     */
    public AbstractBuilder getConstructorBuilder(ClassWriter classWriter)
            throws Exception {
        return ConstructorBuilder.getInstance(configuration,
            classWriter.getClassDoc(), writerFactory.getConstructorWriter(
            classWriter));
    }

    /**
     * Return an instance of the member summary builder for the given class.
     *
     * @return an instance of the member summary builder for the given class.
     */
    public AbstractBuilder getMemberSummaryBuilder(ClassWriter classWriter)
            throws Exception {
        return MemberSummaryBuilder.getInstance(classWriter, configuration);
    }

    /**
     * Return an instance of the member summary builder for the given annotation
     * type.
     *
     * @return an instance of the member summary builder for the given
     *         annotation type.
     */
    public AbstractBuilder getMemberSummaryBuilder(
            AnnotationTypeWriter annotationTypeWriter)
    throws Exception {
        return MemberSummaryBuilder.getInstance(annotationTypeWriter,
            configuration);
    }

    /**
     * Return the builder that builds the serialized form.
     *
     * @return the builder that builds the serialized form.
     */
    public AbstractBuilder getSerializedFormBuilder()
            throws Exception {
        return SerializedFormBuilder.getInstance(configuration);
    }
}
