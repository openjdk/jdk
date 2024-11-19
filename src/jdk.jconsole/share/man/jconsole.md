---
# Copyright (c) 2004, 2018, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.
#
# This code is distributed in the hope that it will be useful, but WITHOUT
# ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
# FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
# version 2 for more details (a copy is included in the LICENSE file that
# accompanied this code).
#
# You should have received a copy of the GNU General Public License version
# 2 along with this work; if not, write to the Free Software Foundation,
# Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
#
# Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
# or visit www.oracle.com if you need additional information or have any
# questions.
#

title: 'JCONSOLE(1) JDK @@VERSION_SHORT@@ | JDK Commands'
date: @@COPYRIGHT_YEAR@@
lang: en
---

## Name

jconsole - start a graphical console to monitor and manage Java applications

## Synopsis

`jconsole` \[`-interval=`*n*\] \[`-notile`\] \[`-plugin` *path*\]
\[`-version`\] \[*connection* ... \] \[`-J`*input\_arguments*\]

`jconsole` `-help`

## Options

`-interval`
:   Sets the update interval to `n` seconds (default is 4 seconds).

`-notile`
:   Doesn't tile the windows for two or more connections.

`-pluginpath` *path*
:   Specifies the path that `jconsole` uses to look up plug-ins. The plug-in
    *path* should contain a provider-configuration file named
    `META-INF/services/com.sun.tools.jconsole.JConsolePlugin` that contains one
    line for each plug-in. The line specifies the fully qualified class name of
    the class implementing the `com.sun.tools.jconsole.JConsolePlugin` class.

`-version`
:   Prints the program version.

*connection* = *pid* \| *host*`:`*port* \| *jmxURL*
:   A connection is described by either *pid*, *host*`:`*port* or *jmxURL*.

    -   The *pid* value is the process ID of a target process. The JVM must be
        running with the same user ID as the user ID running the `jconsole`
        command.

    -   The *host*`:`*port* values are the name of the host system on which the
        JVM is running, and the port number specified by the system property
        `com.sun.management.jmxremote.port` when the JVM was started.

    -   The *jmxUrl* value is the address of the JMX agent to be connected to
        as described in JMXServiceURL.

`-J`*input\_arguments*
:   Passes *input\_arguments* to the JVM on which the `jconsole` command is
    run.

`-help` or `--help`
:   Displays the help message for the command.

## Description

The `jconsole` command starts a graphical console tool that lets you monitor
and manage Java applications and virtual machines on a local or remote machine.

On Windows, the `jconsole` command doesn't associate with a console window. It
does, however, display a dialog box with error information when the `jconsole`
command fails.
