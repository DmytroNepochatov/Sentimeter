package com.hardcode.commentsanalyzer.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hardcode.commentsanalyzer.model.ProductComment;
import com.hardcode.commentsanalyzer.model.SourceCommentsStorage;
import com.hardcode.commentsanalyzer.model.dto.AboutProduct;
import com.hardcode.commentsanalyzer.model.dto.ProductForMainView;
import com.hardcode.commentsanalyzer.service.ManualUpdater;
import com.hardcode.commentsanalyzer.service.MaxEntAlgorithm;
import com.hardcode.commentsanalyzer.service.NaiveBayesAlgorithm;
import com.hardcode.commentsanalyzer.service.SearchService;
import com.hardcode.commentsanalyzer.util.Util;
import lombok.SneakyThrows;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@Controller
public class ApplicationController {
    private static final String DATE_PATTERN = "dd.M.yyyy";
    private SourceCommentsStorage sourceCommentsStorage;
    private MaxEntAlgorithm maxEntAlgorithm;
    private NaiveBayesAlgorithm naiveBayesAlgorithm;
    private SearchService searchService;
    private ManualUpdater manualUpdater;
    private Util util;

    @Value("${train-data.language}")
    private String target;

    @Value("${input-comments.language}")
    private String source;

    @Autowired
    public ApplicationController(SourceCommentsStorage sourceCommentsStorage,
                                 MaxEntAlgorithm maxEntAlgorithm,
                                 NaiveBayesAlgorithm naiveBayesAlgorithm,
                                 SearchService searchService,
                                 ManualUpdater manualUpdater,
                                 Util util) {
        this.sourceCommentsStorage = sourceCommentsStorage;
        this.maxEntAlgorithm = maxEntAlgorithm;
        this.naiveBayesAlgorithm = naiveBayesAlgorithm;
        this.searchService = searchService;
        this.manualUpdater = manualUpdater;
        this.util = util;
    }

    @GetMapping("/")
    public String getMainPage(Model model, @RequestParam(value = "page", defaultValue = "1") int page) {
        return forMainPageAndSearch(model,
                searchService.getPage(sourceCommentsStorage.getProductsCommentsMap(), page),
                true,
                "Товари не знайдено, щось пішло не так", page);
    }

    @GetMapping("/search")
    public String getSearch(Model model, @RequestParam(value = "searchKeyword") String searchKeyword) {
        if (searchKeyword.isBlank()) {
            return "redirect:/?page=1";
        }
        else {
            return forMainPageAndSearch(model,
                    searchService.getBySearchString(sourceCommentsStorage.getProductsCommentsMap(), searchKeyword),
                    false,
                    "Товари не знайдено", -1);
        }
    }

    @GetMapping("/update")
    public String updateData() {
        manualUpdater.updateSourceCommentsStorage();
        return "redirect:/?page=1";
    }

    @GetMapping("/fullinfo")
    @SneakyThrows
    public String getFullInfo(Model model, @RequestParam(value = "productFullInfo") String productFullInfo) {
        AboutProduct product = new AboutProduct("", 0.0f, 0, "", "", new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        product.setProductName(productFullInfo);
        product.setTotalQuantityComments(sourceCommentsStorage.getProductsCommentsMap().get(productFullInfo).size());

        Map<Date, Integer> chart = new TreeMap<>();
        AtomicInteger rating = new AtomicInteger(0);
        sourceCommentsStorage.getProductsCommentsMap().get(productFullInfo).forEach(productComment -> {
            Date commentDate = productComment.getCommentCreated();
            chart.put(commentDate, chart.getOrDefault(commentDate, 0) + 1);

            rating.addAndGet(productComment.getGrade());
        });

        product.setRatingFromClients(Math.round(((float) rating.get() / product.getTotalQuantityComments()) * 10) / 10.0f);

        Map<String, List<ProductComment>> productMap = new TreeMap<>();
        productMap.put(productFullInfo, sourceCommentsStorage.getProductsCommentsMap().get(productFullInfo));
        Map<String, List<Pair<String, Integer>>> commentsAfterInitMaxEnt = util.init(productMap, new TreeMap<>(), source, target, Math.abs(productFullInfo.hashCode()));
        Map<String, List<Pair<String, Integer>>> commentsAfterInitNaiveBayes = new TreeMap<>();
        for (Map.Entry<String, List<Pair<String, Integer>>> entry : commentsAfterInitMaxEnt.entrySet()) {
            List<Pair<String, Integer>> copiedList = new ArrayList<>(entry.getValue());
            commentsAfterInitNaiveBayes.put(entry.getKey(), copiedList);
        }

        Map<String, List<Pair<String, Integer>>> commentsAfterAnalysisMaxEnt = maxEntAlgorithm.classifyComments(productMap, commentsAfterInitMaxEnt, Math.abs(productFullInfo.hashCode()));
        Map<String, List<Pair<String, Integer>>> commentsAfterAnalysisNaiveBayes = naiveBayesAlgorithm.classifyComments(productMap, commentsAfterInitNaiveBayes, Math.abs(productFullInfo.hashCode()));
        int posNumberMaxEnt = countPosComments(commentsAfterAnalysisMaxEnt.get(productFullInfo), product.getPosCommentsMaxEnt(), product.getNegCommentsMaxEnt());
        int posNumberNaiveBayes = countPosComments(commentsAfterAnalysisNaiveBayes.get(productFullInfo), product.getPosCommentsNaiveBayes(), product.getNegCommentsNaiveBayes());

        int posPercentageMaxEnt = (int) ((float) posNumberMaxEnt / product.getTotalQuantityComments() * 100);
        int posPercentageNaiveBayes = (int) ((float) posNumberNaiveBayes / product.getTotalQuantityComments() * 100);
        product.setPositivePercentageMaxEnt(posPercentageMaxEnt + "% позитивних відгуків згідно алгоритму максимальної ентропії");
        product.setPositivePercentageNaiveBayes(posPercentageNaiveBayes + "% позитивних відгуків згідно з наївним алгоритмом Байєса");

        Map<Date, Integer> posNumberDate = new TreeMap<>();
        Map<Date, Integer> negNumberDate = new TreeMap<>();

        commentsAfterAnalysisMaxEnt.get(productFullInfo).forEach(pair -> {
            productMap.get(productFullInfo).forEach(productCommentWithDate -> {
                if (pair.getKey().equals(productCommentWithDate.getDescription())) {
                    Date date = productCommentWithDate.getCommentCreated();

                    if (pair.getValue() == 1) {
                        posNumberDate.put(date, posNumberDate.getOrDefault(date, 0) + 1);
                    }
                    else {
                        negNumberDate.put(date, negNumberDate.getOrDefault(date, 0) + 1);
                    }
                }
            });
        });

        model.addAttribute("posNumberMaxEnt", posNumberMaxEnt);
        model.addAttribute("negNumberMaxEnt", product.getTotalQuantityComments() - posNumberMaxEnt);
        model.addAttribute("posNumberNaiveBayes", posNumberNaiveBayes);
        model.addAttribute("negNumberNaiveBayes", product.getTotalQuantityComments() - posNumberNaiveBayes);
        model.addAttribute("product", product);
        model.addAttribute("lastUpdated", util.createStringDateFromClassDate(sourceCommentsStorage.getLastUpdated()));
        model.addAttribute("commentsData", convertMapToJson(chart));
        model.addAttribute("posNumberDate", convertMapToJson(posNumberDate));
        model.addAttribute("negNumberDate", convertMapToJson(negNumberDate));
        return "productpage";
    }

    private String forMainPageAndSearch(Model model, Map<String, List<ProductComment>> productsCommentsMap, boolean flag, String errorMsg, int pageCache) {
        List<ProductForMainView> products = new ArrayList<>();

        Map<String, List<Pair<String, Integer>>> commentsAfterInit = util.init(productsCommentsMap, new TreeMap<>(), source, target, pageCache);
        Map<String, List<Pair<String, Integer>>> commentsAfterAnalysis = maxEntAlgorithm.classifyComments(productsCommentsMap, commentsAfterInit, pageCache);

        for (Map.Entry<String, List<Pair<String, Integer>>> entry : commentsAfterAnalysis.entrySet()) {
            List<ProductComment> productComments = sourceCommentsStorage.getProductsCommentsMap().get(entry.getKey());
            AtomicInteger rating = new AtomicInteger(0);
            productComments.forEach(p -> rating.addAndGet(p.getGrade()));

            AtomicInteger posNumber = new AtomicInteger(0);
            entry.getValue().forEach(pair -> {
                if (pair.getValue() == 1) {
                    posNumber.incrementAndGet();
                }
            });

            int positivePercentage = (int) ((float) posNumber.get() / productComments.size() * 100);
            ProductForMainView product = new ProductForMainView(entry.getKey(),
                    Math.round(((float) rating.get() / productComments.size()) * 10) / 10.0f,
                    productComments.size(),
                    posNumber.get(),
                    productComments.size() - posNumber.get(),
                    positivePercentage + "% позитивних відгуків");
            products.add(product);
        }

        List<Integer> pages = new ArrayList<>();
        for (int i = 0; i < searchService
                .getPagesCount(sourceCommentsStorage.getProductsCommentsMap().size()); i++) {
            pages.add(i + 1);
        }

        model.addAttribute("flag", flag);
        model.addAttribute("errorMsg", errorMsg);
        model.addAttribute("products", products);
        model.addAttribute("lastUpdated", util.createStringDateFromClassDate(sourceCommentsStorage.getLastUpdated()));
        model.addAttribute("pages", pages);
        return "mainpage";
    }

    private String convertMapToJson(Map<Date, Integer> commentsByDate) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        SimpleDateFormat dateFormat = new SimpleDateFormat(DATE_PATTERN);

        Map<String, Integer> stringDateMap = new LinkedHashMap<>();
        for (Map.Entry<Date, Integer> entry : commentsByDate.entrySet()) {
            stringDateMap.put(dateFormat.format(entry.getKey()), entry.getValue());
        }

        return mapper.writeValueAsString(stringDateMap);
    }

    private int countPosComments(List<Pair<String, Integer>> commentsAfterAlgorithm, List<String> posComments, List<String> negComments) {
        AtomicInteger posCommentsNumber = new AtomicInteger(0);

        commentsAfterAlgorithm.forEach(pair -> {
            if (pair.getValue() == 1) {
                posCommentsNumber.incrementAndGet();

                posComments.add(pair.getKey());
            }
            else {
                negComments.add(pair.getKey());
            }
        });

        return posCommentsNumber.get();
    }
}
