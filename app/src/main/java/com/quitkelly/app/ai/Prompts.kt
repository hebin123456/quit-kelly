package com.quitkelly.app.ai

import com.quitkelly.app.data.BuiltInAgents
import com.quitkelly.app.data.NewsItemEntity
import com.quitkelly.app.util.formatNewsTime

object Prompts {

    const val DIGEST_SYSTEM = """
你是“凯小丽”，一位有十年卖方研究经验的资深财经分析师，擅长宏观策略、A股行业轮动以及全球市场联动。
你的表达专业但不晦涩：先给结论，再用要点说明依据，并主动提示风险。
你只基于用户提供的新闻材料进行分析，绝不编造数据、事件或来源。
"""

    fun digestUser(news: List<NewsItemEntity>, date: String): String {
        return buildString {
            appendLine("日期：$date")
            appendLine("以下是我今天抓取到的财经新闻（共 ${news.size} 条），请基于这些材料撰写一份中文财经日报。")
            appendLine()
            news.forEachIndexed { index, item ->
                appendLine("${index + 1}. 【${item.source}】${formatNewsTime(item.publishedAt, item.timeApproximate)} ${item.title}")
                if (item.summary.isNotBlank()) {
                    appendLine("   摘要：${item.summary.take(180)}")
                }
            }
            appendLine()
            appendLine("输出要求：")
            appendLine("1. 使用 Markdown，按以下二级标题组织内容：今日市场速览、宏观与政策、A股与港股、行业与公司、海外与大宗、风险与关注、凯小丽的一句话。")
            appendLine("2. 只基于上述新闻，不要编造数据；某个板块信息不足时写“暂无重要信息”。")
            appendLine("3. 全文 600-900 字，语言简洁、观点鲜明，先结论后依据。")
            appendLine("4. 最后单独一行注明：以上内容由 AI 整理，不构成投资建议。")
        }
    }

    fun chatSystem(
        persona: String,
        date: String,
        digestDate: String?,
        digest: String?,
        headlines: String
    ): String {
        return buildString {
            appendLine(persona.trim().ifBlank { BuiltInAgents.DEFAULT_PERSONA })
            appendLine("今天是 $date。")
            appendLine()
            if (!digest.isNullOrBlank()) {
                appendLine("【财经日报（$digestDate）】")
                appendLine(digest.take(6000))
            } else {
                appendLine("【今日日报尚未生成】")
            }
            if (headlines.isNotBlank()) {
                appendLine()
                appendLine("【App 已抓取的财经新闻】")
                appendLine(headlines)
            }
            appendLine()
            appendLine("【回答要求】")
            appendLine("1. 你已拥有上述由 App 抓取并保存的新闻与日报。用户询问“今天的财经新闻”“今天发生了什么”“帮我总结一下”时，必须直接基于这些材料进行总结。")
            appendLine("2. 严禁回答“我无法获取实时新闻”“需要联网查询”“没有数据”这类内容；只有在材料确实为空时才说明“当前没有可用的新闻材料”。")
            appendLine("3. 如果日报日期不是今天，先说明“以下是最近一份日报（日期）的内容”，再继续总结。")
            appendLine("4. 用中文回答，先给结论，再给 2-4 条依据，可用小标题或要点；涉及数据时以材料为准，材料未覆盖的内容明确说“材料中未提及”，不要编造。")
            appendLine("5. 不提供具体买卖指令；涉及投资决策时提醒风险。默认控制在 500 字以内。")
        }
    }
}
