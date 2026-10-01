package com.earlylearning.early_learning_server.material.client.zip;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import com.earlylearning.early_learning_server.common.error.ApiErrorDetails;
import com.earlylearning.early_learning_server.common.error.BusinessException;
import com.earlylearning.early_learning_server.common.error.ErrorCode;
import com.earlylearning.early_learning_server.material.model.MaterialPublishLimits;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 读取材料 ZIP 包并执行结构性校验：根目录平铺、恰好一个 config.json、
 * 文件名忽略大小写不重复、不接受目录、路径、嵌套压缩包与加密条目。
 *
 * <p>校验失败不产生任何临时文件；通过后媒体条目逐个落到临时文件，
 * 由调用方在发布结束后调用 cleanup 删除。
 */
@Component
public class MaterialZipReader {

    public static final String CONFIG_ENTRY_NAME = "config.json";

    private static final Logger log = LoggerFactory.getLogger(MaterialZipReader.class);

    /** 契约的包内文件名规则：非纯点号、无路径与控制字符，长度 1 到 128。 */
    private static final String NAME_PATTERN = "^(?!\\.{1,2}$)[^<>:\"/\\\\|?*\\x00-\\x1F\\x7F]+$";
    private static final int MAX_NAME_LENGTH = 128;

    private final MaterialPublishLimits limits;

    public MaterialZipReader(MaterialPublishLimits limits) {
        this.limits = limits;
    }

    /**
     * 解析暂存的 ZIP 文件。
     *
     * @throws BusinessException 不是有效 ZIP、违反平铺或唯一性规则、嵌套压缩包、加密条目，
     *                           或超出条目数与解压总量上限
     */
    public ZipPackage read(Path zipFile) {
        List<ZipPackage.PackagedFile> spooled = new ArrayList<>();
        try (ZipFile zip = new ZipFile(zipFile.toFile())) {
            byte[] configJson = null;
            Map<String, Boolean> namesByLower = new HashMap<>();
            long totalUncompressed = 0;

            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                requireAcceptableEntryName(name, entry.isDirectory());
                String lowerKey = name.toLowerCase(Locale.ROOT);
                if (namesByLower.put(lowerKey, Boolean.TRUE) != null) {
                    throw new BusinessException(ErrorCode.INVALID_REQUEST,
                            "包内文件名忽略大小写不得重复",
                            new ApiErrorDetails(null, null, null, null, null, null, name));
                }
                if (namesByLower.size() > limits.maxFileCount()) {
                    throw new BusinessException(ErrorCode.INVALID_REQUEST,
                            "包内文件数超过上限 " + limits.maxFileCount());
                }
                if (CONFIG_ENTRY_NAME.equals(name)) {
                    configJson = zip.getInputStream(entry).readAllBytes();
                    totalUncompressed += configJson.length;
                } else {
                    Path staged = spool(zip, entry);
                    long size = Files.size(staged);
                    totalUncompressed += size;
                    spooled.add(new ZipPackage.PackagedFile(name, staged, size));
                }
                if (totalUncompressed > limits.maxUncompressedBytes()) {
                    throw new BusinessException(ErrorCode.PAYLOAD_TOO_LARGE, null,
                            ApiErrorDetails.ofLimit(ApiErrorDetails.LimitName.SIZE_BYTES,
                                    limits.maxUncompressedBytes()));
                }
            }

            if (configJson == null) {
                throw invalid("压缩包缺少 config.json");
            }
            return new ZipPackage(configJson, List.copyOf(spooled));
        } catch (ZipException ex) {
            cleanupSpooled(spooled);
            throw invalid("不是有效的 ZIP 文件或包含不支持的加密条目");
        } catch (BusinessException ex) {
            cleanupSpooled(spooled);
            throw ex;
        } catch (IOException ex) {
            cleanupSpooled(spooled);
            throw new BusinessException(ErrorCode.DEPENDENCY_UNAVAILABLE, "读取材料压缩包失败", ex);
        }
    }

    /** 删除解析过程产生的媒体临时文件；解析失败路径上已自行清理，重复调用安全。 */
    public void cleanup(ZipPackage pkg) {
        if (pkg == null) {
            return;
        }
        for (ZipPackage.PackagedFile file : pkg.files()) {
            try {
                Files.deleteIfExists(file.stagedPath());
            } catch (IOException ex) {
                log.error("材料包临时文件未能删除，需人工清理 path={}", file.stagedPath(), ex);
            }
        }
    }

    private void requireAcceptableEntryName(String name, boolean directory) {
        if (directory) {
            throw invalid("压缩包含目录，只接受根目录平铺文件：" + name);
        }
        if (name.contains("/") || name.contains("\\")) {
            throw invalid("压缩包含路径，只接受根目录平铺文件：" + name);
        }
        if (name.isBlank() || name.length() > MAX_NAME_LENGTH || !name.matches(NAME_PATTERN)) {
            throw invalid("包内文件名不合法：" + name);
        }
        if (name.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            throw invalid("不接受嵌套压缩包：" + name);
        }
    }

    private Path spool(ZipFile zip, ZipEntry entry) throws IOException {
        Path staged = Files.createTempFile("material-entry-", ".part");
        try (InputStream in = zip.getInputStream(entry);
             OutputStream out = Files.newOutputStream(staged)) {
            in.transferTo(out);
        }
        return staged;
    }

    private void cleanupSpooled(List<ZipPackage.PackagedFile> spooled) {
        for (ZipPackage.PackagedFile file : spooled) {
            try {
                Files.deleteIfExists(file.stagedPath());
            } catch (IOException ex) {
                log.error("材料包临时文件未能删除，需人工清理 path={}", file.stagedPath(), ex);
            }
        }
    }

    private BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.INVALID_REQUEST, message);
    }
}
