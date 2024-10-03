package com.hardcode.commentsanalyzer.service;

import com.google.common.collect.ConcurrentHashMultiset;
import com.google.common.collect.Multiset;
import com.hardcode.commentsanalyzer.model.ProductComment;
import com.hardcode.commentsanalyzer.util.Util;
import org.apache.commons.lang3.tuple.Pair;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import javax.annotation.PostConstruct;
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
    private Util util;

    @Autowired
    public NaiveBayesAlgorithm(Util util) {
        this.util = util;
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
        svfsf.run(new String[]{"-i", SEQUENCE_FILE_PATH, "-o", VECTORS_PATH, "-ow", "-s", "3", "-x", "15", "-ng", "6"});
    }

    @PostConstruct
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

    @Cacheable(value = "naiveBayesCache", key = "#param")
    public Map<String, List<Pair<String, Integer>>> classifyComments(Map<String, List<ProductComment>> productsCommentsMap,
                                                                     Map<String, List<Pair<String, Integer>>> processedComments, int param) {
        Map<String, Integer> dictionary = readDictionary(configuration, new Path(DICTIONARY_PATH));
        Map<Integer, Long> documentFrequency = readDocumentFrequency(configuration, new Path(DOCUMENT_FREQUENCY_PATH));
        Multiset<String> words = ConcurrentHashMultiset.create();

        for (Map.Entry<String, List<Pair<String, Integer>>> entry : processedComments.entrySet()) {
            for (int i = 0; i < entry.getValue().size(); i++) {
                String text = entry.getValue().get(i).getKey();

                try {
                    Analyzer analyzer = new StandardAnalyzer(Version.LUCENE_46);
                    TokenStream tokenStream = analyzer.tokenStream("comment", new StringReader(text));
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
                        entry.getValue().set(i, Pair.of(text, 1));
                    }
                    else {
                        entry.getValue().set(i, Pair.of(text, 0));
                    }

                    analyzer.close();
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        return util.result(productsCommentsMap, processedComments);
    }
}