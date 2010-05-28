/*
 * Copyright (c) 2000, 2005, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
 * Read an input file which is output from a java -verbose run,
 * combine with an argument list of files and directories, and
 * write a list of items to be included in a jar file.
 */

package build.tools.jarreorder;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Vector;
import java.io.PrintStream;
import java.io.FileOutputStream;

public class JarReorder {

    // To deal with output
    private static PrintStream out;

    private final static boolean useTopDir = false;

    private static void usage() {
        String help;
        help =
            "Usage:  jar JarReorder [-o <outputfile>] <order_list> <exclude_list> <file> ...\n"
          + "   order_list    is a file containing names of files to load\n"
          + "                 in order at the end of a jar file.\n"
          + "   exclude_list  is a file containing names of files/directories\n"
          + "                 NOT to be included in a jar file.\n";
        if (useTopDir)
            help +=
                "   top_dir       is the top of the directory structure to be searched;\n"
              + "                 the contents of the lists and remaining arguments are\n"
              + "                 relative to this.\n";
        help +=
            "\n"
          + "The order_list or exclude_list may be replaced by a \"_\" if no\n"
          + "data is to be provided.\n"
          + "\n"
          + "   The remaining arguments are files or directories to be included\n"
          + "   in a jar file, from which will be excluded thse entries which\n"
          + "   appear in the exclude list.\n";
        System.err.println(help);
        System.exit(1);
    }


    /*
     * Create a list of files to be included in a jar file, such that the
     * some the files will appear in a specific order, and allowing certain
     * files and directories to be excluded.
     *
     * Command line arguments are
     *    - optional -o outputfile
     *    - name of a file containing a list of files to be included in a jar file.
     *    - name of a file containing a list of files (or directories) to be
     *      excluded from the jar file.
     *    - names of files or directories to be searched for files to include
     *      in the jar file.
     */
    public static void main(String[] args) {

        HashMap filesExcluded = new HashMap();
        Vector filesIncluded = new Vector();
        int fileArgs;
        String topDirName = "";
        int arglen = args.length;
        int argpos = 0;

        // Look for "-o outputfilename" option
        if ( arglen > 0 ) {
            if ( arglen >= 2 && args[0].equals("-o") ) {
                try {
                    out = new PrintStream(new FileOutputStream(args[1]));
                } catch ( FileNotFoundException e ) {
                    System.err.println("Error: " + e.getMessage());
                    e.printStackTrace(System.err);
                    System.exit(1);
                }
                argpos += 2;
                arglen -= 2;
            } else {
                System.err.println("Error: Illegal arg count");
                System.exit(1);
            }
        } else {
            out = System.out;
        }

        fileArgs = useTopDir ? 3 : 2;

        if (arglen <= fileArgs) {
            usage();
        }

        // Read the ordered list of files to be included in rt.jar.
        // Read the list of files/directories to be excluded from rt.jar.

        Vector orderList = readListFromFile(args[argpos], true);
        Vector excludeList = readListFromFile(args[argpos+1], false);
        if (useTopDir) {
            topDirName = args[argpos+2];
            if (!topDirName.endsWith(File.separator))
                topDirName = topDirName + File.separator;
        }

        // Copy these lists into filesExcluded so that these files will be excluded
        // from the file list. (The orderList files will be appended later.)

        for (int i = 0; i < orderList.size(); ++i) {
            String s = (String) orderList.elementAt(i);
            filesExcluded.put(s, s);
        }
        for (int i = 0; i < excludeList.size(); ++i) {
            String s = (String) excludeList.elementAt(i);
            filesExcluded.put(s, s);
        }

        // The remaining arguments are names of files/directories to be included
        // in the jar file.

        String[] files = new String[arglen - fileArgs];
        for (int i = fileArgs; i < arglen; ++i) {
            files[i-fileArgs] = args[argpos+i];
            filesExcluded.put(args[argpos+i], args[argpos+i]);
        }

        // Expand file/directory list to file list excluding those
        // read from the class list.

        if (useTopDir)
            expand(new File(topDirName), files, filesIncluded, filesExcluded, topDirName);
        else
            expand(null, files, filesIncluded, filesExcluded, null);

        // Now add the ordered list to the end of the expanded list.
        // Add in REVERSE ORDER, so that the first element is closest to
        // the end (and the index).

        HashSet excludeSet = new HashSet(excludeList);
        for (int i = orderList.size() - 1; i >= 0; --i) {
            String s = (String) orderList.elementAt(i);
            if (excludeSet.contains(s)) {
                System.err.println("Included file " + s + " is also excluded, skipping.");
                continue;
            }
            if (new File(topDirName + s).exists())
                filesIncluded.addElement(s);
            else
                System.err.println("Included file "+s+" missing, skipping.");
        }

        // Print results.

        for (int i = 0; i < filesIncluded.size(); ++i) {
            if (useTopDir) {
                out.print("-C ");
                out.print(topDirName);
                out.print(" ");
            }
            out.println((String)filesIncluded.elementAt(i));
        }

        out.flush();
        out.close();
    }


    /*
     * Read a file containing a list of files into a Vector.
     */
    private static Vector readListFromFile(String fileName,
                                           boolean addClassSuffix) {

        BufferedReader br = null;
        Vector v = new Vector(2000);

        if ("-".equals(fileName))
            return v;

        try {
            br = new BufferedReader(new FileReader(fileName));

        // Read the input file a line at a time. # in column 1 is a comment.

            while (true) {
                String line = null;
                line = br.readLine();

                if (line == null)
                    break;

                if (line.length() == 0    ||
                    line.charAt(0) == '#')
                    continue;

                // Convert forward or back slashes to the type expected for
                // the current platform.

                if (File.separatorChar == '/')
                    line = line.replace('\\', '/');
                else
                    line = line.replace('/', '\\');

                line = line.trim();
                if (addClassSuffix) {
                    if (!line.endsWith(".class")) {
                        line = line + ".class";
                    }
                }
                v.addElement(line);
            }
            br.close();
        } catch (FileNotFoundException e) {
            System.err.println("Can't find file \"" + fileName + "\".");
            System.exit(1);
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(2);
        }
        return v;
    }


    /*
     * Expands list of files to process into full list of all files that
     * can be found by recursively descending directories.
     */
    private static void expand(File dir, String[] files,
                               Vector includedFiles, HashMap excludedFiles,
                               String topDirName) {
        if (files == null) {
            return;
        }
        for (int i = 0; i < files.length; i++) {
            File f = (dir == null) ? new File(files[i])
                                   : new File(dir, files[i]);
            if (f.isFile()) {
                String filePath = f.getPath();

                if (useTopDir) {
                    if (filePath.startsWith(topDirName))
                        filePath = filePath.substring(topDirName.length());
                }

                if (filePath.length() >= 2 &&
                    filePath.charAt(0) == '.' &&
                    filePath.charAt(1) == File.separatorChar)
                    filePath = filePath.substring(2);

                if (!excludedFiles.containsKey(filePath)) {
                    excludedFiles.put(filePath, filePath);
                    includedFiles.addElement(filePath);
                }
            } else if (f.isDirectory()) {
                String dirPath = f.getPath();
                dirPath = (dirPath.endsWith(File.separator)) ? dirPath :
                    (dirPath + File.separator);

                if (useTopDir) {
                    if (dirPath.startsWith(topDirName))
                        dirPath = dirPath.substring(topDirName.length());
                }

                if (dirPath.length() >= 2 &&
                    dirPath.charAt(0) == '.' &&
                    dirPath.charAt(1) == File.separatorChar)
                    dirPath = dirPath.substring(2);

                if (!excludedFiles.containsKey(dirPath)) {

                    // Sort the directory list so that entries in the jar file
                    // are in a repeatable order.  The order itself is not particularly
                    // important.  [File.list() is unpredictable.]

                    String[] dirList = f.list();
                    Arrays.sort(dirList);
                    expand(f, dirList, includedFiles, excludedFiles, topDirName);
                }
            } else {
                System.err.println("Error accessing: " + f.getPath());
            }
        }
    }
}
