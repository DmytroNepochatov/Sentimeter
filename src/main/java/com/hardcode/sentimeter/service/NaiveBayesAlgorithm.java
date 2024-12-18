package com.hardcode.sentimeter.service;

import com.google.common.collect.ConcurrentHashMultiset;
import com.google.common.collect.Multiset;
import com.hardcode.sentimeter.model.ProductComment;
import com.hardcode.sentimeter.model.dto.MyCustomEvent;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.SequenceFile;
import org.apache.hadoop.io.Text;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.util.Version;
import org.apache.mahout.classifier.naivebayes.NaiveBayesModel;
import org.apache.mahout.classifier.naivebayes.StandardNaiveBayesClassifier;
import org.apache.mahout.classifier.naivebayes.training.TrainNaiveBayesJob;
import org.apache.mahout.common.iterator.sequencefile.SequenceFileIterable;
import org.apache.mahout.math.RandomAccessSparseVector;
import org.apache.mahout.math.Vector;
import org.apache.mahout.vectorizer.SparseVectorsFromSequenceFiles;
import org.apache.mahout.vectorizer.TFIDF;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.StringReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class NaiveBayesAlgorithm {
    private static final String SEQUENCE_FILE_PATH = "input/comments-seq";
    private static final String LABEL_INDEX_PATH = "input/labelindex";
    private static final String MODEL_PATH = "input/model";
    private static final String VECTORS_PATH = "input/comments-vectors";
    private static final String DICTIONARY_PATH = "input/comments-vectors/dictionary.file-0";
    private static final String DOCUMENT_FREQUENCY_PATH = "input/comments-vectors/df-count/part-r-00000";
    private static final String FILENAME = "train_data.txt";
    private Configuration configuration;

    @Value("${naivebayes.variable.min-support}")
    private int minSupport;

    @Value("${naivebayes.variable.max-df-percent}")
    private int maxDFPercent;

    @Value("${naivebayes.variable.max-ngram-size}")
    private int maxNGramSize;

    public NaiveBayesAlgorithm() {
        this.configuration = new Configuration();
    }

    private void inputDataToSequenceFile() throws Exception {
        BufferedReader reader = new BufferedReader(new FileReader(FILENAME));
        FileSystem fs = FileSystem.getLocal(configuration);
        Path seqFilePath = new Path(SEQUENCE_FILE_PATH);
        fs.delete(seqFilePath, false);
        SequenceFile.Writer writer = SequenceFile
                .createWriter(fs, configuration, seqFilePath, Text.class, Text.class);
        int count = 0;

        try {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] tokens = line.split("\t");
                writer.append(new Text("/" + tokens[0] + "/comment" + count++), new Text(tokens[1]));
            }
        }
        finally {
            reader.close();
            writer.close();
        }
    }

    private void sequenceFileToSparseVector() throws Exception {
        SparseVectorsFromSequenceFiles svfsf = new SparseVectorsFromSequenceFiles();
        svfsf.run(new String[]{"-i", SEQUENCE_FILE_PATH, "-o", VECTORS_PATH, "-ow", "-s", Integer.toString(minSupport),
                "-x", Integer.toString(maxDFPercent), "-ng", Integer.toString(maxNGramSize)});
    }

    @Async
    @EventListener
    public void initializeModel(MyCustomEvent event) throws Exception {
        if (isModelSaved()) {
            loadNaiveBayesModel();
        }
        else {
            trainNaiveBayesModel();
        }
    }

    private boolean isModelSaved() {
        try {
            FileSystem fileSystem = FileSystem.getLocal(configuration);
            Path modelPath = new Path(MODEL_PATH);
            return fileSystem.exists(modelPath);
        }
        catch (Exception e) {
            return false;
        }
    }

    private void loadNaiveBayesModel() {
        try {
            NaiveBayesModel model = NaiveBayesModel.materialize(new Path(MODEL_PATH), configuration);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void trainNaiveBayesModel() throws Exception {
        inputDataToSequenceFile();
        sequenceFileToSparseVector();

        TrainNaiveBayesJob trainNaiveBayes = new TrainNaiveBayesJob();
        trainNaiveBayes.setConf(configuration);
        trainNaiveBayes.run(new String[]{"-i",
                VECTORS_PATH + "/tfidf-vectors", "-o", MODEL_PATH, "-li",
                LABEL_INDEX_PATH, "-el", "-c", "-ow"});
    }

    private Map<String, Integer> readDictionary(Configuration conf, Path dictionaryPath) {
        Map<String, Integer> dictionary = new HashMap<>();

        for (org.apache.mahout.common.Pair<Text, IntWritable> pair : new SequenceFileIterable<Text, IntWritable>(
                dictionaryPath, true, conf)) {
            dictionary.put(pair.getFirst().toString(), pair.getSecond().get());
        }

        return dictionary;
    }

    private Map<Integer, Long> readDocumentFrequency(Configuration conf, Path documentFrequencyPath) {
        Map<Integer, Long> documentFrequency = new HashMap<>();

        for (org.apache.mahout.common.Pair<IntWritable, LongWritable> pair : new SequenceFileIterable<IntWritable, LongWritable>(
                documentFrequencyPath, true, conf)) {
            documentFrequency.put(pair.getFirst().get(), pair.getSecond().get());
        }

        return documentFrequency;
    }

    public void classifyComments(Map<String, List<ProductComment>> productsCommentsMap) {
        Map<String, Integer> dictionary = readDictionary(configuration, new Path(DICTIONARY_PATH));
        Map<Integer, Long> documentFrequency = readDocumentFrequency(configuration, new Path(DOCUMENT_FREQUENCY_PATH));
        Multiset<String> words = ConcurrentHashMultiset.create();

        for (Map.Entry<String, List<ProductComment>> entry : productsCommentsMap.entrySet()) {
            for (int i = 0; i < entry.getValue().size(); i++) {
                if (!entry.getValue().get(i).getDescriptionTranslated().isEmpty()
                        && entry.getValue().get(i).getTonalityNaiveBayes() == -1) {
                    try {
                        Analyzer analyzer = new StandardAnalyzer(Version.LUCENE_46);
                        TokenStream tokenStream = analyzer.tokenStream("comment",
                                new StringReader(entry.getValue().get(i).getDescriptionTranslated()));
                        CharTermAttribute termAttribute = tokenStream.addAttribute(CharTermAttribute.class);
                        tokenStream.reset();

                        int wordCount = 0;
                        while (tokenStream.incrementToken()) {
                            if (termAttribute.length() > 0) {
                                String word = tokenStream.getAttribute(CharTermAttribute.class).toString();
                                Integer wordId = dictionary.get(word);

                                if (wordId != null) {
                                    words.add(word);
                                    wordCount++;
                                }
                            }
                        }

                        tokenStream.end();
                        tokenStream.close();

                        int documentCount = documentFrequency.get(-1).intValue();
                        Vector vector = new RandomAccessSparseVector(20000);
                        TFIDF tfidf = new TFIDF();

                        for (Multiset.Entry<String> entryWord : words.entrySet()) {
                            String word = entryWord.getElement();
                            int count = entryWord.getCount();
                            Integer wordId = dictionary.get(word);
                            Long freq = documentFrequency.get(wordId);
                            double tfIdfValue = tfidf.calculate(count, freq.intValue(), wordCount, documentCount);
                            vector.setQuick(wordId, tfIdfValue);
                        }

                        NaiveBayesModel model = NaiveBayesModel.materialize(new Path(MODEL_PATH), configuration);
                        StandardNaiveBayesClassifier classifier = new StandardNaiveBayesClassifier(model);

                        Vector resultVector = classifier.classifyFull(vector);
                        double bestScore = -Double.MAX_VALUE;
                        int bestCategoryId = -1;

                        for (Vector.Element element : resultVector.all()) {
                            int categoryId = element.index();
                            double score = element.get();

                            if (score > bestScore) {
                                bestScore = score;
                                bestCategoryId = categoryId;
                            }
                        }

                        if (bestCategoryId == 1) {
                            entry.getValue().get(i).setTonalityNaiveBayes(1);
                        }
                        else {
                            entry.getValue().get(i).setTonalityNaiveBayes(0);
                        }

                        analyzer.close();
                    }
                    catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    public boolean isModelReady() {
        return isModelSaved();
    }
}