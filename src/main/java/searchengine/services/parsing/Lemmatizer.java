package searchengine.services.parsing;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
@Component
@Slf4j
public class Lemmatizer {

    private final LuceneMorphology luceneMorphology = new RussianLuceneMorphology();
    private static final String[] particlesNames = new String[]{"МЕЖД", "ПРЕДЛ", "СОЮЗ"};

    public Lemmatizer() throws IOException {
    }

    @SneakyThrows
    public Map<String, Integer> getLemmas(String text) {
        Map<String, Integer> lemmas = new HashMap<>();
        List<String> words = splitText(text);

        for (String word : words) {
            try {
                if (word.isBlank()) continue;
                if (isNotWord(word)) continue;

                List<String> normalForms = luceneMorphology.getNormalForms(word.toLowerCase());
                if (normalForms.isEmpty()) continue;

                String normalWord = normalForms.get(0);

                if (lemmas.containsKey(normalWord)) lemmas.put(normalWord, lemmas.get(normalWord) + 1);
                else lemmas.put(normalWord, 1);
            } catch (Exception e) {
                log.info("Не удается получить лемму слова {}", word);
            }
        }
        return lemmas;
    }

    private synchronized List<String> splitText(String text) {
        String[] words = text.toLowerCase()
                .replaceAll("[^А-я\\s]", "")
                .trim()
                .split("\\s+");
        return new ArrayList<>(List.of(words));
    }

    private synchronized boolean isNotWord(String word) {
        List<String> wordInfo = luceneMorphology.getMorphInfo(word);
        for (String property : particlesNames) {
            if (wordInfo.toString().toUpperCase().contains(property)) {
                return luceneMorphology.checkString(word);
            }
        }
        return false;
    }
}
