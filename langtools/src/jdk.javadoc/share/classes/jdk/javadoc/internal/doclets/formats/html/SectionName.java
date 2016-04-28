/*
 * Copyright (c) 2013, 2016, Oracle and/or its affiliates. All rights reserved.
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

/**
 * Enum representing various section names of generated API documentation.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @author Bhavesh Patel
 */
public enum SectionName {

    ANNOTATION_TYPE_ELEMENT_DETAIL("annotation.type.element.detail"),
    ANNOTATION_TYPE_FIELD_DETAIL("annotation.type.field.detail"),
    ANNOTATION_TYPE_FIELD_SUMMARY("annotation.type.field.summary"),
    ANNOTATION_TYPE_OPTIONAL_ELEMENT_SUMMARY("annotation.type.optional.element.summary"),
    ANNOTATION_TYPE_REQUIRED_ELEMENT_SUMMARY("annotation.type.required.element.summary"),
    CONSTRUCTOR_DETAIL("constructor.detail"),
    CONSTRUCTOR_SUMMARY("constructor.summary"),
    ENUM_CONSTANT_DETAIL("enum.constant.detail"),
    ENUM_CONSTANTS_INHERITANCE("enum.constants.inherited.from.class."),
    ENUM_CONSTANT_SUMMARY("enum.constant.summary"),
    FIELD_DETAIL("field.detail"),
    FIELDS_INHERITANCE("fields.inherited.from.class."),
    FIELD_SUMMARY("field.summary"),
    METHOD_DETAIL("method.detail"),
    METHODS_INHERITANCE("methods.inherited.from.class."),
    METHOD_SUMMARY("method.summary"),
    MODULE_DESCRIPTION("module.description"),
    NAVBAR_BOTTOM("navbar.bottom"),
    NAVBAR_BOTTOM_FIRSTROW("navbar.bottom.firstrow"),
    NAVBAR_TOP("navbar.top"),
    NAVBAR_TOP_FIRSTROW("navbar.top.firstrow"),
    NESTED_CLASSES_INHERITANCE("nested.classes.inherited.from.class."),
    NESTED_CLASS_SUMMARY("nested.class.summary"),
    OVERVIEW_DESCRIPTION("overview.description"),
    PACKAGE_DESCRIPTION("package.description"),
    PROPERTY_DETAIL("property.detail"),
    PROPERTIES_INHERITANCE("properties.inherited.from.class."),
    PROPERTY_SUMMARY("property.summary"),
    SKIP_NAVBAR_BOTTOM("skip.navbar.bottom"),
    SKIP_NAVBAR_TOP("skip.navbar.top"),
    UNNAMED_PACKAGE_ANCHOR("unnamed.package");

    private final String value;

    SectionName(String sName) {
        this.value = sName;
    }

    public String getName() {
        return this.value;
    }
}
