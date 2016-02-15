/*
 * Copyright (c) 2005, 2016, Oracle and/or its affiliates. All rights reserved.
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

import java.io.Closeable;
import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.regex.*;
import java.util.stream.Collectors;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.util.*;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import static javax.tools.StandardLocation.*;

import com.sun.source.util.TaskEvent;
import com.sun.tools.javac.api.MultiTaskListener;
import com.sun.tools.javac.code.*;
import com.sun.tools.javac.code.Symbol.*;
import com.sun.tools.javac.code.Type.ClassType;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.comp.AttrContext;
import com.sun.tools.javac.comp.Check;
import com.sun.tools.javac.comp.Enter;
import com.sun.tools.javac.comp.Env;
import com.sun.tools.javac.file.JavacFileManager;
import com.sun.tools.javac.main.JavaCompiler;
import com.sun.tools.javac.model.JavacElements;
import com.sun.tools.javac.model.JavacTypes;
import com.sun.tools.javac.platform.PlatformDescription;
import com.sun.tools.javac.platform.PlatformDescription.PluginInfo;
import com.sun.tools.javac.tree.*;
import com.sun.tools.javac.tree.JCTree.*;
import com.sun.tools.javac.util.Abort;
import com.sun.tools.javac.util.Assert;
import com.sun.tools.javac.util.ClientCodeException;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Convert;
import com.sun.tools.javac.util.DefinedBy;
import com.sun.tools.javac.util.DefinedBy.Api;
import com.sun.tools.javac.util.Iterators;
import com.sun.tools.javac.util.JCDiagnostic;
import com.sun.tools.javac.util.JavacMessages;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Log;
import com.sun.tools.javac.util.MatchingUtils;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Names;
import com.sun.tools.javac.util.Options;
import com.sun.tools.javac.util.ServiceLoader;
import static com.sun.tools.javac.code.Lint.LintCategory.PROCESSING;
import static com.sun.tools.javac.code.Kinds.Kind.*;
import static com.sun.tools.javac.main.Option.*;
import static com.sun.tools.javac.comp.CompileStates.CompileState;
import static com.sun.tools.javac.util.JCDiagnostic.DiagnosticFlag.*;

/**
 * Objects of this class hold and manage the state needed to support
 * annotation processing.
 *
 * <p><b>This is NOT part of any supported API.
 * If you write code that depends on this, you do so at your own risk.
 * This code and its internal interfaces are subject to change or
 * deletion without notice.</b>
 */
public class JavacProcessingEnvironment implements ProcessingEnvironment, Closeable {
    private final Options options;

    private final boolean printProcessorInfo;
    private final boolean printRounds;
    private final boolean verbose;
    private final boolean lint;
    private final boolean fatalErrors;
    private final boolean werror;
    private final boolean showResolveErrors;

    private final JavacFiler filer;
    private final JavacMessager messager;
    private final JavacElements elementUtils;
    private final JavacTypes typeUtils;
    private final Types types;
    private final JavaCompiler compiler;

    /**
     * Holds relevant state history of which processors have been
     * used.
     */
    private DiscoveredProcessors discoveredProcs;

    /**
     * Map of processor-specific options.
     */
    private final Map<String, String> processorOptions;

    /**
     */
    private final Set<String> unmatchedProcessorOptions;

    /**
     * Annotations implicitly processed and claimed by javac.
     */
    private final Set<String> platformAnnotations;

    /**
     * Set of packages given on command line.
     */
    private Set<PackageSymbol> specifiedPackages = Collections.emptySet();

    /** The log to be used for error reporting.
     */
    final Log log;

    /** Diagnostic factory.
     */
    JCDiagnostic.Factory diags;

    /**
     * Source level of the compile.
     */
    Source source;

    private ClassLoader processorClassLoader;
    private SecurityException processorClassLoaderException;

    /**
     * JavacMessages object used for localization
     */
    private JavacMessages messages;

    private MultiTaskListener taskListener;
    private final Symtab symtab;
    private final Names names;
    private final Enter enter;
    private final Completer initialCompleter;
    private final Check chk;

    private final Context context;

    /** Get the JavacProcessingEnvironment instance for this context. */
    public static JavacProcessingEnvironment instance(Context context) {
        JavacProcessingEnvironment instance = context.get(JavacProcessingEnvironment.class);
        if (instance == null)
            instance = new JavacProcessingEnvironment(context);
        return instance;
    }

    protected JavacProcessingEnvironment(Context context) {
        this.context = context;
        context.put(JavacProcessingEnvironment.class, this);
        log = Log.instance(context);
        source = Source.instance(context);
        diags = JCDiagnostic.Factory.instance(context);
        options = Options.instance(context);
        printProcessorInfo = options.isSet(XPRINTPROCESSORINFO);
        printRounds = options.isSet(XPRINTROUNDS);
        verbose = options.isSet(VERBOSE);
        lint = Lint.instance(context).isEnabled(PROCESSING);
        compiler = JavaCompiler.instance(context);
        if (options.isSet(PROC, "only") || options.isSet(XPRINT)) {
            compiler.shouldStopPolicyIfNoError = CompileState.PROCESS;
        }
        fatalErrors = options.isSet("fatalEnterError");
        showResolveErrors = options.isSet("showResolveErrors");
        werror = options.isSet(WERROR);
        platformAnnotations = initPlatformAnnotations();

        // Initialize services before any processors are initialized
        // in case processors use them.
        filer = new JavacFiler(context);
        messager = new JavacMessager(context, this);
        elementUtils = JavacElements.instance(context);
        typeUtils = JavacTypes.instance(context);
        types = Types.instance(context);
        processorOptions = initProcessorOptions();
        unmatchedProcessorOptions = initUnmatchedProcessorOptions();
        messages = JavacMessages.instance(context);
        taskListener = MultiTaskListener.instance(context);
        symtab = Symtab.instance(context);
        names = Names.instance(context);
        enter = Enter.instance(context);
        initialCompleter = ClassFinder.instance(context).getCompleter();
        chk = Check.instance(context);
        initProcessorClassLoader();
    }

    public void setProcessors(Iterable<? extends Processor> processors) {
        Assert.checkNull(discoveredProcs);
        initProcessorIterator(processors);
    }

    private Set<String> initPlatformAnnotations() {
        Set<String> platformAnnotations = new HashSet<>();
        platformAnnotations.add("java.lang.Deprecated");
        platformAnnotations.add("java.lang.Override");
        platformAnnotations.add("java.lang.SuppressWarnings");
        platformAnnotations.add("java.lang.annotation.Documented");
        platformAnnotations.add("java.lang.annotation.Inherited");
        platformAnnotations.add("java.lang.annotation.Retention");
        platformAnnotations.add("java.lang.annotation.Target");
        return Collections.unmodifiableSet(platformAnnotations);
    }

    private void initProcessorClassLoader() {
        JavaFileManager fileManager = context.get(JavaFileManager.class);
        try {
            // If processorpath is not explicitly set, use the classpath.
            processorClassLoader = fileManager.hasLocation(ANNOTATION_PROCESSOR_PATH)
                ? fileManager.getClassLoader(ANNOTATION_PROCESSOR_PATH)
                : fileManager.getClassLoader(CLASS_PATH);

            if (processorClassLoader != null && processorClassLoader instanceof Closeable) {
                compiler.closeables = compiler.closeables.prepend((Closeable) processorClassLoader);
            }
        } catch (SecurityException e) {
            processorClassLoaderException = e;
        }
    }

    private void initProcessorIterator(Iterable<? extends Processor> processors) {
        Iterator<? extends Processor> processorIterator;

        if (options.isSet(XPRINT)) {
            try {
                Processor processor = PrintingProcessor.class.newInstance();
                processorIterator = List.of(processor).iterator();
            } catch (Throwable t) {
                AssertionError assertError =
                    new AssertionError("Problem instantiating PrintingProcessor.");
                assertError.initCause(t);
                throw assertError;
            }
        } else if (processors != null) {
            processorIterator = processors.iterator();
        } else {
            String processorNames = options.get(PROCESSOR);
            if (processorClassLoaderException == null) {
                /*
                 * If the "-processor" option is used, search the appropriate
                 * path for the named class.  Otherwise, use a service
                 * provider mechanism to create the processor iterator.
                 */
                if (processorNames != null) {
                    processorIterator = new NameProcessIterator(processorNames, processorClassLoader, log);
                } else {
                    processorIterator = new ServiceIterator(processorClassLoader, log);
                }
            } else {
                /*
                 * A security exception will occur if we can't create a classloader.
                 * Ignore the exception if, with hindsight, we didn't need it anyway
                 * (i.e. no processor was specified either explicitly, or implicitly,
                 * in service configuration file.) Otherwise, we cannot continue.
                 */
                processorIterator = handleServiceLoaderUnavailability("proc.cant.create.loader",
                        processorClassLoaderException);
            }
        }
        PlatformDescription platformProvider = context.get(PlatformDescription.class);
        java.util.List<Processor> platformProcessors = Collections.emptyList();
        if (platformProvider != null) {
            platformProcessors = platformProvider.getAnnotationProcessors()
                                                 .stream()
                                                 .map(ap -> ap.getPlugin())
                                                 .collect(Collectors.toList());
        }
        List<Iterator<? extends Processor>> iterators = List.of(processorIterator,
                                                                platformProcessors.iterator());
        Iterator<? extends Processor> compoundIterator =
                Iterators.createCompoundIterator(iterators, i -> i);
        discoveredProcs = new DiscoveredProcessors(compoundIterator);
    }

    /**
     * Returns an empty processor iterator if no processors are on the
     * relevant path, otherwise if processors are present, logs an
     * error.  Called when a service loader is unavailable for some
     * reason, either because a service loader class cannot be found
     * or because a security policy prevents class loaders from being
     * created.
     *
     * @param key The resource key to use to log an error message
     * @param e   If non-null, pass this exception to Abort
     */
    private Iterator<Processor> handleServiceLoaderUnavailability(String key, Exception e) {
        JavaFileManager fileManager = context.get(JavaFileManager.class);

        if (fileManager instanceof JavacFileManager) {
            StandardJavaFileManager standardFileManager = (JavacFileManager) fileManager;
            Iterable<? extends File> workingPath = fileManager.hasLocation(ANNOTATION_PROCESSOR_PATH)
                ? standardFileManager.getLocation(ANNOTATION_PROCESSOR_PATH)
                : standardFileManager.getLocation(CLASS_PATH);

            if (needClassLoader(options.get(PROCESSOR), workingPath) )
                handleException(key, e);

        } else {
            handleException(key, e);
        }

        java.util.List<Processor> pl = Collections.emptyList();
        return pl.iterator();
    }

    /**
     * Handle a security exception thrown during initializing the
     * Processor iterator.
     */
    private void handleException(String key, Exception e) {
        if (e != null) {
            log.error(key, e.getLocalizedMessage());
            throw new Abort(e);
        } else {
            log.error(key);
            throw new Abort();
        }
    }

    /**
     * Use a service loader appropriate for the platform to provide an
     * iterator over annotations processors; fails if a loader is
     * needed but unavailable.
     */
    private class ServiceIterator implements Iterator<Processor> {
        private Iterator<Processor> iterator;
        private Log log;
        private ServiceLoader<Processor> loader;

        ServiceIterator(ClassLoader classLoader, Log log) {
            this.log = log;
            try {
                try {
                    loader = ServiceLoader.load(Processor.class, classLoader);
                    this.iterator = loader.iterator();
                } catch (Exception e) {
                    // Fail softly if a loader is not actually needed.
                    this.iterator = handleServiceLoaderUnavailability("proc.no.service", null);
                }
            } catch (Throwable t) {
                log.error("proc.service.problem");
                throw new Abort(t);
            }
        }

        public boolean hasNext() {
            try {
                return iterator.hasNext();
            } catch(ServiceConfigurationError sce) {
                log.error("proc.bad.config.file", sce.getLocalizedMessage());
                throw new Abort(sce);
            } catch (Throwable t) {
                throw new Abort(t);
            }
        }

        public Processor next() {
            try {
                return iterator.next();
            } catch (ServiceConfigurationError sce) {
                log.error("proc.bad.config.file", sce.getLocalizedMessage());
                throw new Abort(sce);
            } catch (Throwable t) {
                throw new Abort(t);
            }
        }

        public void remove() {
            throw new UnsupportedOperationException();
        }

        public void close() {
            if (loader != null) {
                try {
                    loader.reload();
                } catch(Exception e) {
                    // Ignore problems during a call to reload.
                }
            }
        }
    }


    private static class NameProcessIterator implements Iterator<Processor> {
        Processor nextProc = null;
        Iterator<String> names;
        ClassLoader processorCL;
        Log log;

        NameProcessIterator(String names, ClassLoader processorCL, Log log) {
            this.names = Arrays.asList(names.split(",")).iterator();
            this.processorCL = processorCL;
            this.log = log;
        }

        public boolean hasNext() {
            if (nextProc != null)
                return true;
            else {
                if (!names.hasNext())
                    return false;
                else {
                    String processorName = names.next();

                    Processor processor;
                    try {
                        try {
                            processor =
                                (Processor) (processorCL.loadClass(processorName).newInstance());
                        } catch (ClassNotFoundException cnfe) {
                            log.error("proc.processor.not.found", processorName);
                            return false;
                        } catch (ClassCastException cce) {
                            log.error("proc.processor.wrong.type", processorName);
                            return false;
                        } catch (Exception e ) {
                            log.error("proc.processor.cant.instantiate", processorName);
                            return false;
                        }
                    } catch(ClientCodeException e) {
                        throw e;
                    } catch(Throwable t) {
                        throw new AnnotationProcessingError(t);
                    }
                    nextProc = processor;
                    return true;
                }

            }
        }

        public Processor next() {
            if (hasNext()) {
                Processor p = nextProc;
                nextProc = null;
                return p;
            } else
                throw new NoSuchElementException();
        }

        public void remove () {
            throw new UnsupportedOperationException();
        }
    }

    public boolean atLeastOneProcessor() {
        return discoveredProcs.iterator().hasNext();
    }

    private Map<String, String> initProcessorOptions() {
        Set<String> keySet = options.keySet();
        Map<String, String> tempOptions = new LinkedHashMap<>();

        for(String key : keySet) {
            if (key.startsWith("-A") && key.length() > 2) {
                int sepIndex = key.indexOf('=');
                String candidateKey = null;
                String candidateValue = null;

                if (sepIndex == -1)
                    candidateKey = key.substring(2);
                else if (sepIndex >= 3) {
                    candidateKey = key.substring(2, sepIndex);
                    candidateValue = (sepIndex < key.length()-1)?
                        key.substring(sepIndex+1) : null;
                }
                tempOptions.put(candidateKey, candidateValue);
            }
        }

        PlatformDescription platformProvider = context.get(PlatformDescription.class);

        if (platformProvider != null) {
            for (PluginInfo<Processor> ap : platformProvider.getAnnotationProcessors()) {
                tempOptions.putAll(ap.getOptions());
            }
        }

        return Collections.unmodifiableMap(tempOptions);
    }

    private Set<String> initUnmatchedProcessorOptions() {
        Set<String> unmatchedProcessorOptions = new HashSet<>();
        unmatchedProcessorOptions.addAll(processorOptions.keySet());
        return unmatchedProcessorOptions;
    }

    /**
     * State about how a processor has been used by the tool.  If a
     * processor has been used on a prior round, its process method is
     * called on all subsequent rounds, perhaps with an empty set of
     * annotations to process.  The {@code annotationSupported} method
     * caches the supported annotation information from the first (and
     * only) getSupportedAnnotationTypes call to the processor.
     */
    static class ProcessorState {
        public Processor processor;
        public boolean   contributed;
        private ArrayList<Pattern> supportedAnnotationPatterns;
        private ArrayList<String>  supportedOptionNames;

        ProcessorState(Processor p, Log log, Source source, ProcessingEnvironment env) {
            processor = p;
            contributed = false;

            try {
                processor.init(env);

                checkSourceVersionCompatibility(source, log);

                supportedAnnotationPatterns = new ArrayList<>();
                for (String importString : processor.getSupportedAnnotationTypes()) {
                    supportedAnnotationPatterns.add(importStringToPattern(importString,
                                                                          processor,
                                                                          log));
                }

                supportedOptionNames = new ArrayList<>();
                for (String optionName : processor.getSupportedOptions() ) {
                    if (checkOptionName(optionName, log))
                        supportedOptionNames.add(optionName);
                }

            } catch (ClientCodeException e) {
                throw e;
            } catch (Throwable t) {
                throw new AnnotationProcessingError(t);
            }
        }

        /**
         * Checks whether or not a processor's source version is
         * compatible with the compilation source version.  The
         * processor's source version needs to be greater than or
         * equal to the source version of the compile.
         */
        private void checkSourceVersionCompatibility(Source source, Log log) {
            SourceVersion procSourceVersion = processor.getSupportedSourceVersion();

            if (procSourceVersion.compareTo(Source.toSourceVersion(source)) < 0 )  {
                log.warning("proc.processor.incompatible.source.version",
                            procSourceVersion,
                            processor.getClass().getName(),
                            source.name);
            }
        }

        private boolean checkOptionName(String optionName, Log log) {
            boolean valid = isValidOptionName(optionName);
            if (!valid)
                log.error("proc.processor.bad.option.name",
                            optionName,
                            processor.getClass().getName());
            return valid;
        }

        public boolean annotationSupported(String annotationName) {
            for(Pattern p: supportedAnnotationPatterns) {
                if (p.matcher(annotationName).matches())
                    return true;
            }
            return false;
        }

        /**
         * Remove options that are matched by this processor.
         */
        public void removeSupportedOptions(Set<String> unmatchedProcessorOptions) {
            unmatchedProcessorOptions.removeAll(supportedOptionNames);
        }
    }

    // TODO: These two classes can probably be rewritten better...
    /**
     * This class holds information about the processors that have
     * been discoverd so far as well as the means to discover more, if
     * necessary.  A single iterator should be used per round of
     * annotation processing.  The iterator first visits already
     * discovered processors then fails over to the service provider
     * mechanism if additional queries are made.
     */
    class DiscoveredProcessors implements Iterable<ProcessorState> {

        class ProcessorStateIterator implements Iterator<ProcessorState> {
            DiscoveredProcessors psi;
            Iterator<ProcessorState> innerIter;
            boolean onProcInterator;

            ProcessorStateIterator(DiscoveredProcessors psi) {
                this.psi = psi;
                this.innerIter = psi.procStateList.iterator();
                this.onProcInterator = false;
            }

            public ProcessorState next() {
                if (!onProcInterator) {
                    if (innerIter.hasNext())
                        return innerIter.next();
                    else
                        onProcInterator = true;
                }

                if (psi.processorIterator.hasNext()) {
                    ProcessorState ps = new ProcessorState(psi.processorIterator.next(),
                                                           log, source, JavacProcessingEnvironment.this);
                    psi.procStateList.add(ps);
                    return ps;
                } else
                    throw new NoSuchElementException();
            }

            public boolean hasNext() {
                if (onProcInterator)
                    return  psi.processorIterator.hasNext();
                else
                    return innerIter.hasNext() || psi.processorIterator.hasNext();
            }

            public void remove () {
                throw new UnsupportedOperationException();
            }

            /**
             * Run all remaining processors on the procStateList that
             * have not already run this round with an empty set of
             * annotations.
             */
            public void runContributingProcs(RoundEnvironment re) {
                if (!onProcInterator) {
                    Set<TypeElement> emptyTypeElements = Collections.emptySet();
                    while(innerIter.hasNext()) {
                        ProcessorState ps = innerIter.next();
                        if (ps.contributed)
                            callProcessor(ps.processor, emptyTypeElements, re);
                    }
                }
            }
        }

        Iterator<? extends Processor> processorIterator;
        ArrayList<ProcessorState>  procStateList;

        public ProcessorStateIterator iterator() {
            return new ProcessorStateIterator(this);
        }

        DiscoveredProcessors(Iterator<? extends Processor> processorIterator) {
            this.processorIterator = processorIterator;
            this.procStateList = new ArrayList<>();
        }

        /**
         * Free jar files, etc. if using a service loader.
         */
        public void close() {
            if (processorIterator != null &&
                processorIterator instanceof ServiceIterator) {
                ((ServiceIterator) processorIterator).close();
            }
        }
    }

    private void discoverAndRunProcs(Set<TypeElement> annotationsPresent,
                                     List<ClassSymbol> topLevelClasses,
                                     List<PackageSymbol> packageInfoFiles) {
        Map<String, TypeElement> unmatchedAnnotations = new HashMap<>(annotationsPresent.size());

        for(TypeElement a  : annotationsPresent) {
                unmatchedAnnotations.put(a.getQualifiedName().toString(),
                                         a);
        }

        // Give "*" processors a chance to match
        if (unmatchedAnnotations.size() == 0)
            unmatchedAnnotations.put("", null);

        DiscoveredProcessors.ProcessorStateIterator psi = discoveredProcs.iterator();
        // TODO: Create proper argument values; need past round
        // information to fill in this constructor.  Note that the 1
        // st round of processing could be the last round if there
        // were parse errors on the initial source files; however, we
        // are not doing processing in that case.

        Set<Element> rootElements = new LinkedHashSet<>();
        rootElements.addAll(topLevelClasses);
        rootElements.addAll(packageInfoFiles);
        rootElements = Collections.unmodifiableSet(rootElements);

        RoundEnvironment renv = new JavacRoundEnvironment(false,
                                                          false,
                                                          rootElements,
                                                          JavacProcessingEnvironment.this);

        while(unmatchedAnnotations.size() > 0 && psi.hasNext() ) {
            ProcessorState ps = psi.next();
            Set<String>  matchedNames = new HashSet<>();
            Set<TypeElement> typeElements = new LinkedHashSet<>();

            for (Map.Entry<String, TypeElement> entry: unmatchedAnnotations.entrySet()) {
                String unmatchedAnnotationName = entry.getKey();
                if (ps.annotationSupported(unmatchedAnnotationName) ) {
                    matchedNames.add(unmatchedAnnotationName);
                    TypeElement te = entry.getValue();
                    if (te != null)
                        typeElements.add(te);
                }
            }

            if (matchedNames.size() > 0 || ps.contributed) {
                boolean processingResult = callProcessor(ps.processor, typeElements, renv);
                ps.contributed = true;
                ps.removeSupportedOptions(unmatchedProcessorOptions);

                if (printProcessorInfo || verbose) {
                    log.printLines("x.print.processor.info",
                            ps.processor.getClass().getName(),
                            matchedNames.toString(),
                            processingResult);
                }

                if (processingResult) {
                    unmatchedAnnotations.keySet().removeAll(matchedNames);
                }

            }
        }
        unmatchedAnnotations.remove("");

        if (lint && unmatchedAnnotations.size() > 0) {
            // Remove annotations processed by javac
            unmatchedAnnotations.keySet().removeAll(platformAnnotations);
            if (unmatchedAnnotations.size() > 0) {
                log.warning("proc.annotations.without.processors",
                            unmatchedAnnotations.keySet());
            }
        }

        // Run contributing processors that haven't run yet
        psi.runContributingProcs(renv);
    }

    /**
     * Computes the set of annotations on the symbol in question.
     * Leave class public for external testing purposes.
     */
    public static class ComputeAnnotationSet extends
        ElementScanner9<Set<TypeElement>, Set<TypeElement>> {
        final Elements elements;

        public ComputeAnnotationSet(Elements elements) {
            super();
            this.elements = elements;
        }

        @Override @DefinedBy(Api.LANGUAGE_MODEL)
        public Set<TypeElement> visitPackage(PackageElement e, Set<TypeElement> p) {
            // Don't scan enclosed elements of a package
            return p;
        }

        @Override @DefinedBy(Api.LANGUAGE_MODEL)
        public Set<TypeElement> visitType(TypeElement e, Set<TypeElement> p) {
            // Type parameters are not considered to be enclosed by a type
            scan(e.getTypeParameters(), p);
            return super.visitType(e, p);
        }

        @Override @DefinedBy(Api.LANGUAGE_MODEL)
        public Set<TypeElement> visitExecutable(ExecutableElement e, Set<TypeElement> p) {
            // Type parameters are not considered to be enclosed by an executable
            scan(e.getTypeParameters(), p);
            return super.visitExecutable(e, p);
        }

        void addAnnotations(Element e, Set<TypeElement> p) {
            for (AnnotationMirror annotationMirror :
                     elements.getAllAnnotationMirrors(e) ) {
                Element e2 = annotationMirror.getAnnotationType().asElement();
                p.add((TypeElement) e2);
            }
        }

        @Override @DefinedBy(Api.LANGUAGE_MODEL)
        public Set<TypeElement> scan(Element e, Set<TypeElement> p) {
            addAnnotations(e, p);
            return super.scan(e, p);
        }
    }

    private boolean callProcessor(Processor proc,
                                         Set<? extends TypeElement> tes,
                                         RoundEnvironment renv) {
        try {
            return proc.process(tes, renv);
        } catch (ClassFinder.BadClassFile ex) {
            log.error("proc.cant.access.1", ex.sym, ex.getDetailValue());
            return false;
        } catch (CompletionFailure ex) {
            StringWriter out = new StringWriter();
            ex.printStackTrace(new PrintWriter(out));
            log.error("proc.cant.access", ex.sym, ex.getDetailValue(), out.toString());
            return false;
        } catch (ClientCodeException e) {
            throw e;
        } catch (Throwable t) {
            throw new AnnotationProcessingError(t);
        }
    }

    /**
     * Helper object for a single round of annotation processing.
     */
    class Round {
        /** The round number. */
        final int number;
        /** The diagnostic handler for the round. */
        final Log.DeferredDiagnosticHandler deferredDiagnosticHandler;

        /** The ASTs to be compiled. */
        List<JCCompilationUnit> roots;
        /** The trees that need to be cleaned - includes roots and implicitly parsed trees. */
        Set<JCCompilationUnit> treesToClean;
        /** The classes to be compiler that have were generated. */
        Map<String, JavaFileObject> genClassFiles;

        /** The set of annotations to be processed this round. */
        Set<TypeElement> annotationsPresent;
        /** The set of top level classes to be processed this round. */
        List<ClassSymbol> topLevelClasses;
        /** The set of package-info files to be processed this round. */
        List<PackageSymbol> packageInfoFiles;

        /** Create a round (common code). */
        private Round(int number, Set<JCCompilationUnit> treesToClean,
                Log.DeferredDiagnosticHandler deferredDiagnosticHandler) {
            this.number = number;

            if (number == 1) {
                Assert.checkNonNull(deferredDiagnosticHandler);
                this.deferredDiagnosticHandler = deferredDiagnosticHandler;
            } else {
                this.deferredDiagnosticHandler = new Log.DeferredDiagnosticHandler(log);
                compiler.setDeferredDiagnosticHandler(this.deferredDiagnosticHandler);
            }

            // the following will be populated as needed
            topLevelClasses  = List.nil();
            packageInfoFiles = List.nil();
            this.treesToClean = treesToClean;
        }

        /** Create the first round. */
        Round(List<JCCompilationUnit> roots,
              List<ClassSymbol> classSymbols,
              Set<JCCompilationUnit> treesToClean,
              Log.DeferredDiagnosticHandler deferredDiagnosticHandler) {
            this(1, treesToClean, deferredDiagnosticHandler);
            this.roots = roots;
            genClassFiles = new HashMap<>();

            // The reverse() in the following line is to maintain behavioural
            // compatibility with the previous revision of the code. Strictly speaking,
            // it should not be necessary, but a javah golden file test fails without it.
            topLevelClasses =
                getTopLevelClasses(roots).prependList(classSymbols.reverse());

            packageInfoFiles = getPackageInfoFiles(roots);

            findAnnotationsPresent();
        }

        /** Create a new round. */
        private Round(Round prev,
                Set<JavaFileObject> newSourceFiles, Map<String,JavaFileObject> newClassFiles) {
            this(prev.number+1, prev.treesToClean, null);
            prev.newRound();
            this.genClassFiles = prev.genClassFiles;

            List<JCCompilationUnit> parsedFiles = compiler.parseFiles(newSourceFiles);
            roots = prev.roots.appendList(parsedFiles);

            // Check for errors after parsing
            if (unrecoverableError())
                return;

            enterClassFiles(genClassFiles);
            List<ClassSymbol> newClasses = enterClassFiles(newClassFiles);
            genClassFiles.putAll(newClassFiles);
            enterTrees(roots);

            if (unrecoverableError())
                return;

            topLevelClasses = join(
                    getTopLevelClasses(parsedFiles),
                    getTopLevelClassesFromClasses(newClasses));

            packageInfoFiles = join(
                    getPackageInfoFiles(parsedFiles),
                    getPackageInfoFilesFromClasses(newClasses));

            findAnnotationsPresent();
        }

        /** Create the next round to be used. */
        Round next(Set<JavaFileObject> newSourceFiles, Map<String, JavaFileObject> newClassFiles) {
            return new Round(this, newSourceFiles, newClassFiles);
        }

        /** Prepare the compiler for the final compilation. */
        void finalCompiler() {
            newRound();
        }

        /** Return the number of errors found so far in this round.
         * This may include uncoverable errors, such as parse errors,
         * and transient errors, such as missing symbols. */
        int errorCount() {
            return compiler.errorCount();
        }

        /** Return the number of warnings found so far in this round. */
        int warningCount() {
            return compiler.warningCount();
        }

        /** Return whether or not an unrecoverable error has occurred. */
        boolean unrecoverableError() {
            if (messager.errorRaised())
                return true;

            for (JCDiagnostic d: deferredDiagnosticHandler.getDiagnostics()) {
                switch (d.getKind()) {
                    case WARNING:
                        if (werror)
                            return true;
                        break;

                    case ERROR:
                        if (fatalErrors || !d.isFlagSet(RECOVERABLE))
                            return true;
                        break;
                }
            }

            return false;
        }

        /** Find the set of annotations present in the set of top level
         *  classes and package info files to be processed this round. */
        void findAnnotationsPresent() {
            ComputeAnnotationSet annotationComputer = new ComputeAnnotationSet(elementUtils);
            // Use annotation processing to compute the set of annotations present
            annotationsPresent = new LinkedHashSet<>();
            for (ClassSymbol classSym : topLevelClasses)
                annotationComputer.scan(classSym, annotationsPresent);
            for (PackageSymbol pkgSym : packageInfoFiles)
                annotationComputer.scan(pkgSym, annotationsPresent);
        }

        /** Enter a set of generated class files. */
        private List<ClassSymbol> enterClassFiles(Map<String, JavaFileObject> classFiles) {
            List<ClassSymbol> list = List.nil();

            for (Map.Entry<String,JavaFileObject> entry : classFiles.entrySet()) {
                Name name = names.fromString(entry.getKey());
                JavaFileObject file = entry.getValue();
                if (file.getKind() != JavaFileObject.Kind.CLASS)
                    throw new AssertionError(file);
                ClassSymbol cs;
                if (isPkgInfo(file, JavaFileObject.Kind.CLASS)) {
                    Name packageName = Convert.packagePart(name);
                    PackageSymbol p = symtab.enterPackage(packageName);
                    if (p.package_info == null)
                        p.package_info = symtab.enterClass(Convert.shortName(name), p);
                    cs = p.package_info;
                    cs.reset();
                    if (cs.classfile == null)
                        cs.classfile = file;
                    cs.completer = initialCompleter;
                } else {
                    cs = symtab.enterClass(name);
                    cs.reset();
                    cs.classfile = file;
                    cs.completer = initialCompleter;
                }
                list = list.prepend(cs);
            }
            return list.reverse();
        }

        /** Enter a set of syntax trees. */
        private void enterTrees(List<JCCompilationUnit> roots) {
            compiler.enterTrees(roots);
        }

        /** Run a processing round. */
        void run(boolean lastRound, boolean errorStatus) {
            printRoundInfo(lastRound);

            if (!taskListener.isEmpty())
                taskListener.started(new TaskEvent(TaskEvent.Kind.ANNOTATION_PROCESSING_ROUND));

            try {
                if (lastRound) {
                    filer.setLastRound(true);
                    Set<Element> emptyRootElements = Collections.emptySet(); // immutable
                    RoundEnvironment renv = new JavacRoundEnvironment(true,
                            errorStatus,
                            emptyRootElements,
                            JavacProcessingEnvironment.this);
                    discoveredProcs.iterator().runContributingProcs(renv);
                } else {
                    discoverAndRunProcs(annotationsPresent, topLevelClasses, packageInfoFiles);
                }
            } catch (Throwable t) {
                // we're specifically expecting Abort here, but if any Throwable
                // comes by, we should flush all deferred diagnostics, rather than
                // drop them on the ground.
                deferredDiagnosticHandler.reportDeferredDiagnostics();
                log.popDiagnosticHandler(deferredDiagnosticHandler);
                compiler.setDeferredDiagnosticHandler(null);
                throw t;
            } finally {
                if (!taskListener.isEmpty())
                    taskListener.finished(new TaskEvent(TaskEvent.Kind.ANNOTATION_PROCESSING_ROUND));
            }
        }

        void showDiagnostics(boolean showAll) {
            Set<JCDiagnostic.Kind> kinds = EnumSet.allOf(JCDiagnostic.Kind.class);
            if (!showAll) {
                // suppress errors, which are all presumed to be transient resolve errors
                kinds.remove(JCDiagnostic.Kind.ERROR);
            }
            deferredDiagnosticHandler.reportDeferredDiagnostics(kinds);
            log.popDiagnosticHandler(deferredDiagnosticHandler);
            compiler.setDeferredDiagnosticHandler(null);
        }

        /** Print info about this round. */
        private void printRoundInfo(boolean lastRound) {
            if (printRounds || verbose) {
                List<ClassSymbol> tlc = lastRound ? List.<ClassSymbol>nil() : topLevelClasses;
                Set<TypeElement> ap = lastRound ? Collections.<TypeElement>emptySet() : annotationsPresent;
                log.printLines("x.print.rounds",
                        number,
                        "{" + tlc.toString(", ") + "}",
                        ap,
                        lastRound);
            }
        }

        /** Prepare for new round of annotation processing. Cleans trees, resets symbols, and
         * asks selected services to prepare to a new round of annotation processing.
         */
        private void newRound() {
            //ensure treesToClean contains all trees, including implicitly parsed ones
            for (Env<AttrContext> env : enter.getEnvs()) {
                treesToClean.add(env.toplevel);
            }
            for (JCCompilationUnit node : treesToClean) {
                treeCleaner.scan(node);
            }
            chk.newRound();
            enter.newRound();
            filer.newRound();
            messager.newRound();
            compiler.newRound();
            types.newRound();

            boolean foundError = false;

            for (ClassSymbol cs : symtab.classes.values()) {
                if (cs.kind == ERR) {
                    foundError = true;
                    break;
                }
            }

            if (foundError) {
                for (ClassSymbol cs : symtab.classes.values()) {
                    if (cs.classfile != null || cs.kind == ERR) {
                        cs.reset();
                        cs.type = new ClassType(cs.type.getEnclosingType(), null, cs);
                        if (cs.isCompleted()) {
                            cs.completer = initialCompleter;
                        }
                    }
                }
            }
        }
    }


    // TODO: internal catch clauses?; catch and rethrow an annotation
    // processing error
    public boolean doProcessing(List<JCCompilationUnit> roots,
                                List<ClassSymbol> classSymbols,
                                Iterable<? extends PackageSymbol> pckSymbols,
                                Log.DeferredDiagnosticHandler deferredDiagnosticHandler) {
        final Set<JCCompilationUnit> treesToClean =
                Collections.newSetFromMap(new IdentityHashMap<JCCompilationUnit, Boolean>());

        //fill already attributed implicit trees:
        for (Env<AttrContext> env : enter.getEnvs()) {
            treesToClean.add(env.toplevel);
        }

        Set<PackageSymbol> specifiedPackages = new LinkedHashSet<>();
        for (PackageSymbol psym : pckSymbols)
            specifiedPackages.add(psym);
        this.specifiedPackages = Collections.unmodifiableSet(specifiedPackages);

        Round round = new Round(roots, classSymbols, treesToClean, deferredDiagnosticHandler);

        boolean errorStatus;
        boolean moreToDo;
        do {
            // Run processors for round n
            round.run(false, false);

            // Processors for round n have run to completion.
            // Check for errors and whether there is more work to do.
            errorStatus = round.unrecoverableError();
            moreToDo = moreToDo();

            round.showDiagnostics(errorStatus || showResolveErrors);

            // Set up next round.
            // Copy mutable collections returned from filer.
            round = round.next(
                    new LinkedHashSet<>(filer.getGeneratedSourceFileObjects()),
                    new LinkedHashMap<>(filer.getGeneratedClasses()));

             // Check for errors during setup.
            if (round.unrecoverableError())
                errorStatus = true;

        } while (moreToDo && !errorStatus);

        // run last round
        round.run(true, errorStatus);
        round.showDiagnostics(true);

        filer.warnIfUnclosedFiles();
        warnIfUnmatchedOptions();

        /*
         * If an annotation processor raises an error in a round,
         * that round runs to completion and one last round occurs.
         * The last round may also occur because no more source or
         * class files have been generated.  Therefore, if an error
         * was raised on either of the last *two* rounds, the compile
         * should exit with a nonzero exit code.  The current value of
         * errorStatus holds whether or not an error was raised on the
         * second to last round; errorRaised() gives the error status
         * of the last round.
         */
        if (messager.errorRaised()
                || werror && round.warningCount() > 0 && round.errorCount() > 0)
            errorStatus = true;

        Set<JavaFileObject> newSourceFiles =
                new LinkedHashSet<>(filer.getGeneratedSourceFileObjects());
        roots = round.roots;

        errorStatus = errorStatus || (compiler.errorCount() > 0);

        if (!errorStatus)
            round.finalCompiler();

        if (newSourceFiles.size() > 0)
            roots = roots.appendList(compiler.parseFiles(newSourceFiles));

        errorStatus = errorStatus || (compiler.errorCount() > 0);

        // Free resources
        this.close();

        if (!taskListener.isEmpty())
            taskListener.finished(new TaskEvent(TaskEvent.Kind.ANNOTATION_PROCESSING));

        if (errorStatus) {
            if (compiler.errorCount() == 0)
                compiler.log.nerrors++;
            return true;
        }

        compiler.enterTreesIfNeeded(roots);

        return true;
    }

    private void warnIfUnmatchedOptions() {
        if (!unmatchedProcessorOptions.isEmpty()) {
            log.warning("proc.unmatched.processor.options", unmatchedProcessorOptions.toString());
        }
    }

    /**
     * Free resources related to annotation processing.
     */
    public void close() {
        filer.close();
        if (discoveredProcs != null) // Make calling close idempotent
            discoveredProcs.close();
        discoveredProcs = null;
    }

    private List<ClassSymbol> getTopLevelClasses(List<? extends JCCompilationUnit> units) {
        List<ClassSymbol> classes = List.nil();
        for (JCCompilationUnit unit : units) {
            for (JCTree node : unit.defs) {
                if (node.hasTag(JCTree.Tag.CLASSDEF)) {
                    ClassSymbol sym = ((JCClassDecl) node).sym;
                    Assert.checkNonNull(sym);
                    classes = classes.prepend(sym);
                }
            }
        }
        return classes.reverse();
    }

    private List<ClassSymbol> getTopLevelClassesFromClasses(List<? extends ClassSymbol> syms) {
        List<ClassSymbol> classes = List.nil();
        for (ClassSymbol sym : syms) {
            if (!isPkgInfo(sym)) {
                classes = classes.prepend(sym);
            }
        }
        return classes.reverse();
    }

    private List<PackageSymbol> getPackageInfoFiles(List<? extends JCCompilationUnit> units) {
        List<PackageSymbol> packages = List.nil();
        for (JCCompilationUnit unit : units) {
            if (isPkgInfo(unit.sourcefile, JavaFileObject.Kind.SOURCE)) {
                packages = packages.prepend(unit.packge);
            }
        }
        return packages.reverse();
    }

    private List<PackageSymbol> getPackageInfoFilesFromClasses(List<? extends ClassSymbol> syms) {
        List<PackageSymbol> packages = List.nil();
        for (ClassSymbol sym : syms) {
            if (isPkgInfo(sym)) {
                packages = packages.prepend((PackageSymbol) sym.owner);
            }
        }
        return packages.reverse();
    }

    // avoid unchecked warning from use of varargs
    private static <T> List<T> join(List<T> list1, List<T> list2) {
        return list1.appendList(list2);
    }

    private boolean isPkgInfo(JavaFileObject fo, JavaFileObject.Kind kind) {
        return fo.isNameCompatible("package-info", kind);
    }

    private boolean isPkgInfo(ClassSymbol sym) {
        return isPkgInfo(sym.classfile, JavaFileObject.Kind.CLASS) && (sym.packge().package_info == sym);
    }

    /*
     * Called retroactively to determine if a class loader was required,
     * after we have failed to create one.
     */
    private boolean needClassLoader(String procNames, Iterable<? extends File> workingpath) {
        if (procNames != null)
            return true;

        URL[] urls = new URL[1];
        for(File pathElement : workingpath) {
            try {
                urls[0] = pathElement.toURI().toURL();
                if (ServiceProxy.hasService(Processor.class, urls))
                    return true;
            } catch (MalformedURLException ex) {
                throw new AssertionError(ex);
            }
            catch (ServiceProxy.ServiceConfigurationError e) {
                log.error("proc.bad.config.file", e.getLocalizedMessage());
                return true;
            }
        }

        return false;
    }

    class ImplicitCompleter implements Completer {

        private final JCCompilationUnit topLevel;

        public ImplicitCompleter(JCCompilationUnit topLevel) {
            this.topLevel = topLevel;
        }

        @Override public void complete(Symbol sym) throws CompletionFailure {
            compiler.readSourceFile(topLevel, (ClassSymbol) sym);
        }
    }

    private final TreeScanner treeCleaner = new TreeScanner() {
            public void scan(JCTree node) {
                super.scan(node);
                if (node != null)
                    node.type = null;
            }
            JCCompilationUnit topLevel;
            public void visitTopLevel(JCCompilationUnit node) {
                if (node.packge != null) {
                    if (node.packge.package_info != null) {
                        node.packge.package_info.reset();
                    }
                    node.packge.reset();
                }
                node.packge = null;
                topLevel = node;
                try {
                    super.visitTopLevel(node);
                } finally {
                    topLevel = null;
                }
            }
            public void visitClassDef(JCClassDecl node) {
                if (node.sym != null) {
                    node.sym.reset();
                    node.sym.completer = new ImplicitCompleter(topLevel);
                }
                node.sym = null;
                super.visitClassDef(node);
            }
            public void visitMethodDef(JCMethodDecl node) {
                node.sym = null;
                super.visitMethodDef(node);
            }
            public void visitVarDef(JCVariableDecl node) {
                node.sym = null;
                super.visitVarDef(node);
            }
            public void visitNewClass(JCNewClass node) {
                node.constructor = null;
                super.visitNewClass(node);
            }
            public void visitAssignop(JCAssignOp node) {
                node.operator = null;
                super.visitAssignop(node);
            }
            public void visitUnary(JCUnary node) {
                node.operator = null;
                super.visitUnary(node);
            }
            public void visitBinary(JCBinary node) {
                node.operator = null;
                super.visitBinary(node);
            }
            public void visitSelect(JCFieldAccess node) {
                node.sym = null;
                super.visitSelect(node);
            }
            public void visitIdent(JCIdent node) {
                node.sym = null;
                super.visitIdent(node);
            }
            public void visitAnnotation(JCAnnotation node) {
                node.attribute = null;
                super.visitAnnotation(node);
            }
        };


    private boolean moreToDo() {
        return filer.newFiles();
    }

    /**
     * {@inheritDoc}
     *
     * Command line options suitable for presenting to annotation
     * processors.
     * {@literal "-Afoo=bar"} should be {@literal "-Afoo" => "bar"}.
     */
    @DefinedBy(Api.ANNOTATION_PROCESSING)
    public Map<String,String> getOptions() {
        return processorOptions;
    }

    @DefinedBy(Api.ANNOTATION_PROCESSING)
    public Messager getMessager() {
        return messager;
    }

    @DefinedBy(Api.ANNOTATION_PROCESSING)
    public Filer getFiler() {
        return filer;
    }

    @DefinedBy(Api.ANNOTATION_PROCESSING)
    public JavacElements getElementUtils() {
        return elementUtils;
    }

    @DefinedBy(Api.ANNOTATION_PROCESSING)
    public JavacTypes getTypeUtils() {
        return typeUtils;
    }

    @DefinedBy(Api.ANNOTATION_PROCESSING)
    public SourceVersion getSourceVersion() {
        return Source.toSourceVersion(source);
    }

    @DefinedBy(Api.ANNOTATION_PROCESSING)
    public Locale getLocale() {
        return messages.getCurrentLocale();
    }

    public Set<Symbol.PackageSymbol> getSpecifiedPackages() {
        return specifiedPackages;
    }

    public static final Pattern noMatches  = Pattern.compile("(\\P{all})+");

    /**
     * Convert import-style string for supported annotations into a
     * regex matching that string.  If the string is a valid
     * import-style string, return a regex that won't match anything.
     */
    private static Pattern importStringToPattern(String s, Processor p, Log log) {
        if (MatchingUtils.isValidImportString(s)) {
            return MatchingUtils.validImportStringToPattern(s);
        } else {
            log.warning("proc.malformed.supported.string", s, p.getClass().getName());
            return noMatches; // won't match any valid identifier
        }
    }

    /**
     * For internal use only.  This method may be removed without warning.
     */
    public Context getContext() {
        return context;
    }

    /**
     * For internal use only.  This method may be removed without warning.
     */
    public ClassLoader getProcessorClassLoader() {
        return processorClassLoader;
    }

    public String toString() {
        return "javac ProcessingEnvironment";
    }

    public static boolean isValidOptionName(String optionName) {
        for(String s : optionName.split("\\.", -1)) {
            if (!SourceVersion.isIdentifier(s))
                return false;
        }
        return true;
    }
}
