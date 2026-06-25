package com.aifinance.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AI Agent 相关可配置参数(对应 application.yml 的 agent.*)。
 */
@Component
@ConfigurationProperties(prefix = "agent")
public class AgentProperties {

    private Prediction prediction = new Prediction();
    private Finance finance = new Finance();
    private Crawler crawler = new Crawler();

    public Prediction getPrediction() {
        return prediction;
    }

    public void setPrediction(Prediction prediction) {
        this.prediction = prediction;
    }

    public Finance getFinance() {
        return finance;
    }

    public void setFinance(Finance finance) {
        this.finance = finance;
    }

    public Crawler getCrawler() {
        return crawler;
    }

    public void setCrawler(Crawler crawler) {
        this.crawler = crawler;
    }

    public static class Prediction {
        private int rounds = 5;
        private double confidenceThreshold = 0.6;
        private int branches = 3;

        public int getRounds() {
            return rounds;
        }

        public void setRounds(int rounds) {
            this.rounds = rounds;
        }

        public double getConfidenceThreshold() {
            return confidenceThreshold;
        }

        public void setConfidenceThreshold(double confidenceThreshold) {
            this.confidenceThreshold = confidenceThreshold;
        }

        public int getBranches() {
            return branches;
        }

        public void setBranches(int branches) {
            this.branches = branches;
        }
    }

    public static class Finance {
        private int rounds = 5;
        private double confidenceThreshold = 0.6;

        public int getRounds() {
            return rounds;
        }

        public void setRounds(int rounds) {
            this.rounds = rounds;
        }

        public double getConfidenceThreshold() {
            return confidenceThreshold;
        }

        public void setConfidenceThreshold(double confidenceThreshold) {
            this.confidenceThreshold = confidenceThreshold;
        }
    }

    public static class Crawler {
        private boolean enabled = true;
        private int timeoutMs = 6000;
        private int maxNews = 5;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getTimeoutMs() {
            return timeoutMs;
        }

        public void setTimeoutMs(int timeoutMs) {
            this.timeoutMs = timeoutMs;
        }

        public int getMaxNews() {
            return maxNews;
        }

        public void setMaxNews(int maxNews) {
            this.maxNews = maxNews;
        }
    }
}
