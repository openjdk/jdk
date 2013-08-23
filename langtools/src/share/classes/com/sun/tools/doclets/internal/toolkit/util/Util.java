/*
 * Copyright (c) 1999, 2013, Oracle and/or its affiliates. All rights reserved.
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
import java.lang.annotation.ElementType;
import java.util.*;

import com.sun.javadoc.*;
import com.sun.javadoc.AnnotationDesc.ElementValuePair;
import com.sun.tools.doclets.internal.toolkit.*;
import javax.tools.StandardLocation;

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
public class Util {

    /**
     * Return array of class members whose documentation is to be generated.
     * If the member is deprecated do not include such a member in the
     * returned array.
     *
     * @param  members             Array of members to choose from.
     * @return ProgramElementDoc[] Array of eligible members for whom
     *                             documentation is getting generated.
     */
    public static ProgramElementDoc[] excludeDeprecatedMembers(
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
    public static List<ProgramElementDoc> excludeDeprecatedMembersAsList(
        ProgramElementDoc[] members) {
        List<ProgramElementDoc> list = new ArrayList<ProgramElementDoc>();
        for (int i = 0; i < members.length; i++) {
            if (members[i].tags("deprecated").length == 0) {
                list.add(members[i]);
            }
        }
        Collections.sort(list);
        return list;
    }

    /**
     * Return the list of ProgramElementDoc objects as Array.
     */
    public static ProgramElementDoc[] toProgramElementDocArray(List<ProgramElementDoc> list) {
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
    public static boolean nonPublicMemberFound(ProgramElementDoc[] members) {
        for (int i = 0; i < members.length; i++) {
            if (!members[i].isPublic()) {
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
    public static MethodDoc findMethod(ClassDoc cd, MethodDoc method) {
        MethodDoc[] methods = cd.methods();
        for (int i = 0; i < methods.length; i++) {
            if (executableMembersEqual(method, methods[i])) {
                return methods[i];

            }
        }
        return null;
    }

    /**
     * @param member1 the first method to compare.
     * @param member2 the second method to compare.
     * @return true if member1 overrides/hides or is overriden/hidden by member2.
     */
    public static boolean executableMembersEqual(ExecutableMemberDoc member1,
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
    public static boolean isCoreClass(ClassDoc cd) {
        return cd.containingClass() == null || cd.isStatic();
    }

    public static boolean matches(ProgramElementDoc doc1,
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
    public static void copyDocFiles(Configuration configuration, PackageDoc pd) {
        copyDocFiles(configuration, DocPath.forPackage(pd).resolve(DocPaths.DOC_FILES));
    }

    public static void copyDocFiles(Configuration configuration, DocPath dir) {
        try {
            boolean first = true;
            for (DocFile f : DocFile.list(configuration, StandardLocation.SOURCE_PATH, dir)) {
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
                            copyDocFiles(configuration, dir.resolve(srcfile.getName()));
                        }
                    }
                }

                first = false;
            }
        } catch (SecurityException exc) {
            throw new DocletAbortException();
        } catch (IOException exc) {
            throw new DocletAbortException();
        }
    }

    /**
     * We want the list of types in alphabetical order.  However, types are not
     * comparable.  We need a comparator for now.
     */
    private static class TypeComparator implements Comparator<Type> {
        public int compare(Type type1, Type type2) {
            return type1.qualifiedTypeName().toLowerCase().compareTo(
                type2.qualifiedTypeName().toLowerCase());
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
    public static List<Type> getAllInterfaces(Type type,
            Configuration configuration, boolean sort) {
        Map<ClassDoc,Type> results = sort ? new TreeMap<ClassDoc,Type>() : new LinkedHashMap<ClassDoc,Type>();
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

        for (int i = 0; i < interfaceTypes.length; i++) {
            Type interfaceType = interfaceTypes[i];
            ClassDoc interfaceClassDoc = interfaceType.asClassDoc();
            if (! (interfaceClassDoc.isPublic() ||
                (configuration == null ||
                isLinkable(interfaceClassDoc, configuration)))) {
                continue;
            }
            results.put(interfaceClassDoc, interfaceType);
            List<Type> superInterfaces = getAllInterfaces(interfaceType, configuration, sort);
            for (Iterator<Type> iter = superInterfaces.iterator(); iter.hasNext(); ) {
                Type t = iter.next();
                results.put(t.asClassDoc(), t);
            }
        }
        if (superType == null)
            return new ArrayList<Type>(results.values());
        //Try walking the tree.
        addAllInterfaceTypes(results,
            superType,
            interfaceTypesOf(superType),
            false, configuration);
        List<Type> resultsList = new ArrayList<Type>(results.values());
        if (sort) {
                Collections.sort(resultsList, new TypeComparator());
        }
        return resultsList;
    }

    private static Type[] interfaceTypesOf(Type type) {
        if (type instanceof AnnotatedType)
            type = ((AnnotatedType)type).underlyingType();
        return type instanceof ClassDoc ?
                ((ClassDoc)type).interfaceTypes() :
                ((ParameterizedType)type).interfaceTypes();
    }

    public static List<Type> getAllInterfaces(Type type, Configuration configuration) {
        return getAllInterfaces(type, configuration, true);
    }

    private static void findAllInterfaceTypes(Map<ClassDoc,Type> results, ClassDoc c, boolean raw,
            Configuration configuration) {
        Type superType = c.superclassType();
        if (superType == null)
            return;
        addAllInterfaceTypes(results, superType,
                interfaceTypesOf(superType),
                raw, configuration);
    }

    private static void findAllInterfaceTypes(Map<ClassDoc,Type> results, ParameterizedType p,
            Configuration configuration) {
        Type superType = p.superclassType();
        if (superType == null)
            return;
        addAllInterfaceTypes(results, superType,
                interfaceTypesOf(superType),
                false, configuration);
    }

    private static void addAllInterfaceTypes(Map<ClassDoc,Type> results, Type type,
            Type[] interfaceTypes, boolean raw,
            Configuration configuration) {
        for (int i = 0; i < interfaceTypes.length; i++) {
            Type interfaceType = interfaceTypes[i];
            ClassDoc interfaceClassDoc = interfaceType.asClassDoc();
            if (! (interfaceClassDoc.isPublic() ||
                (configuration != null &&
                isLinkable(interfaceClassDoc, configuration)))) {
                continue;
            }
            if (raw)
                interfaceType = interfaceType.asClassDoc();
            results.put(interfaceClassDoc, interfaceType);
            List<Type> superInterfaces = getAllInterfaces(interfaceType, configuration);
            for (Iterator<Type> iter = superInterfaces.iterator(); iter.hasNext(); ) {
                Type superInterface = iter.next();
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
    public static String quote(String filepath) {
        return ("\"" + filepath + "\"");
    }

    /**
     * Given a package, return its name.
     * @param packageDoc the package to check.
     * @return the name of the given package.
     */
    public static String getPackageName(PackageDoc packageDoc) {
        return packageDoc == null || packageDoc.name().length() == 0 ?
            DocletConstants.DEFAULT_PACKAGE_NAME : packageDoc.name();
    }

    /**
     * Given a package, return its file name without the extension.
     * @param packageDoc the package to check.
     * @return the file name of the given package.
     */
    public static String getPackageFileHeadName(PackageDoc packageDoc) {
        return packageDoc == null || packageDoc.name().length() == 0 ?
            DocletConstants.DEFAULT_PACKAGE_FILE_NAME : packageDoc.name();
    }

    /**
     * Given a string, replace all occurrences of 'newStr' with 'oldStr'.
     * @param originalStr the string to modify.
     * @param oldStr the string to replace.
     * @param newStr the string to insert in place of the old string.
     */
    public static String replaceText(String originalStr, String oldStr,
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
    public static boolean isDocumentedAnnotation(AnnotationTypeDoc annotationDoc) {
        AnnotationDesc[] annotationDescList = annotationDoc.annotations();
        for (int i = 0; i < annotationDescList.length; i++) {
            if (annotationDescList[i].annotationType().qualifiedName().equals(
                   java.lang.annotation.Documented.class.getName())){
                return true;
            }
        }
        return false;
    }

    private static boolean isDeclarationTarget(AnnotationDesc targetAnno) {
        // The error recovery steps here are analogous to TypeAnnotations
        ElementValuePair[] elems = targetAnno.elementValues();
        if (elems == null
            || elems.length != 1
            || !"value".equals(elems[0].element().name())
            || !(elems[0].value().value() instanceof AnnotationValue[]))
            return true;    // error recovery

        AnnotationValue[] values = (AnnotationValue[])elems[0].value().value();
        for (int i = 0; i < values.length; i++) {
            Object value = values[i].value();
            if (!(value instanceof FieldDoc))
                return true; // error recovery

            FieldDoc eValue = (FieldDoc)value;
            if (Util.isJava5DeclarationElementType(eValue)) {
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
    public static boolean isDeclarationAnnotation(AnnotationTypeDoc annotationDoc,
            boolean isJava5DeclarationLocation) {
        if (!isJava5DeclarationLocation)
            return false;
        AnnotationDesc[] annotationDescList = annotationDoc.annotations();
        // Annotations with no target are treated as declaration as well
        if (annotationDescList.length==0)
            return true;
        for (int i = 0; i < annotationDescList.length; i++) {
            if (annotationDescList[i].annotationType().qualifiedName().equals(
                    java.lang.annotation.Target.class.getName())) {
                if (isDeclarationTarget(annotationDescList[i]))
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
    public static boolean isLinkable(ClassDoc classDoc,
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
    public static Type getFirstVisibleSuperClass(ClassDoc classDoc,
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
    public static ClassDoc getFirstVisibleSuperClassCD(ClassDoc classDoc,
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
    public static String getTypeName(Configuration config,
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
            lowerCaseOnly ? typeName.toLowerCase() : typeName);
    }

    /**
     * Replace all tabs in a string with the appropriate number of spaces.
     * The string may be a multi-line string.
     * @param configuration the doclet configuration defining the setting for the
     *                      tab length.
     * @param text the text for which the tabs should be expanded
     * @return the text with all tabs expanded
     */
    public static String replaceTabs(Configuration configuration, String text) {
        if (text.indexOf("\t") == -1)
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

    public static String normalizeNewlines(String text) {
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
    public static void setEnumDocumentation(Configuration configuration,
            ClassDoc classDoc) {
        MethodDoc[] methods = classDoc.methods();
        for (int j = 0; j < methods.length; j++) {
            MethodDoc currentMethod = methods[j];
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
    public static boolean isDeprecated(Doc doc) {
        if (doc.tags("deprecated").length > 0) {
            return true;
        }
        AnnotationDesc[] annotationDescList;
        if (doc instanceof PackageDoc)
            annotationDescList = ((PackageDoc)doc).annotations();
        else
            annotationDescList = ((ProgramElementDoc)doc).annotations();
        for (int i = 0; i < annotationDescList.length; i++) {
            if (annotationDescList[i].annotationType().qualifiedName().equals(
                   java.lang.Deprecated.class.getName())){
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
    public static String propertyNameFromMethodName(String name) {
        String propertyName = null;
        if (name.startsWith("get") || name.startsWith("set")) {
            propertyName = name.substring(3);
        } else if (name.startsWith("is")) {
            propertyName = name.substring(2);
        }
        if ((propertyName == null) || propertyName.isEmpty()){
            return "";
        }
        return propertyName.substring(0, 1).toLowerCase()
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
    public static ClassDoc[] filterOutPrivateClasses(final ClassDoc[] classes,
                                                     boolean javafx) {
        if (!javafx) {
            return classes;
        }
        final List<ClassDoc> filteredOutClasses =
                new ArrayList<ClassDoc>(classes.length);
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
    public static boolean isJava5DeclarationElementType(FieldDoc elt) {
        return elt.name().contentEquals(ElementType.ANNOTATION_TYPE.name()) ||
                elt.name().contentEquals(ElementType.CONSTRUCTOR.name()) ||
                elt.name().contentEquals(ElementType.FIELD.name()) ||
                elt.name().contentEquals(ElementType.LOCAL_VARIABLE.name()) ||
                elt.name().contentEquals(ElementType.METHOD.name()) ||
                elt.name().contentEquals(ElementType.PACKAGE.name()) ||
                elt.name().contentEquals(ElementType.PARAMETER.name()) ||
                elt.name().contentEquals(ElementType.TYPE.name());
    }
}
