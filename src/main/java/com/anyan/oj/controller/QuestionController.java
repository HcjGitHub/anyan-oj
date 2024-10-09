package com.anyan.oj.controller;


import cn.dev33.satoken.annotation.SaCheckRole;
import cn.hutool.core.lang.TypeReference;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.anyan.oj.common.BaseResponse;
import com.anyan.oj.common.DeleteRequest;
import com.anyan.oj.common.ErrorCode;
import com.anyan.oj.common.ResultUtils;
import com.anyan.oj.constant.UserConstant;
import com.anyan.oj.exception.BusinessException;
import com.anyan.oj.exception.ThrowUtils;
import com.anyan.oj.model.dto.question.*;
import com.anyan.oj.model.dto.questionsubmit.QuestionSubmitAddRequest;
import com.anyan.oj.model.dto.questionsubmit.QuestionSubmitQueryRequest;
import com.anyan.oj.model.entity.Question;
import com.anyan.oj.model.entity.QuestionSubmit;
import com.anyan.oj.model.entity.User;
import com.anyan.oj.model.enums.QuestionSubmitLanguageEnum;
import com.anyan.oj.model.vo.QuestionSubmitVO;
import com.anyan.oj.model.vo.QuestionVO;
import com.anyan.oj.service.QuestionService;
import com.anyan.oj.service.QuestionSubmitService;
import com.anyan.oj.service.UserService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.anyan.oj.constant.QuestionRedisKeyConstant.CACHE_QUESTION_KEY;
import static com.anyan.oj.constant.RedisConstant.CACHE_QUESTION_PAGE_TTL;

/**
 * 问题接口
 */
@RestController
@RequestMapping("/question")
@Slf4j
public class QuestionController {

    @Resource
    private QuestionService questionService;

    @Resource
    private UserService userService;

    @Resource
    private QuestionSubmitService questionSubmitService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    // region 增删改查

    /**
     * 创建
     *
     * @param questionAddRequest
     * @return
     */
    @PostMapping("/add")
    public BaseResponse<Long> addQuestion(@RequestBody QuestionAddRequest questionAddRequest) {
        if (questionAddRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Question question = new Question();
        BeanUtils.copyProperties(questionAddRequest, question);
        List<String> tags = questionAddRequest.getTags();
        if (tags != null) {
            question.setTags(JSONUtil.toJsonStr(tags));
        }
        List<JudgeCase> judgeCase = questionAddRequest.getJudgeCase();
        if (judgeCase != null) {
            question.setJudgeCase(JSONUtil.toJsonStr(judgeCase));
        }
        JudgeConfig judgeConfig = questionAddRequest.getJudgeConfig();
        if (judgeConfig != null) {
            question.setJudgeConfig(JSONUtil.toJsonStr(judgeConfig));
        }
        questionService.validQuestion(question, true);
        User loginUser = userService.getLoginUser();
        question.setUserId(loginUser.getId());
        question.setFavourNum(0);
        question.setThumbNum(0);
        boolean result = questionService.save(question);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        long newQuestionId = question.getId();
        // 重新刷新缓存
        stringRedisTemplate.delete(CACHE_QUESTION_KEY);
        return ResultUtils.success(newQuestionId);
    }

    /**
     * 删除
     *
     * @param deleteRequest
     * @return
     */
    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteQuestion(@RequestBody DeleteRequest deleteRequest) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User user = userService.getLoginUser();
        long id = deleteRequest.getId();
        // 判断是否存在
        Question oldQuestion = questionService.getById(id);
        ThrowUtils.throwIf(oldQuestion == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可删除
        if (!oldQuestion.getUserId().equals(user.getId()) && !userService.isAdmin()) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        boolean b = questionService.removeById(id);
        // 重新刷新缓存
        stringRedisTemplate.delete(CACHE_QUESTION_KEY);
        return ResultUtils.success(b);
    }

    /**
     * 更新（仅管理员）
     *
     * @param questionUpdateRequest
     * @return
     */
    @PostMapping("/update")
    @SaCheckRole(UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateQuestion(@RequestBody QuestionUpdateRequest questionUpdateRequest) {
        if (questionUpdateRequest == null || questionUpdateRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Question question = new Question();
        BeanUtils.copyProperties(questionUpdateRequest, question);
        List<String> tags = questionUpdateRequest.getTags();
        if (tags != null) {
            question.setTags(JSONUtil.toJsonStr(tags));
        }
        List<JudgeCase> judgeCase = questionUpdateRequest.getJudgeCase();
        if (judgeCase != null) {
            question.setJudgeCase(JSONUtil.toJsonStr(judgeCase));
        }
        JudgeConfig judgeConfig = questionUpdateRequest.getJudgeConfig();
        if (judgeConfig != null) {
            question.setJudgeConfig(JSONUtil.toJsonStr(judgeConfig));
        }
        // 参数校验
        questionService.validQuestion(question, false);
        long id = questionUpdateRequest.getId();
        // 判断是否存在
        Question oldQuestion = questionService.getById(id);
        ThrowUtils.throwIf(oldQuestion == null, ErrorCode.NOT_FOUND_ERROR);
        boolean result = questionService.updateById(question);
        // 重新刷新缓存
        stringRedisTemplate.delete(CACHE_QUESTION_KEY);
        return ResultUtils.success(result);
    }

    /**
     * 根据 id 获取
     *
     * @param id
     * @return
     */
    @GetMapping("/get/vo")
    public BaseResponse<QuestionVO> getQuestionVOById(long id) {
        User loginUser = userService.getLoginUser();
        if (id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Question question = questionService.getById(id);
        return ResultUtils.success(questionService.getQuestionVO(question, loginUser));
    }

    /**
     * 返回对应语言的枚举值给前端
     *
     * @return
     */
    @GetMapping("/languages")
    public BaseResponse<List<String>> getAllLanguages() {
        List<String> statusMap = new ArrayList<>();
        for (QuestionSubmitLanguageEnum status : QuestionSubmitLanguageEnum.values()) {
            statusMap.add(status.getValue());
        }
        return ResultUtils.success(statusMap);
    }

    /**
     * 分页获取列表（仅管理员）
     *
     * @param questionQueryRequest
     * @return
     */
    @PostMapping("/list/page")
    @SaCheckRole(UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<Question>> listQuestionByPage(@RequestBody QuestionQueryRequest questionQueryRequest) {
        long current = questionQueryRequest.getCurrent();
        long size = questionQueryRequest.getPageSize();
        Page<Question> questionPage = questionService.page(new Page<>(current, size), questionService.getQueryWrapper(questionQueryRequest));
        return ResultUtils.success(questionPage);
    }

    /**
     * 分页获取列表（封装类）
     *
     * @param questionQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/list/page/vo")
    public BaseResponse<Page<QuestionVO>> listQuestionVOByPage(@RequestBody QuestionQueryRequest questionQueryRequest, HttpServletRequest request) {
        long current = questionQueryRequest.getCurrent();
        long size = questionQueryRequest.getPageSize();

        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        String pageJson = stringRedisTemplate.opsForValue().get(CACHE_QUESTION_KEY);
        Page<Question> questionPage = null;
        Page<QuestionVO> questionVoPage = null;
        //将分页查询到的数据缓存到redis
        if (StrUtil.isBlank(pageJson)) {
            //3.从数据库取出来之后
            questionPage = questionService.page(new Page<>(current, size), questionService.getQueryWrapper(questionQueryRequest));
            questionVoPage = questionService.getQuestionVOPage(questionPage);
            //4.再次缓存到redis,设置缓存的过期时间，30分钟，重新缓存查询一次
            stringRedisTemplate.opsForValue().set(CACHE_QUESTION_KEY, JSONUtil.toJsonStr((questionVoPage)), CACHE_QUESTION_PAGE_TTL, TimeUnit.MINUTES);
        } else {
            log.info("加载缓存");
            questionVoPage = JSONUtil.toBean(pageJson, new TypeReference<Page<QuestionVO>>() {
            }, true);
        }
        return ResultUtils.success(questionVoPage);
    }

    /**
     * 分页获取当前用户创建的资源列表
     *
     * @param questionQueryRequest
     * @return
     */
    @PostMapping("/my/list/page/vo")
    public BaseResponse<Page<QuestionVO>> listMyQuestionVOByPage(@RequestBody QuestionQueryRequest questionQueryRequest) {
        if (questionQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser();
        questionQueryRequest.setUserId(loginUser.getId());
        long current = questionQueryRequest.getCurrent();
        long size = questionQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        Page<Question> questionPage = questionService.page(new Page<>(current, size), questionService.getQueryWrapper(questionQueryRequest));
        return ResultUtils.success(questionService.getQuestionVOPage(questionPage));
    }

    // endregion

    /**
     * 编辑（用户）
     *
     * @param questionEditRequest
     * @param request
     * @return
     */
    @PostMapping("/edit")
    public BaseResponse<Boolean> editQuestion(@RequestBody QuestionEditRequest questionEditRequest, HttpServletRequest request) {
        if (questionEditRequest == null || questionEditRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Question question = new Question();
        BeanUtils.copyProperties(questionEditRequest, question);
        List<String> tags = questionEditRequest.getTags();
        if (tags != null) {
            question.setTags(JSONUtil.toJsonStr(tags));
        }
        List<JudgeCase> judgeCase = questionEditRequest.getJudgeCase();
        if (judgeCase != null) {
            question.setJudgeCase(JSONUtil.toJsonStr(judgeCase));
        }
        JudgeConfig judgeConfig = questionEditRequest.getJudgeConfig();
        if (judgeConfig != null) {
            question.setJudgeConfig(JSONUtil.toJsonStr(judgeConfig));
        }
        // 参数校验
        questionService.validQuestion(question, false);
        User loginUser = userService.getLoginUser();
        long id = questionEditRequest.getId();
        // 判断是否存在
        Question oldQuestion = questionService.getById(id);
        ThrowUtils.throwIf(oldQuestion == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可编辑
        if (!oldQuestion.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        boolean result = questionService.updateById(question);
        return ResultUtils.success(result);
    }


    /**
     * 提交答案
     *
     * @param id 提交id
     */
    @GetMapping("/question_submit/get/id")
    public BaseResponse<QuestionSubmitVO> getJudgeResult(Long id) {
        User loginUser = userService.getLoginUser();
        if (id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        QuestionSubmit questionSubmit = questionSubmitService.getById(id);
        if (questionSubmit == null) {
            //不存在，直接报错，返回一个空
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "提交答案不存在");
        }
        QuestionSubmitVO questionCommentVO = questionSubmitService.getQuestionSubmitVO(questionSubmit, loginUser);
        return ResultUtils.success(questionCommentVO);
    }


    @PostMapping("/question_submit/do")
    public BaseResponse<Long> doQuestionSubmit(@RequestBody QuestionSubmitAddRequest questionSubmitAddRequest) {
        if (questionSubmitAddRequest == null || questionSubmitAddRequest.getQuestionId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 登录才能提交
        final User loginUser = userService.getLoginUser();
        log.info("提交成功,题号：{},用户：{}", questionSubmitAddRequest.getQuestionId(), loginUser.getId());
        Long result = questionSubmitService.doQuestionSubmit(questionSubmitAddRequest, loginUser);
        stringRedisTemplate.delete(CACHE_QUESTION_KEY);
        return ResultUtils.success(result);

    }


    /**
     * 分页获取列表（封装类）
     *
     * @param questionSubmitQueryRequest
     * @return
     */
    @PostMapping("question_submit/list/page/vo")
    public BaseResponse<Page<QuestionSubmitVO>> listQuestionSubmitVOByPage(@RequestBody QuestionSubmitQueryRequest questionSubmitQueryRequest) {
        long current = questionSubmitQueryRequest.getCurrent();
        long size = questionSubmitQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        Page<QuestionSubmit> questionSubmitPage = questionSubmitService.page(new Page<>(current, size),
                questionSubmitService.getQueryWrapper(questionSubmitQueryRequest));
        final User loginUser = userService.getLoginUser();
        return ResultUtils.success(questionSubmitService.getQuestionSubmitVOPage(questionSubmitPage, loginUser));
    }


    @PostMapping("question_submit/my/list/page/vo")
    public BaseResponse<Page<QuestionSubmitVO>> listMyQuestionSubmitVOByPage(@RequestBody QuestionSubmitQueryRequest questionSubmitQueryRequest) {
        long current = questionSubmitQueryRequest.getCurrent();
        long size = questionSubmitQueryRequest.getPageSize();
        User loginUser = userService.getLoginUser();
        questionSubmitQueryRequest.setUserId(loginUser.getId());
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        Page<QuestionSubmit> questionSubmitPage = questionSubmitService.page(new Page<>(current, size),
                questionSubmitService.getQueryWrapper(questionSubmitQueryRequest));
        return ResultUtils.success(questionSubmitService.getQuestionSubmitVOPage(questionSubmitPage, loginUser));
    }


    /**
     * 根据 id 获取
     *
     * @param id
     * @return
     */
    @GetMapping("/question_submit/get/vo")
    public BaseResponse<QuestionSubmitVO> getQuestionSubmitVOById(long id) {
        if (id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        QuestionSubmit questionSubmit = questionSubmitService.getById(id);
        if (questionSubmit == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        final User loginUser = userService.getLoginUser();
        return ResultUtils.success(questionSubmitService.getQuestionSubmitVO(questionSubmit, loginUser));
    }

    @PostMapping("question_submit/my_record/list/vo")
    public BaseResponse<List<QuestionSubmitVO>> listMyQuestionSubmitVORecord(@RequestBody QuestionSubmitQueryRequest questionSubmitQueryRequest) {
        User loginUser = userService.getLoginUser();
        questionSubmitQueryRequest.setUserId(loginUser.getId());

        // 获取所有 QuestionSubmit 记录
        List<QuestionSubmit> questionSubmitList = questionSubmitService.list(questionSubmitService.getQueryWrapper(questionSubmitQueryRequest));

        // 将 QuestionSubmit 列表转换为 QuestionSubmitVO 列表
        List<QuestionSubmitVO> questionSubmitVOList = questionSubmitService.getQuestionSubmitVOList(questionSubmitList, loginUser);

        // 返回数据
        return ResultUtils.success(questionSubmitVOList);
    }


}
