package com.earlylearning.early_learning_server.storage.service.impl;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.earlylearning.early_learning_server.common.error.BusinessException;
import com.earlylearning.early_learning_server.common.error.ErrorCode;
import com.earlylearning.early_learning_server.entity.CloudFile;
import com.earlylearning.early_learning_server.entity.CloudFileStatus;
import com.earlylearning.early_learning_server.storage.mapper.CloudFileMapper;
import com.earlylearning.early_learning_server.storage.mapper.CloudFileQueryMapper;
import com.earlylearning.early_learning_server.storage.service.CloudFileDeletionService;

/** {@link CloudFileDeletionService} 的实现。 */
@Service
public class CloudFileDeletionServiceImpl implements CloudFileDeletionService {

    private static final Logger log = LoggerFactory.getLogger(CloudFileDeletionServiceImpl.class);

    private final CloudFileMapper cloudFileMapper;
    private final CloudFileQueryMapper cloudFileQueryMapper;
    private final TransactionTemplate transaction;

    public CloudFileDeletionServiceImpl(CloudFileMapper cloudFileMapper,
                                        CloudFileQueryMapper cloudFileQueryMapper,
                                        PlatformTransactionManager transactionManager) {
        this.cloudFileMapper = cloudFileMapper;
        this.cloudFileQueryMapper = cloudFileQueryMapper;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    @Override
    public CloudFile markDeleted(String fileCode) {
        return transaction.execute(status -> {
            CloudFile file = cloudFileMapper.selectForUpdate(fileCode);
            if (file == null) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
            }
            if (file.getStatus() == CloudFileStatus.DELETED) {
                // 幂等：重复调用返回同一结果，不报错。
                return file;
            }
            if (file.getStatus() != CloudFileStatus.READY) {
                // UPLOADING / INVALID 都不在"可标记删除"的范围内——契约只授权对 READY 打标记。
                throw new BusinessException(ErrorCode.RESOURCE_NOT_READY);
            }
            if (isReferenced(file)) {
                // 到这里状态一个字节都没动过，正是契约要的"保持原状态"。
                throw new BusinessException(ErrorCode.RESOURCE_IN_USE);
            }
            cloudFileMapper.updateStatus(file.getId(), CloudFileStatus.DELETED.value());
            log.info("标记删除文件 fileCode={} kind={}", fileCode, file.getFileKind());
            return cloudFileMapper.selectById(file.getId());
        });
    }

    /** 只要有一条引用就算命中，不必数总数。 */
    private boolean isReferenced(CloudFile file) {
        List<CloudFileQueryMapper.ReferenceCount> counts =
                cloudFileQueryMapper.countReferences(List.of(file.getId()));
        return counts.stream().anyMatch(row -> row.referenceCount() != null && row.referenceCount() > 0);
    }
}
