/*
 * Copyright (c) 2003, 2005, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
 *
 */

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.FactoryConfigurationError;
import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.w3c.dom.Document;
import org.w3c.dom.DOMException;

// For write operation
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamSource;
import javax.xml.transform.stream.StreamResult;

import java.io.*;

public class jvmtiGen
{
    /**
     * Write out usage and exit.
     */
    private static void showUsage() {
        System.err.println("usage:");
        System.err.println("  java jvmtiGen " +
                           "-IN <input XML file name> " +
                           "-XSL <XSL file> " +
                           "-OUT <output file name> " +
                           "[-PARAM <name> <expression> ...]");
        System.exit(0);         // There is no returning from showUsage()
    }

    // Global value so it can be ref'd by the tree-adapter
    static Document document;

    public static void main (String argv [])
    {
        String inFileName=null;
        String xslFileName=null;
        String outFileName=null;
        java.util.Vector<String> params=new java.util.Vector<String>();
        for (int ii = 0; ii < argv.length; ii++) {
            if (argv[ii].equals("-IN")) {
                inFileName = argv[++ii];
            } else if (argv[ii].equals("-XSL")) {
                xslFileName = argv[++ii];
            } else if (argv[ii].equals("-OUT")) {
                outFileName = argv[++ii];
            } else if (argv[ii].equals("-PARAM")) {
                if (ii + 2 < argv.length) {
                    String name = argv[++ii];
                    params.addElement(name);
                    String expression = argv[++ii];
                    params.addElement(expression);
                } else {
                    showUsage();
                }
            } else {
                showUsage();
            }
        }
        if (inFileName==null || xslFileName==null || outFileName==null){
            showUsage();
        }

        /*
         * Due to JAXP breakage in some intermediate Tiger builds, the
         * com.sun.org.apache..... parser may throw an exception:
         *   com.sun.org.apache.xml.internal.utils.WrappedRuntimeException:
         *     org.apache.xalan.serialize.SerializerToText
         *
         * To work around the problem, this program uses the
         * org.apache.xalan....  version if it is available.  It is
         * available in J2SE 1.4.x and early builds of 1.5 (Tiger).
         * It was removed at the same time the thrown exception issue
         * above was fixed, so if the class is not found we can proceed
         * and use the default parser.
         */
        final String parserProperty =
            "javax.xml.transform.TransformerFactory";
        final String workaroundParser =
            "org.apache.xalan.processor.TransformerFactoryImpl";

        try {
            java.lang.Class cls = java.lang.Class.forName(workaroundParser);
            /*
             * If we get here, we found the class.  Use it.
             */
            System.setProperty(parserProperty, workaroundParser);
            System.out.println("Info: jvmtiGen using " + parserProperty +
                               " = " + workaroundParser);
        } catch (ClassNotFoundException cnfex) {
            /*
             * We didn't find workaroundParser.  Ignore the
             * exception and proceed with default settings.
             */
        }

        DocumentBuilderFactory factory =
            DocumentBuilderFactory.newInstance();

        factory.setNamespaceAware(true);
        factory.setValidating(true);

        try {
            File datafile   = new File(inFileName);
            File stylesheet = new File(xslFileName);

            DocumentBuilder builder = factory.newDocumentBuilder();
            document = builder.parse(datafile);

            // Use a Transformer for output
            TransformerFactory tFactory =
                TransformerFactory.newInstance();
            StreamSource stylesource = new StreamSource(stylesheet);
            Transformer transformer = tFactory.newTransformer(stylesource);
            for (int ii = 0; ii < params.size(); ii += 2){
                transformer.setParameter((String) params.elementAt(ii),
                                         (String) params.elementAt(ii + 1));
            }
            DOMSource source = new DOMSource(document);

            PrintStream ps = new PrintStream( new FileOutputStream(outFileName));
            StreamResult result = new StreamResult(ps);
            transformer.transform(source, result);

        } catch (TransformerConfigurationException tce) {
           // Error generated by the parser
           System.out.println ("\n** Transformer Factory error");
           System.out.println("   " + tce.getMessage() );

           // Use the contained exception, if any
           Throwable x = tce;
           if (tce.getException() != null)
               x = tce.getException();
           x.printStackTrace();

        } catch (TransformerException te) {
           // Error generated by the parser
           System.out.println ("\n** Transformation error");
           System.out.println("   " + te.getMessage() );

           // Use the contained exception, if any
           Throwable x = te;
           if (te.getException() != null)
               x = te.getException();
           x.printStackTrace();

         } catch (SAXException sxe) {
           // Error generated by this application
           // (or a parser-initialization error)
           Exception  x = sxe;
           if (sxe.getException() != null)
               x = sxe.getException();
           x.printStackTrace();

        } catch (ParserConfigurationException pce) {
            // Parser with specified options can't be built
            pce.printStackTrace();

        } catch (IOException ioe) {
           // I/O error
           ioe.printStackTrace();
        }
    } // main
}
