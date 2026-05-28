package cn.edu.cuc.class10.repository;

import cn.edu.cuc.class10.entity.MistakeWord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MistakeWordRepository extends JpaRepository<MistakeWord, String> {
    
    /**
     * 查询某个用户的所有错题（按创建时间倒序）
     */
    List<MistakeWord> findByUserIdOrderByCreateTimeDesc(String userId);
    
    /**
     * 查询某个用户的某个单词是否在错题本中
     */
    Optional<MistakeWord> findByUserIdAndWordId(String userId, String wordId);
    
    /**
     * 删除某个用户的某个错题记录
     */
    void deleteByUserIdAndWordId(String userId, String wordId);
    
    /**
     * 统计某个用户的错题数量
     */
    long countByUserId(String userId);
}
