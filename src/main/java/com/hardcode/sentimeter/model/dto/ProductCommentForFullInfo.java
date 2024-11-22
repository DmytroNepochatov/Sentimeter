package com.hardcode.sentimeter.model.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.util.Date;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ProductCommentForFullInfo implements Comparable<ProductCommentForFullInfo> {
    private String id;
    private String description;
    private Date commentCreated;
    private int grade;

    @Override
    public int compareTo(ProductCommentForFullInfo o) {
        return o.getCommentCreated().compareTo(this.commentCreated);
    }
}
