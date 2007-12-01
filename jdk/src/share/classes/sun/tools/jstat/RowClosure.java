/*
 * Copyright 2004 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.tools.jstat;

import java.text.*;
import sun.jvmstat.monitor.*;

/**
 * A class implementing the Closure interface for iterating over the
 * specified columns of data and generating the columnized string of
 * data representing a row of output for the form.
 *
 * @author Brian Doherty
 * @since 1.5
 */
public class RowClosure implements Closure {
    private MonitoredVm vm;
    private StringBuilder row = new StringBuilder();

    public RowClosure(MonitoredVm vm) {
        this.vm = vm;
    }

    public void visit(Object o, boolean hasNext) throws MonitorException {
        if (! (o instanceof ColumnFormat)) {
            return;
        }

        ColumnFormat c = (ColumnFormat)o;
        String s = null;

        Expression e = c.getExpression();
        ExpressionEvaluator ee = new ExpressionExecuter(vm);
        Object value = ee.evaluate(e);

        if (value instanceof String) {
            s = (String)value;
        } else if (value instanceof Number) {
            double d = ((Number)value).doubleValue();
            double scaledValue = c.getScale().scale(d);
            DecimalFormat df = new DecimalFormat(c.getFormat());
            s = df.format(scaledValue);
        }

        c.setPreviousValue(value);
        s = c.getAlignment().align(s, c.getWidth());
        row.append(s);
        if (hasNext) {
            row.append(" ");
        }
    }

    public String getRow() {
        return row.toString();
    }
}
