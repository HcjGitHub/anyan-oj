package com.anyan.oj.model.dto.questionsubmit;

import lombok.Data;

import java.io.Serializable;

/**
 * @author 15712
 */
@Data
public class QuestionSubmitAddRequest implements Serializable {
    /**
     * 编程语言
     */
    private String language;

    /**
     * 用户代码
     */
    private String code;

    /**
     * 题目 id
     */
    private Long questionId;

    /**
     * 是否允许可见
     */
    private Integer isVisible;


    private static final long serialVersionUID = 1L;
}
