package cn.edu.cuc.class10.repository;

import cn.edu.cuc.class10.entity.Interaction;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InteractionRepository extends JpaRepository<Interaction, String> {

    List<Interaction> findByUserIdAndWordIdOrderByTimestampDesc(String userId, String wordId);

    List<Interaction> findByUserId(String userId);

    @Query("SELECT MAX(i.timestamp) FROM Interaction i WHERE i.userId = :userId AND i.wordId = :wordId")
    Optional<Long> findLastTimestampByUserAndWord(@Param("userId") String userId, @Param("wordId") String wordId);

    /**
     * 统计指定用户每个单词的错误次数（feedback='unknown'），按错误次数降序返回
     * @param userId 用户ID
     * @param pageable 分页参数（例如 PageRequest.of(0, 5) 取前5个）
     * @return List<Object[]> 每个元素为 [wordId (String), mistakeCount (Long)]
     */
    @Query("SELECT i.wordId, COUNT(i) FROM Interaction i WHERE i.userId = :userId AND i.feedback = 'unknown' GROUP BY i.wordId ORDER BY COUNT(i) DESC")
    List<Object[]> countMistakesByUserId(@Param("userId") String userId, Pageable pageable);
}