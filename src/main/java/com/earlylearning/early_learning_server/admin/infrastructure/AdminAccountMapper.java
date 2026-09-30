package com.earlylearning.early_learning_server.admin.infrastructure;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.earlylearning.early_learning_server.admin.domain.AdminAccount;

/** 管理员账号的读写。 */
@Mapper
public interface AdminAccountMapper extends BaseMapper<AdminAccount> {

    @Select("SELECT * FROM admin_account WHERE username = #{username}")
    AdminAccount selectByUsername(@Param("username") String username);

    @Select("SELECT * FROM admin_account WHERE id = #{id} FOR UPDATE")
    AdminAccount selectForUpdate(@Param("id") int id);

    @Select("SELECT * FROM admin_account ORDER BY id")
    List<AdminAccount> selectAllOrdered();

    /** 锁住全部 ACTIVE 行再计数：两个管理员同时互相停用时，只有一个能成功。 */
    @Select("SELECT COUNT(*) FROM (SELECT id FROM admin_account WHERE status = 'ACTIVE' FOR UPDATE) active")
    long countActiveForUpdate();

    @Select("SELECT COUNT(*) FROM admin_account")
    long countAll();

    @Update("UPDATE admin_account SET status = #{status} WHERE id = #{id}")
    int updateStatus(@Param("id") int id, @Param("status") String status);

    @Update("UPDATE admin_account SET password_hash = #{hash} WHERE id = #{id}")
    int updatePassword(@Param("id") int id, @Param("hash") String hash);
}
