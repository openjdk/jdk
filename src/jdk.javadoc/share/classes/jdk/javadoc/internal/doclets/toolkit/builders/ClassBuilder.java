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

package jdk.javadoc.internal.doclets.toolkit.builders;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Name;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;

import jdk.javadoc.internal.doclets.formats.html.markup.ContentBuilder;
import jdk.javadoc.internal.doclets.toolkit.ClassWriter;
import jdk.javadoc.internal.doclets.toolkit.CommentUtils;
import jdk.javadoc.internal.doclets.toolkit.Content;
import jdk.javadoc.internal.doclets.toolkit.DocFilesHandler;
import jdk.javadoc.internal.doclets.toolkit.DocletException;
import jdk.javadoc.internal.doclets.toolkit.util.DocFileIOException;
import jdk.javadoc.internal.doclets.toolkit.util.Utils;

/**
 * Builds the summary for a given class.
 */
public class ClassBuilder extends AbstractBuilder {

    /**
     * The class being documented.
     */
    private final TypeElement typeElement;

    /**
     * The doclet specific writer.
     */
    private final ClassWriter writer;

    private final Utils utils;

    /**
     * Construct a new ClassBuilder.
     *
     * @param context  the build context
     * @param typeElement the class being documented.
     * @param writer the doclet specific writer.
     */
    private ClassBuilder(Context context, TypeElement typeElement, ClassWriter writer) {
        super(context);
        this.typeElement = typeElement;
        this.writer = writer;
        this.utils = configuration.utils;
        switch (typeElement.getKind()) {
            case ENUM   -> setEnumDocumentation(typeElement);
            case RECORD -> setRecordDocumentation(typeElement);
        }
    }

    /**
     * Constructs a new ClassBuilder.
     *
     * @param context  the build context
     * @param typeElement the class being documented.
     * @param writer the doclet specific writer.
     * @return the new ClassBuilder
     */
    public static ClassBuilder getInstance(Context context, TypeElement typeElement, ClassWriter writer) {
        return new ClassBuilder(context, typeElement, writer);
    }

    @Override
    public void build() throws DocletException {
        buildClassDoc();
    }

    /**
     * Handles the {@literal <TypeElement>} tag.
     *
     * @throws DocletException if there is a problem while building the documentation
     */
    protected void buildClassDoc() throws DocletException {
        String key = switch (typeElement.getKind()) {
            case INTERFACE       -> "doclet.Interface";
            case ENUM            -> "doclet.Enum";
            case RECORD          -> "doclet.RecordClass";
            case ANNOTATION_TYPE -> "doclet.AnnotationType";
            case CLASS           -> "doclet.Class";
            default -> throw new IllegalStateException(typeElement.getKind() + " " + typeElement);
        };
        Content content = writer.getHeader(resources.getText(key) + " "
                + utils.getSimpleName(typeElement));
        Content classContent = writer.getClassContentHeader();

        buildClassTree(classContent);
        buildClassInfo(classContent);
        buildMemberSummary(classContent);
        buildMemberDetails(classContent);

        writer.addClassContent(classContent);
        writer.addFooter();
        writer.printDocument(content);
        copyDocFiles();
    }

    /**
     * Build the class inheritance tree documentation.
     *
     * @param classContent the content to which the documentation will be added
     */
    protected void buildClassTree(Content classContent) {
        writer.addClassTree(classContent);
    }

    /**
     * Build the class information documentation.
     *
     * @param target the content to which the documentation will be added
     * @throws DocletException if there is a problem while building the documentation
     */
    protected void buildClassInfo(Content target) throws DocletException {
        Content c = new ContentBuilder();
        buildParamInfo(c);
        buildSuperInterfacesInfo(c);
        buildImplementedInterfacesInfo(c);
        buildSubClassInfo(c);
        buildSubInterfacesInfo(c);
        buildInterfaceUsageInfo(c);
        buildNestedClassInfo(c);
        buildFunctionalInterfaceInfo(c);
        buildClassSignature(c);
        buildDeprecationInfo(c);
        buildClassDescription(c);
        buildClassTagInfo(c);

        target.add(writer.getClassInfo(c));
    }

    /**
     * Build the type parameters and state components of this class.
     *
     * @param target the content to which the documentation will be added
     */
    protected void buildParamInfo(Content target) {
        writer.addParamInfo(target);
    }

    /**
     * If this is an interface, list all super interfaces.
     *
     * @param target the content to which the documentation will be added
     */
    protected void buildSuperInterfacesInfo(Content target) {
        writer.addSuperInterfacesInfo(target);
    }

    /**
     * If this is a class, list all interfaces implemented by this class.
     *
     * @param target the content to which the documentation will be added
     */
    protected void buildImplementedInterfacesInfo(Content target) {
        writer.addImplementedInterfacesInfo(target);
    }

    /**
     * List all the classes that extend this one.
     *
     * @param target the content to which the documentation will be added
     */
    protected void buildSubClassInfo(Content target) {
        writer.addSubClassInfo(target);
    }

    /**
     * List all the interfaces that extend this one.
     *
     * @param target the content to which the documentation will be added
     */
    protected void buildSubInterfacesInfo(Content target) {
        writer.addSubInterfacesInfo(target);
    }

    /**
     * If this is an interface, list all classes that implement this interface.
     *
     * @param target the content to which the documentation will be added
     */
    protected void buildInterfaceUsageInfo(Content target) {
        writer.addInterfaceUsageInfo(target);
    }

    /**
     * If this is an functional interface, display appropriate message.
     *
     * @param target the content to which the documentation will be added
     */
    protected void buildFunctionalInterfaceInfo(Content target) {
        writer.addFunctionalInterfaceInfo(target);
    }

    /**
     * If this class is deprecated, build the appropriate information.
     *
     * @param target the content to which the documentation will be added
     */
    protected void buildDeprecationInfo(Content target) {
        writer.addClassDeprecationInfo(target);
    }

    /**
     * If this is an inner class or interface, list the enclosing class or interface.
     *
     * @param target the content to which the documentation will be added
     */
    protected void buildNestedClassInfo(Content target) {
        writer.addNestedClassInfo(target);
    }

    /**
     * Copy the doc files.
     *
     * @throws DocFileIOException if there is a problem while copying the files
     */
    private void copyDocFiles() throws DocletException {
        PackageElement containingPackage = utils.containingPackage(typeElement);
        if ((configuration.packages == null ||
            !configuration.packages.contains(containingPackage)) &&
            !containingPackagesSeen.contains(containingPackage)) {
            //Only copy doc files dir if the containing package is not
            //documented AND if we have not documented a class from the same
            //package already. Otherwise, we are making duplicate copies.
            DocFilesHandler docFilesHandler = configuration
                    .getWriterFactory()
                    .getDocFilesHandler(containingPackage);
            docFilesHandler.copyDocFiles();
            containingPackagesSeen.add(containingPackage);
        }
    }

    /**
     * Build the signature of the current class.
     *
     * @param target the content to which the documentation will be added
     */
    protected void buildClassSignature(Content target) {
        writer.addClassSignature(target);
    }

    /**
     * Build the class description.
     *
     * @param target the content to which the documentation will be added
     */
    protected void buildClassDescription(Content target) {
        writer.addClassDescription(target);
    }

    /**
     * Build the tag information for the current class.
     *
     * @param target the content to which the documentation will be added
     */
    protected void buildClassTagInfo(Content target) {
        writer.addClassTagInfo(target);
    }

    /**
     * Build the member summary contents of the page.
     *
     * @param classContent the content to which the documentation will be added
     * @throws DocletException if there is a problem while building the documentation
     */
    protected void buildMemberSummary(Content classContent) throws DocletException {
        Content summariesList = writer.getSummariesList();
        builderFactory.getMemberSummaryBuilder(writer).build(summariesList);
        classContent.add(writer.getMemberSummary(summariesList));
    }

    /**
     * Build the member details contents of the page.
     *
     * @param classContent the content to which the documentation will be added
     * @throws DocletException if there is a problem while building the documentation
     */
    protected void buildMemberDetails(Content classContent) throws DocletException {
        Content detailsList = writer.getDetailsList();

        buildEnumConstantsDetails(detailsList);
        buildPropertyDetails(detailsList);
        buildFieldDetails(detailsList);
        buildConstructorDetails(detailsList);
        buildAnnotationTypeMemberDetails(detailsList);
        buildMethodDetails(detailsList);

        classContent.add(writer.getMemberDetails(detailsList));
    }

    /**
     * Build the enum constants documentation.
     *
     * @param detailsList the content to which the documentation will be added
     * @throws DocletException if there is a problem while building the documentation
     */
    protected void buildEnumConstantsDetails(Content detailsList) throws DocletException {
        builderFactory.getEnumConstantsBuilder(writer).build(detailsList);
    }

    /**
     * Build the field documentation.
     *
     * @param detailsList the content to which the documentation will be added
     * @throws DocletException if there is a problem while building the documentation
     */
    protected void buildFieldDetails(Content detailsList) throws DocletException {
        builderFactory.getFieldBuilder(writer).build(detailsList);
    }

    /**
     * Build the property documentation.
     *
     * @param detailsList the content to which the documentation will be added
     * @throws DocletException if there is a problem while building the documentation
     */
    public void buildPropertyDetails( Content detailsList) throws DocletException {
        builderFactory.getPropertyBuilder(writer).build(detailsList);
    }

    /**
     * Build the constructor documentation.
     *
     * @param detailsList the content to which the documentation will be added
     * @throws DocletException if there is a problem while building the documentation
     */
    protected void buildConstructorDetails(Content detailsList) throws DocletException {
        builderFactory.getConstructorBuilder(writer).build(detailsList);
    }

    /**
     * Build the method documentation.
     *
     * @param detailsList the content to which the documentation will be added
     * @throws DocletException if there is a problem while building the documentation
     */
    protected void buildMethodDetails(Content detailsList) throws DocletException {
        builderFactory.getMethodBuilder(writer).build(detailsList);
    }

    /**
     * Build the annotation type optional member documentation.
     *
     * @param target the content to which the documentation will be added
     * @throws DocletException if there is a problem building the documentation
     */
    protected void buildAnnotationTypeMemberDetails(Content target)
            throws DocletException {
        builderFactory.getAnnotationTypeMemberBuilder(writer).build(target);
    }

    /**
     * The documentation for values() and valueOf() in Enums are set by the
     * doclet only iff the user or overridden methods are missing.
     * @param elem the enum element
     */
    private void setEnumDocumentation(TypeElement elem) {
        CommentUtils cmtUtils = configuration.cmtUtils;
        for (ExecutableElement ee : utils.getMethods(elem)) {
            if (!utils.getFullBody(ee).isEmpty()) // ignore if already set
                continue;
            Name name = ee.getSimpleName();
            if (name.contentEquals("values") && ee.getParameters().isEmpty()) {
                utils.removeCommentHelper(ee); // purge previous entry
                cmtUtils.setEnumValuesTree(ee);
            } else if (name.contentEquals("valueOf") && ee.getParameters().size() == 1) {
                // TODO: check parameter type
                utils.removeCommentHelper(ee); // purge previous entry
                cmtUtils.setEnumValueOfTree(ee);
            }
        }
    }

    /**
     * Sets the documentation as needed for the mandated parts of a record type.
     * This includes the canonical constructor, methods like {@code equals},
     * {@code hashCode}, {@code toString}, the accessor methods, and the underlying
     * field.
     * @param elem the record element
     */

    private void setRecordDocumentation(TypeElement elem) {
        CommentUtils cmtUtils = configuration.cmtUtils;
        Set<Name> componentNames = elem.getRecordComponents().stream()
                .map(Element::getSimpleName)
                .collect(Collectors.toSet());

        for (ExecutableElement ee : utils.getConstructors(elem)) {
            if (utils.isCanonicalRecordConstructor(ee)) {
                if (utils.getFullBody(ee).isEmpty()) {
                    utils.removeCommentHelper(ee); // purge previous entry
                    cmtUtils.setRecordConstructorTree(ee);
                }
                // only one canonical constructor; no need to keep looking
                break;
            }
        }

        var fields = utils.isSerializable(elem)
                ? utils.getFieldsUnfiltered(elem)
                : utils.getFields(elem);
        for (VariableElement ve : fields) {
            // The fields for the record component cannot be declared by the
            // user and so cannot have any pre-existing comment.
            Name name = ve.getSimpleName();
            if (componentNames.contains(name)) {
                utils.removeCommentHelper(ve); // purge previous entry
                cmtUtils.setRecordFieldTree(ve);
            }
        }

        TypeMirror objectType = utils.getObjectType();

        for (ExecutableElement ee : utils.getMethods(elem)) {
            if (!utils.getFullBody(ee).isEmpty()) {
                continue;
            }

            Name name = ee.getSimpleName();
            List<? extends VariableElement> params = ee.getParameters();
            if (name.contentEquals("equals")) {
                if (params.size() == 1 && utils.typeUtils.isSameType(params.get(0).asType(), objectType)) {
                    utils.removeCommentHelper(ee); // purge previous entry
                    cmtUtils.setRecordEqualsTree(ee);
                }
            } else if (name.contentEquals("hashCode")) {
                if (params.isEmpty()) {
                    utils.removeCommentHelper(ee); // purge previous entry
                    cmtUtils.setRecordHashCodeTree(ee);
                }
            } else if (name.contentEquals("toString")) {
                if (params.isEmpty()) {
                    utils.removeCommentHelper(ee); // purge previous entry
                    cmtUtils.setRecordToStringTree(ee);
                }
            } else if (componentNames.contains(name)) {
                if (params.isEmpty()) {
                    utils.removeCommentHelper(ee); // purge previous entry
                    cmtUtils.setRecordAccessorTree(ee);
                }
            }
        }

    }
}
