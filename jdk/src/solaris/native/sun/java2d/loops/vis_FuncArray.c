/*
 * Copyright (c) 2003, 2010, Oracle and/or its affiliates. All rights reserved.
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

#include <stdlib.h>
#include <string.h>
#include <sys/utsname.h>
#include "GraphicsPrimitiveMgr.h"
#include "java2d_Mlib.h"

typedef struct {
    AnyFunc  *func_c;
    AnyFunc  *func_vis;
} AnyFunc_pair;

#define DEF_FUNC(x)    \
    void x();          \
    void ADD_SUFF(x)();

#define ADD_FUNC(x)    \
    { & x, & ADD_SUFF(x) }

/***************************************************************/

DEF_FUNC(AnyByteDrawGlyphList)
DEF_FUNC(AnyByteDrawGlyphListXor)
DEF_FUNC(AnyByteIsomorphicCopy)
DEF_FUNC(AnyByteIsomorphicScaleCopy)
DEF_FUNC(AnyByteIsomorphicXorCopy)
DEF_FUNC(AnyByteSetLine)
DEF_FUNC(AnyByteSetRect)
DEF_FUNC(AnyByteSetSpans)
DEF_FUNC(AnyByteSetParallelogram)
DEF_FUNC(AnyByteXorLine)
DEF_FUNC(AnyByteXorRect)
DEF_FUNC(AnyByteXorSpans)
DEF_FUNC(AnyShortDrawGlyphList)
DEF_FUNC(AnyShortDrawGlyphListXor)
DEF_FUNC(AnyShortIsomorphicCopy)
DEF_FUNC(AnyShortIsomorphicScaleCopy)
DEF_FUNC(AnyShortIsomorphicXorCopy)
DEF_FUNC(AnyShortSetLine)
DEF_FUNC(AnyShortSetRect)
DEF_FUNC(AnyShortSetSpans)
DEF_FUNC(AnyShortSetParallelogram)
DEF_FUNC(AnyShortXorLine)
DEF_FUNC(AnyShortXorRect)
DEF_FUNC(AnyShortXorSpans)
DEF_FUNC(Any3ByteDrawGlyphList)
DEF_FUNC(Any3ByteDrawGlyphListXor)
DEF_FUNC(Any3ByteIsomorphicCopy)
DEF_FUNC(Any3ByteIsomorphicScaleCopy)
DEF_FUNC(Any3ByteIsomorphicXorCopy)
DEF_FUNC(Any3ByteSetLine)
DEF_FUNC(Any3ByteSetRect)
DEF_FUNC(Any3ByteSetSpans)
DEF_FUNC(Any3ByteSetParallelogram)
DEF_FUNC(Any3ByteXorLine)
DEF_FUNC(Any3ByteXorRect)
DEF_FUNC(Any3ByteXorSpans)
DEF_FUNC(Any4ByteDrawGlyphList)
DEF_FUNC(Any4ByteDrawGlyphListXor)
DEF_FUNC(Any4ByteIsomorphicCopy)
DEF_FUNC(Any4ByteIsomorphicScaleCopy)
DEF_FUNC(Any4ByteIsomorphicXorCopy)
DEF_FUNC(Any4ByteSetLine)
DEF_FUNC(Any4ByteSetRect)
DEF_FUNC(Any4ByteSetSpans)
DEF_FUNC(Any4ByteSetParallelogram)
DEF_FUNC(Any4ByteXorLine)
DEF_FUNC(Any4ByteXorRect)
DEF_FUNC(Any4ByteXorSpans)
DEF_FUNC(AnyIntDrawGlyphList)
DEF_FUNC(AnyIntDrawGlyphListXor)
DEF_FUNC(AnyIntIsomorphicCopy)
DEF_FUNC(AnyIntIsomorphicScaleCopy)
DEF_FUNC(AnyIntIsomorphicXorCopy)
DEF_FUNC(AnyIntSetLine)
DEF_FUNC(AnyIntSetRect)
DEF_FUNC(AnyIntSetSpans)
DEF_FUNC(AnyIntSetParallelogram)
DEF_FUNC(AnyIntXorLine)
DEF_FUNC(AnyIntXorRect)
DEF_FUNC(AnyIntXorSpans)
DEF_FUNC(ByteGrayAlphaMaskFill)
DEF_FUNC(ByteGrayDrawGlyphListAA)
DEF_FUNC(ByteGraySrcMaskFill)
DEF_FUNC(ByteGraySrcOverMaskFill)
DEF_FUNC(ByteGrayToIntArgbConvert)
DEF_FUNC(ByteGrayToIntArgbScaleConvert)
DEF_FUNC(ByteIndexedBmToByteGrayScaleXparOver)
DEF_FUNC(ByteIndexedBmToByteGrayXparBgCopy)
DEF_FUNC(ByteIndexedBmToByteGrayXparOver)
DEF_FUNC(ByteIndexedToByteGrayConvert)
DEF_FUNC(ByteIndexedToByteGrayScaleConvert)
DEF_FUNC(Index12GrayToByteGrayConvert)
DEF_FUNC(Index12GrayToByteGrayScaleConvert)
DEF_FUNC(Index8GrayToByteGrayConvert)
DEF_FUNC(Index8GrayToByteGrayScaleConvert)
DEF_FUNC(IntArgbBmToByteGrayScaleXparOver)
DEF_FUNC(IntArgbBmToByteGrayXparBgCopy)
DEF_FUNC(IntArgbBmToByteGrayXparOver)
DEF_FUNC(IntArgbToByteGrayAlphaMaskBlit)
DEF_FUNC(IntArgbToByteGrayConvert)
DEF_FUNC(IntArgbToByteGrayScaleConvert)
DEF_FUNC(IntArgbToByteGraySrcOverMaskBlit)
DEF_FUNC(IntArgbToByteGrayXorBlit)
DEF_FUNC(IntRgbToByteGrayAlphaMaskBlit)
DEF_FUNC(ThreeByteBgrToByteGrayConvert)
DEF_FUNC(ThreeByteBgrToByteGrayScaleConvert)
DEF_FUNC(UshortGrayToByteGrayConvert)
DEF_FUNC(UshortGrayToByteGrayScaleConvert)
DEF_FUNC(ByteGrayToUshortGrayConvert)
DEF_FUNC(ByteGrayToUshortGrayScaleConvert)
DEF_FUNC(ByteIndexedBmToUshortGrayScaleXparOver)
DEF_FUNC(ByteIndexedBmToUshortGrayXparBgCopy)
DEF_FUNC(ByteIndexedBmToUshortGrayXparOver)
DEF_FUNC(ByteIndexedToUshortGrayConvert)
DEF_FUNC(ByteIndexedToUshortGrayScaleConvert)
DEF_FUNC(IntArgbBmToUshortGrayScaleXparOver)
DEF_FUNC(IntArgbToUshortGrayAlphaMaskBlit)
DEF_FUNC(IntArgbToUshortGrayConvert)
DEF_FUNC(IntArgbToUshortGrayScaleConvert)
DEF_FUNC(IntArgbToUshortGraySrcOverMaskBlit)
DEF_FUNC(IntArgbToUshortGrayXorBlit)
DEF_FUNC(IntRgbToUshortGrayAlphaMaskBlit)
DEF_FUNC(ThreeByteBgrToUshortGrayConvert)
DEF_FUNC(ThreeByteBgrToUshortGrayScaleConvert)
DEF_FUNC(UshortGrayAlphaMaskFill)
DEF_FUNC(UshortGrayDrawGlyphListAA)
DEF_FUNC(UshortGraySrcMaskFill)
DEF_FUNC(UshortGraySrcOverMaskFill)
DEF_FUNC(UshortGrayToIntArgbConvert)
DEF_FUNC(UshortGrayToIntArgbScaleConvert)
DEF_FUNC(ByteGrayToByteIndexedConvert)
DEF_FUNC(ByteGrayToByteIndexedScaleConvert)
DEF_FUNC(ByteIndexedAlphaMaskFill)
DEF_FUNC(ByteIndexedBmToByteIndexedScaleXparOver)
DEF_FUNC(ByteIndexedBmToByteIndexedXparBgCopy)
DEF_FUNC(ByteIndexedBmToByteIndexedXparOver)
DEF_FUNC(ByteIndexedDrawGlyphListAA)
DEF_FUNC(ByteIndexedToByteIndexedConvert)
DEF_FUNC(ByteIndexedToByteIndexedScaleConvert)
DEF_FUNC(Index12GrayToByteIndexedConvert)
DEF_FUNC(Index12GrayToByteIndexedScaleConvert)
DEF_FUNC(IntArgbBmToByteIndexedScaleXparOver)
DEF_FUNC(IntArgbBmToByteIndexedXparBgCopy)
DEF_FUNC(IntArgbBmToByteIndexedXparOver)
DEF_FUNC(IntArgbToByteIndexedAlphaMaskBlit)
DEF_FUNC(IntArgbToByteIndexedConvert)
DEF_FUNC(IntArgbToByteIndexedScaleConvert)
DEF_FUNC(IntArgbToByteIndexedXorBlit)
DEF_FUNC(IntRgbToByteIndexedAlphaMaskBlit)
DEF_FUNC(ThreeByteBgrToByteIndexedConvert)
DEF_FUNC(ThreeByteBgrToByteIndexedScaleConvert)
DEF_FUNC(ByteGrayToFourByteAbgrConvert)
DEF_FUNC(ByteGrayToFourByteAbgrScaleConvert)
DEF_FUNC(ByteIndexedBmToFourByteAbgrScaleXparOver)
DEF_FUNC(ByteIndexedBmToFourByteAbgrXparBgCopy)
DEF_FUNC(ByteIndexedBmToFourByteAbgrXparOver)
DEF_FUNC(ByteIndexedToFourByteAbgrConvert)
DEF_FUNC(ByteIndexedToFourByteAbgrScaleConvert)
DEF_FUNC(FourByteAbgrAlphaMaskFill)
DEF_FUNC(FourByteAbgrDrawGlyphListAA)
DEF_FUNC(FourByteAbgrSrcMaskFill)
DEF_FUNC(FourByteAbgrSrcOverMaskFill)
DEF_FUNC(FourByteAbgrToIntArgbConvert)
DEF_FUNC(FourByteAbgrToIntArgbScaleConvert)
DEF_FUNC(IntArgbBmToFourByteAbgrScaleXparOver)
DEF_FUNC(IntArgbToFourByteAbgrAlphaMaskBlit)
DEF_FUNC(IntArgbToFourByteAbgrConvert)
DEF_FUNC(IntArgbToFourByteAbgrScaleConvert)
DEF_FUNC(IntArgbToFourByteAbgrSrcOverMaskBlit)
DEF_FUNC(IntArgbToFourByteAbgrXorBlit)
DEF_FUNC(IntRgbToFourByteAbgrAlphaMaskBlit)
DEF_FUNC(IntRgbToFourByteAbgrConvert)
DEF_FUNC(IntRgbToFourByteAbgrScaleConvert)
DEF_FUNC(ThreeByteBgrToFourByteAbgrConvert)
DEF_FUNC(ThreeByteBgrToFourByteAbgrScaleConvert)
DEF_FUNC(ByteGrayToFourByteAbgrPreConvert)
DEF_FUNC(ByteGrayToFourByteAbgrPreScaleConvert)
DEF_FUNC(ByteIndexedBmToFourByteAbgrPreScaleXparOver)
DEF_FUNC(ByteIndexedBmToFourByteAbgrPreXparBgCopy)
DEF_FUNC(ByteIndexedBmToFourByteAbgrPreXparOver)
DEF_FUNC(ByteIndexedToFourByteAbgrPreConvert)
DEF_FUNC(ByteIndexedToFourByteAbgrPreScaleConvert)
DEF_FUNC(FourByteAbgrPreAlphaMaskFill)
DEF_FUNC(FourByteAbgrPreDrawGlyphListAA)
DEF_FUNC(FourByteAbgrPreSrcMaskFill)
DEF_FUNC(FourByteAbgrPreSrcOverMaskFill)
DEF_FUNC(FourByteAbgrPreToIntArgbConvert)
DEF_FUNC(FourByteAbgrPreToIntArgbScaleConvert)
DEF_FUNC(IntArgbBmToFourByteAbgrPreScaleXparOver)
DEF_FUNC(IntArgbToFourByteAbgrPreAlphaMaskBlit)
DEF_FUNC(IntArgbToFourByteAbgrPreConvert)
DEF_FUNC(IntArgbToFourByteAbgrPreScaleConvert)
DEF_FUNC(IntArgbToFourByteAbgrPreSrcOverMaskBlit)
DEF_FUNC(IntArgbToFourByteAbgrPreXorBlit)
DEF_FUNC(IntRgbToFourByteAbgrPreAlphaMaskBlit)
DEF_FUNC(IntRgbToFourByteAbgrPreConvert)
DEF_FUNC(IntRgbToFourByteAbgrPreScaleConvert)
DEF_FUNC(ThreeByteBgrToFourByteAbgrPreConvert)
DEF_FUNC(ThreeByteBgrToFourByteAbgrPreScaleConvert)
DEF_FUNC(ByteIndexedBmToIntArgbScaleXparOver)
DEF_FUNC(ByteIndexedBmToIntArgbXparBgCopy)
DEF_FUNC(ByteIndexedBmToIntArgbXparOver)
DEF_FUNC(ByteIndexedToIntArgbConvert)
DEF_FUNC(ByteIndexedToIntArgbScaleConvert)
DEF_FUNC(Index12GrayToIntArgbConvert)
DEF_FUNC(IntArgbAlphaMaskFill)
DEF_FUNC(IntArgbBmToIntArgbScaleXparOver)
DEF_FUNC(IntArgbDrawGlyphListAA)
DEF_FUNC(IntArgbSrcMaskFill)
DEF_FUNC(IntArgbSrcOverMaskFill)
DEF_FUNC(IntArgbToIntArgbAlphaMaskBlit)
DEF_FUNC(IntArgbToIntArgbSrcOverMaskBlit)
DEF_FUNC(IntArgbToIntArgbXorBlit)
DEF_FUNC(IntRgbToIntArgbAlphaMaskBlit)
DEF_FUNC(ByteIndexedBmToIntArgbBmScaleXparOver)
DEF_FUNC(ByteIndexedBmToIntArgbBmXparBgCopy)
DEF_FUNC(ByteIndexedBmToIntArgbBmXparOver)
DEF_FUNC(ByteIndexedToIntArgbBmConvert)
DEF_FUNC(ByteIndexedToIntArgbBmScaleConvert)
DEF_FUNC(IntArgbBmAlphaMaskFill)
DEF_FUNC(IntArgbBmDrawGlyphListAA)
DEF_FUNC(IntArgbBmToIntArgbConvert)
DEF_FUNC(IntArgbToIntArgbBmAlphaMaskBlit)
DEF_FUNC(IntArgbToIntArgbBmConvert)
DEF_FUNC(IntArgbToIntArgbBmScaleConvert)
DEF_FUNC(IntArgbToIntArgbBmXorBlit)
DEF_FUNC(ByteGrayToIntArgbPreConvert)
DEF_FUNC(ByteGrayToIntArgbPreScaleConvert)
DEF_FUNC(ByteIndexedBmToIntArgbPreScaleXparOver)
DEF_FUNC(ByteIndexedBmToIntArgbPreXparBgCopy)
DEF_FUNC(ByteIndexedBmToIntArgbPreXparOver)
DEF_FUNC(ByteIndexedToIntArgbPreConvert)
DEF_FUNC(ByteIndexedToIntArgbPreScaleConvert)
DEF_FUNC(IntArgbPreAlphaMaskFill)
DEF_FUNC(IntArgbPreDrawGlyphListAA)
DEF_FUNC(IntArgbPreSrcMaskFill)
DEF_FUNC(IntArgbPreSrcOverMaskFill)
DEF_FUNC(IntArgbPreToIntArgbConvert)
DEF_FUNC(IntArgbPreToIntArgbScaleConvert)
DEF_FUNC(IntArgbToIntArgbPreAlphaMaskBlit)
DEF_FUNC(IntArgbToIntArgbPreConvert)
DEF_FUNC(IntArgbToIntArgbPreScaleConvert)
DEF_FUNC(IntArgbToIntArgbPreSrcOverMaskBlit)
DEF_FUNC(IntArgbToIntArgbPreXorBlit)
DEF_FUNC(IntRgbToIntArgbPreAlphaMaskBlit)
DEF_FUNC(IntRgbToIntArgbPreConvert)
DEF_FUNC(IntRgbToIntArgbPreScaleConvert)
DEF_FUNC(ThreeByteBgrToIntArgbPreConvert)
DEF_FUNC(ThreeByteBgrToIntArgbPreScaleConvert)
DEF_FUNC(ByteIndexedBmToIntBgrScaleXparOver)
DEF_FUNC(ByteIndexedBmToIntBgrXparBgCopy)
DEF_FUNC(ByteIndexedBmToIntBgrXparOver)
DEF_FUNC(ByteIndexedToIntBgrConvert)
DEF_FUNC(ByteIndexedToIntBgrScaleConvert)
DEF_FUNC(IntArgbBmToIntBgrScaleXparOver)
DEF_FUNC(IntArgbBmToIntBgrXparBgCopy)
DEF_FUNC(IntArgbBmToIntBgrXparOver)
DEF_FUNC(IntArgbToIntBgrAlphaMaskBlit)
DEF_FUNC(IntArgbToIntBgrConvert)
DEF_FUNC(IntArgbToIntBgrScaleConvert)
DEF_FUNC(IntArgbToIntBgrSrcOverMaskBlit)
DEF_FUNC(IntArgbToIntBgrXorBlit)
DEF_FUNC(IntBgrAlphaMaskFill)
DEF_FUNC(IntBgrDrawGlyphListAA)
DEF_FUNC(IntBgrSrcMaskFill)
DEF_FUNC(IntBgrSrcOverMaskFill)
DEF_FUNC(IntBgrToIntArgbConvert)
DEF_FUNC(IntBgrToIntArgbScaleConvert)
DEF_FUNC(IntBgrToIntBgrAlphaMaskBlit)
DEF_FUNC(IntRgbToIntBgrAlphaMaskBlit)
DEF_FUNC(ThreeByteBgrToIntBgrConvert)
DEF_FUNC(ThreeByteBgrToIntBgrScaleConvert)
DEF_FUNC(ByteGrayToIntRgbConvert)
DEF_FUNC(ByteGrayToIntRgbScaleConvert)
DEF_FUNC(IntArgbBmToIntRgbXparBgCopy)
DEF_FUNC(IntArgbBmToIntRgbXparOver)
DEF_FUNC(IntArgbToIntRgbAlphaMaskBlit)
DEF_FUNC(IntArgbToIntRgbSrcOverMaskBlit)
DEF_FUNC(IntArgbToIntRgbXorBlit)
DEF_FUNC(IntRgbAlphaMaskFill)
DEF_FUNC(IntRgbDrawGlyphListAA)
DEF_FUNC(IntRgbSrcMaskFill)
DEF_FUNC(IntRgbSrcOverMaskFill)
DEF_FUNC(IntRgbToIntArgbConvert)
DEF_FUNC(IntRgbToIntArgbScaleConvert)
DEF_FUNC(IntRgbToIntRgbAlphaMaskBlit)
DEF_FUNC(ThreeByteBgrToIntRgbConvert)
DEF_FUNC(ThreeByteBgrToIntRgbScaleConvert)
DEF_FUNC(ByteGrayToIntRgbxConvert)
DEF_FUNC(ByteGrayToIntRgbxScaleConvert)
DEF_FUNC(ByteIndexedBmToIntRgbxScaleXparOver)
DEF_FUNC(ByteIndexedBmToIntRgbxXparBgCopy)
DEF_FUNC(ByteIndexedBmToIntRgbxXparOver)
DEF_FUNC(ByteIndexedToIntRgbxConvert)
DEF_FUNC(ByteIndexedToIntRgbxScaleConvert)
DEF_FUNC(IntArgbBmToIntRgbxScaleXparOver)
DEF_FUNC(IntArgbToIntRgbxConvert)
DEF_FUNC(IntArgbToIntRgbxScaleConvert)
DEF_FUNC(IntArgbToIntRgbxXorBlit)
DEF_FUNC(IntRgbxDrawGlyphListAA)
DEF_FUNC(IntRgbxToIntArgbConvert)
DEF_FUNC(IntRgbxToIntArgbScaleConvert)
DEF_FUNC(ThreeByteBgrToIntRgbxConvert)
DEF_FUNC(ThreeByteBgrToIntRgbxScaleConvert)
DEF_FUNC(ByteGrayToThreeByteBgrConvert)
DEF_FUNC(ByteGrayToThreeByteBgrScaleConvert)
DEF_FUNC(ByteIndexedBmToThreeByteBgrScaleXparOver)
DEF_FUNC(ByteIndexedBmToThreeByteBgrXparBgCopy)
DEF_FUNC(ByteIndexedBmToThreeByteBgrXparOver)
DEF_FUNC(ByteIndexedToThreeByteBgrConvert)
DEF_FUNC(ByteIndexedToThreeByteBgrScaleConvert)
DEF_FUNC(IntArgbBmToThreeByteBgrScaleXparOver)
DEF_FUNC(IntArgbBmToThreeByteBgrXparBgCopy)
DEF_FUNC(IntArgbBmToThreeByteBgrXparOver)
DEF_FUNC(IntArgbToThreeByteBgrAlphaMaskBlit)
DEF_FUNC(IntArgbToThreeByteBgrConvert)
DEF_FUNC(IntArgbToThreeByteBgrScaleConvert)
DEF_FUNC(IntArgbToThreeByteBgrSrcOverMaskBlit)
DEF_FUNC(IntArgbToThreeByteBgrXorBlit)
DEF_FUNC(IntRgbToThreeByteBgrAlphaMaskBlit)
DEF_FUNC(ThreeByteBgrAlphaMaskFill)
DEF_FUNC(ThreeByteBgrDrawGlyphListAA)
DEF_FUNC(ThreeByteBgrSrcMaskFill)
DEF_FUNC(ThreeByteBgrSrcOverMaskFill)
DEF_FUNC(ThreeByteBgrToIntArgbConvert)
DEF_FUNC(ThreeByteBgrToIntArgbScaleConvert)
DEF_FUNC(ByteGrayToIndex8GrayConvert)
DEF_FUNC(ByteGrayToIndex8GrayScaleConvert)
DEF_FUNC(ByteIndexedBmToIndex8GrayXparBgCopy)
DEF_FUNC(ByteIndexedBmToIndex8GrayXparOver)
DEF_FUNC(ByteIndexedToIndex8GrayConvert)
DEF_FUNC(ByteIndexedToIndex8GrayScaleConvert)
DEF_FUNC(Index12GrayToIndex8GrayConvert)
DEF_FUNC(Index12GrayToIndex8GrayScaleConvert)
DEF_FUNC(Index8GrayAlphaMaskFill)
DEF_FUNC(Index8GrayDrawGlyphListAA)
DEF_FUNC(Index8GraySrcOverMaskFill)
DEF_FUNC(Index8GrayToIndex8GrayConvert)
DEF_FUNC(Index8GrayToIndex8GrayScaleConvert)
DEF_FUNC(IntArgbToIndex8GrayAlphaMaskBlit)
DEF_FUNC(IntArgbToIndex8GrayConvert)
DEF_FUNC(IntArgbToIndex8GrayScaleConvert)
DEF_FUNC(IntArgbToIndex8GraySrcOverMaskBlit)
DEF_FUNC(IntArgbToIndex8GrayXorBlit)
DEF_FUNC(IntRgbToIndex8GrayAlphaMaskBlit)
DEF_FUNC(ThreeByteBgrToIndex8GrayConvert)
DEF_FUNC(ThreeByteBgrToIndex8GrayScaleConvert)
DEF_FUNC(UshortGrayToIndex8GrayScaleConvert)
DEF_FUNC(ByteGrayToIndex12GrayConvert)
DEF_FUNC(ByteGrayToIndex12GrayScaleConvert)
DEF_FUNC(ByteIndexedBmToIndex12GrayXparBgCopy)
DEF_FUNC(ByteIndexedBmToIndex12GrayXparOver)
DEF_FUNC(ByteIndexedToIndex12GrayConvert)
DEF_FUNC(ByteIndexedToIndex12GrayScaleConvert)
DEF_FUNC(Index12GrayAlphaMaskFill)
DEF_FUNC(Index12GrayDrawGlyphListAA)
DEF_FUNC(Index12GraySrcOverMaskFill)
DEF_FUNC(Index12GrayToIndex12GrayConvert)
DEF_FUNC(Index12GrayToIndex12GrayScaleConvert)
DEF_FUNC(Index12GrayToIntArgbScaleConvert)
DEF_FUNC(Index8GrayToIndex12GrayConvert)
DEF_FUNC(Index8GrayToIndex12GrayScaleConvert)
DEF_FUNC(IntArgbToIndex12GrayAlphaMaskBlit)
DEF_FUNC(IntArgbToIndex12GrayConvert)
DEF_FUNC(IntArgbToIndex12GrayScaleConvert)
DEF_FUNC(IntArgbToIndex12GraySrcOverMaskBlit)
DEF_FUNC(IntArgbToIndex12GrayXorBlit)
DEF_FUNC(IntRgbToIndex12GrayAlphaMaskBlit)
DEF_FUNC(ThreeByteBgrToIndex12GrayConvert)
DEF_FUNC(ThreeByteBgrToIndex12GrayScaleConvert)
DEF_FUNC(UshortGrayToIndex12GrayScaleConvert)
DEF_FUNC(ByteBinary1BitAlphaMaskFill)
DEF_FUNC(ByteBinary1BitDrawGlyphList)
DEF_FUNC(ByteBinary1BitDrawGlyphListAA)
DEF_FUNC(ByteBinary1BitDrawGlyphListXor)
DEF_FUNC(ByteBinary1BitSetLine)
DEF_FUNC(ByteBinary1BitSetRect)
DEF_FUNC(ByteBinary1BitSetSpans)
DEF_FUNC(ByteBinary1BitToByteBinary1BitConvert)
DEF_FUNC(ByteBinary1BitToIntArgbAlphaMaskBlit)
DEF_FUNC(ByteBinary1BitToIntArgbConvert)
DEF_FUNC(ByteBinary1BitXorLine)
DEF_FUNC(ByteBinary1BitXorRect)
DEF_FUNC(ByteBinary1BitXorSpans)
DEF_FUNC(IntArgbToByteBinary1BitAlphaMaskBlit)
DEF_FUNC(IntArgbToByteBinary1BitConvert)
DEF_FUNC(IntArgbToByteBinary1BitXorBlit)
DEF_FUNC(ByteBinary2BitAlphaMaskFill)
DEF_FUNC(ByteBinary2BitDrawGlyphList)
DEF_FUNC(ByteBinary2BitDrawGlyphListAA)
DEF_FUNC(ByteBinary2BitDrawGlyphListXor)
DEF_FUNC(ByteBinary2BitSetLine)
DEF_FUNC(ByteBinary2BitSetRect)
DEF_FUNC(ByteBinary2BitSetSpans)
DEF_FUNC(ByteBinary2BitToByteBinary2BitConvert)
DEF_FUNC(ByteBinary2BitToIntArgbAlphaMaskBlit)
DEF_FUNC(ByteBinary2BitToIntArgbConvert)
DEF_FUNC(ByteBinary2BitXorLine)
DEF_FUNC(ByteBinary2BitXorRect)
DEF_FUNC(ByteBinary2BitXorSpans)
DEF_FUNC(IntArgbToByteBinary2BitAlphaMaskBlit)
DEF_FUNC(IntArgbToByteBinary2BitConvert)
DEF_FUNC(IntArgbToByteBinary2BitXorBlit)
DEF_FUNC(ByteBinary4BitAlphaMaskFill)
DEF_FUNC(ByteBinary4BitDrawGlyphList)
DEF_FUNC(ByteBinary4BitDrawGlyphListAA)
DEF_FUNC(ByteBinary4BitDrawGlyphListXor)
DEF_FUNC(ByteBinary4BitSetLine)
DEF_FUNC(ByteBinary4BitSetRect)
DEF_FUNC(ByteBinary4BitSetSpans)
DEF_FUNC(ByteBinary4BitToByteBinary4BitConvert)
DEF_FUNC(ByteBinary4BitToIntArgbAlphaMaskBlit)
DEF_FUNC(ByteBinary4BitToIntArgbConvert)
DEF_FUNC(ByteBinary4BitXorLine)
DEF_FUNC(ByteBinary4BitXorRect)
DEF_FUNC(ByteBinary4BitXorSpans)
DEF_FUNC(IntArgbToByteBinary4BitAlphaMaskBlit)
DEF_FUNC(IntArgbToByteBinary4BitConvert)
DEF_FUNC(IntArgbToByteBinary4BitXorBlit)
DEF_FUNC(ByteGrayToUshort555RgbConvert)
DEF_FUNC(ByteGrayToUshort555RgbScaleConvert)
DEF_FUNC(ByteIndexedBmToUshort555RgbScaleXparOver)
DEF_FUNC(ByteIndexedBmToUshort555RgbXparBgCopy)
DEF_FUNC(ByteIndexedBmToUshort555RgbXparOver)
DEF_FUNC(ByteIndexedToUshort555RgbConvert)
DEF_FUNC(ByteIndexedToUshort555RgbScaleConvert)
DEF_FUNC(IntArgbBmToUshort555RgbScaleXparOver)
DEF_FUNC(IntArgbBmToUshort555RgbXparBgCopy)
DEF_FUNC(IntArgbBmToUshort555RgbXparOver)
DEF_FUNC(IntArgbToUshort555RgbAlphaMaskBlit)
DEF_FUNC(IntArgbToUshort555RgbConvert)
DEF_FUNC(IntArgbToUshort555RgbScaleConvert)
DEF_FUNC(IntArgbToUshort555RgbSrcOverMaskBlit)
DEF_FUNC(IntArgbToUshort555RgbXorBlit)
DEF_FUNC(IntRgbToUshort555RgbAlphaMaskBlit)
DEF_FUNC(ThreeByteBgrToUshort555RgbConvert)
DEF_FUNC(ThreeByteBgrToUshort555RgbScaleConvert)
DEF_FUNC(Ushort555RgbAlphaMaskFill)
DEF_FUNC(Ushort555RgbDrawGlyphListAA)
DEF_FUNC(Ushort555RgbSrcMaskFill)
DEF_FUNC(Ushort555RgbSrcOverMaskFill)
DEF_FUNC(Ushort555RgbToIntArgbConvert)
DEF_FUNC(Ushort555RgbToIntArgbScaleConvert)
DEF_FUNC(ByteGrayToUshort555RgbxConvert)
DEF_FUNC(ByteGrayToUshort555RgbxScaleConvert)
DEF_FUNC(ByteIndexedBmToUshort555RgbxScaleXparOver)
DEF_FUNC(ByteIndexedBmToUshort555RgbxXparBgCopy)
DEF_FUNC(ByteIndexedBmToUshort555RgbxXparOver)
DEF_FUNC(ByteIndexedToUshort555RgbxConvert)
DEF_FUNC(ByteIndexedToUshort555RgbxScaleConvert)
DEF_FUNC(IntArgbBmToUshort555RgbxScaleXparOver)
DEF_FUNC(IntArgbToUshort555RgbxConvert)
DEF_FUNC(IntArgbToUshort555RgbxScaleConvert)
DEF_FUNC(IntArgbToUshort555RgbxXorBlit)
DEF_FUNC(ThreeByteBgrToUshort555RgbxConvert)
DEF_FUNC(ThreeByteBgrToUshort555RgbxScaleConvert)
DEF_FUNC(Ushort555RgbxDrawGlyphListAA)
DEF_FUNC(Ushort555RgbxToIntArgbConvert)
DEF_FUNC(Ushort555RgbxToIntArgbScaleConvert)
DEF_FUNC(ByteGrayToUshort565RgbConvert)
DEF_FUNC(ByteGrayToUshort565RgbScaleConvert)
DEF_FUNC(ByteIndexedBmToUshort565RgbScaleXparOver)
DEF_FUNC(ByteIndexedBmToUshort565RgbXparBgCopy)
DEF_FUNC(ByteIndexedBmToUshort565RgbXparOver)
DEF_FUNC(ByteIndexedToUshort565RgbConvert)
DEF_FUNC(ByteIndexedToUshort565RgbScaleConvert)
DEF_FUNC(IntArgbBmToUshort565RgbScaleXparOver)
DEF_FUNC(IntArgbBmToUshort565RgbXparBgCopy)
DEF_FUNC(IntArgbBmToUshort565RgbXparOver)
DEF_FUNC(IntArgbToUshort565RgbAlphaMaskBlit)
DEF_FUNC(IntArgbToUshort565RgbConvert)
DEF_FUNC(IntArgbToUshort565RgbScaleConvert)
DEF_FUNC(IntArgbToUshort565RgbSrcOverMaskBlit)
DEF_FUNC(IntArgbToUshort565RgbXorBlit)
DEF_FUNC(IntRgbToUshort565RgbAlphaMaskBlit)
DEF_FUNC(ThreeByteBgrToUshort565RgbConvert)
DEF_FUNC(ThreeByteBgrToUshort565RgbScaleConvert)
DEF_FUNC(Ushort565RgbAlphaMaskFill)
DEF_FUNC(Ushort565RgbDrawGlyphListAA)
DEF_FUNC(Ushort565RgbSrcMaskFill)
DEF_FUNC(Ushort565RgbSrcOverMaskFill)
DEF_FUNC(Ushort565RgbToIntArgbConvert)
DEF_FUNC(Ushort565RgbToIntArgbScaleConvert)

/***************************************************************/

static AnyFunc_pair vis_func_pair_array[] = {
    ADD_FUNC(AnyByteDrawGlyphList),
    ADD_FUNC(AnyByteDrawGlyphListXor),
    ADD_FUNC(AnyByteIsomorphicCopy),
    ADD_FUNC(AnyByteIsomorphicScaleCopy),
    ADD_FUNC(AnyByteIsomorphicXorCopy),
    ADD_FUNC(AnyByteSetLine),
    ADD_FUNC(AnyByteSetRect),
    ADD_FUNC(AnyByteSetSpans),
    ADD_FUNC(AnyByteSetParallelogram),
    ADD_FUNC(AnyByteXorLine),
    ADD_FUNC(AnyByteXorRect),
    ADD_FUNC(AnyByteXorSpans),
    ADD_FUNC(AnyShortDrawGlyphList),
    ADD_FUNC(AnyShortDrawGlyphListXor),
    ADD_FUNC(AnyShortIsomorphicCopy),
    ADD_FUNC(AnyShortIsomorphicScaleCopy),
    ADD_FUNC(AnyShortIsomorphicXorCopy),
    ADD_FUNC(AnyShortSetLine),
    ADD_FUNC(AnyShortSetRect),
    ADD_FUNC(AnyShortSetSpans),
    ADD_FUNC(AnyShortSetParallelogram),
    ADD_FUNC(AnyShortXorLine),
    ADD_FUNC(AnyShortXorRect),
    ADD_FUNC(AnyShortXorSpans),
    ADD_FUNC(Any3ByteIsomorphicCopy),
    ADD_FUNC(Any3ByteIsomorphicScaleCopy),
    ADD_FUNC(Any3ByteIsomorphicXorCopy),
    ADD_FUNC(Any3ByteSetLine),
    ADD_FUNC(Any3ByteSetRect),
    ADD_FUNC(Any3ByteSetSpans),
    ADD_FUNC(Any3ByteSetParallelogram),
    ADD_FUNC(Any3ByteXorLine),
    ADD_FUNC(Any3ByteXorRect),
    ADD_FUNC(Any3ByteXorSpans),
    ADD_FUNC(Any4ByteDrawGlyphList),
    ADD_FUNC(Any4ByteDrawGlyphListXor),
    ADD_FUNC(Any4ByteIsomorphicCopy),
    ADD_FUNC(Any4ByteIsomorphicScaleCopy),
    ADD_FUNC(Any4ByteIsomorphicXorCopy),
    ADD_FUNC(Any4ByteSetLine),
    ADD_FUNC(Any4ByteSetRect),
    ADD_FUNC(Any4ByteSetSpans),
    ADD_FUNC(Any4ByteSetParallelogram),
    ADD_FUNC(Any4ByteXorLine),
    ADD_FUNC(Any4ByteXorRect),
    ADD_FUNC(Any4ByteXorSpans),
    ADD_FUNC(AnyIntDrawGlyphList),
    ADD_FUNC(AnyIntDrawGlyphListXor),
    ADD_FUNC(AnyIntIsomorphicCopy),
    ADD_FUNC(AnyIntIsomorphicScaleCopy),
    ADD_FUNC(AnyIntIsomorphicXorCopy),
    ADD_FUNC(AnyIntSetLine),
    ADD_FUNC(AnyIntSetRect),
    ADD_FUNC(AnyIntSetSpans),
    ADD_FUNC(AnyIntSetParallelogram),
    ADD_FUNC(AnyIntXorLine),
    ADD_FUNC(AnyIntXorRect),
    ADD_FUNC(AnyIntXorSpans),
    ADD_FUNC(ByteGrayAlphaMaskFill),
    ADD_FUNC(ByteGrayDrawGlyphListAA),
    ADD_FUNC(ByteGraySrcMaskFill),
    ADD_FUNC(ByteGraySrcOverMaskFill),
    ADD_FUNC(ByteGrayToIntArgbConvert),
    ADD_FUNC(ByteGrayToIntArgbScaleConvert),
    ADD_FUNC(ByteIndexedBmToByteGrayScaleXparOver),
    ADD_FUNC(ByteIndexedBmToByteGrayXparBgCopy),
    ADD_FUNC(ByteIndexedBmToByteGrayXparOver),
    ADD_FUNC(ByteIndexedToByteGrayConvert),
    ADD_FUNC(ByteIndexedToByteGrayScaleConvert),
    ADD_FUNC(Index12GrayToByteGrayConvert),
    ADD_FUNC(Index12GrayToByteGrayScaleConvert),
    ADD_FUNC(Index8GrayToByteGrayConvert),
    ADD_FUNC(Index8GrayToByteGrayScaleConvert),
    ADD_FUNC(IntArgbBmToByteGrayScaleXparOver),
    ADD_FUNC(IntArgbBmToByteGrayXparBgCopy),
    ADD_FUNC(IntArgbBmToByteGrayXparOver),
    ADD_FUNC(IntArgbToByteGrayAlphaMaskBlit),
    ADD_FUNC(IntArgbToByteGrayConvert),
    ADD_FUNC(IntArgbToByteGrayScaleConvert),
    ADD_FUNC(IntArgbToByteGraySrcOverMaskBlit),
    ADD_FUNC(IntArgbToByteGrayXorBlit),
    ADD_FUNC(IntRgbToByteGrayAlphaMaskBlit),
    ADD_FUNC(ThreeByteBgrToByteGrayConvert),
    ADD_FUNC(ThreeByteBgrToByteGrayScaleConvert),
    ADD_FUNC(UshortGrayToByteGrayConvert),
    ADD_FUNC(UshortGrayToByteGrayScaleConvert),
    ADD_FUNC(ByteGrayToUshortGrayConvert),
    ADD_FUNC(ByteGrayToUshortGrayScaleConvert),
    ADD_FUNC(ByteIndexedBmToUshortGrayScaleXparOver),
    ADD_FUNC(ByteIndexedBmToUshortGrayXparBgCopy),
    ADD_FUNC(ByteIndexedBmToUshortGrayXparOver),
    ADD_FUNC(ByteIndexedToUshortGrayConvert),
    ADD_FUNC(ByteIndexedToUshortGrayScaleConvert),
    ADD_FUNC(IntArgbBmToUshortGrayScaleXparOver),
    ADD_FUNC(IntArgbToUshortGrayConvert),
    ADD_FUNC(IntArgbToUshortGrayScaleConvert),
    ADD_FUNC(ThreeByteBgrToUshortGrayConvert),
    ADD_FUNC(ThreeByteBgrToUshortGrayScaleConvert),
    ADD_FUNC(UshortGrayToIntArgbConvert),
    ADD_FUNC(UshortGrayToIntArgbScaleConvert),
    ADD_FUNC(ByteGrayToByteIndexedConvert),
    ADD_FUNC(ByteGrayToByteIndexedScaleConvert),
    ADD_FUNC(ByteIndexedBmToByteIndexedScaleXparOver),
    ADD_FUNC(ByteIndexedBmToByteIndexedXparBgCopy),
    ADD_FUNC(ByteIndexedBmToByteIndexedXparOver),
    ADD_FUNC(ByteIndexedToByteIndexedConvert),
    ADD_FUNC(ByteIndexedToByteIndexedScaleConvert),
    ADD_FUNC(Index12GrayToByteIndexedConvert),
    ADD_FUNC(Index12GrayToByteIndexedScaleConvert),
    ADD_FUNC(IntArgbBmToByteIndexedScaleXparOver),
    ADD_FUNC(IntArgbBmToByteIndexedXparBgCopy),
    ADD_FUNC(IntArgbBmToByteIndexedXparOver),
    ADD_FUNC(IntArgbToByteIndexedConvert),
    ADD_FUNC(IntArgbToByteIndexedScaleConvert),
    ADD_FUNC(IntArgbToByteIndexedXorBlit),
    ADD_FUNC(ThreeByteBgrToByteIndexedConvert),
    ADD_FUNC(ThreeByteBgrToByteIndexedScaleConvert),
    ADD_FUNC(ByteGrayToFourByteAbgrConvert),
    ADD_FUNC(ByteGrayToFourByteAbgrScaleConvert),
    ADD_FUNC(ByteIndexedBmToFourByteAbgrScaleXparOver),
    ADD_FUNC(ByteIndexedBmToFourByteAbgrXparBgCopy),
    ADD_FUNC(ByteIndexedBmToFourByteAbgrXparOver),
    ADD_FUNC(ByteIndexedToFourByteAbgrConvert),
    ADD_FUNC(ByteIndexedToFourByteAbgrScaleConvert),
    ADD_FUNC(FourByteAbgrAlphaMaskFill),
    ADD_FUNC(FourByteAbgrDrawGlyphListAA),
    ADD_FUNC(FourByteAbgrSrcMaskFill),
    ADD_FUNC(FourByteAbgrSrcOverMaskFill),
    ADD_FUNC(FourByteAbgrToIntArgbConvert),
    ADD_FUNC(FourByteAbgrToIntArgbScaleConvert),
    ADD_FUNC(IntArgbBmToFourByteAbgrScaleXparOver),
    ADD_FUNC(IntArgbToFourByteAbgrAlphaMaskBlit),
    ADD_FUNC(IntArgbToFourByteAbgrConvert),
    ADD_FUNC(IntArgbToFourByteAbgrScaleConvert),
    ADD_FUNC(IntArgbToFourByteAbgrSrcOverMaskBlit),
    ADD_FUNC(IntArgbToFourByteAbgrXorBlit),
    ADD_FUNC(IntRgbToFourByteAbgrAlphaMaskBlit),
    ADD_FUNC(IntRgbToFourByteAbgrConvert),
    ADD_FUNC(IntRgbToFourByteAbgrScaleConvert),
    ADD_FUNC(ThreeByteBgrToFourByteAbgrConvert),
    ADD_FUNC(ThreeByteBgrToFourByteAbgrScaleConvert),
    ADD_FUNC(ByteGrayToFourByteAbgrPreConvert),
    ADD_FUNC(ByteGrayToFourByteAbgrPreScaleConvert),
    ADD_FUNC(ByteIndexedBmToFourByteAbgrPreScaleXparOver),
    ADD_FUNC(ByteIndexedBmToFourByteAbgrPreXparBgCopy),
    ADD_FUNC(ByteIndexedBmToFourByteAbgrPreXparOver),
    ADD_FUNC(ByteIndexedToFourByteAbgrPreConvert),
    ADD_FUNC(ByteIndexedToFourByteAbgrPreScaleConvert),
    ADD_FUNC(FourByteAbgrPreAlphaMaskFill),
    ADD_FUNC(FourByteAbgrPreDrawGlyphListAA),
    ADD_FUNC(FourByteAbgrPreSrcMaskFill),
    ADD_FUNC(FourByteAbgrPreSrcOverMaskFill),
    ADD_FUNC(FourByteAbgrPreToIntArgbConvert),
    ADD_FUNC(FourByteAbgrPreToIntArgbScaleConvert),
    ADD_FUNC(IntArgbBmToFourByteAbgrPreScaleXparOver),
    ADD_FUNC(IntArgbToFourByteAbgrPreAlphaMaskBlit),
    ADD_FUNC(IntArgbToFourByteAbgrPreConvert),
    ADD_FUNC(IntArgbToFourByteAbgrPreScaleConvert),
    ADD_FUNC(IntArgbToFourByteAbgrPreSrcOverMaskBlit),
    ADD_FUNC(IntArgbToFourByteAbgrPreXorBlit),
    ADD_FUNC(IntRgbToFourByteAbgrPreAlphaMaskBlit),
    ADD_FUNC(IntRgbToFourByteAbgrPreConvert),
    ADD_FUNC(IntRgbToFourByteAbgrPreScaleConvert),
    ADD_FUNC(ThreeByteBgrToFourByteAbgrPreConvert),
    ADD_FUNC(ThreeByteBgrToFourByteAbgrPreScaleConvert),
    ADD_FUNC(ByteIndexedBmToIntArgbScaleXparOver),
    ADD_FUNC(ByteIndexedBmToIntArgbXparBgCopy),
    ADD_FUNC(ByteIndexedBmToIntArgbXparOver),
    ADD_FUNC(ByteIndexedToIntArgbConvert),
    ADD_FUNC(ByteIndexedToIntArgbScaleConvert),
    ADD_FUNC(Index12GrayToIntArgbConvert),
    ADD_FUNC(IntArgbAlphaMaskFill),
    ADD_FUNC(IntArgbBmToIntArgbScaleXparOver),
    ADD_FUNC(IntArgbDrawGlyphListAA),
    ADD_FUNC(IntArgbSrcMaskFill),
    ADD_FUNC(IntArgbSrcOverMaskFill),
    ADD_FUNC(IntArgbToIntArgbAlphaMaskBlit),
    ADD_FUNC(IntArgbToIntArgbSrcOverMaskBlit),
    ADD_FUNC(IntArgbToIntArgbXorBlit),
    ADD_FUNC(IntRgbToIntArgbAlphaMaskBlit),
    ADD_FUNC(ByteIndexedBmToIntArgbBmScaleXparOver),
    ADD_FUNC(ByteIndexedBmToIntArgbBmXparBgCopy),
    ADD_FUNC(ByteIndexedBmToIntArgbBmXparOver),
    ADD_FUNC(ByteIndexedToIntArgbBmConvert),
    ADD_FUNC(ByteIndexedToIntArgbBmScaleConvert),
    ADD_FUNC(IntArgbBmDrawGlyphListAA),
    ADD_FUNC(IntArgbBmToIntArgbConvert),
    ADD_FUNC(IntArgbToIntArgbBmConvert),
    ADD_FUNC(IntArgbToIntArgbBmScaleConvert),
    ADD_FUNC(IntArgbToIntArgbBmXorBlit),
    ADD_FUNC(ByteGrayToIntArgbPreConvert),
    ADD_FUNC(ByteGrayToIntArgbPreScaleConvert),
    ADD_FUNC(ByteIndexedBmToIntArgbPreScaleXparOver),
    ADD_FUNC(ByteIndexedBmToIntArgbPreXparBgCopy),
    ADD_FUNC(ByteIndexedBmToIntArgbPreXparOver),
    ADD_FUNC(ByteIndexedToIntArgbPreConvert),
    ADD_FUNC(ByteIndexedToIntArgbPreScaleConvert),
    ADD_FUNC(IntArgbPreAlphaMaskFill),
    ADD_FUNC(IntArgbPreDrawGlyphListAA),
    ADD_FUNC(IntArgbPreSrcMaskFill),
    ADD_FUNC(IntArgbPreSrcOverMaskFill),
    ADD_FUNC(IntArgbPreToIntArgbConvert),
    ADD_FUNC(IntArgbPreToIntArgbScaleConvert),
    ADD_FUNC(IntArgbToIntArgbPreAlphaMaskBlit),
    ADD_FUNC(IntArgbToIntArgbPreConvert),
    ADD_FUNC(IntArgbToIntArgbPreScaleConvert),
    ADD_FUNC(IntArgbToIntArgbPreSrcOverMaskBlit),
    ADD_FUNC(IntArgbToIntArgbPreXorBlit),
    ADD_FUNC(IntRgbToIntArgbPreAlphaMaskBlit),
    ADD_FUNC(IntRgbToIntArgbPreConvert),
    ADD_FUNC(IntRgbToIntArgbPreScaleConvert),
    ADD_FUNC(ThreeByteBgrToIntArgbPreConvert),
    ADD_FUNC(ThreeByteBgrToIntArgbPreScaleConvert),
    ADD_FUNC(ByteIndexedBmToIntBgrScaleXparOver),
    ADD_FUNC(ByteIndexedBmToIntBgrXparBgCopy),
    ADD_FUNC(ByteIndexedBmToIntBgrXparOver),
    ADD_FUNC(ByteIndexedToIntBgrConvert),
    ADD_FUNC(ByteIndexedToIntBgrScaleConvert),
    ADD_FUNC(IntArgbBmToIntBgrScaleXparOver),
    ADD_FUNC(IntArgbBmToIntBgrXparBgCopy),
    ADD_FUNC(IntArgbBmToIntBgrXparOver),
    ADD_FUNC(IntArgbToIntBgrAlphaMaskBlit),
    ADD_FUNC(IntArgbToIntBgrConvert),
    ADD_FUNC(IntArgbToIntBgrScaleConvert),
    ADD_FUNC(IntArgbToIntBgrSrcOverMaskBlit),
    ADD_FUNC(IntArgbToIntBgrXorBlit),
    ADD_FUNC(IntBgrAlphaMaskFill),
    ADD_FUNC(IntBgrDrawGlyphListAA),
    ADD_FUNC(IntBgrSrcMaskFill),
    ADD_FUNC(IntBgrSrcOverMaskFill),
    ADD_FUNC(IntBgrToIntArgbConvert),
    ADD_FUNC(IntBgrToIntArgbScaleConvert),
    ADD_FUNC(IntBgrToIntBgrAlphaMaskBlit),
    ADD_FUNC(IntRgbToIntBgrAlphaMaskBlit),
    ADD_FUNC(ThreeByteBgrToIntBgrConvert),
    ADD_FUNC(ThreeByteBgrToIntBgrScaleConvert),
    ADD_FUNC(ByteGrayToIntRgbConvert),
    ADD_FUNC(ByteGrayToIntRgbScaleConvert),
    ADD_FUNC(IntArgbBmToIntRgbXparBgCopy),
    ADD_FUNC(IntArgbBmToIntRgbXparOver),
    ADD_FUNC(IntArgbToIntRgbAlphaMaskBlit),
    ADD_FUNC(IntArgbToIntRgbSrcOverMaskBlit),
    ADD_FUNC(IntArgbToIntRgbXorBlit),
    ADD_FUNC(IntRgbAlphaMaskFill),
    ADD_FUNC(IntRgbDrawGlyphListAA),
    ADD_FUNC(IntRgbSrcMaskFill),
    ADD_FUNC(IntRgbSrcOverMaskFill),
    ADD_FUNC(IntRgbToIntArgbConvert),
    ADD_FUNC(IntRgbToIntArgbScaleConvert),
    ADD_FUNC(IntRgbToIntRgbAlphaMaskBlit),
    ADD_FUNC(ThreeByteBgrToIntRgbConvert),
    ADD_FUNC(ThreeByteBgrToIntRgbScaleConvert),
    ADD_FUNC(ByteGrayToIntRgbxConvert),
    ADD_FUNC(ByteGrayToIntRgbxScaleConvert),
    ADD_FUNC(ByteIndexedBmToIntRgbxScaleXparOver),
    ADD_FUNC(ByteIndexedBmToIntRgbxXparBgCopy),
    ADD_FUNC(ByteIndexedBmToIntRgbxXparOver),
    ADD_FUNC(ByteIndexedToIntRgbxConvert),
    ADD_FUNC(ByteIndexedToIntRgbxScaleConvert),
    ADD_FUNC(IntArgbBmToIntRgbxScaleXparOver),
    ADD_FUNC(IntArgbToIntRgbxConvert),
    ADD_FUNC(IntArgbToIntRgbxScaleConvert),
    ADD_FUNC(IntArgbToIntRgbxXorBlit),
    ADD_FUNC(IntRgbxDrawGlyphListAA),
    ADD_FUNC(IntRgbxToIntArgbConvert),
    ADD_FUNC(IntRgbxToIntArgbScaleConvert),
    ADD_FUNC(ThreeByteBgrToIntRgbxConvert),
    ADD_FUNC(ThreeByteBgrToIntRgbxScaleConvert),
    ADD_FUNC(ThreeByteBgrAlphaMaskFill),
    ADD_FUNC(ThreeByteBgrSrcMaskFill),
    ADD_FUNC(ThreeByteBgrSrcOverMaskFill),
    ADD_FUNC(ThreeByteBgrToIntArgbConvert),
    ADD_FUNC(ThreeByteBgrToIntArgbScaleConvert),
};

/***************************************************************/

#define NUM_VIS_FUNCS sizeof(vis_func_pair_array)/sizeof(AnyFunc_pair)

/***************************************************************/

#define HASH_SIZE     1024 /* must be power of 2 and > number of functions */
#define PTR_SHIFT     ((sizeof(void*) == 4) ? 2 : 3)
#define HASH_FUNC(x)  (((jint)(x) >> PTR_SHIFT) & (HASH_SIZE - 1))
#define NEXT_INDEX(j) ((j + 1) & (HASH_SIZE - 1))

static AnyFunc* hash_table[HASH_SIZE];
static AnyFunc* hash_table_vis[HASH_SIZE];

/***************************************************************/

static int initialized;
static int usevis = JNI_TRUE;

#if defined(__linux__) || defined(MACOSX)
#   define ULTRA_CHIP   "sparc64"
#else
#   define ULTRA_CHIP   "sun4u"
#endif

extern TransformInterpFunc *pBilinearFunc;
extern TransformInterpFunc *pBicubicFunc;
extern TransformInterpFunc vis_BilinearBlend;
extern TransformInterpFunc vis_BicubicBlend;

/*
 * This function returns a pointer to the VIS accelerated version
 * of the indicated C function if it exists and if the conditions
 * are correct to use the VIS functions.
 */
AnyFunc* MapAccelFunction(AnyFunc *func_c)
{
    jint i, j;

    if (!initialized) {
        struct utsname name;

        /*
         * Only use the vis loops if the environment variable is set.
         * Find out the machine name. If it is an SUN ultra, we
         * can use the vis library
         */
        if (uname(&name) < 0 || strcmp(name.machine, ULTRA_CHIP) != 0) {
            usevis = JNI_FALSE;
        } else {
            char *vis_env = getenv("J2D_USE_VIS_LOOPS");
            if (vis_env != 0) {
                switch (*vis_env) {
                case 'T':
                    fprintf(stderr, "VIS loops enabled\n");
                case 't':
                    usevis = JNI_TRUE;
                    break;

                case 'F':
                    fprintf(stderr, "VIS loops disabled\n");
                case 'f':
                    usevis = JNI_FALSE;
                    break;

                default:
                    fprintf(stderr, "VIS loops %s by default\n",
                            usevis ? "enabled" : "disabled");
                    break;
                }
            }
        }
        initialized = 1;
        if (usevis) {
            /* fill hash table */
            memset(hash_table, 0, sizeof(hash_table));
            for (i = 0; i < NUM_VIS_FUNCS; i++) {
                AnyFunc* func = vis_func_pair_array[i].func_c;
                j = HASH_FUNC(func);
                while (hash_table[j] != NULL) {
                    j = NEXT_INDEX(j);
                }
                hash_table[j] = func;
                hash_table_vis[j] = vis_func_pair_array[i].func_vis;
            }
            pBilinearFunc = vis_BilinearBlend;
            pBicubicFunc = vis_BicubicBlend;
        }
    }
    if (!usevis) {
        return func_c;
    }

    j = HASH_FUNC(func_c);
    while (hash_table[j] != NULL) {
        if (hash_table[j] == func_c) {
            return hash_table_vis[j];
        }
        j = NEXT_INDEX(j);
    }

    return func_c;
}

/***************************************************************/
