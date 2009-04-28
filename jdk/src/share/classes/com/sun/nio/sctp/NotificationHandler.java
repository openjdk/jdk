/*
 * Copyright 2009 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */
package com.sun.nio.sctp;

/**
 * A handler for consuming notifications from the SCTP stack.
 *
 * <P> The SCTP channels defined in this package allow a notification handler to
 * be specified to consume notifications from the SCTP stack. When a
 * notification is received the {@linkplain #handleNotification
 * handleNotification} method of the handler is invoked to handle that
 * notification.
 *
 * <P> Additionally, an attachment object can be attached to the {@code receive}
 * operation to provide context when consuming the notification. The
 * attachment is important for cases where a <i>state-less</i> {@code
 * NotificationHandler} is used to consume the result of many {@code receive}
 * operations.
 *
 * <P> Handler implementations are encouraged to extend the {@link
 * AbstractNotificationHandler} class which implements this interface and
 * provide notification specific methods. However, an API should generally use
 * this handler interface as the type for parameters, return type, etc. rather
 * than the abstract class.
 *
 * @param  T  The type of the object attached to the receive operation
 *
 * @since 1.7
 */
public interface NotificationHandler<T> {
    /**
     * Invoked when a notification is received from the SCTP stack.
     *
     * @param  notification
     *         The notification
     *
     * @param  attachment
     *         The object attached to the receive operation when it was initiated.
     *
     * @return  The handler result
     */
    HandlerResult handleNotification(Notification notification, T attachment);
}
