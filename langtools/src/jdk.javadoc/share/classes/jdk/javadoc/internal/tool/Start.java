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
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import static javax.tools.DocumentationTool.Location.*;

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
    /** Context for this invocation. */
    private final Context context;

    private static final String ProgramName = "javadoc";

    // meaning we allow all visibility of PROTECTED and PUBLIC
    private static final String defaultModifier = "protected";

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
                PrintWriter out = context.get(Log.outKey);
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
        usage("main.usage", "-help", null, exit);
    }

    @Override
    void Xusage() {
        Xusage(true);
    }

    void Xusage(boolean exit) {
        usage("main.Xusage", "-X", "main.Xusage.foot", exit);
    }

    private void usage(String main, String option, String foot, boolean exit) {
        messager.notice(main);
        // let doclet print usage information (does nothing on error)
        if (docletClass != null) {
            String name = doclet.getName();
            Set<Option> supportedOptions = doclet.getSupportedOptions();
            messager.notice("main.doclet.usage.header", name);
            Option.Kind myKind = option.equals("-X")
                    ? Option.Kind.EXTENDED
                    : Option.Kind.STANDARD;
            supportedOptions.stream()
                    .filter(opt -> opt.getKind() == myKind)
                    .forEach(opt -> messager.printNotice(opt.toString()));
        }
        if (foot != null)
            messager.notice(foot);

        if (exit) exit();
    }

    /**
     * Exit
     */
    private void exit() {
        messager.exit();
    }

    /**
     * Main program - external wrapper
     */
    int begin(String... argv) {
        // Preprocess @file arguments
        try {
            argv = CommandLine.parse(argv);
        } catch (FileNotFoundException e) {
            messager.error("main.cant.read", e.getMessage());
            exit();
        } catch (IOException e) {
            e.printStackTrace(System.err);
            exit();
        }

        List<String> argList = Arrays.asList(argv);
        boolean ok = begin(argList, Collections.<JavaFileObject> emptySet());
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

    private boolean begin(List<String> options, Iterable<? extends JavaFileObject> fileObjects) {

        fileManager = context.get(JavaFileManager.class);
        if (fileManager == null) {
            JavacFileManager.preRegister(context);
            fileManager = context.get(JavaFileManager.class);
            if (fileManager instanceof BaseFileManager) {
                ((BaseFileManager) fileManager).autoClose = true;
            }
        }
        // locale and doclet needs to be determined first
        docletClass = preProcess(fileManager, options);

        if (jdk.javadoc.doclet.Doclet.class.isAssignableFrom(docletClass)) {
            // no need to dispatch to old, safe to init now
            initMessager();
            messager.setLocale(locale);
            try {
                doclet = (Doclet) docletClass.newInstance();
            } catch (InstantiationException | IllegalAccessException exc) {
                exc.printStackTrace();
                if (!apiMode) {
                    error("main.could_not_instantiate_class", docletClass);
                    messager.exit();
                }
                throw new ClientCodeException(exc);
            }
        } else {
            if (this.apiMode) {
                com.sun.tools.javadoc.Start ostart
                        = new com.sun.tools.javadoc.Start(context);
                return ostart.begin(docletClass, options, fileObjects);
            }
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
     * Ensures that the module of the given class is readable to this
     * module.
     * @param targetClass class in module to be made readable
     */
    private void ensureReadable(Class<?> targetClass) {
        try {
            Method getModuleMethod = Class.class.getMethod("getModule");
            Object thisModule = getModuleMethod.invoke(this.getClass());
            Object targetModule = getModuleMethod.invoke(targetClass);

            Class<?> moduleClass = getModuleMethod.getReturnType();
            Method addReadsMethod = moduleClass.getMethod("addReads", moduleClass);
            addReadsMethod.invoke(thisModule, targetModule);
        } catch (NoSuchMethodException e) {
            // ignore
        } catch (Exception e) {
            throw new InternalError(e);
        }
    }

    /**
     * Main program - internal
     */
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

        String platformString = compOpts.get("-release");

        if (platformString != null) {
            if (compOpts.isSet("-source")) {
                usageError("main.release.bootclasspath.conflict", "-source");
            }
            if (fileManagerOpts.containsKey(BOOTCLASSPATH)) {
                usageError("main.release.bootclasspath.conflict", BOOTCLASSPATH.getText());
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

        if (javaNames.isEmpty() && subPackages.isEmpty() && isEmpty(fileObjects)) {
            usageError("main.No_packages_or_classes_specified");
        }

        JavadocTool comp = JavadocTool.make0(context);
        if (comp == null) return false;

        if (showAccess == null) {
            setFilter(defaultModifier);
        }

        DocletEnvironment root = comp.getEnvironment(
                encoding,
                showAccess,
                overviewpath,
                javaNames,
                fileObjects,
                subPackages,
                excludedPackages,
                docClasses,
                quiet);

        // release resources
        comp = null;

        if (breakiterator || !locale.getLanguage().equals(Locale.ENGLISH.getLanguage())) {
            JavacTrees trees = JavacTrees.instance(context);
            trees.setBreakIterator(BreakIterator.getSentenceInstance(locale));
        }
        // pass off control to the doclet
        boolean ok = root != null;
        if (ok) ok = doclet.run(root);

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

        for (Doclet.Option opt : docletOptions) {
            if (opt.matches(arg)) {
                if (args.size() - idx < opt.getArgumentCount()) {
                    usageError("main.requires_argument", arg);
                }
                opt.process(arg, args.listIterator(idx + 1));
                idx += opt.getArgumentCount();
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

        // Step 1: loop through the args, set locale early on, if found.
        for (int i = 0 ; i < argv.size() ; i++) {
            String arg = argv.get(i);
            if (arg.equals(ToolOption.LOCALE.opt)) {
                oneArg(argv, i++);
                String lname = argv.get(i);
                locale = getLocale(lname);
            } else if (arg.equals(ToolOption.DOCLET.opt)) {
                oneArg(argv, i++);
                if (userDocletName != null) {
                    usageError("main.more_than_one_doclet_specified_0_and_1",
                               userDocletName, argv.get(i));
                }
                if (docletName != null) {
                    usageError("main.more_than_one_doclet_specified_0_and_1",
                            docletName, argv.get(i));
                }
                userDocletName = argv.get(i);
            } else if (arg.equals(ToolOption.DOCLETPATH.opt)) {
                oneArg(argv, i++);
                if (userDocletPath == null) {
                    userDocletPath = argv.get(i);
                } else {
                    userDocletPath += File.pathSeparator + argv.get(i);
                }
            }
        }
        // Step 2: a doclet has already been provided,
        // nothing more to do.
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
                        panic("main.doclet_no_classloader_found", ioe);
                        return null; // keep compiler happy
                    }
                }
                cl = fileManager.getClassLoader(DOCLET_PATH);
                if (cl == null) {
                    // despite doclet specified on cmdline no classloader found!
                    panic("main.doclet_no_classloader_found", userDocletName);
                    return null; // keep compiler happy
                }
                try {
                    Class<?> klass = cl.loadClass(userDocletName);
                    ensureReadable(klass);
                    return klass;
                } catch (ClassNotFoundException cnfe) {
                    panic("main.doclet_class_not_found", userDocletName);
                    return null; // keep compiler happy
                }
            }
        }
        // Step 4: we have a doclet, try loading it, otherwise
        // return back the standard doclet
        if (docletName != null) {
            try {
                return Class.forName(docletName, true, getClass().getClassLoader());
            } catch (ClassNotFoundException cnfe) {
                panic("main.doclet_class_not_found", userDocletName);
                return null; // happy compiler, should not happen
            }
        } else {
            return jdk.javadoc.internal.doclets.standard.Standard.class;
        }
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
                    oneArg(args, i++);
                    o.process(this, args.get(i));
                } else if (o.hasSuffix) {
                    o.process(this, arg);
                } else {
                    setOption(arg);
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
     * Set one arg option.
     * Error and exit if one argument is not provided.
     */
    private void oneArg(List<String> args, int index) {
        if ((index + 1) < args.size()) {
            setOption(args.get(index), args.get(index+1));
        } else {
            usageError("main.requires_argument", args.get(index));
        }
    }

    @Override
    void usageError(String key, Object... args) {
        error(key, args);
        usage(true);
    }

    // a terminal call, will not return
    void panic(String key, Object... args) {
        error(key, args);
        messager.exit();
    }

    void error(String key, Object... args) {
        messager.error(key, args);
    }

    /**
     * indicate an option with no arguments was given.
     */
    private void setOption(String opt) {
        String[] option = { opt };
        options.add(Arrays.asList(option));
    }

    /**
     * indicate an option with one argument was given.
     */
    private void setOption(String opt, String argument) {
        String[] option = { opt, argument };
        options.add(Arrays.asList(option));
    }

    /**
     * indicate an option with the specified list of arguments was given.
     */
    private void setOption(String opt, List<String> arguments) {
        List<String> args = new ArrayList<>(arguments.size() + 1);
        args.add(opt);
        args.addAll(arguments);
        options.add(args);
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
