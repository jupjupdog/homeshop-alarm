package com.homeshop.alarm.collect

/**
 * 홈쇼핑 상품명을 최저가 검색어로 정제한다 (collector/normalize.py 를 옮긴 것).
 * 상품명에는 "[TV상품]", "[최초가 199,000원]", "3+1종 세트", "+사은품" 같은 판촉 문구가 많아서
 * 그대로 검색하면 결과가 엉뚱해진다.
 */
object SearchQuery {
    private val BRACKETS = Regex("""\[[^\]]*]|【[^】]*】|\((?:[^)]*(?:사은품|증정|특가|방송|한정|단독|최초가|무료)[^)]*)\)""")
    private val PROMO_WORDS = Regex(
        "(방송에서만|방송\\s*특가|단독\\s*구성|단독|한정\\s*수량|한정|특별\\s*구성|최초가|역대\\s*최저|" +
            "무료\\s*배송|무이자|사은품|증정|TV\\s*상품|론칭|런칭|앵콜|재방)"
    )
    private val QUANTITY = Regex("""(총\s*)?\d+(\s*\+\s*\d+)?\s*(종|팩|개입|개|박스|병|봉|매|세트|SET|set|입)(\s*세트)?""")
    private val PLUS_GIFT = Regex("""\+\s*\S*(사은품|증정|선물)\S*""")
    // Python 의 \b 대신 명시적 경계 (Android ICU 와 JVM 의 \b 동작이 달라서)
    private val SEASON = Regex("""(?<![0-9A-Za-z가-힣])\d{2}(SS|FW|S/S|F/W)(?![0-9A-Za-z가-힣])""", RegexOption.IGNORE_CASE)
    private val PUNCT = Regex("""[.,!~★☆♥/|]+""")

    fun from(name: String, maxWords: Int = 6): String {
        var q = name
        for (re in listOf(BRACKETS, PLUS_GIFT, PROMO_WORDS, QUANTITY, SEASON, PUNCT)) q = re.replace(q, " ")
        q = q.squash()
        // 너무 긴 상품명은 앞쪽(브랜드+핵심 명사)만 사용
        return if (q.isEmpty()) name.trim() else q.split(" ").take(maxWords).joinToString(" ")
    }
}
