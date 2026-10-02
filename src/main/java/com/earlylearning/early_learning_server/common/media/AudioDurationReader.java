package com.earlylearning.early_learning_server.common.media;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;

import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.exceptions.CannotReadException;
import org.jaudiotagger.audio.exceptions.InvalidAudioFrameException;
import org.jaudiotagger.audio.exceptions.ReadOnlyFileException;
import org.jaudiotagger.tag.TagException;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * 读取音频文件时长，写入上传记录的 duration_ms。
 *
 * <p>支持的容器由 jaudiotagger 决定；webm 不在其中，这类文件的时长为 null。
 * 读取时显式传扩展名，不依赖临时文件的名字（见 readMillis 的注释）。
 * 该库只给到整秒，因此毫秒值必然是 1000 的整数倍。
 */
@Component
@Slf4j
public class AudioDurationReader {

    /**
     * 可解析的 MIME。
     *
     * <p><b>webm 不在其中</b>：jaudiotagger 不支持该容器，而契约的转写接口列了 webm。
     * 这类格式的 {@code duration_ms} 保持为空——宁可留空，也不填猜测值。
     */
    private static final Map<String, String> EXTENSION_BY_MIME = Map.ofEntries(
            Map.entry("audio/mpeg", "mp3"),
            Map.entry("audio/mp3", "mp3"),
            Map.entry("audio/mp4", "m4a"),
            Map.entry("audio/m4a", "m4a"),
            Map.entry("audio/x-m4a", "m4a"),
            Map.entry("audio/wav", "wav"),
            Map.entry("audio/x-wav", "wav"),
            Map.entry("audio/wave", "wav"),
            Map.entry("audio/ogg", "ogg"),
            Map.entry("audio/flac", "flac"),
            Map.entry("audio/x-flac", "flac"));

    /**
     * 读取时长。
     *
     * @return 毫秒；<b>null 表示无法确定</b>，调用方应保持字段为空，不要填替代值
     */
    public Long readMillis(File audioFile, String mimeType) {
        String extension = mimeType == null ? null : EXTENSION_BY_MIME.get(mimeType.toLowerCase(Locale.ROOT));
        if (extension == null) {
            return null;
        }
        try {
            // 必须用 readAs 指定扩展名：上传的内容先落成 *.part 的暂存文件，
            // 而 jaudiotagger 是按文件名扩展名挑 reader 的——用 read() 会得到
            // "No Reader associated with this extension:part"，于是所有音频的时长恒为空。
            int seconds = AudioFileIO.readAs(audioFile, extension).getAudioHeader().getTrackLength();
            return seconds <= 0 ? null : seconds * 1000L;
        } catch (CannotReadException | IOException | TagException
                 | ReadOnlyFileException | InvalidAudioFrameException ex) {
            // 声明是支持的格式却读不出时长：可能是文件损坏，也可能是库的兼容性缺口。
            // 不因它让整次上传失败（契约允许 duration_ms 为空），但必须留下痕迹——静默留空无法排查。
            log.warn("音频时长读取失败，duration_ms 将留空 file={} mimeType={}",
                    audioFile.getName(), mimeType, ex);
            return null;
        }
    }
}
