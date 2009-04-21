/*
 * Copyright 1999-2005 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */

import java.io.*;
import java.util.*;

public class Database {
  private MacroDefinitions macros;
  // allFiles is kept in lexicographically sorted order. See get().
  private FileList allFiles;
  // files that have implicit dependency on platform files
  // e.g. os.hpp: os_<os_family>.hpp os_<os_arch>.hpp but only
  // recorded if the platform file was seen.
  private FileList platformFiles;
  private FileList outerFiles;
  private FileList indivIncludes;
  private FileList grandInclude; // the results for the grand include file
  private HashMap<String,String> platformDepFiles;
  private long threshold;
  private int nOuterFiles;
  private int nPrecompiledFiles;
  private boolean missingOk;
  private Platform plat;
  /** These allow you to specify files not in the include database
    which are prepended and appended to the file list, allowing
    you to have well-known functions at the start and end of the
    text segment (allows us to find out in a portable fashion
    whether the current PC is in VM code or not upon a crash) */
  private String firstFile;
  private String lastFile;

  public Database(Platform plat, long t) {
    this.plat = plat;
    macros          = new MacroDefinitions();
    allFiles        = new FileList("allFiles", plat);
    platformFiles   = new FileList("platformFiles", plat);
    outerFiles      = new FileList("outerFiles", plat);
    indivIncludes   = new FileList("IndivIncludes", plat);
    grandInclude    = new FileList(plat.getGIFileTemplate().nameOfList(), plat);
    platformDepFiles = new HashMap<String,String>();

    threshold = t;
    nOuterFiles = 0;
    nPrecompiledFiles = 0;
    missingOk = false;
    firstFile = null;
    lastFile = null;
  };

  public FileList getAllFiles() {
    return allFiles;
  }

  public Iterator getMacros() {
    return macros.getMacros();
  }

  public void canBeMissing() {
    missingOk = true;
  }

  public boolean hfileIsInGrandInclude(FileList hfile, FileList cfile) {
    return ((hfile.getCount() >= threshold) && (cfile.getUseGrandInclude()));
  }

  /** These allow you to specify files not in the include database
    which are prepended and appended to the file list, allowing
    you to have well-known functions at the start and end of the
    text segment (allows us to find out in a portable fashion
    whether the current PC is in VM code or not upon a crash) */
  public void setFirstFile(String fileName) {
    firstFile = fileName;
  }

  public void setLastFile(String fileName) {
    lastFile = fileName;
  }

  public void get(String platFileName, String dbFileName)
    throws FileFormatException, IOException, FileNotFoundException {
      macros.readFrom(platFileName, missingOk);

      BufferedReader reader = null;
      try {
        reader = new BufferedReader(new FileReader(dbFileName));
      } catch (FileNotFoundException e) {
        if (missingOk) {
          return;
        } else {
          throw(e);
        }
      }
      System.out.println("\treading database: " + dbFileName);
      String line;
      int lineNo = 0;
      do {
        line = reader.readLine();
        lineNo++;
        if (line != null) {
          StreamTokenizer tokenizer =
            new StreamTokenizer(new StringReader(line));
          tokenizer.slashSlashComments(true);
          tokenizer.wordChars('_', '_');
          tokenizer.wordChars('<', '>');
          // NOTE: if we didn't have to do this line by line,
          // we could trivially recognize C-style comments as
          // well.
          // tokenizer.slashStarComments(true);
          int numTok = 0;
          int res;
          String unexpandedIncluder = null;
          String unexpandedIncludee = null;
          do {
            res = tokenizer.nextToken();
            if (res != StreamTokenizer.TT_EOF) {
              if (numTok == 0) {
                unexpandedIncluder = tokenizer.sval;
              } else if (numTok == 1) {
                unexpandedIncludee = tokenizer.sval;
              } else {
                throw new FileFormatException(
                    "invalid line: \"" + line +
                    "\". Error position: line " + lineNo
                    );
              }
              numTok++;
            }
          } while (res != StreamTokenizer.TT_EOF);

          if ((numTok != 0) && (numTok != 2)) {
            throw new FileFormatException(
                "invalid line: \"" + line +
                "\". Error position: line " + lineNo
                );
          }

          if (numTok == 2) {
            // Non-empty line
            String includer = macros.expand(unexpandedIncluder);
            String includee = macros.expand(unexpandedIncludee);

            if (includee.equals(plat.generatePlatformDependentInclude())) {
              MacroDefinitions localExpander = macros.copy();
              MacroDefinitions localExpander2 = macros.copy();
              localExpander.setAllMacroBodiesTo("pd");
              localExpander2.setAllMacroBodiesTo("");

              // unexpanded_includer e.g. thread_<os_arch>.hpp
              // thread_solaris_i486.hpp -> _thread_pd.hpp.incl

              FileName pdName =
                plat.getInclFileTemplate().copyStem(
                    localExpander.expand(unexpandedIncluder)
                    );

              // derive generic name from platform specific name
              // e.g. os_<arch_os>.hpp => os.hpp. We enforce the
              // restriction (imperfectly) noted in includeDB_core
              // that platform specific files will have an underscore
              // preceding the macro invocation.

              // First expand macro as null string.

              String newIncluder_temp =
                localExpander2.expand(unexpandedIncluder);

              // Now find "_." and remove the underscore.

              String newIncluder = "";

              int len = newIncluder_temp.length();
              int count = 0;

              for ( int i = 0; i < len - 1 ; i++ ) {
                if (newIncluder_temp.charAt(i) == '_' && newIncluder_temp.charAt(i+1) == '.') {
                  count++;
                } else {
                  newIncluder += newIncluder_temp.charAt(i);
                }
              }
              newIncluder += newIncluder_temp.charAt(len-1);

              if (count != 1) {
                throw new FileFormatException(
                    "Unexpected filename format for platform dependent file.\nline: \"" + line +
                    "\".\nError position: line " + lineNo
                    );
              }

              FileList p = allFiles.listForFile(includer);
              p.setPlatformDependentInclude(pdName.dirPreStemSuff());

              // Record the implicit include of this file so that the
              // dependencies for precompiled headers can mention it.
              platformDepFiles.put(newIncluder, includer);

              // Add an implicit dependency on platform
              // specific file for the generic file

              p = platformFiles.listForFile(newIncluder);

              // if this list is empty then this is 1st
              // occurance of a platform dependent file and
              // we need a new version of the include file.
              // Otherwise we just append to the current
              // file.

              PrintWriter pdFile =
                new PrintWriter(
                    new FileWriter(pdName.dirPreStemSuff(),
                      !p.isEmpty())
                    );
              pdFile.println("# include \"" + includer + "\"");
              pdFile.close();

              // Add the platform specific file to the list
              // for this generic file.

              FileList q = allFiles.listForFile(includer);
              p.addIfAbsent(q);
            } else {
              FileList p = allFiles.listForFile(includer);
              if (isOuterFile(includer))
                outerFiles.addIfAbsent(p);

              if (includee.equals(plat.noGrandInclude())) {
                p.setUseGrandInclude(false);
              } else {
                FileList q = allFiles.listForFile(includee);
                p.addIfAbsent(q);
              }
            }
          }
        }
      } while (line != null);
      reader.close();

      // Keep allFiles in well-known order so we can easily determine
      // whether the known files are the same
      allFiles.sortByName();

      // Add first and last files differently to prevent a mistake
      // in ordering in the include databases from breaking the
      // error reporting in the VM.
      if (firstFile != null) {
        FileList p = allFiles.listForFile(firstFile);
        allFiles.setFirstFile(p);
        outerFiles.setFirstFile(p);
      }

      if (lastFile != null) {
        FileList p = allFiles.listForFile(lastFile);
        allFiles.setLastFile(p);
        outerFiles.setLastFile(p);
      }
    }

  public void compute() {
    System.out.println("\tcomputing closures\n");
    // build both indiv and grand results
    for (Iterator iter = outerFiles.iterator(); iter.hasNext(); ) {
      indivIncludes.add(((FileList) iter.next()).doCFile());
      ++nOuterFiles;
    }

    if (!plat.haveGrandInclude())
      return; // nothing in grand include

    // count how many times each include is included & add em to grand
    for (Iterator iter = indivIncludes.iterator(); iter.hasNext(); ) {
      FileList indivInclude = (FileList) iter.next();
      if (!indivInclude.getUseGrandInclude()) {
        continue; // do not bump count if my files cannot be
        // in grand include
      }
      indivInclude.doFiles(grandInclude); // put em on
      // grand_include list
      for (Iterator incListIter = indivInclude.iterator();
          incListIter.hasNext(); ) {
        ((FileList) incListIter.next()).incrementCount();
      }
    }
  }

  // Not sure this is necessary in Java
  public void verify() {
    for (Iterator iter = indivIncludes.iterator(); iter.hasNext(); ) {
      if (iter.next() == null) {
        plat.abort();
      }
    }
  }

  public void put() throws IOException {
    writeIndividualIncludes();

    if (plat.haveGrandInclude())
      writeGrandInclude();

    writeGrandUnixMakefile();
  }

  private void writeIndividualIncludes() throws IOException {
    System.out.println("\twriting individual include files\n");

    for (Iterator iter = indivIncludes.iterator(); iter.hasNext(); ) {
      FileList list = (FileList) iter.next();
      System.out.println("\tcreating " + list.getName());
      list.putInclFile(this);
    }
  }

  private void writeGrandInclude() throws IOException {
    System.out.println("\twriting grand include file\n");
    PrintWriter inclFile =
      new PrintWriter(new FileWriter(plat.getGIFileTemplate().dirPreStemSuff()));
    plat.writeGIPragma(inclFile);
    for (Iterator iter = grandInclude.iterator(); iter.hasNext(); ) {
      FileList list = (FileList) iter.next();
      if (list.getCount() >= threshold) {
        inclFile.println("# include \"" +
            plat.getGIFileTemplate().getInvDir() +
            list.getName() +
            "\"");
        nPrecompiledFiles += 1;
      }
    }
    inclFile.println();
    inclFile.close();
  }

  private void writeGrandUnixMakefile() throws IOException {
    if (!plat.writeDeps())
      return;

    System.out.println("\twriting dependencies file\n");
    PrintWriter gd =
      new PrintWriter(new FileWriter(
            plat.getGDFileTemplate().dirPreStemSuff())
          );
    gd.println("# generated by makeDeps");
    gd.println();


    // HACK ALERT. The compilation of ad_<arch> files is very slow.
    // We want to start compiling them as early as possible. The compilation
    // order on unix is dependent on the order we emit files here.
    // By sorting the output before emitting it, we expect
    // that ad_<arch> will be compiled early.
    boolean shouldSortObjFiles = true;

    if (shouldSortObjFiles) {
      ArrayList sortList = new ArrayList();

      // We need to preserve the ordering of the first and last items
      // in outerFiles.
      int size = outerFiles.size() - 1;
      String firstName = removeSuffixFrom(((FileList)outerFiles.get(0)).getName());
      String lastName = removeSuffixFrom(((FileList)outerFiles.get(size)).getName());

      for (int i=1; i<size; i++) {
        FileList anOuterFile = (FileList)outerFiles.get(i);
        String stemName = removeSuffixFrom(anOuterFile.getName());
        sortList.add(stemName);
      }
      Collections.sort(sortList);

      // write Obj_Files = ...
      gd.println("Obj_Files = \\");
      gd.println(firstName + plat.objFileSuffix() + " \\");
      for (Iterator iter = sortList.iterator(); iter.hasNext(); ) {
        gd.println(iter.next() + plat.objFileSuffix() + " \\");
      }
      gd.println(lastName + plat.objFileSuffix() + " \\");
      gd.println();
      gd.println();
    } else {
      // write Obj_Files = ...
      gd.println("Obj_Files = \\");
      for (Iterator iter = outerFiles.iterator(); iter.hasNext(); ) {
        FileList anOuterFile = (FileList) iter.next();

        String stemName = removeSuffixFrom(anOuterFile.getName());
        gd.println(stemName + plat.objFileSuffix() + " \\");
      }
      gd.println();
      gd.println();
    }

    if (nPrecompiledFiles > 0) {
      // write Precompiled_Files = ...
      gd.println("Precompiled_Files = \\");
      for (Iterator iter = grandInclude.iterator(); iter.hasNext(); ) {
        FileList list = (FileList) iter.next();
        gd.println(list.getName() + " \\");
        String platformDep = platformDepFiles.get(list.getName());
        if (platformDep != null) {
            // make sure changes to the platform dependent file will
            // cause regeneration of the pch file.
            gd.println(platformDep + " \\");
        }
      }
      gd.println();
      gd.println();
    }

    gd.println("DTraced_Files = \\");
    for (Iterator iter = outerFiles.iterator(); iter.hasNext(); ) {
      FileList anOuterFile = (FileList) iter.next();

      if (anOuterFile.hasListForFile("dtrace.hpp")) {
        String stemName = removeSuffixFrom(anOuterFile.getName());
        gd.println(stemName + plat.objFileSuffix() + " \\");
      }
    }
    gd.println();
    gd.println();

    {
      // write each dependency

      for (Iterator iter = indivIncludes.iterator(); iter.hasNext(); ) {

        FileList anII = (FileList) iter.next();

        String stemName = removeSuffixFrom(anII.getName());
        String inclFileName =
          plat.getInclFileTemplate().copyStem(anII.getName()).
          preStemSuff();

        gd.println(stemName + plat.objFileSuffix() + " " +
            stemName + plat.asmFileSuffix() + ": \\");

        printDependentOn(gd, anII.getName());
        // this gets the include file that includes all that
        // this file needs (first level) since nested includes
        // are skipped to avoid cycles.
        printDependentOn(gd, inclFileName);

        if ( plat.haveGrandInclude() ) {
          printDependentOn(gd,
              plat.getGIFileTemplate().preStemSuff());
        }

        for (Iterator iiIter = anII.iterator(); iiIter.hasNext(); ) {
          FileList hfile = (FileList) iiIter.next();
          if (!hfileIsInGrandInclude(hfile, anII) ||
              plat.writeDependenciesOnHFilesFromGI()) {
                printDependentOn(gd, hfile.getName());
          }
          if (platformFiles.hasListForFile(hfile.getName())) {
            FileList p =
              platformFiles.listForFile(hfile.getName());;
            for (Iterator hiIter = p.iterator();
                hiIter.hasNext(); ) {
              FileList hi2 = (FileList) hiIter.next();
              if (!hfileIsInGrandInclude(hi2, p)) {
                printDependentOn(gd, hi2.getName());
              }
            }
          }
        }

        if (plat.includeGIDependencies()
            && nPrecompiledFiles > 0
            && anII.getUseGrandInclude()) {
          gd.println("    $(Precompiled_Files) \\");
        }
        gd.println();
        gd.println();
      }
    }

    gd.close();
  }

  public void putDiffs(Database previous) throws IOException {
    System.out.println("\tupdating output files\n");

    if (!indivIncludes.compareLists(previous.indivIncludes)
        || !grandInclude.compareLists(previous.grandInclude)) {
      System.out.println("The order of .c or .s has changed, or " +
          "the grand include file has changed.");
      put();
      return;
    }

    Iterator curIter = indivIncludes.iterator();
    Iterator prevIter = previous.indivIncludes.iterator();

    try {
      while (curIter.hasNext()) {
        FileList newCFileList = (FileList) curIter.next();
        FileList prevCFileList = (FileList) prevIter.next();
        if (!newCFileList.compareLists(prevCFileList)) {
          System.out.println("\tupdating " + newCFileList.getName());
          newCFileList.putInclFile(this);
        }
      }
    }
    catch (Exception e) {
      throw new InternalError("assertion failure: cur and prev " +
          "database lists changed unexpectedly.");
    }

    writeGrandUnixMakefile();
  }

  private void printDependentOn(PrintWriter gd, String name) {
    gd.print(" ");
    gd.print(plat.dependentPrefix() + name);
  }

  private boolean isOuterFile(String s) {
    int len = s.length();
    String[] suffixes = plat.outerSuffixes();
    for (int i = 0; i < suffixes.length; i++) {
      String suffix = suffixes[i];
      int suffLen = suffix.length();
      if ((len >= suffLen) &&
          (plat.fileNameStringEquality(s.substring(len - suffLen),
                                       suffix))) {
        return true;
      }
    }
    return false;
  }

  private String removeSuffixFrom(String s) {
    int idx = s.lastIndexOf('.');
    if (idx <= 0)
      plat.abort();
    return s.substring(0, idx);
  }
}
