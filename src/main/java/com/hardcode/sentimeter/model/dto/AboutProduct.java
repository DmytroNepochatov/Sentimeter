package com.hardcode.sentimeter.model.dto;

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
    private List<ProductCommentForFullInfo> posCommentsMaxEnt;
    private List<ProductCommentForFullInfo> negCommentsMaxEnt;
    private List<ProductCommentForFullInfo> posCommentsNaiveBayes;
    private List<ProductCommentForFullInfo> negCommentsNaiveBayes;
}
