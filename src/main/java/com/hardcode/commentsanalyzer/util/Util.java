package com.hardcode.commentsanalyzer.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hardcode.commentsanalyzer.model.ProductComment;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
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

    public Map<String, List<ProductComment>> readProductsCommentsFromCSVFile() {
        Map<String, List<ProductComment>> productsCommentsMap = new TreeMap<>();

        try {
            CSVParser parser = CSVParser.parse(Paths.get(FILENAME),
                    StandardCharsets.UTF_8,
                    CSVFormat.DEFAULT);

            for (CSVRecord record : parser) {
                String model = record.get(0);
                ProductComment comment = new ProductComment(record.get(1),
                        createDateClassFromStringDate(record.get(2).split(" ")[0]),
                        Integer.parseInt(record.get(3)));

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

        return FORMATTER_GET.format(newDate) + " в " + TIME_FORMATTER_GET.format(newDate);
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

    @Cacheable(value = "initCache", key = "#param")
    public Map<String, List<Pair<String, Integer>>> init(Map<String, List<ProductComment>> productsCommentsMap, Map<String, List<Pair<String, Integer>>> processedComments,
                                                         String source, String target, int param) {
        for (Map.Entry<String, List<ProductComment>> entry : productsCommentsMap.entrySet()) {

            StringBuilder commentsForTranslate = new StringBuilder();
            entry.getValue().forEach(comment -> commentsForTranslate.append(comment.getDescription())
                    .append("\n"));
            commentsForTranslate.deleteCharAt(commentsForTranslate.length() - 1);

            List<Pair<String, Integer>> commentsPairs = new ArrayList<>();

            try {
                for (String str : translate(commentsForTranslate.toString(), source, target)) {
                    commentsPairs.add(Pair.of(str
                                    .replaceAll("[^\\p{L}\\s]+", "")
                                    .replaceAll("\\s+", " ")
                                    .trim()
                                    .toLowerCase(),
                            -1));
                }
            }
            catch (Exception e) {
                log.info(e.getMessage());
            }

            processedComments.put(entry.getKey(), commentsPairs);
        }

        return processedComments;
    }

    public Map<String, List<Pair<String, Integer>>> result(Map<String, List<ProductComment>> productsCommentsMap, Map<String, List<Pair<String, Integer>>> processedComments) {
        for (Map.Entry<String, List<Pair<String, Integer>>> entry : processedComments.entrySet()) {
            List<ProductComment> productComments = productsCommentsMap.get(entry.getKey());
            for (int i = 0; i < entry.getValue().size(); i++) {
                int value = entry.getValue().get(i).getValue();
                entry.getValue().set(i, Pair.of(productComments.get(i).getDescription(), value));
            }
        }

        return processedComments;
    }

    public void startScript() {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(GET_DATA_SCRIPT);
            processBuilder.directory(new java.io.File("."));
            Process process = processBuilder.start();

            log.info("Script finished with code: {}", process.waitFor());
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}
