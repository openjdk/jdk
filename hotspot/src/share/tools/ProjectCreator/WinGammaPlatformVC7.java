/*
 * Copyright (c) 2005, 2013, Oracle and/or its affiliates. All rights reserved.
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

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.FileSystems;
import java.util.Vector;

public class WinGammaPlatformVC7 extends WinGammaPlatform {

   // TODO How about moving all globals configs to its own BuildConfig?

   String projectVersion() {
      return "7.10";
   };

   public void writeProjectFile(String projectFileName, String projectName,
         Vector<BuildConfig> allConfigs) throws IOException {
      System.out.println();
      System.out.println("    Writing .vcproj file: " + projectFileName);
      // If we got this far without an error, we're safe to actually
      // write the .vcproj file
      printWriter = new PrintWriter(new FileWriter(projectFileName));

      printWriter
      .println("<?xml version=\"1.0\" encoding=\"windows-1251\"?>");
      startTag("VisualStudioProject", new String[] { "ProjectType",
            "Visual C++", "Version", projectVersion(), "Name", projectName,
            "ProjectGUID", "{8822CB5C-1C41-41C2-8493-9F6E1994338B}",
            "SccProjectName", "", "SccLocalPath", "" });
      startTag("Platforms");
      tag("Platform",
            new String[] { "Name",
            (String) BuildConfig.getField(null, "PlatformName") });
      endTag();

      startTag("Configurations");

      for (BuildConfig cfg : allConfigs) {
         writeConfiguration(cfg);
      }

      endTag();

      tag("References");

      writeFiles(allConfigs);

      tag("Globals");

      endTag();
      printWriter.close();

      System.out.println("    Done.");
   }

   void writeCustomToolConfig(Vector<BuildConfig> configs, String[] customToolAttrs) {
      for (BuildConfig cfg : configs) {
         startTag("FileConfiguration",
               new String[] { "Name", (String) cfg.get("Name") });
         tag("Tool", customToolAttrs);

         endTag();
      }
   }

   void writeFiles(Vector<BuildConfig> allConfigs) {

      // This code assummes there are no config specific includes.
      startTag("Files");
      String sourceBase = BuildConfig.getFieldString(null, "SourceBase");

      // Use first config for all global absolute includes.
      BuildConfig baseConfig = allConfigs.firstElement();
      Vector<String> rv = new Vector<String>();

      // Then use first config for all relative includes
      Vector<String> ri = new Vector<String>();
      baseConfig.collectRelevantVectors(ri, "RelativeSrcInclude");
      for (String f : ri) {
         rv.add(sourceBase + Util.sep + f);
      }

      baseConfig.collectRelevantVectors(rv, "AbsoluteSrcInclude");

      handleIncludes(rv, allConfigs);

      startTag("Filter", new String[] { "Name", "Resource Files", "Filter",
      "ico;cur;bmp;dlg;rc2;rct;bin;cnt;rtf;gif;jpg;jpeg;jpe" });
      endTag();

      endTag();
   }

   // Will visit file tree for each include
   private void handleIncludes(Vector<String> includes, Vector<BuildConfig> allConfigs) {
      for (String path : includes)  {
         FileTreeCreatorVC7 ftc = new FileTreeCreatorVC7(FileSystems.getDefault().getPath(path) , allConfigs, this);
         try {
            ftc.writeFileTree();
         } catch (IOException e) {
            e.printStackTrace();
         }
      }
   }

   void writeConfiguration(BuildConfig cfg) {
      startTag("Configuration", new String[] { "Name", cfg.get("Name"),
            "OutputDirectory", cfg.get("OutputDir"),
            "IntermediateDirectory", cfg.get("OutputDir"),
            "ConfigurationType", "2", "UseOfMFC", "0",
            "ATLMinimizesCRunTimeLibraryUsage", "FALSE" });

      tagV("Tool", cfg.getV("CompilerFlags"));

      tag("Tool", new String[] { "Name", "VCCustomBuildTool" });

      tagV("Tool", cfg.getV("LinkerFlags"));

      String postBuildCmd = BuildConfig.getFieldString(null,
            "PostbuildCommand");
      if (postBuildCmd != null) {
         tag("Tool",
               new String[] {
               "Name",
               "VCPostBuildEventTool",
               "Description",
               BuildConfig
               .getFieldString(null, "PostbuildDescription"),
               // Caution: String.replace(String,String) is available
               // from JDK5 onwards only
               "CommandLine",
                   cfg.expandFormat(postBuildCmd.replace("\t",
                           "&#x0D;&#x0A;")) });
      }

      tag("Tool", new String[] { "Name", "VCPreBuildEventTool" });

      tag("Tool",
            new String[] {
            "Name",
            "VCPreLinkEventTool",
            "Description",
            BuildConfig.getFieldString(null, "PrelinkDescription"),
            // Caution: String.replace(String,String) is available
            // from JDK5 onwards only
            "CommandLine",
            cfg.expandFormat(BuildConfig.getFieldString(null,
                  "PrelinkCommand").replace("\t", "&#x0D;&#x0A;")) });

      tag("Tool", new String[] { "Name", "VCResourceCompilerTool",
            "PreprocessorDefinitions", "NDEBUG", "Culture", "1033" });

      tag("Tool", new String[] { "Name", "VCMIDLTool",
            "PreprocessorDefinitions", "NDEBUG", "MkTypLibCompatible",
            "TRUE", "SuppressStartupBanner", "TRUE", "TargetEnvironment",
            "1", "TypeLibraryName",
            cfg.get("OutputDir") + Util.sep + "vm.tlb", "HeaderFileName",
      "" });

      endTag();
   }



   protected String getProjectExt() {
      return ".vcproj";
   }
}

class CompilerInterfaceVC7 extends CompilerInterface {
   void getBaseCompilerFlags_common(Vector defines, Vector includes,
         String outDir, Vector rv) {

      // advanced M$ IDE (2003) can only recognize name if it's first or
      // second attribute in the tag - go guess
      addAttr(rv, "Name", "VCCLCompilerTool");
      addAttr(rv, "AdditionalIncludeDirectories", Util.join(",", includes));
      addAttr(rv, "PreprocessorDefinitions",
            Util.join(";", defines).replace("\"", "&quot;"));
      addAttr(rv, "PrecompiledHeaderThrough", "precompiled.hpp");
      addAttr(rv, "PrecompiledHeaderFile", outDir + Util.sep + "vm.pch");
      addAttr(rv, "AssemblerListingLocation", outDir);
      addAttr(rv, "ObjectFile", outDir + Util.sep);
      addAttr(rv, "ProgramDataBaseFileName", outDir + Util.sep + "jvm.pdb");
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

      getBaseCompilerFlags_common(defines, includes, outDir, rv);
      // Set /Yu option. 3 is pchUseUsingSpecific
      // Note: Starting VC8 pchUseUsingSpecific is 2 !!!
      addAttr(rv, "UsePrecompiledHeader", "3");
      // Set /EHsc- option
      addAttr(rv, "ExceptionHandling", "FALSE");

      return rv;
   }

   Vector getBaseLinkerFlags(String outDir, String outDll, String platformName) {
      Vector rv = new Vector();

      addAttr(rv, "Name", "VCLinkerTool");
      addAttr(rv, "AdditionalOptions",
            "/export:JNI_GetDefaultJavaVMInitArgs "
                  + "/export:JNI_CreateJavaVM "
                  + "/export:JVM_FindClassFromBootLoader "
                  + "/export:JNI_GetCreatedJavaVMs "
                  + "/export:jio_snprintf /export:jio_printf "
                  + "/export:jio_fprintf /export:jio_vfprintf "
                  + "/export:jio_vsnprintf "
                  + "/export:JVM_GetVersionInfo "
                  + "/export:JVM_GetThreadStateNames "
                  + "/export:JVM_GetThreadStateValues "
                  + "/export:JVM_InitAgentProperties ");
      addAttr(rv, "AdditionalDependencies", "Wsock32.lib winmm.lib");
      addAttr(rv, "OutputFile", outDll);
      // Set /INCREMENTAL option. 1 is linkIncrementalNo
      addAttr(rv, "LinkIncremental", "1");
      addAttr(rv, "SuppressStartupBanner", "TRUE");
      addAttr(rv, "ModuleDefinitionFile", outDir + Util.sep + "vm.def");
      addAttr(rv, "ProgramDatabaseFile", outDir + Util.sep + "jvm.pdb");
      // Set /SUBSYSTEM option. 2 is subSystemWindows
      addAttr(rv, "SubSystem", "2");
      addAttr(rv, "BaseAddress", "0x8000000");
      addAttr(rv, "ImportLibrary", outDir + Util.sep + "jvm.lib");
      if (platformName.equals("Win32")) {
         // Set /MACHINE option. 1 is X86
         addAttr(rv, "TargetMachine", "1");
      } else {
         // Set /MACHINE option. 17 is X64
         addAttr(rv, "TargetMachine", "17");
      }

      return rv;
   }

   void getDebugCompilerFlags_common(String opt, Vector rv) {

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

      getDebugCompilerFlags_common(opt, rv);

      return rv;
   }

   Vector getDebugLinkerFlags() {
      Vector rv = new Vector();

      addAttr(rv, "GenerateDebugInformation", "TRUE"); // == /DEBUG option

      return rv;
   }

   void getAdditionalNonKernelLinkerFlags(Vector rv) {
      extAttr(rv, "AdditionalOptions", "/export:AsyncGetCallTrace ");
   }

   void getProductCompilerFlags_common(Vector rv) {
      // Set /O2 option. 2 is optimizeMaxSpeed
      addAttr(rv, "Optimization", "2");
      // Set /Oy- option
      addAttr(rv, "OmitFramePointers", "FALSE");
      // Set /Ob option. 1 is expandOnlyInline
      addAttr(rv, "InlineFunctionExpansion", "1");
      // Set /GF option.
      addAttr(rv, "StringPooling", "TRUE");
      // Set /MD option. 2 is rtMultiThreadedDLL
      addAttr(rv, "RuntimeLibrary", "2");
      // Set /Gy option
      addAttr(rv, "EnableFunctionLevelLinking", "TRUE");
   }

   Vector getProductCompilerFlags() {
      Vector rv = new Vector();

      getProductCompilerFlags_common(rv);

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

   String makeCfgName(String flavourBuild, String platform) {
      return flavourBuild + "|" + platform;
   }

}
