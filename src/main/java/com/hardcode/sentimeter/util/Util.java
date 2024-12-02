package com.hardcode.sentimeter.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hardcode.sentimeter.model.ProductComment;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.tika.language.detect.LanguageDetector;
import org.apache.tika.language.detect.LanguageResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;

@Slf4j
@Component
public class Util {
    private static final String FILENAME = "output.csv";
    private static final String DATE_PATTERN_GET = "dd.M.yyyy";
    private static final String TIME_PATTERN_GET = "HH:mm:ss";
    private static final String DATE_PATTERN_SET = "yyyy-MM-dd";
    private static final SimpleDateFormat FORMATTER_GET = new SimpleDateFormat(DATE_PATTERN_GET, Locale.ENGLISH);
    private static final SimpleDateFormat TIME_FORMATTER_GET = new SimpleDateFormat(TIME_PATTERN_GET, Locale.ENGLISH);
    private static final SimpleDateFormat FORMATTER_SET = new SimpleDateFormat(DATE_PATTERN_SET, Locale.ENGLISH);
    private static final String URL = "http://translator:5000/translate";
    private static final String GET_DATA_SCRIPT = "./get_data_script.sh";

    @Value("${train-data.language}")
    private String target;

    @Value("${input-comments.language}")
    private String[] source;

    @Value("${ui.language.comments}")
    private String commentLang;

    public Map<String, List<ProductComment>> readProductsCommentsFromCSVFile() {
        startScript();
        Map<String, List<ProductComment>> productsCommentsMap = new TreeMap<>();

        try {
            CSVParser parser = CSVParser.parse(Paths.get(FILENAME),
                    StandardCharsets.UTF_8,
                    CSVFormat.DEFAULT);

            LanguageDetector detector = LanguageDetector.getDefaultLanguageDetector();
            detector.loadModels();

            for (CSVRecord record : parser) {
                String model = record.get(1);
                String description = record.get(2);

                LanguageResult result = detector.detect(description);
                String locale = result.getLanguage();

                ProductComment comment = new ProductComment(record.get(0), description, "",
                        createDateClassFromStringDate(record.get(3).split(" ")[0]),
                        Integer.parseInt(record.get(4)), "", locale, -1, -1);

                if (!productsCommentsMap.containsKey(model)) {
                    productsCommentsMap.put(model, new ArrayList<>());
                }

                productsCommentsMap.get(model).add(comment);
            }
        }
        catch (Exception e) {
            log.error(e.getMessage());
        }

        return productsCommentsMap;
    }

    public String createStringDateFromClassDate(Date date) {
        Instant instant = date.toInstant();
        ZonedDateTime dateTime = instant.atZone(ZoneId.systemDefault());
        ZonedDateTime newDateTime = dateTime.plus(Duration.ofHours(3));
        Date newDate = Date.from(newDateTime.toInstant());

        return FORMATTER_GET.format(newDate) + " at " + TIME_FORMATTER_GET.format(newDate);
    }

    public Date createDateClassFromStringDate(String date) throws ParseException {
        return FORMATTER_SET.parse(date);
    }

    public List<String> translate(String comments, String source, String target) throws JsonProcessingException {
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, String> requestBody = new HashMap<>();
        requestBody.put("q", comments);
        requestBody.put("source", source);
        requestBody.put("target", target);

        HttpEntity<Map<String, String>> request = new HttpEntity<>(requestBody, headers);
        ResponseEntity<String> response = restTemplate.exchange(
                URL,
                HttpMethod.POST,
                request,
                String.class
        );

        ObjectMapper mapper = new ObjectMapper();
        Map<String, String> result = mapper.readValue(response.getBody(), Map.class);
        String translatedText = result.get("translatedText");

        return Arrays.asList(translatedText.split("\n"));
    }

    public void init(Map<String, List<ProductComment>> productsCommentsMap) {
        log.info("Started translated product comments");

        for (Map.Entry<String, List<ProductComment>> entry : productsCommentsMap.entrySet()) {
            Map<String, List<ProductComment>> mapLocale = new TreeMap<>();

            for (int i = 0; i < source.length; i++) {
                mapLocale.put(source[i], new ArrayList<>());
            }

            entry.getValue().forEach(comment -> {
                if (mapLocale.containsKey(comment.getLocale())) {
                    mapLocale.get(comment.getLocale()).add(comment);
                }
            });

            for (Map.Entry<String, List<ProductComment>> localeEntry : mapLocale.entrySet()) {
                if (!localeEntry.getValue().isEmpty()) {
                    boolean allNonEmpty = localeEntry.getValue().stream().allMatch(c -> !c.getDescriptionTranslated().isEmpty());

                    if (!allNonEmpty) {
                        StringBuilder commentsForTranslate = new StringBuilder();
                        localeEntry.getValue().forEach(comment -> commentsForTranslate.append(comment.getDescription())
                                .append("\n"));
                        commentsForTranslate.deleteCharAt(commentsForTranslate.length() - 1);

                        try {
                            List<String> translatedComments = translate(commentsForTranslate.toString(), localeEntry.getKey(), target);
                            List<String> translatedCommentsForUI = Collections.emptyList();
                            boolean flag = false;

                            if (!localeEntry.getKey().equals(commentLang)) {
                                translatedCommentsForUI = translate(commentsForTranslate.toString(), localeEntry.getKey(), commentLang);
                                flag = true;
                            }

                            for (int i = 0; i < localeEntry.getValue().size(); i++) {
                                if (flag) {
                                    localeEntry.getValue().get(i).setDescriptionForUI(translatedCommentsForUI.get(i));
                                }

                                localeEntry.getValue().get(i).setDescriptionTranslated(translatedComments.get(i)
                                        .replaceAll("[^\\p{L}\\s]+", "")
                                        .replaceAll("\\s+", " ")
                                        .trim()
                                        .toLowerCase());
                            }
                        }
                        catch (Exception e) {
                            log.info(e.getMessage());
                        }
                    }
                }
            }
        }
    }

    private void startScript() {
        try {
            String scriptPath = GET_DATA_SCRIPT;
            ProcessBuilder chmodProcessBuilder = new ProcessBuilder("chmod", "+x", scriptPath);
            chmodProcessBuilder.directory(new java.io.File("."));
            Process chmodProcess = chmodProcessBuilder.start();

            int chmodExitCode = chmodProcess.waitFor();
            if (chmodExitCode != 0) {
                log.error("Failed to set executable permissions for script. Exit code: {}", chmodExitCode);
                return;
            }

            ProcessBuilder processBuilder = new ProcessBuilder(scriptPath);
            processBuilder.directory(new java.io.File("."));
            Process process = processBuilder.start();

            log.info("Script finished with code: {}", process.waitFor());
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public boolean isFileAvailable(String fileName) {
        return new File(fileName).exists();
    }
}
