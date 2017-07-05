import static java.nio.file.FileVisitResult.CONTINUE;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Stack;
import java.util.Vector;

public class FileTreeCreatorVC7 extends FileTreeCreator {

      public FileTreeCreatorVC7(Path startDir, Vector<BuildConfig> allConfigs, WinGammaPlatform wg) {
         super(startDir, allConfigs, wg);
      }

      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attr) {
         DirAttributes currentFileAttr = attributes.peek().clone();
         boolean usePch = false;
         boolean disablePch = false;
         boolean useIgnore = false;
         String fileName = file.getFileName().toString();

         // usePch applies to all configs for a file.
         if (fileName.equals(BuildConfig.getFieldString(null, "UseToGeneratePch"))) {
            usePch = true;
         }

         for (BuildConfig cfg : allConfigs) {
            if (cfg.lookupHashFieldInContext("IgnoreFile", fileName) != null) {
               useIgnore = true;
               currentFileAttr.setIgnore(cfg);
            } else if (cfg.matchesIgnoredPath(file.toAbsolutePath().toString())) {
               useIgnore = true;
               currentFileAttr.setIgnore(cfg);
            }

            if (cfg.lookupHashFieldInContext("DisablePch", fileName) != null) {
               disablePch = true;
               currentFileAttr.setDisablePch(cfg);
            }

            Vector<String> rv = new Vector<String>();
            cfg.collectRelevantVectors(rv, "AdditionalFile");
            for(String addFile : rv) {
               if (addFile.equals(fileName)) {
                  // supress any ignore
                  currentFileAttr.removeFromIgnored(cfg);
               }
            }
         }

         if (!useIgnore && !disablePch && !usePch) {
            wg.tag("File", new String[] { "RelativePath", vcProjLocation.relativize(file).toString()});
         } else {
            wg.startTag(
                  "File",
                  new String[] { "RelativePath", vcProjLocation.relativize(file).toString()});

            for (BuildConfig cfg : allConfigs) {
               boolean ignore = currentFileAttr.hasIgnore(cfg);
               String [] fileConfAttr;

               if (ignore) {
                  fileConfAttr = new String[] {"Name", cfg.get("Name"), "ExcludedFromBuild", "TRUE" };
               } else {
                  fileConfAttr = new String[] {"Name", cfg.get("Name")};
               }

               if (!disablePch && !usePch && !ignore) {
                  continue;
               } else if (!disablePch && !usePch) {
                  wg.tag("FileConfiguration", fileConfAttr);
               } else {
                  wg.startTag("FileConfiguration", fileConfAttr);
                  if (usePch) {
                     // usePch always applies to all configs, might not always be so.
                     wg.tag("Tool", new String[] {
                           "Name", "VCCLCompilerTool", "UsePrecompiledHeader",
                     "1" });
                     assert(!disablePch);
                  }
                  if (disablePch) {
                     if (currentFileAttr.hasDisablePch(cfg)) {
                        wg.tag("Tool", new String[] {
                              "Name", "VCCLCompilerTool", "UsePrecompiledHeader",
                        "0" });
                     }
                     assert(!usePch);
                  }
                  wg.endTag();
               }
            }
            wg.endTag();
         }

         return CONTINUE;
      }

      @Override
      public FileVisitResult preVisitDirectory(Path path, BasicFileAttributes attrs)
            throws IOException {
         Boolean hide = false;
         DirAttributes newAttr = attributes.peek().clone();

         String rPath;
         if (path.toAbsolutePath().toString().equals(this.startDir.toAbsolutePath().toString())){
            rPath = startDir.toString();
         } else {
            rPath = path.getFileName().toString();
         }

         // check per config ignorePaths!
         for (BuildConfig cfg : allConfigs) {
            if (cfg.matchesIgnoredPath(path.toAbsolutePath().toString())) {
               newAttr.setIgnore(cfg);
            }

            // Hide is always on all configs. And additional files are never hiddden
            if (cfg.matchesHidePath(path.toAbsolutePath().toString())) {
               hide = true;
               break;
            }
         }

         if (!hide) {
            wg.startTag("Filter", new String[] {
                  "Name", rPath});

            attributes.push(newAttr);
            return super.preVisitDirectory(path, attrs);
         } else {
            return FileVisitResult.SKIP_SUBTREE;
         }
      }

      @Override
      public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
         //end matching attributes set by ignorepath
         wg.endTag();
         attributes.pop();

         return CONTINUE;
      }

      @Override
      public FileVisitResult visitFileFailed(Path file, IOException exc) {
         return CONTINUE;
      }

      public void writeFileTree() throws IOException {
         Files.walkFileTree(this.startDir, this);
      }
   }