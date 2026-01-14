package kr.co.xai.portal.backend.integration.external;

import kr.co.xai.portal.backend.ai.annotation.AiTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class GoogleNewsClient {

    private final RestTemplate restTemplate;

    // 구글 뉴스 RSS URL
    private static final String GOOGLE_NEWS_RSS_URL = "https://news.google.com/rss/search?q={keyword}&hl=ko&gl=KR&ceid=KR:ko";

    /**
     * [AI 도구] 실시간 뉴스 검색
     */
    @AiTool(description = "Search real-time international or local news using Google News. Keyword required.")
    public List<Map<String, String>> searchRealTimeNews(String keyword) {
        // [수정 1] 키워드가 없거나 빈 값일 경우 '주요 뉴스'로 기본값 설정 (에러 방지 핵심!)
        if (keyword == null || keyword.trim().isEmpty() || "null".equalsIgnoreCase(keyword)) {
            keyword = "주요 뉴스";
        }

        log.info(">> [Google News] Searching for: {}", keyword);

        try {
            // RSS 데이터 가져오기
            String rssData = restTemplate.getForObject(GOOGLE_NEWS_RSS_URL, String.class, keyword);

            // 파싱
            return parseRssSimple(rssData);

        } catch (Exception e) {
            log.error("News Search Failed: {}", e.getMessage());
            // 에러 발생 시 빈 리스트 대신 에러 메시지 맵 반환
            List<Map<String, String>> errorList = new ArrayList<>();
            Map<String, String> errMap = new HashMap<>();
            errMap.put("title", "뉴스 검색 중 오류가 발생했습니다.");
            errMap.put("date", "");
            errorList.add(errMap);
            return errorList;
        }
    }

    // 간단한 RSS 파서 (라이브러리 없이 문자열 처리)
    private List<Map<String, String>> parseRssSimple(String rssXml) {
        List<Map<String, String>> newsList = new ArrayList<>();
        if (rssXml == null)
            return newsList;

        // <item> 태그 단위로 분리
        String[] items = rssXml.split("<item>");

        // 최대 5개만 추출
        for (int i = 1; i < Math.min(items.length, 6); i++) {
            String item = items[i];
            String title = extractTag(item, "title");
            String pubDate = extractTag(item, "pubDate");

            if (title != null) {
                Map<String, String> news = new HashMap<>();
                // CDATA 태그 제거 및 HTML 엔티티 처리
                title = title.replace("<![CDATA[", "").replace("]]>", "");
                title = title.replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&");

                news.put("title", title);
                news.put("date", pubDate); // 필요시 날짜 포맷팅 추가 가능
                newsList.add(news);
            }
        }
        return newsList;
    }

    private String extractTag(String source, String tagName) {
        String startTag = "<" + tagName + ">";
        String endTag = "</" + tagName + ">";
        int start = source.indexOf(startTag);
        int end = source.indexOf(endTag);
        if (start == -1 || end == -1)
            return null;
        return source.substring(start + startTag.length(), end);
    }
}