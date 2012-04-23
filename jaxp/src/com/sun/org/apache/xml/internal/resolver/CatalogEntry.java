/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
// CatalogEntry.java - Represents Catalog entries

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

import java.util.Hashtable;
import java.util.Vector;

/**
 * Represents a Catalog entry.
 *
 * <p>Instances of this class represent individual entries
 * in a Catalog.</p>
 *
 * <p>Each catalog entry has a unique name and is associated with
 * an arbitrary number of arguments (all strings). For example, the
 * TR9401 catalog entry "PUBLIC" has two arguments, a public identifier
 * and a system identifier. Each entry has a unique numeric type,
 * assigned automatically when the entry type is created.</p>
 *
 * <p>The number and type of catalog entries is maintained
 * <em>statically</em>. Catalog classes, or their subclasses, can add
 * new entry types, but all Catalog objects share the same global pool
 * of types.</p>
 *
 * <p>Initially there are no valid entries.</p>
 *
 * @see Catalog
 *
 * @author Norman Walsh
 * <a href="mailto:Norman.Walsh@Sun.COM">Norman.Walsh@Sun.COM</a>
 *
 */
public class CatalogEntry {
  /** The nextEntry is the ordinal number of the next entry type. */
  protected static int nextEntry = 0;

  /**
   * The entryTypes vector maps catalog entry names
   * (e.g., 'BASE' or 'SYSTEM') to their type (1, 2, etc.).
   * Names are case sensitive.
   */
  protected static Hashtable entryTypes = new Hashtable();

  /** The entryTypes vector maps catalog entry types to the
      number of arguments they're required to have. */
  protected static Vector entryArgs = new Vector();

  /**
   * Adds a new catalog entry type.
   *
   * @param name The name of the catalog entry type. This must be
   * unique among all types and is case-sensitive. (Adding a duplicate
   * name effectively replaces the old type with the new type.)
   * @param numArgs The number of arguments that this entry type
   * is required to have. There is no provision for variable numbers
   * of arguments.
   * @return The type for the new entry.
   */
  public static int addEntryType(String name, int numArgs) {
    entryTypes.put(name, new Integer(nextEntry));
    entryArgs.add(nextEntry, new Integer(numArgs));
    nextEntry++;

    return nextEntry-1;
  }

  /**
   * Lookup an entry type
   *
   * @param name The name of the catalog entry type.
   * @return The type of the catalog entry with the specified name.
   * @throws InvalidCatalogEntryTypeException if no entry has the
   * specified name.
   */
  public static int getEntryType(String name)
    throws CatalogException {
    if (!entryTypes.containsKey(name)) {
      throw new CatalogException(CatalogException.INVALID_ENTRY_TYPE);
    }

    Integer iType = (Integer) entryTypes.get(name);

    if (iType == null) {
      throw new CatalogException(CatalogException.INVALID_ENTRY_TYPE);
    }

    return iType.intValue();
  }

  /**
   * Find out how many arguments an entry is required to have.
   *
   * @param name The name of the catalog entry type.
   * @return The number of arguments that entry type is required to have.
   * @throws InvalidCatalogEntryTypeException if no entry has the
   * specified name.
   */
  public static int getEntryArgCount(String name)
    throws CatalogException {
    return getEntryArgCount(getEntryType(name));
  }

  /**
   * Find out how many arguments an entry is required to have.
   *
   * @param type A valid catalog entry type.
   * @return The number of arguments that entry type is required to have.
   * @throws InvalidCatalogEntryTypeException if the type is invalid.
   */
  public static int getEntryArgCount(int type)
    throws CatalogException {
    try {
      Integer iArgs = (Integer) entryArgs.get(type);
      return iArgs.intValue();
    } catch (ArrayIndexOutOfBoundsException e) {
      throw new CatalogException(CatalogException.INVALID_ENTRY_TYPE);
    }
  }

  /** The entry type of this entry */
  protected int entryType = 0;

  /** The arguments associated with this entry */
  protected Vector args = null;

  /**
   * Null constructor; something for subclasses to call.
   */
  public CatalogEntry() {}

  /**
   * Construct a catalog entry of the specified type.
   *
   * @param name The name of the entry type
   * @param args A String Vector of arguments
   * @throws InvalidCatalogEntryTypeException if no such entry type
   * exists.
   * @throws InvalidCatalogEntryException if the wrong number of arguments
   * is passed.
   */
  public CatalogEntry(String name, Vector args)
    throws CatalogException {
    Integer iType = (Integer) entryTypes.get(name);

    if (iType == null) {
      throw new CatalogException(CatalogException.INVALID_ENTRY_TYPE);
    }

    int type = iType.intValue();

    try {
      Integer iArgs = (Integer) entryArgs.get(type);
      if (iArgs.intValue() != args.size()) {
        throw new CatalogException(CatalogException.INVALID_ENTRY);
      }
    } catch (ArrayIndexOutOfBoundsException e) {
      throw new CatalogException(CatalogException.INVALID_ENTRY_TYPE);
    }

    entryType = type;
    this.args = args;
  }

  /**
   * Construct a catalog entry of the specified type.
   *
   * @param type The entry type
   * @param args A String Vector of arguments
   * @throws InvalidCatalogEntryTypeException if no such entry type
   * exists.
   * @throws InvalidCatalogEntryException if the wrong number of arguments
   * is passed.
   */
  public CatalogEntry(int type, Vector args)
    throws CatalogException {
    try {
      Integer iArgs = (Integer) entryArgs.get(type);
      if (iArgs.intValue() != args.size()) {
        throw new CatalogException(CatalogException.INVALID_ENTRY);
      }
    } catch (ArrayIndexOutOfBoundsException e) {
      throw new CatalogException(CatalogException.INVALID_ENTRY_TYPE);
    }

    entryType = type;
    this.args = args;
  }

  /**
   * Get the entry type.
   *
   * @return The entry type of the CatalogEntry
   */
  public int getEntryType() {
    return entryType;
  }

  /**
   * Get an entry argument.
   *
   * @param argNum The argument number (arguments are numbered from 0).
   * @return The specified argument or null if an invalid argNum is
   * provided.
   */
  public String getEntryArg(int argNum) {
    try {
      String arg = (String) args.get(argNum);
      return arg;
    } catch (ArrayIndexOutOfBoundsException e) {
      return null;
    }
  }

  /**
   * Set an entry argument.
   *
   * <p>Catalogs sometimes need to adjust the catlog entry parameters,
   * for example to make a relative URI absolute with respect to the
   * current base URI. But in general, this function should only be
   * called shortly after object creation to do some sort of cleanup.
   * Catalog entries should not mutate over time.</p>
   *
   * @param argNum The argument number (arguments are numbered from 0).
   * @throws ArrayIndexOutOfBoundsException if an invalid argument
   * number is provided.
   */
  public void setEntryArg(int argNum, String newspec)
    throws ArrayIndexOutOfBoundsException {
    args.set(argNum, newspec);
  }
}
