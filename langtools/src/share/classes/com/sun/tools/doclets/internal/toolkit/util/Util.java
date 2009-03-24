/*
 * Copyright 1999-2009 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package com.sun.tools.doclets.internal.toolkit.util;

import java.io.*;
import java.util.*;

import com.sun.javadoc.*;
import com.sun.tools.doclets.internal.toolkit.*;

/**
 * Utilities Class for Doclets.
 *
 * This code is not part of an API.
 * It is implementation that is subject to change.
 * Do not use it as an API
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
     * According to the Java Language Specifications, all the outer classes
     * and static inner classes are core classes.
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
     * Copy source file to destination file.
     *
     * @throws SecurityException
     * @throws IOException
     */
    public static void copyFile(File destfile, File srcfile)
        throws IOException {
        byte[] bytearr = new byte[512];
        int len = 0;
        FileInputStream input = new FileInputStream(srcfile);
        File destDir = destfile.getParentFile();
        destDir.mkdirs();
        FileOutputStream output = new FileOutputStream(destfile);
        try {
            while ((len = input.read(bytearr)) != -1) {
                output.write(bytearr, 0, len);
            }
        } catch (FileNotFoundException exc) {
        } catch (SecurityException exc) {
        } finally {
            input.close();
            output.close();
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
    public static void copyDocFiles(Configuration configuration,
            String path, String dir, boolean overwrite) {
        if (checkCopyDocFilesErrors(configuration, path, dir)) {
            return;
        }
        String destname = configuration.docFileDestDirName;
        File srcdir = new File(path + dir);
        if (destname.length() > 0 && !destname.endsWith(
               DirectoryManager.URL_FILE_SEPERATOR)) {
            destname += DirectoryManager.URL_FILE_SEPERATOR;
        }
        String dest = destname + dir;
        try {
            File destdir = new File(dest);
            DirectoryManager.createDirectory(configuration, dest);
            String[] files = srcdir.list();
            for (int i = 0; i < files.length; i++) {
                File srcfile = new File(srcdir, files[i]);
                File destfile = new File(destdir, files[i]);
                if (srcfile.isFile()) {
                    if(destfile.exists() && ! overwrite) {
                        configuration.message.warning((SourcePosition) null,
                                "doclet.Copy_Overwrite_warning",
                                srcfile.toString(), destdir.toString());
                    } else {
                        configuration.message.notice(
                            "doclet.Copying_File_0_To_Dir_1",
                            srcfile.toString(), destdir.toString());
                        Util.copyFile(destfile, srcfile);
                    }
                } else if(srcfile.isDirectory()) {
                    if(configuration.copydocfilesubdirs
                        && ! configuration.shouldExcludeDocFileDir(
                          srcfile.getName())){
                        copyDocFiles(configuration, path, dir +
                                    DirectoryManager.URL_FILE_SEPERATOR + srcfile.getName(),
                                overwrite);
                    }
                }
            }
        } catch (SecurityException exc) {
            throw new DocletAbortException();
        } catch (IOException exc) {
            throw new DocletAbortException();
        }
    }

    /**
     * Given the parameters for copying doc-files, check for errors.
     *
     * @param configuration The configuration of the current doclet.
     * @param path The relative path to the directory to be copied.
     * @param dirName The original directory name to copy from.
     */
    private static boolean checkCopyDocFilesErrors (Configuration configuration,
            String path, String dirName) {
        if ((configuration.sourcepath == null || configuration.sourcepath.length() == 0) &&
               (configuration.destDirName == null || configuration.destDirName.length() == 0)) {
            //The destination path and source path are definitely equal.
            return true;
        }
        File sourcePath, destPath = new File(configuration.destDirName);
        StringTokenizer pathTokens = new StringTokenizer(
            configuration.sourcepath == null ? "" : configuration.sourcepath,
            File.pathSeparator);
        //Check if the destination path is equal to the source path.  If yes,
        //do not copy doc-file directories.
        while(pathTokens.hasMoreTokens()){
            sourcePath = new File(pathTokens.nextToken());
            if(destPath.equals(sourcePath)){
                return true;
            }
        }
        //Make sure the doc-file being copied exists.
        File srcdir = new File(path + dirName);
        if (! srcdir.exists()) {
            return true;
        }
        return false;
    }

    /**
     * Copy a file in the resources directory to the destination
     * directory (if it is not there already).  If
     * <code>overwrite</code> is true and the destination file
     * already exists, overwrite it.
     *
     * @param configuration  Holds the destination directory and error message
     * @param resourcefile   The name of the resource file to copy
     * @param overwrite      A flag to indicate whether the file in the
     *                       destination directory will be overwritten if
     *                       it already exists.
     */
    public static void copyResourceFile(Configuration configuration,
            String resourcefile,
            boolean overwrite) {
        String destdir = configuration.destDirName;
        String destresourcesdir = destdir + "resources";
        DirectoryManager.createDirectory(configuration, destresourcesdir);
        File destfile = new File(destresourcesdir, resourcefile);
        if(destfile.exists() && (! overwrite)) return;
        try {

            InputStream in = Configuration.class.getResourceAsStream(
                "resources/" + resourcefile);

            if(in==null) return;

            OutputStream out = new FileOutputStream(destfile);
            byte[] buf = new byte[2048];
            int n;
            while((n = in.read(buf))>0) out.write(buf,0,n);

            in.close();
            out.close();
        } catch(Throwable t) {}
    }

    /**
     * Given a PackageDoc, return the source path for that package.
     * @param configuration The Configuration for the current Doclet.
     * @param pkgDoc The package to seach the path for.
     * @return A string representing the path to the given package.
     */
    public static String getPackageSourcePath(Configuration configuration,
            PackageDoc pkgDoc){
        try{
            String pkgPath = DirectoryManager.getDirectoryPath(pkgDoc);
            String completePath = new SourcePath(configuration.sourcepath).
                getDirectory(pkgPath) + DirectoryManager.URL_FILE_SEPERATOR;
            //Make sure that both paths are using the same seperators.
            completePath = Util.replaceText(completePath, File.separator,
                    DirectoryManager.URL_FILE_SEPERATOR);
            pkgPath = Util.replaceText(pkgPath, File.separator,
                    DirectoryManager.URL_FILE_SEPERATOR);
            return completePath.substring(0, completePath.indexOf(pkgPath));
        } catch (Exception e){
            return "";
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


    public static <T extends ProgramElementDoc> List<T> asList(T[] members) {
        List<T> list = new ArrayList<T>();
        for (int i = 0; i < members.length; i++) {
            list.add(members[i]);
        }
        return list;
    }

    /**
     * Enclose in quotes, used for paths and filenames that contains spaces
     */
    public static String quote(String filepath) {
        return ("\"" + filepath + "\"");
    }

    /**
     * Given a package, return it's name.
     * @param packageDoc the package to check.
     * @return the name of the given package.
     */
    public static String getPackageName(PackageDoc packageDoc) {
        return packageDoc == null || packageDoc.name().length() == 0 ?
            DocletConstants.DEFAULT_PACKAGE_NAME : packageDoc.name();
    }

    /**
     * Given a package, return it's file name without the extension.
     * @param packageDoc the package to check.
     * @return the file name of the given package.
     */
    public static String getPackageFileHeadName(PackageDoc packageDoc) {
        return packageDoc == null || packageDoc.name().length() == 0 ?
            DocletConstants.DEFAULT_PACKAGE_FILE_NAME : packageDoc.name();
    }

    /**
     * Given a string, replace all occurraces of 'newStr' with 'oldStr'.
     * @param originalStr the string to modify.
     * @param oldStr the string to replace.
     * @param newStr the string to insert in place of the old string.
     */
    public static String replaceText(String originalStr, String oldStr,
            String newStr) {
        if (oldStr == null || newStr == null || oldStr.equals(newStr)) {
            return originalStr;
        }
        StringBuffer result = new StringBuffer(originalStr);
        int startIndex = 0;
        while ((startIndex = result.indexOf(oldStr, startIndex)) != -1) {
            result = result.replace(startIndex, startIndex + oldStr.length(),
                    newStr);
            startIndex += newStr.length();
        }
        return result.toString();
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
     * Create the directory path for the file to be generated, construct
     * FileOutputStream and OutputStreamWriter depending upon docencoding.
     *
     * @param path The directory path to be created for this file.
     * @param filename File Name to which the PrintWriter will do the Output.
     * @param docencoding Encoding to be used for this file.
     * @exception IOException Exception raised by the FileWriter is passed on
     * to next level.
     * @exception UnsupportedEncodingException Exception raised by the
     * OutputStreamWriter is passed on to next level.
     * @return Writer Writer for the file getting generated.
     * @see java.io.FileOutputStream
     * @see java.io.OutputStreamWriter
     */
    public static Writer genWriter(Configuration configuration,
            String path, String filename,
            String docencoding)
        throws IOException, UnsupportedEncodingException {
        FileOutputStream fos;
        if (path != null) {
            DirectoryManager.createDirectory(configuration, path);
            fos = new FileOutputStream(((path.length() > 0)?
                                                  path + File.separator: "") + filename);
        } else {
            fos = new FileOutputStream(filename);
        }
        if (docencoding == null) {
            return new OutputStreamWriter(fos);
        } else {
            return new OutputStreamWriter(fos, docencoding);
        }
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
     * Given a string, return an array of tokens.  The separator can be escaped
     * with the '\' character.  The '\' character may also be escaped by the
     * '\' character.
     *
     * @param s         the string to tokenize.
     * @param separator the separator char.
     * @param maxTokens the maxmimum number of tokens returned.  If the
     *                  max is reached, the remaining part of s is appended
     *                  to the end of the last token.
     *
     * @return an array of tokens.
     */
    public static String[] tokenize(String s, char separator, int maxTokens) {
        List<String> tokens = new ArrayList<String>();
        StringBuilder  token = new StringBuilder ();
        boolean prevIsEscapeChar = false;
        for (int i = 0; i < s.length(); i += Character.charCount(i)) {
            int currentChar = s.codePointAt(i);
            if (prevIsEscapeChar) {
                // Case 1:  escaped character
                token.appendCodePoint(currentChar);
                prevIsEscapeChar = false;
            } else if (currentChar == separator && tokens.size() < maxTokens-1) {
                // Case 2:  separator
                tokens.add(token.toString());
                token = new StringBuilder();
            } else if (currentChar == '\\') {
                // Case 3:  escape character
                prevIsEscapeChar = true;
            } else {
                // Case 4:  regular character
                token.appendCodePoint(currentChar);
            }
        }
        if (token.length() > 0) {
            tokens.add(token.toString());
        }
        return tokens.toArray(new String[] {});
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
     *                      If false, the first letter of the name is capatilized.
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
    public static void replaceTabs(int tabLength, StringBuffer s) {
        int index, col;
        StringBuffer whitespace;
        while ((index = s.indexOf("\t")) != -1) {
            whitespace = new StringBuffer();
            col = index;
            do {
                whitespace.append(" ");
                col++;
            } while ((col%tabLength) != 0);
            s.replace(index, index+1, whitespace.toString());
        }
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
    public static boolean isDeprecated(ProgramElementDoc doc) {
        if (doc.tags("deprecated").length > 0) {
            return true;
        }
        AnnotationDesc[] annotationDescList = doc.annotations();
        for (int i = 0; i < annotationDescList.length; i++) {
            if (annotationDescList[i].annotationType().qualifiedName().equals(
                   java.lang.Deprecated.class.getName())){
                return true;
            }
        }
        return false;
    }
}
