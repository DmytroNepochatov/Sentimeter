package com.hardcode.sentimeter.service;

import com.hardcode.sentimeter.model.ProductComment;
import com.hardcode.sentimeter.model.dto.MyCustomEvent;
import opennlp.tools.doccat.DoccatModel;
import opennlp.tools.doccat.DocumentCategorizerME;
import opennlp.tools.doccat.DocumentSampleStream;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import java.io.*;
import java.util.List;
import java.util.Map;

@Service
public class MaxEntAlgorithm {
    private static final String MODEL_PATH = "maxentmodel/model.bin";
    private static final String FILENAME = "train_data.txt";
    private static final String CHARSET = "UTF-8";
    private boolean isTrained = false;
    private DoccatModel model;

    @Value("${train-data.language}")
    private String target;

    @Value("${maxent.variable.cutoff}")
    private int cutoff;

    @Value("${maxent.variable.training-iterations}")
    private int trainingIterations;

    @Async
    @EventListener
    public void trainModel(MyCustomEvent event) {
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
                isTrained = true;
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
        else {
            isTrained = true;
        }
    }

    public void classifyComments(Map<String, List<ProductComment>> productsCommentsMap) {
        DocumentCategorizerME myCategorizer = new DocumentCategorizerME(model);

        for (Map.Entry<String, List<ProductComment>> entry : productsCommentsMap.entrySet()) {
            for (int i = 0; i < entry.getValue().size(); i++) {
                if (!entry.getValue().get(i).getDescriptionTranslated().isEmpty()
                        && entry.getValue().get(i).getTonalityMaxEnt() == -1) {
                    double[] outcomes = myCategorizer.categorize(entry.getValue().get(i).getDescriptionTranslated().split(" "));
                    String category = myCategorizer.getBestCategory(outcomes);

                    if (category.equalsIgnoreCase("1")) {
                        entry.getValue().get(i).setTonalityMaxEnt(1);
                    }
                    else {
                        entry.getValue().get(i).setTonalityMaxEnt(0);
                    }
                }
            }
        }
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

    public boolean isModelReady() {
        return model != null && isTrained;
    }
}