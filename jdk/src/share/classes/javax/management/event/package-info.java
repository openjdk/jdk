/**
 * <p>Defines the <em>Event Service</em>, which provides extended support
 * for JMX notifications.</p>
 *
 * <p>The Event Service provides greater control over
 * notification handling than the default technique using {@link
 * javax.management.MBeanServer#addNotificationListener(ObjectName,
 * NotificationListener, NotificationFilter, Object)
 * MBeanServer.addNotificationListener} or {@link
 * javax.management.MBeanServerConnection#addNotificationListener(ObjectName,
 * NotificationListener, NotificationFilter, Object)
 * MBeanServerConnection.addNotificationListener}.</p>
 *
 * <p>Here are some reasons you may want to use the Event Service:</p>
 *
 * <ul>
 * <li>To receive notifications from a set of MBeans defined by an
 * ObjectName pattern, such as {@code com.example.config:type=Cache,*}.
 *
 * <li>When the notification-handling behavior of the connector you are
 * using does not match your requirements.  For example, with the standard
 * RMI connector you can lose notifications if there are very many of them
 * in the MBean Server you are connected to, even if you are only listening
 * for a small proportion of them.
 *
 * <li>To change the threading behavior of notification dispatch.
 *
 * <li>To define a different transport for notifications, for example to
 * arrange for them to be delivered through the Java Message Service (<a
 * href="http://java.sun.com/jms">JMS</a>).  The Event Service comes with
 * one alternative transport as standard, a "push-mode" RMI transport.
 *
 * <li>To handle notifications on behalf of MBeans (often virtual) in a
 * namespace.
 * </ul>
 *
 * <p>The Event Service is new in version 2.0 of the JMX API, which is the
 * version introduced in version 7 of the Java SE platform.  It is not usually
 * possible to use the Event Service when connecting remotely to an
 * MBean Server that is running an earlier version.</p>
 *
 *
 * <h3 id="handlingremote">Handling remote notifications with the Event
 * Service</h3>
 *
 * <p>Prior to version 2.0 of the JMX API, every connector
 * had to include logic to handle notifications. The standard {@linkplain
 * javax.management.remote.rmi RMI} and JMXMP connectors defined by <a
 * href="http://jcp.org/en/jsr/detail?id=160">JSR 160</a> handle notifications
 * in a way that is not always appropriate for applications. Specifically,
 * the connector server adds one listener to every MBean that might emit
 * notifications, and adds all received notifications to a fixed-size
 * buffer. This means that if there are very many notifications, a
 * remote client may miss some, even if it is only registered for a
 * very small subset of notifications. Furthermore, since every {@link
 * javax.management.NotificationBroadcaster NotificationBroadcaster} MBean
 * gets a listener from the connector server, MBeans cannot usefully optimize
 * by only sending notifications when there is a listener. Finally, since
 * the connector server uses just one listener per MBean, MBeans cannot
 * impose custom behavior per listener, such as security checks or localized
 * notifications.</p>
 *
 * <p>The Event Service does not have these restrictions.  The RMI connector
 * that is included in this version of the JMX API uses the Event Service by
 * default, although it can be configured to have the previous behavior if
 * required.</p>
 *
 * <p>The Event Service can be used with <em>any</em> connector via the
 * method {@link javax.management.event.EventClient#getEventClientConnection
 * EventClient.getEventClientConnection}, like this:</p>
 *
 * <pre>
 * JMXConnector conn = ...;
 * MBeanServerConnection mbsc = conn.getMBeanServerConnection();
 * MBeanServerConnection eventMbsc = EventClient.getEventClientConnection(mbsc);
 * </pre>
 *
 * <p>If you add listeners using {@code eventMbsc.addNotificationListener}
 * instead of {@code mbsc.addNotificationListener}, then they will be handled
 * by the Event Service rather than by the connector's notification system.</p>
 *
 * <p>For the Event Service to work, either the {@link
 * javax.management.event.EventClientDelegateMBean EventClientDelegateMBean}
 * must be registered in the MBean Server, or the connector server must
 * be configured to simulate the existence of this MBean, for example
 * using {@link javax.management.event.EventClientDelegate#newForwarder
 * EventClientDelegate.newForwarder}. The standard RMI connector is so
 * configured by default. The {@code EventClientDelegateMBean} documentation
 * has further details.</p>
 *
 *
 * <h3 id="subscribepattern">Receiving notifications from a set of MBeans</h3>
 *
 * <p>The Event Server allows you to receive notifications from every MBean
 * that matches an {@link javax.management.ObjectName ObjectName} pattern.
 * For local clients (in the same JVM as the MBean Server), the {@link
 * javax.management.event.EventSubscriber EventSubscriber} class can be used for
 * this. For remote clients, or if the same code is to be used locally and
 * remotely, use an
 * {@link javax.management.event.EventClient EventClient}.</p>
 *
 * <p>EventSubscriber and EventClient correctly handle the case where a new
 * MBean is registered under a name that matches the pattern. Notifications
 * from the new MBean will also be received.</p>
 *
 * <p>Here is how to receive notifications from all MBeans in a local
 * {@code MBeanServer} that match {@code com.example.config:type=Cache,*}:</p>
 *
 * <pre>
 * MBeanServer mbs = ...;
 * NotificationListener listener = ...;
 * ObjectName pattern = new ObjectName("com.example.config:type=Cache,*");
 * EventSubscriber esub = EventSubscriber.getEventSubscriber(mbs);
 * esub.{@link javax.management.event.EventSubscriber#subscribe
 * subscribe}(pattern, listener, null, null);
 * </pre>
 *
 * <p>Here is how to do the same thing remotely:</p>
 *
 * <pre>
 * MBeanServerConnection mbsc = jmxConnector.getMBeanServerConnection();
 * EventClient events = new EventClient(mbsc);
 * NotificationListener listener = ...;
 * ObjectName pattern = new ObjectName("com.example.config:type=Cache,*");
 * events.{@link javax.management.event.EventClient#subscribe
 * subscribe}(pattern, listener, null, null);
 * </pre>
 *
 *
 * <h3 id="threading">Controlling threading behavior for notification
 * dispatch</h3>
 *
 * <p>The EventClient class can be used to control threading of listener
 * dispatch.  For example, to arrange for all listeners to be invoked
 * in the same thread, you can create an {@code EventClient} like this:</p>
 *
 * <pre>
 * MBeanServerConnection mbsc = ...;
 * Executor singleThreadExecutor = {@link
 * java.util.concurrent.Executors#newSingleThreadExecutor()
 * Executors.newSingleThreadExecutor}();
 * EventClient events = new EventClient(
 *         mbsc, null, singleThreadExecutor, EventClient.DEFAULT_LEASE_TIMEOUT);
 * events.addNotificationListener(...);
 * events.subscribe(...);
 * </pre>
 *
 *
 * <h3 id="leasing">Leasing</h3>
 *
 * <p>The {@code EventClient} uses a <em>lease</em> mechanism to ensure
 * that resources are eventually released on the server even if the client
 * does not explicitly clean up.  (This can happen through network
 * partitioning, for example.)</p>
 *
 * <p>When an {@code EventClient} registers with the {@code
 * EventClientDelegateMBean} using one of the {@code addClient} methods,
 * an initial lease is created with a default expiry time. The {@code
 * EventClient} requests an explicit lease shortly after that, with a
 * configurable expiry time. Then the {@code EventClient} periodically
 * <em>renews</em> the lease before it expires, typically about half way
 * through the lifetime of the lease. If at any point the lease reaches
 * the expiry time of the last renewal then it expires, and {@code
 * EventClient} is unregistered as if it had called the {@link
 * javax.management.event.EventClientDelegateMBean#removeClient removeClient}
 * method.</p>
 *
 *
 * <h3 id="transports">Custom notification transports</h3>
 *
 * <p>When you create an {@code EventClient}, you can define the transport
 * that it uses to deliver notifications. The transport might use the
 * Java Message Service (<a href="http://java.sun.com/jms">JMS</a>) or
 * any other communication system. Specifying a transport is useful for
 * example when you want different network behavior from the default, or
 * different reliability guarantees. The default transport calls {@link
 * javax.management.event.EventClientDelegateMBean#fetchNotifications
 * EventClientDelegateMBean.fetchNotifications} repeatedly, which usually means
 * that there must be a network connection permanently open between the client
 * and the server. If the same client is connected to many servers this can
 * cause scalability problems.  If notifications are relatively rare, then
 * JMS or the {@linkplain javax.management.event.RMIPushEventRelay push-mode
 * RMI transport} may be more suitable.</p>
 *
 * <p>A transport is implemented by an {@link javax.management.event.EventRelay
 * EventRelay} on the client side and a corresponding {@link
 * javax.management.event.EventForwarder EventForwarder}
 * on the server side. An example is the {@link
 * javax.management.event.RMIPushEventRelay RMIPushEventRelay} and its
 * {@link javax.management.event.RMIPushEventForwarder RMIPushEventForwarder}.</p>
 *
 * <p>To use a given transport with an {@code EventClient}, you first create
 * an instance of its {@code EventRelay}. Typically the {@code EventRelay}'s
 * constructor will have a parameter of type {@code MBeanServerConnection}
 * or {@code EventClientDelegateMBean}, so that it can communicate with the
 * {@code EventClientDelegateMBean} in the server. For example, the {@link
 * javax.management.event.RMIPushEventForwarder RMIPushEventForwarder}'s constructors
 * all take an {@code EventClientDelegateMBean} parameter, which is expected to
 * be a {@linkplain javax.management.JMX#newMBeanProxy(MBeanServerConnection,
 * ObjectName, Class) proxy} for the {@code EventClientDelegateMBean} in the
 * server.</p>
 *
 * <p>When it is created, the {@code EventRelay} will call
 * {@link javax.management.event.EventClientDelegateMBean#addClient(String,
 * Object[], String[]) EventClientDelegateMBean.addClient}.  It passes the
 * name of the {@code EventForwarder} class and its constructor parameters.
 * The {@code EventClientDelegateMBean} will instantiate this class using
 * {@link javax.management.MBeanServer#instantiate(String, Object[], String[])
 * MBeanServer.instantiate}, and it will return a unique <em>client id</em>.</p>
 *
 * <p>Then you pass the newly-created {@code EventRelay} to one of the {@code
 * EventClient} constructors, and you have an {@code EventClient} that uses the
 * chosen transport.</p>
 *
 * <p>For example, when you create an {@code RMIPushEventRelay}, it
 * uses {@code MBeanServerDelegateMBean.addClient} to create an {@code
 * RMIEventForwarder} in the server. Notifications will then be delivered
 * through an RMI communication from the {@code RMIEventForwarder} to the
 * {@code RMIPushEventRelay}.</p>
 *
 *
 * <h4 id="writingcustomtransport">Writing a custom transport</h4>
 *
 * <p>To write a custom transport, you need to understand the sequence
 * of events when an {@code EventRelay} and its corresponding {@code
 * EventForwarder} are created, and when a notification is sent from the {@code
 * EventForwarder} to the {@code EventRelay}.</p>
 *
 * <p>When an {@code EventRelay} is created:</p>
 *
 * <ul>
 * <li><p>The {@code EventRelay} must call {@code
 * EventClientDelegateMBean.addClient} with the name of the {@code
 * EventForwarder} and the constructor parameters.</p>
 *
 * <li><p>{@code EventClientDelegateMBean.addClient} will do the following
 * steps:</p>
 *
 * <ul>
 * <li>create the {@code EventForwarder} using {@code MBeanServer.instantiate};
 * <li>allocate a unique client id;
 * <li>call the new {@code EventForwarder}'s {@link
 * javax.management.event.EventForwarder#setClientId setClientId} method with
 * the new client id;
 * <li>return the client id to the caller.
 * </ul>
 *
 * </ul>
 *
 * <p>When an {@code EventClient} is created with an {@code EventRelay}
 * parameter, it calls {@link javax.management.event.EventRelay#setEventReceiver
 * EventRelay.setEventReceiver} with an {@code EventReceiver} that the
 * {@code EventRelay} will use to deliver notifications.</p>
 *
 * <p>When a listener is added using the {@code EventClient}, the
 * {@code EventRelay} and {@code EventForwarder} are not involved.</p>
 *
 * <p>When an MBean emits a notification and a listener has been added
 * to that MBean using the {@code EventClient}:</p>
 *
 * <ul>
 * <li><p>The {@code EventForwarder}'s
 * {@link javax.management.event.EventForwarder#forward forward} method
 * is called with the notification and a <em>listener id</em>.</p>
 *
 * <li><p>The {@code EventForwarder} sends the notification and listener id
 * to the {@code EventRelay} using the custom transport.</p>
 *
 * <li><p>The {@code EventRelay} delivers the notification by calling
 * {@link javax.management.event.EventReceiver#receive EventReceiver.receive}.</p>
 * </ul>
 *
 * <p>When the {@code EventClient} is closed ({@link
 * javax.management.event.EventClient#close EventClient.close}):</p>
 *
 * <ul>
 * <li><p>The {@code EventClient} calls {@link
 * javax.management.event.EventRelay#stop EventRelay.stop}.</p>
 *
 * <li><p>The {@code EventClient} calls {@link
 * javax.management.event.EventClientDelegateMBean#removeClient
 * EventClientDelegateMBean.removeClient}.</p>
 *
 * <li><p>The {@code EventClientDelegateMBean} removes any listeners it
 * had added on behalf of this {@code EventClient}.</p>
 *
 * <li><p>The {@code EventClientDelegateMBean} calls
 * {@link javax.management.event.EventForwarder#close EventForwarder.close}.</p>
 * </ul>
 *
 *
 * <h4 id="threading">Threading and buffering</h3>
 *
 * <p>The {@link javax.management.event.EventForwarder#forward
 * EventForwarder.forward} method may be called in the thread that the
 * source MBean is using to send its notification.  MBeans can expect
 * that notification sending does not block.  Therefore a {@code forward}
 * method will typically add the notification to a queue, with a separate
 * thread that takes notifications off the queue and sends them.</p>
 *
 * <p>An {@code EventRelay} does not usually need to buffer notifications
 * before giving them to
 * {@link javax.management.event.EventReceiver#receive EventReceiver.receive}.
 * Although by default each such notification will be sent to potentially-slow
 * listeners, if this is a problem then an {@code Executor} can be given to
 * the {@code EventClient} constructor to cause the listeners to be called
 * in a different thread.</p>
 *
 * @since 1.7
 */

package javax.management.event;
