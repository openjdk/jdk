/*
 * Copyright 2003 Sun Microsystems, Inc.  All Rights Reserved.
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

package build.tools.generatebreakiteratordata;

import java.util.Enumeration;
import java.util.ListResourceBundle;
import java.util.Locale;
import java.util.ResourceBundle;

/**
 * Generates datafile for BreakIterator.
 */
public class GenerateBreakIteratorData {

    /**
     * Directory where generated data files are put in.
     */
    private static String outputDir = "" ;

    /**
     * Unicode data file
     */
    private static String unicodeData = "UnicodeData.txt";

    /**
     * Rules file
     */
    private static String rules = "sun.text.resources.BreakIteratorRules";

    /**
     * Locale data
     */
    private static String language = "";
    private static String country = "";
    private static String valiant = "";
    private static String localeName = "";  /* _language_country_valiant */


    public static void main(String[] args) {
        /* Parse command-line options */
        processArgs(args);

        /* Make categoryMap from Unicode data */
        CharacterCategory.makeCategoryMap(unicodeData);

        /* Generate files */
        generateFiles();
    }

    /**
     * Generate data files whose names are included in
     * sun.text.resources.BreakIteratorInfo+<localeName>
     */
    private static void generateFiles() {
        String[] classNames;
        ResourceBundle rules, info;

        info =  ResourceBundle.getBundle("sun.text.resources.BreakIteratorInfo",
                                       new Locale(language, country, valiant));
        classNames = info.getStringArray("BreakIteratorClasses");

        rules = ResourceBundle.getBundle("sun.text.resources.BreakIteratorRules",
                                       new Locale(language, country, valiant));

        /*
         * Fallback is not necessary here.... So, cannot use getBundle().
         */
        try {
            info = (ResourceBundle)Class.forName("sun.text.resources.BreakIteratorInfo" + localeName).newInstance();

            Enumeration keys = info.getKeys();
            while (keys.hasMoreElements()) {
                String key = (String)keys.nextElement();

                if (key.equals("CharacterData")) {
                    generateDataFile(info.getString(key),
                                     rules.getString("CharacterBreakRules"),
                                     classNames[0]);
                } else if (key.endsWith("WordData")) {
                    generateDataFile(info.getString(key),
                                     rules.getString("WordBreakRules"),
                                     classNames[1]);
                } else if (key.endsWith("LineData")) {
                    generateDataFile(info.getString(key),
                                     rules.getString("LineBreakRules"),
                                     classNames[2]);
                } else if (key.endsWith("SentenceData")) {
                    generateDataFile(info.getString(key),
                                     rules.getString("SentenceBreakRules"),
                                     classNames[3]);
                }
            }
        }
        catch (Exception e) {
            throw new InternalError(e.toString());
        }
    }

    /**
     * Generate a data file for break-iterator
     */
    private static void generateDataFile(String datafile, String rule, String builder) {
        RuleBasedBreakIteratorBuilder bld;
        if (builder.equals("RuleBasedBreakIterator")) {
            bld = new RuleBasedBreakIteratorBuilder(rule);
        } else if (builder.equals("DictionaryBasedBreakIterator")) {
            bld = new DictionaryBasedBreakIteratorBuilder(rule);
        } else {
            throw new IllegalArgumentException("Invalid break iterator class \"" + builder + "\"");
        }

        bld.makeFile(datafile);
    }

    /**
     * Parses the specified arguments and sets up the variables.
     */
    private static void processArgs(String[] args) {
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.equals("-o")) {
                outputDir = args[++i];
            } else if (arg.equals("-spec")) {
                unicodeData = args[++i];
            } else if (arg.equals("-language")) {
                language = args[++i];
            } else if (arg.equals("-country")) {
                country = args[++i];
            } else if (arg.equals("-valiant")) {
                valiant = args[++i];
            } else {
                usage();
            }
        }

        // Set locale name
        localeName = getLocaleName();
    }

    /**
     * Make locale name ("_language_country_valiant")
     */
    private static String getLocaleName() {
        if (language.equals("")) {
            if (!country.equals("") || !valiant.equals("")) {
                language = "en";
            } else {
                return "";
            }
        }

        StringBuffer sb = new StringBuffer();
        sb.append('_');
        sb.append(language);
        if (!country.equals("") || !valiant.equals("")) {
            sb.append('_');
            sb.append(country);
            if (!valiant.equals("")) {
                sb.append('_');
                sb.append(valiant);
            }
        }

        return sb.toString();
    }

    /**
     * Usage: Displayed when an invalid command-line option is specified.
     */
    private static void usage() {
        System.err.println("Usage: GenerateBreakIteratorData [options]\n" +
        "    -o outputDir                 output directory name\n" +
        "    -spec specname               unicode text filename\n" +
        "  and locale data:\n" +
        "    -lang language               target language name\n" +
        "    -country country             target country name\n" +
        "    -valiant valiant             target valiant name\n"
        );
    }

    /**
     * Return the path of output directory
     */
    static String getOutputDirectory() {
        return outputDir;
    }
}
