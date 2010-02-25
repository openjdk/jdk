/*
 * Copyright 2005-2009 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.tools.javac.processing;

import java.lang.reflect.*;
import java.util.*;
import java.util.regex.*;

import java.net.URL;
import java.io.Closeable;
import java.io.File;
import java.io.PrintWriter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.io.StringWriter;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.util.*;
import javax.tools.JavaFileManager;
import javax.tools.StandardJavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.DiagnosticListener;

import com.sun.source.util.AbstractTypeProcessor;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import com.sun.tools.javac.api.JavacTaskImpl;
import com.sun.tools.javac.code.*;
import com.sun.tools.javac.code.Symbol.*;
import com.sun.tools.javac.file.JavacFileManager;
import com.sun.tools.javac.jvm.*;
import com.sun.tools.javac.main.JavaCompiler;
import com.sun.tools.javac.main.JavaCompiler.CompileState;
import com.sun.tools.javac.model.JavacElements;
import com.sun.tools.javac.model.JavacTypes;
import com.sun.tools.javac.parser.*;
import com.sun.tools.javac.tree.*;
import com.sun.tools.javac.tree.JCTree.*;
import com.sun.tools.javac.util.Abort;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Convert;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;
import com.sun.tools.javac.util.Log;
import com.sun.tools.javac.util.JavacMessages;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Names;
import com.sun.tools.javac.util.Options;

import static javax.tools.StandardLocation.*;

/**
 * Objects of this class hold and manage the state needed to support
 * annotation processing.
 *
 * <p><b>This is NOT part of any API supported by Sun Microsystems.
 * If you write code that depends on this, you do so at your own risk.
 * This code and its internal interfaces are subject to change or
 * deletion without notice.</b>
 */
public class JavacProcessingEnvironment implements ProcessingEnvironment, Closeable {
    Options options;

    private final boolean printProcessorInfo;
    private final boolean printRounds;
    private final boolean verbose;
    private final boolean lint;
    private final boolean procOnly;
    private final boolean fatalErrors;
    private boolean foundTypeProcessors;

    private final JavacFiler filer;
    private final JavacMessager messager;
    private final JavacElements elementUtils;
    private final JavacTypes typeUtils;

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
    Log log;

    /**
     * Source level of the compile.
     */
    Source source;

    private ClassLoader processorClassLoader;

    /**
     * JavacMessages object used for localization
     */
    private JavacMessages messages;

    private Context context;

    public JavacProcessingEnvironment(Context context, Iterable<? extends Processor> processors) {
        options = Options.instance(context);
        this.context = context;
        log = Log.instance(context);
        source = Source.instance(context);
        printProcessorInfo = options.get("-XprintProcessorInfo") != null;
        printRounds = options.get("-XprintRounds") != null;
        verbose = options.get("-verbose") != null;
        lint = options.lint("processing");
        procOnly = options.get("-proc:only") != null ||
            options.get("-Xprint") != null;
        fatalErrors = options.get("fatalEnterError") != null;
        platformAnnotations = initPlatformAnnotations();
        foundTypeProcessors = false;

        // Initialize services before any processors are initialzied
        // in case processors use them.
        filer = new JavacFiler(context);
        messager = new JavacMessager(context, this);
        elementUtils = new JavacElements(context);
        typeUtils = new JavacTypes(context);
        processorOptions = initProcessorOptions(context);
        unmatchedProcessorOptions = initUnmatchedProcessorOptions();
        messages = JavacMessages.instance(context);
        initProcessorIterator(context, processors);
    }

    private Set<String> initPlatformAnnotations() {
        Set<String> platformAnnotations = new HashSet<String>();
        platformAnnotations.add("java.lang.Deprecated");
        platformAnnotations.add("java.lang.Override");
        platformAnnotations.add("java.lang.SuppressWarnings");
        platformAnnotations.add("java.lang.annotation.Documented");
        platformAnnotations.add("java.lang.annotation.Inherited");
        platformAnnotations.add("java.lang.annotation.Retention");
        platformAnnotations.add("java.lang.annotation.Target");
        return Collections.unmodifiableSet(platformAnnotations);
    }

    private void initProcessorIterator(Context context, Iterable<? extends Processor> processors) {
        Log   log   = Log.instance(context);
        Iterator<? extends Processor> processorIterator;

        if (options.get("-Xprint") != null) {
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
            String processorNames = options.get("-processor");
            JavaFileManager fileManager = context.get(JavaFileManager.class);
            try {
                // If processorpath is not explicitly set, use the classpath.
                processorClassLoader = fileManager.hasLocation(ANNOTATION_PROCESSOR_PATH)
                    ? fileManager.getClassLoader(ANNOTATION_PROCESSOR_PATH)
                    : fileManager.getClassLoader(CLASS_PATH);

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
            } catch (SecurityException e) {
                /*
                 * A security exception will occur if we can't create a classloader.
                 * Ignore the exception if, with hindsight, we didn't need it anyway
                 * (i.e. no processor was specified either explicitly, or implicitly,
                 * in service configuration file.) Otherwise, we cannot continue.
                 */
                processorIterator = handleServiceLoaderUnavailability("proc.cant.create.loader", e);
            }
        }
        discoveredProcs = new DiscoveredProcessors(processorIterator);
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

            if (needClassLoader(options.get("-processor"), workingPath) )
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
     * iterator over annotations processors.  If
     * java.util.ServiceLoader is present use it, otherwise, use
     * sun.misc.Service, otherwise fail if a loader is needed.
     */
    private class ServiceIterator implements Iterator<Processor> {
        // The to-be-wrapped iterator.
        private Iterator<?> iterator;
        private Log log;
        private Class<?> loaderClass;
        private boolean jusl;
        private Object loader;

        ServiceIterator(ClassLoader classLoader, Log log) {
            String loadMethodName;

            this.log = log;
            try {
                try {
                    loaderClass = Class.forName("java.util.ServiceLoader");
                    loadMethodName = "load";
                    jusl = true;
                } catch (ClassNotFoundException cnfe) {
                    try {
                        loaderClass = Class.forName("sun.misc.Service");
                        loadMethodName = "providers";
                        jusl = false;
                    } catch (ClassNotFoundException cnfe2) {
                        // Fail softly if a loader is not actually needed.
                        this.iterator = handleServiceLoaderUnavailability("proc.no.service",
                                                                          null);
                        return;
                    }
                }

                // java.util.ServiceLoader.load or sun.misc.Service.providers
                Method loadMethod = loaderClass.getMethod(loadMethodName,
                                                          Class.class,
                                                          ClassLoader.class);

                Object result = loadMethod.invoke(null,
                                                  Processor.class,
                                                  classLoader);

                // For java.util.ServiceLoader, we have to call another
                // method to get the iterator.
                if (jusl) {
                    loader = result; // Store ServiceLoader to call reload later
                    Method m = loaderClass.getMethod("iterator");
                    result = m.invoke(result); // serviceLoader.iterator();
                }

                // The result should now be an iterator.
                this.iterator = (Iterator<?>) result;
            } catch (Throwable t) {
                log.error("proc.service.problem");
                throw new Abort(t);
            }
        }

        public boolean hasNext() {
            try {
                return iterator.hasNext();
            } catch (Throwable t) {
                if ("ServiceConfigurationError".
                    equals(t.getClass().getSimpleName())) {
                    log.error("proc.bad.config.file", t.getLocalizedMessage());
                }
                throw new Abort(t);
            }
        }

        public Processor next() {
            try {
                return (Processor)(iterator.next());
            } catch (Throwable t) {
                if ("ServiceConfigurationError".
                    equals(t.getClass().getSimpleName())) {
                    log.error("proc.bad.config.file", t.getLocalizedMessage());
                } else {
                    log.error("proc.processor.constructor.error", t.getLocalizedMessage());
                }
                throw new Abort(t);
            }
        }

        public void remove() {
            throw new UnsupportedOperationException();
        }

        public void close() {
            if (jusl) {
                try {
                    // Call java.util.ServiceLoader.reload
                    Method reloadMethod = loaderClass.getMethod("reload");
                    reloadMethod.invoke(loader);
                } catch(Exception e) {
                    ; // Ignore problems during a call to reload.
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

    private Map<String, String> initProcessorOptions(Context context) {
        Options options = Options.instance(context);
        Set<String> keySet = options.keySet();
        Map<String, String> tempOptions = new LinkedHashMap<String, String>();

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

        return Collections.unmodifiableMap(tempOptions);
    }

    private Set<String> initUnmatchedProcessorOptions() {
        Set<String> unmatchedProcessorOptions = new HashSet<String>();
        unmatchedProcessorOptions.addAll(processorOptions.keySet());
        return unmatchedProcessorOptions;
    }

    /**
     * State about how a processor has been used by the tool.  If a
     * processor has been used on a prior round, its process method is
     * called on all subsequent rounds, perhaps with an empty set of
     * annotations to process.  The {@code annotatedSupported} method
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

                supportedAnnotationPatterns = new ArrayList<Pattern>();
                for (String importString : processor.getSupportedAnnotationTypes()) {
                    supportedAnnotationPatterns.add(importStringToPattern(importString,
                                                                          processor,
                                                                          log));
                }

                supportedOptionNames = new ArrayList<String>();
                for (String optionName : processor.getSupportedOptions() ) {
                    if (checkOptionName(optionName, log))
                        supportedOptionNames.add(optionName);
                }

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
            this.procStateList = new ArrayList<ProcessorState>();
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

    private void discoverAndRunProcs(Context context,
                                     Set<TypeElement> annotationsPresent,
                                     List<ClassSymbol> topLevelClasses,
                                     List<PackageSymbol> packageInfoFiles) {
        // Writer for -XprintRounds and -XprintProcessorInfo data
        PrintWriter xout = context.get(Log.outKey);

        Map<String, TypeElement> unmatchedAnnotations =
            new HashMap<String, TypeElement>(annotationsPresent.size());

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

        Set<Element> rootElements = new LinkedHashSet<Element>();
        rootElements.addAll(topLevelClasses);
        rootElements.addAll(packageInfoFiles);
        rootElements = Collections.unmodifiableSet(rootElements);

        RoundEnvironment renv = new JavacRoundEnvironment(false,
                                                          false,
                                                          rootElements,
                                                          JavacProcessingEnvironment.this);

        while(unmatchedAnnotations.size() > 0 && psi.hasNext() ) {
            ProcessorState ps = psi.next();
            Set<String>  matchedNames = new HashSet<String>();
            Set<TypeElement> typeElements = new LinkedHashSet<TypeElement>();

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
                foundTypeProcessors = foundTypeProcessors || (ps.processor instanceof AbstractTypeProcessor);
                boolean processingResult = callProcessor(ps.processor, typeElements, renv);
                ps.contributed = true;
                ps.removeSupportedOptions(unmatchedProcessorOptions);

                if (printProcessorInfo || verbose) {
                    xout.println(Log.getLocalizedString("x.print.processor.info",
                                                        ps.processor.getClass().getName(),
                                                        matchedNames.toString(),
                                                        processingResult));
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
                log = Log.instance(context);
                log.warning("proc.annotations.without.processors",
                            unmatchedAnnotations.keySet());
            }
        }

        // Run contributing processors that haven't run yet
        psi.runContributingProcs(renv);

        // Debugging
        if (options.get("displayFilerState") != null)
            filer.displayState();
    }

    /**
     * Computes the set of annotations on the symbol in question.
     * Leave class public for external testing purposes.
     */
    public static class ComputeAnnotationSet extends
        ElementScanner6<Set<TypeElement>, Set<TypeElement>> {
        final Elements elements;

        public ComputeAnnotationSet(Elements elements) {
            super();
            this.elements = elements;
        }

        @Override
        public Set<TypeElement> visitPackage(PackageElement e, Set<TypeElement> p) {
            // Don't scan enclosed elements of a package
            return p;
        }

        @Override
         public Set<TypeElement> scan(Element e, Set<TypeElement> p) {
            for (AnnotationMirror annotationMirror :
                     elements.getAllAnnotationMirrors(e) ) {
                Element e2 = annotationMirror.getAnnotationType().asElement();
                p.add((TypeElement) e2);
            }
            return super.scan(e, p);
        }
    }

    private boolean callProcessor(Processor proc,
                                         Set<? extends TypeElement> tes,
                                         RoundEnvironment renv) {
        try {
            return proc.process(tes, renv);
        } catch (CompletionFailure ex) {
            StringWriter out = new StringWriter();
            ex.printStackTrace(new PrintWriter(out));
            log.error("proc.cant.access", ex.sym, ex.getDetailValue(), out.toString());
            return false;
        } catch (Throwable t) {
            throw new AnnotationProcessingError(t);
        }
    }


    // TODO: internal catch clauses?; catch and rethrow an annotation
    // processing error
    public JavaCompiler doProcessing(Context context,
                                     List<JCCompilationUnit> roots,
                                     List<ClassSymbol> classSymbols,
                                     Iterable<? extends PackageSymbol> pckSymbols)
        throws IOException {

        log = Log.instance(context);
        // Writer for -XprintRounds and -XprintProcessorInfo data
        PrintWriter xout = context.get(Log.outKey);
        TaskListener taskListener = context.get(TaskListener.class);


        AnnotationCollector collector = new AnnotationCollector();

        JavaCompiler compiler = JavaCompiler.instance(context);
        compiler.todo.clear(); // free the compiler's resources

        int round = 0;

        // List<JCAnnotation> annotationsPresentInSource = collector.findAnnotations(roots);
        List<ClassSymbol> topLevelClasses = getTopLevelClasses(roots);

        for (ClassSymbol classSym : classSymbols)
            topLevelClasses = topLevelClasses.prepend(classSym);
        List<PackageSymbol> packageInfoFiles =
            getPackageInfoFiles(roots);

        Set<PackageSymbol> specifiedPackages = new LinkedHashSet<PackageSymbol>();
        for (PackageSymbol psym : pckSymbols)
            specifiedPackages.add(psym);
        this.specifiedPackages = Collections.unmodifiableSet(specifiedPackages);

        // Use annotation processing to compute the set of annotations present
        Set<TypeElement> annotationsPresent = new LinkedHashSet<TypeElement>();
        ComputeAnnotationSet annotationComputer = new ComputeAnnotationSet(elementUtils);
        for (ClassSymbol classSym : topLevelClasses)
            annotationComputer.scan(classSym, annotationsPresent);
        for (PackageSymbol pkgSym : packageInfoFiles)
            annotationComputer.scan(pkgSym, annotationsPresent);

        Context currentContext = context;

        int roundNumber = 0;
        boolean errorStatus = false;

        runAround:
        while(true) {
            if (fatalErrors && compiler.errorCount() != 0) {
                errorStatus = true;
                break runAround;
            }

            this.context = currentContext;
            roundNumber++;
            printRoundInfo(xout, roundNumber, topLevelClasses, annotationsPresent, false);

            if (taskListener != null)
                taskListener.started(new TaskEvent(TaskEvent.Kind.ANNOTATION_PROCESSING_ROUND));

            try {
                discoverAndRunProcs(currentContext, annotationsPresent, topLevelClasses, packageInfoFiles);
            } finally {
                if (taskListener != null)
                    taskListener.finished(new TaskEvent(TaskEvent.Kind.ANNOTATION_PROCESSING_ROUND));
            }

            /*
             * Processors for round n have run to completion.  Prepare
             * for round (n+1) by checked for errors raised by
             * annotation processors and then checking for syntax
             * errors on any generated source files.
             */
            if (messager.errorRaised()) {
                errorStatus = true;
                break runAround;
            } else {
                if (moreToDo()) {
                    // annotationsPresentInSource = List.nil();
                    annotationsPresent = new LinkedHashSet<TypeElement>();
                    topLevelClasses  = List.nil();
                    packageInfoFiles = List.nil();

                    compiler.close(false);
                    currentContext = contextForNextRound(currentContext, true);

                    JavaFileManager fileManager = currentContext.get(JavaFileManager.class);

                    compiler = JavaCompiler.instance(currentContext);
                    List<JCCompilationUnit> parsedFiles = sourcesToParsedFiles(compiler);
                    roots = cleanTrees(roots).appendList(parsedFiles);

                    // Check for errors after parsing
                    if (log.unrecoverableError) {
                        errorStatus = true;
                        break runAround;
                    } else {
                        List<ClassSymbol> newClasses = enterNewClassFiles(currentContext);
                        compiler.enterTrees(roots);

                        // annotationsPresentInSource =
                        // collector.findAnnotations(parsedFiles);
                        ListBuffer<ClassSymbol> tlc = new ListBuffer<ClassSymbol>();
                        tlc.appendList(getTopLevelClasses(parsedFiles));
                        tlc.appendList(getTopLevelClassesFromClasses(newClasses));
                        topLevelClasses  = tlc.toList();

                        ListBuffer<PackageSymbol> pif = new ListBuffer<PackageSymbol>();
                        pif.appendList(getPackageInfoFiles(parsedFiles));
                        pif.appendList(getPackageInfoFilesFromClasses(newClasses));
                        packageInfoFiles = pif.toList();

                        annotationsPresent = new LinkedHashSet<TypeElement>();
                        for (ClassSymbol classSym : topLevelClasses)
                            annotationComputer.scan(classSym, annotationsPresent);
                        for (PackageSymbol pkgSym : packageInfoFiles)
                            annotationComputer.scan(pkgSym, annotationsPresent);

                        updateProcessingState(currentContext, false);
                    }
                } else
                    break runAround; // No new files
            }
        }
        roots = runLastRound(xout, roundNumber, errorStatus, compiler, roots, taskListener);
        // Set error status for any files compiled and generated in
        // the last round
        if (log.unrecoverableError)
            errorStatus = true;

        compiler.close(false);
        currentContext = contextForNextRound(currentContext, true);
        compiler = JavaCompiler.instance(currentContext);

        filer.newRound(currentContext, true);
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
        errorStatus = errorStatus || messager.errorRaised();


        // Free resources
        this.close();

        if (taskListener != null)
            taskListener.finished(new TaskEvent(TaskEvent.Kind.ANNOTATION_PROCESSING));

        if (errorStatus) {
            compiler.log.nerrors += messager.errorCount();
            if (compiler.errorCount() == 0)
                compiler.log.nerrors++;
        } else if (procOnly && !foundTypeProcessors) {
            compiler.todo.clear();
        } else { // Final compilation
            compiler.close(false);
            currentContext = contextForNextRound(currentContext, true);
            this.context = currentContext;
            updateProcessingState(currentContext, true);
            compiler = JavaCompiler.instance(currentContext);
            if (procOnly && foundTypeProcessors)
                compiler.shouldStopPolicy = CompileState.FLOW;

            if (true) {
                compiler.enterTrees(cleanTrees(roots));
            } else {
                List<JavaFileObject> fileObjects = List.nil();
                for (JCCompilationUnit unit : roots)
                    fileObjects = fileObjects.prepend(unit.getSourceFile());
                roots = null;
                compiler.enterTrees(compiler.parseFiles(fileObjects.reverse()));
            }
        }

        return compiler;
    }

    private List<JCCompilationUnit> sourcesToParsedFiles(JavaCompiler compiler)
        throws IOException {
        List<JavaFileObject> fileObjects = List.nil();
        for (JavaFileObject jfo : filer.getGeneratedSourceFileObjects() ) {
            fileObjects = fileObjects.prepend(jfo);
        }

       return compiler.parseFiles(fileObjects);
    }

    // Call the last round of annotation processing
    private List<JCCompilationUnit> runLastRound(PrintWriter xout,
                                                 int roundNumber,
                                                 boolean errorStatus,
                                                 JavaCompiler compiler,
                                                 List<JCCompilationUnit> roots,
                              TaskListener taskListener) throws IOException {
        roundNumber++;
        List<ClassSymbol> noTopLevelClasses = List.nil();
        Set<TypeElement> noAnnotations =  Collections.emptySet();
        printRoundInfo(xout, roundNumber, noTopLevelClasses, noAnnotations, true);

        Set<Element> emptyRootElements = Collections.emptySet(); // immutable
        RoundEnvironment renv = new JavacRoundEnvironment(true,
                                                          errorStatus,
                                                          emptyRootElements,
                                                          JavacProcessingEnvironment.this);
        if (taskListener != null)
            taskListener.started(new TaskEvent(TaskEvent.Kind.ANNOTATION_PROCESSING_ROUND));

        try {
            discoveredProcs.iterator().runContributingProcs(renv);
        } finally {
            if (taskListener != null)
                taskListener.finished(new TaskEvent(TaskEvent.Kind.ANNOTATION_PROCESSING_ROUND));
        }

        // Add any sources generated during the last round to the set
        // of files to be compiled.
        if (moreToDo()) {
            List<JCCompilationUnit> parsedFiles = sourcesToParsedFiles(compiler);
            roots = cleanTrees(roots).appendList(parsedFiles);
        }

        return roots;
    }

    private void updateProcessingState(Context currentContext, boolean lastRound) {
        filer.newRound(currentContext, lastRound);
        messager.newRound(currentContext);

        elementUtils.setContext(currentContext);
        typeUtils.setContext(currentContext);
    }

    private void warnIfUnmatchedOptions() {
        if (!unmatchedProcessorOptions.isEmpty()) {
            log.warning("proc.unmatched.processor.options", unmatchedProcessorOptions.toString());
        }
    }

    private void printRoundInfo(PrintWriter xout,
                                int roundNumber,
                                List<ClassSymbol> topLevelClasses,
                                Set<TypeElement> annotationsPresent,
                                boolean lastRound) {
        if (printRounds || verbose) {
            xout.println(Log.getLocalizedString("x.print.rounds",
                                                roundNumber,
                                                "{" + topLevelClasses.toString(", ") + "}",
                                                annotationsPresent,
                                                lastRound));
        }
    }

    private List<ClassSymbol> enterNewClassFiles(Context currentContext) {
        ClassReader reader = ClassReader.instance(currentContext);
        Names names = Names.instance(currentContext);
        List<ClassSymbol> list = List.nil();

        for (Map.Entry<String,JavaFileObject> entry : filer.getGeneratedClasses().entrySet()) {
            Name name = names.fromString(entry.getKey());
            JavaFileObject file = entry.getValue();
            if (file.getKind() != JavaFileObject.Kind.CLASS)
                throw new AssertionError(file);
            ClassSymbol cs;
            if (isPkgInfo(file, JavaFileObject.Kind.CLASS)) {
                Name packageName = Convert.packagePart(name);
                PackageSymbol p = reader.enterPackage(packageName);
                if (p.package_info == null)
                    p.package_info = reader.enterClass(Convert.shortName(name), p);
                cs = p.package_info;
                if (cs.classfile == null)
                    cs.classfile = file;
            } else
                cs = reader.enterClass(name, file);
            list = list.prepend(cs);
        }
        return list.reverse();
    }

    /**
     * Free resources related to annotation processing.
     */
    public void close() throws IOException {
        filer.close();
        if (discoveredProcs != null) // Make calling close idempotent
            discoveredProcs.close();
        discoveredProcs = null;
        if (processorClassLoader != null && processorClassLoader instanceof Closeable)
            ((Closeable) processorClassLoader).close();
    }

    private List<ClassSymbol> getTopLevelClasses(List<? extends JCCompilationUnit> units) {
        List<ClassSymbol> classes = List.nil();
        for (JCCompilationUnit unit : units) {
            for (JCTree node : unit.defs) {
                if (node.getTag() == JCTree.CLASSDEF) {
                    classes = classes.prepend(((JCClassDecl) node).sym);
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

    private boolean isPkgInfo(JavaFileObject fo, JavaFileObject.Kind kind) {
        return fo.isNameCompatible("package-info", kind);
    }

    private boolean isPkgInfo(ClassSymbol sym) {
        return isPkgInfo(sym.classfile, JavaFileObject.Kind.CLASS) && (sym.packge().package_info == sym);
    }

    private Context contextForNextRound(Context context, boolean shareNames)
        throws IOException
    {
        Context next = new Context();

        Options options = Options.instance(context);
        assert options != null;
        next.put(Options.optionsKey, options);

        PrintWriter out = context.get(Log.outKey);
        assert out != null;
        next.put(Log.outKey, out);

        if (shareNames) {
            Names names = Names.instance(context);
            assert names != null;
            next.put(Names.namesKey, names);
        }

        DiagnosticListener<?> dl = context.get(DiagnosticListener.class);
        if (dl != null)
            next.put(DiagnosticListener.class, dl);

        TaskListener tl = context.get(TaskListener.class);
        if (tl != null)
            next.put(TaskListener.class, tl);

        JavaFileManager jfm = context.get(JavaFileManager.class);
        assert jfm != null;
        next.put(JavaFileManager.class, jfm);
        if (jfm instanceof JavacFileManager) {
            ((JavacFileManager)jfm).setContext(next);
        }

        Names names = Names.instance(context);
        assert names != null;
        next.put(Names.namesKey, names);

        Keywords keywords = Keywords.instance(context);
        assert(keywords != null);
        next.put(Keywords.keywordsKey, keywords);

        JavaCompiler oldCompiler = JavaCompiler.instance(context);
        JavaCompiler nextCompiler = JavaCompiler.instance(next);
        nextCompiler.initRound(oldCompiler);

        JavacTaskImpl task = context.get(JavacTaskImpl.class);
        if (task != null) {
            next.put(JavacTaskImpl.class, task);
            task.updateContext(next);
        }

        context.clear();
        return next;
    }

    /*
     * Called retroactively to determine if a class loader was required,
     * after we have failed to create one.
     */
    private boolean needClassLoader(String procNames, Iterable<? extends File> workingpath) {
        if (procNames != null)
            return true;

        String procPath;
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

    private static class AnnotationCollector extends TreeScanner {
        List<JCTree> path = List.nil();
        static final boolean verbose = false;
        List<JCAnnotation> annotations = List.nil();

        public List<JCAnnotation> findAnnotations(List<? extends JCTree> nodes) {
            annotations = List.nil();
            scan(nodes);
            List<JCAnnotation> found = annotations;
            annotations = List.nil();
            return found.reverse();
        }

        public void scan(JCTree node) {
            if (node == null)
                return;
            Symbol sym = TreeInfo.symbolFor(node);
            if (sym != null)
                path = path.prepend(node);
            super.scan(node);
            if (sym != null)
                path = path.tail;
        }

        public void visitAnnotation(JCAnnotation node) {
            annotations = annotations.prepend(node);
            if (verbose) {
                StringBuilder sb = new StringBuilder();
                for (JCTree tree : path.reverse()) {
                    System.err.print(sb);
                    System.err.println(TreeInfo.symbolFor(tree));
                    sb.append("  ");
                }
                System.err.print(sb);
                System.err.println(node);
            }
        }
    }

    private static <T extends JCTree> List<T> cleanTrees(List<T> nodes) {
        for (T node : nodes)
            treeCleaner.scan(node);
        return nodes;
    }

    private static TreeScanner treeCleaner = new TreeScanner() {
            public void scan(JCTree node) {
                super.scan(node);
                if (node != null)
                    node.type = null;
            }
            public void visitTopLevel(JCCompilationUnit node) {
                node.packge = null;
                super.visitTopLevel(node);
            }
            public void visitClassDef(JCClassDecl node) {
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
        };


    private boolean moreToDo() {
        return filer.newFiles();
    }

    /**
     * {@inheritdoc}
     *
     * Command line options suitable for presenting to annotation
     * processors.  "-Afoo=bar" should be "-Afoo" => "bar".
     */
    public Map<String,String> getOptions() {
        return processorOptions;
    }

    public Messager getMessager() {
        return messager;
    }

    public Filer getFiler() {
        return filer;
    }

    public JavacElements getElementUtils() {
        return elementUtils;
    }

    public JavacTypes getTypeUtils() {
        return typeUtils;
    }

    public SourceVersion getSourceVersion() {
        return Source.toSourceVersion(source);
    }

    public Locale getLocale() {
        return messages.getCurrentLocale();
    }

    public Set<Symbol.PackageSymbol> getSpecifiedPackages() {
        return specifiedPackages;
    }

    private static final Pattern allMatches = Pattern.compile(".*");
    public static final Pattern noMatches  = Pattern.compile("(\\P{all})+");

    /**
     * Convert import-style string for supported annotations into a
     * regex matching that string.  If the string is a valid
     * import-style string, return a regex that won't match anything.
     */
    private static Pattern importStringToPattern(String s, Processor p, Log log) {
        if (isValidImportString(s)) {
            return validImportStringToPattern(s);
        } else {
            log.warning("proc.malformed.supported.string", s, p.getClass().getName());
            return noMatches; // won't match any valid identifier
        }
    }

    /**
     * Return true if the argument string is a valid import-style
     * string specifying claimed annotations; return false otherwise.
     */
    public static boolean isValidImportString(String s) {
        if (s.equals("*"))
            return true;

        boolean valid = true;
        String t = s;
        int index = t.indexOf('*');

        if (index != -1) {
            // '*' must be last character...
            if (index == t.length() -1) {
                // ... any and preceding character must be '.'
                if ( index-1 >= 0 ) {
                    valid = t.charAt(index-1) == '.';
                    // Strip off ".*$" for identifier checks
                    t = t.substring(0, t.length()-2);
                }
            } else
                return false;
        }

        // Verify string is off the form (javaId \.)+ or javaId
        if (valid) {
            String[] javaIds = t.split("\\.", t.length()+2);
            for(String javaId: javaIds)
                valid &= SourceVersion.isIdentifier(javaId);
        }
        return valid;
    }

    public static Pattern validImportStringToPattern(String s) {
        if (s.equals("*")) {
            return allMatches;
        } else {
            String s_prime = s.replace(".", "\\.");

            if (s_prime.endsWith("*")) {
                s_prime =  s_prime.substring(0, s_prime.length() - 1) + ".+";
            }

            return Pattern.compile(s_prime);
        }
    }

    /**
     * For internal use by Sun Microsystems only.  This method will be
     * removed without warning.
     */
    public Context getContext() {
        return context;
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
