package com.quitkelly.app.data.news

data class NewsSourceInfo(
    val id: String,
    val name: String,
    val description: String
)

object NewsSourceCatalog {

    val all: List<NewsSourceInfo> = listOf(
        NewsSourceInfo("wscn", "华尔街见闻", "7×24 全球快讯"),
        NewsSourceInfo("sina", "新浪财经", "财经滚动新闻"),
        NewsSourceInfo("eastmoney", "东方财富", "财经要闻"),
        NewsSourceInfo("ths", "同花顺", "A股快讯与异动"),
        NewsSourceInfo("jin10", "金十数据", "全球市场快讯"),
        NewsSourceInfo("nbd", "每日经济新闻", "财经要闻"),
        NewsSourceInfo("tmtpost", "钛媒体", "商业与科技"),
        NewsSourceInfo("yicai", "第一财经", "财经新闻与深度"),
        NewsSourceInfo("21jingji", "21世纪经济报道", "财经深度报道"),
        NewsSourceInfo("netease", "网易财经", "财经要闻")
    )

    val defaultIds: Set<String> = all.map { it.id }.toSet()

    val newIds: Set<String> = setOf(
        "ths",
        "jin10",
        "nbd",
        "tmtpost",
        "yicai",
        "21jingji",
        "netease"
    )
}
