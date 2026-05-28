package cn.edu.cuc.class10.repository;

import cn.edu.cuc.class10.entity.UserWordFamiliarity;
import cn.edu.cuc.class10.entity.UserWordFamiliarityId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;          // 添加导入
import java.util.Optional;

@Repository
public interface UserWordFamiliarityRepository extends JpaRepository<UserWordFamiliarity, UserWordFamiliarityId> {

    Optional<UserWordFamiliarity> findByUserIdAndWordId(String userId, String wordId);

    // 新增：根据 userId 查询该用户所有单词的熟悉度记录
    List<UserWordFamiliarity> findByUserId(String userId);
}