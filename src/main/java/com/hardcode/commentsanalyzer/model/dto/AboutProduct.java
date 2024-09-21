package com.hardcode.commentsanalyzer.model.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class AboutProduct {
    private String productName;
    private float ratingFromClients;
    private int totalQuantityComments;
    private String positivePercentageMaxEnt;
    private String positivePercentageNaiveBayes;
    private List<String> posCommentsMaxEnt;
    private List<String> negCommentsMaxEnt;
    private List<String> posCommentsNaiveBayes;
    private List<String> negCommentsNaiveBayes;
}
