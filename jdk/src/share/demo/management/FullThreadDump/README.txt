FullThreadDump demonstrates the use of the java.lang.management API 
to print the full thread dump.  JDK 6 defines a new API to dump
the information about monitors and java.util.concurrent ownable
synchronizers.

This demo also illustrates how to monitor JDK 5 and JDK 6 VMs with
two versions of APIs.

It contains two parts: 
a) Local monitoring within the application
b) Remote monitoring by connecting to a JMX agent with a JMX service URL:
      service:jmx:rmi:///jndi/rmi://<hostName>:<portNum>/jmxrmi
   where <hostName> is the hostname and <portNum> is the port number
   to which the JMX agent will be connected.

To run the demo
---------------
a) Local Monitoring

   java -cp <JDK_HOME>/demo/management/FullThreadDump/FullThreadDump.jar Deadlock

   This will dump the stack trace and then detect deadlocks locally
   within the application.

b) Remote Monitoring

  (1) Start the Deadlock application (or any other application) 
      with the JMX agent as follows:
   
      java -Dcom.sun.management.jmxremote.port=1090
           -Dcom.sun.management.jmxremote.ssl=false
           -Dcom.sun.management.jmxremote.authenticate=false
           -cp <JDK_HOME>/demo/management/FullThreadDump/FullThreadDump.jar
           Deadlock

      This instruction uses the Sun's built-in support to enable a JMX agent.
      You can programmatically start a JMX agent with the RMI connector
      using javax.management.remote API.  See the javadoc and examples for 
      javax.management.remote API for details.

  (2) Run FullThreadDump 

      java -jar <JDK_HOME>/demo/management/FullThreadDump/FullThreadDump.jar \
	  localhost:1090

      This will dump the stack trace and then print out the deadlocked threads.
      
These instructions assume that this installation's version of the java
command is in your path.  If it isn't, then you should either
specify the complete path to the java command or update your
PATH environment variable as described in the installation
instructions for the Java(TM) SDK.
