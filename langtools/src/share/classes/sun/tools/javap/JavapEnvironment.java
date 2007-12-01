/*
 * Copyright 2002-2006 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */


package sun.tools.javap;

import java.util.*;
import java.io.*;
import java.util.jar.*;


/**
 * Strores flag values according to command line options
 * and sets path where to find classes.
 *
 * @author  Sucheta Dambalkar
 */
public class JavapEnvironment {

    //Access flags
    public static final int PRIVATE = 0;
    public static final int PROTECTED  = 1;
    public static final int PACKAGE = 2;
    public static final int PUBLIC  = 3;

    //search path flags.
    private static final int start = 0;
    private static final int  cmdboot= 1;
    private static final int sunboot = 2;
    private static final int  javaclass= 3;
    private static final int  cmdextdir= 4;
    private static final int  javaext= 5;
    private static final int  cmdclasspath= 6;
    private static final int  envclasspath= 7;
    private static final int  javaclasspath= 8;
    private static final int  currentdir = 9;


    // JavapEnvironment flag settings
    boolean showLineAndLocal = false;
    int showAccess = PACKAGE;
    boolean showDisassembled = false;
    boolean showVerbose = false;
    boolean showInternalSigs = false;
    String classPathString = null;
    String bootClassPathString = null;
    String extDirsString = null;
    boolean extDirflag = false;
    boolean nothingToDo = true;
    boolean showallAttr = false;
    String classpath = null;
    int searchpath = start;

    /**
     *  According to which flags are set,
     *  returns file input stream for classfile to disassemble.
     */

    public InputStream getFileInputStream(String Name){
        InputStream fileInStream = null;
        searchpath = cmdboot;
        try{
            if(searchpath == cmdboot){
                if(bootClassPathString != null){
                    //search in specified bootclasspath.
                    classpath = bootClassPathString;
                    if((fileInStream = resolvefilename(Name)) != null) return fileInStream;
                    //no classes found in search path.
                    else searchpath = cmdextdir;
                }
                else searchpath = sunboot;
            }

            if(searchpath == sunboot){
                if(System.getProperty("sun.boot.class.path") != null){
                    //search in sun.boot.class.path
                    classpath = System.getProperty("sun.boot.class.path");
                    if((fileInStream = resolvefilename(Name)) != null) return fileInStream;
                    //no classes found in search path
                    else searchpath = cmdextdir;
                }
                else searchpath = javaclass;
            }

            if(searchpath == javaclass){
                if(System.getProperty("java.class.path") != null){
                    //search in java.class.path
                    classpath =System.getProperty("java.class.path");
                    if((fileInStream = resolvefilename(Name)) != null) return fileInStream;
                    //no classes found in search path
                    else searchpath = cmdextdir;
                }
                else searchpath = cmdextdir;
            }

            if(searchpath == cmdextdir){
                if(extDirsString != null){
                    //search in specified extdir.
                    classpath = extDirsString;
                    extDirflag = true;
                    if((fileInStream = resolvefilename(Name)) != null) return fileInStream;
                    //no classes found in search path
                    else {
                        searchpath = cmdclasspath;
                        extDirflag = false;
                    }
                }
                else searchpath = javaext;
            }

            if(searchpath == javaext){
                if(System.getProperty("java.ext.dirs") != null){
                    //search in java.ext.dirs
                    classpath = System.getProperty("java.ext.dirs");
                    extDirflag = true;
                    if((fileInStream = resolvefilename(Name)) != null) return fileInStream;
                    //no classes found in search path
                    else {
                        searchpath = cmdclasspath;
                        extDirflag = false;
                    }
                }
                else searchpath = cmdclasspath;
            }
            if(searchpath == cmdclasspath){
                if(classPathString != null){
                    //search in specified classpath.
                    classpath = classPathString;
                    if((fileInStream = resolvefilename(Name)) != null) return fileInStream;
                    //no classes found in search path
                    else searchpath = 8;
                }
                else searchpath = envclasspath;
            }

            if(searchpath == envclasspath){
                if(System.getProperty("env.class.path")!= null){
                    //search in env.class.path
                    classpath = System.getProperty("env.class.path");
                    if((fileInStream = resolvefilename(Name)) != null) return fileInStream;
                    //no classes found in search path.
                    else searchpath = javaclasspath;
                }
                else searchpath = javaclasspath;
            }

            if(searchpath == javaclasspath){
                if(("application.home") == null){
                    //search in java.class.path
                    classpath = System.getProperty("java.class.path");
                    if((fileInStream = resolvefilename(Name)) != null) return fileInStream;
                    //no classes found in search path.
                    else searchpath = currentdir;
                }
                else searchpath = currentdir;
            }

            if(searchpath == currentdir){
                classpath = ".";
                //search in current dir.
                if((fileInStream = resolvefilename(Name)) != null) return fileInStream;
                else {
                    //no classes found in search path.
                    error("Could not find "+ Name);
                    System.exit(1);
                }
            }
        }catch(SecurityException excsec){
            excsec.printStackTrace();
            error("fatal exception");
        }catch(NullPointerException excnull){
            excnull.printStackTrace();
            error("fatal exception");
        }catch(IllegalArgumentException excill){
            excill.printStackTrace();
            error("fatal exception");
        }

        return null;
    }


    public void error(String msg) {
        System.err.println("ERROR:" +msg);
    }

    /**
     * Resolves file name for classfile to disassemble.
     */
    public InputStream resolvefilename(String name){
        String classname = name.replace('.', '/') + ".class";
        while (true) {
            InputStream instream = extDirflag
                ? resolveExdirFilename(classname)
                : resolveclasspath(classname);
            if (instream != null)
                return instream;
            int lastindex = classname.lastIndexOf('/');
            if (lastindex == -1) return null;
            classname = classname.substring(0, lastindex) + "$" +
                classname.substring(lastindex + 1);
        }
    }

    /**
     * Resolves file name for classfile to disassemble if flag exdir is set.
     */
    public InputStream resolveExdirFilename(String classname){
        if(classpath.indexOf(File.pathSeparator) != -1){
            //separates path
            StringTokenizer st = new StringTokenizer(classpath, File.pathSeparator);
            while(st.hasMoreTokens()){
                String path = st.nextToken();
                InputStream in = resolveExdirFilenamehelper(path, classname);
                if (in != null)
                    return in;
            }
        }else return (resolveExdirFilenamehelper(classpath, classname));

        return null;
    }

    /**
     * Resolves file name for classfile to disassemble.
     */
    public InputStream resolveclasspath(String classname){
        if(classpath.indexOf(File.pathSeparator) != -1){
            StringTokenizer st = new StringTokenizer(classpath, File.pathSeparator);
            //separates path.
            while(st.hasMoreTokens()){
                String path = (st.nextToken()).trim();
                InputStream in = resolveclasspathhelper(path, classname);
                if(in != null) return in;

            }
            return null;
        }
        else return (resolveclasspathhelper(classpath, classname));
    }


    /**
     * Returns file input stream for classfile to disassemble if exdir is set.
     */
    public InputStream resolveExdirFilenamehelper(String path, String classname){
        File fileobj = new File(path);
        if(fileobj.isDirectory()){
            // gets list of files in that directory.
            File[] filelist = fileobj.listFiles();
            for(int i = 0; i < filelist.length; i++){
                try{
                    //file is a jar file.
                    if(filelist[i].toString().endsWith(".jar")){
                        JarFile jfile = new JarFile(filelist[i]);
                        if((jfile.getEntry(classname)) != null){

                            InputStream filein = jfile.getInputStream(jfile.getEntry(classname));
                            int bytearraysize = filein.available();
                            byte []b =  new byte[bytearraysize];
                            int totalread = 0;
                            while(totalread < bytearraysize){
                                totalread += filein.read(b, totalread, bytearraysize-totalread);
                            }
                            InputStream inbyte = new ByteArrayInputStream(b);
                            filein.close();
                            return inbyte;
                        }
                    } else {
                        //not a jar file.
                        String filename = path+"/"+ classname;
                        File file = new File(filename);
                        if(file.isFile()){
                            return (new FileInputStream(file));
                        }
                    }
                }catch(FileNotFoundException fnexce){
                    fnexce.printStackTrace();
                    error("cant read file");
                    error("fatal exception");
                }catch(IOException ioexc){
                    ioexc.printStackTrace();
                    error("fatal exception");
                }
            }
        }

        return null;
    }


    /**
     * Returns file input stream for classfile to disassemble.
     */
    public InputStream resolveclasspathhelper(String path, String classname){
        File fileobj = new File(path);
        try{
            if(fileobj.isDirectory()){
                //is a directory.
                String filename = path+"/"+ classname;
                File file = new File(filename);
                if(file.isFile()){
                    return (new FileInputStream(file));
                }

            }else if(fileobj.isFile()){
                if(fileobj.toString().endsWith(".jar")){
                    //is a jar file.
                    JarFile jfile = new JarFile(fileobj);
                    if((jfile.getEntry(classname)) != null){
                        InputStream filein = jfile.getInputStream(jfile.getEntry(classname));
                        int bytearraysize = filein.available();
                        byte []b =  new byte[bytearraysize];
                        int totalread = 0;
                        while(totalread < bytearraysize){
                                totalread += filein.read(b, totalread, bytearraysize-totalread);
                        }
                        InputStream inbyte = new ByteArrayInputStream(b);
                        filein.close();
                         return inbyte;
                    }
                }
            }
        }catch(FileNotFoundException fnexce){
            fnexce.printStackTrace();
            error("cant read file");
            error("fatal exception");
        }catch(IOException ioexce){
            ioexce.printStackTrace();
            error("fatal exception");
        }
        return null;
    }
}
