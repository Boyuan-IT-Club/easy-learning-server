package com.earlylearning.early_learning_server.material.client.zip;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.earlylearning.early_learning_server.common.error.BusinessException;
import com.earlylearning.early_learning_server.material.model.MaterialPublishLimits;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ZIP 结构校验的行为测试：平铺、唯一性、嵌套包与上限。
 * 走真实的 java.util.zip 打包与解包，不 mock 任何东西。
 */
class MaterialZipReaderTests {

    private static final MaterialPublishLimits LIMITS = new MaterialPublishLimits(10_000_000, 100_000, 3);

    @TempDir
    Path tempDir;

    private MaterialZipReader reader;
    private Path zipFile;

    @BeforeEach
    void setUp() {
        reader = new MaterialZipReader(LIMITS);
    }

    @AfterEach
    void tearDown() throws IOException {
        Files.deleteIfExists(zipFile);
    }

    @Test
    void readsFlatPackageWithSingleConfig() throws IOException {
        zipFile = writeZip(entry("config.json", "{}".getBytes()), entry("img.png", bytes(8)));

        ZipPackage pkg = reader.read(zipFile);

        assertThat(new String(pkg.configJson())).isEqualTo("{}");
        assertThat(pkg.fileNames()).containsExactly("img.png");
        assertThat(pkg.files().get(0).sizeBytes()).isEqualTo(8);
        reader.cleanup(pkg);
        assertThat(Files.exists(pkg.files().get(0).stagedPath())).isFalse();
    }

    @Test
    void rejectsMissingConfigJson() throws IOException {
        zipFile = writeZip(entry("img.png", bytes(8)));
        assertThatThrownBy(() -> reader.read(zipFile))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode().name()).isEqualTo("INVALID_REQUEST"));
    }

    @Test
    void rejectsCaseInsensitiveDuplicateNames() throws IOException {
        zipFile = writeZip(entry("IMG.png", bytes(8)), entry("img.PNG", bytes(8)));
        assertThatThrownBy(() -> reader.read(zipFile))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode().name()).isEqualTo("INVALID_REQUEST");
                    assertThat(ex.getDetails().fileName()).isEqualTo("img.PNG");
                });
    }

    @Test
    void rejectsDirectoryAndPathEntries() throws IOException {
        zipFile = writeZip(entry("config.json", "{}".getBytes()), entry("nested/dir.png", bytes(8)));
        assertThatThrownBy(() -> reader.read(zipFile)).isInstanceOf(BusinessException.class);
    }

    @Test
    void rejectsNestedZipEntry() throws IOException {
        zipFile = writeZip(entry("config.json", "{}".getBytes()), entry("inner.ZIP", bytes(8)));
        assertThatThrownBy(() -> reader.read(zipFile)).isInstanceOf(BusinessException.class);
    }

    @Test
    void rejectsFileCountOverLimit() throws IOException {
        zipFile = writeZip(entry("config.json", "{}".getBytes()),
                entry("a.png", bytes(1)), entry("b.png", bytes(1)), entry("c.png", bytes(1)));
        assertThatThrownBy(() -> reader.read(zipFile)).isInstanceOf(BusinessException.class);
    }

    @Test
    void rejectsUncompressedSizeOverLimit() throws IOException {
        zipFile = writeZip(entry("config.json", "{}".getBytes()), entry("big.wav", bytes(200_000)));
        assertThatThrownBy(() -> reader.read(zipFile))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode().name()).isEqualTo("PAYLOAD_TOO_LARGE");
                    assertThat(ex.getDetails().limit().maximum()).isEqualTo(100_000);
                });
    }

    private record Named(String name, byte[] content) {
    }

    private static Named entry(String name, byte[] content) {
        return new Named(name, content);
    }

    private static byte[] bytes(int size) {
        return new byte[size];
    }

    private Path writeZip(Named... entries) throws IOException {
        Path file = Files.createTempFile(tempDir, "test-zip-", ".zip");
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(buffer)) {
            for (Named entry : entries) {
                zip.putNextEntry(new ZipEntry(entry.name()));
                zip.write(entry.content());
                zip.closeEntry();
            }
        }
        Files.write(file, buffer.toByteArray());
        return file;
    }
}
