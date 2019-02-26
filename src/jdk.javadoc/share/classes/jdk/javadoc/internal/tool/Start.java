/*
 * Copyright (c) 1997, 2018, Oracle and/or its affiliates. All rights reserved.
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
import java.util.IllformedLocaleException;
import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.Objects;
import java.util.ResourceBundle;
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
import com.sun.tools.javac.jvm.Target;
import com.sun.tools.javac.main.Arguments;
import com.sun.tools.javac.main.CommandLine;
import com.sun.tools.javac.main.OptionHelper;
import com.sun.tools.javac.main.OptionHelper.GrumpyHelper;
import com.sun.tools.javac.platform.PlatformDescription;
import com.sun.tools.javac.platform.PlatformUtils;
import com.sun.tools.javac.util.ClientCodeException;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Log;
import com.sun.tools.javac.util.Log.WriterKind;
import com.sun.tools.javac.util.Options;
import com.sun.tools.javac.util.StringUtils;

import jdk.javadoc.doclet.Doclet;
import jdk.javadoc.doclet.Doclet.Option;
import jdk.javadoc.doclet.DocletEnvironment;
import jdk.javadoc.internal.tool.Main.Result;

import static javax.tools.DocumentationTool.Location.*;

import static com.sun.tools.javac.main.Option.*;
import static jdk.javadoc.internal.tool.Main.Result.*;

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

    private static final Class<?> StdDoclet =
            jdk.javadoc.doclet.StandardDoclet.class;
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
        this(null, null, null, null, null, null);
    }

    Start(PrintWriter outWriter, PrintWriter errWriter) {
        this(null, null, outWriter, errWriter, null, null);
    }

    Start(Context context, String programName,
            PrintWriter outWriter, PrintWriter errWriter,
            String docletName, ClassLoader classLoader) {
        this.context = context == null ? new Context() : context;
        String pname = programName == null ? ProgramName : programName;
        this.messager = (outWriter == null && errWriter == null)
                ? new Messager(this.context, pname)
                : new Messager(this.context, pname, outWriter, errWriter);
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
        usage("main.usage", OptionKind.STANDARD, "main.usage.foot");
    }

    @Override
    void Xusage() {
        usage("main.Xusage", OptionKind.EXTENDED, "main.Xusage.foot");
    }

    @Override
    void version() {
        messager.notice("javadoc.version", messager.programName, version("release"));
    }

    @Override
    void fullVersion() {
        messager.notice("javadoc.fullversion", messager.programName, version("full"));
    }

    private void usage(String headerKey, OptionKind kind, String footerKey) {
        messager.notice(headerKey);
        showToolOptions(kind);

        // let doclet print usage information
        if (docletClass != null) {
            String name = doclet.getName();
            messager.notice("main.doclet.usage.header", name);
            showDocletOptions(kind == OptionKind.EXTENDED
                    ? Option.Kind.EXTENDED
                    : Option.Kind.STANDARD);
        }
        if (footerKey != null)
            messager.notice(footerKey);
    }

    private static final String versionRBName = "jdk.javadoc.internal.tool.resources.version";
    private static ResourceBundle versionRB;

    private static String version(String key) {
        if (versionRB == null) {
            try {
                versionRB = ResourceBundle.getBundle(versionRBName);
            } catch (MissingResourceException e) {
                return Log.getLocalizedString("version.not.available");
            }
        }
        try {
            return versionRB.getString(key);
        } catch (MissingResourceException e) {
            return Log.getLocalizedString("version.not.available");
        }
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
                    .forEach(this::showToolOption);
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
                return collator.compare(o1.getNames().get(0), o2.getNames().get(0));
            }
        };

        doclet.getSupportedOptions().stream()
                .filter(opt -> opt.getKind() == kind)
                .sorted(comp)
                .forEach(this::showDocletOption);
    }

    void showDocletOption(Doclet.Option option) {
        List<String> names = option.getNames();
        String parameters;
        String optname = names.get(0);
        if (option.getArgumentCount() > 0 || optname.endsWith(":")) {
            String sep = optname.endsWith(":") ? "" : " ";
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
     * CLI compatibility, the execution is dispatched to the appropriate
     * Start mechanism, depending on the doclet variant.
     *
     * The doclet tests are performed in the begin method, further on,
     * this is to minimize argument processing and most importantly the impact
     * of class loader creation, needed to detect the doclet class variants.
     */
    @SuppressWarnings("deprecation")
    Result begin(String... argv) {
        // Preprocess @file arguments
        try {
            argv = CommandLine.parse(argv);
            return begin(Arrays.asList(argv), Collections.emptySet());
        } catch (IOException e) {
            error("main.cant.read", e.getMessage());
            return ERROR;
        }
    }

    // Called by 199 API.
    public boolean begin(Class<?> docletClass,
            Iterable<String> options,
            Iterable<? extends JavaFileObject> fileObjects) {
        this.docletClass = docletClass;
        List<String> opts = new ArrayList<>();
        for (String opt: options)
            opts.add(opt);

        return begin(opts, fileObjects).isOK();
    }

    @SuppressWarnings("removal")
    private Result begin(List<String> options, Iterable<? extends JavaFileObject> fileObjects) {
        fileManager = context.get(JavaFileManager.class);
        if (fileManager == null) {
            JavacFileManager.preRegister(context);
            fileManager = context.get(JavaFileManager.class);
            if (fileManager instanceof BaseFileManager) {
                ((BaseFileManager) fileManager).autoClose = true;
            }
        }

        // locale, doclet and maybe taglet, needs to be determined first
        try {
            docletClass = preprocess(fileManager, options);
        } catch (ToolException te) {
            if (!te.result.isOK()) {
                if (te.message != null) {
                    messager.printError(te.message);
                }
                Throwable t = te.getCause();
                dumpStack(t == null ? te : t);
            }
            return te.result;
        } catch (OptionException oe) {
            if (oe.message != null) {
                messager.printError(oe.message);
            }
            oe.m.run();
            Throwable t = oe.getCause();
            dumpStack(t == null ? oe : t);
            return oe.result;
        }
        if (jdk.javadoc.doclet.Doclet.class.isAssignableFrom(docletClass)) {
            // no need to dispatch to old, safe to init now
            initMessager();
            messager.setLocale(locale);
            try {
                Object o = docletClass.getConstructor().newInstance();
                doclet = (Doclet) o;
            } catch (ReflectiveOperationException exc) {
                if (apiMode) {
                    throw new ClientCodeException(exc);
                }
                error("main.could_not_instantiate_class", docletClass.getName());
                return ERROR;
            }
        } else {
            error("main.not_a_doclet", docletClass.getName());
            return ERROR;
        }

        Result result = OK;
        try {
            result = parseAndExecute(options, fileObjects);
        } catch (com.sun.tools.javac.main.Option.InvalidValueException e) {
            messager.printError(e.getMessage());
            Throwable t = e.getCause();
            dumpStack(t == null ? e : t);
            return ERROR;
        } catch (OptionException toe) {
            if (toe.message != null)
                messager.printError(toe.message);

            toe.m.run();
            Throwable t = toe.getCause();
            dumpStack(t == null ? toe : t);
            return toe.result;
        } catch (ToolException exc) {
            if (exc.message != null) {
                messager.printError(exc.message);
            }
            Throwable t = exc.getCause();
            if (result == ABNORMAL) {
                reportInternalError(t == null ? exc : t);
            } else {
                dumpStack(t == null ? exc : t);
            }
            return exc.result;
        } catch (OutOfMemoryError ee) {
            error("main.out.of.memory");
            result = SYSERR;
            dumpStack(ee);
        } catch (ClientCodeException e) {
            // simply rethrow these exceptions, to be caught and handled by JavadocTaskImpl
            throw e;
        } catch (Error | Exception ee) {
            error("main.fatal.error", ee);
            reportInternalError(ee);
            result = ABNORMAL;
        } finally {
            if (fileManager != null
                    && fileManager instanceof BaseFileManager
                    && ((BaseFileManager) fileManager).autoClose) {
                try {
                    fileManager.close();
                } catch (IOException ignore) {}
            }
            boolean haveErrorWarnings = messager.hasErrors()
                    || (rejectWarnings && messager.hasWarnings());
            if (!result.isOK() && !haveErrorWarnings) {
                // the doclet failed, but nothing reported, flag it!.
                error("main.unknown.error");
            }
            if (haveErrorWarnings && result.isOK()) {
                result = ERROR;
            }
            messager.printErrorWarningCounts();
            messager.flush();
        }
        return result;
    }

    private void reportInternalError(Throwable t) {
        messager.printErrorUsingKey("doclet.internal.report.bug");
        dumpStack(true, t);
    }

    private void dumpStack(Throwable t) {
        dumpStack(false, t);
    }

    private void dumpStack(boolean enabled, Throwable t) {
        if (t != null && (enabled || dumpOnError)) {
            t.printStackTrace(System.err);
        }
    }

    /**
     * Main program - internal
     */
    @SuppressWarnings("unchecked")
    private Result parseAndExecute(List<String> argList, Iterable<? extends JavaFileObject> fileObjects)
            throws ToolException, OptionException, com.sun.tools.javac.main.Option.InvalidValueException {
        long tm = System.currentTimeMillis();

        List<String> javaNames = new ArrayList<>();

        compOpts = Options.instance(context);

        // Make sure no obsolete source/target messages are reported
        try {
            com.sun.tools.javac.main.Option.XLINT_CUSTOM.process(getOptionHelper(), "-Xlint:-options");
        } catch (com.sun.tools.javac.main.Option.InvalidValueException ignore) {
        }

        Arguments arguments = Arguments.instance(context);
        arguments.init(ProgramName);
        arguments.allowEmpty();

        doclet.init(locale, messager);
        parseArgs(argList, javaNames);

        if (!arguments.handleReleaseOptions(extra -> true)) {
            // Arguments does not always increase the error count in the
            // case of errors, so increment the error count only if it has
            // not been updated previously, preventing complaints by callers
            if (!messager.hasErrors() && !messager.hasWarnings())
                messager.nerrors++;
            return CMDERR;
        }

        if (!arguments.validate()) {
            // Arguments does not always increase the error count in the
            // case of errors, so increment the error count only if it has
            // not been updated previously, preventing complaints by callers
            if (!messager.hasErrors() && !messager.hasWarnings())
                messager.nerrors++;
            return CMDERR;
        }

        if (fileManager instanceof BaseFileManager) {
            ((BaseFileManager) fileManager).handleOptions(fileManagerOpts);
        }

        if (fileManager.isSupportedOption(MULTIRELEASE.primaryName) == 1) {
            Target target = Target.instance(context);
            List<String> list = List.of(target.multiReleaseValue());
            fileManager.handleOption(MULTIRELEASE.primaryName, list.iterator());
        }
        compOpts.notifyListeners();
        List<String> modules = (List<String>) jdtoolOpts.computeIfAbsent(ToolOption.MODULE,
                s -> Collections.EMPTY_LIST);

        if (modules.isEmpty()) {
            List<String> subpkgs = (List<String>) jdtoolOpts.computeIfAbsent(ToolOption.SUBPACKAGES,
                    s -> Collections.EMPTY_LIST);
            if (subpkgs.isEmpty()) {
                if (javaNames.isEmpty() && isEmpty(fileObjects)) {
                    String text = messager.getText("main.No_modules_packages_or_classes_specified");
                    throw new ToolException(CMDERR, text);
                }
            }
        }

        JavadocTool comp = JavadocTool.make0(context);
        if (comp == null) return ABNORMAL;

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
        Result returnStatus = docEnv != null && doclet.run(docEnv)
                ? OK
                : ERROR;

        // We're done.
        if (compOpts.get("-verbose") != null) {
            tm = System.currentTimeMillis() - tm;
            messager.notice("main.done_in", Long.toString(tm));
        }

        return returnStatus;
    }

    boolean matches(List<String> names, String arg) {
        for (String name : names) {
            if (StringUtils.toLowerCase(name).equals(StringUtils.toLowerCase(arg)))
                return true;
        }
        return false;
    }

    boolean matches(Doclet.Option option, String arg) {
        if (matches(option.getNames(), arg))
             return true;
        int sep = arg.indexOf(':');
        String targ = arg.substring(0, sep + 1);
        return matches(option.getNames(), targ);
    }

    Set<? extends Doclet.Option> docletOptions = null;
    int handleDocletOptions(int idx, List<String> args, boolean isToolOption)
            throws OptionException {
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
        String text = null;
        for (Doclet.Option opt : docletOptions) {
            if (matches(opt, argBase)) {
                if (argVal != null) {
                    switch (opt.getArgumentCount()) {
                        case 0:
                            text = messager.getText("main.unnecessary_arg_provided", argBase);
                            throw new OptionException(ERROR, this::usage, text);
                        case 1:
                            opt.process(arg, Arrays.asList(argVal));
                            break;
                        default:
                            text = messager.getText("main.only_one_argument_with_equals", argBase);
                            throw new OptionException(ERROR, this::usage, text);
                    }
                } else {
                    if (args.size() - idx -1 < opt.getArgumentCount()) {
                        text = messager.getText("main.requires_argument", arg);
                        throw new OptionException(ERROR, this::usage, text);
                    }
                    opt.process(arg, args.subList(idx + 1, args.size()));
                    idx += opt.getArgumentCount();
                }
                return idx;
            }
        }
        // check if arg is accepted by the tool before emitting error
        if (!isToolOption) {
            text = messager.getText("main.invalid_flag", arg);
            throw new OptionException(ERROR, this::usage, text);
        }
        return idx;
    }

    private Class<?> preprocess(JavaFileManager jfm,
            List<String> argv) throws ToolException, OptionException {
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
            if (arg.equals(ToolOption.DUMPONERROR.primaryName)) {
                dumpOnError = true;
            } else if (arg.equals(ToolOption.LOCALE.primaryName)) {
                checkOneArg(argv, i++);
                String lname = argv.get(i);
                locale = getLocale(lname);
            } else if (arg.equals(ToolOption.DOCLET.primaryName)) {
                checkOneArg(argv, i++);
                if (userDocletName != null) {
                    if (apiMode) {
                        throw new IllegalArgumentException("More than one doclet specified (" +
                                userDocletName + " and " + argv.get(i) + ").");
                    }
                    String text = messager.getText("main.more_than_one_doclet_specified_0_and_1",
                            userDocletName, argv.get(i));
                    throw new ToolException(CMDERR, text);
                }
                if (docletName != null) {
                    if (apiMode) {
                        throw new IllegalArgumentException("More than one doclet specified (" +
                                docletName + " and " + argv.get(i) + ").");
                    }
                    String text = messager.getText("main.more_than_one_doclet_specified_0_and_1",
                            docletName, argv.get(i));
                    throw new ToolException(CMDERR, text);
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
                        if (apiMode) {
                            throw new IllegalArgumentException("Could not set location for " +
                                    userDocletPath, ioe);
                        }
                        String text = messager.getText("main.doclet_could_not_set_location",
                                userDocletPath);
                        throw new ToolException(CMDERR, text, ioe);
                    }
                }
                cl = fileManager.getClassLoader(DOCLET_PATH);
                if (cl == null) {
                    // despite doclet specified on cmdline no classloader found!
                    if (apiMode) {
                        throw new IllegalArgumentException("Could not obtain classloader to load "
                                + userDocletPath);
                    }
                    String text = messager.getText("main.doclet_no_classloader_found",
                            userDocletName);
                    throw new ToolException(CMDERR, text);
                }
            }
            try {
                return cl.loadClass(userDocletName);
            } catch (ClassNotFoundException cnfe) {
                if (apiMode) {
                    throw new IllegalArgumentException("Cannot find doclet class " + userDocletName,
                            cnfe);
                }
                String text = messager.getText("main.doclet_class_not_found", userDocletName);
                throw new ToolException(CMDERR, text, cnfe);
            } catch (NoClassDefFoundError ncfe) {
                if (ncfe.getMessage().contains("com/sun/javadoc/Doclet")) {
                    String text = messager.getText("main.not_a_doclet", userDocletName);
                    throw new ToolException(ERROR, text, ncfe);
                } else {
                    throw ncfe;
                }
            }
        }

        // Step 4: we have a doclet, try loading it
        if (docletName != null) {
            return loadDocletClass(docletName);
        }

        // finally
        return StdDoclet;
    }

    private Class<?> loadDocletClass(String docletName) throws ToolException {
        try {
            return Class.forName(docletName, true, getClass().getClassLoader());
        } catch (ClassNotFoundException cnfe) {
            if (apiMode) {
                throw new IllegalArgumentException("Cannot find doclet class " + docletName);
            }
            String text = messager.getText("main.doclet_class_not_found", docletName);
            throw new ToolException(CMDERR, text, cnfe);
        }
    }

    private void parseArgs(List<String> args, List<String> javaNames) throws ToolException,
            OptionException, com.sun.tools.javac.main.Option.InvalidValueException {
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
    private void checkOneArg(List<String> args, int index) throws OptionException {
        if ((index + 1) >= args.size() || args.get(index + 1).startsWith("-d")) {
            String text = messager.getText("main.requires_argument", args.get(index));
            throw new OptionException(CMDERR, this::usage, text);
        }
    }

    void error(String key, Object... args) {
        messager.printErrorUsingKey(key, args);
    }

    void warn(String key, Object... args)  {
        messager.printWarningUsingKey(key, args);
    }

    /**
     * Get the locale if specified on the command line
     * else return null and if locale option is not used
     * then return default locale.
     */
    private Locale getLocale(String localeName) throws ToolException {
        try {
            // Tolerate, at least for a while, the older syntax accepted by javadoc,
            // using _ as the separator
            localeName = localeName.replace("_", "-");
            Locale l =  new Locale.Builder().setLanguageTag(localeName).build();
            // Ensure that a non-empty language is available for the <HTML lang=...> element
            return (l.getLanguage().isEmpty()) ? Locale.ENGLISH : l;
        } catch (IllformedLocaleException e) {
            String text = messager.getText("main.malformed_locale_name", localeName);
            throw new ToolException(CMDERR, text);
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
        return new GrumpyHelper(messager) {
            @Override
            public String get(com.sun.tools.javac.main.Option option) {
                return compOpts.get(option);
            }

            @Override
            public void put(String name, String value) {
                compOpts.put(name, value);
            }

            @Override
            public void remove(String name) {
                compOpts.remove(name);
            }

            @Override
            public boolean handleFileManagerOption(com.sun.tools.javac.main.Option option, String value) {
                fileManagerOpts.put(option, value);
                return true;
            }
        };
    }

    @Override
    String getLocalizedMessage(String msg, Object... args) {
        return messager.getText(msg, args);
    }
}
