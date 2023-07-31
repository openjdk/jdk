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

import jdk.javadoc.internal.doclets.toolkit.util.ClassTree;
import jdk.javadoc.internal.doclets.toolkit.util.VisibleMemberTable;

/**
 * The factory that returns HTML writers.
 */
public class WriterFactory {

    private final HtmlConfiguration configuration;

    public WriterFactory(HtmlConfiguration configuration) {
        this.configuration = configuration;
    }

    /**
     * {@return a new {@link ConstantsSummaryWriter}}
     */
    public ConstantsSummaryWriter newConstantsSummaryWriter() {
        return new ConstantsSummaryWriter(configuration);
    }

    /**
     * {@return a new {@link PackageWriter}}
     */
    public PackageWriter newPackageWriter(PackageElement packageElement) {
        return new PackageWriter(configuration, packageElement);
    }

    /**
     * {@return a new {@link ModuleWriter}}
     */
    public ModuleWriter newModuleWriter(ModuleElement mdle) {
        return new ModuleWriter(configuration, mdle);
    }

    /**
     * {@return a new {@link ClassWriter}}
     */
    public ClassWriter newClassWriter(TypeElement typeElement, ClassTree classTree) {
        return new ClassWriter(configuration, typeElement, classTree);
    }
    /**
     * {@return a new {@link SerializedFormWriter}}
     */
    public SerializedFormWriter newSerializedFormWriter() {
        return new SerializedFormWriter(configuration);
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
