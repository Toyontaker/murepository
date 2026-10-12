package com.toyontaker.manabikko.data

import kotlin.random.Random

/** なぞりがきで つぎに だす 文字の じゅんばん。 */
sealed interface LetterOrder {
    /** あいうえお じゅん */
    data object Sequential : LetterOrder

    /** ぜんぶの 文字から ランダム */
    data object AllRandom : LetterOrder

    /** ひとつの 行(あ行、か行 …)の なかで ランダム。row は 五十音表の 行番号。 */
    data class RowRandom(val row: Int) : LetterOrder

    /** 保存用の 文字列(「seq」「all」「row:3」)。 */
    fun encode(): String = when (this) {
        Sequential -> "seq"
        AllRandom -> "all"
        is RowRandom -> "row:$row"
    }

    companion object {
        fun decode(s: String?): LetterOrder = when {
            s == "all" -> AllRandom
            s != null && s.startsWith("row:") -> s.removePrefix("row:").toIntOrNull()?.let { RowRandom(it) } ?: Sequential
            else -> Sequential
        }
    }
}

/**
 * [order] に したがって つぎの 文字を えらぶ。
 * ランダムの ときは 山札のように シャッフルして 1まいずつ くばるので、
 * ぜんぶ 出るまで 同じ文字は 出ず、つづけて 同じ文字にも ならない。
 */
class LetterDeck(
    private val set: KanaSet,
    val order: LetterOrder,
    private val random: Random = Random.Default,
) {
    /** この じゅんばんで 出る 文字。 */
    val pool: List<Kana> = when (order) {
        is LetterOrder.RowRandom -> set.table.getOrNull(order.row)?.filterNotNull().orEmpty().ifEmpty { set.all }
        else -> set.all
    }

    private val queue = ArrayDeque<Kana>()

    fun next(current: Kana?): Kana {
        if (order == LetterOrder.Sequential) {
            val i = pool.indexOf(current)
            return pool[(i + 1) % pool.size]
        }
        if (queue.isEmpty()) {
            val shuffled = pool.shuffled(random).toMutableList()
            // 山札を くばりなおした ときに、いまの 文字が また 先頭に こないように
            if (shuffled.size > 1 && shuffled.first() == current) shuffled.add(shuffled.removeAt(0))
            queue.addAll(shuffled)
        }
        return queue.removeFirst()
    }

    /** じゅんばん モードで 1つ まえ。 */
    fun previous(current: Kana): Kana {
        val i = pool.indexOf(current).coerceAtLeast(0)
        return pool[(i - 1 + pool.size) % pool.size]
    }
}
