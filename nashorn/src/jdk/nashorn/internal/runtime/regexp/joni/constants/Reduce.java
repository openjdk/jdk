/*
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies
 * of the Software, and to permit persons to whom the Software is furnished to do
 * so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package jdk.nashorn.internal.runtime.regexp.joni.constants;

import static jdk.nashorn.internal.runtime.regexp.joni.constants.Reduce.ReduceType.A;
import static jdk.nashorn.internal.runtime.regexp.joni.constants.Reduce.ReduceType.AQ;
import static jdk.nashorn.internal.runtime.regexp.joni.constants.Reduce.ReduceType.ASIS;
import static jdk.nashorn.internal.runtime.regexp.joni.constants.Reduce.ReduceType.DEL;
import static jdk.nashorn.internal.runtime.regexp.joni.constants.Reduce.ReduceType.PQ_Q;
import static jdk.nashorn.internal.runtime.regexp.joni.constants.Reduce.ReduceType.P_QQ;
import static jdk.nashorn.internal.runtime.regexp.joni.constants.Reduce.ReduceType.QQ;

public interface Reduce {

    enum ReduceType {
        ASIS,       /* as is */
        DEL,        /* delete parent */
        A,          /* to '*'    */
        AQ,         /* to '*?'   */
        QQ,         /* to '??'   */
        P_QQ,       /* to '+)??' */
        PQ_Q,       /* to '+?)?' */
    }

    final ReduceType[][]REDUCE_TABLE = {
      {DEL,     A,      A,      QQ,     AQ,     ASIS}, /* '?'  */
      {DEL,     DEL,    DEL,    P_QQ,   P_QQ,   DEL},  /* '*'  */
      {A,       A,      DEL,    ASIS,   P_QQ,   DEL},  /* '+'  */
      {DEL,     AQ,     AQ,     DEL,    AQ,     AQ},   /* '??' */
      {DEL,     DEL,    DEL,    DEL,    DEL,    DEL},  /* '*?' */
      {ASIS,    PQ_Q,   DEL,    AQ,     AQ,     DEL}   /* '+?' */
    };


    final String PopularQStr[] = new String[] {
        "?", "*", "+", "??", "*?", "+?"
    };

    String ReduceQStr[]= new String[] {
        "", "", "*", "*?", "??", "+ and ??", "+? and ?"
    };

}

