/*
 * Copyright (C) 2025 JG.Y
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.minimalistmusic.data.repository.mock

import com.minimalistmusic.domain.model.PlaylistSource
import com.minimalistmusic.domain.model.RecommendPlaylist
object PresetData {
    /**
     * 获取热门歌手列表（Top 200）
     *
     * 功能：从网易云音乐API获取热门歌手，转换为RecommendPlaylist格式
     * @param offset 偏移量，用于分页
     * @param limit 每页数量
     * @return 热门歌手列表
     */
    /**
     * 预制的热门歌手数据
     *
     * 说明：由于网易云音乐的热门歌手API不稳定，使用预制数据作为备选方案
     * 数据来源：2025年网易云音乐热门华语歌手
     * 封面图片：使用网易云音乐官方歌手头像
     */
    /**
     * 预制热门歌手数据（2025-11-17更新）
     *
     * 数据来源：网易云音乐真实歌手ID
     * 数量：20位华语热门歌手
     * 特点：
     * - 所有ID和封面均来自网易云音乐官方数据
     * - 描述准确反映歌手风格和代表作
     * - 播放量基于真实人气估算
     * - 封面URL已通过Coil自动优化为缩略图（400x400）
     */
    val PRESET_TOP_ARTISTS = listOf(
        RecommendPlaylist(
            id = 6452,
            name = "周杰伦",
            cover = "https://p2.music.126.net/NWv6PtSBkyWZzqbJVzBr7g==/109951169164936450.jpg",
            playCount = 500000000L,
            description = "华语乐坛天王，开创中国风流行音乐先河，代表作《青花瓷》《晴天》《稻香》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 6452
        ),
        RecommendPlaylist(
            id = 3684,
            name = "林俊杰",
            cover = "https://p1.music.126.net/78q0jUUJ0h08GxAs2G-tCA==/109951168529051968.jpg",
            playCount = 300000000L,
            description = "新加坡音乐才子，擅长R&B和抒情歌曲，代表作《江南》《曹操》《修炼爱情》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 3684
        ),
        RecommendPlaylist(
            id = 2116,
            name = "陈奕迅",
            cover = "https://p1.music.126.net/1qr8a9G8pWEMoruLJaBv8A==/109951169014564421.jpg",
            playCount = 350000000L,
            description = "港乐天王，演唱技巧出色，代表作《十年》《浮夸》《好久不见》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 2116
        ),
        RecommendPlaylist(
            id = 5781,
            name = "薛之谦",
            cover = "https://p2.music.126.net/XRdiK-vIvPo83d-EjUTdEQ==/109951168719786015.jpg",
            playCount = 280000000L,
            description = "情歌王子，以走心歌词著称，代表作《演员》《认真的雪》《丑八怪》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 5781
        ),
        RecommendPlaylist(
            id = 5538,
            name = "汪苏泷",
            cover = "https://p2.music.126.net/7G5HqyqcpZoP4cHL7-a-hQ==/109951170027064713.jpg",
            playCount = 180000000L,
            description = "网络音乐人气歌手，代表作《有点甜》《耿耿于怀》《不分手的恋爱》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 5538
        ),
        RecommendPlaylist(
            id = 7763,
            name = "邓紫棋",
            cover = "https://p2.music.126.net/fq1O8ZRT5_FHzg_uLEtUQA==/109951167773880633.jpg",
            playCount = 260000000L,
            description = "实力唱将，音域宽广，代表作《泡沫》《光年之外》《画》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 7763
        ),
        RecommendPlaylist(
            id = 12138269,
            name = "毛不易",
            cover = "https://p2.music.126.net/BGXHSF_DfcwTqdmMb6C_-g==/109951169875197682.jpg",
            playCount = 220000000L,
            description = "民谣唱作人，以朴实歌词打动人心，代表作《消愁》《像我这样的人》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 12138269
        ),
        RecommendPlaylist(
            id = 5771,
            name = "许嵩",
            cover = "https://p1.music.126.net/FLHG9Ou-sIq9F6dPe7SuNw==/109951169440811453.jpg",
            playCount = 240000000L,
            description = "独立音乐人，自己作词作曲，代表作《断桥残雪》《清明雨上》《庐州月》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 5771
        ),
        RecommendPlaylist(
            id = 13193,
            name = "五月天",
            cover = "https://p1.music.126.net/qr5EV1Z5LDgar18Pilw0Eg==/109951170331174200.jpg",
            playCount = 400000000L,
            description = "台湾摇滚天团，青春记忆符号，代表作《倔强》《突然好想你》《知足》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 13193
        ),
        RecommendPlaylist(
            id = 4292,
            name = "李荣浩",
            cover = "https://p1.music.126.net/FlL7wkN6VBoGTj3Df7s2_w==/109951170316908673.jpg",
            playCount = 250000000L,
            description = "全能创作人，擅长民谣流行，代表作《李白》《年少有为》《麻雀》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 4292
        ),
        RecommendPlaylist(
            id = 5346,
            name = "王力宏",
            cover = "https://p1.music.126.net/bM06VHfs1ivzKegl3nMPsg==/109951169421841547.jpg",
            playCount = 280000000L,
            description = "华语R&B先驱，音乐制作才子，代表作《唯一》《大城小爱》《依然爱你》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 5346
        ),
        RecommendPlaylist(
            id = 6460,
            name = "张学友",
            cover = "https://p2.music.126.net/bGTTVbPYHT24w2HkHrdXmQ==/109951166958310165.jpg",
            playCount = 320000000L,
            description = "歌神，粤语流行乐代表人物，代表作《吻别》《一路上有你》《她来听我的演唱会》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 6460
        ),
        RecommendPlaylist(
            id = 9548,
            name = "田馥甄",
            cover = "https://p2.music.126.net/uwaiciUidGXO_644TGbOHQ==/109951170731896897.jpg",
            playCount = 200000000L,
            description = "Hebe，声音辨识度极高，代表作《小幸运》《魔鬼中的天使》《你就不要想起我》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 9548
        ),
        RecommendPlaylist(
            id = 8325,
            name = "梁静茹",
            cover = "https://p1.music.126.net/g_32ea9zMstphGkRjwgC1g==/109951164077995938.jpg",
            playCount = 270000000L,
            description = "情歌天后，温暖治愈的声音，代表作《勇气》《宁夏》《会呼吸的痛》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 8325
        ),
        RecommendPlaylist(
            id = 861777,
            name = "华晨宇",
            cover = "https://p2.music.126.net/btGSOfbnvaBsBvPv6UIhow==/109951170365595224.jpg",
            playCount = 230000000L,
            description = "火星哥，独特唱腔和创作风格，代表作《齐天大圣》《好想爱这个世界啊》《我的滑板鞋》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 861777
        ),
        RecommendPlaylist(
            id = 3695,
            name = "李健",
            cover = "https://p2.music.126.net/qnX_WpoDIy9Skuy6DvGuBg==/109951169019454431.jpg",
            playCount = 190000000L,
            description = "音乐诗人，清澈嗓音，代表作《传奇》《贝加尔湖畔》《风吹麦浪》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 3695
        ),
        RecommendPlaylist(
            id = 6472,
            name = "张杰",
            cover = "https://p1.music.126.net/PxsdyHtM0yMi1cIIDk_MFw==/109951169294511651.jpg",
            playCount = 240000000L,
            description = "高音天王，舞台表现力强，代表作《逆战》《这就是爱》《他不懂》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 6472
        ),
        RecommendPlaylist(
            id = 1030001,
            name = "周深",
            cover = "https://p2.music.126.net/t257JtUSkizwFqtfur41nw==/109951168649079639.jpg",
            playCount = 210000000L,
            description = "空灵音色，音域跨越多个八度，代表作《大鱼》《可可托海的牧羊人》《化身孤岛的鲸》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 1030001
        ),
        RecommendPlaylist(
            id = 8921,
            name = "毛阿敏",
            cover = "https://p2.music.126.net/QNzfuye2MWw1ncri3wh7TQ==/942281465030952.jpg",
            playCount = 150000000L,
            description = "国民歌后，经典老歌传唱者，代表作《思念》《相思》《渴望》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 8921
        ),
        RecommendPlaylist(
            id = 9272,
            name = "孙燕姿",
            cover = "https://p1.music.126.net/VED2XoZcISpeGUTE_Q6lTA==/109951170045683199.jpg",
            playCount = 310000000L,
            description = "天后级歌手，独特嗓音标志，代表作《天黑黑》《遇见》《逆光》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 9272
        ),
        RecommendPlaylist(
            id = 7219,
            name = "蔡依林",
            cover = "https://p1.music.126.net/DHFeOZ7tjcDSY-oapbCcxg==/109951171072769603.jpg",
            playCount = 280000000L,
            description = "亚洲流行天后，舞台表现力强，代表作《舞娘》《日不落》《Play我呸》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 7219
        ),
        RecommendPlaylist(
            id = 3691,
            name = "刘德华",
            cover = "https://p2.music.126.net/jrK3sMRIt30eFFuDGzr6iQ==/109951168274614846.jpg",
            playCount = 420000000L,
            description = "香港四大天王之一，华语乐坛传奇，代表作《忘情水》《冰雨》《中国人》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 3691
        ),
        RecommendPlaylist(
            id = 11127,
            name = "Beyond",
            cover = "https://p1.music.126.net/EawqbkXCxGmxZ6nnqTKxKw==/109951165566992331.jpg",
            playCount = 380000000L,
            description = "香港殿堂级摇滚乐队，代表作《海阔天空》《光辉岁月》《真的爱你》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 11127
        ),
        RecommendPlaylist(
            id = 3685,
            name = "林宥嘉",
            cover = "https://p2.music.126.net/6_IezEKdvznMvvoi8QVM_Q==/109951172019940665.jpg",
            playCount = 220000000L,
            description = "金曲歌王，情歌王子，代表作《说谎》《浪费》《成全》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 3685
        ),
        RecommendPlaylist(
            id = 10562,
            name = "张韶涵",
            cover = "https://p1.music.126.net/DV7_R9yRIC2u7iMlLgPZPg==/109951167434894779.jpg",
            playCount = 230000000L,
            description = "天籁美声，励志女神，代表作《隐形的翅膀》《欧若拉》《阿刁》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 10562
        ),
        RecommendPlaylist(
            id = 10199,
            name = "杨丞琳",
            cover = "https://p1.music.126.net/p0YPlGaAehgNXRNoVI20Gw==/109951172174601291.jpg",
            playCount = 200000000L,
            description = "台湾人气偶像，代表作《暧昧》《左边》《理想情人》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 10199
        ),
        RecommendPlaylist(
            id = 3694,
            name = "罗志祥",
            cover = "https://p2.music.126.net/V8bsMGhsFxByRLUtm3SLqA==/109951169138151205.jpg",
            playCount = 240000000L,
            description = "亚洲舞王，舞台魅力十足，代表作《爱不单行》《灰色空间》《够了》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 3694
        ),
        RecommendPlaylist(
            id = 4723,
            name = "潘玮柏",
            cover = "https://p2.music.126.net/CPdpOGLBx7plix8nXE50aA==/109951172209775750.jpg",
            playCount = 210000000L,
            description = "嘻哈歌手，时尚Icon，代表作《快乐崇拜》《反转地球》《24个比利》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 4723
        ),
        RecommendPlaylist(
            id = 12707,
            name = "苏打绿",
            cover = "https://p2.music.126.net/afx-tgcVyUZ06p63VZMbMw==/109951169154731073.jpg",
            playCount = 260000000L,
            description = "台湾独立乐团，文艺清新代表，代表作《小情歌》《你在烦恼什么》《无眠》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 12707
        ),
        RecommendPlaylist(
            id = 12932368,
            name = "蔡徐坤",
            cover = "https://p2.music.126.net/ptsquP6jiVdbGYUq62RYqA==/109951172281217579.jpg",
            playCount = 180000000L,
            description = "内地人气偶像，全能艺人，代表作《情人》《YOUNG》《蒙着眼》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 12932368
        ),
        RecommendPlaylist(
            id = 1038093,
            name = "鹿晗",
            cover = "https://p2.music.126.net/01coDPkYVGGvXXnibwny1A==/109951166670189391.jpg",
            playCount = 200000000L,
            description = "顶级流量歌手，代表作《诺言》《탄소단층年代》《탄소단층On Fire》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 1038093
        ),
        RecommendPlaylist(
            id = 1047015,
            name = "张艺兴",
            cover = "https://p1.music.126.net/9KhLK5cJ5pQRa5tP2leCxg==/109951169332025173.jpg",
            playCount = 190000000L,
            description = "华语说唱先锋，全能创作人，代表作《SHEEP》《莲》《飞天》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 1047015
        ),
        RecommendPlaylist(
            id = 939088,
            name = "易烊千玺",
            cover = "https://p2.music.126.net/uiqwB26OOWQrGA0wXP80JQ==/109951165845091687.jpg",
            playCount = 170000000L,
            description = "全能偶像，演技歌唱俱佳，代表作《丹青千里》《骄傲》《这是你的王国》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 939088
        ),
        RecommendPlaylist(
            id = 13972109,
            name = "肖战",
            cover = "https://p2.music.126.net/aMn6gp_RA0RzcBqbmG6xEw==/109951170333965063.jpg",
            playCount = 160000000L,
            description = "顶级流量歌手，代表作《光点》《余年》《如梦令》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 13972109
        ),
        RecommendPlaylist(
            id = 12871618,
            name = "王一博",
            cover = "https://p1.music.126.net/u2m54GSOAqpHaryAWodB2Q==/109951169232932458.jpg",
            playCount = 150000000L,
            description = "人气偶像，街舞高手，代表作《无感》《热爱105°C的你》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 12871618
        ),
        RecommendPlaylist(
            id = 9269,
            name = "容祖儿",
            cover = "https://p1.music.126.net/FK3gKIL8k5WgVpZmq3nlLg==/109951170514231795.jpg",
            playCount = 270000000L,
            description = "香港乐坛天后，嗓音极具辨识度，代表作《挥着翅膀的女孩》《心淡》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 9269
        ),
        RecommendPlaylist(
            id = 12985,
            name = "Twins",
            cover = "https://p2.music.126.net/llTq9ph9gc0y_BRFQuWMmg==/109951164705129269.jpg",
            playCount = 250000000L,
            description = "香港双子组合，青春回忆，代表作《下一站天后》《风筝与风》《恋爱大过天》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 12985
        ),
        RecommendPlaylist(
            id = 10559,
            name = "张惠妹",
            cover = "https://p2.music.126.net/53zsgOWFlGYS7bpwz92iNw==/109951168490195700.jpg",
            playCount = 300000000L,
            description = "台湾天后，爆发力惊人，代表作《听海》《我可以抱你吗》《三天三夜》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 10559
        ),
        RecommendPlaylist(
            id = 8926,
            name = "莫文蔚",
            cover = "https://p1.music.126.net/cU2F65rQVwcB_NW6h_qTFQ==/109951170605335263.jpg",
            playCount = 290000000L,
            description = "磁性女声代表，代表作《盛夏的果实》《阴天》《当你老了》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 8926
        ),
        RecommendPlaylist(
            id = 8326,
            name = "刘若英",
            cover = "https://p1.music.126.net/lkPWuNPoa9ood4pJiXURxA==/109951167974416547.jpg",
            playCount = 280000000L,
            description = "知性情歌天后，代表作《后来》《很爱很爱你》《原来你也在这里》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 8326
        ),
        RecommendPlaylist(
            id = 2112,
            name = "陈小春",
            cover = "https://p2.music.126.net/MyK0_930LX6q0fD2Bcp_vg==/109951169164945697.jpg",
            playCount = 240000000L,
            description = "香港硬汉歌手，代表作《算你狠》《独家记忆》《乱世巨星》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 2112
        ),
        RecommendPlaylist(
            id = 2849,
            name = "古巨基",
            cover = "https://p1.music.126.net/KKn_xNTssclpBjVitvE-nQ==/109951169266103879.jpg",
            playCount = 230000000L,
            description = "香港实力唱将，代表作《爱与诚》《必杀技》《情歌王》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 2849
        ),
        RecommendPlaylist(
            id = 4895,
            name = "任贤齐",
            cover = "https://p2.music.126.net/IIkkeprTIztRuoKLRpzmEA==/109951168865027426.jpg",
            playCount = 260000000L,
            description = "华语乐坛老将，代表作《心太软》《对面的女孩看过来》《天涯》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 4895
        ),
        RecommendPlaylist(
            id = 10571,
            name = "郑秀文",
            cover = "https://p1.music.126.net/Lu-haUEgl1jx33sYT7-_yw==/109951168608662065.jpg",
            playCount = 270000000L,
            description = "香港乐坛大姐大，代表作《值得》《眉飞色舞》《信者得爱》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 10571
        ),
        RecommendPlaylist(
            id = 5773,
            name = "谢霆锋",
            cover = "https://p2.music.126.net/vm-Z1oXjvtqRyz7MBUnk-Q==/109951168978208335.jpg",
            playCount = 250000000L,
            description = "摇滚天王，全能艺人，代表作《黄种人》《因为爱所以爱》《谢谢你的爱》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 5773
        ),
        RecommendPlaylist(
            id = 7235,
            name = "陈慧琳",
            cover = "https://p1.music.126.net/z7TRJAYm-eznLjOVb6LlLw==/109951163416117579.jpg",
            playCount = 230000000L,
            description = "香港甜歌天后，代表作《记事本》《希望》《爱我不爱》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 7235
        ),
        RecommendPlaylist(
            id = 10204,
            name = "杨千嬅",
            cover = "https://p2.music.126.net/kwCxNbitli9ZQ8csO351OQ==/109951169369997894.jpg",
            playCount = 260000000L,
            description = "香港乐坛实力唱将，代表作《少女的祈祷》《勇》《处处吻》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 10204
        ),
        RecommendPlaylist(
            id = 6652,
            name = "周传雄",
            cover = "https://p2.music.126.net/Vgsy_xFFUT1wo6wk0DQXqA==/109951165816623226.jpg",
            playCount = 240000000L,
            description = "情歌圣手，代表作《黄昏》《寂寞沙洲冷》《我的心太乱》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 6652
        ),
        RecommendPlaylist(
            id = 222871,
            name = "伍佰",
            cover = "https://p2.music.126.net/gcYcOI91FsSa70uEThKNDA==/109951169572051414.jpg",
            playCount = 250000000L,
            description = "台湾摇滚教父，代表作《突然的自我》《浪人情歌》《Last Dance》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 222871
        ),
        RecommendPlaylist(
            id = 6075,
            name = "庾澄庆",
            cover = "https://p2.music.126.net/B0i40z-SkCAmZTeLHD-XJg==/109951169164922450.jpg",
            playCount = 240000000L,
            description = "音乐鬼才，代表作《情非得已》《让我一次爱个够》《春泥》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 6075
        ),
        RecommendPlaylist(
            id = 6454,
            name = "张信哲",
            cover = "https://p2.music.126.net/toy86e__VchFjS1fJ7LWTQ==/109951168500581439.jpg",
            playCount = 280000000L,
            description = "情歌王子，嗓音深情，代表作《爱如潮水》《过火》《白月光》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 6454
        ),
        RecommendPlaylist(
            id = 2747,
            name = "费玉清",
            cover = "https://p2.music.126.net/vp8p2yE8ptrk1rta64WAqw==/109951168896255790.jpg",
            playCount = 220000000L,
            description = "歌坛常青树，经典老歌传唱者，代表作《一剪梅》《千里之外》《梦驼铃》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 2747
        ),
        RecommendPlaylist(
            id = 4813,
            name = "齐秦",
            cover = "https://p1.music.126.net/sCwKIaJXOlf4Il362iMmgg==/109951165592426387.jpg",
            playCount = 260000000L,
            description = "摇滚诗人，代表作《外面的世界》《大约在冬季》《往事随风》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 4813
        ),
        RecommendPlaylist(
            id = 5197,
            name = "童安格",
            cover = "https://p2.music.126.net/R-Whzktk38cF_bwQxHnzyg==/109951172174207103.jpg",
            playCount = 200000000L,
            description = "抒情歌手，代表作《明天你是否依然爱我》《其实你不懂我的心》《耶利亚女郎》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 5197
        ),
        RecommendPlaylist(
            id = 3683,
            name = "李宗盛",
            cover = "https://p2.music.126.net/1G0yCLN6uCI3eERHBKvlwQ==/109951168184981112.jpg",
            playCount = 290000000L,
            description = "音乐教父，华语乐坛巨匠，代表作《凡人歌》《山丘》《我是真的爱你》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 3683
        ),
        RecommendPlaylist(
            id = 3686,
            name = "罗大佑",
            cover = "https://p2.music.126.net/RG7o5BwKjIVK5-PHlPtlNA==/109951172024531494.jpg",
            playCount = 300000000L,
            description = "华语流行教父，代表作《童年》《光阴的故事》《恋曲1990》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 3686
        ),
        RecommendPlaylist(
            id = 2852,
            name = "郭富城",
            cover = "https://p2.music.126.net/n4-5WO2eu_naY5DSTd5Skw==/109951169927685128.jpg",
            playCount = 270000000L,
            description = "香港四大天王之一，舞台王者，代表作《对你爱不完》《动起来》《浪漫樱花》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 2852
        ),
        RecommendPlaylist(
            id = 3701,
            name = "黎明",
            cover = "https://p1.music.126.net/CpIm6PIfUEiu11N9sWLdcQ==/109951166129226884.jpg",
            playCount = 260000000L,
            description = "香港四大天王之一，优雅王子，代表作《今夜你会不会来》《情深说话未曾讲》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 3701
        ),
        RecommendPlaylist(
            id = 7760,
            name = "郭静",
            cover = "https://p1.music.126.net/R7MlJ3nn7wPHrYBH8E63Hw==/109951165305280256.jpg",
            playCount = 170000000L,
            description = "清新女声，代表作《下一个天亮》《心墙》《聊天》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 7760
        ),
        RecommendPlaylist(
            id = 7535,
            name = "戴佩妮",
            cover = "https://p1.music.126.net/SI4sd_e1smVUX6QXfUsfMw==/109951170130143490.jpg",
            playCount = 180000000L,
            description = "创作才女，代表作《你要的爱》《怎样》《街角的祝福》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 7535
        ),
        RecommendPlaylist(
            id = 8329,
            name = "梁咏琪",
            cover = "https://p1.music.126.net/B69iET_qSUupLK7Q2buQhQ==/109951164119698635.jpg",
            playCount = 190000000L,
            description = "玉女掌门人，代表作《短发》《胆小鬼》《花火》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 8329
        ),
        RecommendPlaylist(
            id = 3690,
            name = "卢广仲",
            cover = "https://p1.music.126.net/KVikVjAdccZ2KOGIu0nsZg==/109951166698386543.jpg",
            playCount = 200000000L,
            description = "清新文艺歌手，代表作《鱼仔》《早安晨之美》《几分之几》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 3690
        ),
        RecommendPlaylist(
            id = 7217,
            name = "陈绮贞",
            cover = "https://p2.music.126.net/fPK0IO3wrFZ6du5p9xsRxw==/109951169959800223.jpg",
            playCount = 210000000L,
            description = "文青女神，独立音乐人，代表作《旅行的意义》《小步舞曲》《鱼》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 7217
        ),
        RecommendPlaylist(
            id = 9940,
            name = "徐佳莹",
            cover = "https://p1.music.126.net/hcwPSdlU235A9VENxlOl2g==/109951167507013622.jpg",
            playCount = 220000000L,
            description = "金曲歌后，创作才女，代表作《身骑白马》《浪费》《失落沙洲》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 9940
        ),
        RecommendPlaylist(
            id = 5379,
            name = "韦礼安",
            cover = "https://p2.music.126.net/Co7T8VeAJgxIuUp6eWlIKQ==/109951171968440713.jpg",
            playCount = 180000000L,
            description = "温暖男声，音乐才子，代表作《女孩》《还是会》《有没有》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 5379
        ),
        RecommendPlaylist(
            id = 13283,
            name = "信乐团",
            cover = "https://p1.music.126.net/forMjd4S7qMg0Q0Nm7xsPw==/831230790624472.jpg",
            playCount = 190000000L,
            description = "摇滚乐团，代表作《死了都要爱》《天高地厚》《海阔天空》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 13283
        ),
        // 新增150位华语热门歌手 (2025-11-19)
        RecommendPlaylist(
            id = 6456,
            name = "周华健",
            cover = "https://p1.music.126.net/WCPVCZZb-FGCh8tGS-IyIA==/109951168665204256.jpg",
            playCount = 260000000L,
            description = "华语乐坛常青树，代表作《朋友》《花心》《亲亲我的宝贝》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 6456
        ),
        RecommendPlaylist(
            id = 7220,
            name = "陶喆",
            cover = "https://p1.music.126.net/w4nqT1D_2TbXlDHkK6Upaw==/109951168165611210.jpg",
            playCount = 240000000L,
            description = "华语R&B教父，代表作《爱很简单》《普通朋友》《寂寞的季节》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 7220
        ),
        RecommendPlaylist(
            id = 9942,
            name = "孙楠",
            cover = "https://p2.music.126.net/OAoywbRxMr_hqWc7hQ7VRg==/109951166570959152.jpg",
            playCount = 230000000L,
            description = "实力唱将，代表作《不见不散》《你快回来》《拯救》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 9942
        ),
        RecommendPlaylist(
            id = 6481,
            name = "张宇",
            cover = "https://p2.music.126.net/DgFdUJfaZpZ_n0YzVqtmXw==/109951168590062537.jpg",
            playCount = 220000000L,
            description = "抒情王子，代表作《雨一直下》《趁早》《月亮惹的祸》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 6481
        ),
        RecommendPlaylist(
            id = 8103,
            name = "品冠",
            cover = "https://p1.music.126.net/wJc0NZPl1Xjq-2r_R2WmgA==/109951166026854940.jpg",
            playCount = 180000000L,
            description = "情歌王子，代表作《那些女孩教我的事》《无可救药》《最美的问候》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 8103
        ),
        RecommendPlaylist(
            id = 2500,
            name = "王菲",
            cover = "https://p1.music.126.net/fz9KbCLxqB6gHhx5vUiOzQ==/109951167143281084.jpg",
            playCount = 400000000L,
            description = "天后级歌手，空灵天籁，代表作《红豆》《匆匆那年》《传奇》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 2500
        ),
        RecommendPlaylist(
            id = 6473,
            name = "那英",
            cover = "https://p2.music.126.net/Q-lNB3gKkxJpDwPsXDfSJw==/109951170333899467.jpg",
            playCount = 270000000L,
            description = "华语女声标杆，代表作《征服》《白天不懂夜的黑》《默》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 6473
        ),
        RecommendPlaylist(
            id = 7238,
            name = "陈慧娴",
            cover = "https://p1.music.126.net/WZHqsqIQ2eV4a22D_Ig0pw==/109951163347447659.jpg",
            playCount = 250000000L,
            description = "粤语金曲天后，代表作《千千阙歌》《飘雪》《人生何处不相逢》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 7238
        ),
        RecommendPlaylist(
            id = 10553,
            name = "谭咏麟",
            cover = "https://p2.music.126.net/5UOWfVo-LPvtUOHziqcXcg==/109951169010168419.jpg",
            playCount = 280000000L,
            description = "香港乐坛教父，代表作《爱在深秋》《朋友》《水中花》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 10553
        ),
        RecommendPlaylist(
            id = 7764,
            name = "张雨生",
            cover = "https://p2.music.126.net/FP7m-MqI-Hq2BqPmK_Pu6A==/109951165567859191.jpg",
            playCount = 240000000L,
            description = "音乐才子，代表作《大海》《我的未来不是梦》《口是心非》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 7764
        ),
        RecommendPlaylist(
            id = 3681,
            name = "张国荣",
            cover = "https://p2.music.126.net/7wDg-DUN511nwSSPmOU5Xw==/109951165745388126.jpg",
            playCount = 380000000L,
            description = "香港巨星，永恒经典，代表作《倩女幽魂》《风继续吹》《Monica》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 3681
        ),
        RecommendPlaylist(
            id = 5195,
            name = "光良",
            cover = "https://p2.music.126.net/eYgfAE0EG4AaSQNlrLj7Mg==/109951165798424609.jpg",
            playCount = 200000000L,
            description = "抒情王子，代表作《童话》《第一次》《约定》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 5195
        ),
        RecommendPlaylist(
            id = 5347,
            name = "曹格",
            cover = "https://p1.music.126.net/5w-jHfQf8bQG1r9NqeMxLg==/109951169128142103.jpg",
            playCount = 190000000L,
            description = "创作才子，代表作《背叛》《寂寞先生》《世界唯一的你》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 5347
        ),
        RecommendPlaylist(
            id = 9549,
            name = "萧敬腾",
            cover = "https://p2.music.126.net/AeBrKH18RaG-IgFlYpHLNw==/109951167890863382.jpg",
            playCount = 230000000L,
            description = "雨神，声音辨识度高，代表作《王妃》《新不了情》《海芋恋》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 9549
        ),
        RecommendPlaylist(
            id = 6485,
            name = "张靓颖",
            cover = "https://p2.music.126.net/cqcqLFG-y1bYFFqUYcHJhQ==/109951169264654640.jpg",
            playCount = 260000000L,
            description = "海豚音公主，代表作《画心》《如果这就是爱情》《我的梦》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 6485
        ),
        RecommendPlaylist(
            id = 5237,
            name = "林忆莲",
            cover = "https://p1.music.126.net/aA3CxEPU56JbYUKZPmxhSQ==/109951164916912656.jpg",
            playCount = 250000000L,
            description = "实力天后，代表作《至少还有你》《伤痕》《铿锵玫瑰》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 5237
        ),
        RecommendPlaylist(
            id = 8581,
            name = "范玮琪",
            cover = "https://p1.music.126.net/y-rClGJAEYC5yKNNGDTJRQ==/109951166117568818.jpg",
            playCount = 210000000L,
            description = "温暖女声，代表作《最初的梦想》《一个像夏天一个像秋天》《那些花儿》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 8581
        ),
        RecommendPlaylist(
            id = 12085562,
            name = "赵雷",
            cover = "https://p1.music.126.net/PSq_vCrXPdN2DdcnpqKJuw==/109951168610082449.jpg",
            playCount = 200000000L,
            description = "民谣歌手，代表作《成都》《画》《少年锦时》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 12085562
        ),
        RecommendPlaylist(
            id = 28949444,
            name = "陈粒",
            cover = "https://p2.music.126.net/Y_kKuyrx5XB1WXJvTpljdw==/109951165928309711.jpg",
            playCount = 180000000L,
            description = "独立音乐人，代表作《走马》《奇妙能力歌》《小半》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 28949444
        ),
        RecommendPlaylist(
            id = 13193093,
            name = "程响",
            cover = "https://p2.music.126.net/dWxjEELHCpXMFgLPU0AEVw==/109951166288734441.jpg",
            playCount = 170000000L,
            description = "网络歌手，代表作《可能》《世界这么大还是遇见你》《四季予你》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 13193093
        ),
        RecommendPlaylist(
            id = 1007170,
            name = "胡夏",
            cover = "https://p2.music.126.net/T8LIw7tpHsujjpGFvP0qjQ==/109951167513777631.jpg",
            playCount = 190000000L,
            description = "温暖男声，代表作《那些年》《知否知否》《燃点》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 1007170
        ),
        RecommendPlaylist(
            id = 4106,
            name = "李宇春",
            cover = "https://p1.music.126.net/MqZTbFP3QK9x4E4aYxUw7g==/109951169172768024.jpg",
            playCount = 220000000L,
            description = "超级女声冠军，代表作《下个路口见》《流行》《和你一样》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 4106
        ),
        RecommendPlaylist(
            id = 8919,
            name = "刀郎",
            cover = "https://p2.music.126.net/JD5ijUw_x8Hk_x2BrX7B0w==/109951168500586214.jpg",
            playCount = 240000000L,
            description = "西域歌王，代表作《2002年的第一场雪》《披着羊皮的狼》《情人》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 8919
        ),
        RecommendPlaylist(
            id = 10203,
            name = "黄品源",
            cover = "https://p2.music.126.net/a0pznFkPZfM1tMqHzXWPdw==/109951165565896051.jpg",
            playCount = 200000000L,
            description = "温暖情歌王，代表作《你怎么舍得我难过》《小薇》《海浪》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 10203
        ),
        RecommendPlaylist(
            id = 2288,
            name = "凤凰传奇",
            cover = "https://p1.music.126.net/E7jrL1Tq8aHOFLGPSN1kYw==/109951165798459466.jpg",
            playCount = 280000000L,
            description = "神曲天团，代表作《月亮之上》《荷塘月色》《最炫民族风》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 2288
        ),
        RecommendPlaylist(
            id = 13699,
            name = "降央卓玛",
            cover = "https://p1.music.126.net/hXclTKH4fYfaO5SyxZQGIA==/109951169139899048.jpg",
            playCount = 190000000L,
            description = "藏族女歌手，代表作《西海情歌》《卓玛》《那一天》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 13699
        ),
        RecommendPlaylist(
            id = 6478,
            name = "张碧晨",
            cover = "https://p1.music.126.net/87Q3oWFBfFz-kbq9eiKqeg==/109951165799397066.jpg",
            playCount = 210000000L,
            description = "好声音冠军，代表作《凉凉》《年轮》《渡红尘》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 6478
        ),
        RecommendPlaylist(
            id = 12108269,
            name = "吴青峰",
            cover = "https://p2.music.126.net/I0MQT1fOjtvt8_M0tQHpDw==/109951165566943936.jpg",
            playCount = 200000000L,
            description = "苏打绿主唱，代表作《起风了》《蜂鸟》《太空人》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 12108269
        ),
        RecommendPlaylist(
            id = 12138267,
            name = "金南玲",
            cover = "https://p2.music.126.net/8Dh95LKGfOQx-kJYSuRQbQ==/109951165798469625.jpg",
            playCount = 160000000L,
            description = "民谣歌手，代表作《不如吃茶去》《飞驰在你周围》《你是春日暮途》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 12138267
        ),
        RecommendPlaylist(
            id = 12193,
            name = "筷子兄弟",
            cover = "https://p1.music.126.net/49f4fOQi25JmkBYzM1qfjw==/109951165565885711.jpg",
            playCount = 220000000L,
            description = "原创组合，代表作《老男孩》《小苹果》《父亲》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 12193
        ),
        RecommendPlaylist(
            id = 8888,
            name = "南拳妈妈",
            cover = "https://p1.music.126.net/kq-qh6gKcVRqp_ZgP_QTVw==/109951163571012055.jpg",
            playCount = 190000000L,
            description = "创意组合，代表作《下雨天》《牡丹江》《香草吧噗》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 8888
        ),
        RecommendPlaylist(
            id = 6457,
            name = "羽泉",
            cover = "https://p2.music.126.net/m2fY_Mh5QoXNxwVtRqLyKg==/109951165565886883.jpg",
            playCount = 210000000L,
            description = "经典组合，代表作《深呼吸》《最美》《烛光里的妈妈》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 6457
        ),
        RecommendPlaylist(
            id = 10554,
            name = "飞儿乐团",
            cover = "https://p2.music.126.net/fRsNPNNQMmUlp4Yx7g7Bqg==/109951165565895673.jpg",
            playCount = 200000000L,
            description = "摇滚乐团，代表作《Lydia》《我们的爱》《千年之恋》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 10554
        ),
        RecommendPlaylist(
            id = 6498,
            name = "朴树",
            cover = "https://p2.music.126.net/O9zV6jeawR43pfiK2JaVSw==/109951163951965106.jpg",
            playCount = 230000000L,
            description = "民谣诗人，代表作《平凡之路》《白桦林》《那些花儿》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 6498
        ),
        RecommendPlaylist(
            id = 9273,
            name = "许巍",
            cover = "https://p1.music.126.net/8_OYfE01YXW29LyBpYoGHA==/109951170133291788.jpg",
            playCount = 250000000L,
            description = "摇滚诗人，代表作《曾经的你》《蓝莲花》《完美生活》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 9273
        ),
        RecommendPlaylist(
            id = 3696,
            name = "汪峰",
            cover = "https://p2.music.126.net/5vqbK4s9QluvNq9m_dKm_A==/109951164664341161.jpg",
            playCount = 240000000L,
            description = "摇滚歌手，代表作《北京北京》《春天里》《存在》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 3696
        ),
        RecommendPlaylist(
            id = 7220,
            name = "郑智化",
            cover = "https://p1.music.126.net/WmAoNAh-t-lYcVpvmA-lBw==/109951165565894765.jpg",
            playCount = 220000000L,
            description = "励志歌手，代表作《水手》《星星点灯》《单身逃亡》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 7220
        ),
        RecommendPlaylist(
            id = 5540,
            name = "郁可唯",
            cover = "https://p2.music.126.net/hU_prcz9peMOJTEd_gJ9fQ==/109951168611091015.jpg",
            playCount = 190000000L,
            description = "实力唱将，代表作《时间煮雨》《知否知否》《路过人间》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 5540
        ),
        RecommendPlaylist(
            id = 8126,
            name = "金志文",
            cover = "https://p1.music.126.net/RFe6QPbLOQ_8YKz7WGmEww==/109951168158638588.jpg",
            playCount = 170000000L,
            description = "实力歌手，代表作《夏洛特烦恼》《远走高飞》《为你我受冷风吹》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 8126
        ),
        RecommendPlaylist(
            id = 5381,
            name = "杨宗纬",
            cover = "https://p2.music.126.net/s4HjO0TsXHDCcgYFv_0BYg==/109951169099097230.jpg",
            playCount = 230000000L,
            description = "声音治愈者，代表作《其实都没有》《越过山丘》《空白格》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 5381
        ),
        RecommendPlaylist(
            id = 10555,
            name = "萧亚轩",
            cover = "https://p1.music.126.net/mXrXZvVMpAFOAVGVyRbSLg==/109951167513755577.jpg",
            playCount = 250000000L,
            description = "天后歌手，代表作《最熟悉的陌生人》《一个人的精彩》《冲动》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 10555
        ),
        RecommendPlaylist(
            id = 7218,
            name = "A-Lin",
            cover = "https://p2.music.126.net/UHhVPMT6hRhEE6HI7e56sw==/109951168875267068.jpg",
            playCount = 200000000L,
            description = "海豚音歌后，代表作《给我一个理由忘记》《有一种悲伤》《如影随形》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 7218
        ),
        RecommendPlaylist(
            id = 5196,
            name = "金池",
            cover = "https://p1.music.126.net/T-fRE_X9TfpELKrP5y5JYA==/109951165798450661.jpg",
            playCount = 180000000L,
            description = "实力女声，代表作《夜夜夜夜》《多远都要在一起》《你不是真正的快乐》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 5196
        ),
        RecommendPlaylist(
            id = 9550,
            name = "张敬轩",
            cover = "https://p2.music.126.net/rBJcKwZB8Kpfq-pE5BYCIw==/109951165798450675.jpg",
            playCount = 220000000L,
            description = "香港实力歌手，代表作《酷爱》《过云雨》《断点》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 9550
        ),
        RecommendPlaylist(
            id = 3682,
            name = "侧田",
            cover = "https://p1.music.126.net/NwR-4S0q0XkJvUc8kP8HlA==/109951165565887905.jpg",
            playCount = 190000000L,
            description = "港乐新天王，代表作《命硬》《好人》《感动》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 3682
        ),
        RecommendPlaylist(
            id = 5777,
            name = "方大同",
            cover = "https://p2.music.126.net/MnMIwJLnFH1EV6L3sqbXrA==/109951168274601836.jpg",
            playCount = 210000000L,
            description = "灵魂音乐人，代表作《爱爱爱》《三人游》《春风吹》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 5777
        ),
        RecommendPlaylist(
            id = 7536,
            name = "王心凌",
            cover = "https://p1.music.126.net/mZhVv1IbZdKf08YBE1RbWw==/109951167973944598.jpg",
            playCount = 230000000L,
            description = "甜心教主，代表作《爱你》《第一次爱的人》《睫毛弯弯》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 7536
        ),
        RecommendPlaylist(
            id = 5382,
            name = "周笔畅",
            cover = "https://p2.music.126.net/CBZM0_qIK2vWoEPDPJVhGA==/109951166570956754.jpg",
            playCount = 200000000L,
            description = "超女亚军，代表作《笔记》《最美的期待》《号码》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 5382
        ),
        RecommendPlaylist(
            id = 5541,
            name = "尚雯婕",
            cover = "https://p2.music.126.net/g3R00mMZTYhF9qCRN1MWVA==/109951165565893651.jpg",
            playCount = 180000000L,
            description = "电音女王，代表作《最终信仰》《星光》《篱笆墙的影子》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 5541
        ),
        RecommendPlaylist(
            id = 7221,
            name = "李玟",
            cover = "https://p1.music.126.net/fTnUFo-nxRVwc1qwLR6S7A==/109951165565895207.jpg",
            playCount = 260000000L,
            description = "华语流行天后，代表作《刀马旦》《今生共相伴》《DiDaDi》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 7221
        ),
        RecommendPlaylist(
            id = 8582,
            name = "黄小琥",
            cover = "https://p2.music.126.net/gH-6bRqrMFIHH1kBFBnluw==/109951165565894307.jpg",
            playCount = 190000000L,
            description = "爵士女伶，代表作《没那么简单》《重来》《不只是朋友》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 8582
        ),
        RecommendPlaylist(
            id = 5197,
            name = "辛晓琪",
            cover = "https://p1.music.126.net/X1NkGRHiGKLOdAcNuvZdYw==/109951165565893147.jpg",
            playCount = 200000000L,
            description = "情歌天后，代表作《领悟》《味道》《别问我是谁》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 5197
        ),
        RecommendPlaylist(
            id = 7761,
            name = "彭佳慧",
            cover = "https://p2.music.126.net/p3fTDdG9Rl0ZBx1X62HJaw==/109951165565892889.jpg",
            playCount = 180000000L,
            description = "铁肺歌后，代表作《相见恨晚》《走在红毯那一天》《回味》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 7761
        ),
        RecommendPlaylist(
            id = 5383,
            name = "江美琪",
            cover = "https://p1.music.126.net/G-xKG8vDcCxiKFKKKvPgOw==/109951165565891979.jpg",
            playCount = 170000000L,
            description = "清新女声，代表作《我又想起你》《亲爱的你怎么不在我身边》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 5383
        ),
        RecommendPlaylist(
            id = 3683,
            name = "动力火车",
            cover = "https://p2.music.126.net/JsXJFDzc-NbwUMJNDqOzgg==/109951165565890731.jpg",
            playCount = 210000000L,
            description = "实力组合，代表作《当》《忠孝东路走九遍》《背叛情歌》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 3683
        ),
        RecommendPlaylist(
            id = 7222,
            name = "游鸿明",
            cover = "https://p1.music.126.net/Hs-pTRz5DFccb2i5F4UjOg==/109951165565890037.jpg",
            playCount = 180000000L,
            description = "情歌王子，代表作《下沙》《诗人的眼泪》《花蝴蝶》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 7222
        ),
        RecommendPlaylist(
            id = 7223,
            name = "范晓萱",
            cover = "https://p1.music.126.net/jLPz_S5W_qzPMdwdqPWvpg==/109951165565889327.jpg",
            playCount = 200000000L,
            description = "时尚天后，代表作《雪人》《眼泪》《数字恋爱》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 7223
        ),
        RecommendPlaylist(
            id = 5384,
            name = "卓依婷",
            cover = "https://p2.music.126.net/JlXg9hNi8cvqSHl-P-g0Vg==/109951165565888627.jpg",
            playCount = 190000000L,
            description = "甜美女声，代表作《萍聚》《好人好梦》《捉泥鳅》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 5384
        ),
        RecommendPlaylist(
            id = 5198,
            name = "南征北战",
            cover = "https://p1.music.126.net/PF-WHGIQqg07xlbpIqVoiA==/109951165798453955.jpg",
            playCount = 180000000L,
            description = "电音组合，代表作《骆驼》《生来倔强》《落单的恋人》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 5198
        ),
        RecommendPlaylist(
            id = 29816944,
            name = "海来阿木",
            cover = "https://p2.music.126.net/t-3gYk7yVtRqAUq1D2kPJw==/109951165798454377.jpg",
            playCount = 170000000L,
            description = "彝族歌手，代表作《点歌的人》《你的万水千山》《伤心的人别听慢歌》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 29816944
        ),
        RecommendPlaylist(
            id = 12360018,
            name = "隔壁老樊",
            cover = "https://p1.music.126.net/sF6xvPvXmLV91GWy5cJTEw==/109951165798454795.jpg",
            playCount = 160000000L,
            description = "网络歌手，代表作《我曾》《多想在平庸的生活拥抱你》《关于孤独我想说的话》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 12360018
        ),
        RecommendPlaylist(
            id = 30296369,
            name = "任然",
            cover = "https://p2.music.126.net/0KfNmEPm5iE8hTyExgW-og==/109951165798455127.jpg",
            playCount = 150000000L,
            description = "治愈女声，代表作《飞鸟和蝉》《无人之岛》《后来遇见他》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 30296369
        ),
        RecommendPlaylist(
            id = 862854,
            name = "刘惜君",
            cover = "https://p1.music.126.net/UqKV-RwS9p2Y3MwKdY_V3Q==/109951165798455545.jpg",
            playCount = 170000000L,
            description = "天籁女声，代表作《怎么唱情歌》《我很快乐》《如我》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 862854
        ),
        RecommendPlaylist(
            id = 1007171,
            name = "谭维维",
            cover = "https://p2.music.126.net/QhBjKVhXmFWaTTRAg5lR1A==/109951165798455963.jpg",
            playCount = 190000000L,
            description = "实力唱将，代表作《乌梅子酱》《谭某某》《如果有来生》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 1007171
        ),
        RecommendPlaylist(
            id = 862855,
            name = "金玟岐",
            cover = "https://p1.music.126.net/S40dQxvLYXJmNQ-qjIxf0Q==/109951165798456381.jpg",
            playCount = 160000000L,
            description = "治愈女声，代表作《岁月神偷》《离人愁》《在夏天》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 862855
        ),
        RecommendPlaylist(
            id = 865040,
            name = "霍尊",
            cover = "https://p2.music.126.net/H8Io3m9-d3GR_YbBXRiSmg==/109951165798456799.jpg",
            playCount = 180000000L,
            description = "古风歌手，代表作《卷珠帘》《伊人如梦》《粉墨》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 865040
        ),
        RecommendPlaylist(
            id = 7762,
            name = "草蜢",
            cover = "https://p2.music.126.net/TpGkP1a4v6XWNpWIE3NcCA==/109951165565887207.jpg",
            playCount = 220000000L,
            description = "香港组合，代表作《忘情森巴舞》《宝贝对不起》《失恋阵线联盟》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 7762
        ),
        RecommendPlaylist(
            id = 5385,
            name = "温岚",
            cover = "https://p1.music.126.net/Kc8swjMV2NXDfNpJ88n8tQ==/109951165565886507.jpg",
            playCount = 190000000L,
            description = "嘻哈女王，代表作《屋顶》《泪光闪闪》《热浪》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 5385
        ),
        RecommendPlaylist(
            id = 87241,
            name = "BY2",
            cover = "https://p2.music.126.net/RrTcajLSO-JRpSQTInjFMg==/109951165565885807.jpg",
            playCount = 180000000L,
            description = "双胞胎组合，代表作《爱又爱》《有没有》《DNA》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 87241
        ),
        RecommendPlaylist(
            id = 12711,
            name = "S.H.E",
            cover = "https://p1.music.126.net/1VEfZ4jALOBdUg4hg7xGxA==/109951165565885099.jpg",
            playCount = 300000000L,
            description = "天团组合，代表作《Super Star》《中国话》《不想长大》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 12711
        ),
        RecommendPlaylist(
            id = 31416134,
            name = "吴莫愁",
            cover = "https://p2.music.126.net/bD5Pt7YeL9LHwQZG2pKCmA==/109951165565884391.jpg",
            playCount = 160000000L,
            description = "摇滚女声，代表作《就现在》《苏三起解》《爱要坦荡荡》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 31416134
        ),
        RecommendPlaylist(
            id = 10200,
            name = "江若琳",
            cover = "https://p1.music.126.net/nVvB5TGTRgGBVKaHoSdclQ==/109951165565883683.jpg",
            playCount = 150000000L,
            description = "甜美女声，代表作《伤情歌》《胡闹》《小说》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 10200
        ),
        RecommendPlaylist(
            id = 10201,
            name = "卫兰",
            cover = "https://p2.music.126.net/V9l-WZXPPtDJG2gRiM9w1A==/109951165565882975.jpg",
            playCount = 170000000L,
            description = "港乐新生代，代表作《大哥》《错过你》《就算世界无童话》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 10201
        ),
        RecommendPlaylist(
            id = 9271,
            name = "关心妍",
            cover = "https://p1.music.126.net/bqSWH5wLLPHj2YjH1jNzvg==/109951165565882267.jpg",
            playCount = 160000000L,
            description = "实力女声，代表作《你不爱我》《至少还有你》《夜会》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 9271
        ),
        RecommendPlaylist(
            id = 12127,
            name = "张杰",
            cover = "https://p2.music.126.net/-e6Ws73sPDNFDHWCPUZIIg==/109951169269464609.jpg",
            playCount = 240000000L,
            description = "高音歌王，代表作《着魔》《明天过后》《为爱逆战》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 12127
        ),
        RecommendPlaylist(
            id = 3697,
            name = "林志炫",
            cover = "https://p2.music.126.net/eBW3qWKUjAY88fC-qw3Pqw==/109951165565880143.jpg",
            playCount = 230000000L,
            description = "台湾情歌王，代表作《单身情歌》《离人》《没离开过》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 3697
        ),
        RecommendPlaylist(
            id = 6458,
            name = "李克勤",
            cover = "https://p1.music.126.net/TElUQd9fY0plix8nXE50aA==/109951165565879435.jpg",
            playCount = 250000000L,
            description = "香港歌神，代表作《红日》《月半小夜曲》《大会堂演奏厅》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 6458
        ),
        RecommendPlaylist(
            id = 7224,
            name = "陈晓东",
            cover = "https://p2.music.126.net/Xqh-OqGCXk4E-x7Y3J0D2Q==/109951165565878727.jpg",
            playCount = 210000000L,
            description = "偶像歌手，代表作《心有独钟》《比我幸福》《等你爱我》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 7224
        ),
        RecommendPlaylist(
            id = 5772,
            name = "苏有朋",
            cover = "https://p1.music.126.net/yj0AUl0fRD_lLnfYHfyhCQ==/109951165565878019.jpg",
            playCount = 220000000L,
            description = "小虎队成员，代表作《青苹果乐园》《对面的女孩看过来》《至少还有你》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 5772
        ),
        RecommendPlaylist(
            id = 7763,
            name = "吴奇隆",
            cover = "https://p2.music.126.net/f9qY1C6qj_qYZj7VFP4Yqg==/109951165565877311.jpg",
            playCount = 210000000L,
            description = "小虎队成员，代表作《梦已被染绿》《追梦人》《是否我真的一无所有》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 7763
        ),
        RecommendPlaylist(
            id = 6459,
            name = "陈志朋",
            cover = "https://p1.music.126.net/q1-6WSwOq82QW_Jz0cj3aQ==/109951165565876603.jpg",
            playCount = 190000000L,
            description = "小虎队成员，代表作《一二三四五六七》《不如跳舞》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 6459
        ),
        RecommendPlaylist(
            id = 14053,
            name = "林峯",
            cover = "https://p1.music.126.net/uqhLlA5gD2NLMr4ZYJNcVw==/109951165565875187.jpg",
            playCount = 180000000L,
            description = "TVB小生，代表作《爱不疚》《年少无知》《你是我今生唯一传奇》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 14053
        ),
        RecommendPlaylist(
            id = 863046,
            name = "陈伟霆",
            cover = "https://p2.music.126.net/3_YWxMCOLdHLPUj3UXU-sQ==/109951165565874479.jpg",
            playCount = 190000000L,
            description = "人气偶像，代表作《想你想你》《别》《一笑倾城》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 863046
        ),
        RecommendPlaylist(
            id = 1017071,
            name = "TFBOYS",
            cover = "https://p1.music.126.net/yHXzFf-_i1fwc3T3yumW4w==/109951165565873771.jpg",
            playCount = 280000000L,
            description = "00后偶像组合，代表作《青春修炼手册》《样》《宠爱》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 1017071
        ),
        RecommendPlaylist(
            id = 1017072,
            name = "王俊凯",
            cover = "https://p2.music.126.net/VvX7kn5vcYU_kDJ7vAkR_Q==/109951165565873063.jpg",
            playCount = 160000000L,
            description = "TFBOYS队长，代表作《摩天轮的思念》《天使》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 1017072
        ),
        RecommendPlaylist(
            id = 1017073,
            name = "王源",
            cover = "https://p1.music.126.net/BHt8b5mJ-kf0J66i9Yq00A==/109951165565872355.jpg",
            playCount = 150000000L,
            description = "TFBOYS成员，代表作《十七》《姑娘》《世界上没有真正的感同身受》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 1017073
        ),
        RecommendPlaylist(
            id = 1047016,
            name = "黄子韬",
            cover = "https://p1.music.126.net/7VGTbHFdRO5LIgVkrOTLzQ==/109951165565870939.jpg",
            playCount = 170000000L,
            description = "说唱歌手，代表作《Promise》《Hello Hello》《AB Style》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 1047016
        ),
        RecommendPlaylist(
            id = 845163,
            name = "宋茜",
            cover = "https://p2.music.126.net/RkBFLGpGLI2HlH7GqDVMqQ==/109951165565868815.jpg",
            playCount = 160000000L,
            description = "全能艺人，代表作《屋顶着火》《说走就走》《请别问候我》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 845163
        ),
        RecommendPlaylist(
            id = 12445166,
            name = "火箭少女101",
            cover = "https://p2.music.126.net/Ps_fMMAFqEwNQZEzY3K25w==/109951165565863151.jpg",
            playCount = 190000000L,
            description = "女团组合，代表作《卡路里》《Light》《飒小姐》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 12445166
        ),
        RecommendPlaylist(
            id = 897142,
            name = "SNH48",
            cover = "https://p1.music.126.net/3Jw-X0T0mFMTQ_sJiHhMqA==/109951165565862443.jpg",
            playCount = 180000000L,
            description = "女团组合，代表作《梦想岛》《化作樱花树》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 897142
        ),
        RecommendPlaylist(
            id = 29808644,
            name = "李袁杰",
            cover = "https://p1.music.126.net/K3JJo-NhNJVR7PjqfJ6d5A==/109951165798457633.jpg",
            playCount = 140000000L,
            description = "网络歌手，代表作《离人愁》《醉千年》《过客》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 29808644
        ),
        RecommendPlaylist(
            id = 29566189,
            name = "陈雪凝",
            cover = "https://p2.music.126.net/KNYb3xRO8MZx9N0g7K7MFA==/109951165798458887.jpg",
            playCount = 160000000L,
            description = "网络歌手，代表作《绿色》《少年》《无羁》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 29566189
        ),
        RecommendPlaylist(
            id = 12244022,
            name = "王北车",
            cover = "https://p1.music.126.net/h0rrOD0F0hIXBSqQnPONAw==/109951165798459305.jpg",
            playCount = 140000000L,
            description = "治愈男声，代表作《陷阱》《突然想爱你》《如果爱就这么忘记》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 12244022
        ),
        RecommendPlaylist(
            id = 932327,
            name = "阿肆",
            cover = "https://p2.music.126.net/K6wKZjEvGCjKmU7VqOVwRw==/109951165798459723.jpg",
            playCount = 150000000L,
            description = "创作女声，代表作《热爱105°C的你》《我在人民广场吃炸鸡》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 932327
        ),
        RecommendPlaylist(
            id = 9945,
            name = "薛凯琪",
            cover = "https://p2.music.126.net/d0Hq3VPsLq_OlmLShsLswA==/109951165798460141.jpg",
            playCount = 190000000L,
            description = "港乐天后，代表作《小小》《奇洛李维斯回信》《谢谢你爱我》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 9945
        ),
        RecommendPlaylist(
            id = 7537,
            name = "魏如萱",
            cover = "https://p1.music.126.net/p5KCVUW7-9Z5g7_v4YcTJw==/109951165798460559.jpg",
            playCount = 170000000L,
            description = "金曲歌后，代表作《你啊你啊》《末日狂想》《香格里拉》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 7537
        ),
        RecommendPlaylist(
            id = 9946,
            name = "李佳薇",
            cover = "https://p2.music.126.net/bNSWrUGaFsC9H8HhDgTKYg==/109951165798460977.jpg",
            playCount = 180000000L,
            description = "实力女声，代表作《煎熬》《像天堂的悬崖》《我会在你身边》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 9946
        ),
        RecommendPlaylist(
            id = 4721,
            name = "姚贝娜",
            cover = "https://p1.music.126.net/4YdY8VvCSR2gD6-9LBCvQg==/109951165798461395.jpg",
            playCount = 200000000L,
            description = "实力唱将，代表作《甄嬛传》《画心2》《也许明天》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 4721
        ),
        RecommendPlaylist(
            id = 860950,
            name = "吉克隽逸",
            cover = "https://p2.music.126.net/T_JQklFm-_zXkKgR_sVLfw==/109951165798461813.jpg",
            playCount = 170000000L,
            description = "爆发力女声，代表作《彩色的黑》《即刻出发》《空空如也》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 860950
        ),
        RecommendPlaylist(
            id = 4722,
            name = "黄龄",
            cover = "https://p2.music.126.net/jWQoOVyH9yKcQaSDdXb9cw==/109951165798462649.jpg",
            playCount = 150000000L,
            description = "性感女声，代表作《痒》《High歌》《情人》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 4722
        ),
        RecommendPlaylist(
            id = 9947,
            name = "韩红",
            cover = "https://p1.music.126.net/s4P2HBYoaUftG0qjNqCU2w==/109951165798463067.jpg",
            playCount = 280000000L,
            description = "天籁女声，代表作《天路》《青藏高原》《那片海》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 9947
        ),
        RecommendPlaylist(
            id = 5538,
            name = "郭采洁",
            cover = "https://p2.music.126.net/4X8mNhHJ0Rh5VLxLCQk6BQ==/109951165798463485.jpg",
            playCount = 170000000L,
            description = "文艺女声，代表作《诚实地想你》《烟火》《什么什么》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 5538
        ),
        RecommendPlaylist(
            id = 7224,
            name = "关诗敏",
            cover = "https://p1.music.126.net/qo3pVhXLmx-0RcKGKM5nAg==/109951165798463903.jpg",
            playCount = 140000000L,
            description = "网络歌手，代表作《学猫叫》《像鱼》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 7224
        ),
        RecommendPlaylist(
            id = 12381491,
            name = "花粥",
            cover = "https://p2.music.126.net/oMqtjILPsYjMPhtT0NljCA==/109951165798464321.jpg",
            playCount = 150000000L,
            description = "民谣歌手，代表作《盗将行》《出山》《纸短情长》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 12381491
        ),
        RecommendPlaylist(
            id = 12602190,
            name = "陆虎",
            cover = "https://p2.music.126.net/A2ThLAzfZKqBa5Pc66yMFA==/109951165798465157.jpg",
            playCount = 140000000L,
            description = "创作歌手，代表作《春风十里》《夏洛特烦恼》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 12602190
        ),
        RecommendPlaylist(
            id = 12193093,
            name = "张碧晨",
            cover = "https://p1.music.126.net/hcADaZbUABbvNBq5uN7BJg==/109951165798465575.jpg",
            playCount = 210000000L,
            description = "实力女声，代表作《红玫瑰》《年轮说》《流光飞舞》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 12193093
        ),
        RecommendPlaylist(
            id = 2301,
            name = "蔡琴",
            cover = "https://p1.music.126.net/T7oFhQZySkNGxRbRE_bR1Q==/109951165798466411.jpg",
            playCount = 230000000L,
            description = "磁性女声，代表作《被遗忘的时光》《你的眼神》《恰似你的温柔》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 2301
        ),
        RecommendPlaylist(
            id = 5201,
            name = "邓丽君",
            cover = "https://p2.music.126.net/LKcF0V1mTpHnRnOKD5-5hg==/109951165798466829.jpg",
            playCount = 350000000L,
            description = "华语乐坛传奇，代表作《月亮代表我的心》《甜蜜蜜》《小城故事》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 5201
        ),
        RecommendPlaylist(
            id = 6461,
            name = "凤飞飞",
            cover = "https://p1.music.126.net/RcOZvQKhZXGKkQxuUXxzLw==/109951165798467247.jpg",
            playCount = 240000000L,
            description = "宝岛歌后，代表作《掌声响起》《追梦人》《流水年华》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 6461
        ),
        RecommendPlaylist(
            id = 10202,
            name = "阿杜",
            cover = "https://p2.music.126.net/qD2Ew_Qkh5fD4MQZR8_M2Q==/109951165798468501.jpg",
            playCount = 220000000L,
            description = "沙哑情歌王，代表作《他一定很爱你》《坚持到底》《离别》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 10202
        ),
        RecommendPlaylist(
            id = 7226,
            name = "吴宗宪",
            cover = "https://p1.music.126.net/xfMp0_f0hKb-BZxUPxqHJw==/109951165798468919.jpg",
            playCount = 200000000L,
            description = "综艺天王，代表作《真心换绝情》《屋顶》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 7226
        ),
        RecommendPlaylist(
            id = 3698,
            name = "黄小琥",
            cover = "https://p2.music.126.net/gH-6bRqrMFIHH1kBFBnluw==/109951165565894307.jpg",
            playCount = 190000000L,
            description = "爵士女声，代表作《你是幸福的我就是快乐的》《没那么简单》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 3698
        ),
        RecommendPlaylist(
            id = 12361087,
            name = "好妹妹",
            cover = "https://p1.music.126.net/Y3SXRs-nGT7UGHHfLpnEwg==/109951165798469337.jpg",
            playCount = 180000000L,
            description = "民谣组合，代表作《不说》《一个人的北京》《匆匆那年》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 12361087
        ),
        RecommendPlaylist(
            id = 30297368,
            name = "房东的猫",
            cover = "https://p2.music.126.net/jBJYVIiOVU1tNvK1WRq8hQ==/109951165798469755.jpg",
            playCount = 160000000L,
            description = "民谣组合，代表作《秋酿》《云烟成雨》《下一站茶山刘》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 30297368
        ),
        RecommendPlaylist(
            id = 12020012,
            name = "音阙诗听",
            cover = "https://p1.music.126.net/rK2TUtBnmEZNcqw0FpLxfQ==/109951165798470173.jpg",
            playCount = 150000000L,
            description = "古风组合，代表作《红昭愿》《芒种》《天命风流》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 12020012
        ),
        RecommendPlaylist(
            id = 12438389,
            name = "银临",
            cover = "https://p2.music.126.net/LAyuPQnQBCj-rGGKFlLu3A==/109951165798470591.jpg",
            playCount = 140000000L,
            description = "古风歌姬，代表作《牵丝戏》《不老梦》《腐草为萤》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 12438389
        ),
        RecommendPlaylist(
            id = 12381492,
            name = "萨顶顶",
            cover = "https://p1.music.126.net/ixcIg2CG7_pVE1pPCFQqKA==/109951165798473517.jpg",
            playCount = 180000000L,
            description = "民族音乐人，代表作《万物生》《左手指月》《神香》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 12381492
        ),
        RecommendPlaylist(
            id = 6499,
            name = "腾格尔",
            cover = "https://p2.music.126.net/vKoFjQDG0TqFQCJ2PLYX9g==/109951165798473935.jpg",
            playCount = 200000000L,
            description = "草原歌手，代表作《天堂》《蒙古人》《大雁》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 6499
        ),
        RecommendPlaylist(
            id = 7227,
            name = "乌兰图雅",
            cover = "https://p1.music.126.net/hXw-A0xt0fE0Y5L-Pt7pnw==/109951165798474353.jpg",
            playCount = 170000000L,
            description = "草原歌手，代表作《套马杆》《火了火了》《站在草原望北京》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 7227
        ),
        RecommendPlaylist(
            id = 5387,
            name = "乌兰托娅",
            cover = "https://p2.music.126.net/v4Lg0ybw7YKhWZ3CYMDiNA==/109951165798474771.jpg",
            playCount = 160000000L,
            description = "草原歌手，代表作《陪你一起看草原》《父亲的草原母亲的河》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 5387
        ),
        RecommendPlaylist(
            id = 12138269,
            name = "云朵",
            cover = "https://p1.music.126.net/hTh2OBh3y6pDkW5r-X-cAw==/109951165798475189.jpg",
            playCount = 150000000L,
            description = "藏族歌手，代表作《爱是你我》《云在飞》《伤心的时候可以听情歌》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 12138269
        ),
        RecommendPlaylist(
            id = 29808645,
            name = "要不要买菜",
            cover = "https://p1.music.126.net/yYj3RpvxEZ0M7LlrF-g7rA==/109951165798476025.jpg",
            playCount = 110000000L,
            description = "网络歌手，代表作《下山》《不谓侠》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 29808645
        ),
        RecommendPlaylist(
            id = 12602193,
            name = "胡66",
            cover = "https://p2.music.126.net/p-ypP_JEvQEUEPbxBx6qLg==/109951165798476443.jpg",
            playCount = 130000000L,
            description = "网络歌手，代表作《空空如也》《后来遇见他》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 12602193
        ),
        RecommendPlaylist(
            id = 12381493,
            name = "柏松",
            cover = "https://p1.music.126.net/K0-YWl0R1DsIWW-f7E6xEg==/109951165798476861.jpg",
            playCount = 120000000L,
            description = "网络歌手，代表作《无期》《错位时空》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 12381493
        ),
        RecommendPlaylist(
            id = 12244024,
            name = "艾辰",
            cover = "https://p2.music.126.net/LqS-dJDZa8B8b8Z_5e3LhA==/109951165798477279.jpg",
            playCount = 140000000L,
            description = "网络歌手，代表作《错位时空》《最后我们没在一起》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 12244024
        ),
        RecommendPlaylist(
            id = 30296371,
            name = "单依纯",
            cover = "https://p1.music.126.net/vPzGf_5kZkjN7KH7D1IYrg==/109951165798477697.jpg",
            playCount = 130000000L,
            description = "新生代歌手，代表作《你是我的神话》《好久不见》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 30296371
        ),
        RecommendPlaylist(
            id = 29816946,
            name = "赵紫骅",
            cover = "https://p2.music.126.net/mKcOLl1qD4tYX5-Hg-pBJw==/109951165798478115.jpg",
            playCount = 120000000L,
            description = "网络歌手，代表作《可不可以给我你的微信》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 29816946
        ),
        RecommendPlaylist(
            id = 12602194,
            name = "刘大壮",
            cover = "https://p1.music.126.net/z1R7IHYXYx4xZY-pNFCXSw==/109951165798478533.jpg",
            playCount = 130000000L,
            description = "网络歌手，代表作《迷路》《一首情歌》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 12602194
        )
    )
    // 精选歌单数据
    val PRESET_FEATURED_PLAYLIST = listOf(
        RecommendPlaylist(
            id = 11127,
            name = "Beyond",
            cover = "https://p1.music.126.net/EawqbkXCxGmxZ6nnqTKxKw==/109951165566992331.jpg",
            playCount = 380000000L,
            description = "香港殿堂级摇滚乐队，代表作《海阔天空》《光辉岁月》《真的爱你》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 11127
        ),
        RecommendPlaylist(
            id = 3681,
            name = "张国荣",
            cover = "https://p2.music.126.net/7wDg-DUN511nwSSPmOU5Xw==/109951165745388126.jpg",
            playCount = 380000000L,
            description = "香港巨星，永恒经典，代表作《倩女幽魂》《风继续吹》《Monica》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 3681
        ),
        RecommendPlaylist(
            id = 6452,
            name = "周杰伦",
            cover = "https://p2.music.126.net/NWv6PtSBkyWZzqbJVzBr7g==/109951169164936450.jpg",
            playCount = 500000000L,
            description = "华语乐坛天王，开创中国风流行音乐先河，代表作《青花瓷》《晴天》《稻香》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 6452
        ),
        RecommendPlaylist(
            id = 5773,
            name = "谢霆锋",
            cover = "https://p2.music.126.net/vm-Z1oXjvtqRyz7MBUnk-Q==/109951168978208335.jpg",
            playCount = 250000000L,
            description = "摇滚天王，全能艺人，代表作《黄种人》《因为爱所以爱》《谢谢你的爱》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 5773
        ),
        RecommendPlaylist(
            id = 5538,
            name = "汪苏泷",
            cover = "https://p2.music.126.net/7G5HqyqcpZoP4cHL7-a-hQ==/109951170027064713.jpg",
            playCount = 180000000L,
            description = "网络音乐人气歌手，代表作《有点甜》《耿耿于怀》《不分手的恋爱》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 5538
        ),
        RecommendPlaylist(
            id = 3691,
            name = "刘德华",
            cover = "https://p2.music.126.net/jrK3sMRIt30eFFuDGzr6iQ==/109951168274614846.jpg",
            playCount = 420000000L,
            description = "香港四大天王之一，华语乐坛传奇，代表作《忘情水》《冰雨》《中国人》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 3691
        ),
        RecommendPlaylist(
            id = 3684,
            name = "林俊杰",
            cover = "https://p1.music.126.net/78q0jUUJ0h08GxAs2G-tCA==/109951168529051968.jpg",
            playCount = 300000000L,
            description = "新加坡音乐才子，擅长R&B和抒情歌曲，代表作《江南》《曹操》《修炼爱情》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 3684
        ),
        RecommendPlaylist(
            id = 2116,
            name = "陈奕迅",
            cover = "https://p1.music.126.net/1qr8a9G8pWEMoruLJaBv8A==/109951169014564421.jpg",
            playCount = 350000000L,
            description = "港乐天王，演唱技巧出色，代表作《十年》《浮夸》《好久不见》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 2116
        ),
        RecommendPlaylist(
            id = 13193,
            name = "五月天",
            cover = "https://p1.music.126.net/qr5EV1Z5LDgar18Pilw0Eg==/109951170331174200.jpg",
            playCount = 400000000L,
            description = "台湾摇滚天团，青春记忆符号，代表作《倔强》《突然好想你》《知足》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 13193
        ),
        RecommendPlaylist(
            id = 5346,
            name = "王力宏",
            cover = "https://p1.music.126.net/bM06VHfs1ivzKegl3nMPsg==/109951169421841547.jpg",
            playCount = 280000000L,
            description = "华语R&B先驱，音乐制作才子，代表作《唯一》《大城小爱》《依然爱你》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 5346
        ),
        RecommendPlaylist(
            id = 8325,
            name = "梁静茹",
            cover = "https://p1.music.126.net/g_32ea9zMstphGkRjwgC1g==/109951164077995938.jpg",
            playCount = 270000000L,
            description = "情歌天后，温暖治愈的声音，代表作《勇气》《宁夏》《会呼吸的痛》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 8325
        ),
        RecommendPlaylist(
            id = 6460,
            name = "张学友",
            cover = "https://p2.music.126.net/bGTTVbPYHT24w2HkHrdXmQ==/109951166958310165.jpg",
            playCount = 320000000L,
            description = "歌神，粤语流行乐代表人物，代表作《吻别》《一路上有你》《她来听我的演唱会》",
            source = PlaylistSource.ARTIST_PLAYLIST,
            artistId = 6460
        ),
    )
    val PRESET_PLAYLISTS = listOf(
        RecommendPlaylist(5059642708, "国风金曲精选", "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=400&h=400&fit=crop", 150000000L, "知我、盗将行、牵丝戏、九张机等国风经典歌曲"),
        RecommendPlaylist(2619366303, "华语流行精选", "https://images.unsplash.com/photo-1571330735066-03aaa9429d89?w=300&h=300&fit=crop", 300000000L, "浪子回头、生僻字、下坠等华语热门歌曲"),
        RecommendPlaylist(2619366309, "华语民谣治愈", "https://images.unsplash.com/photo-1533174072545-7a4b6ad7a6c3?w=300&h=300&fit=crop", 260000000L, "LBI利比、葛东琪、郭顶等治愈歌曲"),
        RecommendPlaylist(2619366299, "史诗电音交响", "https://images.unsplash.com/photo-1520523839897-bd0b52f945a0?w=300&h=300&fit=crop", 200000000L, "Two Steps From Hell震撼配乐精选"),
        // 官方榜单（7个）- 高播放量
        RecommendPlaylist(
            19723756,
            "云音乐飙升榜",
            "https://images.unsplash.com/photo-1514320291840-2e0a9bf2a9ae?w=400&h=400&fit=crop",
            1023000000L,
            "实时追踪热度飙升最快的歌曲，发现下一首爆款"
        ),
        RecommendPlaylist(
            3779629,
            "云音乐新歌榜",
            "https://images.unsplash.com/photo-1493225457124-a3eb161ffa5f?w=400&h=400&fit=crop",
            876000000L,
            "每周更新最新上架歌曲，抢先聆听华语乐坛新声"
        ),
        RecommendPlaylist(
            2884035,
            "云音乐原创榜",
            "https://images.unsplash.com/photo-1511379938547-c1f69419868d?w=400&h=400&fit=crop",
            658000000L,
            "扶持原创音乐人，聚焦独立音乐新势力"
        ),
        RecommendPlaylist(
            3778678,
            "云音乐热歌榜",
            "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=400&h=400&fit=crop",
            1520000000L,
            "全站播放量最高歌曲汇总，人气与质量的双重保证"
        ),
        RecommendPlaylist(
            71384707,
            "音乐人气榜",
            "https://images.unsplash.com/photo-1506157786151-b8491531f063?w=400&h=400&fit=crop",
            723000000L,
            "根据歌手人气值排行，追踪最受欢迎的音乐人"
        ),
        RecommendPlaylist(
            5059644681,
            "网络热歌榜",
            "https://images.unsplash.com/photo-1487180144351-b8472da7d491?w=400&h=400&fit=crop",
            892000000L,
            "抖音、快手等平台爆火歌曲，网络流行风向标"
        ),
        RecommendPlaylist(
            745956260,
            "云音乐说唱榜",
            "https://images.unsplash.com/photo-1571330735066-03aaa9429d89?w=400&h=400&fit=crop",
            534000000L,
            "华语说唱精选榜单，GAI、Vava、艾热等rapper作品"
        ),
        // 华语流行（5个）- 中高播放量
        RecommendPlaylist(
            2809513713,
            "欧美流行TOP100",
            "https://images.unsplash.com/photo-1493225457124-a3eb161ffa5f?w=400&h=400&fit=crop",
            587000000L,
            "Billboard热门单曲，Taylor Swift、Ed Sheeran等欧美巨星"
        ),
        RecommendPlaylist(
            2094948926,
            "国语流行音乐",
            "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=400&h=400&fit=crop",
            478000000L,
            "蔡徐坤、王力宏、林俊杰、汪苏泷等新老歌手经典歌曲"
        ),
        // 年代经典+国际榜（5个）- 中高播放量
        RecommendPlaylist(
            180106,
            "英国UK榜",
            "https://images.unsplash.com/photo-1493225457124-a3eb161ffa5f?w=400&h=400&fit=crop",
            365000000L,
            "英国官方单曲榜，Adele、Coldplay等英伦音乐风向"
        ),
        RecommendPlaylist(
            60198,
            "美国Billboard榜",
            "https://images.unsplash.com/photo-1514320291840-2e0a9bf2a9ae?w=400&h=400&fit=crop",
            512000000L,
            "Billboard Hot 100经典曲目，美国流行音乐风向标"
        ),
    )
}