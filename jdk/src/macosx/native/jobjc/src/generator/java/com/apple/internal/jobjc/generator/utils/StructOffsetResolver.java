/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.apple.internal.jobjc.generator.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.apple.internal.jobjc.generator.Utils;
import com.apple.internal.jobjc.generator.model.Framework;
import com.apple.internal.jobjc.generator.model.Struct;
import com.apple.internal.jobjc.generator.model.types.NType;
import com.apple.internal.jobjc.generator.model.types.TypeCache;
import com.apple.internal.jobjc.generator.model.types.NType.NField;
import com.apple.internal.jobjc.generator.model.types.NType.NStruct;
import com.apple.internal.jobjc.generator.utils.Fp.Map1;
import com.apple.jobjc.JObjCRuntime.Width;
import java.util.Date;

/**
 * Takes a framework, compiles a native source file with all its structs,
 * and figures out their sizes and field offsets.
 */
public class StructOffsetResolver {
    public void resolve(Collection<Framework> fws){
        try {
            _resolve(fws);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected void _resolve(final Collection<Framework> fws) throws Exception{
        for(final Framework fw : fws){
            for(final Width width : Width.values()){
                System.out.println("SOR -- Getting Struct offsets @" + width + " for " + fw.name);
                String nativeSrc = generateFileForFramework(fw, width);
                String executable = compileObjC(nativeSrc, width);
                execute(executable, new Map1<String,Object>(){
                    public Object apply(String ln) {
                        try {
                            processLine(ln, fws, width);
                            return null;
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }
                });
            }
        }
    }

    static Set<String> alwaysHeaders_shared = new TreeSet<String>(Arrays.asList(
            "<Cocoa/Cocoa.h>",
    "\"/System/Library/Frameworks/Carbon.framework/Versions/A/Frameworks/HIToolbox.framework/Versions/A/Headers/HIToolbox.h\""));
    static Map<Width,Set<String>> alwaysHeaders = Fp.litMap(
            Width.W32, alwaysHeaders_shared,
            Width.W64, alwaysHeaders_shared);

    static Set<String> bannedHeaders_shared = new TreeSet<String>(Arrays.asList(
            "NSJavaSetup.h", "IMKInputController.h", "NSSimpleHorizontalTypesetter.h", "NSSpellServer.h", "IMKServer.h", "IKImageBrowserCell.h"));
    static Map<Width,Set<String>> bannedHeaders = Fp.litMap(
            Width.W32, bannedHeaders_shared,
            Width.W64, Fp.appendSet(bannedHeaders_shared,
                    Arrays.asList("npapi.h", "npruntime.h", "npfunctions.h")));

    // We can cache the last accessed framework because, 99% of the time,
    // the caller will ask for the same one, over and over again.
    protected Framework cachedFw;
    protected Framework findFrameworkByName(Collection<Framework> fws, String name){
        if(cachedFw != null && cachedFw.name.equals(name))
            return cachedFw;
        cachedFw = null;
        for(Framework fw : fws)
            if(fw.name.equals(name)){
                cachedFw = fw;
                break;
            }
        return cachedFw;
    }

    protected void processLine(String ln, Collection<Framework> fws, Width arch) throws Exception{
        System.out.println("\tSOR '" + ln + "'");
        if(ln.trim().length() == 0) return;
        Pattern stinfo = Pattern.compile("^(.*) (.*):(\\d+).*$");
        Matcher m = stinfo.matcher(ln);
        if(!m.matches()) throw new RuntimeException("Failed to parse line from exec: " + ln);
        String fwname = m.group(1);
        String stname = m.group(2);
        int stsize = Integer.parseInt(m.group(3));

        Framework fw = findFrameworkByName(fws, fwname);

        Struct st = fw.getStructByName(stname);
        NStruct nst = wget(arch, st.type.type32, st.type.type64);
        nst.sizeof.put(arch, stsize);

//        System.out.println(st.name + " : " + stsize);

        Pattern finfo = Pattern.compile(" (-?\\d+)");
        Matcher fm = finfo.matcher(ln);
        int fi = 0;
        while(fm.find()){
            NField sf = nst.fields.get(fi++);
            sf.offset.put(arch, Integer.parseInt(fm.group(1)));
//            System.out.println("\t" + sf.name + " : " + off);
        }

        TypeCache.inst().pingType(st.type);
    }

    /**
     * Generates Objective-C file and returns absolute path name.
     */
    private String generateFileForFramework(Framework fw, Width arch) throws Exception{
        File tempfile = File.createTempFile("JObjC-SOR-" + fw.name + "-" + arch + "-", ".mm");
        PrintWriter out = new PrintWriter(new FileWriter(tempfile));
        out.println("#include<iostream>");
        printHeaderLines(fw, arch, out);
        out.println("");
        out.println("int main(int argc, char** argv){");
        printStructInfos(fw, arch, out);
        out.println("\treturn 0;");
        out.println("}");
        out.close();
        return tempfile.getAbsolutePath();
    }

    protected void execute(String executable, Fp.Map1<String,Object> lineProcessor) throws Exception {
//        System.out.println(">>>> Executing " + new Date().toString());
        Process p = Runtime.getRuntime().exec(new String[]{executable});

        if(lineProcessor != null){
            BufferedReader stdout = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            while ((line = stdout.readLine()) != null)
                lineProcessor.apply(line);
            stdout.close();
        }
        p.waitFor();
        if(p.exitValue() != 0)
            throw new RuntimeException(executable + " did not execute successfully: " + p.exitValue());
    }

    private static Map<Width,String> gccFlag = Fp.litMap(Width.W32, "-m32", Width.W64, "-m64");

    static boolean isDone(Process p){
        try{
            p.exitValue();
            return true;
        }
        catch(Exception x){
            return false;
        }
    }

    protected static String compileObjC(String nativeSrc, Width arch) throws Exception {
        String execPath = nativeSrc.replace(".mm", "");
        Process p = Runtime.getRuntime().exec(new String[]{
                "llvm-g++", "-Wall", gccFlag.get(arch), "-ObjC++", "-framework", "Foundation", "-o", execPath, nativeSrc
        });
        BufferedReader stdout = new BufferedReader(new InputStreamReader(p.getInputStream()));
        BufferedReader stderr = new BufferedReader(new InputStreamReader(p.getErrorStream()));
        while(!isDone(p)){
            while(stdout.ready()) System.out.println(stdout.readLine());
            while(stderr.ready()) System.out.println(stderr.readLine());
        }
        p.waitFor();
        while(stdout.ready() || stderr.ready()){
            if(stdout.ready()) System.out.println(stdout.readLine());
            if(stderr.ready()) System.out.println(stderr.readLine());
        }
        if(p.exitValue() != 0)
            throw new RuntimeException("gcc did not compile '" + nativeSrc + "' successfully: " + p.exitValue());
        return execPath;
    }

    static void printStructInfos(Framework fw, Width arch, PrintWriter out){
        for(Struct st : fw.structs){
            NStruct nst = wget(arch, st.type.type32, st.type.type64);
            out.println("std::cout << \"" + fw.name + " " + st.name + "\" << ':' << sizeof("+st.name+")");
            for(NField sf : nst.fields){
                out.print("\t<< ' ' << ");
                out.println(sf.type instanceof NType.NBitfield
                          ? "-1"
                          : "offsetof("+st.name+","+sf.name+")");
            }
            out.println("\t<< std::endl;");
        }
    }

    static void printHeaderLines(Framework fw, Width arch, PrintWriter out) throws Exception {
        Collection<String> always = alwaysHeaders.get(arch);
        Collection<String> banned = bannedHeaders.get(arch);
        out.println("#define COREFOUNDATION_CFPLUGINCOM_SEPARATE 0");
        for(String header : always)
            out.println("#import " + header);

        out.println("#undef COREFOUNDATION_CFPLUGINCOM_SEPARATE");
        String umbrella = fw.path + "/Headers/" + fw.name;
        if(new File(umbrella).exists())
            out.println("#import \"" + umbrella + "\"");

        for(File header : getHeaders(fw))
            if(!banned.contains(header.getName()))
                out.println("#import \"" + header.getAbsolutePath() + "\"");
    }

    static <A,B> A wget(Width arch, B x32, B x64){
        switch(arch){
        case W32: return (A) x32;
        case W64: return (A) x64;
        default: throw new RuntimeException();
        }
    }

    /**
     * Gets the absolute path to every header in FOO.framework/Headers
     */
    static Collection<File> getHeaders(Framework fw) throws Exception {
        String hpath = fw.path + "/Headers";
        return Utils.find(new File(hpath), "^.*\\.h$", "");
    }
}
