JTop monitors the CPU usage of all threads in a remote application
which has remote management enabled.  JTop demonstrates the use of 
the java.lang.management API to obtain the CPU consumption for 
each thread.

JTop is also a JConsole Plugin.  See below for details.

JTop Standalone GUI
===================

JTop first establishes a connection to a JMX agent in a remote
application with a JMX service URL:
   service:jmx:rmi:///jndi/rmi://<hostName>:<portNum>/jmxrmi

where <hostName> is the hostname and <portNum> is the port number
to which the JMX agent will be connected.

To run the demo
---------------
(1) Start the application with the JMX agent - here's an example of 
    how the Java2D is started
   
      java -Dcom.sun.management.jmxremote.port=1090
           -Dcom.sun.management.jmxremote.ssl=false
           -Dcom.sun.management.jmxremote.authenticate=false
           -jar <JDK_HOME>/demo/jfc/Java2D/Java2Demo.jar

    This instruction uses the Sun's built-in support to enable a JMX agent
    with a JMX service URL as described above.
    You can programmatically start a JMX agent with the RMI connector
    using javax.management.remote API.  See the javadoc and examples for 
    javax.management.remote API for details.

(2) Run JTop on a different machine:

      java -jar <JDK_HOME>/demo/management/JTop/JTop.jar <hostname>:1090

    where <hostname> is where the Java2Demo.jar runs in step (1).

These instructions assume that this installation's version of the java
command is in your path.  If it isn't, then you should either
specify the complete path to the java command or update your
PATH environment variable as described in the installation
instructions for the Java(TM) SDK.

JTop JConsole Plugin
====================

JTop is a JConsole Plugin which adds a "JTop" tab to JConsole.

To run JConsole with the JTop plugin 
------------------------------------
    jconsole -pluginpath <JDK_HOME>/demo/management/JTop/JTop.jar


To compile
----------
    javac -classpath <JDK_HOME>/lib/jconsole.jar JTopPlugin.java
 
com.sun.tools.jconsole API is in jconsole.jar which is needed
in the classpath for compilation.
