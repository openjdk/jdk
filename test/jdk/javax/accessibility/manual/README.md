<!--
Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.

This code is free software; you can redistribute it and/or modify it
under the terms of the GNU General Public License version 2 only, as
published by the Free Software Foundation.

This code is distributed in the hope that it will be useful, but WITHOUT
ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
version 2 for more details (a copy is included in the LICENSE file that
accompanied this code).

You should have received a copy of the GNU General Public License version
2 along with this work; if not, write to the Free Software Foundation,
Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.

Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
or visit www.oracle.com if you need additional information or have any
questions.
-->

# Manual javax.accessibility test suite

##  Configure environment

### Swing Set 2

Prepare an appropriate version of Swing Set 2 from JDK demos.

### Acessibility frameworks

Testing can be performed without an accessibility framework or with one of these frameworks:

1. Windows
   1. JAWS
   2. NVDA
2. Mac OS X
   1. Voice over

## Executing a test run

* Start the required accessibility framework, if necessary
* Swing Set 2 jar default location is
<code>&lt;tested jdk&gt;/demo/jfc/SwingSet2/SwingSet2.jar</code>
* To override Swing Set 2 jar use <code>SWINGSET2_JAR</code> environment variable:


    jtreg ... -e:SWINGSET2_JAR=<file location> -m .../javax/accessibility/manual/...


## Performing tests

When a test a started, a UI appears consisting of two frames: test framework frame and Swing Set 2 frame. Test framework
frame will contain a name of the test in the title and detailed instructions.

1. Follow the test instructions
2. If everything goes as expected
   1. Push "Pass"
   2. UI for this test closes
3. If something goes not accordding to the instructions:
   1. Push "Fail"
   2. A screenshot is taken automatically
   3. Describe the problem
   4. Retake the screenshot, if necessary
      1. Interract with the Swing Set 2 UI to make it showing the failure. Hit "Retake screenshot"
      2. If to demonstrate the failure the UI need to be in a state which prevents using test framework UI, such as model dialogs need to be opened or menu expanded
         1. Enter delay (in seconds)
         2. Push "Retake screenshot"
         3. Prepare the UI
         4. Wait for the screenshot to be retaken
   5. Push "Fail" button again
   6. Screenshot and the description are saved for further analysis
   7. Test UI closes
   
**Wasning: Do not close any window directly, all windows will be closed once the test is finished as passed or failed.**

**Note: Keyboard navigation is supported throughout the test framework UI.**
