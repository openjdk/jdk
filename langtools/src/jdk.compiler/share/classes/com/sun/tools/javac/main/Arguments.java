/*
 * Copyright (c) 1999, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.tools.javac.main;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;

import com.sun.tools.doclint.DocLint;
import com.sun.tools.javac.code.Lint.LintCategory;
import com.sun.tools.javac.code.Source;
import com.sun.tools.javac.file.BaseFileManager;
import com.sun.tools.javac.file.JavacFileManager;
import com.sun.tools.javac.jvm.Profile;
import com.sun.tools.javac.jvm.Target;
import com.sun.tools.javac.main.OptionHelper.GrumpyHelper;
import com.sun.tools.javac.platform.PlatformDescription;
import com.sun.tools.javac.platform.PlatformUtils;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;
import com.sun.tools.javac.util.Log;
import com.sun.tools.javac.util.Log.PrefixKind;
import com.sun.tools.javac.util.Log.WriterKind;
import com.sun.tools.javac.util.Options;
import com.sun.tools.javac.util.PropagatedException;

/**
 * Shared option and argument handling for command line and API usage of javac.
 */
public class Arguments {

    /**
     * The context key for the arguments.
     */
    public static final Context.Key<Arguments> argsKey = new Context.Key<>();

    private String ownName;
    private Set<String> classNames;
    private Set<File> files;
    private Map<Option, String> deferredFileManagerOptions;
    private Set<JavaFileObject> fileObjects;
    private final Options options;

    private JavaFileManager fileManager;
    private final Log log;
    private final Context context;

    private enum ErrorMode { ILLEGAL_ARGUMENT, ILLEGAL_STATE, LOG };
    private ErrorMode errorMode;
    private boolean errors;

    /**
     * Gets the Arguments instance for this context.
     *
     * @param context the content
     * @return the Arguments instance for this context.
     */
    public static Arguments instance(Context context) {
        Arguments instance = context.get(argsKey);
        if (instance == null) {
            instance = new Arguments(context);
        }
        return instance;
    }

    protected Arguments(Context context) {
        context.put(argsKey, this);
        options = Options.instance(context);
        log = Log.instance(context);
        this.context = context;

        // Ideally, we could init this here and update/configure it as
        // needed, but right now, initializing a file manager triggers
        // initialization of other items in the context, such as Lint
        // and FSInfo, which should not be initialized until after
        // processArgs
        //        fileManager = context.get(JavaFileManager.class);
    }

    private final OptionHelper cmdLineHelper = new OptionHelper() {
        @Override
        public String get(Option option) {
            return options.get(option);
        }

        @Override
        public void put(String name, String value) {
            options.put(name, value);
        }

        @Override
        public void remove(String name) {
            options.remove(name);
        }

        @Override
        public boolean handleFileManagerOption(Option option, String value) {
            options.put(option.getText(), value);
            deferredFileManagerOptions.put(option, value);
            return true;
        }

        @Override
        public Log getLog() {
            return log;
        }

        @Override
        public String getOwnName() {
            return ownName;
        }

        @Override
        public void error(String key, Object... args) {
            Arguments.this.error(key, args);
        }

        @Override
        public void addFile(File f) {
            files.add(f);
        }

        @Override
        public void addClassName(String s) {
            classNames.add(s);
        }

    };

    /**
     * Initializes this Args instance with a set of command line args.
     * The args will be processed in conjunction with the full set of
     * command line options, including -help, -version etc.
     * The args may also contain class names and filenames.
     * Any errors during this call, and later during validate, will be reported
     * to the log.
     * @param ownName the name of this tool; used to prefix messages
     * @param args the args to be processed
     */
    public void init(String ownName, String... args) {
        this.ownName = ownName;
        errorMode = ErrorMode.LOG;
        files = new LinkedHashSet<>();
        deferredFileManagerOptions = new LinkedHashMap<>();
        fileObjects = null;
        classNames = new LinkedHashSet<>();
        processArgs(List.from(args), Option.getJavaCompilerOptions(), cmdLineHelper, true, false);
    }

    private final OptionHelper apiHelper = new GrumpyHelper(null) {
        @Override
        public String get(Option option) {
            return options.get(option.getText());
        }

        @Override
        public void put(String name, String value) {
            options.put(name, value);
        }

        @Override
        public void remove(String name) {
            options.remove(name);
        }

        @Override
        public void error(String key, Object... args) {
            Arguments.this.error(key, args);
        }

        @Override
        public Log getLog() {
            return Arguments.this.log;
        }
    };

    /**
     * Initializes this Args instance with the parameters for a JavacTask.
     * The options will be processed in conjunction with the restricted set
     * of tool options, which does not include -help, -version, etc,
     * nor does it include classes and filenames, which should be specified
     * separately.
     * File manager options are handled directly by the file manager.
     * Any errors found while processing individual args will be reported
     * via IllegalArgumentException.
     * Any subsequent errors during validate will be reported via IllegalStateException.
     * @param ownName the name of this tool; used to prefix messages
     * @param options the options to be processed
     * @param classNames the classes to be subject to annotation processing
     * @param files the files to be compiled
     */
    public void init(String ownName,
            Iterable<String> options,
            Iterable<String> classNames,
            Iterable<? extends JavaFileObject> files) {
        this.ownName = ownName;
        this.classNames = toSet(classNames);
        this.fileObjects = toSet(files);
        this.files = null;
        errorMode = ErrorMode.ILLEGAL_ARGUMENT;
        if (options != null) {
            processArgs(toList(options), Option.getJavacToolOptions(), apiHelper, false, true);
        }
        errorMode = ErrorMode.ILLEGAL_STATE;
    }

    /**
     * Gets the files to be compiled.
     * @return the files to be compiled
     */
    public Set<JavaFileObject> getFileObjects() {
        if (fileObjects == null) {
            if (files == null) {
                fileObjects = Collections.emptySet();
            } else {
                fileObjects = new LinkedHashSet<>();
                JavacFileManager jfm = (JavacFileManager) getFileManager();
                for (JavaFileObject fo: jfm.getJavaFileObjectsFromFiles(files))
                    fileObjects.add(fo);
            }
        }
        return fileObjects;
    }

    /**
     * Gets the classes to be subject to annotation processing.
     * @return the classes to be subject to annotation processing
     */
    public Set<String> getClassNames() {
        return classNames;
    }

    /**
     * Processes strings containing options and operands.
     * @param args the strings to be processed
     * @param allowableOpts the set of option declarations that are applicable
     * @param helper a help for use by Option.process
     * @param allowOperands whether or not to check for files and classes
     * @param checkFileManager whether or not to check if the file manager can handle
     *      options which are not recognized by any of allowableOpts
     * @return true if all the strings were successfully processed; false otherwise
     * @throws IllegalArgumentException if a problem occurs and errorMode is set to
     *      ILLEGAL_ARGUMENT
     */
    private boolean processArgs(Iterable<String> args,
            Set<Option> allowableOpts, OptionHelper helper,
            boolean allowOperands, boolean checkFileManager) {
        if (!doProcessArgs(args, allowableOpts, helper, allowOperands, checkFileManager))
            return false;

        String platformString = options.get(Option.RELEASE);

        checkOptionAllowed(platformString == null,
                option -> error("err.release.bootclasspath.conflict", option.getText()),
                Option.BOOTCLASSPATH, Option.XBOOTCLASSPATH, Option.XBOOTCLASSPATH_APPEND,
                Option.XBOOTCLASSPATH_PREPEND, Option.ENDORSEDDIRS, Option.EXTDIRS, Option.SOURCE,
                Option.TARGET);

        if (platformString != null) {
            PlatformDescription platformDescription = PlatformUtils.lookupPlatformDescription(platformString);

            if (platformDescription == null) {
                error("err.unsupported.release.version", platformString);
                return false;
            }

            options.put(Option.SOURCE, platformDescription.getSourceVersion());
            options.put(Option.TARGET, platformDescription.getTargetVersion());

            context.put(PlatformDescription.class, platformDescription);

            if (!doProcessArgs(platformDescription.getAdditionalOptions(), allowableOpts, helper, allowOperands, checkFileManager))
                return false;

            Collection<Path> platformCP = platformDescription.getPlatformPath();

            if (platformCP != null) {
                JavaFileManager fm = getFileManager();

                if (!(fm instanceof StandardJavaFileManager)) {
                    error("err.release.not.standard.file.manager");
                    return false;
                }

                try {
                    StandardJavaFileManager sfm = (StandardJavaFileManager) fm;

                    sfm.setLocationFromPaths(StandardLocation.PLATFORM_CLASS_PATH, platformCP);
                } catch (IOException ex) {
                    log.printLines(PrefixKind.JAVAC, "msg.io");
                    ex.printStackTrace(log.getWriter(WriterKind.NOTICE));
                    return false;
                }
            }
        }

        options.notifyListeners();

        return true;
    }

    private boolean doProcessArgs(Iterable<String> args,
            Set<Option> allowableOpts, OptionHelper helper,
            boolean allowOperands, boolean checkFileManager) {
        JavaFileManager fm = checkFileManager ? getFileManager() : null;
        Iterator<String> argIter = args.iterator();
        while (argIter.hasNext()) {
            String arg = argIter.next();
            if (arg.isEmpty()) {
                error("err.invalid.flag", arg);
                return false;
            }

            Option option = null;
            if (arg.startsWith("-")) {
                for (Option o : allowableOpts) {
                    if (o.matches(arg)) {
                        option = o;
                        break;
                    }
                }
            } else if (allowOperands && Option.SOURCEFILE.matches(arg)) {
                option = Option.SOURCEFILE;
            }

            if (option == null) {
                if (fm != null && fm.handleOption(arg, argIter)) {
                    continue;
                }
                error("err.invalid.flag", arg);
                return false;
            }

            if (option.hasArg()) {
                if (!argIter.hasNext()) {
                    error("err.req.arg", arg);
                    return false;
                }
                String operand = argIter.next();
                if (option.process(helper, arg, operand)) {
                    return false;
                }
            } else {
                if (option.process(helper, arg)) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Validates the overall consistency of the options and operands
     * processed by processOptions.
     * @return true if all args are successfully validating; false otherwise.
     * @throws IllegalStateException if a problem is found and errorMode is set to
     *      ILLEGAL_STATE
     */
    public boolean validate() {
        if (isEmpty()) {
            // It is allowed to compile nothing if just asking for help or version info.
            // But also note that none of these options are supported in API mode.
            if (options.isSet(Option.HELP)
                || options.isSet(Option.X)
                || options.isSet(Option.VERSION)
                || options.isSet(Option.FULLVERSION))
                return true;

            if (JavaCompiler.explicitAnnotationProcessingRequested(options)) {
                error("err.no.source.files.classes");
            } else {
                error("err.no.source.files");
            }
            return false;
        }

        if (!checkDirectory(Option.D)) {
            return false;
        }
        if (!checkDirectory(Option.S)) {
            return false;
        }

        String sourceString = options.get(Option.SOURCE);
        Source source = (sourceString != null)
                ? Source.lookup(sourceString)
                : Source.DEFAULT;
        String targetString = options.get(Option.TARGET);
        Target target = (targetString != null)
                ? Target.lookup(targetString)
                : Target.DEFAULT;

        // We don't check source/target consistency for CLDC, as J2ME
        // profiles are not aligned with J2SE targets; moreover, a
        // single CLDC target may have many profiles.  In addition,
        // this is needed for the continued functioning of the JSR14
        // prototype.
        if (Character.isDigit(target.name.charAt(0))) {
            if (target.compareTo(source.requiredTarget()) < 0) {
                if (targetString != null) {
                    if (sourceString == null) {
                        error("warn.target.default.source.conflict",
                                targetString,
                                source.requiredTarget().name);
                    } else {
                        error("warn.source.target.conflict",
                                sourceString,
                                source.requiredTarget().name);
                    }
                    return false;
                } else {
                    target = source.requiredTarget();
                    options.put("-target", target.name);
                }
            }
        }

        String profileString = options.get(Option.PROFILE);
        if (profileString != null) {
            Profile profile = Profile.lookup(profileString);
            if (!profile.isValid(target)) {
                error("warn.profile.target.conflict", profileString, target.name);
            }

            // This check is only effective in command line mode,
            // where the file manager options are added to options
            if (options.get(Option.BOOTCLASSPATH) != null) {
                error("err.profile.bootclasspath.conflict");
            }
        }

        boolean lintOptions = options.isUnset(Option.XLINT_CUSTOM, "-" + LintCategory.OPTIONS.option);

        if (lintOptions && source.compareTo(Source.DEFAULT) < 0 && !options.isSet(Option.RELEASE)) {
            JavaFileManager fm = getFileManager();
            if (fm instanceof BaseFileManager) {
                if (((BaseFileManager) fm).isDefaultBootClassPath())
                    log.warning(LintCategory.OPTIONS, "source.no.bootclasspath", source.name);
            }
        }

        boolean obsoleteOptionFound = false;

        if (source.compareTo(Source.MIN) < 0) {
            log.error("option.removed.source", source.name, Source.MIN.name);
        } else if (source == Source.MIN && lintOptions) {
            log.warning(LintCategory.OPTIONS, "option.obsolete.source", source.name);
            obsoleteOptionFound = true;
        }

        if (target.compareTo(Target.MIN) < 0) {
            log.error("option.removed.target", target.name, Target.MIN.name);
        } else if (target == Target.MIN && lintOptions) {
            log.warning(LintCategory.OPTIONS, "option.obsolete.target", target.name);
            obsoleteOptionFound = true;
        }

        if (obsoleteOptionFound)
            log.warning(LintCategory.OPTIONS, "option.obsolete.suppression");

        return !errors;
    }

    /**
     * Returns true if there are no files or classes specified for use.
     * @return true if there are no files or classes specified for use
     */
    public boolean isEmpty() {
        return ((files == null) || files.isEmpty())
                && ((fileObjects == null) || fileObjects.isEmpty())
                && classNames.isEmpty();
    }

    /**
     * Gets the file manager options which may have been deferred
     * during processArgs.
     * @return the deferred file manager options
     */
    public Map<Option, String> getDeferredFileManagerOptions() {
        return deferredFileManagerOptions;
    }

    /**
     * Gets any options specifying plugins to be run.
     * @return options for plugins
     */
    public Set<List<String>> getPluginOpts() {
        String plugins = options.get(Option.PLUGIN);
        if (plugins == null)
            return Collections.emptySet();

        Set<List<String>> pluginOpts = new LinkedHashSet<>();
        for (String plugin: plugins.split("\\x00")) {
            pluginOpts.add(List.from(plugin.split("\\s+")));
        }
        return Collections.unmodifiableSet(pluginOpts);
    }

    /**
     * Gets any options specifying how doclint should be run.
     * An empty list is returned if no doclint options are specified
     * or if the only doclint option is -Xdoclint:none.
     * @return options for doclint
     */
    public List<String> getDocLintOpts() {
        String xdoclint = options.get(Option.XDOCLINT);
        String xdoclintCustom = options.get(Option.XDOCLINT_CUSTOM);
        if (xdoclint == null && xdoclintCustom == null)
            return List.nil();

        Set<String> doclintOpts = new LinkedHashSet<>();
        if (xdoclint != null)
            doclintOpts.add(DocLint.XMSGS_OPTION);
        if (xdoclintCustom != null) {
            for (String s: xdoclintCustom.split("\\s+")) {
                if (s.isEmpty())
                    continue;
                doclintOpts.add(s.replace(Option.XDOCLINT_CUSTOM.text, DocLint.XMSGS_CUSTOM_PREFIX));
            }
        }

        if (doclintOpts.equals(Collections.singleton(DocLint.XMSGS_CUSTOM_PREFIX + "none")))
            return List.nil();

        String checkPackages = options.get(Option.XDOCLINT_PACKAGE);

        if (checkPackages != null) {
            for (String s : checkPackages.split("\\s+")) {
                doclintOpts.add(s.replace(Option.XDOCLINT_PACKAGE.text, DocLint.XCHECK_PACKAGE));
            }
        }

        // standard doclet normally generates H1, H2,
        // so for now, allow user comments to assume that
        doclintOpts.add(DocLint.XIMPLICIT_HEADERS + "2");

        return List.from(doclintOpts.toArray(new String[doclintOpts.size()]));
    }

    private boolean checkDirectory(Option option) {
        String value = options.get(option);
        if (value == null) {
            return true;
        }
        File file = new File(value);
        if (!file.exists()) {
            error("err.dir.not.found", value);
            return false;
        }
        if (!file.isDirectory()) {
            error("err.file.not.directory", value);
            return false;
        }
        return true;
    }

    private interface ErrorReporter {
        void report(Option o);
    }

    void checkOptionAllowed(boolean allowed, ErrorReporter r, Option... opts) {
        if (!allowed) {
            Stream.of(opts)
                  .filter(options :: isSet)
                  .forEach(r :: report);
        }
    }

    void error(String key, Object... args) {
        errors = true;
        switch (errorMode) {
            case ILLEGAL_ARGUMENT: {
                String msg = log.localize(PrefixKind.JAVAC, key, args);
                throw new PropagatedException(new IllegalArgumentException(msg));
            }
            case ILLEGAL_STATE: {
                String msg = log.localize(PrefixKind.JAVAC, key, args);
                throw new PropagatedException(new IllegalStateException(msg));
            }
            case LOG:
                report(key, args);
                log.printLines(PrefixKind.JAVAC, "msg.usage", ownName);
        }
    }

    void warning(String key, Object... args) {
        report(key, args);
    }

    private void report(String key, Object... args) {
        log.printRawLines(ownName + ": " + log.localize(PrefixKind.JAVAC, key, args));
    }

    private JavaFileManager getFileManager() {
        if (fileManager == null)
            fileManager = context.get(JavaFileManager.class);
        return fileManager;
    }

    <T> ListBuffer<T> toList(Iterable<? extends T> items) {
        ListBuffer<T> list = new ListBuffer<>();
        if (items != null) {
            for (T item : items) {
                list.add(item);
            }
        }
        return list;
    }

    <T> Set<T> toSet(Iterable<? extends T> items) {
        Set<T> set = new LinkedHashSet<>();
        if (items != null) {
            for (T item : items) {
                set.add(item);
            }
        }
        return set;
    }
}
