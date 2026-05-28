package cn.edu.cuc.class10.repository;

import cn.edu.cuc.class10.entity.TestAnswerRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TestAnswerRecordRepository extends JpaRepository<TestAnswerRecord, String> {
    
    /**
     * 查询某个测试会话的所有答题记录（按题目序号排序）
     */
    List<TestAnswerRecord> findBySessionIdOrderByQuestionIndexAsc(String sessionId);
}
