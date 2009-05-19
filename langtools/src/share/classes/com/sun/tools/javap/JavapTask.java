/*
 * Copyright 2007-2008 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.tools.javap;

import java.io.EOFException;
import java.io.FileNotFoundException;
import java.io.FilterInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticListener;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;

import com.sun.tools.classfile.*;

/**
 *  "Main" class for javap, normally accessed from the command line
 *  via Main, or from JSR199 via DisassemblerTool.
 *
 *  <p><b>This is NOT part of any API supported by Sun Microsystems.  If
 *  you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class JavapTask implements DisassemblerTool.DisassemblerTask, Messages {
    public class BadArgs extends Exception {
        static final long serialVersionUID = 8765093759964640721L;
        BadArgs(String key, Object... args) {
            super(JavapTask.this.getMessage(key, args));
            this.key = key;
            this.args = args;
        }

        BadArgs showUsage(boolean b) {
            showUsage = b;
            return this;
        }

        final String key;
        final Object[] args;
        boolean showUsage;
    }

    static abstract class Option {
        Option(boolean hasArg, String... aliases) {
            this.hasArg = hasArg;
            this.aliases = aliases;
        }

        boolean matches(String opt) {
            for (String a: aliases) {
                if (a.equals(opt))
                    return true;
            }
            return false;
        }

        boolean ignoreRest() {
            return false;
        }

        abstract void process(JavapTask task, String opt, String arg) throws BadArgs;

        final boolean hasArg;
        final String[] aliases;
    }

    static Option[] recognizedOptions = {

        new Option(false, "-help", "--help", "-?") {
            void process(JavapTask task, String opt, String arg) {
                task.options.help = true;
            }
        },

        new Option(false, "-version") {
            void process(JavapTask task, String opt, String arg) {
                task.options.version = true;
            }
        },

        new Option(false, "-fullversion") {
            void process(JavapTask task, String opt, String arg) {
                task.options.fullVersion = true;
            }
        },

        new Option(false, "-v", "-verbose", "-all") {
            void process(JavapTask task, String opt, String arg) {
                task.options.verbose = true;
                task.options.showFlags = true;
                task.options.showAllAttrs = true;
            }
        },

        new Option(false, "-l") {
            void process(JavapTask task, String opt, String arg) {
                task.options.showLineAndLocalVariableTables = true;
            }
        },

        new Option(false, "-public") {
            void process(JavapTask task, String opt, String arg) {
                task.options.accessOptions.add(opt);
                task.options.showAccess = AccessFlags.ACC_PUBLIC;
            }
        },

        new Option(false, "-protected") {
            void process(JavapTask task, String opt, String arg) {
                task.options.accessOptions.add(opt);
                task.options.showAccess = AccessFlags.ACC_PROTECTED;
            }
        },

        new Option(false, "-package") {
            void process(JavapTask task, String opt, String arg) {
                task.options.accessOptions.add(opt);
                task.options.showAccess = 0;
            }
        },

        new Option(false, "-p", "-private") {
            void process(JavapTask task, String opt, String arg) {
                if (!task.options.accessOptions.contains("-p") &&
                        !task.options.accessOptions.contains("-private")) {
                    task.options.accessOptions.add(opt);
                }
                task.options.showAccess = AccessFlags.ACC_PRIVATE;
            }
        },

        new Option(false, "-c") {
            void process(JavapTask task, String opt, String arg) {
                task.options.showDisassembled = true;
            }
        },

        new Option(false, "-s") {
            void process(JavapTask task, String opt, String arg) {
                task.options.showInternalSignatures = true;
            }
        },

//        new Option(false, "-all") {
//            void process(JavapTask task, String opt, String arg) {
//                task.options.showAllAttrs = true;
//            }
//        },

        new Option(false, "-h") {
            void process(JavapTask task, String opt, String arg) throws BadArgs {
                throw task.new BadArgs("err.h.not.supported");
            }
        },

        new Option(false, "-verify", "-verify-verbose") {
            void process(JavapTask task, String opt, String arg) throws BadArgs {
                throw task.new BadArgs("err.verify.not.supported");
            }
        },

        new Option(false, "-sysinfo") {
            void process(JavapTask task, String opt, String arg) {
                task.options.sysInfo = true;
            }
        },

        new Option(false, "-Xold") {
            void process(JavapTask task, String opt, String arg) throws BadArgs {
                // -Xold is only supported as first arg when invoked from
                // command line; this is handled in Main,main
                throw task.new BadArgs("err.Xold.not.supported.here");
            }
        },

        new Option(false, "-Xnew") {
            void process(JavapTask task, String opt, String arg) throws BadArgs {
                // ignore: this _is_ the new version
            }
        },

        new Option(false, "-XDcompat") {
            void process(JavapTask task, String opt, String arg) {
                task.options.compat = true;
            }
        },

        new Option(false, "-XDjsr277") {
            void process(JavapTask task, String opt, String arg) {
                task.options.jsr277 = true;
            }
        },

        new Option(false, "-XDignore.symbol.file") {
            void process(JavapTask task, String opt, String arg) {
                task.options.ignoreSymbolFile = true;
            }
        },

        new Option(false, "-XDdetails") {
            void process(JavapTask task, String opt, String arg) {
                task.options.details = EnumSet.allOf(InstructionDetailWriter.Kind.class);
            }

        },

        new Option(false, "-XDdetails:") {
            @Override
            boolean matches(String opt) {
                int sep = opt.indexOf(":");
                return sep != -1 && super.matches(opt.substring(0, sep + 1));
            }

            void process(JavapTask task, String opt, String arg) throws BadArgs {
                int sep = opt.indexOf(":");
                for (String v: opt.substring(sep + 1).split("[,: ]+")) {
                    if (!handleArg(task, v))
                        throw task.new BadArgs("err.invalid.arg.for.option", v);
                }
            }

            boolean handleArg(JavapTask task, String arg) {
                if (arg.length() == 0)
                    return true;

                if (arg.equals("all")) {
                    task.options.details = EnumSet.allOf(InstructionDetailWriter.Kind.class);
                    return true;
                }

                boolean on = true;
                if (arg.startsWith("-")) {
                    on = false;
                    arg = arg.substring(1);
                }

                for (InstructionDetailWriter.Kind k: InstructionDetailWriter.Kind.values()) {
                    if (arg.equalsIgnoreCase(k.option)) {
                        if (on)
                            task.options.details.add(k);
                        else
                            task.options.details.remove(k);
                        return true;
                    }
                }
                return false;
            }
        },

        new Option(false, "-constants") {
            void process(JavapTask task, String opt, String arg) {
                task.options.showConstants = true;
            }
        }

    };

    JavapTask() {
        context = new Context();
        context.put(Messages.class, this);
        options = Options.instance(context);
    }

    JavapTask(Writer out,
            JavaFileManager fileManager,
            DiagnosticListener<? super JavaFileObject> diagnosticListener,
            Iterable<String> options,
            Iterable<String> classes) {
        this();
        this.log = getPrintWriterForWriter(out);
        this.fileManager = fileManager;
        this.diagnosticListener = diagnosticListener;

        try {
            handleOptions(options, false);
        } catch (BadArgs e) {
            throw new IllegalArgumentException(e.getMessage());
        }

        this.classes = new ArrayList<String>();
        for (String classname: classes) {
            classname.getClass(); // null-check
            this.classes.add(classname);
        }
    }

    public void setLocale(Locale locale) {
        if (locale == null)
            locale = Locale.getDefault();
        task_locale = locale;
    }

    public void setLog(PrintWriter log) {
        this.log = log;
    }

    public void setLog(OutputStream s) {
        setLog(getPrintWriterForStream(s));
    }

    private static PrintWriter getPrintWriterForStream(OutputStream s) {
        return new PrintWriter(s, true);
    }

    private static PrintWriter getPrintWriterForWriter(Writer w) {
        if (w == null)
            return getPrintWriterForStream(null);
        else if (w instanceof PrintWriter)
            return (PrintWriter) w;
        else
            return new PrintWriter(w, true);
    }

    public void setDiagnosticListener(DiagnosticListener<? super JavaFileObject> dl) {
        diagnosticListener = dl;
    }

    public void setDiagnosticListener(OutputStream s) {
        setDiagnosticListener(getDiagnosticListenerForStream(s));
    }

    private DiagnosticListener<JavaFileObject> getDiagnosticListenerForStream(OutputStream s) {
        return getDiagnosticListenerForWriter(getPrintWriterForStream(s));
    }

    private DiagnosticListener<JavaFileObject> getDiagnosticListenerForWriter(Writer w) {
        final PrintWriter pw = getPrintWriterForWriter(w);
        return new DiagnosticListener<JavaFileObject> () {
            public void report(Diagnostic<? extends JavaFileObject> diagnostic) {
                if (diagnostic.getKind() == Diagnostic.Kind.ERROR) {
                        pw.print(getMessage("err.prefix"));
                    pw.print(" ");
                }
                pw.println(diagnostic.getMessage(null));
            }
        };
    }

    /** Result codes.
     */
    static final int
        EXIT_OK = 0,        // Compilation completed with no errors.
        EXIT_ERROR = 1,     // Completed but reported errors.
        EXIT_CMDERR = 2,    // Bad command-line arguments
        EXIT_SYSERR = 3,    // System error or resource exhaustion.
        EXIT_ABNORMAL = 4;  // Compiler terminated abnormally

    int run(String[] args) {
        try {
            handleOptions(args);

            // the following gives consistent behavior with javac
            if (classes == null || classes.size() == 0) {
                if (options.help || options.version || options.fullVersion)
                    return EXIT_OK;
                else
                    return EXIT_CMDERR;
            }

            boolean ok = run();
            return ok ? EXIT_OK : EXIT_ERROR;
        } catch (BadArgs e) {
            diagnosticListener.report(createDiagnostic(e.key, e.args));
            if (e.showUsage) {
                log.println(getMessage("main.usage.summary", progname));
            }
            return EXIT_CMDERR;
        } catch (InternalError e) {
            Object[] e_args;
            if (e.getCause() == null)
                e_args = e.args;
            else {
                e_args = new Object[e.args.length + 1];
                e_args[0] = e.getCause();
                System.arraycopy(e.args, 0, e_args, 1, e.args.length);
            }
            diagnosticListener.report(createDiagnostic("err.internal.error", e_args));
            return EXIT_ABNORMAL;
        } finally {
            log.flush();
        }
    }

    public void handleOptions(String[] args) throws BadArgs {
        handleOptions(Arrays.asList(args), true);
    }

    private void handleOptions(Iterable<String> args, boolean allowClasses) throws BadArgs {
        if (log == null) {
            log = getPrintWriterForStream(System.out);
            if (diagnosticListener == null)
              diagnosticListener = getDiagnosticListenerForStream(System.err);
        } else {
            if (diagnosticListener == null)
              diagnosticListener = getDiagnosticListenerForWriter(log);
        }


        if (fileManager == null)
            fileManager = getDefaultFileManager(diagnosticListener, log);

        Iterator<String> iter = args.iterator();
        boolean noArgs = !iter.hasNext();

        while (iter.hasNext()) {
            String arg = iter.next();
            if (arg.startsWith("-"))
                handleOption(arg, iter);
            else if (allowClasses) {
                if (classes == null)
                    classes = new ArrayList<String>();
                classes.add(arg);
                while (iter.hasNext())
                    classes.add(iter.next());
            } else
                throw new BadArgs("err.unknown.option", arg).showUsage(true);
        }

        if (!options.compat && options.accessOptions.size() > 1) {
            StringBuilder sb = new StringBuilder();
            for (String opt: options.accessOptions) {
                if (sb.length() > 0)
                    sb.append(" ");
                sb.append(opt);
            }
            throw new BadArgs("err.incompatible.options", sb);
        }

        if (options.ignoreSymbolFile && fileManager instanceof JavapFileManager)
            ((JavapFileManager) fileManager).setIgnoreSymbolFile(true);

        if ((classes == null || classes.size() == 0) &&
                !(noArgs || options.help || options.version || options.fullVersion)) {
            throw new BadArgs("err.no.classes.specified");
        }

        if (noArgs || options.help)
            showHelp();

        if (options.version || options.fullVersion)
            showVersion(options.fullVersion);
    }

    private void handleOption(String name, Iterator<String> rest) throws BadArgs {
        for (Option o: recognizedOptions) {
            if (o.matches(name)) {
                if (o.hasArg) {
                    if (rest.hasNext())
                        o.process(this, name, rest.next());
                    else
                        throw new BadArgs("err.missing.arg", name).showUsage(true);
                } else
                    o.process(this, name, null);

                if (o.ignoreRest()) {
                    while (rest.hasNext())
                        rest.next();
                }
                return;
            }
        }

        if (fileManager.handleOption(name, rest))
            return;

        throw new BadArgs("err.unknown.option", name).showUsage(true);
    }

    public Boolean call() {
        return run();
    }

    public boolean run() {
        if (classes == null || classes.size() == 0)
            return false;

        context.put(PrintWriter.class, log);
        ClassWriter classWriter = ClassWriter.instance(context);
        SourceWriter sourceWriter = SourceWriter.instance(context);
        sourceWriter.setFileManager(fileManager);

        boolean ok = true;

        for (String className: classes) {
            JavaFileObject fo;
            try {
                if (className.endsWith(".class")) {
                    if (fileManager instanceof StandardJavaFileManager) {
                        StandardJavaFileManager sfm = (StandardJavaFileManager) fileManager;
                        fo = sfm.getJavaFileObjects(className).iterator().next();
                    } else {
                       diagnosticListener.report(createDiagnostic("err.not.standard.file.manager", className));
                       ok = false;
                       continue;
                    }
                } else {
                    fo = getClassFileObject(className);
                    if (fo == null) {
                        // see if it is an inner class, by replacing dots to $, starting from the right
                        String cn = className;
                        int lastDot;
                        while (fo == null && (lastDot = cn.lastIndexOf(".")) != -1) {
                            cn = cn.substring(0, lastDot) + "$" + cn.substring(lastDot + 1);
                            fo = getClassFileObject(cn);
                        }
                    }
                    if (fo == null) {
                       diagnosticListener.report(createDiagnostic("err.class.not.found", className));
                       ok = false;
                       continue;
                    }
                }
                Attribute.Factory attributeFactory = new Attribute.Factory();
                attributeFactory.setCompat(options.compat);
                attributeFactory.setJSR277(options.jsr277);

                InputStream in = fo.openInputStream();
                SizeInputStream sizeIn = null;
                MessageDigest md  = null;
                if (options.sysInfo || options.verbose) {
                    md = MessageDigest.getInstance("MD5");
                    in = new DigestInputStream(in, md);
                    in = sizeIn = new SizeInputStream(in);
                }

                ClassFile cf = ClassFile.read(in, attributeFactory);

                if (options.sysInfo || options.verbose) {
                    classWriter.setFile(fo.toUri());
                    classWriter.setLastModified(fo.getLastModified());
                    classWriter.setDigest("MD5", md.digest());
                    classWriter.setFileSize(sizeIn.size());
                }

                classWriter.write(cf);

            } catch (ConstantPoolException e) {
                diagnosticListener.report(createDiagnostic("err.bad.constant.pool", className, e.getLocalizedMessage()));
                ok = false;
            } catch (EOFException e) {
                diagnosticListener.report(createDiagnostic("err.end.of.file", className));
                ok = false;
            } catch (FileNotFoundException e) {
                diagnosticListener.report(createDiagnostic("err.file.not.found", e.getLocalizedMessage()));
                ok = false;
            } catch (IOException e) {
                //e.printStackTrace();
                Object msg = e.getLocalizedMessage();
                if (msg == null)
                    msg = e;
                diagnosticListener.report(createDiagnostic("err.ioerror", className, msg));
                ok = false;
            } catch (Throwable t) {
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                t.printStackTrace(pw);
                pw.close();
                diagnosticListener.report(createDiagnostic("err.crash", t.toString(), sw.toString()));
                ok = false;
            }
        }

        return ok;
    }

    private JavaFileManager getDefaultFileManager(final DiagnosticListener<? super JavaFileObject> dl, PrintWriter log) {
        return JavapFileManager.create(dl, log, options);
    }

    private JavaFileObject getClassFileObject(String className) throws IOException {
        JavaFileObject fo;
        fo = fileManager.getJavaFileForInput(StandardLocation.PLATFORM_CLASS_PATH, className, JavaFileObject.Kind.CLASS);
        if (fo == null)
            fo = fileManager.getJavaFileForInput(StandardLocation.CLASS_PATH, className, JavaFileObject.Kind.CLASS);
        return fo;
    }

    private void showHelp() {
        log.println(getMessage("main.usage", progname));
        for (Option o: recognizedOptions) {
            String name = o.aliases[0].substring(1); // there must always be at least one name
            if (name.startsWith("X") || name.equals("fullversion") || name.equals("h") || name.equals("verify"))
                continue;
            log.println(getMessage("main.opt." + name));
        }
        String[] fmOptions = { "-classpath", "-bootclasspath" };
        for (String o: fmOptions) {
            if (fileManager.isSupportedOption(o) == -1)
                continue;
            String name = o.substring(1);
            log.println(getMessage("main.opt." + name));
        }

    }

    private void showVersion(boolean full) {
        log.println(version(full ? "full" : "release"));
    }

    private static final String versionRBName = "com.sun.tools.javap.resources.version";
    private static ResourceBundle versionRB;

    private String version(String key) {
        // key=version:  mm.nn.oo[-milestone]
        // key=full:     mm.mm.oo[-milestone]-build
        if (versionRB == null) {
            try {
                versionRB = ResourceBundle.getBundle(versionRBName);
            } catch (MissingResourceException e) {
                return getMessage("version.resource.missing", System.getProperty("java.version"));
            }
        }
        try {
            return versionRB.getString(key);
        }
        catch (MissingResourceException e) {
            return getMessage("version.unknown", System.getProperty("java.version"));
        }
    }

    private Diagnostic<JavaFileObject> createDiagnostic(final String key, final Object... args) {
        return new Diagnostic<JavaFileObject>() {
            public Kind getKind() {
                return Diagnostic.Kind.ERROR;
            }

            public JavaFileObject getSource() {
                return null;
            }

            public long getPosition() {
                return Diagnostic.NOPOS;
            }

            public long getStartPosition() {
                return Diagnostic.NOPOS;
            }

            public long getEndPosition() {
                return Diagnostic.NOPOS;
            }

            public long getLineNumber() {
                return Diagnostic.NOPOS;
            }

            public long getColumnNumber() {
                return Diagnostic.NOPOS;
            }

            public String getCode() {
                return key;
            }

            public String getMessage(Locale locale) {
                return JavapTask.this.getMessage(locale, key, args);
            }

        };

    }

    public String getMessage(String key, Object... args) {
        return getMessage(task_locale, key, args);
    }

    public String getMessage(Locale locale, String key, Object... args) {
        if (bundles == null) {
            // could make this a HashMap<Locale,SoftReference<ResourceBundle>>
            // and for efficiency, keep a hard reference to the bundle for the task
            // locale
            bundles = new HashMap<Locale, ResourceBundle>();
        }

        if (locale == null)
            locale = Locale.getDefault();

        ResourceBundle b = bundles.get(locale);
        if (b == null) {
            try {
                b = ResourceBundle.getBundle("com.sun.tools.javap.resources.javap", locale);
                bundles.put(locale, b);
            } catch (MissingResourceException e) {
                throw new InternalError("Cannot find javap resource bundle for locale " + locale);
            }
        }

        try {
            return MessageFormat.format(b.getString(key), args);
        } catch (MissingResourceException e) {
            throw new InternalError(e, key);
        }
    }

    Context context;
    JavaFileManager fileManager;
    PrintWriter log;
    DiagnosticListener<? super JavaFileObject> diagnosticListener;
    List<String> classes;
    Options options;
    //ResourceBundle bundle;
    Locale task_locale;
    Map<Locale, ResourceBundle> bundles;

    private static final String progname = "javap";

    private static class SizeInputStream extends FilterInputStream {
        SizeInputStream(InputStream in) {
            super(in);
        }

        int size() {
            return size;
        }

        @Override
        public int read(byte[] buf, int offset, int length) throws IOException {
            int n = super.read(buf, offset, length);
            if (n > 0)
                size += n;
            return n;
        }

        @Override
        public int read() throws IOException {
            int b = super.read();
            size += 1;
            return b;
        }

        private int size;
    }
}
