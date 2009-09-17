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
package com.sun.tools.internal.ws.resources;

import com.sun.xml.internal.ws.util.localization.Localizable;
import com.sun.xml.internal.ws.util.localization.LocalizableMessageFactory;
import com.sun.xml.internal.ws.util.localization.Localizer;


/**
 * Defines string formatting method for each constant in the resource file
 *
 */
public final class WscompileMessages {

    private final static LocalizableMessageFactory messageFactory = new LocalizableMessageFactory("com.sun.tools.internal.ws.resources.wscompile");
    private final static Localizer localizer = new Localizer();

    public static Localizable localizableWSGEN_CLASS_MUST_BE_IMPLEMENTATION_CLASS(Object arg0) {
        return messageFactory.getMessage("wsgen.class.must.be.implementation.class", arg0);
    }

    /**
     * The class "{0}" is not an endpoint implementation class.
     *
     */
    public static String WSGEN_CLASS_MUST_BE_IMPLEMENTATION_CLASS(Object arg0) {
        return localizer.localize(localizableWSGEN_CLASS_MUST_BE_IMPLEMENTATION_CLASS(arg0));
    }

    public static Localizable localizableWSGEN_CLASS_NOT_FOUND(Object arg0) {
        return messageFactory.getMessage("wsgen.class.not.found", arg0);
    }

    /**
     * Class not found: "{0}"
     *
     */
    public static String WSGEN_CLASS_NOT_FOUND(Object arg0) {
        return localizer.localize(localizableWSGEN_CLASS_NOT_FOUND(arg0));
    }

    public static Localizable localizableWSIMPORT_HTTP_REDIRECT(Object arg0, Object arg1) {
        return messageFactory.getMessage("wsimport.httpRedirect", arg0, arg1);
    }

    /**
     * Server returned HTTP Status code: "{0}", retrying with "{1}"
     *
     */
    public static String WSIMPORT_HTTP_REDIRECT(Object arg0, Object arg1) {
        return localizer.localize(localizableWSIMPORT_HTTP_REDIRECT(arg0, arg1));
    }

    public static Localizable localizableWSIMPORT_AUTH_INFO_NEEDED(Object arg0, Object arg1, Object arg2) {
        return messageFactory.getMessage("wsimport.authInfoNeeded", arg0, arg1, arg2);
    }

    /**
     * {0},  "{1}" needs authorization, please provide authorization file with read access at {2} or use -Xauthfile to give the authorization file and on each line provide authorization information using this format : http[s]://user:password@host:port//<url-path>
     *
     */
    public static String WSIMPORT_AUTH_INFO_NEEDED(Object arg0, Object arg1, Object arg2) {
        return localizer.localize(localizableWSIMPORT_AUTH_INFO_NEEDED(arg0, arg1, arg2));
    }

    public static Localizable localizableWSGEN_USAGE_EXAMPLES() {
        return messageFactory.getMessage("wsgen.usage.examples");
    }

    /**
     *
     * Examples:
     *   wsgen -cp . example.Stock
     *   wsgen -cp . example.Stock -wsdl -servicename '{http://mynamespace}MyService'
     *
     *
     */
    public static String WSGEN_USAGE_EXAMPLES() {
        return localizer.localize(localizableWSGEN_USAGE_EXAMPLES());
    }

    public static Localizable localizableWRAPPER_TASK_LOADING_20_API(Object arg0) {
        return messageFactory.getMessage("wrapperTask.loading20Api", arg0);
    }

    /**
     * You are loading JAX-WS 2.0 API from {0} but this tool requires JAX-WS 2.1 API.
     *
     */
    public static String WRAPPER_TASK_LOADING_20_API(Object arg0) {
        return localizer.localize(localizableWRAPPER_TASK_LOADING_20_API(arg0));
    }

    public static Localizable localizableWSGEN_INVALID_PROTOCOL(Object arg0, Object arg1) {
        return messageFactory.getMessage("wsgen.invalid.protocol", arg0, arg1);
    }

    /**
     * "{0}" is not a supported protocol.  Supported protocols include: {1}.
     *
     */
    public static String WSGEN_INVALID_PROTOCOL(Object arg0, Object arg1) {
        return localizer.localize(localizableWSGEN_INVALID_PROTOCOL(arg0, arg1));
    }

    public static Localizable localizableWSIMPORT_USAGE_EXAMPLES() {
        return messageFactory.getMessage("wsimport.usage.examples");
    }

    /**
     *
     * Examples:
     *   wsimport stock.wsdl -b stock.xml -b stock.xjb
     *   wsimport -d generated http://example.org/stock?wsdl
     *
     *
     */
    public static String WSIMPORT_USAGE_EXAMPLES() {
        return localizer.localize(localizableWSIMPORT_USAGE_EXAMPLES());
    }

    public static Localizable localizableINVOKER_NEED_ENDORSED() {
        return messageFactory.getMessage("invoker.needEndorsed");
    }

    /**
     * You are running on JDK6 which comes with JAX-WS 2.0 API, but this tool requires JAX-WS 2.1 API. Use the endorsed standards override mechanism (http://java.sun.com/javase/6/docs/technotes/guides/standards/), or use -Xendorsed option.
     *
     */
    public static String INVOKER_NEED_ENDORSED() {
        return localizer.localize(localizableINVOKER_NEED_ENDORSED());
    }

    public static Localizable localizableWSIMPORT_MISSING_FILE() {
        return messageFactory.getMessage("wsimport.missingFile");
    }

    /**
     * Missing WSDL_URI
     *
     */
    public static String WSIMPORT_MISSING_FILE() {
        return localizer.localize(localizableWSIMPORT_MISSING_FILE());
    }

    public static Localizable localizableWSIMPORT_USAGE_EXTENSIONS() {
        return messageFactory.getMessage("wsimport.usage.extensions");
    }

    /**
     *
     * Extensions:
     *   -XadditionalHeaders         map headers not bound to request or response message to
     *                               Java method parameters
     *   -Xauthfile                  file to carry authorization information in the format
     *                               http://username:password@example.org/stock?wsdl
     *   -Xdebug                     print debug information
     *   -Xno-addressing-databinding enable binding of W3C EndpointReferenceType to Java
     *   -Xnocompile                 do not compile generated Java files
     *
     *
     */
    public static String WSIMPORT_USAGE_EXTENSIONS() {
        return localizer.localize(localizableWSIMPORT_USAGE_EXTENSIONS());
    }

    public static Localizable localizableWSIMPORT_USAGE(Object arg0) {
        return messageFactory.getMessage("wsimport.usage", arg0);
    }

    /**
     * Usage: {0} [options] <WSDL_URI>
     *
     * Use "wsimport -help" for a detailed description of options.
     *
     */
    public static String WSIMPORT_USAGE(Object arg0) {
        return localizer.localize(localizableWSIMPORT_USAGE(arg0));
    }

    public static Localizable localizablePLEASE() {
        return messageFactory.getMessage("Please");
    }

    /**
     * specify "-extension" and "-wsdl:protocol XSoap1.2" switches. For example:
     *
     *
     *
     */
    public static String PLEASE() {
        return localizer.localize(localizablePLEASE());
    }

    public static Localizable localizableWSIMPORT_PARSING_WSDL() {
        return messageFactory.getMessage("wsimport.ParsingWSDL");
    }

    /**
     * parsing WSDL...
     *
     *
     *
     */
    public static String WSIMPORT_PARSING_WSDL() {
        return localizer.localize(localizableWSIMPORT_PARSING_WSDL());
    }

    public static Localizable localizableWSGEN_MISSING_FILE() {
        return messageFactory.getMessage("wsgen.missingFile");
    }

    /**
     * Missing SEI
     *
     */
    public static String WSGEN_MISSING_FILE() {
        return localizer.localize(localizableWSGEN_MISSING_FILE());
    }

    public static Localizable localizableWSIMPORT_HELP(Object arg0) {
        return messageFactory.getMessage("wsimport.help", arg0);
    }

    /**
     *
     * Usage: {0} [options] <WSDL_URI>
     *
     * where [options] include:
     *   -b <path>                 specify jaxws/jaxb binding files or additional schemas
     *                             (Each <path> must have its own -b)
     *   -B<jaxbOption>            Pass this option to JAXB schema compiler
     *   -catalog <file>           specify catalog file to resolve external entity references
     *                             supports TR9401, XCatalog, and OASIS XML Catalog format.
     *   -d <directory>            specify where to place generated output files
     *   -extension                allow vendor extensions - functionality not specified
     *                             by the specification.  Use of extensions may
     *                             result in applications that are not portable or
     *                             may not interoperate with other implementations
     *   -help                     display help
     *   -httpproxy:<host>:<port>  specify a HTTP proxy server (port defaults to 8080)
     *   -keep                     keep generated files
     *   -p <pkg>                  specifies the target package
     *   -quiet                    suppress wsimport output
     *   -s <directory>            specify where to place generated source files
     *   -target <version>         generate code as per the given JAXWS spec version
     *                             e.g. 2.0 will generate compliant code for JAXWS 2.0 spec
     *   -verbose                  output messages about what the compiler is doing
     *   -version                  print version information
     *   -wsdllocation <location>  @WebServiceClient.wsdlLocation value
     *
     *
     */
    public static String WSIMPORT_HELP(Object arg0) {
        return localizer.localize(localizableWSIMPORT_HELP(arg0));
    }

    public static Localizable localizableWSCOMPILE_ERROR(Object arg0) {
        return messageFactory.getMessage("wscompile.error", arg0);
    }

    /**
     * error: {0}
     *
     */
    public static String WSCOMPILE_ERROR(Object arg0) {
        return localizer.localize(localizableWSCOMPILE_ERROR(arg0));
    }

    public static Localizable localizableWSGEN_PROTOCOL_WITHOUT_EXTENSION(Object arg0) {
        return messageFactory.getMessage("wsgen.protocol.without.extension", arg0);
    }

    /**
     * The optional protocol "{0}" must be used in conjunction with the "-extension" option.
     *
     */
    public static String WSGEN_PROTOCOL_WITHOUT_EXTENSION(Object arg0) {
        return localizer.localize(localizableWSGEN_PROTOCOL_WITHOUT_EXTENSION(arg0));
    }

    public static Localizable localizableWSIMPORT_COMPILING_CODE() {
        return messageFactory.getMessage("wsimport.CompilingCode");
    }

    /**
     *
     * compiling code...
     *
     *
     */
    public static String WSIMPORT_COMPILING_CODE() {
        return localizer.localize(localizableWSIMPORT_COMPILING_CODE());
    }

    public static Localizable localizableWSIMPORT_READING_AUTH_FILE(Object arg0) {
        return messageFactory.getMessage("wsimport.readingAuthFile", arg0);
    }

    /**
     * Trying to read authorization file : "{0}"...
     *
     */
    public static String WSIMPORT_READING_AUTH_FILE(Object arg0) {
        return localizer.localize(localizableWSIMPORT_READING_AUTH_FILE(arg0));
    }

    public static Localizable localizableWSGEN_NO_WEBSERVICES_CLASS(Object arg0) {
        return messageFactory.getMessage("wsgen.no.webservices.class", arg0);
    }

    /**
     * wsgen did not find any class with @WebService annotation. Please specify @WebService annotation on {0}.
     *
     */
    public static String WSGEN_NO_WEBSERVICES_CLASS(Object arg0) {
        return localizer.localize(localizableWSGEN_NO_WEBSERVICES_CLASS(arg0));
    }

    public static Localizable localizableWSCOMPILE_NO_SUCH_DIRECTORY(Object arg0) {
        return messageFactory.getMessage("wscompile.noSuchDirectory", arg0);
    }

    /**
     * directory not found: {0}
     *
     */
    public static String WSCOMPILE_NO_SUCH_DIRECTORY(Object arg0) {
        return localizer.localize(localizableWSCOMPILE_NO_SUCH_DIRECTORY(arg0));
    }

    public static Localizable localizableWSCOMPILE_INFO(Object arg0) {
        return messageFactory.getMessage("wscompile.info", arg0);
    }

    /**
     * info: {0}
     *
     */
    public static String WSCOMPILE_INFO(Object arg0) {
        return localizer.localize(localizableWSCOMPILE_INFO(arg0));
    }

    public static Localizable localizableWSIMPORT_MAX_REDIRECT_ATTEMPT() {
        return messageFactory.getMessage("wsimport.maxRedirectAttempt");
    }

    /**
     * Can not get a WSDL maximum number of redirects(5) reached
     *
     */
    public static String WSIMPORT_MAX_REDIRECT_ATTEMPT() {
        return localizer.localize(localizableWSIMPORT_MAX_REDIRECT_ATTEMPT());
    }

    public static Localizable localizableWSIMPORT_WARNING_MESSAGE(Object arg0) {
        return messageFactory.getMessage("wsimport.WarningMessage", arg0);
    }

    /**
     * [WARNING] {0}
     *
     */
    public static String WSIMPORT_WARNING_MESSAGE(Object arg0) {
        return localizer.localize(localizableWSIMPORT_WARNING_MESSAGE(arg0));
    }

    public static Localizable localizableWSCOMPILE_INVALID_OPTION(Object arg0) {
        return messageFactory.getMessage("wscompile.invalidOption", arg0);
    }

    /**
     * unrecognized parameter {0}
     *
     */
    public static String WSCOMPILE_INVALID_OPTION(Object arg0) {
        return localizer.localize(localizableWSCOMPILE_INVALID_OPTION(arg0));
    }

    public static Localizable localizableWSIMPORT_ERROR_MESSAGE(Object arg0) {
        return messageFactory.getMessage("wsimport.ErrorMessage", arg0);
    }

    /**
     * [ERROR] {0}
     *
     */
    public static String WSIMPORT_ERROR_MESSAGE(Object arg0) {
        return localizer.localize(localizableWSIMPORT_ERROR_MESSAGE(arg0));
    }

    public static Localizable localizableWSIMPORT_GENERATING_CODE() {
        return messageFactory.getMessage("wsimport.GeneratingCode");
    }

    /**
     * generating code...
     *
     *
     */
    public static String WSIMPORT_GENERATING_CODE() {
        return localizer.localize(localizableWSIMPORT_GENERATING_CODE());
    }

    public static Localizable localizableWSGEN() {
        return messageFactory.getMessage("wsgen");
    }

    /**
     * -wsdl:protocol XSoap1.2 -extenson {1}
     *
     */
    public static String WSGEN() {
        return localizer.localize(localizableWSGEN());
    }

    public static Localizable localizableWSIMPORT_NOT_A_FILE_NOR_URL(Object arg0) {
        return messageFactory.getMessage("wsimport.NotAFileNorURL", arg0);
    }

    /**
     * "{0}" is neither a file name nor an URL
     *
     */
    public static String WSIMPORT_NOT_A_FILE_NOR_URL(Object arg0) {
        return localizer.localize(localizableWSIMPORT_NOT_A_FILE_NOR_URL(arg0));
    }

    public static Localizable localizableWSCOMPILE_WARNING(Object arg0) {
        return messageFactory.getMessage("wscompile.warning", arg0);
    }

    /**
     * warning: {0}
     *
     */
    public static String WSCOMPILE_WARNING(Object arg0) {
        return localizer.localize(localizableWSCOMPILE_WARNING(arg0));
    }

    public static Localizable localizableWRAPPER_TASK_NEED_ENDORSED(Object arg0) {
        return messageFactory.getMessage("wrapperTask.needEndorsed", arg0);
    }

    /**
     * You are running on JDK6 which comes with JAX-WS 2.0 API, but this tool requires JAX-WS 2.1 API. Use the endorsed standards override mechanism (http://java.sun.com/javase/6/docs/technotes/guides/standards/), or set xendorsed="true" on <{0}>.
     *
     */
    public static String WRAPPER_TASK_NEED_ENDORSED(Object arg0) {
        return localizer.localize(localizableWRAPPER_TASK_NEED_ENDORSED(arg0));
    }

    public static Localizable localizableWSIMPORT_NO_SUCH_JAXB_OPTION(Object arg0) {
        return messageFactory.getMessage("wsimport.noSuchJaxbOption", arg0);
    }

    /**
     * no such JAXB option: {0}
     *
     */
    public static String WSIMPORT_NO_SUCH_JAXB_OPTION(Object arg0) {
        return localizer.localize(localizableWSIMPORT_NO_SUCH_JAXB_OPTION(arg0));
    }

    public static Localizable localizableWSIMPORT_AUTH_FILE_NOT_FOUND(Object arg0, Object arg1) {
        return messageFactory.getMessage("wsimport.authFileNotFound", arg0, arg1);
    }

    /**
     * Authorization file "{0}" not found. If the WSDL access needs Basic Authentication, please provide authorization file with read access at {1} or use -Xauthfile to give the authorization file and on each line provide authorization information using this format : http[s]://user:password@host:port//<url-path>
     *
     */
    public static String WSIMPORT_AUTH_FILE_NOT_FOUND(Object arg0, Object arg1) {
        return localizer.localize(localizableWSIMPORT_AUTH_FILE_NOT_FOUND(arg0, arg1));
    }

    public static Localizable localizableWSIMPORT_DEBUG_MESSAGE(Object arg0) {
        return messageFactory.getMessage("wsimport.DebugMessage", arg0);
    }

    /**
     * [DEBUG] {0}
     *
     */
    public static String WSIMPORT_DEBUG_MESSAGE(Object arg0) {
        return localizer.localize(localizableWSIMPORT_DEBUG_MESSAGE(arg0));
    }

    public static Localizable localizableWSGEN_COULD_NOT_CREATE_FILE(Object arg0) {
        return messageFactory.getMessage("wsgen.could.not.create.file", arg0);
    }

    /**
     * "Could not create file: "{0}"
     *
     */
    public static String WSGEN_COULD_NOT_CREATE_FILE(Object arg0) {
        return localizer.localize(localizableWSGEN_COULD_NOT_CREATE_FILE(arg0));
    }

    public static Localizable localizableWSGEN_WSDL_ARG_NO_GENWSDL(Object arg0) {
        return messageFactory.getMessage("wsgen.wsdl.arg.no.genwsdl", arg0);
    }

    /**
     * The "{0}" option can only be in conjunction with the "-wsdl" option.
     *
     */
    public static String WSGEN_WSDL_ARG_NO_GENWSDL(Object arg0) {
        return localizer.localize(localizableWSGEN_WSDL_ARG_NO_GENWSDL(arg0));
    }

    public static Localizable localizableWSGEN_HELP(Object arg0, Object arg1, Object arg2) {
        return messageFactory.getMessage("wsgen.help", arg0, arg1, arg2);
    }

    /**
     *
     * Usage: {0} [options] <SEI>
     *
     * where [options] include:
     *   -classpath <path>          specify where to find input class files
     *   -cp <path>                 same as -classpath <path>
     *   -d <directory>             specify where to place generated output files
     *   -extension                 allow vendor extensions - functionality not specified
     *                              by the specification.  Use of extensions may
     *                              result in applications that are not portable or
     *                              may not interoperate with other implementations
     *   -help                      display help
     *   -keep                      keep generated files
     *   -r <directory>             resource destination directory, specify where to
     *                              place resouce files such as WSDLs
     *   -s <directory>             specify where to place generated source files
     *   -verbose                   output messages about what the compiler is doing
     *   -version                   print version information
     *   -wsdl[:protocol]           generate a WSDL file. The protocol is optional.
     *                              Valid protocols are {1},
     *                              the default is soap1.1.
     *                              The non stanadard protocols {2}
     *                              can only be used in conjunction with the
     *                              -extension option.
     *   -servicename <name>        specify the Service name to use in the generated WSDL
     *                              Used in conjunction with the -wsdl option.
     *   -portname <name>           specify the Port name to use in the generated WSDL
     *                              Used in conjunction with the -wsdl option.
     *
     */
    public static String WSGEN_HELP(Object arg0, Object arg1, Object arg2) {
        return localizer.localize(localizableWSGEN_HELP(arg0, arg1, arg2));
    }

    public static Localizable localizableWSIMPORT_INFO_MESSAGE(Object arg0) {
        return messageFactory.getMessage("wsimport.InfoMessage", arg0);
    }

    /**
     * [INFO] {0}
     *
     */
    public static String WSIMPORT_INFO_MESSAGE(Object arg0) {
        return localizer.localize(localizableWSIMPORT_INFO_MESSAGE(arg0));
    }

    public static Localizable localizableWSGEN_SOAP_12_WITHOUT_EXTENSION() {
        return messageFactory.getMessage("wsgen.soap12.without.extension");
    }

    /**
     * The optional protocol "Xsoap1.2" must be used in conjunction with the "-extension" option.
     *
     */
    public static String WSGEN_SOAP_12_WITHOUT_EXTENSION() {
        return localizer.localize(localizableWSGEN_SOAP_12_WITHOUT_EXTENSION());
    }

    public static Localizable localizableWSIMPORT_ILLEGAL_AUTH_INFO(Object arg0) {
        return messageFactory.getMessage("wsimport.ILLEGAL_AUTH_INFO", arg0);
    }

    /**
     * "{0}" is not a valid authorization information format. The format is http[s]://user:password@host:port//<url-path>.
     *
     */
    public static String WSIMPORT_ILLEGAL_AUTH_INFO(Object arg0) {
        return localizer.localize(localizableWSIMPORT_ILLEGAL_AUTH_INFO(arg0));
    }

    public static Localizable localizableWSCOMPILE_COMPILATION_FAILED() {
        return messageFactory.getMessage("wscompile.compilationFailed");
    }

    /**
     * compilation failed, errors should have been reported
     *
     */
    public static String WSCOMPILE_COMPILATION_FAILED() {
        return localizer.localize(localizableWSCOMPILE_COMPILATION_FAILED());
    }

    public static Localizable localizableWSCOMPILE_MISSING_OPTION_ARGUMENT(Object arg0) {
        return messageFactory.getMessage("wscompile.missingOptionArgument", arg0);
    }

    /**
     * option "{0}" requires an argument
     *
     */
    public static String WSCOMPILE_MISSING_OPTION_ARGUMENT(Object arg0) {
        return localizer.localize(localizableWSCOMPILE_MISSING_OPTION_ARGUMENT(arg0));
    }

    public static Localizable localizableWSGEN_CANNOT_GEN_WSDL_FOR_NON_SOAP_BINDING(Object arg0, Object arg1) {
        return messageFactory.getMessage("wsgen.cannot.gen.wsdl.for.non.soap.binding", arg0, arg1);
    }

    /**
     * wsgen can not generate WSDL for non-SOAP binding: {0} on Class {1}
     *
     */
    public static String WSGEN_CANNOT_GEN_WSDL_FOR_NON_SOAP_BINDING(Object arg0, Object arg1) {
        return localizer.localize(localizableWSGEN_CANNOT_GEN_WSDL_FOR_NON_SOAP_BINDING(arg0, arg1));
    }

    public static Localizable localizableWSCOMPILE_DUPLICATE_OPTION(Object arg0) {
        return messageFactory.getMessage("wscompile.duplicateOption", arg0);
    }

    /**
     * duplicate option: {0}
     *
     */
    public static String WSCOMPILE_DUPLICATE_OPTION(Object arg0) {
        return localizer.localize(localizableWSCOMPILE_DUPLICATE_OPTION(arg0));
    }

    public static Localizable localizableWSIMPORT_FAILED_TO_PARSE(Object arg0, Object arg1) {
        return messageFactory.getMessage("wsimport.FailedToParse", arg0, arg1);
    }

    /**
     * Failed to parse "{0}": {1}
     *
     */
    public static String WSIMPORT_FAILED_TO_PARSE(Object arg0, Object arg1) {
        return localizer.localize(localizableWSIMPORT_FAILED_TO_PARSE(arg0, arg1));
    }

    public static Localizable localizableWSIMPORT_NO_WSDL(Object arg0) {
        return messageFactory.getMessage("wsimport.no.wsdl", arg0);
    }

    /**
     * Failed to read the WSDL document: {0}, because 1) could not find the document; /2) the document could not be read; 3) the root element of the document is not <wsdl:definitions>.
     *
     */
    public static String WSIMPORT_NO_WSDL(Object arg0) {
        return localizer.localize(localizableWSIMPORT_NO_WSDL(arg0));
    }

    public static Localizable localizableWSIMPORT_AUTH_INFO_LINENO(Object arg0, Object arg1) {
        return messageFactory.getMessage("wsimport.AUTH_INFO_LINENO", arg0, arg1);
    }

    /**
     * "line {0} of {1}
     *
     */
    public static String WSIMPORT_AUTH_INFO_LINENO(Object arg0, Object arg1) {
        return localizer.localize(localizableWSIMPORT_AUTH_INFO_LINENO(arg0, arg1));
    }

    public static Localizable localizableWSGEN_USAGE(Object arg0) {
        return messageFactory.getMessage("wsgen.usage", arg0);
    }

    /**
     * Usage: {0} [options] <SEI>
     *
     * Use "wsgen -help" for a detailed description of options.
     *
     */
    public static String WSGEN_USAGE(Object arg0) {
        return localizer.localize(localizableWSGEN_USAGE(arg0));
    }

    public static Localizable localizableWSGEN_SERVICENAME_MISSING_LOCALNAME(Object arg0) {
        return messageFactory.getMessage("wsgen.servicename.missing.localname", arg0);
    }

    /**
     * The service name "{0}" is missing a localname.
     *
     */
    public static String WSGEN_SERVICENAME_MISSING_LOCALNAME(Object arg0) {
        return localizer.localize(localizableWSGEN_SERVICENAME_MISSING_LOCALNAME(arg0));
    }

    public static Localizable localizableWSGEN_SERVICENAME_MISSING_NAMESPACE(Object arg0) {
        return messageFactory.getMessage("wsgen.servicename.missing.namespace", arg0);
    }

    /**
     * The service name "{0}" is missing a namespace.
     *
     */
    public static String WSGEN_SERVICENAME_MISSING_NAMESPACE(Object arg0) {
        return localizer.localize(localizableWSGEN_SERVICENAME_MISSING_NAMESPACE(arg0));
    }

    public static Localizable localizableWSGEN_INVALID_TRANSPORT(Object arg0, Object arg1) {
        return messageFactory.getMessage("wsgen.invalid.transport", arg0, arg1);
    }

    /**
     * "{0}" is not a supported transport.  Supported transport include: {1}.
     *
     */
    public static String WSGEN_INVALID_TRANSPORT(Object arg0, Object arg1) {
        return localizer.localize(localizableWSGEN_INVALID_TRANSPORT(arg0, arg1));
    }

    public static Localizable localizableWSGEN_CANNOT_GEN_WSDL_FOR_SOAP_12_BINDING(Object arg0, Object arg1) {
        return messageFactory.getMessage("wsgen.cannot.gen.wsdl.for.soap12.binding", arg0, arg1);
    }

    /**
     * wsgen can not generate WSDL for SOAP 1.2 binding: {0} on class: {1}.
     *
     *
     */
    public static String WSGEN_CANNOT_GEN_WSDL_FOR_SOAP_12_BINDING(Object arg0, Object arg1) {
        return localizer.localize(localizableWSGEN_CANNOT_GEN_WSDL_FOR_SOAP_12_BINDING(arg0, arg1));
    }

    public static Localizable localizableWSIMPORT_ILLEGAL_TARGET_VERSION(Object arg0) {
        return messageFactory.getMessage("wsimport.ILLEGAL_TARGET_VERSION", arg0);
    }

    /**
     * "{0}" is not a valid target version. "2.0" and "2.1" are supported.
     *
     */
    public static String WSIMPORT_ILLEGAL_TARGET_VERSION(Object arg0) {
        return localizer.localize(localizableWSIMPORT_ILLEGAL_TARGET_VERSION(arg0));
    }

    public static Localizable localizableWSGEN_PORTNAME_MISSING_LOCALNAME(Object arg0) {
        return messageFactory.getMessage("wsgen.portname.missing.localname", arg0);
    }

    /**
     * The port name "{0}" is missing a localname.
     *
     */
    public static String WSGEN_PORTNAME_MISSING_LOCALNAME(Object arg0) {
        return localizer.localize(localizableWSGEN_PORTNAME_MISSING_LOCALNAME(arg0));
    }

    public static Localizable localizableWSGEN_PORTNAME_MISSING_NAMESPACE(Object arg0) {
        return messageFactory.getMessage("wsgen.portname.missing.namespace", arg0);
    }

    /**
     * The port name "{0}" is missing a namespace.
     *
     */
    public static String WSGEN_PORTNAME_MISSING_NAMESPACE(Object arg0) {
        return localizer.localize(localizableWSGEN_PORTNAME_MISSING_NAMESPACE(arg0));
    }

}
