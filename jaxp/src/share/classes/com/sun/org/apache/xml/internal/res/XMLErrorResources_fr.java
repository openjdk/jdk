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
 * $Id: XMLErrorResources_fr.java,v 1.2.4.1 2005/09/15 07:45:41 suresh_emailid Exp $
 */
package com.sun.org.apache.xml.internal.res;


import java.util.ListResourceBundle;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * Set up error messages.
 * We build a two dimensional array of message keys and
 * message strings. In order to add a new message here,
 * you need to first add a String constant. And you need
 * to enter key, value pair as part of the contents
 * array. You also need to update MAX_CODE for error strings
 * and MAX_WARNING for warnings ( Needed for only information
 * purpose )
 */
public class XMLErrorResources_fr extends ListResourceBundle
{

/*
 * This file contains error and warning messages related to Xalan Error
 * Handling.
 *
 *  General notes to translators:
 *
 *  1) Xalan (or more properly, Xalan-interpretive) and XSLTC are names of
 *     components.
 *     XSLT is an acronym for "XML Stylesheet Language: Transformations".
 *     XSLTC is an acronym for XSLT Compiler.
 *
 *  2) A stylesheet is a description of how to transform an input XML document
 *     into a resultant XML document (or HTML document or text).  The
 *     stylesheet itself is described in the form of an XML document.
 *
 *  3) A template is a component of a stylesheet that is used to match a
 *     particular portion of an input document and specifies the form of the
 *     corresponding portion of the output document.
 *
 *  4) An element is a mark-up tag in an XML document; an attribute is a
 *     modifier on the tag.  For example, in <elem attr='val' attr2='val2'>
 *     "elem" is an element name, "attr" and "attr2" are attribute names with
 *     the values "val" and "val2", respectively.
 *
 *  5) A namespace declaration is a special attribute that is used to associate
 *     a prefix with a URI (the namespace).  The meanings of element names and
 *     attribute names that use that prefix are defined with respect to that
 *     namespace.
 *
 *  6) "Translet" is an invented term that describes the class file that
 *     results from compiling an XML stylesheet into a Java class.
 *
 *  7) XPath is a specification that describes a notation for identifying
 *     nodes in a tree-structured representation of an XML document.  An
 *     instance of that notation is referred to as an XPath expression.
 *
 */

  /** Maximum error messages, this is needed to keep track of the number of messages.    */
  public static final int MAX_CODE = 61;

  /** Maximum warnings, this is needed to keep track of the number of warnings.          */
  public static final int MAX_WARNING = 0;

  /** Maximum misc strings.   */
  public static final int MAX_OTHERS = 4;

  /** Maximum total warnings and error messages.          */
  public static final int MAX_MESSAGES = MAX_CODE + MAX_WARNING + 1;


  /*
   * Message keys
   */
  public static final String ER_FUNCTION_NOT_SUPPORTED = "ER_FUNCTION_NOT_SUPPORTED";
  public static final String ER_CANNOT_OVERWRITE_CAUSE = "ER_CANNOT_OVERWRITE_CAUSE";
  public static final String ER_NO_DEFAULT_IMPL = "ER_NO_DEFAULT_IMPL";
  public static final String ER_CHUNKEDINTARRAY_NOT_SUPPORTED = "ER_CHUNKEDINTARRAY_NOT_SUPPORTED";
  public static final String ER_OFFSET_BIGGER_THAN_SLOT = "ER_OFFSET_BIGGER_THAN_SLOT";
  public static final String ER_COROUTINE_NOT_AVAIL = "ER_COROUTINE_NOT_AVAIL";
  public static final String ER_COROUTINE_CO_EXIT = "ER_COROUTINE_CO_EXIT";
  public static final String ER_COJOINROUTINESET_FAILED = "ER_COJOINROUTINESET_FAILED";
  public static final String ER_COROUTINE_PARAM = "ER_COROUTINE_PARAM";
  public static final String ER_PARSER_DOTERMINATE_ANSWERS = "ER_PARSER_DOTERMINATE_ANSWERS";
  public static final String ER_NO_PARSE_CALL_WHILE_PARSING = "ER_NO_PARSE_CALL_WHILE_PARSING";
  public static final String ER_TYPED_ITERATOR_AXIS_NOT_IMPLEMENTED = "ER_TYPED_ITERATOR_AXIS_NOT_IMPLEMENTED";
  public static final String ER_ITERATOR_AXIS_NOT_IMPLEMENTED = "ER_ITERATOR_AXIS_NOT_IMPLEMENTED";
  public static final String ER_ITERATOR_CLONE_NOT_SUPPORTED = "ER_ITERATOR_CLONE_NOT_SUPPORTED";
  public static final String ER_UNKNOWN_AXIS_TYPE = "ER_UNKNOWN_AXIS_TYPE";
  public static final String ER_AXIS_NOT_SUPPORTED = "ER_AXIS_NOT_SUPPORTED";
  public static final String ER_NO_DTMIDS_AVAIL = "ER_NO_DTMIDS_AVAIL";
  public static final String ER_NOT_SUPPORTED = "ER_NOT_SUPPORTED";
  public static final String ER_NODE_NON_NULL = "ER_NODE_NON_NULL";
  public static final String ER_COULD_NOT_RESOLVE_NODE = "ER_COULD_NOT_RESOLVE_NODE";
  public static final String ER_STARTPARSE_WHILE_PARSING = "ER_STARTPARSE_WHILE_PARSING";
  public static final String ER_STARTPARSE_NEEDS_SAXPARSER = "ER_STARTPARSE_NEEDS_SAXPARSER";
  public static final String ER_COULD_NOT_INIT_PARSER = "ER_COULD_NOT_INIT_PARSER";
  public static final String ER_EXCEPTION_CREATING_POOL = "ER_EXCEPTION_CREATING_POOL";
  public static final String ER_PATH_CONTAINS_INVALID_ESCAPE_SEQUENCE = "ER_PATH_CONTAINS_INVALID_ESCAPE_SEQUENCE";
  public static final String ER_SCHEME_REQUIRED = "ER_SCHEME_REQUIRED";
  public static final String ER_NO_SCHEME_IN_URI = "ER_NO_SCHEME_IN_URI";
  public static final String ER_NO_SCHEME_INURI = "ER_NO_SCHEME_INURI";
  public static final String ER_PATH_INVALID_CHAR = "ER_PATH_INVALID_CHAR";
  public static final String ER_SCHEME_FROM_NULL_STRING = "ER_SCHEME_FROM_NULL_STRING";
  public static final String ER_SCHEME_NOT_CONFORMANT = "ER_SCHEME_NOT_CONFORMANT";
  public static final String ER_HOST_ADDRESS_NOT_WELLFORMED = "ER_HOST_ADDRESS_NOT_WELLFORMED";
  public static final String ER_PORT_WHEN_HOST_NULL = "ER_PORT_WHEN_HOST_NULL";
  public static final String ER_INVALID_PORT = "ER_INVALID_PORT";
  public static final String ER_FRAG_FOR_GENERIC_URI ="ER_FRAG_FOR_GENERIC_URI";
  public static final String ER_FRAG_WHEN_PATH_NULL = "ER_FRAG_WHEN_PATH_NULL";
  public static final String ER_FRAG_INVALID_CHAR = "ER_FRAG_INVALID_CHAR";
  public static final String ER_PARSER_IN_USE = "ER_PARSER_IN_USE";
  public static final String ER_CANNOT_CHANGE_WHILE_PARSING = "ER_CANNOT_CHANGE_WHILE_PARSING";
  public static final String ER_SELF_CAUSATION_NOT_PERMITTED = "ER_SELF_CAUSATION_NOT_PERMITTED";
  public static final String ER_NO_USERINFO_IF_NO_HOST = "ER_NO_USERINFO_IF_NO_HOST";
  public static final String ER_NO_PORT_IF_NO_HOST = "ER_NO_PORT_IF_NO_HOST";
  public static final String ER_NO_QUERY_STRING_IN_PATH = "ER_NO_QUERY_STRING_IN_PATH";
  public static final String ER_NO_FRAGMENT_STRING_IN_PATH = "ER_NO_FRAGMENT_STRING_IN_PATH";
  public static final String ER_CANNOT_INIT_URI_EMPTY_PARMS = "ER_CANNOT_INIT_URI_EMPTY_PARMS";
  public static final String ER_METHOD_NOT_SUPPORTED ="ER_METHOD_NOT_SUPPORTED";
  public static final String ER_INCRSAXSRCFILTER_NOT_RESTARTABLE = "ER_INCRSAXSRCFILTER_NOT_RESTARTABLE";
  public static final String ER_XMLRDR_NOT_BEFORE_STARTPARSE = "ER_XMLRDR_NOT_BEFORE_STARTPARSE";
  public static final String ER_AXIS_TRAVERSER_NOT_SUPPORTED = "ER_AXIS_TRAVERSER_NOT_SUPPORTED";
  public static final String ER_ERRORHANDLER_CREATED_WITH_NULL_PRINTWRITER = "ER_ERRORHANDLER_CREATED_WITH_NULL_PRINTWRITER";
  public static final String ER_SYSTEMID_UNKNOWN = "ER_SYSTEMID_UNKNOWN";
  public static final String ER_LOCATION_UNKNOWN = "ER_LOCATION_UNKNOWN";
  public static final String ER_PREFIX_MUST_RESOLVE = "ER_PREFIX_MUST_RESOLVE";
  public static final String ER_CREATEDOCUMENT_NOT_SUPPORTED = "ER_CREATEDOCUMENT_NOT_SUPPORTED";
  public static final String ER_CHILD_HAS_NO_OWNER_DOCUMENT = "ER_CHILD_HAS_NO_OWNER_DOCUMENT";
  public static final String ER_CHILD_HAS_NO_OWNER_DOCUMENT_ELEMENT = "ER_CHILD_HAS_NO_OWNER_DOCUMENT_ELEMENT";
  public static final String ER_CANT_OUTPUT_TEXT_BEFORE_DOC = "ER_CANT_OUTPUT_TEXT_BEFORE_DOC";
  public static final String ER_CANT_HAVE_MORE_THAN_ONE_ROOT = "ER_CANT_HAVE_MORE_THAN_ONE_ROOT";
  public static final String ER_ARG_LOCALNAME_NULL = "ER_ARG_LOCALNAME_NULL";
  public static final String ER_ARG_LOCALNAME_INVALID = "ER_ARG_LOCALNAME_INVALID";
  public static final String ER_ARG_PREFIX_INVALID = "ER_ARG_PREFIX_INVALID";

  // Message keys used by the serializer
  public static final String ER_RESOURCE_COULD_NOT_FIND = "ER_RESOURCE_COULD_NOT_FIND";
  public static final String ER_RESOURCE_COULD_NOT_LOAD = "ER_RESOURCE_COULD_NOT_LOAD";
  public static final String ER_BUFFER_SIZE_LESSTHAN_ZERO = "ER_BUFFER_SIZE_LESSTHAN_ZERO";
  public static final String ER_INVALID_UTF16_SURROGATE = "ER_INVALID_UTF16_SURROGATE";
  public static final String ER_OIERROR = "ER_OIERROR";
  public static final String ER_NAMESPACE_PREFIX = "ER_NAMESPACE_PREFIX";
  public static final String ER_STRAY_ATTRIBUTE = "ER_STRAY_ATTIRBUTE";
  public static final String ER_STRAY_NAMESPACE = "ER_STRAY_NAMESPACE";
  public static final String ER_COULD_NOT_LOAD_RESOURCE = "ER_COULD_NOT_LOAD_RESOURCE";
  public static final String ER_COULD_NOT_LOAD_METHOD_PROPERTY = "ER_COULD_NOT_LOAD_METHOD_PROPERTY";
  public static final String ER_SERIALIZER_NOT_CONTENTHANDLER = "ER_SERIALIZER_NOT_CONTENTHANDLER";
  public static final String ER_ILLEGAL_ATTRIBUTE_POSITION = "ER_ILLEGAL_ATTRIBUTE_POSITION";

  /*
   * Now fill in the message text.
   * Then fill in the message text for that message code in the
   * array. Use the new error code as the index into the array.
   */

  // Error messages...

  /**
   * Get the lookup table for error messages
   *
   * @return The association list.
   */
  public Object[][] getContents()
  {
    return new Object[][] {

  /** Error message ID that has a null message, but takes in a single object.    */
    {"ER0000" , "{0}" },

    { ER_FUNCTION_NOT_SUPPORTED,
      "Fonction non prise en charge !"},

    { ER_CANNOT_OVERWRITE_CAUSE,
      "Impossible de remplacer la cause"},

    { ER_NO_DEFAULT_IMPL,
      "Impossible de trouver une impl\u00e9mentation par d\u00e9faut "},

    { ER_CHUNKEDINTARRAY_NOT_SUPPORTED,
      "ChunkedIntArray({0}) n''est pas pris en charge"},

    { ER_OFFSET_BIGGER_THAN_SLOT,
      "D\u00e9calage plus important que l'emplacement"},

    { ER_COROUTINE_NOT_AVAIL,
      "Coroutine non disponible, id={0}"},

    { ER_COROUTINE_CO_EXIT,
      "CoroutineManager a re\u00e7u une demande de co_exit()"},

    { ER_COJOINROUTINESET_FAILED,
      "Echec de co_joinCoroutineSet()"},

    { ER_COROUTINE_PARAM,
      "Erreur de param\u00e8tre de Coroutine ({0})"},

    { ER_PARSER_DOTERMINATE_ANSWERS,
      "\nRESULTAT INATTENDU : L''analyseur doTerminate r\u00e9pond {0}"},

    { ER_NO_PARSE_CALL_WHILE_PARSING,
      "parse ne peut \u00eatre appel\u00e9 lors de l'analyse"},

    { ER_TYPED_ITERATOR_AXIS_NOT_IMPLEMENTED,
      "Erreur : it\u00e9rateur typ\u00e9 de l''axe {0} non impl\u00e9ment\u00e9"},

    { ER_ITERATOR_AXIS_NOT_IMPLEMENTED,
      "Erreur : it\u00e9rateur de l''axe {0} non impl\u00e9ment\u00e9 "},

    { ER_ITERATOR_CLONE_NOT_SUPPORTED,
      "Clone de l'it\u00e9rateur non pris en charge"},

    { ER_UNKNOWN_AXIS_TYPE,
      "Type transversal d''axe inconnu : {0}"},

    { ER_AXIS_NOT_SUPPORTED,
      "Traverseur d''axe non pris en charge : {0}"},

    { ER_NO_DTMIDS_AVAIL,
      "Aucun autre ID de DTM disponible"},

    { ER_NOT_SUPPORTED,
      "Non pris en charge : {0}"},

    { ER_NODE_NON_NULL,
      "Le noeud ne doit pas \u00eatre vide pour getDTMHandleFromNode"},

    { ER_COULD_NOT_RESOLVE_NODE,
      "Impossible de convertir le noeud en pointeur"},

    { ER_STARTPARSE_WHILE_PARSING,
       "startParse ne peut \u00eatre appel\u00e9 pendant l'analyse"},

    { ER_STARTPARSE_NEEDS_SAXPARSER,
       "startParse requiert un SAXParser non vide"},

    { ER_COULD_NOT_INIT_PARSER,
       "impossible d'initialiser l'analyseur"},

    { ER_EXCEPTION_CREATING_POOL,
       "exception durant la cr\u00e9ation d'une instance du pool"},

    { ER_PATH_CONTAINS_INVALID_ESCAPE_SEQUENCE,
       "Le chemin d'acc\u00e8s contient une s\u00e9quence d'\u00e9chappement non valide"},

    { ER_SCHEME_REQUIRED,
       "Processus requis !"},

    { ER_NO_SCHEME_IN_URI,
       "Processus introuvable dans l''URI : {0}"},

    { ER_NO_SCHEME_INURI,
       "Processus introuvable dans l'URI"},

    { ER_PATH_INVALID_CHAR,
       "Le chemin contient un caract\u00e8re non valide : {0}"},

    { ER_SCHEME_FROM_NULL_STRING,
       "Impossible de d\u00e9finir le processus \u00e0 partir de la cha\u00eene vide"},

    { ER_SCHEME_NOT_CONFORMANT,
       "Le processus n'est pas conforme."},

    { ER_HOST_ADDRESS_NOT_WELLFORMED,
       "L'h\u00f4te n'est pas une adresse bien form\u00e9e"},

    { ER_PORT_WHEN_HOST_NULL,
       "Le port ne peut \u00eatre d\u00e9fini quand l'h\u00f4te est vide"},

    { ER_INVALID_PORT,
       "Num\u00e9ro de port non valide"},

    { ER_FRAG_FOR_GENERIC_URI,
       "Le fragment ne peut \u00eatre d\u00e9fini que pour un URI g\u00e9n\u00e9rique"},

    { ER_FRAG_WHEN_PATH_NULL,
       "Le fragment ne peut \u00eatre d\u00e9fini quand le chemin d'acc\u00e8s est vide"},

    { ER_FRAG_INVALID_CHAR,
       "Le fragment contient un caract\u00e8re non valide"},

    { ER_PARSER_IN_USE,
      "L'analyseur est d\u00e9j\u00e0 utilis\u00e9"},

    { ER_CANNOT_CHANGE_WHILE_PARSING,
      "Impossible de modifier {0} {1} durant l''analyse"},

    { ER_SELF_CAUSATION_NOT_PERMITTED,
      "Auto-causalit\u00e9 interdite"},

    { ER_NO_USERINFO_IF_NO_HOST,
      "Userinfo ne peut \u00eatre sp\u00e9cifi\u00e9 si l'h\u00f4te ne l'est pas"},

    { ER_NO_PORT_IF_NO_HOST,
      "Le port peut ne pas \u00eatre sp\u00e9cifi\u00e9 si l'h\u00f4te n'est pas sp\u00e9cifi\u00e9"},

    { ER_NO_QUERY_STRING_IN_PATH,
      "La cha\u00eene de requ\u00eate ne doit pas figurer dans un chemin et une cha\u00eene de requ\u00eate"},

    { ER_NO_FRAGMENT_STRING_IN_PATH,
      "Le fragment ne doit pas \u00eatre indiqu\u00e9 \u00e0 la fois dans le chemin et dans le fragment"},

    { ER_CANNOT_INIT_URI_EMPTY_PARMS,
      "Impossible d'initialiser l'URI avec des param\u00e8tres vides"},

    { ER_METHOD_NOT_SUPPORTED,
      "Cette m\u00e9thode n'est pas encore prise en charge "},

    { ER_INCRSAXSRCFILTER_NOT_RESTARTABLE,
      "IncrementalSAXSource_Filter ne peut red\u00e9marrer"},

    { ER_XMLRDR_NOT_BEFORE_STARTPARSE,
      "XMLReader ne figure pas avant la demande startParse"},

    { ER_AXIS_TRAVERSER_NOT_SUPPORTED,
      "Traverseur d''axe non pris en charge : {0}"},

    { ER_ERRORHANDLER_CREATED_WITH_NULL_PRINTWRITER,
      "ListingErrorHandler cr\u00e9\u00e9 avec PrintWriter vide !"},

    { ER_SYSTEMID_UNKNOWN,
      "ID syst\u00e8me inconnu"},

    { ER_LOCATION_UNKNOWN,
      "Emplacement inconnu de l'erreur"},

    { ER_PREFIX_MUST_RESOLVE,
      "Le pr\u00e9fixe doit se convertir en espace de noms : {0}"},

    { ER_CREATEDOCUMENT_NOT_SUPPORTED,
      "createDocument() non pris en charge dans XPathContext !"},

    { ER_CHILD_HAS_NO_OWNER_DOCUMENT,
      "L'enfant de l'attribut ne poss\u00e8de pas de document propri\u00e9taire !"},

    { ER_CHILD_HAS_NO_OWNER_DOCUMENT_ELEMENT,
      "Le contexte ne poss\u00e8de pas d'\u00e9l\u00e9ment de document propri\u00e9taire !"},

    { ER_CANT_OUTPUT_TEXT_BEFORE_DOC,
      "Avertissement : impossible d'afficher du texte avant l'\u00e9l\u00e9ment de document !  Traitement ignor\u00e9..."},

    { ER_CANT_HAVE_MORE_THAN_ONE_ROOT,
      "Un DOM ne peut poss\u00e9der plusieurs racines !"},

    { ER_ARG_LOCALNAME_NULL,
       "L'argument ''localName'' est vide"},

    // Note to translators:  A QNAME has the syntactic form [NCName:]NCName
    // The localname is the portion after the optional colon; the message indicates
    // that there is a problem with that part of the QNAME.
    { ER_ARG_LOCALNAME_INVALID,
       "Dans QNAME, le nom local doit \u00eatre un nom NCName valide"},

    // Note to translators:  A QNAME has the syntactic form [NCName:]NCName
    // The prefix is the portion before the optional colon; the message indicates
    // that there is a problem with that part of the QNAME.
    { ER_ARG_PREFIX_INVALID,
       "Dans QNAME, le pr\u00e9fixe doit \u00eatre un nom NCName valide"},

    { "BAD_CODE", "Le param\u00e8tre de createMessage se trouve hors limites"},
    { "FORMAT_FAILED", "Exception soulev\u00e9e lors de l'appel de messageFormat"},
    { "line", "Ligne #"},
    { "column","Colonne #"},

    {ER_SERIALIZER_NOT_CONTENTHANDLER,
      "La classe de la m\u00e9thode de s\u00e9rialisation ''{0}'' n''impl\u00e9mente pas org.xml.sax.ContentHandler."},

    {ER_RESOURCE_COULD_NOT_FIND,
      "La ressource [ {0} ] est introuvable.\n {1}" },

    {ER_RESOURCE_COULD_NOT_LOAD,
      "La ressource [ {0} ] n''a pas pu charger : {1} \n {2} \t {3}" },

    {ER_BUFFER_SIZE_LESSTHAN_ZERO,
      "Taille du tampon <=0" },

    {ER_INVALID_UTF16_SURROGATE,
      "Substitut UTF-16 non valide d\u00e9tect\u00e9 : {0} ?" },

    {ER_OIERROR,
      "Erreur d''E-S" },

    {ER_ILLEGAL_ATTRIBUTE_POSITION,
      "Ajout impossible de l''attribut {0} apr\u00e8s des noeuds enfants ou avant la production d''un \u00e9l\u00e9ment.  L''attribut est ignor\u00e9."},

      /*
       * Note to translators:  The stylesheet contained a reference to a
       * namespace prefix that was undefined.  The value of the substitution
       * text is the name of the prefix.
       */
    {ER_NAMESPACE_PREFIX,
      "L''espace de noms du pr\u00e9fixe ''{0}'' n''a pas \u00e9t\u00e9 d\u00e9clar\u00e9." },
      /*
       * Note to translators:  This message is reported if the stylesheet
       * being processed attempted to construct an XML document with an
       * attribute in a place other than on an element.  The substitution text
       * specifies the name of the attribute.
       */
    {ER_STRAY_ATTRIBUTE,
      "L''attribut ''{0}'' est \u00e0 l''ext\u00e9rieur de l''\u00e9l\u00e9ment." },

      /*
       * Note to translators:  As with the preceding message, a namespace
       * declaration has the form of an attribute and is only permitted to
       * appear on an element.  The substitution text {0} is the namespace
       * prefix and {1} is the URI that was being used in the erroneous
       * namespace declaration.
       */
    {ER_STRAY_NAMESPACE,
      "La d\u00e9claration d''espace de noms ''{0}''=''{1}'' est \u00e0 l''ext\u00e9rieur de l''\u00e9l\u00e9ment." },

    {ER_COULD_NOT_LOAD_RESOURCE,
      "Impossible de charger ''{0}'' (v\u00e9rifier CLASSPATH), les valeurs par d\u00e9faut sont donc employ\u00e9es "},

    {ER_COULD_NOT_LOAD_METHOD_PROPERTY,
      "Impossible de charger le fichier de propri\u00e9t\u00e9s ''{0}'' pour la m\u00e9thode de sortie ''{1}'' (v\u00e9rifier CLASSPATH)" }


  };
  }

  /**
   *   Return a named ResourceBundle for a particular locale.  This method mimics the behavior
   *   of ResourceBundle.getBundle().
   *
   *   @param className the name of the class that implements the resource bundle.
   *   @return the ResourceBundle
   *   @throws MissingResourceException
   */
  public static final XMLErrorResources loadResourceBundle(String className)
          throws MissingResourceException
  {

    Locale locale = Locale.getDefault();
    String suffix = getResourceSuffix(locale);

    try
    {

      // first try with the given locale
      return (XMLErrorResources) ResourceBundle.getBundle(className
              + suffix, locale);
    }
    catch (MissingResourceException e)
    {
      try  // try to fall back to en_US if we can't load
      {

        // Since we can't find the localized property file,
        // fall back to en_US.
        return (XMLErrorResources) ResourceBundle.getBundle(className,
                new Locale("en", "US"));
      }
      catch (MissingResourceException e2)
      {

        // Now we are really in trouble.
        // very bad, definitely very bad...not going to get very far
        throw new MissingResourceException(
          "Could not load any resource bundles.", className, "");
      }
    }
  }

  /**
   * Return the resource file suffic for the indicated locale
   * For most locales, this will be based the language code.  However
   * for Chinese, we do distinguish between Taiwan and PRC
   *
   * @param locale the locale
   * @return an String suffix which canbe appended to a resource name
   */
  private static final String getResourceSuffix(Locale locale)
  {

    String suffix = "_" + locale.getLanguage();
    String country = locale.getCountry();

    if (country.equals("TW"))
      suffix += "_" + country;

    return suffix;
  }

}
