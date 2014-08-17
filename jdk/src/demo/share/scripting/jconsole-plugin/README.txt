What is this demo about?

This is "script shell" plugin for jconsole  - the monitoring and management 
client tool shipped with JRE. This plugin adds "Script Shell" tab to jconsole.
This serves as a demo for jconsole plugin API (com.sun.tools.jconsole) as well 
as a demo for scripting API (javax.script) for the Java platform.

Script console is an interactive read-eval-print interface that can be used
used to execute advanced monitoring and management queries. By default,
JavaScript is used as the scripting language. The scripting language can be 
changed using the system property com.sun.demo.jconsole.console.language. To 
use other scripting languages, you need to specify the corresponding engine
jar file in pluginpath along with this plugin's jar file.

The following 3 global variables are exposed to the script engine:

    window      javax.swing.JPanel
    engine      javax.script.ScriptEngine
    plugin      com.sun.tools.jconsole.JConsolePlugin

If you use JavaScript, there are many useful global functions defined in 
./src/resources/jconsole.js. This is built into the script plugin jar file. 
In addition, you can add other global functions and global variables by 
defining those in ~/jconsole.js (or jconsole.<ext> where <ext> is the file 
extension for your scripting language of choice under your home directory).

How do I compile script console plugin?

You can use the Java based build tool "ant" (http://ant.apache.org) to build 
this plugin. To build using ant, please use the following command in the
current directory:

    ant

How do I use script console plugin?

To start jconsole with this plugin, please use the following command

    jconsole -pluginpath jconsole-plugin.jar

How do I load my own script files in script console?

If you use JavaScript (the default), then there is a global function called
"load" to load any script file from your file system. In script console
prompt, enter the following:

    load(<script-file-path>);

where <script-file-path> is the path of your script file to load. If you don't 
specify the file path, then the load function shows file dialog box to choose 
the script file to load.

How do I get help on script global functions?

If you use JavaScript (the default), then there is a global function called
"help" that prints one-line help messages on global functions. In script
console prompt, enter the following:

    help(); 

Where are the sample JavaScript files?

./src/scripts directory contains JavaScript files that can be loaded into
script console.
