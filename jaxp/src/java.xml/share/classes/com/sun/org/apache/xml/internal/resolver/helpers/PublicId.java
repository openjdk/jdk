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

package com.sun.org.apache.xml.internal.resolver.helpers;

/**
 * Static methods for dealing with public identifiers.
 *
 * <p>This class defines a set of static methods that can be called
 * to handle public identifiers.</p>
 *
 * @author Norman Walsh
 * <a href="mailto:Norman.Walsh@Sun.COM">Norman.Walsh@Sun.COM</a>
 *
 */
public abstract class PublicId {

  protected PublicId() {}

  /**
   * Normalize a public identifier.
   *
   * <p>Public identifiers must be normalized according to the following
   * rules before comparisons between them can be made:</p>
   *
   * <ul>
   * <li>Whitespace characters are normalized to spaces (e.g., line feeds,
   * tabs, etc. become spaces).</li>
   * <li>Leading and trailing whitespace is removed.</li>
   * <li>Multiple internal whitespaces are normalized to a single
   * space.</li>
   * </ul>
   *
   * <p>This method is declared static so that other classes
   * can use it directly.</p>
   *
   * @param publicId The unnormalized public identifier.
   *
   * @return The normalized identifier.
   */
  public static String normalize(String publicId) {
      String normal = publicId.replace('\t', ' ');
      normal = normal.replace('\r', ' ');
      normal = normal.replace('\n', ' ');
      normal = normal.trim();

      int pos;

      while ((pos = normal.indexOf("  ")) >= 0) {
          normal = normal.substring(0, pos) + normal.substring(pos+1);
      }
      return normal;
  }

  /**
   * Encode a public identifier as a "publicid" URN.
   *
   * <p>This method is declared static so that other classes
   * can use it directly.</p>
   *
   * @param publicId The unnormalized public identifier.
   *
   * @return The normalized identifier.
   */
  public static String encodeURN(String publicId) {
      String urn = PublicId.normalize(publicId);

      urn = PublicId.stringReplace(urn, "%", "%25");
      urn = PublicId.stringReplace(urn, ";", "%3B");
      urn = PublicId.stringReplace(urn, "'", "%27");
      urn = PublicId.stringReplace(urn, "?", "%3F");
      urn = PublicId.stringReplace(urn, "#", "%23");
      urn = PublicId.stringReplace(urn, "+", "%2B");
      urn = PublicId.stringReplace(urn, " ", "+");
      urn = PublicId.stringReplace(urn, "::", ";");
      urn = PublicId.stringReplace(urn, ":", "%3A");
      urn = PublicId.stringReplace(urn, "//", ":");
      urn = PublicId.stringReplace(urn, "/", "%2F");

      StringBuilder buffer = new StringBuilder(13 + urn.length());
      buffer.append("urn:publicid:");
      buffer.append(urn);
      return buffer.toString();
  }

  /**
   * Decode a "publicid" URN into a public identifier.
   *
   * <p>This method is declared static so that other classes
   * can use it directly.</p>
   *
   * @param urn The urn:publicid: URN
   *
   * @return The normalized identifier.
   */
  public static String decodeURN(String urn) {
      String publicId;
      if (urn.startsWith("urn:publicid:")) {
          publicId = urn.substring(13);
      }
      else {
          return urn;
      }

      final boolean hasEscape = (publicId.indexOf('%') >= 0);
      if (hasEscape) {
          publicId = PublicId.stringReplace(publicId, "%2F", "/");
      }
      publicId = PublicId.stringReplace(publicId, ":", "//");
      if (hasEscape) {
          publicId = PublicId.stringReplace(publicId, "%3A", ":");
      }
      publicId = PublicId.stringReplace(publicId, ";", "::");
      publicId = PublicId.stringReplace(publicId, "+", " ");
      if (hasEscape) {
          publicId = PublicId.stringReplace(publicId, "%2B", "+");
          publicId = PublicId.stringReplace(publicId, "%23", "#");
          publicId = PublicId.stringReplace(publicId, "%3F", "?");
          publicId = PublicId.stringReplace(publicId, "%27", "'");
          publicId = PublicId.stringReplace(publicId, "%3B", ";");
          publicId = PublicId.stringReplace(publicId, "%25", "%");
      }

      return publicId;
  }

  /**
   * Replace one string with another.
   */
  private static String stringReplace(String str,
          String oldStr,
          String newStr) {
      int pos = str.indexOf(oldStr);
      if (pos >= 0) {
          final StringBuilder buffer = new StringBuilder();
          final int oldStrLength = oldStr.length();
          int start = 0;
          do {
              for (int i = start; i < pos; ++i) {
                  buffer.append(str.charAt(i));
              }
              buffer.append(newStr);
              start = pos + oldStrLength;
              pos = str.indexOf(oldStr, start);
          }
          while (pos >= 0);
          final int strLength = str.length();
          for (int i = start; i < strLength; ++i) {
              buffer.append(str.charAt(i));
          }
          return buffer.toString();
      }
      return str;
  }
}
