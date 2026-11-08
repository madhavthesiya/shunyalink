package com.shunyalink.analytics;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BotClassifierTest {
    private final BotClassifier classifier = new BotClassifier();

    @Test
    void recognizesAutomatedClientsRegardlessOfCase() {
        assertThat(classifier.isBot("Mozilla/5.0 (compatible; Googlebot/2.1)")).isTrue();
        assertThat(classifier.isBot("Slackbot-LinkExpanding 1.0")).isTrue();
        assertThat(classifier.isBot("python-requests/2.32")).isTrue();
        assertThat(classifier.isBot("HeadlessChrome/130")).isTrue();
        assertThat(classifier.isBot("ShunyaLink-DeploySmoke/1.0")).isTrue();
    }

    @Test
    void leavesOrdinaryOrMissingUserAgentsCountable() {
        assertThat(classifier.isBot("Mozilla/5.0 Chrome/130.0 Safari/537.36")).isFalse();
        assertThat(classifier.isBot(null)).isFalse();
        assertThat(classifier.isBot(" ")).isFalse();
    }
}
