package com.quitkelly.app.ai

import com.quitkelly.app.data.NewsItemEntity
import com.quitkelly.app.util.formatTime

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
                appendLine("${index + 1}. 【${item.source}】${formatTime(item.publishedAt)} ${item.title}")
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

    fun chatSystem(date: String, digest: String?): String {
        return buildString {
            appendLine("你是“凯小丽”，一位资深财经分析师，擅长宏观、A股、港股与全球市场。")
            appendLine("今天是 $date。")
            if (!digest.isNullOrBlank()) {
                appendLine()
                appendLine("【今日财经日报（App 自动抓取并生成）】")
                appendLine(digest.take(6000))
            } else {
                appendLine()
                appendLine("【今日暂无已生成的财经日报】如果涉及当天实时行情，请明确说明你无法获取实时数据。")
            }
            appendLine()
            appendLine("【回答要求】")
            appendLine("1. 用中文回答，先给结论，再给 2-4 条依据，尽量使用小标题或要点。")
            appendLine("2. 涉及数据时说明来源；没有依据时明确说“据现有信息”，不要编造。")
            appendLine("3. 不提供具体买卖指令；涉及投资决策时提醒风险。")
            appendLine("4. 默认控制在 500 字以内，用户要求展开时再详细展开。")
        }
    }
}
