package com.anyan.oj.service;

import com.anyan.oj.model.dto.question.QuestionQueryRequest;
import com.anyan.oj.model.entity.Question;
import com.anyan.oj.model.entity.User;
import com.anyan.oj.model.vo.QuestionVO;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * @author 15712
 * @description 针对表【question(题目)】的数据库操作Service
 * @createDate 2024-03-25 16:24:02
 */
public interface QuestionService extends IService<Question> {
    /**
     * 校验
     *
     * @param question
     * @param add
     */
    void validQuestion(Question question, boolean add);

    /**
     * 获取查询条件
     *
     * @param questionQueryRequest
     * @return
     */
    QueryWrapper<Question> getQueryWrapper(QuestionQueryRequest questionQueryRequest);

    /**
     * 获取题目封装
     *
     * @param question
     * @param loginUser
     * @return
     */
    QuestionVO getQuestionVO(Question question, User loginUser);

    /**
     * 分页获取题目封装
     *
     * @param questionPage
     * @return
     */
    Page<QuestionVO> getQuestionVOPage(Page<Question> questionPage);
}
