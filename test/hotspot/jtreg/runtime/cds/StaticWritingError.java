/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @summary Test the writing error when archive file cannot be created
 * @requires vm.cds
 * @library /test/lib
 * @run driver StaticWritingError
 */

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.UserPrincipal;
import java.util.List;

import jdk.test.lib.cds.CDSOptions;
import jdk.test.lib.cds.CDSTestUtils;
import jdk.test.lib.process.OutputAnalyzer;

public class StaticWritingError {
    public static void main(String[] args) throws Exception {
        String directoryName = "unwritable";
        String archiveName = "staticWritingError.jsa";

        // Create directory that cannot be written to
        if (System.getProperty("os.name").startsWith("Windows")) {
            // Windows filesystem uses Access Control Lists instead of permissions
            Path dir = Files.createTempDirectory(directoryName);
            AclFileAttributeView view = Files.getFileAttributeView(dir, AclFileAttributeView.class);
                UserPrincipal owner = view.getOwner();
                List<AclEntry> acl = view.getAcl();

            // Insert entry to deny WRITE and EXECUTE
            AclEntry entry = AclEntry.newBuilder()
                .setType(AclEntryType.DENY)
                .setPrincipal(owner)
                .setPermissions(AclEntryPermission.WRITE_DATA,
                                AclEntryPermission.EXECUTE)
                .build();
            acl.add(0, entry);
            view.setAcl(acl);
       } else {
            File directory = new File(directoryName);
            directory.mkdir();
            directory.setReadable(false);
            directory.setWritable(false);
       }

        // Perform static dump and attempt to write archive in unwritable directory
        CDSOptions opts = (new CDSOptions())
            .addPrefix("-Xlog:cds")
            .setArchiveName(directoryName + File.separator + archiveName);
        OutputAnalyzer out = CDSTestUtils.createArchive(opts);
        out.shouldHaveExitValue(1);
        out.shouldContain("Unable to create shared archive file");
        out.shouldContain("Encountered error while dumping");
    }
}
