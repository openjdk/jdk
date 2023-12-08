package jdk.internal.management.remote.rest;

import jdk.internal.agent.spi.AgentProvider;

import java.io.IOException;
import java.util.Properties;

public class RestAdapterProvider extends AgentProvider {

    private static final String REST_ADAPTER_NAME = "RestAdapter";

    @Override
    public synchronized void startAgent() {
        try {
            PlatformRestAdapter.init(null);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public synchronized void startAgent(Properties props) {
        try {
            PlatformRestAdapter.init(props);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public synchronized void stopAgent() {
        PlatformRestAdapter.stop();
    }

    @Override
    public String getName() {
        return REST_ADAPTER_NAME;
    }

    @Override
    public synchronized boolean isActive() {
        return PlatformRestAdapter.isStarted();
    }
}
