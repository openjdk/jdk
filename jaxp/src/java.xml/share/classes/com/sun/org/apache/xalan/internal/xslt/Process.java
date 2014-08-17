/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
/*
 * Copyright 1999-2004 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/*
 * $Id: Process.java,v 1.2.4.2 2005/09/15 18:21:57 jeffsuttor Exp $
 */
package com.sun.org.apache.xalan.internal.xslt;

import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringReader;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.Vector;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Source;
import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.URIResolver;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import com.sun.org.apache.xalan.internal.Version;
import com.sun.org.apache.xalan.internal.res.XSLMessages;
import com.sun.org.apache.xalan.internal.res.XSLTErrorResources;
import com.sun.org.apache.xalan.internal.utils.ObjectFactory;
import com.sun.org.apache.xalan.internal.utils.ConfigurationError;
import com.sun.org.apache.xalan.internal.utils.SecuritySupport;

//J2SE does not support Xalan interpretive
/*
import com.sun.org.apache.xalan.internal.trace.PrintTraceListener;
import com.sun.org.apache.xalan.internal.trace.TraceManager;
import com.sun.org.apache.xalan.internal.transformer.XalanProperties;
*/

import com.sun.org.apache.xml.internal.utils.DefaultErrorHandler;

import org.w3c.dom.Document;
import org.w3c.dom.Node;

import org.xml.sax.ContentHandler;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLReaderFactory;

/**
 * The main() method handles the Xalan command-line interface.
 * @xsl.usage general
 */
public class Process
{
  /**
   * Prints argument options.
   *
   * @param resbundle Resource bundle
   */
  protected static void printArgOptions(ResourceBundle resbundle)
  {
    System.out.println(resbundle.getString("xslProc_option"));  //"xslproc options: ");
    System.out.println("\n\t\t\t" + resbundle.getString("xslProc_common_options") + "\n");
    System.out.println(resbundle.getString("optionXSLTC"));  //"    [-XSLTC (use XSLTC for transformation)]
    System.out.println(resbundle.getString("optionIN"));  //"    [-IN inputXMLURL]");
    System.out.println(resbundle.getString("optionXSL"));  //"   [-XSL XSLTransformationURL]");
    System.out.println(resbundle.getString("optionOUT"));  //"   [-OUT outputFileName]");

    // System.out.println(resbundle.getString("optionE")); //"   [-E (Do not expand entity refs)]");
    System.out.println(resbundle.getString("optionV"));  //"   [-V (Version info)]");

    // System.out.println(resbundle.getString("optionVALIDATE")); //"   [-VALIDATE (Set whether validation occurs.  Validation is off by default.)]");
    System.out.println(resbundle.getString("optionEDUMP"));  //"   [-EDUMP {optional filename} (Do stackdump on error.)]");
    System.out.println(resbundle.getString("optionXML"));  //"   [-XML (Use XML formatter and add XML header.)]");
    System.out.println(resbundle.getString("optionTEXT"));  //"   [-TEXT (Use simple Text formatter.)]");
    System.out.println(resbundle.getString("optionHTML"));  //"   [-HTML (Use HTML formatter.)]");
    System.out.println(resbundle.getString("optionPARAM"));  //"   [-PARAM name expression (Set a stylesheet parameter)]");

    System.out.println(resbundle.getString("optionMEDIA"));
    System.out.println(resbundle.getString("optionFLAVOR"));
    System.out.println(resbundle.getString("optionDIAG"));
    System.out.println(resbundle.getString("optionURIRESOLVER"));  //"   [-URIRESOLVER full class name (URIResolver to be used to resolve URIs)]");
    System.out.println(resbundle.getString("optionENTITYRESOLVER"));  //"   [-ENTITYRESOLVER full class name (EntityResolver to be used to resolve entities)]");
    waitForReturnKey(resbundle);
    System.out.println(resbundle.getString("optionCONTENTHANDLER"));  //"   [-CONTENTHANDLER full class name (ContentHandler to be used to serialize output)]");
    System.out.println(resbundle.getString("optionSECUREPROCESSING")); //"   [-SECURE (set the secure processing feature to true)]");

    // J2SE does not support Xalan interpretive
    /*
    System.out.println("\n\t\t\t" + resbundle.getString("xslProc_xalan_options") + "\n");

    System.out.println(resbundle.getString("optionQC"));  //"   [-QC (Quiet Pattern Conflicts Warnings)]");

    // System.out.println(resbundle.getString("optionQ"));  //"   [-Q  (Quiet Mode)]"); // sc 28-Feb-01 commented out
    System.out.println(resbundle.getString("optionTT"));  //"   [-TT (Trace the templates as they are being called.)]");
    System.out.println(resbundle.getString("optionTG"));  //"   [-TG (Trace each generation event.)]");
    System.out.println(resbundle.getString("optionTS"));  //"   [-TS (Trace each selection event.)]");
    System.out.println(resbundle.getString("optionTTC"));  //"   [-TTC (Trace the template children as they are being processed.)]");
    System.out.println(resbundle.getString("optionTCLASS"));  //"   [-TCLASS (TraceListener class for trace extensions.)]");
    System.out.println(resbundle.getString("optionLINENUMBERS")); //"   [-L use line numbers]"
    System.out.println(resbundle.getString("optionINCREMENTAL"));
    System.out.println(resbundle.getString("optionNOOPTIMIMIZE"));
    System.out.println(resbundle.getString("optionRL"));
    */

    System.out.println("\n\t\t\t" + resbundle.getString("xslProc_xsltc_options") + "\n");
    System.out.println(resbundle.getString("optionXO"));
    waitForReturnKey(resbundle);
    System.out.println(resbundle.getString("optionXD"));
    System.out.println(resbundle.getString("optionXJ"));
    System.out.println(resbundle.getString("optionXP"));
    System.out.println(resbundle.getString("optionXN"));
    System.out.println(resbundle.getString("optionXX"));
    System.out.println(resbundle.getString("optionXT"));
  }

  /**
   * Command line interface to transform an XML document according to
   * the instructions found in an XSL stylesheet.
   * <p>The Process class provides basic functionality for
   * performing transformations from the command line.  To see a
   * list of arguments supported, call with zero arguments.</p>
   * <p>To set stylesheet parameters from the command line, use
   * <code>-PARAM name expression</code>. If you want to set the
   * parameter to a string value, simply pass the string value
   * as-is, and it will be interpreted as a string.  (Note: if
   * the value has spaces in it, you may need to quote it depending
   * on your shell environment).</p>
   *
   * @param argv Input parameters from command line
   */
  // J2SE does not support Xalan interpretive
  // main -> _main
  public static void _main(String argv[])
  {

    // Runtime.getRuntime().traceMethodCalls(false); // turns Java tracing off
    boolean doStackDumpOnError = false;
    boolean setQuietMode = false;
    boolean doDiag = false;
    String msg = null;
    boolean isSecureProcessing = false;

    // Runtime.getRuntime().traceMethodCalls(false);
    // Runtime.getRuntime().traceInstructions(false);

    /**
     * The default diagnostic writer...
     */
    java.io.PrintWriter diagnosticsWriter = new PrintWriter(System.err, true);
    java.io.PrintWriter dumpWriter = diagnosticsWriter;
    ResourceBundle resbundle =
      (SecuritySupport.getResourceBundle(
        com.sun.org.apache.xml.internal.utils.res.XResourceBundle.ERROR_RESOURCES));
    String flavor = "s2s";

    if (argv.length < 1)
    {
      printArgOptions(resbundle);
    }
    else
    {
        // J2SE does not support Xalan interpretive
        // false -> true
        boolean useXSLTC = true;
      for (int i = 0; i < argv.length; i++)
      {
        if ("-XSLTC".equalsIgnoreCase(argv[i]))
        {
          useXSLTC = true;
        }
      }

      TransformerFactory tfactory;
      if (useXSLTC)
      {
         String key = "javax.xml.transform.TransformerFactory";
         String value = "com.sun.org.apache.xalan.internal.xsltc.trax.TransformerFactoryImpl";
         Properties props = System.getProperties();
         props.put(key, value);
         System.setProperties(props);
      }

      try
      {
        tfactory = TransformerFactory.newInstance();
        tfactory.setErrorListener(new DefaultErrorHandler());
      }
      catch (TransformerFactoryConfigurationError pfe)
      {
        pfe.printStackTrace(dumpWriter);
//      "XSL Process was not successful.");
        msg = XSLMessages.createMessage(
            XSLTErrorResources.ER_NOT_SUCCESSFUL, null);
        diagnosticsWriter.println(msg);

        tfactory = null;  // shut up compiler

        doExit(msg);
      }

      boolean formatOutput = false;
      boolean useSourceLocation = false;
      String inFileName = null;
      String outFileName = null;
      String dumpFileName = null;
      String xslFileName = null;
      String treedumpFileName = null;
      // J2SE does not support Xalan interpretive
      /*
      PrintTraceListener tracer = null;
      */
      String outputType = null;
      String media = null;
      Vector params = new Vector();
      boolean quietConflictWarnings = false;
      URIResolver uriResolver = null;
      EntityResolver entityResolver = null;
      ContentHandler contentHandler = null;
      int recursionLimit=-1;

      for (int i = 0; i < argv.length; i++)
      {
        if ("-XSLTC".equalsIgnoreCase(argv[i]))
        {
          // The -XSLTC option has been processed.
        }
        // J2SE does not support Xalan interpretive
        /*
        else if ("-TT".equalsIgnoreCase(argv[i]))
        {
          if (!useXSLTC)
          {
            if (null == tracer)
              tracer = new PrintTraceListener(diagnosticsWriter);

            tracer.m_traceTemplates = true;
          }
          else
            printInvalidXSLTCOption("-TT");

          // tfactory.setTraceTemplates(true);
        }
        else if ("-TG".equalsIgnoreCase(argv[i]))
        {
          if (!useXSLTC)
          {
            if (null == tracer)
              tracer = new PrintTraceListener(diagnosticsWriter);

            tracer.m_traceGeneration = true;
          }
          else
            printInvalidXSLTCOption("-TG");

          // tfactory.setTraceSelect(true);
        }
        else if ("-TS".equalsIgnoreCase(argv[i]))
        {
          if (!useXSLTC)
          {
            if (null == tracer)
              tracer = new PrintTraceListener(diagnosticsWriter);

            tracer.m_traceSelection = true;
          }
          else
            printInvalidXSLTCOption("-TS");

          // tfactory.setTraceTemplates(true);
        }
        else if ("-TTC".equalsIgnoreCase(argv[i]))
        {
          if (!useXSLTC)
          {
            if (null == tracer)
              tracer = new PrintTraceListener(diagnosticsWriter);

            tracer.m_traceElements = true;
          }
          else
            printInvalidXSLTCOption("-TTC");

          // tfactory.setTraceTemplateChildren(true);
        }
        */
        else if ("-INDENT".equalsIgnoreCase(argv[i]))
        {
          int indentAmount;

          if (((i + 1) < argv.length) && (argv[i + 1].charAt(0) != '-'))
          {
            indentAmount = Integer.parseInt(argv[++i]);
          }
          else
          {
            indentAmount = 0;
          }

          // TBD:
          // xmlProcessorLiaison.setIndent(indentAmount);
        }
        else if ("-IN".equalsIgnoreCase(argv[i]))
        {
          if (i + 1 < argv.length && argv[i + 1].charAt(0) != '-')
            inFileName = argv[++i];
          else
            System.err.println(
              XSLMessages.createMessage(
                XSLTErrorResources.ER_MISSING_ARG_FOR_OPTION,
                new Object[]{ "-IN" }));  //"Missing argument for);
        }
        else if ("-MEDIA".equalsIgnoreCase(argv[i]))
        {
          if (i + 1 < argv.length)
            media = argv[++i];
          else
            System.err.println(
              XSLMessages.createMessage(
                XSLTErrorResources.ER_MISSING_ARG_FOR_OPTION,
                new Object[]{ "-MEDIA" }));  //"Missing argument for);
        }
        else if ("-OUT".equalsIgnoreCase(argv[i]))
        {
          if (i + 1 < argv.length && argv[i + 1].charAt(0) != '-')
            outFileName = argv[++i];
          else
            System.err.println(
              XSLMessages.createMessage(
                XSLTErrorResources.ER_MISSING_ARG_FOR_OPTION,
                new Object[]{ "-OUT" }));  //"Missing argument for);
        }
        else if ("-XSL".equalsIgnoreCase(argv[i]))
        {
          if (i + 1 < argv.length && argv[i + 1].charAt(0) != '-')
            xslFileName = argv[++i];
          else
            System.err.println(
              XSLMessages.createMessage(
                XSLTErrorResources.ER_MISSING_ARG_FOR_OPTION,
                new Object[]{ "-XSL" }));  //"Missing argument for);
        }
        else if ("-FLAVOR".equalsIgnoreCase(argv[i]))
        {
          if (i + 1 < argv.length)
          {
            flavor = argv[++i];
          }
          else
            System.err.println(
              XSLMessages.createMessage(
                XSLTErrorResources.ER_MISSING_ARG_FOR_OPTION,
                new Object[]{ "-FLAVOR" }));  //"Missing argument for);
        }
        else if ("-PARAM".equalsIgnoreCase(argv[i]))
        {
          if (i + 2 < argv.length)
          {
            String name = argv[++i];

            params.addElement(name);

            String expression = argv[++i];

            params.addElement(expression);
          }
          else
            System.err.println(
              XSLMessages.createMessage(
                XSLTErrorResources.ER_MISSING_ARG_FOR_OPTION,
                new Object[]{ "-PARAM" }));  //"Missing argument for);
        }
        else if ("-E".equalsIgnoreCase(argv[i]))
        {

          // TBD:
          // xmlProcessorLiaison.setShouldExpandEntityRefs(false);
        }
        else if ("-V".equalsIgnoreCase(argv[i]))
        {
          diagnosticsWriter.println(resbundle.getString("version")  //">>>>>>> Xalan Version "
                                    + Version.getVersion() + ", " +

          /* xmlProcessorLiaison.getParserDescription()+ */
          resbundle.getString("version2"));  // "<<<<<<<");
        }
        // J2SE does not support Xalan interpretive
        /*
        else if ("-QC".equalsIgnoreCase(argv[i]))
        {
          if (!useXSLTC)
            quietConflictWarnings = true;
          else
            printInvalidXSLTCOption("-QC");
        }
        */
        else if ("-Q".equalsIgnoreCase(argv[i]))
        {
          setQuietMode = true;
        }
        else if ("-DIAG".equalsIgnoreCase(argv[i]))
        {
          doDiag = true;
        }
        else if ("-XML".equalsIgnoreCase(argv[i]))
        {
          outputType = "xml";
        }
        else if ("-TEXT".equalsIgnoreCase(argv[i]))
        {
          outputType = "text";
        }
        else if ("-HTML".equalsIgnoreCase(argv[i]))
        {
          outputType = "html";
        }
        else if ("-EDUMP".equalsIgnoreCase(argv[i]))
        {
          doStackDumpOnError = true;

          if (((i + 1) < argv.length) && (argv[i + 1].charAt(0) != '-'))
          {
            dumpFileName = argv[++i];
          }
        }
        else if ("-URIRESOLVER".equalsIgnoreCase(argv[i]))
        {
          if (i + 1 < argv.length)
          {
            try
            {
              uriResolver = (URIResolver) ObjectFactory.newInstance(argv[++i], true);

              tfactory.setURIResolver(uriResolver);
            }
            catch (ConfigurationError cnfe)
            {
                msg = XSLMessages.createMessage(
                    XSLTErrorResources.ER_CLASS_NOT_FOUND_FOR_OPTION,
                    new Object[]{ "-URIResolver" });
              System.err.println(msg);
              doExit(msg);
            }
          }
          else
          {
            msg = XSLMessages.createMessage(
                    XSLTErrorResources.ER_MISSING_ARG_FOR_OPTION,
                    new Object[]{ "-URIResolver" });  //"Missing argument for);
            System.err.println(msg);
            doExit(msg);
          }
        }
        else if ("-ENTITYRESOLVER".equalsIgnoreCase(argv[i]))
        {
          if (i + 1 < argv.length)
          {
            try
            {
              entityResolver = (EntityResolver) ObjectFactory.newInstance(argv[++i], true);
            }
            catch (ConfigurationError cnfe)
            {
                msg = XSLMessages.createMessage(
                    XSLTErrorResources.ER_CLASS_NOT_FOUND_FOR_OPTION,
                    new Object[]{ "-EntityResolver" });
              System.err.println(msg);
              doExit(msg);
            }
          }
          else
          {
//            "Missing argument for);
              msg = XSLMessages.createMessage(
                    XSLTErrorResources.ER_MISSING_ARG_FOR_OPTION,
                    new Object[]{ "-EntityResolver" });
            System.err.println(msg);
            doExit(msg);
          }
        }
        else if ("-CONTENTHANDLER".equalsIgnoreCase(argv[i]))
        {
          if (i + 1 < argv.length)
          {
            try
            {
              contentHandler = (ContentHandler) ObjectFactory.newInstance(argv[++i], true);
            }
            catch (ConfigurationError cnfe)
            {
                msg = XSLMessages.createMessage(
                    XSLTErrorResources.ER_CLASS_NOT_FOUND_FOR_OPTION,
                    new Object[]{ "-ContentHandler" });
              System.err.println(msg);
              doExit(msg);
            }
          }
          else
          {
//            "Missing argument for);
              msg = XSLMessages.createMessage(
                    XSLTErrorResources.ER_MISSING_ARG_FOR_OPTION,
                    new Object[]{ "-ContentHandler" });
            System.err.println(msg);
            doExit(msg);
          }
        }
        // J2SE does not support Xalan interpretive
        /*
        else if ("-L".equalsIgnoreCase(argv[i]))
        {
          if (!useXSLTC)
            tfactory.setAttribute(XalanProperties.SOURCE_LOCATION, Boolean.TRUE);
          else
            printInvalidXSLTCOption("-L");
        }
        else if ("-INCREMENTAL".equalsIgnoreCase(argv[i]))
        {
          if (!useXSLTC)
            tfactory.setAttribute
              ("http://xml.apache.org/xalan/features/incremental",
               java.lang.Boolean.TRUE);
          else
            printInvalidXSLTCOption("-INCREMENTAL");
        }
        else if ("-NOOPTIMIZE".equalsIgnoreCase(argv[i]))
        {
          // Default is true.
          //
          // %REVIEW% We should have a generalized syntax for negative
          // switches...  and probably should accept the inverse even
          // if it is the default.
          if (!useXSLTC)
            tfactory.setAttribute
              ("http://xml.apache.org/xalan/features/optimize",
               java.lang.Boolean.FALSE);
          else
            printInvalidXSLTCOption("-NOOPTIMIZE");
        }
        else if ("-RL".equalsIgnoreCase(argv[i]))
        {
          if (!useXSLTC)
          {
            if (i + 1 < argv.length)
              recursionLimit = Integer.parseInt(argv[++i]);
            else
              System.err.println(
                XSLMessages.createMessage(
                  XSLTErrorResources.ER_MISSING_ARG_FOR_OPTION,
                  new Object[]{ "-rl" }));  //"Missing argument for);
          }
          else
          {
            if (i + 1 < argv.length && argv[i + 1].charAt(0) != '-')
             i++;

            printInvalidXSLTCOption("-RL");
          }
        }
        */
        // Generate the translet class and optionally specify the name
        // of the translet class.
        else if ("-XO".equalsIgnoreCase(argv[i]))
        {
          if (useXSLTC)
          {
            if (i + 1 < argv.length && argv[i+1].charAt(0) != '-')
            {
              tfactory.setAttribute("generate-translet", "true");
              tfactory.setAttribute("translet-name", argv[++i]);
            }
            else
              tfactory.setAttribute("generate-translet", "true");
          }
          else
          {
            if (i + 1 < argv.length && argv[i + 1].charAt(0) != '-')
             i++;
            printInvalidXalanOption("-XO");
          }
        }
        // Specify the destination directory for the translet classes.
        else if ("-XD".equalsIgnoreCase(argv[i]))
        {
          if (useXSLTC)
          {
            if (i + 1 < argv.length && argv[i+1].charAt(0) != '-')
              tfactory.setAttribute("destination-directory", argv[++i]);
            else
              System.err.println(
                XSLMessages.createMessage(
                  XSLTErrorResources.ER_MISSING_ARG_FOR_OPTION,
                  new Object[]{ "-XD" }));  //"Missing argument for);

          }
          else
          {
            if (i + 1 < argv.length && argv[i + 1].charAt(0) != '-')
             i++;

            printInvalidXalanOption("-XD");
          }
        }
        // Specify the jar file name which the translet classes are packaged into.
        else if ("-XJ".equalsIgnoreCase(argv[i]))
        {
          if (useXSLTC)
          {
            if (i + 1 < argv.length && argv[i+1].charAt(0) != '-')
            {
              tfactory.setAttribute("generate-translet", "true");
              tfactory.setAttribute("jar-name", argv[++i]);
            }
            else
              System.err.println(
                XSLMessages.createMessage(
                  XSLTErrorResources.ER_MISSING_ARG_FOR_OPTION,
                  new Object[]{ "-XJ" }));  //"Missing argument for);
          }
          else
          {
            if (i + 1 < argv.length && argv[i + 1].charAt(0) != '-')
             i++;

            printInvalidXalanOption("-XJ");
          }

        }
        // Specify the package name prefix for the generated translet classes.
        else if ("-XP".equalsIgnoreCase(argv[i]))
        {
          if (useXSLTC)
          {
            if (i + 1 < argv.length && argv[i+1].charAt(0) != '-')
              tfactory.setAttribute("package-name", argv[++i]);
            else
              System.err.println(
                XSLMessages.createMessage(
                  XSLTErrorResources.ER_MISSING_ARG_FOR_OPTION,
                  new Object[]{ "-XP" }));  //"Missing argument for);
          }
          else
          {
            if (i + 1 < argv.length && argv[i + 1].charAt(0) != '-')
             i++;

            printInvalidXalanOption("-XP");
          }

        }
        // Enable template inlining.
        else if ("-XN".equalsIgnoreCase(argv[i]))
        {
          if (useXSLTC)
          {
            tfactory.setAttribute("enable-inlining", "true");
          }
          else
            printInvalidXalanOption("-XN");
        }
        // Turns on additional debugging message output
        else if ("-XX".equalsIgnoreCase(argv[i]))
        {
          if (useXSLTC)
          {
            tfactory.setAttribute("debug", "true");
          }
          else
            printInvalidXalanOption("-XX");
        }
        // Create the Transformer from the translet if the translet class is newer
        // than the stylesheet.
        else if ("-XT".equalsIgnoreCase(argv[i]))
        {
          if (useXSLTC)
          {
            tfactory.setAttribute("auto-translet", "true");
          }
          else
            printInvalidXalanOption("-XT");
        }
        else if ("-SECURE".equalsIgnoreCase(argv[i]))
        {
          isSecureProcessing = true;
          try
          {
            tfactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
          }
          catch (TransformerConfigurationException e) {}
        }
        else
          System.err.println(
            XSLMessages.createMessage(
              XSLTErrorResources.ER_INVALID_OPTION, new Object[]{ argv[i] }));  //"Invalid argument:);
      }

      // Print usage instructions if no xml and xsl file is specified in the command line
      if (inFileName == null && xslFileName == null)
      {
          msg = resbundle.getString("xslProc_no_input");
        System.err.println(msg);
        doExit(msg);
      }

      // Note that there are usage cases for calling us without a -IN arg
      // The main XSL transformation occurs here!
      try
      {
        long start = System.currentTimeMillis();

        if (null != dumpFileName)
        {
          dumpWriter = new PrintWriter(new FileWriter(dumpFileName));
        }

        Templates stylesheet = null;

        if (null != xslFileName)
        {
          if (flavor.equals("d2d"))
          {

            // Parse in the xml data into a DOM
            DocumentBuilderFactory dfactory =
              DocumentBuilderFactory.newInstance();

            dfactory.setNamespaceAware(true);

            if (isSecureProcessing)
            {
              try
              {
                dfactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
              }
              catch (ParserConfigurationException pce) {}
            }

            DocumentBuilder docBuilder = dfactory.newDocumentBuilder();
            Node xslDOM = docBuilder.parse(new InputSource(xslFileName));

            stylesheet = tfactory.newTemplates(new DOMSource(xslDOM,
                    xslFileName));
          }
          else
          {
            // System.out.println("Calling newTemplates: "+xslFileName);
            stylesheet = tfactory.newTemplates(new StreamSource(xslFileName));
            // System.out.println("Done calling newTemplates: "+xslFileName);
          }
        }

        PrintWriter resultWriter;
        StreamResult strResult;

        if (null != outFileName)
        {
          strResult = new StreamResult(new FileOutputStream(outFileName));
          // One possible improvement might be to ensure this is
          //  a valid URI before setting the systemId, but that
          //  might have subtle changes that pre-existing users
          //  might notice; we can think about that later -sc r1.46
          strResult.setSystemId(outFileName);
        }
        else
        {
          strResult = new StreamResult(System.out);
          // We used to default to incremental mode in this case.
          // We've since decided that since the -INCREMENTAL switch is
          // available, that default is probably not necessary nor
          // necessarily a good idea.
        }

        SAXTransformerFactory stf = (SAXTransformerFactory) tfactory;

        // J2SE does not support Xalan interpretive
        /*
                // This is currently controlled via TransformerFactoryImpl.
        if (!useXSLTC && useSourceLocation)
           stf.setAttribute(XalanProperties.SOURCE_LOCATION, Boolean.TRUE);
        */

        // Did they pass in a stylesheet, or should we get it from the
        // document?
        if (null == stylesheet)
        {
          Source source =
            stf.getAssociatedStylesheet(new StreamSource(inFileName), media,
                                        null, null);

          if (null != source)
            stylesheet = tfactory.newTemplates(source);
          else
          {
            if (null != media)
              throw new TransformerException(XSLMessages.createMessage(XSLTErrorResources.ER_NO_STYLESHEET_IN_MEDIA, new Object[]{inFileName, media})); //"No stylesheet found in: "
                                            // + inFileName + ", media="
                                            // + media);
            else
              throw new TransformerException(XSLMessages.createMessage(XSLTErrorResources.ER_NO_STYLESHEET_PI, new Object[]{inFileName})); //"No xml-stylesheet PI found in: "
                                             //+ inFileName);
          }
        }

        if (null != stylesheet)
        {
          Transformer transformer = flavor.equals("th") ? null : stylesheet.newTransformer();
          transformer.setErrorListener(new DefaultErrorHandler());

          // Override the output format?
          if (null != outputType)
          {
            transformer.setOutputProperty(OutputKeys.METHOD, outputType);
          }

          // J2SE does not support Xalan interpretive
          /*
          if (transformer instanceof com.sun.org.apache.xalan.internal.transformer.TransformerImpl)
          {
            com.sun.org.apache.xalan.internal.transformer.TransformerImpl impl = (com.sun.org.apache.xalan.internal.transformer.TransformerImpl)transformer;
            TraceManager tm = impl.getTraceManager();

            if (null != tracer)
              tm.addTraceListener(tracer);

            impl.setQuietConflictWarnings(quietConflictWarnings);

                        // This is currently controlled via TransformerFactoryImpl.
            if (useSourceLocation)
              impl.setProperty(XalanProperties.SOURCE_LOCATION, Boolean.TRUE);

            if(recursionLimit>0)
              impl.setRecursionLimit(recursionLimit);

            // sc 28-Feb-01 if we re-implement this, please uncomment helpmsg in printArgOptions
            // impl.setDiagnosticsOutput( setQuietMode ? null : diagnosticsWriter );
          }
          */

          int nParams = params.size();

          for (int i = 0; i < nParams; i += 2)
          {
            transformer.setParameter((String) params.elementAt(i),
                                     (String) params.elementAt(i + 1));
          }

          if (uriResolver != null)
            transformer.setURIResolver(uriResolver);

          if (null != inFileName)
          {
            if (flavor.equals("d2d"))
            {

              // Parse in the xml data into a DOM
              DocumentBuilderFactory dfactory =
                DocumentBuilderFactory.newInstance();

              dfactory.setCoalescing(true);
              dfactory.setNamespaceAware(true);

              if (isSecureProcessing)
              {
                try
                {
                  dfactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
                }
                catch (ParserConfigurationException pce) {}
              }

              DocumentBuilder docBuilder = dfactory.newDocumentBuilder();

              if (entityResolver != null)
                docBuilder.setEntityResolver(entityResolver);

              Node xmlDoc = docBuilder.parse(new InputSource(inFileName));
              Document doc = docBuilder.newDocument();
              org.w3c.dom.DocumentFragment outNode =
                doc.createDocumentFragment();

              transformer.transform(new DOMSource(xmlDoc, inFileName),
                                    new DOMResult(outNode));

              // Now serialize output to disk with identity transformer
              Transformer serializer = stf.newTransformer();
              serializer.setErrorListener(new DefaultErrorHandler());

              Properties serializationProps =
                stylesheet.getOutputProperties();

              serializer.setOutputProperties(serializationProps);

              if (contentHandler != null)
              {
                SAXResult result = new SAXResult(contentHandler);

                serializer.transform(new DOMSource(outNode), result);
              }
              else
                serializer.transform(new DOMSource(outNode), strResult);
            }
            else if (flavor.equals("th"))
            {
              for (int i = 0; i < 1; i++) // Loop for diagnosing bugs with inconsistent behavior
              {
              // System.out.println("Testing the TransformerHandler...");

              XMLReader reader = null;

              // Use JAXP1.1 ( if possible )
              try
              {
                javax.xml.parsers.SAXParserFactory factory =
                  javax.xml.parsers.SAXParserFactory.newInstance();

                factory.setNamespaceAware(true);

                if (isSecureProcessing)
                {
                  try
                  {
                    factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
                  }
                  catch (org.xml.sax.SAXException se) {}
                }

                javax.xml.parsers.SAXParser jaxpParser =
                  factory.newSAXParser();

                reader = jaxpParser.getXMLReader();
              }
              catch (javax.xml.parsers.ParserConfigurationException ex)
              {
                throw new org.xml.sax.SAXException(ex);
              }
              catch (javax.xml.parsers.FactoryConfigurationError ex1)
              {
                throw new org.xml.sax.SAXException(ex1.toString());
              }
              catch (NoSuchMethodError ex2){}
              catch (AbstractMethodError ame){}

              if (null == reader)
              {
                reader = XMLReaderFactory.createXMLReader();
              }

              // J2SE does not support Xalan interpretive
              /*
              if (!useXSLTC)
                stf.setAttribute(com.sun.org.apache.xalan.internal.processor.TransformerFactoryImpl.FEATURE_INCREMENTAL,
                   Boolean.TRUE);
              */

              TransformerHandler th = stf.newTransformerHandler(stylesheet);

              reader.setContentHandler(th);
              reader.setDTDHandler(th);

              if(th instanceof org.xml.sax.ErrorHandler)
                reader.setErrorHandler((org.xml.sax.ErrorHandler)th);

              try
              {
                reader.setProperty(
                  "http://xml.org/sax/properties/lexical-handler", th);
              }
              catch (org.xml.sax.SAXNotRecognizedException e){}
              catch (org.xml.sax.SAXNotSupportedException e){}
              try
              {
                reader.setFeature("http://xml.org/sax/features/namespace-prefixes",
                                  true);
              } catch (org.xml.sax.SAXException se) {}

              th.setResult(strResult);

              reader.parse(new InputSource(inFileName));
              }
            }
            else
            {
              if (entityResolver != null)
              {
                XMLReader reader = null;

                // Use JAXP1.1 ( if possible )
                try
                {
                  javax.xml.parsers.SAXParserFactory factory =
                    javax.xml.parsers.SAXParserFactory.newInstance();

                  factory.setNamespaceAware(true);

                  if (isSecureProcessing)
                  {
                    try
                    {
                      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
                    }
                    catch (org.xml.sax.SAXException se) {}
                  }

                  javax.xml.parsers.SAXParser jaxpParser =
                    factory.newSAXParser();

                  reader = jaxpParser.getXMLReader();
                }
                catch (javax.xml.parsers.ParserConfigurationException ex)
                {
                  throw new org.xml.sax.SAXException(ex);
                }
                catch (javax.xml.parsers.FactoryConfigurationError ex1)
                {
                  throw new org.xml.sax.SAXException(ex1.toString());
                }
                catch (NoSuchMethodError ex2){}
                catch (AbstractMethodError ame){}

                if (null == reader)
                {
                  reader = XMLReaderFactory.createXMLReader();
                }

                reader.setEntityResolver(entityResolver);

                if (contentHandler != null)
                {
                  SAXResult result = new SAXResult(contentHandler);

                  transformer.transform(
                    new SAXSource(reader, new InputSource(inFileName)),
                    result);
                }
                else
                {
                  transformer.transform(
                    new SAXSource(reader, new InputSource(inFileName)),
                    strResult);
                }
              }
              else if (contentHandler != null)
              {
                SAXResult result = new SAXResult(contentHandler);

                transformer.transform(new StreamSource(inFileName), result);
              }
              else
              {
                // System.out.println("Starting transform");
                transformer.transform(new StreamSource(inFileName),
                                      strResult);
                // System.out.println("Done with transform");
              }
            }
          }
          else
          {
            StringReader reader =
              new StringReader("<?xml version=\"1.0\"?> <doc/>");

            transformer.transform(new StreamSource(reader), strResult);
          }
        }
        else
        {
//          "XSL Process was not successful.");
            msg = XSLMessages.createMessage(
                XSLTErrorResources.ER_NOT_SUCCESSFUL, null);
          diagnosticsWriter.println(msg);
          doExit(msg);
        }

        // close output streams
        if (null != outFileName && strResult!=null)
        {
          java.io.OutputStream out = strResult.getOutputStream();
          java.io.Writer writer = strResult.getWriter();
          try
          {
            if (out != null) out.close();
            if (writer != null) writer.close();
          }
          catch(java.io.IOException ie) {}
        }

        long stop = System.currentTimeMillis();
        long millisecondsDuration = stop - start;

        if (doDiag)
        {
                Object[] msgArgs = new Object[]{ inFileName, xslFileName, new Long(millisecondsDuration) };
            msg = XSLMessages.createMessage("diagTiming", msgArgs);
                diagnosticsWriter.println('\n');
                diagnosticsWriter.println(msg);
        }

      }
      catch (Throwable throwable)
      {
        while (throwable
               instanceof com.sun.org.apache.xml.internal.utils.WrappedRuntimeException)
        {
          throwable =
            ((com.sun.org.apache.xml.internal.utils.WrappedRuntimeException) throwable).getException();
        }

        if ((throwable instanceof NullPointerException)
                || (throwable instanceof ClassCastException))
          doStackDumpOnError = true;

        diagnosticsWriter.println();

        if (doStackDumpOnError)
          throwable.printStackTrace(dumpWriter);
        else
        {
          DefaultErrorHandler.printLocation(diagnosticsWriter, throwable);
          diagnosticsWriter.println(
            XSLMessages.createMessage(XSLTErrorResources.ER_XSLT_ERROR, null)
            + " (" + throwable.getClass().getName() + "): "
            + throwable.getMessage());
        }

        // diagnosticsWriter.println(XSLMessages.createMessage(XSLTErrorResources.ER_NOT_SUCCESSFUL, null)); //"XSL Process was not successful.");
        if (null != dumpFileName)
        {
          dumpWriter.close();
        }

        doExit(throwable.getMessage());
      }

      if (null != dumpFileName)
      {
        dumpWriter.close();
      }

      if (null != diagnosticsWriter)
      {

        // diagnosticsWriter.close();
      }

      // if(!setQuietMode)
      //  diagnosticsWriter.println(resbundle.getString("xsldone")); //"Xalan: done");
      // else
      // diagnosticsWriter.println("");  //"Xalan: done");
    }
  }

  /** It is _much_ easier to debug under VJ++ if I can set a single breakpoint
   * before this blows itself out of the water...
   * (I keep checking this in, it keeps vanishing. Grr!)
   * */
  static void doExit(String msg)
  {
    throw new RuntimeException(msg);
  }

  /**
   * Wait for a return key to continue
   *
   * @param resbundle The resource bundle
   */
  private static void waitForReturnKey(ResourceBundle resbundle)
  {
    System.out.println(resbundle.getString("xslProc_return_to_continue"));
    try
    {
      while (System.in.read() != '\n');
    }
    catch (java.io.IOException e) { }
  }

  /**
   * Print a message if an option cannot be used with -XSLTC.
   *
   * @param option The option String
   */
  private static void printInvalidXSLTCOption(String option)
  {
    System.err.println(XSLMessages.createMessage("xslProc_invalid_xsltc_option", new Object[]{option}));
  }

  /**
   * Print a message if an option can only be used with -XSLTC.
   *
   * @param option The option String
   */
  private static void printInvalidXalanOption(String option)
  {
    System.err.println(XSLMessages.createMessage("xslProc_invalid_xalan_option", new Object[]{option}));
  }
}
