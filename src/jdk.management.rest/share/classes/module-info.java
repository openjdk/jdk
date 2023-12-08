import jdk.internal.management.remote.rest.RestAdapterProvider;

module jdk.management.rest {

    requires transitive java.management;
    requires jdk.httpserver;
    requires jdk.management.agent;

    provides jdk.internal.agent.spi.AgentProvider with RestAdapterProvider;
}
