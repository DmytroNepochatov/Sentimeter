package com.hardcode.sentimeter.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.util.Date;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ProductComment {
    private String id;
    private String description;
    private String descriptionForUI;
    private Date commentCreated;
    private int grade;
    private String descriptionTranslated;
    private String locale;
    private int tonalityMaxEnt;
    private int tonalityNaiveBayes;
}
