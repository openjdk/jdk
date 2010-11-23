/*
 * Copyright (c) 2005, 2010, Oracle and/or its affiliates. All rights reserved.
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

import java.util.*;
import java.io.File;

class BuildConfig {
    Hashtable vars;
    Vector basicNames, basicPaths;
    String[] context;

    static CompilerInterface ci;
    static CompilerInterface getCI() {
        if (ci == null) {
            String comp = (String)getField(null, "CompilerVersion");
            try {
                ci = (CompilerInterface)Class.forName("CompilerInterface" + comp).newInstance();
            } catch (Exception cnfe) {
                System.err.println("Cannot find support for compiler " + comp);
                throw new RuntimeException(cnfe.toString());
            }
        }
        return ci;
    }

    protected void initNames(String flavour, String build, String outDll) {
        if (vars == null) vars = new Hashtable();

        String flavourBuild =  flavour + "_" + build;
        System.out.println();
        System.out.println(flavourBuild);

        put("Name", getCI().makeCfgName(flavourBuild));
        put("Flavour", flavour);
        put("Build", build);

        // ones mentioned above were needed to expand format
        String buildBase = expandFormat(getFieldString(null, "BuildBase"));
        String jdkDir =  getFieldString(null, "JdkTargetRoot");
        String sourceBase = getFieldString(null, "SourceBase");
        String outDir = buildBase;

        put("Id", flavourBuild);
        put("OutputDir", outDir);
        put("SourceBase", sourceBase);
        put("BuildBase", buildBase);
        put("OutputDll", jdkDir + Util.sep + outDll);

        context = new String [] {flavourBuild, flavour, build, null};
    }

    protected void init(Vector includes, Vector defines) {
        initDefaultDefines(defines);
        initDefaultCompilerFlags(includes);
        initDefaultLinkerFlags();
        handleDB();
    }


    protected void initDefaultCompilerFlags(Vector includes) {
        Vector compilerFlags = new Vector();

        compilerFlags.addAll(getCI().getBaseCompilerFlags(getV("Define"),
                                                          includes,
                                                          get("OutputDir")));

        put("CompilerFlags", compilerFlags);
    }

    protected void initDefaultLinkerFlags() {
        Vector linkerFlags = new Vector();

        linkerFlags.addAll(getCI().getBaseLinkerFlags( get("OutputDir"), get("OutputDll")));

        put("LinkerFlags", linkerFlags);
    }

    DirectoryTree getSourceTree(String sourceBase, String startAt) {
        DirectoryTree tree = new DirectoryTree();

        tree.addSubdirToIgnore("Codemgr_wsdata");
        tree.addSubdirToIgnore("deleted_files");
        tree.addSubdirToIgnore("SCCS");
        tree.setVerbose(true);
        if (startAt != null) {
            tree.readDirectory(sourceBase + File.separator + startAt);
        } else {
            tree.readDirectory(sourceBase);
        }

        return tree;
    }


    Vector getPreferredPaths(MacroDefinitions macros) {
        Vector preferredPaths = new Vector();
        // In the case of multiple files with the same name in
        // different subdirectories, prefer the versions specified in
        // the platform file as the "os_family" and "arch" macros.
        for (Iterator iter = macros.getMacros(); iter.hasNext(); ) {
            Macro macro = (Macro) iter.next();
            if (macro.name.equals("os_family") ||
                macro.name.equals("arch")) {
                preferredPaths.add(macro.contents);
            }
        }
        // Also prefer "opto" over "adlc" for adlcVMDeps.hpp
        preferredPaths.add("opto");

        return preferredPaths;
    }


    void handleDB() {
        WinGammaPlatform platform = (WinGammaPlatform)getField(null, "PlatformObject");

        File incls = new File(get("OutputDir")+Util.sep+"incls");

        incls.mkdirs();

        MacroDefinitions macros = new MacroDefinitions();
        try {
            macros.readFrom(getFieldString(null, "Platform"), false);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        putSpecificField("AllFilesHash", computeAllFiles(platform, macros));
    }


    private boolean matchesIgnoredPath(String prefixedName) {
        Vector rv = new Vector();
        collectRelevantVectors(rv, "IgnorePath");
        for (Iterator i = rv.iterator(); i.hasNext(); ) {
            String pathPart = (String) i.next();
            if (prefixedName.contains(Util.normalize(pathPart)))  {
                return true;
            }
        }
        return false;
    }

    void addAll(Iterator i, Hashtable hash,
                WinGammaPlatform platform, DirectoryTree tree,
                Vector preferredPaths, Vector filesNotFound, Vector filesDuplicate) {
        for (; i.hasNext(); ) {
            String fileName = (String) i.next();
            if (lookupHashFieldInContext("IgnoreFile", fileName) == null) {
                String prefixedName = platform.envVarPrefixedFileName(fileName,
                                                                      0, /* ignored */
                                                                      tree,
                                                                      preferredPaths,
                                                                      filesNotFound,
                                                                      filesDuplicate);
                if (prefixedName != null) {
                    prefixedName = Util.normalize(prefixedName);
                    if (!matchesIgnoredPath(prefixedName)) {
                        addTo(hash, prefixedName, fileName);
                    }
                }
            }
        }
    }

    void addTo(Hashtable ht, String key, String value) {
        ht.put(expandFormat(key), expandFormat(value));
    }

    Hashtable computeAllFiles(WinGammaPlatform platform, MacroDefinitions macros) {
        Hashtable rv = new Hashtable();
        DirectoryTree tree = getSourceTree(get("SourceBase"), getFieldString(null, "StartAt"));
        Vector preferredPaths = getPreferredPaths(macros);

        // Hold errors until end
        Vector filesNotFound = new Vector();
        Vector filesDuplicate = new Vector();

        Vector includedFiles = new Vector();

        // find all files
        Vector dirs = getSourceIncludes();
        for (Iterator i = dirs.iterator(); i.hasNext(); ) {
            String dir = (String)i.next();
            DirectoryTree subtree = getSourceTree(dir, null);
            for (Iterator fi = subtree.getFileIterator(); fi.hasNext(); ) {
                String name = ((File)fi.next()).getName();
                includedFiles.add(name);
            }
        }
        addAll(includedFiles.iterator(), rv,
               platform, tree,
               preferredPaths, filesNotFound, filesDuplicate);

        Vector addFiles = new Vector();
        collectRelevantVectors(addFiles, "AdditionalFile");
        addAll(addFiles.iterator(), rv,
               platform, tree,
               preferredPaths, filesNotFound, filesDuplicate);

        collectRelevantHashes(rv, "AdditionalGeneratedFile");

        if ((filesNotFound.size() != 0) ||
            (filesDuplicate.size() != 0)) {
            System.err.println("Error: some files were not found or " +
                               "appeared in multiple subdirectories of " +
                               "directory " + get("SourceBase") + " and could not " +
                               "be resolved with the os_family and arch " +
                               "macros in the platform file.");
            if (filesNotFound.size() != 0) {
                System.err.println("Files not found:");
                for (Iterator iter = filesNotFound.iterator();
                     iter.hasNext(); ) {
                    System.err.println("  " + (String) iter.next());
                }
            }
            if (filesDuplicate.size() != 0) {
                System.err.println("Duplicate files:");
                for (Iterator iter = filesDuplicate.iterator();
                     iter.hasNext(); ) {
                    System.err.println("  " + (String) iter.next());
                }
            }
            throw new RuntimeException();
        }

        return rv;
    }

    void initDefaultDefines(Vector defines) {
        Vector sysDefines = new Vector();
        sysDefines.add("WIN32");
        sysDefines.add("_WINDOWS");
        sysDefines.add("HOTSPOT_BUILD_USER="+System.getProperty("user.name"));
        sysDefines.add("HOTSPOT_BUILD_TARGET=\\\""+get("Build")+"\\\"");
        sysDefines.add("_JNI_IMPLEMENTATION_");
        sysDefines.add("HOTSPOT_LIB_ARCH=\\\"i386\\\"");

        sysDefines.addAll(defines);

        put("Define", sysDefines);
    }

    String get(String key) {
        return (String)vars.get(key);
    }

    Vector getV(String key) {
        return (Vector)vars.get(key);
    }

    Object getO(String key) {
        return vars.get(key);
    }

    Hashtable getH(String key) {
        return (Hashtable)vars.get(key);
    }

    Object getFieldInContext(String field) {
        for (int i=0; i<context.length; i++) {
            Object rv = getField(context[i], field);
            if (rv != null) {
                return rv;
            }
        }
        return null;
    }

    Object lookupHashFieldInContext(String field, String key) {
        for (int i=0; i<context.length; i++) {
            Hashtable ht = (Hashtable)getField(context[i], field);
            if (ht != null) {
                Object rv = ht.get(key);
                if (rv != null) {
                    return rv;
                }
            }
        }
        return null;
    }

    void put(String key, String value) {
        vars.put(key, value);
    }

    void put(String key, Vector vvalue) {
        vars.put(key, vvalue);
    }

    void add(String key, Vector vvalue) {
        getV(key).addAll(vvalue);
    }

    String flavour() {
        return get("Flavour");
    }

    String build() {
        return get("Build");
    }

    Object getSpecificField(String field) {
        return getField(get("Id"), field);
    }

    void putSpecificField(String field, Object value) {
        putField(get("Id"), field, value);
    }

    void collectRelevantVectors(Vector rv, String field) {
        for (int i = 0; i < context.length; i++) {
            Vector v = getFieldVector(context[i], field);
            if (v != null) {
                for (Iterator j=v.iterator(); j.hasNext(); ) {
                    String val = (String)j.next();
                    rv.add(expandFormat(val));
                }
            }
        }
    }

    void collectRelevantHashes(Hashtable rv, String field) {
        for (int i = 0; i < context.length; i++) {
            Hashtable v = (Hashtable)getField(context[i], field);
            if (v != null) {
                for (Enumeration e=v.keys(); e.hasMoreElements(); ) {
                    String key = (String)e.nextElement();
                    String val =  (String)v.get(key);
                    addTo(rv, key, val);
                }
            }
        }
    }


    Vector getDefines() {
        Vector rv = new Vector();
        collectRelevantVectors(rv, "Define");
        return rv;
    }

    Vector getIncludes() {
        Vector rv = new Vector();

        collectRelevantVectors(rv, "AbsoluteInclude");

        rv.addAll(getSourceIncludes());

        return rv;
    }

    private Vector getSourceIncludes() {
        Vector rv = new Vector();
        Vector ri = new Vector();
        String sourceBase = getFieldString(null, "SourceBase");
        collectRelevantVectors(ri, "RelativeInclude");
        for (Iterator i = ri.iterator(); i.hasNext(); ) {
            String f = (String)i.next();
            rv.add(sourceBase + Util.sep + f);
        }
        return rv;
    }

    static Hashtable cfgData = new Hashtable();
    static Hashtable globalData = new Hashtable();

    static boolean appliesToTieredBuild(String cfg) {
        return (cfg != null &&
                (cfg.startsWith("compiler1") ||
                 cfg.startsWith("compiler2")));
    }

    // Filters out the IgnoreFile and IgnorePaths since they are
    // handled specially for tiered builds.
    static boolean appliesToTieredBuild(String cfg, String key) {
        return (appliesToTieredBuild(cfg))&& (key != null && !key.startsWith("Ignore"));
    }

    static String getTieredBuildCfg(String cfg) {
        assert appliesToTieredBuild(cfg) : "illegal configuration " + cfg;
        return "tiered" + cfg.substring(9);
    }

    static Object getField(String cfg, String field) {
        if (cfg == null) {
            return globalData.get(field);
        }

        Hashtable ht =  (Hashtable)cfgData.get(cfg);
        return ht == null ? null : ht.get(field);
    }

    static String getFieldString(String cfg, String field) {
        return (String)getField(cfg, field);
    }

    static Vector getFieldVector(String cfg, String field) {
        return (Vector)getField(cfg, field);
    }

    static void putField(String cfg, String field, Object value) {
        putFieldImpl(cfg, field, value);
        if (appliesToTieredBuild(cfg, field)) {
            putFieldImpl(getTieredBuildCfg(cfg), field, value);
        }
    }

    private static void putFieldImpl(String cfg, String field, Object value) {
        if (cfg == null) {
            globalData.put(field, value);
            return;
        }

        Hashtable ht = (Hashtable)cfgData.get(cfg);
        if (ht == null) {
            ht = new Hashtable();
            cfgData.put(cfg, ht);
        }

        ht.put(field, value);
    }

    static Object getFieldHash(String cfg, String field, String name) {
        Hashtable ht = (Hashtable)getField(cfg, field);

        return ht == null ? null : ht.get(name);
    }

    static void putFieldHash(String cfg, String field, String name, Object val) {
        putFieldHashImpl(cfg, field, name, val);
        if (appliesToTieredBuild(cfg, field)) {
            putFieldHashImpl(getTieredBuildCfg(cfg), field, name, val);
        }
    }

    private static void putFieldHashImpl(String cfg, String field, String name, Object val) {
        Hashtable ht = (Hashtable)getField(cfg, field);

        if (ht == null) {
            ht = new Hashtable();
            putFieldImpl(cfg, field, ht);
        }

        ht.put(name, val);
    }

    static void addFieldVector(String cfg, String field, String element) {
        addFieldVectorImpl(cfg, field, element);
        if (appliesToTieredBuild(cfg, field)) {
            addFieldVectorImpl(getTieredBuildCfg(cfg), field, element);
        }
    }

    private static void addFieldVectorImpl(String cfg, String field, String element) {
        Vector v = (Vector)getField(cfg, field);

        if (v == null) {
            v = new Vector();
            putFieldImpl(cfg, field, v);
        }

        v.add(element);
    }

    String expandFormat(String format) {
        if (format == null) {
            return null;
        }

        if (format.indexOf('%') == -1) {
            return format;
        }

        StringBuffer sb = new StringBuffer();
        int len = format.length();
        for (int i=0; i<len; i++) {
            char ch = format.charAt(i);
            if (ch == '%') {
                char ch1 = format.charAt(i+1);
                switch (ch1) {
                case '%':
                    sb.append(ch1);
                    break;
                case 'b':
                    sb.append(build());
                    break;
                case 'f':
                    sb.append(flavour());
                    break;
                default:
                    sb.append(ch);
                    sb.append(ch1);
                }
                i++;
            } else {
                sb.append(ch);
            }
        }

        return sb.toString();
    }
}

abstract class GenericDebugConfig extends BuildConfig {
    abstract String getOptFlag();

    protected void init(Vector includes, Vector defines) {
        defines.add("_DEBUG");
        defines.add("ASSERT");

        super.init(includes, defines);

        getV("CompilerFlags").addAll(getCI().getDebugCompilerFlags(getOptFlag()));
        getV("LinkerFlags").addAll(getCI().getDebugLinkerFlags());
   }
}

class C1DebugConfig extends GenericDebugConfig {
    String getOptFlag() {
        return getCI().getNoOptFlag();
    }

    C1DebugConfig() {
        initNames("compiler1", "debug", "fastdebug\\jre\\bin\\client\\jvm.dll");
        init(getIncludes(), getDefines());
    }
}

class C1FastDebugConfig extends GenericDebugConfig {
    String getOptFlag() {
        return getCI().getOptFlag();
    }

    C1FastDebugConfig() {
        initNames("compiler1", "fastdebug", "fastdebug\\jre\\bin\\client\\jvm.dll");
        init(getIncludes(), getDefines());
    }
}

class C2DebugConfig extends GenericDebugConfig {
    String getOptFlag() {
        return getCI().getNoOptFlag();
    }

    C2DebugConfig() {
        initNames("compiler2", "debug", "fastdebug\\jre\\bin\\server\\jvm.dll");
        init(getIncludes(), getDefines());
    }
}

class C2FastDebugConfig extends GenericDebugConfig {
    String getOptFlag() {
        return getCI().getOptFlag();
    }

    C2FastDebugConfig() {
        initNames("compiler2", "fastdebug", "fastdebug\\jre\\bin\\server\\jvm.dll");
        init(getIncludes(), getDefines());
    }
}

class TieredDebugConfig extends GenericDebugConfig {
    String getOptFlag() {
        return getCI().getNoOptFlag();
    }

    TieredDebugConfig() {
        initNames("tiered", "debug", "fastdebug\\jre\\bin\\server\\jvm.dll");
        init(getIncludes(), getDefines());
    }
}

class TieredFastDebugConfig extends GenericDebugConfig {
    String getOptFlag() {
        return getCI().getOptFlag();
    }

    TieredFastDebugConfig() {
        initNames("tiered", "fastdebug", "fastdebug\\jre\\bin\\server\\jvm.dll");
        init(getIncludes(), getDefines());
    }
}


abstract class ProductConfig extends BuildConfig {
    protected void init(Vector includes, Vector defines) {
        defines.add("NDEBUG");
        defines.add("PRODUCT");

        super.init(includes, defines);

        getV("CompilerFlags").addAll(getCI().getProductCompilerFlags());
        getV("LinkerFlags").addAll(getCI().getProductLinkerFlags());
    }
}

class C1ProductConfig extends ProductConfig {
    C1ProductConfig() {
        initNames("compiler1", "product", "jre\\bin\\client\\jvm.dll");
        init(getIncludes(), getDefines());
    }
}

class C2ProductConfig extends ProductConfig {
    C2ProductConfig() {
        initNames("compiler2", "product", "jre\\bin\\server\\jvm.dll");
        init(getIncludes(), getDefines());
    }
}

class TieredProductConfig extends ProductConfig {
    TieredProductConfig() {
        initNames("tiered", "product", "jre\\bin\\server\\jvm.dll");
        init(getIncludes(), getDefines());
    }
}


class CoreDebugConfig extends GenericDebugConfig {
    String getOptFlag() {
        return getCI().getNoOptFlag();
    }

    CoreDebugConfig() {
        initNames("core", "debug", "fastdebug\\jre\\bin\\core\\jvm.dll");
        init(getIncludes(), getDefines());
    }
}


class CoreFastDebugConfig extends GenericDebugConfig {
    String getOptFlag() {
        return getCI().getOptFlag();
    }

    CoreFastDebugConfig() {
        initNames("core", "fastdebug", "fastdebug\\jre\\bin\\core\\jvm.dll");
        init(getIncludes(), getDefines());
    }
}


class CoreProductConfig extends ProductConfig {
    CoreProductConfig() {
        initNames("core", "product", "jre\\bin\\core\\jvm.dll");
        init(getIncludes(), getDefines());
    }
}

class KernelDebugConfig extends GenericDebugConfig {
    String getOptFlag() {
        return getCI().getNoOptFlag();
    }

    KernelDebugConfig() {
        initNames("kernel", "debug", "fastdebug\\jre\\bin\\kernel\\jvm.dll");
        init(getIncludes(), getDefines());
    }
}


class KernelFastDebugConfig extends GenericDebugConfig {
    String getOptFlag() {
        return getCI().getOptFlag();
    }

    KernelFastDebugConfig() {
        initNames("kernel", "fastdebug", "fastdebug\\jre\\bin\\kernel\\jvm.dll");
        init(getIncludes(), getDefines());
    }
}


class KernelProductConfig extends ProductConfig {
    KernelProductConfig() {
        initNames("kernel", "product", "jre\\bin\\kernel\\jvm.dll");
        init(getIncludes(), getDefines());
    }
}
abstract class CompilerInterface {
    abstract Vector getBaseCompilerFlags(Vector defines, Vector includes, String outDir);
    abstract Vector getBaseLinkerFlags(String outDir, String outDll);
    abstract Vector getDebugCompilerFlags(String opt);
    abstract Vector getDebugLinkerFlags();
    abstract Vector getProductCompilerFlags();
    abstract Vector getProductLinkerFlags();
    abstract String getOptFlag();
    abstract String getNoOptFlag();
    abstract String makeCfgName(String flavourBuild);

    void addAttr(Vector receiver, String attr, String value) {
        receiver.add(attr); receiver.add(value);
    }
}
