package jdk.internal.management.remote.rest;

import javax.management.MBeanServer;

public interface JmxRestAdapter {

    public void start();

    public void stop();

    public String getUrl();

    public MBeanServer getMBeanServer();
}