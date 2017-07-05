VerboseGC demonstrates the use of the java.lang.management API to 
print the garbage collection statistics and memory usage remotely 
by connecting to a JMX agent with a JMX service URL:
      service:jmx:rmi:///jndi/rmi://<hostName>:<portNum>/jmxrmi
where <hostName> is the hostname and <portNum> is the port number
to which the JMX agent will be connected.

To run the VerboseGC demo

(1) Start the application with the JMX agent - here's an example of 
    how the Java2D is started

    java -Dcom.sun.management.jmxremote.port=1090
         -Dcom.sun.management.jmxremote.ssl=false
         -Dcom.sun.management.jmxremote.authenticate=false
         -jar <JDK_HOME>/demo/jfc/Java2D/Java2Demo.jar

    This instruction uses the Sun's built-in support to enable a JMX agent.
    You can programmatically start a JMX agent with the RMI connector
    using javax.management.remote API.  See the javadoc and examples for
    javax.management.remote API for details.

(2) Run VerboseGC
   
      java -jar <JDK_HOME>/demo/management/VerboseGC/VerboseGC.jar localhost:1090

These instructions assume that this installation's version of the java
command is in your path.  If it isn't, then you should either
specify the complete path to the java command or update your
PATH environment variable as described in the installation
instructions for the Java(TM) SDK.
