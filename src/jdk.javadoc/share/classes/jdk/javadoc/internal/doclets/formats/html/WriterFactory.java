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


import java.util.List;

import javax.lang.model.element.Element;
import javax.lang.model.element.ModuleElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;

import jdk.javadoc.internal.doclets.toolkit.DocFileElement;
import jdk.javadoc.internal.doclets.toolkit.util.ClassTree;
import jdk.javadoc.internal.doclets.toolkit.util.ClassUseMapper;
import jdk.javadoc.internal.doclets.toolkit.util.DocPath;
import jdk.javadoc.internal.doclets.toolkit.util.IndexBuilder;
import jdk.javadoc.internal.doclets.toolkit.util.VisibleMemberTable;

/**
 * The factory that returns HTML writers, to be used to generate pages in the overall API documentation.
 */
public class WriterFactory {

    private final HtmlConfiguration configuration;

    public WriterFactory(HtmlConfiguration configuration) {
        this.configuration = configuration;
    }

    /**
     * {@return a new writer for the page for a module}
     */
    public HtmlDocletWriter newModuleWriter(ModuleElement mdle) {
        return new ModuleWriter(configuration, mdle);
    }

    /**
     * {@return a new writer for the "module index" page}
     */
    public HtmlDocletWriter newModuleIndexWriter() {
        return new ModuleIndexWriter(configuration);
    }

    /**
     * {@return a new writer for the page for a package}
     */
    public HtmlDocletWriter newPackageWriter(PackageElement packageElement) {
        return new PackageWriter(configuration, packageElement);
    }

    /**
     * {@return a new writer for the "package index" page}
     */
    public HtmlDocletWriter newPackageIndexWriter() {
        return new PackageIndexWriter(configuration);
    }

    /**
     * {@return a new writer for the "package use" page for a package}
     */
    public HtmlDocletWriter newPackageUseWriter(PackageElement packageElement, ClassUseMapper mapper) {
        return new PackageUseWriter(configuration, mapper, packageElement);
    }

    /**
     * {@return a new writer for the page for a class or other type element}
     */
    public HtmlDocletWriter newClassWriter(TypeElement typeElement, ClassTree classTree) {
        return new ClassWriter(configuration, typeElement, classTree);
    }

    /**
     * {@return a new writer for the "class use" page for a class or other type element}
     */
    public HtmlDocletWriter newClassUseWriter(TypeElement typeElement, ClassUseMapper mapper) {
        return new ClassUseWriter(configuration, mapper, typeElement);
    }

    /**
     * {@return a new writer for the list of "all classes"}
     */
    public HtmlDocletWriter newAllClassesIndexWriter() {
        return new AllClassesIndexWriter(configuration);
    }

    /**
     * {@return a new writer for the list of "all packages"}
     */
    public HtmlDocletWriter newAllPackagesIndexWriter() {
        return new AllPackagesIndexWriter(configuration);
    }

    /**
     * {@return a new writer for the "constants summary" page}
     */
    public HtmlDocletWriter newConstantsSummaryWriter() {
        return new ConstantsSummaryWriter(configuration);
    }

    /**
     * {@return a new writer for the page giving the API that has been deprecated in recent releases}
     */
    public HtmlDocletWriter newDeprecatedListWriter() {
        return new DeprecatedListWriter(configuration);
    }

    /**
     * {@return a new writer for a "doc-file" page}
     */
    public HtmlDocletWriter newDocFileWriter(DocPath path, DocFileElement dfElement) {
        return new DocFilesHandler.DocFileWriter(configuration, path, dfElement);
    }

    /**
     * {@return a new writer for the page listing external specifications referenced in the API}
     */
    public HtmlDocletWriter newExternalSpecsWriter() {
        return new ExternalSpecsWriter(configuration);
    }

    /**
     * {@return a new writer for the "help" page}
     */
    public HtmlDocletWriter newHelpWriter() {
        return new HelpWriter(configuration);
    }

    /**
     * {@return a new writer for an "index" page}
     */
    public HtmlDocletWriter newIndexWriter(DocPath path, List<Character> allFirstCharacters, List<Character> displayFirstCharacters) {
        return new IndexWriter(configuration, path, allFirstCharacters, displayFirstCharacters);
    }

    /**
     * {@return a new writer for the list of new API in recent releases}
     */
    public HtmlDocletWriter newNewAPIListWriter() {
        return new NewAPIListWriter(configuration);
    }

    /**
     * {@return a new writer for the list of preview API in this release}
     */
    public HtmlDocletWriter newPreviewListWriter() {
        return new PreviewListWriter(configuration);
    }

    /**
     * {@return a new writer for the "search" page}
     */
    public HtmlDocletWriter newSearchWriter() {
        return new SearchWriter(configuration);
    }

    /**
     * {@return a new writer for the page giving the serialized forms of classes and other type elements}
     */
    public HtmlDocletWriter newSerializedFormWriter() {
        return new SerializedFormWriter(configuration);
    }

    /**
     * {@return a new writer for the page listing system properties referenced in the API}
     */
    public HtmlDocletWriter newSystemPropertiesWriter() {
        return new SystemPropertiesWriter(configuration);
    }

    /**
     * {@return a new writer for the page showing the hierarchy of classes and their superclasses}
     */
    public HtmlDocletWriter newTreeWriter(ClassTree classTree) {
        return new TreeWriter(configuration, classTree);
    }

    /**
     * Returns a new member writer for the members of a given class and given kind.
     *
     * @param classWriter the writer for the enclosing class
     * @param kind the kind
     *
     * @return the writer
     */
    public AbstractMemberWriter newMemberWriter(ClassWriter classWriter,
                                                VisibleMemberTable.Kind kind) {
        return switch (kind) {
            case ANNOTATION_TYPE_MEMBER,
                    ANNOTATION_TYPE_MEMBER_OPTIONAL,
                    ANNOTATION_TYPE_MEMBER_REQUIRED -> new AnnotationTypeMemberWriter(classWriter, kind);
            case CONSTRUCTORS -> new ConstructorWriter(classWriter);
            case ENUM_CONSTANTS -> new EnumConstantWriter(classWriter);
            case FIELDS -> new FieldWriter(classWriter);
            case NESTED_CLASSES -> new NestedClassWriter(classWriter);
            case METHODS -> new MethodWriter(classWriter);
            case PROPERTIES -> new PropertyWriter(classWriter);
        };
    }

    /**
     * {@return a new {@link DocFilesHandler}}
     */
    public DocFilesHandler newDocFilesHandler(Element element) {
        return new DocFilesHandler(configuration, element);
    }
}
