Scriptpad Sample

* Introduction

Scriptpad is a notepad like editor to open/edit/save and run 
script (JavaScript) files. This sample demonstrates the use of 
javax.script (JSR-223) API and JavaScript engine that is bundled 
with JDK 6.

Scriptpad sample demonstrates how to use Javascript to use Java 
classes and objects to perform various tasks such as to modify,
customize Swing GUI or to connect to a running application and 
monitor it using JMX (Java Management Extensions) API.

* How to run Scriptpad?

Scriptpad can be run with the following command:
    
    java -jar ./build/scriptpad.jar

(be sure to use the correct version of java).  You can
open/edit/save scripts using menu items under "File" menu.
To run currently edited script, you can use "Tools->Run" menu.

For example, you may enter

    alert("hello, world");

in the editor and run the same with "Tools->Run" menu. 
You will see an alert box with the message "hello, world".

In addition to being a simple script editor/runner, scriptpad 
can be used to connect to a JMX MBean server ("Tools->JMX Connect" 
menu). User can specify JMX hostname and port. After connecting, 
user can use "monitoring and management" script functions defined 
in "mm.js" (see below).

* Scriptpad Sources

com.sun.demo.scriptpad.Main class is the entry point of this
sample. This class creates ScriptEngine and evaluates few
JavaScript "files" -- which are stored as resources (please
refer to src/resources/*.js). Actual code for the scriptpad's
main functionality lives in these JavaScript files.

1. conc.js
 -- simple concurrency utilities for JavaScript

2. gui.js
 -- simple GUI utilities for JavaScript

3. mm.js
 -- Monitoring and Management utilities for JavaScript

4. scriptpad.js
 -- This creates main "notepad"-like GUI for open/edit/save
    and run script files

5. Main.js
 -- This script file can be used under "jrunscript" tool.
    jrunscript is an experimental tool shipped with JDK (under
    $JDK_HOME/bin directory). The scriptpad application can be
    run by the following commands:

    cd ./src/resources
    $JDK_HOME/bin/jrunscript -f Main.js -f -


* Extending Scriptpad:

It is possible to extend scriptpad using scripts. There is a global
object called "application". This object has 2 fields and a method.

    Fields of the application object:

        frame  -> JFrame of the scriptpad
        editor -> editor pane of the scriptpad
 
    Method of the application object:

        addTool -> adds a menu item under "Tools" menu

    Example script to add "Tools->Hello" menu item:

        application.addTool("Hello", 
            function() { alert("hello, world"); });

After running the above script, you can click Tools->Hello menu item
and you'll see an alert box.

Scriptpad customization may also be done by defining a file named 
"scriptpad.js" under your home directory,. If this file is found, 
scriptpad loads this file just after initializating everything. 
In your initialization file, you can additional script functions 
by "load" function.

* Script Samples:

On clicking the menu items under "Examples" menu, scriptpad shows 
built-in examples in the editor. Also, there are few script samples
under the ./src/scripts directory.

* Monitoring and Management with Scriptpad:

(1) Start the application with the JMX agent - here's an example of 
    how the Java2D demo is started
   
      java -Dcom.sun.management.jmxremote.port=1090          \
           -Dcom.sun.management.jmxremote.ssl=false          \
           -Dcom.sun.management.jmxremote.authenticate=false \
           -jar $JDK_HOME/demo/jfc/Font2DTest/Font2DTest.jar

(2) Start scriptpad and click on "Tools->JMX Connect" menu.
    In the prompt, enter "localhost:1090" to connect to the above
    program.

After connecting to a MBeanServer (using "Tools->JMX Connect"),
you can run any script that uses functions defined in "mm.js". 
For example, it is possible to load and run management scripts that
are part of JConsole script shell plugin under the directory:

    $JDK_HOME/demo/scripting/jconsole-plugin/src/scripts
