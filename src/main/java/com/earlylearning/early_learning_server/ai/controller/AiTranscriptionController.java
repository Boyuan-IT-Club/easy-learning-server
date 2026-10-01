package com.earlylearning.early_learning_server.ai.controller;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.earlylearning.early_learning_server.ai.dto.TaskHandleResponse;
import com.earlylearning.early_learning_server.ai.dto.TranscriptionContext;
import com.earlylearning.early_learning_server.ai.model.task.AiTask;
import com.earlylearning.early_learning_server.ai.model.transcription.TranscriptionCommand;
import com.earlylearning.early_learning_server.ai.service.transcription.AiTranscriptionService;
import com.earlylearning.early_learning_server.ai.service.transcription.AudioValidationService;
import com.earlylearning.early_learning_server.common.error.BusinessException;
import com.earlylearning.early_learning_server.common.error.ErrorCode;
import com.earlylearning.early_learning_server.common.web.ApiResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 录音转写提交。
 *
 * <p>权限：按教师隔离任务；本模块不校验。
 */
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@Slf4j
public class AiTranscriptionController {

    private final AudioValidationService audioValidationService;
    private final AiTranscriptionService aiTranscriptionService;

    /**
     * 提交一段录音。音频只读进内存（不写临时文件），校验通过后登记任务并立即返回凭据。
     */
    @PostMapping(path = "/transcribe", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<TaskHandleResponse>> transcribe(
            @RequestParam(name = "retry_attempt", defaultValue = "0") int retryAttempt,
            @RequestPart("audio") MultipartFile audio,
            @RequestPart("context") TranscriptionContext context) {
        log.info("录音转写请求 requestId={} businessType={} sizeBytes={} declaredMime={} retryAttempt={}",
                context.requestId(), context.businessType(), audio.getSize(), audio.getContentType(), retryAttempt);
        byte[] content = readAudio(audio);
        String detectedMime = audioValidationService.detect(content);
        audioValidationService.validate(content, audio.getContentType());

        AiTask task = aiTranscriptionService.submit(content, detectedMime, toCommand(context), retryAttempt);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.ok(TaskHandleResponse.from(task)));
    }

    private TranscriptionCommand toCommand(TranscriptionContext context) {
        return new TranscriptionCommand(context.requestId(), context.inputRevision(), context.businessType(),
                context.activityId(), context.target(), context.questionId(), context.attempt());
    }

    private byte[] readAudio(MultipartFile audio) {
        try {
            return audio.getBytes();
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "音频读取失败");
        }
    }
}
