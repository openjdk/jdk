/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * Accessor for Native calls to get information about File Descriptors.
 * If it's a pipe and its Inode number.
 */
public class LinuxFDInfo {

    static {
        System.loadLibrary("LinuxFDInfo");
    }

    // Maximum file descriptor to probe for being a pipe,
    private final static int MAX_FD = 100;

    // A simple main to print the pipes in this process and their Inode value
    public static void main() {
        for (int fd = 0; fd < MAX_FD; fd++) {
            long inode = getPipeInodeNum(fd);
            if (inode != 0) {
                System.out.printf("fd: %d, inode: 0x%08x\n", fd, inode);
            }
        }
    }

    // Parse the output from main into a long array of the fd, and Inode.
    public static FdAndInode parseFdAndInode(String s) {
        String[] args = s.split(",");
        return new FdAndInode(Integer.parseUnsignedInt(args[0].split(":")[1].trim()),
            Long.parseUnsignedLong(args[1].split(":")[1].trim().substring(2), 16));
    }

    /**
     * Return the inode number for the FD, if it is a pipe.
     * @param fd file descriptor
     * @return the Inode number.
     */
    public static native long getPipeInodeNum(int fd);

    public record FdAndInode(int fd, long inode) {}
}
