/*
 * Copyright (c) 2005, 2006, Oracle and/or its affiliates. All rights reserved.
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

package sun.nio.cs.ext;

import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;

class JIS_X_0212_MS5022X_Decoder extends JIS_X_0212_Decoder
{
    private int _start, _end;

    public JIS_X_0212_MS5022X_Decoder(Charset cs) {
        super(cs);
        _start = 0x21;
        _end = 0x7E;
    }

    protected char decodeDouble(int byte1, int byte2) {
        if (((byte1 < 0) || (byte1 > _index1.length))
            || ((byte2 < _start) || (byte2 > _end)))
            return REPLACE_CHAR;
        int n = (_index1[byte1] & 0xf)*(_end - _start + 1) + (byte2 - _start);
        char unicode = _index2[_index1[byte1] >> 4].charAt(n);
        if (unicode == '\u0000')
            return (super.decodeDouble(byte1, byte2));
        else
            return unicode;
    }

    private final static String _innerIndex0=
        "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000"+
        "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000"+
        "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000"+
        "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000"+
        "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000"+
        "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000"+
        "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000"+
        "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000"+
        "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000"+
        "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000"+
        "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000"+
        "\u0000\u0000\u0000\u0000\u0000\u0000\u2170\u2171"+
        "\u2172\u2173\u2174\u2175\u2176\u2177\u2178\u2179"+
        "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000"+
        "\u0000\u0000\uFF07\uFF02\u0000\u0000\u0000\u70BB"+
        "\u4EFC\u50F4\u51EC\u5307\u5324\uFA0E\u548A\u5759"+
        "\uFA0F\uFA10\u589E\u5BEC\u5CF5\u5D53\uFA11\u5FB7"+
        "\u6085\u6120\u654E\u0000\u6665\uFA12\uF929\u6801"+
        "\uFA13\uFA14\u6A6B\u6AE2\u6DF8\u6DF2\u7028\uFA15"+
        "\uFA16\u7501\u7682\u769E\uFA17\u7930\uFA18\uFA19"+
        "\uFA1A\uFA1B\u7AE7\uFA1C\uFA1D\u7DA0\u7DD6\uFA1E"+
        "\u8362\uFA1F\u85B0\uFA20\uFA21\u8807\uFA22\u8B7F"+
        "\u8CF4\u8D76\uFA23\uFA24\uFA25\u90DE\uFA26\u9115"+
        "\uFA27\uFA28\u9592\uF9DC\uFA29\u973B\u0000\u9751"+
        "\uFA2A\uFA2B\uFA2C\u999E\u9AD9\u9B72\uFA2D\u9ED1"+
        "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000"+
        "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000"+
        "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000"+
        "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000"+
        "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000"+
        "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000"+
        "\u0000\u0000\u0000\u0000\u0000\u0000\u974D\u0000"+
        "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000"+
        "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000"+
        "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000"+
        "\u0000\u0000\uE3AC\uE3AD\uE3AE\uE3AF\uE3B0\uE3B1"+
        "\uE3B2\uE3B3\uE3B4\uE3B5\uE3B6\uE3B7\uE3B8\uE3B9"+
        "\uE3BA\uE3BB\uE3BC\uE3BD\uE3BE\uE3BF\uE3C0\uE3C1"+
        "\uE3C2\uE3C3\uE3C4\uE3C5\uE3C6\uE3C7\uE3C8\uE3C9"+
        "\uE3CA\uE3CB\uE3CC\uE3CD\uE3CE\uE3CF\uE3D0\uE3D1"+
        "\uE3D2\uE3D3\uE3D4\uE3D5\uE3D6\uE3D7\uE3D8\uE3D9"+
        "\uE3DA\uE3DB\uE3DC\uE3DD\uE3DE\uE3DF\uE3E0\uE3E1"+
        "\uE3E2\uE3E3\uE3E4\uE3E5\uE3E6\uE3E7\uE3E8\uE3E9"+
        "\uE3EA\uE3EB\uE3EC\uE3ED\uE3EE\uE3EF\uE3F0\uE3F1"+
        "\uE3F2\uE3F3\uE3F4\uE3F5\uE3F6\uE3F7\uE3F8\uE3F9"+
        "\uE3FA\uE3FB\uE3FC\uE3FD\uE3FE\uE3FF\uE400\uE401"+
        "\uE402\uE403\uE404\uE405\uE406\uE407\uE408\uE409"+
        "\uE40A\uE40B\uE40C\uE40D\uE40E\uE40F\uE410\uE411"+
        "\uE412\uE413\uE414\uE415\uE416\uE417\uE418\uE419"+
        "\uE41A\uE41B\uE41C\uE41D\uE41E\uE41F\uE420\uE421"+
        "\uE422\uE423\uE424\uE425\uE426\uE427\uE428\uE429"+
        "\uE42A\uE42B\uE42C\uE42D\uE42E\uE42F\uE430\uE431"+
        "\uE432\uE433\uE434\uE435\uE436\uE437\uE438\uE439"+
        "\uE43A\uE43B\uE43C\uE43D\uE43E\uE43F\uE440\uE441"+
        "\uE442\uE443\uE444\uE445\uE446\uE447\uE448\uE449"+
        "\uE44A\uE44B\uE44C\uE44D\uE44E\uE44F\uE450\uE451"+
        "\uE452\uE453\uE454\uE455\uE456\uE457\uE458\uE459"+
        "\uE45A\uE45B\uE45C\uE45D\uE45E\uE45F\uE460\uE461"+
        "\uE462\uE463\uE464\uE465\uE466\uE467\uE468\uE469"+
        "\uE46A\uE46B\uE46C\uE46D\uE46E\uE46F\uE470\uE471"+
        "\uE472\uE473\uE474\uE475\uE476\uE477\uE478\uE479"+
        "\uE47A\uE47B\uE47C\uE47D\uE47E\uE47F\uE480\uE481"+
        "\uE482\uE483\uE484\uE485\uE486\uE487\uE488\uE489"+
        "\uE48A\uE48B\uE48C\uE48D\uE48E\uE48F\uE490\uE491"+
        "\uE492\uE493\uE494\uE495\uE496\uE497\uE498\uE499"+
        "\uE49A\uE49B\uE49C\uE49D\uE49E\uE49F\uE4A0\uE4A1"+
        "\uE4A2\uE4A3\uE4A4\uE4A5\uE4A6\uE4A7\uE4A8\uE4A9"+
        "\uE4AA\uE4AB\uE4AC\uE4AD\uE4AE\uE4AF\uE4B0\uE4B1"+
        "\uE4B2\uE4B3\uE4B4\uE4B5\uE4B6\uE4B7\uE4B8\uE4B9"+
        "\uE4BA\uE4BB\uE4BC\uE4BD\uE4BE\uE4BF\uE4C0\uE4C1"+
        "\uE4C2\uE4C3\uE4C4\uE4C5\uE4C6\uE4C7\uE4C8\uE4C9"+
        "\uE4CA\uE4CB\uE4CC\uE4CD\uE4CE\uE4CF\uE4D0\uE4D1"+
        "\uE4D2\uE4D3\uE4D4\uE4D5\uE4D6\uE4D7\uE4D8\uE4D9"+
        "\uE4DA\uE4DB\uE4DC\uE4DD\uE4DE\uE4DF\uE4E0\uE4E1"+
        "\uE4E2\uE4E3\uE4E4\uE4E5\uE4E6\uE4E7\uE4E8\uE4E9"+
        "\uE4EA\uE4EB\uE4EC\uE4ED\uE4EE\uE4EF\uE4F0\uE4F1"+
        "\uE4F2\uE4F3\uE4F4\uE4F5\uE4F6\uE4F7\uE4F8\uE4F9"+
        "\uE4FA\uE4FB\uE4FC\uE4FD\uE4FE\uE4FF\uE500\uE501"+
        "\uE502\uE503\uE504\uE505\uE506\uE507\uE508\uE509"+
        "\uE50A\uE50B\uE50C\uE50D\uE50E\uE50F\uE510\uE511"+
        "\uE512\uE513\uE514\uE515\uE516\uE517\uE518\uE519"+
        "\uE51A\uE51B\uE51C\uE51D\uE51E\uE51F\uE520\uE521"+
        "\uE522\uE523\uE524\uE525\uE526\uE527\uE528\uE529"+
        "\uE52A\uE52B\uE52C\uE52D\uE52E\uE52F\uE530\uE531"+
        "\uE532\uE533\uE534\uE535\uE536\uE537\uE538\uE539"+
        "\uE53A\uE53B\uE53C\uE53D\uE53E\uE53F\uE540\uE541"+
        "\uE542\uE543\uE544\uE545\uE546\uE547\uE548\uE549"+
        "\uE54A\uE54B\uE54C\uE54D\uE54E\uE54F\uE550\uE551"+
        "\uE552\uE553\uE554\uE555\uE556\uE557\uE558\uE559"+
        "\uE55A\uE55B\uE55C\uE55D\uE55E\uE55F\uE560\uE561"+
        "\uE562\uE563\uE564\uE565\uE566\uE567\uE568\uE569"+
        "\uE56A\uE56B\uE56C\uE56D\uE56E\uE56F\uE570\uE571"+
        "\uE572\uE573\uE574\uE575\uE576\uE577\uE578\uE579"+
        "\uE57A\uE57B\uE57C\uE57D\uE57E\uE57F\uE580\uE581"+
        "\uE582\uE583\uE584\uE585\uE586\uE587\uE588\uE589"+
        "\uE58A\uE58B\uE58C\uE58D\uE58E\uE58F\uE590\uE591"+
        "\uE592\uE593\uE594\uE595\uE596\uE597\uE598\uE599"+
        "\uE59A\uE59B\uE59C\uE59D\uE59E\uE59F\uE5A0\uE5A1"+
        "\uE5A2\uE5A3\uE5A4\uE5A5\uE5A6\uE5A7\uE5A8\uE5A9"+
        "\uE5AA\uE5AB\uE5AC\uE5AD\uE5AE\uE5AF\uE5B0\uE5B1"+
        "\uE5B2\uE5B3\uE5B4\uE5B5\uE5B6\uE5B7\uE5B8\uE5B9"+
        "\uE5BA\uE5BB\uE5BC\uE5BD\uE5BE\uE5BF\uE5C0\uE5C1"+
        "\uE5C2\uE5C3\uE5C4\uE5C5\uE5C6\uE5C7\uE5C8\uE5C9"+
        "\uE5CA\uE5CB\uE5CC\uE5CD\uE5CE\uE5CF\uE5D0\uE5D1"+
        "\uE5D2\uE5D3\uE5D4\uE5D5\uE5D6\uE5D7\uE5D8\uE5D9"+
        "\uE5DA\uE5DB\uE5DC\uE5DD\uE5DE\uE5DF\uE5E0\uE5E1"+
        "\uE5E2\uE5E3\uE5E4\uE5E5\uE5E6\uE5E7\uE5E8\uE5E9"+
        "\uE5EA\uE5EB\uE5EC\uE5ED\uE5EE\uE5EF\uE5F0\uE5F1"+
        "\uE5F2\uE5F3\uE5F4\uE5F5\uE5F6\uE5F7\uE5F8\uE5F9"+
        "\uE5FA\uE5FB\uE5FC\uE5FD\uE5FE\uE5FF\uE600\uE601"+
        "\uE602\uE603\uE604\uE605\uE606\uE607\uE608\uE609"+
        "\uE60A\uE60B\uE60C\uE60D\uE60E\uE60F\uE610\uE611"+
        "\uE612\uE613\uE614\uE615\uE616\uE617\uE618\uE619"+
        "\uE61A\uE61B\uE61C\uE61D\uE61E\uE61F\uE620\uE621"+
        "\uE622\uE623\uE624\uE625\uE626\uE627\uE628\uE629"+
        "\uE62A\uE62B\uE62C\uE62D\uE62E\uE62F\uE630\uE631"+
        "\uE632\uE633\uE634\uE635\uE636\uE637\uE638\uE639"+
        "\uE63A\uE63B\uE63C\uE63D\uE63E\uE63F\uE640\uE641"+
        "\uE642\uE643\uE644\uE645\uE646\uE647\uE648\uE649"+
        "\uE64A\uE64B\uE64C\uE64D\uE64E\uE64F\uE650\uE651"+
        "\uE652\uE653\uE654\uE655\uE656\uE657\uE658\uE659"+
        "\uE65A\uE65B\uE65C\uE65D\uE65E\uE65F\uE660\uE661"+
        "\uE662\uE663\uE664\uE665\uE666\uE667\uE668\uE669"+
        "\uE66A\uE66B\uE66C\uE66D\uE66E\uE66F\uE670\uE671"+
        "\uE672\uE673\uE674\uE675\uE676\uE677\uE678\uE679"+
        "\uE67A\uE67B\uE67C\uE67D\uE67E\uE67F\uE680\uE681"+
        "\uE682\uE683\uE684\uE685\uE686\uE687\uE688\uE689"+
        "\uE68A\uE68B\uE68C\uE68D\uE68E\uE68F\uE690\uE691"+
        "\uE692\uE693\uE694\uE695\uE696\uE697\uE698\uE699"+
        "\uE69A\uE69B\uE69C\uE69D\uE69E\uE69F\uE6A0\uE6A1"+
        "\uE6A2\uE6A3\uE6A4\uE6A5\uE6A6\uE6A7\uE6A8\uE6A9"+
        "\uE6AA\uE6AB\uE6AC\uE6AD\uE6AE\uE6AF\uE6B0\uE6B1"+
        "\uE6B2\uE6B3\uE6B4\uE6B5\uE6B6\uE6B7\uE6B8\uE6B9"+
        "\uE6BA\uE6BB\uE6BC\uE6BD\uE6BE\uE6BF\uE6C0\uE6C1"+
        "\uE6C2\uE6C3\uE6C4\uE6C5\uE6C6\uE6C7\uE6C8\uE6C9"+
        "\uE6CA\uE6CB\uE6CC\uE6CD\uE6CE\uE6CF\uE6D0\uE6D1"+
        "\uE6D2\uE6D3\uE6D4\uE6D5\uE6D6\uE6D7\uE6D8\uE6D9"+
        "\uE6DA\uE6DB\uE6DC\uE6DD\uE6DE\uE6DF\uE6E0\uE6E1"+
        "\uE6E2\uE6E3\uE6E4\uE6E5\uE6E6\uE6E7\uE6E8\uE6E9"+
        "\uE6EA\uE6EB\uE6EC\uE6ED\uE6EE\uE6EF\uE6F0\uE6F1"+
        "\uE6F2\uE6F3\uE6F4\uE6F5\uE6F6\uE6F7\uE6F8\uE6F9"+
        "\uE6FA\uE6FB\uE6FC\uE6FD\uE6FE\uE6FF\uE700\uE701"+
        "\uE702\uE703\uE704\uE705\uE706\uE707\uE708\uE709"+
        "\uE70A\uE70B\uE70C\uE70D\uE70E\uE70F\uE710\uE711"+
        "\uE712\uE713\uE714\uE715\uE716\uE717\uE718\uE719"+
        "\uE71A\uE71B\uE71C\uE71D\uE71E\uE71F\uE720\uE721"+
        "\uE722\uE723\uE724\uE725\uE726\uE727\uE728\uE729"+
        "\uE72A\uE72B\uE72C\uE72D\uE72E\uE72F\uE730\uE731"+
        "\uE732\uE733\uE734\uE735\uE736\uE737\uE738\uE739"+
        "\uE73A\uE73B\uE73C\uE73D\uE73E\uE73F\uE740\uE741"+
        "\uE742\uE743\uE744\uE745\uE746\uE747\uE748\uE749"+
        "\uE74A\uE74B\uE74C\uE74D\uE74E\uE74F\uE750\uE751"+
        "\uE752\uE753\uE754\uE755\uE756\uE757";

    private final static short _index1[] = {
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 0,
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0
    };

    private final static String _index2[] = {
        _innerIndex0
    };

}
