/*
 * Copyright (c) 1999, 2012, Oracle and/or its affiliates. All rights reserved.
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
import java.util.*;

import com.sun.javadoc.*;
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
     * A mapping between characters and their
     * corresponding HTML escape character.
     */
    public static final String[][] HTML_ESCAPE_CHARS =
    {{"&", "&amp;"}, {"<", "&lt;"}, {">", "&gt;"}};

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
            superType instanceof ClassDoc ?
                ((ClassDoc) superType).interfaceTypes() :
                ((ParameterizedType) superType).interfaceTypes(),
            false, configuration);
        List<Type> resultsList = new ArrayList<Type>(results.values());
        if (sort) {
                Collections.sort(resultsList, new TypeComparator());
        }
        return resultsList;
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
                superType instanceof ClassDoc ?
                ((ClassDoc) superType).interfaceTypes() :
                ((ParameterizedType) superType).interfaceTypes(),
                raw, configuration);
    }

    private static void findAllInterfaceTypes(Map<ClassDoc,Type> results, ParameterizedType p,
            Configuration configuration) {
        Type superType = p.superclassType();
        if (superType == null)
            return;
        addAllInterfaceTypes(results, superType,
                superType instanceof ClassDoc ?
                ((ClassDoc) superType).interfaceTypes() :
                ((ParameterizedType) superType).interfaceTypes(),
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
     * Given a string, escape all special html characters and
     * return the result.
     *
     * @param s The string to check.
     * @return the original string with all of the HTML characters
     * escaped.
     *
     * @see #HTML_ESCAPE_CHARS
     */
    public static String escapeHtmlChars(String s) {
        String result = s;
        for (int i = 0; i < HTML_ESCAPE_CHARS.length; i++) {
            result = Util.replaceText(result,
                    HTML_ESCAPE_CHARS[i][0], HTML_ESCAPE_CHARS[i][1]);
        }
        return result;
    }

    /**
     * Given a string, strips all html characters and
     * return the result.
     *
     * @param rawString The string to check.
     * @return the original string with all of the HTML characters
     * stripped.
     *
     */
    public static String stripHtml(String rawString) {
        // remove HTML tags
        rawString = rawString.replaceAll("\\<.*?>", " ");
        // consolidate multiple spaces between a word to a single space
        rawString = rawString.replaceAll("\\b\\s{2,}\\b", " ");
        // remove extra whitespaces
        return rawString.trim();
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
     * Given a string, replace all tabs with the appropriate
     * number of spaces.
     * @param tabLength the length of each tab.
     * @param s the String to scan.
     */
    public static void replaceTabs(int tabLength, StringBuilder s) {
        if (whitespace == null || whitespace.length() < tabLength)
            whitespace = String.format("%" + tabLength + "s", " ");
        int index = 0;
        while ((index = s.indexOf("\t", index)) != -1) {
            int spaceCount = tabLength - index % tabLength;
            s.replace(index, index+1, whitespace.substring(0, spaceCount));
            index += spaceCount;
        }
    }
    private static String whitespace;

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
                currentMethod.setRawCommentText(
                    configuration.getText("doclet.enum_values_doc", classDoc.name()));
            } else if (currentMethod.name().equals("valueOf") &&
                currentMethod.parameters().length == 1) {
                Type paramType = currentMethod.parameters()[0].type();
                if (paramType != null &&
                    paramType.qualifiedTypeName().equals(String.class.getName())) {
                    currentMethod.setRawCommentText(
                        configuration.getText("doclet.enum_valueof_doc"));
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
}
