/*
 * Copyright (c) 2019, Red Hat Inc.
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.test.lib;

public class Container {
    // Use this property to specify container command on your system.
    // You may specify either short command or full path.
    // E.g.: "/usr/local/bin/docker", "podman".  We define this constant here so
    // that it can be used in VMProps as well which checks docker support
    // via this command
    public static final String ENGINE_COMMAND =
        System.getProperty("jdk.test.container.command", "docker");

    // Use this property to specify command used to detect the ability to run
    // container testing on a given system. The command will be used by jtreg
    // "at requires" extention.
    // If not specified or empty then container testing will proceed w/o any checks.
    // Default value is "<ENGINE_COMMAND> ps" as in "docker ps".
    public static final String CONTAINER_REQUIRES_COMMAND =
        System.getProperty("jdk.test.container.requires.command", ENGINE_COMMAND + " ps");
}
