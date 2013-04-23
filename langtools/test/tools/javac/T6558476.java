/*
 * Copyright (c) 2008, 2013, Oracle and/or its affiliates. All rights reserved.
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
 */

/*
 * @test
 * @bug 6558476
 * @summary com/sun/tools/javac/Main.compile don't release file handles on return
 * @run main/othervm -Xmx512m -Xms512m  T6558476
 */

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Random;

import com.sun.tools.javac.Main;

public class T6558476 {
    private static File copyFileTo(File file, File directory) throws IOException {
        File newFile = new File(directory, file.getName());
        FileInputStream fis = null;
        FileOutputStream fos = null;
        try {
            fis = new FileInputStream(file);
            fos = new FileOutputStream(newFile);
            byte buff[] = new byte[1024];
            int val;
            while ((val = fis.read(buff)) > 0)
                fos.write(buff, 0, val);
        } finally {
            if (fis != null)
                fis.close();
            if (fos != null)
                fos.close();
        }
        return newFile;
    }

    private static String generateJavaClass(String className) {
        StringBuffer sb = new StringBuffer();
        sb.append("import sun.net.spi.nameservice.dns.DNSNameService;\n");
        sb.append("public class ");
        sb.append(className);
        sb.append(" {\n");
        sb.append("  public void doStuff() {\n");
        sb.append("    DNSNameService dns = null;\n");
        sb.append("  }\n");
        sb.append("}\n");
        return sb.toString();
    }

    public static void main(String[] args) throws IOException {
        File javaHomeDir = new File(System.getProperty("java.home"));
        File outputDir = new File("outputDir" + new Random().nextInt(65536));
        outputDir.mkdir();
        outputDir.deleteOnExit();

        File dnsjarfile = new File(javaHomeDir, "lib" + File.separator + "ext" + File.separator + "dnsns.jar");
        File tmpJar = copyFileTo(dnsjarfile, outputDir);
        String className = "TheJavaFile";
        File javaFile = new File(outputDir, className + ".java");
        javaFile.deleteOnExit();
        FileOutputStream fos = new FileOutputStream(javaFile);
        fos.write(generateJavaClass(className).getBytes());
        fos.close();

        int rc = Main.compile(new String[]{"-d", outputDir.getPath(),
                    "-classpath",
                    tmpJar.getPath(),
                    javaFile.getAbsolutePath()});
        if (rc != 0) {
            throw new Error("Couldn't compile the file (exit code=" + rc + ")");
        }

        if (tmpJar.delete()) {
            System.out.println("jar file successfully deleted");
        } else {
            throw new Error("Error deleting file \"" + tmpJar.getPath() + "\"");
        }
    }
}
