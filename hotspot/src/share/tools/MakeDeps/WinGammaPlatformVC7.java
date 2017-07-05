/*
 * Copyright (c) 2005, 2009, Oracle and/or its affiliates. All rights reserved.
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

public class WinGammaPlatformVC7 extends WinGammaPlatform {

    String projectVersion() {return "7.10";};

    public void writeProjectFile(String projectFileName, String projectName,
                                 Vector allConfigs) throws IOException {
        System.out.println();
        System.out.println("    Writing .vcproj file...");
        // If we got this far without an error, we're safe to actually
        // write the .vcproj file
        printWriter = new PrintWriter(new FileWriter(projectFileName));

        printWriter.println("<?xml version=\"1.0\" encoding=\"windows-1251\"?>");
        startTag(
            "VisualStudioProject",
            new String[] {
                "ProjectType", "Visual C++",
                "Version", projectVersion(),
                "Name", projectName,
                "ProjectGUID", "{8822CB5C-1C41-41C2-8493-9F6E1994338B}",
                "SccProjectName", "",
                "SccLocalPath", ""
            }
            );

        startTag("Platforms", null);
        tag("Platform", new String[] {"Name", Util.os});
        endTag("Platforms");

        startTag("Configurations", null);

        for (Iterator i = allConfigs.iterator(); i.hasNext(); ) {
            writeConfiguration((BuildConfig)i.next());
        }

        endTag("Configurations");

        tag("References", null);

        writeFiles(allConfigs);

        tag("Globals", null);

        endTag("VisualStudioProject");
        printWriter.close();

        System.out.println("    Done.");
    }


    abstract class NameFilter {
        protected String fname;

        abstract boolean match(FileInfo fi);

        String  filterString() { return ""; }
        String name() { return this.fname;}
    }

    class DirectoryFilter extends NameFilter {
        String dir;
        int baseLen, dirLen;

        DirectoryFilter(String dir, String sbase) {
            this.dir = dir;
            this.baseLen = sbase.length();
            this.dirLen = dir.length();
            this.fname = dir;
        }

        DirectoryFilter(String fname, String dir, String sbase) {
            this.dir = dir;
            this.baseLen = sbase.length();
            this.dirLen = dir.length();
            this.fname = fname;
        }


        boolean match(FileInfo fi) {
            return fi.full.regionMatches(true, baseLen, dir, 0, dirLen);
        }
    }

    class TypeFilter extends NameFilter {
        String[] exts;

        TypeFilter(String fname, String[] exts) {
            this.fname = fname;
            this.exts = exts;
        }

        boolean match(FileInfo fi) {
            for (int i=0; i<exts.length; i++) {
                if (fi.full.endsWith(exts[i])) {
                    return true;
                }
            }
            return false;
        }

        String  filterString() {
            return Util.join(";", exts);
        }
    }

    class TerminatorFilter extends NameFilter {
        TerminatorFilter(String fname) {
            this.fname = fname;

        }
        boolean match(FileInfo fi) {
            return true;
        }

    }

    class SpecificNameFilter extends NameFilter {
        String pats[];

        SpecificNameFilter(String fname, String[] pats) {
            this.fname = fname;
            this.pats = pats;
        }

        boolean match(FileInfo fi) {
            for (int i=0; i<pats.length; i++) {
                if (fi.attr.shortName.matches(pats[i])) {
                    return true;
                }
            }
            return false;
        }

    }

    class ContainerFilter extends NameFilter {
        Vector children;

        ContainerFilter(String fname) {
            this.fname = fname;
            children = new Vector();

        }
        boolean match(FileInfo fi) {
            return false;
        }

        Iterator babies() { return children.iterator(); }

        void add(NameFilter f) {
            children.add(f);
        }
    }


    void writeCustomToolConfig(Vector configs, String[] customToolAttrs) {
        for (Iterator i = configs.iterator(); i.hasNext(); ) {
            startTag("FileConfiguration",
                     new String[] {
                         "Name",  (String)i.next()
                     }
                     );
            tag("Tool", customToolAttrs);

            endTag("FileConfiguration");
        }
    }

    // here we define filters, which define layout of what can be seen in 'Solution View' of MSVC
    // Basically there are two types of entities - container filters and real filters
    //   - container filter just provides a container to group together real filters
    //   - real filter can select elements from the set according to some rule, put it into XML
    //     and remove from the list
    Vector makeFilters(TreeSet files) {
        Vector rv = new Vector();
        String sbase = Util.normalize(BuildConfig.getFieldString(null, "SourceBase")+"/src/");

        ContainerFilter rt = new ContainerFilter("Runtime");
        rt.add(new DirectoryFilter("share/vm/prims", sbase));
        rt.add(new DirectoryFilter("share/vm/runtime", sbase));
        rt.add(new DirectoryFilter("share/vm/oops", sbase));
        rv.add(rt);

        ContainerFilter gc = new ContainerFilter("GC");
        gc.add(new DirectoryFilter("share/vm/memory", sbase));
        gc.add(new DirectoryFilter("share/vm/gc_interface", sbase));

        ContainerFilter gc_impl = new ContainerFilter("Implementations");
        gc_impl.add(new DirectoryFilter("CMS",
                                        "share/vm/gc_implementation/concurrentMarkSweep",
                                        sbase));
        gc_impl.add(new DirectoryFilter("Parallel Scavenge",
                                        "share/vm/gc_implementation/parallelScavenge",
                                        sbase));
        gc_impl.add(new DirectoryFilter("Shared",
                                        "share/vm/gc_implementation/shared",
                                        sbase));
        // for all leftovers
        gc_impl.add(new DirectoryFilter("Misc",
                                        "share/vm/gc_implementation",
                                        sbase));

        gc.add(gc_impl);
        rv.add(gc);

        rv.add(new DirectoryFilter("C1", "share/vm/c1", sbase));

        ContainerFilter c2 = new ContainerFilter("C2");
        //c2.add(new DirectoryFilter("share/vm/adlc", sbase));
        c2.add(new DirectoryFilter("share/vm/opto", sbase));
        c2.add(new SpecificNameFilter("Generated", new String[] {"^ad_.+", "^dfa_.+", "^adGlobals.+"}));
        rv.add(c2);

        ContainerFilter comp = new ContainerFilter("Compiler Common");
        comp.add(new DirectoryFilter("share/vm/asm", sbase));
        comp.add(new DirectoryFilter("share/vm/ci", sbase));
        comp.add(new DirectoryFilter("share/vm/code", sbase));
        comp.add(new DirectoryFilter("share/vm/compiler", sbase));
        rv.add(comp);

        rv.add(new DirectoryFilter("Interpreter",
                                   "share/vm/interpreter",
                                   sbase));

        ContainerFilter misc = new ContainerFilter("Misc");
        //misc.add(new DirectoryFilter("share/vm/launch", sbase));
        misc.add(new DirectoryFilter("share/vm/libadt", sbase));
        misc.add(new DirectoryFilter("share/vm/services", sbase));
        misc.add(new DirectoryFilter("share/vm/utilities", sbase));
        rv.add(misc);

        rv.add(new DirectoryFilter("os_cpu", sbase));

        rv.add(new DirectoryFilter("cpu", sbase));

        rv.add(new DirectoryFilter("os", sbase));

        rv.add(new SpecificNameFilter("JVMTI Generated", new String[] {"^jvmti.+"}));

        rv.add(new SpecificNameFilter("C++ Interpreter Generated", new String[] {"^bytecodeInterpreterWithChecks.+"}));

        rv.add(new SpecificNameFilter("Include DBs", new String[] {"^includeDB_.+"}));

        // this one is to catch files not caught by other filters
        //rv.add(new TypeFilter("Header Files", new String[] {"h", "hpp", "hxx", "hm", "inl", "fi", "fd"}));
        rv.add(new TerminatorFilter("Source Files"));

        return rv;
    }

    void writeFiles(Vector allConfigs) {

        Hashtable allFiles = computeAttributedFiles(allConfigs);

        Vector allConfigNames = new Vector();
        for (Iterator i = allConfigs.iterator(); i.hasNext(); ) {
            allConfigNames.add(((BuildConfig)i.next()).get("Name"));
        }

        TreeSet sortedFiles = sortFiles(allFiles);

        startTag("Files", null);

        for (Iterator i = makeFilters(sortedFiles).iterator(); i.hasNext(); ) {
            doWriteFiles(sortedFiles, allConfigNames, (NameFilter)i.next());
        }


        startTag("Filter",
                 new String[] {
                     "Name", "Resource Files",
                     "Filter", "ico;cur;bmp;dlg;rc2;rct;bin;cnt;rtf;gif;jpg;jpeg;jpe"
                 }
                 );
        endTag("Filter");

        endTag("Files");
    }

    void doWriteFiles(TreeSet allFiles, Vector allConfigNames, NameFilter filter) {
        startTag("Filter",
                 new String[] {
                     "Name",   filter.name(),
                     "Filter", filter.filterString()
                 }
                 );

        if (filter instanceof ContainerFilter) {

            Iterator i = ((ContainerFilter)filter).babies();
            while (i.hasNext()) {
                doWriteFiles(allFiles, allConfigNames, (NameFilter)i.next());
            }

        } else {

            Iterator i = allFiles.iterator();
            while (i.hasNext()) {
                FileInfo fi = (FileInfo)i.next();

                if (!filter.match(fi)) {
                    continue;
                }

                startTag("File",
                         new String[] {
                             "RelativePath", fi.full.replace('/', '\\')
                         }
                         );

                FileAttribute a = fi.attr;
                if (a.pchRoot) {
                    writeCustomToolConfig(allConfigNames,
                                          new String[] {
                                              "Name", "VCCLCompilerTool",
                                              "UsePrecompiledHeader", "1"
                                          });
                }

                if (a.noPch) {
                    writeCustomToolConfig(allConfigNames,
                                          new String[] {
                                              "Name", "VCCLCompilerTool",
                                              "UsePrecompiledHeader", "0"
                                          });
                }

                if (a.configs != null) {
                    for (Iterator j=allConfigNames.iterator(); j.hasNext();) {
                        String cfg = (String)j.next();
                        if (!a.configs.contains(cfg)) {
                            startTag("FileConfiguration",
                                     new String[] {
                                         "Name", cfg,
                                         "ExcludedFromBuild", "TRUE"
                                     });
                            tag("Tool", new String[] {"Name", "VCCLCompilerTool"});
                            endTag("FileConfiguration");

                        }
                    }
                }

                endTag("File");

                // we not gonna look at this file anymore
                i.remove();
            }
        }

        endTag("Filter");
    }


    void writeConfiguration(BuildConfig cfg) {
        startTag("Configuration",
                 new String[] {
                     "Name", cfg.get("Name"),
                     "OutputDirectory",  cfg.get("OutputDir"),
                     "IntermediateDirectory",  cfg.get("OutputDir"),
                     "ConfigurationType", "2",
                     "UseOfMFC", "0",
                     "ATLMinimizesCRunTimeLibraryUsage", "FALSE"
                 }
                 );



        tagV("Tool", cfg.getV("CompilerFlags"));

        tag("Tool",
            new String[] {
                "Name", "VCCustomBuildTool"
            }
            );

        tagV("Tool", cfg.getV("LinkerFlags"));

        tag("Tool",
            new String[] {
                "Name", "VCPostBuildEventTool"
            }
            );

        tag("Tool",
            new String[] {
                "Name", "VCPreBuildEventTool"
            }
            );

        tag("Tool",
            new String[] {
                "Name", "VCPreLinkEventTool",
                "Description", BuildConfig.getFieldString(null, "PrelinkDescription"),
                //Caution: String.replace(String,String) is available from JDK5 onwards only
                "CommandLine", cfg.expandFormat(BuildConfig.getFieldString(null, "PrelinkCommand").replace
                   ("\t", "&#x0D;&#x0A;"))
            }
            );

        tag("Tool",
            new String[] {
                "Name", "VCResourceCompilerTool",
                // XXX???
                "PreprocessorDefinitions", "NDEBUG",
                "Culture", "1033"
            }
            );
        tag("Tool",
            new String[] {
              "Name", "VCWebServiceProxyGeneratorTool"
            }
            );

        tag ("Tool",
             new String[] {
              "Name", "VCXMLDataGeneratorTool"
             }
             );

        tag("Tool",
            new String[] {
              "Name", "VCWebDeploymentTool"
            }
            );
        tag("Tool",
             new String[] {
            "Name", "VCManagedWrapperGeneratorTool"
             }
            );
        tag("Tool",
            new String[] {
              "Name", "VCAuxiliaryManagedWrapperGeneratorTool"
            }
            );

        tag("Tool",
            new String[] {
                "Name", "VCMIDLTool",
                "PreprocessorDefinitions", "NDEBUG",
                "MkTypLibCompatible", "TRUE",
                "SuppressStartupBanner", "TRUE",
                "TargetEnvironment", "1",
                "TypeLibraryName", cfg.get("OutputDir") + Util.sep + "vm.tlb",
                "HeaderFileName", ""
            }
            );

        endTag("Configuration");
    }

    int indent;

    private void startTagPrim(String name,
                              String[] attrs,
                              boolean close) {
        doIndent();
        printWriter.print("<"+name);
        indent++;

        if (attrs != null) {
            printWriter.println();
            for (int i=0; i<attrs.length; i+=2) {
                doIndent();
                printWriter.println(" " + attrs[i]+"=\""+attrs[i+1]+"\"");
            }
        }

        if (close) {
            indent--;
            //doIndent();
            printWriter.println("/>");
        } else {
            //doIndent();
            printWriter.println(">");
        }
    }

    void startTag(String name, String[] attrs) {
        startTagPrim(name, attrs, false);
    }

    void startTagV(String name, Vector attrs) {
        String s[] = new String [attrs.size()];
         for (int i=0; i<attrs.size(); i++) {
             s[i] = (String)attrs.elementAt(i);
         }
        startTagPrim(name, s, false);
    }

    void endTag(String name) {
        indent--;
        doIndent();
        printWriter.println("</"+name+">");
    }

    void tag(String name, String[] attrs) {
        startTagPrim(name, attrs, true);
    }

     void tagV(String name, Vector attrs) {
         String s[] = new String [attrs.size()];
         for (int i=0; i<attrs.size(); i++) {
             s[i] = (String)attrs.elementAt(i);
         }
         startTagPrim(name, s, true);
    }


    void doIndent() {
        for (int i=0; i<indent; i++) {
            printWriter.print("    ");
        }
    }

    protected String getProjectExt() {
        return ".vcproj";
    }
}

class CompilerInterfaceVC7 extends CompilerInterface {
    void getBaseCompilerFlags_common(Vector defines, Vector includes, String outDir,Vector rv) {

        // advanced M$ IDE (2003) can only recognize name if it's first or
        // second attribute in the tag - go guess
        addAttr(rv, "Name", "VCCLCompilerTool");
        addAttr(rv, "AdditionalIncludeDirectories", Util.join(",", includes));
        addAttr(rv, "PreprocessorDefinitions",
                                Util.join(";", defines).replace("\"","&quot;"));
        addAttr(rv, "PrecompiledHeaderThrough",
                                "incls"+Util.sep+"_precompiled.incl");
        addAttr(rv, "PrecompiledHeaderFile", outDir+Util.sep+"vm.pch");
        addAttr(rv, "AssemblerListingLocation", outDir);
        addAttr(rv, "ObjectFile", outDir+Util.sep);
        addAttr(rv, "ProgramDataBaseFileName", outDir+Util.sep+"vm.pdb");
        // Set /nologo optin
        addAttr(rv, "SuppressStartupBanner", "TRUE");
        // Surpass the default /Tc or /Tp. 0 is compileAsDefault
        addAttr(rv, "CompileAs", "0");
        // Set /W3 option. 3 is warningLevel_3
        addAttr(rv, "WarningLevel", "3");
        // Set /WX option,
        addAttr(rv, "WarnAsError", "TRUE");
        // Set /GS option
        addAttr(rv, "BufferSecurityCheck", "FALSE");
        // Set /Zi option. 3 is debugEnabled
        addAttr(rv, "DebugInformationFormat", "3");
    }
    Vector getBaseCompilerFlags(Vector defines, Vector includes, String outDir) {
        Vector rv = new Vector();

        getBaseCompilerFlags_common(defines,includes, outDir, rv);
        // Set /Yu option. 3 is pchUseUsingSpecific
        // Note: Starting VC8 pchUseUsingSpecific is 2 !!!
        addAttr(rv, "UsePrecompiledHeader", "3");
        // Set /EHsc- option
        addAttr(rv, "ExceptionHandling", "FALSE");

        return rv;
    }

    Vector getBaseLinkerFlags(String outDir, String outDll) {
        Vector rv = new Vector();

        addAttr(rv, "Name", "VCLinkerTool");
        addAttr(rv, "AdditionalOptions",
                "/export:JNI_GetDefaultJavaVMInitArgs " +
                "/export:JNI_CreateJavaVM " +
                "/export:JNI_GetCreatedJavaVMs "+
                "/export:jio_snprintf /export:jio_printf "+
                "/export:jio_fprintf /export:jio_vfprintf "+
                "/export:jio_vsnprintf ");
        addAttr(rv, "AdditionalDependencies", "Wsock32.lib winmm.lib");
        addAttr(rv, "OutputFile", outDll);
        // Set /INCREMENTAL option. 1 is linkIncrementalNo
        addAttr(rv, "LinkIncremental", "1");
        addAttr(rv, "SuppressStartupBanner", "TRUE");
        addAttr(rv, "ModuleDefinitionFile", outDir+Util.sep+"vm.def");
        addAttr(rv, "ProgramDatabaseFile", outDir+Util.sep+"vm.pdb");
        // Set /SUBSYSTEM option. 2 is subSystemWindows
        addAttr(rv, "SubSystem", "2");
        addAttr(rv, "BaseAddress", "0x8000000");
        addAttr(rv, "ImportLibrary", outDir+Util.sep+"jvm.lib");
        // Set /MACHINE option. 1 is machineX86
        addAttr(rv, "TargetMachine", "1");

        return rv;
    }

    void  getDebugCompilerFlags_common(String opt,Vector rv) {

        // Set /On option
        addAttr(rv, "Optimization", opt);
        // Set /FR option. 1 is brAllInfo
        addAttr(rv, "BrowseInformation", "1");
        addAttr(rv, "BrowseInformationFile", "$(IntDir)" + Util.sep);
        // Set /MD option. 2 is rtMultiThreadedDLL
        addAttr(rv, "RuntimeLibrary", "2");
        // Set /Oy- option
        addAttr(rv, "OmitFramePointers", "FALSE");

    }

    Vector getDebugCompilerFlags(String opt) {
        Vector rv = new Vector();

        getDebugCompilerFlags_common(opt,rv);

        return rv;
    }

    Vector getDebugLinkerFlags() {
        Vector rv = new Vector();

        addAttr(rv, "GenerateDebugInformation", "TRUE"); // == /DEBUG option

        return rv;
    }

    void getProductCompilerFlags_common(Vector rv) {
        // Set /O2 option. 2 is optimizeMaxSpeed
        addAttr(rv, "Optimization", "2");
        // Set /Oy- option
        addAttr(rv, "OmitFramePointers", "FALSE");
    }

    Vector getProductCompilerFlags() {
        Vector rv = new Vector();

        getProductCompilerFlags_common(rv);
        // Set /Ob option.  1 is expandOnlyInline
        addAttr(rv, "InlineFunctionExpansion", "1");
        // Set /GF option.
        addAttr(rv, "StringPooling", "TRUE");
        // Set /MD option. 2 is rtMultiThreadedDLL
        addAttr(rv, "RuntimeLibrary", "2");
        // Set /Gy option
        addAttr(rv, "EnableFunctionLevelLinking", "TRUE");

        return rv;
    }

    Vector getProductLinkerFlags() {
        Vector rv = new Vector();

        // Set /OPT:REF option. 2 is optReferences
        addAttr(rv, "OptimizeReferences", "2");
        // Set /OPT:optFolding option. 2 is optFolding
        addAttr(rv, "EnableCOMDATFolding", "2");

        return rv;
    }

    String getOptFlag() {
        return "2";
    }

    String getNoOptFlag() {
        return "0";
    }

    String makeCfgName(String flavourBuild) {
        return  flavourBuild + "|" + Util.os;
    }
}
