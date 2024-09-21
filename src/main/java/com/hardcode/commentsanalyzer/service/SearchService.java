package com.hardcode.commentsanalyzer.service;

import com.hardcode.commentsanalyzer.model.ProductComment;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class SearchService {
    private static final int PAGE_SIZE = 5;

    public Map<String, List<ProductComment>> getPage(Map<String, List<ProductComment>> map, int page) {
        List<Map.Entry<String, List<ProductComment>>> list = new ArrayList<>(map.entrySet());

        int fromIndex = (page - 1) * PAGE_SIZE;
        int toIndex = Math.min(fromIndex + PAGE_SIZE, list.size());

        if (fromIndex >= list.size() || fromIndex < 0) {
            return Collections.emptyMap();
        }

        Map<String, List<ProductComment>> resultMap = new TreeMap<>();
        list.subList(fromIndex, toIndex).forEach(entry -> resultMap.put(entry.getKey(), entry.getValue()));

        return resultMap;
    }

    public Map<String, List<ProductComment>> getBySearchString(Map<String, List<ProductComment>> map, String searchString) {
        Map<String, List<ProductComment>> resultMap = new TreeMap<>();
        List<Integer> listOccurrences = new ArrayList<>();

        for (int i = 0; i < map.size(); i++) {
            listOccurrences.add(0);
        }

        String[] searchStrArr = searchString.split(" ");
        for (int i = 0; i < searchStrArr.length; i++) {
            searchStrArr[i].trim();
        }

        int i = 0;
        for (Map.Entry<String, List<ProductComment>> entry : map.entrySet()) {
            for (int j = 0; j < searchStrArr.length; j++) {
                if (entry.getKey().contains(searchStrArr[j])) {
                    int value = listOccurrences.get(i);
                    listOccurrences.set(i, value + 1);
                }
            }
            i++;
        }

        int occurrenceValue = 0;
        if (searchStrArr.length == 1) {
            occurrenceValue = 1;
        }
        else if (searchStrArr.length > 2) {
            occurrenceValue = 3;
        }
        else {
            occurrenceValue = 2;
        }

        int iter = 0;
        for (Map.Entry<String, List<ProductComment>> entry : map.entrySet()) {
            if (listOccurrences.get(iter) >= occurrenceValue) {
                resultMap.put(entry.getKey(), entry.getValue());
            }

            iter++;
        }

        return resultMap;
    }

    public int getPagesCount(int phonesAvailable) {
        return (phonesAvailable % PAGE_SIZE == 0) ? phonesAvailable / PAGE_SIZE : (phonesAvailable / PAGE_SIZE) + 1;
    }
}
