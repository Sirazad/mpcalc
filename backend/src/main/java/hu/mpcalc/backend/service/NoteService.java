package hu.mpcalc.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The notes of the chat: Hungarian texts that are not used in any calculation. A note is found when a word of the question
 * matches its keywords (weight A) or its title (B), stemmed with Postgres' Hungarian configuration, so "pentagrammával" finds a note
 * about the pentagramma. The text (C) only raises the rank of a note that is found this way: matching on the text alone found notes
 * for questions that merely share a common word (measured 2026-10-19). Stemming does not join word forms whose vowel changes
 * (fonál, fonalat), so such forms are listed as keywords of the note.
 */
@Service
public class NoteService {
    /** A note another note refers to (declared in one note file, the reverse link is made by the build step). */
    public record Link(String id, String title) {}
    public record Note(String id, String title, String category, String school, String text, String source, double rank, List<Link> links) {}

    static final int MAX_WORDS = 30;
    static final int MAX_LIMIT = 5;
    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}]+");

    private final JdbcTemplate jdbc;
    private final double minRank;

    public NoteService(JdbcTemplate jdbc, @Value("${mpcalc.notes.min-rank:0.05}") double minRank) {
        this.jdbc = jdbc;
        this.minRank = minRank;
    }

    /** The words of a question that go into the search: letters and digits only (nothing of the question reaches the query syntax). */
    static List<String> words(String question) {
        Set<String> out = new LinkedHashSet<>();
        Matcher m = WORD.matcher(question == null ? "" : question.toLowerCase(Locale.ROOT));
        while (m.find() && out.size() < MAX_WORDS) if (m.group().length() > 1) out.add(m.group());
        return List.copyOf(out);
    }

    /** The notes that match the question, best first; category null = all categories. */
    @Transactional(readOnly = true)
    public List<Note> search(String question, String category, int limit) {
        List<String> words = words(question);
        if (words.isEmpty()) return List.of();
        String query = words.stream().map(w -> w + ":AB").collect(java.util.stream.Collectors.joining(" | "));
        String cat = category == null || category.isBlank() ? null : category;
        int max = Math.max(1, Math.min(limit, MAX_LIMIT));
        List<Hit> hits = jdbc.query("""
                        SELECT id, title, category, school, text, source, links, rank FROM (
                            SELECT id, title, category, school, text, source, links, ts_rank(search, q) AS rank
                            FROM notes, to_tsquery('hungarian', ?) q
                            WHERE search @@ q AND (?::text IS NULL OR category = ?)) hits
                        WHERE rank >= ?
                        ORDER BY rank DESC, id
                        LIMIT ?""",
                (rs, i) -> new Hit(new Note(rs.getString("id"), rs.getString("title"), rs.getString("category"), rs.getString("school"),
                        rs.getString("text"), rs.getString("source"), rs.getDouble("rank"), List.of()), linkIds(rs.getString("links"))),
                query, cat, cat, minRank, max);
        Map<String, String> titles = titles(hits.stream().flatMap(h -> h.linkIds().stream()).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new)));
        List<Note> out = new ArrayList<>();
        for (Hit h : hits) {
            List<Link> links = h.linkIds().stream().filter(titles::containsKey).map(id -> new Link(id, titles.get(id))).toList();
            Note n = h.note();
            out.add(new Note(n.id(), n.title(), n.category(), n.school(), n.text(), n.source(), n.rank(), links));
        }
        return out;
    }

    private record Hit(Note note, List<String> linkIds) {}

    private static List<String> linkIds(String links) {
        return links == null || links.isBlank() ? List.of() : java.util.Arrays.stream(links.split(",")).map(String::trim).filter(x -> !x.isEmpty()).toList();
    }

    /** Id -> title of the notes that exist; a link to a note that is gone is left out. */
    private Map<String, String> titles(Set<String> ids) {
        Map<String, String> out = new LinkedHashMap<>();
        if (ids.isEmpty()) return out;
        String marks = String.join(", ", java.util.Collections.nCopies(ids.size(), "?"));
        jdbc.query("SELECT id, title FROM notes WHERE id IN (" + marks + ")", rs -> { out.put(rs.getString("id"), rs.getString("title")); }, ids.toArray());
        return out;
    }
}
