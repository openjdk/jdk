/*
 * Copyright (c) 2005, 2008, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.javac.processing;

import com.sun.tools.javac.util.*;
import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.Element;
import java.util.*;

import java.io.Closeable;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.FilterOutputStream;
import java.io.Reader;
import java.io.Writer;
import java.io.FilterWriter;
import java.io.PrintWriter;
import java.io.IOException;

import javax.tools.*;
import static java.util.Collections.*;

import javax.tools.JavaFileManager.Location;
import static javax.tools.StandardLocation.SOURCE_OUTPUT;
import static javax.tools.StandardLocation.CLASS_OUTPUT;

/**
 * The FilerImplementation class must maintain a number of
 * constraints.  First, multiple attempts to open the same path within
 * the same invocation of the tool results in an IOException being
 * thrown.  For example, trying to open the same source file twice:
 *
 * <pre>
 * createSourceFile("foo.Bar")
 * ...
 * createSourceFile("foo.Bar")
 * </pre>
 *
 * is disallowed as is opening a text file that happens to have
 * the same name as a source file:
 *
 * <pre>
 * createSourceFile("foo.Bar")
 * ...
 * createTextFile(SOURCE_TREE, "foo", new File("Bar"), null)
 * </pre>
 *
 * <p>Additionally, creating a source file that corresponds to an
 * already created class file (or vice versa) also results in an
 * IOException since each type can only be created once.  However, if
 * the Filer is used to create a text file named *.java that happens
 * to correspond to an existing class file, a warning is *not*
 * generated.  Similarly, a warning is not generated for a binary file
 * named *.class and an existing source file.
 *
 * <p>The reason for this difference is that source files and class
 * files are registered with the tool and can get passed on as
 * declarations to the next round of processing.  Files that are just
 * named *.java and *.class are not processed in that manner; although
 * having extra source files and class files on the source path and
 * class path can alter the behavior of the tool and any final
 * compile.
 *
 * <p><b>This is NOT part of any supported API.
 * If you write code that depends on this, you do so at your own risk.
 * This code and its internal interfaces are subject to change or
 * deletion without notice.</b>
 */
public class JavacFiler implements Filer, Closeable {
    // TODO: Implement different transaction model for updating the
    // Filer's record keeping on file close.

    private static final String ALREADY_OPENED =
        "Output stream or writer has already been opened.";
    private static final String NOT_FOR_READING =
        "FileObject was not opened for reading.";
    private static final String NOT_FOR_WRITING =
        "FileObject was not opened for writing.";

    /**
     * Wrap a JavaFileObject to manage writing by the Filer.
     */
    private class FilerOutputFileObject extends ForwardingFileObject<FileObject> {
        private boolean opened = false;
        private String name;

        FilerOutputFileObject(String name, FileObject fileObject) {
            super(fileObject);
            this.name = name;
        }

        @Override
        public synchronized OutputStream openOutputStream() throws IOException {
            if (opened)
                throw new IOException(ALREADY_OPENED);
            opened = true;
            return new FilerOutputStream(name, fileObject);
        }

        @Override
        public synchronized Writer openWriter() throws IOException {
            if (opened)
                throw new IOException(ALREADY_OPENED);
            opened = true;
            return new FilerWriter(name, fileObject);
        }

        // Three anti-literacy methods
        @Override
        public InputStream openInputStream() throws IOException {
            throw new IllegalStateException(NOT_FOR_READING);
        }

        @Override
        public Reader openReader(boolean ignoreEncodingErrors) throws IOException {
            throw new IllegalStateException(NOT_FOR_READING);
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
            throw new IllegalStateException(NOT_FOR_READING);
        }

        @Override
        public boolean delete() {
            return false;
        }
    }

    private class FilerOutputJavaFileObject extends FilerOutputFileObject implements JavaFileObject {
        private final JavaFileObject javaFileObject;
        FilerOutputJavaFileObject(String name, JavaFileObject javaFileObject) {
            super(name, javaFileObject);
            this.javaFileObject = javaFileObject;
        }

        public JavaFileObject.Kind getKind() {
            return javaFileObject.getKind();
        }

        public boolean isNameCompatible(String simpleName,
                                        JavaFileObject.Kind kind) {
            return javaFileObject.isNameCompatible(simpleName, kind);
        }

        public NestingKind getNestingKind() {
            return javaFileObject.getNestingKind();
        }

        public Modifier getAccessLevel() {
            return javaFileObject.getAccessLevel();
        }
    }

    /**
     * Wrap a JavaFileObject to manage reading by the Filer.
     */
    private class FilerInputFileObject extends ForwardingFileObject<FileObject> {
        FilerInputFileObject(FileObject fileObject) {
            super(fileObject);
        }

        @Override
        public OutputStream openOutputStream() throws IOException {
            throw new IllegalStateException(NOT_FOR_WRITING);
        }

        @Override
        public Writer openWriter() throws IOException {
            throw new IllegalStateException(NOT_FOR_WRITING);
        }

        @Override
        public boolean delete() {
            return false;
        }
    }

    private class FilerInputJavaFileObject extends FilerInputFileObject implements JavaFileObject {
        private final JavaFileObject javaFileObject;
        FilerInputJavaFileObject(JavaFileObject javaFileObject) {
            super(javaFileObject);
            this.javaFileObject = javaFileObject;
        }

        public JavaFileObject.Kind getKind() {
            return javaFileObject.getKind();
        }

        public boolean isNameCompatible(String simpleName,
                                        JavaFileObject.Kind kind) {
            return javaFileObject.isNameCompatible(simpleName, kind);
        }

        public NestingKind getNestingKind() {
            return javaFileObject.getNestingKind();
        }

        public Modifier getAccessLevel() {
            return javaFileObject.getAccessLevel();
        }
    }


    /**
     * Wrap a {@code OutputStream} returned from the {@code
     * JavaFileManager} to properly register source or class files
     * when they are closed.
     */
    private class FilerOutputStream extends FilterOutputStream {
        String typeName;
        FileObject fileObject;
        boolean closed = false;

        /**
         * @param typeName name of class or {@code null} if just a
         * binary file
         */
        FilerOutputStream(String typeName, FileObject fileObject) throws IOException {
            super(fileObject.openOutputStream());
            this.typeName = typeName;
            this.fileObject = fileObject;
        }

        public synchronized void close() throws IOException {
            if (!closed) {
                closed = true;
                /*
                 * If an IOException occurs when closing the underlying
                 * stream, still try to process the file.
                 */

                closeFileObject(typeName, fileObject);
                out.close();
            }
        }
    }

    /**
     * Wrap a {@code Writer} returned from the {@code JavaFileManager}
     * to properly register source or class files when they are
     * closed.
     */
    private class FilerWriter extends FilterWriter {
        String typeName;
        FileObject fileObject;
        boolean closed = false;

        /**
         * @param fileObject the fileObject to be written to
         * @param typeName name of source file or {@code null} if just a
         * text file
         */
        FilerWriter(String typeName, FileObject fileObject) throws IOException {
            super(fileObject.openWriter());
            this.typeName = typeName;
            this.fileObject = fileObject;
        }

        public synchronized void close() throws IOException {
            if (!closed) {
                closed = true;
                /*
                 * If an IOException occurs when closing the underlying
                 * Writer, still try to process the file.
                 */

                closeFileObject(typeName, fileObject);
                out.close();
            }
        }
    }

    JavaFileManager fileManager;
    Log log;
    Context context;
    boolean lastRound;

    private final boolean lint;

    /**
     * Logical names of all created files.  This set must be
     * synchronized.
     */
    private final Set<FileObject> fileObjectHistory;

    /**
     * Names of types that have had files created but not closed.
     */
    private final Set<String> openTypeNames;

    /**
     * Names of source files closed in this round.  This set must be
     * synchronized.  Its iterators should preserve insertion order.
     */
    private Set<String> generatedSourceNames;

    /**
     * Names and class files of the class files closed in this round.
     * This set must be synchronized.  Its iterators should preserve
     * insertion order.
     */
    private final Map<String, JavaFileObject> generatedClasses;

    /**
     * JavaFileObjects for source files closed in this round.  This
     * set must be synchronized.  Its iterators should preserve
     * insertion order.
     */
    private Set<JavaFileObject> generatedSourceFileObjects;

    /**
     * Names of all created source files.  Its iterators should
     * preserve insertion order.
     */
    private final Set<String> aggregateGeneratedSourceNames;

    /**
     * Names of all created class files.  Its iterators should
     * preserve insertion order.
     */
    private final Set<String> aggregateGeneratedClassNames;


    JavacFiler(Context context) {
        this.context = context;
        fileManager = context.get(JavaFileManager.class);

        log = Log.instance(context);

        fileObjectHistory = synchronizedSet(new LinkedHashSet<FileObject>());
        generatedSourceNames = synchronizedSet(new LinkedHashSet<String>());
        generatedSourceFileObjects = synchronizedSet(new LinkedHashSet<JavaFileObject>());

        generatedClasses = synchronizedMap(new LinkedHashMap<String, JavaFileObject>());

        openTypeNames  = synchronizedSet(new LinkedHashSet<String>());

        aggregateGeneratedSourceNames = new LinkedHashSet<String>();
        aggregateGeneratedClassNames  = new LinkedHashSet<String>();

        lint = (Options.instance(context)).lint("processing");
    }

    public JavaFileObject createSourceFile(CharSequence name,
                                           Element... originatingElements) throws IOException {
        return createSourceOrClassFile(true, name.toString());
    }

    public JavaFileObject createClassFile(CharSequence name,
                                           Element... originatingElements) throws IOException {
        return createSourceOrClassFile(false, name.toString());
    }

    private JavaFileObject createSourceOrClassFile(boolean isSourceFile, String name) throws IOException {
        if (lint) {
            int periodIndex = name.lastIndexOf(".");
            if (periodIndex != -1) {
                String base = name.substring(periodIndex);
                String extn = (isSourceFile ? ".java" : ".class");
                if (base.equals(extn))
                    log.warning("proc.suspicious.class.name", name, extn);
            }
        }
        checkNameAndExistence(name, isSourceFile);
        Location loc = (isSourceFile ? SOURCE_OUTPUT : CLASS_OUTPUT);
        JavaFileObject.Kind kind = (isSourceFile ?
                                    JavaFileObject.Kind.SOURCE :
                                    JavaFileObject.Kind.CLASS);

        JavaFileObject fileObject =
            fileManager.getJavaFileForOutput(loc, name, kind, null);
        checkFileReopening(fileObject, true);

        if (lastRound)
            log.warning("proc.file.create.last.round", name);

        if (isSourceFile)
            aggregateGeneratedSourceNames.add(name);
        else
            aggregateGeneratedClassNames.add(name);
        openTypeNames.add(name);

        return new FilerOutputJavaFileObject(name, fileObject);
    }

    public FileObject createResource(JavaFileManager.Location location,
                                     CharSequence pkg,
                                     CharSequence relativeName,
                                     Element... originatingElements) throws IOException {
        locationCheck(location);

        String strPkg = pkg.toString();
        if (strPkg.length() > 0)
            checkName(strPkg);

        FileObject fileObject =
            fileManager.getFileForOutput(location, strPkg,
                                         relativeName.toString(), null);
        checkFileReopening(fileObject, true);

        if (fileObject instanceof JavaFileObject)
            return new FilerOutputJavaFileObject(null, (JavaFileObject)fileObject);
        else
            return new FilerOutputFileObject(null, fileObject);
    }

    private void locationCheck(JavaFileManager.Location location) {
        if (location instanceof StandardLocation) {
            StandardLocation stdLoc = (StandardLocation) location;
            if (!stdLoc.isOutputLocation())
                throw new IllegalArgumentException("Resource creation not supported in location " +
                                                   stdLoc);
        }
    }

    public FileObject getResource(JavaFileManager.Location location,
                                  CharSequence pkg,
                                  CharSequence relativeName) throws IOException {
        String strPkg = pkg.toString();
        if (strPkg.length() > 0)
            checkName(strPkg);

        // TODO: Only support reading resources in selected output
        // locations?  Only allow reading of non-source, non-class
        // files from the supported input locations?
        FileObject fileObject = fileManager.getFileForOutput(location,
                                                             pkg.toString(),
                                                             relativeName.toString(),
                                                             null);
        // If the path was already opened for writing, throw an exception.
        checkFileReopening(fileObject, false);
        return new FilerInputFileObject(fileObject);
    }

    private void checkName(String name) throws FilerException {
        checkName(name, false);
    }

    private void checkName(String name, boolean allowUnnamedPackageInfo) throws FilerException {
        if (!SourceVersion.isName(name) && !isPackageInfo(name, allowUnnamedPackageInfo)) {
            if (lint)
                log.warning("proc.illegal.file.name", name);
            throw new FilerException("Illegal name " + name);
        }
    }

    private boolean isPackageInfo(String name, boolean allowUnnamedPackageInfo) {
        // Is the name of the form "package-info" or
        // "foo.bar.package-info"?
        final String PKG_INFO = "package-info";
        int periodIndex = name.lastIndexOf(".");
        if (periodIndex == -1) {
            return allowUnnamedPackageInfo ? name.equals(PKG_INFO) : false;
        } else {
            // "foo.bar.package-info." illegal
            String prefix = name.substring(0, periodIndex);
            String simple = name.substring(periodIndex+1);
            return SourceVersion.isName(prefix) && simple.equals(PKG_INFO);
        }
    }

    private void checkNameAndExistence(String typename, boolean allowUnnamedPackageInfo) throws FilerException {
        // TODO: Check if type already exists on source or class path?
        // If so, use warning message key proc.type.already.exists
        checkName(typename, allowUnnamedPackageInfo);
        if (aggregateGeneratedSourceNames.contains(typename) ||
            aggregateGeneratedClassNames.contains(typename)) {
            if (lint)
                log.warning("proc.type.recreate", typename);
            throw new FilerException("Attempt to recreate a file for type " + typename);
        }
    }

    /**
     * Check to see if the file has already been opened; if so, throw
     * an exception, otherwise add it to the set of files.
     */
    private void checkFileReopening(FileObject fileObject, boolean addToHistory) throws FilerException {
        for(FileObject veteran : fileObjectHistory) {
            if (fileManager.isSameFile(veteran, fileObject)) {
                if (lint)
                    log.warning("proc.file.reopening", fileObject.getName());
                throw new FilerException("Attempt to reopen a file for path " + fileObject.getName());
            }
        }
        if (addToHistory)
            fileObjectHistory.add(fileObject);
    }

    public boolean newFiles() {
        return (!generatedSourceNames.isEmpty())
            || (!generatedClasses.isEmpty());
    }

    public Set<String> getGeneratedSourceNames() {
        return generatedSourceNames;
    }

    public Set<JavaFileObject> getGeneratedSourceFileObjects() {
        return generatedSourceFileObjects;
    }

    public Map<String, JavaFileObject> getGeneratedClasses() {
        return generatedClasses;
    }

    public void warnIfUnclosedFiles() {
        if (!openTypeNames.isEmpty())
            log.warning("proc.unclosed.type.files", openTypeNames.toString());
    }

    /**
     * Update internal state for a new round.
     */
    public void newRound(Context context, boolean lastRound) {
        this.context = context;
        this.log = Log.instance(context);
        this.lastRound = lastRound;
        clearRoundState();
    }

    public void close() {
        clearRoundState();
        // Cross-round state
        fileObjectHistory.clear();
        openTypeNames.clear();
        aggregateGeneratedSourceNames.clear();
        aggregateGeneratedClassNames.clear();
    }

    private void clearRoundState() {
        generatedSourceNames.clear();
        generatedSourceFileObjects.clear();
        generatedClasses.clear();
    }

    /**
     * Debugging function to display internal state.
     */
    public void displayState() {
        PrintWriter xout = context.get(Log.outKey);
        xout.println("File Object History : " +  fileObjectHistory);
        xout.println("Open Type Names     : " +  openTypeNames);
        xout.println("Gen. Src Names      : " +  generatedSourceNames);
        xout.println("Gen. Cls Names      : " +  generatedClasses.keySet());
        xout.println("Agg. Gen. Src Names : " +  aggregateGeneratedSourceNames);
        xout.println("Agg. Gen. Cls Names : " +  aggregateGeneratedClassNames);
    }

    public String toString() {
        return "javac Filer";
    }

    /**
     * Upon close, register files opened by create{Source, Class}File
     * for annotation processing.
     */
    private void closeFileObject(String typeName, FileObject fileObject) {
        /*
         * If typeName is non-null, the file object was opened as a
         * source or class file by the user.  If a file was opened as
         * a resource, typeName will be null and the file is *not*
         * subject to annotation processing.
         */
        if ((typeName != null)) {
            if (!(fileObject instanceof JavaFileObject))
                throw new AssertionError("JavaFileOject not found for " + fileObject);
            JavaFileObject javaFileObject = (JavaFileObject)fileObject;
            switch(javaFileObject.getKind()) {
            case SOURCE:
                generatedSourceNames.add(typeName);
                generatedSourceFileObjects.add(javaFileObject);
                openTypeNames.remove(typeName);
                break;

            case CLASS:
                generatedClasses.put(typeName, javaFileObject);
                openTypeNames.remove(typeName);
                break;

            default:
                break;
            }
        }
    }

}
