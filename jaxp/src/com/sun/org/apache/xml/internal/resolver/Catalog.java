/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
// Catalog.java - Represents OASIS Open Catalog files.

/*
 * Copyright 2001-2004 The Apache Software Foundation or its licensors,
 * as applicable.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sun.org.apache.xml.internal.resolver;

import com.sun.org.apache.xerces.internal.jaxp.SAXParserFactoryImpl;
import com.sun.org.apache.xerces.internal.utils.SecuritySupport;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.io.DataInputStream;

import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;

import java.net.URL;
import java.net.MalformedURLException;

import javax.xml.parsers.SAXParserFactory;

import com.sun.org.apache.xml.internal.resolver.CatalogManager;
import com.sun.org.apache.xml.internal.resolver.helpers.PublicId;
import com.sun.org.apache.xml.internal.resolver.readers.CatalogReader;
import com.sun.org.apache.xml.internal.resolver.readers.SAXCatalogReader;
import com.sun.org.apache.xml.internal.resolver.readers.TR9401CatalogReader;
import com.sun.org.apache.xml.internal.resolver.readers.OASISXMLCatalogReader;
import com.sun.org.apache.xml.internal.resolver.helpers.FileURL;

/**
 * Represents OASIS Open Catalog files.
 *
 * <p>This class implements the semantics of OASIS Open Catalog files
 * (defined by
 * <a href="http://www.oasis-open.org/html/a401.htm">OASIS Technical
 * Resolution 9401:1997 (Amendment 2 to TR 9401)</a>).</p>
 *
 * <p>The primary purpose of the Catalog is to associate resources in the
 * document with local system identifiers. Some entities
 * (document types, XML entities, and notations) have names and all of them
 * can have either public or system identifiers or both. (In XML, only a
 * notation can have a public identifier without a system identifier, but
 * the methods implemented in this class obey the Catalog semantics
 * from the SGML
 * days when system identifiers were optional.)</p>
 *
 * <p>The system identifiers returned by the resolution methods in this
 * class are valid, i.e. usable by, and in fact constructed by, the
 * <tt>java.net.URL</tt> class. Unfortunately, this class seems to behave in
 * somewhat non-standard ways and the system identifiers returned may
 * not be directly usable in a browser or filesystem context.
 *
 * <p>This class recognizes all of the Catalog entries defined in
 * TR9401:1997:</p>
 *
 * <ul>
 * <li><b>BASE</b>
 * changes the base URI for resolving relative system identifiers. The
 * initial base URI is the URI of the location of the catalog (which is,
 * in turn, relative to the location of the current working directory
 * at startup, as returned by the <tt>user.dir</tt> system property).</li>
 * <li><b>CATALOG</b>
 * processes other catalog files. An included catalog occurs logically
 * at the end of the including catalog.</li>
 * <li><b>DELEGATE_PUBLIC</b>
 * specifies alternate catalogs for some public identifiers. The delegated
 * catalogs are not loaded until they are needed, but they are cached
 * once loaded.</li>
 * <li><b>DELEGATE_SYSTEM</b>
 * specifies alternate catalogs for some system identifiers. The delegated
 * catalogs are not loaded until they are needed, but they are cached
 * once loaded.</li>
 * <li><b>DELEGATE_URI</b>
 * specifies alternate catalogs for some URIs. The delegated
 * catalogs are not loaded until they are needed, but they are cached
 * once loaded.</li>
 * <li><b>REWRITE_SYSTEM</b>
 * specifies alternate prefix for a system identifier.</li>
 * <li><b>REWRITE_URI</b>
 * specifies alternate prefix for a URI.</li>
 * <li><b>SYSTEM_SUFFIX</b>
 * maps any system identifier that ends with a particular suffix to another
 * system identifier.</li>
 * <li><b>URI_SUFFIX</b>
 * maps any URI that ends with a particular suffix to another URI.</li>
 * <li><b>DOCTYPE</b>
 * associates the names of root elements with URIs. (In other words, an XML
 * processor might infer the doctype of an XML document that does not include
 * a doctype declaration by looking for the DOCTYPE entry in the
 * catalog which matches the name of the root element of the document.)</li>
 * <li><b>DOCUMENT</b>
 * provides a default document.</li>
 * <li><b>DTDDECL</b>
 * recognized and silently ignored. Not relevant for XML.</li>
 * <li><b>ENTITY</b>
 * associates entity names with URIs.</li>
 * <li><b>LINKTYPE</b>
 * recognized and silently ignored. Not relevant for XML.</li>
 * <li><b>NOTATION</b>
 * associates notation names with URIs.</li>
 * <li><b>OVERRIDE</b>
 * changes the override behavior. Initial behavior is set by the
 * system property <tt>xml.catalog.override</tt>. The default initial
 * behavior is 'YES', that is, entries in the catalog override
 * system identifiers specified in the document.</li>
 * <li><b>PUBLIC</b>
 * maps a public identifier to a system identifier.</li>
 * <li><b>SGMLDECL</b>
 * recognized and silently ignored. Not relevant for XML.</li>
 * <li><b>SYSTEM</b>
 * maps a system identifier to another system identifier.</li>
 * <li><b>URI</b>
 * maps a URI to another URI.</li>
 * </ul>
 *
 * <p>Note that BASE entries are treated as described by RFC2396. In
 * particular, this has the counter-intuitive property that after a BASE
 * entry identifing "http://example.com/a/b/c" as the base URI,
 * the relative URI "foo" is resolved to the absolute URI
 * "http://example.com/a/b/foo". You must provide the trailing slash if
 * you do not want the final component of the path to be discarded as a
 * filename would in a URI for a resource: "http://example.com/a/b/c/".
 * </p>
 *
 * <p>Note that subordinate catalogs (all catalogs except the first,
 * including CATALOG and DELEGATE* catalogs) are only loaded if and when
 * they are required.</p>
 *
 * <p>This class relies on classes which implement the CatalogReader
 * interface to actually load catalog files. This allows the catalog
 * semantics to be implemented for TR9401 text-based catalogs, XML
 * catalogs, or any number of other storage formats.</p>
 *
 * <p>Additional catalogs may also be loaded with the
 * {@link #parseCatalog} method.</p>
 * </dd>
 * </dl>
 *
 * <p><b>Change Log:</b></p>
 * <dl>
 * <dt>2.0</dt>
 * <dd><p>Rewrite to use CatalogReaders.</p></dd>
 * <dt>1.1</dt>
 * <dd><p>Allow quoted components in <tt>xml.catalog.files</tt>
 * so that URLs containing colons can be used on Unix.
 * The string passed to <tt>xml.catalog.files</tt> can now have the form:</p>
 * <pre>
 * unquoted-path-with-no-sep-chars:"double-quoted path with or without sep chars":'single-quoted path with or without sep chars'
 * </pre>
 * <p>(Where ":" is the separater character in this example.)</p>
 * <p>If an unquoted path contains an embedded double or single quote
 * character, no special processig is performed on that character. No
 * path can contain separater characters, double, and single quotes
 * simultaneously.</p>
 * <p>Fix bug in calculation of BASE entries: if
 * a catalog contains multiple BASE entries, each is relative to the preceding
 * base, not the default base URI of the catalog.</p>
 * </dd>
 * <dt>1.0.1</dt>
 * <dd><p>Fixed a bug in the calculation of the list of subordinate catalogs.
 * This bug caused an infinite loop where parsing would alternately process
 * two catalogs indefinitely.</p>
 * </dd>
 * </dl>
 *
 * @see CatalogReader
 * @see CatalogEntry
 *
 * @author Norman Walsh
 * <a href="mailto:Norman.Walsh@Sun.COM">Norman.Walsh@Sun.COM</a>
 *
 * @version 1.0
 *
 * <p>Derived from public domain code originally published by Arbortext,
 * Inc.</p>
 */
public class Catalog {
  /** The BASE Catalog Entry type. */
  public static final int BASE     = CatalogEntry.addEntryType("BASE", 1);

  /** The CATALOG Catalog Entry type. */
  public static final int CATALOG  = CatalogEntry.addEntryType("CATALOG", 1);

  /** The DOCUMENT Catalog Entry type. */
  public static final int DOCUMENT = CatalogEntry.addEntryType("DOCUMENT", 1);

  /** The OVERRIDE Catalog Entry type. */
  public static final int OVERRIDE = CatalogEntry.addEntryType("OVERRIDE", 1);

  /** The SGMLDECL Catalog Entry type. */
  public static final int SGMLDECL = CatalogEntry.addEntryType("SGMLDECL", 1);

  /** The DELEGATE_PUBLIC Catalog Entry type. */
  public static final int DELEGATE_PUBLIC = CatalogEntry.addEntryType("DELEGATE_PUBLIC", 2);

  /** The DELEGATE_SYSTEM Catalog Entry type. */
  public static final int DELEGATE_SYSTEM = CatalogEntry.addEntryType("DELEGATE_SYSTEM", 2);

  /** The DELEGATE_URI Catalog Entry type. */
  public static final int DELEGATE_URI = CatalogEntry.addEntryType("DELEGATE_URI", 2);

  /** The DOCTYPE Catalog Entry type. */
  public static final int DOCTYPE  = CatalogEntry.addEntryType("DOCTYPE", 2);

  /** The DTDDECL Catalog Entry type. */
  public static final int DTDDECL  = CatalogEntry.addEntryType("DTDDECL", 2);

  /** The ENTITY Catalog Entry type. */
  public static final int ENTITY   = CatalogEntry.addEntryType("ENTITY", 2);

  /** The LINKTYPE Catalog Entry type. */
  public static final int LINKTYPE = CatalogEntry.addEntryType("LINKTYPE", 2);

  /** The NOTATION Catalog Entry type. */
  public static final int NOTATION = CatalogEntry.addEntryType("NOTATION", 2);

  /** The PUBLIC Catalog Entry type. */
  public static final int PUBLIC   = CatalogEntry.addEntryType("PUBLIC", 2);

  /** The SYSTEM Catalog Entry type. */
  public static final int SYSTEM   = CatalogEntry.addEntryType("SYSTEM", 2);

  /** The URI Catalog Entry type. */
  public static final int URI      = CatalogEntry.addEntryType("URI", 2);

  /** The REWRITE_SYSTEM Catalog Entry type. */
  public static final int REWRITE_SYSTEM = CatalogEntry.addEntryType("REWRITE_SYSTEM", 2);

  /** The REWRITE_URI Catalog Entry type. */
  public static final int REWRITE_URI = CatalogEntry.addEntryType("REWRITE_URI", 2);
  /** The SYSTEM_SUFFIX Catalog Entry type. */
  public static final int SYSTEM_SUFFIX = CatalogEntry.addEntryType("SYSTEM_SUFFIX", 2);
  /** The URI_SUFFIX Catalog Entry type. */
  public static final int URI_SUFFIX = CatalogEntry.addEntryType("URI_SUFFIX", 2);

  /**
   * The base URI for relative system identifiers in the catalog.
   * This may be changed by BASE entries in the catalog.
   */
  protected URL base;

  /** The base URI of the Catalog file currently being parsed. */
  protected URL catalogCwd;

  /** The catalog entries currently known to the system. */
  protected Vector catalogEntries = new Vector();

  /** The default initial override setting. */
  protected boolean default_override = true;

  /** The catalog manager in use for this instance. */
  protected CatalogManager catalogManager = CatalogManager.getStaticManager();

  /**
   * A vector of catalog files to be loaded.
   *
   * <p>This list is initially established by
   * <code>loadSystemCatalogs</code> when
   * it parses the system catalog list, but CATALOG entries may
   * contribute to it during the course of parsing.</p>
   *
   * @see #loadSystemCatalogs
   * @see #localCatalogFiles
   */
  protected Vector catalogFiles = new Vector();

  /**
   * A vector of catalog files constructed during processing of
   * CATALOG entries in the current catalog.
   *
   * <p>This two-level system is actually necessary to correctly implement
   * the semantics of the CATALOG entry. If one catalog file includes
   * another with a CATALOG entry, the included catalog logically
   * occurs <i>at the end</i> of the including catalog, and after any
   * preceding CATALOG entries. In other words, the CATALOG entry
   * cannot insert anything into the middle of a catalog file.</p>
   *
   * <p>When processing reaches the end of each catalog files, any
   * elements on this vector are added to the front of the
   * <code>catalogFiles</code> vector.</p>
   *
   * @see #catalogFiles
   */
  protected Vector localCatalogFiles = new Vector();

  /**
   * A vector of Catalogs.
   *
   * <p>The semantics of Catalog resolution are such that each
   * catalog is effectively a list of Catalogs (in other words,
   * a recursive list of Catalog instances).</p>
   *
   * <p>Catalogs that are processed as the result of CATALOG or
   * DELEGATE* entries are subordinate to the catalog that contained
   * them, but they may in turn have subordinate catalogs.</p>
   *
   * <p>Catalogs are only loaded when they are needed, so this vector
   * initially contains a list of Catalog filenames (URLs). If, during
   * processing, one of these catalogs has to be loaded, the resulting
   * Catalog object is placed in the vector, effectively caching it
   * for the next query.</p>
   */
  protected Vector catalogs = new Vector();

  /**
   * A vector of DELEGATE* Catalog entries constructed during
   * processing of the Catalog.
   *
   * <p>This two-level system has two purposes; first, it allows
   * us to sort the DELEGATE* entries by the length of the partial
   * public identifier so that a linear search encounters them in
   * the correct order and second, it puts them all at the end of
   * the Catalog.</p>
   *
   * <p>When processing reaches the end of each catalog file, any
   * elements on this vector are added to the end of the
   * <code>catalogEntries</code> vector. This assures that matching
   * PUBLIC keywords are encountered before DELEGATE* entries.</p>
   */
  protected Vector localDelegate = new Vector();

  /**
   * A hash of CatalogReaders.
   *
   * <p>This hash maps MIME types to elements in the readerArr
   * vector. This allows the Catalog to quickly locate the reader
   * for a particular MIME type.</p>
   */
  protected Hashtable readerMap = new Hashtable();

  /**
   * A vector of CatalogReaders.
   *
   * <p>This vector contains all of the readers in the order that they
   * were added. In the event that a catalog is read from a file, where
   * the MIME type is unknown, each reader is attempted in turn until
   * one succeeds.</p>
   */
  protected Vector readerArr = new Vector();

  /**
   * Constructs an empty Catalog.
   *
   * <p>The constructor interrogates the relevant system properties
   * using the default (static) CatalogManager
   * and initializes the catalog data structures.</p>
   */
  public Catalog() {
    // nop;
  }

  /**
   * Constructs an empty Catalog with a specific CatalogManager.
   *
   * <p>The constructor interrogates the relevant system properties
   * using the specified Catalog Manager
   * and initializes the catalog data structures.</p>
   */
  public Catalog(CatalogManager manager) {
    catalogManager = manager;
  }

  /**
   * Return the CatalogManager used by this catalog.
   *
   */
  public CatalogManager getCatalogManager() {
    return catalogManager;
  }

  /**
   * Establish the CatalogManager used by this catalog.
   *
   */
  public void setCatalogManager(CatalogManager manager) {
    catalogManager = manager;
  }

  /**
   * Setup readers.
   */
  public void setupReaders() {
    SAXParserFactory spf = catalogManager.useServicesMechanism() ?
                    SAXParserFactory.newInstance() : new SAXParserFactoryImpl();
    spf.setNamespaceAware(true);
    spf.setValidating(false);

    SAXCatalogReader saxReader = new SAXCatalogReader(spf);

    saxReader.setCatalogParser(null, "XMLCatalog",
                               "com.sun.org.apache.xml.internal.resolver.readers.XCatalogReader");

    saxReader.setCatalogParser(OASISXMLCatalogReader.namespaceName,
                               "catalog",
                               "com.sun.org.apache.xml.internal.resolver.readers.OASISXMLCatalogReader");

    addReader("application/xml", saxReader);

    TR9401CatalogReader textReader = new TR9401CatalogReader();
    addReader("text/plain", textReader);
  }

  /**
   * Add a new CatalogReader to the Catalog.
   *
   * <p>This method allows you to add a new CatalogReader to the
   * catalog. The reader will be associated with the specified mimeType.
   * You can only have one reader per mimeType.</p>
   *
   * <p>In the absence of a mimeType (e.g., when reading a catalog
   * directly from a file on the local system), the readers are attempted
   * in the order that you add them to the Catalog.</p>
   *
   * <p>Note that subordinate catalogs (created by CATALOG or
   * DELEGATE* entries) get a copy of the set of readers present in
   * the primary catalog when they are created. Readers added subsequently
   * will not be available. For this reason, it is best to add all
   * of the readers before the first call to parse a catalog.</p>
   *
   * @param mimeType The MIME type associated with this reader.
   * @param reader The CatalogReader to use.
   */
  public void addReader(String mimeType, CatalogReader reader) {
    if (readerMap.containsKey(mimeType)) {
      Integer pos = (Integer) readerMap.get(mimeType);
      readerArr.set(pos.intValue(), reader);
    } else {
      readerArr.add(reader);
      Integer pos = new Integer(readerArr.size()-1);
      readerMap.put(mimeType, pos);
    }
  }

  /**
   * Copies the reader list from the current Catalog to a new Catalog.
   *
   * <p>This method is used internally when constructing a new catalog.
   * It copies the current reader associations over to the new catalog.
   * </p>
   *
   * @param newCatalog The new Catalog.
   */
  protected void copyReaders(Catalog newCatalog) {
    // Have to copy the readers in the right order...convert hash to arr
    Vector mapArr = new Vector(readerMap.size());

    // Pad the mapArr out to the right length
    for (int count = 0; count < readerMap.size(); count++) {
      mapArr.add(null);
    }

    Enumeration en = readerMap.keys();
    while (en.hasMoreElements()) {
      String mimeType = (String) en.nextElement();
      Integer pos = (Integer) readerMap.get(mimeType);
      mapArr.set(pos.intValue(), mimeType);
    }

    for (int count = 0; count < mapArr.size(); count++) {
      String mimeType = (String) mapArr.get(count);
      Integer pos = (Integer) readerMap.get(mimeType);
      newCatalog.addReader(mimeType,
                           (CatalogReader)
                           readerArr.get(pos.intValue()));
    }
  }

  /**
   * Create a new Catalog object.
   *
   * <p>This method constructs a new instance of the running Catalog
   * class (which might be a subtype of com.sun.org.apache.xml.internal.resolver.Catalog).
   * All new catalogs are managed by the same CatalogManager.
   * </p>
   *
   * <p>N.B. All Catalog subtypes should call newCatalog() to construct
   * a new Catalog. Do not simply use "new Subclass()" since that will
   * confuse future subclasses.</p>
   */
  protected Catalog newCatalog() {
    String catalogClass = this.getClass().getName();

    try {
      Catalog c = (Catalog) (Class.forName(catalogClass).newInstance());
      c.setCatalogManager(catalogManager);
      copyReaders(c);
      return c;
    } catch (ClassNotFoundException cnfe) {
      catalogManager.debug.message(1, "Class Not Found Exception: " + catalogClass);
    } catch (IllegalAccessException iae) {
      catalogManager.debug.message(1, "Illegal Access Exception: " + catalogClass);
    } catch (InstantiationException ie) {
      catalogManager.debug.message(1, "Instantiation Exception: " + catalogClass);
    } catch (ClassCastException cce) {
      catalogManager.debug.message(1, "Class Cast Exception: " + catalogClass);
    } catch (Exception e) {
      catalogManager.debug.message(1, "Other Exception: " + catalogClass);
    }

    Catalog c = new Catalog();
    c.setCatalogManager(catalogManager);
    copyReaders(c);
    return c;
  }

  /**
   * Returns the current base URI.
   */
  public String getCurrentBase() {
    return base.toString();
  }

  /**
   * Returns the default override setting associated with this
   * catalog.
   *
   * <p>All catalog files loaded by this catalog will have the
   * initial override setting specified by this default.</p>
   */
  public String getDefaultOverride() {
    if (default_override) {
      return "yes";
    } else {
      return "no";
    }
  }

  /**
   * Load the system catalog files.
   *
   * <p>The method adds all of the
   * catalogs specified in the <tt>xml.catalog.files</tt> property
   * to the Catalog list.</p>
   *
   * @throws MalformedURLException  One of the system catalogs is
   * identified with a filename that is not a valid URL.
   * @throws IOException One of the system catalogs cannot be read.
   */
  public void loadSystemCatalogs()
    throws MalformedURLException, IOException {

    Vector catalogs = catalogManager.getCatalogFiles();
    if (catalogs != null) {
      for (int count = 0; count < catalogs.size(); count++) {
        catalogFiles.addElement(catalogs.elementAt(count));
      }
    }

    if (catalogFiles.size() > 0) {
      // This is a little odd. The parseCatalog() method expects
      // a filename, but it adds that name to the end of the
      // catalogFiles vector, and then processes that vector.
      // This allows the system to handle CATALOG entries
      // correctly.
      //
      // In this init case, we take the last element off the
      // catalogFiles vector and pass it to parseCatalog. This
      // will "do the right thing" in the init case, and allow
      // parseCatalog() to do the right thing in the non-init
      // case. Honest.
      //
      String catfile = (String) catalogFiles.lastElement();
      catalogFiles.removeElement(catfile);
      parseCatalog(catfile);
    }
  }

  /**
   * Parse a catalog file, augmenting internal data structures.
   *
   * @param fileName The filename of the catalog file to process
   *
   * @throws MalformedURLException The fileName cannot be turned into
   * a valid URL.
   * @throws IOException Error reading catalog file.
   */
  public synchronized void parseCatalog(String fileName)
    throws MalformedURLException, IOException {

    default_override = catalogManager.getPreferPublic();
    catalogManager.debug.message(4, "Parse catalog: " + fileName);

    // Put the file into the list of catalogs to process...
    // In all cases except the case when initCatalog() is the
    // caller, this will be the only catalog initially in the list...
    catalogFiles.addElement(fileName);

    // Now process all the pending catalogs...
    parsePendingCatalogs();
  }

  /**
   * Parse a catalog file, augmenting internal data structures.
   *
   * <p>Catalogs retrieved over the net may have an associated MIME type.
   * The MIME type can be used to select an appropriate reader.</p>
   *
   * @param mimeType The MIME type of the catalog file.
   * @param is The InputStream from which the catalog should be read
   *
   * @throws CatalogException Failed to load catalog
   * mimeType.
   * @throws IOException Error reading catalog file.
   */
  public synchronized void parseCatalog(String mimeType, InputStream is)
    throws IOException, CatalogException {

    default_override = catalogManager.getPreferPublic();
    catalogManager.debug.message(4, "Parse " + mimeType + " catalog on input stream");

    CatalogReader reader = null;

    if (readerMap.containsKey(mimeType)) {
      int arrayPos = ((Integer) readerMap.get(mimeType)).intValue();
      reader = (CatalogReader) readerArr.get(arrayPos);
    }

    if (reader == null) {
      String msg = "No CatalogReader for MIME type: " + mimeType;
      catalogManager.debug.message(2, msg);
      throw new CatalogException(CatalogException.UNPARSEABLE, msg);
    }

    reader.readCatalog(this, is);

    // Now process all the pending catalogs...
    parsePendingCatalogs();
  }

  /**
   * Parse a catalog document, augmenting internal data structures.
   *
   * <p>This method supports catalog files stored in jar files: e.g.,
   * jar:file:///path/to/filename.jar!/path/to/catalog.xml". That URI
   * doesn't survive transmogrification through the URI processing that
   * the parseCatalog(String) performs and passing it as an input stream
   * doesn't set the base URI appropriately.</p>
   *
   * <p>Written by Stefan Wachter (2002-09-26)</p>
   *
   * @param aUrl The URL of the catalog document to process
   *
   * @throws IOException Error reading catalog file.
   */
  public synchronized void parseCatalog(URL aUrl) throws IOException {
    catalogCwd = aUrl;
    base = aUrl;

    default_override = catalogManager.getPreferPublic();
    catalogManager.debug.message(4, "Parse catalog: " + aUrl.toString());

    DataInputStream inStream = null;
    boolean parsed = false;

    for (int count = 0; !parsed && count < readerArr.size(); count++) {
      CatalogReader reader = (CatalogReader) readerArr.get(count);

      try {
        inStream = new DataInputStream(aUrl.openStream());
      } catch (FileNotFoundException fnfe) {
        // No catalog; give up!
        break;
      }

      try {
        reader.readCatalog(this, inStream);
        parsed=true;
      } catch (CatalogException ce) {
        if (ce.getExceptionType() == CatalogException.PARSE_FAILED) {
          // give up!
          break;
        } else {
          // try again!
        }
      }

      try {
        inStream.close();
      } catch (IOException e) {
        //nop
      }
    }

    if (parsed) parsePendingCatalogs();
  }

  /**
   * Parse all of the pending catalogs.
   *
   * <p>Catalogs may refer to other catalogs, this method parses
   * all of the currently pending catalog files.</p>
   */
  protected synchronized void parsePendingCatalogs()
    throws MalformedURLException, IOException {

    if (!localCatalogFiles.isEmpty()) {
      // Move all the localCatalogFiles into the front of
      // the catalogFiles queue
      Vector newQueue = new Vector();
      Enumeration q = localCatalogFiles.elements();
      while (q.hasMoreElements()) {
        newQueue.addElement(q.nextElement());
      }

      // Put the rest of the catalogs on the end of the new list
      for (int curCat = 0; curCat < catalogFiles.size(); curCat++) {
        String catfile = (String) catalogFiles.elementAt(curCat);
        newQueue.addElement(catfile);
      }

      catalogFiles = newQueue;
      localCatalogFiles.clear();
    }

    // Suppose there are no catalog files to process, but the
    // single catalog already parsed included some delegate
    // entries? Make sure they don't get lost.
    if (catalogFiles.isEmpty() && !localDelegate.isEmpty()) {
      Enumeration e = localDelegate.elements();
      while (e.hasMoreElements()) {
        catalogEntries.addElement(e.nextElement());
      }
      localDelegate.clear();
    }

    // Now process all the files on the catalogFiles vector. This
    // vector can grow during processing if CATALOG entries are
    // encountered in the catalog
    while (!catalogFiles.isEmpty()) {
      String catfile = (String) catalogFiles.elementAt(0);
      try {
        catalogFiles.remove(0);
      } catch (ArrayIndexOutOfBoundsException e) {
        // can't happen
      }

      if (catalogEntries.size() == 0 && catalogs.size() == 0) {
        // We haven't parsed any catalogs yet, let this
        // catalog be the first...
        try {
          parseCatalogFile(catfile);
        } catch (CatalogException ce) {
          System.out.println("FIXME: " + ce.toString());
        }
      } else {
        // This is a subordinate catalog. We save its name,
        // but don't bother to load it unless it's necessary.
        catalogs.addElement(catfile);
      }

      if (!localCatalogFiles.isEmpty()) {
        // Move all the localCatalogFiles into the front of
        // the catalogFiles queue
        Vector newQueue = new Vector();
        Enumeration q = localCatalogFiles.elements();
        while (q.hasMoreElements()) {
          newQueue.addElement(q.nextElement());
        }

        // Put the rest of the catalogs on the end of the new list
        for (int curCat = 0; curCat < catalogFiles.size(); curCat++) {
          catfile = (String) catalogFiles.elementAt(curCat);
          newQueue.addElement(catfile);
        }

        catalogFiles = newQueue;
        localCatalogFiles.clear();
      }

      if (!localDelegate.isEmpty()) {
        Enumeration e = localDelegate.elements();
        while (e.hasMoreElements()) {
          catalogEntries.addElement(e.nextElement());
        }
        localDelegate.clear();
      }
    }

    // We've parsed them all, reinit the vector...
    catalogFiles.clear();
  }

  /**
   * Parse a single catalog file, augmenting internal data structures.
   *
   * @param fileName The filename of the catalog file to process
   *
   * @throws MalformedURLException The fileName cannot be turned into
   * a valid URL.
   * @throws IOException Error reading catalog file.
   */
  protected synchronized void parseCatalogFile(String fileName)
    throws MalformedURLException, IOException, CatalogException {

    CatalogEntry entry;

    // The base-base is the cwd. If the catalog file is specified
    // with a relative path, this assures that it gets resolved
    // properly...
    try {
      // tack on a basename because URLs point to files not dirs
      catalogCwd = FileURL.makeURL("basename");
    } catch (MalformedURLException e) {
      String userdir = SecuritySupport.getSystemProperty("user.dir");
      userdir.replace('\\', '/');
      catalogManager.debug.message(1, "Malformed URL on cwd", userdir);
      catalogCwd = null;
    }

    // The initial base URI is the location of the catalog file
    try {
      base = new URL(catalogCwd, fixSlashes(fileName));
    } catch (MalformedURLException e) {
      try {
        base = new URL("file:" + fixSlashes(fileName));
      } catch (MalformedURLException e2) {
        catalogManager.debug.message(1, "Malformed URL on catalog filename",
                      fixSlashes(fileName));
        base = null;
      }
    }

    catalogManager.debug.message(2, "Loading catalog", fileName);
    catalogManager.debug.message(4, "Default BASE", base.toString());

    fileName = base.toString();

    DataInputStream inStream = null;
    boolean parsed = false;
    boolean notFound = false;

    for (int count = 0; !parsed && count < readerArr.size(); count++) {
      CatalogReader reader = (CatalogReader) readerArr.get(count);

      try {
        notFound = false;
        inStream = new DataInputStream(base.openStream());
      } catch (FileNotFoundException fnfe) {
        // No catalog; give up!
        notFound = true;
        break;
      }

      try {
        reader.readCatalog(this, inStream);
        parsed = true;
      } catch (CatalogException ce) {
        if (ce.getExceptionType() == CatalogException.PARSE_FAILED) {
          // give up!
          break;
        } else {
          // try again!
        }
      }

      try {
        inStream.close();
      } catch (IOException e) {
        //nop
      }
    }

    if (!parsed) {
      if (notFound) {
        catalogManager.debug.message(3, "Catalog does not exist", fileName);
      } else {
        catalogManager.debug.message(1, "Failed to parse catalog", fileName);
      }
    }
  }

  /**
   * Cleanup and process a Catalog entry.
   *
   * <p>This method processes each Catalog entry, changing mapped
   * relative system identifiers into absolute ones (based on the current
   * base URI), and maintaining other information about the current
   * catalog.</p>
   *
   * @param entry The CatalogEntry to process.
   */
  public void addEntry(CatalogEntry entry) {
    int type = entry.getEntryType();

    if (type == BASE) {
      String value = entry.getEntryArg(0);
      URL newbase = null;

      if (base == null) {
        catalogManager.debug.message(5, "BASE CUR", "null");
      } else {
        catalogManager.debug.message(5, "BASE CUR", base.toString());
      }
      catalogManager.debug.message(4, "BASE STR", value);

      try {
        value = fixSlashes(value);
        newbase = new URL(base, value);
      } catch (MalformedURLException e) {
        try {
          newbase = new URL("file:" + value);
        } catch (MalformedURLException e2) {
          catalogManager.debug.message(1, "Malformed URL on base", value);
          newbase = null;
        }
      }

      if (newbase != null) {
        base = newbase;
      }

      catalogManager.debug.message(5, "BASE NEW", base.toString());
    } else if (type == CATALOG) {
      String fsi = makeAbsolute(entry.getEntryArg(0));

      catalogManager.debug.message(4, "CATALOG", fsi);

      localCatalogFiles.addElement(fsi);
    } else if (type == PUBLIC) {
      String publicid = PublicId.normalize(entry.getEntryArg(0));
      String systemid = makeAbsolute(normalizeURI(entry.getEntryArg(1)));

      entry.setEntryArg(0, publicid);
      entry.setEntryArg(1, systemid);

      catalogManager.debug.message(4, "PUBLIC", publicid, systemid);

      catalogEntries.addElement(entry);
    } else if (type == SYSTEM) {
      String systemid = normalizeURI(entry.getEntryArg(0));
      String fsi = makeAbsolute(normalizeURI(entry.getEntryArg(1)));

      entry.setEntryArg(1, fsi);

      catalogManager.debug.message(4, "SYSTEM", systemid, fsi);

      catalogEntries.addElement(entry);
    } else if (type == URI) {
      String uri = normalizeURI(entry.getEntryArg(0));
      String altURI = makeAbsolute(normalizeURI(entry.getEntryArg(1)));

      entry.setEntryArg(1, altURI);

      catalogManager.debug.message(4, "URI", uri, altURI);

      catalogEntries.addElement(entry);
    } else if (type == DOCUMENT) {
      String fsi = makeAbsolute(normalizeURI(entry.getEntryArg(0)));
      entry.setEntryArg(0, fsi);

      catalogManager.debug.message(4, "DOCUMENT", fsi);

      catalogEntries.addElement(entry);
    } else if (type == OVERRIDE) {
      catalogManager.debug.message(4, "OVERRIDE", entry.getEntryArg(0));

      catalogEntries.addElement(entry);
    } else if (type == SGMLDECL) {
      // meaningless in XML
      String fsi = makeAbsolute(normalizeURI(entry.getEntryArg(0)));
      entry.setEntryArg(0, fsi);

      catalogManager.debug.message(4, "SGMLDECL", fsi);

      catalogEntries.addElement(entry);
    } else if (type == DELEGATE_PUBLIC) {
      String ppi = PublicId.normalize(entry.getEntryArg(0));
      String fsi = makeAbsolute(normalizeURI(entry.getEntryArg(1)));

      entry.setEntryArg(0, ppi);
      entry.setEntryArg(1, fsi);

      catalogManager.debug.message(4, "DELEGATE_PUBLIC", ppi, fsi);

      addDelegate(entry);
    } else if (type == DELEGATE_SYSTEM) {
      String psi = normalizeURI(entry.getEntryArg(0));
      String fsi = makeAbsolute(normalizeURI(entry.getEntryArg(1)));

      entry.setEntryArg(0, psi);
      entry.setEntryArg(1, fsi);

      catalogManager.debug.message(4, "DELEGATE_SYSTEM", psi, fsi);

      addDelegate(entry);
    } else if (type == DELEGATE_URI) {
      String pui = normalizeURI(entry.getEntryArg(0));
      String fsi = makeAbsolute(normalizeURI(entry.getEntryArg(1)));

      entry.setEntryArg(0, pui);
      entry.setEntryArg(1, fsi);

      catalogManager.debug.message(4, "DELEGATE_URI", pui, fsi);

      addDelegate(entry);
    } else if (type == REWRITE_SYSTEM) {
      String psi = normalizeURI(entry.getEntryArg(0));
      String rpx = makeAbsolute(normalizeURI(entry.getEntryArg(1)));

      entry.setEntryArg(0, psi);
      entry.setEntryArg(1, rpx);

      catalogManager.debug.message(4, "REWRITE_SYSTEM", psi, rpx);

      catalogEntries.addElement(entry);
    } else if (type == REWRITE_URI) {
      String pui = normalizeURI(entry.getEntryArg(0));
      String upx = makeAbsolute(normalizeURI(entry.getEntryArg(1)));

      entry.setEntryArg(0, pui);
      entry.setEntryArg(1, upx);

      catalogManager.debug.message(4, "REWRITE_URI", pui, upx);

      catalogEntries.addElement(entry);
    } else if (type == SYSTEM_SUFFIX) {
      String pui = normalizeURI(entry.getEntryArg(0));
      String upx = makeAbsolute(normalizeURI(entry.getEntryArg(1)));

      entry.setEntryArg(0, pui);
      entry.setEntryArg(1, upx);

      catalogManager.debug.message(4, "SYSTEM_SUFFIX", pui, upx);

      catalogEntries.addElement(entry);
    } else if (type == URI_SUFFIX) {
      String pui = normalizeURI(entry.getEntryArg(0));
      String upx = makeAbsolute(normalizeURI(entry.getEntryArg(1)));

      entry.setEntryArg(0, pui);
      entry.setEntryArg(1, upx);

      catalogManager.debug.message(4, "URI_SUFFIX", pui, upx);

      catalogEntries.addElement(entry);
    } else if (type == DOCTYPE) {
      String fsi = makeAbsolute(normalizeURI(entry.getEntryArg(1)));
      entry.setEntryArg(1, fsi);

      catalogManager.debug.message(4, "DOCTYPE", entry.getEntryArg(0), fsi);

      catalogEntries.addElement(entry);
    } else if (type == DTDDECL) {
      // meaningless in XML
      String fpi = PublicId.normalize(entry.getEntryArg(0));
      entry.setEntryArg(0, fpi);
      String fsi = makeAbsolute(normalizeURI(entry.getEntryArg(1)));
      entry.setEntryArg(1, fsi);

      catalogManager.debug.message(4, "DTDDECL", fpi, fsi);

      catalogEntries.addElement(entry);
    } else if (type == ENTITY) {
      String fsi = makeAbsolute(normalizeURI(entry.getEntryArg(1)));
      entry.setEntryArg(1, fsi);

      catalogManager.debug.message(4, "ENTITY", entry.getEntryArg(0), fsi);

      catalogEntries.addElement(entry);
    } else if (type == LINKTYPE) {
      // meaningless in XML
      String fsi = makeAbsolute(normalizeURI(entry.getEntryArg(1)));
      entry.setEntryArg(1, fsi);

      catalogManager.debug.message(4, "LINKTYPE", entry.getEntryArg(0), fsi);

      catalogEntries.addElement(entry);
    } else if (type == NOTATION) {
      String fsi = makeAbsolute(normalizeURI(entry.getEntryArg(1)));
      entry.setEntryArg(1, fsi);

      catalogManager.debug.message(4, "NOTATION", entry.getEntryArg(0), fsi);

      catalogEntries.addElement(entry);
    } else {
      catalogEntries.addElement(entry);
    }
  }

  /**
   * Handle unknown CatalogEntry types.
   *
   * <p>This method exists to allow subclasses to deal with unknown
   * entry types.</p>
   */
  public void unknownEntry(Vector strings) {
    if (strings != null && strings.size() > 0) {
      String keyword = (String) strings.elementAt(0);
      catalogManager.debug.message(2, "Unrecognized token parsing catalog", keyword);
    }
  }

  /**
   * Parse all subordinate catalogs.
   *
   * <p>This method recursively parses all of the subordinate catalogs.
   * If this method does not throw an exception, you can be confident that
   * no subsequent call to any resolve*() method will either, with two
   * possible exceptions:</p>
   *
   * <ol>
   * <li><p>Delegated catalogs are re-parsed each time they are needed
   * (because a variable list of them may be needed in each case,
   * depending on the length of the matching partial public identifier).</p>
   * <p>But they are parsed by this method, so as long as they don't
   * change or disappear while the program is running, they shouldn't
   * generate errors later if they don't generate errors now.</p>
   * <li><p>If you add new catalogs with <code>parseCatalog</code>, they
   * won't be loaded until they are needed or until you call
   * <code>parseAllCatalogs</code> again.</p>
   * </ol>
   *
   * <p>On the other hand, if you don't call this method, you may
   * successfully parse documents without having to load all possible
   * catalogs.</p>
   *
   * @throws MalformedURLException The filename (URL) for a
   * subordinate or delegated catalog is not a valid URL.
   * @throws IOException Error reading some subordinate or delegated
   * catalog file.
   */
  public void parseAllCatalogs()
    throws MalformedURLException, IOException {

    // Parse all the subordinate catalogs
    for (int catPos = 0; catPos < catalogs.size(); catPos++) {
      Catalog c = null;

      try {
        c = (Catalog) catalogs.elementAt(catPos);
      } catch (ClassCastException e) {
        String catfile = (String) catalogs.elementAt(catPos);
        c = newCatalog();

        c.parseCatalog(catfile);
        catalogs.setElementAt(c, catPos);
        c.parseAllCatalogs();
      }
    }

    // Parse all the DELEGATE catalogs
    Enumeration en = catalogEntries.elements();
    while (en.hasMoreElements()) {
      CatalogEntry e = (CatalogEntry) en.nextElement();
      if (e.getEntryType() == DELEGATE_PUBLIC
          || e.getEntryType() == DELEGATE_SYSTEM
          || e.getEntryType() == DELEGATE_URI) {
        Catalog dcat = newCatalog();
        dcat.parseCatalog(e.getEntryArg(1));
      }
    }
  }


  /**
   * Return the applicable DOCTYPE system identifier.
   *
   * @param entityName The name of the entity (element) for which
   * a doctype is required.
   * @param publicId The nominal public identifier for the doctype
   * (as provided in the source document).
   * @param systemId The nominal system identifier for the doctype
   * (as provided in the source document).
   *
   * @return The system identifier to use for the doctype.
   *
   * @throws MalformedURLException The formal system identifier of a
   * subordinate catalog cannot be turned into a valid URL.
   * @throws IOException Error reading subordinate catalog file.
   */
  public String resolveDoctype(String entityName,
                               String publicId,
                               String systemId)
    throws MalformedURLException, IOException {
    String resolved = null;

    catalogManager.debug.message(3, "resolveDoctype("
                  +entityName+","+publicId+","+systemId+")");

    systemId = normalizeURI(systemId);

    if (publicId != null && publicId.startsWith("urn:publicid:")) {
      publicId = PublicId.decodeURN(publicId);
    }

    if (systemId != null && systemId.startsWith("urn:publicid:")) {
      systemId = PublicId.decodeURN(systemId);
      if (publicId != null && !publicId.equals(systemId)) {
        catalogManager.debug.message(1, "urn:publicid: system identifier differs from public identifier; using public identifier");
        systemId = null;
      } else {
        publicId = systemId;
        systemId = null;
      }
    }

    if (systemId != null) {
      // If there's a SYSTEM entry in this catalog, use it
      resolved = resolveLocalSystem(systemId);
      if (resolved != null) {
        return resolved;
      }
    }

    if (publicId != null) {
      // If there's a PUBLIC entry in this catalog, use it
      resolved = resolveLocalPublic(DOCTYPE,
                                    entityName,
                                    publicId,
                                    systemId);
      if (resolved != null) {
        return resolved;
      }
    }

    // If there's a DOCTYPE entry in this catalog, use it
    boolean over = default_override;
    Enumeration en = catalogEntries.elements();
    while (en.hasMoreElements()) {
      CatalogEntry e = (CatalogEntry) en.nextElement();
      if (e.getEntryType() == OVERRIDE) {
        over = e.getEntryArg(0).equalsIgnoreCase("YES");
        continue;
      }

      if (e.getEntryType() == DOCTYPE
          && e.getEntryArg(0).equals(entityName)) {
        if (over || systemId == null) {
          return e.getEntryArg(1);
        }
      }
    }

    // Otherwise, look in the subordinate catalogs
    return resolveSubordinateCatalogs(DOCTYPE,
                                      entityName,
                                      publicId,
                                      systemId);
  }

  /**
   * Return the applicable DOCUMENT entry.
   *
   * @return The system identifier to use for the doctype.
   *
   * @throws MalformedURLException The formal system identifier of a
   * subordinate catalog cannot be turned into a valid URL.
   * @throws IOException Error reading subordinate catalog file.
   */
  public String resolveDocument()
    throws MalformedURLException, IOException {
    // If there's a DOCUMENT entry, return it

    catalogManager.debug.message(3, "resolveDocument");

    Enumeration en = catalogEntries.elements();
    while (en.hasMoreElements()) {
      CatalogEntry e = (CatalogEntry) en.nextElement();
      if (e.getEntryType() == DOCUMENT) {
        return e.getEntryArg(0);
      }
    }

    return resolveSubordinateCatalogs(DOCUMENT,
                                      null, null, null);
  }

  /**
   * Return the applicable ENTITY system identifier.
   *
   * @param entityName The name of the entity for which
   * a system identifier is required.
   * @param publicId The nominal public identifier for the entity
   * (as provided in the source document).
   * @param systemId The nominal system identifier for the entity
   * (as provided in the source document).
   *
   * @return The system identifier to use for the entity.
   *
   * @throws MalformedURLException The formal system identifier of a
   * subordinate catalog cannot be turned into a valid URL.
   * @throws IOException Error reading subordinate catalog file.
   */
  public String resolveEntity(String entityName,
                              String publicId,
                              String systemId)
    throws MalformedURLException, IOException {
    String resolved = null;

    catalogManager.debug.message(3, "resolveEntity("
                  +entityName+","+publicId+","+systemId+")");

    systemId = normalizeURI(systemId);

    if (publicId != null && publicId.startsWith("urn:publicid:")) {
      publicId = PublicId.decodeURN(publicId);
    }

    if (systemId != null && systemId.startsWith("urn:publicid:")) {
      systemId = PublicId.decodeURN(systemId);
      if (publicId != null && !publicId.equals(systemId)) {
        catalogManager.debug.message(1, "urn:publicid: system identifier differs from public identifier; using public identifier");
        systemId = null;
      } else {
        publicId = systemId;
        systemId = null;
      }
    }

    if (systemId != null) {
      // If there's a SYSTEM entry in this catalog, use it
      resolved = resolveLocalSystem(systemId);
      if (resolved != null) {
        return resolved;
      }
    }

    if (publicId != null) {
      // If there's a PUBLIC entry in this catalog, use it
      resolved = resolveLocalPublic(ENTITY,
                                    entityName,
                                    publicId,
                                    systemId);
      if (resolved != null) {
        return resolved;
      }
    }

    // If there's a ENTITY entry in this catalog, use it
    boolean over = default_override;
    Enumeration en = catalogEntries.elements();
    while (en.hasMoreElements()) {
      CatalogEntry e = (CatalogEntry) en.nextElement();
      if (e.getEntryType() == OVERRIDE) {
        over = e.getEntryArg(0).equalsIgnoreCase("YES");
        continue;
      }

      if (e.getEntryType() == ENTITY
          && e.getEntryArg(0).equals(entityName)) {
        if (over || systemId == null) {
          return e.getEntryArg(1);
        }
      }
    }

    // Otherwise, look in the subordinate catalogs
    return resolveSubordinateCatalogs(ENTITY,
                                      entityName,
                                      publicId,
                                      systemId);
  }

  /**
   * Return the applicable NOTATION system identifier.
   *
   * @param notationName The name of the notation for which
   * a doctype is required.
   * @param publicId The nominal public identifier for the notation
   * (as provided in the source document).
   * @param systemId The nominal system identifier for the notation
   * (as provided in the source document).
   *
   * @return The system identifier to use for the notation.
   *
   * @throws MalformedURLException The formal system identifier of a
   * subordinate catalog cannot be turned into a valid URL.
   * @throws IOException Error reading subordinate catalog file.
   */
  public String resolveNotation(String notationName,
                                String publicId,
                                String systemId)
    throws MalformedURLException, IOException {
    String resolved = null;

    catalogManager.debug.message(3, "resolveNotation("
                  +notationName+","+publicId+","+systemId+")");

    systemId = normalizeURI(systemId);

    if (publicId != null && publicId.startsWith("urn:publicid:")) {
      publicId = PublicId.decodeURN(publicId);
    }

    if (systemId != null && systemId.startsWith("urn:publicid:")) {
      systemId = PublicId.decodeURN(systemId);
      if (publicId != null && !publicId.equals(systemId)) {
        catalogManager.debug.message(1, "urn:publicid: system identifier differs from public identifier; using public identifier");
        systemId = null;
      } else {
        publicId = systemId;
        systemId = null;
      }
    }

    if (systemId != null) {
      // If there's a SYSTEM entry in this catalog, use it
      resolved = resolveLocalSystem(systemId);
      if (resolved != null) {
        return resolved;
      }
    }

    if (publicId != null) {
      // If there's a PUBLIC entry in this catalog, use it
      resolved = resolveLocalPublic(NOTATION,
                                    notationName,
                                    publicId,
                                    systemId);
      if (resolved != null) {
        return resolved;
      }
    }

    // If there's a NOTATION entry in this catalog, use it
    boolean over = default_override;
    Enumeration en = catalogEntries.elements();
    while (en.hasMoreElements()) {
      CatalogEntry e = (CatalogEntry) en.nextElement();
      if (e.getEntryType() == OVERRIDE) {
        over = e.getEntryArg(0).equalsIgnoreCase("YES");
        continue;
      }

      if (e.getEntryType() == NOTATION
          && e.getEntryArg(0).equals(notationName)) {
        if (over || systemId == null) {
          return e.getEntryArg(1);
        }
      }
    }

    // Otherwise, look in the subordinate catalogs
    return resolveSubordinateCatalogs(NOTATION,
                                      notationName,
                                      publicId,
                                      systemId);
  }

  /**
   * Return the applicable PUBLIC or SYSTEM identifier.
   *
   * <p>This method searches the Catalog and returns the system
   * identifier specified for the given system or
   * public identifiers. If
   * no appropriate PUBLIC or SYSTEM entry is found in the Catalog,
   * null is returned.</p>
   *
   * @param publicId The public identifier to locate in the catalog.
   * Public identifiers are normalized before comparison.
   * @param systemId The nominal system identifier for the entity
   * in question (as provided in the source document).
   *
   * @throws MalformedURLException The formal system identifier of a
   * subordinate catalog cannot be turned into a valid URL.
   * @throws IOException Error reading subordinate catalog file.
   *
   * @return The system identifier to use.
   * Note that the nominal system identifier is not returned if a
   * match is not found in the catalog, instead null is returned
   * to indicate that no match was found.
   */
  public String resolvePublic(String publicId, String systemId)
    throws MalformedURLException, IOException {

    catalogManager.debug.message(3, "resolvePublic("+publicId+","+systemId+")");

    systemId = normalizeURI(systemId);

    if (publicId != null && publicId.startsWith("urn:publicid:")) {
      publicId = PublicId.decodeURN(publicId);
    }

    if (systemId != null && systemId.startsWith("urn:publicid:")) {
      systemId = PublicId.decodeURN(systemId);
      if (publicId != null && !publicId.equals(systemId)) {
        catalogManager.debug.message(1, "urn:publicid: system identifier differs from public identifier; using public identifier");
        systemId = null;
      } else {
        publicId = systemId;
        systemId = null;
      }
    }

    // If there's a SYSTEM entry in this catalog, use it
    if (systemId != null) {
      String resolved = resolveLocalSystem(systemId);
      if (resolved != null) {
        return resolved;
      }
    }

    // If there's a PUBLIC entry in this catalog, use it
    String resolved = resolveLocalPublic(PUBLIC,
                                         null,
                                         publicId,
                                         systemId);
    if (resolved != null) {
      return resolved;
    }

    // Otherwise, look in the subordinate catalogs
    return resolveSubordinateCatalogs(PUBLIC,
                                      null,
                                      publicId,
                                      systemId);
  }

  /**
   * Return the applicable PUBLIC or SYSTEM identifier.
   *
   * <p>This method searches the Catalog and returns the system
   * identifier specified for the given system or public identifiers.
   * If no appropriate PUBLIC or SYSTEM entry is found in the Catalog,
   * delegated Catalogs are interrogated.</p>
   *
   * <p>There are four possible cases:</p>
   *
   * <ul>
   * <li>If the system identifier provided matches a SYSTEM entry
   * in the current catalog, the SYSTEM entry is returned.
   * <li>If the system identifier is not null, the PUBLIC entries
   * that were encountered when OVERRIDE YES was in effect are
   * interrogated and the first matching entry is returned.</li>
   * <li>If the system identifier is null, then all of the PUBLIC
   * entries are interrogated and the first matching entry
   * is returned. This may not be the same as the preceding case, if
   * some PUBLIC entries are encountered when OVERRIDE NO is in effect. In
   * XML, the only place where a public identifier may occur without
   * a system identifier is in a notation declaration.</li>
   * <li>Finally, if the public identifier matches one of the partial
   * public identifiers specified in a DELEGATE* entry in
   * the Catalog, the delegated catalog is interrogated. The first
   * time that the delegated catalog is required, it will be
   * retrieved and parsed. It is subsequently cached.
   * </li>
   * </ul>
   *
   * @param entityType The CatalogEntry type for which this query is
   * being conducted. This is necessary in order to do the approprate
   * query on a delegated catalog.
   * @param entityName The name of the entity being searched for, if
   * appropriate.
   * @param publicId The public identifier of the entity in question.
   * @param systemId The nominal system identifier for the entity
   * in question (as provided in the source document).
   *
   * @throws MalformedURLException The formal system identifier of a
   * delegated catalog cannot be turned into a valid URL.
   * @throws IOException Error reading delegated catalog file.
   *
   * @return The system identifier to use.
   * Note that the nominal system identifier is not returned if a
   * match is not found in the catalog, instead null is returned
   * to indicate that no match was found.
   */
  protected synchronized String resolveLocalPublic(int entityType,
                                                   String entityName,
                                                   String publicId,
                                                   String systemId)
    throws MalformedURLException, IOException {

    // Always normalize the public identifier before attempting a match
    publicId = PublicId.normalize(publicId);

    // If there's a SYSTEM entry in this catalog, use it
    if (systemId != null) {
      String resolved = resolveLocalSystem(systemId);
      if (resolved != null) {
        return resolved;
      }
    }

    // If there's a PUBLIC entry in this catalog, use it
    boolean over = default_override;
    Enumeration en = catalogEntries.elements();
    while (en.hasMoreElements()) {
      CatalogEntry e = (CatalogEntry) en.nextElement();
      if (e.getEntryType() == OVERRIDE) {
        over = e.getEntryArg(0).equalsIgnoreCase("YES");
        continue;
      }

      if (e.getEntryType() == PUBLIC
          && e.getEntryArg(0).equals(publicId)) {
        if (over || systemId == null) {
          return e.getEntryArg(1);
        }
      }
    }

    // If there's a DELEGATE_PUBLIC entry in this catalog, use it
    over = default_override;
    en = catalogEntries.elements();
    Vector delCats = new Vector();
    while (en.hasMoreElements()) {
      CatalogEntry e = (CatalogEntry) en.nextElement();
      if (e.getEntryType() == OVERRIDE) {
        over = e.getEntryArg(0).equalsIgnoreCase("YES");
        continue;
      }

      if (e.getEntryType() == DELEGATE_PUBLIC
          && (over || systemId == null)) {
        String p = (String) e.getEntryArg(0);
        if (p.length() <= publicId.length()
            && p.equals(publicId.substring(0, p.length()))) {
          // delegate this match to the other catalog

          delCats.addElement(e.getEntryArg(1));
        }
      }
    }

    if (delCats.size() > 0) {
      Enumeration enCats = delCats.elements();

      if (catalogManager.debug.getDebug() > 1) {
        catalogManager.debug.message(2, "Switching to delegated catalog(s):");
        while (enCats.hasMoreElements()) {
          String delegatedCatalog = (String) enCats.nextElement();
          catalogManager.debug.message(2, "\t" + delegatedCatalog);
        }
      }

      Catalog dcat = newCatalog();

      enCats = delCats.elements();
      while (enCats.hasMoreElements()) {
        String delegatedCatalog = (String) enCats.nextElement();
        dcat.parseCatalog(delegatedCatalog);
      }

      return dcat.resolvePublic(publicId, null);
    }

    // Nada!
    return null;
  }

  /**
   * Return the applicable SYSTEM system identifier.
   *
   * <p>If a SYSTEM entry exists in the Catalog
   * for the system ID specified, return the mapped value.</p>
   *
   * <p>On Windows-based operating systems, the comparison between
   * the system identifier provided and the SYSTEM entries in the
   * Catalog is case-insensitive.</p>
   *
   * @param systemId The system ID to locate in the catalog.
   *
   * @return The resolved system identifier.
   *
   * @throws MalformedURLException The formal system identifier of a
   * subordinate catalog cannot be turned into a valid URL.
   * @throws IOException Error reading subordinate catalog file.
   */
  public String resolveSystem(String systemId)
    throws MalformedURLException, IOException {

    catalogManager.debug.message(3, "resolveSystem("+systemId+")");

    systemId = normalizeURI(systemId);

    if (systemId != null && systemId.startsWith("urn:publicid:")) {
      systemId = PublicId.decodeURN(systemId);
      return resolvePublic(systemId, null);
    }

    // If there's a SYSTEM entry in this catalog, use it
    if (systemId != null) {
      String resolved = resolveLocalSystem(systemId);
      if (resolved != null) {
        return resolved;
      }
    }

    // Otherwise, look in the subordinate catalogs
    return resolveSubordinateCatalogs(SYSTEM,
                                      null,
                                      null,
                                      systemId);
  }

  /**
   * Return the applicable SYSTEM system identifier in this
   * catalog.
   *
   * <p>If a SYSTEM entry exists in the catalog file
   * for the system ID specified, return the mapped value.</p>
   *
   * @param systemId The system ID to locate in the catalog
   *
   * @return The mapped system identifier or null
   */
  protected String resolveLocalSystem(String systemId)
    throws MalformedURLException, IOException {

    String osname = SecuritySupport.getSystemProperty("os.name");
    boolean windows = (osname.indexOf("Windows") >= 0);
    Enumeration en = catalogEntries.elements();
    while (en.hasMoreElements()) {
      CatalogEntry e = (CatalogEntry) en.nextElement();
      if (e.getEntryType() == SYSTEM
          && (e.getEntryArg(0).equals(systemId)
              || (windows
                  && e.getEntryArg(0).equalsIgnoreCase(systemId)))) {
        return e.getEntryArg(1);
      }
    }

    // If there's a REWRITE_SYSTEM entry in this catalog, use it
    en = catalogEntries.elements();
    String startString = null;
    String prefix = null;
    while (en.hasMoreElements()) {
      CatalogEntry e = (CatalogEntry) en.nextElement();

      if (e.getEntryType() == REWRITE_SYSTEM) {
        String p = (String) e.getEntryArg(0);
        if (p.length() <= systemId.length()
            && p.equals(systemId.substring(0, p.length()))) {
          // Is this the longest prefix?
          if (startString == null
              || p.length() > startString.length()) {
            startString = p;
            prefix = e.getEntryArg(1);
          }
        }
      }
    }

    if (prefix != null) {
      // return the systemId with the new prefix
      return prefix + systemId.substring(startString.length());
    }

    // If there's a SYSTEM_SUFFIX entry in this catalog, use it
    en = catalogEntries.elements();
    String suffixString = null;
    String suffixURI = null;
    while (en.hasMoreElements()) {
      CatalogEntry e = (CatalogEntry) en.nextElement();

      if (e.getEntryType() == SYSTEM_SUFFIX) {
        String p = (String) e.getEntryArg(0);
        if (p.length() <= systemId.length()
            && systemId.endsWith(p)) {
          // Is this the longest prefix?
          if (suffixString == null
              || p.length() > suffixString.length()) {
            suffixString = p;
            suffixURI = e.getEntryArg(1);
          }
        }
      }
    }

    if (suffixURI != null) {
      // return the systemId for the suffix
      return suffixURI;
    }

    // If there's a DELEGATE_SYSTEM entry in this catalog, use it
    en = catalogEntries.elements();
    Vector delCats = new Vector();
    while (en.hasMoreElements()) {
      CatalogEntry e = (CatalogEntry) en.nextElement();

      if (e.getEntryType() == DELEGATE_SYSTEM) {
        String p = (String) e.getEntryArg(0);
        if (p.length() <= systemId.length()
            && p.equals(systemId.substring(0, p.length()))) {
          // delegate this match to the other catalog

          delCats.addElement(e.getEntryArg(1));
        }
      }
    }

    if (delCats.size() > 0) {
      Enumeration enCats = delCats.elements();

      if (catalogManager.debug.getDebug() > 1) {
        catalogManager.debug.message(2, "Switching to delegated catalog(s):");
        while (enCats.hasMoreElements()) {
          String delegatedCatalog = (String) enCats.nextElement();
          catalogManager.debug.message(2, "\t" + delegatedCatalog);
        }
      }

      Catalog dcat = newCatalog();

      enCats = delCats.elements();
      while (enCats.hasMoreElements()) {
        String delegatedCatalog = (String) enCats.nextElement();
        dcat.parseCatalog(delegatedCatalog);
      }

      return dcat.resolveSystem(systemId);
    }

    return null;
  }

  /**
   * Return the applicable URI.
   *
   * <p>If a URI entry exists in the Catalog
   * for the URI specified, return the mapped value.</p>
   *
   * <p>URI comparison is case sensitive.</p>
   *
   * @param uri The URI to locate in the catalog.
   *
   * @return The resolved URI.
   *
   * @throws MalformedURLException The system identifier of a
   * subordinate catalog cannot be turned into a valid URL.
   * @throws IOException Error reading subordinate catalog file.
   */
  public String resolveURI(String uri)
    throws MalformedURLException, IOException {

    catalogManager.debug.message(3, "resolveURI("+uri+")");

    uri = normalizeURI(uri);

    if (uri != null && uri.startsWith("urn:publicid:")) {
      uri = PublicId.decodeURN(uri);
      return resolvePublic(uri, null);
    }

    // If there's a URI entry in this catalog, use it
    if (uri != null) {
      String resolved = resolveLocalURI(uri);
      if (resolved != null) {
        return resolved;
      }
    }

    // Otherwise, look in the subordinate catalogs
    return resolveSubordinateCatalogs(URI,
                                      null,
                                      null,
                                      uri);
  }

  /**
   * Return the applicable URI in this catalog.
   *
   * <p>If a URI entry exists in the catalog file
   * for the URI specified, return the mapped value.</p>
   *
   * @param uri The URI to locate in the catalog
   *
   * @return The mapped URI or null
   */
  protected String resolveLocalURI(String uri)
    throws MalformedURLException, IOException {
    Enumeration en = catalogEntries.elements();
    while (en.hasMoreElements()) {
      CatalogEntry e = (CatalogEntry) en.nextElement();
      if (e.getEntryType() == URI
          && (e.getEntryArg(0).equals(uri))) {
        return e.getEntryArg(1);
      }
    }

    // If there's a REWRITE_URI entry in this catalog, use it
    en = catalogEntries.elements();
    String startString = null;
    String prefix = null;
    while (en.hasMoreElements()) {
      CatalogEntry e = (CatalogEntry) en.nextElement();

      if (e.getEntryType() == REWRITE_URI) {
        String p = (String) e.getEntryArg(0);
        if (p.length() <= uri.length()
            && p.equals(uri.substring(0, p.length()))) {
          // Is this the longest prefix?
          if (startString == null
              || p.length() > startString.length()) {
            startString = p;
            prefix = e.getEntryArg(1);
          }
        }
      }
    }

    if (prefix != null) {
      // return the uri with the new prefix
      return prefix + uri.substring(startString.length());
    }

    // If there's a URI_SUFFIX entry in this catalog, use it
    en = catalogEntries.elements();
    String suffixString = null;
    String suffixURI = null;
    while (en.hasMoreElements()) {
      CatalogEntry e = (CatalogEntry) en.nextElement();

      if (e.getEntryType() == URI_SUFFIX) {
        String p = (String) e.getEntryArg(0);
        if (p.length() <= uri.length()
            && uri.endsWith(p)) {
          // Is this the longest prefix?
          if (suffixString == null
              || p.length() > suffixString.length()) {
            suffixString = p;
            suffixURI = e.getEntryArg(1);
          }
        }
      }
    }

    if (suffixURI != null) {
      // return the uri for the suffix
      return suffixURI;
    }

    // If there's a DELEGATE_URI entry in this catalog, use it
    en = catalogEntries.elements();
    Vector delCats = new Vector();
    while (en.hasMoreElements()) {
      CatalogEntry e = (CatalogEntry) en.nextElement();

      if (e.getEntryType() == DELEGATE_URI) {
        String p = (String) e.getEntryArg(0);
        if (p.length() <= uri.length()
            && p.equals(uri.substring(0, p.length()))) {
          // delegate this match to the other catalog

          delCats.addElement(e.getEntryArg(1));
        }
      }
    }

    if (delCats.size() > 0) {
      Enumeration enCats = delCats.elements();

      if (catalogManager.debug.getDebug() > 1) {
        catalogManager.debug.message(2, "Switching to delegated catalog(s):");
        while (enCats.hasMoreElements()) {
          String delegatedCatalog = (String) enCats.nextElement();
          catalogManager.debug.message(2, "\t" + delegatedCatalog);
        }
      }

      Catalog dcat = newCatalog();

      enCats = delCats.elements();
      while (enCats.hasMoreElements()) {
        String delegatedCatalog = (String) enCats.nextElement();
        dcat.parseCatalog(delegatedCatalog);
      }

      return dcat.resolveURI(uri);
    }

    return null;
  }

  /**
   * Search the subordinate catalogs, in order, looking for a match.
   *
   * <p>This method searches the Catalog and returns the system
   * identifier specified for the given entity type with the given
   * name, public, and system identifiers. In some contexts, these
   * may be null.</p>
   *
   * @param entityType The CatalogEntry type for which this query is
   * being conducted. This is necessary in order to do the approprate
   * query on a subordinate catalog.
   * @param entityName The name of the entity being searched for, if
   * appropriate.
   * @param publicId The public identifier of the entity in question
   * (as provided in the source document).
   * @param systemId The nominal system identifier for the entity
   * in question (as provided in the source document). This parameter is
   * overloaded for the URI entry type.
   *
   * @throws MalformedURLException The formal system identifier of a
   * delegated catalog cannot be turned into a valid URL.
   * @throws IOException Error reading delegated catalog file.
   *
   * @return The system identifier to use.
   * Note that the nominal system identifier is not returned if a
   * match is not found in the catalog, instead null is returned
   * to indicate that no match was found.
   */
  protected synchronized String resolveSubordinateCatalogs(int entityType,
                                                           String entityName,
                                                           String publicId,
                                                           String systemId)
    throws MalformedURLException, IOException {

    for (int catPos = 0; catPos < catalogs.size(); catPos++) {
      Catalog c = null;

      try {
        c = (Catalog) catalogs.elementAt(catPos);
      } catch (ClassCastException e) {
        String catfile = (String) catalogs.elementAt(catPos);
        c = newCatalog();

        try {
          c.parseCatalog(catfile);
        } catch (MalformedURLException mue) {
          catalogManager.debug.message(1, "Malformed Catalog URL", catfile);
        } catch (FileNotFoundException fnfe) {
          catalogManager.debug.message(1, "Failed to load catalog, file not found",
                        catfile);
        } catch (IOException ioe) {
          catalogManager.debug.message(1, "Failed to load catalog, I/O error", catfile);
        }

        catalogs.setElementAt(c, catPos);
      }

      String resolved = null;

      // Ok, now what are we supposed to call here?
      if (entityType == DOCTYPE) {
        resolved = c.resolveDoctype(entityName,
                                    publicId,
                                    systemId);
      } else if (entityType == DOCUMENT) {
        resolved = c.resolveDocument();
      } else if (entityType == ENTITY) {
        resolved = c.resolveEntity(entityName,
                                   publicId,
                                   systemId);
      } else if (entityType == NOTATION) {
        resolved = c.resolveNotation(entityName,
                                     publicId,
                                     systemId);
      } else if (entityType == PUBLIC) {
        resolved = c.resolvePublic(publicId, systemId);
      } else if (entityType == SYSTEM) {
        resolved = c.resolveSystem(systemId);
      } else if (entityType == URI) {
        resolved = c.resolveURI(systemId);
      }

      if (resolved != null) {
        return resolved;
      }
    }

    return null;
  }

  // -----------------------------------------------------------------

  /**
   * Replace backslashes with forward slashes. (URLs always use
   * forward slashes.)
   *
   * @param sysid The input system identifier.
   * @return The same system identifier with backslashes turned into
   * forward slashes.
   */
  protected String fixSlashes (String sysid) {
    return sysid.replace('\\', '/');
  }

  /**
   * Construct an absolute URI from a relative one, using the current
   * base URI.
   *
   * @param sysid The (possibly relative) system identifier
   * @return The system identifier made absolute with respect to the
   * current {@link #base}.
   */
  protected String makeAbsolute(String sysid) {
    URL local = null;

    sysid = fixSlashes(sysid);

    try {
      local = new URL(base, sysid);
    } catch (MalformedURLException e) {
      catalogManager.debug.message(1, "Malformed URL on system identifier", sysid);
    }

    if (local != null) {
      return local.toString();
    } else {
      return sysid;
    }
  }

  /**
   * Perform character normalization on a URI reference.
   *
   * @param uriref The URI reference
   * @return The normalized URI reference.
   */
  protected String normalizeURI(String uriref) {
    if (uriref == null) {
      return null;
    }

    byte[] bytes;
    try {
      bytes = uriref.getBytes("UTF-8");
    } catch (UnsupportedEncodingException uee) {
      // this can't happen
      catalogManager.debug.message(1, "UTF-8 is an unsupported encoding!?");
      return uriref;
    }

    StringBuilder newRef = new StringBuilder(bytes.length);
    for (int count = 0; count < bytes.length; count++) {
      int ch = bytes[count] & 0xFF;

      if ((ch <= 0x20)    // ctrl
          || (ch > 0x7F)  // high ascii
          || (ch == 0x22) // "
          || (ch == 0x3C) // <
          || (ch == 0x3E) // >
          || (ch == 0x5C) // \
          || (ch == 0x5E) // ^
          || (ch == 0x60) // `
          || (ch == 0x7B) // {
          || (ch == 0x7C) // |
          || (ch == 0x7D) // }
          || (ch == 0x7F)) {
        newRef.append(encodedByte(ch));
      } else {
        newRef.append((char) bytes[count]);
      }
    }

    return newRef.toString();
  }

  /**
   * Perform %-encoding on a single byte.
   *
   * @param b The 8-bit integer that represents th byte. (Bytes are signed
              but encoding needs to look at the bytes unsigned.)
   * @return The %-encoded string for the byte in question.
   */
  protected String encodedByte (int b) {
    String hex = Integer.toHexString(b).toUpperCase();
    if (hex.length() < 2) {
      return "%0" + hex;
    } else {
      return "%" + hex;
    }
  }

  // -----------------------------------------------------------------

  /**
   * Add to the current list of delegated catalogs.
   *
   * <p>This method always constructs the {@link #localDelegate}
   * vector so that it is ordered by length of partial
   * public identifier.</p>
   *
   * @param entry The DELEGATE catalog entry
   */
  protected void addDelegate(CatalogEntry entry) {
    int pos = 0;
    String partial = entry.getEntryArg(0);

    Enumeration local = localDelegate.elements();
    while (local.hasMoreElements()) {
      CatalogEntry dpe = (CatalogEntry) local.nextElement();
      String dp = dpe.getEntryArg(0);
      if (dp.equals(partial)) {
        // we already have this prefix
        return;
      }
      if (dp.length() > partial.length()) {
        pos++;
      }
      if (dp.length() < partial.length()) {
        break;
      }
    }

    // now insert partial into the vector at [pos]
    if (localDelegate.size() == 0) {
      localDelegate.addElement(entry);
    } else {
      localDelegate.insertElementAt(entry, pos);
    }
  }
}
