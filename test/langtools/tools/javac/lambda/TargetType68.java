/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * @test
 * @bug 8010303 8062373
 * @summary Graph inference: missing incorporation step causes spurious inference error
 * @compile/fail/ref=TargetType68.out -XDrawDiagnostics TargetType68.java
 */
import java.util.*;

class TargetType68 {

    //derived from FX 2.2 API
    static class XYChart<X,Y> {
        static final class Series<X,Y> {
            Series(java.lang.String name, ObservableList<XYChart.Data<X,Y>> data) { }
        }

        static final class Data<X,Y> { }

        ObservableList<XYChart.Series<X,Y>> getData() { return null; }
    }

    //derived from FX 2.2 API
    interface ObservableList<X> extends List<X> {
        boolean setAll(Collection<? extends X> col);
    }

    //derived from FX 2.2 API
    static class FXCollections {
        static <E> ObservableList<E> observableList(List<E> l) { return null; }
    }

    private void testMethod() {
            XYChart<Number, Number> numberChart = null;
            List<XYChart.Data<Number, Number>> data_1 = new ArrayList<>();
            List<XYChart.Data<Number, Number>> data_2 = new ArrayList<>();
            numberChart.getData().setAll(
                    Arrays.asList(new XYChart.Series<>("Data", FXCollections.observableList(data_1)),
                    new XYChart.Series<>("Data", FXCollections.observableList(data_2)) {}));
    }
}
