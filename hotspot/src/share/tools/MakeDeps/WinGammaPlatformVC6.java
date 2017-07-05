/*
 * Copyright (c) 2005, Oracle and/or its affiliates. All rights reserved.
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

public class WinGammaPlatformVC6 extends WinGammaPlatform {
    public void writeProjectFile(String projectFileName, String projectName,
                                 Vector allConfigs) throws IOException {
        Vector allConfigNames = new Vector();

        printWriter = new PrintWriter(new FileWriter(projectFileName));
        String cfg = ((BuildConfig)allConfigs.get(0)).get("Name");

        printWriter.println("# Microsoft Developer Studio Project File - Name=\"" + projectName + "\" - Package Owner=<4>");
        printWriter.println("# Microsoft Developer Studio Generated Build File, Format Version 6.00");
        printWriter.println("# ** DO NOT EDIT **");
        printWriter.println("");
        printWriter.println("# TARGTYPE \"Win32 (x86) Dynamic-Link Library\" 0x0102");
        printWriter.println("CFG=" + cfg);
        printWriter.println("");

        printWriter.println("!MESSAGE This is not a valid makefile. To build this project using NMAKE,");
        printWriter.println("!MESSAGE use the Export Makefile command and run");
        printWriter.println("!MESSAGE ");
        printWriter.println("!MESSAGE NMAKE /f \"" + projectName + ".mak\".");
        printWriter.println("!MESSAGE ");
        printWriter.println("!MESSAGE You can specify a configuration when running NMAKE");
        printWriter.println("!MESSAGE by defining the macro CFG on the command line. For example:");
        printWriter.println("!MESSAGE ");
        printWriter.println("!MESSAGE NMAKE /f \"" + projectName + ".mak\" CFG=\"" + cfg + "\"");
        printWriter.println("!MESSAGE ");
        printWriter.println("!MESSAGE Possible choices for configuration are:");
        printWriter.println("!MESSAGE ");
        for (Iterator i = allConfigs.iterator(); i.hasNext(); ) {
            String name = ((BuildConfig)i.next()).get("Name");
            printWriter.println("!MESSAGE \""+ name + "\" (based on \"Win32 (x86) Dynamic-Link Library\")");
            allConfigNames.add(name);
        }
        printWriter.println("!MESSAGE ");
        printWriter.println("");

        printWriter.println("# Begin Project");
        printWriter.println("# PROP AllowPerConfigDependencies 0");
        printWriter.println("# PROP Scc_ProjName \"\"");
        printWriter.println("# PROP Scc_LocalPath \"\"");
        printWriter.println("CPP=cl.exe");
        printWriter.println("MTL=midl.exe");
        printWriter.println("RSC=rc.exe");


        String keyword = "!IF";
        for (Iterator i = allConfigs.iterator(); i.hasNext(); ) {
            BuildConfig bcfg = (BuildConfig)i.next();
            printWriter.println(keyword + "  \"$(CFG)\" == \"" + bcfg.get("Name") + "\"");
            writeConfigHeader(bcfg);
            keyword = "!ELSEIF";
            if (!i.hasNext()) printWriter.println("!ENDIF");
        }


        TreeSet sortedFiles = sortFiles(computeAttributedFiles(allConfigs));

        printWriter.println("# Begin Target");

        for (Iterator i = allConfigs.iterator(); i.hasNext(); ) {
            printWriter.println("# Name \"" + ((BuildConfig)i.next()).get("Name") + "\"");
        }
        printWriter.println("# Begin Group \"Header Files\"");
        printWriter.println("# PROP Default_Filter \"h;hpp;hxx;hm;inl;fi;fd\"");

        Iterator i = sortedFiles.iterator();

        while (i.hasNext()) {
            FileInfo fi = (FileInfo)i.next();

            // skip sources
            if (!fi.isHeader()) {
                continue;
            }

            printFile(fi, allConfigNames);
        }
        printWriter.println("# End Group");
        printWriter.println("");

        printWriter.println("# Begin Group \"Source Files\"");
        printWriter.println("# PROP Default_Filter \"cpp;c;cxx;rc;def;r;odl;hpj;bat;for;f90\"");

        i = sortedFiles.iterator();
        while (i.hasNext()) {
            FileInfo fi = (FileInfo)i.next();

            // skip headers
            if (fi.isHeader()) {
                continue;
            }

            printFile(fi, allConfigNames);
        }
        printWriter.println("# End Group");
        printWriter.println("");


        printWriter.println("# Begin Group \"Resource Files\"");
        printWriter.println("# PROP Default_Filter \"ico;cur;bmp;dlg;rc2;rct;bin;cnt;rtf;gif;jpg;jpeg;jpe\"");
        printWriter.println("# End Group");
        printWriter.println("");
        printWriter.println("# End Target");

        printWriter.println("# End Project");

        printWriter.close();
    }


    void printFile(FileInfo fi, Vector allConfigNames) {
        printWriter.println("# Begin Source File");
        printWriter.println("");
        printWriter.println("SOURCE=\"" + fi.full + "\"");
        FileAttribute attr = fi.attr;

        if (attr.noPch) {
            printWriter.println("# SUBTRACT CPP /YX /Yc /Yu");
        }

        if (attr.pchRoot) {
            printWriter.println("# ADD CPP /Yc\"incls/_precompiled.incl\"");
        }
        if (attr.configs != null) {
            String keyword = "!IF";
            for (Iterator j=allConfigNames.iterator(); j.hasNext();) {
                String cfg = (String)j.next();
                if (!attr.configs.contains(cfg)) {
                    printWriter.println(keyword+" \"$(CFG)\" == \"" + cfg +"\"");
                    printWriter.println("# PROP BASE Exclude_From_Build 1");
                    printWriter.println("# PROP Exclude_From_Build 1");
                    keyword = "!ELSEIF";
                }
            }
            printWriter.println("!ENDIF");
        }

        printWriter.println("# End Source File");
    }

    void writeConfigHeader(BuildConfig cfg) {
        printWriter.println("# Begin Special Build Tool");
        printWriter.println("SOURCE=\"$(InputPath)\"");
        printWriter.println("PreLink_Desc=" +  BuildConfig.getFieldString(null, "PrelinkDescription"));
        printWriter.println("PreLink_Cmds=" +
                            cfg.expandFormat(BuildConfig.getFieldString(null, "PrelinkCommand")));
        printWriter.println("# End Special Build Tool");
        printWriter.println("");

        for (Iterator i = cfg.getV("CompilerFlags").iterator(); i.hasNext(); ) {
            printWriter.println("# "+(String)i.next());
        }


        printWriter.println("LINK32=link.exe");

        for (Iterator i = cfg.getV("LinkerFlags").iterator(); i.hasNext(); ) {
            printWriter.println("# "+(String)i.next());
        }

        printWriter.println("ADD BASE MTL /nologo /D \"_DEBUG\" /mktyplib203 /win32");
        printWriter.println("ADD MTL /nologo /D \"_DEBUG\" /mktyplib203 /win32");
        printWriter.println("ADD BASE RSC /l 0x409 /d \"_DEBUG\"");
        printWriter.println("ADD RSC /l 0x409 /d \"_DEBUG\"");
        printWriter.println("BSC32=bscmake.exe");
        printWriter.println("ADD BASE BSC32 /nologo");
        printWriter.println("ADD BSC32 /nologo");
        printWriter.println("");
    }

    protected String getProjectExt() {
        return ".dsp";
    }
}


class CompilerInterfaceVC6  extends CompilerInterface {
    Vector getBaseCompilerFlags(Vector defines, Vector includes, String outDir) {
        Vector rv = new Vector();

        rv.add("PROP BASE Use_MFC 0");
        rv.add("PROP Use_MFC 0");
        rv.add("ADD CPP /nologo /MT /W3 /WX /GX /YX /Fr /FD /c");
        rv.add("PROP BASE Output_Dir \""+outDir+"\"");
        rv.add("PROP Output_Dir \""+outDir+"\"");
        rv.add("PROP BASE Intermediate_Dir \""+outDir+"\"");
        rv.add("PROP Intermediate_Dir \""+outDir+"\"");
        rv.add("PROP BASE Target_Dir \"\"");
        rv.add("PROP Target_Dir \"\"");
        rv.add("ADD BASE CPP "+Util.prefixed_join(" /I ", includes, true));
        rv.add("ADD CPP "+Util.prefixed_join(" /I ", includes, true));
        rv.add("ADD BASE CPP "+Util.prefixed_join(" /D ", defines, true));
        rv.add("ADD CPP "+Util.prefixed_join(" /D ", defines, true));
        rv.add("ADD CPP /Yu\"incls/_precompiled.incl\"");

        return rv;
    }

    Vector getBaseLinkerFlags(String outDir, String outDll) {
        Vector rv = new Vector();

        rv.add("PROP Ignore_Export_Lib 0");
        rv.add("ADD BASE CPP /MD");
        rv.add("ADD CPP /MD");
        rv.add("ADD LINK32 kernel32.lib user32.lib gdi32.lib winspool.lib comdlg32.lib " +
               "           advapi32.lib shell32.lib ole32.lib oleaut32.lib winmm.lib");
        rv.add("ADD LINK32      /out:\""+outDll+"\" "+
               "                /nologo /subsystem:windows /machine:I386" +
               "                /nologo /base:\"0x8000000\" /subsystem:windows /dll" +
               "                /export:JNI_GetDefaultJavaVMInitArgs /export:JNI_CreateJavaVM /export:JNI_GetCreatedJavaVMs "+
               "                /export:jio_snprintf /export:jio_printf /export:jio_fprintf /export:jio_vfprintf "+
               "                /export:jio_vsnprintf ");
        rv.add("SUBTRACT LINK32 /pdb:none /map");

        return rv;
    }

    Vector getDebugCompilerFlags(String opt) {
        Vector rv = new Vector();

        rv.add("ADD BASE CPP /Gm /Zi /O"+opt);

        return rv;
    }

    Vector getDebugLinkerFlags() {
        Vector rv = new Vector();

        rv.add("PROP BASE Use_Debug_Libraries 1");
        rv.add("PROP Use_Debug_Libraries 1");
        rv.add("ADD LINK32 /debug");

        return rv;
    }

    Vector getProductCompilerFlags() {
        Vector rv = new Vector();

        rv.add("ADD CPP /O"+getOptFlag());

        return rv;
    }

    Vector getProductLinkerFlags() {
        Vector rv = new Vector();

        rv.add("PROP BASE Use_Debug_Libraries 0");
        rv.add("PROP Use_Debug_Libraries 0");

        return rv;
    }

    String getOptFlag() {
        return "2";
    }

    String getNoOptFlag() {
        return "d";
    }

    String makeCfgName(String flavourBuild) {
        return "vm - "+ Util.os + " " + flavourBuild;
    }
}
