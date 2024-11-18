package com.hardcode.commentsanalyzer.service;

import com.hardcode.commentsanalyzer.model.ProductComment;
import com.hardcode.commentsanalyzer.util.Util;
import opennlp.tools.doccat.DoccatModel;
import opennlp.tools.doccat.DocumentCategorizerME;
import opennlp.tools.doccat.DocumentSampleStream;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import javax.annotation.PostConstruct;
import java.io.*;
import java.util.List;
import java.util.Map;

@Service
public class MaxEntAlgorithm {
    private static final String MODEL_PATH = "maxentmodel/model.bin";
    private static final String FILENAME = "train_data.txt";
    private static final String CHARSET = "UTF-8";
    private DoccatModel model;
    private Util util;

    @Autowired
    public MaxEntAlgorithm(Util util) {
        this.util = util;
    }

    @Value("${train-data.language}")
    private String target;

    @Value("${maxent.variable.cutoff}")
    private int cutoff;

    @Value("${maxent.variable.training-iterations}")
    private int trainingIterations;

    @PostConstruct
    protected void trainModel() {
        model = loadModel();

        if (model == null) {
            InputStream dataIn = null;

            try {
                dataIn = new FileInputStream(FILENAME);
                ObjectStream lineStream = new PlainTextByLineStream(dataIn, CHARSET);
                ObjectStream sampleStream = new DocumentSampleStream(lineStream);

                model = DocumentCategorizerME.train(target, sampleStream, cutoff,
                        trainingIterations);

                saveModel(model);
            }
            catch (IOException e) {
                e.printStackTrace();
            }
            finally {
                if (dataIn != null) {
                    try {
                        dataIn.close();
                    }
                    catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    @Cacheable(value = "maxEntCache", key = "#param", condition = "#param > 0")
    public Map<String, List<Pair<String, Integer>>> classifyComments(Map<String, List<ProductComment>> productsCommentsMap,
                                                                     Map<String, List<Pair<String, Integer>>> processedComments, int param) {
        DocumentCategorizerME myCategorizer = new DocumentCategorizerME(model);

        for (Map.Entry<String, List<Pair<String, Integer>>> entry : processedComments.entrySet()) {
            for (int i = 0; i < entry.getValue().size(); i++) {
                String text = entry.getValue().get(i).getKey();
                double[] outcomes = myCategorizer.categorize(entry.getValue().get(i).getKey().split(" "));
                String category = myCategorizer.getBestCategory(outcomes);

                if (category.equalsIgnoreCase("1")) {
                    entry.getValue().set(i, Pair.of(text, 1));
                }
                else {
                    entry.getValue().set(i, Pair.of(text, 0));
                }
            }
        }

        return util.result(productsCommentsMap, processedComments);
    }

    private void saveModel(DoccatModel model) {
        try {
            File directory = new File("maxentmodel");
            if (!directory.exists()) {
                directory.mkdirs();
            }

            try (FileOutputStream outputStream = new FileOutputStream(MODEL_PATH)) {
                model.serialize(outputStream);
            }
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    private DoccatModel loadModel() {
        try (FileInputStream inputStream = new FileInputStream(MODEL_PATH)) {
            return new DoccatModel(inputStream);
        }
        catch (IOException e) {
            return null;
        }
    }
}