/*
 * Copyright 2004-2005 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.tools.apt.mirror.apt;


import java.io.*;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Set;

import com.sun.mirror.apt.Filer;
import com.sun.tools.apt.mirror.declaration.DeclarationMaker;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Options;
import com.sun.tools.javac.util.Position;
import com.sun.tools.apt.util.Bark;

import static com.sun.mirror.apt.Filer.Location.*;


/**
 * Implementation of Filer.
 */
@SuppressWarnings("deprecation")
public class FilerImpl implements Filer {
    /*
     * The Filer class must maintain a number of constraints.  First,
     * multiple attempts to open the same path within the same
     * invocation of apt results in an IOException being thrown.  For
     * example, trying to open the same source file twice:
     *
     * createSourceFile("foo.Bar")
     * ...
     * createSourceFile("foo.Bar")
     *
     * is disallowed as is opening a text file that happens to have
     * the same name as a source file:
     *
     * createSourceFile("foo.Bar")
     * ...
     * createTextFile(SOURCE_TREE, "foo", new File("Bar"), null)
     *
     * Additionally, creating a source file that corresponds to an
     * already created class file (or vice versa) generates at least a
     * warning.  This is an error if -XclassesAsDecls is being used
     * since you can't create the same type twice.  However, if the
     * Filer is used to create a text file named *.java that happens
     * to correspond to an existing class file, a warning is *not*
     * generated.  Similarly, a warning is not generated for a binary
     * file named *.class and an existing source file.
     *
     * The reason for this difference is that source files and class
     * files are registered with apt and can get passed on as
     * declarations to the next round of processing.  Files that are
     * just named *.java and *.class are not processed in that manner;
     * although having extra source files and class files on the
     * source path and class path can alter the behavior of the tool
     * and any final compile.
     */

    private enum FileKind {
        SOURCE {
            void register(File file, String name, FilerImpl that) throws IOException {
                // Check for corresponding class file
                if (that.filesCreated.contains(new File(that.locations.get(CLASS_TREE),
                                                        that.nameToPath(name, ".class")))) {

                    that.bark.aptWarning("CorrespondingClassFile", name);
                    if (that.opts.get("-XclassesAsDecls") != null)
                        throw new IOException();
                }
                that.sourceFileNames.add(file.getPath());
            }
        },

        CLASS  {
            void register(File file, String name, FilerImpl that) throws IOException {
                if (that.filesCreated.contains(new File(that.locations.get(SOURCE_TREE),
                                                        that.nameToPath(name, ".java")))) {
                    that.bark.aptWarning("CorrespondingSourceFile", name);
                    if (that.opts.get("-XclassesAsDecls") != null)
                        throw new IOException();
                }
                // Track the binary name instead of the filesystem location
                that.classFileNames.add(name);
            }
        },

        OTHER  {
            // Nothing special to do
            void register(File file, String name, FilerImpl that) throws IOException {}
        };

        abstract void register(File file, String name, FilerImpl that) throws IOException;
    }

    private final Options opts;
    private final DeclarationMaker declMaker;
    private final com.sun.tools.apt.main.JavaCompiler comp;

    // Platform's default encoding
    private final static String DEFAULT_ENCODING =
            new OutputStreamWriter(new ByteArrayOutputStream()).getEncoding();

    private String encoding;    // name of charset used for source files

    private final EnumMap<Location, File> locations;    // where new files go


    private static final Context.Key<FilerImpl> filerKey =
            new Context.Key<FilerImpl>();

    // Set of files opened.
    private Collection<Flushable> wc;

    private Bark bark;

    // All created files.
    private final Set<File> filesCreated;

    // Names of newly created source files
    private HashSet<String> sourceFileNames = new HashSet<String>();

    // Names of newly created class files
    private HashSet<String> classFileNames  = new HashSet<String>();

    private boolean roundOver;

    public static FilerImpl instance(Context context) {
        FilerImpl instance = context.get(filerKey);
        if (instance == null) {
            instance = new FilerImpl(context);
        }
        return instance;
    }

    // flush all output streams;
    public void flush() {
        for(Flushable opendedFile: wc) {
            try {
                opendedFile.flush();
                if (opendedFile instanceof FileOutputStream) {
                    try {
                        ((FileOutputStream) opendedFile).getFD().sync() ;
                    } catch (java.io.SyncFailedException sfe) {}
                }
            } catch (IOException e) { }
        }
    }

    private FilerImpl(Context context) {
        context.put(filerKey, this);
        opts = Options.instance(context);
        declMaker = DeclarationMaker.instance(context);
        bark = Bark.instance(context);
        comp = com.sun.tools.apt.main.JavaCompiler.instance(context);
        roundOver = false;
        this.filesCreated = comp.getAggregateGenFiles();

        // Encoding
        encoding = opts.get("-encoding");
        if (encoding == null) {
            encoding = DEFAULT_ENCODING;
        }

        wc = new HashSet<Flushable>();

        // Locations
        locations = new EnumMap<Location, File>(Location.class);
        String s = opts.get("-s");      // location for new source files
        String d = opts.get("-d");      // location for new class files
        locations.put(SOURCE_TREE, new File(s != null ? s : "."));
        locations.put(CLASS_TREE,  new File(d != null ? d : "."));
    }


    /**
     * {@inheritDoc}
     */
    public PrintWriter createSourceFile(String name) throws IOException {
        String pathname = nameToPath(name, ".java");
        File file = new File(locations.get(SOURCE_TREE),
                             pathname);
        PrintWriter pw = getPrintWriter(file, encoding, name, FileKind.SOURCE);
        return pw;
    }

    /**
     * {@inheritDoc}
     */
    public OutputStream createClassFile(String name) throws IOException {
        String pathname = nameToPath(name, ".class");
        File file = new File(locations.get(CLASS_TREE),
                             pathname);
        OutputStream os = getOutputStream(file, name, FileKind.CLASS);
        return os;
    }

    /**
     * {@inheritDoc}
     */
    public PrintWriter createTextFile(Location loc,
                                      String pkg,
                                      File relPath,
                                      String charsetName) throws IOException {
        File file = (pkg.length() == 0)
                        ? relPath
                        : new File(nameToPath(pkg), relPath.getPath());
        if (charsetName == null) {
            charsetName = encoding;
        }
        return getPrintWriter(loc, file.getPath(), charsetName, null, FileKind.OTHER);
    }

    /**
     * {@inheritDoc}
     */
    public OutputStream createBinaryFile(Location loc,
                                         String pkg,
                                         File relPath) throws IOException {
        File file = (pkg.length() == 0)
                        ? relPath
                        : new File(nameToPath(pkg), relPath.getPath());
        return getOutputStream(loc, file.getPath(), null, FileKind.OTHER);
    }


    /**
     * Converts the canonical name of a top-level type or package to a
     * pathname.  Suffix is ".java" or ".class" or "".
     */
    private String nameToPath(String name, String suffix) throws IOException {
        if (!DeclarationMaker.isJavaIdentifier(name.replace('.', '_'))) {
            bark.aptWarning("IllegalFileName", name);
            throw new IOException();
        }
        return name.replace('.', File.separatorChar) + suffix;
    }

    private String nameToPath(String name) throws IOException {
        return nameToPath(name, "");
    }

    /**
     * Returns a writer for a text file given its location, its
     * pathname relative to that location, and its encoding.
     */
    private PrintWriter getPrintWriter(Location loc, String pathname,
                                       String encoding, String name, FileKind kind) throws IOException {
        File file = new File(locations.get(loc), pathname);
        return getPrintWriter(file, encoding, name, kind);
    }

    /**
     * Returns a writer for a text file given its encoding.
     */
    private PrintWriter getPrintWriter(File file,
                                       String encoding, String name, FileKind kind) throws IOException {
        prepareFile(file, name, kind);
        PrintWriter pw =
            new PrintWriter(
                    new BufferedWriter(
                         new OutputStreamWriter(new FileOutputStream(file),
                                                encoding)));
        wc.add(pw);
        return pw;
    }

    /**
     * Returns an output stream for a binary file given its location
     * and its pathname relative to that location.
     */
    private OutputStream getOutputStream(Location loc, String pathname, String name, FileKind kind)
                                                        throws IOException {
        File file = new File(locations.get(loc), pathname);
        return getOutputStream(file, name, kind);
    }

    private OutputStream getOutputStream(File file, String name, FileKind kind) throws IOException {
        prepareFile(file, name, kind);
        OutputStream os = new FileOutputStream(file);
        wc.add(os);
        return os;

    }

    public Set<String> getSourceFileNames() {
        return sourceFileNames;
    }

    public Set<String> getClassFileNames() {
        return classFileNames;
    }

    public void roundOver() {
        roundOver = true;
    }

    /**
     * Checks that the file has not already been created during this
     * invocation.  If not, creates intermediate directories, and
     * deletes the file if it already exists.
     */
    private void prepareFile(File file, String name, FileKind kind) throws IOException {
        if (roundOver) {
            bark.aptWarning("NoNewFilesAfterRound", file.toString());
            throw new IOException();
        }

        if (filesCreated.contains(file)) {
            bark.aptWarning("FileReopening", file.toString());
            throw new IOException();
        } else {
            if (file.exists()) {
                file.delete();
            } else {
                File parent = file.getParentFile();
                if (parent != null && !parent.exists()) {
                    if(!parent.mkdirs()) {
                        bark.aptWarning("BadParentDirectory", file.toString());
                        throw new IOException();
                    }
                }
            }

            kind.register(file, name, this);
            filesCreated.add(file);
        }
    }
}
