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
 * $Id: XResourceBundle.java,v 1.2.4.1 2005/09/15 08:16:04 suresh_emailid Exp $
 */
package com.sun.org.apache.xml.internal.utils.res;

import java.util.ListResourceBundle;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * The default (english) resource bundle.
 * @xsl.usage internal
 */
public class XResourceBundle extends ListResourceBundle
{

  /** Error resource constants */
  public static final String ERROR_RESOURCES =
    "com.sun.org.apache.xalan.internal.res.XSLTErrorResources", XSLT_RESOURCE =
    "com.sun.org.apache.xml.internal.utils.res.XResourceBundle", LANG_BUNDLE_NAME =
    "com.sun.org.apache.xml.internal.utils.res.XResources", MULT_ORDER =
    "multiplierOrder", MULT_PRECEDES = "precedes", MULT_FOLLOWS =
    "follows", LANG_ORIENTATION = "orientation", LANG_RIGHTTOLEFT =
    "rightToLeft", LANG_LEFTTORIGHT = "leftToRight", LANG_NUMBERING =
    "numbering", LANG_ADDITIVE = "additive", LANG_MULT_ADD =
    "multiplicative-additive", LANG_MULTIPLIER =
    "multiplier", LANG_MULTIPLIER_CHAR =
    "multiplierChar", LANG_NUMBERGROUPS = "numberGroups", LANG_NUM_TABLES =
    "tables", LANG_ALPHABET = "alphabet", LANG_TRAD_ALPHABET = "tradAlphabet";

  /**
   * Return a named ResourceBundle for a particular locale.  This method mimics the behavior
   * of ResourceBundle.getBundle().
   *
   * @param className Name of local-specific subclass.
   * @param locale the locale to prefer when searching for the bundle
   */
  public static final XResourceBundle loadResourceBundle(
          String className, Locale locale) throws MissingResourceException
  {

    String suffix = getResourceSuffix(locale);

    //System.out.println("resource " + className + suffix);
    try
    {

      // first try with the given locale
      String resourceName = className + suffix;
      return (XResourceBundle) ResourceBundle.getBundle(resourceName, locale);
    }
    catch (MissingResourceException e)
    {
      try  // try to fall back to en_US if we can't load
      {

        // Since we can't find the localized property file,
        // fall back to en_US.
        return (XResourceBundle) ResourceBundle.getBundle(
          XSLT_RESOURCE, new Locale("en", "US"));
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

    String lang = locale.getLanguage();
    String country = locale.getCountry();
    String variant = locale.getVariant();
    String suffix = "_" + locale.getLanguage();

    if (lang.equals("zh"))
      suffix += "_" + country;

    if (country.equals("JP"))
      suffix += "_" + country + "_" + variant;

    return suffix;
  }

  /**
   * Get the association list.
   *
   * @return The association list.
   */
  public Object[][] getContents()
  {
    return new Object[][]
  {
    { "ui_language", "en" }, { "help_language", "en" }, { "language", "en" },
    { "alphabet", new CharArrayWrapper(new char[]{ 'A', 'B', 'C', 'D', 'E', 'F', 'G',
         'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U',
         'V', 'W', 'X', 'Y', 'Z' })},
    { "tradAlphabet", new CharArrayWrapper(new char[]{ 'A', 'B', 'C', 'D', 'E', 'F',
         'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T',
         'U', 'V', 'W', 'X', 'Y', 'Z' }) },

    //language orientation
    { "orientation", "LeftToRight" },

    //language numbering
    { "numbering", "additive" },
  };
  }
}
