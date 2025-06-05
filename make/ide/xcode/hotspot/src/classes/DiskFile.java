/*
 * Copyright (c) 2017, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class DiskFile extends LinkedHashMap<Path, DiskFile> implements Comparable<DiskFile> {
    // xcode id ex: D50000000000000000000000
    private static long xcodeIdCount = 0xF0000001;
    private final Path path;
    private final boolean directory;
    private final String xcodeId;
    private final String xcodeId2;
    private Iterable<String> compilerFlags;

    public DiskFile(String path, boolean directory) {
        this(stringToPath(path), directory);
    }

    private DiskFile(Path path, boolean directory) {
        this.path = path;
        this.directory = directory;
        this.compilerFlags = null;
        this.xcodeId = getNextXcodeId();
        this.xcodeId2 = getNextXcodeId();
    }

    private static Path stringToPath(String string) {
        if (string != null) {
            return new File(string).toPath();
        } else {
            return null;
        }
    }

    private static Path clipPath(Path path, String clip) {
        return clipPath(path.toString(), clip);
    }

    private static Path clipPath(String path, String clip) {
        String subpath = path;
        if (path.contains(clip)) {
            subpath = clip;
        }
        int index = path.indexOf(subpath);
        return stringToPath(path.substring(index));
    }

    private String getNextXcodeId() {
        String id = "D5FFFFFF" + Long.toHexString(xcodeIdCount).toUpperCase(Locale.ROOT);
        xcodeIdCount++;

        return id;
    }

    private String getPath() {
        return this.path.toString();
    }

    public boolean isDirectory() {
        return this.directory;
    }

    public void markAsCompiled(List<String> compilerFlags) {
        this.compilerFlags = compilerFlags;
    }

    private boolean isCompiled() {
        return (this.compilerFlags != null);
    }

    public String getXcodeId() {
        return this.xcodeId;
    }

    public String generatePbxSourcesBuildPhase() {
        String string = "";
        if (isCompiled()) {
            String fileName = getFileName();
            string += String.format("        %s /* %s in Sources */,\n", this.xcodeId2, fileName);
        } else if (isDirectory()) {
            for (Map.Entry<Path, DiskFile> entry : entrySet()) {
                DiskFile file = entry.getValue();
                string += file.generatePbxSourcesBuildPhase();
            }
        }
        return string;
    }

    // D5FFFFFFFFFFFFFFF0006506 /* vm_version.cpp in Sources */ = {isa = PBXBuildFile; fileRef = D5FFFFFFFFFFFFFFF0006505 /* vm_version.cpp */; settings = {COMPILER_FLAGS = HEREHERE; }; };
    public String generatePbxBuildFile() {
        String string = "";
        if (isCompiled()) {
            String flagsString = "";
            for (String flag : this.compilerFlags) {
                flagsString += flag.replace("\"", "\\\\\"") + " ";
            }
            String fileName = getFileName();
            string += String.format("    %s /* %s in Sources */ = {isa = PBXBuildFile; fileRef = %s /* %s */; settings = {COMPILER_FLAGS = \"%s\"; }; };\n", this.xcodeId2, fileName, this.xcodeId, fileName, flagsString);
        } else if (isDirectory()) {
            for (Map.Entry<Path, DiskFile> entry : entrySet()) {
                DiskFile file = entry.getValue();
                string += file.generatePbxBuildFile();
            }
        }
        return string;
    }

    public String generatePbxFileReference(String relativePathToRoot) {
        String string = "";
        if (!isDirectory()) {
            String fileName = getFileName();
            String suffix = getFileNameSuffix();
            string += String.format("    %s /* %s */ = {isa = PBXFileReference; fileEncoding = 4; lastKnownFileType = %s%s; name = %s; path = \"%s%s\"; sourceTree = \"<group>\"; };\n", this.xcodeId, fileName, fileName, suffix, fileName, relativePathToRoot, getPath());
        } else if (isDirectory()) {
            for (Map.Entry<Path, DiskFile> entry : entrySet()) {
                DiskFile file = entry.getValue();
                string += file.generatePbxFileReference(relativePathToRoot);
            }
        }
        return string;
    }

    public String generatePbxGroup() {
        String string = String.format("    %s /* %s */ = {\n      isa = PBXGroup;\n      children = (\n", this.xcodeId, getFileName());

        Set<DiskFile> sortedSet = new TreeSet<>(values());

        for (DiskFile file : sortedSet) {
            string += String.format("        %s /* %s */,\n", file.getXcodeId(), file.getFileName());
        }
        string += String.format("      );\n      name = %s;\n      sourceTree = \"<group>\";\n    };\n", getFileName());

        for (DiskFile file : sortedSet) {
            if (file.isDirectory()) {
                string += file.generatePbxGroup();
            }
        }

        return string;
    }

    private ArrayList<DiskFile> getFiles(ArrayList<DiskFile> array) {
        for (Map.Entry<Path, DiskFile> entry : entrySet()) {
            DiskFile file = entry.getValue();
            if (file.isDirectory()) {
                array.add(file);
                array = file.getFiles(array);
            } else {
                array.add(file);
            }
        }
        return array;
    }

    public ArrayList<DiskFile> getFiles() {
        return getFiles(new ArrayList<>());
    }

    public String getFilePath() {
        return this.path.toString();
    }

    private String getFileName() {
        Path fileName = this.path.getFileName();
        if (fileName != null) {
            return fileName.toString();
        } else {
            return this.path.toString();
        }
    }

    private String getFileNameNoSuffix() {
        String string;
        Path fileName = this.path.getFileName();
        if (fileName != null) {
            string = fileName.toString();
            int index = string.indexOf('.');
            if (index >= 0) {
                string = string.substring(0, index);
            }
        } else {
            string = this.path.toString();
        }
        return string;
    }

    private String getFileNameSuffix() {
        String fileName = getFileName();
        int index = fileName.indexOf('.');
        if (index >= 0) {
            return fileName.substring(index);
        } else {
            return "";
        }
    }

    public DiskFile getChild(String fileName) {
        DiskFile child = null;
        for (Map.Entry<Path, DiskFile> entry : entrySet()) {
            DiskFile file = entry.getValue();
            if (file.getFileName().equals(fileName)) {
                child = entry.getValue();
                break;
            } else if (file.isDirectory()) {
                child = file.getChild(fileName);
                if (child != null) {
                    break;
                }
            }
        }
        return child;
    }

    private DiskFile getParent(Path path) {
        Path pathParent = path.getParent();
        DiskFile parent = get(pathParent);
        if (parent == null) {
            if (this.path.equals(pathParent)) {
                parent = this;
            } else {
                parent = getParent(pathParent).get(pathParent);
            }
            parent.putIfAbsent(path, new DiskFile(path, true));
        }
        return parent;
    }

    public void addFile(Path path, String clip) {
        path = clipPath(path, clip);
        DiskFile parent = getParent(path);
        parent.put(path, new DiskFile(path, false));
    }

    public void addDirectory(Path path, String clip) {
        path = clipPath(path, clip);
        DiskFile parent = getParent(path);
        parent.putIfAbsent(path, new DiskFile(path, true));
    }

    @Override
    public int compareTo(DiskFile file) {
        // ".hpp", then ".inline.hpp", then ".cpp"
        int equal = getFileNameNoSuffix().compareTo(file.getFileNameNoSuffix());
        if (equal == 0) {
            String suffix1 = getFileNameSuffix();
            String suffix2 = file.getFileNameSuffix();
            if (!suffix1.equals(".inline.hpp") && !suffix2.equals(".inline.hpp")) {
                // .hpp before .cpp
                equal = -(getFileNameSuffix().compareTo(file.getFileNameSuffix()));
            } else if (suffix1.equals(".inline.hpp") && suffix2.equals(".hpp")) {
                return 1;
            } else if (suffix1.equals(".inline.hpp") && suffix2.equals(".cpp")) {
                return -1;
            } else if (suffix1.equals(".hpp") && suffix2.equals(".inline.hpp")) {
                return -1;
            } else if (suffix1.equals(".cpp") && suffix2.equals(".inline.hpp")) {
                return 1;
            }
        }
        return equal;
    }
}
