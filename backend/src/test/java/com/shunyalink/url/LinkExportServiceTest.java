package com.shunyalink.url;

import com.shunyalink.exception.NotFoundException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LinkExportServiceTest {
    private final UrlRepository urls = mock(UrlRepository.class);
    private final CsvExportService csv = mock(CsvExportService.class);
    private final LinkExportService exports = new LinkExportService(urls, csv);

    private static UrlEntity link(String shortId, Long owner) {
        UrlEntity link = new UrlEntity();
        link.setShortId(shortId);
        link.setUserId(owner);
        return link;
    }

    @Test
    void theOwner_getsTheirLinksRow() {
        UrlEntity mine = link("mine", 7L);
        when(urls.findByShortId("mine")).thenReturn(Optional.of(mine));
        when(csv.exportToCsv(List.of(mine))).thenReturn("csv");

        assertThat(exports.linkCsv("mine", 7L)).isEqualTo("csv");
    }

    @Test
    void someoneElsesLink_andAMissingLink_lookTheSame_notFound() {
        // 403 for someone else's link would confirm that it exists; viewing its stats already answers 404.
        when(urls.findByShortId("theirs")).thenReturn(Optional.of(link("theirs", 99L)));
        when(urls.findByShortId("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> exports.linkCsv("theirs", 7L)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> exports.linkCsv("ghost", 7L)).isInstanceOf(NotFoundException.class);
        verify(csv, never()).exportToCsv(anyList());
    }

    @Test
    void anAnonymousLink_isNobodysToExport() {
        when(urls.findByShortId("anon")).thenReturn(Optional.of(link("anon", null)));

        assertThatThrownBy(() -> exports.linkCsv("anon", 7L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void allLinks_areTheUsersOwn_newestFirst() {
        List<UrlEntity> mine = List.of(link("b", 7L), link("a", 7L));
        when(urls.findByUserIdOrderByCreatedAtDesc(7L)).thenReturn(mine);
        when(csv.exportToCsv(mine)).thenReturn("all");

        assertThat(exports.allLinksCsv(7L)).isEqualTo("all");
    }
}
