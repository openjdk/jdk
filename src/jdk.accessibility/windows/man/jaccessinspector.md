---
# Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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

title: 'JACCESSINSPECTOR(1) JDK @@VERSION_SHORT@@ | JDK Commands'
date: @@COPYRIGHT_YEAR@@
lang: en
---

## Name

jaccessinspector - examine accessible information about the objects in the
Java Virtual Machine using the Java Accessibility Utilities API

## Description

The `jaccessinspector` tool lets you select different methods for examining the
object accessibility information:

-   When events occur such as a change of focus, mouse movement, property
    change, menu selection, and the display of a popup menu

-   When you press the F1 key when the mouse is over an object, or F2 when the
    mouse is over a window

After an object has been selected for examination, the `jaccessinspector` tool
displays the results of calling Java Accessibility API methods on that object.

## Running the jaccessinspector Tool

To use the `jaccessinspector` tool, launch the `jaccessinspector` tool after
launching a Java application. To launch `jaccessinspector`, run the following
command:

**Note:**

`JAVA_HOME` is an environment variable and should be set to the path of the JDK
or JRE, such as `c:\Program Files\Java\jdk-10`.

>   `%JAVA_HOME%\bin\jaccessinspector.exe`

You now have two windows open: The Java application window and the
`jaccessinspector` window. The `jaccessinspector` window contains five menus:

-   [File Menu]

-   [UpdateSettings Menu]

-   [JavaEvents Menu]

-   [AccessibilityEvents Menu]

-   [Options Menu]

The items in **UpdateSettings**, **JavaEvents**, and **AccessibilityEvents**
menus let you query Java applications in a variety of ways.

## File Menu

This section describes the **File** menu items.

AccessBridge DLL Loaded
:   Enables and disables AccessBridge DLL Loaded.

Exit
:   Exits from the tool.

## UpdateSettings Menu

This section describes the **UpdateSettings** menu items.

Update from Mouse
:   Determines the x- and y-coordinates of the mouse (assuming the
    `jaccessinspector` tool window is topmost) when the mouse has stopped
    moving, and then queries the Java application for the accessible object
    underneath the mouse, dumping the output into the `jaccessinspector`
    window.

Update with F2 (Mouse HWND)
:   Determines the x- and y-coordinates of the mouse (assuming the
    `jaccessinspector` tool window is topmost), and then queries the Java
    application for the accessible object of the HWND underneath the mouse,
    dumping the output into the `jaccessinspector` window.

Update with F1 (Mouse Point)
:   Determines the x- and y-coordinates of the mouse (assuming the
    `jaccessinspector` tool window is topmost), and then queries the Java
    application for the accessible object underneath the cursor, dumping the
    output into the `jaccessinspector` window.

## JavaEvents Menu

This section describes the **JavaEvents** menu items.

Track Mouse Events
:   Registers with the Java application all Java Mouse Entered events, and upon
    receiving one, queries the object that was entered by the cursor and dumps
    the output into the `jaccessinspector` window.

    **Note:** If the mouse is moved quickly, then there may be some delay
    before the displayed information is updated.

Track Focus Events
:   Registers with the Java application all Java Focus Gained events, and upon
    receiving an event, queries the object that received the focus and dumps
    the output into the `jaccessinspector` window.

Track Caret Events
:   Register with the Java application all Java Caret Update events, and upon
    receiving an event, queries the object in which the caret was updated, and
    dumps the output into the `jaccessinspector` window.

    **Note:** Because objects that contain carets are almost by definition
    objects that are rich text objects, this won't seem as responsive as the
    other event tracking options. In real use, one would make fewer
    accessibility calls in Caret Update situations (for example, just get the
    new letter, word, sentence at the caret location), which would be
    significantly faster.

Track Menu Selected \| Deselected \| Cancelled Events
:   Registers with the Java application all Menu events, and upon receiving an
    event, queries the object in which the caret was updated, and dumps the
    output into the `jaccessinspector` window.

Track Popup Visible \| Invisible \| Cancelled Events
:   Registers with the Java application all Popup Menu events, and upon
    receiving an event, queries the object in which the caret was updated, and
    dumps the output into the `jaccessinspector` window.

Track Shutdown Events
:   Registers with the Java application to receive a Property Changed event
    when a Java application terminates.

## AccessibilityEvents Menu

This section describes the **AccessibilityEvents** menu items.

**Note:** The items listed in the **AccessibilityEvents** menu are the most
important for testing applications, especially for assistive technology
applications.

Track Name Property Events
:   Registers with the Java application all Java Property Changed events
    specifically on accessible objects in which the Name property has changed,
    and upon receiving an event, dumps the output into the scrolling window,
    along with information about the property that changed.

Track Description Property Events
:   Register with the Java application for all Java Property Changed events
    specifically on accessible objects in which the Description property has
    changed, and upon receiving an event, dumps the output into the
    `jaccessinspector` window, along with information about the property that
    changed.

Track State Property Events
:   Register with the Java application all Java Property Changed events
    specifically on accessible objects in which the State property has changed,
    and upon receiving an event, dumps the output into the `jaccessinspector`
    window, along with information about the property that changed.

Track Value Property Events
:   Register with the Java application all Java Property Changed events
    specifically on accessible objects in which the Value property has changed,
    and upon receiving an event, dumps the output into the scrolling window,
    along with information about the property that changed.

Track Selection Property Events
:   Register with the Java application all Java Property Changed events
    specifically on accessible objects in which the Selection property has
    changed, and upon receiving an event, dumps the output into the
    `jaccessinspector` window, along with information about the property that
    changed.

Track Text Property Events
:   Register with the Java application all Java Property Changed events
    specifically on accessible objects in which the Text property has changed,
    and upon receiving one event, dump the output into the `jaccessinspector`
    window, along with information about the property that changed.

Track Caret Property Events
:   Register with the Java application all Java Property Changed events
    specifically on accessible objects in which the Caret property has changed,
    and upon receiving an event, dumps the output into the `jaccessinspector`
    window, along with information about the property that changed.

Track VisibleData Property Events
:   Register with the Java application all Java Property Changed events
    specifically on accessible objects in which the VisibleData property has
    changed, and upon receiving an event, dumps the output into the
    `jaccessinspector` window, along with information about the property that
    changed.

Track Child Property Events
:   Register with the Java application all Java Property Changed events
    specifically on accessible objects in which the Child property has changed,
    and upon receiving an event, dumps the output into the `jaccessinspector`
    window, along with information about the property that changed.

Track Active Descendent Property Events
:   Register with the Java application all Java Property Changed events
    specifically on accessible objects in which the Active Descendent property
    has changed, and upon receiving an event, dumps the output into the
    `jaccessinspector` window, along with information about the property that
    changed.

Track Table Model Change Property Events
:   Register with the Java application all Property Changed events specifically
    on accessible objects in which the Table Model Change property has changed,
    and upon receiving an event, dumps the output into the `jaccessinspector`
    window, along with information about the property that changed.

## Options Menu

This section describes the **Options** menu items.

Monitor the same events as JAWS
:   Enables monitoring of only the events also monitored by JAWS.

Monitor All Events
:   Enables monitoring of all events in the `jaccessinspector` window.

Reset All Events
:   Resets the selected Options to the default settings.

Go To Message
:   Opens the **Go To Message** dialog that lets you display a logged message
    by entering its message number.

Clear Message History
:   Clears the history of logged messages from the `jaccessinspector` window.
