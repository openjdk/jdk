import jdk.internal.management.remote.rest.RestAdapterProvider;

module jdk.management.rest {

//    requires transitive java.management;
    requires java.management;
    requires jdk.httpserver;
    requires jdk.management.agent;

    provides jdk.internal.agent.spi.AgentProvider with RestAdapterProvider;

    // The java.management.rest module provides implementations
    // to support the HTTP protocol.
    provides javax.management.remote.JMXConnectorProvider with
        com.sun.jmx.remote.protocol.http.ClientProvider;
    //provides javax.management.remote.JMXConnectorServerProvider with
    //    com.sun.jmx.remote.protocol.http.ServerProvider;
}
