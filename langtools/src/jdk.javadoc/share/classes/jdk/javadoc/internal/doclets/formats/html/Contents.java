/*
 * Copyright (c) 1998, 2017, Oracle and/or its affiliates. All rights reserved.
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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jdk.javadoc.internal.doclets.formats.html.markup.ContentBuilder;
import jdk.javadoc.internal.doclets.formats.html.markup.FixedStringContent;
import jdk.javadoc.internal.doclets.formats.html.markup.RawHtml;
import jdk.javadoc.internal.doclets.formats.html.markup.StringContent;
import jdk.javadoc.internal.doclets.toolkit.Content;
import jdk.javadoc.internal.doclets.toolkit.Resources;
import jdk.javadoc.internal.doclets.toolkit.util.DocletConstants;

/**
 * Constants and factory methods for common fragments of content
 * used by HtmlDoclet. The string content of these fragments is
 * generally obtained from the {@link Resources resources} found
 * in the doclet's configuration.
 *
 * @implNote
 * Many constants are made available in this class, so that they are
 * only created once per doclet-instance, instead of once per generated page.
 */
public class Contents {
    public static final Content SPACE = RawHtml.nbsp;
    public static final Content ZERO_WIDTH_SPACE = RawHtml.zws;

    public final Content allClassesLabel;
    public final Content allImplementedInterfacesLabel;
    public final Content allModulesLabel;
    public final Content allPackagesLabel;
    public final Content allSuperinterfacesLabel;
    public final Content also;
    public final Content annotateTypeOptionalMemberSummaryLabel;
    public final Content annotateTypeRequiredMemberSummaryLabel;
    public final Content annotationType;
    public final Content annotationTypeDetailsLabel;
    public final Content annotationTypeMemberDetail;
    public final Content annotationTypes;
    public final Content classLabel;
    public final Content classes;
    public final Content constantsSummaryTitle;
    public final Content constructorDetailsLabel;
    public final Content constructorSummaryLabel;
    public final Content constructors;
    public final Content contentsHeading;
    public final Content defaultPackageLabel;
    public final Content default_;
    public final Content deprecatedAPI;
    public final Content deprecatedLabel;
    public final Content deprecatedPhrase;
    public final Content deprecatedForRemovalPhrase;
    public final Content descfrmClassLabel;
    public final Content descfrmInterfaceLabel;
    public final Content descriptionLabel;
    public final Content detailLabel;
    public final Content enclosingClassLabel;
    public final Content enclosingInterfaceLabel;
    public final Content enumConstantDetailLabel;
    public final Content enumConstantSummary;
    public final Content enum_;
    public final Content enums;
    public final Content errors;
    public final Content exceptions;
    public final Content fieldDetailsLabel;
    public final Content fieldSummaryLabel;
    public final Content fields;
    public final Content framesLabel;
    public final Content functionalInterface;
    public final Content functionalInterfaceMessage;
    public final Content helpLabel;
    public final Content hierarchyForAllPackages;
    public final Content implementation;
    public final Content implementingClassesLabel;
    public final Content inClass;
    public final Content inInterface;
    public final Content indexLabel;
    public final Content interfaces;
    public final Content interfacesItalic;
    public final Content methodDetailLabel;
    public final Content methodSummary;
    public final Content methods;
    public final Content moduleLabel;
    public final Content module_;
    public final Content moduleSubNavLabel;
    public final Content modulesLabel;
    public final Content navAnnotationTypeMember;
    public final Content navAnnotationTypeOptionalMember;
    public final Content navAnnotationTypeRequiredMember;
    public final Content navConstructor;
    public final Content navEnum;
    public final Content navField;
    public final Content navMethod;
    public final Content navModuleDescription;
    public final Content navModules;
    public final Content navNested;
    public final Content navPackages;
    public final Content navProperty;
    public final Content navServices;
    public final Content nestedClassSummary;
    public final Content nextClassLabel;
    public final Content nextLabel;
    public final Content nextLetter;
    public final Content nextModuleLabel;
    public final Content nextPackageLabel;
    public final Content noFramesLabel;
    public final Content noScriptMessage;
    public final Content openModuleLabel;
    public final Content overridesLabel;
    public final Content overviewLabel;
    public final Content packageHierarchies;
    public final Content packageLabel;
    public final Content package_;
    public final Content packagesLabel;
    public final Content prevClassLabel;
    public final Content prevLabel;
    public final Content prevLetter;
    public final Content prevModuleLabel;
    public final Content prevPackageLabel;
    public final Content properties;
    public final Content propertyDetailsLabel;
    public final Content propertySummary;
    public final Content seeLabel;
    public final Content serializedForm;
    public final Content specifiedByLabel;
    public final Content subclassesLabel;
    public final Content subinterfacesLabel;
    public final Content summaryLabel;
    public final Content treeLabel;
    public final Content useLabel;

    private final Resources resources;

    /**
     * Creates a {@code Contents} object.
     *
     * @param configuration the configuration in which to find the
     * resources used to look up resource keys, and other details.
     */
    Contents(ConfigurationImpl configuration) {
        this.resources = configuration.getResources();

        allClassesLabel = getNonBreakContent("doclet.All_Classes");
        allImplementedInterfacesLabel = getContent("doclet.All_Implemented_Interfaces");
        allModulesLabel = getNonBreakContent("doclet.All_Modules");
        allPackagesLabel = getNonBreakContent("doclet.All_Packages");
        allSuperinterfacesLabel = getContent("doclet.All_Superinterfaces");
        also = getContent("doclet.also");
        annotateTypeOptionalMemberSummaryLabel = getContent("doclet.Annotation_Type_Optional_Member_Summary");
        annotateTypeRequiredMemberSummaryLabel = getContent("doclet.Annotation_Type_Required_Member_Summary");
        annotationType = getContent("doclet.AnnotationType");
        annotationTypeDetailsLabel = getContent("doclet.Annotation_Type_Member_Detail");
        annotationTypeMemberDetail = getContent("doclet.Annotation_Type_Member_Detail");
        annotationTypes = getContent("doclet.AnnotationTypes");
        classLabel = getContent("doclet.Class");
        classes = getContent("doclet.Classes");
        constantsSummaryTitle = getContent("doclet.Constants_Summary");
        constructorDetailsLabel = getContent("doclet.Constructor_Detail");
        constructorSummaryLabel = getContent("doclet.Constructor_Summary");
        constructors = getContent("doclet.Constructors");
        contentsHeading = getContent("doclet.Contents");
        defaultPackageLabel = new StringContent(DocletConstants.DEFAULT_PACKAGE_NAME);
        default_ = getContent("doclet.Default");
        deprecatedAPI = getContent("doclet.Deprecated_API");
        deprecatedLabel = getContent("doclet.navDeprecated");
        deprecatedPhrase = getContent("doclet.Deprecated");
        deprecatedForRemovalPhrase = getContent("doclet.DeprecatedForRemoval");
        descfrmClassLabel = getContent("doclet.Description_From_Class");
        descfrmInterfaceLabel = getContent("doclet.Description_From_Interface");
        descriptionLabel = getContent("doclet.Description");
        detailLabel = getContent("doclet.Detail");
        enclosingClassLabel = getContent("doclet.Enclosing_Class");
        enclosingInterfaceLabel = getContent("doclet.Enclosing_Interface");
        enumConstantDetailLabel = getContent("doclet.Enum_Constant_Detail");
        enumConstantSummary = getContent("doclet.Enum_Constant_Summary");
        enum_ = getContent("doclet.Enum");
        enums = getContent("doclet.Enums");
        errors = getContent("doclet.Errors");
        exceptions = getContent("doclet.Exceptions");
        fieldDetailsLabel = getContent("doclet.Field_Detail");
        fieldSummaryLabel = getContent("doclet.Field_Summary");
        fields = getContent("doclet.Fields");
        framesLabel = getContent("doclet.Frames");
        functionalInterface = getContent("doclet.Functional_Interface");
        functionalInterfaceMessage = getContent("doclet.Functional_Interface_Message");
        helpLabel = getContent("doclet.Help");
        hierarchyForAllPackages = getContent("doclet.Hierarchy_For_All_Packages");
        implementation = getContent("doclet.Implementation");
        implementingClassesLabel = getContent("doclet.Implementing_Classes");
        inClass = getContent("doclet.in_class");
        inInterface = getContent("doclet.in_interface");
        indexLabel = getContent("doclet.Index");
        interfaces = getContent("doclet.Interfaces");
        interfacesItalic = getContent("doclet.Interfaces_Italic");
        methodDetailLabel = getContent("doclet.Method_Detail");
        methodSummary = getContent("doclet.Method_Summary");
        methods = getContent("doclet.Methods");
        moduleLabel = getContent("doclet.Module");
        module_ = getContent("doclet.module");
        moduleSubNavLabel = getContent("doclet.Module_Sub_Nav");
        modulesLabel = getContent("doclet.Modules");
        navAnnotationTypeMember = getContent("doclet.navAnnotationTypeMember");
        navAnnotationTypeOptionalMember = getContent("doclet.navAnnotationTypeOptionalMember");
        navAnnotationTypeRequiredMember = getContent("doclet.navAnnotationTypeRequiredMember");
        navConstructor = getContent("doclet.navConstructor");
        navEnum = getContent("doclet.navEnum");
        navField = getContent("doclet.navField");
        navMethod = getContent("doclet.navMethod");
        navModuleDescription = getContent("doclet.navModuleDescription");
        navModules = getContent("doclet.navModules");
        navNested = getContent("doclet.navNested");
        navPackages = getContent("doclet.navPackages");
        navProperty = getContent("doclet.navProperty");
        navServices = getContent("doclet.navServices");
        nestedClassSummary = getContent("doclet.Nested_Class_Summary");
        nextClassLabel = getNonBreakContent("doclet.Next_Class");
        nextLabel = getNonBreakContent("doclet.Next");
        nextLetter = getContent("doclet.Next_Letter");
        nextModuleLabel = getNonBreakContent("doclet.Next_Module");
        nextPackageLabel = getNonBreakContent("doclet.Next_Package");
        noFramesLabel = getNonBreakContent("doclet.No_Frames");
        noScriptMessage = getContent("doclet.No_Script_Message");
        openModuleLabel = getContent("doclet.Open_Module");
        overridesLabel = getContent("doclet.Overrides");
        overviewLabel = getContent("doclet.Overview");
        packageHierarchies = getContent("doclet.Package_Hierarchies");
        packageLabel = getContent("doclet.Package");
        package_ = getContent("doclet.package");
        packagesLabel = getContent("doclet.Packages");
        prevClassLabel = getNonBreakContent("doclet.Prev_Class");
        prevLabel = getContent("doclet.Prev");
        prevLetter = getContent("doclet.Prev_Letter");
        prevModuleLabel = getNonBreakContent("doclet.Prev_Module");
        prevPackageLabel = getNonBreakContent("doclet.Prev_Package");
        properties = getContent("doclet.Properties");
        propertyDetailsLabel = getContent("doclet.Property_Detail");
        propertySummary = getContent("doclet.Property_Summary");
        seeLabel = getContent("doclet.See");
        serializedForm = getContent("doclet.Serialized_Form");
        specifiedByLabel = getContent("doclet.Specified_By");
        subclassesLabel = getContent("doclet.Subclasses");
        subinterfacesLabel = getContent("doclet.Subinterfaces");
        summaryLabel = getContent("doclet.Summary");
        treeLabel = getContent("doclet.Tree");
        useLabel = getContent("doclet.navClassUse");
    }

    /**
     * Gets a {@code Content} object, containing the string for
     * a given key in the doclet's resources.
     *
     * @param key the key for the desired string
     * @return a content tree for the string
     */
    public Content getContent(String key) {
        return new FixedStringContent(resources.getText(key));
    }

    /**
     * Gets a {@code Content} object, containing the string for
     * a given key in the doclet's resources, formatted with
     * given arguments.
     *
     * @param key the key to look for in the configuration fil
     * @param key the key for the desired string
     * @param o0  string or content argument to be formatted into the result
     * @return a content tree for the text
     */
    public Content getContent(String key, Object o0) {
        return getContent(key, o0, null, null);
    }

    /**
     * Gets a {@code Content} object, containing the string for
     * a given key in the doclet's resources, formatted with
     * given arguments.

     * @param key the key for the desired string
     * @param o0  string or content argument to be formatted into the result
     * @param o1  string or content argument to be formatted into the result
     * @return a content tree for the text
     */
    public Content getContent(String key, Object o0, Object o1) {
        return getContent(key, o0, o1, null);
    }

    /**
     * Gets a {@code Content} object, containing the string for
     * a given key in the doclet's resources, formatted with
     * given arguments.
     *
     * @param key the key for the desired string
     * @param o0  string or content argument to be formatted into the result
     * @param o1  string or content argument to be formatted into the result
     * @param o2  string or content argument to be formatted into the result
     * @return a content tree for the text
     */
    public Content getContent(String key, Object o0, Object o1, Object o2) {
        Content c = new ContentBuilder();
        Pattern p = Pattern.compile("\\{([012])\\}");
        String text = resources.getText(key); // TODO: cache
        Matcher m = p.matcher(text);
        int start = 0;
        while (m.find(start)) {
            c.addContent(text.substring(start, m.start()));

            Object o = null;
            switch (m.group(1).charAt(0)) {
                case '0': o = o0; break;
                case '1': o = o1; break;
                case '2': o = o2; break;
            }

            if (o == null) {
                c.addContent("{" + m.group(1) + "}");
            } else if (o instanceof String) {
                c.addContent((String) o);
            } else if (o instanceof Content) {
                c.addContent((Content) o);
            }

            start = m.end();
        }

        c.addContent(text.substring(start));
        return c;
    }

    /**
     * Gets a {@code Content} object, containing the string for
     * a given key in the doclet's resources, substituting
     * <code>&nbsp;</code> for any space characters found in
     * the named resource string.
     *
     * @param key the key for the desired string
     * @return a content tree for the string
     */
    private Content getNonBreakContent(String key) {
        String text = resources.getText(key); // TODO: cache
        Content c = new ContentBuilder();
        int start = 0;
        int p;
        while ((p = text.indexOf(" ", start)) != -1) {
            c.addContent(text.substring(start, p));
            c.addContent(RawHtml.nbsp);
            start = p + 1;
        }
        c.addContent(text.substring(start));
        return c; // TODO: should be made immutable
    }
}
