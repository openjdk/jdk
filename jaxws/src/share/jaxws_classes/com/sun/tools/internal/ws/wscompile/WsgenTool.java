/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.internal.ws.wscompile;

import com.oracle.webservices.internal.api.databinding.WSDLResolver;
import com.sun.istack.internal.tools.ParallelWorldClassLoader;
import com.sun.tools.internal.ws.ToolVersion;
import com.sun.tools.internal.ws.processor.modeler.annotation.WebServiceAp;
import com.sun.tools.internal.ws.processor.modeler.wsdl.ConsoleErrorReporter;
import com.sun.tools.internal.ws.resources.WscompileMessages;
import com.sun.tools.internal.xjc.util.NullStream;
import com.sun.xml.internal.txw2.TXW;
import com.sun.xml.internal.txw2.TypedXmlWriter;
import com.sun.xml.internal.txw2.annotation.XmlAttribute;
import com.sun.xml.internal.txw2.annotation.XmlElement;
import com.sun.xml.internal.txw2.output.StreamSerializer;
import com.sun.xml.internal.ws.api.BindingID;
import com.sun.xml.internal.ws.api.databinding.DatabindingConfig;
import com.sun.xml.internal.ws.api.databinding.DatabindingFactory;
import com.sun.xml.internal.ws.api.databinding.WSDLGenInfo;
import com.sun.xml.internal.ws.api.server.Container;
import com.sun.xml.internal.ws.api.wsdl.writer.WSDLGeneratorExtension;
import com.sun.xml.internal.ws.binding.WebServiceFeatureList;
import com.sun.xml.internal.ws.model.ExternalMetadataReader;
import com.sun.xml.internal.ws.model.AbstractSEIModelImpl;
import com.sun.xml.internal.ws.util.ServiceFinder;
import org.xml.sax.SAXParseException;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
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
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Vivek Pandey
 */

/*
 * All annotation types are supported.
 */
public class WsgenTool {
    private final PrintStream out;
    private final WsgenOptions options = new WsgenOptions();


    public WsgenTool(OutputStream out, Container container) {
        this.out = (out instanceof PrintStream) ? (PrintStream) out : new PrintStream(out);
        this.container = container;
    }


    public WsgenTool(OutputStream out) {
        this(out, null);
    }

    public boolean run(String[] args) {
        final Listener listener = new Listener();
        for (String arg : args) {
            if (arg.equals("-version")) {
                listener.message(
                        WscompileMessages.WSGEN_VERSION(ToolVersion.VERSION.MAJOR_VERSION));
                return true;
            }
            if (arg.equals("-fullversion")) {
                listener.message(
                        WscompileMessages.WSGEN_FULLVERSION(ToolVersion.VERSION.toString()));
                return true;
            }
        }
        try {
            options.parseArguments(args);
            options.validate();
            if (!buildModel(options.endpoint.getName(), listener)) {
                return false;
            }
        } catch (Options.WeAreDone done) {
            usage(done.getOptions());
        } catch (BadCommandLineException e) {
            if (e.getMessage() != null) {
                System.out.println(e.getMessage());
                System.out.println();
            }
            usage(e.getOptions());
            return false;
        } catch (AbortException e) {
            //error might have been reported
        } finally {
            if (!options.keep) {
                options.removeGeneratedFiles();
            }
        }
        return true;
    }

    private final Container container;

    /*
     * To take care of JDK6-JDK6u3, where 2.1 API classes are not there
     */
    private static boolean useBootClasspath(Class clazz) {
        try {
            ParallelWorldClassLoader.toJarUrl(clazz.getResource('/' + clazz.getName().replace('.', '/') + ".class"));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     *
     * @param endpoint
     * @param listener
     * @return
     * @throws BadCommandLineException
     */
    public boolean buildModel(String endpoint, Listener listener) throws BadCommandLineException {
        final ErrorReceiverFilter errReceiver = new ErrorReceiverFilter(listener);

        boolean bootCP = useBootClasspath(EndpointReference.class) || useBootClasspath(XmlSeeAlso.class);
        List<String> args = new ArrayList<String>(6 + (bootCP ? 1 : 0) + (options.nocompile ? 1 : 0)
                + (options.encoding != null ? 2 : 0));
        args.add("-d");
        args.add(options.destDir.getAbsolutePath());
        args.add("-classpath");
        args.add(options.classpath);
        args.add("-s");
        args.add(options.sourceDir.getAbsolutePath());
        if (options.nocompile) {
            args.add("-proc:only");
        }
        if (options.encoding != null) {
            args.add("-encoding");
            args.add(options.encoding);
        }
        if (bootCP) {
            args.add(new StringBuilder()
                    .append("-Xbootclasspath/p:")
                    .append(JavaCompilerHelper.getJarFile(EndpointReference.class))
                    .append(File.pathSeparator)
                    .append(JavaCompilerHelper.getJarFile(XmlSeeAlso.class)).toString());
        }
        if (options.javacOptions != null) {
            args.addAll(options.getJavacOptions(args, listener));
        }

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();//        compiler = JavacTool.create();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<JavaFileObject>();
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, null);
        JavaCompiler.CompilationTask task = compiler.getTask(
                null,
                fileManager,
                diagnostics,
                args,
                Collections.singleton(endpoint.replaceAll("\\$", ".")),
                null);
        task.setProcessors(Collections.singleton(new WebServiceAp(options, out)));
        boolean result = task.call();

        if (!result) {
            out.println(WscompileMessages.WSCOMPILE_ERROR(WscompileMessages.WSCOMPILE_COMPILATION_FAILED()));
            return false;
        }
        if (options.genWsdl) {
            DatabindingConfig config = new DatabindingConfig();

            List<String> externalMetadataFileNames = options.externalMetadataFiles;
            boolean disableXmlSecurity = options.disableXmlSecurity;
            if (externalMetadataFileNames != null && externalMetadataFileNames.size() > 0) {
                config.setMetadataReader(new ExternalMetadataReader(getExternalFiles(externalMetadataFileNames), null, null, true, disableXmlSecurity));
            }

            String tmpPath = options.destDir.getAbsolutePath() + File.pathSeparator + options.classpath;
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
//            RuntimeModeler rtModeler = new RuntimeModeler(endpointClass, options.serviceName, bindingID, wsfeatures.toArray());
//            rtModeler.setClassLoader(classLoader);
            if (options.portName != null)
                config.getMappingInfo().setPortName(options.portName);//rtModeler.setPortName(options.portName);
//            AbstractSEIModelImpl rtModel = rtModeler.buildRuntimeModel();

            DatabindingFactory fac = DatabindingFactory.newInstance();
            config.setEndpointClass(endpointClass);
            config.getMappingInfo().setServiceName(options.serviceName);
            config.setFeatures(wsfeatures.toArray());
            config.setClassLoader(classLoader);
            config.getMappingInfo().setBindingID(bindingID);
            com.sun.xml.internal.ws.db.DatabindingImpl rt = (com.sun.xml.internal.ws.db.DatabindingImpl) fac.createRuntime(config);

            final File[] wsdlFileName = new File[1]; // used to capture the generated WSDL file.
            final Map<String, File> schemaFiles = new HashMap<String, File>();

            WSDLGenInfo wsdlGenInfo = new WSDLGenInfo();
            wsdlGenInfo.setSecureXmlProcessingDisabled(disableXmlSecurity);

            wsdlGenInfo.setWsdlResolver(
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

                        @Override
                        public Result getWSDL(String suggestedFilename) {
                            File f = toFile(suggestedFilename);
                            wsdlFileName[0] = f;
                            return toResult(f);
                        }

                        public Result getSchemaOutput(String namespace, String suggestedFilename) {
                            if (namespace == null)
                                return null;
                            File f = toFile(suggestedFilename);
                            schemaFiles.put(namespace, f);
                            return toResult(f);
                        }

                        @Override
                        public Result getAbstractWSDL(Holder<String> filename) {
                            return toResult(toFile(filename.value));
                        }

                        @Override
                        public Result getSchemaOutput(String namespace, Holder<String> filename) {
                            return getSchemaOutput(namespace, filename.value);
                        }
                        // TODO pass correct impl's class name
                    });

            wsdlGenInfo.setContainer(container);
            wsdlGenInfo.setExtensions(ServiceFinder.find(WSDLGeneratorExtension.class).toArray());
            wsdlGenInfo.setInlineSchemas(options.inlineSchemas);
            rt.generateWSDL(wsdlGenInfo);


            if (options.wsgenReport != null)
                generateWsgenReport(endpointClass, (AbstractSEIModelImpl) rt.getModel(), wsdlFileName[0], schemaFiles);
        }
        return true;
    }

    private List<File> getExternalFiles(List<String> exts) {
        List<File> files = new ArrayList<File>();
        for (String ext : exts) {
            // first try absolute path ...
            File file = new File(ext);
            if (!file.exists()) {
                // then relative path ...
                file = new File(options.sourceDir.getAbsolutePath() + File.separator + ext);
            }
            files.add(file);
        }
        return files;
    }

    /**
     * Generates a small XML file that captures the key activity of wsgen,
     * so that test harness can pick up artifacts.
     */
    private void generateWsgenReport(Class<?> endpointClass, AbstractSEIModelImpl rtModel, File wsdlFile, Map<String, File> schemaFiles) {
        try {
            ReportOutput.Report report = TXW.create(ReportOutput.Report.class,
                    new StreamSerializer(new BufferedOutputStream(new FileOutputStream(options.wsgenReport))));

            report.wsdl(wsdlFile.getAbsolutePath());
            ReportOutput.writeQName(rtModel.getServiceQName(), report.service());
            ReportOutput.writeQName(rtModel.getPortName(), report.port());
            ReportOutput.writeQName(rtModel.getPortTypeName(), report.portType());

            report.implClass(endpointClass.getName());

            for (Map.Entry<String, File> e : schemaFiles.entrySet()) {
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

        private static void writeQName(QName n, QualifiedName w) {
            w.uri(n.getNamespaceURI());
            w.localName(n.getLocalPart());
        }
    }

    protected void usage(Options options) {
        // Just don't see any point in passing WsgenOptions
        // BadCommandLineException also shouldn't have options
        if (options == null)
            options = this.options;
        if (options instanceof WsgenOptions) {
            System.out.println(WscompileMessages.WSGEN_HELP("WSGEN",
                    ((WsgenOptions)options).protocols,
                    ((WsgenOptions)options).nonstdProtocols.keySet()));
            System.out.println(WscompileMessages.WSGEN_USAGE_EXTENSIONS());
            System.out.println(WscompileMessages.WSGEN_USAGE_EXAMPLES());
        }
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
}
