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
import java.io.PrintWriter;
import java.net.URL;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

import javax.annotation.processing.Processor;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;

import com.sun.source.util.JavacTask;
import com.sun.source.util.Plugin;
import com.sun.tools.doclint.DocLint;
import com.sun.tools.javac.api.BasicJavacTask;
import com.sun.tools.javac.code.Source;
import com.sun.tools.javac.file.CacheFSInfo;
import com.sun.tools.javac.file.JavacFileManager;
import com.sun.tools.javac.jvm.Profile;
import com.sun.tools.javac.jvm.Target;
import com.sun.tools.javac.processing.AnnotationProcessingError;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.util.*;
import com.sun.tools.javac.util.Log.PrefixKind;
import com.sun.tools.javac.util.Log.WriterKind;
import com.sun.tools.javac.util.ServiceLoader;
import static com.sun.tools.javac.main.Option.*;

/** This class provides a command line interface to the javac compiler.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class Main {

    /** The name of the compiler, for use in diagnostics.
     */
    String ownName;

    /** The writer to use for diagnostic output.
     */
    PrintWriter out;

    /** The log to use for diagnostic output.
     */
    public Log log;

    /**
     * If true, certain errors will cause an exception, such as command line
     * arg errors, or exceptions in user provided code.
     */
    boolean apiMode;


    /** Result codes.
     */
    public enum Result {
        OK(0),        // Compilation completed with no errors.
        ERROR(1),     // Completed but reported errors.
        CMDERR(2),    // Bad command-line arguments
        SYSERR(3),    // System error or resource exhaustion.
        ABNORMAL(4);  // Compiler terminated abnormally

        Result(int exitCode) {
            this.exitCode = exitCode;
        }

        public boolean isOK() {
            return (exitCode == 0);
        }

        public final int exitCode;
    }

    private Option[] recognizedOptions =
            Option.getJavaCompilerOptions().toArray(new Option[0]);

    private OptionHelper optionHelper = new OptionHelper() {
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
        public Log getLog() {
            return log;
        }

        @Override
        public String getOwnName() {
            return ownName;
        }

        @Override
        public void error(String key, Object... args) {
            Main.this.error(key, args);
        }

        @Override
        public void addFile(File f) {
            filenames.add(f);
        }

        @Override
        public void addClassName(String s) {
            classnames.append(s);
        }

    };

    /**
     * Construct a compiler instance.
     */
    public Main(String name) {
        this(name, new PrintWriter(System.err, true));
    }

    /**
     * Construct a compiler instance.
     */
    public Main(String name, PrintWriter out) {
        this.ownName = name;
        this.out = out;
    }

    /** A table of all options that's passed to the JavaCompiler constructor.  */
    private Options options = null;

    /** The list of source files to process
     */
    public Set<File> filenames = null; // XXX sb protected

    /** List of class files names passed on the command line
     */
    public ListBuffer<String> classnames = null; // XXX sb protected

    /** Report a usage error.
     */
    void error(String key, Object... args) {
        if (apiMode) {
            String msg = log.localize(PrefixKind.JAVAC, key, args);
            throw new PropagatedException(new IllegalStateException(msg));
        }
        warning(key, args);
        log.printLines(PrefixKind.JAVAC, "msg.usage", ownName);
    }

    /** Report a warning.
     */
    void warning(String key, Object... args) {
        log.printRawLines(ownName + ": " + log.localize(PrefixKind.JAVAC, key, args));
    }

    public Option getOption(String flag) {
        for (Option option : recognizedOptions) {
            if (option.matches(flag))
                return option;
        }
        return null;
    }

    public void setOptions(Options options) {
        if (options == null)
            throw new NullPointerException();
        this.options = options;
    }

    public void setAPIMode(boolean apiMode) {
        this.apiMode = apiMode;
    }

    /** Process command line arguments: store all command line options
     *  in `options' table and return all source filenames.
     *  @param flags    The array of command line arguments.
     */
    public Collection<File> processArgs(String[] flags) { // XXX sb protected
        return processArgs(flags, null);
    }

    public Collection<File> processArgs(String[] flags, String[] classNames) { // XXX sb protected
        int ac = 0;
        while (ac < flags.length) {
            String flag = flags[ac];
            ac++;

            Option option = null;

            if (flag.length() > 0) {
                // quick hack to speed up file processing:
                // if the option does not begin with '-', there is no need to check
                // most of the compiler options.
                int firstOptionToCheck = flag.charAt(0) == '-' ? 0 : recognizedOptions.length-1;
                for (int j=firstOptionToCheck; j<recognizedOptions.length; j++) {
                    if (recognizedOptions[j].matches(flag)) {
                        option = recognizedOptions[j];
                        break;
                    }
                }
            }

            if (option == null) {
                error("err.invalid.flag", flag);
                return null;
            }

            if (option.hasArg()) {
                if (ac == flags.length) {
                    error("err.req.arg", flag);
                    return null;
                }
                String operand = flags[ac];
                ac++;
                if (option.process(optionHelper, flag, operand))
                    return null;
            } else {
                if (option.process(optionHelper, flag))
                    return null;
            }
        }

        if (options.get(PROFILE) != null && options.get(BOOTCLASSPATH) != null) {
            error("err.profile.bootclasspath.conflict");
            return null;
        }

        if (this.classnames != null && classNames != null) {
            this.classnames.addAll(Arrays.asList(classNames));
        }

        if (!checkDirectory(D))
            return null;
        if (!checkDirectory(S))
            return null;

        String sourceString = options.get(SOURCE);
        Source source = (sourceString != null)
            ? Source.lookup(sourceString)
            : Source.DEFAULT;
        String targetString = options.get(TARGET);
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
                        warning("warn.target.default.source.conflict",
                                targetString,
                                source.requiredTarget().name);
                    } else {
                        warning("warn.source.target.conflict",
                                sourceString,
                                source.requiredTarget().name);
                    }
                    return null;
                } else {
                    target = source.requiredTarget();
                    options.put("-target", target.name);
                }
            } else {
                if (targetString == null && !source.allowGenerics()) {
                    target = Target.JDK1_4;
                    options.put("-target", target.name);
                }
            }
        }

        String profileString = options.get(PROFILE);
        if (profileString != null) {
            Profile profile = Profile.lookup(profileString);
            if (!profile.isValid(target)) {
                warning("warn.profile.target.conflict", profileString, target.name);
                return null;
            }
        }

        // handle this here so it works even if no other options given
        String showClass = options.get("showClass");
        if (showClass != null) {
            if (showClass.equals("showClass")) // no value given for option
                showClass = "com.sun.tools.javac.Main";
            showClass(showClass);
        }

        options.notifyListeners();

        return filenames;
    }
    // where
        private boolean checkDirectory(Option option) {
            String value = options.get(option);
            if (value == null)
                return true;
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

    /** Programmatic interface for main function.
     * @param args    The command line parameters.
     */
    public Result compile(String[] args) {
        Context context = new Context();
        JavacFileManager.preRegister(context); // can't create it until Log has been set up
        Result result = compile(args, context);
        if (fileManager instanceof JavacFileManager) {
            // A fresh context was created above, so jfm must be a JavacFileManager
            ((JavacFileManager)fileManager).close();
        }
        return result;
    }

    public Result compile(String[] args, Context context) {
        return compile(args, context, List.<JavaFileObject>nil(), null);
    }

    /** Programmatic interface for main function.
     * @param args    The command line parameters.
     */
    public Result compile(String[] args,
                       Context context,
                       List<JavaFileObject> fileObjects,
                       Iterable<? extends Processor> processors)
    {
        return compile(args,  null, context, fileObjects, processors);
    }

    public Result compile(String[] args,
                          String[] classNames,
                          Context context,
                          List<JavaFileObject> fileObjects,
                          Iterable<? extends Processor> processors)
    {
        context.put(Log.outKey, out);
        log = Log.instance(context);

        if (options == null)
            options = Options.instance(context); // creates a new one

        filenames = new LinkedHashSet<>();
        classnames = new ListBuffer<>();
        JavaCompiler comp = null;
        /*
         * TODO: Logic below about what is an acceptable command line
         * should be updated to take annotation processing semantics
         * into account.
         */
        try {
            if (args.length == 0
                    && (classNames == null || classNames.length == 0)
                    && fileObjects.isEmpty()) {
                Option.HELP.process(optionHelper, "-help");
                return Result.CMDERR;
            }

            Collection<File> files;
            try {
                files = processArgs(CommandLine.parse(args), classNames);
                if (files == null) {
                    // null signals an error in options, abort
                    return Result.CMDERR;
                } else if (files.isEmpty() && fileObjects.isEmpty() && classnames.isEmpty()) {
                    // it is allowed to compile nothing if just asking for help or version info
                    if (options.isSet(HELP)
                        || options.isSet(X)
                        || options.isSet(VERSION)
                        || options.isSet(FULLVERSION))
                        return Result.OK;
                    if (JavaCompiler.explicitAnnotationProcessingRequested(options)) {
                        error("err.no.source.files.classes");
                    } else {
                        error("err.no.source.files");
                    }
                    return Result.CMDERR;
                }
            } catch (java.io.FileNotFoundException e) {
                warning("err.file.not.found", e.getMessage());
                return Result.SYSERR;
            }

            boolean forceStdOut = options.isSet("stdout");
            if (forceStdOut) {
                log.flush();
                log.setWriters(new PrintWriter(System.out, true));
            }

            // allow System property in following line as a Mustang legacy
            boolean batchMode = (options.isUnset("nonBatchMode")
                        && System.getProperty("nonBatchMode") == null);
            if (batchMode)
                CacheFSInfo.preRegister(context);

            // FIXME: this code will not be invoked if using JavacTask.parse/analyze/generate
            // invoke any available plugins
            String plugins = options.get(PLUGIN);
            if (plugins != null) {
                JavacProcessingEnvironment pEnv = JavacProcessingEnvironment.instance(context);
                ClassLoader cl = pEnv.getProcessorClassLoader();
                ServiceLoader<Plugin> sl = ServiceLoader.load(Plugin.class, cl);
                Set<List<String>> pluginsToCall = new LinkedHashSet<>();
                for (String plugin: plugins.split("\\x00")) {
                    pluginsToCall.add(List.from(plugin.split("\\s+")));
                }
                JavacTask task = null;
                for (Plugin plugin : sl) {
                    for (List<String> p : pluginsToCall) {
                        if (plugin.getName().equals(p.head)) {
                            pluginsToCall.remove(p);
                            try {
                                if (task == null)
                                    task = JavacTask.instance(pEnv);
                                plugin.init(task, p.tail.toArray(new String[p.tail.size()]));
                            } catch (Throwable ex) {
                                if (apiMode)
                                    throw new RuntimeException(ex);
                                pluginMessage(ex);
                                return Result.SYSERR;
                            }
                        }
                    }
                }
                for (List<String> p: pluginsToCall) {
                    log.printLines(PrefixKind.JAVAC, "msg.plugin.not.found", p.head);
                }
            }

            comp = JavaCompiler.instance(context);

            // FIXME: this code will not be invoked if using JavacTask.parse/analyze/generate
            String xdoclint = options.get(XDOCLINT);
            String xdoclintCustom = options.get(XDOCLINT_CUSTOM);
            if (xdoclint != null || xdoclintCustom != null) {
                Set<String> doclintOpts = new LinkedHashSet<>();
                if (xdoclint != null)
                    doclintOpts.add(DocLint.XMSGS_OPTION);
                if (xdoclintCustom != null) {
                    for (String s: xdoclintCustom.split("\\s+")) {
                        if (s.isEmpty())
                            continue;
                        doclintOpts.add(s.replace(XDOCLINT_CUSTOM.text, DocLint.XMSGS_CUSTOM_PREFIX));
                    }
                }
                if (!(doclintOpts.size() == 1
                        && doclintOpts.iterator().next().equals(DocLint.XMSGS_CUSTOM_PREFIX + "none"))) {
                    JavacTask t = BasicJavacTask.instance(context);
                    // standard doclet normally generates H1, H2
                    doclintOpts.add(DocLint.XIMPLICIT_HEADERS + "2");
                    new DocLint().init(t, doclintOpts.toArray(new String[doclintOpts.size()]));
                    comp.keepComments = true;
                }
            }

            if (options.get(XSTDOUT) != null) {
                // Stdout reassigned - ask compiler to close it when it is done
                comp.closeables = comp.closeables.prepend(log.getWriter(WriterKind.NOTICE));
            }

            fileManager = context.get(JavaFileManager.class);

            if (!files.isEmpty()) {
                // add filenames to fileObjects
                comp = JavaCompiler.instance(context);
                List<JavaFileObject> otherFiles = List.nil();
                JavacFileManager dfm = (JavacFileManager)fileManager;
                for (JavaFileObject fo : dfm.getJavaFileObjectsFromFiles(files))
                    otherFiles = otherFiles.prepend(fo);
                for (JavaFileObject fo : otherFiles)
                    fileObjects = fileObjects.prepend(fo);
            }
            comp.compile(fileObjects,
                         classnames.toList(),
                         processors);

            if (log.expectDiagKeys != null) {
                if (log.expectDiagKeys.isEmpty()) {
                    log.printRawLines("all expected diagnostics found");
                    return Result.OK;
                } else {
                    log.printRawLines("expected diagnostic keys not found: " + log.expectDiagKeys);
                    return Result.ERROR;
                }
            }

            if (comp.errorCount() != 0)
                return Result.ERROR;
        } catch (IOException ex) {
            ioMessage(ex);
            return Result.SYSERR;
        } catch (OutOfMemoryError ex) {
            resourceMessage(ex);
            return Result.SYSERR;
        } catch (StackOverflowError ex) {
            resourceMessage(ex);
            return Result.SYSERR;
        } catch (FatalError ex) {
            feMessage(ex);
            return Result.SYSERR;
        } catch (AnnotationProcessingError ex) {
            if (apiMode)
                throw new RuntimeException(ex.getCause());
            apMessage(ex);
            return Result.SYSERR;
        } catch (ClientCodeException ex) {
            // as specified by javax.tools.JavaCompiler#getTask
            // and javax.tools.JavaCompiler.CompilationTask#call
            throw new RuntimeException(ex.getCause());
        } catch (PropagatedException ex) {
            throw ex.getCause();
        } catch (Throwable ex) {
            // Nasty.  If we've already reported an error, compensate
            // for buggy compiler error recovery by swallowing thrown
            // exceptions.
            if (comp == null || comp.errorCount() == 0 ||
                options == null || options.isSet("dev"))
                bugMessage(ex);
            return Result.ABNORMAL;
        } finally {
            if (comp != null) {
                try {
                    comp.close();
                } catch (ClientCodeException ex) {
                    throw new RuntimeException(ex.getCause());
                }
            }
            filenames = null;
            options = null;
        }
        return Result.OK;
    }

    /** Print a message reporting an internal error.
     */
    void bugMessage(Throwable ex) {
        log.printLines(PrefixKind.JAVAC, "msg.bug", JavaCompiler.version());
        ex.printStackTrace(log.getWriter(WriterKind.NOTICE));
    }

    /** Print a message reporting a fatal error.
     */
    void feMessage(Throwable ex) {
        log.printRawLines(ex.getMessage());
        if (ex.getCause() != null && options.isSet("dev")) {
            ex.getCause().printStackTrace(log.getWriter(WriterKind.NOTICE));
        }
    }

    /** Print a message reporting an input/output error.
     */
    void ioMessage(Throwable ex) {
        log.printLines(PrefixKind.JAVAC, "msg.io");
        ex.printStackTrace(log.getWriter(WriterKind.NOTICE));
    }

    /** Print a message reporting an out-of-resources error.
     */
    void resourceMessage(Throwable ex) {
        log.printLines(PrefixKind.JAVAC, "msg.resource");
        ex.printStackTrace(log.getWriter(WriterKind.NOTICE));
    }

    /** Print a message reporting an uncaught exception from an
     * annotation processor.
     */
    void apMessage(AnnotationProcessingError ex) {
        log.printLines(PrefixKind.JAVAC, "msg.proc.annotation.uncaught.exception");
        ex.getCause().printStackTrace(log.getWriter(WriterKind.NOTICE));
    }

    /** Print a message reporting an uncaught exception from an
     * annotation processor.
     */
    void pluginMessage(Throwable ex) {
        log.printLines(PrefixKind.JAVAC, "msg.plugin.uncaught.exception");
        ex.printStackTrace(log.getWriter(WriterKind.NOTICE));
    }

    /** Display the location and checksum of a class. */
    void showClass(String className) {
        PrintWriter pw = log.getWriter(WriterKind.NOTICE);
        pw.println("javac: show class: " + className);
        URL url = getClass().getResource('/' + className.replace('.', '/') + ".class");
        if (url == null)
            pw.println("  class not found");
        else {
            pw.println("  " + url);
            try {
                final String algorithm = "MD5";
                byte[] digest;
                MessageDigest md = MessageDigest.getInstance(algorithm);
                try (DigestInputStream in = new DigestInputStream(url.openStream(), md)) {
                    byte[] buf = new byte[8192];
                    int n;
                    do { n = in.read(buf); } while (n > 0);
                    digest = md.digest();
                }
                StringBuilder sb = new StringBuilder();
                for (byte b: digest)
                    sb.append(String.format("%02x", b));
                pw.println("  " + algorithm + " checksum: " + sb);
            } catch (Exception e) {
                pw.println("  cannot compute digest: " + e);
            }
        }
    }

    private JavaFileManager fileManager;

    /* ************************************************************************
     * Internationalization
     *************************************************************************/

//    /** Find a localized string in the resource bundle.
//     *  @param key     The key for the localized string.
//     */
//    public static String getLocalizedString(String key, Object... args) { // FIXME sb private
//        try {
//            if (messages == null)
//                messages = new JavacMessages(javacBundleName);
//            return messages.getLocalizedString("javac." + key, args);
//        }
//        catch (MissingResourceException e) {
//            throw new Error("Fatal Error: Resource for javac is missing", e);
//        }
//    }
//
//    public static void useRawMessages(boolean enable) {
//        if (enable) {
//            messages = new JavacMessages(javacBundleName) {
//                    @Override
//                    public String getLocalizedString(String key, Object... args) {
//                        return key;
//                    }
//                };
//        } else {
//            messages = new JavacMessages(javacBundleName);
//        }
//    }

    public static final String javacBundleName =
        "com.sun.tools.javac.resources.javac";
//
//    private static JavacMessages messages;
}
