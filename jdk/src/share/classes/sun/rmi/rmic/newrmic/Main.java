/*
 * Copyright (c) 2003, 2005, Oracle and/or its affiliates. All rights reserved.
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

package sun.rmi.rmic.newrmic;

import com.sun.javadoc.ClassDoc;
import com.sun.javadoc.RootDoc;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import sun.rmi.rmic.newrmic.jrmp.JrmpGenerator;
import sun.tools.util.CommandLine;

/**
 * The rmic front end.  This class contains the "main" method for rmic
 * command line invocation.
 *
 * A Main instance contains the stream to output error messages and
 * other diagnostics to.
 *
 * An rmic compilation batch (for example, one rmic command line
 * invocation) is executed by invoking the "compile" method of a Main
 * instance.
 *
 * WARNING: The contents of this source file are not part of any
 * supported API.  Code that depends on them does so at its own risk:
 * they are subject to change or removal without notice.
 *
 * NOTE: If and when there is a J2SE API for invoking SDK tools, this
 * class should be updated to support that API.
 *
 * NOTE: This class is the front end for a "new" rmic implementation,
 * which uses javadoc and the doclet API for reading class files and
 * javac for compiling generated source files.  This implementation is
 * incomplete: it lacks any CORBA-based back end implementations, and
 * thus the command line options "-idl", "-iiop", and their related
 * options are not yet supported.  The front end for the "old",
 * oldjavac-based rmic implementation is sun.rmi.rmic.Main.
 *
 * @author Peter Jones
 **/
public class Main {

    /*
     * Implementation note:
     *
     * In order to use the doclet API to read class files, much of
     * this implementation of rmic executes as a doclet within an
     * invocation of javadoc.  This class is used as the doclet class
     * for such javadoc invocations, via its static "start" and
     * "optionLength" methods.  There is one javadoc invocation per
     * rmic compilation batch.
     *
     * The only guaranteed way to pass data to a doclet through a
     * javadoc invocation is through doclet-specific options on the
     * javadoc "command line".  Rather than passing numerous pieces of
     * individual data in string form as javadoc options, we use a
     * single doclet-specific option ("-batchID") to pass a numeric
     * identifier that uniquely identifies the rmic compilation batch
     * that the javadoc invocation is for, and that identifier can
     * then be used as a key in a global table to retrieve an object
     * containing all of batch-specific data (rmic command line
     * arguments, etc.).
     */

    /** guards "batchCount" */
    private static final Object batchCountLock = new Object();

    /** number of batches run; used to generated batch IDs */
    private static long batchCount = 0;

    /** maps batch ID to batch data */
    private static final Map<Long,Batch> batchTable =
        Collections.synchronizedMap(new HashMap<Long,Batch>());

    /** stream to output error messages and other diagnostics to */
    private final PrintStream out;

    /** name of this program, to use in error messages */
    private final String program;

    /**
     * Command line entry point.
     **/
    public static void main(String[] args) {
        Main rmic = new Main(System.err, "rmic");
        System.exit(rmic.compile(args) ? 0 : 1);
    }

    /**
     * Creates a Main instance that writes output to the specified
     * stream.  The specified program name is used in error messages.
     **/
    public Main(OutputStream out, String program) {
        this.out = out instanceof PrintStream ?
            (PrintStream) out : new PrintStream(out);
        this.program = program;
    }

    /**
     * Compiles a batch of input classes, as given by the specified
     * command line arguments.  Protocol-specific generators are
     * determined by the choice options on the command line.  Returns
     * true if successful, or false if an error occurred.
     *
     * NOTE: This method is retained for transitional consistency with
     * previous implementations.
     **/
    public boolean compile(String[] args) {
        long startTime = System.currentTimeMillis();

        long batchID;
        synchronized (batchCountLock) {
            batchID = batchCount++;     // assign batch ID
        }

        // process command line
        Batch batch = parseArgs(args);
        if (batch == null) {
            return false;               // terminate if error occurred
        }

        /*
         * With the batch data retrievable in the global table, run
         * javadoc to continue the rest of the batch's compliation as
         * a doclet.
         */
        boolean status;
        try {
            batchTable.put(batchID, batch);
            status = invokeJavadoc(batch, batchID);
        } finally {
            batchTable.remove(batchID);
        }

        if (batch.verbose) {
            long deltaTime = System.currentTimeMillis() - startTime;
            output(Resources.getText("rmic.done_in",
                                     Long.toString(deltaTime)));
        }

        return status;
    }

    /**
     * Prints the specified string to the output stream of this Main
     * instance.
     **/
    public void output(String msg) {
        out.println(msg);
    }

    /**
     * Prints an error message to the output stream of this Main
     * instance.  The first argument is used as a key in rmic's
     * resource bundle, and the rest of the arguments are used as
     * arguments in the formatting of the resource string.
     **/
    public void error(String msg, String... args) {
        output(Resources.getText(msg, args));
    }

    /**
     * Prints rmic's usage message to the output stream of this Main
     * instance.
     *
     * This method is public so that it can be used by the "parseArgs"
     * methods of Generator implementations.
     **/
    public void usage() {
        error("rmic.usage", program);
    }

    /**
     * Processes rmic command line arguments.  Returns a Batch object
     * representing the command line arguments if successful, or null
     * if an error occurred.  Processed elements of the args array are
     * set to null.
     **/
    private Batch parseArgs(String[] args) {
        Batch batch = new Batch();

        /*
         * Pre-process command line for @file arguments.
         */
        try {
            args = CommandLine.parse(args);
        } catch (FileNotFoundException e) {
            error("rmic.cant.read", e.getMessage());
            return null;
        } catch (IOException e) {
            e.printStackTrace(out);
            return null;
        }

        for (int i = 0; i < args.length; i++) {

            if (args[i] == null) {
                // already processed by a generator
                continue;

            } else if (args[i].equals("-Xnew")) {
                // we're already using the "new" implementation
                args[i] = null;

            } else if (args[i].equals("-show")) {
                // obselete: fail
                error("rmic.option.unsupported", args[i]);
                usage();
                return null;

            } else if (args[i].equals("-O")) {
                // obselete: warn but tolerate
                error("rmic.option.unsupported", args[i]);
                args[i] = null;

            } else if (args[i].equals("-debug")) {
                // obselete: warn but tolerate
                error("rmic.option.unsupported", args[i]);
                args[i] = null;

            } else if (args[i].equals("-depend")) {
                // obselete: warn but tolerate
                // REMIND: should this fail instead?
                error("rmic.option.unsupported", args[i]);
                args[i] = null;

            } else if (args[i].equals("-keep") ||
                       args[i].equals("-keepgenerated"))
            {
                batch.keepGenerated = true;
                args[i] = null;

            } else if (args[i].equals("-g")) {
                batch.debug = true;
                args[i] = null;

            } else if (args[i].equals("-nowarn")) {
                batch.noWarn = true;
                args[i] = null;

            } else if (args[i].equals("-nowrite")) {
                batch.noWrite = true;
                args[i] = null;

            } else if (args[i].equals("-verbose")) {
                batch.verbose = true;
                args[i] = null;

            } else if (args[i].equals("-Xnocompile")) {
                batch.noCompile = true;
                batch.keepGenerated = true;
                args[i] = null;

            } else if (args[i].equals("-bootclasspath")) {
                if ((i + 1) >= args.length) {
                    error("rmic.option.requires.argument", args[i]);
                    usage();
                    return null;
                }
                if (batch.bootClassPath != null) {
                    error("rmic.option.already.seen", args[i]);
                    usage();
                    return null;
                }
                args[i] = null;
                batch.bootClassPath = args[++i];
                assert batch.bootClassPath != null;
                args[i] = null;

            } else if (args[i].equals("-extdirs")) {
                if ((i + 1) >= args.length) {
                    error("rmic.option.requires.argument", args[i]);
                    usage();
                    return null;
                }
                if (batch.extDirs != null) {
                    error("rmic.option.already.seen", args[i]);
                    usage();
                    return null;
                }
                args[i] = null;
                batch.extDirs = args[++i];
                assert batch.extDirs != null;
                args[i] = null;

            } else if (args[i].equals("-classpath")) {
                if ((i + 1) >= args.length) {
                    error("rmic.option.requires.argument", args[i]);
                    usage();
                    return null;
                }
                if (batch.classPath != null) {
                    error("rmic.option.already.seen", args[i]);
                    usage();
                    return null;
                }
                args[i] = null;
                batch.classPath = args[++i];
                assert batch.classPath != null;
                args[i] = null;

            } else if (args[i].equals("-d")) {
                if ((i + 1) >= args.length) {
                    error("rmic.option.requires.argument", args[i]);
                    usage();
                    return null;
                }
                if (batch.destDir != null) {
                    error("rmic.option.already.seen", args[i]);
                    usage();
                    return null;
                }
                args[i] = null;
                batch.destDir = new File(args[++i]);
                assert batch.destDir != null;
                args[i] = null;
                if (!batch.destDir.exists()) {
                    error("rmic.no.such.directory", batch.destDir.getPath());
                    usage();
                    return null;
                }

            } else if (args[i].equals("-v1.1") ||
                       args[i].equals("-vcompat") ||
                       args[i].equals("-v1.2"))
            {
                Generator gen = new JrmpGenerator();
                batch.generators.add(gen);
                // JrmpGenerator only requires base BatchEnvironment class
                if (!gen.parseArgs(args, this)) {
                    return null;
                }

            } else if (args[i].equalsIgnoreCase("-iiop")) {
                error("rmic.option.unimplemented", args[i]);
                return null;

                // Generator gen = new IiopGenerator();
                // batch.generators.add(gen);
                // if (!batch.envClass.isAssignableFrom(gen.envClass())) {
                //   error("rmic.cannot.use.both",
                //         batch.envClass.getName(), gen.envClass().getName());
                //   return null;
                // }
                // batch.envClass = gen.envClass();
                // if (!gen.parseArgs(args, this)) {
                //   return null;
                // }

            } else if (args[i].equalsIgnoreCase("-idl")) {
                error("rmic.option.unimplemented", args[i]);
                return null;

                // see implementation sketch above

            } else if (args[i].equalsIgnoreCase("-xprint")) {
                error("rmic.option.unimplemented", args[i]);
                return null;

                // see implementation sketch above
            }
        }

        /*
         * At this point, all that remains non-null in the args
         * array are input class names or illegal options.
         */
        for (int i = 0; i < args.length; i++) {
            if (args[i] != null) {
                if (args[i].startsWith("-")) {
                    error("rmic.no.such.option", args[i]);
                    usage();
                    return null;
                } else {
                    batch.classes.add(args[i]);
                }
            }
        }
        if (batch.classes.isEmpty()) {
            usage();
            return null;
        }

        /*
         * If options did not specify at least one protocol-specific
         * generator, then JRMP is the default.
         */
        if (batch.generators.isEmpty()) {
            batch.generators.add(new JrmpGenerator());
        }
        return batch;
    }

    /**
     * Doclet class entry point.
     **/
    public static boolean start(RootDoc rootDoc) {

        /*
         * Find batch ID among javadoc options, and retrieve
         * corresponding batch data from global table.
         */
        long batchID = -1;
        for (String[] option : rootDoc.options()) {
            if (option[0].equals("-batchID")) {
                try {
                    batchID = Long.parseLong(option[1]);
                } catch (NumberFormatException e) {
                    throw new AssertionError(e);
                }
            }
        }
        Batch batch = batchTable.get(batchID);
        assert batch != null;

        /*
         * Construct batch environment using class agreed upon by
         * generator implementations.
         */
        BatchEnvironment env;
        try {
            Constructor<? extends BatchEnvironment> cons =
                batch.envClass.getConstructor(new Class[] { RootDoc.class });
            env = cons.newInstance(rootDoc);
        } catch (NoSuchMethodException e) {
            throw new AssertionError(e);
        } catch (IllegalAccessException e) {
            throw new AssertionError(e);
        } catch (InstantiationException e) {
            throw new AssertionError(e);
        } catch (InvocationTargetException e) {
            throw new AssertionError(e);
        }

        env.setVerbose(batch.verbose);

        /*
         * Determine the destination directory (the top of the package
         * hierarchy) for the output of this batch; if no destination
         * directory was specified on the command line, then the
         * default is the current working directory.
         */
        File destDir = batch.destDir;
        if (destDir == null) {
            destDir = new File(System.getProperty("user.dir"));
        }

        /*
         * Run each input class through each generator.
         */
        for (String inputClassName : batch.classes) {
            ClassDoc inputClass = rootDoc.classNamed(inputClassName);
            try {
                for (Generator gen : batch.generators) {
                    gen.generate(env, inputClass, destDir);
                }
            } catch (NullPointerException e) {
                /*
                 * We assume that this means that some class that was
                 * needed (perhaps even a bootstrap class) was not
                 * found, and that javadoc has already reported this
                 * as an error.  There is nothing for us to do here
                 * but try to continue with the next input class.
                 *
                 * REMIND: More explicit error checking throughout
                 * would be preferable, however.
                 */
            }
        }

        /*
         * Compile any generated source files, if configured to do so.
         */
        boolean status = true;
        List<File> generatedFiles = env.generatedFiles();
        if (!batch.noCompile && !batch.noWrite && !generatedFiles.isEmpty()) {
            status = batch.enclosingMain().invokeJavac(batch, generatedFiles);
        }

        /*
         * Delete any generated source files, if configured to do so.
         */
        if (!batch.keepGenerated) {
            for (File file : generatedFiles) {
                file.delete();
            }
        }

        return status;
    }

    /**
     * Doclet class method that indicates that this doclet class
     * recognizes (only) the "-batchID" option on the javadoc command
     * line, and that the "-batchID" option comprises two arguments on
     * the javadoc command line.
     **/
    public static int optionLength(String option) {
        if (option.equals("-batchID")) {
            return 2;
        } else {
            return 0;
        }
    }

    /**
     * Runs the javadoc tool to invoke this class as a doclet, passing
     * command line options derived from the specified batch data and
     * indicating the specified batch ID.
     *
     * NOTE: This method currently uses a J2SE-internal API to run
     * javadoc.  If and when there is a J2SE API for invoking SDK
     * tools, this method should be updated to use that API instead.
     **/
    private boolean invokeJavadoc(Batch batch, long batchID) {
        List<String> javadocArgs = new ArrayList<String>();

        // include all types, regardless of language-level access
        javadocArgs.add("-private");

        // inputs are class names, not source files
        javadocArgs.add("-Xclasses");

        // reproduce relevant options from rmic invocation
        if (batch.verbose) {
            javadocArgs.add("-verbose");
        }
        if (batch.bootClassPath != null) {
            javadocArgs.add("-bootclasspath");
            javadocArgs.add(batch.bootClassPath);
        }
        if (batch.extDirs != null) {
            javadocArgs.add("-extdirs");
            javadocArgs.add(batch.extDirs);
        }
        if (batch.classPath != null) {
            javadocArgs.add("-classpath");
            javadocArgs.add(batch.classPath);
        }

        // specify batch ID
        javadocArgs.add("-batchID");
        javadocArgs.add(Long.toString(batchID));

        /*
         * Run javadoc on union of rmic input classes and all
         * generators' bootstrap classes, so that they will all be
         * available to the doclet code.
         */
        Set<String> classNames = new HashSet<String>();
        for (Generator gen : batch.generators) {
            classNames.addAll(gen.bootstrapClassNames());
        }
        classNames.addAll(batch.classes);
        for (String s : classNames) {
            javadocArgs.add(s);
        }

        // run javadoc with our program name and output stream
        int status = com.sun.tools.javadoc.Main.execute(
            program,
            new PrintWriter(out, true),
            new PrintWriter(out, true),
            new PrintWriter(out, true),
            this.getClass().getName(),          // doclet class is this class
            javadocArgs.toArray(new String[javadocArgs.size()]));
        return status == 0;
    }

    /**
     * Runs the javac tool to compile the specified source files,
     * passing command line options derived from the specified batch
     * data.
     *
     * NOTE: This method currently uses a J2SE-internal API to run
     * javac.  If and when there is a J2SE API for invoking SDK tools,
     * this method should be updated to use that API instead.
     **/
    private boolean invokeJavac(Batch batch, List<File> files) {
        List<String> javacArgs = new ArrayList<String>();

        // rmic never wants to display javac warnings
        javacArgs.add("-nowarn");

        // reproduce relevant options from rmic invocation
        if (batch.debug) {
            javacArgs.add("-g");
        }
        if (batch.verbose) {
            javacArgs.add("-verbose");
        }
        if (batch.bootClassPath != null) {
            javacArgs.add("-bootclasspath");
            javacArgs.add(batch.bootClassPath);
        }
        if (batch.extDirs != null) {
            javacArgs.add("-extdirs");
            javacArgs.add(batch.extDirs);
        }
        if (batch.classPath != null) {
            javacArgs.add("-classpath");
            javacArgs.add(batch.classPath);
        }

        /*
         * For now, rmic still always produces class files that have a
         * class file format version compatible with JDK 1.1.
         */
        javacArgs.add("-source");
        javacArgs.add("1.3");
        javacArgs.add("-target");
        javacArgs.add("1.1");

        // add source files to compile
        for (File file : files) {
            javacArgs.add(file.getPath());
        }

        // run javac with our output stream
        int status = com.sun.tools.javac.Main.compile(
            javacArgs.toArray(new String[javacArgs.size()]),
            new PrintWriter(out, true));
        return status == 0;
    }

    /**
     * The data for an rmic compliation batch: the processed command
     * line arguments.
     **/
    private class Batch {
        boolean keepGenerated = false;  // -keep or -keepgenerated
        boolean debug = false;          // -g
        boolean noWarn = false;         // -nowarn
        boolean noWrite = false;        // -nowrite
        boolean verbose = false;        // -verbose
        boolean noCompile = false;      // -Xnocompile
        String bootClassPath = null;    // -bootclasspath
        String extDirs = null;          // -extdirs
        String classPath = null;        // -classpath
        File destDir = null;            // -d
        List<Generator> generators = new ArrayList<Generator>();
        Class<? extends BatchEnvironment> envClass = BatchEnvironment.class;
        List<String> classes = new ArrayList<String>();

        Batch() { }

        /**
         * Returns the Main instance for this batch.
         **/
        Main enclosingMain() {
            return Main.this;
        }
    }
}
