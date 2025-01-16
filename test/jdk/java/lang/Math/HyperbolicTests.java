/*
 * Copyright (c) 2003, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4851625 4900189 4939441
 * @build Tests
 * @build HyperbolicTests
 * @run main HyperbolicTests
 * @summary Tests for {Math, StrictMath}.{sinh, cosh, tanh}
 */

import static java.lang.Double.longBitsToDouble;

public class HyperbolicTests {
    private HyperbolicTests(){}

    static final double NaNd = Double.NaN;

    public static void main(String... argv) {
        int failures = 0;

        failures += testSinh();
        failures += testCosh();
        failures += testTanh();

        if (failures > 0) {
            System.err.println("Testing the hyperbolic functions incurred "
                               + failures + " failures.");
            throw new RuntimeException();
        }
    }

    /**
     * Test accuracy of {Math, StrictMath}.sinh.  The specified
     * accuracy is 2.5 ulps.
     *
     * The defintion of sinh(x) is
     *
     * (e^x - e^(-x))/2
     *
     * The series expansion of sinh(x) =
     *
     * x + x^3/3! + x^5/5! + x^7/7! +...
     *
     * Therefore,
     *
     * 1. For large values of x sinh(x) ~= signum(x)*exp(|x|)/2
     *
     * 2. For small values of x, sinh(x) ~= x.
     *
     * Additionally, sinh is an odd function; sinh(-x) = -sinh(x).
     *
     */
    static int testSinh() {
        int failures = 0;
        /*
         * Array elements below generated using a quad sinh
         * implementation.  Rounded to a double, the quad result
         * *should* be correctly rounded, unless we are quite unlucky.
         * Assuming the quad value is a correctly rounded double, the
         * allowed error is 3.0 ulps instead of 2.5 since the quad
         * value rounded to double can have its own 1/2 ulp error.
         */
        double [][] testCases = {
            // x                sinh(x)
            {0.0625,            0.06254069805219182172183988501029229},
            {0.1250,            0.12532577524111545698205754229137154},
            {0.1875,            0.18860056562029018382047025055167585},
            {0.2500,            0.25261231680816830791412515054205787},
            {0.3125,            0.31761115611357728583959867611490292},
            {0.3750,            0.38385106791361456875429567642050245},
            {0.4375,            0.45159088610312053032509815226723017},
            {0.5000,            0.52109530549374736162242562641149155},
            {0.5625,            0.59263591611468777373870867338492247},
            {0.6250,            0.66649226445661608227260655608302908},
            {0.6875,            0.74295294580567543571442036910465007},
            {0.7500,            0.82231673193582998070366163444691386},
            {0.8125,            0.90489373856606433650504536421491368},
            {0.8750,            0.99100663714429475605317427568995231},
            {0.9375,            1.08099191569306394011007867453992548},
            {1.0000,            1.17520119364380145688238185059560082},
            {1.0625,            1.27400259579739321279181130344911907},
            {1.1250,            1.37778219077984075760379987065228373},
            {1.1875,            1.48694549961380717221109202361777593},
            {1.2500,            1.60191908030082563790283030151221415},
            {1.3125,            1.72315219460596010219069206464391528},
            {1.3750,            1.85111856355791532419998548438506416},
            {1.4375,            1.98631821852425112898943304217629457},
            {1.5000,            2.12927945509481749683438749467763195},
            {1.5625,            2.28056089740825247058075476705718764},
            {1.6250,            2.44075368098794353221372986997161132},
            {1.6875,            2.61048376261693140366028569794027603},
            {1.7500,            2.79041436627764265509289122308816092},
            {1.8125,            2.98124857471401377943765253243875520},
            {1.8750,            3.18373207674259205101326780071803724},
            {1.9375,            3.39865608104779099764440244167531810},
            {2.0000,            3.62686040784701876766821398280126192},
            {2.0625,            3.86923677050642806693938384073620450},
            {2.1250,            4.12673225993027252260441410537905269},
            {2.1875,            4.40035304533919660406976249684469164},
            {2.2500,            4.69116830589833069188357567763552003},
            {2.3125,            5.00031440855811351554075363240262157},
            {2.3750,            5.32899934843284576394645856548481489},
            {2.4375,            5.67850746906785056212578751630266858},
            {2.5000,            6.05020448103978732145032363835040319},
            {2.5625,            6.44554279850040875063706020260185553},
            {2.6250,            6.86606721451642172826145238779845813},
            {2.6875,            7.31342093738196587585692115636603571},
            {2.7500,            7.78935201149073201875513401029935330},
            {2.8125,            8.29572014785741787167717932988491961},
            {2.8750,            8.83450399097893197351853322827892144},
            {2.9375,            9.40780885043076394429977972921690859},
            {3.0000,            10.01787492740990189897459361946582867},
            {3.0625,            10.66708606836969224165124519209968368},
            {3.1250,            11.35797907995166028304704128775698426},
            {3.1875,            12.09325364161259019614431093344260209},
            {3.2500,            12.87578285468067003959660391705481220},
            {3.3125,            13.70862446906136798063935858393686525},
            {3.3750,            14.59503283146163690015482636921657975},
            {3.4375,            15.53847160182039311025096666980558478},
            {3.5000,            16.54262728763499762495673152901249743},
            {3.5625,            17.61142364906941482858466494889121694},
            {3.6250,            18.74903703113232171399165788088277979},
            {3.6875,            19.95991268283598684128844120984214675},
            {3.7500,            21.24878212710338697364101071825171163},
            {3.8125,            22.62068164929685091969259499078125023},
            {3.8750,            24.08097197661255803883403419733891573},
            {3.9375,            25.63535922523855307175060244757748997},
            {4.0000,            27.28991719712775244890827159079382096},
            {4.0625,            29.05111111351106713777825462100160185},
            {4.1250,            30.92582287788986031725487699744107092},
            {4.1875,            32.92137796722343190618721270937061472},
            {4.2500,            35.04557405638942942322929652461901154},
            {4.3125,            37.30671148776788628118833357170042385},
            {4.3750,            39.71362570500944929025069048612806024},
            {4.4375,            42.27572177772344954814418332587050658},
            {4.5000,            45.00301115199178562180965680564371424},
            {4.5625,            47.90615077031205065685078058248081891},
            {4.6250,            50.99648471383193131253995134526177467},
            {4.6875,            54.28608852959281437757368957713936555},
            {4.7500,            57.78781641599226874961859781628591635},
            {4.8125,            61.51535145084362283008545918273109379},
            {4.8750,            65.48325905829987165560146562921543361},
            {4.9375,            69.70704392356508084094318094283346381},
            {5.0000,            74.20321057778875897700947199606456364},
            {5.0625,            78.98932788987998983462810080907521151},
            {5.1250,            84.08409771724448958901392613147384951},
            {5.1875,            89.50742798369883598816307922895346849},
            {5.2500,            95.28051047011540739630959111303975956},
            {5.3125,            101.42590362176666730633859252034238987},
            {5.3750,            107.96762069594029162704530843962700133},
            {5.4375,            114.93122359426386042048760580590182604},
            {5.5000,            122.34392274639096192409774240457730721},
            {5.5625,            130.23468343534638291488502321709913206},
            {5.6250,            138.63433897999898233879574111119546728},
            {5.6875,            147.57571121692522056519568264304815790},
            {5.7500,            157.09373875244884423880085377625986165},
            {5.8125,            167.22561348600435888568183143777868662},
            {5.8750,            178.01092593829229887752609866133883987},
            {5.9375,            189.49181995209921964640216682906501778},
            {6.0000,            201.71315737027922812498206768797872263},
            {6.0625,            214.72269333437984291483666459592578915},
            {6.1250,            228.57126288889537420461281285729970085},
            {6.1875,            243.31297962030799867970551767086092471},
            {6.2500,            259.00544710710289911522315435345489966},
            {6.3125,            275.70998400700299790136562219920451185},
            {6.3750,            293.49186366095654566861661249898332253},
            {6.4375,            312.42056915013535342987623229485223434},
            {6.5000,            332.57006480258443156075705566965111346},
            {6.5625,            354.01908521044116928437570109827956007},
            {6.6250,            376.85144288706511933454985188849781703},
            {6.6875,            401.15635576625530823119100750634165252},
            {6.7500,            427.02879582326538080306830640235938517},
            {6.8125,            454.56986017986077163530945733572724452},
            {6.8750,            483.88716614351897894746751705315210621},
            {6.9375,            515.09527172439720070161654727225752288},
            {7.0000,            548.31612327324652237375611757601851598},
            {7.0625,            583.67953198942753384680988096024373270},
            {7.1250,            621.32368116099280160364794462812762880},
            {7.1875,            661.39566611888784148449430491465857519},
            {7.2500,            704.05206901515336623551137120663358760},
            {7.3125,            749.45957067108712382864538206200700256},
            {7.3750,            797.79560188617531521347351754559776282},
            {7.4375,            849.24903675279739482863565789325699416},
            {7.5000,            904.02093068584652953510919038935849651},
            {7.5625,            962.32530605113249628368993221570636328},
            {7.6250,            1024.38998846242707559349318193113614698},
            {7.6875,            1090.45749701500081956792547346904792325},
            {7.7500,            1160.78599193425808533255719118417856088},
            {7.8125,            1235.65028334242796895820912936318532502},
            {7.8750,            1315.34290508508890654067255740428824014},
            {7.9375,            1400.17525781352742299995139486063802583},
            {8.0000,            1490.47882578955018611587663903188144796},
            {8.0625,            1586.60647216744061169450001100145859236},
            {8.1250,            1688.93381781440241350635231605477507900},
            {8.1875,            1797.86070905726094477721128358866360644},
            {8.2500,            1913.81278009067446281883262689250118009},
            {8.3125,            2037.24311615199935553277163192983440062},
            {8.3750,            2168.63402396170125867037749369723761636},
            {8.4375,            2308.49891634734644432370720900969004306},
            {8.5000,            2457.38431841538268239359965370719928775},
            {8.5625,            2615.87200310986940554256648824234335262},
            {8.6250,            2784.58126450289932429469130598902487336},
            {8.6875,            2964.17133769964321637973459949999057146},
            {8.7500,            3155.34397481384944060352507473513108710},
            {8.8125,            3358.84618707947841898217318996045550438},
            {8.8750,            3575.47316381333288862617411467285480067},
            {8.9375,            3806.07137963459383403903729660349293583},
            {9.0000,            4051.54190208278996051522359589803425598},
            {9.0625,            4312.84391255878980330955246931164633615},
            {9.1250,            4590.99845434696991399363282718106006883},
            {9.1875,            4887.09242236403719571363798584676797558},
            {9.2500,            5202.28281022453561319352901552085348309},
            {9.3125,            5537.80123121853803935727335892054791265},
            {9.3750,            5894.95873086734181634245918412592155656},
            {9.4375,            6275.15090986233399457103055108344546942},
            {9.5000,            6679.86337740502119410058225086262108741},
            {9.5625,            7110.67755625726876329967852256934334025},
            {9.6250,            7569.27686218510919585241049433331592115},
            {9.6875,            8057.45328194243077504648484392156371121},
            {9.7500,            8577.11437549816065709098061006273039092},
            {9.8125,            9130.29072986829727910801024120918114778},
            {9.8750,            9719.14389367880274015504995181862860062},
            {9.9375,            10345.97482346383208590278839409938269134},
            {10.0000,           11013.23287470339337723652455484636420303},
        };

        for(int i = 0; i < testCases.length; i++) {
            double [] testCase = testCases[i];
            failures += testSinhCaseWithUlpDiff(testCase[0],
                                                testCase[1],
                                                3.0);
        }

        for(double nan : Tests.NaNs) {
            failures += testSinhCaseWithUlpDiff(nan, NaNd, 0);
        }

        double [][] specialTestCases = {
            {0.0,                       0.0},
            {Double.POSITIVE_INFINITY,  Double.POSITIVE_INFINITY}
        };

        for(int i = 0; i < specialTestCases.length; i++) {
            failures += testSinhCaseWithUlpDiff(specialTestCases[i][0],
                                                specialTestCases[i][1],
                                                0.0);
        }

        // For powers of 2 less than 2^(-27), the second and
        // subsequent terms of the Taylor series expansion will get
        // rounded away since |n-n^3| > 53, the binary precision of a
        // double significand.

        for(int i = DoubleConsts.MIN_SUB_EXPONENT; i < -27; i++) {
            double d = Math.scalb(2.0, i);

            // Result and expected are the same.
            failures += testSinhCaseWithUlpDiff(d, d, 2.5);
        }

        // For values of x larger than 22, the e^(-x) term is
        // insignificant to the floating-point result.  Util exp(x)
        // overflows around 709.8, sinh(x) ~= exp(x)/2; will test
        // 10000 values in this range.

        long trans22 = Double.doubleToLongBits(22.0);
        // (approximately) largest value such that exp shouldn't
        // overflow
        long transExpOvfl = Double.doubleToLongBits(Math.nextDown(709.7827128933841));

        for(long i = trans22;
            i < transExpOvfl;
            i +=(transExpOvfl-trans22)/10000) {

            double d = Double.longBitsToDouble(i);

            // Allow 3.5 ulps of error to deal with error in exp.
            failures += testSinhCaseWithUlpDiff(d, StrictMath.exp(d)*0.5, 3.5);
        }

        // (approximately) largest value such that sinh shouldn't
        // overflow.
        long transSinhOvfl = Double.doubleToLongBits(710.4758600739439);

        // Make sure sinh(x) doesn't overflow as soon as exp(x)
        // overflows.

        /*
         * For large values of x, sinh(x) ~= 0.5*(e^x).  Therefore,
         *
         * sinh(x) ~= e^(ln 0.5) * e^x = e^(x + ln 0.5)
         *
         * So, we can calculate the approximate expected result as
         * exp(x + -0.693147186).  However, this sum suffers from
         * roundoff, limiting the accuracy of the approximation.  The
         * accuracy can be improved by recovering the rounded-off
         * information.  Since x is larger than ln(0.5), the trailing
         * bits of ln(0.5) get rounded away when the two values are
         * added.  However, high-order bits of ln(0.5) that
         * contribute to the sum can be found:
         *
         * offset = log(0.5);
         * effective_offset = (x + offset) - x; // exact subtraction
         * rounded_away_offset = offset - effective_offset; // exact subtraction
         *
         * Therefore, the product
         *
         * exp(x + offset)*exp(rounded_away_offset)
         *
         * will be a better approximation to the exact value of
         *
         * e^(x + offset)
         *
         * than exp(x+offset) alone.  (The expected result cannot be
         * computed as exp(x)*exp(offset) since exp(x) by itself would
         * overflow to infinity.)
         */
        double offset = StrictMath.log(0.5);
        for(long i = transExpOvfl+1; i < transSinhOvfl;
            i += (transSinhOvfl-transExpOvfl)/1000 ) {
            double input = Double.longBitsToDouble(i);

            double expected =
                StrictMath.exp(input + offset) *
                StrictMath.exp( offset - ((input + offset) - input) );

            failures += testSinhCaseWithUlpDiff(input, expected, 4.0);
        }

        // sinh(x) overflows for values greater than 710; in
        // particular, it overflows for all 2^i, i > 10.
        for(int i = 10; i <= Double.MAX_EXPONENT; i++) {
            double d = Math.scalb(2.0, i);

            // Result and expected are the same.
            failures += testSinhCaseWithUlpDiff(d,
                                                Double.POSITIVE_INFINITY, 0.0);
        }

        return failures;
    }

    public static int testSinhCaseWithTolerance(double input,
                                                double expected,
                                                double tolerance) {
        int failures = 0;
        failures += Tests.testTolerance("Math.sinh",        input, Math::sinh,        expected, tolerance);
        failures += Tests.testTolerance("Math.sinh",       -input, Math::sinh,       -expected, tolerance);

        failures += Tests.testTolerance("StrictMath.sinh",  input, StrictMath::sinh,  expected, tolerance);
        failures += Tests.testTolerance("StrictMath.sinh", -input, StrictMath::sinh, -expected, tolerance);
        return failures;
    }

    public static int testSinhCaseWithUlpDiff(double input,
                                              double expected,
                                              double ulps) {
        int failures = 0;
        failures += Tests.testUlpDiff("Math.sinh",        input, Math::sinh,        expected, ulps);
        failures += Tests.testUlpDiff("Math.sinh",       -input, Math::sinh,       -expected, ulps);

        failures += Tests.testUlpDiff("StrictMath.sinh",  input, StrictMath::sinh,  expected, ulps);
        failures += Tests.testUlpDiff("StrictMath.sinh", -input, StrictMath::sinh, -expected, ulps);
        return failures;
    }

    /**
     * Test accuracy of {Math, StrictMath}.cosh.  The specified
     * accuracy is 2.5 ulps.
     *
     * The defintion of cosh(x) is
     *
     * (e^x + e^(-x))/2
     *
     * The series expansion of cosh(x) =
     *
     * 1 + x^2/2! + x^4/4! + x^6/6! +...
     *
     * Therefore,
     *
     * 1. For large values of x cosh(x) ~= exp(|x|)/2
     *
     * 2. For small values of x, cosh(x) ~= 1.
     *
     * Additionally, cosh is an even function; cosh(-x) = cosh(x).
     *
     */
    static int testCosh() {
        int failures = 0;
        /*
         * Array elements below generated using a quad cosh
         * implementation.  Rounded to a double, the quad result
         * *should* be correctly rounded, unless we are quite unlucky.
         * Assuming the quad value is a correctly rounded double, the
         * allowed error is 3.0 ulps instead of 2.5 since the quad
         * value rounded to double can have its own 1/2 ulp error.
         */
        double [][] testCases = {
            // x                cosh(x)
            {0.0625,            1.001953760865667607841550709632597376},
            {0.1250,            1.007822677825710859846949685520422223},
            {0.1875,            1.017629683800690526835115759894757615},
            {0.2500,            1.031413099879573176159295417520378622},
            {0.3125,            1.049226785060219076999158096606305793},
            {0.3750,            1.071140346704586767299498015567016002},
            {0.4375,            1.097239412531012567673453832328262160},
            {0.5000,            1.127625965206380785226225161402672030},
            {0.5625,            1.162418740845610783505338363214045218},
            {0.6250,            1.201753692975606324229229064105075301},
            {0.6875,            1.245784523776616395403056980542275175},
            {0.7500,            1.294683284676844687841708185390181730},
            {0.8125,            1.348641048647144208352285714214372703},
            {0.8750,            1.407868656822803158638471458026344506},
            {0.9375,            1.472597542369862933336886403008640891},
            {1.0000,            1.543080634815243778477905620757061497},
            {1.0625,            1.619593348374367728682469968448090763},
            {1.1250,            1.702434658138190487400868008124755757},
            {1.1875,            1.791928268324866464246665745956119612},
            {1.2500,            1.888423877161015738227715728160051696},
            {1.3125,            1.992298543335143985091891077551921106},
            {1.3750,            2.103958159362661802010972984204389619},
            {1.4375,            2.223839037619709260803023946704272699},
            {1.5000,            2.352409615243247325767667965441644201},
            {1.5625,            2.490172284559350293104864895029231913},
            {1.6250,            2.637665356192137582275019088061812951},
            {1.6875,            2.795465162524235691253423614360562624},
            {1.7500,            2.964188309728087781773608481754531801},
            {1.8125,            3.144494087167972176411236052303565201},
            {1.8750,            3.337087043587520514308832278928116525},
            {1.9375,            3.542719740149244276729383650503145346},
            {2.0000,            3.762195691083631459562213477773746099},
            {2.0625,            3.996372503438463642260225717607554880},
            {2.1250,            4.246165228196992140600291052990934410},
            {2.1875,            4.512549935859540340856119781585096760},
            {2.2500,            4.796567530460195028666793366876218854},
            {2.3125,            5.099327816921939817643745917141739051},
            {2.3750,            5.422013837643509250646323138888569746},
            {2.4375,            5.765886495263270945949271410819116399},
            {2.5000,            6.132289479663686116619852312817562517},
            {2.5625,            6.522654518468725462969589397439224177},
            {2.6250,            6.938506971550673190999796241172117288},
            {2.6875,            7.381471791406976069645686221095397137},
            {2.7500,            7.853279872697439591457564035857305647},
            {2.8125,            8.355774815752725814638234943192709129},
            {2.8750,            8.890920130482709321824793617157134961},
            {2.9375,            9.460806908834119747071078865866737196},
            {3.0000,            10.067661995777765841953936035115890343},
            {3.0625,            10.713856690753651225304006562698007312},
            {3.1250,            11.401916013575067700373788969458446177},
            {3.1875,            12.134528570998387744547733730974713055},
            {3.2500,            12.914557062512392049483503752322408761},
            {3.3125,            13.745049466398732213877084541992751273},
            {3.3750,            14.629250949773302934853381428660210721},
            {3.4375,            15.570616549147269180921654324879141947},
            {3.5000,            16.572824671057316125696517821376119469},
            {3.5625,            17.639791465519127930722105721028711044},
            {3.6250,            18.775686128468677200079039891415789429},
            {3.6875,            19.984947192985946987799359614758598457},
            {3.7500,            21.272299872959396081877161903352144126},
            {3.8125,            22.642774526961913363958587775566619798},
            {3.8750,            24.101726314486257781049388094955970560},
            {3.9375,            25.654856121347151067170940701379544221},
            {4.0000,            27.308232836016486629201989612067059978},
            {4.0625,            29.068317063936918520135334110824828950},
            {4.1250,            30.941986372478026192360480044849306606},
            {4.1875,            32.936562165180269851350626768308756303},
            {4.2500,            35.059838290298428678502583470475012235},
            {4.3125,            37.320111495433027109832850313172338419},
            {4.3750,            39.726213847251883288518263854094284091},
            {4.4375,            42.287547242982546165696077854963452084},
            {4.5000,            45.014120148530027928305799939930642658},
            {4.5625,            47.916586706774825161786212701923307169},
            {4.6250,            51.006288368867753140854830589583165950},
            {4.6875,            54.295298211196782516984520211780624960},
            {4.7500,            57.796468111195389383795669320243166117},
            {4.8125,            61.523478966332915041549750463563672435},
            {4.8750,            65.490894152518731617237739112888213645},
            {4.9375,            69.714216430810089539924900313140922323},
            {5.0000,            74.209948524787844444106108044487704798},
            {5.0625,            78.995657605307475581204965926043112946},
            {5.1250,            84.090043934600961683400343038519519678},
            {5.1875,            89.513013937957834087706670952561002466},
            {5.2500,            95.285757988514588780586084642381131013},
            {5.3125,            101.430833209098212357990123684449846912},
            {5.3750,            107.972251614673824873137995865940755392},
            {5.4375,            114.935573939814969189535554289886848550},
            {5.5000,            122.348009517829425991091207107262038316},
            {5.5625,            130.238522601820409078244923165746295574},
            {5.6250,            138.637945543134998069351279801575968875},
            {5.6875,            147.579099269447055276899288971207106581},
            {5.7500,            157.096921533245353905868840194264636395},
            {5.8125,            167.228603431860671946045256541679445836},
            {5.8750,            178.013734732486824390148614309727161925},
            {5.9375,            189.494458570056311567917444025807275896},
            {6.0000,            201.715636122455894483405112855409538488},
            {6.0625,            214.725021906554080628430756558271312513},
            {6.1250,            228.573450380013557089736092321068279231},
            {6.1875,            243.315034578039208138752165587134488645},
            {6.2500,            259.007377561239126824465367865430519592},
            {6.3125,            275.711797500835732516530131577254654076},
            {6.3750,            293.493567280752348242602902925987643443},
            {6.4375,            312.422169552825597994104814531010579387},
            {6.5000,            332.571568241777409133204438572983297292},
            {6.5625,            354.020497560858198165985214519757890505},
            {6.6250,            376.852769667496146326030849450983914197},
            {6.6875,            401.157602161123700280816957271992998156},
            {6.7500,            427.029966702886171977469256622451185850},
            {6.8125,            454.570960119471524953536004647195906721},
            {6.8750,            483.888199441157626584508920036981010995},
            {6.9375,            515.096242417696720610477570797503766179},
            {7.0000,            548.317035155212076889964120712102928484},
            {7.0625,            583.680388623257719787307547662358502345},
            {7.1250,            621.324485894002926216918634755431456031},
            {7.1875,            661.396422095589629755266517362992812037},
            {7.2500,            704.052779189542208784574955807004218856},
            {7.3125,            749.460237818184878095966335081928645934},
            {7.3750,            797.796228612873763671070863694973560629},
            {7.4375,            849.249625508044731271830060572510241864},
            {7.5000,            904.021483770216677368692292389446994987},
            {7.5625,            962.325825625814651122171697031114091993},
            {7.6250,            1024.390476557670599008492465853663578558},
            {7.6875,            1090.457955538048482588540574008226583335},
            {7.7500,            1160.786422676798661020094043586456606003},
            {7.8125,            1235.650687987597295222707689125107720568},
            {7.8750,            1315.343285214046776004329388551335841550},
            {7.9375,            1400.175614911635999247504386054087931958},
            {8.0000,            1490.479161252178088627715460421007179728},
            {8.0625,            1586.606787305415349050508956232945539108},
            {8.1250,            1688.934113859132470361718199038326340668},
            {8.1875,            1797.860987165547537276364148450577336075},
            {8.2500,            1913.813041349231764486365114317586148767},
            {8.3125,            2037.243361581700856522236313401822532385},
            {8.3750,            2168.634254521568851112005905503069409349},
            {8.4375,            2308.499132938297821208734949028296170563},
            {8.5000,            2457.384521883751693037774022640629666294},
            {8.5625,            2615.872194250713123494312356053193077854},
            {8.6250,            2784.581444063104750127653362960649823247},
            {8.6875,            2964.171506380845754878370650565756538203},
            {8.7500,            3155.344133275174556354775488913749659006},
            {8.8125,            3358.846335940117183452010789979584950102},
            {8.8750,            3575.473303654961482727206202358956274888},
            {8.9375,            3806.071511003646460448021740303914939059},
            {9.0000,            4051.542025492594047194773093534725371440},
            {9.0625,            4312.844028491571841588188869958240355518},
            {9.1250,            4590.998563255739769060078863130940205710},
            {9.1875,            4887.092524674358252509551443117048351290},
            {9.2500,            5202.282906336187674588222835339193136030},
            {9.3125,            5537.801321507079474415176386655744387251},
            {9.3750,            5894.958815685577062811620236195525504885},
            {9.4375,            6275.150989541692149890530417987358096221},
            {9.5000,            6679.863452256851081801173722051940058824},
            {9.5625,            7110.677626574055535297758456126491707647},
            {9.6250,            7569.276928241617224537226019600213961572},
            {9.6875,            8057.453343996777301036241026375049070162},
            {9.7500,            8577.114433792824387959788368429252257664},
            {9.8125,            9130.290784631065880205118262838330689429},
            {9.8750,            9719.143945123662919857326995631317996715},
            {9.9375,            10345.974871791805753327922796701684092861},
            {10.0000,           11013.232920103323139721376090437880844591},
        };

        for(int i = 0; i < testCases.length; i++) {
            double [] testCase = testCases[i];
            failures += testCoshCaseWithUlpDiff(testCase[0],
                                                testCase[1],
                                                3.0);
        }

        for(double nan : Tests.NaNs) {
            failures += testCoshCaseWithUlpDiff(nan, NaNd, 0);
        }

        double [][] specialTestCases = {
            {0.0,                       1.0},
            {Double.POSITIVE_INFINITY,  Double.POSITIVE_INFINITY}
        };

        for(int i = 0; i < specialTestCases.length; i++ ) {
            failures += testCoshCaseWithUlpDiff(specialTestCases[i][0],
                                                specialTestCases[i][1],
                                                0.0);
        }

        // For powers of 2 less than 2^(-27), the second and
        // subsequent terms of the Taylor series expansion will get
        // rounded.

        for(int i = DoubleConsts.MIN_SUB_EXPONENT; i < -27; i++) {
            double d = Math.scalb(2.0, i);

            // Result and expected are the same.
            failures += testCoshCaseWithUlpDiff(d, 1.0, 2.5);
        }

        // For values of x larger than 22, the e^(-x) term is
        // insignificant to the floating-point result.  Util exp(x)
        // overflows around 709.8, cosh(x) ~= exp(x)/2; will test
        // 10000 values in this range.

        long trans22 = Double.doubleToLongBits(22.0);
        // (approximately) largest value such that exp shouldn't
        // overflow
        long transExpOvfl = Double.doubleToLongBits(Math.nextDown(709.7827128933841));

        for(long i = trans22;
            i < transExpOvfl;
            i +=(transExpOvfl-trans22)/10000) {

            double d = Double.longBitsToDouble(i);

            // Allow 3.5 ulps of error to deal with error in exp.
            failures += testCoshCaseWithUlpDiff(d, StrictMath.exp(d)*0.5, 3.5);
        }

        // (approximately) largest value such that cosh shouldn't
        // overflow.
        long transCoshOvfl = Double.doubleToLongBits(710.4758600739439);

        // Make sure sinh(x) doesn't overflow as soon as exp(x)
        // overflows.

        /*
         * For large values of x, cosh(x) ~= 0.5*(e^x).  Therefore,
         *
         * cosh(x) ~= e^(ln 0.5) * e^x = e^(x + ln 0.5)
         *
         * So, we can calculate the approximate expected result as
         * exp(x + -0.693147186).  However, this sum suffers from
         * roundoff, limiting the accuracy of the approximation.  The
         * accuracy can be improved by recovering the rounded-off
         * information.  Since x is larger than ln(0.5), the trailing
         * bits of ln(0.5) get rounded away when the two values are
         * added.  However, high-order bits of ln(0.5) that
         * contribute to the sum can be found:
         *
         * offset = log(0.5);
         * effective_offset = (x + offset) - x; // exact subtraction
         * rounded_away_offset = offset - effective_offset; // exact subtraction
         *
         * Therefore, the product
         *
         * exp(x + offset)*exp(rounded_away_offset)
         *
         * will be a better approximation to the exact value of
         *
         * e^(x + offset)
         *
         * than exp(x+offset) alone.  (The expected result cannot be
         * computed as exp(x)*exp(offset) since exp(x) by itself would
         * overflow to infinity.)
         */
        double offset = StrictMath.log(0.5);
        for(long i = transExpOvfl+1; i < transCoshOvfl;
            i += (transCoshOvfl-transExpOvfl)/1000 ) {
            double input = Double.longBitsToDouble(i);

            double expected =
                StrictMath.exp(input + offset) *
                StrictMath.exp( offset - ((input + offset) - input) );

            failures += testCoshCaseWithUlpDiff(input, expected, 4.0);
        }

        // cosh(x) overflows for values greater than 710; in
        // particular, it overflows for all 2^i, i > 10.
        for(int i = 10; i <= Double.MAX_EXPONENT; i++) {
            double d = Math.scalb(2.0, i);

            // Result and expected are the same.
            failures += testCoshCaseWithUlpDiff(d,
                                                Double.POSITIVE_INFINITY, 0.0);
        }
        return failures;
    }

    public static int testCoshCaseWithTolerance(double input,
                                                double expected,
                                                double tolerance) {
        int failures = 0;
        failures += Tests.testTolerance("Math.cosh(double)",
                                        input, Math.cosh(input),
                                        expected, tolerance);
        failures += Tests.testTolerance("Math.cosh(double)",
                                        -input, Math.cosh(-input),
                                        expected, tolerance);

        failures += Tests.testTolerance("StrictMath.cosh(double)",
                                        input, StrictMath.cosh(input),
                                        expected, tolerance);
        failures += Tests.testTolerance("StrictMath.cosh(double)",
                                        -input, StrictMath.cosh(-input),
                                        expected, tolerance);
        return failures;
    }

    public static int testCoshCaseWithUlpDiff(double input,
                                              double expected,
                                              double ulps) {
        int failures = 0;
        failures += Tests.testUlpDiff("Math.cosh",        input, Math::cosh,       expected, ulps);
        failures += Tests.testUlpDiff("Math.cosh",       -input, Math::cosh,       expected, ulps);

        failures += Tests.testUlpDiff("StrictMath.cosh",  input, StrictMath::cosh, expected, ulps);
        failures += Tests.testUlpDiff("StrictMath.cosh", -input, StrictMath::cosh, expected, ulps);
        return failures;
    }

    /**
     * Test accuracy of {Math, StrictMath}.tanh.  The specified
     * accuracy is 2.5 ulps.
     *
     * The defintion of tanh(x) is
     *
     * (e^x - e^(-x))/(e^x + e^(-x))
     *
     * The series expansion of tanh(x) =
     *
     * x - x^3/3 + 2x^5/15 - 17x^7/315 + ...
     *
     * Therefore,
     *
     * 1. For large values of x tanh(x) ~= signum(x)
     *
     * 2. For small values of x, tanh(x) ~= x.
     *
     * Additionally, tanh is an odd function; tanh(-x) = -tanh(x).
     *
     */
    static int testTanh() {
        int failures = 0;
        /*
         * Array elements below generated using a quad sinh
         * implementation.  Rounded to a double, the quad result
         * *should* be correctly rounded, unless we are quite unlucky.
         * Assuming the quad value is a correctly rounded double, the
         * allowed error is 3.0 ulps instead of 2.5 since the quad
         * value rounded to double can have its own 1/2 ulp error.
         */
        double [][] testCases = {
            // x                tanh(x)
            {0.0625,            0.06241874674751251449014289119421133},
            {0.1250,            0.12435300177159620805464727580589271},
            {0.1875,            0.18533319990813951753211997502482787},
            {0.2500,            0.24491866240370912927780113149101697},
            {0.3125,            0.30270972933210848724239738970991712},
            {0.3750,            0.35835739835078594631936023155315807},
            {0.4375,            0.41157005567402245143207555859415687},
            {0.5000,            0.46211715726000975850231848364367256},
            {0.5625,            0.50982997373525658248931213507053130},
            {0.6250,            0.55459972234938229399903909532308371},
            {0.6875,            0.59637355547924233984437303950726939},
            {0.7500,            0.63514895238728731921443435731249638},
            {0.8125,            0.67096707420687367394810954721913358},
            {0.8750,            0.70390560393662106058763026963135371},
            {0.9375,            0.73407151960434149263991588052503660},
            {1.0000,            0.76159415595576488811945828260479366},
            {1.0625,            0.78661881210869761781941794647736081},
            {1.1250,            0.80930107020178101206077047354332696},
            {1.1875,            0.82980190998595952708572559629034476},
            {1.2500,            0.84828363995751289761338764670750445},
            {1.3125,            0.86490661772074179125443141102709751},
            {1.3750,            0.87982669965198475596055310881018259},
            {1.4375,            0.89319334040035153149249598745889365},
            {1.5000,            0.90514825364486643824230369645649557},
            {1.5625,            0.91582454416876231820084311814416443},
            {1.6250,            0.92534622531174107960457166792300374},
            {1.6875,            0.93382804322259173763570528576138652},
            {1.7500,            0.94137553849728736226942088377163687},
            {1.8125,            0.94808528560440629971240651310180052},
            {1.8750,            0.95404526017994877009219222661968285},
            {1.9375,            0.95933529331468249183399461756952555},
            {2.0000,            0.96402758007581688394641372410092317},
            {2.0625,            0.96818721657637057702714316097855370},
            {2.1250,            0.97187274591350905151254495374870401},
            {2.1875,            0.97513669829362836159665586901156483},
            {2.2500,            0.97802611473881363992272924300618321},
            {2.3125,            0.98058304703705186541999427134482061},
            {2.3750,            0.98284502917257603002353801620158861},
            {2.4375,            0.98484551746427837912703608465407824},
            {2.5000,            0.98661429815143028888127603923734964},
            {2.5625,            0.98817786228751240824802592958012269},
            {2.6250,            0.98955974861288320579361709496051109},
            {2.6875,            0.99078085564125158320311117560719312},
            {2.7500,            0.99185972456820774534967078914285035},
            {2.8125,            0.99281279483715982021711715899682324},
            {2.8750,            0.99365463431502962099607366282699651},
            {2.9375,            0.99439814606575805343721743822723671},
            {3.0000,            0.99505475368673045133188018525548849},
            {3.0625,            0.99563456710930963835715538507891736},
            {3.1250,            0.99614653067334504917102591131792951},
            {3.1875,            0.99659855517712942451966113109487039},
            {3.2500,            0.99699763548652601693227592643957226},
            {3.3125,            0.99734995516557367804571991063376923},
            {3.3750,            0.99766097946988897037219469409451602},
            {3.4375,            0.99793553792649036103161966894686844},
            {3.5000,            0.99817789761119870928427335245061171},
            {3.5625,            0.99839182812874152902001617480606320},
            {3.6250,            0.99858065920179882368897879066418294},
            {3.6875,            0.99874733168378115962760304582965538},
            {3.7500,            0.99889444272615280096784208280487888},
            {3.8125,            0.99902428575443546808677966295308778},
            {3.8750,            0.99913888583735077016137617231569011},
            {3.9375,            0.99924003097049627100651907919688313},
            {4.0000,            0.99932929973906704379224334434172499},
            {4.0625,            0.99940808577297384603818654530731215},
            {4.1250,            0.99947761936180856115470576756499454},
            {4.1875,            0.99953898655601372055527046497863955},
            {4.2500,            0.99959314604388958696521068958989891},
            {4.3125,            0.99964094406130644525586201091350343},
            {4.3750,            0.99968312756179494813069349082306235},
            {4.4375,            0.99972035584870534179601447812936151},
            {4.5000,            0.99975321084802753654050617379050162},
            {4.5625,            0.99978220617994689112771768489030236},
            {4.6250,            0.99980779516900105210240981251048167},
            {4.6875,            0.99983037791655283849546303868853396},
            {4.7500,            0.99985030754497877753787358852000255},
            {4.8125,            0.99986789571029070417475400133989992},
            {4.8750,            0.99988341746867772271011794614780441},
            {4.9375,            0.99989711557251558205051185882773206},
            {5.0000,            0.99990920426259513121099044753447306},
            {5.0625,            0.99991987261554158551063867262784721},
            {5.1250,            0.99992928749851651137225712249720606},
            {5.1875,            0.99993759617721206697530526661105307},
            {5.2500,            0.99994492861777083305830639416802036},
            {5.3125,            0.99995139951851344080105352145538345},
            {5.3750,            0.99995711010315817210152906092289064},
            {5.4375,            0.99996214970350792531554669737676253},
            {5.5000,            0.99996659715630380963848952941756868},
            {5.5625,            0.99997052203605101013786592945475432},
            {5.6250,            0.99997398574306704793434088941484766},
            {5.6875,            0.99997704246374583929961850444364696},
            {5.7500,            0.99997974001803825215761760428815437},
            {5.8125,            0.99998212060739040166557477723121777},
            {5.8750,            0.99998422147482750993344503195672517},
            {5.9375,            0.99998607548749972326220227464612338},
            {6.0000,            0.99998771165079557056434885235523206},
            {6.0625,            0.99998915556205996764518917496149338},
            {6.1250,            0.99999042981101021976277974520745310},
            {6.1875,            0.99999155433311068015449574811497719},
            {6.2500,            0.99999254672143162687722782398104276},
            {6.3125,            0.99999342250186907900400800240980139},
            {6.3750,            0.99999419537602957780612639767025158},
            {6.4375,            0.99999487743557848265406225515388994},
            {6.5000,            0.99999547935140419285107893831698753},
            {6.5625,            0.99999601054055694588617385671796346},
            {6.6250,            0.99999647931357331502887600387959900},
            {6.6875,            0.99999689300449080997594368612277442},
            {6.7500,            0.99999725808558628431084200832778748},
            {6.8125,            0.99999758026863294516387464046135924},
            {6.8750,            0.99999786459425991170635407313276785},
            {6.9375,            0.99999811551081218572759991597586905},
            {7.0000,            0.99999833694394467173571641595066708},
            {7.0625,            0.99999853235803894918375164252059190},
            {7.1250,            0.99999870481040359014665019356422927},
            {7.1875,            0.99999885699910593255108365463415411},
            {7.2500,            0.99999899130518359709674536482047025},
            {7.3125,            0.99999910982989611769943303422227663},
            {7.3750,            0.99999921442759946591163427422888252},
            {7.4375,            0.99999930673475777603853435094943258},
            {7.5000,            0.99999938819554614875054970643513124},
            {7.5625,            0.99999946008444508183970109263856958},
            {7.6250,            0.99999952352618001331402589096040117},
            {7.6875,            0.99999957951331792817413683491979752},
            {7.7500,            0.99999962892179632633374697389145081},
            {7.8125,            0.99999967252462750190604116210421169},
            {7.8750,            0.99999971100399253750324718031574484},
            {7.9375,            0.99999974496191422474977283863588658},
            {8.0000,            0.99999977492967588981001883295636840},
            {8.0625,            0.99999980137613348259726597081723424},
            {8.1250,            0.99999982471505097353529823063673263},
            {8.1875,            0.99999984531157382142423402736529911},
            {8.2500,            0.99999986348794179107425910499030547},
            {8.3125,            0.99999987952853049895833839645847571},
            {8.3750,            0.99999989368430056302584289932834041},
            {8.4375,            0.99999990617672396471542088609051728},
            {8.5000,            0.99999991720124905211338798152800748},
            {8.5625,            0.99999992693035839516545287745322387},
            {8.6250,            0.99999993551626733394129009365703767},
            {8.6875,            0.99999994309330543951799157347876934},
            {8.7500,            0.99999994978001814614368429416607424},
            {8.8125,            0.99999995568102143535399207289008504},
            {8.8750,            0.99999996088863858914831986187674522},
            {8.9375,            0.99999996548434461974481685677429908},
            {9.0000,            0.99999996954004097447930211118358244},
            {9.0625,            0.99999997311918045901919121395899372},
            {9.1250,            0.99999997627775997868467948564005257},
            {9.1875,            0.99999997906519662964368381583648379},
            {9.2500,            0.99999998152510084671976114264303159},
            {9.3125,            0.99999998369595870397054673668361266},
            {9.3750,            0.99999998561173404286033236040150950},
            {9.4375,            0.99999998730239984852716512979473289},
            {9.5000,            0.99999998879440718770812040917618843},
            {9.5625,            0.99999999011109904501789298212541698},
            {9.6250,            0.99999999127307553219220251303121960},
            {9.6875,            0.99999999229851618412119275358396363},
            {9.7500,            0.99999999320346438410630581726217930},
            {9.8125,            0.99999999400207836827291739324060736},
            {9.8750,            0.99999999470685273619047001387577653},
            {9.9375,            0.99999999532881393331131526966058758},
            {10.0000,           0.99999999587769276361959283713827574},
        };

        for(int i = 0; i < testCases.length; i++) {
            double [] testCase = testCases[i];
            failures += testTanhCaseWithUlpDiff(testCase[0],
                                                testCase[1],
                                                3.0);
        }

        for(double nan : Tests.NaNs) {
            failures += testTanhCaseWithUlpDiff(nan, NaNd, 0);
        }

        double [][] specialTestCases = {
            {0.0,                       0.0},
            {Double.POSITIVE_INFINITY,  1.0}
        };

        for(int i = 0; i < specialTestCases.length; i++) {
            failures += testTanhCaseWithUlpDiff(specialTestCases[i][0],
                                                specialTestCases[i][1],
                                                0.0);
        }

        // For powers of 2 less than 2^(-27), the second and
        // subsequent terms of the Taylor series expansion will get
        // rounded away since |n-n^3| > 53, the binary precision of a
        // double significand.

        for(int i = DoubleConsts.MIN_SUB_EXPONENT; i < -27; i++) {
            double d = Math.scalb(2.0, i);

            // Result and expected are the same.
            failures += testTanhCaseWithUlpDiff(d, d, 2.5);
        }

        // For values of x larger than 22, tanh(x) is 1.0 in double
        // floating-point arithmetic.

        for(int i = 22; i < 32; i++) {
            failures += testTanhCaseWithUlpDiff(i, 1.0, 2.5);
        }

        for(int i = 5; i <= Double.MAX_EXPONENT; i++) {
            double d = Math.scalb(2.0, i);

            failures += testTanhCaseWithUlpDiff(d, 1.0, 2.5);
        }

        failures += testTanhAdditionalTests();

        return failures;
    }

    /**
     * Test accuracy of {Math, StrictMath}.tanh using quad precision
     * tanh implementation as the reference. There are additional tests.
     * The specified accuracy is 2.5 ulps.
     *
     */
    static int testTanhAdditionalTests() {
        int failures = 0;
        /*
         * Array elements below are generated using a quad precision tanh
         * implementation (libquadmath). Rounded to a double, the quad result
         * *should* be correctly rounded, unless we are quite unlucky.
         * Assuming the quad value is a correctly rounded double, the
         * allowed error is 3.0 ulps instead of 2.5 since the quad
         * value rounded to double can have its own 1/2 ulp error.
         */
        double[][] testCases = {
            // x                                                   tanh(x)
            {1.09951162777600024414062500000000000e+12,            1.00000000000000000000000000000000000e+00},
            {1.56250000000000416333634234433702659e-02,            1.56237285584089068255495133849899136e-02},
            {1.61254882812500000000000000000000000e+01,            9.99999999999980293529906376885389048e-01},
            {2.53165043529127054000582575099542737e-01,            2.47891535884497437358843835970604812e-01},
            {2.05669906337718799704816774465143681e+00,            9.67821952180774991463712302156014956e-01},
            {8.73243486124784240587359818164259195e+00,            9.99999947984421044859570034536492937e-01},
            {1.35302734375000000000000000000000000e+00,            8.74765946489987955543753077657414741e-01},
            {7.51299319580434721288497712521348149e-01,            6.35923468395323117288273690770900477e-01},
            {9.53088818012631927567568368431238923e-02,            9.50213381512267711017656118902912332e-02},
            {7.64443165964961757197215774795040488e-01,            6.43686625696507143760198874608796949e-01},
            {9.80772770147126660145175947036477737e-02,            9.77640088885469645387119927980991050e-02},
            {8.00000000000000044408920985006261617e-01,            6.64036770267848988511881426480887109e-01},
            {6.58800443825626694943631278533757722e-03,            6.58790912948844334160953310959647563e-03},
            {3.50634723606509357551885841530747712e+00,            9.98200861366828007281302037717336212e-01},
            {8.80951107580675074615328412619419396e-01,            7.06895478355484050029724917425086249e-01},
            {9.41693953354077795125931515940465033e-01,            7.35999567964351009171211613664845735e-01},
            {4.86714106743433794211028953213826753e-01,            4.51604571680788935707314000261162601e-01},
            {4.99999999970896114032115065128891729e-01,            4.62117157237121073362068671381593592e-01},
            {1.27999999999999971578290569595992565e+02,            1.00000000000000000000000000000000000e+00},
            {1.00000000000000022204460492503130808e+00,            7.61594155955764981372495044941331753e-01},
            {1.09951162777600024414062500000000000e+12,            1.00000000000000000000000000000000000e+00},
            {5.00000000000000777156117237609578297e-01,            4.62117157260010369694985045764006657e-01},
            {3.90625000000001474514954580286030250e-03,            3.90623013190635482599726614938805467e-03},
            {1.56250000000000659194920871186695877e-02,            1.56237285584089311057499113400637264e-02},
            {1.25000000000001332267629550187848508e-01,            1.24353001771597519720531117125519878e-01},
            {1.56250000000005169475958410885141348e-02,            1.56237285584093820237573019342883109e-02},
            {2.00000000000022737367544323205947876e+00,            9.64027580075832948084133680630298643e-01},
            {6.25000000000080352391407245704613160e-02,            6.24187467475205184231888372622987839e-02},
            {2.50000000000049737991503207013010979e-01,            2.44918662403755883728363950973251081e-01},
            {2.50000000000454747350886464118957520e-01,            2.44918662404136598540089724354621762e-01},
            {7.81250000001537658889105841808486730e-03,            7.81234105817638947180855590780540396e-03},
            {8.00000000002179945113311987370252609e+00,            9.99999774929675899622836792366347278e-01},
            {8.00000000002182787284255027770996094e+00,            9.99999774929675899635630557632573807e-01},
            {1.00000000004506106598967107856879011e+00,            7.61594155974689379640247120538425632e-01},
            {5.00000000024432678102925819985102862e-01,            4.62117157279224782806433798595181278e-01},
            {5.00000000124148025193449029757175595e-01,            4.62117157357645691462301850285961295e-01},
            {1.25000000043655745685100555419921875e-01,            1.24353001814576875736126314329404676e-01},
            {1.56250130385160446166992187500000000e-02,            1.56237415937421937398207048034470765e-02},
            {6.25000596046447753906250000000000000e-02,            6.24188061199314157260056878713262148e-02},
            {6.25001570879248902201652526855468750e-02,            6.24189032234056148184566765458350515e-02},
            {3.12509536743164062500000000000000000e-02,            3.12407841896026978197614959195842857e-02},
            {1.00024414062500000000000000000000000e+00,            7.61696669690972358277739369649969500e-01},
            {1.25091552734375000000000000000000000e-01,            1.24443137738349286849917747910445080e-01},
            {6.25703578334750876166481248219497502e-02,            6.24888301519391612116796252071905868e-02},
            {2.52525252525252597024518763646483421e-01,            2.47290965006585965880182136581880733e-01},
            {1.00000000164410457817454336293394590e-03,            9.99999668310902934017090322313224382e-04},
            {1.00000000966720672609944209341392707e-03,            9.99999676333997058845099107943491685e-04},
            {5.13687551499984795810860305209644139e-01,            4.72813376851263299460550751434331149e-01},
            {1.03125000000000000000000000000000000e+00,            7.74409187434213568286703209738132986e-01},
            {1.03372912114974835340319714305223897e+00,            7.75399652279487427958938283855319050e-01},
            {8.73243486124791523650401359191164374e+00,            9.99999947984421044867146689152891277e-01},
            {5.46364074509520181166521979321260005e-01,            4.97790203319363272413440879555555135e-01},
            {5.48776992118357842542764046811498702e-01,            4.99603030846724465253358333732665160e-01},
            {8.62884521484375000000000000000000000e-03,            8.62863106199946057455229821459862778e-03},
            {5.56840723899044820477399753144709393e-01,            5.05629619734278492036257594911833276e-01},
            {1.12042968912429174999090264464030042e+00,            8.07718324543002512898290101804260243e-01},
            {2.80761718750000000000000000000000000e-01,            2.73609921989813966516244201735889906e-01},
            {4.50982142857161694138312668655999005e+00,            9.99758010610690750512553927515350523e-01},
            {1.79803946803892764072507759465224808e-02,            1.79784572761372499903768063141254578e-02},
            {2.90674624105783541150316295897937380e-01,            2.82755618405959946459876962574827861e-01},
            {3.00000000019552404140199541870970279e-01,            2.91312612469484033539387970973996561e-01},
            {1.52306844600212459850396840010944288e-01,            1.51139964502163284820786391222343666e-01},
            {1.21913138136517762433186362613923848e+00,            8.39397762830401796350294214789399315e-01},
            {1.91612901016097944562055488404439529e-02,            1.91589453912240029886209020645693670e-02},
            {1.23194037796136601770058405236341059e+00,            8.43141232466373734055029303451281784e-01},
            {5.14544145441922751160745974630117416e+00,            9.99932120037417992977353814124626761e-01},
            {1.29608715898613313655118872702587396e+00,            8.60712461444305632100271902930674052e-01},
            {1.35302734375000000000000000000000000e+00,            8.74765946489987955543753077657414741e-01},
            {6.89141205308152926534148718928918242e-01,            5.97430012402391408227990740295335202e-01},
            {2.16702398900134561576802383342510439e-02,            2.16668484172600518601166701940771309e-02},
            {6.95330121814107471323040954302996397e-01,            6.01395252733578654705526153849150843e-01},
            {1.70127028180982076566857275068400668e-04,            1.70127026539641570641832179464678521e-04},
            {6.98731899876564921392230189667316154e-01,            6.03562246839061712657431798989209285e-01},
            {2.82308042901865396956395670713391155e+00,            9.92962754889608618611084237181745775e-01},
            {8.85009765625000000000000000000000000e-02,            8.82706391518277914218106043840064600e-02},
            {1.44086021505376304929768593865446746e+00,            8.93870759190524111186764508137227647e-01},
            {4.52708479240923750142044923450157512e-02,            4.52399464814195843886615749285771251e-02},
            {7.42434201630502221824770003877347335e-01,            6.30613596749571014884527941122811076e-01},
            {7.47314453125000000000000000000000000e-01,            6.33544059591028741704251380359792317e-01},
            {2.33572976827208893257914468222224968e-02,            2.33530509808936286709795071423335732e-02},
            {7.51392746195329142011587464367039502e-01,            6.35979110106607807348963004903609067e-01},
            {7.51649175412362091641682582121575251e-01,            6.36131796640758591543062918907122080e-01},
            {7.62560692649938864917658065678551793e-01,            6.42582785959772486552828548950126373e-01},
            {7.64660852335945273594575155584607273e-01,            6.43814099671361386286313072270915932e-01},
            {1.92871093750000000000000000000000000e-01,            1.90514597602311764623059750704793759e-01},
            {2.43864313521849479515779535176989157e-02,            2.43815983142663741885939851467013521e-02},
            {3.97705078125000000000000000000000000e-01,            3.77983627858614640283948547303348236e-01},
            {7.98034667968750000000000000000000000e-01,            6.62936606884708330125541187161682941e-01},
            {7.99316406250000000000000000000000000e-01,            6.63654430152659513562528989372441102e-01},
            {1.99890136718750000000000000000000000e-01,            1.97269734600247465099938891830640889e-01},
            {2.00000000008910994164779140191967599e-01,            1.97375320233467849151455260287892058e-01},
            {4.00000000093461316463816501709516160e-01,            3.79948962335194012629557116519596150e-01},
            {2.00000000069810862646235705142316874e-01,            1.97375320291995240418209079080646997e-01},
            {1.00000000056612609045103567950718571e-01,            9.96679946810060529704707312198636589e-02},
            {1.00000000080404896629637789828848327e-01,            9.96679947045619948897444345018478492e-02},
            {1.66666666666666696272613990004174411e+00,            9.31109608667577693190680177920119455e-01},
            {1.31034851074218750000000000000000000e-02,            1.31027351970209980614612589861988504e-02},
            {8.43444227005861080215254332870244980e-01,            6.87629045782656322925865941652078512e-01},
            {4.25596815032856623517432126391213387e-01,            4.01634947321531793299729470086813678e-01},
            {8.54614885269050605920426733064232394e-01,            6.93472710492835200064966331725774025e-01},
            {8.63777419830865200722769259300548583e-01,            6.98198780318331041148483592329099127e-01},
            {2.70117449276632004551146337689715438e-02,            2.70051772786722224566765342032635559e-02},
            {2.16282487792377908775165451515931636e-01,            2.12971988592557031781365419455581001e-01},
            {1.73204653003120601084674490266479552e+00,            9.39297315789076214802641716736658105e-01},
            {2.71436010781672190650404274947504746e-02,            2.71369367992428389623549774823710978e-02},
            {8.69092640155437079485523099720012397e-01,            7.00912831250687651307196017605464473e-01},
            {2.78015136718750000000000000000000000e-02,            2.77943530651526982827985645341156701e-02},
            {9.10156250000000000000000000000000000e-01,            7.21207240669352050307412688531998165e-01},
            {2.27787862235060922788676407435559668e-01,            2.23928183342045426304404589794035157e-01},
            {5.71524498377538048288215577485971153e-02,            5.70903033991663026980553749418981725e-02},
            {3.66406250000000000000000000000000000e+00,            9.98687254335130669671645868977829517e-01},
            {5.72863132979952241474741470028675394e-02,            5.72237295373843844708720164610859507e-02},
            {1.15265335196343660095763539175095502e-01,            1.14757558082362397172277983632352767e-01},
            {9.22871508732805212460448274214286357e-01,            7.27253018562057912939305739474564638e-01},
            {1.44882202148437500000000000000000000e-02,            1.44872065663080247568859985337817684e-02},
            {2.33459472656250000000000000000000000e-01,            2.29308506606965514793638071915067313e-01},
            {4.67608948699241744328958247933769599e-01,            4.36265367328226513916944408221096440e-01},
            {2.34375000000000000000000000000000000e-01,            2.30175711032132981819570603563403063e-01},
            {2.93977526337722387672624080323657836e-02,            2.93892867747176836833509722656208701e-02},
            {1.89257812500000000000000000000000000e+00,            9.55597542193888546329823463414294394e-01},
            {2.95798696005085230698039566732404637e-02,            2.95712454656271068251835101101858187e-02},
            {1.89360756176453159937977943627629429e+00,            9.55686843743833788059988471898348142e-01},
            {4.74943000289441419337066463413066231e-01,            4.42184502480986035803118250513914071e-01},
            {4.76562500000000000000000000000000000e-01,            4.43486412595195826790440814160101630e-01},
            {9.59027831303091549131067949929274619e-01,            7.43842915769532264613887042424467929e-01},
            {3.09784640940728682456661857713697827e-02,            3.09685582448730820784897541732374591e-02},
            {1.98437499999999977795539507496869192e+00,            9.62906870975765387287608356129776957e-01},
            {9.97205648659918675313917901803506538e-01,            7.60418100316000600203658397859135661e-01},
            {3.90291213989257769131913100579822640e-03,            3.90289232268659551022766662201699736e-03},
            {3.90481948852539019131913100579822640e-03,            3.90479964225138860483705936617354227e-03},
            {3.12423706054687500000000000000000000e-02,            3.12322094954161727499363714231262395e-02},
            {3.90535406768321947598709975579822640e-03,            3.90533421325712750455622728849037270e-03},
            {7.81154632568359288263826201159645279e-03,            7.81138744204279466358299901763388375e-03},
            {1.24999789521095569511111023075500270e-01,            1.24352794547462473786771370350680283e-01},
            {9.99999444341875043384959553804947063e-01,            7.61593922593510941370556728778707492e-01},
            {9.99999895691871532044103787484345958e-01,            7.61594112149023829770882693858011645e-01},
            {2.49999998130078865399283927217766177e-01,            2.44918660645955495851244772320875652e-01},
            {2.49999998603016110321206610933586489e-01,            2.44918661090523528987141309434443089e-01},
            {4.99999999970896114032115065128891729e-01,            4.62117157237121073362068671381593592e-01},
            {9.99999999999829358721115113439736888e-01,            7.61594155955693223160706417649502130e-01},
            {3.12499999999979183318288278314867057e-02,            3.12398314460291771315638233977623908e-02},
            {6.24999999999973701592104191604448715e-02,            6.24187467475098948954758673929811576e-02},
            {9.99999999999998556710067987296497449e-01,            7.61594155955764281974719327416526334e-01},
            {1.27999999999999971578290569595992565e+02,            1.00000000000000000000000000000000000e+00},
            {3.44827586206896546938693859374325257e-02,            3.44690977543900329082306735053903756e-02},
            {6.89655172413793093877387718748650514e-02,            6.88563859490195017187269420471893052e-02},
            {1.03448275862068964081608157812297577e-01,            1.03080829858086020470241143281488892e-01},
            {1.37931034482758618775477543749730103e-01,            1.37062928881132531260309423128680656e-01},
            {1.72413793103448287347134737501619384e-01,            1.70725445282084714146447066718646674e-01},
            {2.06896551724137955918791931253508665e-01,            2.03994088403983264406130799853712156e-01},
            {2.41379310344827624490449125005397946e-01,            2.36798141876826809207868665407968027e-01},
            {2.75862068965517293062106318757287227e-01,            2.69071023201531202536913498454407638e-01},
            {3.10344827586206961633763512509176508e-01,            3.00750767242988858303859696730149916e-01},
            {3.44827586206896630205420706261065789e-01,            3.31780427497542984412066808006924260e-01},
            {3.79310344827586298777077900012955070e-01,            3.62108391409330839416919529705937418e-01},
            {4.13793103448275967348735093764844351e-01,            3.91688608393346163715111892758489641e-01},
            {4.48275862068965635920392287516733631e-01,            4.20480731486975012003415012347372452e-01},
            {4.82758620689655304492049481268622912e-01,            4.48450175615929701255698232730127770e-01},
            {5.17241379310344973063706675020512193e-01,            4.75568097261544496368767486763625886e-01},
            {5.51724137931034586124212637514574453e-01,            5.01811301809605377924874787743959204e-01},
            {5.86206896551724199184718600008636713e-01,            5.27162086020673527345538794213535134e-01},
            {6.20689655172413812245224562502698973e-01,            5.51608023880856575817362987825134405e-01},
            {6.55172413793103425305730524996761233e-01,            5.75141704579102279221464447290041163e-01},
            {6.89655172413793038366236487490823492e-01,            5.97760431534850182076591161239096491e-01},
            {7.24137931034482651426742449984885752e-01,            6.19465891301270655454827665546029664e-01},
            {7.58620689655172264487248412478948012e-01,            6.40263800834536321750527885396253899e-01},
            {7.93103448275861877547754374973010272e-01,            6.60163541092833363676687202005166905e-01},
            {8.27586206896551490608260337467072532e-01,            6.79177784255529339466238655218797135e-01},
            {8.62068965517241103668766299961134791e-01,            6.97322121077226884958095667604561029e-01},
            {8.96551724137930716729272262455197051e-01,            7.14614694054361357412620518070189428e-01},
            {9.31034482758620329789778224949259311e-01,            7.31075841220047215751737025073520835e-01},
            {9.65517241379309942850284187443321571e-01,            7.46727754527182387965057729340710925e-01},
            {9.99999999999999555910790149937383831e-01,            7.61594155955764701613384757931622516e-01},
            {1.26765060022822940149670320537600000e+30,            1.00000000000000000000000000000000000e+00},
            {1.33436905287182034855574634496000000e+30,            1.00000000000000000000000000000000000e+00},
            {1.40108750551541129561478948454400000e+30,            1.00000000000000000000000000000000000e+00},
            {1.46780595815900224267383262412800000e+30,            1.00000000000000000000000000000000000e+00},
            {1.53452441080259318973287576371200000e+30,            1.00000000000000000000000000000000000e+00},
            {1.60124286344618413679191890329600000e+30,            1.00000000000000000000000000000000000e+00},
            {1.66796131608977508385096204288000000e+30,            1.00000000000000000000000000000000000e+00},
            {1.73467976873336603091000518246400000e+30,            1.00000000000000000000000000000000000e+00},
            {1.80139822137695697796904832204800000e+30,            1.00000000000000000000000000000000000e+00},
            {1.86811667402054792502809146163200000e+30,            1.00000000000000000000000000000000000e+00},
            {1.93483512666413887208713460121600000e+30,            1.00000000000000000000000000000000000e+00},
            {2.00155357930772981914617774080000000e+30,            1.00000000000000000000000000000000000e+00},
            {2.06827203195132076620522088038400000e+30,            1.00000000000000000000000000000000000e+00},
            {2.13499048459491171326426401996800000e+30,            1.00000000000000000000000000000000000e+00},
            {2.20170893723850266032330715955200000e+30,            1.00000000000000000000000000000000000e+00},
            {2.26842738988209360738235029913600000e+30,            1.00000000000000000000000000000000000e+00},
            {2.33514584252568455444139343872000000e+30,            1.00000000000000000000000000000000000e+00},
            {2.40186429516927550150043657830400000e+30,            1.00000000000000000000000000000000000e+00},
            {2.46858274781286644855947971788800000e+30,            1.00000000000000000000000000000000000e+00},
            {2.53530120045645739561852285747200000e+30,            1.00000000000000000000000000000000000e+00},
            {1.60693804425899027554196209234116260e+60,            1.00000000000000000000000000000000000e+00},
            {1.69151373079893703825155926128281056e+60,            1.00000000000000000000000000000000000e+00},
            {1.77608941733888380096115643022445853e+60,            1.00000000000000000000000000000000000e+00},
            {1.86066510387883056367075359916610649e+60,            1.00000000000000000000000000000000000e+00},
            {1.94524079041877732638035076810775445e+60,            1.00000000000000000000000000000000000e+00},
            {2.02981647695872408908994793704940241e+60,            1.00000000000000000000000000000000000e+00},
            {2.11439216349867085179954510599105038e+60,            1.00000000000000000000000000000000000e+00},
            {2.19896785003861761450914227493269834e+60,            1.00000000000000000000000000000000000e+00},
            {2.28354353657856437721873944387434630e+60,            1.00000000000000000000000000000000000e+00},
            {2.36811922311851113992833661281599426e+60,            1.00000000000000000000000000000000000e+00},
            {2.45269490965845790263793378175764222e+60,            1.00000000000000000000000000000000000e+00},
            {2.53727059619840466534753095069929019e+60,            1.00000000000000000000000000000000000e+00},
            {2.62184628273835142805712811964093815e+60,            1.00000000000000000000000000000000000e+00},
            {2.70642196927829819076672528858258611e+60,            1.00000000000000000000000000000000000e+00},
            {2.79099765581824495347632245752423407e+60,            1.00000000000000000000000000000000000e+00},
            {2.87557334235819171618591962646588203e+60,            1.00000000000000000000000000000000000e+00},
            {2.96014902889813847889551679540753000e+60,            1.00000000000000000000000000000000000e+00},
            {3.04472471543808524160511396434917796e+60,            1.00000000000000000000000000000000000e+00},
            {3.12930040197803200431471113329082592e+60,            1.00000000000000000000000000000000000e+00},
            {3.21387608851797876702430830223247388e+60,            1.00000000000000000000000000000000000e+00},
            {1.07150860718626732094842504906000181e+301,           1.00000000000000000000000000000000000e+00},
            {1.12790379703817606470289337889334663e+301,           1.00000000000000000000000000000000000e+00},
            {1.18429898689008480845736170872669145e+301,           1.00000000000000000000000000000000000e+00},
            {1.24069417674199355221183003856003627e+301,           1.00000000000000000000000000000000000e+00},
            {1.29708936659390229596629836839338109e+301,           1.00000000000000000000000000000000000e+00},
            {1.35348455644581103972076669822672591e+301,           1.00000000000000000000000000000000000e+00},
            {1.40987974629771978347523502806007073e+301,           1.00000000000000000000000000000000000e+00},
            {1.46627493614962852722970335789341555e+301,           1.00000000000000000000000000000000000e+00},
            {1.52267012600153727098417168772676037e+301,           1.00000000000000000000000000000000000e+00},
            {1.57906531585344601473864001756010519e+301,           1.00000000000000000000000000000000000e+00},
            {1.63546050570535475849310834739345001e+301,           1.00000000000000000000000000000000000e+00},
            {1.69185569555726350224757667722679483e+301,           1.00000000000000000000000000000000000e+00},
            {1.74825088540917224600204500706013965e+301,           1.00000000000000000000000000000000000e+00},
            {1.80464607526108098975651333689348447e+301,           1.00000000000000000000000000000000000e+00},
            {1.86104126511298973351098166672682928e+301,           1.00000000000000000000000000000000000e+00},
            {1.91743645496489847726544999656017410e+301,           1.00000000000000000000000000000000000e+00},
            {1.97383164481680722101991832639351892e+301,           1.00000000000000000000000000000000000e+00},
            {2.03022683466871596477438665622686374e+301,           1.00000000000000000000000000000000000e+00},
            {2.08662202452062470852885498606020856e+301,           1.00000000000000000000000000000000000e+00},
            {2.14301721437253345228332331589355338e+301,           1.00000000000000000000000000000000000e+00},
            {4.94065645841246544176568792868221372e-324,           4.94065645841246544176568792868221372e-324},
            {4.94065645841246544176568792868221372e-324,           4.94065645841246544176568792868221372e-324},
            {4.99999999999999944488848768742172979e-01,            4.62117157260009714845699443492203290e-01},
            {5.00000000000000000000000000000000000e-01,            4.62117157260009758502318483643672557e-01},
            {5.00000000000000111022302462515654042e-01,            4.62117157260009845815556563946604302e-01},
            {5.49306144334054669009503868437604979e-01,            4.99999999999999867483910937482244858e-01},
            {5.49306144334054780031806330953259021e-01,            4.99999999999999950750637784368995452e-01},
            {5.49306144334054891054108793468913063e-01,            5.00000000000000034017364631255736851e-01},
            {2.19999999999999964472863211994990706e+01,            9.99999999999999999844377355177323009e-01},
            {2.20000000000000000000000000000000000e+01,            9.99999999999999999844377355177324068e-01},
            {2.20000000000000035527136788005009294e+01,            9.99999999999999999844377355177325223e-01},
            {6.93147180559945397249066445510834455e-01,            6.00000000000000056212373967393698031e-01},
            {6.93147180559945286226763982995180413e-01,            5.99999999999999985158100391383682202e-01},
            {6.93147180559945175204461520479526371e-01,            5.99999999999999914103826815373657032e-01},
            {3.46573590279972698624533222755417228e-01,            3.33333333333333372369704144023402903e-01},
            {3.46573590279972643113381991497590207e-01,            3.33333333333333323026458605127557235e-01},
            {3.46573590279972587602230760239763185e-01,            3.33333333333333273683213066231709688e-01},
            {1.73286795139986349312266611377708614e-01,            1.71572875253809923708199182915954510e-01},
            {1.73286795139986321556690995748795103e-01,            1.71572875253809896769671427846052946e-01},
            {1.73286795139986293801115380119881593e-01,            1.71572875253809869831143672776151118e-01},
            {8.66433975699931746561333056888543069e-02,            8.64272337258898029408455765418952337e-02},
            {8.66433975699931607783454978743975516e-02,            8.64272337258897891667202185946638536e-02},
            {8.66433975699931469005576900599407963e-02,            8.64272337258897753925948606474324374e-02},
            {4.33216987849965873280666528444271535e-02,            4.32946174993891841617996586480128793e-02},
            {4.33216987849965803891727489371987758e-02,            4.32946174993891772359121833444914284e-02},
            {4.33216987849965734502788450299703982e-02,            4.32946174993891703100247080409699774e-02},
            {2.16608493924982936640333264222135767e-02,            2.16574623262262954492383391751347008e-02},
            {2.16608493924982901945863744685993879e-02,            2.16574623262262919814187163069359478e-02},
            {2.16608493924982867251394225149851991e-02,            2.16574623262262885135990934387371889e-02},
            {2.16608493924982867251394225149851991e-02,            2.16574623262262885135990934387371889e-02},
            {2.16608493924982901945863744685993879e-02,            2.16574623262262919814187163069359478e-02},
            {2.16608493924982936640333264222135767e-02,            2.16574623262262954492383391751347008e-02},
            {4.33216987849965734502788450299703982e-02,            4.32946174993891703100247080409699774e-02},
            {4.33216987849965803891727489371987758e-02,            4.32946174993891772359121833444914284e-02},
            {4.33216987849965873280666528444271535e-02,            4.32946174993891841617996586480128793e-02},
            {8.66433975699931469005576900599407963e-02,            8.64272337258897753925948606474324374e-02},
            {8.66433975699931607783454978743975516e-02,            8.64272337258897891667202185946638536e-02},
            {8.66433975699931746561333056888543069e-02,            8.64272337258898029408455765418952337e-02},
            {1.73286795139986293801115380119881593e-01,            1.71572875253809869831143672776151118e-01},
            {1.73286795139986321556690995748795103e-01,            1.71572875253809896769671427846052946e-01},
            {1.73286795139986349312266611377708614e-01,            1.71572875253809923708199182915954510e-01},
            {3.46573590279972587602230760239763185e-01,            3.33333333333333273683213066231709688e-01},
            {3.46573590279972643113381991497590207e-01,            3.33333333333333323026458605127557235e-01},
            {3.46573590279972698624533222755417228e-01,            3.33333333333333372369704144023402903e-01},
            {6.93147180559945175204461520479526371e-01,            5.99999999999999914103826815373657032e-01},
            {6.93147180559945286226763982995180413e-01,            5.99999999999999985158100391383682202e-01},
            {6.93147180559945397249066445510834455e-01,            6.00000000000000056212373967393698031e-01},
            {7.09782712893383859409368596971035004e+02,            1.00000000000000000000000000000000000e+00},
            {7.09782712893383973096206318587064743e+02,            1.00000000000000000000000000000000000e+00},
            {7.09782712893384086783044040203094482e+02,            1.00000000000000000000000000000000000e+00},
            {7.41782712893384086783044040203094482e+02,            1.00000000000000000000000000000000000e+00},
            {7.41782712893383973096206318587064743e+02,            1.00000000000000000000000000000000000e+00},
            {7.41782712893383859409368596971035004e+02,            1.00000000000000000000000000000000000e+00},
            {7.10475860073943749739555642008781433e+02,            1.00000000000000000000000000000000000e+00},
            {7.10475860073943863426393363624811172e+02,            1.00000000000000000000000000000000000e+00},
            {7.10475860073943977113231085240840912e+02,            1.00000000000000000000000000000000000e+00},
            {7.09782712893384086783044040203094482e+02,            1.00000000000000000000000000000000000e+00},
            {7.09782712893383973096206318587064743e+02,            1.00000000000000000000000000000000000e+00},
            {7.09782712893383859409368596971035004e+02,            1.00000000000000000000000000000000000e+00},
            {9.22337203685477478400000000000000000e+18,            1.00000000000000000000000000000000000e+00},
            {9.22337203685477580800000000000000000e+18,            1.00000000000000000000000000000000000e+00},
            {9.22337203685477785600000000000000000e+18,            1.00000000000000000000000000000000000e+00},
            {1.34217727999999985098838806152343750e+08,            1.00000000000000000000000000000000000e+00},
            {1.34217728000000000000000000000000000e+08,            1.00000000000000000000000000000000000e+00},
            {1.34217728000000029802322387695312500e+08,            1.00000000000000000000000000000000000e+00},
            {1.67772159999999981373548507690429688e+07,            1.00000000000000000000000000000000000e+00},
            {1.67772160000000000000000000000000000e+07,            1.00000000000000000000000000000000000e+00},
            {1.67772160000000037252902984619140625e+07,            1.00000000000000000000000000000000000e+00},
            {3.19999999999999964472863211994990706e+01,            9.99999999999999999999999999679237812e-01},
            {3.20000000000000000000000000000000000e+01,            9.99999999999999999999999999679237812e-01},
            {3.20000000000000071054273576010018587e+01,            9.99999999999999999999999999679237812e-01},
            {1.59999999999999982236431605997495353e+01,            9.99999999999974671668901811879331665e-01},
            {1.60000000000000000000000000000000000e+01,            9.99999999999974671668901811969315927e-01},
            {1.60000000000000035527136788005009294e+01,            9.99999999999974671668901812149284547e-01},
            {7.99999999999999911182158029987476766e+00,            9.99999774929675889809619027791781323e-01},
            {8.00000000000000000000000000000000000e+00,            9.99999774929675889810018832956368404e-01},
            {8.00000000000000177635683940025046468e+00,            9.99999774929675889810818443285542469e-01},
            {3.99999999999999955591079014993738383e+00,            9.99329299739067043196741615068852355e-01},
            {4.00000000000000000000000000000000000e+00,            9.99329299739067043792243344341724993e-01},
            {4.00000000000000088817841970012523234e+00,            9.99329299739067044983246802887468536e-01},
            {1.99999999999999977795539507496869192e+00,            9.64027580075816868258779231952432911e-01},
            {2.00000000000000000000000000000000000e+00,            9.64027580075816883946413724100923171e-01},
            {2.00000000000000044408920985006261617e+00,            9.64027580075816915321682708397883469e-01},
            {9.99999999999999888977697537484345958e-01,            7.61594155955764841492939901436512668e-01},
            {1.00000000000000000000000000000000000e+00,            7.61594155955764888119458282604793657e-01},
            {1.00000000000000022204460492503130808e+00,            7.61594155955764981372495044941331753e-01},
            {4.99999999999999944488848768742172979e-01,            4.62117157260009714845699443492203290e-01},
            {5.00000000000000000000000000000000000e-01,            4.62117157260009758502318483643672557e-01},
            {5.00000000000000111022302462515654042e-01,            4.62117157260009845815556563946604302e-01},
            {2.49999999999999972244424384371086489e-01,            2.44918662403709103187147915631612892e-01},
            {2.50000000000000000000000000000000000e-01,            2.44918662403709129277801131491016945e-01},
            {2.50000000000000055511151231257827021e-01,            2.44918662403709181459107563209824042e-01},
            {1.24999999999999986122212192185543245e-01,            1.24353001771596194391460985792144305e-01},
            {1.25000000000000000000000000000000000e-01,            1.24353001771596208054647275805892707e-01},
            {1.25000000000000027755575615628913511e-01,            1.24353001771596235381019855833389378e-01},
            {6.24999999999999930611060960927716224e-02,            6.24187467475125075782836114480350829e-02},
            {6.25000000000000000000000000000000000e-02,            6.24187467475125144901428911942113317e-02},
            {6.25000000000000138777878078144567553e-02,            6.24187467475125283138614506865638292e-02},
            {3.12499999999999965305530480463858112e-02,            3.12398314460312533021176543496182149e-02},
            {3.12500000000000000000000000000000000e-02,            3.12398314460312567681786791091369499e-02},
            {3.12500000000000069388939039072283776e-02,            3.12398314460312637003007286281744168e-02},
            {1.56249999999999982652765240231929056e-02,            1.56237285584088634680488027509294906e-02},
            {1.56250000000000000000000000000000000e-02,            1.56237285584088652023488311762919065e-02},
            {1.56250000000000034694469519536141888e-02,            1.56237285584088686709488880270167445e-02},
            {6.10351562499999932237364219655972875e-05,            6.10351561742087681889301535131725312e-05},
            {6.10351562500000000000000000000000000e-05,            6.10351561742087749651937063040263414e-05},
            {6.10351562500000135525271560688054251e-05,            6.10351561742087885177208118857339557e-05},
            {9.31322574615478412227423430871540641e-10,            9.31322574615478411958158908556102005e-10},
            {9.31322574615478515625000000000000000e-10,            9.31322574615478515355735477684561274e-10},
            {9.31322574615478722420153138256918718e-10,            9.31322574615478722150888615941479902e-10},
            {2.77555756156289104291028806826931429e-17,            2.77555756156289104291028806826931349e-17},
            {2.77555756156289135105907917022705078e-17,            2.77555756156289135105907917022704998e-17},
            {2.77555756156289196735666137414252376e-17,            2.77555756156289196735666137414252322e-17},
            {1.79769313486231570814527423731704357e+308,           1.00000000000000000000000000000000000e+00},
            {1.79769313486231570814527423731704357e+308,           1.00000000000000000000000000000000000e+00},
            {1.79769313486231570814527423731704357e+308,           1.00000000000000000000000000000000000e+00},
            {1.79769313486231550856124328384506240e+308,           1.00000000000000000000000000000000000e+00},
            {3.14159265358979311599796346854418516e+00,            9.96272076220749943353314537833579484e-01},
            {1.57079632679489655799898173427209258e+00,            9.17152335667274336647462811870662140e-01},
            {1.00000000000000022204460492503130808e+00,            7.61594155955764981372495044941331753e-01},
            {1.00000000000000000000000000000000000e+00,            7.61594155955764888119458282604793657e-01},
            {9.99999999999999888977697537484345958e-01,            7.61594155955764841492939901436512668e-01},
            {7.85398163397448278999490867136046290e-01,            6.55794202632672418203926030568705821e-01},
            {2.22507385850720187715587855857894824e-308,           2.22507385850720187715587855857894824e-308},
            {2.22507385850720138309023271733240406e-308,           2.22507385850720138309023271733240406e-308},
            {2.22507385850720088902458687608585989e-308,           2.22507385850720088902458687608585989e-308},
            {2.22507385850720039495894103483931571e-308,           2.22507385850720039495894103483931571e-308},
            {9.88131291682493088353137585736442745e-324,           9.88131291682493088353137585736442745e-324},
            {4.94065645841246544176568792868221372e-324,           4.94065645841246544176568792868221372e-324},
        };

        for (int i = 0; i < testCases.length; i++) {
            double[] testCase = testCases[i];
            failures += testTanhCaseWithUlpDiff(testCase[0],
                    testCase[1],
                    3.0);
        }

        return failures;
    }

    public static int testTanhCaseWithTolerance(double input,
                                                double expected,
                                                double tolerance) {
        int failures = 0;
        failures += Tests.testTolerance("Math.tanh",       input, Math::tanh,         expected, tolerance);
        failures += Tests.testTolerance("Math.tanh",      -input, Math::tanh,        -expected, tolerance);

        failures += Tests.testTolerance("StrictMath.tanh",  input, StrictMath::tanh,  expected, tolerance);
        failures += Tests.testTolerance("StrictMath.tanh", -input, StrictMath::tanh, -expected, tolerance);
        return failures;
    }

    public static int testTanhCaseWithUlpDiff(double input,
                                              double expected,
                                              double ulps) {
        int failures = 0;

        failures += Tests.testUlpDiffWithAbsBound("Math.tanh",       input,  Math::tanh,       expected, ulps, 1.0);
        failures += Tests.testUlpDiffWithAbsBound("Math.tanh",      -input,  Math::tanh,      -expected, ulps, 1.0);

        failures += Tests.testUlpDiffWithAbsBound("StrictMath.tanh",  input, StrictMath::tanh,  expected, ulps, 1.0);
        failures += Tests.testUlpDiffWithAbsBound("StrictMath.tanh", -input, StrictMath::tanh, -expected, ulps, 1.0);
        return failures;
    }
}
