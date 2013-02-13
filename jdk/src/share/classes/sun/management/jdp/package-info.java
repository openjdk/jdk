/**
 *  Summary
 *  -------
 *
 *  Define a lightweight network protocol for discovering running and
 *  manageable Java processes within a network subnet.
 *
 *
 * Description
 * -----------
 *
 * The protocol is lightweight multicast based, and works like a beacon,
 * broadcasting the JMXService URL needed to connect to the external JMX
 * agent if an application is started with appropriate parameters.
 *
 * The payload is structured like this:
 *
 *  4 bytes JDP magic (0xC0FFEE42)
 *  2 bytes JDP protocol version (1)
 *  2 bytes size of the next entry
 *      x bytes next entry (UTF-8 encoded)
 *  2 bytes size of next entry
 *    ...   Rinse and repeat...
 *
 * The payload will be parsed as even entries being keys, odd entries being
 * values.
 *
 * The standard JDP packet contains four entries:
 *
 * - `DISCOVERABLE_SESSION_UUID` -- Unique id of the instance; this id changes every time
 *    the discovery protocol starts and stops
 *
 * - `MAIN_CLASS` -- The value of the `sun.java.command` property
 *
 * - `JMX_SERVICE_URL` -- The URL to connect to the JMX agent
 *
 * - `INSTANCE_NAME` -- The user-provided name of the running instance
 *
 * The protocol sends packets to 239.255.255.225:7095 by default.
 *
 * The protocol uses system properties to control it's behaviour:
 * - `com.sun.management.jdp.port` -- override default port
 *
 * - `com.sun.management.jdp.address` -- override default address
 *
 * - `com.sun.management.jmxremote.autodiscovery` -- whether we should start autodiscovery or
 * not. Autodiscovery starts if and only if following conditions are met: (autodiscovery is
 * true OR (autodiscovery is not set AND jdp.port is set))
 *
 * - `com.sun.management.jdp.ttl`         -- set ttl for broadcast packet, default is 1
 * - `com.sun.management.jdp.pause`       -- set broadcast interval in seconds default is 5
 * - `com.sun.management.jdp.source_addr` -- an address of interface to use for broadcast
 */

package sun.management.jdp;
