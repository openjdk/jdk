import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.TreeSet;
import java.util.UUID;
import java.util.Vector;

public class WinGammaPlatformVC10 extends WinGammaPlatformVC7 {

    @Override
    protected String getProjectExt() {
        return ".vcxproj";
    }

    @Override
    public void writeProjectFile(String projectFileName, String projectName,
            Vector<BuildConfig> allConfigs) throws IOException {
        System.out.println();
        System.out.print("    Writing .vcxproj file: " + projectFileName);

        String projDir = Util.normalize(new File(projectFileName).getParent());

        printWriter = new PrintWriter(projectFileName, "UTF-8");
        printWriter.println("<?xml version=\"1.0\" encoding=\"utf-8\"?>");
        startTag("Project",
                "DefaultTargets", "Build",
                "ToolsVersion", "4.0",
                "xmlns", "http://schemas.microsoft.com/developer/msbuild/2003");
        startTag("ItemGroup",
                "Label", "ProjectConfigurations");
        for (BuildConfig cfg : allConfigs) {
            startTag("ProjectConfiguration",
                    "Include", cfg.get("Name"));
            tagData("Configuration", cfg.get("Id"));
            tagData("Platform", cfg.get("PlatformName"));
            endTag("ProjectConfiguration");
        }
        endTag("ItemGroup");

        startTag("PropertyGroup", "Label", "Globals");
        tagData("ProjectGuid", "{8822CB5C-1C41-41C2-8493-9F6E1994338B}");
        tag("SccProjectName");
        tag("SccLocalPath");
        endTag("PropertyGroup");

        tag("Import", "Project", "$(VCTargetsPath)\\Microsoft.Cpp.Default.props");

        for (BuildConfig cfg : allConfigs) {
            startTag(cfg, "PropertyGroup", "Label", "Configuration");
            tagData("ConfigurationType", "DynamicLibrary");
            tagData("UseOfMfc", "false");
            endTag("PropertyGroup");
        }

        tag("Import", "Project", "$(VCTargetsPath)\\Microsoft.Cpp.props");
        startTag("ImportGroup", "Label", "ExtensionSettings");
        endTag("ImportGroup");
        for (BuildConfig cfg : allConfigs) {
            startTag(cfg, "ImportGroup", "Label", "PropertySheets");
            tag("Import",
                    "Project", "$(UserRootDir)\\Microsoft.Cpp.$(Platform).user.props",
                    "Condition", "exists('$(UserRootDir)\\Microsoft.Cpp.$(Platform).user.props')",
                    "Label", "LocalAppDataPlatform");
            endTag("ImportGroup");
        }

        tag("PropertyGroup", "Label", "UserMacros");

        startTag("PropertyGroup");
        tagData("_ProjectFileVersion", "10.0.30319.1");
        for (BuildConfig cfg : allConfigs) {
            tagData(cfg, "OutDir", cfg.get("OutputDir") + Util.sep);
            tagData(cfg, "IntDir", cfg.get("OutputDir") + Util.sep);
            tagData(cfg, "LinkIncremental", "false");
        }
        for (BuildConfig cfg : allConfigs) {
            tagData(cfg, "CodeAnalysisRuleSet", "AllRules.ruleset");
            tag(cfg, "CodeAnalysisRules");
            tag(cfg, "CodeAnalysisRuleAssemblies");
        }
        endTag("PropertyGroup");

        for (BuildConfig cfg : allConfigs) {
            startTag(cfg, "ItemDefinitionGroup");
            startTag("ClCompile");
            tagV(cfg.getV("CompilerFlags"));
            endTag("ClCompile");

            startTag("Link");
            tagV(cfg.getV("LinkerFlags"));
            endTag("Link");

            startTag("PostBuildEvent");
            tagData("Message", BuildConfig.getFieldString(null, "PostbuildDescription"));
            tagData("Command", cfg.expandFormat(BuildConfig.getFieldString(null, "PostbuildCommand").replace("\t", "\r\n")));
            endTag("PostBuildEvent");

            startTag("PreLinkEvent");
            tagData("Message", BuildConfig.getFieldString(null, "PrelinkDescription"));
            tagData("Command", cfg.expandFormat(BuildConfig.getFieldString(null, "PrelinkCommand").replace("\t", "\r\n")));
            endTag("PreLinkEvent");

            endTag("ItemDefinitionGroup");
        }

        writeFiles(allConfigs, projDir);

        tag("Import", "Project", "$(VCTargetsPath)\\Microsoft.Cpp.targets");
        startTag("ImportGroup", "Label", "ExtensionTargets");
        endTag("ImportGroup");

        endTag("Project");
        printWriter.close();
        System.out.println("    Done.");

        writeFilterFile(projectFileName, projectName, allConfigs, projDir);
        writeUserFile(projectFileName, allConfigs);
    }


    private void writeUserFile(String projectFileName, Vector<BuildConfig> allConfigs) throws FileNotFoundException, UnsupportedEncodingException {
        String userFileName = projectFileName + ".user";
        if (new File(userFileName).exists()) {
            return;
        }
        System.out.print("    Writing .vcxproj.user file: " + userFileName);
        printWriter = new PrintWriter(userFileName, "UTF-8");

        printWriter.println("<?xml version=\"1.0\" encoding=\"utf-8\"?>");
        startTag("Project",
                "ToolsVersion", "4.0",
                "xmlns", "http://schemas.microsoft.com/developer/msbuild/2003");

        for (BuildConfig cfg : allConfigs) {
            startTag(cfg, "PropertyGroup");
            tagData("LocalDebuggerCommand", "$(TargetDir)/hotspot.exe");
            endTag("PropertyGroup");
        }

        endTag("Project");
        printWriter.close();
        System.out.println("    Done.");
    }

    private void writeFilterFile(String projectFileName, String projectName,
            Vector<BuildConfig> allConfigs, String base) throws FileNotFoundException, UnsupportedEncodingException {
        String filterFileName = projectFileName + ".filters";
        System.out.print("    Writing .vcxproj.filters file: " + filterFileName);
        printWriter = new PrintWriter(filterFileName, "UTF-8");

        printWriter.println("<?xml version=\"1.0\" encoding=\"utf-8\"?>");
        startTag("Project",
                "ToolsVersion", "4.0",
                "xmlns", "http://schemas.microsoft.com/developer/msbuild/2003");

        Hashtable<String, FileAttribute> allFiles = computeAttributedFiles(allConfigs);
        TreeSet<FileInfo> sortedFiles = sortFiles(allFiles);
        Vector<NameFilter> filters = makeFilters(sortedFiles);

        // first all filters
        startTag("ItemGroup");
        for (NameFilter filter : filters) {
            doWriteFilter(filter, "");
        }
        startTag("Filter", "Include", "Resource Files");
        UUID uuid = UUID.randomUUID();
        tagData("UniqueIdentifier", "{" + uuid.toString() + "}");
        tagData("Extensions", "ico;cur;bmp;dlg;rc2;rct;bin;cnt;rtf;gif;jpg;jpeg;jpe");
        endTag("Filter");
        endTag("ItemGroup");

        // then all cpp files
        startTag("ItemGroup");
        for (NameFilter filter : filters) {
            doWriteFiles(sortedFiles, filter, "", "ClCompile", new Evaluator() {
                public boolean pick(FileInfo fi) {
                    return fi.isCpp();
                }
            }, base);
        }
        endTag("ItemGroup");

        // then all header files
        startTag("ItemGroup");
        for (NameFilter filter : filters) {
            doWriteFiles(sortedFiles, filter, "", "ClInclude", new Evaluator() {
                public boolean pick(FileInfo fi) {
                    return fi.isHeader();
                }
            }, base);
        }
        endTag("ItemGroup");

        // then all other files
        startTag("ItemGroup");
        for (NameFilter filter : filters) {
            doWriteFiles(sortedFiles, filter, "", "None", new Evaluator() {
                public boolean pick(FileInfo fi) {
                    return true;
                }
            }, base);
        }
        endTag("ItemGroup");

        endTag("Project");
        printWriter.close();
        System.out.println("    Done.");
    }


    private void doWriteFilter(NameFilter filter, String start) {
        startTag("Filter", "Include", start + filter.fname);
        UUID uuid = UUID.randomUUID();
        tagData("UniqueIdentifier", "{" + uuid.toString() + "}");
        endTag("Filter");
        if (filter instanceof ContainerFilter) {
            Iterator i = ((ContainerFilter)filter).babies();
            while (i.hasNext()) {
                doWriteFilter((NameFilter)i.next(), start + filter.fname + "\\");
            }
        }
    }

    interface Evaluator {
        boolean pick(FileInfo fi);
    }

    private void doWriteFiles(TreeSet<FileInfo> allFiles, NameFilter filter, String start, String tool, Evaluator eval, String base) {
        if (filter instanceof ContainerFilter) {
            Iterator i = ((ContainerFilter)filter).babies();
            while (i.hasNext()) {
                doWriteFiles(allFiles, (NameFilter)i.next(), start + filter.fname + "\\", tool, eval, base);
            }
        }
        else {
            Iterator i = allFiles.iterator();
            while (i.hasNext()) {
                FileInfo fi = (FileInfo)i.next();

                if (!filter.match(fi)) {
                    continue;
                }
                if (eval.pick(fi)) {
                    startTag(tool, "Include", rel(fi.full, base));
                    tagData("Filter", start + filter.fname);
                    endTag(tool);

                    // we not gonna look at this file anymore (sic!)
                    i.remove();
                }
            }
        }
    }


    void writeFiles(Vector<BuildConfig> allConfigs, String projDir) {
        Hashtable<String, FileAttribute> allFiles = computeAttributedFiles(allConfigs);
        TreeSet<FileInfo> sortedFiles = sortFiles(allFiles);

        // first cpp-files
        startTag("ItemGroup");
        for (FileInfo fi : sortedFiles) {
            if (!fi.isCpp()) {
                continue;
            }
            writeFile("ClCompile", allConfigs, fi, projDir);
        }
        endTag("ItemGroup");

        // then header-files
        startTag("ItemGroup");
        for (FileInfo fi : sortedFiles) {
            if (!fi.isHeader()) {
                continue;
            }
            writeFile("ClInclude", allConfigs, fi, projDir);
        }
        endTag("ItemGroup");

        // then others
        startTag("ItemGroup");
        for (FileInfo fi : sortedFiles) {
            if (fi.isHeader() || fi.isCpp()) {
                continue;
            }
            writeFile("None", allConfigs, fi, projDir);
        }
        endTag("ItemGroup");
    }

    /**
     * Make "path" into a relative path using "base" as the base.
     *
     * path and base are assumed to be normalized with / as the file separator.
     * returned path uses "\\" as file separator
     */
    private String rel(String path, String base)
    {
        if(!base.endsWith("/")) {
                base += "/";
        }
        String[] pathTok = path.split("/");
        String[] baseTok = base.split("/");
        int pi = 0;
        int bi = 0;
        StringBuilder newPath = new StringBuilder();

        // first step past all path components that are the same
        while (pi < pathTok.length &&
                bi < baseTok.length &&
                pathTok[pi].equals(baseTok[bi])) {
            pi++;
            bi++;
        }

        // for each path component left in base, add "../"
        while (bi < baseTok.length) {
            bi++;
                newPath.append("..\\");
        }

        // now add everything left in path
        while (pi < pathTok.length) {
                newPath.append(pathTok[pi]);
                pi++;
            if (pi != pathTok.length) {
                newPath.append("\\");
            }
        }
        return newPath.toString();
    }

    private void writeFile(String tool, Vector<BuildConfig> allConfigs, FileInfo fi, String base) {
        if (fi.attr.configs == null && fi.attr.pchRoot == false && fi.attr.noPch == false) {
            tag(tool, "Include", rel(fi.full, base));
        }
        else {
            startTag(tool, "Include", rel(fi.full, base));
            for (BuildConfig cfg : allConfigs) {
                if (fi.attr.configs != null && !fi.attr.configs.contains(cfg.get("Name"))) {
                    tagData(cfg, "ExcludedFromBuild", "true");
                }
                if (fi.attr.pchRoot) {
                        tagData(cfg, "PrecompiledHeader", "Create");
                }
                if (fi.attr.noPch) {
                        startTag(cfg, "PrecompiledHeader");
                        endTag("PrecompiledHeader");
                }
            }
            endTag(tool);
        }
    }

    String buildCond(BuildConfig cfg) {
        return "'$(Configuration)|$(Platform)'=='"+cfg.get("Name")+"'";
    }


    void tagV(Vector<String> v) {
        Iterator<String> i = v.iterator();
        while(i.hasNext()) {
            String name = i.next();
            String data = i.next();
            tagData(name, data);
        }
    }

    void tagData(BuildConfig cfg, String name, String data) {
        tagData(name, data, "Condition", buildCond(cfg));
    }

    void tag(BuildConfig cfg, String name, String... attrs) {
        String[] ss = new String[attrs.length + 2];
        ss[0] = "Condition";
        ss[1] = buildCond(cfg);
        System.arraycopy(attrs, 0, ss, 2, attrs.length);

        tag(name, ss);
    }

    void startTag(BuildConfig cfg, String name, String... attrs) {
        String[] ss = new String[attrs.length + 2];
        ss[0] = "Condition";
        ss[1] = buildCond(cfg);
        System.arraycopy(attrs, 0, ss, 2, attrs.length);

        startTag(name, ss);
    }
}

class CompilerInterfaceVC10 extends CompilerInterface {

    @Override
    Vector getBaseCompilerFlags(Vector defines, Vector includes, String outDir) {
        Vector rv = new Vector();

        addAttr(rv, "AdditionalIncludeDirectories", Util.join(";", includes));
        addAttr(rv, "PreprocessorDefinitions",
                Util.join(";", defines).replace("\\\"", "\""));
        addAttr(rv, "PrecompiledHeaderFile", "precompiled.hpp");
        addAttr(rv, "PrecompiledHeaderOutputFile", outDir+Util.sep+"vm.pch");
        addAttr(rv, "AssemblerListingLocation", outDir);
        addAttr(rv, "ObjectFileName", outDir+Util.sep);
        addAttr(rv, "ProgramDataBaseFileName", outDir+Util.sep+"jvm.pdb");
        // Set /nologo option
        addAttr(rv, "SuppressStartupBanner", "true");
        // Surpass the default /Tc or /Tp.
        addAttr(rv, "CompileAs", "Default");
        // Set /W3 option.
        addAttr(rv, "WarningLevel", "Level3");
        // Set /WX option,
        addAttr(rv, "TreatWarningAsError", "true");
        // Set /GS option
        addAttr(rv, "BufferSecurityCheck", "false");
        // Set /Zi option.
        addAttr(rv, "DebugInformationFormat", "ProgramDatabase");
        // Set /Yu option.
        addAttr(rv, "PrecompiledHeader", "Use");
        // Set /EHsc- option
        addAttr(rv, "ExceptionHandling", "");

        addAttr(rv, "MultiProcessorCompilation", "true");

        return rv;
    }

    @Override
    Vector getDebugCompilerFlags(String opt) {
        Vector rv = new Vector();

        // Set /On option
        addAttr(rv, "Optimization", opt);
        // Set /FR option.
        addAttr(rv, "BrowseInformation", "true");
        addAttr(rv, "BrowseInformationFile", "$(IntDir)");
        // Set /MD option.
        addAttr(rv, "RuntimeLibrary", "MultiThreadedDLL");
        // Set /Oy- option
        addAttr(rv, "OmitFramePointers", "false");

        return rv;
    }

    @Override
    Vector getProductCompilerFlags() {
        Vector rv = new Vector();

        // Set /O2 option.
        addAttr(rv, "Optimization", "MaxSpeed");
        // Set /Oy- option
        addAttr(rv, "OmitFramePointers", "false");
        // Set /Ob option.  1 is expandOnlyInline
        addAttr(rv, "InlineFunctionExpansion", "OnlyExplicitInline");
        // Set /GF option.
        addAttr(rv, "StringPooling", "true");
        // Set /MD option. 2 is rtMultiThreadedDLL
        addAttr(rv, "RuntimeLibrary", "MultiThreadedDLL");
        // Set /Gy option
        addAttr(rv, "FunctionLevelLinking", "true");

        return rv;
    }

    @Override
    Vector getBaseLinkerFlags(String outDir, String outDll, String platformName) {
        Vector rv = new Vector();

        addAttr(rv, "AdditionalOptions",
                "/export:JNI_GetDefaultJavaVMInitArgs " +
                "/export:JNI_CreateJavaVM " +
                "/export:JVM_FindClassFromBootLoader "+
                "/export:JNI_GetCreatedJavaVMs "+
                "/export:jio_snprintf /export:jio_printf "+
                "/export:jio_fprintf /export:jio_vfprintf "+
                "/export:jio_vsnprintf "+
                "/export:JVM_GetVersionInfo "+
                "/export:JVM_GetThreadStateNames "+
                "/export:JVM_GetThreadStateValues "+
                "/export:JVM_InitAgentProperties");
        addAttr(rv, "AdditionalDependencies", "kernel32.lib;user32.lib;gdi32.lib;winspool.lib;comdlg32.lib;advapi32.lib;shell32.lib;ole32.lib;oleaut32.lib;uuid.lib;Wsock32.lib;winmm.lib;psapi.lib");
        addAttr(rv, "OutputFile", outDll);
        addAttr(rv, "SuppressStartupBanner", "true");
        addAttr(rv, "ModuleDefinitionFile", outDir+Util.sep+"vm.def");
        addAttr(rv, "ProgramDatabaseFile", outDir+Util.sep+"jvm.pdb");
        addAttr(rv, "SubSystem", "Windows");
        addAttr(rv, "BaseAddress", "0x8000000");
        addAttr(rv, "ImportLibrary", outDir+Util.sep+"jvm.lib");

        if(platformName.equals("Win32")) {
            addAttr(rv, "TargetMachine", "MachineX86");
        } else {
            addAttr(rv, "TargetMachine", "MachineX64");
        }

        // We always want the /DEBUG option to get full symbol information in the pdb files
        addAttr(rv, "GenerateDebugInformation", "true");

        return rv;
    }

    @Override
    Vector getDebugLinkerFlags() {
        Vector rv = new Vector();

        // Empty now that /DEBUG option is used by all configs

        return rv;
    }

    @Override
    Vector getProductLinkerFlags() {
        Vector rv = new Vector();

        // Set /OPT:REF option.
        addAttr(rv, "OptimizeReferences", "true");
        // Set /OPT:ICF option.
        addAttr(rv, "EnableCOMDATFolding", "true");

        return rv;
    }

    @Override
    void getAdditionalNonKernelLinkerFlags(Vector rv) {
        extAttr(rv, "AdditionalOptions", " /export:AsyncGetCallTrace");
    }

    @Override
    String getOptFlag() {
        return "MaxSpeed";
    }

    @Override
    String getNoOptFlag() {
        return "Disabled";
    }

    @Override
    String makeCfgName(String flavourBuild, String platform) {
        return  flavourBuild + "|" + platform;
    }

}
