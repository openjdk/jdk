package sun.management;

import jdk.internal.platform.Container;
import jdk.internal.platform.Metrics;

import javax.management.ObjectName;
import java.lang.management.ContainerMXBean;
import java.lang.management.ManagementFactory;

public class ContainerImpl implements ContainerMXBean {
    final Metrics containerMetrics = Container.metrics();

    @Override
    public String getProvider() {
        return containerMetrics.getProvider();
    }

    @Override
    public long getCpuPeriod() {
        return containerMetrics.getCpuPeriod();
    }

    @Override
    public long getCpuQuota() {
        return containerMetrics.getCpuQuota();
    }

    @Override
    public long getCpuShares() {
        return 0;
    }

    @Override
    public long getEffectiveCpuCount() {
        return containerMetrics.getEffectiveCpuCount();
    }

    @Override
    public long getMemorySoftLimit() {
        return containerMetrics.getMemorySoftLimit();
    }

    @Override
    public long getMemoryLimit() {
        return containerMetrics.getMemoryLimit();
    }

    @Override
    public long getMemoryAndSwapLimit() {
        return containerMetrics.getMemoryAndSwapLimit();
    }

    @Override
    public ObjectName getObjectName() {
        return Util.newObjectName(ManagementFactory.CONTAINER_MXBEAN_NAME);
    }
}
