/*
 * Copyright 1999-2005 Sun Microsystems, Inc.  All Rights Reserved.
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

package javax.management;


/**
 * <p>Constructs query object constraints.</p>
 *
 * <p>The MBean Server can be queried for MBeans that meet a particular
 * condition, using its {@link MBeanServer#queryNames queryNames} or
 * {@link MBeanServer#queryMBeans queryMBeans} method.  The {@link QueryExp}
 * parameter to the method can be any implementation of the interface
 * {@code QueryExp}, but it is usually best to obtain the {@code QueryExp}
 * value by calling the static methods in this class.  This is particularly
 * true when querying a remote MBean Server: a custom implementation of the
 * {@code QueryExp} interface might not be present in the remote MBean Server,
 * but the methods in this class return only standard classes that are
 * part of the JMX implementation.</p>
 *
 * <p>There are two ways to create {@code QueryExp} objects using the methods
 * in this class.  The first is to build them by chaining together calls to
 * the various methods.  The second is to use the Query Language described
 * <a href="#ql">below</a> and produce the {@code QueryExp} by calling
 * {@link #fromString Query.fromString}.  The two ways are equivalent:
 * every {@code QueryExp} returned by {@code fromString} can also be
 * constructed by chaining method calls.</p>
 *
 * <p>As an example, suppose you wanted to find all MBeans where the {@code
 * Enabled} attribute is {@code true} and the {@code Owner} attribute is {@code
 * "Duke"}. Here is how you could construct the appropriate {@code QueryExp} by
 * chaining together method calls:</p>
 *
 * <pre>
 * QueryExp query =
 *     Query.and(Query.eq(Query.attr("Enabled"), Query.value(true)),
 *               Query.eq(Query.attr("Owner"), Query.value("Duke")));
 * </pre>
 *
 * <p>Here is how you could construct the same {@code QueryExp} using the
 * Query Language:</p>
 *
 * <pre>
 * QueryExp query = Query.fromString("Enabled = true and Owner = 'Duke'");
 * </pre>
 *
 * <p>The principal advantage of the method-chaining approach is that the
 * compiler will check that the query makes sense.  The principal advantage
 * of the Query Language approach is that it is easier to write and especially
 * read.</p>
 *
 *
 * <h4 id="ql">Query Language</h4>
 *
 * <p>The query language is closely modeled on the WHERE clause of
 * SQL SELECT statements. The formal specification of the language
 * appears <a href="#formal-ql">below</a>, but it is probably easier to
 * understand it with examples such as the following.</p>
 *
 * <dl>
 * <dt>{@code Message = 'OK'}
 * <dd>Selects MBeans that have a {@code Message} attribute whose value
 *     is the string {@code OK}.
 *
 * <dt>{@code FreeSpacePercent < 10}
 * <dd>Selects MBeans that have a {@code FreeSpacePercent} attribute whose
 *     value is a number less than 10.
 *
 * <dt>{@code FreeSpacePercent < 10 and WarningSent = false}
 * <dd>Selects the same MBeans as the previous example, but they must
 *     also have a boolean attribute {@code WarningSent} whose value
 *     is false.
 *
 * <dt>{@code SpaceUsed > TotalSpace * (2.0 / 3.0)}
 * <dd>Selects MBeans that have {@code SpaceUsed} and {@code TotalSpace}
 *     attributes where the first is more than two-thirds the second.
 *
 * <dt>{@code not (FreeSpacePercent between 10 and 90)}
 * <dd>Selects MBeans that have a {@code FreeSpacePercent} attribute whose
 *     value is not between 10 and 90, inclusive.
 *
 * <dt>{@code FreeSpacePercent not between 10 and 90}
 * <dd>Another way of writing the previous query.
 *
 * <dt>{@code Status in ('STOPPED', 'STARTING', 'STARTED')}
 * <dd>Selects MBeans that have a {@code Status} attribute whose value
 *     is one of those three strings.
 *
 * <dt>{@code Message like 'OK: %'}
 * <dd>Selects MBeans that have a {@code Message} attribute whose value
 *     is a string beginning with {@code "OK: "}.  <b>Notice that the
 *     wildcard characters are SQL's ones.</b>  In the query language,
 *     {@code %} means "any sequence of characters" and {@code _}
 *     means "any single character".  In the rest of the JMX API, these
 *     correspond to {@code *} and {@code %} respectively.
 *
 * <dt>{@code instanceof 'javax.management.NotificationBroadcaster'}
 * <dd>Selects MBeans that are instances of
 *     {@link javax.management.NotificationBroadcaster}, as reported by
 *     {@link javax.management.MBeanServer#isInstanceOf MBeanServer.isInstanceOf}.
 *
 * <dt>{@code like 'mydomain:*'}
 * <dd>Selects MBeans whose {@link ObjectName}s have the domain {@code mydomain}.
 *
 * </dl>
 *
 * <p>The last two examples do not correspond to valid SQL syntax, but all
 * the others do.</p>
 *
 * <p>The remainder of this description is a formal specification of the
 * query language.</p>
 *
 *
 * <h4 id="formal-ql">Lexical elements</h4>
 *
 * <p>Keywords such as <b>and</b>, <b>like</b>, and <b>between</b> are not
 * case sensitive.  You can write <b>between</b>, <b>BETWEEN</b>, or
 * <b>BeTwEeN</b> with the same effect.</p>
 *
 * <p>On the other hand, attribute names <i>are</i> case sensitive.  The
 * attribute {@code Name} is not the same as the attribute {@code name}.</p>
 *
 * <p>To access an attribute whose name, ignoring case, is the same as one of
 * the keywords {@code not}, {@code instanceof}, {@code like}, {@code true},
 * or {@code false}, you can use double quotes, for example {@code "not"}.
 * Double quotes can also be used to include non-identifier characters in
 * the name of an attribute, for example {@code "attribute-name-with-hyphens"}.
 * To include the double quote character in the attribute name, write it
 * twice.  {@code "foo""bar""baz"} represents the attribute called
 * {@code foo"bar"baz}.
 *
 * <p>String constants are written with single quotes like {@code 'this'}.  A
 * single quote within a string constant must be doubled, for example
 * {@code 'can''t'}.</p>
 *
 * <p>Integer constants are written as a sequence of decimal digits,
 * optionally preceded by a plus or minus sign.  An integer constant must be
 * a valid input to {@link Long#valueOf(String)}.</p>
 *
 * <p>Floating-point constants are written using the Java syntax.  A
 * floating-point constant must be a valid input to
 * {@link Double#valueOf(String)}.</p>
 *
 * <p>A boolean constant is either {@code true} or {@code false}, ignoring
 * case.</p>
 *
 * <p>Spaces cannot appear inside identifiers (unless written with double
 * quotes) or keywords or multi-character tokens such as {@code <=}. Spaces can
 * appear anywhere else, but are not required except to separate tokens. For
 * example, the query {@code a < b and 5 = c} could also be written {@code a<b
 * and 5=c}, but no further spaces can be removed.</p>
 *
 *
 * <h4 id="grammar-ql">Grammar</h4>
 *
 * <dl>
 * <dt id="query">query:
 * <dd><a href="#andquery">andquery</a> [<b>OR</b> <a href="#query">query</a>]
 *
 * <dt id="andquery">andquery:
 * <dd><a href="#predicate">predicate</a> [<b>AND</b> <a href="#andquery">andquery</a>]
 *
 * <dt id="predicate">predicate:
 * <dd><b>(</b> <a href="#query">query</a> <b>)</b> |<br>
 *     <b>NOT</b> <a href="#predicate">predicate</a> |<br>
 *     <b>INSTANCEOF</b> <a href="#stringvalue">stringvalue</a> |<br>
 *     <b>LIKE</b> <a href="#objectnamepattern">objectnamepattern</a> |<br>
 *     <a href="#value">value</a> <a href="#predrhs">predrhs</a>
 *
 * <dt id="predrhs">predrhs:
 * <dd><a href="#compare">compare</a> <a href="#value">value</a> |<br>
 *     [<b>NOT</b>] <b>BETWEEN</b> <a href="#value">value</a> <b>AND</b>
 *         <a href="#value">value</a> |<br>
 *     [<b>NOT</b>] <b>IN (</b> <a href="#value">value</a>
 *           <a href="#commavalues">commavalues</a> <b>)</b> |<br>
 *     [<b>NOT</b>] <b>LIKE</b> <a href="#stringvalue">stringvalue</a>
 *
 * <dt id="commavalues">commavalues:
 * <dd>[ <b>,</b> <a href="#value">value</a> <a href="#commavalues">commavalues</a> ]
 *
 * <dt id="compare">compare:
 * <dd><b>=</b> | <b>&lt;</b> | <b>&gt;</b> |
 *     <b>&lt;=</b> | <b>&gt;=</b> | <b>&lt;&gt;</b> | <b>!=</b>
 *
 * <dt id="value">value:
 * <dd><a href="#factor">factor</a> [<a href="#plusorminus">plusorminus</a>
 *     <a href="#value">value</a>]
 *
 * <dt id="plusorminus">plusorminus:
 * <dd><b>+</b> | <b>-</b>
 *
 * <dt id="factor">factor:
 * <dd><a href="#term">term</a> [<a href="#timesordivide">timesordivide</a>
 *     <a href="#factor">factor</a>]
 *
 * <dt id="timesordivide">timesordivide:
 * <dd><b>*</b> | <b>/</b>
 *
 * <dt id="term">term:
 * <dd><a href="#attr">attr</a> | <a href="#literal">literal</a> |
 *     <b>(</b> <a href="#value">value</a> <b>)</b>
 *
 * <dt id="attr">attr:
 * <dd><a href="#name">name</a> [<b>#</b> <a href="#name">name</a>]
 *
 * <dt id="name">name:
 * <dd><a href="#identifier">identifier</a> [<b>.</b><a href="#name">name</a>]
 *
 * <dt id="identifier">identifier:
 * <dd><i>Java-identifier</i> | <i>double-quoted-identifier</i>
 *
 * <dt id="literal">literal:
 * <dd><a href="#booleanlit">booleanlit</a> | <i>longlit</i> |
 *     <i>doublelit</i> | <i>stringlit</i>
 *
 * <dt id="booleanlit">booleanlit:
 * <dd><b>FALSE</b> | <b>TRUE</b>
 *
 * <dt id="stringvalue">stringvalue:
 * <dd><i>stringlit</i>
 *
 * <dt id="objectnamepattern">objectnamepattern:
 * <dd><i>stringlit</i>
 *
 * </dl>
 *
 *
 * <h4>Semantics</h4>
 *
 * <p>The meaning of the grammar is described in the table below.
 * This defines a function <i>q</i> that maps a string to a Java object
 * such as a {@link QueryExp} or a {@link ValueExp}.</p>
 *
 * <table border="1" cellpadding="5">
 * <tr><th>String <i>s</i></th><th><i>q(s)</th></tr>
 *
 * <tr><td><i>query1</i> <b>OR</b> <i>query2</i>
 *     <td>{@link Query#or Query.or}(<i>q(query1)</i>, <i>q(query2)</i>)
 *
 * <tr><td><i>query1</i> <b>AND</b> <i>query2</i>
 *     <td>{@link Query#and Query.and}(<i>q(query1)</i>, <i>q(query2)</i>)
 *
 * <tr><td><b>(</b> <i>queryOrValue</i> <b>)</b>
 *     <td><i>q(queryOrValue)</i>
 *
 * <tr><td><b>NOT</b> <i>query</i>
 *     <td>{@link Query#not Query.not}(<i>q(query)</i>)
 *
 * <tr><td><b>INSTANCEOF</b> <i>stringLiteral</i>
 *     <td>{@link Query#isInstanceOf Query.isInstanceOf}(<!--
 * -->{@link Query#value(String) Query.value}(<i>q(stringLiteral)</i>))
 *
 * <tr><td><b>LIKE</b> <i>stringLiteral</i>
 *     <td>{@link ObjectName#ObjectName(String) new ObjectName}(<!--
 * --><i>q(stringLiteral)</i>)
 *
 * <tr><td><i>value1</i> <b>=</b> <i>value2</i>
 *     <td>{@link Query#eq Query.eq}(<i>q(value1)</i>, <i>q(value2)</i>)
 *
 * <tr><td><i>value1</i> <b>&lt;</b> <i>value2</i>
 *     <td>{@link Query#lt Query.lt}(<i>q(value1)</i>, <i>q(value2)</i>)
 *
 * <tr><td><i>value1</i> <b>&gt;</b> <i>value2</i>
 *     <td>{@link Query#gt Query.gt}(<i>q(value1)</i>, <i>q(value2)</i>)
 *
 * <tr><td><i>value1</i> <b>&lt;=</b> <i>value2</i>
 *     <td>{@link Query#leq Query.leq}(<i>q(value1)</i>, <i>q(value2)</i>)
 *
 * <tr><td><i>value1</i> <b>&gt;=</b> <i>value2</i>
 *     <td>{@link Query#geq Query.geq}(<i>q(value1)</i>, <i>q(value2)</i>)
 *
 * <tr><td><i>value1</i> <b>&lt;&gt;</b> <i>value2</i>
 *     <td>{@link Query#not Query.not}({@link Query#eq Query.eq}(<!--
 * --><i>q(value1)</i>, <i>q(value2)</i>))
 *
 * <tr><td><i>value1</i> <b>!=</b> <i>value2</i>
 *     <td>{@link Query#not Query.not}({@link Query#eq Query.eq}(<!--
 * --><i>q(value1)</i>, <i>q(value2)</i>))
 *
 * <tr><td><i>value1</i> <b>BETWEEN</b> <i>value2</i> AND <i>value3</i>
 *     <td>{@link Query#between Query.between}(<i>q(value1)</i>,
 *         <i>q(value2)</i>, <i>q(value3)</i>)
 *
 * <tr><td><i>value1</i> <b>NOT BETWEEN</b> <i>value2</i> AND <i>value3</i>
 *     <td>{@link Query#not Query.not}({@link Query#between Query.between}(<!--
 * --><i>q(value1)</i>, <i>q(value2)</i>, <i>q(value3)</i>))
 *
 * <tr><td><i>value1</i> <b>IN (</b> <i>value2</i>, <i>value3</i> <b>)</b>
 *     <td>{@link Query#in Query.in}(<i>q(value1)</i>,
 *         <code>new ValueExp[] {</code>
 *         <i>q(value2)</i>, <i>q(value3)</i><code>}</code>)
 *
 * <tr><td><i>value1</i> <b>NOT IN (</b> <i>value2</i>, <i>value3</i> <b>)</b>
 *     <td>{@link Query#not Query.not}({@link Query#in Query.in}(<i>q(value1)</i>,
 *         <code>new ValueExp[] {</code>
 *         <i>q(value2)</i>, <i>q(value3)</i><code>}</code>))
 *
 * <tr><td><i>value</i> <b>LIKE</b> <i>stringLiteral</i>
 *     <td>{@link Query#match Query.match}(<i>q(value)</i>,
 *         <i><a href="#translateWildcards">translateWildcards</a>(q(stringLiteral))</i>)
 *
 * <tr><td><i>value</i> <b>NOT LIKE</b> <i>stringLiteral</i>
 *     <td>{@link Query#not Query.not}({@link Query#match Query.match}(<i>q(value)</i>,
 *         <i><a href="#translateWildcards">translateWildcards</a>(q(stringLiteral))</i>))
 *
 * <tr><td><i>value1</i> <b>+</b> <i>value2</i>
 *     <td>{@link Query#plus Query.plus}(<i>q(value1)</i>, <i>q(value2)</i>)
 *
 * <tr><td><i>value1</i> <b>-</b> <i>value2</i>
 *     <td>{@link Query#minus Query.minus}(<i>q(value1)</i>, <i>q(value2)</i>)
 *
 * <tr><td><i>value1</i> <b>*</b> <i>value2</i>
 *     <td>{@link Query#times Query.times}(<i>q(value1)</i>, <i>q(value2)</i>)
 *
 * <tr><td><i>value1</i> <b>/</b> <i>value2</i>
 *     <td>{@link Query#div Query.div}(<i>q(value1)</i>, <i>q(value2)</i>)
 *
 * <tr><td><i>name</i>
 *     <td>{@link Query#attr(String) Query.attr}(<i>q(name)</i>)
 *
 * <tr><td><i>name1<b>#</b>name2</i>
 *     <td>{@link Query#attr(String,String) Query.attr}(<i>q(name1)</i>,
 *         <i>q(name2)</i>)
 *
 * <tr><td><b>FALSE</b>
 *     <td>{@link Query#value(boolean) Query.value}(false)
 *
 * <tr><td><b>TRUE</b>
 *     <td>{@link Query#value(boolean) Query.value}(true)
 *
 * <tr><td><i>decimalLiteral</i>
 *     <td>{@link Query#value(long) Query.value}(<!--
 * -->{@link Long#valueOf(String) Long.valueOf}(<i>decimalLiteral</i>))
 *
 * <tr><td><i>floatingPointLiteral</i>
 *     <td>{@link Query#value(double) Query.value}(<!--
 * -->{@link Double#valueOf(String) Double.valueOf}(<!--
 * --><i>floatingPointLiteral</i>))
 * </table>
 *
 * <p id="translateWildcards">Here, <i>translateWildcards</i> is a function
 * that translates from the SQL notation for wildcards, using {@code %} and
 * {@code _}, to the JMX API notation, using {@code *} and {@code ?}.  If the
 * <b>LIKE</b> string already contains {@code *} or {@code ?}, these characters
 * have their literal meanings, and will be quoted in the call to
 * {@link Query#match Query.match}.</p>
 *
 * @since 1.5
 */
 public class Query extends Object   {


     /**
      * A code representing the {@link Query#gt} query.  This is chiefly
      * of interest for the serialized form of queries.
      */
     public static final int GT  = 0;

     /**
      * A code representing the {@link Query#lt} query.  This is chiefly
      * of interest for the serialized form of queries.
      */
     public static final int LT  = 1;

     /**
      * A code representing the {@link Query#geq} query.  This is chiefly
      * of interest for the serialized form of queries.
      */
     public static final int GE  = 2;

     /**
      * A code representing the {@link Query#leq} query.  This is chiefly
      * of interest for the serialized form of queries.
      */
     public static final int LE  = 3;

     /**
      * A code representing the {@link Query#eq} query.  This is chiefly
      * of interest for the serialized form of queries.
      */
     public static final int EQ  = 4;


     /**
      * A code representing the {@link Query#plus} expression.  This
      * is chiefly of interest for the serialized form of queries.
      */
     public static final int PLUS  = 0;

     /**
      * A code representing the {@link Query#minus} expression.  This
      * is chiefly of interest for the serialized form of queries.
      */
     public static final int MINUS = 1;

     /**
      * A code representing the {@link Query#times} expression.  This
      * is chiefly of interest for the serialized form of queries.
      */
     public static final int TIMES = 2;

     /**
      * A code representing the {@link Query#div} expression.  This is
      * chiefly of interest for the serialized form of queries.
      */
     public static final int DIV   = 3;


     /**
      * Basic constructor.
      */
     public Query() {
     }


     /**
      * Returns a query expression that is the conjunction of two other query
      * expressions.
      *
      * @param q1 A query expression.
      * @param q2 Another query expression.
      *
      * @return  The conjunction of the two arguments.  The returned object
      * will be serialized as an instance of the non-public class {@link
      * <a href="../../serialized-form.html#javax.management.AndQueryExp">
      * javax.management.AndQueryExp</a>}.
      */
     public static QueryExp and(QueryExp q1, QueryExp q2)  {
         return new AndQueryExp(q1, q2);
     }

     /**
      * Returns a query expression that is the disjunction of two other query
      * expressions.
      *
      * @param q1 A query expression.
      * @param q2 Another query expression.
      *
      * @return  The disjunction of the two arguments.  The returned object
      * will be serialized as an instance of the non-public class {@link
      * <a href="../../serialized-form.html#javax.management.OrQueryExp">
      * javax.management.OrQueryExp</a>}.
      */
     public static QueryExp or(QueryExp q1, QueryExp q2)  {
         return new OrQueryExp(q1, q2);
     }

     /**
      * Returns a query expression that represents a "greater than" constraint on
      * two values.
      *
      * @param v1 A value expression.
      * @param v2 Another value expression.
      *
      * @return A "greater than" constraint on the arguments.  The
      * returned object will be serialized as an instance of the
      * non-public class {@link <a
      * href="../../serialized-form.html#javax.management.BinaryRelQueryExp">
      * javax.management.BinaryRelQueryExp</a>} with a {@code relOp} equal
      * to {@link #GT}.
      */
     public static QueryExp gt(ValueExp v1, ValueExp v2)  {
         return new BinaryRelQueryExp(GT, v1, v2);
     }

     /**
      * Returns a query expression that represents a "greater than or equal
      * to" constraint on two values.
      *
      * @param v1 A value expression.
      * @param v2 Another value expression.
      *
      * @return A "greater than or equal to" constraint on the
      * arguments.  The returned object will be serialized as an
      * instance of the non-public class {@link <a
      * href="../../serialized-form.html#javax.management.BinaryRelQueryExp">
      * javax.management.BinaryRelQueryExp</a>} with a {@code relOp} equal
      * to {@link #GE}.
      */
     public static QueryExp geq(ValueExp v1, ValueExp v2)  {
         return new BinaryRelQueryExp(GE, v1, v2);
     }

     /**
      * Returns a query expression that represents a "less than or equal to"
      * constraint on two values.
      *
      * @param v1 A value expression.
      * @param v2 Another value expression.
      *
      * @return A "less than or equal to" constraint on the arguments.
      * The returned object will be serialized as an instance of the
      * non-public class {@link <a
      * href="../../serialized-form.html#javax.management.BinaryRelQueryExp">
      * javax.management.BinaryRelQueryExp</a>} with a {@code relOp} equal
      * to {@link #LE}.
      */
     public static QueryExp leq(ValueExp v1, ValueExp v2)  {
         return new BinaryRelQueryExp(LE, v1, v2);
     }

     /**
      * Returns a query expression that represents a "less than" constraint on
      * two values.
      *
      * @param v1 A value expression.
      * @param v2 Another value expression.
      *
      * @return A "less than" constraint on the arguments.  The
      * returned object will be serialized as an instance of the
      * non-public class {@link <a
      * href="../../serialized-form.html#javax.management.BinaryRelQueryExp">
      * javax.management.BinaryRelQueryExp</a>} with a {@code relOp} equal
      * to {@link #LT}.
      */
     public static QueryExp lt(ValueExp v1, ValueExp v2)  {
         return new BinaryRelQueryExp(LT, v1, v2);
     }

     /**
      * Returns a query expression that represents an equality constraint on
      * two values.
      *
      * @param v1 A value expression.
      * @param v2 Another value expression.
      *
      * @return A "equal to" constraint on the arguments.  The
      * returned object will be serialized as an instance of the
      * non-public class {@link <a
      * href="../../serialized-form.html#javax.management.BinaryRelQueryExp">
      * javax.management.BinaryRelQueryExp</a>} with a {@code relOp} equal
      * to {@link #EQ}.
      */
     public static QueryExp eq(ValueExp v1, ValueExp v2)  {
         return new BinaryRelQueryExp(EQ, v1, v2);
     }

     /**
      * Returns a query expression that represents the constraint that one
      * value is between two other values.
      *
      * @param v1 A value expression that is "between" v2 and v3.
      * @param v2 Value expression that represents a boundary of the constraint.
      * @param v3 Value expression that represents a boundary of the constraint.
      *
      * @return The constraint that v1 lies between v2 and v3.  The
      * returned object will be serialized as an instance of the
      * non-public class {@link <a
      * href="../../serialized-form.html#javax.management.BetweenQueryExp">
      * javax.management.BetweenQueryExp</a>}.
      */
     public static QueryExp between(ValueExp v1, ValueExp v2, ValueExp v3) {
         return new BetweenQueryExp(v1, v2, v3);
     }

     /**
      * Returns a query expression that represents a matching constraint on
      * a string argument. The matching syntax is consistent with file globbing:
      * supports "<code>?</code>", "<code>*</code>", "<code>[</code>",
      * each of which may be escaped with "<code>\</code>";
      * character classes may use "<code>!</code>" for negation and
      * "<code>-</code>" for range.
      * (<code>*</code> for any character sequence,
      * <code>?</code> for a single arbitrary character,
      * <code>[...]</code> for a character sequence).
      * For example: <code>a*b?c</code> would match a string starting
      * with the character <code>a</code>, followed
      * by any number of characters, followed by a <code>b</code>,
      * any single character, and a <code>c</code>.
      *
      * @param a An attribute expression
      * @param s A string value expression representing a matching constraint
      *
      * @return A query expression that represents the matching
      * constraint on the string argument.  The returned object will
      * be serialized as an instance of the non-public class {@link <a
      * href="../../serialized-form.html#javax.management.MatchQueryExp">
      * javax.management.MatchQueryExp</a>}.
      */
     public static QueryExp match(AttributeValueExp a, StringValueExp s)  {
         return new MatchQueryExp(a, s);
     }

     /**
      * <p>Returns a new attribute expression.  See {@link AttributeValueExp}
      * for a detailed description of the semantics of the expression.</p>
      *
      * @param name The name of the attribute.
      *
      * @return An attribute expression for the attribute named {@code name}.
      */
     public static AttributeValueExp attr(String name)  {
         return new AttributeValueExp(name);
     }

     /**
      * <p>Returns a new qualified attribute expression.</p>
      *
      * <p>Evaluating this expression for a given
      * <code>objectName</code> includes performing {@link
      * MBeanServer#getObjectInstance
      * MBeanServer.getObjectInstance(objectName)} and {@link
      * MBeanServer#getAttribute MBeanServer.getAttribute(objectName,
      * name)}.</p>
      *
      * @param className The name of the class possessing the attribute.
      * @param name The name of the attribute.
      *
      * @return An attribute expression for the attribute named name.
      * The returned object will be serialized as an instance of the
      * non-public class {@link <a
      * href="../../serialized-form.html#javax.management.QualifiedAttributeValueExp">
      * javax.management.QualifiedAttributeValueExp</a>}.
      */
     public static AttributeValueExp attr(String className, String name)  {
         return new QualifiedAttributeValueExp(className, name);
     }

     /**
      * <p>Returns a new class attribute expression which can be used in any
      * Query call that expects a ValueExp.</p>
      *
      * <p>Evaluating this expression for a given
      * <code>objectName</code> includes performing {@link
      * MBeanServer#getObjectInstance
      * MBeanServer.getObjectInstance(objectName)}.</p>
      *
      * @return A class attribute expression.  The returned object
      * will be serialized as an instance of the non-public class
      * {@link <a
      * href="../../serialized-form.html#javax.management.ClassAttributeValueExp">
      * javax.management.ClassAttributeValueExp</a>}.
      */
     public static AttributeValueExp classattr()  {
         return new ClassAttributeValueExp();
     }

     /**
      * Returns a constraint that is the negation of its argument.
      *
      * @param queryExp The constraint to negate.
      *
      * @return A negated constraint.  The returned object will be
      * serialized as an instance of the non-public class {@link <a
      * href="../../serialized-form.html#javax.management.NotQueryExp">
      * javax.management.NotQueryExp</a>}.
      */
     public static QueryExp not(QueryExp queryExp)  {
         return new NotQueryExp(queryExp);
     }

     /**
      * Returns an expression constraining a value to be one of an explicit list.
      *
      * @param val A value to be constrained.
      * @param valueList An array of ValueExps.
      *
      * @return A QueryExp that represents the constraint.  The
      * returned object will be serialized as an instance of the
      * non-public class {@link <a
      * href="../../serialized-form.html#javax.management.InQueryExp">
      * javax.management.InQueryExp</a>}.
      */
     public static QueryExp in(ValueExp val, ValueExp valueList[])  {
         return new InQueryExp(val, valueList);
     }

     /**
      * Returns a new string expression.
      *
      * @param val The string value.
      *
      * @return  A ValueExp object containing the string argument.
      */
     public static StringValueExp value(String val)  {
         return new StringValueExp(val);
     }

     /**
      * Returns a numeric value expression that can be used in any Query call
      * that expects a ValueExp.
      *
      * @param val An instance of Number.
      *
      * @return A ValueExp object containing the argument.  The
      * returned object will be serialized as an instance of the
      * non-public class {@link <a
      * href="../../serialized-form.html#javax.management.NumericValueExp">
      * javax.management.NumericValueExp</a>}.
      */
     public static ValueExp value(Number val)  {
         return new NumericValueExp(val);
     }

     /**
      * Returns a numeric value expression that can be used in any Query call
      * that expects a ValueExp.
      *
      * @param val An int value.
      *
      * @return A ValueExp object containing the argument.  The
      * returned object will be serialized as an instance of the
      * non-public class {@link <a
      * href="../../serialized-form.html#javax.management.NumericValueExp">
      * javax.management.NumericValueExp</a>}.
      */
     public static ValueExp value(int val)  {
         return new NumericValueExp((long) val);
     }

     /**
      * Returns a numeric value expression that can be used in any Query call
      * that expects a ValueExp.
      *
      * @param val A long value.
      *
      * @return A ValueExp object containing the argument.  The
      * returned object will be serialized as an instance of the
      * non-public class {@link <a
      * href="../../serialized-form.html#javax.management.NumericValueExp">
      * javax.management.NumericValueExp</a>}.
      */
     public static ValueExp value(long val)  {
         return new NumericValueExp(val);
     }

     /**
      * Returns a numeric value expression that can be used in any Query call
      * that expects a ValueExp.
      *
      * @param val A float value.
      *
      * @return A ValueExp object containing the argument.  The
      * returned object will be serialized as an instance of the
      * non-public class {@link <a
      * href="../../serialized-form.html#javax.management.NumericValueExp">
      * javax.management.NumericValueExp</a>}.
      */
     public static ValueExp value(float val)  {
         return new NumericValueExp((double) val);
     }

     /**
      * Returns a numeric value expression that can be used in any Query call
      * that expects a ValueExp.
      *
      * @param val A double value.
      *
      * @return  A ValueExp object containing the argument.  The
      * returned object will be serialized as an instance of the
      * non-public class {@link <a
      * href="../../serialized-form.html#javax.management.NumericValueExp">
      * javax.management.NumericValueExp</a>}.
      */
     public static ValueExp value(double val)  {
         return new NumericValueExp(val);
     }

     /**
      * Returns a boolean value expression that can be used in any Query call
      * that expects a ValueExp.
      *
      * @param val A boolean value.
      *
      * @return A ValueExp object containing the argument.  The
      * returned object will be serialized as an instance of the
      * non-public class {@link <a
      * href="../../serialized-form.html#javax.management.BooleanValueExp">
      * javax.management.BooleanValueExp</a>}.
      */
     public static ValueExp value(boolean val)  {
         return new BooleanValueExp(val);
     }

     /**
      * Returns a binary expression representing the sum of two numeric values,
      * or the concatenation of two string values.
      *
      * @param value1 The first '+' operand.
      * @param value2 The second '+' operand.
      *
      * @return A ValueExp representing the sum or concatenation of
      * the two arguments.  The returned object will be serialized as
      * an instance of the non-public class {@link <a
      * href="../../serialized-form.html#javax.management.BinaryOpValueExp">
      * javax.management.BinaryOpValueExp</a>} with an {@code op} equal to
      * {@link #PLUS}.
      */
     public static ValueExp plus(ValueExp value1, ValueExp value2) {
         return new BinaryOpValueExp(PLUS, value1, value2);
     }

     /**
      * Returns a binary expression representing the product of two numeric values.
      *
      *
      * @param value1 The first '*' operand.
      * @param value2 The second '*' operand.
      *
      * @return A ValueExp representing the product.  The returned
      * object will be serialized as an instance of the non-public
      * class {@link <a
      * href="../../serialized-form.html#javax.management.BinaryOpValueExp">
      * javax.management.BinaryOpValueExp</a>} with an {@code op} equal to
      * {@link #TIMES}.
      */
     public static ValueExp times(ValueExp value1,ValueExp value2) {
         return new BinaryOpValueExp(TIMES, value1, value2);
     }

     /**
      * Returns a binary expression representing the difference between two numeric
      * values.
      *
      * @param value1 The first '-' operand.
      * @param value2 The second '-' operand.
      *
      * @return A ValueExp representing the difference between two
      * arguments.  The returned object will be serialized as an
      * instance of the non-public class {@link <a
      * href="../../serialized-form.html#javax.management.BinaryOpValueExp">
      * javax.management.BinaryOpValueExp</a>} with an {@code op} equal to
      * {@link #MINUS}.
      */
     public static ValueExp minus(ValueExp value1, ValueExp value2) {
         return new BinaryOpValueExp(MINUS, value1, value2);
     }

     /**
      * Returns a binary expression representing the quotient of two numeric
      * values.
      *
      * @param value1 The first '/' operand.
      * @param value2 The second '/' operand.
      *
      * @return A ValueExp representing the quotient of two arguments.
      * The returned object will be serialized as an instance of the
      * non-public class {@link <a
      * href="../../serialized-form.html#javax.management.BinaryOpValueExp">
      * javax.management.BinaryOpValueExp</a>} with an {@code op} equal to
      * {@link #DIV}.
      */
     public static ValueExp div(ValueExp value1, ValueExp value2) {
         return new BinaryOpValueExp(DIV, value1, value2);
     }

     /**
      * Returns a query expression that represents a matching constraint on
      * a string argument. The value must start with the given literal string
      * value.
      *
      * @param a An attribute expression.
      * @param s A string value expression representing the beginning of the
      * string value.
      *
      * @return The constraint that a matches s.  The returned object
      * will be serialized as an instance of the non-public class
      * {@link <a
      * href="../../serialized-form.html#javax.management.MatchQueryExp">
      * javax.management.MatchQueryExp</a>}.
      */
     public static QueryExp initialSubString(AttributeValueExp a, StringValueExp s)  {
         return new MatchQueryExp(a,
             new StringValueExp(escapeString(s.getValue()) + "*"));
     }

     /**
      * Returns a query expression that represents a matching constraint on
      * a string argument. The value must contain the given literal string
      * value.
      *
      * @param a An attribute expression.
      * @param s A string value expression representing the substring.
      *
      * @return The constraint that a matches s.  The returned object
      * will be serialized as an instance of the non-public class
      * {@link <a
      * href="../../serialized-form.html#javax.management.MatchQueryExp">
      * javax.management.MatchQueryExp</a>}.
      */
     public static QueryExp anySubString(AttributeValueExp a, StringValueExp s) {
         return new MatchQueryExp(a,
             new StringValueExp("*" + escapeString(s.getValue()) + "*"));
     }

     /**
      * Returns a query expression that represents a matching constraint on
      * a string argument. The value must end with the given literal string
      * value.
      *
      * @param a An attribute expression.
      * @param s A string value expression representing the end of the string
      * value.
      *
      * @return The constraint that a matches s.  The returned object
      * will be serialized as an instance of the non-public class
      * {@link <a
      * href="../../serialized-form.html#javax.management.MatchQueryExp">
      * javax.management.MatchQueryExp</a>}.
      */
     public static QueryExp finalSubString(AttributeValueExp a, StringValueExp s) {
         return new MatchQueryExp(a,
             new StringValueExp("*" + escapeString(s.getValue())));
     }

     /**
      * Returns a query expression that represents an inheritance constraint
      * on an MBean class.
      * <p>Example: to find MBeans that are instances of
      * {@link NotificationBroadcaster}, use
      * {@code Query.isInstanceOf(Query.value(NotificationBroadcaster.class.getName()))}.
      * </p>
      * <p>Evaluating this expression for a given
      * <code>objectName</code> includes performing {@link
      * MBeanServer#isInstanceOf MBeanServer.isInstanceOf(objectName,
      * ((StringValueExp)classNameValue.apply(objectName)).getValue()}.</p>
      *
      * @param classNameValue The {@link StringValueExp} returning the name
      *        of the class of which selected MBeans should be instances.
      * @return a query expression that represents an inheritance
      * constraint on an MBean class.  The returned object will be
      * serialized as an instance of the non-public class {@link <a
      * href="../../serialized-form.html#javax.management.InstanceOfQueryExp">
      * javax.management.InstanceOfQueryExp</a>}.
      * @since 1.6
      */
     public static QueryExp isInstanceOf(StringValueExp classNameValue) {
        return new InstanceOfQueryExp(classNameValue);
     }

     /**
      * <p>Return a string representation of the given query.  The string
      * returned by this method can be converted back into an equivalent
      * query using {@link #fromString fromString}.</p>
      *
      * <p>(Two queries are equivalent if they produce the same result in
      * all cases.  Equivalent queries are not necessarily identical:
      * for example the queries {@code Query.lt(Query.attr("A"), Query.attr("B"))}
      * and {@code Query.not(Query.ge(Query.attr("A"), Query.attr("B")))} are
      * equivalent but not identical.)</p>
      *
      * <p>The string returned by this method is only guaranteed to be converted
      * back into an equivalent query if {@code query} was constructed, or
      * could have been constructed, using the methods of this class.
      * If you make a custom query {@code myQuery} by implementing
      * {@link QueryExp} yourself then the result of
      * {@code Query.toString(myQuery)} is unspecified.</p>
      *
      * @param query the query to convert.  If it is null, the result will
      * also be null.
      * @return the string representation of the query, or null if the
      * query is null.
      *
      * @since 1.7
      */
     public static String toString(QueryExp query) {
         if (query == null)
             return null;

         // This is ugly. At one stage we had a non-public class called
         // ToQueryString with the toQueryString() method, and every class
         // mentioned here inherited from that class. But that interfered
         // with serialization of custom subclasses of e.g. QueryEval. Even
         // though we could make it work by adding a public constructor to this
         // non-public class, that seemed fragile because according to the
         // serialization spec it shouldn't work. If only non-public interfaces
         // could have non-public methods.
         if (query instanceof ObjectName)
             return ((ObjectName) query).toQueryString();
         if (query instanceof QueryEval)
             return ((QueryEval) query).toQueryString();

         return query.toString();
     }

     /**
      * <p>Produce a query from the given string.  The query returned
      * by this method can be converted back into a string using
      * {@link #toString(QueryExp) toString}.  The resultant string will
      * not necessarily be equal to {@code s}.</p>
      *
      * @param s the string to convert.
      *
      * @return a {@code QueryExp} derived by parsing the string, or
      * null if the string is null.
      *
      * @throws IllegalArgumentException if the string is not a valid
      * query string.
      *
      * @since 1.7
      */
     public static QueryExp fromString(String s) {
         if (s == null)
             return null;
         return new QueryParser(s).parseQuery();
     }

     /**
      * Utility method to escape strings used with
      * Query.{initial|any|final}SubString() methods.
      */
     private static String escapeString(String s) {
         if (s == null)
             return null;
         s = s.replace("\\", "\\\\");
         s = s.replace("*", "\\*");
         s = s.replace("?", "\\?");
         s = s.replace("[", "\\[");
         return s;
     }
 }
