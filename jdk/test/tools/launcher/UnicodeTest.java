/*
 * Copyright (c) 2007, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */


/*
 *
 *
 * Used by UnicodeTest.sh.
 *
 * This class creates Java source files using Unicode characters
 * that test the limits of what's possible
 * - in situations where the platform encoding imposes limits
 *   (command line arguments, non-Unicode file system)
 * - in situations where full Unicode is supported
 *   (file system access in UTF-8 locales and on Windows 2000++,
 *    jar file contents)
 *
 * @author Norbert Lindenberg
 */



import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.util.Locale;

public class UnicodeTest {

    public static void main(String[] args) throws Exception {

        String commandLineClassNameSuffix = commandLineClassNameSuffix();
        String commandLineClassName = "ClassA" + commandLineClassNameSuffix;
        String manifestClassName;
        if (hasUnicodeFileSystem()) {
            manifestClassName = "ClassB" + unicode;
        } else {
            manifestClassName = "ClassB" + commandLineClassNameSuffix;
        }

        generateSource(commandLineClassName, manifestClassName);
        generateSource(manifestClassName, commandLineClassName);
        generateManifest(manifestClassName);

        System.out.println(commandLineClassName);
    }

    private static final String fileSeparator = System.getProperty("file.separator");
    private static final String osName = System.getProperty("os.name");
    private static final String defaultEncoding = Charset.defaultCharset().name();

    // language names taken from java.util.Locale.getDisplayLanguage for the respective language
    private static final String arabic = "\u0627\u0644\u0639\u0631\u0628\u064a\u0629";
    private static final String s_chinese = "\u4e2d\u6587";
    private static final String t_chinese = "\u4e2d\u6587";
    private static final String russian = "\u0440\u0443\u0441\u0441\u043A\u0438\u0439";
    private static final String hindi = "\u0939\u093f\u0902\u0926\u0940";
    private static final String greek = "\u03b5\u03bb\u03bb\u03b7\u03bd\u03b9\u03ba\u03ac";
    private static final String hebrew = "\u05e2\u05d1\u05e8\u05d9\u05ea";
    private static final String japanese = "\u65e5\u672c\u8a9e";
    private static final String korean = "\ud55c\uad6d\uc5b4";
    private static final String lithuanian = "Lietuvi\u0173";
    private static final String czech = "\u010de\u0161tina";
    private static final String turkish = "T\u00fcrk\u00e7e";
    private static final String spanish = "espa\u00f1ol";
    private static final String thai = "\u0e44\u0e17\u0e22";
    private static final String unicode = arabic + s_chinese + t_chinese
            + russian + hindi + greek + hebrew + japanese + korean
            + lithuanian + czech + turkish + spanish + thai;

    private static String commandLineClassNameSuffix() {

        // Mapping from main platform encodings to language names
        // for Unix and Windows, respectively. Use empty suffix
        // for Windows encodings where OEM encoding differs.
        // Use null if encoding isn't used.
        String[][] names = {
            { "UTF-8",          unicode,        ""              },
            { "windows-1256",   null,           ""              },
            { "iso-8859-6",     arabic,         null            },
            { "GBK",            s_chinese,      s_chinese       },
            { "GB18030",        s_chinese,      s_chinese       },
            { "GB2312",         s_chinese,      null            },
            { "x-windows-950",  null,           t_chinese       },
            { "x-MS950-HKSCS",  null,           t_chinese       },
            { "x-euc-tw",       t_chinese,      null            },
            { "Big5",           t_chinese,      null            },
            { "Big5-HKSCS",     t_chinese,      null            },
            { "windows-1251",   null,           ""              },
            { "iso-8859-5",     russian,        null            },
            { "koi8-r",         russian,        null            },
            { "windows-1253",   null,           ""              },
            { "iso-8859-7",     greek,          null            },
            { "windows-1255",   null,           ""              },
            { "iso8859-8",      hebrew,         null            },
            { "windows-31j",    null,           japanese        },
            { "x-eucJP-Open",   japanese,       null            },
            { "x-EUC-JP-LINUX", japanese,       null            },
            { "x-pck",          japanese,       null            },
            { "x-windows-949",  null,           korean          },
            { "euc-kr",         korean,         null            },
            { "windows-1257",   null,           ""              },
            { "iso-8859-13",    lithuanian,     null            },
            { "windows-1250",   null,           ""              },
            { "iso-8859-2",     czech,          null            },
            { "windows-1254",   null,           ""              },
            { "iso-8859-9",     turkish,        null            },
            { "windows-1252",   null,           ""              },
            { "iso-8859-1",     spanish,        null            },
            { "iso-8859-15",    spanish,        null            },
            { "x-windows-874",  null,           thai            },
            { "tis-620",        thai,           null            },
        };

        int column;
        if (osName.startsWith("Windows")) {
            column = 2;
        } else {
            column = 1;
        }
        for (int i = 0; i < names.length; i++) {
             if (names[i][0].equalsIgnoreCase(defaultEncoding)) {
                 return names[i][column];
             }
         }
         return "";
    }

    private static boolean hasUnicodeFileSystem() {
        if (osName.startsWith("Windows")) {
            return ! osName.startsWith("Windows 9") &&
                   ! osName.equals("Windows Me");
        } else {
            return defaultEncoding.equalsIgnoreCase("UTF-8");
        }
    }

    private static void generateSource(String thisClass, String otherClass) throws Exception {
        String fileName = "UnicodeTest-src" + fileSeparator + thisClass + ".java";
        OutputStreamWriter out = new OutputStreamWriter(new FileOutputStream(fileName), "UTF-8");
        out.write("public class " + thisClass + " {\n");
        out.write("    public static void main(String[] args) {\n");
        out.write("        if (!" + otherClass + "." + otherClass.toLowerCase() + "().equals(\"" + otherClass + "\")) {\n");
        out.write("            throw new RuntimeException();\n");
        out.write("        }\n");
        out.write("    }\n");
        out.write("    public static String " + thisClass.toLowerCase() + "() {\n");
        out.write("        return \"" + thisClass + "\";\n");
        out.write("    }\n");
        out.write("}\n");
        out.close();
    }

    private static void generateManifest(String mainClass) throws Exception {
        String fileName = "UnicodeTest-src" + fileSeparator + "MANIFEST.MF";
        FileOutputStream out = new FileOutputStream(fileName);
        out.write("Manifest-Version: 1.0\n".getBytes("UTF-8"));
        // Header lines are limited to 72 bytes.
        // The manifest spec doesn't say we have to break at character boundaries,
        // so we rudely break at byte boundaries.
        byte[] headerBytes = ("Main-Class: " + mainClass + "\n").getBytes("UTF-8");
        if (headerBytes.length <= 72) {
            out.write(headerBytes);
        } else {
            out.write(headerBytes, 0, 72);
            int start = 72;
            while (headerBytes.length > start) {
                out.write((byte) '\n');
                out.write((byte) ' ');
                int count = Math.min(71, headerBytes.length - start);
                out.write(headerBytes, start, count);
                start += count;
            }
        }
        out.close();
    }
}
