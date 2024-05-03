/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8310844
 * @summary Verify that monitors with large offset in the OSR buffer are handled properly.
 * @run main/othervm -Xbatch -XX:CompileCommand=compileonly,TestLargeMonitorOffset::* TestLargeMonitorOffset
 */

public class TestLargeMonitorOffset {

    public static void test() {
        long l1,   l2,   l3,   l4,   l5,   l6,   l7,   l8,   l9,   l10,
             l11,  l12,  l13,  l14,  l15,  l16,  l17,  l18,  l19,  l20,
             l21,  l22,  l23,  l24,  l25,  l26,  l27,  l28,  l29,  l30,
             l31,  l32,  l33,  l34,  l35,  l36,  l37,  l38,  l39,  l40,
             l41,  l42,  l43,  l44,  l45,  l46,  l47,  l48,  l49,  l50,
             l51,  l52,  l53,  l54,  l55,  l56,  l57,  l58,  l59,  l60,
             l61,  l62,  l63,  l64,  l65,  l66,  l67,  l68,  l69,  l70,
             l71,  l72,  l73,  l74,  l75,  l76,  l77,  l78,  l79,  l80,
             l81,  l82,  l83,  l84,  l85,  l86,  l87,  l88,  l89,  l90,
             l91,  l92,  l93,  l94,  l95,  l96,  l97,  l98,  l99,  l100,
             l101, l102, l103, l104, l105, l106, l107, l108, l109, l110,
             l111, l112, l113, l114, l115, l116, l117, l118, l119, l120,
             l121, l122, l123, l124, l125, l126, l127, l128, l129, l130,
             l131, l132, l133, l134, l135, l136, l137, l138, l139, l140,
             l141, l142, l143, l144, l145, l146, l147, l148, l149, l150,
             l151, l152, l153, l154, l155, l156, l157, l158, l159, l160,
             l161, l162, l163, l164, l165, l166, l167, l168, l169, l170,
             l171, l172, l173, l174, l175, l176, l177, l178, l179, l180,
             l181, l182, l183, l184, l185, l186, l187, l188, l189, l190,
             l191, l192, l193, l194, l195, l196, l197, l198, l199, l200,
             l201, l202, l203, l204, l205, l206, l207, l208, l209, l210,
             l211, l212, l213, l214, l215, l216, l217, l218, l219, l220,
             l221, l222, l223, l224, l225, l226, l227, l228, l229, l230,
             l231, l232, l233, l234, l235, l236, l237, l238, l239, l240,
             l241, l242, l243, l244, l245, l246, l247, l248, l249, l250,
             l251, l252, l253, l254, l255, l256, l257, l258, l259, l260,
             l261, l262, l263, l264, l265, l266, l267, l268, l269, l270,
             l271, l272, l273, l274, l275, l276, l277, l278, l279, l280,
             l281, l282, l283, l284, l285, l286, l287, l288, l289, l290,
             l291, l292, l293, l294, l295, l296, l297, l298, l299, l300,
             l301, l302, l303, l304, l305, l306, l307, l308, l309, l310,
             l311, l312, l313, l314, l315, l316, l317, l318, l319, l320,
             l321, l322, l323, l324, l325, l326, l327, l328, l329, l330,
             l331, l332, l333, l334, l335, l336, l337, l338, l339, l340,
             l341, l342, l343, l344, l345, l346, l347, l348, l349, l350,
             l351, l352, l353, l354, l355, l356, l357, l358, l359, l360,
             l361, l362, l363, l364, l365, l366, l367, l368, l369, l370,
             l371, l372, l373, l374, l375, l376, l377, l378, l379, l380,
             l381, l382, l383, l384, l385, l386, l387, l388, l389, l390,
             l391, l392, l393, l394, l395, l396, l397, l398, l399, l400,
             l401, l402, l403, l404, l405, l406, l407, l408, l409, l410,
             l411, l412, l413, l414, l415, l416, l417, l418, l419, l420,
             l421, l422, l423, l424, l425, l426, l427, l428, l429, l430,
             l431, l432, l433, l434, l435, l436, l437, l438, l439, l440,
             l441, l442, l443, l444, l445, l446, l447, l448, l449, l450,
             l451, l452, l453, l454, l455, l456, l457, l458, l459, l460,
             l461, l462, l463, l464, l465, l466, l467, l468, l469, l470,
             l471, l472, l473, l474, l475, l476, l477, l478, l479, l480,
             l481, l482, l483, l484, l485, l486, l487, l488, l489, l490,
             l491, l492, l493, l494, l495, l496, l497, l498, l499, l500,
             l501, l502, l503, l504, l505, l506, l507, l508, l509, l510,
             l511, l512, l513, l514, l515, l516, l517, l518, l519, l520,
             l521, l522, l523, l524, l525, l526, l527, l528, l529, l530,
             l531, l532, l533, l534, l535, l536, l537, l538, l539, l540,
             l541, l542, l543, l544, l545, l546, l547, l548, l549, l550,
             l551, l552, l553, l554, l555, l556, l557, l558, l559, l560,
             l561, l562, l563, l564, l565, l566, l567, l568, l569, l570,
             l571, l572, l573, l574, l575, l576, l577, l578, l579, l580,
             l581, l582, l583, l584, l585, l586, l587, l588, l589, l590,
             l591, l592, l593, l594, l595, l596, l597, l598, l599, l600,
             l601, l602, l603, l604, l605, l606, l607, l608, l609, l610,
             l611, l612, l613, l614, l615, l616, l617, l618, l619, l620,
             l621, l622, l623, l624, l625, l626, l627, l628, l629, l630,
             l631, l632, l633, l634, l635, l636, l637, l638, l639, l640,
             l641, l642, l643, l644, l645, l646, l647, l648, l649, l650,
             l651, l652, l653, l654, l655, l656, l657, l658, l659, l660,
             l661, l662, l663, l664, l665, l666, l667, l668, l669, l670,
             l671, l672, l673, l674, l675, l676, l677, l678, l679, l680,
             l681, l682, l683, l684, l685, l686, l687, l688, l689, l690,
             l691, l692, l693, l694, l695, l696, l697, l698, l699, l700,
             l701, l702, l703, l704, l705, l706, l707, l708, l709, l710,
             l711, l712, l713, l714, l715, l716, l717, l718, l719, l720,
             l721, l722, l723, l724, l725, l726, l727, l728, l729, l730,
             l731, l732, l733, l734, l735, l736, l737, l738, l739, l740,
             l741, l742, l743, l744, l745, l746, l747, l748, l749, l750,
             l751, l752, l753, l754, l755, l756, l757, l758, l759, l760,
             l761, l762, l763, l764, l765, l766, l767, l768, l769, l770,
             l771, l772, l773, l774, l775, l776, l777, l778, l779, l780,
             l781, l782, l783, l784, l785, l786, l787, l788, l789, l790,
             l791, l792, l793, l794, l795, l796, l797, l798, l799, l800,
             l801, l802, l803, l804, l805, l806, l807, l808, l809, l810,
             l811, l812, l813, l814, l815, l816, l817, l818, l819, l820,
             l821, l822, l823, l824, l825, l826, l827, l828, l829, l830,
             l831, l832, l833, l834, l835, l836, l837, l838, l839, l840,
             l841, l842, l843, l844, l845, l846, l847, l848, l849, l850,
             l851, l852, l853, l854, l855, l856, l857, l858, l859, l860,
             l861, l862, l863, l864, l865, l866, l867, l868, l869, l870,
             l871, l872, l873, l874, l875, l876, l877, l878, l879, l880,
             l881, l882, l883, l884, l885, l886, l887, l888, l889, l890,
             l891, l892, l893, l894, l895, l896, l897, l898, l899, l900,
             l901, l902, l903, l904, l905, l906, l907, l908, l909, l910,
             l911, l912, l913, l914, l915, l916, l917, l918, l919, l920,
             l921, l922, l923, l924, l925, l926, l927, l928, l929, l930,
             l931, l932, l933, l934, l935, l936, l937, l938, l939, l940,
             l941, l942, l943, l944, l945, l946, l947, l948, l949, l950,
             l951, l952, l953, l954, l955, l956, l957, l958, l959, l960,
             l961, l962, l963, l964, l965, l966, l967, l968, l969, l970,
             l971, l972, l973, l974, l975, l976, l977, l978, l979, l980,
             l981, l982, l983, l984, l985, l986, l987, l988, l989, l990,
             l991, l992, l993, l994, l995, l996, l997, l998, l999, l1000;

        synchronized (TestLargeMonitorOffset.class) {
            // Trigger OSR compilation with monitor in the OSR buffer
            for (int i = 0; i < 1_000_000; ++i) {

            }
        }
    }

    public static void main(String[] args) {
        test();
    }
}
