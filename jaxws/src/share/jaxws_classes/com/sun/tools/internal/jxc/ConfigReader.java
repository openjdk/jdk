/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.internal.jxc;

import com.sun.tools.internal.jxc.ap.Options;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.bind.SchemaOutputResolver;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.Result;
import javax.xml.transform.stream.StreamResult;
import javax.xml.validation.ValidatorHandler;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.TypeElement;
import com.sun.tools.internal.jxc.gen.config.Config;
import com.sun.tools.internal.jxc.gen.config.Schema;
import com.sun.tools.internal.xjc.SchemaCache;
import com.sun.tools.internal.xjc.api.Reference;
import com.sun.tools.internal.xjc.util.ForkContentHandler;

import com.sun.xml.internal.bind.v2.util.XmlFactory;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;


/**
 * This reads the config files passed by the user to annotation processing
 * and obtains a list of classes that need to be included
 * for a particular config from the set of classes passed
 * by the user to annotation processing.
 *
 * @author Bhakti Mehta (bhakti.mehta@sun.com)
 */
public final class ConfigReader  {

    /**
     * The set of classes to be passed to XJC
     *
     */
    private final Set<Reference> classesToBeIncluded = new HashSet<Reference>();


    /**
     *  The SchemaOutputResolver used to generate the schemas
     */
    private final SchemaOutputResolver schemaOutputResolver;

    private final ProcessingEnvironment env;

    /**
     *
     * @param classes
     *      The set of classes passed to the AnnotationProcessor
     * @param xmlFile
     *      The configuration file.
     * @throws SAXException
     *      If this is thrown, the error has already been reported.
     * @throws IOException
     *     If any IO errors occur.
     */
    public ConfigReader(ProcessingEnvironment env, Collection<? extends TypeElement> classes, File xmlFile, ErrorHandler errorHandler) throws SAXException, IOException {
        this.env = env;
        Config config = parseAndGetConfig(xmlFile, errorHandler, env.getOptions().containsKey(Options.DISABLE_XML_SECURITY));
        checkAllClasses(config,classes);
        String path =   xmlFile.getAbsolutePath();
        String xmlPath = path.substring(0,path.lastIndexOf(File.separatorChar));
        schemaOutputResolver = createSchemaOutputResolver(config,xmlPath);

    }


    /**
     * This creates a regular expression
     * for the user pattern , matches the input classes
     * passed by the user and returns the final
     * list of classes that need to be included for a config file
     * after applying those patterns
     *
     */
    public Collection<Reference> getClassesToBeIncluded() {
        return classesToBeIncluded;
    }

    private void checkAllClasses(Config config, Collection<? extends TypeElement> rootClasses) {

        List<Pattern> includeRegexList = config.getClasses().getIncludes();
        List<Pattern>  excludeRegexList = config.getClasses().getExcludes();

        OUTER:
        for (TypeElement typeDecl : rootClasses) {

            String qualifiedName = typeDecl.getQualifiedName().toString();

            for (Pattern pattern : excludeRegexList) {
                boolean match = checkPatternMatch(qualifiedName, pattern);
                if (match)
                    continue OUTER; // excluded
            }

            for (Pattern pattern : includeRegexList) {
                boolean match = checkPatternMatch(qualifiedName, pattern);
                if (match) {
                    classesToBeIncluded.add(new Reference(typeDecl,env));
                    break;
                }
            }
        }
    }

    /**
     * This returns the SchemaOutputResolver to generate the schemas
     */
    public SchemaOutputResolver getSchemaOutputResolver(){
        return schemaOutputResolver;
    }

    private SchemaOutputResolver createSchemaOutputResolver(Config config, String xmlpath) {
        File baseDir = new File(xmlpath, config.getBaseDir().getPath());
        SchemaOutputResolverImpl outResolver = new SchemaOutputResolverImpl (baseDir);

        for( Schema schema : (List<Schema>)config.getSchema() ) {
            String namespace = schema.getNamespace();
            File location = schema.getLocation();
            outResolver.addSchemaInfo(namespace,location);
        }
        return outResolver;
    }

    /**
     * This will  check if the qualified name matches the pattern
     *
     * @param qualifiedName
     *      The qualified name of the TypeDeclaration
     * @param pattern
     *       The  pattern obtained from the users input
     *
     */
    private boolean checkPatternMatch(String qualifiedName, Pattern pattern) {
        Matcher matcher = pattern.matcher(qualifiedName);
        return matcher.matches();
    }



    /**
     * Lazily parsed schema for the binding file.
     */
    private static SchemaCache configSchema = new SchemaCache(Config.class.getResource("config.xsd"));


    /**
     * Parses an xml config file and returns a Config object.
     *
     * @param xmlFile
     *        The xml config file which is passed by the user to annotation processing
     * @return
     *        A non null Config object
     */
    private Config parseAndGetConfig (File xmlFile, ErrorHandler errorHandler, boolean disableSecureProcessing) throws SAXException, IOException {
        XMLReader reader;
        try {
            SAXParserFactory factory = XmlFactory.createParserFactory(disableSecureProcessing);
            reader = factory.newSAXParser().getXMLReader();
        } catch (ParserConfigurationException e) {
            // in practice this will never happen
            throw new Error(e);
        }
        NGCCRuntimeEx runtime = new NGCCRuntimeEx(errorHandler);

        // set up validator
        ValidatorHandler validator = configSchema.newValidator();
        validator.setErrorHandler(errorHandler);

        // the validator will receive events first, then the parser.
        reader.setContentHandler(new ForkContentHandler(validator,runtime));

        reader.setErrorHandler(errorHandler);
        Config config = new Config(runtime);
        runtime.setRootHandler(config);
        reader.parse(new InputSource(xmlFile.toURL().toExternalForm()));
        runtime.reset();

        return config;
    }
    /**
     * Controls where the JAXB RI puts the generates
     * schema files.
     * @author
     *     Bhakti Mehta (bhakti.mehta@sun.com)
     */
    private static final class SchemaOutputResolverImpl extends SchemaOutputResolver{

        /**
         * Directory to which we put the rest of the files.
         * Never be null.
         */
        private final File baseDir;

        /**
         * Namespace URI to the location of the schema.
         * This captures what the user specifies.
         */
        private final Map<String,File> schemas = new HashMap<String,File>();


        /**
         * Decides where the schema file (of the given namespace URI)
         * will be written, and return it as a {@link Result} object.
         *
         */
        public Result createOutput( String namespaceUri, String suggestedFileName ) {

            // the user's preference takes a precedence
            if(schemas.containsKey(namespaceUri)) {
                File loc = schemas.get(namespaceUri);
                if(loc==null)   return null;    // specifically not to generate a schema

                // create directories if necessary. we've already checked that the baseDir
                // exists, so this should be no surprise to users.
                loc.getParentFile().mkdirs();

                return new StreamResult(loc);   // generate into a file the user specified.
            }

            // if the user didn't say anything about this namespace,
            // generate it into the default directory with a default name.

             File schemaFile = new File (baseDir, suggestedFileName);
             // The systemId for the result will be schemaFile
             return new StreamResult(schemaFile);
        }


        public SchemaOutputResolverImpl(File baseDir) {
            assert baseDir!=null;
            this.baseDir = baseDir;
        }

        public void addSchemaInfo(String namespaceUri, File location) {
            if (namespaceUri == null )
                //generate elements in no namespace
                namespaceUri = "";
            schemas.put(namespaceUri, location);

        }

    }
}
