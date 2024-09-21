package com.hardcode.commentsanalyzer.service;

import com.hardcode.commentsanalyzer.model.ProductComment;
import com.hardcode.commentsanalyzer.util.Util;
import opennlp.tools.doccat.DoccatModel;
import opennlp.tools.doccat.DocumentCategorizerME;
import opennlp.tools.doccat.DocumentSampleStream;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.annotation.PostConstruct;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

@Service
public class MaxEntAlgorithm {
    private static final int CUTOFF = 2;
    private static final int TRAINING_ITERATIONS = 2000;
    private static final String FILENAME = "train_data.txt";
    private static final String CHARSET = "UTF-8";
    private DoccatModel model;

    @Value("${train-data.language}")
    private String target;

    @PostConstruct
    protected void trainModel() {
        InputStream dataIn = null;

        try {
            dataIn = new FileInputStream(FILENAME);
            ObjectStream lineStream = new PlainTextByLineStream(dataIn, CHARSET);
            ObjectStream sampleStream = new DocumentSampleStream(lineStream);

            model = DocumentCategorizerME.train(target, sampleStream, CUTOFF,
                    TRAINING_ITERATIONS);
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

    public Map<String, List<Pair<String, Integer>>> classifyComments(Map<String, List<ProductComment>> productsCommentsMap,
                                                                     Map<String, List<Pair<String, Integer>>> processedComments) {
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

        return Util.result(productsCommentsMap, processedComments);
    }
}