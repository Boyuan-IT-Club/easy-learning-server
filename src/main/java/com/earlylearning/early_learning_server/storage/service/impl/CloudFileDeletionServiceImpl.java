package com.earlylearning.early_learning_server.storage.service.impl;

import java.util.List;

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

    private final CloudFileMapper mapper;
    private final CloudFileQueryMapper queryMapper;
    private final TransactionTemplate transaction;

    public CloudFileDeletionServiceImpl(CloudFileMapper mapper,
                                        CloudFileQueryMapper queryMapper,
                                        PlatformTransactionManager transactionManager) {
        this.mapper = mapper;
        this.queryMapper = queryMapper;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    @Override
    public CloudFile markDeleted(String fileCode) {
        return transaction.execute(status -> {
            CloudFile file = mapper.selectForUpdate(fileCode);
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
            mapper.updateStatus(file.getId(), CloudFileStatus.DELETED.value());
            return mapper.selectById(file.getId());
        });
    }

    /** 只要有一条引用就算命中，不必数总数。 */
    private boolean isReferenced(CloudFile file) {
        List<CloudFileQueryMapper.ReferenceCount> counts =
                queryMapper.countReferences(List.of(file.getId()));
        return counts.stream().anyMatch(row -> row.referenceCount() != null && row.referenceCount() > 0);
    }
}
