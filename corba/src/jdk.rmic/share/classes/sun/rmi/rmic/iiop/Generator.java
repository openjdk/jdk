/*
 * Copyright (c) 1998, 2007, Oracle and/or its affiliates. All rights reserved.
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

/*
 * Licensed Materials - Property of IBM
 * RMI-IIOP v1.0
 * Copyright IBM Corp. 1998 1999  All Rights Reserved
 *
 */


package sun.rmi.rmic.iiop;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.IOException;
import sun.tools.java.Identifier;
import sun.tools.java.ClassPath;
import sun.tools.java.ClassFile;
import sun.tools.java.ClassNotFound;
import sun.tools.java.ClassDefinition;
import sun.tools.java.ClassDeclaration;
import sun.rmi.rmic.IndentingWriter;
import sun.rmi.rmic.Main;
import sun.rmi.rmic.iiop.Util;
import java.util.HashSet;

/**
 * Generator provides a small framework from which IIOP-specific
 * generators can inherit.  Common logic is implemented here which uses
 * both abstract methods as well as concrete methods which subclasses may
 * want to override. The following methods must be present in any subclass:
 * <pre>
 *      Default constructor
 *              CompoundType getTopType(BatchEnvironment env, ClassDefinition cdef);
 *      int parseArgs(String argv[], int currentIndex);
 *      boolean requireNewInstance();
 *              OutputType[] getOutputTypesFor(CompoundType topType,
 *                                     HashSet alreadyChecked);
 *              String getFileNameExtensionFor(OutputType outputType);
 *              void writeOutputFor (   OutputType outputType,
 *                              HashSet alreadyChecked,
 *                                                              IndentingWriter writer) throws IOException;
 * </pre>
 * @author      Bryan Atsatt
 */
public abstract class Generator implements      sun.rmi.rmic.Generator,
                                                sun.rmi.rmic.iiop.Constants {

    protected boolean alwaysGenerate = false;
    protected BatchEnvironment env = null;
    protected ContextStack contextStack = null;
    private boolean trace = false;
    protected boolean idl = false;

    /**
     * Examine and consume command line arguments.
     * @param argv The command line arguments. Ignore null
     * and unknown arguments. Set each consumed argument to null.
     * @param error Report any errors using the main.error() methods.
     * @return true if no errors, false otherwise.
     */
    public boolean parseArgs(String argv[], Main main) {
        for (int i = 0; i < argv.length; i++) {
            if (argv[i] != null) {
                if (argv[i].equalsIgnoreCase("-always") ||
                    argv[i].equalsIgnoreCase("-alwaysGenerate")) {
                    alwaysGenerate = true;
                    argv[i] = null;
                } else if (argv[i].equalsIgnoreCase("-xtrace")) {
                    trace = true;
                    argv[i] = null;
                }
            }
        }
        return true;
    }

    /**
     * Return true if non-conforming types should be parsed.
     * @param stack The context stack.
     */
    protected abstract boolean parseNonConforming(ContextStack stack);

    /**
     * Create and return a top-level type.
     * @param cdef The top-level class definition.
     * @param stack The context stack.
     * @return The compound type or null if is non-conforming.
     */
    protected abstract CompoundType getTopType(ClassDefinition cdef, ContextStack stack);

    /**
     * Return an array containing all the file names and types that need to be
     * generated for the given top-level type.  The file names must NOT have an
     * extension (e.g. ".java").
     * @param topType The type returned by getTopType().
     * @param alreadyChecked A set of Types which have already been checked.
     *  Intended to be passed to Type.collectMatching(filter,alreadyChecked).
     */
    protected abstract OutputType[] getOutputTypesFor(CompoundType topType,
                                                      HashSet alreadyChecked);

    /**
     * Return the file name extension for the given file name (e.g. ".java").
     * All files generated with the ".java" extension will be compiled. To
     * change this behavior for ".java" files, override the compileJavaSourceFile
     * method to return false.
     * @param outputType One of the items returned by getOutputTypesFor(...)
     */
    protected abstract String getFileNameExtensionFor(OutputType outputType);

    /**
     * Write the output for the given OutputFileName into the output stream.
     * @param name One of the items returned by getOutputTypesFor(...)
     * @param alreadyChecked A set of Types which have already been checked.
     *  Intended to be passed to Type.collectMatching(filter,alreadyChecked).
     * @param writer The output stream.
     */
    protected abstract void writeOutputFor(OutputType outputType,
                                                HashSet alreadyChecked,
                                                IndentingWriter writer) throws IOException;

    /**
     * Return true if a new instance should be created for each
     * class on the command line. Subclasses which return true
     * should override newInstance() to return an appropriately
     * constructed instance.
     */
    protected abstract boolean requireNewInstance();

    /**
     * Return true if the specified file needs generation.
     */
    public boolean requiresGeneration (File target, Type theType) {

        boolean result = alwaysGenerate;

        if (!result) {

            // Get a ClassFile instance for base source or class
            // file.  We use ClassFile so that if the base is in
            // a zip file, we can still get at it's mod time...

            ClassFile baseFile;
            ClassPath path = env.getClassPath();
            String className = theType.getQualifiedName().replace('.',File.separatorChar);

            // First try the source file...

            baseFile = path.getFile(className + ".source");

            if (baseFile == null) {

                // Then try class file...

                baseFile = path.getFile(className + ".class");
            }

            // Do we have a baseFile?

            if (baseFile != null) {

                // Yes, grab baseFile's mod time...

                long baseFileMod = baseFile.lastModified();

                // Get a File instance for the target. If it is a source
                // file, create a class file instead since the source file
                // will frequently be deleted...

                String targetName = IDLNames.replace(target.getName(),".java",".class");
                String parentPath = target.getParent();
                File targetFile = new File(parentPath,targetName);

                // Does the target file exist?

                if (targetFile.exists()) {

                    // Yes, so grab it's mod time...

                    long targetFileMod = targetFile.lastModified();

                    // Set result...

                    result = targetFileMod < baseFileMod;

                } else {

                    // No, so we must generate...

                    result = true;
                }
            } else {

                // No, so we must generate...

                result = true;
            }
        }

        return result;
    }

    /**
     * Create and return a new instance of self. Subclasses
     * which need to do something other than default construction
     * must override this method.
     */
    protected Generator newInstance() {
        Generator result = null;
        try {
            result = (Generator) getClass().newInstance();
        }
        catch (Exception e){} // Should ALWAYS work!

        return result;
    }

    /**
     * Default constructor for subclasses to use.
     */
    protected Generator() {
    }

    /**
     * Generate output. Any source files created which need compilation should
     * be added to the compiler environment using the addGeneratedFile(File)
     * method.
     *
     * @param env       The compiler environment
     * @param cdef      The definition for the implementation class or interface from
     *              which to generate output
     * @param destDir   The directory for the root of the package hierarchy
     *                          for generated files. May be null.
     */
    public void generate(sun.rmi.rmic.BatchEnvironment env, ClassDefinition cdef, File destDir) {

        this.env = (BatchEnvironment) env;
        contextStack = new ContextStack(this.env);
        contextStack.setTrace(trace);

        // Make sure the environment knows whether or not to parse
        // non-conforming types. This will clear out any previously
        // parsed types if necessary...

        this.env.setParseNonConforming(parseNonConforming(contextStack));

        // Get our top level type...

        CompoundType topType = getTopType(cdef,contextStack);
        if (topType != null) {

            Generator generator = this;

            // Do we need to make a new instance?

            if (requireNewInstance()) {

                                // Yes, so make one.  'this' instance is the one instantiated by Main
                                // and which knows any needed command line args...

                generator = newInstance();
            }

            // Now generate all output files...

            generator.generateOutputFiles(topType, this.env, destDir);
        }
    }

    /**
     * Create and return a new instance of self. Subclasses
     * which need to do something other than default construction
     * must override this method.
     */
    protected void generateOutputFiles (CompoundType topType,
                                        BatchEnvironment env,
                                        File destDir) {

        // Grab the 'alreadyChecked' HashSet from the environment...

        HashSet alreadyChecked = env.alreadyChecked;

        // Ask subclass for a list of output types...

        OutputType[] types = getOutputTypesFor(topType,alreadyChecked);

        // Process each file...

        for (int i = 0; i < types.length; i++) {
            OutputType current = types[i];
            String className = current.getName();
            File file = getFileFor(current,destDir);
            boolean sourceFile = false;

            // Do we need to generate this file?

            if (requiresGeneration(file,current.getType())) {

                // Yes. If java source file, add to environment so will be compiled...

                if (file.getName().endsWith(".java")) {
                    sourceFile = compileJavaSourceFile(current);

                                // Are we supposeded to compile this one?

                    if (sourceFile) {
                        env.addGeneratedFile(file);
                    }
                }

                // Now create an output stream and ask subclass to fill it up...

                try {
                   IndentingWriter out = new IndentingWriter(
                                                              new OutputStreamWriter(new FileOutputStream(file)),INDENT_STEP,TAB_SIZE);

                    long startTime = 0;
                    if (env.verbose()) {
                        startTime = System.currentTimeMillis();
                    }

                    writeOutputFor(types[i],alreadyChecked,out);
                    out.close();

                    if (env.verbose()) {
                        long duration = System.currentTimeMillis() - startTime;
                        env.output(Main.getText("rmic.generated", file.getPath(), Long.toString(duration)));
                    }
                    if (sourceFile) {
                        env.parseFile(ClassFile.newClassFile(file));
                    }
                } catch (IOException e) {
                    env.error(0, "cant.write", file.toString());
                    return;
                }
            } else {

                // No, say so if we need to...

                if (env.verbose()) {
                    env.output(Main.getText("rmic.previously.generated", file.getPath()));
                }
            }
        }
    }

    /**
     * Return the File object that should be used as the output file
     * for the given OutputType.
     * @param outputType The type to create a file for.
     * @param destinationDir The directory to use as the root of the
     * package heirarchy.  May be null, in which case the current
     * classpath is searched to find the directory in which to create
     * the output file.  If that search fails (most likely because the
     * package directory lives in a zip or jar file rather than the
     * file system), the current user directory is used.
     */
    protected File getFileFor(OutputType outputType, File destinationDir) {
        // Calling this method does some crucial initialization
        // in a subclass implementation. Don't skip it.
        Identifier id = getOutputId(outputType);
        File packageDir = null;
        if(idl){
            packageDir = Util.getOutputDirectoryForIDL(id,destinationDir,env);
        } else {
            packageDir = Util.getOutputDirectoryForStub(id,destinationDir,env);
        }
        String classFileName = outputType.getName() + getFileNameExtensionFor(outputType);
        return new File(packageDir, classFileName);
    }

    /**
     * Return an identifier to use for output.
     * @param outputType the type for which output is to be generated.
     * @return the new identifier. This implementation returns the input parameter.
     */
    protected Identifier getOutputId (OutputType outputType) {
        return outputType.getType().getIdentifier();
    }

    /**
     * Return true if the given file should be compiled.
     * @param outputType One of the items returned by getOutputTypesFor(...) for
     *   which getFileNameExtensionFor(OutputType) returned ".java".
     */
    protected boolean compileJavaSourceFile (OutputType outputType) {
        return true;
    }

    //_____________________________________________________________________
    // OutputType is a simple wrapper for a name and a Type
    //_____________________________________________________________________

    public class OutputType {
        private String name;
        private Type type;

        public OutputType (String name, Type type) {
            this.name = name;
            this.type = type;
        }

        public String getName() {
            return name;
        }

        public Type getType() {
            return type;
        }
    }
}
