/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package jdk.jpackage.internal.util;

import java.nio.file.Path;
import java.util.Optional;
import jdk.jpackage.internal.IOUtils;

public final class PathUtils {

    public static String getSuffix(Path path) {
        String filename = replaceSuffix(IOUtils.getFileName(path), null).toString();
        return IOUtils.getFileName(path).toString().substring(filename.length());
    }

    public static Path addSuffix(Path path, String suffix) {
        Path parent = path.getParent();
        String filename = IOUtils.getFileName(path).toString() + suffix;
        return parent != null ? parent.resolve(filename) : Path.of(filename);
    }

    public static Path replaceSuffix(Path path, String suffix) {
        Path parent = path.getParent();
        String filename = IOUtils.getFileName(path).toString().replaceAll("\\.[^.]*$",
                "") + Optional.ofNullable(suffix).orElse("");
        return parent != null ? parent.resolve(filename) : Path.of(filename);
    }

    public static Path resolveNullablePath(Path base, Path path) {
        return Optional.ofNullable(path).map(base::resolve).orElse(null);
    }
}
