/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
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


package com.sun.tools.internal.ws.wscompile;

import com.sun.mirror.apt.AnnotationProcessor;
import com.sun.mirror.apt.AnnotationProcessorEnvironment;
import com.sun.mirror.apt.AnnotationProcessorFactory;
import com.sun.mirror.declaration.AnnotationTypeDeclaration;
import com.sun.tools.internal.ws.ToolVersion;
import com.sun.tools.internal.ws.api.WsgenExtension;
import com.sun.tools.internal.ws.api.WsgenProtocol;
import com.sun.tools.internal.ws.processor.modeler.annotation.AnnotationProcessorContext;
import com.sun.tools.internal.ws.processor.modeler.annotation.WebServiceAP;
import com.sun.tools.internal.ws.processor.modeler.wsdl.ConsoleErrorReporter;
import com.sun.tools.internal.ws.resources.WscompileMessages;
import com.sun.tools.internal.xjc.util.NullStream;
import com.sun.xml.internal.txw2.TXW;
import com.sun.xml.internal.txw2.TypedXmlWriter;
import com.sun.xml.internal.txw2.annotation.XmlAttribute;
import com.sun.xml.internal.txw2.annotation.XmlElement;
import com.sun.xml.internal.txw2.output.StreamSerializer;
import com.sun.xml.internal.ws.api.BindingID;
import com.sun.xml.internal.ws.api.server.Container;
import com.sun.xml.internal.ws.api.wsdl.writer.WSDLGeneratorExtension;
import com.sun.xml.internal.ws.binding.WebServiceFeatureList;
import com.sun.xml.internal.ws.binding.SOAPBindingImpl;
import com.sun.xml.internal.ws.model.AbstractSEIModelImpl;
import com.sun.xml.internal.ws.model.RuntimeModeler;
import com.sun.xml.internal.ws.util.ServiceFinder;
import com.sun.xml.internal.ws.wsdl.writer.WSDLGenerator;
import com.sun.xml.internal.ws.wsdl.writer.WSDLResolver;
import com.sun.istack.internal.tools.ParallelWorldClassLoader;
import org.xml.sax.SAXParseException;

import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.namespace.QName;
import javax.xml.transform.Result;
import javax.xml.transform.stream.StreamResult;
import javax.xml.ws.EndpointReference;
import javax.xml.ws.Holder;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.net.URLClassLoader;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author Vivek Pandey
 */
public class WsgenTool implements AnnotationProcessorFactory {
    private final PrintStream out;
    private final WsgenOptions options = new WsgenOptions();


    public WsgenTool(OutputStream out, Container container) {
        this.out = (out instanceof PrintStream)?(PrintStream)out:new PrintStream(out);
        this.container = container;
    }


    public WsgenTool(OutputStream out) {
        this(out, null);
    }

    public boolean run(String[] args){
        final Listener listener = new Listener();
        for (String arg : args) {
            if (arg.equals("-version")) {
                listener.message(ToolVersion.VERSION.BUILD_VERSION);
                return true;
            }
        }
        try {
            options.parseArguments(args);
            options.validate();
            if(!buildModel(options.endpoint.getName(), listener)){
                return false;
            }
        }catch (Options.WeAreDone done){
            usage((WsgenOptions)done.getOptions());
        }catch (BadCommandLineException e) {
            if(e.getMessage()!=null) {
                System.out.println(e.getMessage());
                System.out.println();
            }
            usage((WsgenOptions)e.getOptions());
            return false;
        }catch(AbortException e){
            //error might have been reported
        }finally{
            if(!options.keep){
                options.removeGeneratedFiles();
            }
        }
        return true;
    }

    private AnnotationProcessorContext context;
    private final Container container;

    private WebServiceAP webServiceAP;

    private int round = 0;

    // Workaround for bug 6499165 on jax-ws,
    // Original bug with JDK 6500594 , 6500594 when compiled with debug option,
    private void workAroundJavacDebug() {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        try {
            final Class aptMain = cl.loadClass("com.sun.tools.apt.main.Main");
            AccessController.doPrivileged(new PrivilegedAction<Void>() {
                public Void run() {
                    try {
                        Field forcedOpts = aptMain.getDeclaredField("forcedOpts");
                        forcedOpts.setAccessible(true);
                        forcedOpts.set(null, new String[]{});
                    } catch (NoSuchFieldException e) {
                        if(options.verbose)
                            e.printStackTrace();
                    } catch (IllegalAccessException e) {
                        if(options.verbose)
                            e.printStackTrace();
                    }
                    return null;
                }
            });
        } catch (ClassNotFoundException e) {
            if(options.verbose)
                e.printStackTrace();
        }
    }

    /*
     * To take care of JDK6-JDK6u3, where 2.1 API classes are not there
     */
    private static boolean useBootClasspath(Class clazz) {
        try {
            ParallelWorldClassLoader.toJarUrl(clazz.getResource('/'+clazz.getName().replace('.','/')+".class"));
            return true;
        } catch(Exception e) {
            return false;
        }
    }


    public boolean buildModel(String endpoint, Listener listener) throws BadCommandLineException {
        final ErrorReceiverFilter errReceiver = new ErrorReceiverFilter(listener);
        context = new AnnotationProcessorContext();
        webServiceAP = new WebServiceAP(options, context, errReceiver, out);

        boolean bootCP = useBootClasspath(EndpointReference.class) || useBootClasspath(XmlSeeAlso.class);

        String[] args = new String[8 + (bootCP ? 1 :0)];
        args[0] = "-d";
        args[1] = options.destDir.getAbsolutePath();
        args[2] = "-classpath";
        args[3] = options.classpath;
        args[4] = "-s";
        args[5] = options.sourceDir.getAbsolutePath();
        args[6] = "-XclassesAsDecls";
        args[7] = endpoint;
        if (bootCP) {
            args[8] = "-Xbootclasspath/p:"+JavaCompilerHelper.getJarFile(EndpointReference.class)+File.pathSeparator+JavaCompilerHelper.getJarFile(XmlSeeAlso.class);
        }

        // Workaround for bug 6499165: issue with javac debug option
        workAroundJavacDebug();
        int result = com.sun.tools.apt.Main.process(this, args);
        if (result != 0) {
            out.println(WscompileMessages.WSCOMPILE_ERROR(WscompileMessages.WSCOMPILE_COMPILATION_FAILED()));
            return false;
        }
        if (options.genWsdl) {
            String tmpPath = options.destDir.getAbsolutePath()+ File.pathSeparator+options.classpath;
            ClassLoader classLoader = new URLClassLoader(Options.pathToURLs(tmpPath),
                    this.getClass().getClassLoader());
            Class<?> endpointClass;
            try {
                endpointClass = classLoader.loadClass(endpoint);
            } catch (ClassNotFoundException e) {
                throw new BadCommandLineException(WscompileMessages.WSGEN_CLASS_NOT_FOUND(endpoint));
            }

            BindingID bindingID = options.getBindingID(options.protocol);
            if (!options.protocolSet) {
                bindingID = BindingID.parse(endpointClass);
            }
            WebServiceFeatureList wsfeatures = new WebServiceFeatureList(endpointClass);
            RuntimeModeler rtModeler = new RuntimeModeler(endpointClass, options.serviceName, bindingID, wsfeatures.toArray());
            rtModeler.setClassLoader(classLoader);
            if (options.portName != null)
                rtModeler.setPortName(options.portName);
            AbstractSEIModelImpl rtModel = rtModeler.buildRuntimeModel();

            final File[] wsdlFileName = new File[1]; // used to capture the generated WSDL file.
            final Map<String,File> schemaFiles = new HashMap<String,File>();

            WSDLGenerator wsdlGenerator = new WSDLGenerator(rtModel,
                    new WSDLResolver() {
                        private File toFile(String suggestedFilename) {
                            return new File(options.nonclassDestDir, suggestedFilename);
                        }
                        private Result toResult(File file) {
                            Result result;
                            try {
                                result = new StreamResult(new FileOutputStream(file));
                                result.setSystemId(file.getPath().replace('\\', '/'));
                            } catch (FileNotFoundException e) {
                                errReceiver.error(e);
                                return null;
                            }
                            return result;
                        }

                        public Result getWSDL(String suggestedFilename) {
                            File f = toFile(suggestedFilename);
                            wsdlFileName[0] = f;
                            return toResult(f);
                        }
                        public Result getSchemaOutput(String namespace, String suggestedFilename) {
                            if (namespace.equals(""))
                                return null;
                            File f = toFile(suggestedFilename);
                            schemaFiles.put(namespace,f);
                            return toResult(f);
                        }
                        public Result getAbstractWSDL(Holder<String> filename) {
                            return toResult(toFile(filename.value));
                        }
                        public Result getSchemaOutput(String namespace, Holder<String> filename) {
                            return getSchemaOutput(namespace, filename.value);
                        }
                        // TODO pass correct impl's class name
                    }, bindingID.createBinding(wsfeatures.toArray()), container, endpointClass, ServiceFinder.find(WSDLGeneratorExtension.class).toArray());
            wsdlGenerator.doGeneration();

            if(options.wsgenReport!=null)
                generateWsgenReport(endpointClass,rtModel,wsdlFileName[0],schemaFiles);
        }
        return true;
    }

    /**
     * Generates a small XML file that captures the key activity of wsgen,
     * so that test harness can pick up artifacts.
     */
    private void generateWsgenReport(Class<?> endpointClass, AbstractSEIModelImpl rtModel, File wsdlFile, Map<String,File> schemaFiles) {
        try {
            ReportOutput.Report report = TXW.create(ReportOutput.Report.class,
                new StreamSerializer(new BufferedOutputStream(new FileOutputStream(options.wsgenReport))));

            report.wsdl(wsdlFile.getAbsolutePath());
            ReportOutput.writeQName(rtModel.getServiceQName(), report.service());
            ReportOutput.writeQName(rtModel.getPortName(), report.port());
            ReportOutput.writeQName(rtModel.getPortTypeName(), report.portType());

            report.implClass(endpointClass.getName());

            for (Map.Entry<String,File> e : schemaFiles.entrySet()) {
                ReportOutput.Schema s = report.schema();
                s.ns(e.getKey());
                s.location(e.getValue().getAbsolutePath());
            }

            report.commit();
        } catch (IOException e) {
            // this is code for the test, so we can be lousy in the error handling
            throw new Error(e);
        }
    }

    /**
     * "Namespace" for code needed to generate the report file.
     */
    static class ReportOutput {
        @XmlElement("report")
        interface Report extends TypedXmlWriter {
            @XmlElement
            void wsdl(String file); // location of WSDL
            @XmlElement
            QualifiedName portType();
            @XmlElement
            QualifiedName service();
            @XmlElement
            QualifiedName port();

            /**
             * Name of the class that has {@link javax.jws.WebService}.
             */
            @XmlElement
            void implClass(String name);

            @XmlElement
            Schema schema();
        }

        interface QualifiedName extends TypedXmlWriter {
            @XmlAttribute
            void uri(String ns);
            @XmlAttribute
            void localName(String localName);
        }

        interface Schema extends TypedXmlWriter {
            @XmlAttribute
            void ns(String ns);
            @XmlAttribute
            void location(String filePath);
        }

        private static void writeQName( QName n, QualifiedName w ) {
            w.uri(n.getNamespaceURI());
            w.localName(n.getLocalPart());
        }
    }

    protected void usage(WsgenOptions options) {
        // Just don't see any point in passing WsgenOptions
        // BadCommandLineException also shouldn't have options
        if (options == null)
            options = this.options;
        System.out.println(WscompileMessages.WSGEN_HELP("WSGEN", options.protocols, options.nonstdProtocols.keySet()));
        System.out.println(WscompileMessages.WSGEN_USAGE_EXAMPLES());
    }

    public Collection<String> supportedOptions() {
        return supportedOptions;
    }

    public Collection<String> supportedAnnotationTypes() {
        return supportedAnnotations;
    }

    public AnnotationProcessor getProcessorFor(Set<AnnotationTypeDeclaration> set, AnnotationProcessorEnvironment apEnv) {
        if (options.verbose)
            apEnv.getMessager().printNotice("\tap round: " + ++round);
        webServiceAP.init(apEnv);
        return webServiceAP;
    }

    class Listener extends WsimportListener {
        ConsoleErrorReporter cer = new ConsoleErrorReporter(out == null ? new PrintStream(new NullStream()) : out);

        @Override
        public void generatedFile(String fileName) {
            message(fileName);
        }

        @Override
        public void message(String msg) {
            out.println(msg);
        }

        @Override
        public void error(SAXParseException exception) {
            cer.error(exception);
        }

        @Override
        public void fatalError(SAXParseException exception) {
            cer.fatalError(exception);
        }

        @Override
        public void warning(SAXParseException exception) {
            cer.warning(exception);
        }

        @Override
        public void info(SAXParseException exception) {
            cer.info(exception);
        }
    }

    /*
     * Processor doesn't examine any options.
     */
    static final Collection<String> supportedOptions = Collections
            .unmodifiableSet(new HashSet<String>());

    /*
     * All annotation types are supported.
     */
    static final Collection<String> supportedAnnotations;
    static {
        Collection<String> types = new HashSet<String>();
        types.add("*");
        types.add("javax.jws.*");
        types.add("javax.jws.soap.*");
        supportedAnnotations = Collections.unmodifiableCollection(types);
    }
}
