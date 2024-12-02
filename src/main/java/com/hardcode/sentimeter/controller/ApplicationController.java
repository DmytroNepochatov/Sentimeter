package com.hardcode.sentimeter.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hardcode.sentimeter.model.ProductComment;
import com.hardcode.sentimeter.model.SourceCommentsStorage;
import com.hardcode.sentimeter.model.dto.AboutProduct;
import com.hardcode.sentimeter.model.dto.MyCustomEvent;
import com.hardcode.sentimeter.model.dto.ProductCommentForFullInfo;
import com.hardcode.sentimeter.model.dto.ProductForMainView;
import com.hardcode.sentimeter.service.ManualUpdater;
import com.hardcode.sentimeter.service.MaxEntAlgorithm;
import com.hardcode.sentimeter.service.NaiveBayesAlgorithm;
import com.hardcode.sentimeter.service.SearchService;
import com.hardcode.sentimeter.util.Util;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Controller
public class ApplicationController {
    private static final String DATE_PATTERN = "dd.M.yyyy";
    private static final String FILENAME_TRAIN_DATA = "train_data.txt";
    private static final String FILENAME_DATA_SCRIPT = "get_data_script.sh";
    private final ApplicationEventPublisher eventPublisher;
    private SourceCommentsStorage sourceCommentsStorage;
    private MaxEntAlgorithm maxEntAlgorithm;
    private NaiveBayesAlgorithm naiveBayesAlgorithm;
    private SearchService searchService;
    private ManualUpdater manualUpdater;
    private Util util;
    private boolean startEventFlag = false;

    @Autowired
    public ApplicationController(SourceCommentsStorage sourceCommentsStorage,
                                 MaxEntAlgorithm maxEntAlgorithm,
                                 NaiveBayesAlgorithm naiveBayesAlgorithm,
                                 SearchService searchService,
                                 ManualUpdater manualUpdater,
                                 Util util, ApplicationEventPublisher eventPublisher) {
        this.sourceCommentsStorage = sourceCommentsStorage;
        this.maxEntAlgorithm = maxEntAlgorithm;
        this.naiveBayesAlgorithm = naiveBayesAlgorithm;
        this.searchService = searchService;
        this.manualUpdater = manualUpdater;
        this.util = util;
        this.eventPublisher = eventPublisher;
    }

    @GetMapping("/")
    public String getMainPage(Model model, @RequestParam(value = "page", defaultValue = "1") int page) {
        Map<String, List<ProductComment>> productMap = null;

        if (sourceCommentsStorage.isDataReady() &&
                maxEntAlgorithm.isModelReady() &&
                naiveBayesAlgorithm.isModelReady()) {
            productMap = searchService.getPage(sourceCommentsStorage.getProductsCommentsMap(), page);
        }

        return forMainPageAndSearch(model, productMap, true, "No products found, something went wrong");
    }

    @GetMapping("/search")
    public String getSearch(Model model, @RequestParam(value = "searchKeyword") String searchKeyword) {
        if (searchKeyword.isBlank()) {
            return "redirect:/?page=1";
        }
        else {
            Map<String, List<ProductComment>> productMap = null;

            if (sourceCommentsStorage.isDataReady() &&
                    maxEntAlgorithm.isModelReady() &&
                    naiveBayesAlgorithm.isModelReady()) {
                productMap = searchService.getBySearchString(sourceCommentsStorage.getProductsCommentsMap(), searchKeyword);
            }

            return forMainPageAndSearch(model, productMap, false, "No products found");
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

        AtomicInteger quantity = new AtomicInteger(0);
        sourceCommentsStorage.getProductsCommentsMap().get(productFullInfo).forEach(comment -> {
            if (!comment.getDescriptionTranslated().isEmpty()) {
                quantity.incrementAndGet();
            }
        });
        product.setTotalQuantityComments(quantity.get());

        Map<Date, Integer> chart = new TreeMap<>();
        AtomicInteger rating = new AtomicInteger(0);
        sourceCommentsStorage.getProductsCommentsMap().get(productFullInfo).forEach(productComment -> {
            Date commentDate = productComment.getCommentCreated();
            chart.put(commentDate, chart.getOrDefault(commentDate, 0) + 1);

            rating.addAndGet(productComment.getGrade());
        });

        product.setRatingFromClients(Math.round(((float) rating.get() / sourceCommentsStorage.getProductsCommentsMap().get(productFullInfo).size()) * 10) / 10.0f);

        Map<String, List<ProductComment>> productMap = new TreeMap<>();
        productMap.put(productFullInfo, sourceCommentsStorage.getProductsCommentsMap().get(productFullInfo));

        maxEntAlgorithm.classifyComments(productMap);
        naiveBayesAlgorithm.classifyComments(productMap);
        int posNumberMaxEnt = countPosComments(productMap.get(productFullInfo), product.getPosCommentsMaxEnt(), product.getNegCommentsMaxEnt(), true);
        int posNumberNaiveBayes = countPosComments(productMap.get(productFullInfo), product.getPosCommentsNaiveBayes(), product.getNegCommentsNaiveBayes(), false);

        int posPercentageMaxEnt = (int) ((float) posNumberMaxEnt / product.getTotalQuantityComments() * 100);
        int posPercentageNaiveBayes = (int) ((float) posNumberNaiveBayes / product.getTotalQuantityComments() * 100);
        product.setPositivePercentageMaxEnt(posPercentageMaxEnt + "% positive reviews according to the maximum entropy algorithm");
        product.setPositivePercentageNaiveBayes(posPercentageNaiveBayes + "% positive reviews according to the naive Bayes algorithm");

        Map<Date, Integer> posNumberDateMaxEnt = new TreeMap<>();
        Map<Date, Integer> negNumberDateMaxEnt = new TreeMap<>();
        prepareMapsForCharts(productMap, productFullInfo, posNumberDateMaxEnt, negNumberDateMaxEnt, true);

        Map<Date, Integer> posNumberDateNaiveBayes = new TreeMap<>();
        Map<Date, Integer> negNumberDateNaiveBayes = new TreeMap<>();
        prepareMapsForCharts(productMap, productFullInfo, posNumberDateNaiveBayes, negNumberDateNaiveBayes, false);

        product.getPosCommentsMaxEnt().sort(null);
        product.getNegCommentsMaxEnt().sort(null);
        product.getPosCommentsNaiveBayes().sort(null);
        product.getNegCommentsNaiveBayes().sort(null);

        model.addAttribute("posNumberMaxEnt", posNumberMaxEnt);
        model.addAttribute("negNumberMaxEnt", product.getTotalQuantityComments() - posNumberMaxEnt);
        model.addAttribute("posNumberNaiveBayes", posNumberNaiveBayes);
        model.addAttribute("negNumberNaiveBayes", product.getTotalQuantityComments() - posNumberNaiveBayes);
        model.addAttribute("product", product);
        model.addAttribute("lastUpdated", util.createStringDateFromClassDate(sourceCommentsStorage.getLastUpdated()));
        model.addAttribute("commentsData", convertMapToJson(chart));
        model.addAttribute("posNumberDateMaxEnt", convertMapToJson(posNumberDateMaxEnt));
        model.addAttribute("negNumberDateMaxEnt", convertMapToJson(negNumberDateMaxEnt));
        model.addAttribute("posNumberDateNaiveBayes", convertMapToJson(posNumberDateNaiveBayes));
        model.addAttribute("negNumberDateNaiveBayes", convertMapToJson(negNumberDateNaiveBayes));
        model.addAttribute("posCommentsMaxEntList", product.getPosCommentsMaxEnt());
        model.addAttribute("negCommentsMaxEntList", product.getNegCommentsMaxEnt());
        model.addAttribute("posCommentsNaiveBayesList", product.getPosCommentsNaiveBayes());
        model.addAttribute("negCommentsNaiveBayesList", product.getNegCommentsNaiveBayes());
        model.addAttribute("flagInit", false);
        return "productpage";
    }

    @PostMapping("/upload")
    public String uploadFiles(Model model, @RequestParam("files") MultipartFile[] files) {
        boolean trainFile = false;
        boolean dataScript = false;

        for (MultipartFile file : files) {
            try {
                File destFile = new File(file.getOriginalFilename());
                file.transferTo(destFile.toPath());
                log.info("File uploaded successfully: {}", destFile.getAbsolutePath());

                if (file.getOriginalFilename().equals(FILENAME_TRAIN_DATA)) {
                    trainFile = true;
                }
                else if (file.getOriginalFilename().equals(FILENAME_DATA_SCRIPT)) {
                    dataScript = true;
                }
            }
            catch (IOException e) {
                log.info("Error uploading file: {}, : {}", file.getOriginalFilename(), e.getMessage());
            }
        }

        if ((trainFile && dataScript) ||
                (util.isFileAvailable(FILENAME_TRAIN_DATA) && util.isFileAvailable(FILENAME_DATA_SCRIPT))) {
            startEventFlag = true;
            sourceCommentsStorage.setReadyFlag(false);
            eventPublisher.publishEvent(new MyCustomEvent(this));
            return "redirect:/?page=1";
        }
        else if (trainFile) {
            model.addAttribute("errorMsg", "The script for receiving data is not loaded");
        }
        else if (dataScript) {
            model.addAttribute("errorMsg", "Training data file not loaded");
        }

        model.addAttribute("flagInit", true);
        model.addAttribute("flag", false);
        model.addAttribute("products", new ArrayList<>());
        model.addAttribute("lastUpdated", "");

        return "mainpage";
    }

    private String forMainPageAndSearch(Model model, Map<String, List<ProductComment>> productsCommentsMap, boolean flag, String errorMsg) {
        if (sourceCommentsStorage.isDataReady() &&
                maxEntAlgorithm.isModelReady() &&
                naiveBayesAlgorithm.isModelReady()) {
            List<ProductForMainView> products = new ArrayList<>();

            maxEntAlgorithm.classifyComments(productsCommentsMap);

            for (Map.Entry<String, List<ProductComment>> entry : productsCommentsMap.entrySet()) {
                AtomicInteger rating = new AtomicInteger(0);
                entry.getValue().forEach(c -> rating.addAndGet(c.getGrade()));

                AtomicInteger posNumber = new AtomicInteger(0);
                AtomicInteger quantity = new AtomicInteger(0);
                entry.getValue().forEach(c -> {
                    if (c.getTonalityMaxEnt() == 1) {
                        posNumber.incrementAndGet();
                    }
                    if (!c.getDescriptionTranslated().isEmpty()) {
                        quantity.incrementAndGet();
                    }
                });

                int positivePercentage = (int) ((float) posNumber.get() / quantity.get() * 100);
                ProductForMainView product = new ProductForMainView(entry.getKey(),
                        Math.round(((float) rating.get() / entry.getValue().size()) * 10) / 10.0f,
                        quantity.get(),
                        posNumber.get(),
                        quantity.get() - posNumber.get(),
                        positivePercentage + "% positive reviews");
                products.add(product);
            }

            List<Integer> pages = new ArrayList<>();
            for (int i = 0; i < searchService
                    .getPagesCount(sourceCommentsStorage.getProductsCommentsMap().size()); i++) {
                pages.add(i + 1);
            }

            model.addAttribute("flagInit", false);
            model.addAttribute("flag", flag);
            model.addAttribute("errorMsg", errorMsg);
            model.addAttribute("products", products);
            model.addAttribute("lastUpdated", util.createStringDateFromClassDate(sourceCommentsStorage.getLastUpdated()));
            model.addAttribute("pages", pages);
        }
        else {
            if (!util.isFileAvailable(FILENAME_TRAIN_DATA) || !util.isFileAvailable(FILENAME_DATA_SCRIPT)) {
                model.addAttribute("errorMsg", "Please upload the training data file and script to receive comments");
            }
            else {
                model.addAttribute("errorMsg", "Please wait, analysis is in progress");

                if (!startEventFlag) {
                    eventPublisher.publishEvent(new MyCustomEvent(this));
                }
            }

            model.addAttribute("flagInit", true);
            model.addAttribute("flag", false);
            model.addAttribute("products", new ArrayList<>());
            model.addAttribute("lastUpdated", "");
        }

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

    private int countPosComments(List<ProductComment> commentsAfterAlgorithm,
                                 List<ProductCommentForFullInfo> posComments,
                                 List<ProductCommentForFullInfo> negComments, boolean flag) {
        AtomicInteger posCommentsNumber = new AtomicInteger(0);

        commentsAfterAlgorithm.forEach(c -> {
            if (flag) {
                if (c.getTonalityMaxEnt() == 1) {
                    posCommentsNumber.incrementAndGet();
                    posComments.add(createProductCommentForFullInfo(c));
                }
                else if (c.getTonalityMaxEnt() == 0) {
                    negComments.add(createProductCommentForFullInfo(c));
                }
            }
            else {
                if (c.getTonalityNaiveBayes() == 1) {
                    posCommentsNumber.incrementAndGet();
                    posComments.add(createProductCommentForFullInfo(c));
                }
                else if (c.getTonalityNaiveBayes() == 0) {
                    negComments.add(createProductCommentForFullInfo(c));
                }
            }
        });

        return posCommentsNumber.get();
    }

    private ProductCommentForFullInfo createProductCommentForFullInfo(ProductComment c) {
        ProductCommentForFullInfo comment = new ProductCommentForFullInfo(c.getId(), c.getDescription(),
                c.getCommentCreated(), c.getGrade());

        if (!c.getDescriptionForUI().isEmpty()) {
            comment.setDescription(c.getDescriptionForUI() + "<br>" + "Original comment:<br>" + c.getDescription());
        }

        return comment;
    }

    private void prepareMapsForCharts(Map<String, List<ProductComment>> productMap, String productFullInfo,
                                      Map<Date, Integer> posNumberDate, Map<Date, Integer> negNumberDate, boolean flag) {

        productMap.get(productFullInfo).forEach(c -> {
            Date date = c.getCommentCreated();

            if (flag) {
                if (c.getTonalityMaxEnt() == 1) {
                    posNumberDate.put(date, posNumberDate.getOrDefault(date, 0) + 1);
                }
                else if (c.getTonalityMaxEnt() == 0) {
                    negNumberDate.put(date, negNumberDate.getOrDefault(date, 0) + 1);
                }
            }
            else {
                if (c.getTonalityNaiveBayes() == 1) {
                    posNumberDate.put(date, posNumberDate.getOrDefault(date, 0) + 1);
                }
                else if (c.getTonalityNaiveBayes() == 0) {
                    negNumberDate.put(date, negNumberDate.getOrDefault(date, 0) + 1);
                }
            }
        });
    }
}
