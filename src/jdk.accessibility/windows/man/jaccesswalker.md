---
# Copyright (c) 2017, 2024, Oracle and/or its affiliates. All rights reserved.
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

title: 'JACCESSWALKER(1) JDK @@VERSION_SHORT@@ | JDK Commands'
date: @@COPYRIGHT_YEAR@@
lang: en
---

## Name

jaccesswalker - navigate through the component trees in a particular Java
Virtual Machine and present the hierarchy in a tree view

## Description

You select a node in the tree, and from the **Panels** menu, you select
**Accessibility API Panel**. The `jaccesswalker` tool shows you the
accessibility information for the object in the window.

## Running the jaccesswalker Tool

To use `jaccesswalker`, launch the `jaccesswalker` tool after launching a Java
application. For example, to launch `jaccesswalker`, enter the following
command:

**Note:**

`JAVA_HOME` is an environment variable and should be set to the path of the JDK
or JRE, such as, `c:\Program Files\Java\jdk-10`.

>   `%JAVA_HOME%\bin\jaccesswalker.exe`

You now have two windows open: The Java application window, and the window for
the `jaccesswalker` tool. There are two tasks that you can do with
`jaccesswalker` . You can build a tree view of the Java applications' GUI
hierarchy, and you can query the Java Accessibility API information of a
particular element in the GUI hierarchy.

## Building the GUI Hierarchy

From the **File** menu, select **Refresh Tree** menu. The `jaccesswalker` tool
builds a list of the top-level windows belonging to Java applications. The tool
then recursively queries the elements in those windows, and builds a tree of
all of the GUI components in all of the Java applications in all of the JVMs
running in the system.

## Examining a GUI Component

After a GUI tree is built, you can view detailed accessibility information
about an individual GUI component by selecting it in the tree, then selecting
**Panels**, and then **Display Accessibility Information**.
