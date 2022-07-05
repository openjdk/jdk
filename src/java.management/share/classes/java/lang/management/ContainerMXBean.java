package java.lang.management;

/**
 * The management interface for the container system of
 * the Java virtual machine.
 *
 * <p> A Java virtual machine has a single instance of the implementation
 * class of this interface.  This instance implementing this interface is
 * an <a href="ManagementFactory.html#MXBean">MXBean</a>
 * that can be obtained by calling
 * the {@link ManagementFactory#getRuntimeMXBean} method or
 * from the {@link ManagementFactory#getPlatformMBeanServer
 * platform MBeanServer} method.
 *
 * <p>The {@code ObjectName} for uniquely identifying the MXBean for
 * the runtime system within an MBeanServer is:
 * <blockquote>
 *    {@link ManagementFactory#CONTAINER_MXBEAN_NAME
 *           java.lang:type=Container}
 * </blockquote>
 *
 * It can be obtained by calling the
 * {@link PlatformManagedObject#getObjectName} method.
 *
 * <p> This interface defines several convenient methods for accessing
 * system properties about the Java virtual machine.
 *
 * @see ManagementFactory#getPlatformMXBeans(Class)
 * @see <a href="../../../javax/management/package-summary.html">
 *      JMX Specification.</a>
 * @see <a href="package-summary.html#examples">
 *      Ways to Access MXBeans</a>
 *
 * @author  pxie
 * @since   20
 */
public interface ContainerMXBean extends PlatformManagedObject {
    /**
     * Returns the interface responsible for providing the
     * platform metrics.
     *
     * @implNote
     * Metrics are currently only supported Linux.
     * The provider for Linux is cgroups (version 1 or 2).
     *
     * @return The name of the provider.
     *
     */
    String getProvider();

    /**
     * Returns the length of the scheduling period, in
     * microseconds, for processes within the Isolation Group.
     *
     * @return time in microseconds, -1 if the metric is not available or
     *         -2 if the metric is not supported.
     *
     */
    long getCpuPeriod();

    /**
     * Returns the total available run-time allowed, in microseconds,
     * during each scheduling period for all tasks in the Isolation
     * Group.
     *
     * @return time in microseconds, -1 if the quota is unlimited or
     *         -2 if not supported.
     *
     */
    long getCpuQuota();

    /**
     * Returns the relative weighting of processes with the Isolation
     * Group used for prioritizing the scheduling of processes across
     * all Isolation Groups running on a host.
     *
     * @implNote
     * Popular container orchestration systems have standardized shares
     * to be multiples of 1024, where 1024 is interpreted as 1 CPU share
     * of execution.  Users can distribute CPU resources to multiple
     * Isolation Groups by specifying the CPU share weighting needed by
     * each process.  To request 2 CPUS worth of execution time, CPU shares
     * would be set to 2048.
     *
     * @return shares value, -1 if the metric is not available or
     *         -2 if cpu shares are not supported.
     *
     */
    long getCpuShares();

    /**
     * Returns the number of effective processors that this Isolation
     * group has available to it.  This effective processor count is
     * computed based on the number of dedicated CPUs, CPU shares and
     * CPU quotas in effect for this isolation group.
     *
     * This method returns the same value as
     * {@link java.lang.Runtime#availableProcessors()}.
     *
     * @return The number of effective CPUs.
     *
     */
    long getEffectiveCpuCount();

    /**
     * Returns the hint to the operating system that allows groups
     * to specify the minimum amount of physical memory that they need to
     * achieve reasonable performance in low memory systems.  This allows
     * host systems to provide greater sharing of memory.
     *
     * @return The minimum amount of physical memory, in bytes, that the
     *         operating system will try to maintain under low memory
     *         conditions.  If this metric is not available, -1 will be
     *         returned. Returns -2 if the metric is not supported.
     *
     */
    long getMemorySoftLimit();

    /**
     * Returns the maximum amount of physical memory, in bytes, that
     * can be allocated in the Isolation Group.
     *
     * @return The maximum amount of memory in bytes or -1 if
     *         there is no limit or -2 if this metric is not supported.
     *
     */
    long getMemoryLimit();

    /**
     * Returns the maximum amount of physical memory and swap space,
     * in bytes, that can be allocated in the Isolation Group.
     *
     * @return The maximum amount of memory in bytes or -1 if
     *         there is no limit set or -2 if this metric is not supported.
     *
     */
    long getMemoryAndSwapLimit();
}
