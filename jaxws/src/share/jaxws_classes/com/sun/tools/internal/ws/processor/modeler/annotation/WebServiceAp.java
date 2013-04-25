/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.internal.ws.processor.modeler.annotation;

import com.sun.istack.internal.logging.Logger;
import com.sun.tools.internal.ws.processor.generator.GeneratorUtil;
import com.sun.tools.internal.ws.processor.modeler.ModelerException;
import com.sun.tools.internal.ws.resources.WebserviceapMessages;
import com.sun.tools.internal.ws.wscompile.AbortException;
import com.sun.tools.internal.ws.wscompile.WsgenOptions;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.jws.WebService;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.tools.Diagnostic;
import javax.xml.ws.Holder;
import javax.xml.ws.WebServiceProvider;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Scanner;
import java.util.Set;
import java.util.logging.Level;

/**
 * WebServiceAp is a AnnotationProcessor for processing javax.jws.* and
 * javax.xml.ws.* annotations. This class is used either by the WsGen (CompileTool) tool or
 * indirectly when invoked by javac.
 *
 * @author WS Development Team
 */
@SupportedAnnotationTypes({
        "javax.jws.HandlerChain",
        "javax.jws.Oneway",
        "javax.jws.WebMethod",
        "javax.jws.WebParam",
        "javax.jws.WebResult",
        "javax.jws.WebService",
        "javax.jws.soap.InitParam",
        "javax.jws.soap.SOAPBinding",
        "javax.jws.soap.SOAPMessageHandler",
        "javax.jws.soap.SOAPMessageHandlers",
        "javax.xml.ws.BindingType",
        "javax.xml.ws.RequestWrapper",
        "javax.xml.ws.ResponseWrapper",
        "javax.xml.ws.ServiceMode",
        "javax.xml.ws.WebEndpoint",
        "javax.xml.ws.WebFault",
        "javax.xml.ws.WebServiceClient",
        "javax.xml.ws.WebServiceProvider",
        "javax.xml.ws.WebServiceRef"
})
@SupportedOptions({WebServiceAp.DO_NOT_OVERWRITE, WebServiceAp.IGNORE_NO_WEB_SERVICE_FOUND_WARNING})
public class WebServiceAp extends AbstractProcessor implements ModelBuilder {

    private static final Logger LOGGER = Logger.getLogger(WebServiceAp.class);

    public static final String DO_NOT_OVERWRITE = "doNotOverWrite";
    public static final String IGNORE_NO_WEB_SERVICE_FOUND_WARNING = "ignoreNoWebServiceFoundWarning";

    private WsgenOptions options;
    protected AnnotationProcessorContext context;
    private File sourceDir;
    private boolean doNotOverWrite;
    private boolean ignoreNoWebServiceFoundWarning = false;
    private TypeElement remoteElement;
    private TypeMirror remoteExceptionElement;
    private TypeMirror exceptionElement;
    private TypeMirror runtimeExceptionElement;
    private TypeElement defHolderElement;
    private boolean isCommandLineInvocation;
    private PrintStream out;
    private Collection<TypeElement> processedTypeElements = new HashSet<TypeElement>();

    public WebServiceAp() {
        this.context = new AnnotationProcessorContext();
    }

    public WebServiceAp(WsgenOptions options, PrintStream out) {
        this.options = options;
        this.sourceDir = (options != null) ? options.sourceDir : null;
        this.doNotOverWrite = (options != null) && options.doNotOverWrite;
        this.context = new AnnotationProcessorContext();
        this.out = out;
    }

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        remoteElement = processingEnv.getElementUtils().getTypeElement(Remote.class.getName());
        remoteExceptionElement = processingEnv.getElementUtils().getTypeElement(RemoteException.class.getName()).asType();
        exceptionElement = processingEnv.getElementUtils().getTypeElement(Exception.class.getName()).asType();
        runtimeExceptionElement = processingEnv.getElementUtils().getTypeElement(RuntimeException.class.getName()).asType();
        defHolderElement = processingEnv.getElementUtils().getTypeElement(Holder.class.getName());
        if (options == null) {
            options = new WsgenOptions();

            out = new PrintStream(new ByteArrayOutputStream());

            doNotOverWrite = getOption(DO_NOT_OVERWRITE);
            ignoreNoWebServiceFoundWarning = getOption(IGNORE_NO_WEB_SERVICE_FOUND_WARNING);

            String classDir = parseArguments();
            String property = System.getProperty("java.class.path");
            options.classpath = classDir + File.pathSeparator + (property != null ? property : "");
            isCommandLineInvocation = true;
        }
        options.filer = processingEnv.getFiler();
    }

    private String parseArguments() {
        // let's try to parse JavacOptions

        String classDir = null;
        try {
            ClassLoader cl = WebServiceAp.class.getClassLoader();
            Class javacProcessingEnvironmentClass = Class.forName("com.sun.tools.javac.processing.JavacProcessingEnvironment", false, cl);
            if (javacProcessingEnvironmentClass.isInstance(processingEnv)) {
                Method getContextMethod = javacProcessingEnvironmentClass.getDeclaredMethod("getContext");
                Object tmpContext = getContextMethod.invoke(processingEnv);
                Class optionsClass = Class.forName("com.sun.tools.javac.util.Options", false, cl);
                Class contextClass = Class.forName("com.sun.tools.javac.util.Context", false, cl);
                Method instanceMethod = optionsClass.getDeclaredMethod("instance", new Class[]{contextClass});
                Object tmpOptions = instanceMethod.invoke(null, tmpContext);
                if (tmpOptions != null) {
                    Method getMethod = optionsClass.getDeclaredMethod("get", new Class[]{String.class});
                    Object result = getMethod.invoke(tmpOptions, "-s"); // todo: we have to check for -d also
                    if (result != null) {
                        classDir = (String) result;
                    }
                    this.options.verbose = getMethod.invoke(tmpOptions, "-verbose") != null;
                }
            }
        } catch (Exception e) {
            /// some Error was here - problems with reflection or security
            processWarning(WebserviceapMessages.WEBSERVICEAP_PARSING_JAVAC_OPTIONS_ERROR());
            report(e.getMessage());
        }

        if (classDir == null) { // some error within reflection block
            String property = System.getProperty("sun.java.command");
            if (property != null) {
                Scanner scanner = new Scanner(property);
                boolean sourceDirNext = false;
                while (scanner.hasNext()) {
                    String token = scanner.next();
                    if (sourceDirNext) {
                        classDir = token;
                        sourceDirNext = false;
                    } else if ("-verbose".equals(token)) {
                        options.verbose = true;
                    } else if ("-s".equals(token)) {
                        sourceDirNext = true;
                    }
                }
            }
        }
        if (classDir != null) {
            sourceDir = new File(classDir);
        }
        return classDir;
    }

    private boolean getOption(String key) {
        String value = processingEnv.getOptions().get(key);
        if (value != null) {
            return Boolean.valueOf(value);
        }
        return false;
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (context.getRound() != 1) {
            return true;
        }
        context.incrementRound();
        WebService webService;
        WebServiceProvider webServiceProvider;
        WebServiceVisitor webServiceVisitor = new WebServiceWrapperGenerator(this, context);
        boolean processedEndpoint = false;
        Collection<TypeElement> classes = new ArrayList<TypeElement>();
        filterClasses(classes, roundEnv.getRootElements());
        for (TypeElement element : classes) {
            webServiceProvider = element.getAnnotation(WebServiceProvider.class);
            webService = element.getAnnotation(WebService.class);
            if (webServiceProvider != null) {
                if (webService != null) {
                    processError(WebserviceapMessages.WEBSERVICEAP_WEBSERVICE_AND_WEBSERVICEPROVIDER(element.getQualifiedName()));
                }
                processedEndpoint = true;
            }

            if (webService == null) {
                continue;
            }

            element.accept(webServiceVisitor, null);
            processedEndpoint = true;
        }
        if (!processedEndpoint) {
            if (isCommandLineInvocation) {
                if (!ignoreNoWebServiceFoundWarning) {
                    processWarning(WebserviceapMessages.WEBSERVICEAP_NO_WEBSERVICE_ENDPOINT_FOUND());
                }
            } else {
                processError(WebserviceapMessages.WEBSERVICEAP_NO_WEBSERVICE_ENDPOINT_FOUND());
            }
        }
        return true;
    }

    private void filterClasses(Collection<TypeElement> classes, Collection<? extends Element> elements) {
        for (Element element : elements) {
            if (element.getKind().equals(ElementKind.CLASS)) {
                classes.add((TypeElement) element);
                filterClasses(classes, ElementFilter.typesIn(element.getEnclosedElements()));
            }
        }
    }

    @Override
    public void processWarning(String message) {
        if (isCommandLineInvocation) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING, message);
        } else {
            report(message);
        }
    }

    protected void report(String msg) {
        if (out == null) {
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.log(Level.FINE, "No output set for web service annotation processor reporting.");
            }
            return;
        }
        out.println(msg);
        out.flush();
    }

    @Override
    public void processError(String message) {
        if (isCommandLineInvocation) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, message);
            throw new AbortException();
        } else {
            throw new ModelerException(message);
        }
    }

    @Override
    public void processError(String message, Element element) {
        if (isCommandLineInvocation) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, message, element);
        } else {
            throw new ModelerException(message);
        }
    }

    @Override
    public boolean canOverWriteClass(String className) {
        return !((doNotOverWrite && GeneratorUtil.classExists(options, className)));
    }

    @Override
    public File getSourceDir() {
        return sourceDir;
    }

    @Override
    public boolean isRemote(TypeElement typeElement) {
        return processingEnv.getTypeUtils().isSubtype(typeElement.asType(), remoteElement.asType());
    }

    @Override
    public boolean isServiceException(TypeMirror typeMirror) {
        return processingEnv.getTypeUtils().isSubtype(typeMirror, exceptionElement)
                && !processingEnv.getTypeUtils().isSubtype(typeMirror, runtimeExceptionElement)
                && !processingEnv.getTypeUtils().isSubtype(typeMirror, remoteExceptionElement);
    }

    @Override
    public TypeMirror getHolderValueType(TypeMirror type) {
        return TypeModeler.getHolderValueType(type, defHolderElement, processingEnv);
    }

    @Override
    public boolean checkAndSetProcessed(TypeElement typeElement) {
        if (!processedTypeElements.contains(typeElement)) {
            processedTypeElements.add(typeElement);
            return false;
        }
        return true;
    }

    @Override
    public void log(String message) {
        if (options != null && options.verbose) {
            message = new StringBuilder().append('[').append(message).append(']').toString(); // "[%s]"
            processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, message);
        }
    }

    @Override
    public WsgenOptions getOptions() {
        return options;
    }

    @Override
    public ProcessingEnvironment getProcessingEnvironment() {
        return processingEnv;
    }

    @Override
    public String getOperationName(Name messageName) {
        return messageName != null ? messageName.toString() : null;
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latest();
    }
}
