/*
 * Portions Copyright 2006 Sun Microsystems, Inc.  All Rights Reserved.
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
import com.sun.tools.internal.ws.processor.Processor;
import com.sun.tools.internal.ws.processor.ProcessorAction;
import com.sun.tools.internal.ws.processor.ProcessorConstants;
import com.sun.tools.internal.ws.processor.ProcessorNotificationListener;
import com.sun.tools.internal.ws.processor.ProcessorOptions;
import com.sun.tools.internal.ws.processor.config.ClassModelInfo;
import com.sun.tools.internal.ws.processor.config.Configuration;
import com.sun.tools.internal.ws.processor.config.WSDLModelInfo;
import com.sun.tools.internal.ws.processor.config.parser.Reader;
import com.sun.tools.internal.ws.processor.generator.CustomExceptionGenerator;
import com.sun.tools.internal.ws.processor.generator.SeiGenerator;
import com.sun.tools.internal.ws.processor.model.Model;
import com.sun.tools.internal.ws.processor.modeler.annotation.AnnotationProcessorContext;
import com.sun.tools.internal.ws.processor.modeler.annotation.WebServiceAP;
import com.sun.tools.internal.ws.processor.util.ClientProcessorEnvironment;
import com.sun.tools.internal.ws.processor.util.GeneratedFileInfo;
import com.sun.tools.internal.ws.processor.util.ProcessorEnvironment;
import com.sun.tools.internal.ws.processor.util.ProcessorEnvironmentBase;
import com.sun.xml.internal.ws.util.JAXWSUtils;
import com.sun.tools.internal.ws.util.JavaCompilerHelper;
import com.sun.tools.internal.ws.util.ToolBase;
import com.sun.tools.internal.ws.util.ForkEntityResolver;
import com.sun.tools.internal.ws.ToolVersion;
import com.sun.xml.internal.ws.util.VersionUtil;
import com.sun.xml.internal.ws.util.xml.XmlUtil;
import com.sun.xml.internal.ws.util.localization.Localizable;
import com.sun.xml.internal.ws.wsdl.writer.WSDLGenerator;
import com.sun.xml.internal.ws.binding.BindingImpl;
import com.sun.xml.internal.ws.binding.soap.SOAPBindingImpl;


import javax.xml.namespace.QName;
import javax.xml.transform.Result;
import javax.xml.transform.stream.StreamResult;
import javax.xml.ws.BindingType;
import javax.xml.ws.Holder;
import javax.xml.ws.soap.SOAPBinding;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.xml.sax.EntityResolver;

/**
 *    This is the real implementation class for both WsGen and WsImport.
 *
 *    <P>If the program being executed is WsGen, the CompileTool acts as an
 *    {@link com.sun.mirror.apt.AnnotationProcessorFactory AnnotationProcessorFactory}
 *    and uses {@link com.sun.tools.internal.ws.processor.modeler.annotation.WebServiceAP
 *    WebServiceAP} as the {@link com.sun.mirror.apt.AnnotationProcessor
 *    AnnotationProcessor} for the APT framework.  In this case APT takes control
 *    while processing the SEI passed to WsGen.  The APT framework then invokes the
 *    WebServiceAP to process classes that contain javax.jws.* annotations.
 *    WsGen uses the APT reflection library to process the SEI and to generate any
 *    JAX-WS spec mandated Java beans.
 *
 *    <p>If the program being executed is WsImport, the CompileTool creates a
 *    {@link com.sun.tools.internal.ws.processor.Processor  Processor} to help with the
 *    processing.  First the {@link com.sun.tools.internal.ws.processor.Processor#runModeler()
 *    Processor.runModeler()} method is called to create an instance of the
 *    {@link com.sun.tools.internal.ws.processor.modeler.wsdl.WSDLModeler WSDLModeler} to
 *    process the WSDL being imported, which intern processes the WSDL and creates
 *    a {@link com.sun.tools.internal.ws.processor.model.Model Model} that is returned to the
 *    Processor.  The CompileTool then registers a number of
 *    {@link com.sun.tools.internal.ws.processor.ProcessorAction ProcessorActions} with the
 *    Processor.  Some of these ProcessorActions include
 *    the {@link com.sun.tools.internal.ws.processor.generator.CustomExceptionGenerator
 *    CustomExceptionGenerator} to generate Exception classes,
 *    the {@link com.sun.tools.internal.ws.processor.generator.JAXBTypeGenerator
 *    JAXBTypeGenerator} to generate JAXB types,
 *    the {@link com.sun.tools.internal.ws.processor.generator.ServiceGenerator
 *    ServiceGenerator} to generate the Service interface, and
 *    the {@link com.sun.tools.internal.ws.processor.generator.SeiGenerator
 *    RemoteInterfaceGenerator} to generate the service endpoint interface.
 *    The CompileTool then invokes the {@link com.sun.tools.internal.ws.processor.Processor#runActions()
 *    Processor.runActions()} method to cause these ProcessorActions to run.
 *    Once the ProcessorActions have been run, the CompileTool will invoke javac
 *    to compile any classes generated by the  ProcessorActions.
 *
 * @author WS Development Team
 *
 */
public class CompileTool extends ToolBase implements ProcessorNotificationListener,
        AnnotationProcessorFactory {

    public CompileTool(OutputStream out, String program) {
        super(out, program);
        listener = this;
    }

    protected boolean parseArguments(String[] args) {
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("")) {
                args[i] = null;
            } else if (args[i].equals("-g")) {
                compilerDebug = true;
                args[i] = null;
            } /*else if (args[i].equals("-O")) {
                compilerOptimize = true;
                args[i] = null;
            }*/ else if (args[i].equals("-verbose")) {
                verbose = true;
                args[i] = null;
            } else if (args[i].equals("-b")) {
                if(program.equals(WSGEN)) {
                    onError(getMessage("wscompile.invalidOption", args[i]));
                    usage();
                    return false;
                }
                if ((i + 1) < args.length) {
                    args[i] = null;
                    String file = args[++i];
                    args[i] = null;
                    bindingFiles.add(JAXWSUtils.absolutize(JAXWSUtils.getFileOrURLName(file)));
                } else {
                    onError(getMessage("wscompile.missingOptionArgument", "-b"));
                    usage();
                    return false;
                }
            } else if (args[i].equals("-version")) {
                report(ToolVersion.VERSION.BUILD_VERSION);
                doNothing = true;
                args[i] = null;
                return true;
            } else if (args[i].equals("-keep")) {
                keepGenerated = true;
                args[i] = null;
            } else if(args[i].equals("-wsdllocation")){
                if(program.equals(WSGEN)) {
                    onError(getMessage("wscompile.invalidOption", args[i]));
                    usage();
                    return false;
                }
                if ((i + 1) < args.length) {
                    args[i]=null;
                    wsdlLocation = args[++i];
                    args[i]=null;
                } else {
                    onError(getMessage("wscompile.missingOptionArgument", args[i]));
                    usage();
                    return false;
                }
            } else if(args[i].equals("-p")){
                if(program.equals(WSGEN)) {
                    onError(getMessage("wscompile.invalidOption", args[i]));
                    usage();
                    return false;
                }
                if ((i + 1) < args.length) {
                    args[i]=null;
                    defaultPackage = args[++i];
                    args[i]=null;
                } else {
                    onError(getMessage("wscompile.missingOptionArgument", args[i]));
                    usage();
                    return false;
                }
            }else if(args[i].equals("-catalog")){
                if(program.equals(WSGEN)) {
                    onError(getMessage("wscompile.invalidOption", args[i]));
                    usage();
                    return false;
                }
                if ((i + 1) < args.length) {
                    args[i]=null;
                    catalog = args[++i];
                    args[i]=null;
                } else {
                    onError(getMessage("wscompile.missingOptionArgument", args[i]));
                    usage();
                    return false;
                }
            }else if (args[i].equals(SERVICENAME_OPTION)) {
                if(program.equals(WSIMPORT)) {
                    onError(getMessage("wscompile.invalidOption", args[i]));
                    usage();
                    return false;
                }
                if ((i + 1) < args.length) {
                    args[i] = null;
                    serviceName = QName.valueOf(args[++i]);
                    if (serviceName.getNamespaceURI() == null || serviceName.getNamespaceURI().length() == 0) {
                        onError(getMessage("wsgen.servicename.missing.namespace", args[i]));
                        usage();
                        return false;
                    }
                    if (serviceName.getLocalPart() == null || serviceName.getLocalPart().length() == 0) {
                        onError(getMessage("wsgen.servicename.missing.localname", args[i]));
                        usage();
                        return false;
                    }
                    args[i] = null;
                } else {
                    onError(getMessage("wscompile.missingOptionArgument", args[i]));
                    usage();
                    return false;
                }
            } else if (args[i].equals(PORTNAME_OPTION)) {
                if(program.equals(WSIMPORT)) {
                    onError(getMessage("wscompile.invalidOption", args[i]));
                    usage();
                    return false;
                }
                if ((i + 1) < args.length) {
                    args[i] = null;
                    portName = QName.valueOf(args[++i]);
                    if (portName.getNamespaceURI() == null || portName.getNamespaceURI().length() == 0) {
                        onError(getMessage("wsgen.portname.missing.namespace", args[i]));
                        usage();
                        return false;
                    }
                    if (portName.getLocalPart() == null || portName.getLocalPart().length() == 0) {
                        onError(getMessage("wsgen.portname.missing.localname", args[i]));
                        usage();
                        return false;
                    }
                    args[i] = null;
                } else {
                    onError(getMessage("wscompile.missingOptionArgument", args[i]));
                    usage();
                    return false;
                }
            } else if (args[i].equals("-d")) {
                if ((i + 1) < args.length) {
                    if (destDir != null) {
                        onError(getMessage("wscompile.duplicateOption", "-d"));
                        usage();
                        return false;
                    }
                    args[i] = null;
                    destDir = new File(args[++i]);
                    args[i] = null;
                    if (!destDir.exists()) {
                        onError(getMessage("wscompile.noSuchDirectory", destDir.getPath()));
                        usage();
                        return false;
                    }
                } else {
                    onError(getMessage("wscompile.missingOptionArgument", "-d"));
                    usage();
                    return false;
                }
            } else if (args[i].equals("-r")) {
                if (program.equals(WSIMPORT)) {
                    onError(getMessage("wscompile.invalidOption", args[i]));
                    usage();
                    return false;
                }
                if ((i + 1) < args.length) {
                    if (nonclassDestDir != null) {
                        onError(getMessage("wscompile.duplicateOption", "-r"));
                        usage();
                        return false;
                    }
                    args[i] = null;
                    nonclassDestDir = new File(args[++i]);
                    args[i] = null;
                    if (!nonclassDestDir.exists()) {
                        onError(getMessage("wscompile.noSuchDirectory", nonclassDestDir.getPath()));
                        usage();
                        return false;
                    }
                } else {
                onError(getMessage("wscompile.missingOptionArgument", "-r"));
                    usage();
                    return false;
                }
            } else if (args[i].equals("-s")) {
                if ((i + 1) < args.length) {
                    if (sourceDir != null) {
                        onError(getMessage("wscompile.duplicateOption", "-s"));
                        usage();
                        return false;
                    }
                    args[i] = null;
                    sourceDir = new File(args[++i]);
                    args[i] = null;
                    if (!sourceDir.exists()) {
                        onError(getMessage("wscompile.noSuchDirectory", sourceDir.getPath()));
                        usage();
                        return false;
                    }
                    keepGenerated = true;
                } else {
                    onError(getMessage("wscompile.missingOptionArgument", "-s"));
                    usage();
                    return false;
                }
            } else if (args[i].equals("-classpath") || args[i].equals("-cp")) {
                if (program.equals(WSIMPORT)) {
                    onError(getMessage("wscompile.invalidOption", args[i]));
                    usage();
                    return false;
                }
                if ((i + 1) < args.length) {
                    if (userClasspath != null) {
                        onError(getMessage("wscompile.duplicateOption", args[i]));
                        usage();
                        return false;
                    }
                    args[i] = null;
                    userClasspath = args[++i];
                    args[i] = null;
                } else {
                    onError(getMessage("wscompile.missingOptionArgument", args[i]));
                    usage();
                    return false;
                }

            } else if (args[i].startsWith("-httpproxy:")) {
                if(program.equals(WSGEN)) {
                    onError(getMessage("wscompile.invalidOption", args[i]));
                    usage();
                    return false;
                }
                String value = args[i].substring(11);
                if (value.length() == 0) {
                    onError(getMessage("wscompile.invalidOption", args[i]));
                    usage();
                    return false;
                }
                int index = value.indexOf(':');
                if (index == -1) {
                    System.setProperty("proxySet", TRUE);
                    System.setProperty("proxyHost", value);
                    System.setProperty("proxyPort", "8080");
                } else {
                    System.setProperty("proxySet", TRUE);
                    System.setProperty("proxyHost", value.substring(0, index));
                    System.setProperty("proxyPort", value.substring(index + 1));
                }
                args[i] = null;
            } else if (args[i].startsWith("-wsdl")) {
                if (program.equals(WSIMPORT)) {
                    onError(getMessage("wscompile.invalidOption", args[i]));
                    usage();
                    return false;
                }
                genWsdl = true;
                String value = args[i].substring(5);
                int index = value.indexOf(':');
                if (index == 0) {
                    value = value.substring(1);
                    index = value.indexOf('/');
                    if (index == -1) {
                        protocol = value;
                        transport = HTTP;
                    } else {
                        protocol = value.substring(0, index);
                        transport = value.substring(index + 1);
                    }
                    if (!isValidProtocol(protocol)) {
                        onError(getMessage("wsgen.invalid.protocol", protocol, VALID_PROTOCOLS));
                    }
                    protocolSet = true;
                    if (!isValidTransport(transport)) {
                        onError(getMessage("wsgen.invalid.transport", transport, VALID_TRANSPORTS));
                    }
                }
                args[i] = null;
            } else if (args[i].equals("-extension")) {
                extensions = true;
                args[i] = null;
            } else if (args[i].startsWith("-help")) {
                help();
                return false;
            } else if (args[i].equals("-Xdonotoverwrite")) {
                if(program.equals(WSIMPORT)) {
                    onError(getMessage("wscompile.invalidOption", args[i]));
                    usage();
                    return false;
                }
                doNotOverWrite = true;
                args[i] = null;
            }
        }

        for (String arg : args) {
            if (arg != null) {
                if (arg.startsWith("-")) {
                    onError(getMessage("wscompile.invalidOption", arg));
                    usage();
                    return false;
                }

                // the input source could be a local file or a URL,get the
                // abolutized URL string
                String fileName = arg;
                if (program.equals(WSGEN)) {
                    if (!isValidWSGenClass(fileName))
                        return false;
                }
                inputFiles.add(fileName);
            }
        }

        if (inputFiles.isEmpty()) {
            onError(getMessage(program+".missingFile"));
            usage();
            return false;
        }
        if (!extensions && hasExtensions())
            return false;

        // put jaxws and jaxb binding files
        properties.put(ProcessorOptions.BINDING_FILES, bindingFiles);
        if (!validateArguments()) {
            usage();
            return false;
        }
        return true;
    }

    protected boolean isValidWSGenClass(String className) {
        Class clazz = getClass(className);
        if (clazz == null) {
            onError(getMessage("wsgen.class.not.found", className));
            return false;
        }
        if (clazz.isEnum() || clazz.isInterface() ||
            clazz.isPrimitive()) {
            onError(getMessage("wsgen.class.must.be.implementation.class", className));
            return false;
        }
        if (genWsdl) {
            BindingImpl binding = (BindingImpl)BindingImpl.getBinding(null, clazz, null, false);
            if (!(binding instanceof SOAPBinding)) {
                onError(getMessage("wsgen.cannot.gen.wsdl.for.non.soap.binding",
                        new Object[] {className, binding.getBindingId()}));
                return false;
            }
            SOAPBindingImpl soapBinding = (SOAPBindingImpl)binding;
            if ((soapBinding.getActualBindingId().equals(SOAPBinding.SOAP12HTTP_BINDING) ||
                soapBinding.getActualBindingId().equals(SOAPBinding.SOAP12HTTP_MTOM_BINDING)) &&
                    !(protocol.equals(X_SOAP12) && extensions)) {
                onError(getMessage("wsgen.cannot.gen.wsdl.for.soap12.binding",
                        new Object[] {className, binding.getBindingId()}));
                return false;
            }
            if (soapBinding.getActualBindingId().equals(SOAPBindingImpl.X_SOAP12HTTP_BINDING) &&
                !extensions) {
                onError(getMessage("wsgen.cannot.gen.wsdl.for.xsoap12.binding.wo.extention",
                        new Object[] {className, binding.getBindingId()}));
                return false;
            }
        }
        return true;
    }

    protected boolean validateArguments() {
        if (!genWsdl) {
            if (serviceName != null) {
                onError(getMessage("wsgen.wsdl.arg.no.genwsdl", SERVICENAME_OPTION));
                return false;
            }
            if (portName != null) {
                onError(getMessage("wsgen.wsdl.arg.no.genwsdl", PORTNAME_OPTION));
                return false;
            }
        }
        return true;
    }

    protected boolean hasExtensions() {
        if (protocol.equalsIgnoreCase(X_SOAP12)) {
            onError(getMessage("wsgen.soap12.without.extension"));
            return true;
        }
        return false;
    }


    static public boolean isValidProtocol(String protocol) {
        return (protocol.equalsIgnoreCase(SOAP11) ||
                protocol.equalsIgnoreCase(X_SOAP12));
    }

    static public boolean isValidTransport(String transport) {
        return (transport.equalsIgnoreCase(HTTP));
    }

    protected void run() throws Exception {
        if (doNothing) {
            return;
        }
        try {
            beforeHook();
            if(entityResolver == null){
                if(catalog != null && catalog.length() > 0)
                    entityResolver = XmlUtil.createEntityResolver(JAXWSUtils.getFileOrURL(catalog));
            }else if(catalog != null && catalog.length() > 0){
                EntityResolver er = XmlUtil.createEntityResolver(JAXWSUtils.getFileOrURL(catalog));
                entityResolver =  new ForkEntityResolver(er, entityResolver);
            }
            environment = createEnvironment();
            configuration = createConfiguration();
            setEnvironmentValues(environment);
            if (configuration.getModelInfo() instanceof ClassModelInfo) {
                buildModel(((ClassModelInfo) configuration.getModelInfo()).getClassName());
            } else {
                processor = new Processor(configuration, properties);
                configuration.getModelInfo().setEntityResolver(entityResolver);
                configuration.getModelInfo().setDefaultJavaPackage(defaultPackage);
                processor.runModeler();
                withModelHook();
                registerProcessorActions(processor);
                processor.runActions();
                if (environment.getErrorCount() == 0) {
                    compileGeneratedClasses();
                }
            }
            afterHook();
        } finally {
            if (!keepGenerated) {
                removeGeneratedFiles();
            }
            if (environment != null) {
                environment.shutdown();
            }
        }
    }

    protected void setEnvironmentValues(ProcessorEnvironment env) {
        int envFlags = env.getFlags();
        envFlags |= ProcessorEnvironment.F_WARNINGS;
        if (verbose) {
            envFlags |= ProcessorEnvironment.F_VERBOSE;
        }
        env.setFlags(envFlags);
    }

    protected void initialize() {
        super.initialize();
        properties = new Properties();
        actions = new HashMap<String,ProcessorAction>();
        actions.put(ActionConstants.ACTION_SERVICE_GENERATOR,
                new com.sun.tools.internal.ws.processor.generator.ServiceGenerator());
        actions.put(ActionConstants.ACTION_REMOTE_INTERFACE_GENERATOR,
                new SeiGenerator());
        actions.put(ActionConstants.ACTION_CUSTOM_EXCEPTION_GENERATOR,
                new CustomExceptionGenerator());
        actions.put(ActionConstants.ACTION_JAXB_TYPE_GENERATOR,
                new com.sun.tools.internal.ws.processor.generator.JAXBTypeGenerator());
    }

    public void removeGeneratedFiles() {
        environment.deleteGeneratedFiles();
    }

    public void buildModel(String endpoint) {
        context = new AnnotationProcessorContext();
        webServiceAP = new WebServiceAP(this, environment, properties, context);

        String classpath = environment.getClassPath();

        String[] args = new String[8];
        args[0] = "-d";
        args[1] = destDir.getAbsolutePath();
        args[2] = "-classpath";
        args[3] = classpath;
        args[4] = "-s";
        args[5] = sourceDir.getAbsolutePath();
        args[6] = "-XclassesAsDecls";
        args[7] = endpoint;

        int result = com.sun.tools.apt.Main.process(this, args);
        if (result != 0) {
            environment.error(getMessage("wscompile.compilationFailed"));
            return;
        }
        if (genWsdl) {
            String tmpPath = destDir.getAbsolutePath()+File.pathSeparator+classpath;
            ClassLoader classLoader = new URLClassLoader(ProcessorEnvironmentBase.pathToURLs(tmpPath),
                    this.getClass().getClassLoader());
            Class<?> endpointClass = null;

            try {
                endpointClass = classLoader.loadClass(endpoint);
            } catch (ClassNotFoundException e) {
                // this should never happen
                environment.error(getMessage("wsgen.class.not.found", endpoint));
            }
            String bindingID = getBindingID(protocol);
            if (!protocolSet) {
                BindingImpl binding = (BindingImpl)BindingImpl.getBinding(null,
                                                    endpointClass, null, false);
                bindingID = binding.getBindingId();
            }
            com.sun.xml.internal.ws.modeler.RuntimeModeler rtModeler =
                    new com.sun.xml.internal.ws.modeler.RuntimeModeler(endpointClass, serviceName, bindingID);
            rtModeler.setClassLoader(classLoader);
            if (portName != null)
                rtModeler.setPortName(portName);
            com.sun.xml.internal.ws.model.RuntimeModel rtModel = rtModeler.buildRuntimeModel();
            WSDLGenerator wsdlGenerator = new WSDLGenerator(rtModel,
                    new com.sun.xml.internal.ws.wsdl.writer.WSDLOutputResolver() {
                        public Result getWSDLOutput(String suggestedFilename) {
                            File wsdlFile =
                                new File(nonclassDestDir, suggestedFilename);

                            Result result = new StreamResult();
                            try {
                                result = new StreamResult(new FileOutputStream(wsdlFile));
                                result.setSystemId(wsdlFile.toString().replace('\\', '/'));
                            } catch (FileNotFoundException e) {
                                environment.error(getMessage("wsgen.could.not.create.file", wsdlFile.toString()));
                            }
                            return result;
                        }
                        public Result getSchemaOutput(String namespace, String suggestedFilename) {
                            if (namespace.equals(""))
                                return null;
                            return getWSDLOutput(suggestedFilename);
                        }
                        public Result getAbstractWSDLOutput(Holder<String> filename) {
                            return getWSDLOutput(filename.value);
                        }
                        public Result getSchemaOutput(String namespace, Holder<String> filename) {
                            return getSchemaOutput(namespace, filename.value);
                        }
                    }, bindingID);
            wsdlGenerator.doGeneration();
        }
    }

    static public String getBindingID(String protocol) {
        if (protocol.equals(SOAP11))
            return SOAP11_ID;
        if (protocol.equals(X_SOAP12))
            return SOAP12_ID;
        return null;
    }

    public void runProcessorActions() {
        if (!(configuration.getModelInfo() instanceof ClassModelInfo)) {
            onError(getMessage("wscompile.classmodelinfo.expected", new Object[] { configuration
                    .getModelInfo() }));
            return;
        }
        Model model = context.getSEIContext(
                ((ClassModelInfo) configuration.getModelInfo()).getClassName()).getModel();
        processor = new Processor(configuration, properties, model);
        withModelHook();
        registerProcessorActions(processor);
        processor.runActions();
       // TODO throw an error
//        if (environment.getErrorCount() != 0) {
//        }

    }
    /**
     * @return the SourceVersion string
     */
    protected String getSourceVersion() {
        if (targetVersion == null) {

            /* no target specified, defaulting to the default version,
             * which is the latest version
             */
            return VersionUtil.JAXWS_VERSION_DEFAULT;
        }
        return targetVersion;
    }

    protected void withModelHook() {
    }

    protected void afterHook() {
    }

    protected void compileGeneratedClasses() {
        List<String> sourceFiles = new ArrayList<String>();

        for (Iterator iter = environment.getGeneratedFiles(); iter.hasNext();) {
            GeneratedFileInfo fileInfo = (GeneratedFileInfo) iter.next();
            File f = fileInfo.getFile();
            if (f.exists() && f.getName().endsWith(".java")) {
                sourceFiles.add(f.getAbsolutePath());
            }
        }

        if (sourceFiles.size() > 0) {
            String classDir = destDir.getAbsolutePath();
            String classpathString = createClasspathString();
            String[] args = new String[4 + (compilerDebug ? 1 : 0)
                    + (compilerOptimize ? 1 : 0) + sourceFiles.size()];
            args[0] = "-d";
            args[1] = classDir;
            args[2] = "-classpath";
            args[3] = classpathString;
            int baseIndex = 4;
            if (compilerDebug) {
                args[baseIndex++] = "-g";
            }
            if (compilerOptimize) {
                args[baseIndex++] = "-O";
            }
            for (int i = 0; i < sourceFiles.size(); ++i) {
                args[baseIndex + i] = sourceFiles.get(i);
            }

            // ByteArrayOutputStream javacOutput = new ByteArrayOutputStream();
            JavaCompilerHelper compilerHelper = new JavaCompilerHelper(out);
            boolean result = compilerHelper.compile(args);
            if (!result) {
                environment.error(getMessage("wscompile.compilationFailed"));
            }
        }
    }

    protected ProcessorAction getAction(String name) {
        return actions.get(name);
    }

    protected String createClasspathString() {
        if (userClasspath == null) {
            userClasspath = "";
        }
        return userClasspath + File.pathSeparator + System.getProperty("java.class.path");
    }

    protected void registerProcessorActions(Processor processor) {
        register(processor);
    }

    protected void register(Processor processor) {
        boolean genServiceInterface = false;
        boolean genInterface = false;
        boolean genCustomClasses = false;

        if (configuration.getModelInfo() instanceof WSDLModelInfo) {
            genInterface = true;
            //genInterfaceTemplate = true;
            genServiceInterface = true;
            genCustomClasses = true;
        }

        if (genServiceInterface) {
            processor.add(getAction(ActionConstants.ACTION_SERVICE_GENERATOR));
        }

        if (genCustomClasses) {
            processor.add(getAction(ActionConstants.ACTION_JAXB_TYPE_GENERATOR));
        }

        if (genInterface) {
            processor.add(getAction(ActionConstants.ACTION_CUSTOM_EXCEPTION_GENERATOR));
            processor.add(getAction(ActionConstants.ACTION_REMOTE_INTERFACE_GENERATOR));
        }
    }

    protected Configuration createConfiguration() throws Exception {
        if (environment == null)
            environment = createEnvironment();
        Reader reader = new Reader(environment, properties);
        return reader.parse(entityResolver, inputFiles);
    }

    protected void beforeHook() {
        if (destDir == null) {
            destDir = new File(".");
        }
        if (sourceDir == null) {
            sourceDir = destDir;
        }
        if (nonclassDestDir == null) {
            nonclassDestDir = destDir;
        }

        properties.setProperty(ProcessorOptions.SOURCE_DIRECTORY_PROPERTY, sourceDir
                .getAbsolutePath());
        properties.setProperty(ProcessorOptions.DESTINATION_DIRECTORY_PROPERTY, destDir
                .getAbsolutePath());
        properties.setProperty(ProcessorOptions.NONCLASS_DESTINATION_DIRECTORY_PROPERTY,
                nonclassDestDir.getAbsolutePath());
        properties.setProperty(ProcessorOptions.EXTENSION, (extensions ? "true" : "false"));
        properties.setProperty(ProcessorOptions.PRINT_STACK_TRACE_PROPERTY,
                (verbose ? TRUE : FALSE));
        properties.setProperty(ProcessorOptions.PROTOCOL, protocol);
        properties.setProperty(ProcessorOptions.TRANSPORT, transport);
        properties.setProperty(ProcessorOptions.JAXWS_SOURCE_VERSION, getSourceVersion());
        if(wsdlLocation != null)
            properties.setProperty(ProcessorOptions.WSDL_LOCATION, wsdlLocation);
        if(defaultPackage != null)
            properties.setProperty(ProcessorOptions.DEFAULT_PACKAGE, defaultPackage);
        properties.setProperty(ProcessorOptions.DONOT_OVERWRITE_CLASSES, (doNotOverWrite ? TRUE : FALSE));
    }

    protected String getGenericErrorMessage() {
        return "wscompile.error";
    }

    protected String getResourceBundleName() {
        return "com.sun.tools.internal.ws.resources.wscompile";
    }

    public Collection<String> supportedOptions() {
        return supportedOptions;
    }

    public Collection<String> supportedAnnotationTypes() {
        return supportedAnnotations;
    }

    public void onError(Localizable msg) {
        report(getMessage("wscompile.error", localizer.localize(msg)));
    }

    public void onWarning(Localizable msg) {
        report(getMessage("wscompile.warning", localizer.localize(msg)));
    }

    public void onInfo(Localizable msg) {
        report(getMessage("wscompile.info", localizer.localize(msg)));
    }

    public AnnotationProcessor getProcessorFor(Set<AnnotationTypeDeclaration> atds,
            AnnotationProcessorEnvironment apEnv) {
        if (verbose)
            apEnv.getMessager().printNotice("\tap round: " + ++round);
        webServiceAP.init(apEnv);
        return webServiceAP;
    }

    private Class getClass(String className) {
        try {
            ProcessorEnvironment env = createEnvironment();
            return env.getClassLoader().loadClass(className);
        } catch (ClassNotFoundException e) {
            return null;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private ProcessorEnvironment createEnvironment() throws Exception {
        String cpath = userClasspath + File.pathSeparator + System.getProperty("java.class.path");
        ProcessorEnvironment env = new ClientProcessorEnvironment(System.out, cpath, listener);
        return env;
    }

    protected void usage() {
        help();
        //report(getMessage(program+".usage", program));
    }

    protected void help() {
        report(getMessage(program+".help", program));
        report(getMessage(program+".usage.examples"));
    }

    public void setEntityResolver(EntityResolver entityResolver) {
        this.entityResolver = entityResolver;
    }

    /*
     * Processor doesn't examine any options.
     */
    static final Collection<String> supportedOptions = Collections
            .unmodifiableSet(new HashSet<String>());

    /*
     * All annotation types are supported.
     */
    static Collection<String> supportedAnnotations;
    static {
        Collection<String> types = new HashSet<String>();
        types.add("*");
        types.add("javax.jws.*");
        types.add("javax.jws.soap.*");
        supportedAnnotations = Collections.unmodifiableCollection(types);
    }

    private AnnotationProcessorContext context;

    private WebServiceAP webServiceAP;

    private int round = 0;

    // End AnnotationProcessorFactory stuff
    // -----------------------------------------------------------------------------

    protected Properties properties;
    protected ProcessorEnvironment environment;
    protected Configuration configuration;
    protected ProcessorNotificationListener listener;
    protected Processor processor;
    protected Map<String,ProcessorAction> actions;
    protected List<String> inputFiles = new ArrayList<String>();
    protected File sourceDir;
    protected File destDir;
    protected File nonclassDestDir;
    protected boolean doNothing = false;
    protected boolean compilerDebug = false;
    protected boolean compilerOptimize = false;
    protected boolean verbose = false;
    protected boolean keepGenerated = false;
    protected boolean doNotOverWrite = false;
    protected boolean extensions = false;
    protected String userClasspath = null;
    protected Set<String> bindingFiles = new HashSet<String>();
    protected boolean genWsdl = false;
    protected String protocol = SOAP11;
    protected boolean protocolSet = false;
    protected String transport = HTTP;
    protected static final String SOAP11 = "soap1.1";
    protected static final String X_SOAP12 = "Xsoap1.2";
    protected static final String HTTP   = "http";
    protected static final String WSIMPORT = "wsimport";
    protected static final String WSGEN    = "wsgen";
    protected static final String SOAP11_ID = javax.xml.ws.soap.SOAPBinding.SOAP11HTTP_BINDING;
    protected static final String SOAP12_ID = javax.xml.ws.soap.SOAPBinding.SOAP12HTTP_BINDING;
    protected static final String VALID_PROTOCOLS = SOAP11 +", "+X_SOAP12;
    protected static final String VALID_TRANSPORTS = "http";
    protected String  targetVersion = null;
    protected String wsdlLocation;
    protected String defaultPackage;
    protected String catalog;
    protected QName serviceName;
    protected QName portName;
    protected static final String PORTNAME_OPTION = "-portname";
    protected static final String SERVICENAME_OPTION = "-servicename";
    protected EntityResolver entityResolver;
}
