/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the  "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.sun.org.apache.xml.internal.serializer.utils;

import java.util.ListResourceBundle;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * An instance of this class is a ListResourceBundle that
 * has the required getContents() method that returns
 * an array of message-key/message associations.
 * <p>
 * The message keys are defined in {@link MsgKey}. The
 * messages that those keys map to are defined here.
 * <p>
 * The messages in the English version are intended to be
 * translated.
 *
 * This class is not a public API, it is only public because it is
 * used in com.sun.org.apache.xml.internal.serializer.
 *
 * @xsl.usage internal
 */
public class SerializerMessages_ja extends ListResourceBundle {

    /*
     * This file contains error and warning messages related to
     * Serializer Error Handling.
     *
     *  General notes to translators:

     *  1) A stylesheet is a description of how to transform an input XML document
     *     into a resultant XML document (or HTML document or text).  The
     *     stylesheet itself is described in the form of an XML document.

     *
     *  2) An element is a mark-up tag in an XML document; an attribute is a
     *     modifier on the tag.  For example, in <elem attr='val' attr2='val2'>
     *     "elem" is an element name, "attr" and "attr2" are attribute names with
     *     the values "val" and "val2", respectively.
     *
     *  3) A namespace declaration is a special attribute that is used to associate
     *     a prefix with a URI (the namespace).  The meanings of element names and
     *     attribute names that use that prefix are defined with respect to that
     *     namespace.
     *
     *
     */

    /** The lookup table for error messages.   */
    public Object[][] getContents() {
        Object[][] contents = new Object[][] {
            {   MsgKey.BAD_MSGKEY,
                "メッセージ・キー''{0}''は、メッセージ・クラス''{1}''ではありません" },

            {   MsgKey.BAD_MSGFORMAT,
                "メッセージ・クラス''{1}''のメッセージ''{0}''のフォーマットが失敗しました。" },

            {   MsgKey.ER_SERIALIZER_NOT_CONTENTHANDLER,
                "シリアライザ・クラス''{0}''はorg.xml.sax.ContentHandlerを実装しません。" },

            {   MsgKey.ER_RESOURCE_COULD_NOT_FIND,
                    "リソース[ {0} ]は見つかりませんでした。\n {1}" },

            {   MsgKey.ER_RESOURCE_COULD_NOT_LOAD,
                    "リソース[ {0} ]をロードできませんでした: {1} \n {2} \t {3}" },

            {   MsgKey.ER_BUFFER_SIZE_LESSTHAN_ZERO,
                    "バッファ・サイズ<=0" },

            {   MsgKey.ER_INVALID_UTF16_SURROGATE,
                    "無効なUTF-16サロゲートが検出されました: {0}。" },

            {   MsgKey.ER_OIERROR,
                "IOエラー" },

            {   MsgKey.ER_ILLEGAL_ATTRIBUTE_POSITION,
                "子ノードの後または要素が生成される前に属性{0}を追加できません。属性は無視されます。" },

            /*
             * Note to translators:  The stylesheet contained a reference to a
             * namespace prefix that was undefined.  The value of the substitution
             * text is the name of the prefix.
             */
            {   MsgKey.ER_NAMESPACE_PREFIX,
                "接頭辞''{0}''のネームスペースが宣言されていません。" },

            /*
             * Note to translators:  This message is reported if the stylesheet
             * being processed attempted to construct an XML document with an
             * attribute in a place other than on an element.  The substitution text
             * specifies the name of the attribute.
             */
            {   MsgKey.ER_STRAY_ATTRIBUTE,
                "属性''{0}''が要素の外側にあります。" },

            /*
             * Note to translators:  As with the preceding message, a namespace
             * declaration has the form of an attribute and is only permitted to
             * appear on an element.  The substitution text {0} is the namespace
             * prefix and {1} is the URI that was being used in the erroneous
             * namespace declaration.
             */
            {   MsgKey.ER_STRAY_NAMESPACE,
                "ネームスペース宣言''{0}''=''{1}''が要素の外側にあります。" },

            {   MsgKey.ER_COULD_NOT_LOAD_RESOURCE,
                "''{0}''をロードできませんでした(CLASSPATHを確認してください)。現在は単にデフォルトを使用しています" },

            {   MsgKey.ER_ILLEGAL_CHARACTER,
                "{1}の指定された出力エンコーディングで示されない整数値{0}の文字を出力しようとしました。" },

            {   MsgKey.ER_COULD_NOT_LOAD_METHOD_PROPERTY,
                "出力メソッド''{1}''のプロパティ・ファイル''{0}''をロードできませんでした(CLASSPATHを確認してください)" },

            {   MsgKey.ER_INVALID_PORT,
                "無効なポート番号" },

            {   MsgKey.ER_PORT_WHEN_HOST_NULL,
                "ホストがnullの場合はポートを設定できません" },

            {   MsgKey.ER_HOST_ADDRESS_NOT_WELLFORMED,
                "ホストは整形式のアドレスではありません" },

            {   MsgKey.ER_SCHEME_NOT_CONFORMANT,
                "スキームが整合していません。" },

            {   MsgKey.ER_SCHEME_FROM_NULL_STRING,
                "null文字列からはスキームを設定できません" },

            {   MsgKey.ER_PATH_CONTAINS_INVALID_ESCAPE_SEQUENCE,
                "パスに無効なエスケープ・シーケンスが含まれています" },

            {   MsgKey.ER_PATH_INVALID_CHAR,
                "パスに無効な文字が含まれています: {0}" },

            {   MsgKey.ER_FRAG_INVALID_CHAR,
                "フラグメントに無効文字が含まれています" },

            {   MsgKey.ER_FRAG_WHEN_PATH_NULL,
                "パスがnullの場合はフラグメントを設定できません" },

            {   MsgKey.ER_FRAG_FOR_GENERIC_URI,
                "汎用URIのフラグメントのみ設定できます" },

            {   MsgKey.ER_NO_SCHEME_IN_URI,
                "スキームがURIに見つかりません" },

            {   MsgKey.ER_CANNOT_INIT_URI_EMPTY_PARMS,
                "URIは空のパラメータを使用して初期化できません" },

            {   MsgKey.ER_NO_FRAGMENT_STRING_IN_PATH,
                "フラグメントはパスとフラグメントの両方に指定できません" },

            {   MsgKey.ER_NO_QUERY_STRING_IN_PATH,
                "問合せ文字列はパスおよび問合せ文字列内に指定できません" },

            {   MsgKey.ER_NO_PORT_IF_NO_HOST,
                "ホストが指定されていない場合はポートを指定できません" },

            {   MsgKey.ER_NO_USERINFO_IF_NO_HOST,
                "ホストが指定されていない場合はUserinfoを指定できません" },

            {   MsgKey.ER_XML_VERSION_NOT_SUPPORTED,
                "警告: 出力ドキュメントのバージョンは、''{0}''であることがリクエストされています。XMLのこのバージョンはサポートされていません。出力ドキュメントのバージョンは、''1.0''になります。" },

            {   MsgKey.ER_SCHEME_REQUIRED,
                "スキームが必要です。" },

            /*
             * Note to translators:  The words 'Properties' and
             * 'SerializerFactory' in this message are Java class names
             * and should not be translated.
             */
            {   MsgKey.ER_FACTORY_PROPERTY_MISSING,
                "SerializerFactoryに渡されるプロパティ・オブジェクトに、''{0}''プロパティがありません。" },

            {   MsgKey.ER_ENCODING_NOT_SUPPORTED,
                "警告:  エンコーディング''{0}''は、Javaランタイムでサポートされていません。" },

             {MsgKey.ER_FEATURE_NOT_FOUND,
             "パラメータ''{0}''は認識されません。"},

             {MsgKey.ER_FEATURE_NOT_SUPPORTED,
             "パラメータ''{0}''は認識されますが、リクエストした値は設定できません。"},

             {MsgKey.ER_STRING_TOO_LONG,
             "結果の文字列は長すぎるため、DOMStringに収まりません: ''{0}''。"},

             {MsgKey.ER_TYPE_MISMATCH_ERR,
             "このパラメータ名の値タイプは、予想した値タイプと互換性がありません。 "},

             {MsgKey.ER_NO_OUTPUT_SPECIFIED,
             "書き込まれるデータの出力先がnullになっています。"},

             {MsgKey.ER_UNSUPPORTED_ENCODING,
             "サポートされていないエンコーディングが見つかりました。"},

             {MsgKey.ER_UNABLE_TO_SERIALIZE_NODE,
             "ノードをシリアライズできませんでした。"},

             {MsgKey.ER_CDATA_SECTIONS_SPLIT,
             "CDATAセクションに1つ以上の終了マーカー']]>'が含まれています。"},

             {MsgKey.ER_WARNING_WF_NOT_CHECKED,
                 "整形式チェッカのインスタンスを作成できませんでした。整形式パラメータはtrueに設定されていますが、整形式チェックを実行できませんでした。"
             },

             {MsgKey.ER_WF_INVALID_CHARACTER,
                 "ノード''{0}''に無効なXML文字が含まれています。"
             },

             { MsgKey.ER_WF_INVALID_CHARACTER_IN_COMMENT,
                 "コメントに無効なXML文字(Unicode: 0x{0})が見つかりました。"
             },

             { MsgKey.ER_WF_INVALID_CHARACTER_IN_PI,
                 "処理命令データに無効なXML文字(Unicode: 0x{0})が見つかりました。"
             },

             { MsgKey.ER_WF_INVALID_CHARACTER_IN_CDATA,
                 "CDATASectionのコンテンツに無効なXML文字(Unicode: 0x{0})が見つかりました。"
             },

             { MsgKey.ER_WF_INVALID_CHARACTER_IN_TEXT,
                 "ノードの文字データ・コンテンツに無効なXML文字(Unicode: 0x{0})が見つかりました。"
             },

             { MsgKey.ER_WF_INVALID_CHARACTER_IN_NODE_NAME,
                 "''{1}''という名前の{0}ノードに無効なXML文字が見つかりました。"
             },

             { MsgKey.ER_WF_DASH_IN_COMMENT,
                 "コメント内では文字列\"--\"は使用できません。"
             },

             {MsgKey.ER_WF_LT_IN_ATTVAL,
                 "要素タイプ\"{0}\"に関連付けられている属性\"{1}\"の値には、''<''文字を含めることはできません。"
             },

             {MsgKey.ER_WF_REF_TO_UNPARSED_ENT,
                 "未解析エンティティ参照\"&{0};\"は許可されていません。"
             },

             {MsgKey.ER_WF_REF_TO_EXTERNAL_ENT,
                 "外部エンティティ参照\"&{0};\"は、属性値では許可されていません。"
             },

             {MsgKey.ER_NS_PREFIX_CANNOT_BE_BOUND,
                 "接頭辞\"{0}\"はネームスペース\"{1}\"にバインドできません。"
             },

             {MsgKey.ER_NULL_LOCAL_ELEMENT_NAME,
                 "要素\"{0}\"のローカル名がnullです。"
             },

             {MsgKey.ER_NULL_LOCAL_ATTR_NAME,
                 "属性\"{0}\"のローカル名がnullです。"
             },

             { MsgKey.ER_ELEM_UNBOUND_PREFIX_IN_ENTREF,
                 "エンティティ・ノード\"{0}\"の置換テキストには、バインドされていない接頭辞\"{2}\"を持つ要素ノード\"{1}\"が含まれています。"
             },

             { MsgKey.ER_ATTR_UNBOUND_PREFIX_IN_ENTREF,
                 "エンティティ・ノード\"{0}\"の置換テキストには、バインドされていない接頭辞\"{2}\"を持つ属性ノード\"{1}\"が含まれています。"
             },

             { MsgKey.ER_WRITING_INTERNAL_SUBSET,
                 "内部サブセットの書込み中にエラーが発生しました。"
             },

        };

        return contents;
    }
}
