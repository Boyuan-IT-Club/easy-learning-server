package com.earlylearning.early_learning_server.material.client.zip;

import java.nio.file.Path;
import java.util.List;

/**
 * 解析后的材料 ZIP 包：config.json 的字节内容加全部媒体文件。
 *
 * <p>媒体文件落成临时文件而不是留在内存：解压总量的部署上限（默认 1GB）远大于
 * 适合堆内持有的体积，后续上传按暂存路径流式读取。
 */
public record ZipPackage(

        byte[] configJson,

        List<PackagedFile> files) {

    /** 包内一个媒体文件：原始文件名加暂存路径。fileName 已通过平铺与重名校验。 */
    public record PackagedFile(

            String fileName,

            Path stagedPath,

            long sizeBytes) {
    }

    public java.util.Set<String> fileNames() {
        return files.stream().map(PackagedFile::fileName).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
}
