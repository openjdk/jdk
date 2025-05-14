/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sun.org.apache.xml.internal.res;


import java.util.ListResourceBundle;

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
public class XMLErrorResources_tr extends ListResourceBundle
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
  private static final Object[][] _contents = new Object[][] {

  /** Error message ID that has a null message, but takes in a single object.    */
    {"ER0000" , "{0}" },

    { ER_FUNCTION_NOT_SUPPORTED,
      "İşlev desteklenmiyor!"},

    { ER_CANNOT_OVERWRITE_CAUSE,
      "Nedenin üzerine yazılamaz"},

    { ER_NO_DEFAULT_IMPL,
      "Varsayılan uygulama bulunamadı"},

    { ER_CHUNKEDINTARRAY_NOT_SUPPORTED,
      "ChunkedIntArray({0}) şu an desteklenmiyor"},

    { ER_OFFSET_BIGGER_THAN_SLOT,
      "Göreli konum yuvadan büyük"},

    { ER_COROUTINE_NOT_AVAIL,
      "Coroutine kullanılamıyor, id={0}"},

    { ER_COROUTINE_CO_EXIT,
      "CoroutineManager co_exit() isteği aldı"},

    { ER_COJOINROUTINESET_FAILED,
      "co_joinCoroutineSet() başarısız oldu"},

    { ER_COROUTINE_PARAM,
      "Coroutine değiştirgesi hatası ({0})"},

    { ER_PARSER_DOTERMINATE_ANSWERS,
      "\nBEKLENMEYEN: Parser doTerminate yanıtı {0}"},

    { ER_NO_PARSE_CALL_WHILE_PARSING,
      "Ayrıştırma sırasında parse çağrılamaz"},

    { ER_TYPED_ITERATOR_AXIS_NOT_IMPLEMENTED,
      "Hata: {0} ekseni için tip atanmış yineleyici gerçekleştirilmedi"},

    { ER_ITERATOR_AXIS_NOT_IMPLEMENTED,
      "Hata: {0} ekseni için yineleyici gerçekleştirilmedi"},

    { ER_ITERATOR_CLONE_NOT_SUPPORTED,
      "Yineleyici eşkopyası desteklenmiyor"},

    { ER_UNKNOWN_AXIS_TYPE,
      "Bilinmeyen eksen dolaşma tipi: {0}"},

    { ER_AXIS_NOT_SUPPORTED,
      "Eksen dolaşıcı desteklenmiyor: {0}"},

    { ER_NO_DTMIDS_AVAIL,
      "Kullanılabilecek başka DTM tanıtıcısı yok"},

    { ER_NOT_SUPPORTED,
      "Desteklenmiyor: {0}"},

    { ER_NODE_NON_NULL,
      "getDTMHandleFromNode için düğüm boş değerli olmamalıdır"},

    { ER_COULD_NOT_RESOLVE_NODE,
      "Düğüm tanıtıcı değere çözülemedi"},

    { ER_STARTPARSE_WHILE_PARSING,
       "Ayrıştırma sırasında startParse çağrılamaz"},

    { ER_STARTPARSE_NEEDS_SAXPARSER,
       "startParse için boş değerli olmayan SAXParser gerekiyor"},

    { ER_COULD_NOT_INIT_PARSER,
       "Ayrıştırıcı bununla kullanıma hazırlanamadı"},

    { ER_EXCEPTION_CREATING_POOL,
       "Havuz için yeni örnek yaratılırken kural dışı durum"},

    { ER_PATH_CONTAINS_INVALID_ESCAPE_SEQUENCE,
       "Yol geçersiz kaçış dizisi içeriyor"},

    { ER_SCHEME_REQUIRED,
       "Şema gerekli!"},

    { ER_NO_SCHEME_IN_URI,
       "URI içinde şema bulunamadı: {0}"},

    { ER_NO_SCHEME_INURI,
       "URI içinde şema bulunamadı"},

    { ER_PATH_INVALID_CHAR,
       "Yol geçersiz karakter içeriyor: {0}"},

    { ER_SCHEME_FROM_NULL_STRING,
       "Boş değerli dizgiden şema tanımlanamaz"},

    { ER_SCHEME_NOT_CONFORMANT,
       "Şema uyumlu değil."},

    { ER_HOST_ADDRESS_NOT_WELLFORMED,
       "Anasistem doğru biçimli bir adres değil"},

    { ER_PORT_WHEN_HOST_NULL,
       "Anasistem boş değerliyken kapı tanımlanamaz"},

    { ER_INVALID_PORT,
       "Kapı numarası geçersiz"},

    { ER_FRAG_FOR_GENERIC_URI,
       "Parça yalnızca soysal URI için tanımlanabilir"},

    { ER_FRAG_WHEN_PATH_NULL,
       "Yol boş değerliyken parça tanımlanamaz"},

    { ER_FRAG_INVALID_CHAR,
       "Parça geçersiz karakter içeriyor"},

    { ER_PARSER_IN_USE,
      "Ayrıştırıcı kullanımda"},

    { ER_CANNOT_CHANGE_WHILE_PARSING,
      "Ayrıştırma sırasında {0} {1} değiştirilemez"},

    { ER_SELF_CAUSATION_NOT_PERMITTED,
      "Öznedenselliğe izin verilmez"},

    { ER_NO_USERINFO_IF_NO_HOST,
      "Anasistem belirtilmediyse kullanıcı bilgisi belirtilemez"},

    { ER_NO_PORT_IF_NO_HOST,
      "Anasistem belirtilmediyse kapı belirtilemez"},

    { ER_NO_QUERY_STRING_IN_PATH,
      "Yol ve sorgu dizgisinde sorgu dizgisi belirtilemez"},

    { ER_NO_FRAGMENT_STRING_IN_PATH,
      "Parça hem yolda, hem de parçada belirtilemez"},

    { ER_CANNOT_INIT_URI_EMPTY_PARMS,
      "Boş değiştirgelerle URI kullanıma hazırlanamaz"},

    { ER_METHOD_NOT_SUPPORTED,
      "Yöntem henüz desteklenmiyor"},

    { ER_INCRSAXSRCFILTER_NOT_RESTARTABLE,
      "IncrementalSAXSource_Filter şu an yeniden başlatılabilir durumda değil"},

    { ER_XMLRDR_NOT_BEFORE_STARTPARSE,
      "XMLReader, startParse isteğinden önce olmaz"},

    { ER_AXIS_TRAVERSER_NOT_SUPPORTED,
      "Eksen dolaşıcı desteklenmiyor: {0}"},

    { ER_ERRORHANDLER_CREATED_WITH_NULL_PRINTWRITER,
      "ListingErrorHandler boş değerli PrintWriter ile yaratıldı!"},

    { ER_SYSTEMID_UNKNOWN,
      "SystemId bilinmiyor"},

    { ER_LOCATION_UNKNOWN,
      "Hata yeri bilinmiyor"},

    { ER_PREFIX_MUST_RESOLVE,
      "Önek bir ad alanına çözülmelidir: {0}"},

    { ER_CREATEDOCUMENT_NOT_SUPPORTED,
      "XPathContext içinde createDocument() desteklenmiyor!"},

    { ER_CHILD_HAS_NO_OWNER_DOCUMENT,
      "Özniteliğin alt öğesinin iye belgesi yok!"},

    { ER_CHILD_HAS_NO_OWNER_DOCUMENT_ELEMENT,
      "Özniteliğin alt öğesinin iye belge öğesi yok!"},

    { ER_CANT_OUTPUT_TEXT_BEFORE_DOC,
      "Uyarı: Belge öğesinden önce metin çıkışı olamaz!  Yoksayılıyor..."},

    { ER_CANT_HAVE_MORE_THAN_ONE_ROOT,
      "DOM üzerinde birden fazla kök olamaz!"},

    { ER_ARG_LOCALNAME_NULL,
       "'localName' bağımsız değiştirgesi boş değerli"},

    // Note to translators:  A QNAME has the syntactic form [NCName:]NCName
    // The localname is the portion after the optional colon; the message indicates
    // that there is a problem with that part of the QNAME.
    { ER_ARG_LOCALNAME_INVALID,
       "QNAME içindeki yerel ad (localname) geçerli bir NCName olmalıdır"},

    // Note to translators:  A QNAME has the syntactic form [NCName:]NCName
    // The prefix is the portion before the optional colon; the message indicates
    // that there is a problem with that part of the QNAME.
    { ER_ARG_PREFIX_INVALID,
       "QNAME içindeki önek geçerli bir NCName olmalıdır"},

    { "BAD_CODE", "createMessage için kullanılan değiştirge sınırların dışında"},
    { "FORMAT_FAILED", "messageFormat çağrısı sırasında kural dışı durum yayınlandı"},
    { "line", "Satır #"},
    { "column","Kolon #"},

    {ER_SERIALIZER_NOT_CONTENTHANDLER,
      "Diziselleştirici sınıfı ''{0}'' org.xml.sax.ContentHandler işlevini uygulamıyor."},

    {ER_RESOURCE_COULD_NOT_FIND,
      "Kaynak [ {0} ] bulunamadı.\n {1}" },

    {ER_RESOURCE_COULD_NOT_LOAD,
      "Kaynak [ {0} ] yükleyemedi: {1} \n {2} \t {3}" },

    {ER_BUFFER_SIZE_LESSTHAN_ZERO,
      "Arabellek büyüklüğü <=0" },

    {ER_INVALID_UTF16_SURROGATE,
      "UTF-16 yerine kullanılan değer geçersiz: {0} ?" },

    {ER_OIERROR,
      "GÇ hatası" },

    {ER_ILLEGAL_ATTRIBUTE_POSITION,
      "Alt düğümlerden sonra ya da bir öğe üretilmeden önce {0} özniteliği eklenemez. Öznitelik yoksayılacak."},

      /*
       * Note to translators:  The stylesheet contained a reference to a
       * namespace prefix that was undefined.  The value of the substitution
       * text is the name of the prefix.
       */
    {ER_NAMESPACE_PREFIX,
      "''{0}'' önekine ilişkin ad alanı bildirilmedi." },
      /*
       * Note to translators:  This message is reported if the stylesheet
       * being processed attempted to construct an XML document with an
       * attribute in a place other than on an element.  The substitution text
       * specifies the name of the attribute.
       */
    {ER_STRAY_ATTRIBUTE,
      "''{0}'' özniteliği öğenin dışında." },

      /*
       * Note to translators:  As with the preceding message, a namespace
       * declaration has the form of an attribute and is only permitted to
       * appear on an element.  The substitution text {0} is the namespace
       * prefix and {1} is the URI that was being used in the erroneous
       * namespace declaration.
       */
    {ER_STRAY_NAMESPACE,
      "''{0}''=''{1}'' ad alanı bildirimi öğenin dışında." },

    {ER_COULD_NOT_LOAD_RESOURCE,
      "''{0}'' yüklenemedi (CLASSPATH değişkeninizi inceleyin), yalnızca varsayılanlar kullanılıyor"},

    {ER_COULD_NOT_LOAD_METHOD_PROPERTY,
      "''{1}'' çıkış yöntemi için ''{0}'' özellik dosyası yüklenemedi (CLASSPATH değişkenini inceleyin)" }


  };

  /**
   * Get the lookup table for error messages
   *
   * @return The association list.
   */
  public Object[][] getContents()
  {
    return _contents;
  }

}
