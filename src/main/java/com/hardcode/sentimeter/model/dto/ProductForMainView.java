package com.hardcode.sentimeter.model.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class ProductForMainView {
    private String productName;
    private float ratingFromClients;
    private int totalQuantityComments;
    private int posNumber;
    private int negNumber;
    private String positivePercentage;
}
