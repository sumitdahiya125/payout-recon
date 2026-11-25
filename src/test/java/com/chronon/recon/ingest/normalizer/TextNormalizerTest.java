package com.chronon.recon.ingest.normalizer;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TextNormalizerTest {

    @Test
    void cleansNonBreakingSpaces() {
        assertThat(TextNormalizer.clean("NEFT\u00A0CR"))
                .isEqualTo("NEFT CR");
    }

    @Test
    void stripsControlCharacters() {
        assertThat(TextNormalizer.clean("ab"))
                .isEqualTo("ab");
    }

    @Test
    void foldsAccentsAndCase() {
        assertThat(TextNormalizer.fold("Rahul Sharma"))
                .isEqualTo("rahulsharma");
        assertThat(TextNormalizer.fold("José"))
                .isEqualTo("jose");
    }

    @Test
    void containsFolded() {
        assertThat(TextNormalizer.containsFolded("PO-1042", "po 1042"))
                .isTrue();
    }
}
