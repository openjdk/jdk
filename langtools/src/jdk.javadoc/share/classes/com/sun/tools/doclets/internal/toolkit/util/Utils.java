/*
 * Copyright (c) 1999, 2015, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.doclets.internal.toolkit.util;

import java.io.*;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Target;
import java.text.Collator;
import java.util.*;

import javax.tools.JavaFileManager.Location;
import javax.tools.StandardLocation;

import com.sun.javadoc.*;
import com.sun.javadoc.AnnotationDesc.ElementValuePair;
import com.sun.tools.doclets.internal.toolkit.*;
import com.sun.tools.javac.util.StringUtils;

/**
 * Utilities Class for Doclets.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @author Atul M Dambalkar
 * @author Jamie Ho
 */
public class Utils {
    /**
     * Return array of class members whose documentation is to be generated.
     * If the member is deprecated do not include such a member in the
     * returned array.
     *
     * @param  members             Array of members to choose from.
     * @return ProgramElementDoc[] Array of eligible members for whom
     *                             documentation is getting generated.
     */
    public ProgramElementDoc[] excludeDeprecatedMembers(
        ProgramElementDoc[] members) {
        return
            toProgramElementDocArray(excludeDeprecatedMembersAsList(members));
    }

    /**
     * Return array of class members whose documentation is to be generated.
     * If the member is deprecated do not include such a member in the
     * returned array.
     *
     * @param  members    Array of members to choose from.
     * @return List       List of eligible members for whom
     *                    documentation is getting generated.
     */
    public List<ProgramElementDoc> excludeDeprecatedMembersAsList(
        ProgramElementDoc[] members) {
        List<ProgramElementDoc> list = new ArrayList<>();
        for (ProgramElementDoc member : members) {
            if (member.tags("deprecated").length == 0) {
                list.add(member);
            }
        }
        Collections.sort(list);
        return list;
    }

    /**
     * Return the list of ProgramElementDoc objects as Array.
     */
    public ProgramElementDoc[] toProgramElementDocArray(List<ProgramElementDoc> list) {
        ProgramElementDoc[] pgmarr = new ProgramElementDoc[list.size()];
        for (int i = 0; i < list.size(); i++) {
            pgmarr[i] = list.get(i);
        }
        return pgmarr;
    }

    /**
     * Return true if a non-public member found in the given array.
     *
     * @param  members Array of members to look into.
     * @return boolean True if non-public member found, false otherwise.
     */
    public boolean nonPublicMemberFound(ProgramElementDoc[] members) {
        for (ProgramElementDoc member : members) {
            if (!member.isPublic()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Search for the given method in the given class.
     *
     * @param  cd        Class to search into.
     * @param  method    Method to be searched.
     * @return MethodDoc Method found, null otherwise.
     */
    public MethodDoc findMethod(ClassDoc cd, MethodDoc method) {
        MethodDoc[] methods = cd.methods();
        for (MethodDoc m : methods) {
            if (executableMembersEqual(method, m)) {
                return m;

            }
        }
        return null;
    }

    /**
     * @param member1 the first method to compare.
     * @param member2 the second method to compare.
     * @return true if member1 overrides/hides or is overriden/hidden by member2.
     */
    public boolean executableMembersEqual(ExecutableMemberDoc member1,
            ExecutableMemberDoc member2) {
        if (! (member1 instanceof MethodDoc && member2 instanceof MethodDoc))
            return false;

        MethodDoc method1 = (MethodDoc) member1;
        MethodDoc method2 = (MethodDoc) member2;
        if (method1.isStatic() && method2.isStatic()) {
            Parameter[] targetParams = method1.parameters();
            Parameter[] currentParams;
            if (method1.name().equals(method2.name()) &&
                   (currentParams = method2.parameters()).length ==
                targetParams.length) {
                int j;
                for (j = 0; j < targetParams.length; j++) {
                    if (! (targetParams[j].typeName().equals(
                              currentParams[j].typeName()) ||
                                   currentParams[j].type() instanceof TypeVariable ||
                                   targetParams[j].type() instanceof TypeVariable)) {
                        break;
                    }
                }
                if (j == targetParams.length) {
                    return true;
                }
            }
            return false;
        } else {
                return method1.overrides(method2) ||
                method2.overrides(method1) ||
                                member1 == member2;
        }
    }

    /**
     * According to
     * <cite>The Java&trade; Language Specification</cite>,
     * all the outer classes and static inner classes are core classes.
     */
    public boolean isCoreClass(ClassDoc cd) {
        return cd.containingClass() == null || cd.isStatic();
    }

    public boolean matches(ProgramElementDoc doc1,
            ProgramElementDoc doc2) {
        if (doc1 instanceof ExecutableMemberDoc &&
            doc2 instanceof ExecutableMemberDoc) {
            ExecutableMemberDoc ed1 = (ExecutableMemberDoc)doc1;
            ExecutableMemberDoc ed2 = (ExecutableMemberDoc)doc2;
            return executableMembersEqual(ed1, ed2);
        } else {
            return doc1.name().equals(doc2.name());
        }
    }

    /**
     * Copy the given directory contents from the source package directory
     * to the generated documentation directory. For example for a package
     * java.lang this method find out the source location of the package using
     * {@link SourcePath} and if given directory is found in the source
     * directory structure, copy the entire directory, to the generated
     * documentation hierarchy.
     *
     * @param configuration The configuration of the current doclet.
     * @param path The relative path to the directory to be copied.
     * @param dir The original directory name to copy from.
     * @param overwrite Overwrite files if true.
     */
    public void copyDocFiles(Configuration configuration, PackageDoc pd) {
        Location locn = configuration.getLocationForPackage(pd);
        copyDocFiles(configuration, locn, DocPath.forPackage(pd).resolve(DocPaths.DOC_FILES));
    }

    public void copyDocFiles(Configuration configuration, Location locn, DocPath dir) {
        try {
            boolean first = true;
            for (DocFile f : DocFile.list(configuration, locn, dir)) {
                if (!f.isDirectory()) {
                    continue;
                }
                DocFile srcdir = f;
                DocFile destdir = DocFile.createFileForOutput(configuration, dir);
                if (srcdir.isSameFile(destdir)) {
                    continue;
                }

                for (DocFile srcfile: srcdir.list()) {
                    DocFile destfile = destdir.resolve(srcfile.getName());
                    if (srcfile.isFile()) {
                        if (destfile.exists() && !first) {
                            configuration.message.warning((SourcePosition) null,
                                    "doclet.Copy_Overwrite_warning",
                                    srcfile.getPath(), destdir.getPath());
                        } else {
                            configuration.message.notice(
                                    "doclet.Copying_File_0_To_Dir_1",
                                    srcfile.getPath(), destdir.getPath());
                            destfile.copyFile(srcfile);
                        }
                    } else if (srcfile.isDirectory()) {
                        if (configuration.copydocfilesubdirs
                                && !configuration.shouldExcludeDocFileDir(srcfile.getName())) {
                            copyDocFiles(configuration, locn, dir.resolve(srcfile.getName()));
                        }
                    }
                }

                first = false;
            }
        } catch (SecurityException | IOException exc) {
            throw new DocletAbortException(exc);
        }
    }
    /**
     * Returns a TypeComparator object suitable for sorting Types.
     * @return a TypeComparator objectt
     */
    public Comparator<Type> makeTypeComparator() {
        return new TypeComparator();
    }
    /**
     * We want the list of types in alphabetical order.  However, types are not
     * comparable.  We need a comparator for now.
     */
    private static class TypeComparator implements Comparator<Type> {
        public int compare(Type type1, Type type2) {
            return compareStrings(type1.qualifiedTypeName(), type2.qualifiedTypeName());
        }
    }

    /**
     * For the class return all implemented interfaces including the
     * superinterfaces of the implementing interfaces, also iterate over for
     * all the superclasses. For interface return all the extended interfaces
     * as well as superinterfaces for those extended interfaces.
     *
     * @param  type       type whose implemented or
     *                    super interfaces are sought.
     * @param  configuration the current configuration of the doclet.
     * @param  sort if true, return list of interfaces sorted alphabetically.
     * @return List of all the required interfaces.
     */
    public List<Type> getAllInterfaces(Type type,
            Configuration configuration, boolean sort) {
        Map<ClassDoc,Type> results = sort ?
                new TreeMap<ClassDoc,Type>() :
                new LinkedHashMap<ClassDoc,Type>();
        Type[] interfaceTypes = null;
        Type superType = null;
        if (type instanceof ParameterizedType) {
            interfaceTypes = ((ParameterizedType) type).interfaceTypes();
            superType = ((ParameterizedType) type).superclassType();
        } else if (type instanceof ClassDoc) {
            interfaceTypes = ((ClassDoc) type).interfaceTypes();
            superType = ((ClassDoc) type).superclassType();
        } else {
            interfaceTypes = type.asClassDoc().interfaceTypes();
            superType = type.asClassDoc().superclassType();
        }

        for (Type interfaceType : interfaceTypes) {
            ClassDoc interfaceClassDoc = interfaceType.asClassDoc();
            if (!(interfaceClassDoc.isPublic() ||
                  (configuration == null ||
                   isLinkable(interfaceClassDoc, configuration)))) {
                continue;
            }
            results.put(interfaceClassDoc, interfaceType);
            for (Type t : getAllInterfaces(interfaceType, configuration, sort)) {
                results.put(t.asClassDoc(), t);
            }
        }
        if (superType == null)
            return new ArrayList<>(results.values());
        //Try walking the tree.
        addAllInterfaceTypes(results,
            superType,
            interfaceTypesOf(superType),
            false, configuration);
        List<Type> resultsList = new ArrayList<>(results.values());
        if (sort) {
                Collections.sort(resultsList, new TypeComparator());
        }
        return resultsList;
    }

    private Type[] interfaceTypesOf(Type type) {
        if (type instanceof AnnotatedType)
            type = ((AnnotatedType)type).underlyingType();
        return type instanceof ClassDoc ?
                ((ClassDoc)type).interfaceTypes() :
                ((ParameterizedType)type).interfaceTypes();
    }

    public List<Type> getAllInterfaces(Type type, Configuration configuration) {
        return getAllInterfaces(type, configuration, true);
    }

    private void findAllInterfaceTypes(Map<ClassDoc,Type> results, ClassDoc c, boolean raw,
            Configuration configuration) {
        Type superType = c.superclassType();
        if (superType == null)
            return;
        addAllInterfaceTypes(results, superType,
                interfaceTypesOf(superType),
                raw, configuration);
    }

    private void findAllInterfaceTypes(Map<ClassDoc,Type> results, ParameterizedType p,
            Configuration configuration) {
        Type superType = p.superclassType();
        if (superType == null)
            return;
        addAllInterfaceTypes(results, superType,
                interfaceTypesOf(superType),
                false, configuration);
    }

    private void addAllInterfaceTypes(Map<ClassDoc,Type> results, Type type,
            Type[] interfaceTypes, boolean raw,
            Configuration configuration) {
        for (Type interfaceType : interfaceTypes) {
            ClassDoc interfaceClassDoc = interfaceType.asClassDoc();
            if (!(interfaceClassDoc.isPublic() ||
                  (configuration != null &&
                   isLinkable(interfaceClassDoc, configuration)))) {
                continue;
            }
            if (raw)
                interfaceType = interfaceType.asClassDoc();
            results.put(interfaceClassDoc, interfaceType);
            List<Type> superInterfaces = getAllInterfaces(interfaceType, configuration);
            for (Type superInterface : superInterfaces) {
                results.put(superInterface.asClassDoc(), superInterface);
            }
        }
        if (type instanceof AnnotatedType)
            type = ((AnnotatedType)type).underlyingType();

        if (type instanceof ParameterizedType)
            findAllInterfaceTypes(results, (ParameterizedType) type, configuration);
        else if (((ClassDoc) type).typeParameters().length == 0)
            findAllInterfaceTypes(results, (ClassDoc) type, raw, configuration);
        else
            findAllInterfaceTypes(results, (ClassDoc) type, true, configuration);
    }

    /**
     * Enclose in quotes, used for paths and filenames that contains spaces
     */
    public String quote(String filepath) {
        return ("\"" + filepath + "\"");
    }

    /**
     * Given a package, return its name.
     * @param packageDoc the package to check.
     * @return the name of the given package.
     */
    public String getPackageName(PackageDoc packageDoc) {
        return packageDoc == null || packageDoc.name().length() == 0 ?
            DocletConstants.DEFAULT_PACKAGE_NAME : packageDoc.name();
    }

    /**
     * Given a package, return its file name without the extension.
     * @param packageDoc the package to check.
     * @return the file name of the given package.
     */
    public String getPackageFileHeadName(PackageDoc packageDoc) {
        return packageDoc == null || packageDoc.name().length() == 0 ?
            DocletConstants.DEFAULT_PACKAGE_FILE_NAME : packageDoc.name();
    }

    /**
     * Given a string, replace all occurrences of 'newStr' with 'oldStr'.
     * @param originalStr the string to modify.
     * @param oldStr the string to replace.
     * @param newStr the string to insert in place of the old string.
     */
    public String replaceText(String originalStr, String oldStr,
            String newStr) {
        if (oldStr == null || newStr == null || oldStr.equals(newStr)) {
            return originalStr;
        }
        return originalStr.replace(oldStr, newStr);
    }

    /**
     * Given an annotation, return true if it should be documented and false
     * otherwise.
     *
     * @param annotationDoc the annotation to check.
     *
     * @return true return true if it should be documented and false otherwise.
     */
    public boolean isDocumentedAnnotation(AnnotationTypeDoc annotationDoc) {
        for (AnnotationDesc anno : annotationDoc.annotations()) {
            if (anno.annotationType().qualifiedName().equals(
                    Documented.class.getName())) {
                return true;
            }
        }
        return false;
    }

    private boolean isDeclarationTarget(AnnotationDesc targetAnno) {
        // The error recovery steps here are analogous to TypeAnnotations
        ElementValuePair[] elems = targetAnno.elementValues();
        if (elems == null
            || elems.length != 1
            || !"value".equals(elems[0].element().name())
            || !(elems[0].value().value() instanceof AnnotationValue[]))
            return true;    // error recovery

        for (AnnotationValue aValue : (AnnotationValue[])elems[0].value().value()) {
            Object value = aValue.value();
            if (!(value instanceof FieldDoc))
                return true; // error recovery

            FieldDoc eValue = (FieldDoc) value;
            if (isJava5DeclarationElementType(eValue)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Returns true if the {@code annotationDoc} is to be treated
     * as a declaration annotation, when targeting the
     * {@code elemType} element type.
     *
     * @param annotationDoc the annotationDoc to check
     * @param elemType  the targeted elemType
     * @return true if annotationDoc is a declaration annotation
     */
    public boolean isDeclarationAnnotation(AnnotationTypeDoc annotationDoc,
            boolean isJava5DeclarationLocation) {
        if (!isJava5DeclarationLocation)
            return false;
        AnnotationDesc[] annotationDescList = annotationDoc.annotations();
        // Annotations with no target are treated as declaration as well
        if (annotationDescList.length==0)
            return true;
        for (AnnotationDesc anno : annotationDescList) {
            if (anno.annotationType().qualifiedName().equals(
                    Target.class.getName())) {
                if (isDeclarationTarget(anno))
                    return true;
            }
        }
        return false;
    }

    /**
     * Return true if this class is linkable and false if we can't link to the
     * desired class.
     * <br>
     * <b>NOTE:</b>  You can only link to external classes if they are public or
     * protected.
     *
     * @param classDoc the class to check.
     * @param configuration the current configuration of the doclet.
     *
     * @return true if this class is linkable and false if we can't link to the
     * desired class.
     */
    public boolean isLinkable(ClassDoc classDoc,
            Configuration configuration) {
        return
            ((classDoc.isIncluded() && configuration.isGeneratedDoc(classDoc))) ||
            (configuration.extern.isExternal(classDoc) &&
                (classDoc.isPublic() || classDoc.isProtected()));
    }

    /**
     * Given a class, return the closest visible super class.
     *
     * @param classDoc the class we are searching the parent for.
     * @param configuration the current configuration of the doclet.
     * @return the closest visible super class.  Return null if it cannot
     *         be found (i.e. classDoc is java.lang.Object).
     */
    public Type getFirstVisibleSuperClass(ClassDoc classDoc,
            Configuration configuration) {
        if (classDoc == null) {
            return null;
        }
        Type sup = classDoc.superclassType();
        ClassDoc supClassDoc = classDoc.superclass();
        while (sup != null &&
                  (! (supClassDoc.isPublic() ||
                              isLinkable(supClassDoc, configuration))) ) {
            if (supClassDoc.superclass().qualifiedName().equals(supClassDoc.qualifiedName()))
                break;
            sup = supClassDoc.superclassType();
            supClassDoc = supClassDoc.superclass();
        }
        if (classDoc.equals(supClassDoc)) {
            return null;
        }
        return sup;
    }

    /**
     * Given a class, return the closest visible super class.
     *
     * @param classDoc the class we are searching the parent for.
     * @param configuration the current configuration of the doclet.
     * @return the closest visible super class.  Return null if it cannot
     *         be found (i.e. classDoc is java.lang.Object).
     */
    public ClassDoc getFirstVisibleSuperClassCD(ClassDoc classDoc,
            Configuration configuration) {
        if (classDoc == null) {
            return null;
        }
        ClassDoc supClassDoc = classDoc.superclass();
        while (supClassDoc != null &&
                  (! (supClassDoc.isPublic() ||
                              isLinkable(supClassDoc, configuration))) ) {
            supClassDoc = supClassDoc.superclass();
        }
        if (classDoc.equals(supClassDoc)) {
            return null;
        }
        return supClassDoc;
    }

    /**
     * Given a ClassDoc, return the name of its type (Class, Interface, etc.).
     *
     * @param cd the ClassDoc to check.
     * @param lowerCaseOnly true if you want the name returned in lower case.
     *                      If false, the first letter of the name is capitalized.
     * @return
     */
    public String getTypeName(Configuration config,
        ClassDoc cd, boolean lowerCaseOnly) {
        String typeName = "";
        if (cd.isOrdinaryClass()) {
            typeName = "doclet.Class";
        } else if (cd.isInterface()) {
            typeName = "doclet.Interface";
        } else if (cd.isException()) {
            typeName = "doclet.Exception";
        } else if (cd.isError()) {
            typeName = "doclet.Error";
        } else if (cd.isAnnotationType()) {
            typeName = "doclet.AnnotationType";
        } else if (cd.isEnum()) {
            typeName = "doclet.Enum";
        }
        return config.getText(
            lowerCaseOnly ? StringUtils.toLowerCase(typeName) : typeName);
    }

    /**
     * Replace all tabs in a string with the appropriate number of spaces.
     * The string may be a multi-line string.
     * @param configuration the doclet configuration defining the setting for the
     *                      tab length.
     * @param text the text for which the tabs should be expanded
     * @return the text with all tabs expanded
     */
    public String replaceTabs(Configuration configuration, String text) {
        if (!text.contains("\t"))
            return text;

        final int tabLength = configuration.sourcetab;
        final String whitespace = configuration.tabSpaces;
        final int textLength = text.length();
        StringBuilder result = new StringBuilder(textLength);
        int pos = 0;
        int lineLength = 0;
        for (int i = 0; i < textLength; i++) {
            char ch = text.charAt(i);
            switch (ch) {
                case '\n': case '\r':
                    lineLength = 0;
                    break;
                case '\t':
                    result.append(text, pos, i);
                    int spaceCount = tabLength - lineLength % tabLength;
                    result.append(whitespace, 0, spaceCount);
                    lineLength += spaceCount;
                    pos = i + 1;
                    break;
                default:
                    lineLength++;
            }
        }
        result.append(text, pos, textLength);
        return result.toString();
    }

    public String normalizeNewlines(String text) {
        StringBuilder sb = new StringBuilder();
        final int textLength = text.length();
        final String NL = DocletConstants.NL;
        int pos = 0;
        for (int i = 0; i < textLength; i++) {
            char ch = text.charAt(i);
            switch (ch) {
                case '\n':
                    sb.append(text, pos, i);
                    sb.append(NL);
                    pos = i + 1;
                    break;
                case '\r':
                    sb.append(text, pos, i);
                    sb.append(NL);
                    if (i + 1 < textLength && text.charAt(i + 1) == '\n')
                        i++;
                    pos = i + 1;
                    break;
            }
        }
        sb.append(text, pos, textLength);
        return sb.toString();
    }

    /**
     * The documentation for values() and valueOf() in Enums are set by the
     * doclet.
     */
    public void setEnumDocumentation(Configuration configuration,
            ClassDoc classDoc) {
        for (MethodDoc currentMethod : classDoc.methods()) {
            if (currentMethod.name().equals("values") &&
                currentMethod.parameters().length == 0) {
                StringBuilder sb = new StringBuilder();
                sb.append(configuration.getText("doclet.enum_values_doc.main", classDoc.name()));
                sb.append("\n@return ");
                sb.append(configuration.getText("doclet.enum_values_doc.return"));
                currentMethod.setRawCommentText(sb.toString());
            } else if (currentMethod.name().equals("valueOf") &&
                     currentMethod.parameters().length == 1) {
                Type paramType = currentMethod.parameters()[0].type();
                if (paramType != null &&
                    paramType.qualifiedTypeName().equals(String.class.getName())) {
                    StringBuilder sb = new StringBuilder();
                    sb.append(configuration.getText("doclet.enum_valueof_doc.main", classDoc.name()));
                    sb.append("\n@param name ");
                    sb.append(configuration.getText("doclet.enum_valueof_doc.param_name"));
                    sb.append("\n@return ");
                    sb.append(configuration.getText("doclet.enum_valueof_doc.return"));
                    sb.append("\n@throws IllegalArgumentException ");
                    sb.append(configuration.getText("doclet.enum_valueof_doc.throws_ila"));
                    sb.append("\n@throws NullPointerException ");
                    sb.append(configuration.getText("doclet.enum_valueof_doc.throws_npe"));
                    currentMethod.setRawCommentText(sb.toString());
                }
            }
        }
    }

    /**
     *  Return true if the given Doc is deprecated.
     *
     * @param doc the Doc to check.
     * @return true if the given Doc is deprecated.
     */
    public boolean isDeprecated(Doc doc) {
        if (doc.tags("deprecated").length > 0) {
            return true;
        }
        AnnotationDesc[] annotationDescList;
        if (doc instanceof PackageDoc)
            annotationDescList = ((PackageDoc)doc).annotations();
        else
            annotationDescList = ((ProgramElementDoc)doc).annotations();
        for (AnnotationDesc anno : annotationDescList) {
            if (anno.annotationType().qualifiedName().equals(
                    Deprecated.class.getName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * A convenience method to get property name from the name of the
     * getter or setter method.
     * @param name name of the getter or setter method.
     * @return the name of the property of the given setter of getter.
     */
    public String propertyNameFromMethodName(Configuration configuration, String name) {
        String propertyName = null;
        if (name.startsWith("get") || name.startsWith("set")) {
            propertyName = name.substring(3);
        } else if (name.startsWith("is")) {
            propertyName = name.substring(2);
        }
        if ((propertyName == null) || propertyName.isEmpty()){
            return "";
        }
        return propertyName.substring(0, 1).toLowerCase(configuration.getLocale())
                + propertyName.substring(1);
    }

    /**
     * In case of JavaFX mode on, filters out classes that are private,
     * package private or having the @treatAsPrivate annotation. Those are not
     * documented in JavaFX mode.
     *
     * @param classes array of classes to be filtered.
     * @param javafx set to true if in JavaFX mode.
     * @return list of filtered classes.
     */
    public ClassDoc[] filterOutPrivateClasses(final ClassDoc[] classes,
                                                     boolean javafx) {
        if (!javafx) {
            return classes;
        }
        final List<ClassDoc> filteredOutClasses = new ArrayList<>(classes.length);
        for (ClassDoc classDoc : classes) {
            if (classDoc.isPrivate() || classDoc.isPackagePrivate()) {
                continue;
            }
            Tag[] aspTags = classDoc.tags("treatAsPrivate");
            if (aspTags != null && aspTags.length > 0) {
                continue;
            }
            filteredOutClasses.add(classDoc);
        }

        return filteredOutClasses.toArray(new ClassDoc[0]);
    }

    /**
     * Test whether the given FieldDoc is one of the declaration annotation ElementTypes
     * defined in Java 5.
     * Instead of testing for one of the new enum constants added in Java 8, test for
     * the old constants. This prevents bootstrapping problems.
     *
     * @param elt The FieldDoc to test
     * @return true, iff the given ElementType is one of the constants defined in Java 5
     * @since 1.8
     */
    public boolean isJava5DeclarationElementType(FieldDoc elt) {
        return elt.name().contentEquals(ElementType.ANNOTATION_TYPE.name()) ||
                elt.name().contentEquals(ElementType.CONSTRUCTOR.name()) ||
                elt.name().contentEquals(ElementType.FIELD.name()) ||
                elt.name().contentEquals(ElementType.LOCAL_VARIABLE.name()) ||
                elt.name().contentEquals(ElementType.METHOD.name()) ||
                elt.name().contentEquals(ElementType.PACKAGE.name()) ||
                elt.name().contentEquals(ElementType.PARAMETER.name()) ||
                elt.name().contentEquals(ElementType.TYPE.name());
    }

    /**
     * A general purpose case insensitive String comparator, which compares two Strings using a Collator
     * strength of "TERTIARY".
     *
     * @param s1 first String to compare.
     * @param s2 second String to compare.
     * @return a negative integer, zero, or a positive integer as the first
     *         argument is less than, equal to, or greater than the second.
     */
    public static int compareStrings(String s1, String s2) {
        return compareStrings(true, s1, s2);
    }
    /**
     * A general purpose case sensitive String comparator, which compares two Strings using a Collator
     * strength of "SECONDARY".
     *
     * @param s1 first String to compare.
     * @param s2 second String to compare.
     * @return a negative integer, zero, or a positive integer as the first
     *         argument is less than, equal to, or greater than the second.
     */
    public static int compareCaseCompare(String s1, String s2) {
        return compareStrings(false, s1, s2);
    }
    private static int compareStrings(boolean caseSensitive, String s1, String s2) {
        Collator collator = Collator.getInstance();
        collator.setStrength(caseSensitive ? Collator.TERTIARY : Collator.SECONDARY);
        return collator.compare(s1, s2);
    }
    /**
     * A simple comparator which compares simple names, then the fully qualified names
     * and finally the kinds, ClassUse comparator works well for this purpose.
     *
     * @return a simple general purpose doc comparator
     */
    public Comparator<Doc> makeGeneralPurposeComparator() {
        return makeComparatorForClassUse();
    }
    /**
     * A comparator for index file presentations, and are sorted as follows:
     *  1. sort on simple names of entities
     *  2. if equal, then compare the DocKind ex: Package, Interface etc.
     *  3a. if equal and if the type is of ExecutableMemberDoc(Constructor, Methods),
     *      a case insensitive comparison of parameter the type signatures
     *  3b. if equal, case sensitive comparison of the type signatures
     *  4. finally, if equal, compare the FQNs of the entities
     * @return a comparator for index file use
     */
    public Comparator<Doc> makeComparatorForIndexUse() {
        return new Utils.DocComparator<Doc>() {
            /**
             * Compare two given Doc entities, first sort on names, then on the kinds,
             * then on the parameters only if the type is an instance of ExecutableMemberDocs,
             * the parameters are compared and finally the fully qualified names.
             *
             * @param d1 - a Doc element.
             * @param d2 - a Doc element.
             * @return a negative integer, zero, or a positive integer as the first
             *         argument is less than, equal to, or greater than the second.
             */
            public int compare(Doc d1, Doc d2) {
                int result = compareNames(d1, d2);
                if (result != 0) {
                    return result;
                }
                result = compareDocKinds(d1, d2);
                if (result != 0) {
                    return result;
                }
                if (hasParameters(d1)) {
                    Parameter[] param1 = ((ExecutableMemberDoc) d1).parameters();
                    Parameter[] param2 = ((ExecutableMemberDoc) d2).parameters();
                    result = compareParameters(false, param1, param2);
                    if (result != 0) {
                        return result;
                    }
                    result = compareParameters(true, param1, param2);
                    if (result != 0) {
                        return result;
                    }
                }
                return compareFullyQualifiedNames(d1, d2);
            }
        };
    }
    /**
     * Comparator for ClassUse presentations, and sorted as follows,
     * 1. compares simple names of entities
     * 2. if equal, the fully qualified names of the entities
     * 3. if equal and if applicable, the string representation of parameter types
     * 3a. first by using case insensitive comparison
     * 3b. second by using a case sensitive comparison
     * 4. finally the Doc kinds ie. package, class, interface etc.
     * @return a comparator to sort classes and members for class use
     */
    public Comparator<Doc> makeComparatorForClassUse() {
        return new Utils.DocComparator<Doc>() {
            /**
             * Compares two given Doc entities, first sort on name, and if
             * applicable on the fully qualified name, and if applicable
             * on the parameter types, and finally the DocKind.
             * @param d1 - a Doc element.
             * @param d2 - a Doc element.
             * @return a negative integer, zero, or a positive integer as the first
             *         argument is less than, equal to, or greater than the second.
             */
            public int compare(Doc d1, Doc d2) {
                int result = compareNames(d1, d2);
                if (result != 0) {
                    return result;
                }
                result = compareFullyQualifiedNames(d1, d2);
                if (result != 0) {
                    return result;
                }
                if (hasParameters(d1) && hasParameters(d2)) {
                    Parameter[] param1 = ((ExecutableMemberDoc) d1).parameters();
                    Parameter[] param2 = ((ExecutableMemberDoc) d2).parameters();
                    result = compareParameters(false, param1, param2);
                    if (result != 0) {
                        return result;
                    }
                    return compareParameters(true, param1, param2);
                }
                return compareDocKinds(d1, d2);
            }
        };
    }
    /**
     * A general purpose comparator to sort Doc entities, basically provides the building blocks
     * for creating specific comparators for an use-case.
     * @param <T> a Doc entity
     */
    static abstract class DocComparator<T extends Doc> implements Comparator<Doc> {
        static enum DocKind {
           PACKAGE,
           CLASS,
           ENUM,
           INTERFACE,
           ANNOTATION,
           FIELD,
           CONSTRUCTOR,
           METHOD
        };
        boolean hasParameters(Doc d) {
            return d instanceof ExecutableMemberDoc;
        }
        DocKind getDocKind(Doc d) {
            if (d.isAnnotationType() || d.isAnnotationTypeElement()) {
                return DocKind.ANNOTATION;
            } else if (d.isEnum() || d.isEnumConstant()) {
                return DocKind.ENUM;
            } else if (d.isField()) {
                return DocKind.FIELD;
            } else if (d.isInterface()) {
                return DocKind.INTERFACE;
            } else if (d.isClass()) {
                return DocKind.CLASS;
            } else if (d.isConstructor()) {
                return DocKind.CONSTRUCTOR;
            } else if (d.isMethod()) {
                return DocKind.METHOD;
            } else {
                return DocKind.PACKAGE;
            }
        }
        /**
         * Compares two Doc entities' kinds, and these are ordered as defined in
         * the DocKind enumeration.
         * @param d1 the first Doc object
         * @param d2 the second Doc object
         * @return a negative integer, zero, or a positive integer as the first
         *         argument is less than, equal to, or greater than the second.
         */
        protected int compareDocKinds(Doc d1, Doc d2) {
            return getDocKind(d1).compareTo(getDocKind(d2));
        }
        /**
         * Compares arrays of parameters as a string representation of their types.
         *
         * @param ignoreCase specifies case sensitive or insensitive comparison.
         * @param params1 the first parameter array.
         * @param params2 the first parameter array.
         * @return a negative integer, zero, or a positive integer as the first argument is less
         * than, equal to, or greater than the second.
         */
        protected int compareParameters(boolean caseSensitive,
                                        Parameter[] params1,
                                        Parameter[] params2) {
            String s1 = getParametersAsString(params1);
            String s2 = getParametersAsString(params2);
            return compareStrings(caseSensitive, s1, s2);
        }
        /*
         * This method returns a string representation solely for comparison purposes.
         */
        protected String getParametersAsString(Parameter[] params) {
            StringBuilder sb = new StringBuilder();
            for (Parameter param : params) {
                Type t = param.type();
                // add parameter type to arrays, as TypeMirror does.
                String tname = (t.asParameterizedType() != null && t.getElementType() != null)
                        ? t.getElementType() + t.dimension()
                        : t.toString();
                // prefix P for primitive and R for reference types, thus items will
                // be ordered naturally.
                sb.append(t.isPrimitive() ? "P" : "R").append("-").append(tname).append("-");
            }
            return sb.toString();
        }

        /**
         * Compares two Doc entities typically the simple name of a method,
         * field, constructor etc.
         * @param d1 the first Doc.
         * @param d2 the second Doc.
         * @return a negative integer, zero, or a positive integer as the first
         *         argument is less than, equal to, or greater than the second.
         */
        protected int compareNames(Doc d1, Doc d2) {
            return compareStrings(d1.name(), d2.name());
        }

        /**
         * Compares the fully qualified names of the entities
         * @param d1 the first entity
         * @param d2 the second entity
         * @return a negative integer, zero, or a positive integer as the first
         *         argument is less than, equal to, or greater than the second.
         */
        protected int compareFullyQualifiedNames(Doc d1, Doc d2) {
            String name1 = (d1 instanceof ProgramElementDoc)
                    ? ((ProgramElementDoc)d1).qualifiedName()
                    : d1.name();
            String name2 = (d2 instanceof ProgramElementDoc)
                    ? ((ProgramElementDoc)d2).qualifiedName()
                    : d2.name();
            return compareStrings(name1, name2);
        }
    }
}
