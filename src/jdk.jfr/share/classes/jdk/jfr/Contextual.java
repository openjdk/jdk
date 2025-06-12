package jdk.jfr;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Event field annotation, specifies that the value carries contextual
 * information.
 * <p>
 * Contextual information is data that applies to all events happening in the
 * same thread from the beginning to the end of the event with a field annotated
 * with {@code Contextual}.
 * <p>
 * For example, to trace requests or transactions in a system, a trace event can
 * be created to provide context.
 * {@snippet class = "Snippets" region = "ContextualTrace"}
 * <p>
 * To track details within an order service, an order event can be created where
 * only the order ID provides context.
 * {@snippet class = "Snippets" region = "ContextualOrder"}
 * <p>
 * If an order in the order service stalls due to lock contention, a user
 * interface can display contextual information together with the
 * JavaMonitorEnter event to simplify troubleshooting, for example:
 * {@snippet lang=text :
 *   $ jfr print --events JavaMonitorEnter recording.jfr
 *   jdk.JavaMonitorEnter {
 *     Context: Trace.id = "00-0af7651916cd43dd8448eb211c80319c-00f067aa0ba902b7-01"
 *     Context: Trace.name = "POST /checkout/place-order"
 *     Context: Order.id = 314159
 *     startTime = 17:51:29.038 (2025-02-07)
 *     duration = 50.56 ms
 *     monitorClass = java.util.ArrayDeque (classLoader = bootstrap)
 *    previousOwner = "Order Thread" (javaThreadId = 56209, virtual = true)
 *    address = 0x60000232ECB0
 *    eventThread = "Order Thread" (javaThreadId = 52613, virtual = true)
 *    stackTrace = [
 *      java.util.zip.ZipFile$CleanableResource.getInflater() line: 685
 *      java.util.zip.ZipFile$ZipFileInflaterInputStream.<init>(ZipFile) line: 388
 *      java.util.zip.ZipFile.getInputStream(ZipEntry) line: 355
 *      java.util.jar.JarFile.getInputStream(ZipEntry) line: 833
 *      ...
 *    ]
 *   }
 * }
 * <p>
 * The difference between {@link Relational} and {@link Contextual} annotations
 * is that {@link Relational} ties event data together to form a global data
 * structure, similar to a foreign key in a relational database, but
 * {@link Contextual} represents a state that applies to all events that happen
 * at the same time, in the same thread. A field can be both contextual and
 * relational at the same time.
 * <p>
 * A contextual field may incur overhead on a parser reading a recording file,
 * since it must track active context, so it should be used sparingly and only
 * where appropriate.
 *
 * @since 25
 */
@MetadataDefinition
@Label("Context")
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD})
public @interface Contextual {
}
