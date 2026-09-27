package com.earlylearning.early_learning_server.ai.web;

import java.io.IOException;

import com.earlylearning.early_learning_server.ai.transcribe.AiTranscriptionService;
import com.earlylearning.early_learning_server.ai.transcribe.AudioValidator;
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
 * <p>权限：契约要求按教师隔离任务；本模块不校验。
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
     * 提交一段录音。音频只读进内存（契约要求不写临时文件），校验通过后登记任务并立即返回凭据。
     */
    @PostMapping(path = "/api/ai/transcribe", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<TaskHandleResponse>> transcribe(
            @RequestParam(name = "retry_attempt", defaultValue = "0") int retryAttempt,
            @RequestPart("audio") MultipartFile audio,
            @RequestPart("context") TranscriptionContext context) {
        byte[] content = readAudio(audio);
        String detectedMime = audioValidator.detect(content);
        audioValidator.validate(content, audio.getContentType());

        TaskHandleResponse handle = transcriptionService.submit(content, detectedMime, context, retryAttempt);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.ok(handle));
    }

    private byte[] readAudio(MultipartFile audio) {
        try {
            return audio.getBytes();
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "音频读取失败");
        }
    }
}
