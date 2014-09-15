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

package com.sun.org.apache.xml.internal.resolver.readers;

import com.sun.org.apache.xml.internal.resolver.Catalog;
import com.sun.org.apache.xml.internal.resolver.CatalogEntry;
import com.sun.org.apache.xml.internal.resolver.CatalogException;
import com.sun.org.apache.xml.internal.resolver.readers.CatalogReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Locale;
import java.util.Stack;
import java.util.Vector;

/**
 * Parses plain text Catalog files.
 *
 * <p>This class reads plain text Open Catalog files.</p>
 *
 * @see Catalog
 *
 * @author Norman Walsh
 * <a href="mailto:Norman.Walsh@Sun.COM">Norman.Walsh@Sun.COM</a>
 *
 */
public class TextCatalogReader implements CatalogReader {
  /** The input stream used to read the catalog */
  protected InputStream catfile = null;

  /**
   * Character lookahead stack. Reading a catalog sometimes requires
   * up to two characters of lookahead.
   */
  protected int[] stack = new int[3];

  /**
   * Token stack. Recognizing an unexpected catalog entry requires
   * the ability to "push back" a token.
   */
  protected Stack tokenStack = new Stack();

  /** The current position on the lookahead stack */
  protected int top = -1;

  /** Are keywords in the catalog case sensitive? */
  protected boolean caseSensitive = false;

  /**
   * Construct a CatalogReader object.
   */
  public TextCatalogReader() { }

  public void setCaseSensitive(boolean isCaseSensitive) {
    caseSensitive = isCaseSensitive;
  }

  public boolean getCaseSensitive() {
    return caseSensitive;
  }

  /**
   * Start parsing a text catalog file. The file is
   * actually read and parsed
   * as needed by <code>nextEntry</code>.</p>
   *
   * @param fileUrl  The URL or filename of the catalog file to process
   *
   * @throws MalformedURLException Improper fileUrl
   * @throws IOException Error reading catalog file
   */
  public void readCatalog(Catalog catalog, String fileUrl)
    throws MalformedURLException, IOException {
    URL catURL = null;

    try {
      catURL = new URL(fileUrl);
    } catch (MalformedURLException e) {
      catURL = new URL("file:///" + fileUrl);
    }

    URLConnection urlCon = catURL.openConnection();
    try {
      readCatalog(catalog, urlCon.getInputStream());
    } catch (FileNotFoundException e) {
      catalog.getCatalogManager().debug.message(1, "Failed to load catalog, file not found",
                                                catURL.toString());
    }
  }

  public void readCatalog(Catalog catalog, InputStream is)
    throws MalformedURLException, IOException {

    catfile = is;

    if (catfile == null) {
      return;
    }

    Vector unknownEntry = null;

    try {
      while (true) {
        String token = nextToken();

        if (token == null) {
          if (unknownEntry != null) {
            catalog.unknownEntry(unknownEntry);
            unknownEntry = null;
          }
          catfile.close();
          catfile = null;
          return;
        }

        String entryToken = null;
        if (caseSensitive) {
          entryToken = token;
        } else {
          entryToken = token.toUpperCase(Locale.ENGLISH);
        }

        try {
          int type = CatalogEntry.getEntryType(entryToken);
          int numArgs = CatalogEntry.getEntryArgCount(type);
          Vector args = new Vector();

          if (unknownEntry != null) {
            catalog.unknownEntry(unknownEntry);
            unknownEntry = null;
          }

          for (int count = 0; count < numArgs; count++) {
            args.addElement(nextToken());
          }

          catalog.addEntry(new CatalogEntry(entryToken, args));
        } catch (CatalogException cex) {
          if (cex.getExceptionType() == CatalogException.INVALID_ENTRY_TYPE) {
            if (unknownEntry == null) {
              unknownEntry = new Vector();
            }
            unknownEntry.addElement(token);
          } else if (cex.getExceptionType() == CatalogException.INVALID_ENTRY) {
            catalog.getCatalogManager().debug.message(1, "Invalid catalog entry", token);
            unknownEntry = null;
          } else if (cex.getExceptionType() == CatalogException.UNENDED_COMMENT) {
            catalog.getCatalogManager().debug.message(1, cex.getMessage());
          }
        }
      }
    } catch (CatalogException cex2) {
      if (cex2.getExceptionType() == CatalogException.UNENDED_COMMENT) {
        catalog.getCatalogManager().debug.message(1, cex2.getMessage());
      }
    }
  }

  /**
     * The destructor.
     *
     * <p>Makes sure the catalog file is closed.</p>
     */
  protected void finalize() {
    if (catfile != null) {
      try {
        catfile.close();
      } catch (IOException e) {
        // whatever...
      }
    }
    catfile = null;
  }

  // -----------------------------------------------------------------

    /**
     * Return the next token in the catalog file.
     *
     * <p>FYI: This code does not throw any sort of exception for
     * a file that contains an n
     *
     * @return The Catalog file token from the input stream.
     * @throws IOException If an error occurs reading from the stream.
     */
  protected String nextToken() throws IOException, CatalogException {
    String token = "";
    int ch, nextch;

    if (!tokenStack.empty()) {
      return (String) tokenStack.pop();
    }

    // Skip over leading whitespace and comments
    while (true) {
      // skip leading whitespace
      ch = catfile.read();
      while (ch <= ' ') {      // all ctrls are whitespace
        ch = catfile.read();
        if (ch < 0) {
          return null;
        }
      }

      // now 'ch' is the current char from the file
      nextch = catfile.read();
      if (nextch < 0) {
        return null;
      }

      if (ch == '-' && nextch == '-') {
        // we've found a comment, skip it...
        ch = ' ';
        nextch = nextChar();
        while ((ch != '-' || nextch != '-') && nextch > 0) {
          ch = nextch;
          nextch = nextChar();
        }

        if (nextch < 0) {
          throw new CatalogException(CatalogException.UNENDED_COMMENT,
                                     "Unterminated comment in catalog file; EOF treated as end-of-comment.");
        }

        // Ok, we've found the end of the comment,
        // loop back to the top and start again...
      } else {
        stack[++top] = nextch;
        stack[++top] = ch;
        break;
      }
    }

    ch = nextChar();
    if (ch == '"' || ch == '\'') {
      int quote = ch;
      while ((ch = nextChar()) != quote) {
        char[] chararr = new char[1];
        chararr[0] = (char) ch;
        String s = new String(chararr);
        token = token.concat(s);
      }
      return token;
    } else {
      // return the next whitespace or comment delimited
      // string
      while (ch > ' ') {
        nextch = nextChar();
        if (ch == '-' && nextch == '-') {
          stack[++top] = ch;
          stack[++top] = nextch;
          return token;
        } else {
          char[] chararr = new char[1];
          chararr[0] = (char) ch;
          String s = new String(chararr);
          token = token.concat(s);
          ch = nextch;
        }
      }
      return token;
    }
  }

  /**
     * Return the next logical character from the input stream.
     *
     * @return The next (logical) character from the input stream. The
     * character may be buffered from a previous lookahead.
     *
     * @throws IOException If an error occurs reading from the stream.
     */
  protected int nextChar() throws IOException {
    if (top < 0) {
      return catfile.read();
    } else {
      return stack[top--];
    }
  }
}
