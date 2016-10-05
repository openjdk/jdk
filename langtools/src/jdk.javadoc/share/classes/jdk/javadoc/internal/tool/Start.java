/*
 * Copyright (c) 1997, 2016, Oracle and/or its affiliates. All rights reserved.
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

package jdk.javadoc.internal.tool;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.text.BreakIterator;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;

import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.file.BaseFileManager;
import com.sun.tools.javac.file.JavacFileManager;
import com.sun.tools.javac.main.CommandLine;
import com.sun.tools.javac.main.OptionHelper;
import com.sun.tools.javac.main.OptionHelper.GrumpyHelper;
import com.sun.tools.javac.platform.PlatformDescription;
import com.sun.tools.javac.platform.PlatformUtils;
import com.sun.tools.javac.util.ClientCodeException;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Log;
import com.sun.tools.javac.util.Options;

import jdk.javadoc.doclet.Doclet;
import jdk.javadoc.doclet.Doclet.Option;
import jdk.javadoc.doclet.DocletEnvironment;
import jdk.javadoc.internal.doclets.toolkit.Resources;

import static javax.tools.DocumentationTool.Location.*;

import static com.sun.tools.javac.main.Option.*;

/**
 * Main program of Javadoc.
 * Previously named "Main".
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @author Robert Field
 * @author Neal Gafter (rewrite)
 */
public class Start extends ToolOption.Helper {

    @SuppressWarnings("deprecation")
    private static final Class<?> OldStdDoclet =
            com.sun.tools.doclets.standard.Standard.class;

    private static final Class<?> StdDoclet =
            jdk.javadoc.doclets.StandardDoclet.class;
    /** Context for this invocation. */
    private final Context context;

    private static final String ProgramName = "javadoc";

    private Messager messager;

    private final String docletName;

    private final ClassLoader classLoader;

    private Class<?> docletClass;

    private Doclet doclet;

    // used to determine the locale for the messager
    private Locale locale;


    /**
     * In API mode, exceptions thrown while calling the doclet are
     * propagated using ClientCodeException.
     */
    private boolean apiMode;

    private JavaFileManager fileManager;

    Start() {
        this(null, null, null, null, null);
    }

    Start(PrintWriter writer) {
        this(null, null, writer, null, null);
    }

    Start(Context context, String programName, PrintWriter writer,
            String docletName, ClassLoader classLoader) {
        this.context = context == null ? new Context() : context;
        String pname = programName == null ? ProgramName : programName;
        this.messager = writer == null
                ? new Messager(this.context, pname)
                : new Messager(this.context, pname, writer, writer);
        this.docletName = docletName;
        this.classLoader = classLoader;
        this.docletClass = null;
        this.locale = Locale.getDefault();
    }

    public Start(Context context) {
        this.docletClass = null;
        this.context = Objects.requireNonNull(context);
        this.apiMode = true;
        this.docletName = null;
        this.classLoader = null;
        this.locale = Locale.getDefault();
    }

    void initMessager() {
        if (!apiMode)
            return;
        if (messager == null) {
            Log log = context.get(Log.logKey);
            if (log instanceof Messager) {
                messager = (Messager) log;
            } else {
                PrintWriter out = context.get(Log.errKey);
                messager = (out == null)
                        ? new Messager(context, ProgramName)
                        : new Messager(context, ProgramName, out, out);
            }
        }
    }

    /**
     * Usage
     */
    @Override
    void usage() {
        usage(true);
    }

    void usage(boolean exit) {
        usage("main.usage", "-help", "main.usage.foot");

        if (exit)
            throw new Messager.ExitJavadoc();
    }

    @Override
    void Xusage() {
        Xusage(true);
    }

    void Xusage(boolean exit) {
        usage("main.Xusage", "-X", "main.Xusage.foot");

        if (exit)
            throw new Messager.ExitJavadoc();
    }

    private void usage(String header, String option, String footer) {
        messager.notice(header);
        showToolOptions(option.equals("-X") ? OptionKind.EXTENDED : OptionKind.STANDARD);

        // let doclet print usage information
        if (docletClass != null) {
            String name = doclet.getName();
            messager.notice("main.doclet.usage.header", name);
            showDocletOptions(option.equals("-X") ? Option.Kind.EXTENDED : Option.Kind.STANDARD);
        }

        if (footer != null)
            messager.notice(footer);
    }

    void showToolOptions(OptionKind kind) {
        Comparator<ToolOption> comp = new Comparator<ToolOption>() {
            final Collator collator = Collator.getInstance(Locale.US);
            { collator.setStrength(Collator.PRIMARY); }

            @Override
            public int compare(ToolOption o1, ToolOption o2) {
                return collator.compare(o1.primaryName, o2.primaryName);
            }
        };

        Stream.of(ToolOption.values())
                    .filter(opt -> opt.kind == kind)
                    .sorted(comp)
                    .forEach(opt -> showToolOption(opt));
    }

    void showToolOption(ToolOption option) {
        List<String> names = option.getNames();
        String parameters;
        if (option.hasArg || option.primaryName.endsWith(":")) {
            String sep = (option == ToolOption.J) || option.primaryName.endsWith(":") ? "" : " ";
            parameters = sep + option.getParameters(messager);
        } else {
            parameters = "";
        }
        String description = option.getDescription(messager);
        showUsage(names, parameters, description);
    }

    void showDocletOptions(Option.Kind kind) {
        Comparator<Doclet.Option> comp = new Comparator<Doclet.Option>() {
            final Collator collator = Collator.getInstance(Locale.US);
            { collator.setStrength(Collator.PRIMARY); }

            @Override
            public int compare(Doclet.Option o1, Doclet.Option o2) {
                return collator.compare(o1.getName(), o2.getName());
            }
        };

        doclet.getSupportedOptions().stream()
                .filter(opt -> opt.getKind() == kind)
                .sorted(comp)
                .forEach(opt -> showDocletOption(opt));
    }

    void showDocletOption(Doclet.Option option) {
        List<String> names = Arrays.asList(option.getName());
        String parameters;
        if (option.getArgumentCount() > 0 || option.getName().endsWith(":")) {
            String sep = option.getName().endsWith(":") ? "" : " ";
            parameters = sep + option.getParameters();
        } else {
            parameters = "";
        }
        String description = option.getDescription();
        showUsage(names, parameters, description);
    }

    // The following constants are intended to format the output to
    // be similar to that of the java launcher: i.e. "java -help".

    /** The indent for the option synopsis. */
    private static final String SMALL_INDENT = "    ";
    /** The automatic indent for the description. */
    private static final String LARGE_INDENT = "                  ";
    /** The space allowed for the synopsis, if the description is to be shown on the same line. */
    private static final int DEFAULT_SYNOPSIS_WIDTH = 13;
    /** The nominal maximum line length, when seeing if text will fit on a line. */
    private static final int DEFAULT_MAX_LINE_LENGTH = 80;
    /** The format for a single-line help entry. */
    private static final String COMPACT_FORMAT = SMALL_INDENT + "%-" + DEFAULT_SYNOPSIS_WIDTH + "s %s";

    void showUsage(List<String> names, String parameters, String description) {
        String synopses = names.stream()
                .map(s -> s + parameters)
                .collect(Collectors.joining(", "));
        // If option synopses and description fit on a single line of reasonable length,
        // display using COMPACT_FORMAT
        if (synopses.length() < DEFAULT_SYNOPSIS_WIDTH
                && !description.contains("\n")
                && (SMALL_INDENT.length() + DEFAULT_SYNOPSIS_WIDTH + 1 + description.length() <= DEFAULT_MAX_LINE_LENGTH)) {
            messager.printNotice(String.format(COMPACT_FORMAT, synopses, description));
            return;
        }

        // If option synopses fit on a single line of reasonable length, show that;
        // otherwise, show 1 per line
        if (synopses.length() <= DEFAULT_MAX_LINE_LENGTH) {
            messager.printNotice(SMALL_INDENT + synopses);
        } else {
            for (String name: names) {
                messager.printNotice(SMALL_INDENT + name + parameters);
            }
        }

        // Finally, show the description
        messager.printNotice(LARGE_INDENT + description.replace("\n", "\n" + LARGE_INDENT));
    }


    /**
     * Main program - external wrapper. In order to maintain backward
     * CLI  compatibility, we dispatch to the old tool or the old doclet's
     * Start mechanism, based on the options present on the command line
     * with the following precedence:
     *   1. presence of -Xold, dispatch to old tool
     *   2. doclet variant, if old, dispatch to old Start
     *   3. taglet variant, if old, dispatch to old Start
     *
     * Thus the presence of -Xold switches the tool, soon after command files
     * if any, are expanded, this is performed here, noting that the messager
     * is available at this point in time.
     * The doclet/taglet tests are performed in the begin method, further on,
     * this is to minimize argument processing and most importantly the impact
     * of class loader creation, needed to detect the doclet/taglet class variants.
     */
    @SuppressWarnings("deprecation")
    int begin(String... argv) {
        // Preprocess @file arguments
        try {
            argv = CommandLine.parse(argv);
        } catch (FileNotFoundException e) {
            messager.error("main.cant.read", e.getMessage());
            throw new Messager.ExitJavadoc();
        } catch (IOException e) {
            e.printStackTrace(System.err);
            throw new Messager.ExitJavadoc();
        }

        if (argv.length > 0 && "-Xold".equals(argv[0])) {
            messager.warning("main.legacy_api");
            String[] nargv = Arrays.copyOfRange(argv, 1, argv.length);
            return com.sun.tools.javadoc.Main.execute(nargv);
        }
        boolean ok = begin(Arrays.asList(argv), Collections.<JavaFileObject> emptySet());
        return ok ? 0 : 1;
    }

    // Called by 199 API.
    public boolean begin(Class<?> docletClass,
            Iterable<String> options,
            Iterable<? extends JavaFileObject> fileObjects) {
        this.docletClass = docletClass;
        List<String> opts = new ArrayList<>();
        for (String opt: options)
            opts.add(opt);

        return begin(opts, fileObjects);
    }

    @SuppressWarnings("deprecation")
    private boolean begin(List<String> options, Iterable<? extends JavaFileObject> fileObjects) {
        fileManager = context.get(JavaFileManager.class);
        if (fileManager == null) {
            JavacFileManager.preRegister(context);
            fileManager = context.get(JavaFileManager.class);
            if (fileManager instanceof BaseFileManager) {
                ((BaseFileManager) fileManager).autoClose = true;
            }
        }
        // locale, doclet and maybe taglet, needs to be determined first
        docletClass = preProcess(fileManager, options);
        if (jdk.javadoc.doclet.Doclet.class.isAssignableFrom(docletClass)) {
            // no need to dispatch to old, safe to init now
            initMessager();
            messager.setLocale(locale);
            try {
                Object o = docletClass.getConstructor().newInstance();
                doclet = (Doclet) o;
            } catch (ReflectiveOperationException exc) {
                exc.printStackTrace();
                if (!apiMode) {
                    error("main.could_not_instantiate_class", docletClass);
                    throw new Messager.ExitJavadoc();
                }
                throw new ClientCodeException(exc);
            }
        } else {
            if (this.apiMode) {
                com.sun.tools.javadoc.main.Start ostart
                        = new com.sun.tools.javadoc.main.Start(context);
                return ostart.begin(docletClass, options, fileObjects);
            }
            warn("main.legacy_api");
            String[] array = options.toArray(new String[options.size()]);
            return com.sun.tools.javadoc.Main.execute(array) == 0;
        }

        boolean failed = false;
        try {
            failed = !parseAndExecute(options, fileObjects);
        } catch (Messager.ExitJavadoc exc) {
            // ignore, we just exit this way
        } catch (OutOfMemoryError ee) {
            messager.error("main.out.of.memory");
            failed = true;
        } catch (ClientCodeException e) {
            // simply rethrow these exceptions, to be caught and handled by JavadocTaskImpl
            throw e;
        } catch (Error ee) {
            ee.printStackTrace(System.err);
            messager.error("main.fatal.error");
            failed = true;
        } catch (Exception ee) {
            ee.printStackTrace(System.err);
            messager.error("main.fatal.exception");
            failed = true;
        } finally {
            if (fileManager != null
                    && fileManager instanceof BaseFileManager
                    && ((BaseFileManager) fileManager).autoClose) {
                try {
                    fileManager.close();
                } catch (IOException ignore) {}
            }
            boolean haveErrorWarnings = messager.nerrors() > 0 ||
                    (rejectWarnings && messager.nwarnings() > 0);
            if (failed && !haveErrorWarnings) {
                // the doclet failed, but nothing reported, flag it!.
                messager.error("main.unknown.error");
            }
            failed |= haveErrorWarnings;
            messager.exitNotice();
            messager.flush();
        }
        return !failed;
    }

    /**
     * Main program - internal
     */
    @SuppressWarnings("unchecked")
    private boolean parseAndExecute(List<String> argList,
            Iterable<? extends JavaFileObject> fileObjects) throws IOException {
        long tm = System.currentTimeMillis();

        List<String> javaNames = new ArrayList<>();

        compOpts = Options.instance(context);

        // Make sure no obsolete source/target messages are reported
        com.sun.tools.javac.main.Option.XLINT.process(getOptionHelper(), "-Xlint:-options");

        doclet.init(locale, messager);
        parseArgs(argList, javaNames);

        if (fileManager instanceof BaseFileManager) {
            ((BaseFileManager) fileManager).handleOptions(fileManagerOpts);
        }

        String platformString = compOpts.get("--release");

        if (platformString != null) {
            if (compOpts.isSet("-source")) {
                usageError("main.release.bootclasspath.conflict", "-source");
            }
            if (fileManagerOpts.containsKey(BOOT_CLASS_PATH)) {
                usageError("main.release.bootclasspath.conflict", BOOT_CLASS_PATH.getPrimaryName());
            }

            PlatformDescription platformDescription =
                    PlatformUtils.lookupPlatformDescription(platformString);

            if (platformDescription == null) {
                usageError("main.unsupported.release.version", platformString);
            }

            compOpts.put(SOURCE, platformDescription.getSourceVersion());

            context.put(PlatformDescription.class, platformDescription);

            Collection<Path> platformCP = platformDescription.getPlatformPath();

            if (platformCP != null) {
                if (fileManager instanceof StandardJavaFileManager) {
                    StandardJavaFileManager sfm = (StandardJavaFileManager) fileManager;

                    sfm.setLocationFromPaths(StandardLocation.PLATFORM_CLASS_PATH, platformCP);
                } else {
                    usageError("main.release.not.standard.file.manager", platformString);
                }
            }
        }

        compOpts.notifyListeners();
        List<String> modules = (List<String>) jdtoolOpts.computeIfAbsent(ToolOption.MODULE,
                s -> Collections.EMPTY_LIST);

        if (modules.isEmpty()) {
            List<String> subpkgs = (List<String>) jdtoolOpts.computeIfAbsent(ToolOption.SUBPACKAGES,
                    s -> Collections.EMPTY_LIST);
            if (subpkgs.isEmpty()) {
                if (javaNames.isEmpty() && isEmpty(fileObjects)) {
                    usageError("main.No_modules_packages_or_classes_specified");
                }
            }
        }

        JavadocTool comp = JavadocTool.make0(context);
        if (comp == null) return false;

        DocletEnvironment docEnv = comp.getEnvironment(jdtoolOpts,
                javaNames,
                fileObjects);

        // release resources
        comp = null;

        if (breakiterator || !locale.getLanguage().equals(Locale.ENGLISH.getLanguage())) {
            JavacTrees trees = JavacTrees.instance(context);
            trees.setBreakIterator(BreakIterator.getSentenceInstance(locale));
        }
        // pass off control to the doclet
        boolean ok = docEnv != null;
        if (ok) ok = doclet.run(docEnv);

        // We're done.
        if (compOpts.get("-verbose") != null) {
            tm = System.currentTimeMillis() - tm;
            messager.notice("main.done_in", Long.toString(tm));
        }

        return ok;
    }

    Set<Doclet.Option> docletOptions = null;
    int handleDocletOptions(int idx, List<String> args, boolean isToolOption) {
        if (docletOptions == null) {
            docletOptions = doclet.getSupportedOptions();
        }
        String arg = args.get(idx);
        String argBase, argVal;
        if (arg.startsWith("--") && arg.contains("=")) {
            int sep = arg.indexOf("=");
            argBase = arg.substring(0, sep);
            argVal = arg.substring(sep + 1);
        } else {
            argBase = arg;
            argVal = null;
        }

        for (Doclet.Option opt : docletOptions) {
            if (opt.matches(argBase)) {
                if (argVal != null) {
                    switch (opt.getArgumentCount()) {
                        case 0:
                            usageError("main.unnecessary_arg_provided", argBase);
                            break;
                        case 1:
                            opt.process(arg, Arrays.asList(argVal).listIterator());
                            break;
                        default:
                            usageError("main.only_one_argument_with_equals", argBase);
                            break;
                    }
                } else {
                    if (args.size() - idx -1 < opt.getArgumentCount()) {
                        usageError("main.requires_argument", arg);
                    }
                    opt.process(arg, args.listIterator(idx + 1));
                    idx += opt.getArgumentCount();
                }
                return idx;
            }
        }
        // check if arg is accepted by the tool before emitting error
        if (!isToolOption)
            usageError("main.invalid_flag", arg);
        return idx;
    }

    private Class<?> preProcess(JavaFileManager jfm, List<String> argv) {
        // doclet specifying arguments
        String userDocletPath = null;
        String userDocletName = null;

        // taglet specifying arguments, since tagletpath is a doclet
        // functionality, assume they are repeated and inspect all.
        List<File> userTagletPath = new ArrayList<>();
        List<String> userTagletNames = new ArrayList<>();

        // Step 1: loop through the args, set locale early on, if found.
        for (int i = 0 ; i < argv.size() ; i++) {
            String arg = argv.get(i);
            if (arg.equals(ToolOption.LOCALE.primaryName)) {
                checkOneArg(argv, i++);
                String lname = argv.get(i);
                locale = getLocale(lname);
            } else if (arg.equals(ToolOption.DOCLET.primaryName)) {
                checkOneArg(argv, i++);
                if (userDocletName != null) {
                    usageError("main.more_than_one_doclet_specified_0_and_1",
                            userDocletName, argv.get(i));
                }
                if (docletName != null) {
                    usageError("main.more_than_one_doclet_specified_0_and_1",
                            docletName, argv.get(i));
                }
                userDocletName = argv.get(i);
            } else if (arg.equals(ToolOption.DOCLETPATH.primaryName)) {
                checkOneArg(argv, i++);
                if (userDocletPath == null) {
                    userDocletPath = argv.get(i);
                } else {
                    userDocletPath += File.pathSeparator + argv.get(i);
                }
            } else if ("-taglet".equals(arg)) {
                userTagletNames.add(argv.get(i + 1));
            } else if ("-tagletpath".equals(arg)) {
                for (String pathname : argv.get(i + 1).split(File.pathSeparator)) {
                    userTagletPath.add(new File(pathname));
                }
            }
        }

        // Step 2: a doclet is provided, nothing more to do.
        if (docletClass != null) {
            return docletClass;
        }

        // Step 3: doclet name specified ? if so find a ClassLoader,
        // and load it.
        if (userDocletName != null) {
            ClassLoader cl = classLoader;
            if (cl == null) {
                if (!fileManager.hasLocation(DOCLET_PATH)) {
                    List<File> paths = new ArrayList<>();
                    if (userDocletPath != null) {
                        for (String pathname : userDocletPath.split(File.pathSeparator)) {
                            paths.add(new File(pathname));
                        }
                    }
                    try {
                        ((StandardJavaFileManager)fileManager).setLocation(DOCLET_PATH, paths);
                    } catch (IOException ioe) {
                        error("main.doclet_could_not_set_location", paths);
                        throw new Messager.ExitJavadoc();
                    }
                }
                cl = fileManager.getClassLoader(DOCLET_PATH);
                if (cl == null) {
                    // despite doclet specified on cmdline no classloader found!
                    error("main.doclet_no_classloader_found", userDocletName);
                    throw new Messager.ExitJavadoc();
                }
            }
            try {
                Class<?> klass = cl.loadClass(userDocletName);
                return klass;
            } catch (ClassNotFoundException cnfe) {
                error("main.doclet_class_not_found", userDocletName);
                throw new Messager.ExitJavadoc();
            }
        }

        // Step 4: we have a doclet, try loading it
        if (docletName != null) {
            try {
                return Class.forName(docletName, true, getClass().getClassLoader());
            } catch (ClassNotFoundException cnfe) {
                error("main.doclet_class_not_found", userDocletName);
                throw new Messager.ExitJavadoc();
            }
        }

        // Step 5: we don't have a doclet specified, do we have taglets ?
        if (!userTagletNames.isEmpty() && hasOldTaglet(userTagletNames, userTagletPath)) {
            // found a bogey, return the old doclet
            return OldStdDoclet;
        }

        // finally
        return StdDoclet;
    }

    /*
     * This method returns true iff it finds a legacy taglet, but for
     * all other conditions including errors it returns false, allowing
     * nature to take its own course.
     */
    @SuppressWarnings("deprecation")
    private boolean hasOldTaglet(List<String> tagletNames, List<File> tagletPaths) {
        if (!fileManager.hasLocation(TAGLET_PATH)) {
            try {
                ((StandardJavaFileManager) fileManager).setLocation(TAGLET_PATH, tagletPaths);
            } catch (IOException ioe) {
                error("main.doclet_could_not_set_location", tagletPaths);
                throw new Messager.ExitJavadoc();
            }
        }
        ClassLoader cl = fileManager.getClassLoader(TAGLET_PATH);
        if (cl == null) {
            // no classloader found!
            error("main.doclet_no_classloader_found", tagletNames.get(0));
            throw new Messager.ExitJavadoc();
        }
        for (String tagletName : tagletNames) {
            try {
                Class<?> klass = cl.loadClass(tagletName);
                if (com.sun.tools.doclets.Taglet.class.isAssignableFrom(klass)) {
                    return true;
                }
            } catch (ClassNotFoundException cnfe) {
                error("main.doclet_class_not_found", tagletName);
                throw new Messager.ExitJavadoc();
            }
        }
        return false;
    }

    private void parseArgs(List<String> args, List<String> javaNames) {
        for (int i = 0 ; i < args.size() ; i++) {
            String arg = args.get(i);
            ToolOption o = ToolOption.get(arg);
            if (o != null) {
                // handle a doclet argument that may be needed however
                // don't increment the index, and allow the tool to consume args
                handleDocletOptions(i, args, true);

                if (o.hasArg) {
                    if (arg.startsWith("--") && arg.contains("=")) {
                        o.process(this, arg.substring(arg.indexOf('=') + 1));
                    } else {
                        checkOneArg(args, i++);
                        o.process(this, args.get(i));
                    }
                } else if (o.hasSuffix) {
                    o.process(this, arg);
                } else {
                    o.process(this);
                }
            } else if (arg.startsWith("-XD")) {
                // hidden javac options
                String s = arg.substring("-XD".length());
                int eq = s.indexOf('=');
                String key = (eq < 0) ? s : s.substring(0, eq);
                String value = (eq < 0) ? s : s.substring(eq+1);
                compOpts.put(key, value);
            } else if (arg.startsWith("-")) {
                i = handleDocletOptions(i, args, false);
            } else {
                javaNames.add(arg);
            }
        }
    }

    private <T> boolean isEmpty(Iterable<T> iter) {
        return !iter.iterator().hasNext();
    }

    /**
     * Check the one arg option.
     * Error and exit if one argument is not provided.
     */
    private void checkOneArg(List<String> args, int index) {
        if ((index + 1) >= args.size() || args.get(index + 1).startsWith("-d")) {
            usageError("main.requires_argument", args.get(index));
        }
    }

    @Override
    void usageError(String key, Object... args) {
        error(key, args);
        usage(true);
    }

    void error(String key, Object... args) {
        messager.error(key, args);
    }

    void warn(String key, Object... args)  {
        messager.warning(key, args);
    }

    /**
     * Get the locale if specified on the command line
     * else return null and if locale option is not used
     * then return default locale.
     */
    private Locale getLocale(String localeName) {
        Locale userlocale = null;
        if (localeName == null || localeName.isEmpty()) {
            return Locale.getDefault();
        }
        int firstuscore = localeName.indexOf('_');
        int seconduscore = -1;
        String language = null;
        String country = null;
        String variant = null;
        if (firstuscore == 2) {
            language = localeName.substring(0, firstuscore);
            seconduscore = localeName.indexOf('_', firstuscore + 1);
            if (seconduscore > 0) {
                if (seconduscore != firstuscore + 3
                        || localeName.length() <= seconduscore + 1) {
                    usageError("main.malformed_locale_name", localeName);
                    return null;
                }
                country = localeName.substring(firstuscore + 1,
                        seconduscore);
                variant = localeName.substring(seconduscore + 1);
            } else if (localeName.length() == firstuscore + 3) {
                country = localeName.substring(firstuscore + 1);
            } else {
                usageError("main.malformed_locale_name", localeName);
                return null;
            }
        } else if (firstuscore == -1 && localeName.length() == 2) {
            language = localeName;
        } else {
            usageError("main.malformed_locale_name", localeName);
            return null;
        }
        userlocale = searchLocale(language, country, variant);
        if (userlocale == null) {
            usageError("main.illegal_locale_name", localeName);
            return null;
        } else {
            return userlocale;
        }
    }

    /**
     * Search the locale for specified language, specified country and
     * specified variant.
     */
    private Locale searchLocale(String language, String country,
                                String variant) {
        for (Locale loc : Locale.getAvailableLocales()) {
            if (loc.getLanguage().equals(language) &&
                (country == null || loc.getCountry().equals(country)) &&
                (variant == null || loc.getVariant().equals(variant))) {
                return loc;
            }
        }
        return null;
    }

    @Override
    OptionHelper getOptionHelper() {
        return new GrumpyHelper(null) {
            @Override
            public String get(com.sun.tools.javac.main.Option option) {
                return compOpts.get(option);
            }

            @Override
            public void put(String name, String value) {
                compOpts.put(name, value);
            }
        };
    }
}
