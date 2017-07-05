/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
// Resolver.java - Represents an extension of OASIS Open Catalog files.

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

import java.io.IOException;
import java.io.InputStream;
import java.io.FileNotFoundException;
import java.util.Enumeration;
import java.util.Vector;
import java.net.URL;
import java.net.URLConnection;
import java.net.MalformedURLException;
import javax.xml.parsers.SAXParserFactory;
import com.sun.org.apache.xerces.internal.jaxp.SAXParserFactoryImpl;
import com.sun.org.apache.xerces.internal.utils.SecuritySupport;
import com.sun.org.apache.xml.internal.resolver.readers.SAXCatalogReader;
import com.sun.org.apache.xml.internal.resolver.readers.OASISXMLCatalogReader;
import com.sun.org.apache.xml.internal.resolver.readers.TR9401CatalogReader;

/**
 * An extension to OASIS Open Catalog files, this class supports
 * suffix-based matching and an external RFC2483 resolver.
 *
 * @see Catalog
 *
 * @author Norman Walsh
 * <a href="mailto:Norman.Walsh@Sun.COM">Norman.Walsh@Sun.COM</a>
 *
 * @version 1.0
 */
public class Resolver extends Catalog {
  /**
   * The URISUFFIX Catalog Entry type.
   *
   * <p>URI suffix entries match URIs that end in a specified suffix.</p>
   */
  public static final int URISUFFIX = CatalogEntry.addEntryType("URISUFFIX", 2);

  /**
   * The SYSTEMSUFFIX Catalog Entry type.
   *
   * <p>System suffix entries match system identifiers that end in a
   * specified suffix.</p>
   */
  public static final int SYSTEMSUFFIX = CatalogEntry.addEntryType("SYSTEMSUFFIX", 2);

  /**
   * The RESOLVER Catalog Entry type.
   *
   * <p>A hook for providing support for web-based backup resolvers.</p>
   */
  public static final int RESOLVER = CatalogEntry.addEntryType("RESOLVER", 1);

  /**
   * The SYSTEMREVERSE Catalog Entry type.
   *
   * <p>This is a bit of a hack. There's no actual SYSTEMREVERSE entry,
   * but this entry type is used to indicate that a reverse lookup is
   * being performed. (This allows the Resolver to implement
   * RFC2483 I2N and I2NS.)
   */
  public static final int SYSTEMREVERSE
    = CatalogEntry.addEntryType("SYSTEMREVERSE", 1);

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
                               "com.sun.org.apache.xml.internal.resolver.readers.ExtendedXMLCatalogReader");

    addReader("application/xml", saxReader);

    TR9401CatalogReader textReader = new TR9401CatalogReader();
    addReader("text/plain", textReader);
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

    if (type == URISUFFIX) {
      String suffix = normalizeURI(entry.getEntryArg(0));
      String fsi = makeAbsolute(normalizeURI(entry.getEntryArg(1)));

      entry.setEntryArg(1, fsi);

      catalogManager.debug.message(4, "URISUFFIX", suffix, fsi);
    } else if (type == SYSTEMSUFFIX) {
      String suffix = normalizeURI(entry.getEntryArg(0));
      String fsi = makeAbsolute(normalizeURI(entry.getEntryArg(1)));

      entry.setEntryArg(1, fsi);

      catalogManager.debug.message(4, "SYSTEMSUFFIX", suffix, fsi);
    }

    super.addEntry(entry);
  }

  /**
   * Return the applicable URI.
   *
   * <p>If a URI entry exists in the Catalog
   * for the URI specified, return the mapped value.</p>
   *
   * <p>In the Resolver (as opposed to the Catalog) class, if the
   * URI isn't found by the usual algorithm, URISUFFIX entries are
   * considered.</p>
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

    String resolved = super.resolveURI(uri);
    if (resolved != null) {
      return resolved;
    }

    Enumeration en = catalogEntries.elements();
    while (en.hasMoreElements()) {
      CatalogEntry e = (CatalogEntry) en.nextElement();
      if (e.getEntryType() == RESOLVER) {
        resolved = resolveExternalSystem(uri, e.getEntryArg(0));
        if (resolved != null) {
          return resolved;
        }
      } else if (e.getEntryType() == URISUFFIX) {
        String suffix = e.getEntryArg(0);
        String result = e.getEntryArg(1);

        if (suffix.length() <= uri.length()
            && uri.substring(uri.length()-suffix.length()).equals(suffix)) {
          return result;
        }
      }
    }

    // Otherwise, look in the subordinate catalogs
    return resolveSubordinateCatalogs(Catalog.URI,
                                      null,
                                      null,
                                      uri);
  }

  /**
   * Return the applicable SYSTEM system identifier, resorting
   * to external RESOLVERs if necessary.
   *
   * <p>If a SYSTEM entry exists in the Catalog
   * for the system ID specified, return the mapped value.</p>
   *
   * <p>In the Resolver (as opposed to the Catalog) class, if the
   * URI isn't found by the usual algorithm, SYSTEMSUFFIX entries are
   * considered.</p>
   *
   * <p>On Windows-based operating systems, the comparison between
   * the system identifier provided and the SYSTEM entries in the
   * Catalog is case-insensitive.</p>
   *
   * @param systemId The system ID to locate in the catalog.
   *
   * @return The system identifier to use for systemId.
   *
   * @throws MalformedURLException The formal system identifier of a
   * subordinate catalog cannot be turned into a valid URL.
   * @throws IOException Error reading subordinate catalog file.
   */
  public String resolveSystem(String systemId)
    throws MalformedURLException, IOException {

    String resolved = super.resolveSystem(systemId);
    if (resolved != null) {
      return resolved;
    }

    Enumeration en = catalogEntries.elements();
    while (en.hasMoreElements()) {
      CatalogEntry e = (CatalogEntry) en.nextElement();
      if (e.getEntryType() == RESOLVER) {
        resolved = resolveExternalSystem(systemId, e.getEntryArg(0));
        if (resolved != null) {
          return resolved;
        }
      } else if (e.getEntryType() == SYSTEMSUFFIX) {
        String suffix = e.getEntryArg(0);
        String result = e.getEntryArg(1);

        if (suffix.length() <= systemId.length()
            && systemId.substring(systemId.length()-suffix.length()).equals(suffix)) {
          return result;
        }
      }
    }

    return resolveSubordinateCatalogs(Catalog.SYSTEM,
                                      null,
                                      null,
                                      systemId);
  }

  /**
   * Return the applicable PUBLIC or SYSTEM identifier, resorting
   * to external resolvers if necessary.
   *
   * <p>This method searches the Catalog and returns the system
   * identifier specified for the given system or
   * public identifiers. If
   * no appropriate PUBLIC or SYSTEM entry is found in the Catalog,
   * null is returned.</p>
   *
   * <p>Note that a system or public identifier in the current catalog
   * (or subordinate catalogs) will be used in preference to an
   * external resolver. Further, if a systemId is present, the external
   * resolver(s) will be queried for that before the publicId.</p>
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

    String resolved = super.resolvePublic(publicId, systemId);
    if (resolved != null) {
      return resolved;
    }

    Enumeration en = catalogEntries.elements();
    while (en.hasMoreElements()) {
      CatalogEntry e = (CatalogEntry) en.nextElement();
      if (e.getEntryType() == RESOLVER) {
        if (systemId != null) {
          resolved = resolveExternalSystem(systemId,
                                           e.getEntryArg(0));
          if (resolved != null) {
            return resolved;
          }
        }
        resolved = resolveExternalPublic(publicId, e.getEntryArg(0));
        if (resolved != null) {
          return resolved;
        }
      }
    }

    return resolveSubordinateCatalogs(Catalog.PUBLIC,
                                      null,
                                      publicId,
                                      systemId);
  }

    /**
     * Query an external RFC2483 resolver for a system identifier.
     *
     * @param systemId The system ID to locate.
     * @param resolver The name of the resolver to use.
     *
     * @return The system identifier to use for the systemId.
     */
    protected String resolveExternalSystem(String systemId, String resolver)
        throws MalformedURLException, IOException {
        Resolver r = queryResolver(resolver, "i2l", systemId, null);
        if (r != null) {
            return r.resolveSystem(systemId);
        } else {
            return null;
        }
    }

    /**
     * Query an external RFC2483 resolver for a public identifier.
     *
     * @param publicId The system ID to locate.
     * @param resolver The name of the resolver to use.
     *
     * @return The system identifier to use for the systemId.
     */
    protected String resolveExternalPublic(String publicId, String resolver)
        throws MalformedURLException, IOException {
        Resolver r = queryResolver(resolver, "fpi2l", publicId, null);
        if (r != null) {
            return r.resolvePublic(publicId, null);
        } else {
            return null;
        }
    }

    /**
     * Query an external RFC2483 resolver.
     *
     * @param resolver The URL of the RFC2483 resolver.
     * @param command The command to send the resolver.
     * @param arg1 The first argument to the resolver.
     * @param arg2 The second argument to the resolver, usually null.
     *
     * @return The Resolver constructed.
     */
    protected Resolver queryResolver(String resolver,
                                     String command,
                                     String arg1,
                                     String arg2) {
        InputStream iStream = null;
        String RFC2483 = resolver + "?command=" + command
            + "&format=tr9401&uri=" + arg1
            + "&uri2=" + arg2;
        String line = null;

        try {
            URL url = new URL(RFC2483);

            URLConnection urlCon = url.openConnection();

            urlCon.setUseCaches(false);

            Resolver r = (Resolver) newCatalog();

            String cType = urlCon.getContentType();

            // I don't care about the character set or subtype
            if (cType.indexOf(";") > 0) {
                cType = cType.substring(0, cType.indexOf(";"));
            }

            r.parseCatalog(cType, urlCon.getInputStream());

            return r;
        } catch (CatalogException cex) {
          if (cex.getExceptionType() == CatalogException.UNPARSEABLE) {
            catalogManager.debug.message(1, "Unparseable catalog: " + RFC2483);
          } else if (cex.getExceptionType()
                     == CatalogException.UNKNOWN_FORMAT) {
            catalogManager.debug.message(1, "Unknown catalog format: " + RFC2483);
          }
          return null;
        } catch (MalformedURLException mue) {
            catalogManager.debug.message(1, "Malformed resolver URL: " + RFC2483);
            return null;
        } catch (IOException ie) {
            catalogManager.debug.message(1, "I/O Exception opening resolver: " + RFC2483);
            return null;
        }
    }

    /**
     * Append two vectors, returning the result.
     *
     * @param vec The first vector
     * @param appvec The vector to be appended
     * @return The vector vec, with appvec's elements appended to it
     */
    private Vector appendVector(Vector vec, Vector appvec) {
        if (appvec != null) {
            for (int count = 0; count < appvec.size(); count++) {
                vec.addElement(appvec.elementAt(count));
            }
        }
        return vec;
    }

    /**
     * Find the URNs for a given system identifier in all catalogs.
     *
     * @param systemId The system ID to locate.
     *
     * @return A vector of URNs that map to the systemId.
     */
    public Vector resolveAllSystemReverse(String systemId)
        throws MalformedURLException, IOException {
        Vector resolved = new Vector();

        // If there's a SYSTEM entry in this catalog, use it
        if (systemId != null) {
            Vector localResolved = resolveLocalSystemReverse(systemId);
            resolved = appendVector(resolved, localResolved);
        }

        // Otherwise, look in the subordinate catalogs
        Vector subResolved = resolveAllSubordinateCatalogs(SYSTEMREVERSE,
                                                           null,
                                                           null,
                                                           systemId);

        return appendVector(resolved, subResolved);
    }

    /**
     * Find the URN for a given system identifier.
     *
     * @param systemId The system ID to locate.
     *
     * @return A (single) URN that maps to the systemId.
     */
    public String resolveSystemReverse(String systemId)
        throws MalformedURLException, IOException {
        Vector resolved = resolveAllSystemReverse(systemId);
        if (resolved != null && resolved.size() > 0) {
            return (String) resolved.elementAt(0);
        } else {
            return null;
        }
    }

    /**
     * Return the applicable SYSTEM system identifiers.
     *
     * <p>If one or more SYSTEM entries exists in the Catalog
     * for the system ID specified, return the mapped values.</p>
     *
     * <p>The caller is responsible for doing any necessary
     * normalization of the system identifier before calling
     * this method. For example, a relative system identifier in
     * a document might be converted to an absolute system identifier
     * before attempting to resolve it.</p>
     *
     * <p>Note that this function will force all subordinate catalogs
     * to be loaded.</p>
     *
     * <p>On Windows-based operating systems, the comparison between
     * the system identifier provided and the SYSTEM entries in the
     * Catalog is case-insensitive.</p>
     *
     * @param systemId The system ID to locate in the catalog.
     *
     * @return The system identifier to use for the notation.
     *
     * @throws MalformedURLException The formal system identifier of a
     * subordinate catalog cannot be turned into a valid URL.
     * @throws IOException Error reading subordinate catalog file.
     */
    public Vector resolveAllSystem(String systemId)
        throws MalformedURLException, IOException {
        Vector resolutions = new Vector();

        // If there are SYSTEM entries in this catalog, start with them
        if (systemId != null) {
            Vector localResolutions = resolveAllLocalSystem(systemId);
            resolutions = appendVector(resolutions, localResolutions);
        }

        // Then look in the subordinate catalogs
        Vector subResolutions = resolveAllSubordinateCatalogs(SYSTEM,
                                                              null,
                                                              null,
                                                              systemId);
        resolutions = appendVector(resolutions, subResolutions);

        if (resolutions.size() > 0) {
            return resolutions;
        } else {
            return null;
        }
    }

    /**
     * Return all applicable SYSTEM system identifiers in this
     * catalog.
     *
     * <p>If one or more SYSTEM entries exists in the catalog file
     * for the system ID specified, return the mapped values.</p>
     *
     * @param systemId The system ID to locate in the catalog
     *
     * @return A vector of the mapped system identifiers or null
     */
    private Vector resolveAllLocalSystem(String systemId) {
        Vector map = new Vector();
        String osname = SecuritySupport.getSystemProperty("os.name");
        boolean windows = (osname.indexOf("Windows") >= 0);
        Enumeration en = catalogEntries.elements();
        while (en.hasMoreElements()) {
            CatalogEntry e = (CatalogEntry) en.nextElement();
            if (e.getEntryType() == SYSTEM
                && (e.getEntryArg(0).equals(systemId)
                    || (windows
                        && e.getEntryArg(0).equalsIgnoreCase(systemId)))) {
                map.addElement(e.getEntryArg(1));
            }
        }
        if (map.size() == 0) {
            return null;
        } else {
            return map;
        }
    }

    /**
     * Find the URNs for a given system identifier in the current catalog.
     *
     * @param systemId The system ID to locate.
     *
     * @return A vector of URNs that map to the systemId.
     */
    private Vector resolveLocalSystemReverse(String systemId) {
        Vector map = new Vector();
        String osname = SecuritySupport.getSystemProperty("os.name");
        boolean windows = (osname.indexOf("Windows") >= 0);
        Enumeration en = catalogEntries.elements();
        while (en.hasMoreElements()) {
            CatalogEntry e = (CatalogEntry) en.nextElement();
            if (e.getEntryType() == SYSTEM
                && (e.getEntryArg(1).equals(systemId)
                    || (windows
                        && e.getEntryArg(1).equalsIgnoreCase(systemId)))) {
                map.addElement(e.getEntryArg(0));
            }
        }
        if (map.size() == 0) {
            return null;
        } else {
            return map;
        }
    }

    /**
     * Search the subordinate catalogs, in order, looking for all
     * match.
     *
     * <p>This method searches the Catalog and returns all of the system
     * identifiers specified for the given entity type with the given
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
    private synchronized Vector resolveAllSubordinateCatalogs(int entityType,
                                              String entityName,
                                              String publicId,
                                              String systemId)
        throws MalformedURLException, IOException {

        Vector resolutions = new Vector();

        for (int catPos = 0; catPos < catalogs.size(); catPos++) {
            Resolver c = null;

            try {
                c = (Resolver) catalogs.elementAt(catPos);
            } catch (ClassCastException e) {
                String catfile = (String) catalogs.elementAt(catPos);
                c = (Resolver) newCatalog();

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
                if (resolved != null) {
                    // Only find one DOCTYPE resolution
                    resolutions.addElement(resolved);
                    return resolutions;
                }
            } else if (entityType == DOCUMENT) {
                resolved = c.resolveDocument();
                if (resolved != null) {
                    // Only find one DOCUMENT resolution
                    resolutions.addElement(resolved);
                    return resolutions;
                }
            } else if (entityType == ENTITY) {
                resolved = c.resolveEntity(entityName,
                                           publicId,
                                           systemId);
                if (resolved != null) {
                    // Only find one ENTITY resolution
                    resolutions.addElement(resolved);
                    return resolutions;
                }
            } else if (entityType == NOTATION) {
                resolved = c.resolveNotation(entityName,
                                             publicId,
                                             systemId);
                if (resolved != null) {
                    // Only find one NOTATION resolution
                    resolutions.addElement(resolved);
                    return resolutions;
                }
            } else if (entityType == PUBLIC) {
                resolved = c.resolvePublic(publicId, systemId);
                if (resolved != null) {
                    // Only find one PUBLIC resolution
                    resolutions.addElement(resolved);
                    return resolutions;
                }
            } else if (entityType == SYSTEM) {
                Vector localResolutions = c.resolveAllSystem(systemId);
                resolutions = appendVector(resolutions, localResolutions);
                break;
            } else if (entityType == SYSTEMREVERSE) {
                Vector localResolutions = c.resolveAllSystemReverse(systemId);
                resolutions = appendVector(resolutions, localResolutions);
            }
        }

        if (resolutions != null) {
            return resolutions;
        } else {
            return null;
        }
    }
}
