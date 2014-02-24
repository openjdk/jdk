/*
 * Copyright (c) 2000, 2013, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Collections;
import java.util.HashSet;
import java.io.PrintStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class JarReorder {

    // To deal with output
    private PrintStream out;

    private void usage() {
        String help;
        help =
                "Usage:  jar JarReorder [-o <outputfile>] <order_list> <exclude_list> <file> ...\n"
                + "   order_list    is a file containing names of files to load\n"
                + "                 in order at the end of a jar file unless\n"
                + "                 excluded in the exclude list.\n"
                + "   exclude_list  is a file containing names of files/directories\n"
                + "                 NOT to be included in a jar file.\n"
                + "\n"
                + "The order_list or exclude_list may be replaced by a \"-\" if no\n"
                + "data is to be provided.\n"
                + "\n"
                + "   The remaining arguments are files or directories to be included\n"
                + "   in a jar file, from which will be excluded those entries which\n"
                + "   appear in the exclude list.\n";
        System.err.println(help);
    }


    /*
     * Create the file list to be included in a jar file, such that the
     * list will appear in a specific order, and allowing certain
     * files and directories to be excluded.
     *
     * Command path arguments are
     *    - optional -o outputfile
     *    - name of a file containing a set of files to be included in a jar file.
     *    - name of a file containing a set of files (or directories) to be
     *      excluded from the jar file.
     *    - names of files or directories to be searched for files to include
     *      in the jar file.
     */
    public static void main(String[] args) {
        JarReorder jr = new JarReorder();
        jr.run(args);
    }

    private void run(String args[]) {

        int arglen = args.length;
        int argpos = 0;

        // Look for "-o outputfilename" option
        if (arglen > 0) {
            if (arglen >= 2 && args[0].equals("-o")) {
                try {
                    out = new PrintStream(new FileOutputStream(args[1]));
                } catch (FileNotFoundException e) {
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

        // Should be 2 or more args left
        if (arglen <= 2) {
            usage();
            System.exit(1);
        }

        // Read the ordered set of files to be included in rt.jar.
        // Read the set of files/directories to be excluded from rt.jar.
        String classListFile = args[argpos];
        String excludeListFile = args[argpos + 1];
        argpos += 2;
        arglen -= 2;

        // Create 2 lists and a set of processed files
        List<String> orderList = readListFromFile(classListFile, true);
        List<String> excludeList = readListFromFile(excludeListFile, false);
        Set<String> processed = new HashSet<String>();

        // Create set of all files and directories excluded, then expand
        //   that list completely
        Set<String> excludeSet = new HashSet<String>(excludeList);
        Set<String> allFilesExcluded = expand(null, excludeSet, processed);

        // Indicate all these have been processed, orderList too, kept to end.
        processed.addAll(orderList);

        // The remaining arguments are names of files/directories to be included
        // in the jar file.
        Set<String> inputSet = new HashSet<String>();
        for (int i = 0; i < arglen; ++i) {
            String name = args[argpos + i];
            name = cleanPath(new File(name));
            if ( name != null && name.length() > 0 && !inputSet.contains(name) ) {
                inputSet.add(name);
            }
        }

        // Expand file/directory input so we get a complete set (except ordered)
        //   Should be everything not excluded and not in order list.
        Set<String> allFilesIncluded = expand(null, inputSet, processed);

        // Create simple sorted list so we can add ordered items at end.
        List<String> allFiles = new ArrayList<String>(allFilesIncluded);
        Collections.sort(allFiles);

        // Now add the ordered set to the end of the list.
        // Add in REVERSE ORDER, so that the first element is closest to
        // the end (and the index).
        for (int i = orderList.size() - 1; i >= 0; --i) {
            String s = orderList.get(i);
            if (allFilesExcluded.contains(s)) {
                // Disable this warning until 8005688 is fixed
                // System.err.println("Included order file " + s
                //    + " is also excluded, skipping.");
            } else if (new File(s).exists()) {
                allFiles.add(s);
            } else {
                System.err.println("Included order file " + s
                    + " missing, skipping.");
            }
        }

        // Print final results.
        for (String str : allFiles) {
            out.println(str);
        }
        out.flush();
        out.close();
    }

    /*
     * Read a file containing a list of files and directories into a List.
     */
    private List<String> readListFromFile(String fileName,
            boolean addClassSuffix) {

        BufferedReader br = null;
        List<String> list = new ArrayList<String>();
        // If you see "-" for the name, just assume nothing was provided.
        if ("-".equals(fileName)) {
            return list;
        }
        try {
            br = new BufferedReader(new FileReader(fileName));
            // Read the input file a path at a time. # in column 1 is a comment.
            while (true) {
                String path = br.readLine();
                if (path == null) {
                    break;
                }
                // Look for comments
                path = path.trim();
                if (path.length() == 0
                        || path.charAt(0) == '#') {
                    continue;
                }
                // Add trailing .class if necessary
                if (addClassSuffix && !path.endsWith(".class")) {
                    path = path + ".class";
                }
                // Normalize the path
                path = cleanPath(new File(path));
                // Add to list
                if (path != null && path.length() > 0 && !list.contains(path)) {
                    list.add(path);
                }
            }
            br.close();
        } catch (FileNotFoundException e) {
            System.err.println("Can't find file \"" + fileName + "\".");
            System.exit(1);
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(2);
        }
        return list;
    }

    /*
     * Expands inputSet (files or dirs) into full set of all files that
     * can be found by recursively descending directories.
     * @param dir root directory
     * @param inputSet set of files or dirs to look into
     * @param processed files or dirs already processed
     * @return set of files
     */
    private Set<String> expand(File dir,
            Set<String> inputSet,
            Set<String> processed) {
        Set<String> includedFiles = new HashSet<String>();
        if (inputSet.isEmpty()) {
            return includedFiles;
        }
        for (String name : inputSet) {
            // Depending on start location
            File f = (dir == null) ? new File(name)
                    : new File(dir, name);
            // Normalized path to use
            String path = cleanPath(f);
            if (path != null && path.length() > 0
                    && !processed.contains(path)) {
                if (f.isFile()) {
                    // Not in the excludeList, add it to both lists
                    includedFiles.add(path);
                    processed.add(path);
                } else if (f.isDirectory()) {
                    // Add the directory entries
                    String[] dirList = f.list();
                    Set<String> dirInputSet = new HashSet<String>();
                    for (String x : dirList) {
                        dirInputSet.add(x);
                    }
                    // Process all entries in this directory
                    Set<String> subList = expand(f, dirInputSet, processed);
                    includedFiles.addAll(subList);
                    processed.add(path);
                }
            }
        }
        return includedFiles;
    }

    private String cleanPath(File f) {
        String path = f.getPath();
        if (f.isFile()) {
            path = cleanFilePath(path);
        } else if (f.isDirectory()) {
            path = cleanDirPath(path);
        } else {
            System.err.println("WARNING: Path does not exist as file or directory: " + path);
            path = null;
        }
        return path;
    }

    private String cleanFilePath(String path) {
        // Remove leading and trailing whitespace
        path = path.trim();
        // Make all / and \ chars one
        if (File.separatorChar == '/') {
            path = path.replace('\\', '/');
        } else {
            path = path.replace('/', '\\');
        }
        // Remove leading ./
        if (path.startsWith("." + File.separator)) {
            path = path.substring(2);
        }
        return path;
    }

    private String cleanDirPath(String path) {
        path = cleanFilePath(path);
        // Make sure it ends with a file separator
        if (!path.endsWith(File.separator)) {
            path = path + File.separator;
        }
        return path;
    }

}
