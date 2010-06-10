/*
 * Copyright (c) 1999, 2007, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

import java.io.*;
import java.util.*;

abstract class HsArgHandler extends ArgHandler {
    static final int STRING = 1;
    static final int VECTOR = 2;
    static final int HASH   = 3;

    boolean nextNotKey(ArgIterator it) {
        if (it.next()) {
            String s = it.get();
            return (s.length() == 0) || (s.charAt(0) != '-');
        } else {
            return false;
        }
    }

    void empty(String key, String message) {
        if (key != null) {
            System.err.println("** Error: empty " + key);
        }
        if (message != null) {
            System.err.println(message);
        }
        WinGammaPlatform.usage();
    }

    static String getCfg(String val) {
        int under = val.indexOf('_');
        int len = val.length();
        if (under != -1 && under < len - 1) {
            return val.substring(under+1, len);
        } else {
            return null;
        }
    }
}

class ArgRuleSpecific extends ArgRule {
    ArgRuleSpecific(String arg, ArgHandler handler) {
        super(arg, handler);
    }

    boolean match(String rulePattern, String arg) {
        return rulePattern.startsWith(arg);
    }
}


class SpecificHsArgHandler extends HsArgHandler {

    String message, argKey, valKey;
    int type;

    public void handle(ArgIterator it) {
        String cfg = getCfg(it.get());
        if (nextNotKey(it)) {
            String val = it.get();
            switch (type) {
            case VECTOR:
                BuildConfig.addFieldVector(cfg, valKey, val);
                break;
            case HASH:
                BuildConfig.putFieldHash(cfg, valKey, val, "1");
                break;
            case STRING:
                BuildConfig.putField(cfg, valKey, val);
                break;
            default:
                empty(valKey, "Unknown type: "+type);
            }
            it.next();

        } else {
            empty(argKey, message);
        }
    }

    SpecificHsArgHandler(String argKey, String valKey, String message, int type) {
        this.argKey = argKey;
        this.valKey = valKey;
        this.message = message;
        this.type = type;
    }
}


class HsArgRule extends ArgRuleSpecific {

    HsArgRule(String argKey, String valKey, String message, int type) {
        super(argKey, new SpecificHsArgHandler(argKey, valKey, message, type));
    }

}

public abstract class WinGammaPlatform extends Platform {
    public void setupFileTemplates() {
        inclFileTemplate = new FileName(this,
            "incls\\", "_", "",                      ".incl", "", ""
        );
        giFileTemplate = new FileName(this,
            "incls\\", "",  "_precompiled", ".incl", "", ""
        );
        gdFileTemplate = new FileName(this,
            "", "",  "Dependencies",         "",      "", ""
        );
    }

    private static String[] suffixes = { ".cpp", ".c" };

    public String[] outerSuffixes() {
        return suffixes;
    }

    public String objFileSuffix() {
        return ".obj";
    }

    public String asmFileSuffix() {
        return ".i";
    }

    public String dependentPrefix() {
        return "$(VM_PATH)";
    }

    public boolean includeGIInEachIncl() {
        return false;
    }

    public boolean fileNameStringEquality(String s1, String s2) {
        return s1.equalsIgnoreCase(s2);
    }

    static void usage() throws IllegalArgumentException {
        System.err.println("WinGammaPlatform platform-specific options:");
        System.err.println("  -sourceBase <path to directory (workspace) " +
                           "containing source files; no trailing slash>");
        System.err.println("  -projectFileName <full pathname to which project file " +
                           "will be written; all parent directories must " +
                           "already exist>");
        System.err.println("  If any of the above are specified, "+
                           "they must all be.");
        System.err.println("  Additional, optional arguments, which can be " +
                           "specified multiple times:");
        System.err.println("    -absoluteInclude <string containing absolute " +
                           "path to include directory>");
        System.err.println("    -relativeInclude <string containing include " +
                           "directory relative to -sourceBase>");
        System.err.println("    -define <preprocessor flag to be #defined " +
                           "(note: doesn't yet support " +
                           "#define (flag) (value))>");
        System.err.println("    -startAt <subdir of sourceBase>");
        System.err.println("    -additionalFile <file not in database but " +
                           "which should show up in project file, like " +
                           "includeDB_core>");
        System.err.println("    -additionalGeneratedFile <absolute path to " +
                           "directory containing file; no trailing slash> " +
                           "<name of file generated later in the build process>");
        throw new IllegalArgumentException();
    }


    public void addPerFileLine(Hashtable table,
                               String fileName,
                               String line) {
        Vector v = (Vector) table.get(fileName);
        if (v != null) {
            v.add(line);
        } else {
            v = new Vector();
            v.add(line);
            table.put(fileName, v);
        }
    }

    protected static class PerFileCondData {
        public String releaseString;
        public String debugString;
    }

    protected void addConditionalPerFileLine(Hashtable table,
                                           String fileName,
                                           String releaseLine,
                                           String debugLine) {
        PerFileCondData data = new PerFileCondData();
        data.releaseString = releaseLine;
        data.debugString = debugLine;
        Vector v = (Vector) table.get(fileName);
        if (v != null) {
            v.add(data);
        } else {
            v = new Vector();
            v.add(data);
            table.put(fileName, v);
        }
    }

    protected static class PrelinkCommandData {
      String description;
      String commands;
    }

    protected void addPrelinkCommand(Hashtable table,
                                     String build,
                                     String description,
                                     String commands) {
      PrelinkCommandData data = new PrelinkCommandData();
      data.description = description;
      data.commands = commands;
      table.put(build, data);
    }

    public boolean findString(Vector v, String s) {
        for (Iterator iter = v.iterator(); iter.hasNext(); ) {
            if (((String) iter.next()).equals(s)) {
                return true;
            }
        }

        return false;
    }

    /* This returns a String containing the full path to the passed
       file name, or null if an error occurred. If the file was not
       found or was a duplicate and couldn't be resolved using the
       preferred paths, the file name is added to the appropriate
       Vector of Strings. */
    private String findFileInDirectory(String fileName,
                                       DirectoryTree directory,
                                       Vector preferredPaths,
                                       Vector filesNotFound,
                                       Vector filesDuplicate) {
        List locationsInTree = directory.findFile(fileName);
        int  rootNameLength = directory.getRootNodeName().length();
        String name = null;
        if ((locationsInTree == null) ||
            (locationsInTree.size() == 0)) {
            filesNotFound.add(fileName);
        } else if (locationsInTree.size() > 1) {
            // We shouldn't have duplicate file names in our workspace.
            System.err.println();
            System.err.println("There are multiple files named as: " + fileName);
            System.exit(-1);
            // The following code could be safely removed if we don't need duplicate
            // file names.

            // Iterate through them, trying to find one with a
            // preferred path
        search:
            {
                for (Iterator locIter = locationsInTree.iterator();
                     locIter.hasNext(); ) {
                    DirectoryTreeNode node =
                        (DirectoryTreeNode) locIter.next();
                    String tmpName = node.getName();
                    for (Iterator prefIter = preferredPaths.iterator();
                         prefIter.hasNext(); ) {
                        // We need to make sure the preferred path is
                        // found from the file path not including the root node name.
                        if (tmpName.indexOf((String)prefIter.next(),
                                            rootNameLength) != -1) {
                            name = tmpName;
                            break search;
                        }
                    }
                }
            }

            if (name == null) {
                filesDuplicate.add(fileName);
            }
        } else {
            name = ((DirectoryTreeNode) locationsInTree.get(0)).getName();
        }

        return name;
    }

    protected boolean databaseAllFilesEqual(Database previousDB,
                                            Database currentDB) {
        Iterator i1 = previousDB.getAllFiles().iterator();
        Iterator i2 = currentDB.getAllFiles().iterator();

        while (i1.hasNext() && i2.hasNext()) {
            FileList fl1 = (FileList) i1.next();
            FileList fl2 = (FileList) i2.next();
            if (!fl1.getName().equals(fl2.getName())) {
                return false;
            }
        }

        if (i1.hasNext() != i2.hasNext()) {
            // Different lengths
            return false;
        }

        return true;
    }

    protected String envVarPrefixedFileName(String fileName,
                                            int sourceBaseLen,
                                            DirectoryTree tree,
                                            Vector preferredPaths,
                                            Vector filesNotFound,
                                            Vector filesDuplicate) {
        String fullName = findFileInDirectory(fileName,
                                              tree,
                                              preferredPaths,
                                              filesNotFound,
                                              filesDuplicate);
        return fullName;
    }

     String getProjectName(String fullPath, String extension)
        throws IllegalArgumentException, IOException {
        File file = new File(fullPath).getCanonicalFile();
        fullPath = file.getCanonicalPath();
        String parent = file.getParent();

        if (!fullPath.endsWith(extension)) {
            throw new IllegalArgumentException("project file name \"" +
                                               fullPath +
                                               "\" does not end in "+extension);
        }

        if ((parent != null) &&
            (!fullPath.startsWith(parent))) {
            throw new RuntimeException(
                "Internal error: parent of file name \"" + parent +
                "\" does not match file name \"" + fullPath + "\""
            );
        }

        int len = parent.length();
        if (!parent.endsWith(Util.sep)) {
            len += Util.sep.length();
        }

        int end = fullPath.length() - extension.length();

        if (len == end) {
            throw new RuntimeException(
                "Internal error: file name was empty"
            );
        }

        return fullPath.substring(len, end);
    }

    protected abstract String getProjectExt();

    public void writePlatformSpecificFiles(Database previousDB,
                                           Database currentDB, String[] args)
        throws IllegalArgumentException, IOException {

        parseArguments(args);

        String projectFileName = BuildConfig.getFieldString(null, "ProjectFileName");
        String ext = getProjectExt();

        // Compare contents of allFiles of previousDB and includeDB.
        // If these haven't changed, then skip writing the .vcproj file.
        if (false && databaseAllFilesEqual(previousDB, currentDB) &&
            new File(projectFileName).exists()) {
            System.out.println(
                               "    Databases unchanged; skipping overwrite of "+ext+" file."
                               );
            return;
        }

        String projectName = getProjectName(projectFileName, ext);

        writeProjectFile(projectFileName, projectName, createAllConfigs());
    }

    protected void writePrologue(String[] args) {
        System.err.println("WinGammaPlatform platform-specific arguments:");
        for (int i = 0; i < args.length; i++) {
            System.err.print(args[i] + " ");
        }
        System.err.println();
    }


    void setInclFileTemplate(FileName val) {
        this.inclFileTemplate = val;
    }

    void setGIFileTemplate(FileName val) {
        this.giFileTemplate = val;
    }


    void parseArguments(String[] args) {
        new ArgsParser(args,
                       new ArgRule[]
            {
                new HsArgRule("-sourceBase",
                              "SourceBase",
                              "   (Did you set the HotSpotWorkSpace environment variable?)",
                              HsArgHandler.STRING
                              ),

                new HsArgRule("-buildBase",
                              "BuildBase",
                              "   (Did you set the HotSpotBuildSpace environment variable?)",
                              HsArgHandler.STRING
                              ),

                new HsArgRule("-projectFileName",
                              "ProjectFileName",
                              null,
                              HsArgHandler.STRING
                              ),

                new HsArgRule("-jdkTargetRoot",
                              "JdkTargetRoot",
                              "   (Did you set the HotSpotJDKDist environment variable?)",
                              HsArgHandler.STRING
                              ),

                new HsArgRule("-compiler",
                              "CompilerVersion",
                              "   (Did you set the VcVersion correctly?)",
                              HsArgHandler.STRING
                              ),

                new HsArgRule("-platform",
                              "Platform",
                              null,
                              HsArgHandler.STRING
                              ),

                new HsArgRule("-absoluteInclude",
                              "AbsoluteInclude",
                              null,
                              HsArgHandler.VECTOR
                              ),

                new HsArgRule("-relativeInclude",
                              "RelativeInclude",
                              null,
                              HsArgHandler.VECTOR
                              ),

                new HsArgRule("-define",
                              "Define",
                              null,
                              HsArgHandler.VECTOR
                              ),

                new HsArgRule("-useToGeneratePch",
                              "UseToGeneratePch",
                              null,
                              HsArgHandler.STRING
                              ),

                new ArgRuleSpecific("-perFileLine",
                            new HsArgHandler() {
                                public void handle(ArgIterator it) {
                                    String cfg = getCfg(it.get());
                                    if (nextNotKey(it)) {
                                        String fileName = it.get();
                                        if (nextNotKey(it)) {
                                            String line = it.get();
                                            BuildConfig.putFieldHash(cfg, "PerFileLine", fileName, line);
                                            it.next();
                                            return;
                                        }
                                    }
                                    empty(null, "** Error: wrong number of args to -perFileLine");
                                }
                            }
                            ),

                new ArgRuleSpecific("-conditionalPerFileLine",
                            new HsArgHandler() {
                                public void handle(ArgIterator it) {
                                    String cfg = getCfg(it.get());
                                    if (nextNotKey(it)) {
                                        String fileName = it.get();
                                        if (nextNotKey(it)) {
                                            String productLine = it.get();
                                            if (nextNotKey(it)) {
                                                String debugLine = it.get();
                                                BuildConfig.putFieldHash(cfg+"_debug", "CondPerFileLine",
                                                                         fileName, debugLine);
                                                BuildConfig.putFieldHash(cfg+"_product", "CondPerFileLine",
                                                                         fileName, productLine);
                                                it.next();
                                                return;
                                            }
                                        }
                                    }

                                    empty(null, "** Error: wrong number of args to -conditionalPerFileLine");
                                }
                            }
                            ),

                new HsArgRule("-disablePch",
                              "DisablePch",
                              null,
                              HsArgHandler.HASH
                              ),

                new ArgRule("-startAt",
                            new HsArgHandler() {
                                public void handle(ArgIterator it) {
                                    if (BuildConfig.getField(null, "StartAt") != null) {
                                        empty(null, "** Error: multiple -startAt");
                                    }
                                    if (nextNotKey(it)) {
                                        BuildConfig.putField(null, "StartAt", it.get());
                                        it.next();
                                    } else {
                                        empty("-startAt", null);
                                    }
                                }
                            }
                            ),

                new HsArgRule("-ignoreFile",
                                      "IgnoreFile",
                                      null,
                                      HsArgHandler.HASH
                                      ),

                new HsArgRule("-additionalFile",
                              "AdditionalFile",
                              null,
                              HsArgHandler.VECTOR
                              ),

                new ArgRuleSpecific("-additionalGeneratedFile",
                            new HsArgHandler() {
                                public void handle(ArgIterator it) {
                                    String cfg = getCfg(it.get());
                                    if (nextNotKey(it)) {
                                        String dir = it.get();
                                        if (nextNotKey(it)) {
                                            String fileName = it.get();
                                            // we ignore files that we know are generated, so we coudn't
                                            // find them in sources
                                            BuildConfig.putFieldHash(cfg, "IgnoreFile",  fileName, "1");
                                            BuildConfig.putFieldHash(cfg, "AdditionalGeneratedFile",
                                                                     Util.normalize(dir + Util.sep + fileName),
                                                                     fileName);
                                            it.next();
                                            return;
                                        }
                                    }
                                    empty(null, "** Error: wrong number of args to -additionalGeneratedFile");
                                }
                            }
                            ),

                new HsArgRule("-includeDB",
                              "IncludeDB",
                              null,
                              HsArgHandler.STRING
                              ),

                new ArgRule("-prelink",
                            new HsArgHandler() {
                                public void handle(ArgIterator it) {
                                    if (nextNotKey(it)) {
                                        String build = it.get();
                                        if (nextNotKey(it)) {
                                            String description = it.get();
                                            if (nextNotKey(it)) {
                                                String command = it.get();
                                                BuildConfig.putField(null, "PrelinkDescription", description);
                                                BuildConfig.putField(null, "PrelinkCommand", command);
                                                it.next();
                                                return;
                                            }
                                        }
                                    }

                                    empty(null,  "** Error: wrong number of args to -prelink");
                                }
                            }
                            )
            },
                                       new ArgHandler() {
                                           public void handle(ArgIterator it) {

                                               throw new RuntimeException("Arg Parser: unrecognized option "+it.get());
                                           }
                                       }
                                       );
        if (BuildConfig.getField(null, "SourceBase") == null      ||
            BuildConfig.getField(null, "BuildBase") == null       ||
            BuildConfig.getField(null, "ProjectFileName") == null ||
            BuildConfig.getField(null, "CompilerVersion") == null) {
            usage();
        }

        if (BuildConfig.getField(null, "UseToGeneratePch") == null) {
            throw new RuntimeException("ERROR: need to specify one file to compute PCH, with -useToGeneratePch flag");
        }

        BuildConfig.putField(null, "PlatformObject", this);
    }

    Vector createAllConfigs() {
        Vector allConfigs = new Vector();

        allConfigs.add(new C1DebugConfig());

        boolean b = true;
        if (b) {
            allConfigs.add(new C1FastDebugConfig());
            allConfigs.add(new C1ProductConfig());

            allConfigs.add(new C2DebugConfig());
            allConfigs.add(new C2FastDebugConfig());
            allConfigs.add(new C2ProductConfig());

            allConfigs.add(new TieredDebugConfig());
            allConfigs.add(new TieredFastDebugConfig());
            allConfigs.add(new TieredProductConfig());

            allConfigs.add(new CoreDebugConfig());
            allConfigs.add(new CoreFastDebugConfig());
            allConfigs.add(new CoreProductConfig());

            allConfigs.add(new KernelDebugConfig());
            allConfigs.add(new KernelFastDebugConfig());
            allConfigs.add(new KernelProductConfig());
        }

        return allConfigs;
    }

    class FileAttribute {
        int     numConfigs;
        Vector  configs;
        String  shortName;
        boolean noPch, pchRoot;

        FileAttribute(String shortName, BuildConfig cfg, int numConfigs) {
            this.shortName = shortName;
            this.noPch =  (cfg.lookupHashFieldInContext("DisablePch", shortName) != null);
            this.pchRoot = shortName.equals(BuildConfig.getFieldString(null, "UseToGeneratePch"));
            this.numConfigs = numConfigs;

            configs = new Vector();
            add(cfg.get("Name"));
        }

        void add(String confName) {
            configs.add(confName);

            // if presented in all configs
            if (configs.size() == numConfigs) {
                configs = null;
            }
        }
    }

    class FileInfo implements Comparable {
        String        full;
        FileAttribute attr;

        FileInfo(String full, FileAttribute  attr) {
            this.full = full;
            this.attr = attr;
        }

        public int compareTo(Object o) {
            FileInfo oo = (FileInfo)o;
            // Don't squelch identical short file names where the full
            // paths are different
            if (!attr.shortName.equals(oo.attr.shortName))
              return attr.shortName.compareTo(oo.attr.shortName);
            return full.compareTo(oo.full);
        }

        boolean isHeader() {
            return attr.shortName.endsWith(".h") || attr.shortName.endsWith(".hpp");
        }
    }


    TreeSet sortFiles(Hashtable allFiles) {
        TreeSet rv = new TreeSet();
        Enumeration e = allFiles.keys();
        while (e.hasMoreElements()) {
            String fullPath = (String)e.nextElement();
            rv.add(new FileInfo(fullPath, (FileAttribute)allFiles.get(fullPath)));
        }
        return rv;
    }

    Hashtable computeAttributedFiles(Vector allConfigs) {
        Hashtable ht = new Hashtable();
        int numConfigs = allConfigs.size();

        for (Iterator i = allConfigs.iterator(); i.hasNext(); ) {
            BuildConfig bc = (BuildConfig)i.next();
            Hashtable  confFiles = (Hashtable)bc.getSpecificField("AllFilesHash");
            String confName = bc.get("Name");

            for (Enumeration e=confFiles.keys(); e.hasMoreElements(); ) {
                String filePath = (String)e.nextElement();
                FileAttribute fa = (FileAttribute)ht.get(filePath);

                if (fa == null) {
                    fa = new FileAttribute((String)confFiles.get(filePath), bc, numConfigs);
                    ht.put(filePath, fa);
                } else {
                    fa.add(confName);
                }
            }
        }

        return ht;
    }

     Hashtable computeAttributedFiles(BuildConfig bc) {
        Hashtable ht = new Hashtable();
        Hashtable confFiles = (Hashtable)bc.getSpecificField("AllFilesHash");

        for (Enumeration e = confFiles.keys(); e.hasMoreElements(); ) {
            String filePath = (String)e.nextElement();
            ht.put(filePath,  new FileAttribute((String)confFiles.get(filePath), bc, 1));
        }

        return ht;
    }

    PrintWriter printWriter;

    public void writeProjectFile(String projectFileName, String projectName,
                                 Vector allConfigs) throws IOException {
        throw new RuntimeException("use compiler version specific version");
    }
}
