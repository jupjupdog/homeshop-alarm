package com.homeshop.alarm.collect

import com.homeshop.alarm.model.Categories
import org.junit.Assert.assertEquals
import org.junit.Test

/** collector/tests/test_parsers.py 의 분류·검색어 테스트와 같은 사례. */
class CategorizerTest {
    @Test fun categoryNamesMatchApp() {
        assertEquals(Categories.ALL.toSet(), Categorizer.ALL.toSet())
    }

    @Test fun basic() {
        assertEquals("패션", Categorizer.categorize("[TV상품] 쏘울 26FW 울 저지 더블 재킷"))
        assertEquals("가전·디지털", Categorizer.categorize("LG 통돌이 세탁기 19KG"))
        assertEquals("뷰티", Categorizer.categorize("뷰 유자 멜라토닝 세럼 30ml"))
        assertEquals("식품", Categorizer.categorize("아빠랑 저칼로리 잼 3종 세트"))
        assertEquals("식품", Categorizer.categorize("전통남원추어탕 300g 10팩"))
    }

    @Test fun realNames() {
        val cases = mapOf(
            // 다른 단어 속 글자에 걸리던 것
            "덴프스 트루바이타민 올인원 멀티비타민&루테인&밀크씨슬 10개월분" to "건강",
            "Ninja 크리미 아이스크림 메이커 NC300KR" to "가전·디지털",
            "스케쳐스 26FW 최신상 여성용 이지고잉 슬립인스 로퍼" to "패션",
            "신차 장기렌터카 비교견적 렌터카샵" to "여행·렌탈·보험",
            "[10월CJ단독] 슈퍼소닉 헤어드라이어 오리진 니켈/코퍼" to "가전·디지털",
            "와이즈바이옴 당큐락 6개월분 패키지" to "건강",
            // 핵심 명사가 뒤에 오는 것
            "오븐에 구운 호두찹쌀떡 파이 80g x 24개" to "식품",
            "[최신상] 헤르젠 쌀냉장고 AI CARE 플러스 11kg" to "가전·디지털",
            "VT 리들샷 콜라겐 크림 기획세트" to "뷰티",
            // 덤 상품은 무시
            "그레인온 캡슐레이션효소 14박스+텀블러블렌더+효소G선물" to "건강",
            // '기타'로 빠지던 것
            "관절엔 DEM 6개월분" to "건강",
            "이경제 흑염소진액 眞 1박스 / 총 30포" to "건강",
            "안면도농협 태양초 햇 고춧가루 500gX4봉" to "식품",
            "2026 해남 햇 꿀고구마 3kg x 2박스(총6kg)" to "식품",
            "26FW 멜팅 울트라 파인 울100 터틀넥 풀오버" to "패션",
            "세라 26FW 소가죽 드라이빙 로퍼" to "패션",
            "락앤락 인터락 10종" to "리빙·주방",
            "[뉴질랜드파세오] 파세오 럭셔리 4겹 로션 FT 70매4입X" to "리빙·주방",
            "쿠쿠 음식물처리기 에코웨일 큐브[그레이]" to "가전·디지털",
            "[기본] NEW 시즌2 더창 볼륨핏 정수리 가발 1개" to "뷰티",
            "[허니제주]에코랜드+낭만카페투어 4일" to "여행·렌탈·보험",
            "안경사협회 홍보방송" to "기타",
        )
        for ((name, expected) in cases) assertEquals(name, expected, Categorizer.categorize(name))
    }

    @Test fun searchQuery() {
        assertEquals("램스킨 레더 트리밍 자켓", SearchQuery.from("[최초가 199,000원] 26SS 램스킨 레더 트리밍 자켓"))
        assertEquals("올데이 액티브 셋업 남성", SearchQuery.from("26FW 올데이 액티브 셋업 3+1종 세트 남성"))
        assertEquals("뷰 유자 멜라토닝 세럼 30ml", SearchQuery.from("뷰 유자 멜라토닝 세럼 30ml 6개 방송특가"))
        assertEquals("LG 통돌이 세탁기 19KG T19MX7A 미드", SearchQuery.from("LG 통돌이 세탁기 19KG T19MX7A 미드 블랙"))
    }
}
