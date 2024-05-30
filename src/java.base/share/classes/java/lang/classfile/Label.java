/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package java.lang.classfile;

import jdk.internal.classfile.impl.LabelImpl;
import jdk.internal.javac.PreviewFeature;

/**
 * A marker for a position within the instructions of a method body.  The
 * association between a label's identity and the position it represents is
 * managed by the entity managing the method body (a {@link CodeModel} or {@link
 * CodeBuilder}), not the label itself; this allows the same label to have a
 * meaning both in an existing method (as managed by a {@linkplain CodeModel})
 * and in the transformation of that method (as managed by a {@linkplain
 * CodeBuilder}), while corresponding to different positions in each. When
 * traversing the elements of a {@linkplain CodeModel}, {@linkplain Label}
 * markers will be delivered at the position to which they correspond.  A label
 * can be bound to the current position within a {@linkplain CodeBuilder} via
 * {@link CodeBuilder#labelBinding(Label)} or {@link CodeBuilder#with(ClassFileElement)}.
 *
 * @since 22
 */
@PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
public sealed interface Label
        permits LabelImpl {
}
