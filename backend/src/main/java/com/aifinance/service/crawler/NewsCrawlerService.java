package com.aifinance.service.crawler;

import com.aifinance.config.AgentProperties;
import com.aifinance.entity.NewsArticle;
import com.aifinance.repository.NewsRepository;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 新闻爬虫: 抓取与某资产/关键词相关的最新新闻, 供 AI 读取。
 * <p>
 * 设计为"优雅降级": 当沙箱/生产环境无外网或目标站点结构变化时,
 * 不抛异常, 返回空列表, 由上层 Agent 自行处理"无新闻"的情况。
 */
@Service
public class NewsCrawlerService {

    private static final Logger log = LoggerFactory.getLogger(NewsCrawlerService.class);

    private static final String UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/124.0 Safari/537.36";

    private final NewsRepository newsRepository;
    private final AgentProperties properties;

    public NewsCrawlerService(NewsRepository newsRepository, AgentProperties properties) {
        this.newsRepository = newsRepository;
        this.properties = properties;
    }

    /**
     * 抓取与关键词相关的最新新闻, 并持久化。
     *
     * @param assetCode 资产代码(用于落库关联)
     * @param keyword   检索关键词(通常是资产名称)
     * @return 抓取到的新闻列表(可能为空)
     */
    public List<NewsArticle> fetchAndStore(String assetCode, String keyword) {
        List<NewsArticle> result = new ArrayList<>();
        if (!properties.getCrawler().isEnabled()) {
            return result;
        }
        try {
            result = fetchFromBaiduNews(assetCode, keyword);
        } catch (Exception e) {
            log.warn("百度新闻抓取失败, 降级: {}", e.getMessage());
        }
        if (result.isEmpty()) {
            try {
                result = fetchFromSina(assetCode, keyword);
            } catch (Exception e) {
                log.warn("新浪新闻抓取失败, 降级: {}", e.getMessage());
            }
        }
        if (!result.isEmpty()) {
            try {
                newsRepository.saveAll(result);
            } catch (Exception e) {
                log.warn("新闻落库失败(忽略): {}", e.getMessage());
            }
        }
        return result;
    }

    private List<NewsArticle> fetchFromBaiduNews(String assetCode, String keyword) throws Exception {
        List<NewsArticle> list = new ArrayList<>();
        String q = URLEncoder.encode(keyword + " 股票 基金", StandardCharsets.UTF_8);
        String url = "https://www.baidu.com/s?rtt=1&bsst=1&cl=2&tn=news&word=" + q;

        Document doc = Jsoup.connect(url)
                .userAgent(UA)
                .timeout(properties.getCrawler().getTimeoutMs())
                .ignoreHttpErrors(true)
                .get();

        Elements items = doc.select("div.result, div.result-op");
        int max = properties.getCrawler().getMaxNews();
        for (Element item : items) {
            if (list.size() >= max) break;
            Element titleEl = item.selectFirst("h3 a");
            if (titleEl == null) continue;
            String title = titleEl.text();
            String link = titleEl.absUrl("href");
            String summary = textOf(item.selectFirst("span.c-summary, div.c-summary, .c-span-last"));
            if (title.isBlank()) continue;
            list.add(buildNews(assetCode, keyword, title, link, "百度新闻", summary));
        }
        return list;
    }

    private List<NewsArticle> fetchFromSina(String assetCode, String keyword) throws Exception {
        List<NewsArticle> list = new ArrayList<>();
        String q = URLEncoder.encode(keyword, StandardCharsets.UTF_8);
        String url = "https://search.sina.com.cn/?q=" + q + "&c=news&from=channel&ie=utf-8";

        Document doc = Jsoup.connect(url)
                .userAgent(UA)
                .timeout(properties.getCrawler().getTimeoutMs())
                .ignoreHttpErrors(true)
                .get();

        Elements items = doc.select("div.box-result, div.result");
        int max = properties.getCrawler().getMaxNews();
        for (Element item : items) {
            if (list.size() >= max) break;
            Element titleEl = item.selectFirst("h2 a");
            if (titleEl == null) continue;
            String title = titleEl.text();
            String link = titleEl.absUrl("href");
            String summary = textOf(item.selectFirst("p.content"));
            if (title.isBlank()) continue;
            list.add(buildNews(assetCode, keyword, title, link, "新浪", summary));
        }
        return list;
    }

    private NewsArticle buildNews(String assetCode, String keyword, String title,
                                  String link, String source, String summary) {
        NewsArticle n = new NewsArticle();
        n.setAssetCode(assetCode);
        n.setKeyword(keyword);
        n.setTitle(clip(title, 480));
        n.setUrl(clip(link, 980));
        n.setSource(source);
        n.setSummary(clip(summary, 1000));
        n.setPublishedAt(LocalDateTime.now());
        return n;
    }

    private String textOf(Element el) {
        return el == null ? "" : el.text();
    }

    private String clip(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }
}
