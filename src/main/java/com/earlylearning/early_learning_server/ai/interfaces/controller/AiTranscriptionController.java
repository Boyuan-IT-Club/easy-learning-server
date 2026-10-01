package com.earlylearning.early_learning_server.ai.interfaces.controller;
import com.earlylearning.early_learning_server.ai.interfaces.dto.TranscriptionContext;
import com.earlylearning.early_learning_server.ai.interfaces.dto.TaskHandleResponse;

import java.io.IOException;

import com.earlylearning.early_learning_server.ai.application.transcription.AiTranscriptionService;
import com.earlylearning.early_learning_server.ai.application.transcription.AudioValidator;
import com.earlylearning.early_learning_server.ai.domain.task.AiTask;
import com.earlylearning.early_learning_server.ai.domain.transcription.TranscriptionCommand;
import com.earlylearning.early_learning_server.common.error.BusinessException;
import com.earlylearning.early_learning_server.common.error.ErrorCode;
import com.earlylearning.early_learning_server.common.web.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 录音转写提交。
 *
 * <p>权限：按教师隔离任务；本模块不校验。
 */
@RestController
public class AiTranscriptionController {

    private final AudioValidator audioValidator;
    private final AiTranscriptionService transcriptionService;

    public AiTranscriptionController(AudioValidator audioValidator,
                                     AiTranscriptionService transcriptionService) {
        this.audioValidator = audioValidator;
        this.transcriptionService = transcriptionService;
    }

    /**
     * 提交一段录音。音频只读进内存（不写临时文件），校验通过后登记任务并立即返回凭据。
     */
    @PostMapping(path = "/api/ai/transcribe", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<TaskHandleResponse>> transcribe(
            @RequestParam(name = "retry_attempt", defaultValue = "0") int retryAttempt,
            @RequestPart("audio") MultipartFile audio,
            @RequestPart("context") TranscriptionContext context) {
        byte[] content = readAudio(audio);
        String detectedMime = audioValidator.detect(content);
        audioValidator.validate(content, audio.getContentType());

        AiTask task = transcriptionService.submit(content, detectedMime, toCommand(context), retryAttempt);
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
